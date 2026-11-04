package com.dan.lituya.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Wo das Kartenmaterial liegt und wohin die Aufbereitung schreibt (Werkbuch 3.3, Phase 2). Der Pfad der Rohdaten steht in
 * {@code daten/rohdaten.properties} ({@code rohdaten=…}, in {@code .gitignore}); fehlt die Datei, gilt {@code download/}
 * und sie wird mit diesem Wert angelegt. Nur die Werkzeuge in {@code com.dan.lituya.tools} lesen von dort, die App nie.
 * Dazu das Protokoll {@code daten/aufbereitung.log}.
 */
public final class Rohdaten {
    public final File roh, daten;
    private final PrintWriter log;

    public Rohdaten(File daten) throws IOException {
        this.daten = daten;
        daten.mkdirs();
        File p = new File(daten, "rohdaten.properties");
        Properties pr = new Properties();
        if (p.isFile()) try (var in = Files.newBufferedReader(p.toPath(), StandardCharsets.UTF_8)) { pr.load(in); }
        String r = System.getProperty("rohdaten", pr.getProperty("rohdaten", "download"));
        roh = new File(r);
        if (!p.isFile())
            Files.writeString(p.toPath(), "# Pfad des Kartenmaterials für die Aufbereitung (Werkbuch 3.3); die App liest nie von dort.\nrohdaten="
                    + r.replace("\\", "/") + "\n", StandardCharsets.UTF_8);
        log = new PrintWriter(Files.newBufferedWriter(new File(daten, "aufbereitung.log").toPath(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND), true);
    }

    /** Eine Zeile in Konsole und Protokoll. */
    public synchronized void log(String fmt, Object... a) {
        String s = a.length == 0 ? fmt : String.format(Locale.GERMAN, fmt, a);
        System.out.println(s);
        log.println(java.time.LocalDateTime.now().withNano(0) + "  " + s);
    }

    /** Alle Dateien unter roh/pfad, deren Name auf eines der Enden passt (rekursiv, sortiert). */
    public List<File> dateien(String pfad, String... enden) throws IOException {
        File d = new File(roh, pfad);
        List<File> l = new ArrayList<>();
        if (!d.isDirectory()) return l;
        try (var st = Files.walk(d.toPath())) {
            st.filter(Files::isRegularFile).map(java.nio.file.Path::toFile).filter(f -> {
                String n = f.getName().toLowerCase(Locale.ROOT);
                for (String e : enden) if (n.endsWith(e.toLowerCase(Locale.ROOT))) return true;
                return false;
            }).sorted().forEach(l::add);
        }
        return l;
    }

    /** Wie {@link #dateien}, aber mit Fehler, wenn nichts da ist. */
    public List<File> noetig(String pfad, String... enden) throws IOException {
        List<File> l = dateien(pfad, enden);
        if (l.isEmpty()) throw new IOException("keine Dateien in " + new File(roh, pfad) + " (" + String.join(", ", enden) + ") – Kartenmaterial laden: python tools/kartenmaterial/kartenmaterial.py laden");
        return l;
    }

    public void schliessen() { log.close(); }
}
