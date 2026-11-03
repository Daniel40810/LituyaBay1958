package com.dan.lituya.netz;

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
