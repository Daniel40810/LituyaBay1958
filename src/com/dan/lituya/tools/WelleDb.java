package com.dan.lituya.tools;

import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.UferNetz;
import com.dan.lituya.scene.WellenNetz;
import com.dan.route.Contour;
import com.dan.store.Db;

import java.io.BufferedReader;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Die Netze der Phase 7a in Oracle (Werkbuch 7.1, 7.2), aufgerufen von {@link NetzDb}: {@code LB_NET_WELLE} und
 * {@code LB_NET_UFER} aus {@code daten/netz/} laden, dann die Zweitrechnung: LOD ({@code netz.WelleLod}, eigener
 * Klassenlader) gegen {@code com.dan.route} (die Ankunft in der Datei), Ergebnisse nach {@code LB_ANKUNFT} und
 * {@code LB_ISOCHRONE}, das Ufer mit {@code SDO_LRS} gegen {@code com.dan.route.Measure}.
 */
public final class WelleDb {
    private WelleDb() { }

    public static final String WELLE = "LB_NET_WELLE", UFER = "LB_NET_UFER";
    /** Zeiten der Isochronen (s): Konturen alle 30 s bis 10 min; Flächen (fastWithinCostPolygon) jede Minute bis 6 min. */
    static final double[] KONTUR = new double[20], PUFFER = {60, 120, 180, 240, 300, 360};
    static { for (int i = 0; i < KONTUR.length; i++) KONTUR[i] = 30 * (i + 1); }

    /** Die Kanten der Welle aus kanten.tsv: Nummer, Anfang, Ende, Länge, vier Kosten. */
    public static final class Kanten {
        public long[] id, von, nach;
        public double[] laenge;
        public double[][] kosten = new double[4][];
        public int max;

        public static Kanten lesen(File dir) throws Exception {
            Kanten k = new Kanten();
            List<double[]> z = new ArrayList<>(700_000);
            try (BufferedReader r = Files.newBufferedReader(new File(dir, "kanten.tsv").toPath(), StandardCharsets.UTF_8)) {
                r.readLine();
                for (String s; (s = r.readLine()) != null; ) {
                    if (s.isBlank()) continue;
                    String[] f = s.split("\t");
                    double[] v = new double[8];
                    for (int i = 0; i < 8; i++) v[i] = Double.parseDouble(f[i]);
                    z.add(v);
                }
            }
            int n = z.size();
            k.id = new long[n]; k.von = new long[n]; k.nach = new long[n]; k.laenge = new double[n];
            for (double[] v : z) k.max = Math.max(k.max, (int) v[0]);
            for (int a = 0; a < 4; a++) k.kosten[a] = new double[k.max + 1];
            for (int i = 0; i < n; i++) {
                double[] v = z.get(i);
                k.id[i] = (long) v[0]; k.von[i] = (long) v[1]; k.nach[i] = (long) v[2]; k.laenge[i] = v[3];
                for (int a = 0; a < 4; a++) k.kosten[a][(int) v[0]] = v[4 + a];
            }
            return k;
        }
    }

    // ------------------------------------------------------------------ Laden

