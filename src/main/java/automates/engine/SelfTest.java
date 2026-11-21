package automates.engine;

import automates.model.Automaton;
import automates.model.Automaton.Transition;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Vérification du moteur : java -cp target/classes automates.engine.SelfTest
 *
 * Chaque théorème est recoupé avec une méthode INDÉPENDANTE :
 *  - Thompson / sous-ensembles / minimisation : comparés à java.util.regex sur tous les mots courts ;
 *  - minimalité : comparée à l'algorithme de Brzozowski (double renversement) ;
 *  - Arden et élimination d'états : l'expression obtenue est relue et comparée au langage de départ ;
 *  - automates aléatoires avec transitions ε : comparés à une simulation brute (parcours de configurations).
 */
public final class SelfTest {

    private static int failures = 0, checks = 0, skipped = 0;

    /** Séquence qui coupe le backtracking catastrophique de java.util.regex (étoiles imbriquées). */
    private static final class Budget implements CharSequence {
        private final String s;
        private int left;
        Budget(String s, int budget) { this.s = s; this.left = budget; }
        public int length() { return s.length(); }
        public char charAt(int i) {
            if (--left < 0) throw new IllegalStateException("budget");
            return s.charAt(i);
        }
        public CharSequence subSequence(int a, int b) { return s.substring(a, b); }
        @Override public String toString() { return s; }
    }

