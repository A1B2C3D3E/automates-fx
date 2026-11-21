package automates.engine;

import automates.model.Automaton;
import automates.model.Automaton.Transition;

import java.util.*;

/**
 * Minimisation d'un AFD par raffinement de partitions (algorithme de Moore), avec le détail
 * de chaque étape pour l'affichage.
 *
 * Déroulement :
 *  1. on supprime les états inaccessibles ;
 *  2. si l'AFD est incomplet, on le COMPLÈTE avec un état puits « p » ;
 *  3. P0 = { états finals, états non finals } ;
 *  4. P(k+1) : deux états restent ensemble s'ils sont dans la même classe de P(k) ET si, pour
 *     chaque lettre, leurs successeurs sont dans la même classe de P(k) ;
 *  5. on s'arrête quand P(k+1) = P(k) ; chaque classe devient un état de l'AFD minimal.
 *
 * Le résultat est donné sous deux formes : l'AFD minimal COMPLET (avec le puits s'il y en a un)
 * et l'AFD minimal PARTIEL (puits retiré, c'est ce que l'on dessine souvent).
 */
public final class Minimizer {

    private Minimizer() {}

    /** Une étape : la partition P(k), les classes de destination de chaque état, et P(k+1). */
    public record Round(int index,
                        List<List<Integer>> partition,
                        Map<Integer, Integer> classOf,
                        Map<Integer, int[]> targetClass,
                        List<List<Integer>> refined,
                        boolean stable) {}

    public record Result(Automaton original,
                         List<Integer> unreachable,
                         Automaton complete,
                         Integer sink,
                         List<Character> alphabet,
                         List<Round> rounds,
                         List<List<Integer>> classes,
                         Automaton minimalComplete,
                         Integer minimalSink,
                         Automaton minimalPartial) {
        /** L'AFD de départ était-il incomplet (puits ajouté) ? */
        public boolean wasIncomplete() { return sink != null; }
        /** L'AFD minimal complet possède-t-il un état puits (donc le minimal « simple » est incomplet) ? */
        public boolean minimalHasSink() { return minimalSink != null; }
    }

    public static Result run(Automaton d, Collection<Character> extraAlphabet) { return run(d, extraAlphabet, "q"); }

