package automates.engine;

import automates.model.Automaton;
import automates.model.Automaton.Transition;

import java.util.*;

/**
 * Automate → expression régulière par élimination d'états (Brzozowski-McCluskey).
 * Seconde méthode, indépendante du lemme d'Arden (voir {@link ArdenSolver}) : elle sert
 * à recouper le résultat. Fonctionne pour un AFD, un AFN ou un AFN-ε.
 */
public final class RegexBuilder {

    private static final int S = -1, F = -2; // nouvel état initial / nouvel état final

    private RegexBuilder() {}

    public static String toRegex(Automaton m) { return Re.show(toRe(m)); }

    public static Re toRe(Automaton m) {
        if (m.start() == null) return Re.EMPTY;

        Map<Integer, Map<Integer, Re>> r = new HashMap<>();
        for (Transition t : m.transitions()) add(r, t.from(), t.to(), t.isEpsilon() ? Re.EPS : Re.sym(t.symbol()));
        add(r, S, m.start(), Re.EPS);
        for (int f : m.finals()) add(r, f, F, Re.EPS);

        List<Integer> alive = new ArrayList<>(m.states());
        alive.add(S);
        alive.add(F);

        for (int q : new ArrayList<>(m.states())) {
            Re self = get(r, q, q);
            Re loop = self == null ? Re.EPS : Re.star(self);
            alive.remove(Integer.valueOf(q));
            for (int p : alive) {
                Re pq = get(r, p, q);
                if (pq == null) continue;
                for (int s : alive) {
                    Re qs = get(r, q, s);
                    if (qs == null) continue;
                    add(r, p, s, Re.concat(Re.concat(pq, loop), qs));
                }
            }
            for (Map<Integer, Re> row : r.values()) row.remove(q);
            r.remove(q);
        }
        Re res = get(r, S, F);
        return res == null ? Re.EMPTY : res;
    }

    private static Re get(Map<Integer, Map<Integer, Re>> r, int a, int b) {
        Map<Integer, Re> row = r.get(a);
        return row == null ? null : row.get(b);
    }

    private static void add(Map<Integer, Map<Integer, Re>> r, int a, int b, Re re) {
        Map<Integer, Re> row = r.computeIfAbsent(a, k -> new HashMap<>());
        row.put(b, Re.union(row.get(b), re));
    }
}
