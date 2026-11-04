package com.dan.lituya.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Phase 20, Regel 3.3 Punkt 5: das Kartenmaterial wird ausgelagert ({@code ant auslagerung}). Das Werkzeug verschiebt
 * nichts, es prüft und schreibt auf:
 * <ol>
 * <li>{@code daten/} gegen {@code daten/INHALT.txt}: jede Datei da, Größe und SHA-256 gleich ({@code -Dsha=nein}: nur die
 * Größe).</li>
 * <li>Regel 3.3: kein Laufzeitcode nennt {@code download/} (nur {@code com.dan.lituya.tools}).</li>
 * <li>Das Kartenmaterial nach {@code inventar.csv}: am alten Ort ({@code download/}) oder schon am Ziel
 * ({@code -Dziel=E:\…}), jede Datei mit ihrer Größe.</li>
 * <li>{@code docs/QUELLEN.md}: je Quelle und je Rohdatei der Ort auf der Wechselfestplatte.</li>
 * <li>Liegt das Kartenmaterial vollständig am Ziel, trägt es das Ziel in {@code daten/rohdaten.properties} ein (die
 * Aufbereitung findet es dort); sonst sagt es, was zu verschieben ist.</li>
 * </ol>
 * Die Probe ohne {@code download/} macht {@code ant auslagerungsprobe} (Prüfung und Statusbild bei umbenanntem Ordner).
 */
public final class Auslagerung {
    private Auslagerung() { }

    record Roh(String gruppe, String quelle, String datei, long bytes) { }

    public record Stand(int dateien, long bytes, int fehlen, int falsch, List<String> meldungen) {
        public boolean ok() { return fehlen == 0 && falsch == 0; }
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        File daten = new File(System.getProperty("lituya.daten", "daten"));
        File roh = new File(System.getProperty("roh", "download"));
        String zielS = System.getProperty("ziel", "").strip();
        File ziel = zielS.isEmpty() ? null : new File(zielS);
        boolean sha = !"nein".equalsIgnoreCase(System.getProperty("sha", "ja"));
        boolean alles = true;
        System.out.println("=== auslagerung LituyaBay1958 (Phase 20), " + java.time.LocalDateTime.now().withNano(0));

        // 1. daten/ gegen INHALT.txt
        long t0 = System.nanoTime();
        Stand d = inhalt(daten, sha);
        alles &= d.ok();
        System.out.printf(Locale.GERMAN, "  %-7s daten/: %d Dateien, %.2f GB gegen INHALT.txt (%s) in %.0f s%s%n", d.ok() ? "ok" : "FEHLER", d.dateien(), d.bytes() / 1e9,
                sha ? "Größe und SHA-256" : "nur Größe", (System.nanoTime() - t0) / 1e9, d.ok() ? "" : ": " + d.fehlen() + " fehlen, " + d.falsch() + " anders");
        for (String m : d.meldungen().subList(0, Math.min(20, d.meldungen().size()))) System.out.println("          " + m);

        // 2. Regel 3.3
        List<String> leser = regel33(new File("src"));
        alles &= leser.isEmpty();
        System.out.println("  " + (leser.isEmpty() ? "ok     " : "FEHLER ") + " kein Laufzeitcode nennt download/ (Regel 3.3)" + (leser.isEmpty() ? "" : ": " + leser));

        // 3. Kartenmaterial
        File inv = ziel != null && new File(ziel, "inventar.csv").isFile() ? new File(ziel, "inventar.csv") : new File(roh, "inventar.csv");
        if (!inv.isFile()) {
            System.out.println("  FEHLER  kein inventar.csv in " + roh + (ziel != null ? " oder " + ziel : ""));
            System.out.println("=== FEHLER");
            System.exit(1);
        }
        List<Roh> l = inventar(inv);
        long summe = l.stream().mapToLong(Roh::bytes).sum();
        Stand alt = vorhanden(roh, l), neu = ziel == null ? null : vorhanden(ziel, l);
        System.out.printf(Locale.GERMAN, "  Kartenmaterial: %d Dateien, %.2f GB laut %s%n", l.size(), summe / 1e9, inv.getPath());
        System.out.printf(Locale.GERMAN, "    in %s: %d von %d Dateien da%n", roh.getPath(), alt.dateien(), l.size());
        if (neu != null) System.out.printf(Locale.GERMAN, "    in %s: %d von %d Dateien da, %d mit anderer Größe%n", ziel.getPath(), neu.dateien(), l.size(), neu.falsch());