    /** Verdict de java.util.regex, ou null si l'oracle n'a pas pu trancher (mot ignoré, comptabilisé). */
    private static Boolean jm(Pattern p, String w) {
        try { return p.matcher(new Budget(w, 400000)).matches(); }
        catch (IllegalStateException e) { skipped++; return null; }
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 12345L;
        Random rnd = new Random(seed);

        section("1. Expressions fixes (théorèmes de Kleene, minimisation, Arden)");
        String[] fixed = {
            "(a+b)*ab", "a*b*", "(ab+c)*", "a(a+b)*b", "(a+b)*aa(a+b)*", "(0+1)*1(0+1)",
            "a|b|ab", "(a+ε)b*", "ε", "∅", "a∅", "∅*", "((a*)*+b)*c?", "(a+b)*a(a+b)(a+b)",
            "a*+b*", "(aa+bb)*", "(a+b)(a+b)(a+b)*", "abab", "(a*b)*a*", "b*(ab*ab*)*"
        };
        for (String re : fixed) checkRegex(re, "abc01", 6);

        section("2. Tailles minimales connues");
        expect("(a+b)*ab : minimal = 3 états", minSize("(a+b)*ab") == 3);
        expect("(0+1)*1(0+1) : minimal = 4 états", minSize("(0+1)*1(0+1)") == 4);
        expect("(a+b)*a(a+b)(a+b) : minimal = 8 états", minSize("(a+b)*a(a+b)(a+b)") == 8);
        expect("(aa+bb)* : minimal complet = 4 états dont un puits", minComplete("(aa+bb)*").states().size() == 4);
        expect("a*b* sur {a,b} : puits présent dans le minimal complet", minComplete("a*b*").states().size() == 3);

        section("3. Cas de l'AFD minimal incomplet (puits)");
        {
            Automaton d = AutomatonOps.determinize(RegexParser.toNfa("ab"));
            Minimizer.Result r = Minimizer.run(d, List.of());
            expect("ab : l'AFD de départ est incomplet (puits ajouté)", r.wasIncomplete());
            expect("ab : minimal complet = 4 états (q0,q1,q2,p)", r.minimalComplete().states().size() == 4);
            expect("ab : minimal partiel = 3 états", r.minimalPartial().states().size() == 3);
            expect("ab : le minimal complet a un puits", r.minimalHasSink());
            expect("ab : le minimal complet est complet", isComplete(r.minimalComplete(), r.alphabet()));
            Minimizer.Result r2 = Minimizer.run(d, List.of('c'));
            expect("ab sur {a,b,c} : minimal complet toujours 4 états, complet sur 3 lettres",
                    r2.minimalComplete().states().size() == 4 && isComplete(r2.minimalComplete(), r2.alphabet()));
            Minimizer.Result r3 = Minimizer.run(AutomatonOps.determinize(RegexParser.toNfa("(a+b)*")), List.of());
            expect("(a+b)* : 1 état, pas de puits", r3.minimalComplete().states().size() == 1 && !r3.minimalHasSink());
            Minimizer.Result r4 = Minimizer.run(AutomatonOps.determinize(RegexParser.toNfa("a*")), List.of('b'));
            expect("a* sur {a,b} : 2 états dont un puits ; partiel = 1 état",
                    r4.minimalComplete().states().size() == 2 && r4.minimalPartial().states().size() == 1);
            Minimizer.Result r5 = Minimizer.run(AutomatonOps.determinize(RegexParser.toNfa("∅")), List.of('a'));
            expect("∅ : un seul état non final", r5.minimalComplete().states().size() == 1 && r5.minimalComplete().finals().isEmpty());
            // AFD déjà complet avec états inaccessibles et équivalents
            Automaton x = new Automaton();
            x.setStart(0); x.setFinal(1, true); x.setFinal(2, true); x.addState(9);
            x.addTransition(0, 1, 'a'); x.addTransition(0, 2, 'b');
            x.addTransition(1, 1, 'a'); x.addTransition(1, 2, 'b');
            x.addTransition(2, 1, 'a'); x.addTransition(2, 2, 'b');
            x.addTransition(9, 0, 'a'); x.addTransition(9, 0, 'b');
            Minimizer.Result r6 = Minimizer.run(x, List.of());
            expect("AFD complet : états 1 et 2 fusionnés, 9 inaccessible signalé",
                    r6.minimalComplete().states().size() == 2 && r6.unreachable().equals(List.of(9)));
        }

        section("4. Syntaxe : cours (+ = union) et étendue (+ = un ou plus)");
        expect("COURS : (a+b)*ab ≡ (a|b)*ab",
                AutomatonOps.distinguishingWord(RegexParser.toNfa("(a+b)*ab"), RegexParser.toNfa("(a|b)*ab")) == null);
        expect("ETENDU : a+b = (un ou plus de a) puis b",
                AutomatonOps.accepts(RegexParser.toNfa("a+b", RegexParser.Syntax.ETENDU), "aab")
                        && !AutomatonOps.accepts(RegexParser.toNfa("a+b", RegexParser.Syntax.ETENDU), "b"));
        expect("COURS : a+b = union", AutomatonOps.accepts(RegexParser.toNfa("a+b"), "b")
                && !AutomatonOps.accepts(RegexParser.toNfa("a+b"), "ab"));
        expect("espaces ignorés : « ( a + b ) * a b »",
                AutomatonOps.distinguishingWord(RegexParser.toNfa("( a + b ) * a b"), RegexParser.toNfa("(a+b)*ab")) == null);
        String[] bad = {"(a", "a)", "*a", "a+", "+a", "a||b", "a|", "|a", "()", "(a+)", "a.b", "a[b]", "a\\b", "", "   ", "a^", "(*)", "a+*b"};
        for (String b : bad) {
            String err = RegexParser.check(b, RegexParser.Syntax.COURS);
            expect("rejeté « " + b + " » → " + err, err != null);
        }
        expect("ETENDU : « a+ » accepté", RegexParser.check("a+", RegexParser.Syntax.ETENDU) == null);
        expect("ETENDU : « +a » rejeté", RegexParser.check("+a", RegexParser.Syntax.ETENDU) != null);

        section("4b. Alphabet Σ imposé");
        {
            Set<Character> ab = new TreeSet<>(List.of('a', 'b'));
            expect("Σ={a,b} : (a+b)*ab accepté", RegexParser.check("(a+b)*ab", RegexParser.Syntax.COURS, ab) == null);
            String e1 = RegexParser.check("(a+c)*", RegexParser.Syntax.COURS, ab);
            expect("Σ={a,b} : c refusé → " + e1, e1 != null && e1.contains("Σ"));
            expect("Σ={a,b} : ε et ∅ toujours permis", RegexParser.check("ε+∅", RegexParser.Syntax.COURS, ab) == null);
            expect("Σ={0,1} : a refusé", RegexParser.check("a", RegexParser.Syntax.COURS, new TreeSet<>(List.of('0', '1'))) != null);
            Minimizer.Result r = Minimizer.run(AutomatonOps.determinize(RegexParser.toNfa("a*", RegexParser.Syntax.COURS, ab)), ab);
            expect("a* sur Σ={a,b} : puits ajouté, minimal complet = 2 états (complet sur Σ), partiel = 1",
                    r.minimalHasSink() && r.minimalComplete().states().size() == 2 && isComplete(r.minimalComplete(), r.alphabet())
                            && r.minimalPartial().states().size() == 1 && r.alphabet().equals(List.of('a', 'b')));
            Minimizer.Result r2 = Minimizer.run(AutomatonOps.determinize(RegexParser.toNfa("(a+b)*", RegexParser.Syntax.COURS, ab)), ab);
            expect("(a+b)* sur Σ={a,b} : 1 état, pas de puits", r2.minimalComplete().states().size() == 1 && !r2.minimalHasSink());
        }

        section("5. Expressions aléatoires (Thompson, sous-ensembles, Moore, Arden, élimination)");
        int n = 0;
        for (int i = 0; i < 400; i++) {
            String re = randomRegex(rnd, 4);
            if (!checkRegexQuiet(re)) { expect("aléatoire « " + re + " »", false); n++; if (n > 5) break; }
        }
        if (n == 0) expect("400 expressions aléatoires : tous les théorèmes vérifiés", true);

        section("6. Automates aléatoires (avec ε) vs simulation brute");
        n = 0;
        for (int i = 0; i < 300; i++) {
            Automaton a = randomAutomaton(rnd);
            if (!checkAutomatonQuiet(a)) { expect("automate aléatoire n°" + i, false); n++; if (n > 5) break; }
        }
        if (n == 0) expect("300 automates aléatoires : déterminisation, minimisation, Arden, élimination, trace", true);

        section("7. Équivalence");
        expect("(a+b)* ≡ (a*b*)*", AutomatonOps.distinguishingWord(RegexParser.toNfa("(a+b)*"), RegexParser.toNfa("(a*b*)*")) == null);
        String w = AutomatonOps.distinguishingWord(RegexParser.toNfa("a*b*"), RegexParser.toNfa("(a+b)*"));
        expect("a*b* ≢ (a+b)*  (mot : " + w + ")", w != null && !AutomatonOps.accepts(RegexParser.toNfa("a*b*"), w));
        expect("∅ ≢ ε", AutomatonOps.distinguishingWord(RegexParser.toNfa("∅"), RegexParser.toNfa("ε")) != null);
        expect("ε+a(a)* ≡ a*", AutomatonOps.distinguishingWord(RegexParser.toNfa("ε+aa*"), RegexParser.toNfa("a*")) == null);

        section("8. Intersection et union (automate produit)");
        String[][] fixedPairs = {
            {"(a+b)*a", "b(a+b)*"}, {"a*", "b*"}, {"(a+b)*ab", "(a+b)*ba"}, {"∅", "a*"}, {"ε", "a*"},
            {"(aa)*", "(aaa)*"}, {"(a+b)*", "a"}, {"a*b*", "b*a*"}, {"b*(ab*ab*)*", "a*(ba*ba*)*"}, {"a", "b"}
        };
        for (String[] p : fixedPairs) {
            for (ProductOps.Op op : ProductOps.Op.values()) {
                expect(p[0] + "  " + op.symbol + "  " + p[1], checkProduct(p[0], p[1], op));
            }
        }
        n = 0;
        for (int i = 0; i < 300; i++) {
            String r1 = randomRegex(rnd, 3), r2 = randomRegex(rnd, 3);
            ProductOps.Op op = i % 2 == 0 ? ProductOps.Op.INTERSECTION : ProductOps.Op.UNION;
            if (!checkProduct(r1, r2, op)) { expect("paire aléatoire « " + r1 + " » " + op.symbol + " « " + r2 + " »", false); n++; if (n > 5) break; }
        }
        if (n == 0) expect("300 paires de regex aléatoires (∩ et ∪) : produit, minimisation, Arden vérifiés", true);
        n = 0;
        for (int i = 0; i < 200; i++) {
            Automaton a = randomAutomaton(rnd), b = randomAutomaton(rnd);
            ProductOps.Op op = i % 2 == 0 ? ProductOps.Op.INTERSECTION : ProductOps.Op.UNION;
            if (!checkProductAutomata(a, b, op)) { expect("paire d'automates aléatoires n°" + i, false); n++; if (n > 5) break; }
        }
        if (n == 0) expect("200 paires d'automates aléatoires avec ε (∩ et ∪) vs simulation brute", true);

        if (skipped > 0) System.out.println("\n(oracle java.util.regex indécis sur " + skipped + " mots à cause du backtracking : ignorés ; les autres méthodes les ont vérifiés)");
        System.out.println(failures == 0 ? "\nTOUS LES TESTS PASSENT (" + checks + " vérifications, graine " + seed + ")"
                : "\n" + failures + " ÉCHEC(S) sur " + checks);
        if (failures > 0) System.exit(1);
    }

