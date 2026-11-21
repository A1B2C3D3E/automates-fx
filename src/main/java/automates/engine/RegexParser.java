package automates.engine;

import automates.model.Automaton;

import java.util.StringJoiner;
import java.util.TreeSet;

/**
 * Expression régulière → AFN-ε par la construction de Thompson.
 *
 * Deux syntaxes :
 *  - COURS  : union « + » (ou « | »), étoile « * », concaténation implicite,
 *             ε (ou λ, !) mot vide, ∅ langage vide, « ? » optionnel. C'est la notation de JFLAP et du cours.
 *  - ETENDU : union « | » uniquement ; « + » devient « un ou plus » (comme en programmation).
 *
 * Les symboles de l'alphabet sont les lettres A–Z, a–z (non accentuées) et les chiffres 0–9. Tout autre caractère est refusé
 * avec un message précis (et la position de l'erreur).
 */
public final class RegexParser {

    public enum Syntax { COURS, ETENDU }

    /** Erreur de syntaxe avec la position (0 = premier caractère) du problème. */
    public static final class ParseException extends IllegalArgumentException {
        private final int position;
        public ParseException(String message, int position) {
            super(message);
            this.position = position;
        }
        public int position() { return position; }
    }

    private record Frag(int start, int end) {}

    private final String s;
    private final Syntax syntax;
    private final java.util.Set<Character> sigma;   // alphabet imposé (null = pas de contrainte)
    private int i = 0;
    private final Automaton nfa = new Automaton();
    private int counter = 0;

    private RegexParser(String s, Syntax syntax, java.util.Set<Character> sigma) { this.s = s; this.syntax = syntax; this.sigma = sigma; }

    public static Automaton toNfa(String regex) { return toNfa(regex, Syntax.COURS); }

    public static Automaton toNfa(String regex, Syntax syntax) { return toNfa(regex, syntax, null); }

    /** @param sigma alphabet Σ imposé : un symbole absent de Σ est refusé (null = aucune contrainte) */
    public static Automaton toNfa(String regex, Syntax syntax, java.util.Set<Character> sigma) {
        RegexParser p = new RegexParser(regex, syntax, sigma);
        if (p.peek() == END) throw new ParseException("l’expression est vide (pour le mot vide, écrivez ε ; pour le langage vide, ∅)", 0);
        Frag r = p.union();
        char rest = p.peek();
        if (rest == ')') throw new ParseException("parenthèse « ) » sans « ( » correspondante", p.i);
        if (rest != END) throw p.unexpected(rest);
        p.nfa.setStart(r.start());
        p.nfa.setFinal(r.end(), true);
        return p.nfa;
    }

    /** Message d'erreur (ou null si correcte), pour la vérification en direct dans l'interface. */
    public static String check(String regex, Syntax syntax) { return check(regex, syntax, null); }

    public static String check(String regex, Syntax syntax, java.util.Set<Character> sigma) {
        try { toNfa(regex, syntax, sigma); return null; }
        catch (ParseException e) { return "position " + (e.position() + 1) + " : " + e.getMessage(); }
        catch (RuntimeException e) { return e.getMessage(); }
    }

    public static boolean isEps(char c) { return c == 'ε' || c == 'λ' || c == '!'; }

    // ------------------------------------------------------------ outils
    private static final char END = '\uFFFF';

    private int newState() { int id = counter++; nfa.addState(id); return id; }
    private void eps(int a, int b) { nfa.addTransition(a, b, Automaton.EPS); }

