package com.dan.lituya.scene;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;

/**
 * {@code LB_NET_SICHT} als Datei (Phase 17, Werkbuch 7.6): Sicht und Kamera. Knoten sind Kamerapunkte mit Augenhöhe
 * (Ansichten, Schaustücke, benannte Orte, Ankerplätze, Aufläufe, Punkte am Ufer, Gipfel, Kamerapunkte über dem Wasser),
 * Kanten die freie Sicht zwischen zwei Punkten im Gelände (Strahl mit Erdkrümmung und Refraktion), ungerichtet, Kosten =
 * Flugzeit der Kamera {@link #flugzeit}. Das Netz enthält nur die größte Komponente; was keine Sicht dorthin hat, steht
 * in {@code SICHT.txt}.
 * <p>Die eigenen Rechnungen hier (Dijkstra, Spannbaum nach Kruskal, Rundreise mit nächstem Nachbarn, 2-opt und Or-opt,
 * nächste Nachbarn) sind die Seite der Zweitrechnung gegen LOD ({@code netz.SichtLod}: {@code mcst}, {@code tsp},
 * {@code nearestNeighbors}).
 * <p>Dateien in {@code daten/netz/sicht}: {@code knoten.tsv}, {@code kanten.tsv}, {@code rundflug.tsv} (Rundflug über die
 * Motive, Knoten für Knoten mit Zeit), {@code fahrt.tsv} (Kamerafahrt den Spannbaum entlang), {@code spannbaum.tsv},
 * {@code nachbarn.tsv} (je Ansicht die fünf nächsten), {@code SICHT.txt}.
 */
public final class SichtNetz {
    public static final String ORDNER = "netz/sicht";
    /** Knotenarten. */
    public static final String ANSICHT = "ANSICHT", SCHAUSTUECK = "SCHAUSTUECK", ORT = "ORT", ANKER = "ANKER", AUFLAUF = "AUFLAUF", UFER = "UFER",
            GIPFEL = "GIPFEL", KAMERA = "KAMERA";
    public static final String[] ARTEN = {ANSICHT, SCHAUSTUECK, ORT, ANKER, AUFLAUF, UFER, GIPFEL, KAMERA};
    /** Erdradius (m) und Refraktionskoeffizient [G]. */
    public static final double ERDRADIUS = 6_371_000, REFRAKTION = 0.13;
    /** Kamera: Geschwindigkeit 30 m/s am Boden, + 0,015 m/s je Meter Höhe, höchstens 90 m/s; Steigen und Sinken 12 m/s [G]. */
    public static final double V0 = 30, V_JE_M = 0.015, V_MAX = 90, STEIGEN = 12;

    // Knoten (Index = NODE_ID − 1)
    public int n;
    public String[] art, name;
    public double[] ost, nord, hoehe, grund, blick, neigung;
    public boolean[] motiv;
    // Kanten (Index = LINK_ID − 1), ungerichtet
    public int m;
    public int[] von, nach;
    public double[] laenge, cost, frei;
    public String text = "";
    /** Nachbarn je Knoten (Kanten-Indizes), nach {@link #index()}. */
    public int[][] adj;

    public double x(int k) { return ost[k] - Site.E0; }
    public double z(int k) { return -(nord[k] - Site.N0); }

    /** Flugzeit (s) für eine Strecke: waagerechte Länge l in der mittleren Höhe hm, dazu der Höhenunterschied dh. */
    public static double flugzeit(double l, double hm, double dh) {
        double v = Math.min(V_MAX, V0 + V_JE_M * Math.max(0, hm));
        return l / v + Math.abs(dh) / STEIGEN;
    }

    /** Wirksamer Radius mit Refraktion. */
    public static double radius() { return ERDRADIUS / (1 - REFRAKTION); }

    public void index() {
        int[] c = new int[n];
        for (int e = 0; e < m; e++) { c[von[e]]++; c[nach[e]]++; }
        adj = new int[n][];
        for (int k = 0; k < n; k++) adj[k] = new int[c[k]];
        Arrays.fill(c, 0);
        for (int e = 0; e < m; e++) { adj[von[e]][c[von[e]]++] = e; adj[nach[e]][c[nach[e]]++] = e; }
    }

    public int anderes(int e, int k) { return von[e] == k ? nach[e] : von[e]; }

    /** Knoten nach Name (genau, sonst ohne Groß/klein) oder −1. */
    public int knoten(String nm) {
        for (int k = 0; k < n; k++) if (nm.equals(name[k])) return k;
        for (int k = 0; k < n; k++) if (name[k] != null && nm.equalsIgnoreCase(name[k])) return k;
        return -1;
    }