    // ------------------------------------------------------------ vérifications d'une regex
    private static void checkRegex(String re, String alphabet, int maxLen) {
        Automaton nfa = RegexParser.toNfa(re);
        Pattern p = Pattern.compile(toJava(re));
        List<String> ws = words(alphabet, maxLen);
        boolean ok = true;
        for (String x : ws) { Boolean e = jm(p, x); ok &= e == null || AutomatonOps.accepts(nfa, x) == e; }
        ok &= pipeline(re, nfa, ws, p);
        Automaton min = Minimizer.run(AutomatonOps.determinize(nfa), List.of()).minimalPartial();
        String arden = ArdenSolver.solve(min, "q").text();
        expect(String.format("%-18s NFA=%d  MIN=%d  Arden : %s   (%d mots)", re, nfa.states().size(), min.states().size(), arden, ws.size()), ok);
    }

    private static boolean checkRegexQuiet(String re) {
        Automaton nfa = RegexParser.toNfa(re);
        Pattern p = Pattern.compile(toJava(re));
        List<String> ws = words("ab", 7);
        boolean ok = true;
        for (String x : ws) { Boolean e = jm(p, x); ok &= e == null || AutomatonOps.accepts(nfa, x) == e; }
        return ok & pipeline(re, nfa, ws, p);
    }

