package com.dan.lituya.netz;

import oracle.spatial.network.lod.LODAnalysisInfo;
import oracle.spatial.network.lod.LODNetworkConstraint;
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

import java.sql.Connection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * LOD auf {@code LB_NET_SEE} (Phase 15a, Werkbuch 7.3): die Flucht vor der Front als zweite Rechnung neben dem eigenen
 * Dijkstra von {@code scene.SeeNetz}. Ein {@link LinkCostCalculator} rechnet die Kosten einer Kante aus der Zeit, zu der
 * das Boot an ihrem Anfang ist ({@code getCurrentCosts()}: bisherige Fahrzeit + Abfahrt Δ), mit dem Gezeitenstrom im Tor
 * zu dieser Zeit; eine {@link LODNetworkConstraint} lässt einen Knoten nur zu, wenn das Boot vor der Front dort ist
 * ({@code getNextCosts()}). {@code withinCost} vom Ankerplatz gibt die früheste Ankunft je Knoten.
 * <p>
 * Läuft im eigenen Klassenlader von {@link Lod}; tauscht nur Typen des JDK aus. Kanten, Fronten und die Tabelle des Stroms
 * kommen als Felder je Nummer herein (wie {@code daten/netz/see}), im Speicher wie in Oracle.
 */
public final class SeeLod {
    private SeeLod() { }

    public static final String NETZ = "LB_NET_SEE";
    static final double FAHRT = 8 * 0.514444;

    /** Strom im Tor zur Zeit t (s nach dem Stoß) aus der Tabelle je Minute ab −30 min (wie SeeNetz.stromBei). */
    static double strom(double[] s, double t) {
        double x = (t + 1800) / 60;
        int i = (int) Math.floor(x);
        if (i < 0) return s[0];
        if (i >= s.length - 1) return s[s.length - 1];
        return s[i] + (s[i + 1] - s[i]) * (x - i);
    }

    /** Daten je Kanten-Nummer (Index = LINK_ID) und je Knoten-Nummer (Index = NODE_ID). */
    static final class Felder {
        double[] laenge, re, rn, faktor, front, strom;
        double hinE, hinN, delta;
    }

    static final class Kosten implements LinkCostCalculator {
        final Felder f;
        long aufrufe;
        Kosten(Felder f) { this.f = f; }

        @Override public double getLinkCost(LODAnalysisInfo info) {
            aufrufe++;
            int id = (int) info.getNextLink().getId();
            double[] c = info.getCurrentCosts();
            double t = f.delta + (c != null && c.length > 0 ? c[0] : info.getCurrentCost());
            double u = strom(f.strom, t) * f.faktor[id];
            double v = Math.max(0.5, FAHRT + u * (f.re[id] * f.hinE + f.rn[id] * f.hinN));
            return f.laenge[id] / v;
        }

        @Override public int[] getUserDataCategories() { return null; }
    }

    static final class VorDerFront implements LODNetworkConstraint {
        final Felder f;
        long abgelehnt;
        VorDerFront(Felder f) { this.f = f; }

