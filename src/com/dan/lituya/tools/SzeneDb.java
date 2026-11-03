package com.dan.lituya.tools;

import com.dan.lituya.camera.Fahrt;
import com.dan.lituya.camera.SichtFahrt;
import com.dan.lituya.data.DateiSpeicher;
import com.dan.lituya.data.OracleSpeicher;
import com.dan.lituya.data.Speicher;
import com.dan.lituya.data.Verbindung;
import com.dan.lituya.scene.BootBahn;
import com.dan.lituya.scene.Regie;
import com.dan.lituya.scene.Site;
import com.dan.store.Db;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Tabellen von {@code db/szene} anlegen, befüllen und prüfen (Phase 19, Werkbuch 13.1): {@code ant szenedb}
 * ({@code -Dneu=ja} baut vorher alles ab, auch Sitzungen und eigene Regiebücher), {@code ant szenedbpruefung} nur die
 * Prüfung. Befüllt aus den mitgelieferten Tabellen ({@code com/dan/lituya/data/*.tsv}), aus {@code daten/ereignis}
 * (Läufe mit Kennzahlen, Lage der Boote je Sekunde und ihre Bahn), {@code daten/netz/sicht} (Rundflug, Spannbaum),
 * {@code daten/texturen/LIZENZEN.txt}, den Regiebüchern in {@code scene/regie}, den Hörproben und je Regiebuch dem
 * neuesten Blatt in {@code docs/status}; was die App ohne Datenbank in {@code daten/speicher} gesichert hat, kommt mit.
 * Schlüssel bleiben stabil: Zeilen werden nach ihrem Namen ersetzt, nicht neu angelegt.
 */
public final class SzeneDb {
    private SzeneDb() { }

    static final List<String> PROTOKOLL = new ArrayList<>();
    /** Die Tabellen von db/szene (für Prüfung und Abbau). */
    public static final String[] TABELLEN = {"LB_QUELLE", "LB_BEFUND", "LB_BEFUND_QUELLE", "LB_ZEUGE", "LB_BOOT", "LB_BOOT_LAGE", "LB_BOOT_BAHN", "LB_EREIGNIS_LAUF",
            "LB_SZENARIO", "LB_SZENARIO_SCHRITT", "LB_KAMERA_FAHRT", "LB_SCHNAPPSCHUSS", "LB_SITZUNG", "LB_TEXTUR"};
    public static final String[] SICHTEN = {"LB_V_BEFUND", "LB_V_BEFUND_ART", "LB_V_ZEUGE", "LB_V_BOOT_BAHN", "LB_V_SCHNAPPSCHUSS", "LB_V_LAUF_VERGLEICH", "LB_V_SITZUNG"};
    public static final String[] SKRIPTE = {"01_belege.sql", "02_boote_laeufe.sql", "03_regie_bilder.sql", "04_sitzung_texturen.sql", "05_sichten.sql", "99_abbau.sql"};

    static void log(String f, Object... a) {
        String s = a.length == 0 ? f : String.format(Locale.GERMAN, f, a);
        System.out.println(s);
        PROTOKOLL.add(s);
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        boolean nurPruefen = a.length > 0 && a[0].equalsIgnoreCase("pruefen"), neu = "ja".equalsIgnoreCase(System.getProperty("neu", ""));
        File daten = new File(System.getProperty("lituya.daten", "daten")), sql = new File(System.getProperty("lituya.sql", "db/szene"));
        String was = nurPruefen ? "szenedbpruefung" : "szenedb";
        log("=== %s LituyaBay1958 (Phase 19), %s", was, java.time.LocalDateTime.now().withNano(0));
        Db.Konfig k = Verbindung.konfig();
        if (!k.hatPasswort()) throw new IOException("kein Passwort in db/db.properties und keine Umgebungsvariable " + Verbindung.ENV);
        Db.antwortS = 0;
        boolean ok;
        try (Connection c = Db.oeffnen(k)) {
            c.setAutoCommit(false);
            log("verbunden: %s als %s", k.url, Db.einWert(c, "SELECT USER FROM DUAL"));
            try {
                if (!nurPruefen) {
                    long t0 = System.nanoTime();
                    if (neu) { log("Abbau (-Dneu=ja)"); NetzDb.skript(c, new File(sql, "99_abbau.sql"), false); }
                    if (!Db.namen(c, "SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME = 'LB_QUELLE'").contains("LB_QUELLE")) {
                        for (int i = 0; i < 4; i++) NetzDb.skript(c, new File(sql, SKRIPTE[i]), true);
                    } else log("Tabellen da: angelegt wird nichts (neu mit -Dneu=ja)");
                    NetzDb.skript(c, new File(sql, "05_sichten.sql"), true);
                    laden(c, daten);
                    log("geladen in %.1f s", (System.nanoTime() - t0) / 1e9);
                }
                ok = pruefen(c, k);
            } catch (Exception e) {
                ok = false;
                log("FEHLER: %s", NetzDb.ursache(e));
                try { c.rollback(); } catch (SQLException x) { /* egal */ }
            }
        }
        log("=== %s", ok ? "ok" : "FEHLER (siehe oben)");
        File st = new File("docs/status");
        if (st.isDirectory()) Files.write(new File(st, was + "_protokoll_Phase19.txt").toPath(), PROTOKOLL, StandardCharsets.UTF_8);
        System.exit(ok ? 0 : 1);
    }

    // ------------------------------------------------------------------ mitgelieferte Tabellen

    /** Eine mitgelieferte Tabelle aus com/dan/lituya/data: Zeilen ohne #, Felder mit Tab (leere Felder bleiben). */
    public static List<String[]> tsv(String name) throws IOException {
        try (InputStream in = Verbindung.class.getResourceAsStream(name)) {
            if (in == null) throw new IOException("data/" + name + " fehlt im Build");
            List<String[]> l = new ArrayList<>();
            for (String z : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) if (!z.isBlank() && !z.startsWith("#")) l.add(z.split("\t", -1));
            return l;
        }
    }

    /** Ort aus Name (Site) oder „E N“; null ohne. */
    public static double[] ort(String s) {
        if (s == null || s.isBlank()) return null;
        String[] p = s.trim().split("\\s+");
        if (p.length == 2) try { return new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1])}; } catch (NumberFormatException e) { /* Name */ }
        Site.Ort o = Site.byName(s.trim());
        return o == null ? null : new double[]{o.e(), o.n()};
    }

    static double zahl(String s) {
        if (s == null || s.isBlank()) return Double.NaN;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return Double.NaN; }
    }

    static void laden(Connection c, File daten) throws Exception {
        Map<String, Long> q = quellen(c);
        befunde(c, q);
        zeugen(c, q);
        Map<String, Long> boote = boote(c, q);
        laeufe(c, daten, boote);
        hoerproben(c, q);
        regiebuecher(c);
        fahrten(c, daten);
        texturen(c, daten, q);
        blaetter(c);
        uebernahme(c, daten);
        c.commit();
    }

    static Long id(Connection c, String sql, String key) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, key);
            try (ResultSet r = p.executeQuery()) { return r.next() ? r.getLong(1) : null; }
        }
    }

    static long neueId(Connection c, String seq) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT " + seq + ".NEXTVAL FROM DUAL")) { r.next(); return r.getLong(1); }
    }

    static void num(PreparedStatement p, int i, double v) throws SQLException { if (Double.isNaN(v) || Double.isInfinite(v)) p.setNull(i, Types.NUMERIC); else p.setDouble(i, v); }

    static void str(PreparedStatement p, int i, String s) throws SQLException { if (s == null || s.isBlank()) p.setNull(i, Types.VARCHAR); else p.setString(i, s); }

    static final String PUNKT = "CASE WHEN ? IS NULL THEN CAST(NULL AS MDSYS.SDO_GEOMETRY) ELSE SDO_GEOMETRY(2001, 26908, SDO_POINT_TYPE(?, ?, NULL), NULL, NULL) END";

    static void punkt(PreparedStatement p, int i, double[] o) throws SQLException {
        num(p, i, o == null ? Double.NaN : o[0]);
        num(p, i + 1, o == null ? Double.NaN : o[0]);
        num(p, i + 2, o == null ? Double.NaN : o[1]);
    }

    static Map<String, Long> quellen(Connection c) throws Exception {
        Map<String, Long> m = new HashMap<>();
        for (String[] z : tsv("quellen.tsv")) {
            Long id = id(c, "SELECT QUELLE_ID FROM LB_QUELLE WHERE KURZ = ?", z[0]);
            String sql = id == null ? "INSERT INTO LB_QUELLE (AUTOREN, JAHR, TITEL, ART, FUNDORT, LIZENZ, GEPRUEFT, KURZ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                    : "UPDATE LB_QUELLE SET AUTOREN = ?, JAHR = ?, TITEL = ?, ART = ?, FUNDORT = ?, LIZENZ = ?, GEPRUEFT = ?, GEAENDERT = SYSDATE WHERE KURZ = ?";
            try (PreparedStatement p = c.prepareStatement(sql)) {
                str(p, 1, z[1]); num(p, 2, zahl(z[2])); p.setString(3, z[3]); p.setString(4, z[4]); str(p, 5, z[5]); str(p, 6, z[6]); p.setString(7, z[7]); p.setString(8, z[0]);
                p.executeUpdate();
            }
            m.put(z[0], id != null ? id : id(c, "SELECT QUELLE_ID FROM LB_QUELLE WHERE KURZ = ?", z[0]));
        }
        log("LB_QUELLE: %d Quellen", m.size());
        return m;
    }

    /** Ein Befund nach Schlüssel einfügen oder ersetzen, die Quellen neu ({@code KURZ[:Seite]}). */
    static void befund(Connection c, Map<String, Long> q, String key, String thema, String aussage, double wert, String einheit, String art, String abschnitt,
                       String ortName, double zeit, String gilt, String quellen, String herkunft, String phase) throws SQLException {
        Long id = id(c, "SELECT BEFUND_ID FROM LB_BEFUND WHERE SCHLUESSEL = ?", key);
        if (id == null) {
            id = neueId(c, "LB_BEFUND_SEQ");
            try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_BEFUND (BEFUND_ID, SCHLUESSEL, THEMA, AUSSAGE, ART) VALUES (?, ?, ?, ?, ?)")) {
                p.setLong(1, id); p.setString(2, key); p.setString(3, thema); p.setString(4, aussage); p.setString(5, art);
                p.executeUpdate();
            }
        }
        double[] o = ort(ortName);
        try (PreparedStatement p = c.prepareStatement("UPDATE LB_BEFUND SET THEMA = ?, AUSSAGE = ?, WERT = ?, EINHEIT = ?, ART = ?, ABSCHNITT = ?, ORT = " + PUNKT
                + ", ORT_NAME = ?, ZEIT_S = ?, GILT = ?, HERKUNFT = ?, PHASE = ?, GEAENDERT = SYSDATE WHERE BEFUND_ID = ?")) {
            int i = 1;
            p.setString(i++, thema); p.setString(i++, aussage); num(p, i++, wert); str(p, i++, einheit); p.setString(i++, art); str(p, i++, abschnitt);
            punkt(p, i, o);
            i += 3;
            str(p, i++, o == null ? null : ortName); num(p, i++, zeit); p.setString(i++, gilt == null || gilt.isBlank() ? "beide" : gilt); p.setString(i++, herkunft); str(p, i++, phase);
            p.setLong(i, id);
            p.executeUpdate();
        }
        try (PreparedStatement p = c.prepareStatement("DELETE FROM LB_BEFUND_QUELLE WHERE BEFUND_ID = ?")) { p.setLong(1, id); p.executeUpdate(); }
        if (quellen != null && !quellen.isBlank())
            try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_BEFUND_QUELLE (BEFUND_ID, QUELLE_ID, SEITE) VALUES (?, ?, ?)")) {
                for (String qs : quellen.split(",")) {
                    String[] kp = qs.trim().split(":", 2);
                    Long qid = q.get(kp[0]);
                    if (qid == null) throw new SQLException("Befund " + key + ": Quelle " + kp[0] + " fehlt in quellen.tsv");
                    p.setLong(1, id); p.setLong(2, qid); str(p, 3, kp.length > 1 ? kp[1] : null);
                    p.executeUpdate();
                }
            }
    }

    static void befunde(Connection c, Map<String, Long> q) throws Exception {
        int n = 0;
        for (String[] z : tsv("befunde.tsv")) {
            befund(c, q, z[0], z[1], z[2], zahl(z[3]), z[4], z[5], z[6], z[7], zahl(z[8]), z[9], z[10], "mitgeliefert", "19");
            n++;
        }
        log("LB_BEFUND: %d Befunde aus Werkbuch 2", n);
    }

    static void zeugen(Connection c, Map<String, Long> q) throws Exception {
        try (Statement s = c.createStatement()) { s.executeUpdate("DELETE FROM LB_ZEUGE"); }
        Map<String, Integer> nr = new HashMap<>();
        int n = 0;
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_ZEUGE (NAME, ROLLE, BOOT, ZEIT_VON_S, ZEIT_BIS_S, ORT, ORT_NAME, AUSSAGE, ART, QUELLE_ID, SEITE, NR) "
                + "VALUES (?, ?, ?, ?, ?, " + PUNKT + ", ?, ?, ?, ?, ?, ?)")) {
            for (String[] z : tsv("zeugen.tsv")) {
                String[] kp = z[8].split(",")[0].trim().split(":", 2);
                int i = 1;
                p.setString(i++, z[0]); str(p, i++, z[1]); str(p, i++, z[2]); num(p, i++, zahl(z[3])); num(p, i++, zahl(z[4]));
                double[] o = ort(z[5]);
                punkt(p, i, o);
                i += 3;
                str(p, i++, z[5]); p.setString(i++, z[6]); p.setString(i++, z[7]);
                Long qid = q.get(kp[0]);
                if (qid == null) p.setNull(i++, Types.NUMERIC); else p.setLong(i++, qid);
                str(p, i++, kp.length > 1 ? kp[1] : null);
                p.setInt(i, nr.merge(z[0], 1, Integer::sum));
                p.executeUpdate();
                n++;
            }
        }
        log("LB_ZEUGE: %d Aussagen von %d Zeugen", n, nr.size());
    }

    static Map<String, Long> boote(Connection c, Map<String, Long> q) throws Exception {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String[] z : tsv("boote.tsv")) {
            Long id = id(c, "SELECT BOOT_ID FROM LB_BOOT WHERE NAME = ?", z[0]);
            if (id == null) {
                id = neueId(c, "LB_BOOT_SEQ");
                try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_BOOT (BOOT_ID, NAME) VALUES (?, ?)")) { p.setLong(1, id); p.setString(2, z[0]); p.executeUpdate(); }
            }
            try (PreparedStatement p = c.prepareStatement("UPDATE LB_BOOT SET TYP = ?, LAENGE_FT = ?, BESATZUNG = ?, ANKERPLATZ = " + PUNKT
                    + ", TIEFE_FADEN = ?, KETTE_FADEN = ?, AUSGANG = ?, QUELLE_ID = ? WHERE BOOT_ID = ?")) {
                int i = 1;
                p.setString(i++, z[1]); num(p, i++, zahl(z[2])); p.setString(i++, z[3]);
                punkt(p, i, ort(z[4]));
                i += 3;
                num(p, i++, zahl(z[5])); num(p, i++, zahl(z[6])); p.setString(i++, z[7]);
                Long qid = q.get(z[8]);
                if (qid == null) p.setNull(i++, Types.NUMERIC); else p.setLong(i++, qid);
                p.setLong(i, id);
                p.executeUpdate();
            }
            m.put(z[0].toLowerCase(Locale.ROOT), id);
        }
        log("LB_BOOT: %d Boote", m.size());
        return m;
    }

    // ------------------------------------------------------------------ Läufe und Boote

    static void laeufe(Connection c, File daten, Map<String, Long> boote) throws Exception {
        Map<String, Long> q = new HashMap<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT KURZ, QUELLE_ID FROM LB_QUELLE")) { while (r.next()) q.put(r.getString(1), r.getLong(2)); }
        List<Speicher.Lauf> ls = LaufKennzahlen.alle(daten);
        // vorgerechnete Läufe, die es in daten/ereignis nicht mehr gibt (Phase 19: staub und strom waren keine), fallen weg
        try (PreparedStatement d = c.prepareStatement("DELETE FROM LB_EREIGNIS_LAUF WHERE ART = 'vorgerechnet' AND NAME NOT IN (SELECT COLUMN_VALUE FROM TABLE(SYS.ODCIVARCHAR2LIST("
                + "?" + ", ?".repeat(Math.max(0, ls.size() - 1)) + ")))")) {
            for (int i = 0; i < ls.size(); i++) d.setString(i + 1, ls.get(i).name());
            if (!ls.isEmpty()) { int n = d.executeUpdate(); if (n > 0) log("LB_EREIGNIS_LAUF: %d alte Läufe entfernt", n); }
        }
        for (Speicher.Lauf l : ls) {
            OracleSpeicher.lauf(c, l);
            log("LB_EREIGNIS_LAUF %-14s Sporn %s, größter Stand %s, Ankunft Cenotaph %s, Anchorage Cove %s, Eingang %s", l.name(), m(l.auflaufSporn()), m(l.auflaufMax()),
                    s(l.ankunftCenotaph()), s(l.ankunftAnchorage()), s(l.ankunftEingang()));
            String k = "LAUF_" + l.name().toUpperCase(Locale.ROOT);
            if (!Double.isNaN(l.auflaufSporn()))
                befund(c, q, k + "_SPORN", "Auflauf", String.format(Locale.GERMAN, "Lauf %s: Auflauf am Sporn %.0f m (belegt 524 m)", l.name(), l.auflaufSporn()), l.auflaufSporn(), "m",
                        "gerechnet", "9.4", "Sporn (524 m)", Double.NaN, "1958", "APP", "gerechnet", "19");
            if (!Double.isNaN(l.ankunftCenotaph()))
                befund(c, q, k + "_CENOTAPH", "Welle", String.format(Locale.GERMAN, "Lauf %s: die Front an Cenotaph Island %.0f s nach dem Stoß", l.name(), l.ankunftCenotaph()), l.ankunftCenotaph(), "s",
                        "gerechnet", "7.1", "Cenotaph Island", l.ankunftCenotaph(), "1958", "APP", "gerechnet", "19");
            if (!Double.isNaN(l.ankunftAnchorage()))
                befund(c, q, k + "_ANCHORAGE", "Welle", String.format(Locale.GERMAN, "Lauf %s: die Front in der Anchorage Cove %.0f s nach dem Stoß", l.name(), l.ankunftAnchorage()), l.ankunftAnchorage(), "s",
                        "gerechnet", "7.1", "Anchorage Cove", l.ankunftAnchorage(), "1958", "APP", "gerechnet", "19");
        }
        // Lage der Boote je Sekunde
        File bo = new File(new File(daten, "ereignis"), "boote");
        File[] laeufe = bo.listFiles(File::isDirectory);
        if (laeufe != null) for (File d : laeufe) bootLauf(c, d, boote);
    }

    static String m(double v) { return Double.isNaN(v) ? "–" : String.format(Locale.GERMAN, "%.1f m", v); }

    static String s(double v) { return Double.isNaN(v) ? "–" : String.format(Locale.GERMAN, "%.0f s", v); }

    static void bootLauf(Connection c, File dir, Map<String, Long> boote) throws Exception {
        java.util.Properties p = new java.util.Properties();
        File bt = new File(dir, "BOOTE.txt");
        if (!bt.isFile()) return;
        try (java.io.Reader r = Files.newBufferedReader(bt.toPath(), StandardCharsets.UTF_8)) { p.load(r); }
        String lauf = p.getProperty("lauf", dir.getName());
        Long lid = id(c, "SELECT LAUF_ID FROM LB_EREIGNIS_LAUF WHERE NAME = ?", lauf);
        if (lid == null) { log("Boote %s: Lauf %s fehlt in LB_EREIGNIS_LAUF", dir.getName(), lauf); return; }
        for (Map.Entry<String, Long> b : boote.entrySet()) {
            File f = new File(dir, "bahn_" + b.getKey() + ".bin");
            if (!f.isFile()) continue;
            BootBahn bb = BootBahn.lesen(f, b.getKey());
            com.dan.vessel.Boat boat = switch (b.getKey()) { case "edrie" -> com.dan.vessel.Kutter.edrie(); case "badger" -> com.dan.vessel.Kutter.badger(); default -> com.dan.vessel.Kutter.sunmore(); };
            try (PreparedStatement d = c.prepareStatement("DELETE FROM LB_BOOT_LAGE WHERE BOOT_ID = ? AND LAUF_ID = ?")) { d.setLong(1, b.getValue()); d.setLong(2, lid); d.executeUpdate(); }
            List<double[]> pts = new ArrayList<>();
            int n = 0;
            double len = 0;
            double[] last = null;
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO LB_BOOT_LAGE (BOOT_ID, LAUF_ID, T_S, E, N, H, KURS, KRAENGUNG, TRIMM, WASSER_KG, ZUSTAND) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (double t = Math.ceil(bb.anfang()); t <= bb.ende(); t += 1) {
                    double[] z = bb.zustand(t);
                    double[] u = Site.toUtm(z[0], z[2]);
                    boat.pose(new double[]{z[0], z[1], z[2]}, new double[]{z[3], z[4], z[5], z[6]});
                    double[] w = {boat.heading(), boat.roll(), boat.pitch()};   // wie BooteGl.lage
                    ins.setLong(1, b.getValue()); ins.setLong(2, lid); ins.setDouble(3, t); ins.setDouble(4, u[0]); ins.setDouble(5, u[1]); ins.setDouble(6, z[1]);
                    ins.setDouble(7, w[0]); ins.setDouble(8, w[1]); ins.setDouble(9, w[2]); ins.setDouble(10, z[7]); ins.setInt(11, (int) z[8]);
                    ins.addBatch();
                    n++;
                    double[] q = {u[0], u[1], z[1]};
                    if (last != null) len += Math.hypot(q[0] - last[0], q[1] - last[1]);
                    last = q;
                    pts.add(q);
                }
                ins.executeBatch();
            }
            double hub0 = -Double.MAX_VALUE;
            for (double[] q : pts) hub0 = Math.max(hub0, q[2]);
            final double hub = hub0;
            try (PreparedStatement d = c.prepareStatement("DELETE FROM LB_BOOT_BAHN WHERE BOOT_ID = ? AND LAUF_ID = ?")) { d.setLong(1, b.getValue()); d.setLong(2, lid); d.executeUpdate(); }
            linie(c, "INSERT INTO LB_BOOT_BAHN (BOOT_ID, LAUF_ID, VON_S, BIS_S, LAENGE_M, HUB_M, ENDE, BAHN) VALUES (?, ?, ?, ?, ?, ?, ?, SDO_GEOMETRY(3002, 26908, NULL, SDO_ELEM_INFO_ARRAY(1, 2, 1), ?))",
                    pts, x -> {
                        x.setLong(1, b.getValue()); x.setLong(2, lid); x.setDouble(3, bb.anfang()); x.setDouble(4, bb.ende()); x.setDouble(5, len2(pts));
                        num(x, 6, hub); x.setString(7, p.getProperty(b.getKey() + ".ende"));
                    }, 8);
            log("LB_BOOT_LAGE %-8s %s: %d Sekunden, Bahn %.0f m, höchster Stand %.1f m", b.getKey(), lauf, n, len, hub);
        }
    }

    static double len2(List<double[]> pts) {
        double l = 0;
        for (int i = 1; i < pts.size(); i++) l += Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[1] - pts.get(i - 1)[1]);
        return l;
    }

    interface Binder { void bind(PreparedStatement p) throws SQLException; }

    /** Eine 3D-Linie (3002) als Ordinaten-Feld; Punkte {e, n, h}; Punkte näher als 5 cm am vorigen fallen weg (VALIDATE: doppelte Punkte). */
    static void linie(Connection c, String sql, List<double[]> alle, Binder b, int ordIndex) throws SQLException {
        List<double[]> pts = new ArrayList<>();
        for (double[] q : alle) if (pts.isEmpty() || Math.hypot(q[0] - pts.get(pts.size() - 1)[0], q[1] - pts.get(pts.size() - 1)[1]) >= 0.05) pts.add(q);
        if (pts.size() < 2) { pts.clear(); pts.add(alle.get(0)); pts.add(new double[]{alle.get(0)[0] + 0.1, alle.get(0)[1], alle.get(0)[2]}); }
        Object[] o = new Object[pts.size() * 3];
        for (int i = 0; i < pts.size(); i++) for (int k = 0; k < 3; k++) o[i * 3 + k] = java.math.BigDecimal.valueOf(Math.round(pts.get(i)[k] * 1000) / 1000.0);
        try (PreparedStatement p = c.prepareStatement(sql)) {
            b.bind(p);
            Array a = ((oracle.jdbc.OracleConnection) c.unwrap(oracle.jdbc.OracleConnection.class)).createOracleArray("MDSYS.SDO_ORDINATE_ARRAY", o);
            p.setArray(ordIndex, a);
            p.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ Klang, Regie, Fahrten, Texturen, Bilder

    /** Die Laufzeiten aus docs/hoerproben/hoerproben.txt (Phase 18) als gerechnete Befunde. */
    static void hoerproben(Connection c, Map<String, Long> q) throws Exception {
        File f = new File("docs/hoerproben/hoerproben.txt");
        if (!f.isFile()) { log("Hörproben: docs/hoerproben/hoerproben.txt fehlt (ant hoerproben)"); return; }
        java.util.regex.Pattern kopf = java.util.regex.Pattern.compile("^(\\d_[\\w]+): ([^·]+)·"),
                sturz = java.util.regex.Pattern.compile("Sturz in ([\\d,]+) km: .*bei ([\\d,]+) s; hörbarer Einsatz ab ([\\d,]+) s");
        String name = null, text = null;
        int n = 0;
        for (String z : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            java.util.regex.Matcher m = kopf.matcher(z);
            if (m.find()) { name = m.group(1); text = m.group(2).strip(); continue; }
            m = sturz.matcher(z);
            if (m.find() && name != null) {
                double km = Double.parseDouble(m.group(1).replace(',', '.')), soll = Double.parseDouble(m.group(2).replace(',', '.')), ist = Double.parseDouble(m.group(3).replace(',', '.'));
                befund(c, q, "KLANG_" + name.toUpperCase(Locale.ROOT), "Klang", String.format(Locale.GERMAN, "%s: der Abbruch in %.1f km kommt nach der Laufzeit bei %.1f s an, hörbar ab %.1f s",
                        text, km, soll, ist), ist, "s", "gerechnet", "9.13", null, soll, "1958", "APP", "gerechnet", "18");
                n++;
            }
        }
        log("Hörproben: %d Laufzeiten als Befunde", n);
    }

    static void regiebuecher(Connection c) throws Exception {
        int n = 0;
        for (String d : Regie.DATEIEN) {
            String t = Regie.text(d);
            OracleSpeicher.buch(c, new Speicher.Buch(Regie.parse(t).name(), t, true, null));
            n++;
        }
        log("LB_SZENARIO: %d mitgelieferte Regiebücher (mit Stichworten)", n);
    }

    static void fahrten(Connection c, File daten) throws Exception {
        int n = 0;
        for (String w : new String[]{SichtFahrt.RUNDFLUG, SichtFahrt.KAMERAFAHRT}) {
            Fahrt f = SichtFahrt.bauen(daten, w, 1, null);
            if (f == null) { log("LB_KAMERA_FAHRT: %s fehlt (daten/netz/sicht)", w); continue; }
            List<double[]> pts = new ArrayList<>();
            double[][] sp = f.stuetzpunkte();
            for (int i = 0; i < sp.length; i += Math.max(1, sp.length / 4000)) { double[] u = Site.toUtm(sp[i][0], sp[i][2]); pts.add(new double[]{u[0], u[1], sp[i][1]}); }
            try (PreparedStatement d = c.prepareStatement("DELETE FROM LB_KAMERA_FAHRT WHERE NAME = ?")) { d.setString(1, w); d.executeUpdate(); }
            String art = w.equals(SichtFahrt.RUNDFLUG) ? "Rundflug" : "Spannbaum";
            linie(c, "INSERT INTO LB_KAMERA_FAHRT (NAME, ART, TEMPO, DAUER_S, LAENGE_M, PUNKTE, BAHN) VALUES (?, ?, ?, ?, ?, ?, SDO_GEOMETRY(3002, 26908, NULL, SDO_ELEM_INFO_ARRAY(1, 2, 1), ?))",
                    pts, x -> { x.setString(1, w); x.setString(2, art); x.setDouble(3, 1); x.setDouble(4, f.duration); x.setDouble(5, f.length); x.setInt(6, pts.size()); }, 7);
            n++;
        }
        log("LB_KAMERA_FAHRT: %d Fahrten aus LB_NET_SICHT", n);
    }

    /** Was in Werkbuch 8 zu jeder Textur steht: Material, wo. */
    static final Map<String, String[]> TEXTUR_WO = Map.ofEntries(
            Map.entry("strandkies", new String[]{"Strandkies und Gerölle", "Ufer, La Chaussee Spit"}),
            Map.entry("sand", new String[]{"Sand und Gletschermehl", "Flachwasser, Deltas, Fish Lake"}),
            Map.entry("schiefer_verwittert", new String[]{"Schiefer, verwittert (Amphibol-, Biotitschiefer)", "Wände, Grate"}),
            Map.entry("schiefer_frisch", new String[]{"Schiefer, frischer Bruch", "Rutschnarbe, abgeräumte Hänge"}),
            Map.entry("moraene", new String[]{"Moräne, Schutt, Geröll", "Vorfeld, Kegel"}),
            Map.entry("waldboden", new String[]{"Waldboden mit Nadeln und Moos", "Regenwald"}),
            Map.entry("moos", new String[]{"Moos, Bartflechte", "Boden, Stämme, Steine"}),
            Map.entry("firn", new String[]{"Firn und Schnee", "Gipfel, Firnfelder"}),
            Map.entry("eis", new String[]{"Gletschereis", "Fronten, Spalten"}),
            Map.entry("rinde", new String[]{"Rinde Sitka-Fichte, Hemlock", "Bäume"}),
            Map.entry("holz_entrindet", new String[]{"entrindetes Holz", "Stämme im Band 1958, Treibholz"}),
            Map.entry("kutter_holz", new String[]{"Kutter: Holz, Farbe", "vessel"}),
            Map.entry("kutter_tau", new String[]{"Kutter: Tau", "vessel"}),
            Map.entry("seetang", new String[]{"Seetang, Kelp", "Ufer, Wasser"}));

    static void texturen(Connection c, File daten, Map<String, Long> q) throws Exception {
        File lz = new File(new File(daten, "texturen"), "LIZENZEN.txt");
        if (!lz.isFile()) { log("LB_TEXTUR: daten/texturen/LIZENZEN.txt fehlt"); return; }
        int n = 0;
        for (String z : Files.readAllLines(lz.toPath(), StandardCharsets.UTF_8)) {
            if (z.isBlank() || z.startsWith("#")) continue;
            String[] f = z.split("\t", -1);
            String name = f[0];
            String quelle = f.length > 1 ? f[1] : "", satz = quelle.startsWith("ambientCG ") ? quelle.substring(10) : null;
            File dir = new File(new File(daten, "texturen"), name);
            String[] wo = TEXTUR_WO.getOrDefault(name, new String[]{name, null});
            try (PreparedStatement d = c.prepareStatement("DELETE FROM LB_TEXTUR WHERE NAME = ?")) { d.setString(1, name); d.executeUpdate(); }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_TEXTUR (NAME, MATERIAL, WO, QUELLE, SATZ, URL, LIZENZ, AUFLOESUNG, DATEI, PRUEFSUMME, QUELLE_ID) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                p.setString(1, name); p.setString(2, wo[0]); str(p, 3, wo[1]); str(p, 4, quelle); str(p, 5, satz); str(p, 6, f.length > 3 ? f[3] : null); str(p, 7, f.length > 2 ? f[2] : null);
                str(p, 8, satz != null ? "2K-JPG" : null); p.setString(9, "daten/texturen/" + name); str(p, 10, dir.isDirectory() ? summe(dir) : null);
                Long qid = satz != null ? q.get("AMBIENTCG") : q.get("APP");
                if (qid == null) p.setNull(11, Types.NUMERIC); else p.setLong(11, qid);
                p.executeUpdate();
            }
            n++;
        }
        log("LB_TEXTUR: %d Texturen mit Lizenz", n);
    }

    /** SHA-256 über die Dateien eines Ordners (Namen sortiert), gekürzt auf 64 Zeichen. */
    static String summe(File dir) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        File[] fs = dir.listFiles(File::isFile);
        if (fs == null) return null;
        java.util.Arrays.sort(fs);
        for (File f : fs) { md.update(f.getName().getBytes(StandardCharsets.UTF_8)); md.update(Files.readAllBytes(f.toPath())); }
        StringBuilder b = new StringBuilder();
        for (byte x : md.digest()) b.append(String.format("%02x", x));
        return b.toString();
    }

    /** Je Regiebuch das neueste Blatt aus docs/status (Regiebuch_<Name>_<Zeit>.png) als Schnappschuss. */
    static void blaetter(Connection c) throws Exception {
        File st = new File("docs/status");
        File[] fs = st.listFiles((d, n) -> n.startsWith("Regiebuch_") && n.endsWith(".png"));
        if (fs == null) return;
        Map<String, File> neu = new HashMap<>();
        for (File f : fs) {
            String n = f.getName().replaceFirst("^Regiebuch_", "").replaceFirst("_\\d{8}_\\d{6}\\.png$", "");
            if (n.equals(f.getName())) continue;
            File o = neu.get(n);
            if (o == null || f.getName().compareTo(o.getName()) > 0) neu.put(n, f);
        }
        Map<String, String> namen = new HashMap<>();
        for (Regie.Book b : Regie.all()) namen.put(b.name().replace("ü", "ue").replace("ö", "oe").replace("ä", "ae").replace("ß", "ss").replace("é", "e").replace(' ', '_'), b.name());
        int n = 0;
        for (Map.Entry<String, File> e : neu.entrySet()) {
            BufferedImage im = ImageIO.read(e.getValue());
            if (im == null) continue;
            byte[] png = Files.readAllBytes(e.getValue().toPath());
            OracleSpeicher.bild(c, new Speicher.Bild("Blatt", "docs/status/" + e.getValue().getName(), png, vorschau(im), im.getWidth(), im.getHeight(), null, Double.NaN, null,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, namen.get(e.getKey()), Double.NaN, "Blatt des Regiebuchs (Phase 17a/18)"));
            n++;
        }
        log("LB_SCHNAPPSCHUSS: %d Blätter der Regiebücher (je Buch das neueste)", n);
    }

    static byte[] vorschau(BufferedImage im) throws IOException { return com.dan.lituya.data.Bilder.vorschau(im); }

    /** Was die App ohne Datenbank in daten/speicher gesichert hat (Sitzungen, Läufe, eigene Regiebücher, Bilder), einmal. */
    static void uebernahme(Connection c, File daten) throws Exception {
        DateiSpeicher d = (DateiSpeicher) Speicher.datei();
        File merk = new File(d.ordner(), "uebernommen.txt");
        java.util.Set<String> schon = new java.util.HashSet<>(merk.isFile() ? Files.readAllLines(merk.toPath(), StandardCharsets.UTF_8) : List.of());
        List<String> neu = new ArrayList<>();
        int ns = 0, nl = 0, nb = 0, ni = 0;
        for (Speicher.Sitzung s : d.sitzungen()) {
            String key = "sitzung:" + s.id();
            if (schon.contains(key)) continue;
            Speicher.Sitzung voll = d.sitzung(s.id());
            sitzungOhneCommit(c, voll);
            neu.add(key);
            ns++;
        }
        for (Speicher.Lauf l : d.laeufe()) { if (schon.add("lauf:" + l.name())) { OracleSpeicher.lauf(c, l); neu.add("lauf:" + l.name()); nl++; } }
        for (Speicher.Buch b : d.regiebuecher()) { if (schon.add("buch:" + b.name() + ":" + b.text().hashCode())) { OracleSpeicher.buch(c, b); neu.add("buch:" + b.name() + ":" + b.text().hashCode()); nb++; } }
        for (String[] z : d.bildZeilen()) {
            if (!schon.add("bild:" + z[1])) continue;
            File f = new File(new File(d.ordner(), "bilder"), new File(z[1]).getName());
            byte[] png = f.isFile() ? Files.readAllBytes(f.toPath()) : null;
            BufferedImage im = png == null ? null : ImageIO.read(new java.io.ByteArrayInputStream(png));
            OracleSpeicher.bild(c, new Speicher.Bild(z[0], z[1], png, im == null ? null : vorschau(im), (int) zahl(z[2]), (int) zahl(z[3]), z[4], zahl(z[5]), z[6], zahl(z[7]), zahl(z[8]),
                    zahl(z[9]), zahl(z[10]), zahl(z[11]), z[12].isEmpty() ? null : z[12], zahl(z[13]), z.length > 14 ? z[14] : null));
            neu.add("bild:" + z[1]);
            ni++;
        }
        c.commit();
        if (!neu.isEmpty()) Files.write(merk.toPath(), neu, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        log("aus daten/speicher übernommen: %d Sitzungen, %d Läufe, %d eigene Regiebücher, %d Bilder", ns, nl, nb, ni);
    }

    static void sitzungOhneCommit(Connection c, Speicher.Sitzung s) throws Exception {
        java.io.StringWriter w = new java.io.StringWriter();
        s.zustand().store(w, null);
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_SITZUNG (NAME, RECHNER, KAMERA, HOEHE_M, RICHTUNG, NEIGUNG, MODUS, UHR_MS, ZEITSTAND, RAFFER, ZUSTAND) "
                + "VALUES (?, ?, " + PUNKT + ", ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 1;
            p.setString(i++, s.name()); str(p, i++, s.rechner());
            punkt(p, i, Double.isNaN(s.e()) ? null : new double[]{s.e(), s.n()});
            i += 3;
            num(p, i++, s.h()); num(p, i++, s.richtung()); num(p, i++, s.neigung()); str(p, i++, s.modus()); p.setLong(i++, s.uhrMs()); str(p, i++, s.zeitstand()); num(p, i++, s.raffer());
            p.setString(i, w.toString());
            p.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ Prüfung

    static boolean pruefen(Connection c, Db.Konfig k) throws Exception {
        boolean ok = true;
        java.util.Set<String> t = Db.namen(c, "SELECT TABLE_NAME FROM USER_TABLES"), v = Db.namen(c, "SELECT VIEW_NAME FROM USER_VIEWS");
        for (String x : TABELLEN) if (!t.contains(x)) { log("FEHLER: Tabelle %s fehlt", x); ok = false; }
        for (String x : SICHTEN) if (!v.contains(x)) { log("FEHLER: Sicht %s fehlt", x); ok = false; }
        if (!ok) return false;
        StringBuilder z = new StringBuilder("Zeilen:");
        for (String x : TABELLEN) z.append(' ').append(x.substring(3)).append(' ').append(Db.einWert(c, "SELECT COUNT(*) FROM " + x));
        log("%s", z);
        String[][] soll = {
                {"Quellen ≥ 26", "SELECT CASE WHEN COUNT(*) >= 26 THEN 'ja' ELSE 'nein' END FROM LB_QUELLE"},
                {"jeder mitgelieferte Befund hat eine Quelle", "SELECT CASE WHEN COUNT(*) = 0 THEN 'ja' ELSE 'nein' END FROM LB_BEFUND b WHERE HERKUNFT = 'mitgeliefert' AND NOT EXISTS (SELECT 1 FROM LB_BEFUND_QUELLE q WHERE q.BEFUND_ID = b.BEFUND_ID)"},
                {"Befunde mit Ort in 4326 (Länge um −137,5°)", "SELECT CASE WHEN COUNT(*) > 0 AND MIN(t.X) > -138 AND MAX(t.X) < -137 THEN 'ja' ELSE 'nein' END FROM LB_V_BEFUND b, TABLE(SDO_UTIL.GETVERTICES(b.ORT_4326)) t WHERE b.ORT_4326 IS NOT NULL"},
                {"drei Boote mit Ankerplatz", "SELECT CASE WHEN COUNT(*) = 3 THEN 'ja' ELSE 'nein' END FROM LB_BOOT WHERE ANKERPLATZ IS NOT NULL"},
                {"fünf Regiebücher mit Stichworten", "SELECT CASE WHEN COUNT(DISTINCT z.SZENARIO_ID) >= 5 THEN 'ja' ELSE 'nein' END FROM LB_SZENARIO z JOIN LB_SZENARIO_SCHRITT s ON s.SZENARIO_ID = z.SZENARIO_ID"},
                {"Texturen mit Lizenz", "SELECT CASE WHEN COUNT(*) >= 13 AND COUNT(*) = COUNT(LIZENZ) THEN 'ja' ELSE 'nein' END FROM LB_TEXTUR"},
                {"Bahnen der Boote und Kamerafahrten gültig (VALIDATE in 2D)", "SELECT NVL(LISTAGG(CASE WHEN v <> 'TRUE' THEN n || ': ' || SUBSTR(v, 1, 60) END, '; ') WITHIN GROUP (ORDER BY n), 'ja') FROM ("
                        + "SELECT b.NAME || ' ' || l.NAME AS n, SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(SDO_CS.MAKE_2D(h.BAHN), 0.005) AS v FROM LB_BOOT_BAHN h JOIN LB_BOOT b ON b.BOOT_ID = h.BOOT_ID "
                        + "JOIN LB_EREIGNIS_LAUF l ON l.LAUF_ID = h.LAUF_ID UNION ALL SELECT NAME, SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(SDO_CS.MAKE_2D(BAHN), 0.005) FROM LB_KAMERA_FAHRT)"}};
        for (String[] s : soll) {
            String r;
            try { r = Db.einWert(c, s[1]); } catch (SQLException e) { r = "Fehler: " + Db.erste(e); }
            boolean g = "ja".equals(r);
            ok &= g;
            log("  %s %s", g ? "ok    " : "FEHLER", s[0] + (g ? "" : " (" + r + ")"));
        }
        // die Bahnen auch in 3D (nur als Hinweis: 26908 ist ein 2D-System)
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT b.NAME, SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(h.BAHN, 0.005) FROM LB_BOOT_BAHN h JOIN LB_BOOT b ON b.BOOT_ID = h.BOOT_ID "
                + "UNION ALL SELECT NAME, SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(BAHN, 0.005) FROM LB_KAMERA_FAHRT")) {
            StringBuilder b3 = new StringBuilder();
            while (r.next()) b3.append(b3.length() == 0 ? "" : "; ").append(r.getString(1)).append(' ').append(NetzDb.kurz(r.getString(2), 60));
            log("  Hinweis VALIDATE in 3D: %s", b3);
        } catch (SQLException e) { log("  Hinweis VALIDATE in 3D: %s", Db.erste(e)); }
        // Läufe gegen den Beleg
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT NAME, AUFLAUF_SPORN_M, SPORN_BELEGT_M, CENOTAPH_S, ANCHORAGE_S, EINGANG_S FROM LB_V_LAUF_VERGLEICH ORDER BY NAME")) {
            while (r.next()) log("  Lauf %-14s Sporn %s (belegt %s m), Cenotaph %s s, Anchorage Cove %s s, Eingang %s s", r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6));
        }
        // Speichern und Laden über die App-Schnittstelle (eine Probe, danach weg)
        OracleSpeicher o = new OracleSpeicher(k);
        java.util.Properties z0 = new java.util.Properties();
        z0.setProperty("probe.wert", "äöü ß 524");
        z0.setProperty("renderer.showMarks", "false");
        long id = o.sitzungSpeichern(new Speicher.Sitzung(0, "Probe szenedb", null, "szenedb", Site.ANCHORAGE.e(), Site.ANCHORAGE.n(), 3, 90, -2, "FREI", 0, "VOR", 1, "kette", "Zehn Minuten", z0));
        Speicher.Sitzung back = o.sitzung(id);
        boolean rund = back != null && "äöü ß 524".equals(back.zustand().getProperty("probe.wert")) && Math.abs(back.e() - Site.ANCHORAGE.e()) < 0.01 && "kette".equals(back.lauf())
                && "Zehn Minuten".equals(back.regiebuch());
        try (PreparedStatement p = c.prepareStatement("DELETE FROM LB_SITZUNG WHERE SITZUNG_ID = ?")) { p.setLong(1, id); p.executeUpdate(); }
        c.commit();
        ok &= rund;
        log("  %s Sitzung speichern und laden (Umlaute, Ort, Lauf, Regiebuch) über OracleSpeicher", rund ? "ok    " : "FEHLER");
        log("  Stand: %s", o.zahlen());
        return ok;
    }
}
