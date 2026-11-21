package automates.engine;

import automates.model.Automaton;
import automates.model.Automaton.Transition;

import java.util.*;

/**
 * Automate → expression régulière par le LEMME D'ARDEN, rédigé comme dans le cours :
 *
 *   q0 = a q1      (1)
 *   q1 = b q2      (2)
 *   q2 = ε         (3)
 *   (3) dans (2) :  q1 = b·ε = b      (4)
 *   (4) dans (1) :  q0 = a·b  = ab    (5)
 *
 * Lemme d'Arden : si X = A·X + B, alors X = A*·B (plus petite solution ; unique si ε ∉ A).
 * Chaque état qi est une inconnue : le langage des mots acceptés en partant de qi.
 * Chaque équation reçoit un numéro, et chaque nouvelle équation obtenue en reçoit un nouveau.
 */
public final class ArdenSolver {

    private ArdenSolver() {}

    public record Step(String title, String explanation, List<String> lines) {}

    public record Result(Re regex, String text, List<Step> steps) {}

    /** Équation  qi = Σ coef[j]·qj + constant   (constant == null ⇔ ∅). */
    private static final class Eq {
        final LinkedHashMap<Integer, Re> coef = new LinkedHashMap<>();
        Re constant;
        void add(int j, Re r) { coef.merge(j, r, Re::union); }
        void addConstant(Re r) { constant = Re.union(constant, r); }
    }

    public static Result solve(Automaton m, String prefix) {
        List<Step> steps = new ArrayList<>();
        if (m.start() == null) {
            steps.add(new Step("Automate sans état initial", "Aucun mot n’est reconnu : le langage est vide.", List.of("L = ∅")));
            return new Result(Re.EMPTY, "∅", steps);
        }

        // ---- états utiles : accessibles depuis l'initial ET menant à un état final
        Set<Integer> acc = reach(m, Set.of(m.start()), false);
        Set<Integer> coacc = reach(m, m.finals(), true);
        Set<Integer> useful = new TreeSet<>();
        for (int s : acc) if (coacc.contains(s)) useful.add(s);
        List<String> notes = new ArrayList<>();
        for (int s : m.states()) {
            if (!acc.contains(s)) notes.add(m.name(s, prefix) + " est inaccessible depuis l’état initial : on l’ignore.");
            else if (!coacc.contains(s)) notes.add(m.name(s, prefix) + " ne mène à aucun état final (puits) : son langage est ∅, on supprime les termes qui y mènent.");
        }
        if (!useful.contains(m.start())) {
            steps.add(new Step("Langage vide", "Aucun état final n’est accessible depuis l’état initial.", notes));
            return new Result(Re.EMPTY, "∅", steps);
        }

        // ---- 1. système d'équations (numérotées dans l'ordre des états)
        Map<Integer, Eq> eqs = new TreeMap<>();
        for (int s : useful) eqs.put(s, new Eq());
        for (Transition t : m.transitions()) {
            if (useful.contains(t.from()) && useful.contains(t.to())) {
                eqs.get(t.from()).add(t.to(), t.isEpsilon() ? Re.EPS : Re.sym(t.symbol()));
            }
        }
        for (int f : m.finals()) if (useful.contains(f)) eqs.get(f).addConstant(Re.EPS);

        Map<Integer, Integer> num = new HashMap<>();
        int counter = 0;
        List<String> l1 = new ArrayList<>();
        for (String n : notes) l1.add("• " + n);
        if (!notes.isEmpty()) l1.add("");
        for (int s : useful) {
            num.put(s, ++counter);
            l1.add(equation(m, prefix, s, eqs.get(s)) + "      (" + counter + ")");
        }
        steps.add(new Step("Système d’équations",
                "Pour chaque état, on écrit son équation : la transition qi —a→ qj donne « a qj » ; un état final ajoute ε.", l1));

        steps.add(new Step("Lemme d’Arden (rappel)",
                "Si X = A X + B  alors  X = A* B.   (Si ε ∉ A c’est l’unique solution ; sinon c’est la plus petite, celle de l’automate.)",
                List.of("X = A X + B   ⟹   X = A* B")));

        // ---- 2. résolution : on résout les états un à un, du dernier au premier, l'état initial en dernier
        int start = m.start();
        List<Integer> order = new ArrayList<>(useful);
        order.remove(Integer.valueOf(start));
        Collections.reverse(order);

        List<String> lr = new ArrayList<>();
        for (int k : order) {
            Eq e = eqs.get(k);
            String vk = m.name(k, prefix);
            lr.add("● Résolution de " + vk + " :");
            Re a = e.coef.remove(k);
            if (a != null) {
                Eq before = copy(e);
                before.coef.put(k, a);
                lr.add("    (" + num.get(k) + ")  " + equation(m, prefix, k, before));
                lr.add("    Arden avec A = " + Re.show(a) + (rhsText(m, prefix, e).equals("∅") ? "" : "  et  B = " + rhsText(m, prefix, e))
                        + "   ⟹   " + vk + " = A* B");
                Re star = Re.star(a);
                String raw = Re.show(star) + paren(rhsText(m, prefix, e));
                for (Map.Entry<Integer, Re> en : e.coef.entrySet()) en.setValue(Re.concat(star, en.getValue()));
                e.constant = Re.concat(star, e.constant);
                num.put(k, ++counter);
                String simplified = equation(m, prefix, k, e);
                if (!(vk + " = " + raw).equals(simplified)) lr.add("    ⟹  " + vk + " = " + raw);
                lr.add("    ⟹  " + simplified + "      (" + counter + ")");
            } else {
                lr.add("    (" + num.get(k) + ")  " + equation(m, prefix, k, e) + "      (" + vk + " ne figure pas dans son membre droit : pas besoin d’Arden)");
            }

            boolean used = false;
            for (Map.Entry<Integer, Eq> en : eqs.entrySet()) {
                int i = en.getKey();
                if (i == k) continue;
                Eq t = en.getValue();
                if (!t.coef.containsKey(k)) continue;
                used = true;
                String raw = substitutionRaw(m, prefix, t, k, e);
                int ni = num.get(i);
                Re aik = t.coef.remove(k);
                for (Map.Entry<Integer, Re> b : e.coef.entrySet()) t.add(b.getKey(), Re.concat(aik, b.getValue()));
                t.addConstant(Re.concat(aik, e.constant));
                num.put(i, ++counter);
                lr.add("    (" + num.get(k) + ") dans (" + ni + ") :   " + m.name(i, prefix) + " = " + raw);
                lr.add("                          ⟹  " + equation(m, prefix, i, t) + "      (" + counter + ")");
            }
            if (!used) lr.add("    (" + vk + " n’apparaît dans aucune autre équation.)");
            eqs.remove(k);
            lr.add("");
        }
        if (!lr.isEmpty()) steps.add(new Step("Résolution", "On part du dernier état et on remonte : on résout une équation, "
                + "puis on remplace l’inconnue obtenue dans les autres équations (« (3) dans (2) » = l’équation (3) est reportée dans (2)).", lr));

        // ---- 3. équation de l'état initial
        Eq last = eqs.get(start);
        String vs = m.name(start, prefix);
        List<String> ll = new ArrayList<>();
        ll.add("    (" + num.get(start) + ")  " + equation(m, prefix, start, last));
        Re a = last.coef.remove(start);
        Re result;
        if (a != null) {
            Re star = Re.star(a);
            result = Re.concat(star, last.constant);
            ll.add("    Arden avec A = " + Re.show(a) + "  et  B = " + (last.constant == null ? "∅" : Re.show(last.constant)) + "   ⟹   " + vs + " = A* B");
            ll.add("    ⟹  " + vs + " = " + (result == null ? "∅" : Re.show(result)) + "      (" + (++counter) + ")");
        } else {
            result = last.constant;
            ll.add("    (" + vs + " ne figure pas dans son propre membre droit : pas besoin d’Arden.)");
        }
        if (result == null) result = Re.EMPTY;
        ll.add("");
        ll.add("Expression régulière :   L = " + vs + " = " + Re.show(result));
        steps.add(new Step("Équation de l’état initial " + vs, "Le langage de l’automate est celui de l’inconnue de l’état initial.", ll));

        return new Result(result, Re.show(result), steps);
    }

