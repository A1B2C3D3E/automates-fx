package automates.ui;

import automates.engine.AutomatonOps;
import automates.engine.ProductOps;
import automates.engine.ProductOps.Op;
import automates.engine.RegexParser;
import automates.engine.RegexParser.Syntax;
import automates.io.AutomatonIO;
import automates.model.Automaton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Onglet 3 : intersection (∩) et union (∪) de deux langages réguliers.
 * Chaque langage est donné par une expression régulière, un automate dessiné dans l'éditeur,
 * un fichier .auto ou un exemple. Dès qu'une opération est choisie, toute la construction
 * (AFD complets, automate produit, états finals, minimisation, regex) est affichée avec les explications.
 */
public class OperationsTab extends Tab {

    private final AlphabetModel alpha;
    private final CheckBox extended = new CheckBox("Notation étendue des regex (union = |  et  + = « un ou plus »)");
    private final ToggleButton bInter = new ToggleButton("L₁ ∩ L₂   (intersection)");
    private final ToggleButton bUnion = new ToggleButton("L₁ ∪ L₂   (union)");
    private final VBox out = new VBox(14);
    private final Slot s1, s2;
    private Consumer<Automaton> onOpenInEditor = m -> {};
    private Consumer<String> onVerifyRegex = s -> {};

    public OperationsTab(AlphabetModel alpha, Supplier<Automaton> editorAutomaton) {
        super("Intersection / union");
        this.alpha = alpha;
        setClosable(false);

        Supplier<Syntax> syntax = () -> extended.isSelected() ? Syntax.ETENDU : Syntax.COURS;
        s1 = new Slot(1, alpha, editorAutomaton, syntax, this::refresh);
        s2 = new Slot(2, alpha, editorAutomaton, syntax, this::refresh);
        HBox slots = new HBox(12, s1, s2);
        HBox.setHgrow(s1, Priority.ALWAYS);
        HBox.setHgrow(s2, Priority.ALWAYS);
        s1.setMaxWidth(Double.MAX_VALUE);
        s2.setMaxWidth(Double.MAX_VALUE);
        s1.prefWidthProperty().bind(slots.widthProperty().divide(2));
        s2.prefWidthProperty().bind(slots.widthProperty().divide(2));

        ToggleGroup g = new ToggleGroup();
        bInter.setToggleGroup(g);
        bUnion.setToggleGroup(g);
        g.selectedToggleProperty().addListener((o, a, b) -> refresh());
        extended.setOnAction(e -> { s1.recompute(false); s2.recompute(false); refresh(); });

        Label how = UiUtil.muted("1) Donnez deux langages (une regex, un automate de l’éditeur, un fichier .auto ou l’exemple du cours).  "
                + "2) Choisissez l’opération : la construction s’affiche aussitôt, étape par étape.");
        HBox ops = new HBox(10, UiUtil.heading("Opération :"), bInter, bUnion);
        ops.setAlignment(Pos.CENTER_LEFT);
        Button example = new Button("Charger l’exemple du cours (nombre pair de a  /  nombre pair de b)");
        example.setOnAction(e -> {
            s1.useExample();
            s2.useExample();
            if (g.getSelectedToggle() == null) g.selectToggle(bInter);
            else refresh();
        });

        VBox top = UiUtil.card("Intersection et union de deux langages", how, slots, extended, new HBox(10, ops, example), UiUtil.muted(
                "Les deux automates doivent être écrits sur l’alphabet Σ défini en haut."));
        VBox content = new VBox(14, top, out);
        content.setPadding(new Insets(14));
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        setContent(sp);

        refresh();
    }

    public void setOnOpenInEditor(Consumer<Automaton> c) { onOpenInEditor = c; }
    public void setOnVerifyRegex(Consumer<String> c) { onVerifyRegex = c; }

    /** Σ a changé : on revérifie les deux automates (symboles hors Σ) et on recalcule. */
    public void onAlphabetChanged() {
        s1.recompute(false);
        s2.recompute(false);
        refresh();
    }

