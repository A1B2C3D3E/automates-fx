package automates.ui;

import automates.engine.ArdenSolver;
import automates.engine.AutomatonOps;
import automates.engine.Re;
import automates.engine.RegexParser;
import automates.model.Automaton;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/** Automate → expression régulière par le lemme d'Arden, avec toutes les étapes. */
final class ArdenPanel {

    private ArdenPanel() {}

    static VBox build(Automaton m, String prefix, Consumer<String> onVerify) {
        ArdenSolver.Result res = ArdenSolver.solve(m, prefix);
        VBox root = new VBox(12);
        root.getChildren().add(UiUtil.heading("Automate → expression régulière (lemme d’Arden)"));
        root.getChildren().add(UiUtil.muted("Méthode : on écrit une équation par état, puis on résout le système avec le lemme d’Arden "
                + "(X = AX + B ⟹ X = A*B) et des substitutions. Chaque étape est détaillée ci-dessous."));

        for (ArdenSolver.Step st : res.steps()) {
            root.getChildren().add(UiUtil.stepBox(st.title(), st.explanation(), UiUtil.monoLines(st.lines())));
        }

        // ---- résultat
        String text = res.text();
        boolean empty = res.regex() instanceof Re.Empty;
        TextField field = new TextField(text);
        field.setEditable(false);
        field.getStyleClass().add("big");

        Label verif = new Label();
        verif.setWrapText(true);
        if (!empty) {
            try {
                String w = AutomatonOps.distinguishingWord(m, RegexParser.toNfa(text));
                verif.getStyleClass().add(w == null ? "ok" : "ko");
                verif.setText(w == null
                        ? "✔ Vérifié automatiquement : cette expression reconnaît exactement le langage de l’automate."
                        : "✘ Écart détecté sur le mot : " + (w.isEmpty() ? "λ" : w));
            } catch (RuntimeException ex) {
                verif.setText("(vérification impossible : " + ex.getMessage() + ")");
            }
        } else {
            verif.setText("Le langage de cet automate est vide (aucun état final accessible).");
        }

        VBox box = new VBox(8, UiUtil.heading("Expression régulière obtenue"), field, verif);
        if (!empty) {
            String ext = Re.show(res.regex(), true);
            if (!ext.equals(text)) {
                box.getChildren().add(UiUtil.muted("Même expression en syntaxe étendue (union « | », optionnel « ? ») : " + ext));
            }
        }
        box.getChildren().add(UiUtil.muted("Notation du cours : « + » = union, « * » = étoile, ε = mot vide."));

        Button copy = new Button("Copier");
        copy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(text);
            Clipboard.getSystemClipboard().setContent(cc);
        });
        Button verify = new Button("Vérifier dans l’onglet « Regex → automates »");
        verify.setDisable(empty);
        verify.setOnAction(e -> onVerify.accept(text));
        box.getChildren().add(new HBox(8, copy, verify));
        box.getStyleClass().add("final-note");
        root.getChildren().add(box);
        return root;
    }
}
