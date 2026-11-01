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
import oracle.spatial.network.lod.NetworkBuffer;
import oracle.spatial.network.lod.NetworkIO;
import oracle.spatial.network.lod.OrderedLongSet;
import oracle.spatial.network.lod.PointOnNet;
import oracle.spatial.network.lod.TSP;
import oracle.spatial.network.lod.TspPath;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Der Java-Teil der NDM-Funktionsprobe (Werkbuch 2.8, Phase 3) und die Gegenrechnung der Herkunft mit LOD. Läuft im eigenen
 * Klassenlader von {@link Lod} (Treiber 21, {@code sdo*.jar}, dazu die Klassen des Projekts); darum tauscht er mit dem
 * Aufrufer nur Typen des JDK aus ({@code String[]}, {@code long[]}).
 * <p>
 * Das Probenetz {@code LB_PROBE_RAUM} (aus {@code db/netz/03_probe.sql}): Quadrat 1–2–3–4 mit Kanten zu 1000 m, Diagonale
 * 1–3 (Kante 5) zu 1414,2 m, dazu getrennt 6–7 (Kante 6) zu 500 m; alle Kanten in beide Richtungen.
 * {@link #imSpeicher()} baut dasselbe Netz ohne Datenbank (für die Prüfung).
 */
public final class NdmProbeKern {
    private NdmProbeKern() { }

    /** Eine Zeile für LB_NDM_PROBE: Nr, Fähigkeit, Prüfung, Ergebnis, J/N. */
    static String[] z(int nr, String f, String p, String e, boolean ok) {
        return new String[]{String.valueOf(nr), f, p, e, ok ? "J" : "N"};
    }

    static String f(double d) { return String.format(Locale.ROOT, "%.1f", d); }

    static boolean nah(double a, double b) { return Math.abs(a - b) < 0.5; }

    /** Eigene Kostenfunktion: doppelte Kosten je Kante (etwa für Gegenstrom oder Gefälle später). */
    public static final class Doppelt implements LinkCostCalculator {
        @Override public double getLinkCost(LODAnalysisInfo info) { return 2 * info.getNextLink().getCost(); }
        @Override public int[] getUserDataCategories() { return null; }
    }

    /** Eigene Bedingung: eine Kante ist gesperrt (etwa eine Strecke unter dem Gletscher). */
    public static final class Sperre implements LODNetworkConstraint {
        final long gesperrt;
        public Sperre(long gesperrt) { this.gesperrt = gesperrt; }
        @Override public boolean isSatisfied(LODAnalysisInfo info) {
            LogicalNetLink l = info.getNextLink();
            return l == null || l.getId() != gesperrt;
        }
        @Override public int[] getUserDataCategories() { return null; }
        @Override public int getNumberOfUserObjects() { return 0; }
        @Override public boolean isCurrentNodePartiallyExpanded(LODAnalysisInfo info) { return false; }
        @Override public void reset() { }
        @Override public void setNetworkAnalyst(NetworkAnalyst a) { }
    }

    static PointOnNet p(long n) { return new PointOnNet(n); }

    static TreeSet<Long> enden(LogicalSubPath[] w) {
        TreeSet<Long> s = new TreeSet<>();
        if (w != null) for (LogicalSubPath x : w) s.add(x.getReferencePath().getEndNodeId());
        return s;
    }

    /**
     * Die Knoten, die die Wege von traceIn ({@code rein}) oder traceOut ganz erreichen: ein Weg, der mitten auf einer
     * Kante endet (Kostengrenze), zählt deren fernen Knoten nicht.
     */
    static TreeSet<Long> knoten(LogicalSubPath[] w, boolean rein) {
        TreeSet<Long> s = new TreeSet<>();
        if (w != null)
            for (LogicalSubPath x : w) {
                long[] n = x.getReferencePath().getNodeIds();
                int a = x.getStartLinkIndex(), b = x.getEndLinkIndex() + 1;
                if (rein && x.getStartPercentage() > 1e-9) a++;
                if (!rein && x.getEndPercentage() < 1 - 1e-9) b--;
                for (int i = Math.max(0, a); i <= Math.min(n.length - 1, b); i++) s.add(n[i]);
            }
        return s;
    }

    /** Ein Block der Probe: läuft er nicht, steht die Meldung im Ergebnis und OK = N. */
    interface Block { String[] run() throws Exception; }

    static String[] sicher(int nr, String f, String p, Block b) {
        try {
            return b.run();
        } catch (Exception | LinkageError e) {
            return z(nr, f, p, e.getClass().getSimpleName() + ": " + e.getMessage(), false);
        }
    }

    /** Die Analysen auf dem Probenetz; Nr 11 bis 22 in LB_NDM_PROBE (10 ist der Zugang). */
    public static List<String[]> analysen(NetworkAnalyst a) {
        List<String[]> l = new ArrayList<>();
        l.add(sicher(11, "Dijkstra", "shortestPathDijkstra 1 → 3 (erwartet 1414,2 über die Diagonale)", () -> {
            LogicalSubPath w = a.shortestPathDijkstra(p(1), p(3), null);
            long[] n = w.getReferencePath().getNodeIds();
            return z(11, "Dijkstra", "shortestPathDijkstra 1 → 3 (erwartet 1414,2 über die Diagonale)",
                    "Kosten " + f(w.getCost()) + ", Knoten " + Arrays.toString(n), nah(w.getCost(), 1414.2) && n.length == 2);
        }));
        l.add(sicher(12, "Netzbedingung (eigene Klasse)", "LODNetworkConstraint sperrt Kante 5; 1 → 3 (erwartet 2000)", () -> {
            LogicalSubPath w = a.shortestPathDijkstra(p(1), p(3), new Sperre(5));
            return z(12, "Netzbedingung (eigene Klasse)", "LODNetworkConstraint sperrt Kante 5; 1 → 3 (erwartet 2000)",
                    "Kosten " + f(w.getCost()) + ", Kanten " + Arrays.toString(w.getReferencePath().getLinkIds()), nah(w.getCost(), 2000));
        }));
        l.add(sicher(13, "Kostenfunktion (eigene Klasse)", "LinkCostCalculator mit doppelten Kosten; 1 → 3 (erwartet 2828,4)", () -> {
            LinkCostCalculator[] alt = a.getLinkCostCalculators();
            a.setLinkCostCalculator(new Doppelt());
            try {
                LogicalSubPath w = a.shortestPathDijkstra(p(1), p(3), null);
                return z(13, "Kostenfunktion (eigene Klasse)", "LinkCostCalculator mit doppelten Kosten; 1 → 3 (erwartet 2828,4)",
                        "Kosten " + f(w.getCost()), nah(w.getCost(), 2828.4));
            } finally {
                a.setLinkCostCalculators(alt);
            }
        }));
        l.add(sicher(14, "withinCost", "withinCost(1, 1100) (erwartet die Knoten 2 und 4)", () -> {
            TreeSet<Long> s = enden(a.withinCost(p(1), 1100, null, null));
            s.remove(1L);
            return z(14, "withinCost", "withinCost(1, 1100) (erwartet die Knoten 2 und 4)", "erreicht " + s, s.equals(new TreeSet<>(List.of(2L, 4L))));
        }));
        l.add(sicher(15, "nearestNeighbors", "nearestNeighbors(1, 3) (erwartet 2 und 4 zu 1000, dann 3 zu 1414,2)", () -> {
            LogicalSubPath[] w = a.nearestNeighbors(p(1), 3, null, null);
            StringBuilder b = new StringBuilder();
            for (LogicalSubPath x : w) b.append(x.getReferencePath().getEndNodeId()).append(" (").append(f(x.getCost())).append(") ");
            boolean ok = w.length == 3 && nah(w[0].getCost(), 1000) && nah(w[1].getCost(), 1000) && nah(w[2].getCost(), 1414.2);
            return z(15, "nearestNeighbors", "nearestNeighbors(1, 3) (erwartet 2 und 4 zu 1000, dann 3 zu 1414,2)", b.toString().trim(), ok);
        }));
        l.add(sicher(16, "k kürzeste Wege", "kShortestPaths 1 → 3, k = 3 (erwartet 1414,2, 2000, 2000)", () -> {
            LogicalSubPath[] w = a.kShortestPaths(new PointOnNet[]{p(1)}, new PointOnNet[]{p(3)}, 3, null);
            double[] c = new double[w.length];
            for (int i = 0; i < w.length; i++) c[i] = w[i].getCost();
            boolean ok = w.length == 3 && nah(c[0], 1414.2) && nah(c[1], 2000) && nah(c[2], 2000);
            StringBuilder b = new StringBuilder();
            for (double d : c) b.append(f(d)).append(' ');
            return z(16, "k kürzeste Wege", "kShortestPaths 1 → 3, k = 3 (erwartet 1414,2, 2000, 2000)", "Kosten " + b.toString().trim(), ok);
        }));
        l.add(sicher(17, "TSP", "tsp über 1, 2, 3, 4, geschlossen (erwartet 4000)", () -> {
            TspPath t = a.tsp(new PointOnNet[][]{{p(1)}, {p(2)}, {p(3)}, {p(4)}}, TSP.TourFlag.CLOSED, null);
            double s = 0;
            for (double d : t.getCosts()) s += d;
            return z(17, "TSP", "tsp über 1, 2, 3, 4, geschlossen (erwartet 4000)", "Reihenfolge " + Arrays.toString(t.getTspOrder()) + ", Kosten " + f(s), nah(s, 4000));
        }));
        l.add(sicher(18, "MCST", "mcst (minimaler Spannbaum einer Komponente: 3 Kanten aus 1 bis 4 oder Kante 6)", () -> {
            long[] k = a.mcst(1, null);
            boolean ok = k.length == 3 && Arrays.stream(k).allMatch(x -> x >= 1 && x <= 4) || k.length == 1 && k[0] == 6;
            return z(18, "MCST", "mcst (minimaler Spannbaum einer Komponente: 3 Kanten aus 1 bis 4 oder Kante 6)", "Kanten " + Arrays.toString(k)
                    + " (LOD spannt nur eine Komponente, keinen Wald)", ok);
        }));
        l.add(sicher(19, "traceOut", "traceOut(1, 1500) (erwartet 2, 3, 4)", () -> {
            TreeSet<Long> s = knoten(a.traceOut(p(1), 1500, null, null), false);
            s.remove(1L);
            return z(19, "traceOut", "traceOut(1, 1500) (erwartet 2, 3, 4)", "erreicht " + s, s.equals(new TreeSet<>(List.of(2L, 3L, 4L))));
        }));
        l.add(sicher(20, "traceIn", "traceIn(3, 1100) (erwartet 2 und 4)", () -> {
            TreeSet<Long> s = knoten(a.traceIn(p(3), 1100, null, null), true);
            s.remove(3L);
            return z(20, "traceIn", "traceIn(3, 1100) (erwartet 2 und 4)", "erreicht von " + s, s.equals(new TreeSet<>(List.of(2L, 4L))));
        }));
        l.add(sicher(21, "Netzpuffer", "networkBuffer(1, 1100) (erwartet die Knoten 1, 2, 4, nicht 3)", () -> {
            NetworkBuffer nb = a.networkBuffer(new PointOnNet[]{p(1)}, 1100, null);
            var e = nb.getElements();
            boolean ok = e.hasNode(1) && e.hasNode(2) && e.hasNode(4) && !e.hasNode(3);
            return z(21, "Netzpuffer", "networkBuffer(1, 1100) (erwartet die Knoten 1, 2, 4, nicht 3)",
                    e.getNumberOfNodes() + " Knoten, " + e.getNumberOfLinks() + " Kanten im Puffer", ok);
        }));
        l.add(sicher(22, "Zusammenhangskomponenten (LOD)", "findConnectedComponents(1) (erwartet 2)", () -> {
            OrderedLongSet[] k = a.findConnectedComponents(1);
            StringBuilder b = new StringBuilder();
            for (OrderedLongSet s : k) b.append(Arrays.toString(s.toArray())).append(' ');
            return z(22, "Zusammenhangskomponenten (LOD)", "findConnectedComponents(1) (erwartet 2)", k.length + " Komponenten: " + b.toString().trim(), k.length == 2);
        }));
        return l;
    }

    /** Das Probenetz ohne Datenbank (für die Prüfung und als Gegenstück zu LB_PROBE_RAUM). */
    public static NetworkAnalyst imSpeicher() throws Exception {
        LogicalBasicNetwork n = LODNetworkFactory.createLogicalBasicNetwork("LB_PROBE_RAUM", 1);
        long[] ids = {1, 2, 3, 4, 6, 7};
        java.util.Map<Long, LogicalNetNode> k = new java.util.HashMap<>();
        for (long id : ids) {
            LogicalNetNode x = LODNetworkFactory.createLogicalNetNode(id, 0, true, (java.util.Map<String, Object>) null);
            k.put(id, x);
            n.addNode(x);
        }
        long[][] kanten = {{1, 1, 2}, {2, 2, 3}, {3, 3, 4}, {4, 4, 1}, {5, 1, 3}, {6, 6, 7}};
        double[] kosten = {1000, 1000, 1000, 1000, Math.hypot(1000, 1000), 500};
        for (int i = 0; i < kanten.length; i++) {
            LogicalNetLink l = LODNetworkFactory.createLogicalNetLink(kanten[i][0], k.get(kanten[i][1]), k.get(kanten[i][2]), kosten[i], true, true, (java.util.Map<String, Object>) null);
            n.addLink(l);
            k.get(kanten[i][1]).addOutLink(l);
            k.get(kanten[i][2]).addInLink(l);
        }
        NetworkIO io = LODNetworkManager.getNetworkIOForInMemoryNetwork(new LogicalBasicNetwork[]{n});
        return LODNetworkManager.getNetworkAnalyst(io);
    }

    /** {@code ant netzdb}: die Probe auf LB_PROBE_RAUM in Oracle (über die Partition-BLOBs). */
    public static List<String[]> probe(String url, String user, String pw) throws Exception {
        try (Connection c = LODNetworkManager.getConnection(url, user, pw)) {
            NetworkIO io = LODNetworkManager.getCachedNetworkIO(c, "LB_PROBE_RAUM", "LB_PROBE_RAUM", null);
            List<String[]> l = analysen(LODNetworkManager.getNetworkAnalyst(io));
            l.add(0, z(10, "LOD-Zugang", "LODNetworkManager.getCachedNetworkIO auf LB_PROBE_RAUM, Treiber "
                    + c.getMetaData().getDriverVersion(), "verbunden, gelesen aus den Partition-BLOBs", true));
            return l;
        }
    }

    /**
     * Gegenrechnung der Herkunft: für jedes Ziel (eine Ausgabe in daten/) die Zahl der Rohdateien davor, mit traceIn auf
     * LB_NET_HERKUNFT. Rohdateien sind die Knoten in {@code roh} (aufsteigend sortiert).
     */
    public static long[] herkunft(String url, String user, String pw, long[] ziele, long[] roh) throws Exception {
        try (Connection c = LODNetworkManager.getConnection(url, user, pw)) {
            NetworkIO io = LODNetworkManager.getCachedNetworkIO(c, "LB_NET_HERKUNFT", "LB_NET_HERKUNFT", null);
            NetworkAnalyst a = LODNetworkManager.getNetworkAnalyst(io);
            long[] r = new long[ziele.length];
            for (int i = 0; i < ziele.length; i++) {
                long n = 0;
                for (long k : knoten(a.traceIn(p(ziele[i]), 1e9, null, null), true)) if (Arrays.binarySearch(roh, k) >= 0) n++;
                r[i] = n;
            }
            return r;
        }
    }

    /** Probe ohne Datenbank: druckt die Analysen auf dem Netz im Speicher. */
    public static void main(String[] a) throws Exception {
        for (String[] z : analysen(imSpeicher())) System.out.println(String.join(" | ", z));
    }
}
