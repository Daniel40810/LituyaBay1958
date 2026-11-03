package com.dan.lituya.data;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * Der Rückfall ohne Datenbank (Phase 19): alles unter {@code daten/speicher}. Sitzungen als
 * {@code sitzungen/<id>.properties} (Kopf mit {@code kopf.}, der Zustand ohne), Läufe als {@code laeufe/<name>.properties},
 * eigene Regiebücher als {@code regiebuecher/<name>.txt}, Bilder als PNG in {@code bilder/} mit {@code bilder/bilder.tsv}.
 * {@code tools.SzeneDb} übernimmt den Ordner in Oracle, sobald es die Tabellen gibt.
 */
public final class DateiSpeicher implements Speicher {
    final File dir;
    static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public DateiSpeicher(File dir) { this.dir = dir; }

    @Override public String wo() { return "Dateien in " + dir.getPath(); }

    private File sub(String n) throws IOException {
        File f = new File(dir, n);
        if (!f.isDirectory() && !f.mkdirs()) throw new IOException("Ordner " + f + " lässt sich nicht anlegen");
        return f;
    }

    static String datei(String name) { return name.replaceAll("[^\\p{L}\\p{N}._ -]", "_").strip(); }

    // ------------------------------------------------------------------ Sitzungen

    @Override public long sitzungSpeichern(Sitzung s) throws IOException {
        long id = s.id() > 0 ? s.id() : System.currentTimeMillis();
        Properties p = new Properties();
        if (s.zustand() != null) p.putAll(s.zustand());
        kopf(p, "name", s.name());
        kopf(p, "gespeichert", s.gespeichert() != null ? s.gespeichert() : LocalDateTime.now().format(ZEIT));
        kopf(p, "rechner", s.rechner());
        kopf(p, "e", s.e()); kopf(p, "n", s.n()); kopf(p, "h", s.h()); kopf(p, "richtung", s.richtung()); kopf(p, "neigung", s.neigung());
        kopf(p, "modus", s.modus()); kopf(p, "uhr", s.uhrMs()); kopf(p, "zeitstand", s.zeitstand()); kopf(p, "raffer", s.raffer());
        kopf(p, "lauf", s.lauf()); kopf(p, "regiebuch", s.regiebuch());
        try (Writer w = Files.newBufferedWriter(new File(sub("sitzungen"), id + ".properties").toPath(), StandardCharsets.UTF_8)) { p.store(w, "LituyaBay1958 Sitzung (Phase 19)"); }
        return id;
    }

    private static void kopf(Properties p, String k, Object v) { if (v != null) p.setProperty("kopf." + k, String.valueOf(v)); }

    @Override public List<Sitzung> sitzungen() throws IOException {
        List<Sitzung> l = new ArrayList<>();
        File[] fs = new File(dir, "sitzungen").listFiles((d, n) -> n.endsWith(".properties"));
        if (fs != null) for (File f : fs) { Sitzung s = lies(f); if (s != null) l.add(s); }
        l.sort(Comparator.comparingLong(Sitzung::id).reversed());
        return l;
    }

    @Override public Sitzung sitzung(long id) throws IOException {
        File f = new File(new File(dir, "sitzungen"), id + ".properties");
        return f.isFile() ? lies(f) : null;
    }

