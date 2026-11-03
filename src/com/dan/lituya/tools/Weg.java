package com.dan.lituya.tools;

import com.dan.lituya.scene.Stufe;
import com.dan.lituya.scene.WegNetz;
import com.dan.lituya.scene.WellenNetz;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Schritt {@code weg} der Aufbereitung (Phase 16a, Werkbuch 7.5 und 9.8): {@code LB_NET_WEG} aus den Geländeklassen
 * ({@code daten/klassen}) nach {@code daten/netz/weg} ({@link WegNetz}).
 * <ul>
 * <li>Knoten: jede begehbare Zelle (nicht Meer, Wasser, Spalten, Bach über 0,5 m³/s), Ebene 1 der Kern zu 10 m, Ebene 2
 * die Szene zu 50 m; Elternknoten = die 50-m-Zelle.</li>
 * <li>Kanten: zu den 8 Nachbarn, je Richtung eine; die Diagonale nicht, wenn beide Nachbarn dazwischen gesperrt sind
 * (sonst schlüpft der Weg durch einen Bach). Gehzeit nach Tobler v = 6·e^(−3,5·|tan θ + 0,05|) km/h mal dem Zuschlag
 * der Klassen (Mittel der beiden Zellen); steiler als 45° gesperrt [G].</li>
 * <li>Umstieg zu {@code LB_NET_SEE}: je Knoten des Fahrwassers der nächste Strand bis 300 m; die Landeplätze der Boote.</li>
 * <li>Analysen: eine Stunde vom Strand (Anchorage Cove, Edrie) auf beiden Ebenen; Washburns Route 1934 auf Ebene 2:
 * Crillon Lake → South Crillon Glacier → Mount Crillon, verglichen mit Naismith.</li>
 * </ul>
 * <p>Aufruf: {@code Weg [daten]}.
 */
public final class Weg {
    private Weg() { }

    /** Steiler als 45° ist nicht begehbar [G]. */
    public static final double TAN_MAX = 1.0;
    public static final double STUNDE = 3600, UMSTIEG_M = 300;

    static final int[] DI = {1, 1, 0, -1, -1, -1, 0, 1}, DJ = {0, -1, -1, -1, 0, 1, 1, 1};

    /** Tobler (km/h) für tan θ in Gehrichtung (bergauf positiv). */
    public static double tobler(double tan) { return 6 * Math.exp(-3.5 * Math.abs(tan + 0.05)); }

    /** Naismith: 1 h je 5 km und 1 h je 600 m Aufstieg (s). */
    public static double naismith(double km, double aufM) { return (km / 5 + aufM / 600) * 3600; }

    public static void rechnen(Rohdaten r) throws Exception { rechnen(r.daten, r::log); }

