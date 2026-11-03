package com.dan.lituya.tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 20: fasst die Läufe von {@code ant qualitaet} (je Stufe und Ansicht ein {@link Tempo}) zu einer Tabelle für das
 * Werkbuch 15 zusammen und hängt sie an die Ausgabe an. Die Läufe sind mit Zeilen {@code ### Ansicht · Stufe} getrennt.
 */
public final class QualitaetTabelle {
    private QualitaetTabelle() { }

    static final Pattern ZEILE = Pattern.compile("bestes Bild ([\\d,.]+) ms, Mittel ([\\d,.]+) ms \\(([\\d,.]+) Bilder/s\\)");

    public static void main(String[] a) throws Exception {
        File f = new File(a.length > 0 ? a[0] : "docs/status/qualitaet_Phase20.txt");
        String t = tabelle(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
        Files.writeString(f.toPath(), System.lineSeparator() + t, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        System.out.print(t);
    }

    /** Markdown-Tabelle: Zeilen die Ansichten, Spalten die Stufen, Zelle „Mittel ms (Bilder/s)“. */
    public static String tabelle(List<String> zeilen) {
        Map<String, Map<String, String>> m = new LinkedHashMap<>();
        List<String> stufen = new ArrayList<>();
        String ansicht = null, stufe = null;
        for (String z : zeilen) {
            String s = z.replaceFirst("^\\s*\\[(java|echo)]\\s*", "").strip();
            if (s.startsWith("### ")) {
                String[] p = s.substring(4).split(" · ");
                ansicht = p[0].strip();
                stufe = p.length > 1 ? p[1].strip() : "";
                if (!stufen.contains(stufe)) stufen.add(stufe);
                continue;
            }
            Matcher x = ZEILE.matcher(s);
            if (x.find() && ansicht != null)
                m.computeIfAbsent(ansicht, k -> new LinkedHashMap<>()).put(stufe, x.group(2) + " ms (" + x.group(3) + ")");
        }
        StringBuilder b = new StringBuilder("| Ansicht (1920 × 1080) |");
        for (String s : stufen) b.append(' ').append(s).append(" |");
        b.append("\n|---|");
        for (int i = 0; i < stufen.size(); i++) b.append("---|");
        b.append('\n');
        for (var e : m.entrySet()) {
            b.append("| ").append(e.getKey()).append(" |");
            for (String s : stufen) b.append(' ').append(e.getValue().getOrDefault(s, "–")).append(" |");
            b.append('\n');
        }
        return b.append("\nZelle: mittlere Zeit je Bild bis glFinish (Bilder je Sekunde).\n").toString();
    }
}
