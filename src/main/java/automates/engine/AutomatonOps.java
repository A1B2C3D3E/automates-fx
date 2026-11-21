package automates.engine;

import automates.model.Automaton;
import automates.model.Automaton.Transition;

import java.util.*;

/** Algorithmes sur les automates : simulation, déterminisation, minimisation, équivalence. */
public final class AutomatonOps {

    private static final int MAX_DFA_STATES = 5000;

    private AutomatonOps() {}

    // ------------------------------------------------------------ simulation
    /** ε-fermeture d'un ensemble d'états. */
    public static Set<Integer> epsClosure(Automaton m, Collection<Integer> from) {
        Set<Integer> res = new TreeSet<>(from);
        Deque<Integer> stack = new ArrayDeque<>(from);
        while (!stack.isEmpty()) {
            int u = stack.pop();
            for (int v : m.targets(u, Automaton.EPS)) {
                if (res.add(v)) stack.push(v);
            }
        }
        return res;
    }

    /** États atteints depuis l'ensemble en lisant a (puis ε-fermeture). */
    public static Set<Integer> move(Automaton m, Set<Integer> set, char a) {
        Set<Integer> r = new TreeSet<>();
        for (int u : set) r.addAll(m.targets(u, a));
        return epsClosure(m, r);
    }

    public static boolean accepts(Automaton m, String word) {
        if (m.start() == null) return false;
        Set<Integer> cur = epsClosure(m, List.of(m.start()));
        for (char c : word.toCharArray()) {
            cur = move(m, cur, c);
            if (cur.isEmpty()) return false;
        }
        for (int f : m.finals()) if (cur.contains(f)) return true;
        return false;
    }

    // ------------------------------------------------------------ AFN → AFD
    public static Automaton determinize(Automaton m) { return determinize(m, "q", null); }
    public static Automaton determinize(Automaton m, String prefix) { return determinize(m, prefix, null); }

    /**
     * Construction des sous-ensembles. Chaque état de l'AFD reçoit en « label »
     * l'ensemble d'états de l'AFN qu'il représente (affiché avec le préfixe donné).
     * L'AFD obtenu est partiel : pas d'état puits (voir {@link #complete}).
     *
     * @param log si non nul, reçoit le déroulement de l'algorithme, ligne par ligne
     */
    public static Automaton determinize(Automaton m, String prefix, List<String> log) {
        return determinize(m, prefix, log, "");
    }

    /**
     * Comme ci-dessus, mais les états de l'AFD s'appellent A{suffixe}, B{suffixe}, … :
     * avec le suffixe « ' » on obtient A', B', C' (utile pour distinguer le 2e automate d'un produit).
     */
    public static Automaton determinize(Automaton m, String prefix, List<String> log, String suffix) {
        java.util.function.IntFunction<String> dn = i -> dfaName(i) + suffix;
        Automaton d = new Automaton();
        if (m.start() == null) return d;
        List<Character> alphabet = new ArrayList<>(m.alphabet());

        Map<Set<Integer>, Integer> ids = new HashMap<>();
        List<Set<Integer>> sets = new ArrayList<>();
        Set<Integer> s0 = epsClosure(m, List.of(m.start()));
        ids.put(s0, 0);
        sets.add(s0);
        if (log != null) {
            log.add("ε-fer(" + m.name(m.start(), prefix) + ") = " + describe(m, s0, prefix) + " = " + dn.apply(0) + "      (état initial)");
        }

        for (int i = 0; i < sets.size(); i++) {
            Set<Integer> cur = sets.get(i);
            d.addState(i);
            d.setName(i, dn.apply(i));
            d.setLabel(i, describe(m, cur, prefix));
            for (int f : m.finals()) {
                if (cur.contains(f)) { d.setFinal(i, true); break; }
            }
            for (char a : alphabet) {
                Set<Integer> moved = move(m, cur, a);
                if (moved.isEmpty()) {
                    if (log != null) log.add("ε-fer(trans(" + dn.apply(i) + ", " + a + ")) = -      (aucune transition)");
                    continue;
                }
                Set<Integer> next = epsClosure(m, moved);
                Integer id = ids.get(next);
                boolean fresh = id == null;
                if (fresh) {
                    if (sets.size() >= MAX_DFA_STATES) {
                        throw new IllegalStateException("AFD trop grand (plus de " + MAX_DFA_STATES + " états)");
                    }
                    id = sets.size();
                    ids.put(next, id);
                    sets.add(next);
                }
                if (log != null) {
                    log.add("ε-fer(trans(" + dn.apply(i) + ", " + a + ")) = ε-fer(" + listOf(m, moved, prefix) + ") = "
                            + describe(m, next, prefix) + " = " + dn.apply(id) + (fresh ? "      (nouvel état)" : ""));
                }
                d.addTransition(i, id, a);
            }
        }
        d.setStart(0);
        if (log != null) {
            StringJoiner all = new StringJoiner(", ");
            for (int i = 0; i < sets.size(); i++) all.add(dn.apply(i));
            StringJoiner fin = new StringJoiner(", ");
            for (int f : d.finals()) fin.add(dn.apply(f));
            log.add("");
            log.add("États de l’AFD : {" + all + "}      état initial : " + dn.apply(0));
            log.add("États finals : " + (d.finals().isEmpty() ? "aucun" : "{" + fin + "}")
                    + "      (les ensembles qui contiennent un état final de l’AFN)");
        }
        return d;
    }

