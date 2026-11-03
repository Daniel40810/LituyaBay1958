package com.dan.lituya.tools;

import com.dan.lituya.scene.WegNetz;
import com.dan.lituya.scene.WellenNetz;
import com.dan.store.Db;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code LB_NET_WEG} in Oracle (Phase 16a, Werkbuch 7.5): laden aus {@code daten/netz/weg} (Knoten beider Ebenen mit
 * Elternknoten, Klasse, Lage; Kanten gerichtet; Direktpfad in Blöcken zu 50 000), {@code LB_WEG_UMSTIEG} und
 * {@code LB_WEG_ROUTE} ({@code db/netz/11_weg.sql}, {@code 12_weg_nach_dem_laden.sql}), dann die Zweitrechnung: LOD
 * ({@code netz.WegLod}) gegen den Dijkstra von {@link WegNetz} – eine Stunde vom Strand und Washburns Route.
 */
public final class WegDb {
    private WegDb() { }

    public static final String WEG = "LB_NET_WEG";
    static final int BLOCK = 50_000;

    public static void laden(Connection c, File daten, File sql, Welle.Log log) throws Exception {
        WegNetz w = WegNetz.lesen(daten);
        File d = new File(daten, WegNetz.ORDNER);
        NetzDb.skript(c, new File(sql, "11_weg.sql"), true);
        long t0 = System.nanoTime();
        boolean auto = c.getAutoCommit();
        try (PreparedStatement p = c.prepareStatement("INSERT /*+ APPEND_VALUES */ INTO " + WEG + "_NODE$ (NODE_ID, ACTIVE, HIERARCHY_LEVEL, PARENT_NODE_ID, KLASSE, HOEHE, OST, NORD) "
                + "VALUES (?, 'Y', ?, ?, ?, ?, ?, ?)")) {
            for (int k = 0; k < w.n; k++) {
                p.setLong(1, k + 1);
                p.setInt(2, w.ebene[k]);
                if (w.parent[k] < 0) p.setNull(3, java.sql.Types.NUMERIC); else p.setLong(3, w.parent[k] + 1);
                p.setInt(4, w.klasse[k]);
                p.setFloat(5, w.hoehe[k]);
                p.setDouble(6, w.ost(k));
                p.setDouble(7, w.nord(k));
                p.addBatch();
                if ((k + 1) % BLOCK == 0) { p.executeBatch(); c.commit(); }   // Direktpfad: je Block abschließen (ORA-12838)
            }
            p.executeBatch();
            c.commit();
        }
        double tk = (System.nanoTime() - t0) / 1e9;
        try (PreparedStatement p = c.prepareStatement("INSERT /*+ APPEND_VALUES */ INTO " + WEG + "_LINK$ (LINK_ID, START_NODE_ID, END_NODE_ID, ACTIVE, LINK_LEVEL, COST) VALUES (?, ?, ?, 'Y', ?, ?)")) {
            for (int e = 0; e < w.m; e++) {
                p.setLong(1, e + 1);
                p.setLong(2, w.von[e] + 1);
                p.setLong(3, w.nach[e] + 1);
                p.setInt(4, w.ebene[w.von[e]]);
                p.setFloat(5, w.cost[e]);
                p.addBatch();
                if ((e + 1) % BLOCK == 0) {
                    p.executeBatch();
                    c.commit();
                    if ((e + 1) % 5_000_000 == 0) log.log("  … %,d Kanten (%.0f s)", e + 1, (System.nanoTime() - t0) / 1e9);
                }
            }
            p.executeBatch();
            c.commit();
        }
        double tl = (System.nanoTime() - t0) / 1e9 - tk;
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_WEG_UMSTIEG (SEE_NODE_ID, WEG_NODE_ID, ABSTAND) VALUES (?, ?, ?)")) {
            for (String[] z : WellenNetz.tsv(new File(d, "umstieg.tsv"))) { p.setLong(1, Long.parseLong(z[0])); p.setLong(2, Long.parseLong(z[1])); p.setDouble(3, Double.parseDouble(z[2])); p.addBatch(); }
            p.executeBatch();
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_WEG_ROUTE (ROUTE, SEQ, NODE_ID, HOEHE, ZEIT_S, KLASSE, WEGPUNKT, PUNKT) VALUES ('WASHBURN_1934', ?, ?, ?, ?, ?, ?, "
                + "SDO_GEOMETRY(3001, 26908, SDO_POINT_TYPE(?, ?, ?), NULL, NULL))")) {
            for (String[] z : WellenNetz.tsv(new File(d, "washburn.tsv"))) {
                p.setInt(1, Integer.parseInt(z[0]));
                p.setLong(2, Long.parseLong(z[1]));
                p.setDouble(3, Double.parseDouble(z[4]));
                p.setDouble(4, Double.parseDouble(z[5]));
                p.setString(5, z[6]);
                p.setString(6, z.length > 7 && !z[7].isEmpty() ? z[7] : null);
                p.setDouble(7, Double.parseDouble(z[2]));
                p.setDouble(8, Double.parseDouble(z[3]));
                p.setDouble(9, Double.parseDouble(z[4]));
                p.addBatch();
            }
            p.executeBatch();
        }
        c.commit();
        c.setAutoCommit(auto);
        double t1 = System.nanoTime();
        NetzDb.skript(c, new File(sql, "12_weg_nach_dem_laden.sql"), true);
        log.log("geladen: %s, %,d Knoten in %.0f s, %,d Kanten in %.0f s, Lage, Indizes, Kacheln und BLOBs in %.0f s (zusammen %.1f min)", WEG, w.n, tk, w.m, tl,
                (System.nanoTime() - t1) / 1e9, (System.nanoTime() - t0) / 60e9);
    }

    /** Ergebnis der Zweitrechnung. */
    public record Vergleich(boolean ok, String text) { }

    /** Start (Anchorage Cove, Ebene 1) und Wegpunkte (Washburn, Ebene 2) aus WEG.txt und washburn.tsv (Indizes). */
    static int[] fragen(WegNetz w, File daten) throws Exception {
        String s = w.wert("start.badger");
        int start = s == null ? -1 : Integer.parseInt(s.split(" ")[0]) - 1;
        List<Integer> wp = new ArrayList<>();
        for (String[] z : WellenNetz.tsv(new File(new File(daten, WegNetz.ORDNER), "washburn.tsv"))) if (z.length > 7 && !z[7].isEmpty()) wp.add(Integer.parseInt(z[1]) - 1);
        int[] r = new int[1 + wp.size()];
        r[0] = start;
        for (int i = 0; i < wp.size(); i++) r[i + 1] = wp.get(i);
        return r;
    }

    /**
     * LOD gegen den eigenen Dijkstra. url null: im Speicher auf einem Ausschnitt (Knoten der Ebene 1 bis 2,5 km um den Start,
     * der Ebene 2 im Rechteck der Wegpunkte mit 3 km Rand), sonst in Oracle auf dem ganzen Netz.
     */
    public static Vergleich lodGegenDatei(WegNetz w, File daten, String url, String user, String pw, URL klassen) throws Exception {
        int[] f = fragen(w, daten);
        int start = f[0];
        int[] wp = java.util.Arrays.copyOfRange(f, 1, f.length);
        WegNetz u = w;
        int[] alt = null;
        long[] knoten = null, von = null, nach = null;
        double[] cost = null;
        if (url == null) {
            // Ausschnitt
            double e0 = w.ost(start), n0 = w.nord(start);
            double emin = 1e18, emax = -1e18, nmin = 1e18, nmax = -1e18;
            for (int k : wp) { emin = Math.min(emin, w.ost(k)); emax = Math.max(emax, w.ost(k)); nmin = Math.min(nmin, w.nord(k)); nmax = Math.max(nmax, w.nord(k)); }
            boolean[] in = new boolean[w.n];
            int ni = 0;
            for (int k = 0; k < w.n; k++) {
                double e = w.ost(k), n = w.nord(k);
                in[k] = w.ebene[k] == 1 ? Math.abs(e - e0) <= 2500 && Math.abs(n - n0) <= 2500 : e >= emin - 3000 && e <= emax + 3000 && n >= nmin - 3000 && n <= nmax + 3000;
                if (in[k]) ni++;
            }
            int[] neu = new int[w.n];
            alt = new int[ni];
            java.util.Arrays.fill(neu, -1);
            int q = 0;
            for (int k = 0; k < w.n; k++) if (in[k]) { neu[k] = q; alt[q++] = k; }
            int mi = 0;
            for (int e = 0; e < w.m; e++) if (in[w.von[e]] && in[w.nach[e]]) mi++;
            u = new WegNetz();
            u.n = ni; u.m = mi;
            u.ebene = new byte[ni]; u.klasse = new byte[ni]; u.zelle = new int[ni]; u.hoehe = new float[ni]; u.parent = new int[ni];
            for (int k = 0; k < ni; k++) { int o = alt[k]; u.ebene[k] = w.ebene[o]; u.zelle[k] = w.zelle[o]; u.klasse[k] = w.klasse[o]; u.hoehe[k] = w.hoehe[o]; u.parent[k] = -1; }
            u.von = new int[mi]; u.nach = new int[mi]; u.cost = new float[mi];
            von = new long[mi]; nach = new long[mi]; cost = new double[mi];
            int j = 0;
            for (int e = 0; e < w.m; e++) if (in[w.von[e]] && in[w.nach[e]]) {
                u.von[j] = neu[w.von[e]]; u.nach[j] = neu[w.nach[e]]; u.cost[j] = w.cost[e];
                von[j] = u.von[j] + 1L; nach[j] = u.nach[j] + 1L; cost[j] = w.cost[e];
                j++;
            }
            u.index();
            knoten = new long[ni];
            for (int k = 0; k < ni; k++) knoten[k] = k + 1L;
            start = neu[start];
            for (int i = 0; i < wp.length; i++) wp[i] = neu[wp[i]];
        }
        long[] wpl = new long[wp.length];
        for (int i = 0; i < wp.length; i++) wpl[i] = wp[i] + 1L;
        Object[] r;
        try (URLClassLoader cl = com.dan.lituya.netz.Lod.lader(klassen)) {
            Class<?> k = Class.forName("com.dan.lituya.netz.WegLod", true, cl);
            Method mm = k.getMethod("rechnen", String.class, String.class, String.class, long[].class, long[].class, long[].class, double[].class, long.class, double.class, long[].class);
            r = (Object[]) mm.invoke(null, url, user, pw, knoten, von, nach, cost, start + 1L, Weg.STUNDE, wpl);
        }
        long[] ids = (long[]) r[0];
        double[] ts = (double[]) r[1], ab = (double[]) r[2], k3 = (double[]) r[3];
        // eigene Seite
        double[] t = u.dijkstra(start, Weg.STUNDE, null);
        Map<Integer, Double> lod = new HashMap<>();
        for (int i = 0; i < ids.length; i++) lod.put((int) ids[i] - 1, ts[i]);
        int gleich = 0, anders = 0;
        double dmax = 0;
        for (int k = 0; k < u.n; k++) {
            if (Double.isNaN(t[k])) continue;
            Double x = lod.remove(k);
            if (x == null) { if (Weg.STUNDE - t[k] > 0.01) anders++; continue; }   // am Rand der Stunde darf LOD knapp anders runden
            gleich++;
            dmax = Math.max(dmax, Math.abs(x - t[k]));
        }
        for (double x : lod.values()) if (Weg.STUNDE - x > 0.01) anders++;
        double amax = 0;
        StringBuilder b = new StringBuilder();
        for (int q = 0; q + 1 < wp.length; q++) {
            double[] tz = new double[1];
            int[] p = u.weg(wp[q], wp[q + 1], tz);
            double eig = p == null ? Double.NaN : tz[0];
            amax = Math.max(amax, Math.abs(eig - ab[q]));
            b.append(String.format(Locale.GERMAN, "%.0f s / %.0f s; ", eig, ab[q]));
        }
        boolean k3ok = k3.length > 0 && ab.length > 0 && Math.abs(k3[0] - ab[ab.length - 1]) <= 0.01;
        for (int q = 1; q < k3.length; q++) k3ok &= k3[q] >= k3[q - 1] - 1e-6;
        boolean ok = anders == 0 && gleich > 100 && dmax <= 0.05 && amax <= 0.05 && k3ok;
        return new Vergleich(ok, String.format(Locale.GERMAN, "%s; 1 h vom Strand %,d Knoten gleich bis %.3f s, %d anders; Washburn je Abschnitt eigen/LOD %sk = 3: %s",
                r[4], gleich, dmax, anders, b, java.util.Arrays.toString(java.util.Arrays.stream(k3).map(x -> Math.round(x)).toArray())));
    }

    /** Die Zweitrechnung im Speicher (ohne Oracle; Prüfung). */
    public static Vergleich lodImSpeicher(WegNetz w, File daten) throws Exception { return lodGegenDatei(w, daten, null, null, null, NetzDb.klassen()); }

    /** Validate, Zweitrechnung in Oracle, Eintrag in LB_NETZ_STAND. */
    public static boolean pruefen(Connection c, File daten, String url, String user, String pw, Welle.Log log) throws Exception {
        WegNetz w = WegNetz.lesen(daten);
        long t0 = System.nanoTime();
        String v = WelleDb.validate(c, WEG);
        log.log("VALIDATE_NETWORK(%s) = %s (%.0f s)", WEG, v, (System.nanoTime() - t0) / 1e9);
        Vergleich vg;
        try {
            vg = lodGegenDatei(w, daten, url, user, pw, NetzDb.klassen());
        } catch (Exception e) {
            vg = new Vergleich(false, "LOD in Oracle: " + NetzDb.ursache(e));
        }
        log.log("Zweitrechnung Weg: %s – %s", vg.ok() ? "LOD = Datei" : "LOD ≠ Datei", vg.text());
        String kl = Db.einWert(c, "SELECT COUNT(*) FROM LB_V_WEG_KLASSE");
        String um = Db.einWert(c, "SELECT COUNT(*) FROM LB_WEG_UMSTIEG"), ro = Db.einWert(c, "SELECT COUNT(*) FROM LB_WEG_ROUTE");
        log.log("LB_V_WEG_KLASSE %s Zeilen, LB_WEG_UMSTIEG %s, LB_WEG_ROUTE %s Punkte", kl, um, ro);
        WelleDb.stand(c, WEG, "Weg (Werkbuch 7.5): Tobler mit Zuschlag je Klasse, gerichtet, Kern 10 m und Szene 50 m", w.n, w.m, WegNetz.pruefsumme(daten), "daten/" + WegNetz.ORDNER,
                "VALIDATE " + v + "; LOD = Datei " + (vg.ok() ? "ok" : "FEHLER"));
        return "TRUE".equalsIgnoreCase(v) && vg.ok();
    }
}
