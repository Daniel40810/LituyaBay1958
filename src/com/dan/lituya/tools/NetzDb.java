package com.dan.lituya.tools;

import com.dan.lituya.data.Verbindung;
import com.dan.lituya.netz.Lod;
import com.dan.store.Db;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Die Netze in Oracle (Werkbuch 7.9, Phase 3): {@code ant netzdb} baut das Schema {@code LITUYABAY1958} neu auf, lädt
 * {@code LB_NET_HERKUNFT} aus {@code daten/netz/herkunft/}, macht die NDM-Funktionsprobe (2.8, SQL und Java/LOD) und prüft
 * das Netz zweimal nach (CONNECT BY und traceIn mit LOD gegen {@code pruefwege.tsv}). {@code ant dbpruefung} prüft nur:
 * passen Prüfsumme, Zahlen und VALIDATE_NETWORK zu den Dateien?
 * <p>
 * Reihenfolge: 99_abbau, 01_tabellen, 02_netze, Laden, 03_probe, Java-Probe, 98_probe_abbau, 04_nach_dem_laden,
 * 05_sichten, VALIDATE_NETWORK, Gegenrechnung, LB_NETZ_STAND. Die Rechte (00_sys.sql) gibt SYS einmal in SQL Developer.
 * Protokoll nach {@code docs/status/netzdb_Phase3.txt}.
 */
public final class NetzDb {
    private NetzDb() { }

    public static final String NETZ = "LB_NET_HERKUNFT";
    static final List<String> PROTOKOLL = new ArrayList<>();

    static void log(String f, Object... a) {
        String s = a.length == 0 ? f : String.format(Locale.GERMAN, f, a);
        System.out.println(s);
        PROTOKOLL.add(s);
    }

    public static void main(String[] a) throws Exception {
        boolean nurPruefen = a.length > 0 && a[0].equalsIgnoreCase("pruefen");
        File daten = new File(System.getProperty("lituya.daten", "daten"));
        File dir = new File(daten, Herkunft.ORDNER);
        File sql = new File(System.getProperty("lituya.sql", "db/netz"));
        boolean ok;
        log("=== %s LituyaBay1958 (Phase 3), %s", nurPruefen ? "dbpruefung" : "netzdb", java.time.LocalDateTime.now().withNano(0));
        if (!new File(dir, "knoten.tsv").isFile())
            throw new IOException("Herkunftsnetz fehlt: " + dir + " (ant aufbereitung -Dschritte=herkunft,inhalt)");
        Herkunft h = Herkunft.lesen(dir);
        String f = h.fehler();
        if (f != null) throw new IllegalStateException("Herkunftsnetz in Dateien fehlerhaft: " + f);
        String summe = Herkunft.pruefsumme(dir);
        log("Dateien: %d Knoten, %d Kanten, Prüfsumme %s…", h.knotenListe().size(), h.kantenListe().size(), summe.substring(0, 16));
        Db.Konfig k = Verbindung.konfig();
        if (!k.hatPasswort()) throw new IOException("kein Passwort in db/db.properties und keine Umgebungsvariable " + Verbindung.ENV);
        // Befund Phase 3: Db wartet sonst 30 s auf eine Antwort (oracle.jdbc.ReadTimeout); LOGICAL_PARTITION und
        // GENERATE_PARTITION_BLOBS brauchen länger. 0 = ohne Grenze.
        Db.antwortS = 0;
        try (Connection c = Db.oeffnen(k)) {
            log("verbunden: %s als %s, %s", k.url, Db.einWert(c, "SELECT USER FROM DUAL"), c.getMetaData().getDatabaseProductVersion().split("\\R")[0]);
            try {
                ok = nurPruefen ? pruefen(c, h, summe) : laden(c, k, h, dir, sql, summe);
            } catch (Exception e) {
                ok = false;
                log("FEHLER: %s", ursache(e));
                if (e instanceof SQLException q) for (String z : String.valueOf(q.getMessage()).split("\\R")) if (z.startsWith("ORA-")) log("  %s", z);
                try { c.rollback(); } catch (SQLException x) { /* egal */ }
                probeTabelle(c);
            }
        }
        log("=== %s", ok ? "ok" : "FEHLER (siehe oben)");
        File st = new File("docs/status");
        if (st.isDirectory())
            Files.write(new File(st, (nurPruefen ? "dbpruefung" : "netzdb") + "_Phase3.txt").toPath(), PROTOKOLL, StandardCharsets.UTF_8);
        System.exit(ok ? 0 : 1);
    }

