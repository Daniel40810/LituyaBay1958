package com.dan.lituya.tools;

import com.dan.lituya.scene.EreignisDaten;
import com.dan.lituya.scene.SeeNetz;
import com.dan.lituya.scene.WegNetz;
import com.dan.lituya.scene.WellenNetz;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Schritt {@code landflucht} der Aufbereitung (Phase 16b): „Wer hätte sich an Land retten können?“ – mit dem Boot vom
 * Ankerplatz durch das Fahrwasser ({@code LB_NET_SEE}, Gezeitenstrom, vor der Front) zu einem Strand ({@code LB_WEG_UMSTIEG}),
 * Anlanden 3 min [G], dann zu Fuß ({@code LB_NET_WEG}, Ebene 1) bergauf, bis man über dem Auflauf der Vorrechnung plus 10 m
 * steht – jeder Knoten vor der Front erreicht, an Land wie auf dem Wasser.
 * <ul>
 * <li>Front an Land: {@code ankunft_g*.r32} des Laufs (erstmals 1 m über dem Pegel), aus dem feinsten Gitter, das die Zelle
 * deckt; nie nass = keine Front.</li>
 * <li>Sicher: die Zelle bleibt trocken und liegt mindestens 10 m über dem höchsten Stand ({@code max_g*.r32}) aller nassen
 * Zellen bis 300 m um sie [G].</li>
 * </ul>
 * Ergebnis nach {@code daten/netz/weg}: {@code landflucht.tsv} (je Boot und Abfahrt), {@code landwege.tsv} (der Weg der
 * spätesten Abfahrt, Wasser und Land), {@code sicher_kern_10m.u8}, {@code LANDFLUCHT.txt}.
 * <p>Aufruf: {@code Landflucht [daten] [lauf]}.
 */
public final class Landflucht {
    private Landflucht() { }

    /** Anlanden: vom Boot auf den Strand [G]. */
    public static final double ANLANDEN_S = 180;
    /** Sicher: so hoch über dem Auflauf in der Nähe [G]. */
    public static final double RAND_M = 10, UMKREIS_M = 300;
    /** Länger als so lange an Land zählt nicht mehr als Flucht [G]. */
    public static final double LAND_MAX_S = 3600;

    /** Front und Sicherheit je Zelle des Kerns (10 m). */
    public record Feld(float[] front, float[] stand, float[] auflauf, boolean[] sicher, String quelle) { }

    public static void rechnen(Rohdaten r) throws Exception { rechnen(r.daten, "kette", r::log); }

    /** Front, höchster Stand und Sicherheit auf dem 10-m-Raster des Kerns aus dem Lauf. */
    public static Feld feld(File daten, String lauf, float[] gelaende) throws IOException {
        File ld = EreignisDaten.ordner(daten, lauf);
        Properties p = new Properties();
        try (var in = Files.newBufferedReader(new File(ld, "EREIGNIS.txt").toPath(), StandardCharsets.ISO_8859_1)) { p.load(in); }
        int ng = Integer.parseInt(p.getProperty("gitter").trim());
        List<EreignisDaten.Gitter> g = new ArrayList<>();
        List<float[]> mx = new ArrayList<>(), an = new ArrayList<>();
        for (int k = 0; k < ng; k++) {
            g.add(new EreignisDaten.Gitter(d(p, "g" + k + ".e0"), d(p, "g" + k + ".n0"), d(p, "g" + k + ".zelle"), (int) d(p, "g" + k + ".nx"), (int) d(p, "g" + k + ".nz")));
            mx.add(r32(new File(ld, "max_g" + k + ".r32")));
            an.add(r32(new File(ld, "ankunft_g" + k + ".r32")));
        }
        Klassen.Raster r = Klassen.KERN;
        int W = r.w(), H = r.h(), N = W * H;
        float[] front = new float[N], stand = new float[N];
        Arrays.fill(front, Float.NaN);
        Arrays.fill(stand, Float.NaN);
        for (int j = 0; j < H; j++)
            for (int i = 0; i < W; i++) {
                double e = r.ost(i), n = r.nord(j);
                for (int k = ng - 1; k >= 0; k--) {
                    int x = g.get(k).index(e, n);
                    if (x < 0) continue;
                    stand[j * W + i] = mx.get(k)[x];
                    front[j * W + i] = an.get(k)[x];
                    break;
                }
            }
        // höchster Stand nasser Landzellen im Umkreis (Quadrat ±UMKREIS_M, gleitendes Maximum)
        int q = (int) Math.round(UMKREIS_M / r.zelle());
        float[] h = new float[N], auf = new float[N];
        // alle nassen Zellen, das Meer eingeschlossen (sonst wäre an einer Küste ohne Überflutung jede trockene Zelle sicher)
        for (int c = 0; c < N; c++) h[c] = Float.isNaN(stand[c]) ? Float.NEGATIVE_INFINITY : stand[c];
        float[] tmp = new float[N];
        for (int j = 0; j < H; j++) gleitMax(h, j * W, 1, W, q, tmp);
        for (int i = 0; i < W; i++) gleitMax(tmp, i, W, H, q, auf);
        boolean[] sicher = new boolean[N];
        for (int c = 0; c < N; c++) sicher[c] = Float.isNaN(stand[c]) && gelaende[c] > Wasser.MW && (auf[c] == Float.NEGATIVE_INFINITY || gelaende[c] >= auf[c] + RAND_M);
        return new Feld(front, stand, auf, sicher, lauf + " (" + ng + " Gitter, " + p.getProperty("quelle", "").trim() + ")");
    }

