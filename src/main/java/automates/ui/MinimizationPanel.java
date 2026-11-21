package automates.ui;

import automates.engine.Minimizer;
import automates.engine.Minimizer.Round;
import automates.model.Automaton;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.function.Consumer;

/**
 * Minimisation d'un AFD rédigée comme dans le cours :
 *   - l'AFD est-il complet ? (sinon : puits) ;
 *   - Π0 = {états finals, états non finals} ;
 *   - Π1, Π2, … : on sépare les états qui n'arrivent pas dans les mêmes classes ;
 *   - on s'arrête quand Π(k+1) = Πk ; chaque classe devient un état de l'AFD minimal.
 */
final class MinimizationPanel {

    private MinimizationPanel() {}

    static VBox build(Automaton dfa, Collection<Character> extraAlphabet, Consumer<Automaton> openInEditor) {
        Minimizer.Result r = Minimizer.run(dfa, extraAlphabet, "q");
        VBox root = new VBox(12);
        Automaton c = r.complete();
        String alpha = alphabetText(r.alphabet());

        root.getChildren().add(UiUtil.heading("Minimiser l’AFD"));
        if (dfa.start() == null) {
            root.getChildren().add(UiUtil.muted("Pas d’état initial : rien à minimiser."));
            return root;
        }

        // ------------------------------------------------ vérification : l'AFD est-il complet ?
        List<Node> prep = new ArrayList<>();
        prep.add(wrap(new Label("Alphabet : Σ = " + alpha)));
        if (!r.unreachable().isEmpty()) {
            StringJoiner j = new StringJoiner(", ");
            for (int s : r.unreachable()) j.add(dfa.name(s, "q"));
            prep.add(wrap(new Label("États inaccessibles depuis l’état initial (supprimés) : " + j)));
        }
        if (r.wasIncomplete()) {
            List<String> missing = new ArrayList<>();
            for (Automaton.Transition t : c.transitions()) {
                if (r.sink() != null && t.to() == r.sink() && t.from() != r.sink())
                    missing.add("δ(" + c.name(t.from(), "q") + ", " + t.symbol() + ")");
            }
            Label note = new Label("L’AFD est INCOMPLET : " + String.join(", ", missing)
                    + " n’est (ne sont) pas défini(es). On le complète avec un état PUITS p (non final, qui boucle sur "
                    + alpha.replace("{", "").replace("}", "") + ") vers lequel on envoie toutes les transitions manquantes. "
                    + "Le langage reconnu ne change pas.");
            VBox nb = new VBox(wrap(note));
            nb.getStyleClass().add("sink-note");
            prep.add(nb);
        } else {
            prep.add(wrap(new Label("L’AFD est complet : toutes les transitions sont définies, pas besoin de puits.")));
        }
        root.getChildren().add(UiUtil.stepBox("Vérification", null, prep.toArray(new Node[0])));
        if (r.wasIncomplete()) {
            root.getChildren().add(UiUtil.automatonBlock("AFD complet (avec le puits p)", c, "q", null));
        }

        // ------------------------------------------------ Π0
        Round first = r.rounds().get(0);
        List<String> l0 = new ArrayList<>();
        StringJoiner fin = new StringJoiner(", "), non = new StringJoiner(", ");
        for (int s : c.states()) (c.isFinal(s) ? fin : non).add(c.name(s, "q"));
        l0.add("États finals      : " + (fin.length() == 0 ? "aucun" : "{" + fin + "}"));
        l0.add("États non finals  : " + (non.length() == 0 ? "aucun" : "{" + non + "}"));
        l0.add("");
        l0.add("Π0 = " + partition(c, first.partition()));
        root.getChildren().add(UiUtil.stepBox("Π0 : on sépare les états finals des états non finals", null, UiUtil.monoLines(l0)));

        // ------------------------------------------------ Π1, Π2, …
        for (Round rd : r.rounds()) root.getChildren().add(roundBox(r, rd));

        // ------------------------------------------------ résultat
        Automaton full = r.minimalComplete();
        List<String> lres = new ArrayList<>();
        List<List<Integer>> classes = r.classes();
        for (int i = 0; i < classes.size(); i++) {
            List<Integer> cl = classes.get(i);
            String nm = full.name(i, "q");
            lres.add(set(c, cl) + "  →  état " + nm + (cl.size() > 1 ? "      (" + names(c, cl, " et ") + " sont équivalents : fusionnés)" : ""));
        }
        if (classes.stream().noneMatch(cl -> cl.size() > 1)) {
            lres.add("");
            lres.add("Aucun état n’a été fusionné : l’AFD était déjà minimal.");
        }
        VBox res = new VBox(6, UiUtil.monoLines(lres));
        root.getChildren().add(UiUtil.stepBox("États de l’AFD minimal", "Chaque classe de la dernière partition devient un état.", res));

        root.getChildren().add(UiUtil.automatonBlock("AFD minimal" + (r.minimalHasSink() ? " complet (avec le puits p)" : ""),
                full, "q", openInEditor == null ? null : () -> openInEditor.accept(full.copy())));

        if (r.minimalHasSink()) {
            Label n1 = new Label("L’AFD minimal contient l’état puits p : c’est le cas de l’AFD minimal INCOMPLET. "
                    + "Dans l’écriture usuelle on ne dessine pas le puits : on supprime p et les transitions qui y arrivent. "
                    + "Une transition absente signifie alors « le mot est rejeté ». "
                    + "Les deux automates reconnaissent le même langage.");
            VBox nb = new VBox(wrap(n1));
            nb.getStyleClass().add("sink-note");
            root.getChildren().add(nb);
            Automaton part = r.minimalPartial();
            root.getChildren().add(UiUtil.automatonBlock("AFD minimal sans le puits (incomplet)",
                    part, "q", openInEditor == null ? null : () -> openInEditor.accept(part.copy())));
        } else {
            VBox nb = new VBox(wrap(new Label("L’AFD minimal est complet : il n’y a pas d’état puits.")));
            nb.getStyleClass().add("final-note");
            root.getChildren().add(nb);
        }
        return root;
    }