    // ------------------------------------------------------------ affichage
    /** « q1 = a q1 + b q2 + ε » */
    private static String equation(Automaton m, String prefix, int i, Eq e) {
        return m.name(i, prefix) + " = " + rhsText(m, prefix, e);
    }

    private static String rhsText(Automaton m, String prefix, Eq e) {
        List<String> terms = new ArrayList<>();
        for (Map.Entry<Integer, Re> en : new TreeMap<>(e.coef).entrySet()) terms.add(term(m, prefix, en.getValue(), en.getKey()));
        if (e.constant != null && !(e.constant instanceof Re.Empty)) terms.add(Re.show(e.constant));
        return terms.isEmpty() ? "∅" : String.join(" + ", terms);
    }

    /** « a q1 », « (a+b) q1 », « q1 » (coefficient ε). */
    private static String term(Automaton m, String prefix, Re coef, int j) {
        String v = m.name(j, prefix);
        String c = Re.coefficient(coef);
        return c.isEmpty() ? v : c + " " + v;
    }

    private static String paren(String s) { return s.contains("+") || s.contains(" ") ? "(" + s + ")" : s; }

    /**
     * Équation de qi où le terme « a qk » est remplacé, sans simplifier, par « a·(membre droit de qk) » :
     * c'est la ligne de calcul avant simplification, comme « q1 = b·ε ».
     */
    private static String substitutionRaw(Automaton m, String prefix, Eq t, int k, Eq ek) {
        String rhsK = rhsText(m, prefix, ek);
        List<String> terms = new ArrayList<>();
        for (Map.Entry<Integer, Re> en : new TreeMap<>(t.coef).entrySet()) {
            if (en.getKey() == k) {
                Re c = en.getValue();
                String cs = Re.coefficient(c);
                terms.add(cs.isEmpty() ? paren(rhsK) : cs + "·" + paren(rhsK));
            } else {
                terms.add(term(m, prefix, en.getValue(), en.getKey()));
            }
        }
        if (t.constant != null && !(t.constant instanceof Re.Empty)) terms.add(Re.show(t.constant));
        return String.join(" + ", terms);
    }

    private static Eq copy(Eq e) {
        Eq c = new Eq();
        c.coef.putAll(e.coef);
        c.constant = e.constant;
        return c;
    }

    // ------------------------------------------------------------ accessibilité
    private static Set<Integer> reach(Automaton m, Collection<Integer> from, boolean backwards) {
        Set<Integer> seen = new TreeSet<>(from);
        Deque<Integer> stack = new ArrayDeque<>(from);
        while (!stack.isEmpty()) {
            int u = stack.pop();
            for (Transition t : m.transitions()) {
                int src = backwards ? t.to() : t.from(), dst = backwards ? t.from() : t.to();
                if (src == u && seen.add(dst)) stack.push(dst);
            }
        }
        return seen;
    }
}
