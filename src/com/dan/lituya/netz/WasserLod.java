package com.dan.lituya.netz;

import oracle.spatial.network.lod.LODNetworkFactory;
import oracle.spatial.network.lod.LODNetworkManager;
import oracle.spatial.network.lod.LogicalBasicNetwork;
import oracle.spatial.network.lod.LogicalNetLink;
import oracle.spatial.network.lod.LogicalNetNode;
import oracle.spatial.network.lod.LogicalSubPath;
import oracle.spatial.network.lod.NetworkAnalyst;
import oracle.spatial.network.lod.NetworkIO;
import oracle.spatial.network.lod.OrderedLongSet;
import oracle.spatial.network.lod.PointOnNet;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * LOD auf {@code LB_NET_WASSER} (Phase 16, Werkbuch 7.4): traceIn von jedem Ziel der Ebene 1 (welche Knoten entwässern
 * dorthin, mit der längsten Fließzeit) und die Zusammenhangskomponenten, als zweite Rechnung neben {@code scene.WasserNetz}.
 * Im Speicher oder in Oracle; läuft im eigenen Klassenlader von {@link Lod} und tauscht nur Typen des JDK aus.
 */
public final class WasserLod {
    private WasserLod() { }

    public static final String NETZ = "LB_NET_WASSER";

    /** Das Netz der Ebene 1 im Speicher (gerichtet: x = false „beide Richtungen“, y = true „aktiv“, Befund 15a). */
    static NetworkAnalyst imSpeicher(long[] knoten, long[] id, long[] von, long[] nach, double[] cost) throws Exception {
        LogicalBasicNetwork net = LODNetworkFactory.createLogicalBasicNetwork(NETZ, 1);
        Map<Long, LogicalNetNode> k = new HashMap<>(knoten.length * 2);
        for (long x : knoten) {
            LogicalNetNode nd = LODNetworkFactory.createLogicalNetNode(x, 0, true, (Map<String, Object>) null);
            k.put(x, nd);
            net.addNode(nd);
        }
        for (int i = 0; i < von.length; i++) {
            LogicalNetLink l = LODNetworkFactory.createLogicalNetLink(id[i], k.get(von[i]), k.get(nach[i]), cost[i], false, true, (Map<String, Object>) null);
            net.addLink(l);
            k.get(von[i]).addOutLink(l);
            k.get(nach[i]).addInLink(l);
        }
        return LODNetworkManager.getNetworkAnalyst(LODNetworkManager.getNetworkIOForInMemoryNetwork(new LogicalBasicNetwork[]{net}));
    }

    /**
     * Für {@code tools}: Knoten und Kanten der Ebene 1 (Nummern wie in den Dateien), die Ziele; im Speicher (url null) oder
     * in Oracle. Rückgabe {long[][] Knoten je Ziel (sortiert, ohne das Ziel), double[] längste Zeit je Ziel, Integer
     * Komponenten, String info}.
     */
    public static Object[] rechnen(String url, String user, String pw, long[] knoten, long[] id, long[] von, long[] nach, double[] cost, long[] ziele) throws Exception {
        long t0 = System.nanoTime();
        long[][] r = new long[ziele.length][];
        double[] tmax = new double[ziele.length];
        int komp;
        String wo;
        if (url == null) {
            NetworkAnalyst a = imSpeicher(knoten, id, von, nach, cost);
            for (int z = 0; z < ziele.length; z++) r[z] = trace(a, ziele[z], tmax, z);
            komp = komponenten(a);
            wo = "im Speicher";
        } else {
            try (Connection c = LODNetworkManager.getConnection(url, user, pw)) {
                NetworkIO io = LODNetworkManager.getCachedNetworkIO(c, NETZ, NETZ, null);
                NetworkAnalyst a = LODNetworkManager.getNetworkAnalyst(io);
                for (int z = 0; z < ziele.length; z++) r[z] = trace(a, ziele[z], tmax, z);
                komp = komponenten(a);
                wo = "Oracle " + NETZ + ", Treiber " + c.getMetaData().getDriverVersion();
            }
        }
        String text = String.format(Locale.ROOT, "LOD %s: traceIn × %d, findConnectedComponents = %d, %.1f s", wo, ziele.length, komp, (System.nanoTime() - t0) / 1e9);
        return new Object[]{r, tmax, komp, text};
    }

    static long[] trace(NetworkAnalyst a, long ziel, double[] tmax, int z) throws Exception {
        LogicalSubPath[] w = a.traceIn(new PointOnNet(ziel), Double.MAX_VALUE, null, null);
        TreeSet<Long> s = NdmProbeKern.knoten(w, true);
        s.remove(ziel);
        double m = 0;
        if (w != null) for (LogicalSubPath x : w) m = Math.max(m, x.getCost());
        tmax[z] = m;
        long[] r = new long[s.size()];
        int i = 0;
        for (long v : s) r[i++] = v;
        return r;
    }

    static int komponenten(NetworkAnalyst a) throws Exception {
        OrderedLongSet[] k = a.findConnectedComponents(1);
        return k == null ? 0 : k.length;
    }
}
