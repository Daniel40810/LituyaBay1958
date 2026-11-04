package com.dan.lituya.scene;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code LB_NET_WASSER} als Datei (Phase 16, Werkbuch 7.4): Gewässer und Gletscher der Szene, gerichtet stromab, in zwei
 * Ebenen. Ebene 1: Bäche aus dem Abfluss des Geländes (10 m, ab 0,5 km² Einzugsfläche) mit Quellen, Zusammenflüssen,
 * Gletschertoren, Seeausläufen, Mündungen und Stützknoten je 200 m, dazu die Fließlinien der Gletscher (ITS_LIVE) und die
 * Mündungsziele (Gilbert Inlet, Crillon Inlet, Bucht, Golf von Alaska, Rand der Szene). Ebene 2: die Teileinzugsgebiete
 * als Elternknoten (je Mündung, geteilt an Zusammenflüssen ab 10 km² je Zweig, dazu der Küstensaum je Ziel) mit Kanten
 * zum nächsten Gebiet stromab.
 * <p>Dateien in {@code daten/netz/wasser}: {@code knoten.tsv}, {@code kanten.tsv}, {@code linien.tsv} (Verlauf der Kanten
 * der Ebene 1), {@code einzug.tsv}, {@code umrisse.tsv} (Umrisse der Gebiete), {@code seen.tsv}, {@code senken.tsv},
 * {@code pruefwege.tsv}, {@code einzug_50m.i32} (Gebiet je 50-m-Zelle) und {@code WASSER.txt}.
 * <p>Die Rechnungen hier (traceIn, traceOut, Fließzeit zum Ziel, Komponenten) sind die eigene Seite der Zweitrechnung
 * gegen LOD ({@code netz.WasserLod}) und {@code route}.
 */
public final class WasserNetz {
    public static final String ORDNER = "netz/wasser";

    /** Knotenarten der Ebene 1 und 2. */
    public static final String QUELLE = "QUELLE", ZUSAMMENFLUSS = "ZUSAMMENFLUSS", STUETZ = "STUETZ", MUENDUNG = "MUENDUNG", TEILUNG = "TEILUNG",
            GLETSCHERTOR = "GLETSCHERTOR", SEE = "SEE", EIS = "EIS", ZIEL = "ZIEL", GEBIET = "GEBIET", KUESTE = "KUESTE";
    /** Kantenarten. */
    public static final String BACH = "BACH", BACH_EIS = "BACH_EIS", GLETSCHER = "GLETSCHER", MUENDET = "MUENDET", STROMAB = "STROMAB";
    /** Die Mündungsziele (Ebene 1 und 2), in dieser Folge. */
    public static final String[] ZIELE = {"Gilbert Inlet", "Crillon Inlet", "Bucht", "Golf von Alaska", "Rand der Szene"};

    // Knoten (Index = NODE_ID − 1)
    public int n;
    public int[] ebene, parent;
    public String[] art, name;
    public double[] ost, nord, hoehe, flaeche, eisAnteil;
    // Kanten (Index = LINK_ID − 1)
    public int m;
    public int[] kEbene, von, nach;
    public String[] kArt, kName;
    public double[] laenge, gefaelle, kFlaeche, abfluss, geschw, cost;
    public String text = "";

    // Nachbarschaft (gerichtet), nach index()
    public int[] ausAnf, ausK, einAnf, einK;

    public void index() {
        ausAnf = new int[n + 1]; einAnf = new int[n + 1];
        for (int e = 0; e < m; e++) { ausAnf[von[e] + 1]++; einAnf[nach[e] + 1]++; }
        for (int i = 0; i < n; i++) { ausAnf[i + 1] += ausAnf[i]; einAnf[i + 1] += einAnf[i]; }
        ausK = new int[m]; einK = new int[m];
        int[] a = Arrays.copyOf(ausAnf, n), b = Arrays.copyOf(einAnf, n);
        for (int e = 0; e < m; e++) { ausK[a[von[e]]++] = e; einK[b[nach[e]]++] = e; }
    }

    /** Knoten-Index des Ziels mit diesem Namen (Ebene 1 oder 2) oder −1. */
    public int ziel(String nm, int eb) {
        for (int i = 0; i < n; i++) if (ebene[i] == eb && ZIEL.equals(art[i]) && nm.equals(name[i])) return i;
        return -1;
    }