    static double d(Properties p, String k) { return Double.parseDouble(p.getProperty(k).trim()); }

    static float[] r32(File f) throws IOException {
        byte[] b = Files.readAllBytes(f.toPath());
        float[] v = new float[b.length / 4];
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(v);
        return v;
    }

    /** Gleitendes Maximum über ±q entlang einer Zeile oder Spalte (Anfang a, Schritt s, Länge n), nach out. */
    public static void gleitMax(float[] v, int a, int s, int n, int q, float[] out) {
        java.util.ArrayDeque<Integer> dq = new java.util.ArrayDeque<>();
        for (int i = 0, k = 0; i < n + q; i++) {
            if (i < n) {
                float x = v[a + i * s];
                while (!dq.isEmpty() && v[a + dq.peekLast() * s] <= x) dq.pollLast();
                dq.addLast(i);
            }
            int c = i - q;
            if (c < 0) continue;
            while (dq.peekFirst() < c - q) dq.pollFirst();
            out[a + c * s] = v[a + dq.peekFirst() * s];
            k++;
        }
    }

    /** Ergebnis einer Flucht: angelandet (SEE-Knoten, WEG-Knoten, Zeit), sicher (WEG-Knoten, Zeit), Reserve (kleinster Abstand zur Front auf dem Weg). */
    public record Ergebnis(double delta, boolean gerettet, int seeKnoten, int wegStart, double anLand, int sicherKnoten, double sicher, double reserve, int[] vorLand, double[] tLand,
                           SeeNetz.Flucht boot) { }

    /**
     * Eine Flucht: Boot ab Ankerplatz mit Abfahrt delta durch das Fahrwasser (vor der Front), an jedem Umstieg, den es
     * erreicht, an Land (+ ANLANDEN_S, vor der Front), dann zu Fuß (vor der Front) bis zum ersten sicheren Knoten.
     */
    public static Ergebnis flucht(SeeNetz s, int anker, double delta, double[] strom, WegNetz w, int[][] umstieg, double[] frontWeg, boolean[] sicherWeg) {
        SeeNetz.Flucht f = s.flucht(anker, delta, s.frontEreignis, strom);
        int n = w.n;
        double[] t = new double[n];
        Arrays.fill(t, Double.POSITIVE_INFINITY);
        int[] vor = new int[n], von = new int[n];
        double[] anl = new double[n];
        Arrays.fill(vor, -1);
        Arrays.fill(von, -1);
        int[] heap = new int[1024], pos = new int[n];
        Arrays.fill(pos, -1);
        int hn = 0;
        for (int[] u : umstieg) {
            double ts = f.ankunft()[u[0]];
            if (Double.isNaN(ts)) continue;
            double tw = ts + ANLANDEN_S;
            int k = u[1];
            if (!(tw < frontWeg[k]) && !Double.isNaN(frontWeg[k])) continue;
            if (tw < t[k]) {
                t[k] = tw;
                anl[k] = tw;
                von[k] = u[0];
                if (pos[k] < 0) { if (hn == heap.length) heap = Arrays.copyOf(heap, hn * 2); heap[hn] = k; pos[k] = hn++; }
                hoch(heap, pos, t, pos[k]);
            }
        }
        int ziel = -1;
        while (hn > 0) {
            int v = heap[0];
            pos[v] = -2;
            int last = heap[--hn];
            if (hn > 0) { heap[0] = last; pos[last] = 0; runter(heap, pos, hn, t, 0); }
            if (sicherWeg[v]) { ziel = v; break; }
            if (t[v] - anl[v] > LAND_MAX_S) continue;
            for (int e = w.aus[v]; e < w.aus[v + 1]; e++) {
                int x = w.nach[e];
                if (pos[x] == -2) continue;
                double d = t[v] + w.cost[e];
                if (!(d < frontWeg[x]) && !Double.isNaN(frontWeg[x])) continue;
                if (d < t[x]) {
                    t[x] = d;
                    anl[x] = anl[v];
                    vor[x] = e;
                    if (pos[x] < 0) { if (hn == heap.length) heap = Arrays.copyOf(heap, hn * 2); heap[hn] = x; pos[x] = hn++; }
                    hoch(heap, pos, t, pos[x]);
                }
            }
        }
        if (ziel < 0) return new Ergebnis(delta, false, -1, -1, Double.NaN, -1, Double.NaN, Double.NaN, vor, t, f);
        // der Weg zurück bis zum Strand, Reserve = kleinster Abstand zur Front
        double res = Double.POSITIVE_INFINITY;
        int k = ziel;
        while (true) {
            if (!Double.isNaN(frontWeg[k])) res = Math.min(res, frontWeg[k] - t[k]);
            if (vor[k] < 0) break;
            k = w.von[vor[k]];
        }
        return new Ergebnis(delta, true, von[k], k, t[k], ziel, t[ziel], res, vor, t, f);
    }