    /** Toute la chaîne d'algorithmes à partir d'un AFN ; chaque résultat est comparé au motif Java. */
    private static boolean pipeline(String re, Automaton nfa, List<String> ws, Pattern p) {
        boolean ok = true;
        Automaton dfa = AutomatonOps.determinize(nfa);
        Minimizer.Result mr = Minimizer.run(dfa, List.of());
        ok &= bad(dfa.isDeterministic() && mr.minimalComplete().isDeterministic() && mr.minimalPartial().isDeterministic(), re, "déterminisme");
        ok &= bad(isComplete(mr.complete(), mr.alphabet()), re, "complétion");
        ok &= bad(isComplete(mr.minimalComplete(), mr.alphabet()), re, "minimal complet est complet");
        // minimalité : nombre d'états = Brzozowski ; complet = partiel (+1 si puits)
        int brz = brzozowski(nfa).states().size();
        ok &= bad(mr.minimalPartial().states().size() == brz, re, "minimalité (Moore " + mr.minimalPartial().states().size() + " ≠ Brzozowski " + brz + ")");
        ok &= bad(mr.minimalComplete().states().size() == mr.minimalPartial().states().size() + (mr.minimalHasSink() && !mr.minimalSink().equals(mr.minimalComplete().start()) ? 1 : 0), re, "complet = partiel + puits");
        // partition finale stable et correcte
        ok &= bad(mr.rounds().get(mr.rounds().size() - 1).stable(), re, "dernière étape stable");
        for (String x : ws) {
            Boolean exp = jm(p, x);
            if (exp == null) continue;
            ok &= bad(AutomatonOps.accepts(dfa, x) == exp, re, "AFD sur '" + x + "'");
            ok &= bad(AutomatonOps.accepts(mr.complete(), x) == exp, re, "AFD complet sur '" + x + "'");
            ok &= bad(AutomatonOps.accepts(mr.minimalComplete(), x) == exp, re, "minimal complet sur '" + x + "'");
            ok &= bad(AutomatonOps.accepts(mr.minimalPartial(), x) == exp, re, "minimal partiel sur '" + x + "'");
        }
        for (Automaton src : List.of(nfa, dfa, mr.minimalPartial())) ok &= checkRegexBuilders(re, src, ws, p);
        return ok;
    }

