package automates.ui;

import automates.engine.AutomatonOps;
import automates.engine.RegexParser;
import automates.engine.RegexParser.Syntax;
import automates.model.Automaton;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.function.Consumer;

/** Onglet 1 : expression régulière → AFN → AFD → AFD minimal (étapes), équivalence, tests. */
public class RegexTab extends Tab {

    /** Exemples écrits avec a, b, c : ils sont adaptés aux premiers symboles de Σ. */
    private static final String[] EXAMPLES =
            {"(a+b)*ab", "a*b*", "(ab+c)*", "a(a+b)*b", "(a+b)*aa(a+b)*", "(a+b)*a(a+b)", "ab", "(a+ε)b*", "a*"};

    private final TextField regex = new TextField();
    private final TextField regex2 = new TextField();
    private final AlphabetModel alpha;
    private final Label syntaxState = new Label();
    private final Label syntaxCaret = new Label();
    private final Label error = new Label();
    private final Label equivalence = new Label();
    private final VBox out = new VBox(12);
    private final VBox help = new VBox(6);
    private final FlowPane chips = new FlowPane(6, 6);
    private final RadioButton courseMode = new RadioButton("Notation du cours / JFLAP : union = +");
    private final RadioButton extMode = new RadioButton("Notation étendue : union = |  et  + = « un ou plus »");
    private final ComboBox<String> which = new ComboBox<>();
    private final TesterPanel tester;
    private Automaton nfa, dfa, minFull, minPartial;

    /** Historique des expressions déjà construites (la plus récente en premier). */
    private record HistEntry(String text, Syntax syntax) {}
    private static final int HISTORY_MAX = 30;
    private final LinkedList<HistEntry> history = new LinkedList<>();
    private final FlowPane historyChips = new FlowPane(6, 6);
    private final VBox historyBox = new VBox(4);
    private boolean restoring = false;
    private Consumer<Automaton> onOpenInEditor = m -> {};

    public RegexTab(AlphabetModel alpha) {
        super("Regex → automates");
        this.alpha = alpha;
        setClosable(false);

        ToggleGroup tg = new ToggleGroup();
        courseMode.setToggleGroup(tg);
        extMode.setToggleGroup(tg);
        courseMode.setSelected(true);
        tg.selectedToggleProperty().addListener((o, a, b) -> { refreshChips(); refreshHelp(); validateLive(); build(); });

        regex.getStyleClass().add("mono");
        regex.setPromptText("Ex. : (a+b)*ab");
        regex2.getStyleClass().add("mono");
        regex2.setPromptText("Comparer avec une autre regex (équivalence)…");
        error.getStyleClass().add("ko");
        error.setWrapText(true);
        equivalence.getStyleClass().add("res");
        equivalence.setWrapText(true);
        syntaxState.setWrapText(true);
        syntaxCaret.getStyleClass().addAll("mono", "syntax-ko");

        regex.textProperty().addListener((o, a, b) -> validateLive());

        Button build = new Button("Construire");
        build.getStyleClass().add("primary");
        build.setOnAction(e -> build());
        regex.setOnAction(e -> build());
        HBox row1 = new HBox(8, regex, build);
        HBox.setHgrow(regex, Priority.ALWAYS);

        // boutons d'insertion de symboles spéciaux
        FlowPane keys = new FlowPane(5, 5);
        keys.getChildren().add(UiUtil.muted("Insérer :"));
        for (String k : new String[]{"ε", "∅", "+", "|", "*", "?", "(", ")"}) {
            Button b = new Button(k);
            b.getStyleClass().add("keybtn");
            b.setOnAction(e -> insert(k));
            keys.getChildren().add(b);
        }

        refreshChips();

        Button clearHist = new Button("Effacer l’historique");
        clearHist.setOnAction(e -> { history.clear(); refreshHistory(); });
        HBox histHead = new HBox(10, UiUtil.muted("Historique (cliquez pour revenir à une expression déjà construite) :"), clearHist);
        histHead.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        historyBox.getChildren().addAll(histHead, historyChips);
        refreshHistory();

        Button eqBtn = new Button("Équivalent ?");
        eqBtn.setOnAction(e -> { build(); compare(); });
        regex2.setOnAction(e -> { build(); compare(); });
        HBox row2 = new HBox(8, regex2, eqBtn);
        HBox.setHgrow(regex2, Priority.ALWAYS);

        Label sigmaNote = UiUtil.muted("Les symboles de la regex doivent appartenir à l’alphabet Σ défini en haut. "
                + "Σ sert aussi à compléter l’AFD (état puits) : si Σ = {a, b} et que la regex n’utilise que a, "
                + "l’AFD minimal aura quand même un puits pour b.");

        refreshHelp();
        TitledPane helpPane = new TitledPane("Comment écrire une expression régulière ? (cliquez pour déplier)", help);
        helpPane.setExpanded(false);

        VBox regexCard = UiUtil.card("Expression régulière", new VBox(4, courseMode, extMode), row1, keys,
                syntaxState, syntaxCaret, chips, historyBox, helpPane, sigmaNote, error, row2, equivalence);

        which.getItems().addAll("AFN (Thompson)", "AFD", "AFD minimal complet", "AFD minimal (sans puits)");
        which.getSelectionModel().select(0);
        which.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> refreshTester());
        tester = new TesterPanel(this::testedAutomaton, () -> "q",
                () -> null, alpha);
        tester.addHeaderNode(new Label("sur :"));
        tester.addHeaderNode(which);

