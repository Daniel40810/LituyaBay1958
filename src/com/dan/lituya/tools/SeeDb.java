package com.dan.lituya.tools;

import com.dan.lituya.scene.SeeNetz;
import com.dan.store.Db;
import com.dan.lituya.scene.WellenNetz;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code LB_NET_SEE} in Oracle (Phase 15a, Werkbuch 7.3): laden aus {@code daten/netz/see} (Knoten mit Punkt, Kanten mit
 * Linie auf dem Server, gerichtet), {@code LB_FLUCHT} aus {@code flucht.tsv} und {@code wege.tsv} (die Wege der spätesten
 * Abfahrt als Linie), Partition und BLOBs ({@code db/netz/07_see.sql}, {@code 08_see_nach_dem_laden.sql}), dann die
 * Zweitrechnung: LOD ({@code netz.SeeLod}, eigener Klassenlader) gegen den eigenen Dijkstra von {@link SeeNetz}, im
 * Speicher und in Oracle.
 */
public final class SeeDb {
    private SeeDb() { }

    public static final String SEE = "LB_NET_SEE";
    /** Abfahrten der Zweitrechnung (s nach dem Stoß). */
    static final double[] PROBE = {-300, -120, 0, 60, 120, 180};

    public static void laden(Connection c, File daten, File sql, Welle.Log log) throws Exception {
        SeeNetz s = SeeNetz.lesen(daten);
        NetzDb.skript(c, new File(sql, "07_see.sql"), true);
        long t0 = System.nanoTime();
        double z = Isochronenbild.MSL;
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + SEE + "_NODE$ (NODE_ID, NODE_NAME, ACTIVE, ABSTAND, TIEFE_1940, STROM_FAKTOR, DRAUSSEN, FRONT_LINEAR, FRONT_EREIGNIS, GEOMETRY) "
                + "VALUES (?, ?, 'Y', ?, ?, ?, ?, ?, ?, SDO_GEOMETRY(3001, 26908, SDO_POINT_TYPE(?, ?, ?), NULL, NULL))")) {
            for (int i = 0; i < s.n; i++) {
                p.setLong(1, i + 1);
                p.setString(2, String.format(Locale.ROOT, "S%.0f_%.0f", s.ost[i], s.nord[i]));
                p.setInt(3, s.abstand[i]);
                p.setDouble(4, s.tiefe[i]);
                p.setDouble(5, s.faktorKnoten[i]);
                p.setDouble(6, s.draussen[i]);
                zahl(p, 7, s.frontLinear[i]);
                zahl(p, 8, s.frontEreignis == null ? Double.NaN : s.frontEreignis[i]);
                p.setDouble(9, s.ost[i]);
                p.setDouble(10, s.nord[i]);
                p.setDouble(11, z);
                p.addBatch();
            }
            p.executeBatch();
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + SEE + "_LINK$ (LINK_ID, START_NODE_ID, END_NODE_ID, ACTIVE, LINK_LEVEL, COST, LAENGE, RICHTUNG_OST, RICHTUNG_NORD, STROM_FAKTOR) "
                + "VALUES (?, ?, ?, 'Y', 1, ?, ?, ?, ?, ?)")) {
            for (int e = 0; e < s.m; e++) {
                p.setLong(1, e + 1);
                p.setLong(2, s.von[e] + 1);
                p.setLong(3, s.nach[e] + 1);
                p.setDouble(4, s.laenge[e] / SeeNetz.FAHRT);
                p.setDouble(5, s.laenge[e]);
                p.setDouble(6, s.re[e]);
                p.setDouble(7, s.rn[e]);
                p.setDouble(8, s.faktor[e]);
                p.addBatch();
                if ((e + 1) % 10_000 == 0) p.executeBatch();
            }
            p.executeBatch();
        }
        c.commit();
        try (Statement st = c.createStatement()) {
            st.execute("""
                    MERGE INTO LB_NET_SEE_LINK$ l
                    USING (SELECT k.LINK_ID, SDO_GEOMETRY(3002, 26908, NULL, SDO_ELEM_INFO_ARRAY(1, 2, 1),
                                  SDO_ORDINATE_ARRAY(a.GEOMETRY.SDO_POINT.X, a.GEOMETRY.SDO_POINT.Y, a.GEOMETRY.SDO_POINT.Z,
                                                     b.GEOMETRY.SDO_POINT.X, b.GEOMETRY.SDO_POINT.Y, b.GEOMETRY.SDO_POINT.Z)) AS G
                             FROM LB_NET_SEE_LINK$ k
                             JOIN LB_NET_SEE_NODE$ a ON a.NODE_ID = k.START_NODE_ID
                             JOIN LB_NET_SEE_NODE$ b ON b.NODE_ID = k.END_NODE_ID) x
                       ON (l.LINK_ID = x.LINK_ID)
                     WHEN MATCHED THEN UPDATE SET l.GEOMETRY = x.G""");
        }
        c.commit();
        // Flucht
        File d = new File(daten, SeeNetz.ORDNER);
        List<String[]> wege = WellenNetz.tsv(new File(d, "wege.tsv"));
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_FLUCHT (BOOT, FRONT, ABFAHRT_S, ENTKOMMEN, ANKUNFT_S, RESERVE_S, ZIEL_NODE_ID, SPAETESTE) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (String[] f : WellenNetz.tsv(new File(d, "flucht.tsv"))) eintrag(p, f[0], f[1], Double.parseDouble(f[2]), f[3], f[4], f[5], f[6], "N", null, c);
            // die späteste Abfahrt je Boot und Front mit ihrem Weg
            String k = null;
            List<double[]> pts = new ArrayList<>();
            String[] last = null;
            for (String[] w : wege) {
                String key = w[0] + "|" + w[1] + "|" + w[2];
                if (k != null && !k.equals(key)) { p.executeBatch(); weg(p, last, pts, c); pts.clear(); }
                k = key;
                last = w;
                pts.add(new double[]{Double.parseDouble(w[5]), Double.parseDouble(w[6]), Double.parseDouble(w[7])});
            }
            p.executeBatch();
            if (last != null) weg(p, last, pts, c);
        }
        c.commit();
        NetzDb.skript(c, new File(sql, "08_see_nach_dem_laden.sql"), true);
        log.log("geladen: %s, %,d Knoten, %,d Kanten (gerichtet), LB_FLUCHT in %.1f s", SEE, s.n, s.m, (System.nanoTime() - t0) / 1e9);
    }

    static void weg(PreparedStatement p, String[] w, List<double[]> pts, Connection c) throws Exception {
        double an = pts.get(pts.size() - 1)[2];
        eintrag(p, w[0], w[1], Double.parseDouble(w[2]), "J", String.format(Locale.ROOT, "%.1f", an), "", w[4], "J", null, c);
        p.executeBatch();
        StringBuilder o = new StringBuilder();
        for (double[] q : pts) o.append(o.length() > 0 ? ", " : "").append(String.format(Locale.ROOT, "%.1f, %.1f, %.1f", q[0], q[1], q[2]));   // Z = Ankunft (s)
        try (PreparedStatement u = c.prepareStatement("UPDATE LB_FLUCHT SET WEG = SDO_GEOMETRY(3002, 26908, NULL, SDO_ELEM_INFO_ARRAY(1, 2, 1), SDO_ORDINATE_ARRAY(" + o
                + ")) WHERE BOOT = ? AND FRONT = ? AND SPAETESTE = 'J'")) {
            u.setString(1, w[0]);
            u.setString(2, w[1]);
            u.executeUpdate();
        }
    }

    static void eintrag(PreparedStatement p, String boot, String front, double ab, String ent, String an, String res, String ziel, String sp, Object unbenutzt, Connection c) throws Exception {
        p.setString(1, boot);
        p.setString(2, front);
        p.setDouble(3, ab);
        p.setString(4, ent);
        zahl(p, 5, an.isEmpty() ? Double.NaN : Double.parseDouble(an));
        zahl(p, 6, res.isEmpty() || res.contains("Inf") ? Double.NaN : Double.parseDouble(res));
        if (ziel.isEmpty()) p.setNull(7, java.sql.Types.NUMERIC); else p.setLong(7, Long.parseLong(ziel));
        p.setString(8, sp);
        p.addBatch();
    }

    static void zahl(PreparedStatement p, int i, double v) throws Exception {
        if (Double.isNaN(v) || Double.isInfinite(v)) p.setNull(i, java.sql.Types.NUMERIC); else p.setDouble(i, v);
    }

    /** Ergebnis der Zweitrechnung. */
    public record Vergleich(boolean ok, String text) { }

    /**
     * LOD (im Speicher bei url null, sonst in Oracle) gegen den eigenen Dijkstra: je Ankerplatz und Abfahrt aus {@link #PROBE}
     * mit der linearen Front dieselben Knoten erreicht und dieselbe Ankunft (≤ 0,01 s).
     */
    public static Vergleich lodGegenDijkstra(SeeNetz s, String url, String user, String pw, URL klassen) throws Exception {
        long[] von = new long[s.m], nach = new long[s.m];
        for (int e = 0; e < s.m; e++) { von[e] = s.von[e] + 1; nach[e] = s.nach[e] + 1; }
        long[] starts = new long[SeeNetz.PLAETZE.length];
        for (int i = 0; i < starts.length; i++) starts[i] = s.knotenBei((double) SeeNetz.PLAETZE[i][1], (double) SeeNetz.PLAETZE[i][2]) + 1;
        double[] strom = SeeNetz.stromTabelle();
        Object[] r;
        try (URLClassLoader cl = com.dan.lituya.netz.Lod.lader(klassen)) {
            Class<?> k = Class.forName("com.dan.lituya.netz.SeeLod", true, cl);
            Method m = k.getMethod("rechnen", String.class, String.class, String.class, int.class, long[].class, long[].class, double[].class, double[].class, double[].class,
                    double[].class, double[].class, double[].class, double.class, double.class, long[].class, double[].class);
            r = (Object[]) m.invoke(null, url, user, pw, s.n, von, nach, s.laenge, s.re, s.rn, s.faktor, s.frontLinear, strom, s.hinE, s.hinN, starts, PROBE);
        }
        double[][][] t = (double[][][]) r[0];
        double maxd = 0;
        int anders = 0, erreicht = 0, ent = 0;
        for (int i = 0; i < starts.length; i++)
            for (int d = 0; d < PROBE.length; d++) {
                SeeNetz.Flucht f = s.flucht((int) starts[i] - 1, PROBE[d], s.frontLinear, strom);
                if (f.entkommen()) ent++;
                for (int v = 0; v < s.n; v++) {
                    double a = f.ankunft()[v], b = t[i][d][v + 1];
                    if (Double.isNaN(a) != Double.isNaN(b)) { anders++; continue; }
                    if (!Double.isNaN(a)) { erreicht++; maxd = Math.max(maxd, Math.abs(a - b)); }
                }
            }
        boolean ok = anders == 0 && maxd <= 0.01 && erreicht > 0;
        return new Vergleich(ok, String.format(Locale.GERMAN, "%s; %d Läufe (2 Plätze × %d Abfahrten, davon %d entkommen), %,d Ankünfte gleich bis %.4f s, %d Knoten anders erreicht",
                r[1], starts.length * PROBE.length, PROBE.length, ent, erreicht, maxd, anders));
    }

    /** Die Zweitrechnung im Speicher (ohne Oracle; Prüfung und {@code ant netzvergleich}). */
    public static Vergleich lodImSpeicher(SeeNetz s) throws Exception { return lodGegenDijkstra(s, null, null, null, NetzDb.klassen()); }

    /** Validate, Zweitrechnung in Oracle und Eintrag in LB_NETZ_STAND. */
    public static boolean pruefen(Connection c, File daten, String url, String user, String pw, Welle.Log log) throws Exception {
        SeeNetz s = SeeNetz.lesen(daten);
        String v = WelleDb.validate(c, SEE);
        Vergleich vg = lodGegenDijkstra(s, url, user, pw, NetzDb.klassen());
        log.log("VALIDATE_NETWORK(%s) = %s", SEE, v);
        log.log("Zweitrechnung Flucht: %s – %s", vg.ok() ? "LOD = Dijkstra" : "LOD ≠ Dijkstra", vg.text());
        String n = Db.einWert(c, "SELECT COUNT(*) FROM LB_FLUCHT");
        log.log("LB_FLUCHT: %s Zeilen", n);
        WelleDb.stand(c, SEE, "See (Werkbuch 7.3): Fahrwasser eines Trollers, gerichtet, 40/100/200 m; Kosten nach Gezeitenstrom; Flucht vor der Front", s.n, s.m,
                WellenNetz.pruefsumme(new File(daten, SeeNetz.ORDNER)), "daten/" + SeeNetz.ORDNER, "VALIDATE " + v + "; LOD = Dijkstra " + (vg.ok() ? "ok" : "FEHLER"));
        return "TRUE".equalsIgnoreCase(v) && vg.ok();
    }
}