    // ------------------------------------------------------------------ calcul et affichage
    private Op selectedOp() {
        if (bInter.isSelected()) return Op.INTERSECTION;
        if (bUnion.isSelected()) return Op.UNION;
        return null;
    }

    private void refresh() {
        out.getChildren().clear();
        if (!alpha.isDefined()) return;
        Automaton m1 = s1.automaton(), m2 = s2.automaton();
        if (m1 == null || m2 == null) {
            String which = m1 == null && m2 == null ? "les deux automates" : m1 == null ? "l’automate 1" : "l’automate 2";
            out.getChildren().add(UiUtil.muted("Complétez " + which + " (voir le message en rouge ci-dessus) pour pouvoir calculer."));
            return;
        }
        Op op = selectedOp();
        if (op == null) {
            out.getChildren().add(UiUtil.muted("Choisissez maintenant l’opération : intersection (∩) ou union (∪)."));
            return;
        }
        try {
            out.getChildren().add(render(ProductOps.run(m1, m2, op, alpha.symbols())));
        } catch (RuntimeException e) {
            Label l = new Label("Calcul impossible : " + e.getMessage());
            l.getStyleClass().add("ko");
            l.setWrapText(true);
            out.getChildren().add(l);
        }
    }

    private VBox render(ProductOps.Result r) {
        Op op = r.op();
        String L = "L₁ " + op.symbol + " L₂";
        VBox root = new VBox(14);

        // ---------------- principe
        String principe = op == Op.INTERSECTION
                ? "Un mot est dans L₁ ∩ L₂ s’il est accepté par A1 ET par A2. On lit le mot dans les deux automates EN MÊME TEMPS : "
                  + "un état du produit est un couple (état de A1, état de A2). Le mot est accepté si les DEUX composantes sont finales."
                : "Un mot est dans L₁ ∪ L₂ s’il est accepté par A1 OU par A2. On lit le mot dans les deux automates EN MÊME TEMPS : "
                  + "un état du produit est un couple (état de A1, état de A2). Le mot est accepté si AU MOINS UNE composante est finale. "
                  + "Il faut pour cela des AFD COMPLETS : sinon un mot qui bloque dans A1 serait rejeté alors que A2 l’accepte peut-être.";
        List<String> plan = List.of(
                "Étape 1 : mettre A1 en AFD complet (déterminisation si besoin, puis état puits p si l’AFD est incomplet)",
                "Étape 2 : faire de même pour A2 (ses états sont notés avec une apostrophe : q0', A', p')",
                "Étape 3 : construire l’automate produit (couples atteignables, transitions, états finals)",
                "Étape 4 : minimiser l’AFD obtenu (partitions Π0, Π1, …)",
                "Étape 5 : conclure (mots d’essai, vérification automatique, expression régulière avec le lemme d’Arden)");
        VBox intro = UiUtil.stepBox("Calcul de " + L + " (" + op.noun + ")", principe, UiUtil.monoLines(plan));
        Label d = UiUtil.muted("A1 : " + s1.description() + "      A2 : " + s2.description() + "      Alphabet commun : " + alphabetText(r.alphabet()));
        intro.getChildren().add(d);
        root.getChildren().add(intro);

        // ---------------- étapes 1 et 2
        root.getChildren().add(operandBox(r.first(), 1));
        root.getChildren().add(operandBox(r.second(), 2));

        // ---------------- étape 3 : produit
        VBox prodTitle = UiUtil.stepBox("Étape 3 : automate produit",
                "États = couples (P, X). L’état initial est le couple des deux états initiaux. Pour chaque lettre a : "
                        + "trans((P, X), a) = (trans(P, a), trans(X, a)). On ne garde que les couples atteignables depuis l’état initial.",
                UiUtil.monoLines(r.buildLog()));
        root.getChildren().add(prodTitle);

        String rule = op == Op.INTERSECTION
                ? "Règle de l’intersection : (P, X) est final si P est final ET X est final."
                : "Règle de l’union : (P, X) est final si P est final OU X est final.";
        root.getChildren().add(UiUtil.stepBox("États finals du produit", rule, UiUtil.monoLines(r.finalLog())));

        Automaton prod = r.product();
        root.getChildren().add(UiUtil.automatonBlock("Automate produit pour " + L, prod, "q",
                () -> onOpenInEditor.accept(prod.copy())));
        if (r.isEmptyLanguage()) {
            VBox nb = new VBox(wrap("Aucun état final n’est atteignable dans le produit : " + L + " = ∅ "
                    + "(aucun mot n’est accepté)."));
            nb.getStyleClass().add("sink-note");
            root.getChildren().add(nb);
        }

        // ---------------- étape 4 : minimisation
        root.getChildren().add(UiUtil.heading("Étape 4 : minimisation de l’AFD produit"));
        root.getChildren().add(MinimizationPanel.build(prod, r.alphabet(), m -> onOpenInEditor.accept(m)));

        // ---------------- étape 5 : conclusion
        root.getChildren().add(conclusion(r, L));
        return root;
    }

