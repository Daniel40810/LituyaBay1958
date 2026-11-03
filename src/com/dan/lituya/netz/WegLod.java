package com.dan.lituya.netz;

import oracle.spatial.network.lod.LODAnalysisInfo;
import oracle.spatial.network.lod.LODNetworkConstraint;
import oracle.spatial.network.lod.LODNetworkFactory;
import oracle.spatial.network.lod.LODNetworkManager;
import oracle.spatial.network.lod.LogicalBasicNetwork;
import oracle.spatial.network.lod.LogicalNetLink;
import oracle.spatial.network.lod.LogicalNetNode;
import oracle.spatial.network.lod.LogicalSubPath;
import oracle.spatial.network.lod.NetworkAnalyst;
import oracle.spatial.network.lod.NetworkIO;
import oracle.spatial.network.lod.PointOnNet;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * LOD auf {@code LB_NET_WEG} (Phase 16a, Werkbuch 7.5): {@code withinCost} eine Stunde vom Strand, {@code shortestPathDijkstra}
 * für die Abschnitte von Washburns Route und {@code kShortestPaths} (k = 3) für den letzten, als zweite Rechnung neben dem
 * Dijkstra von {@code scene.WegNetz}. Im Speicher auf einem Ausschnitt (die Kanten kommen herein) oder in Oracle auf dem
 * ganzen Netz; läuft im eigenen Klassenlader von {@link Lod} und tauscht nur Typen des JDK aus.
 */
public final class WegLod {
    private WegLod() { }

    public static final String NETZ = "LB_NET_WEG";

    static NetworkAnalyst imSpeicher(long[] knoten, long[] von, long[] nach, double[] cost) throws Exception {
        LogicalBasicNetwork net = LODNetworkFactory.createLogicalBasicNetwork(NETZ, 1);
        Map<Long, LogicalNetNode> k = new HashMap<>(knoten.length * 2);
        for (long x : knoten) {
            LogicalNetNode nd = LODNetworkFactory.createLogicalNetNode(x, 0, true, (Map<String, Object>) null);
            k.put(x, nd);
            net.addNode(nd);
        }
        for (int i = 0; i < von.length; i++) {
            LogicalNetLink l = LODNetworkFactory.createLogicalNetLink(i + 1, k.get(von[i]), k.get(nach[i]), cost[i], false, true, (Map<String, Object>) null);
            net.addLink(l);
            k.get(von[i]).addOutLink(l);
            k.get(nach[i]).addInLink(l);
        }
        return LODNetworkManager.getNetworkAnalyst(LODNetworkManager.getNetworkIOForInMemoryNetwork(new LogicalBasicNetwork[]{net}));
    }

    /**
     * Rückgabe {long[] erreichte Knoten, double[] ihre Zeit, double[] Zeit je Abschnitt, double[] k = 3 Zeiten des letzten
     * Abschnitts, String info}. Im Speicher, wenn url null ist (dann knoten, von, nach, cost), sonst in Oracle.
     */
    public static Object[] rechnen(String url, String user, String pw, long[] knoten, long[] von, long[] nach, double[] cost, long start, double grenze, long[] wegpunkte) throws Exception {
        long t0 = System.nanoTime();
        String wo;
        Object[] r;
        if (url == null) {
            r = analysen(imSpeicher(knoten, von, nach, cost), start, grenze, wegpunkte);
            wo = "im Speicher (Ausschnitt, " + knoten.length + " Knoten)";
        } else {
            try (Connection c = LODNetworkManager.getConnection(url, user, pw)) {
                NetworkIO io = LODNetworkManager.getCachedNetworkIO(c, NETZ, NETZ, null);
                r = analysen(LODNetworkManager.getNetworkAnalyst(io), start, grenze, wegpunkte);
                wo = "Oracle " + NETZ + ", Treiber " + c.getMetaData().getDriverVersion();
            }
        }
        r[4] = String.format(Locale.ROOT, "LOD %s: withinCost 1 h, shortestPathDijkstra × %d, kShortestPaths k = 3, %.1f s", wo, Math.max(0, wegpunkte.length - 1), (System.nanoTime() - t0) / 1e9);
        return r;
    }

    /** Phase 16b: ein Knoten nur vor seiner Front (Ereigniszeit = delta + bisherige Kosten). */
    static final class VorDerFront implements LODNetworkConstraint {
        final double[] front;
        final double delta;
        long abgelehnt;
        VorDerFront(double[] front, double delta) { this.front = front; this.delta = delta; }

        @Override public boolean isSatisfied(LODAnalysisInfo info) {
            LogicalNetNode n = info.getNextNode();
            if (n == null) return true;
            int id = (int) n.getId();
            double fr = id >= 0 && id < front.length ? front[id] : Double.NaN;
            if (Double.isNaN(fr)) return true;
            double[] c = info.getNextCosts();
            boolean ok = delta + (c != null && c.length > 0 ? c[0] : info.getNextCost()) < fr;
            if (!ok) abgelehnt++;
            return ok;
        }

