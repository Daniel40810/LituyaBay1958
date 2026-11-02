package com.dan.lituya.tools;

import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.UferNetz;
import com.dan.netview.Simplify;
import com.dan.route.Contour;
import com.dan.route.TravelGrid;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Schritt {@code ufer} der Aufbereitung (Phase 7a, Werkbuch 7.2): das Ufer als Messstrecke, Datei-Abbild
 * {@code daten/netz/ufer/} für {@code LB_NET_UFER} ({@link UferNetz} liest es).
 * <ul>
 * <li><b>Ufer heute</b> (Strang 1): der Rand der Wassermaske {@code grund/wasser_4m.u8} um die Bucht (Marching Squares
 * auf 4 m, Douglas-Peucker 2 m), ohne den Eingang; Maß m ab La Chaussee Spit im Uhrzeigersinn bis Harbor Point.</li>
 * <li><b>Cenotaph Island</b> (Strang 2): der Ring um die Insel, m ab dem nördlichsten Punkt im Uhrzeigersinn [G].</li>
 * <li><b>Ufer 1958</b>: noch nicht. Die Lotungen von 1940 liegen in der Bucht nur in Flecken (ihr Rand ist kein Ufer);
 * der Strang kommt mit dem Schritt {@code rueckbau1958} (Karte 1951/C-5, Gletscherfronten F32).</li>
 * <li><b>Netz</b>: Knoten an den Enden und alle 500 m, Kanten dazwischen mit Maß von bis. Der Ring ist in der Geometrie
 * geschlossen; im Netz sind Anfang (m = 0) und Ende (m = Länge) zwei Knoten am selben Ort, weil das Maß einer Kante
 * stetig steigen muss.</li>
 * <li><b>Auflauf</b> (Punkt-Ereignisse): die belegten Höhen aus Werkbuch 2.1 und 2.4 (Sporn 524 m und Südufer bei
 * Mudslide Creek 208 m 1958; Nordufer gegenüber Mudslide Creek 120 m 1853/54; NE-Wand des Crillon Inlet 150 m 1936), Lage
 * [G] auf das heutige Ufer projiziert. Die Spurlinie je Ufermeter kommt mit dem Schritt {@code spurlinie}.</li>
 * </ul>
 */
public final class Ufer {
    private Ufer() { }

    /** Ausschnitt der Bucht im 4-m-Raster (Ost, Nord). */
    static final double AE0 = 342_500, AE1 = 358_500, AN0 = 6_497_500, AN1 = 6_508_500;
    public static final double KNOTEN_ABSTAND = 500, GLAETTEN = 2;

    public static void rechnen(Rohdaten r) throws Exception { rechnen(r.daten, r::log); }