    private VBox operandBox(ProductOps.Operand o, int n) {
        String A = "A" + n;
        VBox box = new VBox(10);
        box.getChildren().add(UiUtil.heading("Étape " + n + " : " + A + " en AFD complet"));

        if (o.wasDeterministic()) {
            box.getChildren().add(UiUtil.stepBox(A + " est déjà un AFD",
                    "Aucune ε-transition et au plus une transition par lettre : pas besoin de déterminiser.", new Label("")));
            box.getChildren().remove(box.getChildren().size() - 1);
        } else {
            box.getChildren().add(UiUtil.stepBox("Déterminisation de " + A + " (" + o.original().kind() + " → AFD)",
                    "Méthode des sous-ensembles avec ε-fermeture. Les états de l’AFD sont notés A, B, C…"
                            + (n == 2 ? " (avec une apostrophe pour A2 : A', B', C'…)" : ""),
                    UiUtil.monoLines(o.detLog())));
        }
        box.getChildren().add(UiUtil.automatonBlock(A + " en AFD" + (o.sink() == null ? "" : " (incomplet)"), o.dfa(), "q", null));

        if (o.sink() != null) {
            String miss = String.join(", ", o.missing());
            VBox nb = new VBox(wrap("L’AFD de " + A + " est INCOMPLET : " + miss + " n’est (ne sont) pas défini(es). "
                    + "On ajoute un état PUITS " + o.complete().name(o.sink(), "q") + " (non final, qui boucle sur toutes les lettres de Σ) "
                    + "vers lequel on envoie les transitions manquantes. Le langage reconnu ne change pas."));
            nb.getStyleClass().add("sink-note");
            box.getChildren().add(nb);
            box.getChildren().add(UiUtil.automatonBlock("AFD complet de " + A + " (avec le puits " + o.complete().name(o.sink(), "q") + ")",
                    o.complete(), "q", null));
        } else {
            VBox nb = new VBox(wrap("L’AFD de " + A + " est déjà COMPLET (toutes les transitions sont définies sur Σ) : pas de puits à ajouter."));
            nb.getStyleClass().add("final-note");
            box.getChildren().add(nb);
        }
        return box;
    }