    private static boolean checkRegexBuilders(String re, Automaton src, List<String> ws, Pattern original) {
        boolean ok = true;
        String ar = ArdenSolver.solve(src, "q").text();
        String el = RegexBuilder.toRegex(src);
        for (String r2 : new String[]{ar, el}) {
            Automaton back = RegexParser.toNfa(r2);
            ok &= bad(AutomatonOps.distinguishingWord(src, back) == null, re, "équivalence de « " + r2 + " »");
            Pattern q = Pattern.compile(toJava(r2));
            for (String x : ws) {
                Boolean a = jm(q, x), b = jm(original, x);
                if (a != null && b != null) ok &= bad(a.equals(b), re, "« " + r2 + " » sur '" + x + "'");
            }
            // l'affichage étendu doit aussi se relire
            String ext = Re.show(parseBack(src, r2), true);
            ok &= bad(RegexParser.check(ext, RegexParser.Syntax.ETENDU) == null, re, "affichage étendu « " + ext + " »");
        }
        return ok;
    }

    private static Re parseBack(Automaton src, String text) {
        return text.equals("∅") ? Re.EMPTY : RegexBuilder.toRe(src);
    }

    private static boolean bad(boolean cond, String ctx, String what) {
        if (!cond) { System.out.println(" FAIL  [" + ctx + "] " + what); failures++; }
        checks++;
        return cond;
    }