    /** Lädt LB_NET_WELLE (Knoten mit Punkt in der Höhe des Mittelwassers, Kanten mit Linie, Kosten, Benutzerdaten). */
    public static void ladeWelle(Connection c, File daten, Welle.Log log) throws Exception {
        File dir = new File(daten, WellenNetz.ORDNER);
        long t0 = System.nanoTime();
        double z = Isochronenbild.MSL;
        int nk = 0;
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + WELLE + "_NODE$ (NODE_ID, NODE_NAME, ACTIVE, ART, ZELLE_I, ZELLE_J, TIEFE_HEUTE, TIEFE_1940, TIEFE_1959, AMPLITUDE, GEOMETRY) "
                + "VALUES (?, ?, 'Y', ?, ?, ?, ?, ?, ?, ?, SDO_GEOMETRY(3001, 26908, SDO_POINT_TYPE(?, ?, ?), NULL, NULL))")) {
            for (String[] f : WellenNetz.tsv(new File(dir, "knoten.tsv"))) {
                p.setLong(1, Long.parseLong(f[0]));
                p.setString(2, f[1].equals("QUELLE") ? "QUELLE" : "Z" + f[2] + "_" + f[3]);
                p.setString(3, f[1]);
                p.setInt(4, Integer.parseInt(f[2]));
                p.setInt(5, Integer.parseInt(f[3]));
                for (int i = 0; i < 4; i++) p.setDouble(6 + i, Double.parseDouble(f[6 + i]));
                p.setDouble(10, Double.parseDouble(f[4]));
                p.setDouble(11, Double.parseDouble(f[5]));
                p.setDouble(12, z);
                p.addBatch();
                if (++nk % 5000 == 0) p.executeBatch();
            }
            p.executeBatch();
        }
        c.commit();
        long t1 = System.nanoTime();
        Kanten k = Kanten.lesen(dir);
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + WELLE + "_LINK$ (LINK_ID, START_NODE_ID, END_NODE_ID, ACTIVE, LINK_LEVEL, COST, LAENGE, T_HEUTE, T_1940, T_1959, T_NICHTLINEAR) "
                + "VALUES (?, ?, ?, 'Y', 1, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < k.id.length; i++) {
                int id = (int) k.id[i];
                p.setLong(1, id);
                p.setLong(2, k.von[i]);
                p.setLong(3, k.nach[i]);
                p.setDouble(4, k.kosten[0][id]);
                p.setDouble(5, k.laenge[i]);
                for (int a = 0; a < 4; a++) p.setDouble(6 + a, k.kosten[a][id]);
                p.addBatch();
                if ((i + 1) % 10_000 == 0) p.executeBatch();
            }
            p.executeBatch();
        }
        c.commit();
        long t2 = System.nanoTime();
        // Linien der Kanten auf dem Server aus den Punkten der Knoten
        try (Statement s = c.createStatement()) {
            s.execute("""
                    MERGE INTO LB_NET_WELLE_LINK$ l
                    USING (SELECT k.LINK_ID, SDO_GEOMETRY(3002, 26908, NULL, SDO_ELEM_INFO_ARRAY(1, 2, 1),
                                  SDO_ORDINATE_ARRAY(a.GEOMETRY.SDO_POINT.X, a.GEOMETRY.SDO_POINT.Y, a.GEOMETRY.SDO_POINT.Z,
                                                     b.GEOMETRY.SDO_POINT.X, b.GEOMETRY.SDO_POINT.Y, b.GEOMETRY.SDO_POINT.Z)) AS G
                             FROM LB_NET_WELLE_LINK$ k
                             JOIN LB_NET_WELLE_NODE$ a ON a.NODE_ID = k.START_NODE_ID
                             JOIN LB_NET_WELLE_NODE$ b ON b.NODE_ID = k.END_NODE_ID) x
                       ON (l.LINK_ID = x.LINK_ID)
                     WHEN MATCHED THEN UPDATE SET l.GEOMETRY = x.G""");
        }
        c.commit();
        log.log("geladen: %s, %,d Knoten in %.1f s, %,d Kanten in %.1f s, Linien in %.1f s", WELLE, nk, (t1 - t0) / 1e9, k.id.length, (t2 - t1) / 1e9,
                (System.nanoTime() - t2) / 1e9);
    }

    /** Lädt LB_NET_UFER: Punkte, Stränge (Linie mit Maß in 06_ufer.sql), Knoten und Kanten mit Maß, Aufläufe. */
    public static void ladeUfer(Connection c, File daten, File sql, Welle.Log log) throws Exception {
        File dir = new File(daten, UferNetz.ORDNER);
        long t0 = System.nanoTime();
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_UFER_GEOM (GEOM_ID, NAME, RING, LAENGE) VALUES (?, ?, ?, ?)")) {
            for (String[] f : WellenNetz.tsv(new File(dir, "straenge.tsv"))) {
                p.setInt(1, Integer.parseInt(f[0])); p.setString(2, f[1]); p.setString(3, f[2]); p.setDouble(4, Double.parseDouble(f[3]));
                p.executeUpdate();
            }
        }
        int np = 0;
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_UFER_PUNKT (GEOM_ID, SEQ, OST, NORD, M) VALUES (?, ?, ?, ?, ?)")) {
            for (String[] f : WellenNetz.tsv(new File(dir, "punkte.tsv"))) {
                p.setInt(1, Integer.parseInt(f[0])); p.setInt(2, Integer.parseInt(f[1]));
                for (int i = 0; i < 3; i++) p.setDouble(3 + i, Double.parseDouble(f[2 + i]));
                p.addBatch();
                if (++np % 5000 == 0) p.executeBatch();
            }
            p.executeBatch();
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + UFER + "_NODE$ (NODE_ID, NODE_NAME, ACTIVE, GEOM_ID, MEASURE, ORT) VALUES (?, ?, 'Y', ?, ?, ?)")) {
            for (String[] f : WellenNetz.tsv(new File(dir, "knoten.tsv"))) {
                p.setLong(1, Long.parseLong(f[0])); p.setString(2, "U" + f[0]); p.setInt(3, Integer.parseInt(f[1])); p.setDouble(4, Double.parseDouble(f[2]));
                p.setString(5, f.length > 5 && !f[5].isEmpty() ? f[5] : null);
                p.addBatch();
            }
            p.executeBatch();
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + UFER + "_LINK$ (LINK_ID, START_NODE_ID, END_NODE_ID, ACTIVE, LINK_LEVEL, GEOM_ID, START_MEASURE, END_MEASURE, COST) "
                + "VALUES (?, ?, ?, 'Y', 1, ?, ?, ?, ?)")) {
            for (String[] f : WellenNetz.tsv(new File(dir, "kanten.tsv"))) {
                p.setLong(1, Long.parseLong(f[0])); p.setLong(2, Long.parseLong(f[1])); p.setLong(3, Long.parseLong(f[2])); p.setInt(4, Integer.parseInt(f[3]));
                p.setDouble(5, Double.parseDouble(f[4])); p.setDouble(6, Double.parseDouble(f[5])); p.setDouble(7, Double.parseDouble(f[6]));
                p.addBatch();
            }
            p.executeBatch();
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_AUFLAUF (GEOM_ID, M, HOEHE, JAHR, NAME, QUELLE, ART) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (String[] f : WellenNetz.tsv(new File(dir, "auflauf.tsv"))) {
                p.setInt(1, Integer.parseInt(f[0])); p.setDouble(2, Double.parseDouble(f[1])); p.setDouble(3, Double.parseDouble(f[2])); p.setInt(4, Integer.parseInt(f[3]));
                p.setString(5, f[4]); p.setString(6, f[5]); p.setString(7, f[6]);
                p.addBatch();
            }
            p.executeBatch();
        }
        c.commit();
        NetzDb.skript(c, new File(sql, "06_ufer.sql"), true);
        log.log("geladen: %s, %d Punkte, Stränge mit Maß, Aufläufe (%.1f s)", UFER, np, (System.nanoTime() - t0) / 1e9);
    }

    // ------------------------------------------------------------------ Zweitrechnung

    /** Ergebnis der Zweitrechnung LOD gegen route. */
    public record Vergleich(boolean ok, double[] groesste, String[] weg, String text) { }

    /**
     * LOD in Oracle gegen die Datei (route): Ankunft je Kostenart, größte Abweichung; Wellenstrahlen zu den Orten (k = 3),
     * Isochronen als Kontur (aus der Ankunft von LOD) und als Fläche (fastWithinCostPolygon). Schreibt LB_ANKUNFT und
     * LB_ISOCHRONE (wenn c nicht null ist).
     */
    public static Vergleich lodGegenRoute(Connection c, Db.Konfig k, String pw, File daten, Welle.Log log) throws Exception {
        File dir = new File(daten, WellenNetz.ORDNER);
        WellenNetz w = WellenNetz.lesen(daten);
        Kanten ka = Kanten.lesen(dir);
        long[] ziele = new long[Welle.ORTE.length];
        for (int i = 0; i < ziele.length; i++) ziele[i] = w.knotenBei((double) Welle.ORTE[i][1], (double) Welle.ORTE[i][2], (double) Welle.ORTE[i][3]);
        Object[] r;
        try (URLClassLoader cl = com.dan.lituya.netz.Lod.lader(NetzDb.klassen())) {
            Class<?> kern = Class.forName("com.dan.lituya.netz.WelleLod", true, cl);
            Method m = kern.getMethod("oracle", String.class, String.class, String.class, long.class, int.class, double[][].class, long[].class, double[].class);
            r = (Object[]) m.invoke(null, k.url, k.user, pw, (long) w.quelle, w.quelle, ka.kosten, ziele, PUFFER);
        }
        double[][] t = (double[][]) r[0];
        String[] weg = (String[]) r[1];
        double[][] strahl = (double[][]) r[2];
        String[] puffer = (String[]) r[3];
        log.log("LOD: %s", r[4]);
        Vergleich v = vergleichen(w, t, weg, log);
        for (int i = 0; i < ziele.length; i++)
            log.log("  Wellenstrahlen QUELLE → %s (Knoten %d), kShortestPaths k = 3: %s s; route %.1f s", Welle.ORTE[i][0], ziele[i],
                    strahl[i].length == 0 ? "–" : Arrays.toString(Arrays.stream(strahl[i]).map(x -> Math.round(x * 10) / 10.0).toArray()),
                    ziele[i] > 0 ? w.ankunft[0][(int) ziele[i] - 1] : Double.NaN);
        if (c != null) schreiben(c, w, t, puffer, log);
        // in LB_NDM_PROBE
        if (c != null) {
            boolean daten0 = weg[0].startsWith("Benutzerdaten") && !weg[0].contains("anders");
            probe(c, 26, "LinkCostCalculator mit Benutzerdaten (LB_NET_WELLE)", "vier Kosten T_* aus den Benutzerdaten der Kanten in den Partition-BLOBs, withinCost von QUELLE",
                    String.join(" | ", weg), daten0);
            probe(c, 27, "withinCost auf LB_NET_WELLE gegen route", "Ankunft aller Knoten je Kostenart gegen daten/netz/welle/ankunft.tsv (≤ 0,01 s)", v.text(), v.ok());
            int pf = 0;
            for (String p : puffer) if (p != null && !p.startsWith("FEHLER")) pf++;
            probe(c, 28, "fastWithinCostPolygon (Network Buffer, F23)", "Isochronen-Flächen nach 60 … 360 s", pf + " von " + puffer.length + " Flächen"
                    + (pf < puffer.length && puffer.length > 0 && puffer[0] != null ? "; " + NetzDb.kurz(puffer[0], 300) : ""), pf == puffer.length);
            boolean ks = true;
            for (int i = 0; i < strahl.length; i++) ks &= ziele[i] <= 0 || strahl[i].length > 0;   // Orte ohne Knoten (Sporn) zählen nicht
            probe(c, 29, "kShortestPaths auf LB_NET_WELLE (Wellenstrahlen)", "QUELLE → Orte, k = 3", ks ? "zu allen Orten" : "nicht zu allen Orten", ks);
        }
        return v;
    }

    static Vergleich vergleichen(WellenNetz w, double[][] t, String[] weg, Welle.Log log) {
        double[] gr = new double[4];
        boolean ok = true;
        StringBuilder b = new StringBuilder();
        for (int a = 0; a < 4; a++) {
            int fehlt = 0;
            for (int k = 0; k < w.n; k++) {
                double r = w.ankunft[a][k], l = t[a][k + 1];
                if (Double.isNaN(r) != Double.isNaN(l)) { fehlt++; continue; }
                if (!Double.isNaN(r)) gr[a] = Math.max(gr[a], Math.abs(r - l));
            }
            boolean g = fehlt == 0 && gr[a] <= 0.01;
            ok &= g;
            String s = String.format(Locale.GERMAN, "%s: größte Abweichung %.4f s, %d ohne Gegenstück (%s)", WellenNetz.KOSTEN[a], gr[a], fehlt, weg[a]);
            log.log("  Ankunft LOD gegen route, %s", s);
            b.append(a > 0 ? "; " : "").append(String.format(Locale.GERMAN, "%s %.4f s", WellenNetz.KOSTEN[a], gr[a]));
        }
        return new Vergleich(ok, gr, weg, b.toString());
    }

    static void probe(Connection c, int nr, String f, String p, String e, boolean ok) throws SQLException {
        try (CallableStatement s = c.prepareCall("BEGIN LB_PROBE_SETZEN(?, ?, ?, ?, ?, 'JAVA'); END;")) {
            s.setInt(1, nr); s.setString(2, f); s.setString(3, p); s.setString(4, NetzDb.kurz(e, 4000)); s.setString(5, ok ? "J" : "N");
            s.execute();
        }
    }

    /** LB_ANKUNFT (LOD und route) und LB_ISOCHRONE (Konturen aus der Ankunft von LOD, Flächen aus fastWithinCostPolygon). */
    static void schreiben(Connection c, WellenNetz w, double[][] t, String[] puffer, Welle.Log log) throws Exception {
        long t0 = System.nanoTime();
        try (Statement s = c.createStatement()) {
            s.execute("DELETE FROM LB_ANKUNFT");
            s.execute("DELETE FROM LB_ISOCHRONE");
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_ANKUNFT (KOSTEN, NODE_ID, T_LOD, T_ROUTE) VALUES (?, ?, ?, ?)")) {
            int n = 0;
            for (int a = 0; a < 4; a++)
                for (int k = 0; k < w.n; k++) {
                    p.setString(1, WellenNetz.KOSTEN[a]);
                    p.setInt(2, k + 1);
                    if (Double.isNaN(t[a][k + 1])) p.setNull(3, java.sql.Types.NUMERIC); else p.setDouble(3, t[a][k + 1]);
                    if (Float.isNaN(w.ankunft[a][k])) p.setNull(4, java.sql.Types.NUMERIC); else p.setDouble(4, w.ankunft[a][k]);
                    p.addBatch();
                    if (++n % 10_000 == 0) p.executeBatch();
                }
            p.executeBatch();
        }
        int nl = 0, np = 0;
        // Befund Phase 7a: SDO_UTIL.FROM_WKTGEOMETRY hat in 21c kein SRID-Argument (PLS-306); der Konstruktor SDO_GEOMETRY(CLOB, SRID) schon
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_ISOCHRONE (KOSTEN, T, ART, GEOMETRY) VALUES (?, ?, ?, SDO_GEOMETRY(?, 26908))")) {
            for (int a = 0; a < 4; a++) {
                float[] f = new float[WellenNetz.BREITE * WellenNetz.HOEHE];
                Arrays.fill(f, Float.NaN);
                for (int k = 0; k < w.n; k++) f[w.j[k] * WellenNetz.BREITE + w.i[k]] = (float) t[a][k + 1];
                for (double lv : KONTUR)
                    for (float[] l : Contour.lines(f, WellenNetz.BREITE, WellenNetz.HOEHE, 0, 0, WellenNetz.ZELLE, lv)) {
                        if (l.length < 4) continue;
                        StringBuilder b = new StringBuilder("LINESTRING (");
                        for (int q = 0; q < l.length; q += 2)
                            b.append(q > 0 ? ", " : "").append(String.format(Locale.ROOT, "%.2f %.2f", WellenNetz.E_NW + WellenNetz.ZELLE / 2 + l[q], WellenNetz.N_NW - WellenNetz.ZELLE / 2 - l[q + 1]));
                        b.append(')');
                        p.setString(1, WellenNetz.KOSTEN[a]); p.setDouble(2, lv); p.setString(3, "KONTUR"); p.setClob(4, new java.io.StringReader(b.toString()));
                        p.addBatch();
                        nl++;
                    }
                p.executeBatch();
            }
            for (int i = 0; i < puffer.length; i++) {
                if (puffer[i] == null || puffer[i].startsWith("FEHLER")) continue;
                p.setString(1, "T_HEUTE"); p.setDouble(2, PUFFER[i]); p.setString(3, "PUFFER"); p.setClob(4, new java.io.StringReader(puffer[i]));
                p.executeUpdate();
                np++;
            }
        }
        c.commit();
        log.log("LB_ANKUNFT: %,d Zeilen; LB_ISOCHRONE: %d Konturen, %d Flächen (%.1f s)", 4 * w.n, nl, np, (System.nanoTime() - t0) / 1e9);
    }

    // ------------------------------------------------------------------ Ufer

    /** SDO_LRS gegen Measure: Lage der Aufläufe aus dem Maß, Projektion der Orte, Länge eines Stücks. */
    public static boolean lrsPruefen(Connection c, File daten, Welle.Log log) throws Exception {
        UferNetz u = UferNetz.lesen(daten);
        boolean ok = true;
        double gr = 0;
        try (PreparedStatement p = c.prepareStatement("""
                SELECT t.X, t.Y FROM LB_UFER_GEOM g, TABLE(SDO_UTIL.GETVERTICES(SDO_LRS.LOCATE_PT(g.GEOMETRY, ?))) t WHERE g.GEOM_ID = ?""")) {
            for (UferNetz.Auflauf a : u.auflauf) {
                p.setDouble(1, a.m()); p.setInt(2, a.strang());
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) { ok = false; continue; }
                    gr = Math.max(gr, Math.hypot(r.getDouble(1) - a.e(), r.getDouble(2) - a.n()));
                }
            }
        }
        boolean locate = gr <= 0.05;
        log.log("  SDO_LRS.LOCATE_PT gegen Measure.locate, %d Aufläufe: größte Abweichung %.4f m", u.auflauf.size(), gr);
        double grm = 0;
        Site.Ort[] orte = {Site.MUDSLIDE, Site.CENOTAPH_POINT, Site.THREE_SAINTS, Site.ANCHORAGE};
        try (PreparedStatement p = c.prepareStatement("""
                SELECT SDO_LRS.GET_MEASURE(SDO_LRS.PROJECT_PT(g.GEOMETRY, SDO_GEOMETRY(2001, 26908, SDO_POINT_TYPE(?, ?, NULL), NULL, NULL))) FROM LB_UFER_GEOM g WHERE g.GEOM_ID = 1""")) {
            for (Site.Ort o : orte) {
                p.setDouble(1, o.e()); p.setDouble(2, o.n());
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) { ok = false; continue; }
                    double m = r.getDouble(1), mj = u.projektion(1, o.e(), o.n())[0];
                    grm = Math.max(grm, Math.abs(m - mj));
                    log.log("    PROJECT_PT %-18s m = %,.2f (Measure %,.2f)", o.name(), m, mj);
                }
            }
        }
        boolean project = grm <= 0.05;
        double l = Double.NaN;
        try (Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT SDO_GEOM.SDO_LENGTH(SDO_LRS.CONVERT_TO_STD_GEOM(SDO_LRS.CLIP_GEOM_SEGMENT(GEOMETRY, 1000, 3000)), 0.005) FROM LB_UFER_GEOM WHERE GEOM_ID = 1")) {
            if (r.next()) l = r.getDouble(1);
        }
        boolean clip = Math.abs(l - 2000) <= 0.05;
        log.log("  SDO_LRS.PROJECT_PT gegen Measure.project, %d Orte: größte Abweichung %.4f m; CLIP_GEOM_SEGMENT 1 000 bis 3 000 m: %.3f m", orte.length, grm, l);
        ok &= locate && project && clip;
        probe(c, 30, "Dynamische Segmentierung LB_NET_UFER", "LOCATE_PT, PROJECT_PT, CLIP_GEOM_SEGMENT gegen com.dan.route.Measure (≤ 5 cm)",
                String.format(Locale.ROOT, "LOCATE_PT %.4f m, PROJECT_PT %.4f m, CLIP %.3f m", gr, grm, l), ok);
        return ok;
    }

    /** VALIDATE_NETWORK für ein Netz. */
    static String validate(Connection c, String netz) throws SQLException {
        try (CallableStatement s = c.prepareCall("BEGIN ? := SDO_NET.VALIDATE_NETWORK(?); END;")) {
            s.registerOutParameter(1, java.sql.Types.VARCHAR);
            s.setString(2, netz);
            s.execute();
            return s.getString(1);
        }
    }

    /** Eintrag in LB_NETZ_STAND. */
    static void stand(Connection c, String netz, String inhalt, long knoten, long kanten, String summe, String quelle, String gueltig) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("""
                MERGE INTO LB_NETZ_STAND t USING (SELECT ? NETZ FROM DUAL) s ON (t.NETZ = s.NETZ)
                 WHEN MATCHED THEN UPDATE SET INHALT = ?, KNOTEN = ?, KANTEN = ?, PRUEFSUMME = ?, QUELLE = ?, GELADEN = SYSDATE, GUELTIG = ?
                 WHEN NOT MATCHED THEN INSERT (NETZ, INHALT, KNOTEN, KANTEN, PRUEFSUMME, QUELLE, GUELTIG) VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
            Object[] w = {netz, inhalt, knoten, kanten, summe, quelle, gueltig};
            p.setString(1, netz);
            for (int i = 1; i < w.length; i++) p.setObject(i + 1, w[i]);
            for (int i = 0; i < w.length; i++) p.setObject(i + 8, w[i]);
            p.executeUpdate();
        }
        c.commit();
    }

    /** Prüfsumme des Ufers (alle Dateien des Abbilds). */
    static String summeUfer(File daten) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        for (String f : new String[]{"straenge.tsv", "punkte.tsv", "knoten.tsv", "kanten.tsv", "auflauf.tsv"})
            md.update(Files.readAllBytes(new File(new File(daten, UferNetz.ORDNER), f).toPath()));
        return java.util.HexFormat.of().formatHex(md.digest());
    }
}
