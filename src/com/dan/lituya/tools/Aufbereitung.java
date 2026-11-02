package com.dan.lituya.tools;

import com.dan.lituya.scene.Stufe;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Die Aufbereitung (Werkbuch 3.3 und 3.4, Phase 2): liest das Kartenmaterial einmal und schreibt nach {@code daten/},
 * was App, Netze und Prüfungen brauchen. Aufruf {@code ant aufbereitung} (alle Schritte) oder
 * {@code ant aufbereitung -Dschritte=gelaende,grund}; fertige Kacheln bleiben stehen, {@code -Dneu=true} rechnet sie neu.
 * <p>
 * Schritte in Phase 2: {@code gelaende} (Fern, Szene, Kern), {@code grund} (heute, 1926, 1940, 1959), {@code namen},
 * {@code bilder} (Prüfbilder nach {@code docs/status/}), {@code inhalt} ({@code daten/INHALT.txt} mit SHA-256 je Datei).
 * Phase 3: {@code herkunft} (das Herkunftsnetz nach {@code daten/netz/herkunft/}, {@link Herkunft}). Phase 5: {@code texturen}
 * (die CC0-Sätze aus {@code download/texturen} gebacken nach {@code daten/texturen/}, {@link Texturen}). Phase 7a:
 * {@code welle} und {@code ufer} (die Netze der Welle und des Ufers als Datei nach {@code daten/netz/welle/} und
 * {@code daten/netz/ufer/}, {@link Welle}, {@link Ufer}; brauchen nur {@code daten/grund}). Phase 14a: {@code wald} (Kronenhöhe ArcticDEM − Gelände und Abgleich der Wiederbewaldung nach {@code daten/wald/}, {@link Wald}). Phase 8: {@code stoerung} (Fairweather-Störung aus Qfaults
 * und Herd aus ComCat nach {@code daten/ereignis/}, {@link Stoerung}). Später: {@code rueckbau1958}, {@code spurlinie} (brauchen die von Hand eingepassten Karten 1951/C-5 und die
 * Miller-Karte), {@code klassen}, {@code wald}, {@code gletscher}, die übrigen räumlichen Netze, {@code ereignis} (Phase 13).
 */
public final class Aufbereitung {
    private Aufbereitung() { }

    public static final String[] SCHRITTE = {"gelaende", "grund", "namen", "welle", "ufer", "see", "stoerung", "texturen", "herkunft", "wald", "bilder", "inhalt"};

