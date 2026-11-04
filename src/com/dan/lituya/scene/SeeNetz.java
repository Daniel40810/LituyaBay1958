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
 * {@code LB_NET_SEE} als Dateien (Phase 15a, Werkbuch 7.3): das Fahrwasser eines Trollers in der Bucht und durch das Tor,
 * gerichtet, mit Kosten, die von der Zeit abhängen. Knoten aus dem Netz der Welle ({@link WellenNetz}, 20 m): 40 m im
 * Umkreis von {@link #NAH} m um das Tor, 100 m auf See, 200 m in der Bucht. Kanten zu den Nachbarn bis 1,5 Abstände, nur
 * wo jede Zelle dazwischen Wasser ist und tief genug: Tiefe 1940 unter Mittelwasser + Gezeit beim Stoß ≥ Tiefgang + 0,5 m.
 * <p>
 * Kosten einer Kante zur Zeit t: Länge / (Fahrt durchs Wasser + Strom · Richtung). Fahrt {@link #FAHRT} (8 kn, volle
 * Maschine eines Trollers [G]); Strom: der Gezeitenstrom im Tor ({@link Gezeiten#stromTor}) mal einem Faktor je Kante,
 * der vom Tor nach innen und außen abklingt (Gauß mit {@link #STROM_BREITE} m [G]), in der Richtung hinaus. Die Front je
 * Knoten zweimal: linear ({@code T_1940} des Wellennetzes + 105 s, Ereigniszeit) und, wenn ein Lauf da ist, aus der
 * Vorrechnung ({@code ankunft_g<k>.r32}: Stand erstmals 1 m über dem Pegel).
 * <p>
 * Flucht ({@link #flucht}): Abfahrt Δ s nach dem Stoß von einem Ankerplatz; zeitabhängiger Dijkstra (FIFO), ein Knoten
 * zählt nur, wenn das Boot vor der Front dort ist; entkommen, wenn ein Knoten 1 km jenseits des Tors erreicht wird.
 */
public final class SeeNetz {
    public static final String ORDNER = "netz/see";
    /** Fahrt durchs Wasser (m/s, 8 kn), Tiefgang + Reserve (m), Umkreis des feinen Netzes um das Tor (m), Breite des Stroms (m). */
    public static final double FAHRT = 8 * 0.514444, TIEFGANG = 1.65, RESERVE = 0.5, NAH = 800, STROM_BREITE = 450;
    /** Jenseits des Tors (m) gilt man als entkommen. */
    public static final double DRAUSSEN = 950;
    /** Die Ankerplätze (UTM 8 N, wie {@code tools.Boote1958} sie findet [G]): Edrie, Badger (die Sunmore daneben). */
    public static final Object[][] PLAETZE = {{"Edrie", 347_545.0, 6_500_506.0}, {"Badger", 345_153.0, 6_500_797.0}};
    /** Abfahrten (s nach dem Stoß) der Tabelle. */
    public static final double[] ABFAHRT = {0, 15, 30, 45, 60, 75, 90, 105, 120, 135, 150, 165, 180};

    // Knoten (Nummer = Index + 1)
    public int n;
    public double[] ost, nord, tiefe, faktorKnoten, frontLinear, frontEreignis, draussen;
    public int[] abstand;
    // Kanten (Nummer = Index + 1), gerichtet
    public int m;
    public int[] von, nach;
    public double[] laenge, re, rn, faktor;
    /** Mitte und Richtung hinaus (Ost, Nord) des Tors. */
    public double torE, torN, hinE, hinN;
    /** Gezeit beim Stoß über Mittelwasser (m) und Uhr des Stoßes (ms). */
    public double gezeit, beben = SceneClock.BEBEN.toEpochMilli();
    public String text = "", frontQuelle = "linear";
    // je Knoten die ausgehenden Kanten
    int[] start, aus;

    public double kosten(int e, double tSeitStoss, double[] strom) {
        double u = stromBei(strom, tSeitStoss) * faktor[e];
        double v = Math.max(0.5, FAHRT + u * (re[e] * hinE + rn[e] * hinN));
        return laenge[e] / v;
    }

    /** Strom im Tor (m/s, + hinaus) zur Zeit t s nach dem Stoß aus der Tabelle je Minute ab −30 min. */
    public static double stromBei(double[] strom, double t) {
        double x = (t + 1800) / 60;
        int i = (int) Math.floor(x);
        if (i < 0) return strom[0];
        if (i >= strom.length - 1) return strom[strom.length - 1];
        return strom[i] + (strom[i + 1] - strom[i]) * (x - i);
    }

    /** Tabelle des Stroms im Tor je Minute von −30 min bis +3 h um den Stoß. */
    public static double[] stromTabelle() {
        Gezeiten g = new Gezeiten();
        double[] s = new double[211];
        for (int i = 0; i < s.length; i++) s[i] = g.stromTor(SceneClock.BEBEN.toEpochMilli() + (i * 60 - 1800) * 1000.0);
        return s;
    }

    void index() {
        start = new int[n + 1];
        for (int e = 0; e < m; e++) start[von[e] + 1]++;
        for (int i = 0; i < n; i++) start[i + 1] += start[i];
        aus = new int[m];
        int[] f = start.clone();
        for (int e = 0; e < m; e++) aus[f[von[e]]++] = e;
    }

    /** Nächster Knoten (Index) zu (E, N). */
    public int knotenBei(double e, double nn) {
        int b = -1;
        double d = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) { double x = Math.hypot(ost[i] - e, nord[i] - nn); if (x < d) { d = x; b = i; } }
        return b;
    }

    /** Ergebnis einer Flucht: Ankunft je Knoten (s nach dem Stoß, NaN = nicht vor der Front), Vorgänger-Kante, Ziel, Reserve. */
    public record Flucht(double delta, double[] ankunft, int[] vor, int ziel, double reserve) {
        public boolean entkommen() { return ziel >= 0; }
    }

    /**
     * Zeitabhängiger Dijkstra ab Knoten s mit Abfahrt delta (s nach dem Stoß); front je Knoten (Ereigniszeit, NaN = nie);
     * ein Knoten wird nur betreten, wenn die Ankunft vor seiner Front liegt. Ziel: der zuerst erreichte Knoten jenseits
     * {@link #DRAUSSEN}; Reserve dort = Front − Ankunft (unendlich, wenn die Front ihn nie erreicht).
     */
    public Flucht flucht(int s, double delta, double[] front, double[] strom) {
        if (start == null) index();
        double[] t = new double[n];
        int[] vor = new int[n];
        Arrays.fill(t, Double.NaN);
        Arrays.fill(vor, -1);
        boolean[] fertig = new boolean[n];
        PriorityQueue<double[]> q = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        if (!Double.isNaN(front[s]) && !(delta < front[s])) return new Flucht(delta, t, vor, -1, Double.NaN);
        t[s] = delta;
        q.add(new double[]{delta, s});
        int ziel = -1;
        while (!q.isEmpty()) {
            double[] x = q.poll();
            int u = (int) x[1];
            if (fertig[u]) continue;
            fertig[u] = true;
            if (ziel < 0 && draussen[u] >= DRAUSSEN) ziel = u;
            for (int k = start[u]; k < start[u + 1]; k++) {
                int e = aus[k], v = nach[e];
                if (fertig[v]) continue;
                double tv = t[u] + kosten(e, t[u], strom);
                if (!(tv < front[v]) && !Double.isNaN(front[v])) continue;
                if (Double.isNaN(t[v]) || tv < t[v]) { t[v] = tv; vor[v] = e; q.add(new double[]{tv, v}); }
            }
        }
        double res = ziel < 0 ? Double.NaN : Double.isNaN(front[ziel]) ? Double.POSITIVE_INFINITY : front[ziel] - t[ziel];
        return new Flucht(delta, t, vor, ziel, res);
    }

    /** Die späteste Abfahrt (s nach dem Stoß, auf 1 s), mit der man noch entkommt, zwischen lo und hi; NaN, wenn keine. */
    public double spaetesteAbfahrt(int s, double[] front, double[] strom, double lo, double hi) {
        if (!flucht(s, lo, front, strom).entkommen()) return Double.NaN;
        if (flucht(s, hi, front, strom).entkommen()) return hi;
        while (hi - lo > 1) {
            double mid = 0.5 * (lo + hi);
            if (flucht(s, mid, front, strom).entkommen()) lo = mid; else hi = mid;
        }
        return Math.floor(lo);
    }

    /** Der Weg zum Ziel als Knoten (Indizes, vom Start an). */
    public int[] weg(Flucht f) {
        if (f.ziel() < 0) return new int[0];
        List<Integer> l = new ArrayList<>();
        int v = f.ziel();
        l.add(v);
        while (f.vor()[v] >= 0) { v = von[f.vor()[v]]; l.add(v); }
        int[] r = new int[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(r.length - 1 - i);
        return r;
    }

    // ------------------------------------------------------------------ Dateien

    public void schreiben(File daten) throws IOException {
        File d = new File(daten, ORDNER);
        d.mkdirs();
        List<String> k = new ArrayList<>();
        k.add("NODE_ID\tOST\tNORD\tTIEFE_1940\tABSTAND\tSTROM_FAKTOR\tDRAUSSEN\tFRONT_LINEAR\tFRONT_EREIGNIS");
        for (int i = 0; i < n; i++)
            k.add(String.format(Locale.ROOT, "%d\t%.1f\t%.1f\t%.2f\t%d\t%.4f\t%.1f\t%s\t%s", i + 1, ost[i], nord[i], tiefe[i], abstand[i], faktorKnoten[i], draussen[i], z(frontLinear[i]), z(frontEreignis[i])));
        Files.write(new File(d, "knoten.tsv").toPath(), k, StandardCharsets.UTF_8);
        List<String> e = new ArrayList<>();
        e.add("LINK_ID\tSTART_NODE_ID\tEND_NODE_ID\tLAENGE\tRICHTUNG_OST\tRICHTUNG_NORD\tSTROM_FAKTOR\tCOST");
        for (int i = 0; i < m; i++)
            e.add(String.format(Locale.ROOT, "%d\t%d\t%d\t%.2f\t%.5f\t%.5f\t%.4f\t%.3f", i + 1, von[i] + 1, nach[i] + 1, laenge[i], re[i], rn[i], faktor[i], laenge[i] / FAHRT));
        Files.write(new File(d, "kanten.tsv").toPath(), e, StandardCharsets.UTF_8);
    }

    static String z(double v) { return Double.isNaN(v) ? "" : String.format(Locale.ROOT, "%.2f", v); }

    static double d(String s) { return s.isEmpty() ? Double.NaN : Double.parseDouble(s); }

    public static boolean da(File daten) { return new File(new File(daten, ORDNER), "kanten.tsv").isFile(); }

    public static SeeNetz lesen(File daten) throws IOException {
        File d = new File(daten, ORDNER);
        SeeNetz s = new SeeNetz();
        List<String[]> k = WellenNetz.tsv(new File(d, "knoten.tsv")), e = WellenNetz.tsv(new File(d, "kanten.tsv"));
        s.n = k.size();
        s.ost = new double[s.n]; s.nord = new double[s.n]; s.tiefe = new double[s.n]; s.abstand = new int[s.n]; s.faktorKnoten = new double[s.n];
        s.draussen = new double[s.n]; s.frontLinear = new double[s.n]; s.frontEreignis = new double[s.n];
        for (String[] z : k) {
            if (z[0].equals("NODE_ID")) continue;
            int i = Integer.parseInt(z[0]) - 1;
            s.ost[i] = d(z[1]); s.nord[i] = d(z[2]); s.tiefe[i] = d(z[3]); s.abstand[i] = Integer.parseInt(z[4]); s.faktorKnoten[i] = d(z[5]);
            s.draussen[i] = d(z[6]); s.frontLinear[i] = d(z[7]); s.frontEreignis[i] = z.length > 8 ? d(z[8]) : Double.NaN;
        }
        s.m = e.size();
        s.von = new int[s.m]; s.nach = new int[s.m]; s.laenge = new double[s.m]; s.re = new double[s.m]; s.rn = new double[s.m]; s.faktor = new double[s.m];
        for (String[] z : e) {
            if (z[0].equals("LINK_ID")) continue;
            int i = Integer.parseInt(z[0]) - 1;
            s.von[i] = Integer.parseInt(z[1]) - 1; s.nach[i] = Integer.parseInt(z[2]) - 1;
            s.laenge[i] = d(z[3]); s.re[i] = d(z[4]); s.rn[i] = d(z[5]); s.faktor[i] = d(z[6]);
        }
        File t = new File(d, "SEE.txt");
        if (t.isFile()) {
            s.text = Files.readString(t.toPath(), StandardCharsets.UTF_8);
            for (String z : s.text.split("\\R")) {
                if (z.startsWith("tor=")) { String[] p = z.substring(4).trim().split("\\s+"); s.torE = d(p[0]); s.torN = d(p[1]); s.hinE = d(p[2]); s.hinN = d(p[3]); }
                if (z.startsWith("gezeit_msl=")) s.gezeit = d(z.substring(11).trim().split("\\s+")[0]);
                if (z.startsWith("front_ereignis=")) s.frontQuelle = z.substring(15).trim();
            }
        }
        boolean leer = true;
        for (double v : s.frontEreignis) if (!Double.isNaN(v)) { leer = false; break; }
        if (leer) s.frontEreignis = null;
        s.index();
        return s;
    }

    /** „Flucht vor der Front (LB_NET_SEE): späteste Abfahrt Edrie −268 s, Badger +216 s (linear) …“ aus SEE.txt, oder ein Hinweis. */
    public static String fluchtZeile(File daten) {
        File t = new File(new File(daten, ORDNER), "SEE.txt");
        if (!t.isFile()) return "Flucht: daten/netz/see fehlt (ant aufbereitung -Dschritte=see)";
        try {
            java.util.Map<String, java.util.List<String>> m = new java.util.LinkedHashMap<>();
            for (String z : Files.readAllLines(t.toPath(), StandardCharsets.UTF_8)) {
                if (!z.startsWith("flucht.")) continue;
                String[] k = z.substring(7).split("[.=]");
                String w = k[2].equals("keine") ? "keine" : String.format(Locale.GERMAN, "%+d s", Integer.parseInt(k[2]));
                m.computeIfAbsent(k[1].equals("linear") ? "linear" : "Vorrechnung", x -> new ArrayList<>()).add(Character.toUpperCase(k[0].charAt(0)) + k[0].substring(1) + " " + w);
            }
            StringBuilder b = new StringBuilder("Flucht vor der Front, späteste Abfahrt nach dem Stoß:");
            for (var e : m.entrySet()) b.append(' ').append(String.join(", ", e.getValue())).append(" (").append(e.getKey()).append(");");
            return b.toString();
        } catch (Exception e) {
            return "Flucht: " + e.getMessage();
        }
    }

    @Override public String toString() { return String.format(Locale.GERMAN, "LB_NET_SEE: %d Knoten, %d Kanten (gerichtet)", n, m); }
}
