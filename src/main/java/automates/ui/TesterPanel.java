package automates.ui;

import automates.engine.Simulator;
import automates.engine.Simulator.Run;
import automates.model.Automaton;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Zone « Tester des mots » : un mot par mot séparé par des espaces, λ = mot vide.
 * Pour chaque mot : verdict ACCEPTÉ / REJETÉ, raison, trace de lecture et bouton
 * « Voir le chemin » (fenêtre pas à pas, comme JFLAP).
 */
final class TesterPanel extends VBox {

    private final TextArea words = new TextArea();
    private final VBox results = new VBox(8);
    private final HBox header = new HBox(12);
    private final Supplier<Automaton> source;
    private final Supplier<String> prefix;
    private final Supplier<Map<Integer, double[]>> positions;
    private final AlphabetModel alpha;
    private String lastSuggestion = "";

    TesterPanel(Supplier<Automaton> source, Supplier<String> prefix,
                Supplier<Map<Integer, double[]>> positions, AlphabetModel alpha) {
        super(8);
        this.source = source;
        this.prefix = prefix;
        this.positions = positions;
        this.alpha = alpha;
        getStyleClass().add("card");

        words.setPrefRowCount(2);
        words.setWrapText(true);
        words.setPromptText("Un ou plusieurs mots séparés par des espaces (λ = mot vide)");
        words.getStyleClass().add("mono");
        words.textProperty().addListener((o, a, b) -> refresh());

        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().add(UiUtil.heading("Tester des mots"));
        getChildren().addAll(header, words, results);
    }

    /** Σ a changé : on propose de nouveaux mots d'essai (sans écraser ce que l'utilisateur a tapé). */
    void onAlphabetChanged() {
        if (!alpha.isDefined()) return;
        String current = words.getText();
        if (current.isBlank() || current.equals(lastSuggestion)) {
            lastSuggestion = alpha.sampleWords();
            words.setText(lastSuggestion);
        }
        refresh();
    }

    /** Ajoute un composant à côté du titre (ex. choix de l'automate testé). */
    void addHeaderNode(Node n) { header.getChildren().add(n); }

    void refresh() {
        results.getChildren().clear();
        Automaton m = source.get();
        if (m == null) return;
        if (m.start() == null) {
            results.getChildren().add(UiUtil.muted("Définissez un état initial pour tester des mots."));
            return;
        }
        String p = prefix.get();
        Set<Character> sigma = alpha.symbols();
        for (String raw : words.getText().trim().split("\\s+")) {
            if (raw.isEmpty()) continue;
            String w = raw.equals("λ") || raw.equals("ε") ? "" : raw;

            // un mot doit être écrit avec les symboles de Σ
            Character foreign = null;
            for (char ch : w.toCharArray()) if (!sigma.contains(ch)) { foreign = ch; break; }
            if (foreign != null) {
                Label wl = new Label("Mot « " + w + " »");
                wl.getStyleClass().add("res");
                HBox t = new HBox(10, UiUtil.badgeWarn("MOT INVALIDE"), wl);
                t.setAlignment(Pos.CENTER_LEFT);
                Label why = new Label("La lettre « " + foreign + " » n’appartient pas à " + alpha.display()
                        + " : ce n’est pas un mot de Σ*, on ne peut donc pas le tester.");
                why.setWrapText(true);
                VBox bx = new VBox(3, t, why);
                bx.getStyleClass().add("word-result");
                results.getChildren().add(bx);
                continue;
            }

            Run run = Simulator.run(m, w, p);

            Label word = new Label("Mot « " + (w.isEmpty() ? "λ" : w) + " »");
            word.getStyleClass().add("res");
            Button path = new Button("Voir le chemin ▶");
            path.setOnAction(e -> PathWindow.open(m, p, positions.get(), run));
            HBox top = new HBox(10, UiUtil.badge(run.accepted() ? "ACCEPTÉ ✔" : "REJETÉ ✘", run.accepted()), word, path);
            top.setAlignment(Pos.CENTER_LEFT);

            Label reason = new Label("Pourquoi : " + run.reason());
            reason.setWrapText(true);
            Label trace = new Label("Trace : " + Simulator.trace(m, run, p) + (run.accepted() ? "" : (run.fullyRead() ? "" : "  ✘")));
            trace.getStyleClass().addAll("muted", "mono");
            trace.setWrapText(true);
            VBox box = new VBox(3, top, reason, trace);
            if (run.accepted() && m.hasEpsilon()) {
                Label pt = new Label("Chemin acceptant : " + Simulator.pathText(m, run, p));
                pt.getStyleClass().addAll("muted", "mono");
                pt.setWrapText(true);
                box.getChildren().add(pt);
            }
            box.getStyleClass().add("word-result");
            results.getChildren().add(box);
        }
    }
}
