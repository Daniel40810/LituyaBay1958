package com.dan.lituya.tools;

import com.dan.lituya.scene.WasserNetz;
import com.dan.lituya.scene.WellenNetz;
import com.dan.store.Db;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code LB_NET_WASSER} in Oracle (Phase 16, Werkbuch 7.4): laden aus {@code daten/netz/wasser} (Knoten beider Ebenen mit
 * Punkt und {@code PARENT_NODE_ID}, Kanten mit dem Verlauf aus {@code linien.tsv} als WKT, {@code LB_EINZUG} mit den
 * Umrissen, {@code LB_SEE}, {@code LB_SENKE}; {@code db/netz/09_wasser.sql}, {@code 10_wasser_nach_dem_laden.sql}), dann
 * die Zweitrechnung: LOD ({@code netz.WasserLod}, traceIn je Ziel und Komponenten) gegen {@link WasserNetz}, die Flächen je
 * Ziel über {@code CONNECT BY} auf der Ebene 2 gegen die Dateien und die Umrisse über {@code SDO_AREA}.
 */
public final class WasserDb {
    private WasserDb() { }

    public static final String WASSER = "LB_NET_WASSER";

    public static void laden(Connection c, File daten, File sql, Welle.Log log) throws Exception {
        WasserNetz w = WasserNetz.lesen(daten);
        File d = new File(daten, WasserNetz.ORDNER);
        NetzDb.skript(c, new File(sql, "09_wasser.sql"), true);
        long t0 = System.nanoTime();
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + WASSER + "_NODE$ (NODE_ID, NODE_NAME, ACTIVE, HIERARCHY_LEVEL, PARENT_NODE_ID, ART, HOEHE, FLAECHE_KM2, EIS_ANTEIL, GEOMETRY) "
                + "VALUES (?, ?, 'Y', ?, ?, ?, ?, ?, ?, SDO_GEOMETRY(2001, 26908, SDO_POINT_TYPE(?, ?, NULL), NULL, NULL))")) {
            for (int i = 0; i < w.n; i++) {
                p.setLong(1, i + 1);
                p.setString(2, w.name[i]);
                p.setInt(3, w.ebene[i]);
                if (w.parent[i] < 0) p.setNull(4, java.sql.Types.NUMERIC); else p.setLong(4, w.parent[i] + 1);
                p.setString(5, w.art[i]);
                p.setDouble(6, w.hoehe[i]);
                p.setDouble(7, w.flaeche[i]);
                p.setDouble(8, w.eisAnteil[i]);
                p.setDouble(9, w.ost[i]);
                p.setDouble(10, w.nord[i]);
                p.addBatch();
                if ((i + 1) % 10_000 == 0) p.executeBatch();
            }
            p.executeBatch();
        }
        Map<Integer, String> lin = new HashMap<>();
        for (String[] z : WellenNetz.tsv(new File(d, "linien.tsv"))) lin.put(Integer.parseInt(z[0]) - 1, z[1]);
        // Befund Phase 7a: SDO_GEOMETRY(CLOB, SRID) statt SDO_UTIL.FROM_WKTGEOMETRY
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + WASSER + "_LINK$ (LINK_ID, LINK_NAME, START_NODE_ID, END_NODE_ID, ACTIVE, LINK_LEVEL, COST, ART, LAENGE, GEFAELLE, FLAECHE_KM2, ABFLUSS, GESCHW, GEOMETRY) "
                + "VALUES (?, ?, ?, ?, 'Y', ?, ?, ?, ?, ?, ?, ?, ?, SDO_GEOMETRY(?, 26908))")) {
            for (int e = 0; e < w.m; e++) {
                p.setLong(1, e + 1);
                p.setString(2, w.kName[e]);
                p.setLong(3, w.von[e] + 1);
                p.setLong(4, w.nach[e] + 1);
                p.setInt(5, w.kEbene[e]);
                p.setDouble(6, w.cost[e]);
                p.setString(7, w.kArt[e]);
                p.setDouble(8, w.laenge[e]);
                p.setDouble(9, w.gefaelle[e]);
                p.setDouble(10, w.kFlaeche[e]);
                p.setDouble(11, w.abfluss[e]);
                p.setDouble(12, w.geschw[e]);
                String pk = lin.get(e);
                if (pk == null) pk = String.format(Locale.ROOT, "%.1f %.1f,%.1f %.1f", w.ost[w.von[e]], w.nord[w.von[e]], w.ost[w.nach[e]], w.nord[w.nach[e]]);
                p.setClob(13, new java.io.StringReader("LINESTRING (" + pk.replace(",", ", ") + ")"));
                p.addBatch();
                if ((e + 1) % 5_000 == 0) p.executeBatch();
            }
            p.executeBatch();
        }
        c.commit();
        // Teileinzugsgebiete mit Umriss
        Map<Integer, List<List<String>>> um = new HashMap<>();
        for (String[] z : WellenNetz.tsv(new File(d, "umrisse.tsv"))) {
            List<List<String>> l = um.computeIfAbsent(Integer.parseInt(z[0]), x -> new ArrayList<>());
            int teil = Integer.parseInt(z[1]);
            while (l.size() <= teil) l.add(new ArrayList<>());
            l.get(teil).add("(" + z[3].replace(",", ", ") + ")");
        }
        int ne = 0;
        String ins = "INSERT INTO LB_EINZUG (NODE_ID, ART, AUSLAUF_NODE_ID, ZIEL, FLAECHE_EIGEN_KM2, FLAECHE_KM2, EIS_ANTEIL, NAME, UMRISS) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ";
        try (PreparedStatement p = c.prepareStatement(ins + "SDO_GEOMETRY(?, 26908))"); PreparedStatement p0 = c.prepareStatement(ins + "NULL)")) {
            for (String[] z : WellenNetz.tsv(new File(d, "einzug.tsv"))) {
                int k = Integer.parseInt(z[0]);
                String wkt = wkt(um.get(k));
                PreparedStatement q = wkt == null ? p0 : p;
                q.setLong(1, k);
                q.setString(2, z[1]);
                if (z[2].isEmpty()) q.setNull(3, java.sql.Types.NUMERIC); else q.setLong(3, Long.parseLong(z[2]));
                q.setString(4, z[3]);
                q.setDouble(5, Double.parseDouble(z[4]));
                q.setDouble(6, Double.parseDouble(z[5]));
                q.setDouble(7, Double.parseDouble(z[6]));
                q.setString(8, z.length > 7 && !z[7].isEmpty() ? z[7] : null);
                if (wkt != null) q.setClob(9, new java.io.StringReader(wkt));
                q.executeUpdate();
                ne++;
            }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_SEE (SEE_ID, NAME, HOEHE, FLAECHE_KM2, AUSLAUF_NODE_ID, PUNKT) VALUES (?, ?, ?, ?, ?, SDO_GEOMETRY(2001, 26908, SDO_POINT_TYPE(?, ?, NULL), NULL, NULL))")) {
            for (String[] z : WellenNetz.tsv(new File(d, "seen.tsv"))) {
                p.setLong(1, Long.parseLong(z[0]));
                p.setString(2, z.length > 6 && !z[6].isEmpty() ? z[6] : null);
                p.setDouble(3, Double.parseDouble(z[3]));
                p.setDouble(4, Double.parseDouble(z[4]));
                if (z[5].isEmpty()) p.setNull(5, java.sql.Types.NUMERIC); else p.setLong(5, Long.parseLong(z[5]));
                p.setDouble(6, Double.parseDouble(z[1]));
                p.setDouble(7, Double.parseDouble(z[2]));
                p.addBatch();
            }
            p.executeBatch();
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO LB_SENKE (SENKE_ID, FLAECHE_KM2, TIEFE_M, VOLUMEN_M3, AUF_EIS, PUNKT) VALUES (?, ?, ?, ?, ?, SDO_GEOMETRY(2001, 26908, SDO_POINT_TYPE(?, ?, NULL), NULL, NULL))")) {
            for (String[] z : WellenNetz.tsv(new File(d, "senken.tsv"))) {
                p.setLong(1, Long.parseLong(z[0]));
                p.setDouble(2, Double.parseDouble(z[3]));
                p.setDouble(3, Double.parseDouble(z[4]));
                p.setDouble(4, Double.parseDouble(z[5]));
                p.setString(5, z[6]);
                p.setDouble(6, Double.parseDouble(z[1]));
                p.setDouble(7, Double.parseDouble(z[2]));
                p.addBatch();
            }
            p.executeBatch();
        }
        c.commit();
        NetzDb.skript(c, new File(sql, "10_wasser_nach_dem_laden.sql"), true);
        log.log("geladen: %s, %,d Knoten, %,d Kanten (zwei Ebenen, gerichtet), LB_EINZUG %d, LB_SEE, LB_SENKE in %.1f s", WASSER, w.n, w.m, ne, (System.nanoTime() - t0) / 1e9);
    }

    /** WKT eines Gebiets: POLYGON oder MULTIPOLYGON aus Teilen mit Ringen, oder null. */
    static String wkt(List<List<String>> teile) {
        if (teile == null || teile.isEmpty()) return null;
        if (teile.size() == 1) return "POLYGON (" + String.join(", ", teile.get(0)) + ")";
        List<String> p = new ArrayList<>();
        for (List<String> t : teile) p.add("(" + String.join(", ", t) + ")");
        return "MULTIPOLYGON (" + String.join(", ", p) + ")";
    }

    /** Ergebnis der Zweitrechnung. */
    public record Vergleich(boolean ok, String text) { }

    /** LOD (im Speicher bei url null, sonst in Oracle) gegen {@link WasserNetz}: traceIn je Ziel, längste Fließzeit, Komponenten. */
    public static Vergleich lodGegenDatei(WasserNetz w, String url, String user, String pw, URL klassen) throws Exception {
        List<Long> kn = new ArrayList<>();
        for (int i = 0; i < w.n; i++) if (w.ebene[i] == 1) kn.add(i + 1L);
        List<long[]> ka = new ArrayList<>();
        List<Double> co = new ArrayList<>();
        for (int e = 0; e < w.m; e++) if (w.kEbene[e] == 1) { ka.add(new long[]{e + 1L, w.von[e] + 1L, w.nach[e] + 1L}); co.add(w.cost[e]); }
        long[] knoten = kn.stream().mapToLong(Long::longValue).toArray(), id = new long[ka.size()], von = new long[ka.size()], nach = new long[ka.size()];
        double[] cost = new double[ka.size()];
        for (int i = 0; i < ka.size(); i++) { id[i] = ka.get(i)[0]; von[i] = ka.get(i)[1]; nach[i] = ka.get(i)[2]; cost[i] = co.get(i); }
        long[] ziele = new long[WasserNetz.ZIELE.length];
        for (int z = 0; z < ziele.length; z++) ziele[z] = w.ziel(WasserNetz.ZIELE[z], 1) + 1;
        Object[] r;
        try (URLClassLoader cl = com.dan.lituya.netz.Lod.lader(klassen)) {
            Class<?> k = Class.forName("com.dan.lituya.netz.WasserLod", true, cl);
            Method m = k.getMethod("rechnen", String.class, String.class, String.class, long[].class, long[].class, long[].class, long[].class, double[].class, long[].class);
            r = (Object[]) m.invoke(null, url, user, pw, knoten, id, von, nach, cost, ziele);
        }
        long[][] s = (long[][]) r[0];
        double[] tmax = (double[]) r[1];
        int komp = (Integer) r[2];
        int anders = 0, gleich = 0;
        double dt = 0;
        int[] kz = new int[1];
        w.komponenten(1, kz);
        StringBuilder b = new StringBuilder();
        for (int z = 0; z < ziele.length; z++) {
            boolean[] in = w.traceIn((int) ziele[z] - 1, 1);
            double[] t = w.zeitZum((int) ziele[z] - 1, 1);
            int n = 0;
            double tm = 0;
            for (int i = 0; i < w.n; i++) if (in[i] && i != ziele[z] - 1) { n++; if (!Double.isNaN(t[i])) tm = Math.max(tm, t[i]); }
            java.util.Set<Long> lod = new java.util.HashSet<>();
            for (long v : s[z]) lod.add(v);
            for (int i = 0; i < w.n; i++) if (in[i] && i != ziele[z] - 1) { if (lod.remove(i + 1L)) gleich++; else anders++; }
            anders += lod.size();
            dt = Math.max(dt, Math.abs(tm - tmax[z]));
            b.append(String.format(Locale.GERMAN, "%s %d; ", WasserNetz.ZIELE[z], n));
        }
        // Befund Phase 16: in Oracle gehören die Kanten der Ebene 2 auch zur Ebene 1 (NDM: höhere Ebenen sind in den unteren
        // enthalten); findConnectedComponents(1) zählt dort beide Ebenen, im Speicher nur die geladene Ebene 1
        int[] kz2 = new int[1];
        w.komponenten(2, kz2);
        int soll = url == null ? kz[0] : kz[0] + kz2[0];
        boolean ok = anders == 0 && dt <= 0.01 && komp == soll && gleich > 0;
        return new Vergleich(ok, String.format(Locale.GERMAN, "%s; traceIn je Ziel (%s) %,d Knoten gleich, %d anders, längste Fließzeit bis %.4f s gleich; Komponenten %d (Datei %s)",
                r[3], b.toString().trim(), gleich, anders, dt, komp, url == null ? String.valueOf(kz[0]) : kz[0] + " + " + kz2[0] + " der Ebene 2"));
    }

    /** Die Zweitrechnung im Speicher (ohne Oracle; Prüfung). */
    public static Vergleich lodImSpeicher(WasserNetz w) throws Exception { return lodGegenDatei(w, null, null, null, NetzDb.klassen()); }

    /** Fließzeit zum Ziel mit {@code route} (die Kanten der Ebene 1 umgedreht, Länge = COST, within) gegen {@link WasserNetz#zeitZum}. */
    public static Vergleich routeGegenDatei(WasserNetz w) {
        com.dan.route.Route r = new com.dan.route.Route();
        int[] k = new int[w.n];
        for (int i = 0; i < w.n; i++) k[i] = w.ebene[i] == 1 ? r.node(i + 1L, w.ost[i], w.nord[i], w.hoehe[i]) : -1;
        // route nimmt die Länge aus den Koordinaten; die Fließzeit kommt über die Kostenfunktion je Kante (a, b)
        Map<Long, Double> kosten = new HashMap<>();
        for (int e = 0; e < w.m; e++) if (w.kEbene[e] == 1) {
            r.edge(k[w.nach[e]], k[w.von[e]], com.dan.route.Route.Kind.FOOTWAY, 1, true);
            kosten.put((long) k[w.nach[e]] << 32 | k[w.von[e]], w.cost[e]);
        }
        com.dan.route.Route.Cost laenge = (rr, ed, vorwaerts) -> kosten.get((long) ed.a() << 32 | ed.b());   // Einbahn hält route selbst ein
        double dmax = 0;
        int n = 0, anders = 0;
        for (String zn : WasserNetz.ZIELE) {
            int z = w.ziel(zn, 1);
            Map<Integer, Double> t = r.within(k[z], laenge, Double.MAX_VALUE);
            double[] tt = w.zeitZum(z, 1);
            for (int i = 0; i < w.n; i++) {
                if (w.ebene[i] != 1) continue;
                Double a = t.get(k[i]);
                if ((a == null) != Double.isNaN(tt[i])) { anders++; continue; }
                if (a != null) { n++; dmax = Math.max(dmax, Math.abs(a - tt[i])); }
            }
        }
        return new Vergleich(anders == 0 && dmax <= 1e-6 * 3600 && n > 0,
                String.format(Locale.GERMAN, "route (within auf den umgedrehten Kanten): %,d Fließzeiten gleich bis %.2g s, %d anders erreicht", n, dmax, anders));
    }

    /** Validate, Zweitrechnung in Oracle, Flächen über CONNECT BY und SDO_AREA, Eintrag in LB_NETZ_STAND. */
    public static boolean pruefen(Connection c, File daten, String url, String user, String pw, Welle.Log log) throws Exception {
        WasserNetz w = WasserNetz.lesen(daten);
        String v = WelleDb.validate(c, WASSER);
        log.log("VALIDATE_NETWORK(%s) = %s", WASSER, v);
        Vergleich vg;
        try {
            vg = lodGegenDatei(w, url, user, pw, NetzDb.klassen());
        } catch (Exception e) {
            vg = new Vergleich(false, "LOD in Oracle: " + NetzDb.ursache(e));
        }
        log.log("Zweitrechnung Wasser: %s – %s", vg.ok() ? "LOD = Datei" : "LOD ≠ Datei", vg.text());
        // Flächen je Ziel: CONNECT BY auf Ebene 2 gegen pruefwege.tsv
        Map<String, Double> soll = new HashMap<>();
        for (String[] z : WellenNetz.tsv(new File(new File(daten, WasserNetz.ORDNER), "pruefwege.tsv"))) if (z[1].equals("2")) soll.put(z[0], Double.parseDouble(z[3]));
        boolean fl = true;
        StringBuilder b = new StringBuilder();
        try (PreparedStatement p = c.prepareStatement("SELECT ZIEL, FLAECHE_KM2, FLAECHE_RASTER_KM2 FROM LB_V_WASSER_ZIEL ORDER BY ZIEL"); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                double a = r.getDouble(2), s = soll.getOrDefault(r.getString(1), Double.NaN);
                fl &= Math.abs(a - s) <= 1e-3 && Math.abs(a - r.getDouble(3)) <= 1e-3;
                b.append(String.format(Locale.GERMAN, "%s %.1f km²; ", r.getString(1), a));
            }
        }
        log.log("Flächen je Ziel über CONNECT BY (Ebene 2): %s%s", b.toString().trim(), fl ? " – wie die Dateien" : " – ANDERS als die Dateien");
        String ungueltig = Db.einWert(c, "SELECT COUNT(*) FROM LB_EINZUG WHERE UMRISS IS NOT NULL AND SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(UMRISS, 0.005) <> 'TRUE'");
        String abw = Db.einWert(c, "SELECT ROUND(MAX(ABS(UMRISS_KM2 - FLAECHE_EIGEN_KM2) / FLAECHE_EIGEN_KM2) * 100, 1) FROM LB_V_EINZUG WHERE FLAECHE_EIGEN_KM2 >= 5");
        if (!"0".equals(ungueltig))
            try (PreparedStatement p = c.prepareStatement("SELECT NODE_ID, SUBSTR(SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(UMRISS, 0.005), 1, 120) FROM LB_EINZUG "
                    + "WHERE UMRISS IS NOT NULL AND SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(UMRISS, 0.005) <> 'TRUE' AND ROWNUM <= 5"); ResultSet r = p.executeQuery()) {
                while (r.next()) log.log("  ungültig: Gebiet %d: %s", r.getLong(1), r.getString(2));
            }
        log.log("LB_EINZUG: %s Umrisse ungültig; Fläche des Umrisses (50 m) gegen die aus 10 m bei Gebieten ab 5 km² bis %s %%", ungueltig, abw);
        boolean gut = "TRUE".equalsIgnoreCase(v) && vg.ok() && fl && "0".equals(ungueltig);
        WelleDb.stand(c, WASSER, "Wasser (Werkbuch 7.4): Bäche ab 0,5 km², Gletscher, zwei Ebenen (Teileinzugsgebiete); Fließzeit bei Regen", w.n, w.m,
                WellenNetz.pruefsumme(new File(daten, WasserNetz.ORDNER)), "daten/" + WasserNetz.ORDNER,
                "VALIDATE " + v + "; LOD = Datei " + (vg.ok() ? "ok" : "FEHLER") + "; CONNECT BY " + (fl ? "ok" : "FEHLER") + "; Umrisse ungültig " + ungueltig);
        return gut;
    }
}
