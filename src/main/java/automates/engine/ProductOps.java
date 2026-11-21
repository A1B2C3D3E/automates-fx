package automates.engine;

import automates.model.Automaton;

import java.util.*;

/**
 * Intersection et union de deux langages réguliers par la construction de l'AUTOMATE PRODUIT.
 *
 * Méthode (identique pour ∩ et ∪, seuls les états finals changent) :
 *  1. chaque automate est rendu DÉTERMINISTE (sous-ensembles) puis COMPLET (état puits p) sur le même Σ ;
 *  2. les états du produit sont des couples (P, X) : P état de l'AFD 1, X état de l'AFD 2 ;
 *     l'état initial est (P0, X0) et trans((P, X), a) = (trans(P, a), trans(X, a)) ;
 *     on ne construit que les couples atteignables depuis l'état initial ;
 *  3. états finals : ∩ → P final ET X final ;  ∪ → P final OU X final ;
 *  4. on minimise l'AFD obtenu (voir {@link Minimizer}).
 *
 * L'union exige des AFD COMPLETS : avec un AFD incomplet, un mot qui bloque dans l'un des deux
 * automates serait rejeté alors qu'il peut être accepté par l'autre. C'est le rôle du puits.
 */
public final class ProductOps {

    private ProductOps() {}

    public enum Op {
        INTERSECTION("∩", "intersection", "ET"),
        UNION("∪", "union", "OU");

        public final String symbol, noun, connective;
        Op(String symbol, String noun, String connective) {
            this.symbol = symbol;
            this.noun = noun;
            this.connective = connective;
        }

        public boolean combine(boolean a, boolean b) { return this == INTERSECTION ? a && b : a || b; }
    }

    /**
     * Un opérande préparé.
     *
     * @param original      automate donné (AFN, AFN-ε ou AFD), noms q0, q1… (ou q0', q1'… pour le 2e)
     * @param wasDeterministic l'automate donné était-il déjà un AFD ?
     * @param detLog        journal de la déterminisation (vide si déjà déterministe)
     * @param dfa           AFD (éventuellement incomplet)
     * @param complete      AFD complet sur Σ
     * @param sink          état puits ajouté (null si l'AFD était déjà complet)
     * @param missing       transitions manquantes ayant conduit au puits, ex. « trans(A, b) »
     */
    public record Operand(int index, String suffix, Automaton original, boolean wasDeterministic,
                          List<String> detLog, Automaton dfa, Automaton complete, Integer sink,
                          List<String> missing) {}

    /** Un mot d'essai et son appartenance à L1, L2 et au résultat. */
    public record Sample(String word, boolean in1, boolean in2, boolean inResult) {}

    public record Verification(int tested, int maxLength, String counterExample,
                               String minimalDifference) {
        public boolean ok() { return counterExample == null && minimalDifference == null; }
    }

    public record Result(Op op, List<Character> alphabet, Operand first, Operand second,
                         Automaton product, List<String> buildLog, List<String> finalLog,
                         int possiblePairs, Minimizer.Result minimization,
                         List<Sample> samples, Verification verification) {
        public boolean isEmptyLanguage() { return product.finals().isEmpty(); }
        public Automaton minimal() { return minimization.minimalComplete(); }
        public Automaton minimalPartial() { return minimization.minimalPartial(); }
    }

    // ------------------------------------------------------------------ préparation d'un opérande
    /** Suffixe de nommage : aucun pour le 1er automate, « ' » pour le 2e (q0 et q0'). */
    private static String suffixOf(int index) { return index == 1 ? "" : "'"; }