    // ------------------------------------------------------------ automates aléatoires
    private static boolean checkAutomatonQuiet(Automaton a) {
        boolean ok = true;
        List<String> ws = words("ab", 6);
        Automaton dfa = AutomatonOps.determinize(a);
        Minimizer.Result mr = Minimizer.run(dfa, List.of());
        ArdenSolver.Result ar = ArdenSolver.solve(a, "q");
        Automaton back = RegexParser.toNfa(ar.text());
        Automaton back2 = RegexParser.toNfa(RegexBuilder.toRegex(a));
        ok &= bad(mr.minimalPartial().states().size() == brzozowski(a).states().size(), "auto", "minimalité");
        ok &= bad(AutomatonOps.distinguishingWord(a, back) == null, "auto", "Arden « " + ar.text() + " »");
        ok &= bad(AutomatonOps.distinguishingWord(a, back2) == null, "auto", "élimination");
        for (String x : ws) {
            boolean exp = brute(a, x);
            ok &= bad(AutomatonOps.accepts(a, x) == exp, "auto", "accepts '" + x + "'");
            ok &= bad(AutomatonOps.accepts(dfa, x) == exp, "auto", "AFD '" + x + "'");
            ok &= bad(AutomatonOps.accepts(mr.minimalComplete(), x) == exp, "auto", "min complet '" + x + "'");
            ok &= bad(AutomatonOps.accepts(mr.minimalPartial(), x) == exp, "auto", "min partiel '" + x + "'");
            ok &= bad(AutomatonOps.accepts(back, x) == exp, "auto", "regex Arden '" + x + "'");
            Simulator.Run run = Simulator.run(a, x, "q");
            ok &= bad(run.accepted() == exp, "auto", "trace '" + x + "'");
            if (exp) ok &= bad(replay(a, x, run), "auto", "chemin acceptant '" + x + "'");
            else ok &= bad(run.acceptingPath() == null && run.reason() != null, "auto", "rejet motivé '" + x + "'");
        }
        return ok;
    }

    private static boolean replay(Automaton a, String x, Simulator.Run run) {
        if (run.acceptingPath() == null) return false;
        int cur = a.start(), pos = 0;
        for (Simulator.Move mv : run.acceptingPath()) {
            Transition t = mv.transition();
            if (t.from() != cur) return false;
            if (!t.isEpsilon()) { if (pos >= x.length() || x.charAt(pos) != t.symbol()) return false; pos++; }
            if (mv.posAfter() != pos) return false;
            cur = t.to();
        }
        return pos == x.length() && a.isFinal(cur);
    }

    /** Simulation brute : parcours de toutes les configurations (état, position). */
    private static boolean brute(Automaton a, String w) {
        if (a.start() == null) return false;
        Set<String> seen = new HashSet<>();
        Deque<int[]> st = new ArrayDeque<>();
        st.push(new int[]{a.start(), 0});
        while (!st.isEmpty()) {
            int[] c = st.pop();
            if (!seen.add(c[0] + "," + c[1])) continue;
            if (c[1] == w.length() && a.isFinal(c[0])) return true;
            for (Transition t : a.transitions()) {
                if (t.from() != c[0]) continue;
                if (t.isEpsilon()) st.push(new int[]{t.to(), c[1]});
                else if (c[1] < w.length() && w.charAt(c[1]) == t.symbol()) st.push(new int[]{t.to(), c[1] + 1});
            }
        }
        return false;
    }

    private static Automaton randomAutomaton(Random r) {
        Automaton a = new Automaton();
        int n = 1 + r.nextInt(5);
        for (int i = 0; i < n; i++) a.addState(i);
        a.setStart(r.nextInt(n));
        for (int i = 0; i < n; i++) if (r.nextInt(3) == 0) a.setFinal(i, true);
        int m = r.nextInt(2 * n + 3);
        for (int k = 0; k < m; k++) {
            int c = r.nextInt(6);
            a.addTransition(r.nextInt(n), r.nextInt(n), c < 3 ? 'a' : c < 5 ? 'b' : Automaton.EPS);
        }
        return a;
    }

    // ------------------------------------------------------------ Brzozowski (indépendant de Moore)
    private static Automaton brzozowski(Automaton m) { return revDet(revDet(m)); }

