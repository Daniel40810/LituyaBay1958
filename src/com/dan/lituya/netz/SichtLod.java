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
import oracle.spatial.network.lod.TSP;
import oracle.spatial.network.lod.TspPath;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * LOD auf {@code LB_NET_SICHT} (Phase 17, Werkbuch 7.6): {@code mcst} (Spannbaum), {@code tsp} (Rundflug über die
 * Motive, geschlossen) und {@code nearestNeighbors} (je Ansicht die nächsten), als zweite Rechnung neben
 * {@code scene.SichtNetz}. Im Speicher oder in Oracle; läuft im eigenen Klassenlader von {@link Lod} und tauscht nur Typen
 * des JDK aus.
 */
public final class SichtLod {
    private SichtLod() { }

    public static final String NETZ = "LB_NET_SICHT";

    /** Das Netz im Speicher, ungerichtet (x = true „in beiden Richtungen“, y = true „aktiv“, wie das Probenetz). */
    static NetworkAnalyst imSpeicher(int n, long[] von, long[] nach, double[] cost) throws Exception {
        LogicalBasicNetwork net = LODNetworkFactory.createLogicalBasicNetwork(NETZ, 1);
        Map<Long, LogicalNetNode> k = new HashMap<>(n * 2);
        for (long id = 1; id <= n; id++) {
            LogicalNetNode x = LODNetworkFactory.createLogicalNetNode(id, 0, true, (Map<String, Object>) null);
            k.put(id, x);
            net.addNode(x);
        }
        for (int i = 0; i < von.length; i++) {
            LogicalNetLink l = LODNetworkFactory.createLogicalNetLink(i + 1, k.get(von[i]), k.get(nach[i]), cost[i], true, true, (Map<String, Object>) null);
            net.addLink(l);
            k.get(von[i]).addOutLink(l);
            k.get(nach[i]).addInLink(l);
        }
        return LODNetworkManager.getNetworkAnalyst(LODNetworkManager.getNetworkIOForInMemoryNetwork(new LogicalBasicNetwork[]{net}));
    }

    /**
     * Für {@code tools}: Knoten 1 … n, Kanten 1 … m (Anfang, Ende, COST), die Motive (der erste ist der Anfang des
     * Rundflugs) und die Knoten für nearestNeighbors mit k; im Speicher (url null) oder in Oracle. Rückgabe {long[]
     * Spannbaum (Kanten), int[] Folge der Motive, double Kosten des Rundflugs, long[][] Nachbarn, double[][] Kosten der
     * Nachbarn, String info}.
     */
    public static Object[] rechnen(String url, String user, String pw, int n, long[] von, long[] nach, double[] cost, long[] motive, long[] starts, int k) throws Exception {
        long t0 = System.nanoTime();
        if (url == null) {
            Object[] r = analysen(imSpeicher(n, von, nach, cost), motive, starts, k);
            r[5] = String.format(Locale.ROOT, "LOD im Speicher: mcst, tsp über %d Motive, nearestNeighbors × %d, %.1f s", motive.length, starts.length, (System.nanoTime() - t0) / 1e9);
            return r;
        }
        try (Connection c = LODNetworkManager.getConnection(url, user, pw)) {
            NetworkIO io = LODNetworkManager.getCachedNetworkIO(c, NETZ, NETZ, null);
            Object[] r = analysen(LODNetworkManager.getNetworkAnalyst(io), motive, starts, k);
            r[5] = String.format(Locale.ROOT, "LOD Oracle %s (Treiber %s): mcst, tsp über %d Motive, nearestNeighbors × %d, %.1f s", NETZ, c.getMetaData().getDriverVersion(),
                    motive.length, starts.length, (System.nanoTime() - t0) / 1e9);
            return r;
        }
    }

    static Object[] analysen(NetworkAnalyst a, long[] motive, long[] starts, int k) throws Exception {
        long[] mst = a.mcst(1, null);
        PointOnNet[][] p = new PointOnNet[motive.length][];
        for (int i = 0; i < motive.length; i++) p[i] = new PointOnNet[]{new PointOnNet(motive[i])};
        TspPath t = a.tsp(p, TSP.TourFlag.CLOSED, null);
        double s = 0;
        for (double d : t.getCosts()) s += d;
        long[][] nb = new long[starts.length][];
        double[][] nc = new double[starts.length][];
        for (int i = 0; i < starts.length; i++) {
            LogicalSubPath[] w = a.nearestNeighbors(new PointOnNet(starts[i]), k, null, null);
            nb[i] = new long[w.length];
            nc[i] = new double[w.length];
            for (int j = 0; j < w.length; j++) { nb[i][j] = w[j].getReferencePath().getEndNodeId(); nc[i][j] = w[j].getCost(); }
        }
        return new Object[]{mst, t.getTspOrder(), s, nb, nc, null};
    }
}