    /** Alle Knoten, von denen Wasser zum Knoten z fließt (traceIn, nur Kanten der Ebene eb), z selbst eingeschlossen. */
    public boolean[] traceIn(int z, int eb) {
        boolean[] s = new boolean[n];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        s[z] = true;
        q.add(z);
        while (!q.isEmpty()) {
            int v = q.poll();
            for (int j = einAnf[v]; j < einAnf[v + 1]; j++) {
                int e = einK[j];
                if (kEbene[e] != eb || s[von[e]]) continue;
                s[von[e]] = true;
                q.add(von[e]);
            }
        }
        return s;
    }

    /** Alle Knoten stromab von q (traceOut, Ebene eb), q eingeschlossen, in der Folge des Wegs. */
    public List<Integer> traceOut(int q, int eb) {
        List<Integer> l = new ArrayList<>();
        boolean[] s = new boolean[n];
        int v = q;
        while (v >= 0 && !s[v]) {
            s[v] = true;
            l.add(v);
            int w = -1;
            for (int j = ausAnf[v]; j < ausAnf[v + 1]; j++) if (kEbene[ausK[j]] == eb) { w = nach[ausK[j]]; break; }
            v = w;
        }
        return l;
    }

    /** Fließzeit (s) jedes Knotens zum Ziel z über die Kanten der Ebene eb (Dijkstra rückwärts), NaN ohne Weg. */
    public double[] zeitZum(int z, int eb) {
        double[] t = new double[n];
        Arrays.fill(t, Double.POSITIVE_INFINITY);
        t[z] = 0;
        java.util.PriorityQueue<double[]> q = new java.util.PriorityQueue<>((x, y) -> Double.compare(x[0], y[0]));
        q.add(new double[]{0, z});
        while (!q.isEmpty()) {
            double[] x = q.poll();
            int v = (int) x[1];
            if (x[0] > t[v]) continue;
            for (int j = einAnf[v]; j < einAnf[v + 1]; j++) {
                int e = einK[j];
                if (kEbene[e] != eb) continue;
                double d = t[v] + cost[e];
                if (d < t[von[e]]) { t[von[e]] = d; q.add(new double[]{d, von[e]}); }
            }
        }
        for (int i = 0; i < n; i++) if (Double.isInfinite(t[i])) t[i] = Double.NaN;
        return t;
    }

    /** Zusammenhangskomponenten (ungerichtet) der Ebene eb: Nummer je Knoten (−1: nicht in der Ebene) und die Zahl. */
    public int[] komponenten(int eb, int[] zahl) {
        int[] k = new int[n];
        Arrays.fill(k, -1);
        int c = 0;
        for (int s = 0; s < n; s++) {
            if (ebene[s] != eb || k[s] >= 0) continue;
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(s);
            k[s] = c;
            while (!q.isEmpty()) {
                int v = q.poll();
                for (int j = ausAnf[v]; j < ausAnf[v + 1]; j++) { int e = ausK[j]; if (kEbene[e] == eb && k[nach[e]] < 0) { k[nach[e]] = c; q.add(nach[e]); } }
                for (int j = einAnf[v]; j < einAnf[v + 1]; j++) { int e = einK[j]; if (kEbene[e] == eb && k[von[e]] < 0) { k[von[e]] = c; q.add(von[e]); } }
            }
            c++;
        }
        zahl[0] = c;
        return k;
    }

    /** Fehler im Aufbau (Baum stromab, kreisfrei, jedes Ziel erreichbar) oder null. */
    public String fehler() {
        for (int i = 0; i < n; i++) {
            int aus = 0;
            for (int j = ausAnf[i]; j < ausAnf[i + 1]; j++) if (kEbene[ausK[j]] == ebene[i]) aus++;
            if (ZIEL.equals(art[i]) ? aus != 0 : aus != 1) return String.format(Locale.GERMAN, "Knoten %d (%s, Ebene %d) hat %d Kanten stromab", i + 1, art[i], ebene[i], aus);
            if (ebene[i] == 1 && parent[i] < 0) return String.format(Locale.GERMAN, "Knoten %d (%s) ohne Gebiet", i + 1, art[i]);
        }
        for (int i = 0; i < n; i++) {
            List<Integer> w = traceOut(i, ebene[i]);
            int e = w.get(w.size() - 1);
            if (!ZIEL.equals(art[e])) return String.format(Locale.GERMAN, "von Knoten %d (%s) kein Weg zu einem Ziel (endet in %d, %s)", i + 1, art[i], e + 1, art[e]);
            if (w.size() > n) return "Kreis";
        }
        return null;
    }