    // ------------------------------------------------------------------ Rechnungen (eigene Seite)

    /** Dijkstra von s: Kosten je Knoten (Infinity: nicht erreicht) und Vorgängerkante in vor. */
    public double[] dijkstra(int s, int[] vor) {
        double[] d = new double[n];
        Arrays.fill(d, Double.POSITIVE_INFINITY);
        if (vor != null) Arrays.fill(vor, -1);
        d[s] = 0;
        PriorityQueue<double[]> q = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        q.add(new double[]{0, s});
        while (!q.isEmpty()) {
            double[] x = q.poll();
            int k = (int) x[1];
            if (x[0] > d[k]) continue;
            for (int e : adj[k]) {
                int o = anderes(e, k);
                double nd = d[k] + cost[e];
                if (nd < d[o]) { d[o] = nd; if (vor != null) vor[o] = e; q.add(new double[]{nd, o}); }
            }
        }
        return d;
    }

    /** Weg (Knotenfolge) von s nach t aus den Vorgängern eines Dijkstra ab s. */
    public List<Integer> weg(int s, int t, int[] vor) {
        List<Integer> l = new ArrayList<>();
        for (int k = t; k != s; ) {
            l.add(k);
            int e = vor[k];
            if (e < 0) return null;
            k = anderes(e, k);
        }
        l.add(s);
        java.util.Collections.reverse(l);
        return l;
    }

    /** Minimaler Spannbaum (Kruskal): die Kanten, nach Kosten. */
    public int[] spannbaum() {
        Integer[] o = new Integer[m];
        for (int e = 0; e < m; e++) o[e] = e;
        Arrays.sort(o, (a, b) -> cost[a] != cost[b] ? Double.compare(cost[a], cost[b]) : Integer.compare(a, b));
        int[] p = new int[n];
        for (int k = 0; k < n; k++) p[k] = k;
        List<Integer> t = new ArrayList<>();
        for (int e : o) {
            int a = finde(p, von[e]), b = finde(p, nach[e]);
            if (a == b) continue;
            p[a] = b;
            t.add(e);
        }
        return t.stream().mapToInt(Integer::intValue).toArray();
    }

    static int finde(int[] p, int k) { while (p[k] != k) { p[k] = p[p[k]]; k = p[k]; } return k; }

    /** Komponenten (Nummer je Knoten), Zahl in zahl[0]. */
    public int[] komponenten(int[] zahl) {
        int[] c = new int[n];
        Arrays.fill(c, -1);
        int z = 0;
        for (int s = 0; s < n; s++) {
            if (c[s] >= 0) continue;
            ArrayList<Integer> st = new ArrayList<>(List.of(s));
            c[s] = z;
            while (!st.isEmpty()) {
                int k = st.remove(st.size() - 1);
                for (int e : adj[k]) { int o = anderes(e, k); if (c[o] < 0) { c[o] = z; st.add(o); } }
            }
            z++;
        }
        if (zahl != null) zahl[0] = z;
        return c;
    }

    /**
     * Geschlossene Rundreise über die Knoten {@code ziele} (der erste ist der Anfang): Kosten aus Dijkstra, nächster Nachbar von jedem Ziel aus,
     * je mit 2-opt und Or-opt verbessert, bis nichts mehr besser wird; die beste bleibt. Rückgabe die Folge (Indizes in ziele) und in kosten[0]
     * die Summe.
     */
    public int[] rundreise(int[] ziele, double[] kosten) {
        int z = ziele.length;
        double[][] d = new double[z][];
        for (int i = 0; i < z; i++) {
            double[] a = dijkstra(ziele[i], null);
            d[i] = new double[z];
            for (int j = 0; j < z; j++) d[i][j] = a[ziele[j]];
        }
        int[] t = null;
        double bt = Double.POSITIVE_INFINITY;
        // nächster Nachbar von jedem Ziel aus, jeweils verbessert; die beste Rundreise bleibt
        for (int s0 = 0; s0 < z; s0++) {
            int[] u = verbessert(naechsterNachbar(d, s0), d);
            double su = 0;
            for (int i = 0; i < z; i++) su += d[u[i]][u[(i + 1) % z]];
            if (su < bt - 1e-9) { bt = su; t = u; }
        }
        // den Anfang wieder nach vorn
        int a0 = 0;
        for (int i = 0; i < z; i++) if (t[i] == 0) a0 = i;
        int[] r = new int[z];
        for (int i = 0; i < z; i++) r[i] = t[(a0 + i) % z];
        double s = 0;
        for (int i = 0; i < z; i++) s += d[r[i]][r[(i + 1) % z]];
        if (kosten != null) kosten[0] = s;
        return r;
    }