    // ------------------------------------------------------------ calcul de Π(k+1) à partir de Πk
    private static VBox roundBox(Minimizer.Result r, Round rd) {
        Automaton c = r.complete();
        int k = rd.index();
        List<String> lines = new ArrayList<>();
        List<String> singles = new ArrayList<>();

        for (List<Integer> cl : rd.partition()) {
            if (cl.size() == 1) { singles.add(set(c, cl)); continue; }
            lines.add("Classe " + set(c, cl) + " :");
            for (int s : cl) {
                StringBuilder sb = new StringBuilder("    " + c.name(s, "q") + " :");
                for (char a : r.alphabet()) {
                    int t = c.targets(s, a).iterator().next();
                    sb.append("   ").append(a).append(" → ").append(c.name(t, "q")).append(" ∈ ")
                      .append(set(c, rd.partition().get(rd.classOf().get(t))));
                }
                lines.add(sb.toString());
            }
            List<List<Integer>> parts = new ArrayList<>();
            for (List<Integer> nx : rd.refined()) if (cl.containsAll(nx)) parts.add(nx);
            if (parts.size() == 1) {
                lines.add("    ⟹ même comportement : la classe reste intacte");
            } else {
                StringJoiner sj = new StringJoiner("  |  ");
                for (List<Integer> pp : parts) sj.add(set(c, pp));
                lines.add("    ⟹ comportements différents : on sépare en " + sj);
            }
            lines.add("");
        }
        if (!singles.isEmpty()) {
            lines.add("Classes à un seul état (rien à séparer) : " + String.join(", ", singles));
            lines.add("");
        }
        lines.add("Π" + (k + 1) + " = " + partition(c, rd.refined()));
        lines.add(rd.stable()
                ? "Π" + (k + 1) + " = Π" + k + "   ⟹   la partition ne change plus : on s’arrête."
                : "Π" + (k + 1) + " ≠ Π" + k + "   ⟹   on continue avec Π" + (k + 1) + ".");

        return UiUtil.stepBox("Calcul de Π" + (k + 1) + " à partir de Π" + k,
                "Pour chaque lettre, on regarde dans quelle classe de Π" + k + " arrive chaque état. "
                        + "Deux états restent ensemble seulement s’ils arrivent dans les mêmes classes.",
                UiUtil.monoLines(lines));
    }

    // ------------------------------------------------------------ texte
    private static Label wrap(Label l) { l.setWrapText(true); return l; }

    /** {{A, D}, {B, C, E}} */
    private static String partition(Automaton c, List<List<Integer>> part) {
        StringJoiner j = new StringJoiner(", ", "{", "}");
        for (List<Integer> cl : part) j.add(set(c, cl));
        return j.toString();
    }

    private static String set(Automaton c, List<Integer> ids) { return "{" + names(c, ids, ", ") + "}"; }

    private static String names(Automaton c, List<Integer> ids, String sep) {
        StringJoiner j = new StringJoiner(sep);
        for (int s : ids) j.add(c.name(s, "q"));
        return j.toString();
    }

    private static String alphabetText(List<Character> a) {
        StringJoiner j = new StringJoiner(", ", "{", "}");
        for (char x : a) j.add(String.valueOf(x));
        return j.toString();
    }
}
