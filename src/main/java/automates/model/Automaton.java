package automates.model;

import java.util.*;

/**
 * Automate fini (AFD, AFN ou AFN-ε). Les états sont des entiers.
 * Le symbole {@link #EPS} représente la transition spontanée ε.
 */
public final class Automaton {

    /** Symbole interne de la transition ε. */
    public static final char EPS = '\0';

    /** Transition from --symbol--> to. */
    public record Transition(int from, int to, char symbol) {
        public boolean isEpsilon() { return symbol == EPS; }
        public String label() { return isEpsilon() ? "ε" : String.valueOf(symbol); }
    }

    private final Set<Integer> states = new LinkedHashSet<>();
    private final List<Transition> transitions = new ArrayList<>();
    private final Set<Integer> finals = new LinkedHashSet<>();
    /** Descriptions facultatives des états (ex. sous-ensemble d'états de l'AFN). */
    private final Map<Integer, String> labels = new HashMap<>();
    /** Noms d'affichage facultatifs (ex. « p » pour l'état puits). */
    private final Map<Integer, String> names = new HashMap<>();
    private String labelHeader = "Sous-ensemble d’états";
    private Integer start;

    // ------------------------------------------------------------ lecture
    public Set<Integer> states() { return Collections.unmodifiableSet(states); }
    public List<Transition> transitions() { return Collections.unmodifiableList(transitions); }
    public Set<Integer> finals() { return Collections.unmodifiableSet(finals); }
    public Integer start() { return start; }
    public boolean hasState(int s) { return states.contains(s); }
    public boolean isFinal(int s) { return finals.contains(s); }
    public boolean isEmpty() { return states.isEmpty(); }

    public String label(int s) { return labels.get(s); }
    public boolean hasLabels() { return !labels.isEmpty(); }
    public String labelHeader() { return labelHeader; }
    public void setLabelHeader(String h) { labelHeader = h; }

    /** Nom affiché d'un état : nom personnalisé, sinon préfixe + numéro. */
    public String name(int s, String prefix) {
        String n = names.get(s);
        return n != null ? n : prefix + s;
    }
    public void setName(int s, String name) { names.put(s, name); }

    /** États atteints depuis s en lisant a. */
    public List<Integer> targets(int s, char a) {
        List<Integer> r = new ArrayList<>();
        for (Transition t : transitions) {
            if (t.from() == s && t.symbol() == a) r.add(t.to());
        }
        return r;
    }

    /** Alphabet (sans ε), trié. */
    public SortedSet<Character> alphabet() {
        SortedSet<Character> r = new TreeSet<>();
        for (Transition t : transitions) if (!t.isEpsilon()) r.add(t.symbol());
        return r;
    }

    public boolean hasEpsilon() {
        for (Transition t : transitions) if (t.isEpsilon()) return true;
        return false;
    }

    public boolean isDeterministic() {
        if (hasEpsilon()) return false;
        Set<String> seen = new HashSet<>();
        for (Transition t : transitions) {
            if (!seen.add(t.from() + ":" + t.symbol())) return false;
        }
        return true;
    }

    /** "AFD", "AFN" ou "AFN-ε". */
    public String kind() {
        if (hasEpsilon()) return "AFN-ε";
        return isDeterministic() ? "AFD" : "AFN";
    }

    // ------------------------------------------------------------ écriture
    public void addState(int s) { states.add(s); }

    public int newState() {
        int max = -1;
        for (int s : states) max = Math.max(max, s);
        int id = max + 1;
        states.add(id);
        return id;
    }

    public void addTransition(int from, int to, char symbol) {
        states.add(from);
        states.add(to);
        Transition t = new Transition(from, to, symbol);
        if (!transitions.contains(t)) transitions.add(t);
    }

    /** Supprime toutes les transitions de from vers to. */
    public void removeTransitions(int from, int to) {
        transitions.removeIf(t -> t.from() == from && t.to() == to);
    }

    public void removeState(int s) {
        states.remove(s);
        finals.remove(s);
        labels.remove(s);
        names.remove(s);
        transitions.removeIf(t -> t.from() == s || t.to() == s);
        if (start != null && start == s) start = null;
    }

    public void setStart(Integer s) {
        if (s != null) states.add(s);
        start = s;
    }

    public void setFinal(int s, boolean value) {
        states.add(s);
        if (value) finals.add(s); else finals.remove(s);
    }

    public void toggleFinal(int s) { setFinal(s, !isFinal(s)); }

    public void setLabel(int s, String text) { labels.put(s, text); }

    public Automaton copy() {
        Automaton c = new Automaton();
        c.states.addAll(states);
        c.transitions.addAll(transitions);
        c.finals.addAll(finals);
        c.labels.putAll(labels);
        c.names.putAll(names);
        c.labelHeader = labelHeader;
        c.start = start;
        return c;
    }
}
