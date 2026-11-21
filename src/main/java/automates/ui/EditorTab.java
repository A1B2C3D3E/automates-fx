package automates.ui;

import automates.engine.AutomatonOps;
import automates.engine.RegexBuilder;
import automates.engine.RegexParser;
import automates.io.AutomatonIO;
import automates.model.Automaton;
import automates.ui.AutomatonView.Tool;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Onglet 2 : éditeur d'automates → regex, AFD, AFD minimal, tests, sauvegarde. */
public class EditorTab extends Tab {

    private static final Map<Tool, String> NAMES = new EnumMap<>(Tool.class);
    private static final Map<Tool, String> HINTS = new EnumMap<>(Tool.class);
    static {
        NAMES.put(Tool.STATE, "État");
        NAMES.put(Tool.TRANSITION, "Transition");
        NAMES.put(Tool.MOVE, "Déplacer");
        NAMES.put(Tool.INITIAL, "Initial");
        NAMES.put(Tool.FINAL, "Final");
        NAMES.put(Tool.DELETE, "Supprimer");
        HINTS.put(Tool.STATE, "Cliquez dans la zone blanche pour créer un état.");
        HINTS.put(Tool.TRANSITION, "Cliquez sur l’état de départ, puis sur l’état d’arrivée (même état = boucle).");
        HINTS.put(Tool.MOVE, "Glissez un état pour le déplacer.");
        HINTS.put(Tool.INITIAL, "Cliquez sur un état pour en faire l’état initial.");
        HINTS.put(Tool.FINAL, "Cliquez sur un état pour le rendre final (ou non).");
        HINTS.put(Tool.DELETE, "Cliquez sur un état ou sur l’étiquette d’une transition pour la supprimer.");
    }

    private final AutomatonView view = new AutomatonView("q", true);
    private final Label hint = UiUtil.muted("");
    private final Label status = UiUtil.muted("");
    private final VBox out = new VBox(12);
    private final AlphabetModel alpha;
    private final TesterPanel tester;
    private Consumer<String> onVerifyRegex = s -> {};

    // ---- historique : automates déjà traités (le plus récent en premier)
    private enum Action {
        NONE(""), REGEX("→ Regex"), DFA("→ AFD"), MIN("→ AFD minimal");
        final String text;
        Action(String t) { text = t; }
    }
    private record Hist(String sig, Automaton m, Map<Integer, double[]> pos, Action action, String label, String tip) {}
    private static final int HISTORY_MAX = 30;
    private final LinkedList<Hist> history = new LinkedList<>();
    private final FlowPane historyChips = new FlowPane(6, 6);
    private final VBox historyBox = new VBox(4);
    private boolean restoring = false;

