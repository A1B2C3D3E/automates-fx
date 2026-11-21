package automates.io;

import automates.model.Automaton;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Sauvegarde / chargement d'un automate et de la position de ses états.
 * Format texte :
 *   AUTOMATON 1
 *   START 0
 *   FINAL 2
 *   STATE 0 60.0 70.0
 *   TRANS 0 1 a        (ε pour une transition spontanée)
 */
public final class AutomatonIO {

    public record Loaded(Automaton automaton, Map<Integer, double[]> positions) {}

    private AutomatonIO() {}

    public static void save(Path file, Automaton m, Map<Integer, double[]> pos) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("AUTOMATON 1");
        if (m.start() != null) lines.add("START " + m.start());
        for (int f : m.finals()) lines.add("FINAL " + f);
        for (int s : m.states()) {
            double[] p = pos.getOrDefault(s, new double[]{60, 70});
            lines.add("STATE " + s + " " + p[0] + " " + p[1]);
        }
        for (Automaton.Transition t : m.transitions()) {
            lines.add("TRANS " + t.from() + " " + t.to() + " " + t.label());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    public static Loaded load(Path file) throws IOException {
        Automaton m = new Automaton();
        Map<Integer, double[]> pos = new HashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] p = line.trim().split("\\s+");
                if (p.length == 0 || p[0].isEmpty()) continue;
                switch (p[0]) {
                    case "START" -> m.setStart(Integer.parseInt(p[1]));
                    case "FINAL" -> m.setFinal(Integer.parseInt(p[1]), true);
                    case "STATE" -> {
                        int id = Integer.parseInt(p[1]);
                        m.addState(id);
                        pos.put(id, new double[]{Double.parseDouble(p[2]), Double.parseDouble(p[3])});
                    }
                    case "TRANS" -> m.addTransition(Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                            p[3].equals("ε") ? Automaton.EPS : p[3].charAt(0));
                    default -> { /* ligne ignorée (AUTOMATON, commentaires) */ }
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("Fichier d'automate invalide : " + e.getMessage(), e);
        }
        return new Loaded(m, pos);
    }
}