    private static Sitzung lies(File f) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) { p.load(r); }
        long id;
        try { id = Long.parseLong(f.getName().replace(".properties", "")); } catch (NumberFormatException e) { return null; }
        Properties z = new Properties();
        for (String k : p.stringPropertyNames()) if (!k.startsWith("kopf.")) z.setProperty(k, p.getProperty(k));
        return new Sitzung(id, p.getProperty("kopf.name", f.getName()), p.getProperty("kopf.gespeichert", ""), p.getProperty("kopf.rechner", ""),
                d(p, "e"), d(p, "n"), d(p, "h"), d(p, "richtung"), d(p, "neigung"), p.getProperty("kopf.modus", ""), (long) d(p, "uhr"),
                p.getProperty("kopf.zeitstand", ""), d(p, "raffer"), p.getProperty("kopf.lauf"), p.getProperty("kopf.regiebuch"), z);
    }

    private static double d(Properties p, String k) {
        String v = p.getProperty("kopf." + k);
        try { return v == null ? Double.NaN : Double.parseDouble(v); } catch (NumberFormatException e) { return Double.NaN; }
    }

    // ------------------------------------------------------------------ Läufe

    @Override public void laufSpeichern(Lauf l) throws IOException {
        Properties p = new Properties();
        java.lang.reflect.RecordComponent[] rc = Lauf.class.getRecordComponents();
        for (java.lang.reflect.RecordComponent c : rc) {
            try {
                Object v = c.getAccessor().invoke(l);
                if (v != null) p.setProperty(c.getName(), String.valueOf(v));
            } catch (ReflectiveOperationException e) { throw new IOException(e); }
        }
        try (Writer w = Files.newBufferedWriter(new File(sub("laeufe"), datei(l.name()) + ".properties").toPath(), StandardCharsets.UTF_8)) { p.store(w, "LituyaBay1958 Lauf (Phase 19)"); }
    }

    @Override public List<Lauf> laeufe() throws IOException {
        List<Lauf> l = new ArrayList<>();
        File[] fs = new File(dir, "laeufe").listFiles((d, n) -> n.endsWith(".properties"));
        if (fs == null) return l;
        java.util.Arrays.sort(fs);
        for (File f : fs) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) { p.load(r); }
            l.add(new Lauf(p.getProperty("name"), p.getProperty("art", "Echtzeit"), p.getProperty("quelle"), p.getProperty("ort"), z(p, "volumen"), z(p, "pegel"),
                    p.getProperty("grund"), p.getProperty("ganglinie"), z(p, "startS"), p.getProperty("gitter"), p.getProperty("rechner"), z(p, "dauerS"),
                    z(p, "auflaufMax"), z(p, "auflaufSporn"), z(p, "ankunftCenotaph"), z(p, "ankunftAnchorage"), z(p, "ankunftEingang"), p.getProperty("eingaben")));
        }
        return l;
    }

    private static double z(Properties p, String k) {
        String v = p.getProperty(k);
        try { return v == null ? Double.NaN : Double.parseDouble(v); } catch (NumberFormatException e) { return Double.NaN; }
    }

    // ------------------------------------------------------------------ Regiebücher

    @Override public void regiebuchSpeichern(Buch b) throws IOException {
        Files.writeString(new File(sub("regiebuecher"), datei(b.name()) + ".txt").toPath(), b.text(), StandardCharsets.UTF_8);
    }

    @Override public List<Buch> regiebuecher() throws IOException {
        List<Buch> l = new ArrayList<>();
        File[] fs = new File(dir, "regiebuecher").listFiles((d, n) -> n.endsWith(".txt"));
        if (fs == null) return l;
        java.util.Arrays.sort(fs);
        for (File f : fs) l.add(new Buch(f.getName().replace(".txt", ""), Files.readString(f.toPath(), StandardCharsets.UTF_8), false,
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(f.lastModified()), java.time.ZoneId.systemDefault()).format(ZEIT)));
        return l;
    }

    // ------------------------------------------------------------------ Bilder

    @Override public void bildSpeichern(Bild b) throws IOException {
        File d = sub("bilder");
        String name = new File(b.datei()).getName();
        File f = new File(d, name);
        if (b.png() != null && !f.isFile()) Files.write(f.toPath(), b.png());
        String z = String.join("\t", b.art(), b.datei(), String.valueOf(b.breite()), String.valueOf(b.hoehe()), nn(b.weltzeit()), String.valueOf(b.zeitS()),
                nn(b.zeitstand()), String.valueOf(b.e()), String.valueOf(b.n()), String.valueOf(b.h()), String.valueOf(b.richtung()), String.valueOf(b.neigung()),
                nn(b.regiebuch()), String.valueOf(b.bildrate()), nn(b.notiz()).replace('\t', ' ').replace('\n', ' ')) + System.lineSeparator();
        Files.writeString(new File(d, "bilder.tsv").toPath(), z, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String nn(String s) { return s == null ? "" : s; }

    @Override public int bilder() throws IOException {
        File f = new File(new File(dir, "bilder"), "bilder.tsv");
        return f.isFile() ? (int) Files.readAllLines(f.toPath(), StandardCharsets.UTF_8).stream().filter(s -> !s.isBlank()).count() : 0;
    }

    /** Die Zeilen von bilder.tsv (für die Übernahme nach Oracle): je Zeile die Felder wie {@link #bildSpeichern}. */
    public List<String[]> bildZeilen() throws IOException {
        File f = new File(new File(dir, "bilder"), "bilder.tsv");
        List<String[]> l = new ArrayList<>();
        if (f.isFile()) for (String s : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) if (!s.isBlank()) l.add(s.split("\t", -1));
        return l;
    }

    public File ordner() { return dir; }
}