    /** Rundreise nach dem nächsten Nachbarn ab Ziel s0. */
    static int[] naechsterNachbar(double[][] d, int s0) {
        int z = d.length;
        int[] t = new int[z];
        boolean[] dabei = new boolean[z];
        t[0] = s0;
        dabei[s0] = true;
        for (int i = 1; i < z; i++) {
            int best = -1;
            for (int j = 0; j < z; j++) if (!dabei[j] && (best < 0 || d[t[i - 1]][j] < d[t[i - 1]][best])) best = j;
            t[i] = best;
            dabei[best] = true;
        }
        return t;
    }

    /** 2-opt und Or-opt (Stücke von 1 bis 3 Zielen), bis nichts mehr besser wird. */
    static int[] verbessert(int[] t, double[][] d) {
        int z = t.length;
        for (boolean besser = true; besser; ) {
            besser = false;
            // 2-opt
            for (int i = 0; i < z - 1; i++)
                for (int j = i + 2; j < z; j++) {
                    int a = t[i], b = t[i + 1], c = t[j], e = t[(j + 1) % z];
                    if (a == e) continue;
                    double g = d[a][c] + d[b][e] - d[a][b] - d[c][e];
                    if (g < -1e-9) { for (int x = i + 1, y = j; x < y; x++, y--) { int h = t[x]; t[x] = t[y]; t[y] = h; } besser = true; }
                }
            // Or-opt: Stücke von 1 bis 3 Zielen an eine andere Stelle
            for (int len = 1; len <= 3 && !besser; len++)
                for (int i = 1; i + len <= z && !besser; i++) {
                    int p = t[i - 1], s0 = t[i], s1 = t[i + len - 1], q = t[(i + len) % z];
                    double weg = d[p][s0] + d[s1][q] - d[p][q];
                    for (int j = 0; j < z && !besser; j++) {
                        if (j >= i - 1 && j <= i + len - 1) continue;
                        int u = t[j], v = t[(j + 1) % z];
                        if (v == s0) continue;
                        double hin = d[u][s0] + d[s1][v] - d[u][v];
                        if (hin < weg - 1e-9) {
                            List<Integer> l = new ArrayList<>();
                            for (int x : t) l.add(x);
                            List<Integer> st = new ArrayList<>(l.subList(i, i + len));
                            l.subList(i, i + len).clear();
                            int pos = l.indexOf(u) + 1;
                            l.addAll(pos, st);
                            for (int x = 0; x < z; x++) t[x] = l.get(x);
                            besser = true;
                        }
                    }
                }
        }
        return t;
    }

    /** Die k nächsten Knoten von s nach Kosten (ohne s): {Knoten, …}, Kosten in kosten. */
    public int[] naechste(int s, int k, double[] kosten) {
        double[] d = dijkstra(s, null);
        Integer[] o = new Integer[n];
        for (int i = 0; i < n; i++) o[i] = i;
        Arrays.sort(o, (a, b) -> Double.compare(d[a], d[b]));
        int[] r = new int[Math.min(k, n - 1)];
        int j = 0;
        for (int i = 0; i < n && j < r.length; i++) if (o[i] != s && Double.isFinite(d[o[i]])) { r[j] = o[i]; if (kosten != null) kosten[j] = d[o[i]]; j++; }
        return Arrays.copyOf(r, j);
    }

    // ------------------------------------------------------------------ Dateien

    public static boolean da(File daten) { return new File(new File(daten, ORDNER), "kanten.tsv").isFile(); }