    public EditorTab(AlphabetModel alpha) {
        super("Éditeur → regex");
        this.alpha = alpha;
        this.tester = new TesterPanel(view::model, () -> "q", view::positions, alpha);
        setClosable(false);

        // --- barre d'outils
        ToggleGroup group = new ToggleGroup();
        HBox toolbar = new HBox(6);
        for (Tool t : Tool.values()) {
            ToggleButton b = new ToggleButton(NAMES.get(t));
            b.setToggleGroup(group);
            b.setUserData(t);
            toolbar.getChildren().add(b);
        }
        group.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null) { old.setSelected(true); return; }   // toujours un outil actif
            Tool t = (Tool) now.getUserData();
            view.setTool(t);
            hint.setText(HINTS.get(t));
        });
        group.selectToggle(group.getToggles().get(0));

        view.setOnChange(this::changed);
        view.setOnTransitionRequest(this::askSymbols);

        ScrollPane canvas = new ScrollPane(view);
        canvas.getStyleClass().add("canvas-scroll");
        canvas.setPrefViewportHeight(400);
        canvas.setMinHeight(420);

        // --- actions
        Button toRegex = new Button("→ Regex (lemme d’Arden)");
        toRegex.getStyleClass().add("primary");
        toRegex.setOnAction(e -> doToRegex());
        Button toDfa = new Button("→ AFD");
        toDfa.setOnAction(e -> doToDfa());
        Button toMin = new Button("→ AFD minimal (étapes)");
        toMin.setOnAction(e -> doToMin());
        Button example = new Button("Exemple");
        example.setOnAction(e -> {
            String ex = alpha.adapt("(a+b)*ab");
            if (ex == null) ex = alpha.adapt("a*");
            if (ex != null) load(RegexParser.toNfa(ex, RegexParser.Syntax.COURS, alpha.symbols()), null);
        });
        Button clear = new Button("Vider");
        clear.setOnAction(e -> { view.clear(); out.getChildren().clear(); });
        Button save = new Button("Enregistrer…");
        save.setOnAction(e -> save());
        Button open = new Button("Ouvrir…");
        open.setOnAction(e -> open());

        FlowPane actions = new FlowPane(8, 8, toRegex, toDfa, toMin, example, clear, save, open);

        Button clearHist = new Button("Effacer l’historique");
        clearHist.setOnAction(e -> { history.clear(); refreshHistory(); });
        HBox histHead = new HBox(10, UiUtil.muted("Historique (cliquez pour rouvrir un automate et son résultat) :"), clearHist);
        histHead.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        historyBox.getChildren().addAll(histHead, historyChips);
        refreshHistory();

        VBox editorCard = UiUtil.card("Éditeur d’automate", toolbar, canvas, hint, status, actions, historyBox);

        VBox content = new VBox(14, editorCard, tester, out);
        content.setPadding(new Insets(14));
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        setContent(sp);

        view.show(new Automaton());
        changed();
    }

    /** Σ a changé : on efface les résultats périmés et on revérifie l'automate. */
    public void onAlphabetChanged() {
        out.getChildren().clear();
        changed();
        tester.onAlphabetChanged();
    }

    /** Symboles de l'automate dessiné qui n'appartiennent pas à Σ. */
    private Set<Character> foreignSymbols() {
        Set<Character> f = new TreeSet<>(view.model().alphabet());
        f.removeAll(alpha.symbols());
        return f;
    }

    public void setOnVerifyRegex(Consumer<String> c) { onVerifyRegex = c; }

    /** Copie de l'automate actuellement dessiné (utilisée par l'onglet « Intersection / union »). */
    public Automaton currentAutomaton() { return view.model().copy(); }

    /** Charge un automate dans l'éditeur (une copie est utilisée). */
    public void load(Automaton m, java.util.Map<Integer, double[]> positions) {
        view.show(m.copy(), positions);
        out.getChildren().clear();
        changed();
        remember(Action.NONE, null);
    }

    // ------------------------------------------------------------ historique
    /** Empreinte de l'automate (états, initial, finals, transitions, noms) : sert à éviter les doublons. */
    private static String signature(Automaton m) {
        StringBuilder sb = new StringBuilder();
        sb.append("S").append(new TreeSet<>(m.states())).append("I").append(m.start())
          .append("F").append(new TreeSet<>(m.finals())).append("T");
        TreeSet<String> ts = new TreeSet<>();
        for (Automaton.Transition t : m.transitions()) ts.add(t.from() + ">" + t.to() + ":" + (int) t.symbol());
        sb.append(ts).append("N");
        for (int s : new TreeSet<>(m.states())) sb.append(m.name(s, "q")).append(',');
        return sb.toString();
    }

    private static Map<Integer, double[]> copyPositions(Map<Integer, double[]> p) {
        if (p == null) return null;
        Map<Integer, double[]> c = new HashMap<>();
        p.forEach((k, v) -> c.put(k, v.clone()));
        return c;
    }

    /** Ajoute l'automate affiché à l'historique (sans doublon : même automate + même action = une seule entrée). */
    private void remember(Action action, String resultText) {
        if (restoring) return;
        Automaton m = view.model();
        if (m.isEmpty()) return;
        String sig = signature(m);
        if (action == Action.NONE) {
            // déjà présent avec une action ? on ne crée pas une entrée « nue » en plus.
            for (Hist h : history) if (h.sig().equals(sig)) return;
        } else {
            history.removeIf(h -> h.sig().equals(sig) && h.action() == Action.NONE);
        }
        history.removeIf(h -> h.sig().equals(sig) && h.action() == action);

        String label = m.kind() + " · " + m.states().size() + " états · " + m.transitions().size() + " trans."
                + (action == Action.NONE ? "" : "  " + action.text);
        if (action == Action.REGEX && resultText != null)
            label = m.kind() + " · " + m.states().size() + " états  →  " + (resultText.length() > 30 ? resultText.substring(0, 30) + "…" : resultText);
        String tip = m.kind() + ", " + m.states().size() + " états, " + m.transitions().size() + " transitions"
                + (action == Action.NONE ? "" : " — " + action.text) + (resultText != null ? " : " + resultText : "");
        history.addFirst(new Hist(sig, m.copy(), copyPositions(view.positions()), action, label, tip));
        while (history.size() > HISTORY_MAX) history.removeLast();
        refreshHistory();
    }

    private void refreshHistory() {
        historyChips.getChildren().clear();
        historyBox.setVisible(!history.isEmpty());
        historyBox.setManaged(!history.isEmpty());
        for (Hist h : history) {
            Button b = new Button(h.label());
            b.getStyleClass().add("chip");
            b.setTooltip(new Tooltip(h.tip()));
            b.setOnAction(x -> restore(h));
            historyChips.getChildren().add(b);
        }
    }

    /** Rouvre l'automate dans l'éditeur et réaffiche directement son résultat (sans nouvelle entrée). */
    private void restore(Hist h) {
        restoring = true;
        try {
            view.show(h.m().copy(), copyPositions(h.pos()));
            out.getChildren().clear();
            changed();
            switch (h.action()) {
                case REGEX -> doToRegex();
                case DFA -> doToDfa();
                case MIN -> doToMin();
                default -> { }
            }
        } finally {
            restoring = false;
        }
        history.remove(h);
        history.addFirst(h);
        refreshHistory();
    }

    // ------------------------------------------------------------ actions
    private void changed() {
        Automaton m = view.model();
        String s = m.isEmpty() ? "Automate vide."
                : m.kind() + " · " + m.states().size() + " états · " + m.transitions().size() + " transitions"
                + (m.start() == null ? " · pas d’état initial" : "");
        if (alpha.isDefined() && !foreignSymbols().isEmpty())
            s += "  ⚠ symboles hors " + alpha.display() + " : " + foreignSymbols();
        status.setText(s);
        tester.refresh();
    }

    private boolean need() {
        if (view.model().start() == null || view.model().isEmpty()) {
            Label l = new Label("Créez au moins un état et définissez l’état initial (outil Initial).");
            l.getStyleClass().add("ko");
            out.getChildren().setAll(l);
            return false;
        }
        if (!foreignSymbols().isEmpty()) {
            Label l = new Label("L’automate utilise des symboles qui n’appartiennent pas à " + alpha.display() + " : " + foreignSymbols()
                    + ". Modifiez Σ en haut de la fenêtre, ou supprimez ces transitions.");
            l.getStyleClass().add("ko");
            l.setWrapText(true);
            out.getChildren().setAll(l);
            return false;
        }
        return true;
    }

    private void showError(String msg) {
        Label l = new Label(msg);
        l.getStyleClass().add("ko");
        l.setWrapText(true);
        out.getChildren().setAll(l);
    }

    private void doToRegex() {
        if (!need()) return;
        out.getChildren().setAll(ArdenPanel.build(view.model(), "q", onVerifyRegex));
        String txt = null;
        try { txt = automates.engine.ArdenSolver.solve(view.model(), "q").text(); } catch (RuntimeException ignored) { }
        remember(Action.REGEX, txt);
    }

    private void doToDfa() {
        if (!need()) return;
        List<String> log = new ArrayList<>();
        Automaton m = view.model();
        Automaton d = AutomatonOps.determinize(m, "q", log);
        VBox detail = UiUtil.stepBox("Déterminisation (ε-fermetures)",
                "On part de ε-fer(état initial) = A. Pour chaque état de l’AFD et chaque lettre, on calcule ε-fer(trans(état, lettre)).",
                UiUtil.monoLines(log));
        out.getChildren().setAll(detail, UiUtil.automatonBlock("AFD", d, "q", () -> load(d, null)));
        remember(Action.DFA, null);
    }

    private void doToMin() {
        if (!need()) return;
        try {
            Set<Character> sigma = alpha.symbols();
            Automaton m = view.model();
            VBox box = new VBox(12);
            Automaton dfa = m;
            if (!m.isDeterministic()) {
                List<String> log = new ArrayList<>();
                dfa = AutomatonOps.determinize(m, "q", log);
                Automaton shown = dfa;
                box.getChildren().addAll(UiUtil.muted("L’automate n’est pas déterministe : on le déterminise d’abord."),
                        UiUtil.stepBox("Déterminisation (ε-fermetures)", null, UiUtil.monoLines(log)),
                        UiUtil.automatonBlock("AFD", shown, "q", null));
            }
            box.getChildren().add(MinimizationPanel.build(dfa, sigma, a -> load(a, null)));
            out.getChildren().setAll(box);
            remember(Action.MIN, null);
        } catch (RuntimeException ex) {
            showError("Erreur : " + ex.getMessage());
        }
    }

    private void askSymbols(int from, int to) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Transition");
        d.setHeaderText("q" + from + " → q" + to);
        d.setContentText("Symboles de " + alpha.display() + " (a  ou  a,b ; vide = ε) :");
        d.showAndWait().ifPresent(v -> {
            try {
                view.addTransitions(from, to, parseSymbols(v, alpha.symbols()));
            } catch (IllegalArgumentException ex) {
                new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
            }
        });
    }

    /** « a », « a,b », vide / ε / λ / ! / eps = transition spontanée. Refuse tout symbole mal formé. */
    static List<Character> parseSymbols(String v, Set<Character> sigma) {
        List<Character> res = new ArrayList<>();
        if (v.isBlank()) { res.add(Automaton.EPS); return res; }
        for (String tok : v.split(",")) {
            String t = tok.trim();
            if (t.isEmpty()) continue;
            boolean eps = t.equals("ε") || t.equals("λ") || t.equals("!") || t.equalsIgnoreCase("eps");
            if (eps) { res.add(Automaton.EPS); continue; }
            if (t.length() != 1 || !AlphabetModel.isAllowed(t.charAt(0)))
                throw new IllegalArgumentException("« " + t + " » n’est pas un symbole valide : une seule lettre (A–Z, a–z) ou un seul chiffre (0–9) par transition (séparez par des virgules : a,b)");
            if (!sigma.contains(t.charAt(0)))
                throw new IllegalArgumentException("« " + t + " » n’appartient pas à l’alphabet Σ = " + new TreeSet<>(sigma)
                        + ". Ajoutez-le à Σ (en haut de la fenêtre) ou choisissez un autre symbole.");
            res.add(t.charAt(0));
        }
        return res;
    }

    // ------------------------------------------------------------ fichiers
    private FileChooser chooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Automate (*.auto)", "*.auto"));
        return fc;
    }

    private void save() {
        File f = chooser("Enregistrer l’automate").showSaveDialog(getContent().getScene().getWindow());
        if (f == null) return;
        if (!f.getName().contains(".")) f = new File(f.getPath() + ".auto");
        try {
            AutomatonIO.save(f.toPath(), view.model(), view.positions());
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
        }
    }

    private void open() {
        File f = chooser("Ouvrir un automate").showOpenDialog(getContent().getScene().getWindow());
        if (f == null) return;
        try {
            AutomatonIO.Loaded l = AutomatonIO.load(f.toPath());
            load(l.automaton(), l.positions());
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
        }
    }
}