        @Override public boolean isSatisfied(LODAnalysisInfo info) {
            LogicalNetNode n = info.getNextNode();
            if (n == null) return true;
            int id = (int) n.getId();
            double fr = id >= 0 && id < f.front.length ? f.front[id] : Double.NaN;
            if (Double.isNaN(fr)) return true;
            double[] c = info.getNextCosts();
            double t = f.delta + (c != null && c.length > 0 ? c[0] : info.getNextCost());
            boolean ok = t < fr;
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
     * Früheste Ankunft (s nach dem Stoß) je Knoten-Nummer bis maxId ab start mit Abfahrt delta (NaN: nicht vor der Front);
     * am Ende {aufrufe der Kosten, abgelehnte Knoten, ms} im Feld info.
     */
    static double[] flucht(NetworkAnalyst a, Felder f, long start, int maxId, double[] info) throws Exception {
        var alt = a.getLinkCostCalculators();
        Kosten k = new Kosten(f);
        VorDerFront v = new VorDerFront(f);
        double[] t = new double[maxId + 1];
        Arrays.fill(t, Double.NaN);
        try {
            a.setLinkCostCalculator(k);
            long t0 = System.nanoTime();
            LogicalSubPath[] w = a.withinCost(new PointOnNet(start), 1e9, v, null);
            info[2] += (System.nanoTime() - t0) / 1e6;
            t[(int) start] = f.delta;
            for (LogicalSubPath x : w) {
                long n = x.getReferencePath().getEndNodeId();
                if (n >= 0 && n <= maxId && x.getEndPercentage() > 1 - 1e-9) t[(int) n] = f.delta + x.getCost();
            }
        } finally {
            a.setLinkCostCalculators(alt);
        }
        info[0] += k.aufrufe;
        info[1] += v.abgelehnt;
        return t;
    }

    static Felder felder(double[] laenge, double[] re, double[] rn, double[] faktor, double[] front, double[] strom, double hinE, double hinN) {
        Felder f = new Felder();
        f.laenge = laenge; f.re = re; f.rn = rn; f.faktor = faktor; f.front = front; f.strom = strom; f.hinE = hinE; f.hinN = hinN;
        return f;
    }

    /**
     * Das Netz im Speicher (ohne Oracle), gerichtet: Knoten-Nummern 1 … n, Kanten 1 … m mit Anfang, Ende, COST. Befund
     * Phase 15a: in {@code createLogicalNetLink(id, a, b, cost, x, y, …)} ist x „in beiden Richtungen“ und y „aktiv“
     * (mit {@code true, false} fand LOD keinen Weg); gerichtet also {@code false, true}.
     */
    public static NetworkAnalyst imSpeicher(int n, long[] von, long[] nach, double[] cost) throws Exception {
        LogicalBasicNetwork net = LODNetworkFactory.createLogicalBasicNetwork(NETZ, 1);
        Map<Long, LogicalNetNode> k = new HashMap<>(n * 2);
        for (long id = 1; id <= n; id++) {
            LogicalNetNode x = LODNetworkFactory.createLogicalNetNode(id, 0, true, (Map<String, Object>) null);
            k.put(id, x);
            net.addNode(x);
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
     * Für {@code scene}/{@code tools} (nur JDK-Typen): je Start und Abfahrt die Ankunft je Knoten, im Speicher (url null)
     * oder in Oracle. Felder je Kante 0 … m−1 und je Knoten 0 … n−1 (Nummer − 1); Rückgabe {double[start][abfahrt][knoten],
     * String info}.
     */
    public static Object[] rechnen(String url, String user, String pw, int n, long[] von, long[] nach, double[] laenge, double[] re, double[] rn, double[] faktor,
                                   double[] front, double[] strom, double hinE, double hinN, long[] starts, double[] abfahrten) throws Exception {
        int m = von.length;
        double[] l1 = new double[m + 1], re1 = new double[m + 1], rn1 = new double[m + 1], fa1 = new double[m + 1], fr1 = new double[n + 1], cost = new double[m];
        for (int i = 0; i < m; i++) { l1[i + 1] = laenge[i]; re1[i + 1] = re[i]; rn1[i + 1] = rn[i]; fa1[i + 1] = faktor[i]; cost[i] = laenge[i] / FAHRT; }
        fr1[0] = Double.NaN;
        for (int i = 0; i < n; i++) fr1[i + 1] = front[i];
        Felder f = felder(l1, re1, rn1, fa1, fr1, strom, hinE, hinN);
        double[] info = new double[3];
        double[][][] r = new double[starts.length][abfahrten.length][];
        long t0 = System.nanoTime();
        String wo;
        if (url == null) {
            NetworkAnalyst a = imSpeicher(n, von, nach, cost);
            for (int s = 0; s < starts.length; s++) for (int d = 0; d < abfahrten.length; d++) { f.delta = abfahrten[d]; r[s][d] = flucht(a, f, starts[s], n, info); }
            wo = "im Speicher";
        } else {
            try (Connection c = LODNetworkManager.getConnection(url, user, pw)) {
                NetworkIO io = LODNetworkManager.getCachedNetworkIO(c, NETZ, NETZ, null);
                NetworkAnalyst a = LODNetworkManager.getNetworkAnalyst(io);
                for (int s = 0; s < starts.length; s++) for (int d = 0; d < abfahrten.length; d++) { f.delta = abfahrten[d]; r[s][d] = flucht(a, f, starts[s], n, info); }
                wo = "Oracle " + NETZ + ", Treiber " + c.getMetaData().getDriverVersion();
            }
        }
        String text = String.format(Locale.ROOT, "LOD %s: withinCost × %d, LinkCostCalculator %.0f-mal, Front lehnte %.0f Knoten ab, %.0f ms (%.1f s gesamt)",
                wo, starts.length * abfahrten.length, info[0], info[1], info[2], (System.nanoTime() - t0) / 1e9);
        return new Object[]{r, text};
    }
}
