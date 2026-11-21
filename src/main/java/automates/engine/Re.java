package automates.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Expression régulière sous forme d'arbre, avec des constructeurs qui simplifient
 * (∅, ε, doublons) sans jamais changer le langage. Sert à écrire les résultats
 * des algorithmes (Arden, élimination d'états) de façon lisible.
 *
 * Deux styles d'affichage : « cours » (union = +, ε écrit explicitement)
 * et « étendu » (union = |, optionnel écrit avec ?).
 */
public sealed interface Re permits Re.Empty, Re.Eps, Re.Sym, Re.Union, Re.Concat, Re.Star {

    record Empty() implements Re {}
    record Eps() implements Re {}
    record Sym(char c) implements Re {}
    record Union(List<Re> items) implements Re {}
    record Concat(List<Re> items) implements Re {}
    record Star(Re inner) implements Re {}

    Re EMPTY = new Empty();
    Re EPS = new Eps();

    static Re sym(char c) { return new Sym(c); }

    // ------------------------------------------------------------ propriétés
    /** ε appartient-il au langage ? */
    static boolean nullable(Re r) {
        if (r instanceof Empty) return false;
        if (r instanceof Eps) return true;
        if (r instanceof Sym) return false;
        if (r instanceof Star) return true;
        if (r instanceof Union u) return u.items().stream().anyMatch(Re::nullable);
        Concat c = (Concat) r;
        return c.items().stream().allMatch(Re::nullable);
    }

    // ------------------------------------------------------------ constructeurs simplifiants
    static Re union(Re a, Re b) {
        if (a == null) return b;
        if (b == null) return a;
        List<Re> items = new ArrayList<>();
        addUnion(items, a);
        addUnion(items, b);
        // ε est inutile si un autre terme contient déjà le mot vide
        if (items.stream().anyMatch(x -> !(x instanceof Eps) && nullable(x))) items.removeIf(x -> x instanceof Eps);
        if (items.isEmpty()) return EMPTY;
        if (items.size() == 1) return items.get(0);
        return new Union(items);
    }

    private static void addUnion(List<Re> items, Re r) {
        if (r instanceof Empty) return;
        if (r instanceof Union u) { for (Re x : u.items()) addUnion(items, x); return; }
        if (!items.contains(r)) items.add(r);
    }

    static Re concat(Re a, Re b) {
        if (a == null || b == null) return null;          // null = ∅ (« absent »)
        if (a instanceof Empty || b instanceof Empty) return EMPTY;
        if (a instanceof Eps) return b;
        if (b instanceof Eps) return a;
        List<Re> items = new ArrayList<>();
        addConcat(items, a);
        addConcat(items, b);
        return new Concat(items);
    }

    private static void addConcat(List<Re> items, Re r) {
        if (r instanceof Concat c) items.addAll(c.items()); else items.add(r);
    }

    static Re star(Re x) {
        if (x == null || x instanceof Empty || x instanceof Eps) return EPS;   // ∅* = ε* = ε
        if (x instanceof Star) return x;                                        // (y*)* = y*
        if (x instanceof Union u && u.items().stream().anyMatch(i -> i instanceof Eps)) {
            List<Re> rest = new ArrayList<>(u.items());
            rest.removeIf(i -> i instanceof Eps);                               // (ε + y)* = y*
            return star(rest.size() == 1 ? rest.get(0) : new Union(rest));
        }
        return new Star(x);
    }

    // ------------------------------------------------------------ affichage
    /** Style « cours » : union +, mot vide ε, vide ∅. Relu par le parseur en syntaxe COURS. */
    static String show(Re r) { return show(r, false); }

    /** @param extended true → union « | » et optionnel « ? » (syntaxe étendue) */
    static String show(Re r, boolean extended) {
        if (r instanceof Empty) return "∅";
        if (r instanceof Eps) return "ε";
        if (r instanceof Sym s) return String.valueOf(s.c());
        if (r instanceof Star s) return atom(s.inner(), extended) + "*";
        if (r instanceof Concat c) {
            StringBuilder sb = new StringBuilder();
            for (Re x : c.items()) sb.append(x instanceof Union ? "(" + show(x, extended) + ")" : show(x, extended));
            return sb.toString();
        }
        Union u = (Union) r;
        if (extended) {
            boolean hasEps = u.items().stream().anyMatch(x -> x instanceof Eps);
            if (hasEps) {
                List<Re> rest = new ArrayList<>(u.items());
                rest.removeIf(x -> x instanceof Eps);
                Re r2 = rest.size() == 1 ? rest.get(0) : new Union(rest);
                return atom(r2, true) + "?";
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Re x : u.items()) {
            if (sb.length() > 0) sb.append(extended ? "|" : "+");
            sb.append(show(x, extended));
        }
        return sb.toString();
    }

    private static String atom(Re r, boolean extended) {
        boolean simple = r instanceof Sym || r instanceof Eps || r instanceof Empty;
        return simple ? show(r, extended) : "(" + show(r, extended) + ")";
    }

    /** Affichage « cours » avec le point de concaténation visible : a·a*·b (pour détailler les calculs). */
    static String showDot(Re r) {
        if (r instanceof Empty) return "∅";
        if (r instanceof Eps) return "ε";
        if (r instanceof Sym s) return String.valueOf(s.c());
        if (r instanceof Star s) return atomDot(s.inner()) + "*";
        if (r instanceof Concat c) {
            StringBuilder sb = new StringBuilder();
            for (Re x : c.items()) {
                if (sb.length() > 0) sb.append("·");
                sb.append(x instanceof Union ? "(" + showDot(x) + ")" : showDot(x));
            }
            return sb.toString();
        }
        Union u = (Union) r;
        StringBuilder sb = new StringBuilder();
        for (Re x : u.items()) { if (sb.length() > 0) sb.append("+"); sb.append(showDot(x)); }
        return sb.toString();
    }

    private static String atomDot(Re r) {
        boolean simple = r instanceof Sym || r instanceof Eps || r instanceof Empty;
        return simple ? showDot(r) : "(" + showDot(r) + ")";
    }

    /** Représentation d'un coefficient devant une variable : « (a+b) », « ab », ou « » pour ε. */
    static String coefficient(Re r) {
        if (r instanceof Eps) return "";
        return r instanceof Union ? "(" + show(r) + ")" : show(r);
    }

    static boolean same(Re a, Re b) { return Objects.equals(a, b); }
}