    public static Operand prepare(Automaton m, int index, Collection<Character> sigma) {
        if (m.start() == null) throw new IllegalArgumentException("l'automate " + index + " n'a pas d'état initial");
        String suffix = suffixOf(index);

        // noms de départ : q0, q1… (ou q0', q1'… pour le 2e)
        Automaton named = m.copy();
        for (int s : m.states()) named.setName(s, m.name(s, "q") + suffix);

        boolean det = named.isDeterministic();
        List<String> log = new ArrayList<>();
        Automaton dfa;
        if (det) {
            dfa = named;
        } else {
            dfa = AutomatonOps.determinize(named, "q", log, suffix);
        }

        AutomatonOps.Completed comp = AutomatonOps.complete(dfa, sigma);
        Automaton complete = comp.automaton();
        List<String> missing = new ArrayList<>();
        if (comp.sink() != null) {
            complete.setName(comp.sink(), "p" + suffix);
            for (int s : dfa.states()) {
                for (char a : new TreeSet<>(sigma)) {
                    if (dfa.targets(s, a).isEmpty()) missing.add("trans(" + dfa.name(s, "q") + ", " + a + ")");
                }
            }
        }
        return new Operand(index, suffix, m, det, log, dfa, complete, comp.sink(), missing);
    }

    // ------------------------------------------------------------------ produit
    public static Result run(Automaton m1, Automaton m2, Op op, Collection<Character> sigmaIn) {
        TreeSet<Character> sigmaSet = new TreeSet<>(sigmaIn);
        sigmaSet.addAll(m1.alphabet());
        sigmaSet.addAll(m2.alphabet());
        List<Character> sigma = new ArrayList<>(sigmaSet);

        Operand o1 = prepare(m1, 1, sigma);
        Operand o2 = prepare(m2, 2, sigma);
        Automaton a = o1.complete(), b = o2.complete();

        Automaton prod = new Automaton();
        List<String> build = new ArrayList<>();
        List<String> fin = new ArrayList<>();
        Map<String, Integer> ids = new HashMap<>();
        List<int[]> pairs = new ArrayList<>();

        int[] init = {a.start(), b.start()};
        ids.put(init[0] + "," + init[1], 0);
        pairs.add(init);

        for (int i = 0; i < pairs.size(); i++) {
            int x = pairs.get(i)[0], y = pairs.get(i)[1];
            String name = pairName(a, b, x, y);
            prod.addState(i);
            prod.setName(i, name);
            boolean f1 = a.isFinal(x), f2 = b.isFinal(y);
            boolean f = op.combine(f1, f2);
            prod.setFinal(i, f);

            build.add("État " + name + (i == 0 ? "      (état initial : couple des deux états initiaux)" : "") + " :");
            for (char c : sigma) {
                int nx = a.targets(x, c).get(0), ny = b.targets(y, c).get(0);
                String key = nx + "," + ny;
                Integer id = ids.get(key);
                boolean fresh = id == null;
                if (fresh) {
                    id = pairs.size();
                    ids.put(key, id);
                    pairs.add(new int[]{nx, ny});
                }
                prod.addTransition(i, id, c);
                build.add("    " + c + " :  " + a.name(x, "q") + " → " + a.name(nx, "q") + " dans A1,   "
                        + b.name(y, "q") + " → " + b.name(ny, "q") + " dans A2   ⟹   "
                        + pairName(a, b, nx, ny) + (fresh ? "   (nouvel état)" : ""));
            }
            build.add("");

            fin.add(name + " :  " + a.name(x, "q") + (f1 ? " est final" : " n’est pas final")
                    + ",  " + b.name(y, "q") + (f2 ? " est final" : " n’est pas final")
                    + "   ⟹   " + f1Text(f1) + " " + op.connective + " " + f1Text(f2) + " = "
                    + (f ? "VRAI : état FINAL" : "FAUX : état non final"));
        }
        prod.setStart(0);

        int possible = a.states().size() * b.states().size();
        build.add("Couples construits : " + pairs.size() + " sur " + possible + " possibles"
                + (pairs.size() < possible ? " (les autres ne sont pas atteignables depuis l’état initial : on ne les écrit pas)." : "."));

        Minimizer.Result min = Minimizer.run(prod, sigma, "q");

        List<Sample> samples = samples(m1, m2, prod, op, sigma);
        Verification ver = verify(m1, m2, prod, min.minimalPartial(), op, sigma);
        return new Result(op, sigma, o1, o2, prod, build, fin, possible, min, samples, ver);
    }