    public static WegNetz rechnen(File daten, Welle.Log log) throws Exception {
        long t0 = System.nanoTime();
        Klassen.Raster[] rs = {Klassen.KERN, Klassen.SZENE};
        byte[][] kl = new byte[2][];
        float[][] z = new float[2][];
        for (int e = 0; e < 2; e++) {
            kl[e] = Klassen.lesen(daten, rs[e]);
            z[e] = Klassen.gelaende(daten, rs[e]);
            if (kl[e] == null || z[e] == null) throw new IOException("daten/klassen fehlt (ant aufbereitung -Dschritte=klassen)");
        }
        // Knoten
        int[][] id = new int[2][];
        int n = 0;
        for (int e = 0; e < 2; e++) {
            id[e] = new int[kl[e].length];
            for (int c = 0; c < kl[e].length; c++) id[e][c] = Double.isNaN(Klassen.ZUSCHLAG[kl[e][c]]) ? -1 : n++;
        }
        WegNetz w = new WegNetz();
        w.n = n;
        w.ebene = new byte[n]; w.klasse = new byte[n]; w.zelle = new int[n]; w.hoehe = new float[n]; w.parent = new int[n];
        for (int e = 0; e < 2; e++)
            for (int c = 0; c < kl[e].length; c++) {
                int k = id[e][c];
                if (k < 0) continue;
                w.ebene[k] = (byte) (e + 1); w.klasse[k] = kl[e][c]; w.zelle[k] = c; w.hoehe[k] = z[e][c]; w.parent[k] = -1;
            }
        int ohneEltern = 0;
        for (int k = 0; k < n; k++) {
            if (w.ebene[k] != 1) continue;
            Klassen.Raster s = Klassen.SZENE;
            int i = (int) Math.floor((w.ost(k) - s.e0()) / s.zelle()), j = (int) Math.floor((s.n0() - w.nord(k)) / s.zelle());
            int p = i < 0 || j < 0 || i >= s.w() || j >= s.h() ? -1 : id[1][j * s.w() + i];
            w.parent[k] = p;
            if (p < 0) ohneEltern++;
        }
        // Kanten: erst zählen, dann füllen (nach von sortiert)
        long m = 0;
        int steil = 0, eck = 0;
        for (int pass = 0; pass < 2; pass++) {
            int e2 = 0;
            for (int e = 0; e < 2; e++) {
                Klassen.Raster r = rs[e];
                int W = r.w(), H = r.h();
                for (int c = 0; c < kl[e].length; c++) {
                    int a = id[e][c];
                    if (a < 0) continue;
                    int i = c % W, j = c / W;
                    for (int d = 0; d < 8; d++) {
                        int ii = i + DI[d], jj = j + DJ[d];
                        if (ii < 0 || jj < 0 || ii >= W || jj >= H) continue;
                        int cb = jj * W + ii, b = id[e][cb];
                        if (b < 0) continue;
                        if (d % 2 == 1 && id[e][j * W + ii] < 0 && id[e][jj * W + i] < 0) { if (pass == 0) eck++; continue; }
                        double L = r.zelle() * (d % 2 == 1 ? Math.sqrt(2) : 1);
                        double tan = (z[e][cb] - z[e][c]) / L;
                        if (Math.abs(tan) > TAN_MAX) { if (pass == 0) steil++; continue; }
                        if (pass == 0) { m++; continue; }
                        double zu = 0.5 * (Klassen.ZUSCHLAG[kl[e][c]] + Klassen.ZUSCHLAG[kl[e][cb]]);
                        w.von[e2] = a;
                        w.nach[e2] = b;
                        w.cost[e2] = (float) (L / (tobler(tan) / 3.6) * zu);
                        e2++;
                    }
                }
            }
            if (pass == 0) {
                if (m > Integer.MAX_VALUE - 8) throw new IllegalStateException("zu viele Kanten: " + m);
                w.m = (int) m;
                w.von = new int[w.m]; w.nach = new int[w.m]; w.cost = new float[w.m];
            }
        }
        w.index();
        int n1 = 0;
        for (byte b : w.ebene) if (b == 1) n1++;
        log.log("LB_NET_WEG: %,d Knoten (Ebene 1 Kern 10 m %,d, Ebene 2 Szene 50 m %,d; %,d ohne Elternknoten), %,d Kanten gerichtet (%,d zu steil, %,d Diagonalen zwischen Sperren)",
                n, n1, n - n1, ohneEltern, w.m, steil, eck);

        // Umstieg zum Fahrwasser und die Landeplätze
        File dir = new File(daten, WegNetz.ORDNER);
        dir.mkdirs();
        List<String> um = new ArrayList<>();
        um.add("SEE_NODE_ID\tWEG_NODE_ID\tABSTAND");
        File see = new File(new File(daten, "netz/see"), "knoten.tsv");
        int nu = 0;
        if (see.isFile())
            for (String[] s : WellenNetz.tsv(see)) {
                int k = w.naechster(1, Double.parseDouble(s[1]), Double.parseDouble(s[2]), UMSTIEG_M, Klassen.STRAND);
                if (k < 0) continue;
                um.add(String.format(Locale.ROOT, "%s\t%d\t%.1f", s[0], k + 1, Math.hypot(w.ost(k) - Double.parseDouble(s[1]), w.nord(k) - Double.parseDouble(s[2]))));
                nu++;
            }
        Files.write(new File(dir, "umstieg.tsv").toPath(), um, StandardCharsets.UTF_8);
        Map<String, double[]> namen = Wasser.namen(daten);
        // Landeplätze: der nächste Strand zu den Ankerplätzen aus LB_NET_SEE
        double[][] plaetze = {{345252.6, 6500936.8}, {347545, 6500506}};   // Badger in Anchorage Cove, Edrie am Südufer (15a [G])
        String[] pn = {"badger", "edrie"};
        File orte = new File(new File(daten, "netz/see"), "orte.tsv");
        if (orte.isFile()) for (String[] s : WellenNetz.tsv(orte)) for (int q = 0; q < 2; q++) if (s[0].equalsIgnoreCase(pn[q])) plaetze[q] = new double[]{Double.parseDouble(s[1]), Double.parseDouble(s[2])};
        List<String> kopf = new ArrayList<>(), pw = new ArrayList<>();
        pw.add("FRAGE\tEBENE\tSTART_NODE_ID\tZIEL_NODE_ID\tKNOTEN\tFLAECHE_KM2\tZEIT_S");
        // Landeplätze und Wegpunkte im größten zusammenhängenden Teil (Befund: Strandstücke und Gletscherteile zwischen Sperren)
        int[] gr = new int[2], kz = new int[1];
        int[] komp = w.komponenten(gr, kz);
        float[] zeit = new float[Klassen.KERN.w() * Klassen.KERN.h()];
        java.util.Arrays.fill(zeit, Float.NaN);
        for (int q = 0; q < 2; q++) {
            int s1 = w.naechster(1, plaetze[q][0], plaetze[q][1], 2000, komp, gr[0], Klassen.STRAND);
            if (s1 < 0) s1 = w.naechster(1, plaetze[q][0], plaetze[q][1], 2000, komp, gr[0]);
            if (s1 < 0) { log.log("  HINWEIS: kein Strand bei %s", pn[q]); continue; }
            double[] t1 = w.dijkstra(s1, STUNDE, null);
            int a1 = 0;
            double weit = 0;
            for (int k = 0; k < n; k++) if (!Double.isNaN(t1[k])) {
                a1++;
                weit = Math.max(weit, Math.hypot(w.ost(k) - w.ost(s1), w.nord(k) - w.nord(s1)));
                float alt = zeit[w.zelle[k]];
                if (Float.isNaN(alt) || t1[k] < alt) zeit[w.zelle[k]] = (float) t1[k];
            }
            int s2 = w.parent[s1] >= 0 && komp[w.parent[s1]] == gr[1] ? w.parent[s1] : w.naechster(2, w.ost(s1), w.nord(s1), 1000, komp, gr[1]);
            double[] t2 = w.dijkstra(s2, STUNDE, null);
            int a2 = 0;
            for (int k = 0; k < n; k++) if (!Double.isNaN(t2[k])) a2++;
            String v = String.format(Locale.GERMAN, "%.2f km² (bis %.1f km Luftlinie) vom Strand bei E %.0f N %.0f; auf 50 m %.2f km²", a1 * 1e-4, weit / 1000, w.ost(s1), w.nord(s1), a2 * 25e-4);
            kopf.add("stunde." + pn[q] + "=" + v);
            kopf.add(String.format(Locale.ROOT, "start.%s=%d %d", pn[q], s1 + 1, s2 + 1));
            pw.add(String.format(Locale.ROOT, "STUNDE_%s\t1\t%d\t\t%d\t%.4f\t%.0f", pn[q].toUpperCase(Locale.ROOT), s1 + 1, a1, a1 * 1e-4, STUNDE));
            pw.add(String.format(Locale.ROOT, "STUNDE_%s\t2\t%d\t\t%d\t%.4f\t%.0f", pn[q].toUpperCase(Locale.ROOT), s2 + 1, a2, a2 * 25e-4, STUNDE));
            log.log("  1 h vom Strand %s: %s", pn[q], v);
        }
        Stufe.schreiben(new File(dir, "zeit_kern_10m.r32"), zeit);
        // Washburn 1934
        double[] cl = namen.getOrDefault("Crillon Lake|Lake", new double[]{361559.0, 6493711.9}), sc = namen.getOrDefault("South Crillon Glacier|Glacier", new double[]{367532.8, 6501250.4}),
                mc = namen.getOrDefault("Mount Crillon|Summit", new double[]{374040.8, 6504480.7});
        double[][] wp = {cl, sc, mc};
        String[] wn = {"Crillon Lake (Basislager)", "South Crillon Glacier", "Mount Crillon"};
        int[] wk = new int[3];
        for (int q = 0; q < 3; q++) wk[q] = q == 1 ? w.naechster(2, wp[q][0], wp[q][1], 3000, komp, gr[1], Klassen.EIS, Klassen.SCHNEE) : w.naechster(2, wp[q][0], wp[q][1], 1500, komp, gr[1]);
        kopf.add(String.format(Locale.ROOT, "komponenten=%d (größte: Ebene 1 %d Knoten, Ebene 2 %d Knoten)", kz[0], anzahl(komp, gr[0]), anzahl(komp, gr[1])));
        List<String> ws = new ArrayList<>();
        ws.add("SEQ\tNODE_ID\tOST\tNORD\tHOEHE\tZEIT_S\tKLASSE\tWEGPUNKT");
        String wash = null;
        if (wk[0] >= 0 && wk[1] >= 0 && wk[2] >= 0) {
            double tt = 0, L = 0, auf = 0, ab = 0;
            int seq = 0;
            boolean ok = true;
            for (int q = 0; q < 2 && ok; q++) {
                double[] tz = new double[1];
                int[] p = w.weg(wk[q], wk[q + 1], tz);
                if (p == null) { ok = false; break; }
                pw.add(String.format(Locale.ROOT, "WASHBURN_%d\t2\t%d\t%d\t%d\t\t%.1f", q + 1, wk[q] + 1, wk[q + 1] + 1, p.length, tz[0]));
                for (int i = q == 0 ? 0 : 1; i < p.length; i++) {
                    int k = p[i];
                    if (i > 0) {
                        int a = p[i - 1];
                        L += Math.hypot(w.ost(k) - w.ost(a), w.nord(k) - w.nord(a));
                        double dh = w.hoehe[k] - w.hoehe[a];
                        if (dh > 0) auf += dh; else ab -= dh;
                    }
                    double tk = tt + (i == 0 ? 0 : kosten(w, p, i));
                    tt = tk;
                    ws.add(String.format(Locale.ROOT, "%d\t%d\t%.1f\t%.1f\t%.1f\t%.1f\t%s\t%s", ++seq, k + 1, w.ost(k), w.nord(k), w.hoehe[k], tt, Klassen.NAME[w.klasse[k]],
                            i == 0 ? wn[q] : i == p.length - 1 ? wn[q + 1] : ""));
                }
            }
            if (ok) {
                double na = naismith(L / 1000, auf);
                wash = String.format(Locale.GERMAN, "%.1f km, %.0f m Aufstieg, %.0f m Abstieg, Gehzeit %.1f h (Naismith %.1f h) von %s (%.0f m) über %s zum %s (%.0f m)", L / 1000, auf, ab, tt / 3600, na / 3600,
                        wn[0], w.hoehe[wk[0]], wn[1], wn[2], w.hoehe[wk[2]]);
                kopf.add("washburn=" + wash);
                log.log("  Washburn 1934: %s", wash);
            } else log.log("  HINWEIS: Washburn 1934: kein Weg (Spalten oder Sperren)");
        } else log.log("  HINWEIS: Washburn 1934: Wegpunkt ohne Knoten (%d, %d, %d)", wk[0], wk[1], wk[2]);
        Files.write(new File(dir, "washburn.tsv").toPath(), ws, StandardCharsets.UTF_8);
        Files.write(new File(dir, "pruefwege.tsv").toPath(), pw, StandardCharsets.UTF_8);
        w.schreiben(daten);

        List<String> t = new ArrayList<>();
        t.add("# LB_NET_WEG als Datei (Phase 16a, Werkbuch 7.5): Wege über Gelände, gerichtet, zwei Ebenen (com.dan.lituya.scene.WegNetz)");
        t.add("format=knoten.bin: int n; byte ebene[n]; byte klasse[n]; int zelle[n]; float hoehe[n]; int parent[n] (Index, −1 keiner) · kanten.bin: int m; int von[m]; int nach[m]; float cost[m] (s) · Little Endian, Nummer = Index + 1");
        t.add(String.format(Locale.ROOT, "knoten=%d", w.n));
        t.add(String.format(Locale.ROOT, "kanten=%d", w.m));
        t.add(String.format(Locale.ROOT, "ebene1=%d Knoten (Kern 10 m)", n1));
        t.add(String.format(Locale.ROOT, "ebene2=%d Knoten (Szene 50 m)", n - n1));
        t.add("kosten=Tobler 6·e^(−3,5·|tan θ + 0,05|) km/h × Zuschlag der Klassen (Mittel beider Zellen); steiler als 45° gesperrt [G]; Diagonale nicht zwischen zwei Sperren");
        StringBuilder z1 = new StringBuilder();
        for (int c = 0; c < Klassen.NAME.length; c++) z1.append(c > 0 ? ", " : "").append(Klassen.NAME[c]).append(Double.isNaN(Klassen.ZUSCHLAG[c]) ? " gesperrt" : String.format(Locale.ROOT, " %.1f", Klassen.ZUSCHLAG[c]));
        t.add("zuschlag=" + z1);
        t.add(String.format(Locale.ROOT, "umstieg=%d Knoten von LB_NET_SEE mit Strand bis %.0f m", nu, UMSTIEG_M));
        t.addAll(kopf);
        t.add("pruefsumme=" + WegNetz.pruefsumme(daten));
        Files.write(new File(dir, "WEG.txt").toPath(), t, StandardCharsets.UTF_8);
        log.log("  nach %s in %.1f s", dir, (System.nanoTime() - t0) / 1e9);
        w.text = String.join("\n", t);
        return w;
    }

    static int anzahl(int[] k, int c) { int n = 0; for (int x : k) if (x == c) n++; return n; }

    /** Kosten der Kante p[i−1] → p[i]. */
    static double kosten(WegNetz w, int[] p, int i) {
        int a = p[i - 1], b = p[i];
        for (int e = w.aus[a]; e < w.aus[a + 1]; e++) if (w.nach[e] == b) return w.cost[e];
        return Double.NaN;
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        rechnen(new File(a.length > 0 ? a[0] : "daten"), (f, x) -> System.out.println(String.format(Locale.GERMAN, f, x)));
    }
}