    static boolean laden(Connection c, Db.Konfig k, Herkunft h, File dir, File sql, String summe) throws Exception {
        c.setAutoCommit(false);
        skript(c, new File(sql, "99_abbau.sql"), false);
        skript(c, new File(sql, "01_tabellen.sql"), true);
        skript(c, new File(sql, "02_netze.sql"), true);
        long t0 = System.nanoTime();
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + NETZ + "_NODE$ (NODE_ID, NODE_NAME, ACTIVE, ART, PFAD, BYTES, SHA256, QUELLE) VALUES (?, ?, 'Y', ?, ?, ?, ?, ?)")) {
            int n = 0;
            for (Herkunft.Knoten x : h.knotenListe()) {
                p.setInt(1, x.id());
                p.setString(2, kurz(x.name(), 400));
                p.setString(3, x.art());
                p.setString(4, kurz(x.pfad(), 400));
                p.setLong(5, x.bytes());
                p.setString(6, x.sha());
                p.setString(7, kurz(x.quelle(), 60));
                p.addBatch();
                if (++n % 1000 == 0) p.executeBatch();
            }
            p.executeBatch();
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + NETZ + "_LINK$ (LINK_ID, START_NODE_ID, END_NODE_ID, LINK_NAME, ACTIVE, COST, ART) VALUES (?, ?, ?, ?, 'Y', 1, ?)")) {
            int n = 0;
            for (Herkunft.Kante x : h.kantenListe()) {
                p.setInt(1, x.id());
                p.setInt(2, x.von());
                p.setInt(3, x.nach());
                p.setString(4, x.art());
                p.setString(5, x.art());
                p.addBatch();
                if (++n % 1000 == 0) p.executeBatch();
            }
            p.executeBatch();
        }
        c.commit();
        log("geladen: %s in %.1f s", NETZ, (System.nanoTime() - t0) / 1e9);
        // NDM-Funktionsprobe
        skript(c, new File(sql, "03_probe.sql"), true);
        boolean javaOk = javaProbe(c, k);
        skript(c, new File(sql, "98_probe_abbau.sql"), true);
        skript(c, new File(sql, "04_nach_dem_laden.sql"), true);
        skript(c, new File(sql, "05_sichten.sql"), true);
        // Prüfungen
        String v = validate(c);
        log("VALIDATE_NETWORK(%s) = %s", NETZ, v);
        boolean gut = "TRUE".equalsIgnoreCase(v);
        gut &= zahlen(c, h);
        gut &= connectBy(c, dir);
        gut &= lodGegenrechnung(k, h, dir);
        // Stand
        try (PreparedStatement p = c.prepareStatement("""
                MERGE INTO LB_NETZ_STAND t USING (SELECT ? NETZ FROM DUAL) s ON (t.NETZ = s.NETZ)
                 WHEN MATCHED THEN UPDATE SET INHALT = ?, KNOTEN = ?, KANTEN = ?, PRUEFSUMME = ?, QUELLE = ?, GELADEN = SYSDATE, GUELTIG = ?
                 WHEN NOT MATCHED THEN INSERT (NETZ, INHALT, KNOTEN, KANTEN, PRUEFSUMME, QUELLE, GUELTIG) VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
            String inhalt = "Herkunft (Werkbuch 7.7): Quelle, Rohdatei, Schritt, Ausgabe, Objekt, Befund";
            String gueltig = "VALIDATE " + v + "; Gegenrechnung " + (gut ? "ok" : "FEHLER") + "; Probe Java " + (javaOk ? "ok" : "mit N");
            Object[] w = {NETZ, inhalt, h.knotenListe().size(), h.kantenListe().size(), summe, "daten/" + Herkunft.ORDNER, gueltig};
            p.setString(1, NETZ);
            for (int i = 1; i < w.length; i++) p.setObject(i + 1, w[i]);
            for (int i = 0; i < w.length; i++) p.setObject(i + 8, w[i]);
            p.executeUpdate();
        }
        c.commit();
        probeTabelle(c);
        return gut;
    }

    static boolean pruefen(Connection c, Herkunft h, String summe) throws SQLException {
        boolean gut = true;
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT PRUEFSUMME, KNOTEN, KANTEN, TO_CHAR(GELADEN, 'YYYY-MM-DD HH24:MI'), GUELTIG FROM LB_NETZ_STAND WHERE NETZ = '" + NETZ + "'")) {
            if (!r.next()) { log("FEHLER: kein Eintrag in LB_NETZ_STAND (ant netzdb)"); return false; }
            boolean gleich = summe.equals(r.getString(1));
            log("LB_NETZ_STAND: geladen %s, %s; Prüfsumme %s den Dateien", r.getString(4), r.getString(5), gleich ? "gleich" : "UNGLEICH");
            gut = gleich;
        }
        String v = validate(c);
        log("VALIDATE_NETWORK(%s) = %s", NETZ, v);
        gut &= "TRUE".equalsIgnoreCase(v);
        gut &= zahlen(c, h);
        probeTabelle(c);
        return gut;
    }

    static String validate(Connection c) throws SQLException {
        try (CallableStatement s = c.prepareCall("BEGIN ? := SDO_NET.VALIDATE_NETWORK(?); END;")) {
            s.registerOutParameter(1, Types.VARCHAR);
            s.setString(2, NETZ);
            s.execute();
            return s.getString(1);
        }
    }

    static boolean zahlen(Connection c, Herkunft h) throws SQLException {
        int kn = Integer.parseInt(Db.einWert(c, "SELECT COUNT(*) FROM " + NETZ + "_NODE$"));
        int ka = Integer.parseInt(Db.einWert(c, "SELECT COUNT(*) FROM " + NETZ + "_LINK$"));
        boolean ok = kn == h.knotenListe().size() && ka == h.kantenListe().size();
        log("Zahlen in Oracle: %d Knoten, %d Kanten – %s", kn, ka, ok ? "wie die Dateien" : "ANDERS als die Dateien");
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT ART, COUNT(*) FROM " + NETZ + "_NODE$ GROUP BY ART ORDER BY ART")) {
            StringBuilder b = new StringBuilder();
            while (r.next()) b.append(r.getString(1)).append(' ').append(r.getInt(2)).append(", ");
            log("  Knoten je Art: %s", b.length() > 2 ? b.substring(0, b.length() - 2) : "");
        }
        return ok;
    }

    /** Zweiter Weg in SQL: Rohdateien vor jeder Ausgabe mit CONNECT BY, gegen pruefwege.tsv. */
    static boolean connectBy(Connection c, File dir) throws Exception {
        Map<Integer, Integer> soll = Herkunft.pruefwege(dir);
        Map<Integer, Integer> ist = new HashMap<>();
        long t0 = System.nanoTime();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("""
                SELECT z.ZIEL, COUNT(DISTINCT z.VON)
                  FROM (SELECT CONNECT_BY_ROOT l.END_NODE_ID AS ZIEL, l.START_NODE_ID AS VON
                          FROM LB_NET_HERKUNFT_LINK$ l
                         START WITH l.END_NODE_ID IN (SELECT NODE_ID FROM LB_NET_HERKUNFT_NODE$ WHERE ART = 'AUSGABE')
                       CONNECT BY NOCYCLE PRIOR l.START_NODE_ID = l.END_NODE_ID) z
                  JOIN LB_NET_HERKUNFT_NODE$ n ON n.NODE_ID = z.VON AND n.ART = 'ROHDATEI'
                 GROUP BY z.ZIEL""")) {
            while (r.next()) ist.put(r.getInt(1), r.getInt(2));
        }
        int falsch = 0;
        for (var e : soll.entrySet()) if (!e.getValue().equals(ist.getOrDefault(e.getKey(), 0))) falsch++;
        log("Gegenrechnung CONNECT BY: %d Ausgaben, %d abweichend (%.1f s)", soll.size(), falsch, (System.nanoTime() - t0) / 1e9);
        return falsch == 0;
    }

    /** Dritter Weg: traceIn mit LOD (Treiber 21, eigener Lader) für eine Auswahl von Ausgaben. */
    @SuppressWarnings("unchecked")
    static boolean lodGegenrechnung(Db.Konfig k, Herkunft h, File dir) throws IOException {
        Map<Integer, Integer> soll = Herkunft.pruefwege(dir);
        List<Integer> aus = new ArrayList<>(soll.keySet());
        List<Integer> wahl = new ArrayList<>();
        int schritt = Math.max(1, aus.size() / 25);
        for (int i = 0; i < aus.size(); i += schritt) wahl.add(aus.get(i));
        for (Herkunft.Knoten x : h.knotenListe())
            if (x.art().equals("AUSGABE") && (x.name().startsWith("grund/") || x.name().equals("namen.tsv")) && !wahl.contains(x.id())) wahl.add(x.id());
        long[] ziele = wahl.stream().mapToLong(Integer::longValue).toArray();
        long[] roh = h.knotenListe().stream().filter(x -> x.art().equals("ROHDATEI")).mapToLong(Herkunft.Knoten::id).sorted().toArray();
        try (URLClassLoader cl = Lod.lader(klassen())) {
            Class<?> kern = Class.forName("com.dan.lituya.netz.NdmProbeKern", true, cl);
            long t0 = System.nanoTime();
            long[] ist = (long[]) kern.getMethod("herkunft", String.class, String.class, String.class, long[].class, long[].class)
                    .invoke(null, k.url, k.user, passwort(k), ziele, roh);
            int falsch = 0;
            for (int i = 0; i < ziele.length; i++) if (ist[i] != soll.get((int) ziele[i])) falsch++;
            log("Gegenrechnung LOD traceIn: %d Ausgaben, %d abweichend (%.1f s)", ziele.length, falsch, (System.nanoTime() - t0) / 1e9);
            return falsch == 0;
        } catch (Exception | LinkageError e) {
            log("FEHLER Gegenrechnung LOD: %s", ursache(e));
            return false;
        }
    }

    /** Der Java-Teil der Funktionsprobe auf LB_PROBE_RAUM; schreibt nach LB_NDM_PROBE (WEG = JAVA). */
    @SuppressWarnings("unchecked")
    static boolean javaProbe(Connection c, Db.Konfig k) throws SQLException {
        List<String[]> l;
        try (URLClassLoader cl = Lod.lader(klassen())) {
            Class<?> kern = Class.forName("com.dan.lituya.netz.NdmProbeKern", true, cl);
            l = (List<String[]>) kern.getMethod("probe", String.class, String.class, String.class).invoke(null, k.url, k.user, passwort(k));
        } catch (Exception | LinkageError e) {
            l = List.<String[]>of(new String[]{"10", "LOD-Zugang", "LODNetworkManager.getCachedNetworkIO auf LB_PROBE_RAUM", ursache(e), "N"});
        }
        boolean ok = true;
        try (CallableStatement s = c.prepareCall("BEGIN LB_PROBE_SETZEN(?, ?, ?, ?, ?, 'JAVA'); END;")) {
            for (String[] z : l) {
                s.setInt(1, Integer.parseInt(z[0]));
                for (int i = 1; i < 5; i++) s.setString(i + 1, kurz(z[i], i == 3 ? 4000 : 400));
                s.execute();
                ok &= z[4].equals("J");
            }
        }
        log("Funktionsprobe Java (LOD): %d Prüfungen, %s", l.size(), ok ? "alle J" : "nicht alle J");
        return ok;
    }

    static String ursache(Throwable e) {
        while ((e instanceof InvocationTargetException || e.getCause() != null && e.getMessage() == null) && e.getCause() != null) e = e.getCause();
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /** Wo die Klassen des Projekts liegen (build/classes oder das Jar), für den Lader von LOD. */
    static URL klassen() {
        return NetzDb.class.getProtectionDomain().getCodeSource().getLocation();
    }

    /** Das Passwort für LOD (eigener Treiber, eigene Verbindung): db.properties, sonst die Umgebungsvariable. */
    static String passwort(Db.Konfig k) throws IOException {
        Properties p = new Properties();
        if (k.datei != null && k.datei.isFile()) try (var in = Files.newBufferedReader(k.datei.toPath(), StandardCharsets.UTF_8)) { p.load(in); }
        String s = p.getProperty("password", "");
        if (s.isEmpty()) s = System.getenv(Verbindung.ENV) != null ? System.getenv(Verbindung.ENV) : "";
        if (s.isEmpty()) s = System.getenv(Verbindung.ENV.toUpperCase(Locale.ROOT)) != null ? System.getenv(Verbindung.ENV.toUpperCase(Locale.ROOT)) : "";
        return s;
    }

    static void probeTabelle(Connection c) throws SQLException {
        log("--- LB_NDM_PROBE (Werkbuch 2.8)");
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT NR, FAEHIGKEIT, OK, WEG, ERGEBNIS FROM LB_NDM_PROBE ORDER BY NR, FAEHIGKEIT")) {
            while (r.next()) log("%3d  %-34s %s  %-4s  %s", r.getInt(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5));
        } catch (SQLException e) {
            log("(LB_NDM_PROBE nicht lesbar: %s)", Db.erste(e));
        }
    }

    /** Ein Skript aus db/netz: Anweisungen enden mit einer Zeile „/“. */
    public static List<String> anweisungen(String text) {
        List<String> l = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        for (String z : text.split("\\R", -1)) {
            if (z.strip().equals("/")) {
                String s = ohneKommentar(b.toString());
                if (!s.isBlank()) l.add(b.toString().strip());
                b.setLength(0);
            } else b.append(z).append('\n');
        }
        if (!ohneKommentar(b.toString()).isBlank()) throw new IllegalArgumentException("Anweisung ohne abschließendes „/“: " + b.toString().strip());
        return l;
    }

    static String ohneKommentar(String s) {
        StringBuilder b = new StringBuilder();
        for (String z : s.split("\\R")) if (!z.strip().startsWith("--")) b.append(z).append('\n');
        return b.toString();
    }

    static void skript(Connection c, File f, boolean streng) throws Exception {
        List<String> l = anweisungen(Files.readString(f.toPath(), StandardCharsets.UTF_8));
        long t0 = System.nanoTime();
        int fehler = 0;
        try (Statement s = c.createStatement()) {
            s.setEscapeProcessing(false);
            for (String a : l) {
                try {
                    s.execute(a);
                } catch (SQLException e) {
                    fehler++;
                    String kopf = ohneKommentar(a).strip().split("\\R")[0];
                    log("  %s: %s bei: %s", streng ? "FEHLER" : "Hinweis", Db.erste(e), kurz(kopf, 100));
                    if (streng) { c.rollback(); throw new SQLException(f.getName() + ": " + Db.erste(e), e); }
                }
            }
        }
        c.commit();
        log("%s: %d Anweisungen%s (%.1f s)", f.getName(), l.size(), fehler > 0 ? ", " + fehler + " Hinweise" : "", (System.nanoTime() - t0) / 1e9);
    }

    static String kurz(String s, int n) {
        if (s == null) return null;
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
