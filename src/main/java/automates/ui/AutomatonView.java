package automates.ui;

import automates.model.Automaton;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Dessin d'un automate (états, transitions, état initial/final).
 * En mode éditable, la souris permet de créer / déplacer / supprimer des états
 * et de relier les états par des transitions (outil sélectionné via {@link #setTool}).
 */
public class AutomatonView extends Pane {

    public enum Tool { STATE, TRANSITION, MOVE, INITIAL, FINAL, DELETE }

    /** Rayon des états : 20 par défaut, agrandi si un nom est long (ex. « (q0,q0') » dans un produit). */
    private double R = 20;
    private static final Color GREY = Color.web("#888888");
    private static final Color AC = Color.web("#2e6fb7");
    private static final Color KO = Color.web("#c0392b");
    private static final Color FIN = Color.web("#d5f0dd");
    private static final Color ST = Color.web("#eaf1fa");
    private static final Color ORANGE = Color.web("#e67e22");
    private static final Color ACTIVE_FILL = Color.web("#ffd9a8");
    private static final Color OK_FILL = Color.web("#8fdc9f");
    private static final Color KO_FILL = Color.web("#f5b7b1");

    /** Verdict affiché sur les états courants lors de la lecture d'un mot. */
    public enum Verdict { NONE, ACCEPT, REJECT }

    private record LabelHit(int from, int to, double x, double y) {}

    private static final class Edge {
        final int from, to;
        final Set<String> labels = new LinkedHashSet<>();
        final List<Automaton.Transition> ts = new ArrayList<>();
        Edge(int from, int to) { this.from = from; this.to = to; }
    }

    private final String prefix;
    private final boolean editable;
    private Automaton model = new Automaton();
    private Map<Integer, double[]> pos = new HashMap<>();
    private Tool tool = Tool.STATE;
    private Integer pending, dragging;
    private int nextId = 0;
    private final List<LabelHit> labelHits = new ArrayList<>();
    private BiConsumer<Integer, Integer> onTransitionRequest = (a, b) -> {};
    private Runnable onChange = () -> {};
    private Set<Integer> hiStates = Set.of();
    private Set<Automaton.Transition> hiActive = Set.of(), hiTrail = Set.of();
    private Verdict verdict = Verdict.NONE;

    public AutomatonView(String prefix, boolean editable) {
        this.prefix = prefix;
        this.editable = editable;
        getStyleClass().add("canvas");
        if (editable) installMouseHandlers();
        redraw();
    }

    // ------------------------------------------------------------ API
    public Automaton model() { return model; }
    public Map<Integer, double[]> positions() { return pos; }

    public void show(Automaton m) { show(m, null); }

    public void show(Automaton m, Map<Integer, double[]> positions) {
        model = m;
        R = radiusFor(m, prefix);
        Map<Integer, double[]> auto = autoLayout(m);
        pos = new HashMap<>();
        for (int s : m.states()) {
            double[] p = positions != null ? positions.get(s) : null;
            pos.put(s, p != null ? p : auto.get(s));
        }
        nextId = 0;
        for (int s : m.states()) nextId = Math.max(nextId, s + 1);
        pending = null;
        redraw();
    }

    /**
     * Mise en évidence d'une lecture de mot : états courants (orange), transitions de l'étape
     * (orange épais), transitions déjà parcourues (bleu) ; verdict : états courants verts/rouges.
     */
    public void setHighlight(Set<Integer> states, Set<Automaton.Transition> active,
                             Set<Automaton.Transition> trail, Verdict v) {
        hiStates = states == null ? Set.of() : states;
        hiActive = active == null ? Set.of() : active;
        hiTrail = trail == null ? Set.of() : trail;
        verdict = v == null ? Verdict.NONE : v;
        redraw();
    }

    public void clearHighlight() { setHighlight(null, null, null, Verdict.NONE); }

    public void setTool(Tool t) { tool = t; pending = null; redraw(); }
    public void setOnTransitionRequest(BiConsumer<Integer, Integer> c) { onTransitionRequest = c; }
    public void setOnChange(Runnable r) { onChange = r; }

    public void addTransitions(int from, int to, List<Character> symbols) {
        for (char c : symbols) model.addTransition(from, to, c);
        redraw();
        onChange.run();
    }

    public void clear() {
        show(new Automaton());
        onChange.run();
    }

    /** Placement automatique : une colonne par distance (BFS) depuis l'état initial. */
    static double radiusFor(Automaton m, String prefix) {
        double w = 0;
        for (int s : m.states()) {
            Text t = new Text(m.name(s, prefix));
            t.setFont(Font.font("System", 13));
            w = Math.max(w, t.getLayoutBounds().getWidth());
        }
        return Math.max(20, w / 2 + 8);
    }

    public static Map<Integer, double[]> autoLayout(Automaton m) {
        double r = radiusFor(m, "q");
        double dx = Math.max(115, 2 * r + 70), dy = Math.max(88, 2 * r + 30);
        Map<Integer, Integer> layer = new HashMap<>();
        Deque<Integer> queue = new ArrayDeque<>();
        if (m.start() != null) { layer.put(m.start(), 0); queue.add(m.start()); }
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (Automaton.Transition t : m.transitions()) {
                if (t.from() == u && !layer.containsKey(t.to())) {
                    layer.put(t.to(), layer.get(u) + 1);
                    queue.add(t.to());
                }
            }
        }
        Map<Integer, Integer> rows = new HashMap<>();
        Map<Integer, double[]> p = new HashMap<>();
        for (int s : m.states()) {
            int l = layer.getOrDefault(s, 0);
            int row = rows.merge(l, 1, Integer::sum) - 1;
            p.put(s, new double[]{Math.max(60, r + 25) + l * dx, Math.max(70, r + 30) + row * dy});
        }
        return p;
    }

    // ------------------------------------------------------------ souris
    private void installMouseHandlers() {
        setOnMousePressed(e -> {
            double x = Math.max(40, e.getX()), y = Math.max(65, e.getY());
            Integer hit = hitState(e.getX(), e.getY());
            boolean changed = false;
            switch (tool) {
                case STATE -> {
                    if (hit == null) {
                        int id = nextId++;
                        model.addState(id);
                        pos.put(id, new double[]{x, y});
                        if (model.start() == null) model.setStart(id);
                        changed = true;
                    }
                }
                case INITIAL -> { if (hit != null) { model.setStart(hit); changed = true; } }
                case FINAL -> { if (hit != null) { model.toggleFinal(hit); changed = true; } }
                case MOVE -> dragging = hit;
                case DELETE -> {
                    if (hit != null) {
                        model.removeState(hit);
                        pos.remove(hit);
                        changed = true;
                    } else {
                        LabelHit lh = hitLabel(e.getX(), e.getY());
                        if (lh != null) { model.removeTransitions(lh.from(), lh.to()); changed = true; }
                    }
                }
                case TRANSITION -> {
                    if (hit != null) {
                        if (pending == null) {
                            pending = hit;
                        } else {
                            int from = pending, to = hit;
                            pending = null;
                            Platform.runLater(() -> onTransitionRequest.accept(from, to));
                        }
                    }
                }
            }
            redraw();
            if (changed) onChange.run();
        });
        setOnMouseDragged(e -> {
            if (tool == Tool.MOVE && dragging != null) {
                pos.put(dragging, new double[]{Math.max(25, e.getX()), Math.max(30, e.getY())});
                redraw();
            }
        });
        setOnMouseReleased(e -> dragging = null);
    }

    private Integer hitState(double x, double y) {
        for (Map.Entry<Integer, double[]> e : pos.entrySet()) {
            if (model.hasState(e.getKey()) && Math.hypot(x - e.getValue()[0], y - e.getValue()[1]) <= R + 3) {
                return e.getKey();
            }
        }
        return null;
    }

    private LabelHit hitLabel(double x, double y) {
        for (LabelHit h : labelHits) if (Math.hypot(x - h.x(), y - h.y()) <= 18) return h;
        return null;
    }

    // ------------------------------------------------------------ dessin
    private void redraw() {
        getChildren().clear();
        labelHits.clear();

        double maxX = 0, maxY = 0;
        for (double[] p : pos.values()) { maxX = Math.max(maxX, p[0]); maxY = Math.max(maxY, p[1]); }
        double w = Math.max(editable ? 900 : 200, maxX + 70);
        double h = Math.max(editable ? 380 : 120, maxY + 90);
        setPrefSize(w, h);
        setMinSize(w, h);

        // regroupe les transitions de même (from, to) : « a, b »
        Map<String, Edge> edges = new LinkedHashMap<>();
        for (Automaton.Transition t : model.transitions()) {
            Edge ed = edges.computeIfAbsent(t.from() + ">" + t.to(), k -> new Edge(t.from(), t.to()));
            ed.labels.add(t.label());
            ed.ts.add(t);
        }
        for (Edge e : edges.values()) drawEdge(e, edges.containsKey(e.to + ">" + e.from));
        for (int s : model.states()) drawState(s);
        if (model.start() != null && pos.containsKey(model.start())) {
            double[] p = pos.get(model.start());
            Line l = new Line(p[0] - 48, p[1], p[0] - 22, p[1]);
            l.setStroke(GREY);
            getChildren().addAll(l, arrow(p[0] - R, p[1], 1, 0));
        }
        for (Node n : getChildren()) n.setMouseTransparent(true);
    }

    private void drawState(int s) {
        double[] p = pos.get(s);
        boolean fin = model.isFinal(s), sel = Integer.valueOf(s).equals(pending);
        boolean hi = hiStates.contains(s);
        Color fill = fin ? FIN : ST;
        if (hi) fill = verdict == Verdict.ACCEPT && fin ? OK_FILL : verdict == Verdict.REJECT ? KO_FILL : ACTIVE_FILL;
        Circle c = new Circle(p[0], p[1], R, fill);
        c.setStroke(sel ? KO : hi ? ORANGE : AC);
        c.setStrokeWidth(sel || hi ? 3 : 1.6);
        getChildren().add(c);
        if (fin) {
            Circle in = new Circle(p[0], p[1], R - 5, Color.TRANSPARENT);
            in.setStroke(AC);
            getChildren().add(in);
        }
        Text t = new Text(model.name(s, prefix));
        t.setFont(Font.font("System", 13));
        t.setX(p[0] - t.getLayoutBounds().getWidth() / 2);
        t.setY(p[1] + 4);
        getChildren().add(t);
    }

    private void drawEdge(Edge e, boolean reversePresent) {
        String text = String.join(", ", e.labels);
        boolean act = e.ts.stream().anyMatch(hiActive::contains);
        boolean trail = !act && e.ts.stream().anyMatch(hiTrail::contains);
        Color col = act ? ORANGE : trail ? AC : GREY;
        double wd = act ? 3.2 : trail ? 2.4 : 1;
        double[] a = pos.get(e.from), b = pos.get(e.to);
        if (e.from == e.to) {                                   // boucle
            double x = a[0], y = a[1];
            // la boucle est dessinée du côté opposé aux autres transitions de l'état
            double sumDy = 0;
            for (Automaton.Transition t : model.transitions()) {
                if (t.from() == t.to()) continue;
                int other = t.from() == e.from ? t.to() : t.to() == e.from ? t.from() : -1;
                if (other >= 0 && pos.containsKey(other)) sumDy += pos.get(other)[1] - y;
            }
            int sg = sumDy < 0 ? 1 : -1;                         // -1 : au-dessus, +1 : en dessous
            CubicCurve c = new CubicCurve(x - 10, y + sg * 18, x - 32, y + sg * 62, x + 32, y + sg * 62, x + 10, y + sg * 18);
            c.setFill(Color.TRANSPARENT);
            c.setStroke(col);
            c.setStrokeWidth(wd);
            getChildren().addAll(c, arrow(x + 10, y + sg * 18, -22, -sg * 44, col));
            addLabel(e, text, x, sg < 0 ? y - 56 : y + 74, col);
            return;
        }
        double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
        if (len == 0) len = 1;
        double off = reversePresent ? 34 : (b[0] < a[0] ? 48 : 0); // arcs retour courbés
        double cx = (a[0] + b[0]) / 2 - dy / len * off, cy = (a[1] + b[1]) / 2 + dx / len * off;
        double[] s = towards(a, cx, cy), t = towards(b, cx, cy);
        QuadCurve q = new QuadCurve(s[0], s[1], cx, cy, t[0], t[1]);
        q.setFill(Color.TRANSPARENT);
        q.setStroke(col);
        q.setStrokeWidth(wd);
        getChildren().addAll(q, arrow(t[0], t[1], t[0] - cx, t[1] - cy, col));
        addLabel(e, text, (s[0] + 2 * cx + t[0]) / 4, (s[1] + 2 * cy + t[1]) / 4 - 9, col);
    }

    /** Point du bord du cercle de centre p, dans la direction (cx, cy). */
    private double[] towards(double[] p, double cx, double cy) {
        double k = Math.hypot(cx - p[0], cy - p[1]);
        if (k == 0) k = 1;
        return new double[]{p[0] + (cx - p[0]) / k * R, p[1] + (cy - p[1]) / k * R};
    }

    private void addLabel(Edge e, String text, double cx, double baseY, Color col) {
        Text t = new Text(text);
        t.setFont(Font.font("System", FontWeight.BOLD, 13));
        t.setFill(col == GREY ? KO : col);
        t.setX(cx - t.getLayoutBounds().getWidth() / 2);
        t.setY(baseY);
        getChildren().add(t);
        labelHits.add(new LabelHit(e.from, e.to, cx, baseY - 4));
    }

    /** Pointe de flèche dont la pointe est en (tx, ty), orientée selon (dx, dy). */
    private static Polygon arrow(double tx, double ty, double dx, double dy) { return arrow(tx, ty, dx, dy, GREY); }

    private static Polygon arrow(double tx, double ty, double dx, double dy, Color color) {
        double k = Math.hypot(dx, dy);
        if (k == 0) k = 1;
        double ux = dx / k, uy = dy / k, px = -uy, py = ux;
        double bx = tx - ux * 10, by = ty - uy * 10;
        Polygon p = new Polygon(tx, ty, bx + px * 4.5, by + py * 4.5, bx - px * 4.5, by - py * 4.5);
        p.setFill(color);
        return p;
    }
}