    // ------------------------------------------------------------------ Dateien

    public static boolean da(File daten) { return new File(new File(daten, ORDNER), "kanten.tsv").isFile(); }

    public void schreiben(File daten) throws IOException {
        File d = new File(daten, ORDNER);
        d.mkdirs();
        List<String> k = new ArrayList<>();
        k.add("NODE_ID\tEBENE\tART\tOST\tNORD\tHOEHE\tFLAECHE_KM2\tEIS_ANTEIL\tPARENT_NODE_ID\tNAME");
        for (int i = 0; i < n; i++)
            k.add(String.format(Locale.ROOT, "%d\t%d\t%s\t%.1f\t%.1f\t%.1f\t%.4f\t%.3f\t%s\t%s", i + 1, ebene[i], art[i], ost[i], nord[i], hoehe[i], flaeche[i], eisAnteil[i],
                    parent[i] < 0 ? "" : String.valueOf(parent[i] + 1), name[i] == null ? "" : name[i]));
        Files.write(new File(d, "knoten.tsv").toPath(), k, StandardCharsets.UTF_8);
        List<String> e = new ArrayList<>();
        e.add("LINK_ID\tEBENE\tART\tSTART_NODE_ID\tEND_NODE_ID\tLAENGE\tGEFAELLE\tFLAECHE_KM2\tABFLUSS\tGESCHW\tCOST\tNAME");
        for (int i = 0; i < m; i++)
            e.add(String.format(Locale.ROOT, "%d\t%d\t%s\t%d\t%d\t%.1f\t%.5f\t%.4f\t%.3f\t%.6g\t%.6g\t%s", i + 1, kEbene[i], kArt[i], von[i] + 1, nach[i] + 1, laenge[i], gefaelle[i],
                    kFlaeche[i], abfluss[i], geschw[i], cost[i], kName[i] == null ? "" : kName[i]));
        Files.write(new File(d, "kanten.tsv").toPath(), e, StandardCharsets.UTF_8);
    }

    static double d(String s) { return s.isEmpty() ? Double.NaN : Double.parseDouble(s); }

    public static WasserNetz lesen(File daten) throws IOException {
        File d = new File(daten, ORDNER);
        WasserNetz w = new WasserNetz();
        List<String[]> k = WellenNetz.tsv(new File(d, "knoten.tsv")), e = WellenNetz.tsv(new File(d, "kanten.tsv"));
        w.n = k.size();
        w.ebene = new int[w.n]; w.parent = new int[w.n]; w.art = new String[w.n]; w.name = new String[w.n];
        w.ost = new double[w.n]; w.nord = new double[w.n]; w.hoehe = new double[w.n]; w.flaeche = new double[w.n]; w.eisAnteil = new double[w.n];
        for (String[] z : k) {
            int i = Integer.parseInt(z[0]) - 1;
            w.ebene[i] = Integer.parseInt(z[1]); w.art[i] = z[2]; w.ost[i] = d(z[3]); w.nord[i] = d(z[4]); w.hoehe[i] = d(z[5]); w.flaeche[i] = d(z[6]); w.eisAnteil[i] = d(z[7]);
            w.parent[i] = z.length > 8 && !z[8].isEmpty() ? Integer.parseInt(z[8]) - 1 : -1;
            w.name[i] = z.length > 9 && !z[9].isEmpty() ? z[9] : null;
        }
        w.m = e.size();
        w.kEbene = new int[w.m]; w.von = new int[w.m]; w.nach = new int[w.m]; w.kArt = new String[w.m]; w.kName = new String[w.m];
        w.laenge = new double[w.m]; w.gefaelle = new double[w.m]; w.kFlaeche = new double[w.m]; w.abfluss = new double[w.m]; w.geschw = new double[w.m]; w.cost = new double[w.m];
        for (String[] z : e) {
            int i = Integer.parseInt(z[0]) - 1;
            w.kEbene[i] = Integer.parseInt(z[1]); w.kArt[i] = z[2]; w.von[i] = Integer.parseInt(z[3]) - 1; w.nach[i] = Integer.parseInt(z[4]) - 1;
            w.laenge[i] = d(z[5]); w.gefaelle[i] = d(z[6]); w.kFlaeche[i] = d(z[7]); w.abfluss[i] = d(z[8]); w.geschw[i] = d(z[9]); w.cost[i] = d(z[10]);
            w.kName[i] = z.length > 11 && !z[11].isEmpty() ? z[11] : null;
        }
        File t = new File(d, "WASSER.txt");
        if (t.isFile()) w.text = Files.readString(t.toPath(), StandardCharsets.UTF_8);
        w.index();
        return w;
    }