        // 4. QUELLEN.md
        String ort = ziel != null ? ziel.getPath() : roh.getAbsolutePath();
        File q = new File("docs/QUELLEN.md");
        quellen(q, l, ort, ziel != null && neu.ok());
        System.out.println("  QUELLEN.md: " + q.getPath() + " (" + l.size() + " Rohdateien, Ort " + ort + ")");

        // 5. rohdaten.properties
        if (ziel != null && neu.ok()) {
            File p = new File(daten, "rohdaten.properties");
            Files.writeString(p.toPath(), "# Pfad des Kartenmaterials für die Aufbereitung (Werkbuch 3.3); die App liest nie von dort.\n"
                    + "# Phase 20: ausgelagert am " + java.time.LocalDate.now() + "\nrohdaten=" + ziel.getPath().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
            System.out.println("  ok      ausgelagert: " + ziel.getPath() + " vollständig, eingetragen in " + p.getPath()
                    + (alt.dateien() > 0 ? "; in " + roh.getPath() + " liegen noch " + alt.dateien() + " Dateien (können weg, INVENTAR.md bleibt)" : ""));
        } else if (ziel != null) {
            System.out.println("  Hinweis noch nicht ausgelagert. Verschieben (Explorer oder robocopy), INVENTAR.md bleibt in download/:");
            System.out.println("          robocopy \"" + roh.getAbsolutePath() + "\" \"" + ziel.getPath() + "\" /E /MOVE /XF INVENTAR.md");
            System.out.println("          danach noch einmal: ant auslagerung -Dziel=" + ziel.getPath());
        } else System.out.println("  Hinweis ohne -Dziel nur geprüft; das Kartenmaterial liegt in " + roh.getAbsolutePath());
        System.out.println(alles ? "=== ok" : "=== FEHLER");
        System.exit(alles ? 0 : 1);
    }