    /**
     * Renverse l'automate (transitions inversées, finals ↔ initial) puis le déterminise par une
     * construction des sous-ensembles écrite ici, indépendamment de AutomatonOps.determinize.
     */
    private static Automaton revDet(Automaton m) {
        Automaton out = new Automaton();
        out.addState(0);
        out.setStart(0);
        if (m.start() == null) return out;
        Set<Character> sigma = new TreeSet<>(m.alphabet());
        Set<Integer> init = closureRev(m, new TreeSet<>(m.finals()));
        Map<Set<Integer>, Integer> ids = new HashMap<>();
        List<Set<Integer>> sets = new ArrayList<>();
        ids.put(init, 0);
        sets.add(init);
        for (int i = 0; i < sets.size(); i++) {
            Set<Integer> cur = sets.get(i);
            out.addState(i);
            if (cur.contains(m.start())) out.setFinal(i, true);
            for (char c : sigma) {
                Set<Integer> nx = new TreeSet<>();
                for (Transition t : m.transitions()) if (t.symbol() == c && cur.contains(t.to())) nx.add(t.from());
                nx = closureRev(m, nx);
                if (nx.isEmpty()) continue;
                Integer id = ids.get(nx);
                if (id == null) { id = sets.size(); ids.put(nx, id); sets.add(nx); }
                out.addTransition(i, id, c);
            }
        }
        return out;
    }

    /** ε-fermeture dans l'automate renversé (on remonte les transitions ε). */
    private static Set<Integer> closureRev(Automaton m, Set<Integer> from) {
        Set<Integer> res = new TreeSet<>(from);
        Deque<Integer> st = new ArrayDeque<>(from);
        while (!st.isEmpty()) {
            int u = st.pop();
            for (Transition t : m.transitions()) if (t.isEpsilon() && t.to() == u && res.add(t.from())) st.push(t.from());
        }
        return res;
    }

    // ------------------------------------------------------------ outils
    private static boolean isComplete(Automaton d, List<Character> sigma) {
        if (d.isEmpty()) return true;
        for (int s : d.states()) for (char a : sigma) if (d.targets(s, a).size() != 1) return false;
        return true;
    }

    private static int minSize(String re) {
        return Minimizer.run(AutomatonOps.determinize(RegexParser.toNfa(re)), List.of()).minimalPartial().states().size();
    }

    private static Automaton minComplete(String re) {
        return Minimizer.run(AutomatonOps.determinize(RegexParser.toNfa(re)), List.of()).minimalComplete();
    }

    /** Syntaxe du cours → syntaxe java.util.regex. */
    private static String toJava(String re) {
        return re.replace("+", "|").replace("ε", "(?:)").replace("λ", "(?:)").replace("∅", "(?!)");
    }

    private static String randomRegex(Random r, int depth) {
        int k = depth <= 0 ? r.nextInt(3) : r.nextInt(9);
        switch (k) {
            case 0: case 1: return r.nextBoolean() ? "a" : "b";
            case 2: return r.nextInt(8) == 0 ? "ε" : (r.nextInt(10) == 0 ? "∅" : "a");
            case 3: case 4: return randomRegex(r, depth - 1) + randomRegex(r, depth - 1);
            case 5: case 6: return "(" + randomRegex(r, depth - 1) + "+" + randomRegex(r, depth - 1) + ")";
            case 7: return "(" + randomRegex(r, depth - 1) + ")*";
            default: return "(" + randomRegex(r, depth - 1) + ")?";
        }
    }

    private static List<String> words(String alphabet, int maxLen) {
        List<String> res = new ArrayList<>();
        res.add("");
        int from = 0;
        for (int len = 1; len <= maxLen; len++) {
            int to = res.size();
            for (int i = from; i < to; i++) for (char c : alphabet.toCharArray()) res.add(res.get(i) + c);
            from = to;
        }
        return res;
    }