    /** Nom d'un état de l'AFD : A, B, C, … Z, A1, B1, … */
    public static String dfaName(int i) {
        char c = (char) ('A' + i % 26);
        return i < 26 ? String.valueOf(c) : c + String.valueOf(i / 26);
    }

    /** « q0, q2 » (sans accolades, comme dans ε-fer(q0, q2)). */
    private static String listOf(Automaton m, Set<Integer> set, String prefix) {
        StringJoiner j = new StringJoiner(", ");
        for (int s : set) j.add(m.name(s, prefix));
        return j.toString();
    }

    private static String describe(Automaton m, Set<Integer> set, String prefix) {
        StringJoiner j = new StringJoiner(", ", "{", "}");
        for (int s : set) j.add(m.name(s, prefix));
        return j.toString();
    }

    // ------------------------------------------------------------ complétion
    /**
     * Complète un AFD : ajoute un état puits (non final, qui boucle sur tout l'alphabet)
     * vers lequel partent toutes les transitions manquantes. Retourne une copie ;
     * l'état puits n'est ajouté que s'il est nécessaire. Voir {@link Completed}.
     */
    public record Completed(Automaton automaton, Integer sink) {}

    public static Completed complete(Automaton d, Collection<Character> alphabet) {
        Automaton c = d.copy();
        List<Character> sigma = new ArrayList<>(new TreeSet<>(alphabet));
        boolean missing = false;
        for (int s : c.states()) {
            for (char a : sigma) if (c.targets(s, a).isEmpty()) { missing = true; break; }
            if (missing) break;
        }
        if (!missing && !c.isEmpty()) return new Completed(c, null);
        int p = c.newState();
        c.setName(p, "p");
        for (int s : new ArrayList<>(c.states())) {
            for (char a : sigma) if (c.targets(s, a).isEmpty()) c.addTransition(s, p, a);
        }
        return new Completed(c, p);
    }

    // ------------------------------------------------------------ minimisation
    /** AFN quelconque → AFD minimal (sans état puits, donc éventuellement incomplet). */
    public static Automaton toMinimalDfa(Automaton m) {
        return Minimizer.run(determinize(m), List.of()).minimalPartial();
    }

    /** Minimisation d'un AFD (éventuellement partiel) ; résultat sans état puits. */
    public static Automaton minimize(Automaton d) {
        return Minimizer.run(d, List.of()).minimalPartial();
    }

    // ------------------------------------------------------------ équivalence
    /**
     * Retourne un mot reconnu par un seul des deux automates, ou null s'ils
     * reconnaissent le même langage (parcours du produit des deux AFD).
     * Le mot vide est retourné sous la forme "".
     */
    public static String distinguishingWord(Automaton a, Automaton b) {
        Automaton da = determinize(a), db = determinize(b);
        SortedSet<Character> alphabet = new TreeSet<>(da.alphabet());
        alphabet.addAll(db.alphabet());

        Set<String> seen = new HashSet<>();
        Deque<Object[]> queue = new ArrayDeque<>();
        int x0 = da.start() == null ? -1 : da.start();
        int y0 = db.start() == null ? -1 : db.start();
        seen.add(x0 + "," + y0);
        queue.add(new Object[]{x0, y0, ""});

        while (!queue.isEmpty()) {
            Object[] cur = queue.poll();
            int x = (Integer) cur[0], y = (Integer) cur[1];
            String w = (String) cur[2];
            if (fin(da, x) != fin(db, y)) return w;
            for (char c : alphabet) {
                int nx = step(da, x, c), ny = step(db, y, c);
                if (seen.add(nx + "," + ny)) queue.add(new Object[]{nx, ny, w + c});
            }
        }
        return null;
    }

    private static boolean fin(Automaton m, int s) { return s >= 0 && m.isFinal(s); }

    private static int step(Automaton m, int s, char c) {
        if (s < 0) return -1;
        List<Integer> t = m.targets(s, c);
        return t.isEmpty() ? -1 : t.get(0);
    }
}