    /** daten/ gegen INHALT.txt (Datei, Bytes, SHA-256 je Zeile). */
    public static Stand inhalt(File daten, boolean sha) throws Exception {
        File f = new File(daten, "INHALT.txt");
        List<String> m = new ArrayList<>();
        if (!f.isFile()) return new Stand(0, 0, 1, 0, List.of("INHALT.txt fehlt (ant aufbereitung -Dschritte=inhalt)"));
        List<String[]> z = new ArrayList<>();
        for (String s : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            if (s.isBlank() || s.startsWith("#")) continue;
            String[] p = s.split("\t");
            if (p.length >= 2) z.add(p);
        }
        AtomicLong bytes = new AtomicLong();
        int[] fehlen = {0}, falsch = {0};
        ExecutorService ex = Executors.newFixedThreadPool(Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())));
        try {
            List<Future<String>> fu = new ArrayList<>();
            for (String[] p : z) fu.add(ex.submit(() -> {
                File x = new File(daten, p[0]);
                if (!x.isFile()) return "fehlt: " + p[0];
                long n = Long.parseLong(p[1]);
                if (x.length() != n) return "Größe: " + p[0] + " " + x.length() + " statt " + n;
                bytes.addAndGet(n);
                if (sha && p.length >= 3 && !p[2].equalsIgnoreCase(sha256(x))) return "SHA-256: " + p[0];
                return null;
            }));
            for (Future<String> g : fu) {
                String s = g.get();
                if (s == null) continue;
                m.add(s);
                if (s.startsWith("fehlt")) fehlen[0]++; else falsch[0]++;
            }
        } finally { ex.shutdown(); }
        return new Stand(z.size(), bytes.get(), fehlen[0], falsch[0], m);
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] b = new byte[1 << 20];
        try (InputStream in = Files.newInputStream(f.toPath())) { for (int n; (n = in.read(b)) > 0; ) md.update(b, 0, n); }
        return HexFormat.of().formatHex(md.digest());
    }

    /** Die Java-Dateien außerhalb von tools, die "download nennen (wie die Prüfung, Regel 3.3). */
    public static List<String> regel33(File src) throws IOException {
        List<String> l = new ArrayList<>();
        if (!src.isDirectory()) return l;
        try (Stream<Path> st = Files.walk(src.toPath())) {
            for (Path p : (Iterable<Path>) st.filter(x -> x.toString().endsWith(".java"))::iterator) {
                String pfad = p.toString().replace('\\', '/');
                if (pfad.contains("/lituya/tools/")) continue;
                if (Files.readString(p, StandardCharsets.UTF_8).contains("\"download")) l.add(pfad);
            }
        }
        return l;
    }

    static List<Roh> inventar(File csv) throws IOException {
        List<Roh> l = new ArrayList<>();
        boolean kopf = true;
        for (String s : Files.readAllLines(csv.toPath(), StandardCharsets.UTF_8)) {
            if (kopf) { kopf = false; continue; }
            if (s.isBlank()) continue;
            String[] p = csv(s);
            if (p.length < 4) continue;
            long n;
            try { n = Long.parseLong(p[3].strip()); } catch (NumberFormatException e) { n = -1; }
            l.add(new Roh(p[0], p[1], p[2].replace('\\', '/'), n));
        }
        return l;
    }

    /** Eine CSV-Zeile mit Anführungszeichen. */
    static String[] csv(String s) {
        List<String> l = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') { if (q && i + 1 < s.length() && s.charAt(i + 1) == '"') { b.append('"'); i++; } else q = !q; }
            else if (c == ',' && !q) { l.add(b.toString()); b.setLength(0); }
            else b.append(c);
        }
        l.add(b.toString());
        return l.toArray(new String[0]);
    }

    static Stand vorhanden(File ort, List<Roh> l) {
        int da = 0, falsch = 0, fehlen = 0;
        long bytes = 0;
        for (Roh r : l) {
            File f = new File(ort, r.datei());
            if (!f.isFile()) { fehlen++; continue; }
            da++;
            bytes += f.length();
            if (r.bytes() >= 0 && f.length() != r.bytes()) falsch++;
        }
        return new Stand(da, bytes, fehlen, falsch, List.of());
    }

    /** docs/QUELLEN.md: je Gruppe und Quelle die Rohdateien mit Größe und Ort. */
    static void quellen(File q, List<Roh> l, String ort, boolean ausgelagert) throws IOException {
        Map<String, Map<String, List<Roh>>> g = new TreeMap<>();
        for (Roh r : l) g.computeIfAbsent(r.gruppe(), k -> new TreeMap<>()).computeIfAbsent(r.quelle(), k -> new ArrayList<>()).add(r);
        StringBuilder b = new StringBuilder();
        long summe = l.stream().mapToLong(r -> Math.max(0, r.bytes())).sum();
        b.append("# LituyaBay1958 – Ort des Kartenmaterials\n\n");
        b.append("Werkbuch 3.3, Punkt 5: Das Kartenmaterial liest nur die Aufbereitung (Phase 2); die App, die Netze und Oracle lesen aus `daten/`. ");
        b.append(String.format(Locale.GERMAN, "Geschrieben von `ant auslagerung` am %s. **%d Dateien, %.2f GB**, ", java.time.LocalDate.now(), l.size(), summe / 1e9));
        b.append(ausgelagert ? "ausgelagert nach `" + ort + "`." : "noch in `" + ort + "`.");
        b.append(" Titel, Adressen und Lizenzen der Quellen stehen in [Kartenmaterial.md](../Kartenmaterial.md), der Ladestand in `download/INVENTAR.md`. ");
        b.append("Um die Aufbereitung wieder laufen zu lassen: die Platte anschließen, `daten/rohdaten.properties` zeigt dorthin, dann `ant aufbereitung`.\n\n");
        b.append("## Übersicht\n\n| Gruppe | Quelle | Dateien | Größe | Ort |\n|---|---|---|---|---|\n");
        for (var e : g.entrySet())
            for (var f : e.getValue().entrySet()) {
                long n = f.getValue().stream().mapToLong(r -> Math.max(0, r.bytes())).sum();
                b.append("| ").append(e.getKey()).append(" | `").append(f.getKey()).append("` | ").append(f.getValue().size()).append(" | ").append(groesse(n))
                        .append(" | `").append(ort).append(File.separator).append(e.getKey()).append(File.separator).append(f.getKey()).append("` |\n");
            }
        b.append("\n## Rohdateien\n");
        for (var e : g.entrySet()) {
            b.append("\n### ").append(e.getKey()).append("\n");
            for (var f : e.getValue().entrySet()) {
                b.append("\n**`").append(f.getKey()).append("`**\n\n");
                for (Roh r : f.getValue()) b.append("- `").append(r.datei()).append("` · ").append(groesse(r.bytes())).append('\n');
            }
        }
        if (q.getAbsoluteFile().getParentFile() != null) q.getAbsoluteFile().getParentFile().mkdirs();
        Files.writeString(q.toPath(), b.toString(), StandardCharsets.UTF_8);
    }

    static String groesse(long n) {
        if (n < 0) return "?";
        if (n >= 1e9) return String.format(Locale.GERMAN, "%.2f GB", n / 1e9);
        if (n >= 1e6) return String.format(Locale.GERMAN, "%.1f MB", n / 1e6);
        if (n >= 1e3) return String.format(Locale.GERMAN, "%.0f kB", n / 1e3);
        return n + " B";
    }

}