    // ------------------------------------------------------------ intersection / union
    private static boolean checkProduct(String r1, String r2, ProductOps.Op op) {
        try {
            Automaton n1 = RegexParser.toNfa(r1), n2 = RegexParser.toNfa(r2);
            Set<Character> sigma = new TreeSet<>(List.of('a', 'b', 'c'));
            ProductOps.Result res = ProductOps.run(n1, n2, op, sigma);
            // oracle indépendant : java.util.regex (∩ par assertion avant, ∪ par alternance)
            String j1 = toJava(r1), j2 = toJava(r2);
            Pattern p = Pattern.compile(op == ProductOps.Op.INTERSECTION
                    ? "(?=(?:" + j1 + ")$)(?:" + j2 + ")" : "(?:" + j1 + ")|(?:" + j2 + ")");
            boolean ok = true;
            for (String w : words("abc", 5)) {
                Boolean expected = jm(p, w);
                if (expected == null) continue;
                ok &= bad(AutomatonOps.accepts(res.product(), w) == expected, r1 + op.symbol + r2, "produit sur « " + w + " »");
                ok &= bad(AutomatonOps.accepts(res.minimal(), w) == expected, r1 + op.symbol + r2, "minimal complet sur « " + w + " »");
                ok &= bad(AutomatonOps.accepts(res.minimalPartial(), w) == expected, r1 + op.symbol + r2, "minimal sans puits sur « " + w + " »");
                if (!ok) break;
            }
            return ok & checkProductShape(res, n1, n2, sigma, r1 + op.symbol + r2);
        } catch (RuntimeException e) {
            System.out.println("   exception : " + e);
            return false;
        }
    }

    private static boolean checkProductAutomata(Automaton a, Automaton b, ProductOps.Op op) {
        if (a.start() == null || b.start() == null) return true;
        try {
            Set<Character> sigma = new TreeSet<>(List.of('a', 'b', 'c'));
            ProductOps.Result res = ProductOps.run(a, b, op, sigma);
            boolean ok = true;
            for (String w : words("abc", 5)) {
                boolean expected = op.combine(brute(a, w), brute(b, w));
                ok &= bad(AutomatonOps.accepts(res.product(), w) == expected, "automates", "produit sur « " + w + " »");
                ok &= bad(AutomatonOps.accepts(res.minimal(), w) == expected, "automates", "minimal sur « " + w + " »");
                if (!ok) break;
            }
            return ok & checkProductShape(res, a, b, sigma, "automates");
        } catch (RuntimeException e) {
            System.out.println("   exception : " + e);
            return false;
        }
    }

    private static boolean checkProductShape(ProductOps.Result res, Automaton a, Automaton b, Set<Character> sigma, String ctx) {
        boolean ok = true;
        Automaton prod = res.product();
        ok &= bad(prod.isDeterministic(), ctx, "le produit doit être déterministe");
        ok &= bad(isComplete(prod, new ArrayList<>(sigma)), ctx, "le produit doit être complet sur Σ");
        ok &= bad(res.verification().ok(), ctx, "auto-vérification du produit");
        ok &= bad(res.first().complete().states().size() * res.second().complete().states().size() >= prod.states().size(), ctx, "trop de couples");
        ok &= bad(AutomatonOps.distinguishingWord(res.minimalPartial(), prod) == null, ctx, "minimal ≢ produit");
        // Arden sur l'AFD minimal : l'expression obtenue doit reconnaître le même langage
        String text = ArdenSolver.solve(OperationsPlain.plain(res.minimalPartial()), "q").text();
        ok &= bad(AutomatonOps.distinguishingWord(prod, RegexParser.toNfa(text)) == null, ctx, "Arden : « " + text + " » ≢ produit");
        return ok;
    }

    /** Copie sans noms personnalisés (comme l'interface avant d'appliquer Arden). */
    private static final class OperationsPlain {
        static Automaton plain(Automaton m) {
            Automaton p = new Automaton();
            for (int s : m.states()) p.addState(s);
            for (Transition t : m.transitions()) p.addTransition(t.from(), t.to(), t.symbol());
            p.setStart(m.start());
            for (int f : m.finals()) p.setFinal(f, true);
            return p;
        }
    }

    private static void section(String t) { System.out.println("\n== " + t); }

    private static void expect(String msg, boolean cond) {
        System.out.println((cond ? "  OK  " : " FAIL ") + msg);
        checks++;
        if (!cond) failures++;
    }
}