    /** Die Verläufe der Kanten der Ebene 1 aus {@code linien.tsv}: Index der Kante → {E, N, E, N, …} (leer, wenn die Datei fehlt). */
    public static java.util.Map<Integer, double[]> linien(File daten) throws IOException {
        java.util.Map<Integer, double[]> lin = new java.util.HashMap<>();
        File f = new File(new File(daten, ORDNER), "linien.tsv");
        if (!f.isFile()) return lin;
        for (String[] z : WellenNetz.tsv(f)) {
            if (z.length < 2 || z[1].isEmpty()) continue;
            String[] p = z[1].split(",");
            double[] q = new double[2 * p.length];
            for (int k = 0; k < p.length; k++) { String[] xy = p[k].split(" "); q[2 * k] = Double.parseDouble(xy[0]); q[2 * k + 1] = Double.parseDouble(xy[1]); }
            lin.put(Integer.parseInt(z[0]) - 1, q);
        }
        return lin;
    }

    /**
     * Abfluss (m³/s) in Phase 16c: Fläche × Regen × Beiwert als Grund über dem Mittelwasser
     * ({@link com.dan.lituya.tools.Klassen#mittelwasser}); Beiwert Land 0,7, Eis 1,0 [G]. Regen in mm/h.
     */
    public static double abfluss(double km2, double eis, double regenMmH) {
        double c = com.dan.lituya.tools.Klassen.BEIWERT_LAND * (1 - eis) + com.dan.lituya.tools.Klassen.BEIWERT_EIS * eis;
        return com.dan.lituya.tools.Klassen.mittelwasser(km2, eis) + km2 * regenMmH * c / 3.6;
    }

    /** Wasserspiegelbreite (m) aus dem Abfluss nach der hydraulischen Geometrie b = 2,5·Q^0,5 [G], 0,5 … 90 m. */
    public static double breite(double q) { return Math.max(0.5, Math.min(90, 2.5 * Math.sqrt(Math.max(0, q)))); }

    /** Ein Wert aus WASSER.txt ({@code schluessel=wert}) oder null. */
    public String wert(String schluessel) {
        for (String z : text.split("\\R")) if (z.startsWith(schluessel + "=")) return z.substring(schluessel.length() + 1).trim();
        return null;
    }

    /** „Einzugsgebiete: Gilbert Inlet 123 km² (88 % Eis) …“ aus WASSER.txt, oder ein Hinweis. */
    public static String zeile(File daten) {
        File t = new File(new File(daten, ORDNER), "WASSER.txt");
        if (!t.isFile()) return "Wasser: daten/netz/wasser fehlt (ant aufbereitung -Dschritte=wasser)";
        try {
            WasserNetz w = new WasserNetz();
            w.text = Files.readString(t.toPath(), StandardCharsets.UTF_8);
            StringBuilder b = new StringBuilder("Einzugsgebiete (LB_NET_WASSER):");
            for (String z : new String[]{"Gilbert Inlet", "Crillon Inlet", "Bucht"}) {
                String v = w.wert("einzug." + schluessel(z));
                if (v != null) b.append(' ').append(z).append(' ').append(v.split(";")[0]).append(';');
            }
            return b.toString();
        } catch (IOException e) {
            return "Wasser: " + e.getMessage();
        }
    }

    /** „gilbert_inlet“ */
    public static String schluessel(String ziel) { return ziel.toLowerCase(Locale.ROOT).replace(' ', '_'); }

    @Override public String toString() {
        int n1 = 0, m1 = 0;
        for (int i = 0; i < n; i++) if (ebene[i] == 1) n1++;
        for (int e = 0; e < m; e++) if (kEbene[e] == 1) m1++;
        return String.format(Locale.GERMAN, "LB_NET_WASSER: %d Knoten (%d Ebene 1, %d Ebene 2), %d Kanten (%d, %d), gerichtet stromab", n, n1, n - n1, m, m1, m - m1);
    }
}