    /** Caractère courant (espaces ignorés), END en fin de texte. */
    private char peek() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i < s.length() ? s.charAt(i) : END;
    }

    private boolean isUnionOp(char c) { return c == '|' || (c == '+' && syntax == Syntax.COURS); }
    private boolean isPostfixOp(char c) { return c == '*' || c == '?' || (c == '+' && syntax == Syntax.ETENDU); }
    private boolean isSymbol(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
    private boolean startsOperand(char c) { return c == '(' || isEps(c) || c == '∅' || isSymbol(c); }

    // ------------------------------------------------------------ grammaire
    // union   := concat (UNION concat)*
    // concat  := postfix+
    // postfix := atom ('*' | '?' | '+' en syntaxe étendue)*
    // atom    := '(' union ')' | ε | ∅ | symbole
    private Frag union() {
        Frag t = concat();
        while (isUnionOp(peek())) {
            char op = s.charAt(i);
            i++;
            char nx = peek();
            if (!startsOperand(nx)) throw missingOperandAfter(op);
            Frag u = concat();
            int a = newState(), b = newState();
            eps(a, t.start()); eps(a, u.start());
            eps(t.end(), b);   eps(u.end(), b);
            t = new Frag(a, b);
        }
        return t;
    }

    private ParseException missingOperandAfter(char op) {
        int pos = i - 1;
        char nx = peek();
        String after = nx == END ? "la fin de l’expression"
                : (nx == ')' ? "« ) »" : "« " + nx + " »");
        if (op == '+' && syntax == Syntax.COURS)
            return new ParseException("« + » est l’union : il faut une expression à sa droite (trouvé : " + after
                    + "). Pour « un ou plus de a », écrivez aa*", pos);
        return new ParseException("« " + op + " » est l’union : il faut une expression à sa droite (trouvé : " + after + ")", pos);
    }

    private Frag concat() {
        char c = peek();
        if (!startsOperand(c)) throw unexpected(c);
        Frag t = null;
        while (startsOperand(peek())) {
            Frag f = postfix();
            if (t != null) {
                eps(t.end(), f.start());
                t = new Frag(t.start(), f.end());
            } else {
                t = f;
            }
        }
        return t;
    }

    /** Erreur : opérande attendue mais autre chose trouvé. */
    private ParseException unexpected(char c) {
        int pos = i;
        if (c == END) return new ParseException("l’expression se termine trop tôt : il manque une opérande", Math.max(0, s.length() - 1));
        if (c == ')') {
            boolean empty = pos > 0 && lastNonSpaceBefore(pos) == '(';
            return new ParseException(empty ? "parenthèses vides « () » : mettez une expression à l’intérieur (ε pour le mot vide)"
                    : "il manque une opérande avant « ) »", pos);
        }
        if (c == '*' || c == '?' || c == '+')
            return new ParseException("« " + c + " » ne s’applique à rien : il faut un symbole ou un groupe avant lui", pos);
        if (c == '|')
            return new ParseException("« | » est l’union : il faut une expression à sa gauche", pos);
        return new ParseException("caractère « " + c + " » non autorisé. Permis : lettres A–Z a–z, chiffres 0–9, + | * ? ( ) ε ∅", pos);
    }

    private char lastNonSpaceBefore(int pos) {
        for (int k = pos - 1; k >= 0; k--) if (!Character.isWhitespace(s.charAt(k))) return s.charAt(k);
        return END;
    }

    private Frag postfix() {
        Frag f = atom();
        while (isPostfixOp(peek())) {
            char op = s.charAt(i++);
            int a = newState(), b = newState();
            eps(a, f.start());
            if (op != '+') eps(a, b);               // * et ? : on peut ne rien lire
            if (op != '?') eps(f.end(), f.start()); // * et + : on peut recommencer
            eps(f.end(), b);
            f = new Frag(a, b);
        }
        return f;
    }

    private Frag atom() {
        char c = peek();
        int pos = i;
        i++;
        if (c == '(') {
            if (peek() == END) throw new ParseException("parenthèse « ( » jamais fermée", pos);
            Frag t = union();
            char close = peek();
            if (close == END) throw new ParseException("parenthèse « ( » jamais fermée", pos);
            if (close != ')') throw unexpected(close);
            i++;
            return t;
        }
        if (isEps(c)) {
            int a = newState(), b = newState();
            eps(a, b);
            return new Frag(a, b);
        }
        if (c == '∅') {
            int a = newState(), b = newState();     // aucun chemin de a vers b
            return new Frag(a, b);
        }
        if (!isSymbol(c)) { i--; throw unexpected(c); }
        if (sigma != null && !sigma.contains(c)) {
            StringJoiner j = new StringJoiner(", ", "{", "}");
            for (char x : new TreeSet<>(sigma)) j.add(String.valueOf(x));
            throw new ParseException("le symbole « " + c + " » n’appartient pas à l’alphabet Σ = " + j
                    + " (modifiez Σ en haut de la fenêtre ou corrigez l’expression)", pos);
        }
        int a = newState(), b = newState();
        nfa.addTransition(a, b, c);
        return new Frag(a, b);
    }
}
