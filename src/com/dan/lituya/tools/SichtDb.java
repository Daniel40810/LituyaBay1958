package com.dan.lituya.tools;

import com.dan.lituya.scene.SichtNetz;
import com.dan.lituya.scene.WellenNetz;
import com.dan.store.Db;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code LB_NET_SICHT} in Oracle (Phase 17, Werkbuch 7.6): laden aus {@code daten/netz/sicht} (Knoten mit Punkt in der
 * Augenhöhe, Kanten als gerade Linie, ungerichtet; {@code LB_SICHT_FAHRT} mit Rundflug und Kamerafahrt;
 * {@code db/netz/13_sicht.sql}, {@code 14_sicht_nach_dem_laden.sql}), dann die Zweitrechnung: LOD ({@code netz.SichtLod}:
 * {@code mcst}, {@code tsp}, {@code nearestNeighbors}) gegen {@link SichtNetz}.
 */
public final class SichtDb {
    private SichtDb() { }

    public static final String SICHT = "LB_NET_SICHT";
    /** So weit darf die Rundreise von LOD (tsp, eine Näherung) von der eigenen abweichen [G]. */
    public static final double TSP_TOLERANZ = 0.05;

    public static void laden(Connection c, File daten, File sql, Welle.Log log) throws Exception {
        SichtNetz w = SichtNetz.lesen(daten);
        NetzDb.skript(c, new File(sql, "13_sicht.sql"), true);
        long t0 = System.nanoTime();
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + SICHT + "_NODE$ (NODE_ID, NODE_NAME, ACTIVE, ART, HOEHE, GRUND, BLICK, NEIGUNG, MOTIV, GEOMETRY, NAME) "
                + "VALUES (?, ?, 'Y', ?, ?, ?, ?, ?, ?, SDO_GEOMETRY(3001, 26908, SDO_POINT_TYPE(?, ?, ?), NULL, NULL), ?)")) {
            for (int i = 0; i < w.n; i++) {
                p.setLong(1, i + 1);
                // Befund 17: NODE_NAME kurz halten (ORA-06502 in GENERATE_PARTITION_BLOBS), der volle Name in NAME
                p.setString(2, w.name[i] == null ? null : w.name[i].length() <= 30 ? w.name[i] : w.name[i].substring(0, 30));
                p.setString(3, w.art[i]);
                p.setDouble(4, w.hoehe[i]);
                zahl(p, 5, w.grund[i]);
                zahl(p, 6, w.blick[i]);
                zahl(p, 7, w.neigung[i]);
                p.setString(8, w.motiv[i] ? "J" : "N");
                p.setDouble(9, w.ost[i]);
                p.setDouble(10, w.nord[i]);
                p.setDouble(11, w.hoehe[i]);
                p.setString(12, w.name[i]);
                p.addBatch();
            }
            p.executeBatch();
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + SICHT + "_LINK$ (LINK_ID, START_NODE_ID, END_NODE_ID, ACTIVE, LINK_LEVEL, COST, LAENGE, FREI_M, GEOMETRY) "
                + "VALUES (?, ?, ?, 'Y', 1, ?, ?, ?, SDO_GEOMETRY(3002, 26908, NULL, SDO_ELEM_INFO_ARRAY(1, 2, 1), SDO_ORDINATE_ARRAY(?, ?, ?, ?, ?, ?)))")) {
            for (int e = 0; e < w.m; e++) {
                int a = w.von[e], b = w.nach[e];
                p.setLong(1, e + 1);
                p.setLong(2, a + 1);
                p.setLong(3, b + 1);
                p.setDouble(4, w.cost[e]);
                p.setDouble(5, w.laenge[e]);
                p.setDouble(6, w.frei[e]);
                p.setDouble(7, w.ost[a]); p.setDouble(8, w.nord[a]); p.setDouble(9, w.hoehe[a]);
                p.setDouble(10, w.ost[b]); p.setDouble(11, w.nord[b]); p.setDouble(12, w.hoehe[b]);
                p.addBatch();
                if ((e + 1) % 5_000 == 0) p.executeBatch();
            }
            p.executeBatch();
        }
        int nf = 0;
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_SICHT_FAHRT (FAHRT, SEQ, NODE_ID, ZEIT_S, MOTIV, PUNKT) "
                + "VALUES (?, ?, ?, ?, ?, SDO_GEOMETRY(3001, 26908, SDO_POINT_TYPE(?, ?, ?), NULL, NULL))")) {
            for (String[] f : new String[][]{{"RUNDFLUG", "rundflug.tsv"}, {"KAMERAFAHRT", "fahrt.tsv"}})
                for (String[] z : WellenNetz.tsv(new File(new File(daten, SichtNetz.ORDNER), f[1]))) {
                    int k = Integer.parseInt(z[1]) - 1;
                    p.setString(1, f[0]);
                    p.setInt(2, Integer.parseInt(z[0]));
                    p.setLong(3, k + 1);
                    p.setDouble(4, Double.parseDouble(z[2]));
                    p.setString(5, z.length > 3 && !z[3].isEmpty() ? z[3] : null);
                    p.setDouble(6, w.ost[k]); p.setDouble(7, w.nord[k]); p.setDouble(8, w.hoehe[k]);
                    p.addBatch();
                    nf++;
                }
            p.executeBatch();
        }
        c.commit();
        NetzDb.skript(c, new File(sql, "14_sicht_nach_dem_laden.sql"), true);
        log.log("geladen: %s, %,d Punkte, %,d Sichtlinien (ungerichtet), LB_SICHT_FAHRT %,d Zeilen in %.1f s", SICHT, w.n, w.m, nf, (System.nanoTime() - t0) / 1e9);
    }

    static void zahl(PreparedStatement p, int i, double v) throws java.sql.SQLException {
        if (Double.isNaN(v)) p.setNull(i, java.sql.Types.NUMERIC); else p.setDouble(i, v);
    }

    /** Ergebnis der Zweitrechnung. */
    public record Vergleich(boolean ok, String text) { }

    /** Die Motive des Rundflugs wie in {@link Sicht}: der Anfang (Anchorage Cove) zuerst. */
    public static int[] motive(SichtNetz w) {
        int s = w.knoten("Anchorage Cove");
        if (s < 0) s = 0;
        List<Integer> l = new ArrayList<>();
        l.add(s);
        for (int k = 0; k < w.n; k++) if (w.motiv[k] && k != s) l.add(k);
        return l.stream().mapToInt(Integer::intValue).toArray();
    }

    /** LOD (im Speicher bei url null, sonst in Oracle) gegen {@link SichtNetz}: Spannbaum, Rundflug, Nachbarn der Ansichten. */
    public static Vergleich lodGegenDatei(SichtNetz w, String url, String user, String pw, URL klassen) throws Exception {
        long[] von = new long[w.m], nach = new long[w.m];
        for (int e = 0; e < w.m; e++) { von[e] = w.von[e] + 1L; nach[e] = w.nach[e] + 1L; }
        int[] mo = motive(w);
        long[] motive = new long[mo.length];
        for (int i = 0; i < mo.length; i++) motive[i] = mo[i] + 1L;
        List<Long> st = new ArrayList<>();
        for (int k = 0; k < w.n; k++) if (SichtNetz.ANSICHT.equals(w.art[k])) st.add(k + 1L);
        long[] starts = st.stream().mapToLong(Long::longValue).toArray();
        Object[] r;
        try (URLClassLoader cl = com.dan.lituya.netz.Lod.lader(klassen)) {
            Class<?> k = Class.forName("com.dan.lituya.netz.SichtLod", true, cl);
            Method m = k.getMethod("rechnen", String.class, String.class, String.class, int.class, long[].class, long[].class, double[].class, long[].class, long[].class, int.class);
            r = (Object[]) m.invoke(null, url, user, pw, w.n, von, nach, w.cost, motive, starts, Sicht.NACHBARN);
        }
        long[] mst = (long[]) r[0];
        double lodMst = 0;
        for (long e : mst) lodMst += w.cost[(int) e - 1];
        double eigenMst = 0;
        int[] em = w.spannbaum();
        for (int e : em) eigenMst += w.cost[e];
        boolean okMst = mst.length == em.length && Math.abs(lodMst - eigenMst) <= 1e-6 * eigenMst;
        double[] ek = new double[1];
        w.rundreise(mo, ek);
        double lodTsp = (Double) r[2];
        int[] folge = (int[]) r[1];
        boolean okTsp = folge.length >= mo.length && Math.abs(lodTsp - ek[0]) <= TSP_TOLERANZ * ek[0];
        double[][] nc = (double[][]) r[4];
        int nGleich = 0, nAnders = 0;
        for (int i = 0; i < starts.length; i++) {
            double[] c = new double[Sicht.NACHBARN];
            int[] x = w.naechste((int) starts[i] - 1, Sicht.NACHBARN, c);
            for (int j = 0; j < x.length; j++) if (j < nc[i].length && Math.abs(nc[i][j] - c[j]) <= 1e-6 * Math.max(1, c[j])) nGleich++; else nAnders++;
        }
        boolean ok = okMst && okTsp && nAnders == 0 && nGleich > 0;
        return new Vergleich(ok, String.format(Locale.GERMAN, "%s; mcst %d Kanten, %.1f s wie eigen (Kruskal %.1f s)%s; tsp %.0f s gegen eigen %.0f s (%+.1f %%, %s); nearestNeighbors %d gleich, %d anders",
                r[5], mst.length, lodMst, eigenMst, okMst ? "" : " – ANDERS", lodTsp, ek[0], 100 * (lodTsp / ek[0] - 1), lodTsp < ek[0] - 1 ? "LOD kürzer" : lodTsp > ek[0] + 1 ? "eigen kürzer" : "gleich", nGleich, nAnders));
    }

    /** Die Zweitrechnung im Speicher (ohne Oracle; Prüfung). */
    public static Vergleich lodImSpeicher(SichtNetz w) throws Exception { return lodGegenDatei(w, null, null, null, NetzDb.klassen()); }

    /** Validate, Zweitrechnung in Oracle, Fahrten gegen die Dateien, Eintrag in LB_NETZ_STAND. */
    public static boolean pruefen(Connection c, File daten, String url, String user, String pw, Welle.Log log) throws Exception {
        SichtNetz w = SichtNetz.lesen(daten);
        String v = WelleDb.validate(c, SICHT);
        log.log("VALIDATE_NETWORK(%s) = %s", SICHT, v);
        Vergleich vg;
        try {
            vg = lodGegenDatei(w, url, user, pw, NetzDb.klassen());
        } catch (Exception e) {
            vg = new Vergleich(false, "LOD in Oracle: " + NetzDb.ursache(e));
        }
        log.log("Zweitrechnung Sicht: %s – %s", vg.ok() ? "LOD = Datei" : "LOD ≠ Datei", vg.text());
        String rf = Db.einWert(c, "SELECT ROUND(MAX(ZEIT_S), 1) FROM LB_SICHT_FAHRT WHERE FAHRT = 'RUNDFLUG'");
        double[][] dr = SichtNetz.fahrt(daten, "rundflug.tsv");
        boolean fahrt = dr != null && rf != null && Math.abs(Double.parseDouble(rf.replace(',', '.')) - dr[dr.length - 1][1]) < 0.1;
        log.log("LB_SICHT_FAHRT: Rundflug %s s (Datei %.1f s)%s", rf, dr == null ? Double.NaN : dr[dr.length - 1][1], fahrt ? "" : " – ANDERS");
        boolean gut = "TRUE".equalsIgnoreCase(v) && vg.ok() && fahrt;
        WelleDb.stand(c, SICHT, "Sicht (Werkbuch 7.6): Kamerapunkte, freie Sicht im Gelände (ungerichtet); Flugzeit der Kamera", w.n, w.m,
                WellenNetz.pruefsumme(new File(daten, SichtNetz.ORDNER)), "daten/" + SichtNetz.ORDNER,
                "VALIDATE " + v + "; LOD = Datei " + (vg.ok() ? "ok" : "FEHLER") + "; Fahrten " + (fahrt ? "ok" : "FEHLER"));
        return gut;
    }
}