    public static UferNetz rechnen(File daten, Welle.Log log) throws Exception {
        long t0 = System.nanoTime();
        byte[] m = Files.readAllBytes(new File(daten, "grund/wasser_4m.u8").toPath());
        int W4 = 6000;
        double z4 = 4;
        int i0 = (int) ((AE0 - 340_000) / z4), i1 = (int) ((AE1 - 340_000) / z4), j0 = (int) ((6_512_000 - AN1) / z4), j1 = (int) ((6_512_000 - AN0) / z4);
        int w = i1 - i0, h = j1 - j0;
        // Bucht im 4-m-Raster: Wasser, das ohne den Eingang an der Bucht hängt (wie tools.Welle)
        boolean[] wasser = new boolean[w * h], tor = new boolean[w * h], bucht = new boolean[w * h];
        double ce = Site.CHAUSSEE.e(), cn = Site.CHAUSSEE.n(), he = Site.HARBOR_POINT.e(), hn = Site.HARBOR_POINT.n();
        double dl = Math.hypot(he - ce, hn - cn), ue = (he - ce) / dl, un = (hn - cn) / dl;
        double ae = ce - ue * Welle.EINGANG_DAZU, an = cn - un * Welle.EINGANG_DAZU, be = he + ue * Welle.EINGANG_DAZU, bn = hn + un * Welle.EINGANG_DAZU;
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                int k = j * w + i;
                wasser[k] = m[(j0 + j) * W4 + i0 + i] != 0;
                tor[k] = Welle.abstand(e(i0 + i), n(j0 + j), ae, an, be, bn) <= z4 * 0.75;
            }
        int si = (int) ((Site.CENOTAPH.e() - 340_000) / z4) - i0, sj = (int) ((6_512_000 - Site.CENOTAPH.n()) / z4) - j0;
        // Startpunkt: das Wasser, das Cenotaph Island am nächsten liegt (mitten in der Bucht)
        int start = -1;
        for (int rr = 0; rr < 400 && start < 0; rr++)
            for (int dj = -rr; dj <= rr && start < 0; dj++)
                for (int di = -rr; di <= rr && start < 0; di++) {
                    int ii = si + di, jj = sj + dj;
                    if (ii >= 0 && jj >= 0 && ii < w && jj < h && wasser[jj * w + ii] && !tor[jj * w + ii]) start = jj * w + ii;
                }
        int nb = Welle.flut(wasser, tor, bucht, List.of(new TravelGrid.Source(start % w, start / w, 0)), w, h);
        float[] f = new float[w * h];
        for (int k = 0; k < f.length; k++) f[k] = bucht[k] ? 1 : 0;
        // Linien auf den Zellmitten
        List<float[]> ls = Contour.lines(f, w, h, e(i0), n(j0), z4, 0.5);
        // Contour rechnet z = oz + j·schritt nach unten; Nord fällt mit j: umrechnen
        List<double[]> ringe = new ArrayList<>();
        for (float[] l : ls) {
            double[] en = new double[l.length];
            for (int k = 0; k < l.length; k += 2) { en[k] = l[k]; en[k + 1] = n(j0) - (l[k + 1] - n(j0)); }
            ringe.add(en);
        }
        // Hauptring: der längste; Insel: der Ring, der Cenotaph Island umschließt
        double[] haupt = null, insel = null;
        double hl = 0, best = Double.MAX_VALUE;
        for (double[] en : ringe) {
            double L = laenge(en);
            if (L > hl) { hl = L; haupt = en; }
        }
        for (double[] en : ringe) {
            if (en == haupt || laenge(en) < 1000) continue;
            if (innen(en, Site.CENOTAPH.e(), Site.CENOTAPH.n())) { double a = Math.abs(flaeche(en)); if (a < best) { best = a; insel = en; } }
        }
        if (haupt == null) throw new IllegalStateException("ufer: kein Rand der Bucht gefunden");
        // den Eingang herausschneiden: die Punkte im Tor entfernen, der Ring zerfällt in einen Zug
        double[] ufer = ohneTor(haupt, ae, an, be, bn, z4 * 1.5);
        // Anfang bei La Chaussee Spit
        if (Math.hypot(ufer[0] - ce, ufer[1] - cn) > Math.hypot(ufer[ufer.length - 2] - ce, ufer[ufer.length - 1] - cn)) ufer = umdrehen(ufer);
        ufer = glatt(ufer, GLAETTEN);
        // Uhrzeigersinn um die Bucht: das Wasser liegt rechts. Prüfen über die Fläche des geschlossenen Zugs (E, N): negativ.
        double richtung = flaeche(ufer);
        double[] ring = insel == null ? null : glatt(inselRing(insel), GLAETTEN);
        File dir = new File(daten, UferNetz.ORDNER);
        dir.mkdirs();
        List<String> st = new ArrayList<>(), pk = new ArrayList<>(), kn = new ArrayList<>(), ka = new ArrayList<>(), au = new ArrayList<>();
        st.add("STRANG_ID\tNAME\tRING\tLAENGE\tPUNKTE");
        pk.add("STRANG_ID\tSEQ\tOST\tNORD\tM");
        kn.add("NODE_ID\tSTRANG_ID\tM\tOST\tNORD\tNAME");
        ka.add("LINK_ID\tSTART_NODE_ID\tEND_NODE_ID\tSTRANG_ID\tSTART_M\tEND_M\tLAENGE");
        int[] nid = {0}, lid = {0};
        strang(1, "Ufer heute", false, ufer, st, pk, kn, ka, nid, lid);
        if (ring != null) strang(2, "Cenotaph Island", true, ring, st, pk, kn, ka, nid, lid);
        Files.write(new File(dir, "straenge.tsv").toPath(), st, StandardCharsets.UTF_8);
        Files.write(new File(dir, "punkte.tsv").toPath(), pk, StandardCharsets.UTF_8);
        Files.write(new File(dir, "knoten.tsv").toPath(), kn, StandardCharsets.UTF_8);
        Files.write(new File(dir, "kanten.tsv").toPath(), ka, StandardCharsets.UTF_8);
        // Auflauf: auf das heutige Ufer projizieren
        au.add("STRANG_ID\tM\tHOEHE\tJAHR\tNAME\tQUELLE\tART\tOST\tNORD");
        Files.write(new File(dir, "auflauf.tsv").toPath(), au, StandardCharsets.UTF_8);
        au.clear();
        UferNetz u = UferNetz.lesen(daten);
        au.add("STRANG_ID\tM\tHOEHE\tJAHR\tNAME\tQUELLE\tART\tOST\tNORD");
        Object[][] belegt = {
                {"Sporn gegenüber dem Felssturz", Site.SPORN.e(), Site.SPORN.n(), 524.0, 1958, "MILLER60, FRITZ01"},
                {"Südufer bei Mudslide Creek", Site.MUDSLIDE.e(), Site.MUDSLIDE.n(), 208.0, 1958, "MILLER60"},
                {"Nordufer gegenüber Mudslide Creek", Site.MUDSLIDE.e(), Site.MUDSLIDE.n() + 2200, 120.0, 1853, "MILLER60 über FRITZ01"},
                {"NE-Wand des Crillon Inlet", Site.CRILLON_INLET.e() + 400, Site.CRILLON_INLET.n() + 300, 150.0, 1936, "MILLER60"},
        };
        for (Object[] b : belegt) {
            double[] p = u.projektion(1, (double) b[1], (double) b[2]);
            double[] q = u.punkt(1, p[0]);
            au.add(String.format(Locale.ROOT, "1\t%.2f\t%.0f\t%d\t%s\t%s\t%s\t%.2f\t%.2f", p[0], (double) b[3], (int) b[4], b[0], b[5], "belegt, Lage [G]", q[0], q[1]));
        }
        Files.write(new File(dir, "auflauf.tsv").toPath(), au, StandardCharsets.UTF_8);
        Files.writeString(new File(dir, "UFER.txt").toPath(), String.format(Locale.ROOT, """
                # LB_NET_UFER als Datei (Phase 7a, Werkbuch 7.2): Rand von grund/wasser_4m.u8 um die Bucht, Douglas-Peucker %.0f m
                ufer_heute=%.1f m, %d Punkte, von La Chaussee Spit im Uhrzeigersinn (Fläche %.0f m², negativ = im Uhrzeigersinn)
                cenotaph=%s
                knoten=%d kanten=%d (alle %.0f m)
                bucht_4m=%d Zellen
                ufer_1958=fehlt (Schritt rueckbau1958)
                """, GLAETTEN, laenge(ufer), ufer.length / 2, richtung, ring == null ? "nicht gefunden" : String.format(Locale.ROOT, "%.1f m, %d Punkte", laenge(ring), ring.length / 2),
                nid[0], lid[0], KNOTEN_ABSTAND, nb), StandardCharsets.UTF_8);
        u = UferNetz.lesen(daten);
        log.log("  ufer: %s; Bucht %.1f km² auf 4 m; in %.1f s", u, nb * 16 / 1e6, (System.nanoTime() - t0) / 1e9);
        for (UferNetz.Auflauf a : u.auflauf) log.log("    Auflauf %d m (%d) bei m = %,.0f: %s", Math.round(a.hoehe()), a.jahr(), a.m(), a.name());
        return u;
    }

    static double e(int i) { return 340_000 + (i + 0.5) * 4; }
    static double n(int j) { return 6_512_000 - (j + 0.5) * 4; }

    /** Schreibt einen Strang: Punkte mit Maß, Knoten alle 500 m und an den Enden, Kanten dazwischen. */
    static void strang(int id, String name, boolean ring, double[] en, List<String> st, List<String> pk, List<String> kn, List<String> ka, int[] nid, int[] lid) {
        double m = 0;
        int n = en.length / 2;
        double[] mm = new double[n];
        for (int k = 0; k < n; k++) {
            if (k > 0) m += Math.hypot(en[2 * k] - en[2 * k - 2], en[2 * k + 1] - en[2 * k - 1]);
            mm[k] = m;
            pk.add(String.format(Locale.ROOT, "%d\t%d\t%.2f\t%.2f\t%.3f", id, k + 1, en[2 * k], en[2 * k + 1], m));
        }
        double L = m;
        st.add(String.format(Locale.ROOT, "%d\t%s\t%s\t%.3f\t%d", id, name, ring ? "J" : "N", L, n));
        int stuecke = Math.max(1, (int) Math.round(L / KNOTEN_ABSTAND));
        int letzter = 0;
        for (int s = 0; s <= stuecke; s++) {
            double ms = L * s / stuecke;
            double[] p = bei(en, mm, ms);
            letzter = ++nid[0];
            kn.add(String.format(Locale.ROOT, "%d\t%d\t%.3f\t%.2f\t%.2f\t%s", letzter, id, ms, p[0], p[1], s == 0 ? name + " Anfang" : s == stuecke ? name + " Ende" : ""));
            if (s > 0) ka.add(String.format(Locale.ROOT, "%d\t%d\t%d\t%d\t%.3f\t%.3f\t%.3f", ++lid[0], letzter - 1, letzter, id, L * (s - 1) / stuecke, ms, ms - L * (s - 1) / stuecke));
        }
    }

    static double[] bei(double[] en, double[] mm, double m) {
        int k = 1;
        while (k < mm.length - 1 && mm[k] < m) k++;
        double t = (m - mm[k - 1]) / Math.max(1e-9, mm[k] - mm[k - 1]);
        return new double[]{en[2 * k - 2] + (en[2 * k] - en[2 * k - 2]) * t, en[2 * k - 1] + (en[2 * k + 1] - en[2 * k - 1]) * t};
    }

    /** Ein geschlossener Ring ohne die Punkte in der Nähe des Tors, als ein offener Zug (der längste Rest). */
    static double[] ohneTor(double[] ring, double ae, double an, double be, double bn, double d) {
        int n = ring.length / 2;
        if (ring[0] == ring[ring.length - 2] && ring[1] == ring[ring.length - 1]) n--;
        boolean[] weg = new boolean[n];
        int frei = -1;
        for (int k = 0; k < n; k++) { weg[k] = Welle.abstand(ring[2 * k], ring[2 * k + 1], ae, an, be, bn) <= d; if (!weg[k] && frei < 0) frei = k; }
        // vom ersten Punkt hinter einem weggefallenen an rundherum sammeln, längstes Stück behalten
        int s0 = -1;
        for (int k = 0; k < n; k++) if (weg[k] && !weg[(k + 1) % n]) { s0 = (k + 1) % n; break; }
        if (s0 < 0) return ring;
        List<double[]> best = new ArrayList<>(), cur = new ArrayList<>();
        for (int c = 0; c < n; c++) {
            int k = (s0 + c) % n;
            if (weg[k]) { if (cur.size() > best.size()) best = cur; cur = new ArrayList<>(); } else cur.add(new double[]{ring[2 * k], ring[2 * k + 1]});
        }
        if (cur.size() > best.size()) best = cur;
        double[] out = new double[2 * best.size()];
        for (int k = 0; k < best.size(); k++) { out[2 * k] = best.get(k)[0]; out[2 * k + 1] = best.get(k)[1]; }
        return out;
    }

    /** Insel als Ring ab dem nördlichsten Punkt, im Uhrzeigersinn, geschlossen. */
    static double[] inselRing(double[] r) {
        int n = r.length / 2;
        if (r[0] == r[r.length - 2] && r[1] == r[r.length - 1]) n--;
        int top = 0;
        for (int k = 1; k < n; k++) if (r[2 * k + 1] > r[2 * top + 1]) top = k;
        double[] out = new double[2 * (n + 1)];
        for (int c = 0; c <= n; c++) { int k = (top + c) % n; out[2 * c] = r[2 * k]; out[2 * c + 1] = r[2 * k + 1]; }
        if (flaeche(out) > 0) {   // gegen den Uhrzeigersinn: umdrehen, Anfang bleibt oben
            double[] u = umdrehen(out);
            return u;
        }
        return out;
    }

    static double[] umdrehen(double[] a) {
        double[] b = new double[a.length];
        for (int k = 0; k < a.length / 2; k++) { b[2 * k] = a[a.length - 2 - 2 * k]; b[2 * k + 1] = a[a.length - 1 - 2 * k]; }
        return b;
    }

    /** Douglas-Peucker (über com.dan.netview.Simplify), Enden bleiben. */
    static double[] glatt(double[] en, double tol) {
        float[] xyz = new float[en.length / 2 * 3];
        for (int k = 0; k < en.length / 2; k++) { xyz[3 * k] = (float) (en[2 * k] - Site.E0); xyz[3 * k + 2] = (float) (en[2 * k + 1] - Site.N0); }
        int[] keep = Simplify.keep(xyz, tol);
        double[] out = new double[2 * keep.length];
        for (int k = 0; k < keep.length; k++) { out[2 * k] = en[2 * keep[k]]; out[2 * k + 1] = en[2 * keep[k] + 1]; }
        return out;
    }

    static double laenge(double[] en) {
        double L = 0;
        for (int k = 2; k < en.length; k += 2) L += Math.hypot(en[k] - en[k - 2], en[k + 1] - en[k - 1]);
        return L;
    }

    /** Fläche nach der Schnürsenkelformel (E, N; der Zug wird geschlossen gedacht); negativ im Uhrzeigersinn. */
    static double flaeche(double[] en) {
        double a = 0;
        int n = en.length / 2;
        for (int k = 0; k < n; k++) { int q = (k + 1) % n; a += en[2 * k] * en[2 * q + 1] - en[2 * q] * en[2 * k + 1]; }
        return a / 2;
    }

    static boolean innen(double[] en, double x, double y) {
        boolean in = false;
        int n = en.length / 2;
        for (int k = 0, q = n - 1; k < n; q = k++) {
            double xi = en[2 * k], yi = en[2 * k + 1], xj = en[2 * q], yj = en[2 * q + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    public static void main(String[] a) throws Exception {
        rechnen(new File(a.length > 0 ? a[0] : "daten"), (f, x) -> System.out.println(String.format(Locale.GERMAN, f, x)));
    }
}
