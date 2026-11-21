package automates.ui;

import java.util.*;

/**
 * Alphabet Σ commun à toute l'application. Il doit être défini AVANT de saisir une regex
 * ou de dessiner un automate : regex, transitions, mots testés et complétion (puits) s'y réfèrent.
 */
final class AlphabetModel {

    private final TreeSet<Character> symbols = new TreeSet<>();
    private boolean defined = false;
    private final List<Runnable> listeners = new ArrayList<>();

    boolean isDefined() { return defined; }

    /** Copie non modifiable, triée. */
    Set<Character> symbols() { return Collections.unmodifiableSet(new TreeSet<>(symbols)); }

    void addListener(Runnable r) { listeners.add(r); }

    /** « Σ = {a, b} ». */
    String display() {
        StringJoiner j = new StringJoiner(", ", "{", "}");
        for (char c : symbols) j.add(String.valueOf(c));
        return "Σ = " + j;
    }

    /**
     * Définit Σ à partir d'un texte comme « a,b », « {0, 1} », « a b c » ou « 01 ».
     * Symboles autorisés : lettres non accentuées A–Z, a–z (majuscules et minuscules distinctes) et chiffres 0–9.
     * @throws IllegalArgumentException si le texte est vide ou contient autre chose
     */
    static boolean isAllowed(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    void set(String text) {
        TreeSet<Character> parsed = parse(text);
        boolean changed = !defined || !parsed.equals(symbols);
        symbols.clear();
        symbols.addAll(parsed);
        defined = true;
        if (changed) listeners.forEach(Runnable::run);
    }

    static TreeSet<Character> parse(String text) {
        String t = text == null ? "" : text.trim();
        if (t.startsWith("{") && t.endsWith("}")) t = t.substring(1, t.length() - 1);
        TreeSet<Character> r = new TreeSet<>();
        for (char c : t.toCharArray()) {
            if (c == ',' || c == ';' || Character.isWhitespace(c)) continue;
            if (c == 'ε' || c == 'λ' || c == '!' )
                throw new IllegalArgumentException("« " + c + " » désigne le mot vide : ce n’est pas un symbole de l’alphabet");
            if (!isAllowed(c))
                throw new IllegalArgumentException("caractère « " + c + " » interdit dans l’alphabet : seuls sont permis les lettres A–Z, a–z et les chiffres 0–9, séparés par des virgules");
            r.add(c);
        }
        if (r.isEmpty()) throw new IllegalArgumentException("l’alphabet est vide : saisissez au moins un symbole (ex. a,b)");
        return r;
    }

    /** Remplace les lettres a, b, c d'un exemple par les premiers symboles de Σ ; null si Σ est trop petit. */
    String adapt(String template) {
        List<Character> s = new ArrayList<>(symbols);
        int need = 0;
        for (char c : template.toCharArray()) if (c >= 'a' && c <= 'c') need = Math.max(need, c - 'a' + 1);
        if (s.size() < need) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : template.toCharArray()) sb.append(c >= 'a' && c <= 'c' ? s.get(c - 'a') : c);
        return sb.toString();
    }

    /** Mots d'essai construits sur Σ (λ = mot vide). */
    String sampleWords() {
        List<Character> s = new ArrayList<>(symbols);
        char x = s.get(0);
        if (s.size() == 1) return "" + x + " " + x + x + " " + x + x + x + " λ";
        char y = s.get(1);
        return "" + x + y + " " + x + x + y + " " + x + y + x + y + " " + y + x + " " + x + y + y + " λ";
    }
}