    public static void main(String[] a) throws Exception {
        File daten = new File(System.getProperty("lituya.daten", "daten"));
        Set<String> s = new LinkedHashSet<>();
        String arg = a.length > 0 && !a[0].isBlank() ? a[0] : String.join(",", SCHRITTE);
        for (String x : arg.split("[,; ]+")) if (!x.isBlank()) s.add(x.trim().toLowerCase(Locale.ROOT));
        for (String x : s) if (!Arrays.asList(SCHRITTE).contains(x)) throw new IllegalArgumentException("Schritt „" + x + "“ unbekannt; da sind: " + String.join(", ", SCHRITTE));
        boolean neu = Boolean.getBoolean("neu");
        int faeden = Integer.getInteger("faeden", 0);
        if (faeden <= 0) faeden = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        Rohdaten r = new Rohdaten(daten);
        long t0 = System.nanoTime();
        r.log("=== Aufbereitung LituyaBay1958 (Phase 8): %s · Rohdaten %s · Ziel %s · %d Fäden · Java %s, %d MB Speicher", String.join(", ", s),
                r.roh.getAbsolutePath(), daten.getAbsolutePath(), faeden, System.getProperty("java.version"), Runtime.getRuntime().maxMemory() >> 20);
        boolean braucheRoh = s.contains("gelaende") || s.contains("grund") || s.contains("herkunft") || s.contains("texturen") || s.contains("stoerung") || s.contains("wald");
        if (braucheRoh && !r.roh.isDirectory()) throw new java.io.IOException("Kartenmaterial nicht gefunden: " + r.roh.getAbsolutePath()
                + " (Pfad in daten/rohdaten.properties oder -Drohdaten=…)");
        try {
            if (s.contains("gelaende"))
                for (Stufe st : new Stufe[]{Stufe.FERN, Stufe.SZENE, Stufe.KERN}) {
                    r.log("--- gelaende %s (%s m, %d Kacheln)", st, Gelaende.fmt(st.zelle), st.nx() * st.ny());
                    Gelaende.stufe(st, r, neu, faeden);
                }
            if (s.contains("grund")) { r.log("--- grund"); Grund.rechnen(r); }
            if (s.contains("namen")) { r.log("--- namen"); Namen.rechnen(r, new File("geodaten/namen_szene.geojson")); }
            if (s.contains("welle")) { r.log("--- welle"); Welle.rechnen(r); }
            if (s.contains("ufer")) { r.log("--- ufer"); Ufer.rechnen(r); }
            if (s.contains("see")) { r.log("--- see"); See.rechnen(r); }
            if (s.contains("stoerung")) { r.log("--- stoerung"); Stoerung.rechnen(r); }
            if (s.contains("texturen")) { r.log("--- texturen"); Texturen.rechnen(r, neu); }
            if (s.contains("herkunft")) { r.log("--- herkunft"); Herkunft.rechnen(r); }
            if (s.contains("wald")) { r.log("--- wald"); Wald.rechnen(r); }
            if (s.contains("bilder")) { r.log("--- bilder"); Pruefbilder.alle(daten, new File("docs/status"), r); }
            if (s.contains("inhalt")) { r.log("--- inhalt"); inhalt(r); }
            r.log("=== fertig in %.1f min", (System.nanoTime() - t0) / 60e9);
        } catch (Exception e) {
            r.log("FEHLER: %s", e);
            throw e;
        } finally {
            r.schliessen();
        }
    }

    /** Schreibt daten/INHALT.txt: je Datei Größe und SHA-256 (ohne Protokoll, Rohdatenpfad und INHALT selbst). */
    static void inhalt(Rohdaten r) throws Exception {
        Path base = r.daten.toPath();
        List<Path> files = new ArrayList<>();
        try (var st = Files.walk(base)) {
            st.filter(Files::isRegularFile).filter(p -> {
                String n = base.relativize(p).toString().replace('\\', '/');
                // gpu_*.bin: gepackte Texturen, die die Grafikkarte des Rechners schreibt (nicht Teil der Aufbereitung)
                return !n.equals("INHALT.txt") && !n.equals("aufbereitung.log") && !n.equals("rohdaten.properties") && !n.equals("fehler.txt") && !n.endsWith(".tmp")
                        && !(n.startsWith("texturen/") && n.contains("/gpu_"));
            }).sorted().forEach(files::add);
        }
        List<String> z = new ArrayList<>();
        z.add("# daten/ von LituyaBay1958, geschrieben von der Aufbereitung (Phase 2, " + java.time.LocalDate.now() + "): Datei, Bytes, SHA-256");
        long sum = 0;
        byte[] buf = new byte[1 << 20];
        for (Path p : files) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(p)) {
                for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            }
            long len = Files.size(p);
            sum += len;
            z.add(base.relativize(p).toString().replace('\\', '/') + "\t" + len + "\t" + java.util.HexFormat.of().formatHex(md.digest()));
        }
        z.add(String.format(Locale.ROOT, "# %d Dateien, %.2f GB", files.size(), sum / 1e9));
        Files.write(r.daten.toPath().resolve("INHALT.txt"), z, StandardCharsets.UTF_8);
        r.log("  inhalt: %d Dateien, %.2f GB, INHALT.txt geschrieben", files.size(), sum / 1e9);
    }
}