        @Override public int[] getUserDataCategories() { return null; }
        @Override public int getNumberOfUserObjects() { return 0; }
        @Override public boolean isCurrentNodePartiallyExpanded(LODAnalysisInfo info) { return false; }
        @Override public void reset() { }
        @Override public void setNetworkAnalyst(NetworkAnalyst a) { }
    }

    /**
     * Phase 16b, Flucht an Land: je Start (Landeplatz) und Zeit an Land die früheste Ankunft auf einem sicheren Knoten,
     * mit withinCost und der Frontbedingung. front und sicher je Knoten-Nummer. Rückgabe {double[] Zeit (Ereigniszeit,
     * NaN: keiner), long[] Knoten, String info}.
     */
    public static Object[] landflucht(String url, String user, String pw, long[] knoten, long[] von, long[] nach, double[] cost, long[] starts, double[] zeiten,
                                      double[] front, boolean[] sicher, double grenze) throws Exception {
        long t0 = System.nanoTime();
        double[] t = new double[starts.length];
        long[] z = new long[starts.length];
        long[] abg = new long[1];
        String wo;
        if (url == null) {
            NetworkAnalyst a = imSpeicher(knoten, von, nach, cost);
            for (int i = 0; i < starts.length; i++) sicher(a, starts[i], zeiten[i], front, sicher, grenze, t, z, i, abg);
            wo = "im Speicher (Ausschnitt, " + knoten.length + " Knoten)";
        } else {
            try (Connection c = LODNetworkManager.getConnection(url, user, pw)) {
                NetworkAnalyst a = LODNetworkManager.getNetworkAnalyst(LODNetworkManager.getCachedNetworkIO(c, NETZ, NETZ, null));
                for (int i = 0; i < starts.length; i++) sicher(a, starts[i], zeiten[i], front, sicher, grenze, t, z, i, abg);
                wo = "Oracle " + NETZ;
            }
        }
        return new Object[]{t, z, String.format(Locale.ROOT, "LOD %s: withinCost mit Frontbedingung × %d (%d Knoten abgelehnt), %.1f s", wo, starts.length, abg[0], (System.nanoTime() - t0) / 1e9)};
    }

    static void sicher(NetworkAnalyst a, long s, double t0, double[] front, boolean[] sicher, double grenze, double[] t, long[] z, int i, long[] abg) throws Exception {
        VorDerFront v = new VorDerFront(front, t0);
        LogicalSubPath[] w = a.withinCost(new PointOnNet(s), grenze, v, null);
        abg[0] += v.abgelehnt;
        double best = sicher[(int) s] ? 0 : Double.POSITIVE_INFINITY;
        long bk = sicher[(int) s] ? s : -1;
        if (w != null)
            for (LogicalSubPath x : w) {
                if (x.getEndPercentage() < 1 - 1e-9) continue;
                long e = x.getReferencePath().getEndNodeId();
                if (e >= 0 && e < sicher.length && sicher[(int) e] && x.getCost() < best) { best = x.getCost(); bk = e; }
            }
        t[i] = bk < 0 ? Double.NaN : t0 + best;
        z[i] = bk;
    }

    static Object[] analysen(NetworkAnalyst a, long start, double grenze, long[] wp) throws Exception {
        LogicalSubPath[] w = a.withinCost(new PointOnNet(start), grenze, null, null);
        Map<Long, Double> t = new HashMap<>();
        t.put(start, 0.0);
        if (w != null)
            for (LogicalSubPath x : w) {
                if (x.getEndPercentage() < 1 - 1e-9) continue;   // endet mitten auf einer Kante
                long e = x.getReferencePath().getEndNodeId();
                t.merge(e, x.getCost(), Math::min);
            }
        long[] ids = new long[t.size()];
        double[] ts = new double[t.size()];
        int i = 0;
        for (var en : t.entrySet()) { ids[i] = en.getKey(); ts[i++] = en.getValue(); }
        double[] ab = new double[Math.max(0, wp.length - 1)];
        double[] k3 = new double[0];
        for (int q = 0; q + 1 < wp.length; q++) {
            LogicalSubPath s = a.shortestPathDijkstra(new PointOnNet(wp[q]), new PointOnNet(wp[q + 1]), null);
            ab[q] = s == null ? Double.NaN : s.getCost();
        }
        if (wp.length >= 2) {
            LogicalSubPath[] k = a.kShortestPaths(new PointOnNet[]{new PointOnNet(wp[wp.length - 2])}, new PointOnNet[]{new PointOnNet(wp[wp.length - 1])}, 3, null);
            k3 = new double[k == null ? 0 : k.length];
            for (int q = 0; q < k3.length; q++) k3[q] = k[q].getCost();
        }
        return new Object[]{ids, ts, ab, k3, null};
    }
}