    static void hoch(int[] h, int[] pos, double[] t, int i) {
        int x = h[i];
        while (i > 0) { int p = (i - 1) >> 1; if (t[h[p]] <= t[x]) break; h[i] = h[p]; pos[h[i]] = i; i = p; }
        h[i] = x; pos[x] = i;
    }

    static void runter(int[] h, int[] pos, int hn, double[] t, int i) {
        int x = h[i];
        while (true) {
            int l = 2 * i + 1;
            if (l >= hn) break;
            int r = l + 1, c = r < hn && t[h[r]] < t[h[l]] ? r : l;
            if (t[h[c]] >= t[x]) break;
            h[i] = h[c]; pos[h[i]] = i; i = c;
        }
        h[i] = x; pos[x] = i;
    }

    /** Eigene Seite der Zweitrechnung: vom Knoten start zur Zeit t0 zu Fuß (vor der Front) zum ersten sicheren Knoten; {Zeit, Knoten} oder {NaN, −1}. */
    public static double[] sicherAb(WegNetz w, int start, double t0, double[] front, boolean[] sicher, double grenze) {
        int[] vor = new int[w.n];
        double[] t = w.dijkstraVorFront(start, t0, front, grenze, vor);
        double best = Double.POSITIVE_INFINITY;
        int bk = -1;
        for (int k = 0; k < w.n; k++) if (sicher[k] && !Double.isNaN(t[k]) && t[k] < best) { best = t[k]; bk = k; }
        return new double[]{bk < 0 ? Double.NaN : best, bk};
    }

    /** Front und Sicherheit je Knoten des Wegnetzes (Ebene 1; Ebene 2 ohne Front und nie sicher). */
    public static double[] frontJeKnoten(WegNetz w, Feld f, boolean[] sicher) {
        double[] fr = new double[w.n];
        for (int k = 0; k < w.n; k++) {
            if (w.ebene[k] != 1) { fr[k] = Double.NaN; continue; }
            float x = f.front()[w.zelle[k]];
            fr[k] = Float.isNaN(x) ? Double.NaN : x;
            sicher[k] = f.sicher()[w.zelle[k]];
        }
        return fr;
    }

    /** Die Umstiege (Index im Fahrwasser, Index im Wegnetz) aus umstieg.tsv. */
    public static int[][] umstieg(File daten) throws IOException {
        List<int[]> l = new ArrayList<>();
        for (String[] z : WellenNetz.tsv(new File(new File(daten, WegNetz.ORDNER), "umstieg.tsv"))) l.add(new int[]{Integer.parseInt(z[0]) - 1, Integer.parseInt(z[1]) - 1});
        return l.toArray(new int[0][]);
    }