    private VBox conclusion(ProductOps.Result r, String L) {
        Op op = r.op();
        VBox root = new VBox(12);
        root.getChildren().add(UiUtil.heading("Étape 5 : conclusion"));

        // ---- résumé
        int n = r.minimal().states().size(), nm = r.minimalPartial().states().size();
        String res;
        if (r.isEmptyLanguage()) {
            res = L + " = ∅ : aucun mot n’appartient à la fois aux deux langages.";
            if (op == Op.UNION) res = L + " = ∅ : les deux langages sont vides.";
        } else {
            res = L + " est reconnu par l’AFD minimal de l’étape 4 : " + n + " état" + (n > 1 ? "s" : "")
                    + (r.minimization().minimalHasSink() ? " (" + nm + " sans le puits)." : ".");
        }
        VBox sum = new VBox(wrap(res));
        sum.getStyleClass().add("final-note");
        root.getChildren().add(sum);

        // ---- mots d'essai
        GridPane g = new GridPane();
        g.setPadding(new Insets(2));
        String[] heads = {"Mot", "dans L₁ ?", "dans L₂ ?", "dans " + L + " ?", "Calcul"};
        for (int c = 0; c < heads.length; c++) g.add(UiUtil.cell(heads[c], true), c, 0);
        int row = 1;
        for (ProductOps.Sample s : r.samples()) {
            g.add(UiUtil.cell(s.word().isEmpty() ? "λ" : s.word(), false), 0, row);
            g.add(mark(s.in1()), 1, row);
            g.add(mark(s.in2()), 2, row);
            g.add(mark(s.inResult()), 3, row);
            g.add(UiUtil.cell(mk(s.in1()) + " " + op.connective + " " + mk(s.in2()) + " = " + mk(s.inResult()), false), 4, row);
            row++;
        }
        ScrollPane gs = new ScrollPane(g);
        gs.setFitToHeight(true);
        gs.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        gs.setStyle("-fx-background-color:transparent;");
        gs.setMinHeight(Region.USE_PREF_SIZE);
        root.getChildren().add(UiUtil.stepBox("Quelques mots pour comprendre",
                "Pour chaque mot : est-il accepté par A1 ? par A2 ? et par le produit ? Le résultat doit toujours être « A1 "
                        + op.connective + " A2 ».", gs));

        // ---- vérification automatique
        ProductOps.Verification v = r.verification();
        Label ver = new Label();
        ver.setWrapText(true);
        if (v.ok()) {
            ver.getStyleClass().add("ok");
            ver.setText("✔ Vérifié automatiquement sur " + v.tested() + " mots (longueur ≤ " + v.maxLength() + ") : le produit accepte exactement les mots acceptés par A1 "
                    + op.connective + " A2, et l’AFD minimal reconnaît le même langage que le produit.");
        } else {
            ver.getStyleClass().add("ko");
            ver.setText("✘ Écart détecté sur le mot « " + show(v.counterExample() != null ? v.counterExample() : v.minimalDifference()) + " ». Signalez-le : le calcul est faux.");
        }
        root.getChildren().add(ver);

        // ---- tester d'autres mots
        Automaton minFull = r.minimal(), minPart = r.minimalPartial(), prod = r.product();
        ComboBox<String> which = new ComboBox<>();
        which.getItems().addAll("AFD minimal complet", "AFD minimal (sans puits)", "Produit (non minimisé)");
        which.getSelectionModel().select(0);
        TesterPanel tester = new TesterPanel(() -> switch (which.getSelectionModel().getSelectedIndex()) {
            case 1 -> minPart;
            case 2 -> prod;
            default -> minFull;
        }, () -> "q", () -> null, alpha);
        tester.addHeaderNode(new Label("sur :"));
        tester.addHeaderNode(which);
        which.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> tester.refresh());
        tester.onAlphabetChanged();
        root.getChildren().add(tester);

