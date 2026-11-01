package com.dan.lituya.netz;

import oracle.spatial.geometry.JGeometry;
import oracle.spatial.network.UserDataMetadata;
import oracle.spatial.network.lod.LODAnalysisInfo;
import oracle.spatial.network.lod.LODNetworkFactory;
import oracle.spatial.network.lod.LODNetworkManager;
import oracle.spatial.network.lod.LinkCostCalculator;
import oracle.spatial.network.lod.LogicalBasicNetwork;
import oracle.spatial.network.lod.LogicalNetLink;
import oracle.spatial.network.lod.LogicalNetNode;
import oracle.spatial.network.lod.LogicalSubPath;
import oracle.spatial.network.lod.NetworkAnalyst;
import oracle.spatial.network.lod.NetworkIO;
import oracle.spatial.network.lod.PointOnNet;
import oracle.spatial.network.lod.UserData;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LOD auf {@code LB_NET_WELLE} (Phase 7a, Werkbuch 7.1, F22): die Ankunft der Front je Knoten mit {@code withinCost}
 * von {@code QUELLE}, je Kostenart mit einem eigenen {@link LinkCostCalculator}, der die Kosten aus den Benutzerdaten der
 * Kanten liest ({@code T_HEUTE}, {@code T_1940}, {@code T_1959}, {@code T_NICHTLINEAR}, Kategorie 0, in den Partition-BLOBs).
 * Fehlen die Benutzerdaten, nimmt er sie aus einer Tabelle je Kanten-Nummer (dieselben Zahlen aus
 * {@code daten/netz/welle/kanten.tsv}) und sagt es. Dazu {@code kShortestPaths} als Wellenstrahlen und
 * {@code fastWithinCostPolygon} als Isochronen-Fläche (Network Buffer, F23).
 * <p>
 * Läuft im eigenen Klassenlader von {@link Lod}; tauscht nur Typen des JDK aus. {@link #imSpeicher} baut dasselbe Netz
 * ohne Datenbank (für die Prüfung hier und {@code ant netzvergleich} ohne Oracle).
 */
public final class WelleLod {
    private WelleLod() { }

    public static final String NETZ = "LB_NET_WELLE";
    static final String[] KOSTEN = {"T_HEUTE", "T_1940", "T_1959", "T_NICHTLINEAR"};

    /** Kosten einer Kostenart: aus den Benutzerdaten (Index aus den Metadaten), sonst aus der Tabelle. */
    public static final class Kosten implements LinkCostCalculator {
        final int index;
        final double[] tabelle;
        long ausDaten, ausTabelle, abweichend;

        Kosten(int index, double[] tabelle) { this.index = index; this.tabelle = tabelle; }

        @Override public double getLinkCost(LODAnalysisInfo info) {
            LogicalNetLink l = info.getNextLink();
            long id = l.getId();
            double t = id >= 0 && id < tabelle.length ? tabelle[(int) id] : l.getCost();
            if (index >= 0) {
                UserData u = l.getUserData(0);
                if (u != null && index < u.getNumberOfUserData() && u.get(index) instanceof Number n) {
                    ausDaten++;
                    if (Math.abs(n.doubleValue() - t) > 1e-3) abweichend++;
                    return n.doubleValue();
                }
            }
            ausTabelle++;
            return t;
        }

        @Override public int[] getUserDataCategories() { return index >= 0 ? new int[]{0} : null; }

        String weg() {
            if (ausDaten > 0 && ausTabelle == 0) return "Benutzerdaten" + (abweichend > 0 ? " (" + abweichend + " anders als die Datei)" : "");
            if (ausDaten > 0) return String.format(Locale.ROOT, "Benutzerdaten %d, Tabelle %d", ausDaten, ausTabelle);
            return "Tabelle";
        }
    }

    /** Der Index einer Benutzerdatenspalte der Kanten (Kategorie 0) oder −1. */
    static int index(NetworkIO io, String name) {
        try {
            UserDataMetadata m = io.getNetworkMetadata().getUserDataMetadata();
            if (m == null) return -1;
            Map<String, Integer> map = m.getLinkDataIndexMap(0);
            Integer i = map == null ? null : map.get(name);
            return i == null ? -1 : i;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * Ankunft je Kostenart und Knoten-Nummer (bis maxId), dazu je Kostenart, woher die Kosten kamen.
     * tabelle[a][linkId] sind die Kosten der Kostenart a (für den Rückfall); io darf null sein (im Speicher).
     */
    public static Object[] ankunft(NetworkAnalyst a, NetworkIO io, long quelle, int maxId, double[][] tabelle) throws Exception {
        double[][] t = new double[4][maxId + 1];
        String[] weg = new String[4];
        double[] ms = new double[4];
        var alt = a.getLinkCostCalculators();
        boolean daten = io != null;
        try {
            for (int k = 0; k < 4; k++) {
                java.util.Arrays.fill(t[k], Double.NaN);
                Kosten c = new Kosten(daten ? index(io, KOSTEN[k]) : -1, tabelle[k]);
                a.setLinkCostCalculator(c);
                long t0 = System.nanoTime();
                LogicalSubPath[] w = a.withinCost(new PointOnNet(quelle), 1e9, null, null);
                ms[k] = (System.nanoTime() - t0) / 1e6;
                t[k][(int) quelle] = 0;
                for (LogicalSubPath x : w) {
                    long n = x.getReferencePath().getEndNodeId();
                    if (n >= 0 && n <= maxId && x.getEndPercentage() > 1 - 1e-9) t[k][(int) n] = x.getCost();
                }
                weg[k] = c.weg() + String.format(Locale.ROOT, ", %d Wege, %.0f ms", w.length, ms[k]);
                // kamen keine Benutzerdaten, fragt die nächste Kostenart gar nicht erst danach (spart das Nachladen)
                if (c.ausDaten == 0) daten = false;
            }
        } finally {
            a.setLinkCostCalculators(alt);
        }
        return new Object[]{t, weg};
    }

    /** Wellenstrahlen: die k kürzesten Wege von QUELLE zum Ziel (Kosten heute linear, COST); je Weg Kosten und Knoten. */
    public static Object[] strahlen(NetworkAnalyst a, long quelle, long ziel, int k) throws Exception {
        LogicalSubPath[] w = a.kShortestPaths(new PointOnNet[]{new PointOnNet(quelle)}, new PointOnNet[]{new PointOnNet(ziel)}, k, null);
        double[] c = new double[w.length];
        long[][] n = new long[w.length][];
        for (int i = 0; i < w.length; i++) { c[i] = w[i].getCost(); n[i] = w[i].getReferencePath().getNodeIds(); }
        return new Object[]{c, n};
    }

    /** Isochrone als Fläche (fastWithinCostPolygon, Kosten heute linear) als WKT; null, wenn es nicht geht. */
    public static String puffer(NetworkAnalyst a, long quelle, double t) {
        try {
            JGeometry g = a.fastWithinCostPolygon(new PointOnNet[]{new PointOnNet(quelle)}, t, null, null);
            return g == null ? null : wkt(g);
        } catch (Exception | LinkageError e) {
            return "FEHLER " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /** Polygone (Typ 2003/2007, auch mit Löchern) als WKT in 2D. */
    static String wkt(JGeometry g) {
        int[] e = g.getElemInfo();
        double[] o = g.getOrdinatesArray();
        int d = g.getDimensions();
        if (e == null || o == null) return null;
        List<List<String>> polys = new ArrayList<>();
        for (int i = 0; i + 2 < e.length; i += 3) {
            int start = e[i] - 1, etype = e[i + 1], end = i + 3 < e.length ? e[i + 3] - 1 : o.length;
            if (etype != 1003 && etype != 2003) continue;
            StringBuilder b = new StringBuilder("(");
            for (int p = start; p + d - 1 < end; p += d) b.append(p > start ? ", " : "").append(String.format(Locale.ROOT, "%.3f %.3f", o[p], o[p + 1]));
            b.append(')');
            if (etype == 1003 || polys.isEmpty()) polys.add(new ArrayList<>());
            polys.get(polys.size() - 1).add(b.toString());
        }
        if (polys.isEmpty()) return null;
        StringBuilder b = new StringBuilder(polys.size() == 1 ? "POLYGON " : "MULTIPOLYGON (");
        for (int i = 0; i < polys.size(); i++) b.append(i > 0 ? ", " : "").append('(').append(String.join(", ", polys.get(i))).append(')');
        if (polys.size() > 1) b.append(')');
        return b.toString();
    }

    /**
     * Zählt Zeilen, die LOD selbst ausgibt und die nur „java.lang.NullPointerException“ sagen (Befund Phase 7a: beim Lesen
     * der Benutzerdaten tausendfach), statt sie durchzulassen; alles andere geht weiter.
     */
    static final class Filter extends java.io.OutputStream {
        final java.io.PrintStream ziel;
        final StringBuilder zeile = new StringBuilder();
        long verschluckt;
        Filter(java.io.PrintStream ziel) { this.ziel = ziel; }
        @Override public synchronized void write(int b) {
            if (b == '\n') {
                String z = zeile.toString().strip();
                if (z.equals("java.lang.NullPointerException")) verschluckt++; else ziel.println(zeile);
                zeile.setLength(0);
            } else if (b != '\r') zeile.append((char) b);
        }
    }

    /** Alles für {@code ant netzdb} und {@code ant netzvergleich} in Oracle: Ankunft, Strahlen zu den Zielen, Flächen zu den Zeiten. */
    public static Object[] oracle(String url, String user, String pw, long quelle, int maxId, double[][] tabelle, long[] ziele, double[] zeiten) throws Exception {
        java.io.PrintStream out = System.out, err = System.err;
        Filter fo = new Filter(out), fe = new Filter(err);
        System.setOut(new java.io.PrintStream(fo, true, java.nio.charset.StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(fe, true, java.nio.charset.StandardCharsets.UTF_8));
        try {
            Object[] r = oracleIntern(url, user, pw, quelle, maxId, tabelle, ziele, zeiten);
            long n = fo.verschluckt + fe.verschluckt;
            if (n > 0) r[4] = r[4] + String.format(Locale.ROOT, "; LOD meldete %d-mal NullPointerException (ausgeblendet)", n);
            return r;
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
    }

    static Object[] oracleIntern(String url, String user, String pw, long quelle, int maxId, double[][] tabelle, long[] ziele, double[] zeiten) throws Exception {
        try (Connection c = LODNetworkManager.getConnection(url, user, pw)) {
            long t0 = System.nanoTime();
            NetworkIO io = LODNetworkManager.getCachedNetworkIO(c, NETZ, NETZ, null);
            NetworkAnalyst a = LODNetworkManager.getNetworkAnalyst(io);
            Object[] an = ankunft(a, io, quelle, maxId, tabelle);
            double[][] sc = new double[ziele.length][];
            for (int i = 0; i < ziele.length; i++) {
                try { sc[i] = (double[]) strahlen(a, quelle, ziele[i], 3)[0]; } catch (Exception e) { sc[i] = new double[0]; }
            }
            String[] p = new String[zeiten.length];
            for (int i = 0; i < zeiten.length; i++) p[i] = puffer(a, quelle, zeiten[i]);
            String info = String.format(Locale.ROOT, "LOD %s, Treiber %s, %.1f s", NETZ, c.getMetaData().getDriverVersion(), (System.nanoTime() - t0) / 1e9);
            return new Object[]{an[0], an[1], sc, p, info};
        }
    }

    /** Dasselbe Netz im Speicher (ohne Oracle): Knoten-Nummern, Kanten mit Anfang, Ende, COST; ungerichtet. */
    public static NetworkAnalyst imSpeicher(long[] knoten, long[] kanten, long[] von, long[] nach, double[] kosten) throws Exception {
        LogicalBasicNetwork n = LODNetworkFactory.createLogicalBasicNetwork(NETZ, 1);
        Map<Long, LogicalNetNode> k = new HashMap<>(knoten.length * 2);
        for (long id : knoten) {
            LogicalNetNode x = LODNetworkFactory.createLogicalNetNode(id, 0, true, (Map<String, Object>) null);
            k.put(id, x);
            n.addNode(x);
        }
        for (int i = 0; i < kanten.length; i++) {
            LogicalNetLink l = LODNetworkFactory.createLogicalNetLink(kanten[i], k.get(von[i]), k.get(nach[i]), kosten[i], true, true, (Map<String, Object>) null);
            n.addLink(l);
            k.get(von[i]).addOutLink(l);
            k.get(nach[i]).addInLink(l);
        }
        return LODNetworkManager.getNetworkAnalyst(LODNetworkManager.getNetworkIOForInMemoryNetwork(new LogicalBasicNetwork[]{n}));
    }

    /** Im Speicher: Ankunft je Kostenart (aus der Tabelle). */
    public static Object[] imSpeicherAnkunft(long[] knoten, long[] kanten, long[] von, long[] nach, double[][] tabelle, long quelle, int maxId) throws Exception {
        double[] cost = new double[kanten.length];
        for (int i = 0; i < kanten.length; i++) cost[i] = tabelle[0][(int) kanten[i]];
        NetworkAnalyst a = imSpeicher(knoten, kanten, von, nach, cost);
        return ankunft(a, null, quelle, maxId, tabelle);
    }
}