    private static String f1Text(boolean b) { return b ? "vrai" : "faux"; }

    private static String pairName(Automaton a, Automaton b, int x, int y) {
        return "(" + a.name(x, "q") + "," + b.name(y, "q") + ")";
    }

    // ------------------------------------------------------------------ mots d'essai et vérification
    /** Les mots dans l'ordre (longueur, puis alphabétique), jusqu'à la longueur maxLen ou limit mots. */
    private static List<String> words(List<Character> sigma, int maxLen, int limit) {
        List<String> res = new ArrayList<>();
        res.add("");
        List<String> layer = List.of("");
        for (int len = 1; len <= maxLen && res.size() < limit; len++) {
            List<String> next = new ArrayList<>();
            for (String w : layer) for (char c : sigma) {
                if (res.size() + next.size() >= limit) break;
                next.add(w + c);
            }
            res.addAll(next);
            layer = next;
        }
        return res;
    }

    private static List<Sample> samples(Automaton m1, Automaton m2, Automaton prod, Op op, List<Character> sigma) {
        List<String> candidates = words(sigma, 6, 4000);
        List<Sample> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        // 8 premiers mots, puis les premiers mots du résultat et des mots qui illustrent la différence ∩ / ∪
        for (int i = 0; i < Math.min(8, candidates.size()); i++) addSample(out, used, candidates.get(i), m1, m2, prod);
        int inRes = 0, onlyOne = 0, neither = 0;
        for (String w : candidates) {
            if (out.size() >= 16) break;
            if (used.contains(w)) continue;
            boolean r = AutomatonOps.accepts(prod, w);
            boolean x = AutomatonOps.accepts(m1, w), y = AutomatonOps.accepts(m2, w);
            if (r && inRes < 3) { addSample(out, used, w, m1, m2, prod); inRes++; }
            else if (x != y && onlyOne < 3) { addSample(out, used, w, m1, m2, prod); onlyOne++; }
            else if (!x && !y && neither < 2) { addSample(out, used, w, m1, m2, prod); neither++; }
        }
        out.sort(Comparator.comparingInt((Sample s) -> s.word().length()).thenComparing(Sample::word));
        return out;
    }

    private static void addSample(List<Sample> out, Set<String> used, String w, Automaton m1, Automaton m2, Automaton prod) {
        if (!used.add(w)) return;
        out.add(new Sample(w, AutomatonOps.accepts(m1, w), AutomatonOps.accepts(m2, w), AutomatonOps.accepts(prod, w)));
    }

    /**
     * Vérifie le produit contre la définition : un mot est dans L1 ∩ L2 (resp. L1 ∪ L2) si et seulement si
     * il est accepté par les AUTOMATES DE DÉPART (sans passer par le produit) à la fois / au moins une fois.
     * Vérifie aussi que l'AFD minimal reconnaît le même langage que le produit.
     */
    private static Verification verify(Automaton m1, Automaton m2, Automaton prod, Automaton minimal,
                                       Op op, List<Character> sigma) {
        List<String> ws = words(sigma, 8, 30000);
        String bad = null;
        for (String w : ws) {
            boolean expected = op.combine(AutomatonOps.accepts(m1, w), AutomatonOps.accepts(m2, w));
            if (AutomatonOps.accepts(prod, w) != expected) { bad = w; break; }
        }
        String diff = AutomatonOps.distinguishingWord(prod, minimal);
        int maxLen = 0;
        for (String w : ws) maxLen = Math.max(maxLen, w.length());
        return new Verification(ws.size(), maxLen, bad, diff);
    }
}