    public static Result run(Automaton d, Collection<Character> extraAlphabet, String prefix) {
        if (!d.isDeterministic()) throw new IllegalArgumentException("l'automate doit être déterministe");
        TreeSet<Character> sigma = new TreeSet<>(d.alphabet());
        if (extraAlphabet != null) sigma.addAll(extraAlphabet);
        List<Character> alphabet = new ArrayList<>(sigma);

        if (d.start() == null) {
            Automaton e = new Automaton();
            return new Result(d, List.of(), e, null, alphabet, List.of(), List.of(), e, null, e);
        }

        // 1. états inaccessibles
        Set<Integer> reach = new TreeSet<>();
        Deque<Integer> stack = new ArrayDeque<>(List.of(d.start()));
        reach.add(d.start());
        while (!stack.isEmpty()) {
            int u = stack.pop();
            for (Transition t : d.transitions()) {
                if (t.from() == u && reach.add(t.to())) stack.push(t.to());
            }
        }
        List<Integer> unreachable = new ArrayList<>();
        Automaton trimmed = d.copy();
        for (int s : d.states()) if (!reach.contains(s)) { unreachable.add(s); trimmed.removeState(s); }

        // 2. complétion
        AutomatonOps.Completed comp = AutomatonOps.complete(trimmed, alphabet);
        Automaton c = comp.automaton();

        List<Integer> ids = new ArrayList<>(c.states());
        Collections.sort(ids);
        int n = ids.size(), k = alphabet.size();
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.put(ids.get(i), i);
        int[][] next = new int[n][k];
        for (Transition t : c.transitions()) next[index.get(t.from())][alphabet.indexOf(t.symbol())] = index.get(t.to());

        // 3. P0 = { finals, non finals }
        List<List<Integer>> part = new ArrayList<>();
        List<Integer> fin = new ArrayList<>(), non = new ArrayList<>();
        for (int i = 0; i < n; i++) (c.isFinal(ids.get(i)) ? fin : non).add(i);
        if (!fin.isEmpty()) part.add(fin);
        if (!non.isEmpty()) part.add(non);

        // 4. raffinements successifs
        List<Round> rounds = new ArrayList<>();
        while (true) {
            int[] cls = new int[n];
            for (int ci = 0; ci < part.size(); ci++) for (int s : part.get(ci)) cls[s] = ci;

            Map<Integer, int[]> targets = new LinkedHashMap<>();
            List<List<Integer>> refined = new ArrayList<>();
            for (List<Integer> group : part) {
                Map<String, List<Integer>> split = new LinkedHashMap<>();
                for (int s : group) {
                    int[] tc = new int[k];
                    StringBuilder sig = new StringBuilder();
                    for (int a = 0; a < k; a++) { tc[a] = cls[next[s][a]]; sig.append(tc[a]).append(','); }
                    targets.put(ids.get(s), tc);
                    split.computeIfAbsent(sig.toString(), x -> new ArrayList<>()).add(s);
                }
                refined.addAll(split.values());
            }
            boolean stable = refined.size() == part.size();
            Map<Integer, Integer> classOf = new LinkedHashMap<>();
            for (int ci = 0; ci < part.size(); ci++) for (int s : part.get(ci)) classOf.put(ids.get(s), ci);
            rounds.add(new Round(rounds.size(), toIds(part, ids), classOf, targets, toIds(refined, ids), stable));
            if (stable) break;
            part = refined;
        }

        // 5. quotient
        int[] cls = new int[n];
        for (int ci = 0; ci < part.size(); ci++) for (int s : part.get(ci)) cls[s] = ci;
        int m = part.size();
        int startClass = cls[index.get(c.start())];

        // classe « puits » : non finale, et toutes ses transitions restent dans la classe
        int sinkClass = -1;
        for (int ci = 0; ci < m && sinkClass < 0; ci++) {
            int rep = part.get(ci).get(0);
            boolean dead = !c.isFinal(ids.get(rep));
            for (int a = 0; a < k && dead; a++) dead = cls[next[rep][a]] == ci;
            if (dead) sinkClass = ci;
        }

        // numérotation : parcours en largeur depuis l'initial, puits en dernier
        Map<Integer, Integer> newId = new LinkedHashMap<>();
        List<Integer> order = new ArrayList<>();
        order.add(startClass);
        for (int i = 0; i < order.size(); i++) {
            int rep = part.get(order.get(i)).get(0);
            for (int a = 0; a < k; a++) {
                int t = cls[next[rep][a]];
                if (!order.contains(t)) order.add(t);
            }
        }
        for (int ci = 0; ci < m; ci++) if (!order.contains(ci)) order.add(ci);   // (inatteignable : par sécurité)
        if (sinkClass >= 0 && sinkClass != startClass) { order.remove(Integer.valueOf(sinkClass)); order.add(sinkClass); }
        for (int i = 0; i < order.size(); i++) newId.put(order.get(i), i);

        Automaton full = new Automaton();
        full.setLabelHeader("Classe d’équivalence");
        for (int ci : order) {
            int id = newId.get(ci);
            full.addState(id);
            StringJoiner j = new StringJoiner(", ", "{", "}");
            for (int s : part.get(ci)) j.add(c.name(ids.get(s), prefix));
            full.setLabel(id, j.toString());
            full.setName(id, c.name(ids.get(part.get(ci).get(0)), prefix));   // représentant de la classe
            if (c.isFinal(ids.get(part.get(ci).get(0)))) full.setFinal(id, true);
            int rep = part.get(ci).get(0);
            for (int a = 0; a < k; a++) full.addTransition(id, newId.get(cls[next[rep][a]]), alphabet.get(a));
        }
        full.setStart(newId.get(startClass));
        Integer minimalSink = sinkClass >= 0 ? newId.get(sinkClass) : null;
        if (minimalSink != null) full.setName(minimalSink, "p");

        // version partielle : on retire le puits (sauf s'il est l'état initial : langage vide)
        Automaton partial = full.copy();
        if (minimalSink != null) {
            if (minimalSink.equals(full.start())) partial.removeTransitions(minimalSink, minimalSink);
            else partial.removeState(minimalSink);
        }

        List<List<Integer>> classes = new ArrayList<>();
        for (int ci : order) classes.add(toIds(List.of(part.get(ci)), ids).get(0));
        return new Result(d, unreachable, c, comp.sink(), alphabet, rounds, classes, full, minimalSink, partial);
    }

    private static List<List<Integer>> toIds(List<List<Integer>> groups, List<Integer> ids) {
        List<List<Integer>> r = new ArrayList<>();
        for (List<Integer> g : groups) {
            List<Integer> x = new ArrayList<>();
            for (int s : g) x.add(ids.get(s));
            r.add(x);
        }
        return r;
    }
}