    public static void rechnen(File daten, String lauf, Welle.Log log) throws Exception {
        long t0 = System.nanoTime();
        WegNetz w = WegNetz.lesen(daten);
        SeeNetz s = SeeNetz.lesen(daten);
        if (s.frontEreignis == null) throw new IllegalStateException("daten/netz/see ohne Front aus der Vorrechnung (ant aufbereitung -Dschritte=see)");
        Feld fd = feld(daten, lauf, Klassen.gelaende(daten, Klassen.KERN));
        boolean[] sicher = new boolean[w.n];
        double[] fw = frontJeKnoten(w, fd, sicher);
        int[][] um = umstieg(daten);
        double[] strom = SeeNetz.stromTabelle();
        File dir = new File(daten, WegNetz.ORDNER);
        byte[] sk = new byte[fd.sicher().length];
        for (int c = 0; c < sk.length; c++) sk[c] = (byte) (fd.sicher()[c] ? 1 : Float.isNaN(fd.stand()[c]) ? 0 : 2);
        Files.write(new File(dir, "sicher_kern_10m.u8").toPath(), sk);
        int nn = 0, ns = 0;
        for (int k = 0; k < w.n; k++) if (w.ebene[k] == 1) { if (!Double.isNaN(fw[k])) nn++; if (sicher[k]) ns++; }
        log.log("Landflucht (Lauf %s): %,d Knoten des Wegnetzes werden nass, %,d sicher (trocken und %.0f m über dem Auflauf bis %.0f m), %d Umstiege", lauf, nn, ns, RAND_M, UMKREIS_M, um.length);
        List<String> fl = new ArrayList<>(), wg = new ArrayList<>(), kopf = new ArrayList<>();
        fl.add("BOOT\tABFAHRT_S\tGERETTET\tSEE_NODE_ID\tWEG_NODE_ID\tAN_LAND_S\tSICHER_NODE_ID\tSICHER_S\tHOEHE\tRESERVE_S");
        wg.add("BOOT\tABFAHRT_S\tSEQ\tNETZ\tNODE_ID\tOST\tNORD\tHOEHE\tZEIT_S\tFRONT_S");
        for (Object[] p : SeeNetz.PLAETZE) {
            String boot = (String) p[0];
            int st = s.knotenBei((double) p[1], (double) p[2]);
            StringBuilder b = new StringBuilder();
            for (double dl : SeeNetz.ABFAHRT) {
                Ergebnis e = flucht(s, st, dl, strom, w, um, fw, sicher);
                fl.add(zeile(boot, e, w));
                b.append(e.gerettet() ? "J" : "·");
            }
            // späteste Abfahrt: in Schritten zu 15 s von spät nach früh, dann auf 1 s
            double sp = Double.NaN;
            for (double dl = 1200; dl >= -1800; dl -= 15) if (flucht(s, st, dl, strom, w, um, fw, sicher).gerettet()) { sp = dl; break; }
            if (!Double.isNaN(sp)) for (double dl = sp + 14; dl > sp; dl--) if (flucht(s, st, dl, strom, w, um, fw, sicher).gerettet()) { sp = dl; break; }
            String z;
            if (Double.isNaN(sp)) z = "keine (auch eine halbe Stunde vorher nicht)";
            else {
                Ergebnis e = flucht(s, st, sp, strom, w, um, fw, sicher);
                fl.add(zeile(boot, e, w).replace(boot + "\t", boot + "*\t"));
                weg(wg, boot, e, s, w, fw);
                z = String.format(Locale.GERMAN, "%+.0f s nach dem Stoß: an Land nach %.0f s bei E %.0f N %.0f, sicher nach %.0f s auf %.0f m (%.0f m zu Fuß), %s", sp, e.anLand(),
                        w.ost(e.wegStart()), w.nord(e.wegStart()), e.sicher(), w.hoehe[e.sicherKnoten()], laenge(e, w),
                        Double.isInfinite(e.reserve()) ? "die Front erreicht den Landweg nicht" : String.format(Locale.GERMAN, "Reserve vor der Front %.0f s", e.reserve()));
            }
            kopf.add(String.format(Locale.ROOT, "landflucht.%s=%s", boot.toLowerCase(Locale.ROOT), Double.isNaN(sp) ? "keine" : String.format(Locale.ROOT, "%.0f", sp)));
            kopf.add("landflucht." + boot.toLowerCase(Locale.ROOT) + ".text=" + z);
            log.log("  %-7s Abfahrt 0 … 180 s: %s · späteste Abfahrt %s", boot, b, z);
        }
        Files.write(new File(dir, "landflucht.tsv").toPath(), fl, StandardCharsets.UTF_8);
        Files.write(new File(dir, "landwege.tsv").toPath(), wg, StandardCharsets.UTF_8);
        List<String> t = new ArrayList<>();
        t.add("# Flucht an Land (Phase 16b): Boot durch LB_NET_SEE zum Strand (LB_WEG_UMSTIEG), Anlanden, zu Fuß durch LB_NET_WEG (Ebene 1) bis über den Auflauf; jeder Knoten vor der Front");
        t.add("front=Lauf " + fd.quelle() + "; ankunft_g* (1 m über dem Pegel), feinstes Gitter");
        t.add(String.format(Locale.ROOT, "sicher=trocken und %.0f m über dem höchsten Stand nasser Zellen bis %.0f m [G]", RAND_M, UMKREIS_M));
        t.add(String.format(Locale.ROOT, "anlanden=%.0f s [G]; Landweg höchstens %.0f s", ANLANDEN_S, LAND_MAX_S));
        t.add(String.format(Locale.ROOT, "knoten_nass=%d", nn));
        t.add(String.format(Locale.ROOT, "knoten_sicher=%d", ns));
        t.addAll(kopf);
        t.add("vergleich_see=" + SeeNetz.fluchtZeile(daten));
        Files.write(new File(dir, "LANDFLUCHT.txt").toPath(), t, StandardCharsets.UTF_8);
        log.log("  %s", SeeNetz.fluchtZeile(daten));
        log.log("  nach %s in %.1f s", dir, (System.nanoTime() - t0) / 1e9);
    }

