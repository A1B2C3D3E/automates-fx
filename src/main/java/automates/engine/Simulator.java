package automates.engine;

import automates.model.Automaton;
import automates.model.Automaton.Transition;

import java.util.*;

/**
 * Lecture d'un mot, étape par étape, comme dans JFLAP : à chaque lettre on connaît
 * l'ensemble des états où l'automate peut se trouver et les transitions empruntées.
 */
public final class Simulator {

    private Simulator() {}

    /** Étape 0 = état initial (après ε-fermeture) ; étape i = après lecture de la i-ème lettre. */
    public record Step(int index, Character symbol, Set<Integer> from, Set<Integer> after, Set<Transition> used) {}

    /** Une transition du chemin acceptant, et le nombre de lettres lues une fois franchie. */
    public record Move(Transition transition, int posAfter) {}

    /**
     * @param consumed      nombre de lettres effectivement lues avant la fin ou le blocage
     * @param acceptingPath un chemin acceptant (liste de transitions) si le mot est accepté, sinon null
     */
    public record Run(String word, List<Step> steps, boolean accepted, int consumed,
                      String reason, List<Move> acceptingPath) {
        /** Ensemble d'états à la fin de la lecture (vide si blocage). */
        public Set<Integer> last() { return steps.isEmpty() ? Set.of() : steps.get(steps.size() - 1).after(); }
        /** Le mot a-t-il été lu jusqu'au bout ? */
        public boolean fullyRead() { return consumed == word.length(); }
    }

    public static Run run(Automaton m, String word, String prefix) {
        List<Step> steps = new ArrayList<>();
        if (m.start() == null) return new Run(word, steps, false, 0, "l’automate n’a pas d’état initial.", null);

        Set<Integer> cur = AutomatonOps.epsClosure(m, List.of(m.start()));
        steps.add(new Step(0, null, Set.of(), cur, epsEdges(m, cur)));
        int consumed = 0;
        String reason = null;
        for (int k = 0; k < word.length(); k++) {
            char c = word.charAt(k);
            Set<Integer> before = cur;
            cur = AutomatonOps.move(m, before, c);
            Set<Transition> used = new LinkedHashSet<>();
            for (Transition t : m.transitions()) if (before.contains(t.from()) && t.symbol() == c) used.add(t);
            used.addAll(epsEdges(m, cur));
            steps.add(new Step(k + 1, c, before, cur, used));
            if (cur.isEmpty()) {
                String where = format(m, before, prefix);
                reason = "blocage à la lettre n° " + (k + 1) + " « " + c + " » : aucune transition étiquetée « " + c + " » ne part de " + where + ".";
                break;
            }
            consumed++;
        }

        boolean fullyRead = consumed == word.length();
        boolean accepted = false;
        if (fullyRead) {
            for (int f : m.finals()) if (cur.contains(f)) { accepted = true; break; }
            String where = format(m, cur, prefix);
            boolean single = cur.size() == 1;
            if (accepted) {
                reason = "mot entièrement lu ; " + (single ? "l’état atteint " + where + " est final."
                        : "l’ensemble " + where + " contient un état final.");
            } else {
                reason = "mot entièrement lu, mais " + (single ? "l’état atteint " + where + " n’est pas final."
                        : "l’ensemble " + where + " ne contient aucun état final.");
            }
        }
        return new Run(word, steps, accepted, consumed, reason, accepted ? acceptingPath(m, word) : null);
    }

    /** Transitions ε dont l'origine est dans l'ensemble (donc empruntées lors de la ε-fermeture). */
    private static Set<Transition> epsEdges(Automaton m, Set<Integer> set) {
        Set<Transition> r = new LinkedHashSet<>();
        for (Transition t : m.transitions()) if (t.isEpsilon() && set.contains(t.from())) r.add(t);
        return r;
    }

    /** Plus court chemin (en nombre de transitions) de (initial, 0) à (final, |mot|). */
    private static List<Move> acceptingPath(Automaton m, String word) {
        int L = word.length();
        Map<String, String> parent = new HashMap<>();
        Map<String, Move> via = new HashMap<>();
        Deque<int[]> queue = new ArrayDeque<>();
        String startKey = m.start() + "," + 0;
        parent.put(startKey, null);
        queue.add(new int[]{m.start(), 0});
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            String key = cur[0] + "," + cur[1];
            if (cur[1] == L && m.isFinal(cur[0])) {
                LinkedList<Move> path = new LinkedList<>();
                for (String k = key; parent.get(k) != null; k = parent.get(k)) path.addFirst(via.get(k));
                return path;
            }
            for (Transition t : m.transitions()) {
                if (t.from() != cur[0]) continue;
                int np;
                if (t.isEpsilon()) np = cur[1];
                else if (cur[1] < L && word.charAt(cur[1]) == t.symbol()) np = cur[1] + 1;
                else continue;
                String nk = t.to() + "," + np;
                if (parent.containsKey(nk)) continue;
                parent.put(nk, key);
                via.put(nk, new Move(t, np));
                queue.add(new int[]{t.to(), np});
            }
        }
        return null;
    }

    // ------------------------------------------------------------ affichage texte
    /** « q1 » pour un seul état, « {q0, q2} » pour plusieurs, « - » si vide. */
    public static String format(Automaton m, Set<Integer> set, String prefix) {
        if (set.isEmpty()) return "-";
        if (set.size() == 1) return m.name(set.iterator().next(), prefix);
        StringJoiner j = new StringJoiner(", ", "{", "}");
        for (int s : set) j.add(m.name(s, prefix));
        return j.toString();
    }

    /** Ex. « q0 —a→ q1 —b→ q2 ». Pour un AFN, les ensembles d'états : « {n0, n1} —a→ {n2} ». */
    public static String trace(Automaton m, Run r, String prefix) {
        if (r.steps().isEmpty()) return "(pas d’état initial)";
        StringBuilder sb = new StringBuilder(format(m, r.steps().get(0).after(), prefix));
        for (int i = 1; i < r.steps().size(); i++) {
            Step st = r.steps().get(i);
            sb.append("  —").append(st.symbol()).append("→  ").append(format(m, st.after(), prefix));
        }
        return sb.toString();
    }

    /** Chemin acceptant détaillé, ε compris : « n0 —ε→ n1 —a→ n2 ». Null si le mot est rejeté. */
    public static String pathText(Automaton m, Run r, String prefix) {
        if (r.acceptingPath() == null) return null;
        StringBuilder sb = new StringBuilder(m.name(m.start(), prefix));
        for (Move mv : r.acceptingPath()) {
            sb.append("  —").append(mv.transition().label()).append("→  ").append(m.name(mv.transition().to(), prefix));
        }
        return sb.toString();
    }
}
