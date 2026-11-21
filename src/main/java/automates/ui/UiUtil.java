package automates.ui;

import automates.model.Automaton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

import java.util.*;

/** Petits composants graphiques partagés par les deux onglets. */
final class UiUtil {

    private UiUtil() {}

    static Label heading(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("h2");
        return l;
    }

    static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        return l;
    }

    static VBox card(String title, Node... content) {
        VBox box = new VBox(8);
        box.getStyleClass().add("card");
        box.getChildren().add(heading(title));
        box.getChildren().addAll(content);
        return box;
    }

    /** Bloc « titre + dessin + table des transitions » pour un automate. */
    static VBox automatonBlock(String title, Automaton m, String prefix, Runnable onEdit) {
        Label t = new Label(title);
        t.setStyle("-fx-font-weight:bold; -fx-font-size:14px;");
        Label count = muted("— " + m.states().size() + " états, " + m.transitions().size() + " transitions");
        HBox header = new HBox(10, t, count);
        header.setAlignment(Pos.CENTER_LEFT);
        if (onEdit != null) {
            Button b = new Button("Ouvrir dans l’éditeur");
            b.setOnAction(e -> onEdit.run());
            header.getChildren().add(b);
        }

        AutomatonView view = new AutomatonView(prefix, false);
        view.show(m);
        ScrollPane sp = new ScrollPane(view);
        sp.getStyleClass().add("canvas-scroll");
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setPrefViewportHeight(view.getPrefHeight() + 2);
        sp.setMinHeight(view.getPrefHeight() + 22);

        // Pas de table de transition pour un AFN-ε (trop longue, inutile) : le dessin suffit.
        boolean hasEps = m.transitions().stream().anyMatch(tr -> tr.isEpsilon());
        Label dd0 = new Label("Automate dessiné");
        dd0.setStyle("-fx-font-weight:bold;");
        if (hasEps) {
            VBox boxEps = new VBox(8, header, dd0, sp);
            boxEps.getStyleClass().add("block");
            return boxEps;
        }

        ScrollPane tableScroll = new ScrollPane(transitionTable(m, prefix));
        tableScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tableScroll.setStyle("-fx-background-color:transparent;");
        tableScroll.setFitToHeight(true);
        tableScroll.setMinHeight(Region.USE_PREF_SIZE);

        Label tt = new Label("Table de transition");
        tt.setStyle("-fx-font-weight:bold;");
        Label dd = new Label("Automate dessiné");
        dd.setStyle("-fx-font-weight:bold;");
        VBox box = new VBox(8, header, tt, tableScroll, dd, sp);
        box.getStyleClass().add("block");
        return box;
    }

    /** Table de transitions : « → » état initial, « * » état final, « - » si aucune transition. */
    static GridPane transitionTable(Automaton m, String prefix) {
        GridPane g = new GridPane();
        g.setPadding(new Insets(2));
        List<Character> cols = new ArrayList<>(m.alphabet());
        if (m.hasEpsilon()) cols.add(Automaton.EPS);
        // la colonne « Sous-ensemble d’états » n'est plus affichée ; « Classe d’équivalence » (minimisation) reste.
        boolean labels = m.hasLabels() && !m.labelHeader().startsWith("Sous-ensemble");

        int c = 0;
        g.add(cell("État", true), c++, 0);
        for (char a : cols) g.add(cell(a == Automaton.EPS ? "ε" : String.valueOf(a), true), c++, 0);
        if (labels) g.add(cell(m.labelHeader(), true), c, 0);

        int r = 1;
        for (int s : m.states()) {
            c = 0;
            String name = (m.start() != null && m.start() == s ? "→" : "") + (m.isFinal(s) ? "*" : "") + m.name(s, prefix);
            g.add(cell(name, false), c++, r);
            for (char a : cols) {
                Set<String> targets = new LinkedHashSet<>();
                for (int t : m.targets(s, a)) targets.add(m.name(t, prefix));
                g.add(cell(targets.isEmpty() ? "-" : String.join(", ", targets), false), c++, r);
            }
            if (labels) g.add(cell(m.label(s) == null ? "" : m.label(s), false), c, r);
            r++;
        }
        return g;
    }

    /** Pastille colorée « ACCEPTÉ » / « REJETÉ » (ou tout autre texte). */
    static Label badge(String text, boolean ok) {
        Label l = new Label(text);
        l.getStyleClass().addAll("badge", ok ? "badge-ok" : "badge-ko");
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    static Label badgeWarn(String text) {
        Label l = new Label(text);
        l.getStyleClass().addAll("badge", "badge-warn");
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    /** Bloc de texte monospace (équations, journaux). */
    static Label monoLines(List<String> lines) {
        Label l = new Label(String.join("\n", lines));
        l.getStyleClass().addAll("mono", "eqbox");
        l.setWrapText(true);
        return l;
    }

    /** Étape numérotée : titre, explication et contenu, dans un cadre. */
    static VBox stepBox(String title, String explanation, Node... content) {
        Label t = new Label(title);
        t.getStyleClass().add("step-title");
        VBox box = new VBox(6, t);
        if (explanation != null && !explanation.isBlank()) box.getChildren().add(muted(explanation));
        box.getChildren().addAll(content);
        box.getStyleClass().add("block");
        return box;
    }

    static Label cell(String text, boolean head) {
        Label l = new Label(text);
        l.getStyleClass().add(head ? "cell-head" : "cell");
        l.setMinWidth(60);
        l.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(l, Priority.ALWAYS);
        return l;
    }
}