    static String zeile(String boot, Ergebnis e, WegNetz w) {
        return String.format(Locale.ROOT, "%s\t%.0f\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s", boot, e.delta(), e.gerettet() ? "J" : "N",
                e.gerettet() ? String.valueOf(e.seeKnoten() + 1) : "", e.gerettet() ? String.valueOf(e.wegStart() + 1) : "", e.gerettet() ? String.format(Locale.ROOT, "%.1f", e.anLand()) : "",
                e.gerettet() ? String.valueOf(e.sicherKnoten() + 1) : "", e.gerettet() ? String.format(Locale.ROOT, "%.1f", e.sicher()) : "",
                e.gerettet() ? String.format(Locale.ROOT, "%.1f", w.hoehe[e.sicherKnoten()]) : "", e.gerettet() && Double.isFinite(e.reserve()) ? String.format(Locale.ROOT, "%.1f", e.reserve()) : "");
    }

    static double laenge(Ergebnis e, WegNetz w) {
        double L = 0;
        for (int k = e.sicherKnoten(); e.vorLand()[k] >= 0; k = w.von[e.vorLand()[k]]) { int a = w.von[e.vorLand()[k]]; L += Math.hypot(w.ost(k) - w.ost(a), w.nord(k) - w.nord(a)); }
        return L;
    }

    /** Der ganze Weg: Fahrwasser bis zum Umstieg, dann Land bis sicher. */
    static void weg(List<String> wg, String boot, Ergebnis e, SeeNetz s, WegNetz w, double[] fw) {
        List<String> z = new ArrayList<>();
        int v = e.seeKnoten();
        List<Integer> see = new ArrayList<>();
        while (v >= 0) { see.add(v); int ev = e.boot().vor()[v]; v = ev < 0 ? -1 : s.von[ev]; }
        java.util.Collections.reverse(see);
        int seq = 0;
        for (int k : see)
            z.add(String.format(Locale.ROOT, "%s\t%.0f\t%d\tSEE\t%d\t%.1f\t%.1f\t%s\t%.1f\t%s", boot, e.delta(), ++seq, k + 1, s.ost[k], s.nord[k], "", e.boot().ankunft()[k],
                    Double.isNaN(s.frontEreignis[k]) ? "" : String.format(Locale.ROOT, "%.1f", s.frontEreignis[k])));
        List<Integer> land = new ArrayList<>();
        for (int k = e.sicherKnoten(); ; k = w.von[e.vorLand()[k]]) { land.add(k); if (e.vorLand()[k] < 0) break; }
        java.util.Collections.reverse(land);
        for (int k : land)
            z.add(String.format(Locale.ROOT, "%s\t%.0f\t%d\tWEG\t%d\t%.1f\t%.1f\t%.1f\t%.1f\t%s", boot, e.delta(), ++seq, k + 1, w.ost(k), w.nord(k), w.hoehe[k], e.tLand()[k],
                    Double.isNaN(fw[k]) ? "" : String.format(Locale.ROOT, "%.1f", fw[k])));
        wg.addAll(z);
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        rechnen(new File(a.length > 0 ? a[0] : "daten"), a.length > 1 ? a[1] : "kette", (f, x) -> System.out.println(String.format(Locale.GERMAN, f, x)));
    }
}