        VBox content = new VBox(14, regexCard, tester, out);
        content.setPadding(new Insets(14));
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        setContent(sp);

        onAlphabetChanged();
    }

    /** Appelé quand Σ est (re)défini : exemples, vérification de la regex et reconstruction. */
    public void onAlphabetChanged() {
        refreshChips();
        refreshHelp();
        refreshHistory();
        tester.onAlphabetChanged();
        if (alpha.isDefined() && regex.getText().isBlank()) {
            String ex = alpha.adapt("(a+b)*ab");
            if (ex == null) ex = alpha.adapt("a*");
            if (ex != null) regex.setText(syntax() == Syntax.ETENDU ? ex.replace('+', '|') : ex);
        }
        validateLive();
        build();
    }

    public void setOnOpenInEditor(Consumer<Automaton> c) { onOpenInEditor = c; }

    /** Utilisé par l'éditeur : l'expression produite est en notation du cours. */
    public void setRegexAndBuild(String re) {
        courseMode.setSelected(true);
        regex.setText(re);
        build();
    }

    private void refreshTester() { tester.refresh(); }

    private Syntax syntax() { return courseMode.isSelected() ? Syntax.COURS : Syntax.ETENDU; }

    private Automaton testedAutomaton() {
        return switch (which.getSelectionModel().getSelectedIndex()) {
            case 1 -> dfa;
            case 2 -> minFull;
            case 3 -> minPartial;
            default -> nfa;
        };
    }

    private void insert(String s) {
        int pos = regex.getCaretPosition();
        regex.insertText(pos, s);
        regex.requestFocus();
    }

    // ------------------------------------------------------------ historique
    /** Sans « + », la notation du cours et la notation étendue sont identiques : une seule entrée. */
    private static Syntax normalized(String text, Syntax syn) {
        return text.indexOf('+') < 0 ? Syntax.COURS : syn;
    }

    private void remember(String text, Syntax syn) {
        if (restoring || text.isEmpty()) return;
        HistEntry e = new HistEntry(text, normalized(text, syn));
        history.remove(e);          // pas de doublon : on remonte l'entrée en tête
        history.addFirst(e);
        while (history.size() > HISTORY_MAX) history.removeLast();
        refreshHistory();
    }

    private void refreshHistory() {
        historyChips.getChildren().clear();
        historyBox.setVisible(!history.isEmpty());
        historyBox.setManaged(!history.isEmpty());
        for (HistEntry e : history) {
            boolean ext = e.syntax() == Syntax.ETENDU;
            Button b = new Button(e.text() + (ext ? "   (étendue)" : ""));
            b.getStyleClass().addAll("chip", "mono");
            boolean usable = alpha.isDefined() && RegexParser.check(e.text(), e.syntax(), alpha.symbols()) == null;
            b.setDisable(!usable);
            b.setTooltip(new Tooltip(usable ? (ext ? "Notation étendue" : "Notation du cours")
                    : "Cette expression utilise des symboles hors de l’alphabet Σ actuel"));
            b.setOnAction(x -> restore(e));
            historyChips.getChildren().add(b);
        }
    }

    /** Remet une expression de l'historique (avec sa notation) et reconstruit les automates. */
    private void restore(HistEntry e) {
        restoring = true;
        try {
            (e.syntax() == Syntax.ETENDU ? extMode : courseMode).setSelected(true);
            regex.setText(e.text());
            build();
        } finally {
            restoring = false;
        }
        history.remove(e);
        history.addFirst(e);
        refreshHistory();
    }

    // ------------------------------------------------------------ aide à la saisie
    private void refreshChips() {
        chips.getChildren().clear();
        if (!alpha.isDefined()) return;
        chips.getChildren().add(UiUtil.muted("Exemples sur " + alpha.display() + " :"));
        Set<String> seen = new LinkedHashSet<>();
        for (String ex : EXAMPLES) {
            String adapted = alpha.adapt(ex);
            if (adapted == null || !seen.add(adapted)) continue;
            String shown = syntax() == Syntax.ETENDU ? adapted.replace('+', '|') : adapted;
            Button b = new Button(shown);
            b.getStyleClass().add("chip");
            b.setOnAction(e -> { regex.setText(shown); build(); });
            chips.getChildren().add(b);
        }
    }

    private void refreshHelp() {
        help.getChildren().clear();
        boolean course = syntax() == Syntax.COURS;
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(4);
        String[][] rows = course ? new String[][]{
                {"Symbole", "un symbole de Σ", "a   b   0   1"},
                {"Union", "« + » (ou « | ») : l’un OU l’autre", "a+b   (0+1)"},
                {"Concaténation", "on écrit les symboles à la suite, sans signe", "ab   a(b+c)"},
                {"Étoile", "« * » : répéter 0 fois ou plus (s’applique à ce qui précède)", "a*   (ab)*   (a+b)*"},
                {"Mot vide", "ε  (ou λ, ou !)", "a+ε"},
                {"Langage vide", "∅", "a∅ = ∅"},
                {"Optionnel", "« ? » : 0 ou 1 fois (raccourci, = (ε+x))", "ab?"},
                {"Parenthèses", "pour grouper ; obligatoires pour une union dans une concaténation", "a(b+c)d"}
        } : new String[][]{
                {"Symbole", "une lettre (A–Z, a–z) ou un chiffre (0–9)", "a   B   0   1"},
                {"Union", "« | » uniquement : l’un OU l’autre", "a|b   (0|1)"},
                {"Concaténation", "on écrit les symboles à la suite, sans signe", "ab   a(b|c)"},
                {"Étoile", "« * » : 0 fois ou plus", "a*   (ab)*"},
                {"Un ou plus", "« + » : 1 fois ou plus (ici « + » n’est PAS l’union)", "a+   (ab)+"},
                {"Optionnel", "« ? » : 0 ou 1 fois", "ab?"},
                {"Mot vide / vide", "ε (ou λ, !)  /  ∅", "a|ε"},
                {"Parenthèses", "pour grouper", "a(b|c)d"}
        };
        if (alpha.isDefined()) help.getChildren().add(UiUtil.muted("Symboles autorisés ici : " + alpha.display()
                + " (toute autre lettre ou chiffre est refusé)."));
        g.add(headCell("Élément"), 0, 0);
        g.add(headCell("Écriture"), 1, 0);
        g.add(headCell("Exemples"), 2, 0);
        for (int i = 0; i < rows.length; i++) {
            Label a = new Label(rows[i][0]);
            a.setStyle("-fx-font-weight:bold;");
            Label b = new Label(rows[i][1]);
            b.setWrapText(true);
            b.setMaxWidth(430);
            Label c = new Label(rows[i][2]);
            c.getStyleClass().add("mono");
            g.add(a, 0, i + 1);
            g.add(b, 1, i + 1);
            g.add(c, 2, i + 1);
        }
        help.getChildren().add(g);
        help.getChildren().add(UiUtil.muted("Priorités : « * » est prioritaire sur la concaténation, elle-même prioritaire sur l’union. "
                + "Ainsi ab* = a(b*), et a+bc = a+(bc). Les espaces sont ignorés."));
        help.getChildren().add(UiUtil.muted("Interdits : tout autre caractère (. [ ] { } ^ $ \\ …), les opérateurs sans opérande (« *a », « a+ », « () »), "
                + "les parenthèses non appariées. Le programme indique la position exacte de l’erreur."));
        if (course) {
            help.getChildren().add(UiUtil.muted("Pour « un ou plus de a » : écrivez aa*. L’expression « a+ » est refusée car « + » est l’union."));
        }
    }

    private static Label headCell(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("help-table-head");
        return l;
    }

    /** Vérification de la syntaxe pendant la frappe. */
    private void validateLive() {
        syntaxState.getStyleClass().removeAll("syntax-ok", "syntax-ko");
        String text = regex.getText();
        syntaxCaret.setText("");
        syntaxCaret.setVisible(false);
        syntaxCaret.setManaged(false);
        if (!alpha.isDefined()) {
            syntaxState.setText("Définissez d’abord l’alphabet Σ.");
            return;
        }
        if (text.isBlank()) {
            syntaxState.setText("Saisissez une expression sur " + alpha.display() + ".");
            return;
        }
        try {
            RegexParser.toNfa(text, syntax(), alpha.symbols());
            syntaxState.getStyleClass().add("syntax-ok");
            syntaxState.setText("✔ Syntaxe correcte");
        } catch (RegexParser.ParseException e) {
            syntaxState.getStyleClass().add("syntax-ko");
            syntaxState.setText("✘ Position " + (e.position() + 1) + " : " + e.getMessage());
            syntaxCaret.setText(text + "\n" + " ".repeat(Math.max(0, e.position())) + "^");
            syntaxCaret.setVisible(true);
            syntaxCaret.setManaged(true);
        } catch (RuntimeException e) {
            syntaxState.getStyleClass().add("syntax-ko");
            syntaxState.setText("✘ " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ construction
    private void build() {
        error.setText("");
        equivalence.setText("");
        if (!alpha.isDefined() || regex.getText().isBlank()) { clearResults(); tester.refresh(); return; }
        try {
            Set<Character> sigma = alpha.symbols();
            Automaton n = RegexParser.toNfa(regex.getText(), syntax(), sigma);
            List<String> log = new ArrayList<>();
            Automaton d = AutomatonOps.determinize(n, "q", log);
            automates.engine.Minimizer.Result mr = automates.engine.Minimizer.run(d, sigma, "q");
            nfa = n;
            dfa = d;
            minFull = mr.minimalComplete();
            minPartial = mr.minimalPartial();
            remember(regex.getText().trim(), syntax());

            VBox detail = UiUtil.stepBox("Déterminisation (ε-fermetures)",
                    "On part de ε-fer(état initial) = A. Pour chaque état de l’AFD et chaque lettre, on calcule "
                            + "ε-fer(trans(état, lettre)) : si l’ensemble obtenu est nouveau, on lui donne la lettre suivante (B, C, …).",
                    UiUtil.monoLines(log));

            out.getChildren().setAll(
                    UiUtil.automatonBlock("AFN-ε (construction de Thompson)", n, "q", () -> onOpenInEditor.accept(n.copy())),
                    detail,
                    UiUtil.automatonBlock("AFD", d, "q", () -> onOpenInEditor.accept(d.copy())),
                    MinimizationPanel.build(d, sigma, onOpenInEditor));
            if (!regex2.getText().isBlank()) compare();
        } catch (RegexParser.ParseException ex) {
            error.setText("Erreur de syntaxe, position " + (ex.position() + 1) + " : " + ex.getMessage());
            clearResults();
        } catch (RuntimeException ex) {
            error.setText("Erreur : " + ex.getMessage());
            clearResults();
        }
        tester.refresh();
    }

    private void clearResults() {
        nfa = dfa = minFull = minPartial = null;
        out.getChildren().clear();
    }

    private void compare() {
        equivalence.getStyleClass().removeAll("ok", "ko");
        try {
            String w = AutomatonOps.distinguishingWord(
                    RegexParser.toNfa(regex.getText(), syntax(), alpha.symbols()), RegexParser.toNfa(regex2.getText(), syntax(), alpha.symbols()));
            if (w == null) {
                equivalence.getStyleClass().add("ok");
                equivalence.setText("✔ Équivalentes : les deux expressions reconnaissent le même langage.");
            } else {
                equivalence.getStyleClass().add("ko");
                equivalence.setText("✘ Différentes — mot qui les distingue : " + (w.isEmpty() ? "λ" : w));
            }
        } catch (RegexParser.ParseException ex) {
            equivalence.getStyleClass().add("ko");
            equivalence.setText("Erreur dans la 2ᵉ expression, position " + (ex.position() + 1) + " : " + ex.getMessage());
        } catch (RuntimeException ex) {
            equivalence.getStyleClass().add("ko");
            equivalence.setText("Erreur : " + ex.getMessage());
        }
    }
}