    public void schreiben(File daten) throws IOException {
        File d = new File(daten, ORDNER);
        d.mkdirs();
        List<String> k = new ArrayList<>();
        k.add("NODE_ID\tART\tNAME\tOST\tNORD\tHOEHE\tGRUND\tBLICK\tNEIGUNG\tMOTIV");
        for (int i = 0; i < n; i++)
            k.add(String.format(Locale.ROOT, "%d\t%s\t%s\t%.1f\t%.1f\t%.1f\t%.1f\t%s\t%s\t%s", i + 1, art[i], name[i] == null ? "" : name[i], ost[i], nord[i], hoehe[i], grund[i],
                    Double.isNaN(blick[i]) ? "" : String.format(Locale.ROOT, "%.1f", blick[i]), Double.isNaN(neigung[i]) ? "" : String.format(Locale.ROOT, "%.1f", neigung[i]), motiv[i] ? "J" : "N"));
        Files.write(new File(d, "knoten.tsv").toPath(), k, StandardCharsets.UTF_8);
        List<String> e = new ArrayList<>();
        e.add("LINK_ID\tSTART_NODE_ID\tEND_NODE_ID\tLAENGE\tCOST\tFREI_M");
        for (int i = 0; i < m; i++) e.add(String.format(Locale.ROOT, "%d\t%d\t%d\t%.1f\t%.3f\t%.1f", i + 1, von[i] + 1, nach[i] + 1, laenge[i], cost[i], frei[i]));
        Files.write(new File(d, "kanten.tsv").toPath(), e, StandardCharsets.UTF_8);
    }

    static double dd(String s) { return s.isEmpty() ? Double.NaN : Double.parseDouble(s); }

    public static SichtNetz lesen(File daten) throws IOException {
        File d = new File(daten, ORDNER);
        SichtNetz w = new SichtNetz();
        List<String[]> k = WellenNetz.tsv(new File(d, "knoten.tsv")), e = WellenNetz.tsv(new File(d, "kanten.tsv"));
        w.n = k.size();
        w.art = new String[w.n]; w.name = new String[w.n]; w.ost = new double[w.n]; w.nord = new double[w.n]; w.hoehe = new double[w.n]; w.grund = new double[w.n];
        w.blick = new double[w.n]; w.neigung = new double[w.n]; w.motiv = new boolean[w.n];
        for (String[] z : k) {
            int i = Integer.parseInt(z[0]) - 1;
            w.art[i] = z[1]; w.name[i] = z[2].isEmpty() ? null : z[2]; w.ost[i] = dd(z[3]); w.nord[i] = dd(z[4]); w.hoehe[i] = dd(z[5]); w.grund[i] = dd(z[6]);
            w.blick[i] = dd(z[7]); w.neigung[i] = dd(z[8]); w.motiv[i] = "J".equals(z[9]);
        }
        w.m = e.size();
        w.von = new int[w.m]; w.nach = new int[w.m]; w.laenge = new double[w.m]; w.cost = new double[w.m]; w.frei = new double[w.m];
        for (String[] z : e) {
            int i = Integer.parseInt(z[0]) - 1;
            w.von[i] = Integer.parseInt(z[1]) - 1; w.nach[i] = Integer.parseInt(z[2]) - 1; w.laenge[i] = dd(z[3]); w.cost[i] = dd(z[4]); w.frei[i] = dd(z[5]);
        }
        File t = new File(d, "SICHT.txt");
        if (t.isFile()) w.text = Files.readString(t.toPath(), StandardCharsets.UTF_8);
        w.index();
        return w;
    }

    /** Eine Fahrt aus {@code rundflug.tsv} oder {@code fahrt.tsv}: je Zeile {Knoten, Zeit s}. */
    public static double[][] fahrt(File daten, String datei) throws IOException {
        File f = new File(new File(daten, ORDNER), datei);
        if (!f.isFile()) return null;
        List<String[]> l = WellenNetz.tsv(f);
        double[][] r = new double[l.size()][];
        for (int i = 0; i < r.length; i++) r[i] = new double[]{Integer.parseInt(l.get(i)[1]) - 1, Double.parseDouble(l.get(i)[2])};
        return r;
    }

    /** Ein Wert aus SICHT.txt oder null. */
    public String wert(String s) {
        for (String z : text.split("\\R")) if (z.startsWith(s + "=")) return z.substring(s.length() + 1).trim();
        return null;
    }

    /** „Sicht (LB_NET_SICHT): 182 Punkte, 5 400 Sichtlinien; Rundflug …“ aus SICHT.txt, oder ein Hinweis. */
    public static String zeile(File daten) {
        try {
            if (!da(daten)) return "Sicht (LB_NET_SICHT): fehlt (ant aufbereitung -Dschritte=sicht)";
            SichtNetz w = new SichtNetz();
            w.text = Files.readString(new File(new File(daten, ORDNER), "SICHT.txt").toPath(), StandardCharsets.UTF_8);
            return "Sicht (LB_NET_SICHT): " + w.wert("netz") + "; Rundflug " + w.wert("rundflug");
        } catch (IOException e) {
            return "Sicht (LB_NET_SICHT): " + e.getMessage();
        }
    }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "LB_NET_SICHT: %,d Punkte, %,d Sichtlinien", n, m);
    }
}