        // ---- expression régulière (lemme d'Arden)
        VBox arden = new VBox(12);
        Button toRegex = new Button("→ Expression régulière de " + L + " (lemme d’Arden)");
        toRegex.getStyleClass().add("primary");
        toRegex.setOnAction(e -> {
            arden.getChildren().clear();
            Automaton plain = plain(minPart);
            List<String> ren = new ArrayList<>();
            for (int s : plain.states()) {
                String lab = minFull.label(s);
                ren.add("q" + s + "  =  " + (lab == null ? minFull.name(s, "q") : lab));
            }
            arden.getChildren().add(UiUtil.stepBox("Renommage des états de l’AFD minimal (pour écrire les équations)",
                    "On applique Arden à l’AFD minimal" + (r.minimization().minimalHasSink() ? " sans le puits (un état puits n’apporte aucun mot)" : "")
                            + ". Chaque état qi correspond à une classe d’états du produit :", UiUtil.monoLines(ren)));
            arden.getChildren().add(ArdenPanel.build(plain, "q", onVerifyRegex));
        });
        root.getChildren().add(new VBox(8, UiUtil.muted("Pour obtenir l’expression régulière de " + L
                + " (utile surtout pour ∩, qui n’a pas de symbole dans une regex) :"), toRegex, arden));
        return root;
    }

    // ------------------------------------------------------------------ utilitaires
    private static Label mark(boolean b) {
        Label l = UiUtil.cell(b ? "✔" : "✘", false);
        l.getStyleClass().add(b ? "ok" : "ko");
        return l;
    }

    private static String mk(boolean b) { return b ? "✔" : "✘"; }

    private static String show(String w) { return w == null || w.isEmpty() ? "λ" : w; }

    private static Label wrap(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        return l;
    }

    private static String alphabetText(List<Character> a) {
        StringJoiner j = new StringJoiner(", ", "Σ = {", "}");
        for (char x : a) j.add(String.valueOf(x));
        return j.toString();
    }

    /** Copie sans noms personnalisés : les états s'appellent q0, q1, … (équations d'Arden lisibles). */
    static Automaton plain(Automaton m) {
        Automaton p = new Automaton();
        for (int s : m.states()) p.addState(s);
        for (Automaton.Transition t : m.transitions()) p.addTransition(t.from(), t.to(), t.symbol());
        p.setStart(m.start());
        for (int f : m.finals()) p.setFinal(f, true);
        return p;
    }

    // ================================================================== un des deux automates d'entrée
    private static final class Slot extends VBox {
        private static final String[] MODES = {"Expression régulière", "Automate de l’éditeur", "Fichier .auto", "Exemple du cours"};

        private final int index;
        private final AlphabetModel alpha;
        private final Supplier<Automaton> editor;
        private final Supplier<Syntax> syntax;
        private final Runnable changed;
        private final ComboBox<String> mode = new ComboBox<>();
        private final TextField regexField = new TextField();
        private final Button grab = new Button("Prendre l’automate dessiné dans l’éditeur");
        private final Button open = new Button("Ouvrir un fichier .auto…");
        private final Label status = new Label();
        private final VBox preview = new VBox(4);
        private final VBox inputs = new VBox(6);
        private Automaton automaton, editorCopy, fileCopy;
        private String description = "(non défini)";

        Slot(int index, AlphabetModel alpha, Supplier<Automaton> editor, Supplier<Syntax> syntax, Runnable changed) {
            super(8);
            this.index = index;
            this.alpha = alpha;
            this.editor = editor;
            this.syntax = syntax;
            this.changed = changed;
            getStyleClass().add("block");

            mode.getItems().addAll(MODES);
            mode.getSelectionModel().select(0);
            regexField.getStyleClass().add("mono");
            regexField.setPromptText(index == 1 ? "Ex. : (a+b)*a" : "Ex. : b(a+b)*");
            status.setWrapText(true);

            mode.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
                if (b.intValue() == 1 && editorCopy == null) grabEditor();
                showInputs();
                recompute(true);
            });
            regexField.textProperty().addListener((o, a, b) -> recompute(true));
            grab.setOnAction(e -> { grabEditor(); recompute(true); });
            open.setOnAction(e -> openFile());

            HBox head = new HBox(10, UiUtil.heading("Automate " + index + "  (langage L" + (index == 1 ? "₁" : "₂") + ")"), mode);
            head.setAlignment(Pos.CENTER_LEFT);
            getChildren().addAll(head, inputs, status, preview);
            showInputs();
            recompute(false);
        }

        Automaton automaton() { return automaton; }
        String description() { return description; }

        private void showInputs() {
            inputs.getChildren().clear();
            switch (mode.getSelectionModel().getSelectedIndex()) {
                case 0 -> inputs.getChildren().add(regexField);
                case 1 -> inputs.getChildren().add(grab);
                case 2 -> inputs.getChildren().add(open);
                default -> inputs.getChildren().add(UiUtil.muted(index == 1
                        ? "A1 : mots avec un nombre PAIR de a (Σ = {a, b})."
                        : "A2 : mots avec un nombre PAIR de b (Σ = {a, b})."));
            }
        }

        void useExample() {
            mode.getSelectionModel().select(3);
            showInputs();
            recompute(false);
        }

        private void grabEditor() {
            Automaton m = editor.get();
            editorCopy = (m == null || m.isEmpty()) ? null : m.copy();
        }

        private void openFile() {
            FileChooser fc = new FileChooser();
            fc.setTitle("Ouvrir un automate");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Automate (*.auto)", "*.auto"));
            File f = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (f == null) return;
            try {
                fileCopy = AutomatonIO.load(f.toPath()).automaton();
                description = "fichier " + f.getName();
            } catch (IOException ex) {
                fileCopy = null;
                status.getStyleClass().setAll("ko");
                status.setText(ex.getMessage());
                return;
            }
            recompute(true);
        }

        /** Parité : 2 états ; la lettre « active » change d'état, l'autre boucle. */
        private Automaton example() {
            char toggle = index == 1 ? 'a' : 'b', stay = index == 1 ? 'b' : 'a';
            Automaton m = new Automaton();
            m.setStart(0);
            m.setFinal(0, true);
            m.addState(1);
            m.addTransition(0, 0, stay);
            m.addTransition(1, 1, stay);
            m.addTransition(0, 1, toggle);
            m.addTransition(1, 0, toggle);
            return m;
        }

        void recompute(boolean notify) {
            Automaton m = null;
            String err = null;
            String desc = "(non défini)";
            switch (mode.getSelectionModel().getSelectedIndex()) {
                case 0 -> {
                    String t = regexField.getText().trim();
                    if (t.isEmpty()) err = "Tapez une expression régulière (ε = mot vide, ∅ = langage vide).";
                    else {
                        err = RegexParser.check(t, syntax.get(), alpha.symbols());
                        if (err == null) { m = RegexParser.toNfa(t, syntax.get(), alpha.symbols()); desc = "regex " + t; }
                    }
                }
                case 1 -> {
                    m = editorCopy;
                    if (m == null) err = "Dessinez un automate dans l’onglet « Éditeur → regex », puis cliquez sur « Prendre l’automate… ».";
                    else desc = "automate de l’éditeur";
                }
                case 2 -> {
                    m = fileCopy;
                    if (m == null) err = "Choisissez un fichier .auto.";
                    else desc = description;
                }
                default -> { m = example(); desc = index == 1 ? "nombre pair de a" : "nombre pair de b"; }
            }
            if (m != null) {
                String v = validate(m);
                if (v != null) { err = v; m = null; }
            }
            automaton = m;
            description = desc;
            preview.getChildren().clear();
            if (m == null) {
                status.getStyleClass().setAll("ko");
                status.setText(err);
            } else {
                status.getStyleClass().setAll("ok");
                status.setText("✔ " + m.kind() + " · " + m.states().size() + " états · " + m.transitions().size() + " transitions");
                AutomatonView v = new AutomatonView("q", false);
                v.show(m);
                ScrollPane sp = new ScrollPane(v);
                sp.getStyleClass().add("canvas-scroll");
                sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                sp.setPrefViewportHeight(Math.min(v.getPrefHeight(), 260) + 2);
                sp.setMinHeight(Math.min(v.getPrefHeight(), 260) + 22);
                preview.getChildren().add(sp);
            }
            if (notify) changed.run();
        }

        private String validate(Automaton m) {
            if (m.start() == null) return "Cet automate n’a pas d’état initial (flèche d’entrée manquante).";
            Set<Character> foreign = new TreeSet<>(m.alphabet());
            foreign.removeAll(alpha.symbols());
            if (!foreign.isEmpty()) return "Symbole(s) hors de l’alphabet " + alpha.display() + " : " + foreign
                    + ". Modifiez Σ en haut ou l’automate.";
            return null;
        }
    }
}
