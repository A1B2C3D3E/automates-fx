package automates.ui;

import automates.engine.Simulator;
import automates.engine.Simulator.Run;
import automates.engine.Simulator.Step;
import automates.model.Automaton;
import automates.model.Automaton.Transition;
import automates.ui.AutomatonView.Verdict;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.*;

/**
 * Fenêtre « chemin du mot » (comme JFLAP) : on suit la lecture du mot lettre par lettre.
 * Le ruban montre la lettre lue, l'automate montre les états courants (orange) et les
 * transitions empruntées ; à la fin, le verdict ACCEPTÉ / REJETÉ et la raison.
 */
final class PathWindow {

    private final Automaton m;
    private final String prefix;
    private final Run run;
    private final AutomatonView view;
    private final HBox tape = new HBox(2);
    private final VBox log = new VBox(4);
    private final Label stageLabel = new Label();
    private final HBox verdictBox = new HBox(10);
    private final Button first = new Button("⏮ Début"), prev = new Button("◀ Précédent"),
            next = new Button("Suivant ▶"), last = new Button("Fin ⏭"), play = new Button("▶ Lecture auto");
    private final Timeline timeline;
    private final List<Label> logLines = new ArrayList<>();
    private int stage = 0;

    private PathWindow(Automaton m, String prefix, Map<Integer, double[]> positions, Run run, String title) {
        this.m = m;
        this.prefix = prefix;
        this.run = run;

        view = new AutomatonView(prefix, false);
        view.show(m, positions);
        ScrollPane canvas = new ScrollPane(view);
        canvas.getStyleClass().add("canvas-scroll");
        canvas.setPrefViewportHeight(380);
        canvas.setFitToWidth(false);

        buildLog();
        ScrollPane logScroll = new ScrollPane(log);
        logScroll.setFitToWidth(true);
        logScroll.setPrefWidth(340);

        String w = run.word().isEmpty() ? "λ (mot vide)" : run.word();
        Label head = new Label("Lecture du mot « " + w + " » sur l’" + title);
        head.getStyleClass().add("h2");

        HBox buttons = new HBox(8, first, prev, next, last, play, stageLabel);
        buttons.setAlignment(Pos.CENTER_LEFT);
        first.setOnAction(e -> go(0));
        prev.setOnAction(e -> go(stage - 1));
        next.setOnAction(e -> go(stage + 1));
        last.setOnAction(e -> go(run.steps().size() - 1));

        timeline = new Timeline(new KeyFrame(Duration.millis(900), e -> {
            if (stage < run.steps().size() - 1) go(stage + 1); else stopPlay();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        play.setOnAction(e -> {
            if (timeline.getStatus() == Timeline.Status.RUNNING) { stopPlay(); return; }
            if (stage >= run.steps().size() - 1) go(0);
            play.setText("⏸ Pause");
            timeline.play();
        });

        verdictBox.setAlignment(Pos.CENTER_LEFT);

        VBox left = new VBox(10, head, tape, canvas, buttons, verdictBox);
        VBox.setVgrow(canvas, Priority.ALWAYS);
        left.setPadding(new Insets(12));
        BorderPane root = new BorderPane();
        root.setCenter(left);
        VBox right = new VBox(6, UiUtil.heading("Déroulement"), logScroll);
        right.setPadding(new Insets(12, 12, 12, 0));
        VBox.setVgrow(logScroll, Priority.ALWAYS);
        root.setRight(right);

        Stage st = new Stage();
        st.setTitle("Chemin du mot — " + w);
        Scene sc = new Scene(root, 1100, 640);
        sc.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        st.setScene(sc);
        st.setOnHidden(e -> timeline.stop());
        go(0);
        st.show();
    }

    static void open(Automaton m, String prefix, Map<Integer, double[]> positions, Run run) {
        new PathWindow(m, prefix, positions, run, m.kind());
    }

    private void stopPlay() { timeline.stop(); play.setText("▶ Lecture auto"); }

    // ------------------------------------------------------------ journal
    private void buildLog() {
        for (Step s : run.steps()) {
            String txt;
            if (s.index() == 0) {
                txt = "0.  Début : l’automate est dans " + Simulator.format(m, s.after(), prefix)
                        + (m.hasEpsilon() ? "  (ε-fermeture de l’état initial)" : "");
            } else if (s.after().isEmpty()) {
                txt = s.index() + ".  Lecture de « " + s.symbol() + " » depuis " + Simulator.format(m, s.from(), prefix)
                        + " : aucune transition → BLOCAGE";
            } else {
                txt = s.index() + ".  Lecture de « " + s.symbol() + " » : " + Simulator.format(m, s.from(), prefix)
                        + "  —" + s.symbol() + "→  " + Simulator.format(m, s.after(), prefix);
            }
            Label l = new Label(txt);
            l.setWrapText(true);
            l.setMaxWidth(310);
            l.setPadding(new Insets(4, 6, 4, 6));
            logLines.add(l);
            log.getChildren().add(l);
        }
        if (run.accepted() && run.acceptingPath() != null) {
            Label p = new Label("Chemin acceptant (avec les ε) :\n" + Simulator.pathText(m, run, prefix));
            p.getStyleClass().add("mono");
            p.setWrapText(true);
            p.setMaxWidth(310);
            p.setPadding(new Insets(8, 6, 4, 6));
            log.getChildren().add(p);
        }
    }

    // ------------------------------------------------------------ navigation
    private void go(int k) {
        int n = run.steps().size();
        stage = Math.max(0, Math.min(n - 1, k));
        boolean isLast = stage == n - 1;
        Step cur = run.steps().get(stage);
        boolean blocked = isLast && !run.fullyRead();

        // transitions déjà parcourues
        Set<Transition> trail = new LinkedHashSet<>();
        for (int i = 0; i < stage; i++) trail.addAll(run.steps().get(i).used());

        Set<Integer> hi = blocked ? cur.from() : cur.after();
        Set<Transition> active = new LinkedHashSet<>(cur.used());
        Verdict v = Verdict.NONE;
        if (isLast) {
            v = run.accepted() ? Verdict.ACCEPT : Verdict.REJECT;
            if (run.accepted() && run.acceptingPath() != null) {     // on dessine le chemin acceptant
                active.clear();
                trail.clear();
                for (Simulator.Move mv : run.acceptingPath()) active.add(mv.transition());
            }
        }
        view.setHighlight(hi, active, trail, v);

        // ruban
        tape.getChildren().clear();
        String word = run.word();
        if (word.isEmpty()) {
            Label c = new Label("λ");
            c.getStyleClass().addAll("tape-cell", isLast ? (run.accepted() ? "tape-read" : "tape-fail") : "tape-next");
            tape.getChildren().add(c);
        } else {
            int read = Math.min(stage, run.consumed());
            for (int i = 0; i < word.length(); i++) {
                Label c = new Label(String.valueOf(word.charAt(i)));
                c.getStyleClass().add("tape-cell");
                if (i < read) c.getStyleClass().add("tape-read");
                else if (i == read && blocked) c.getStyleClass().add("tape-fail");
                else if (i == read && !isLast) c.getStyleClass().add("tape-next");
                tape.getChildren().add(c);
            }
        }

        stageLabel.setText("   étape " + stage + " / " + (n - 1));
        for (int i = 0; i < logLines.size(); i++) {
            logLines.get(i).setStyle(i == stage ? "-fx-background-color:#ffe9cc; -fx-background-radius:6; -fx-font-weight:bold;" : "");
        }
        first.setDisable(stage == 0);
        prev.setDisable(stage == 0);
        next.setDisable(isLast);
        last.setDisable(isLast);

        verdictBox.getChildren().clear();
        if (isLast) {
            Label r = new Label(run.reason());
            r.setWrapText(true);
            r.setMaxWidth(700);
            verdictBox.getChildren().addAll(UiUtil.badge(run.accepted() ? "ACCEPTÉ ✔" : "REJETÉ ✘", run.accepted()), r);
        } else if (stage > 0) {
            verdictBox.getChildren().add(UiUtil.muted("Lecture de « " + cur.symbol() + " » : "
                    + Simulator.format(m, cur.from(), prefix) + "  —" + cur.symbol() + "→  " + Simulator.format(m, cur.after(), prefix)));
        } else {
            verdictBox.getChildren().add(UiUtil.muted("Cliquez sur « Suivant » pour lire la première lettre."));
        }
    }
}
