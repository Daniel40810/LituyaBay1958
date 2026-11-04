package com.dan.lituya.scene;

import com.dan.vessel.Water;

import java.io.File;
import java.io.IOException;

/**
 * Die Vorrechnung eines Laufs ({@code daten/ereignis/<lauf>}) als Wasser für {@code com.dan.vessel} (Phase 15a): Höhe und
 * Strömung aus den Bildern, im Ort bilinear, in der Zeit linear zwischen den Bildern, aus dem feinsten Gitter, das den Ort
 * hat; der Grund 1958 aus {@link Boden1958}. Achsen der Szene (x Ost, z Süd). Vor dem ersten Bild steht das Wasser auf dem
 * Pegel des Laufs, nach dem letzten bleibt das letzte. Ohne Strömung im Lauf (vor 15a gerechnet) ist sie 0, und
 * {@link #mitStroemung} sagt es.
 */
public final class VorrechnungWasser implements Water, AutoCloseable {
    final EreignisDaten.Leser le;
    final Boden1958 boden;
    public final double pegel;
    public final boolean mitStroemung;
    private final int n;
    private final float[][] e0, e1, u0, u1, v0, v1;
    private final int[] b0;
    private final double[] a;
    private double t = Double.NaN;

    public VorrechnungWasser(File daten, String lauf, Boden1958 boden) throws IOException {
        le = new EreignisDaten.Leser(EreignisDaten.ordner(daten, lauf));
        this.boden = boden;
        String p = le.p.getProperty("pegel_m");
        pegel = p == null ? 0 : Double.parseDouble(p.trim());
        mitStroemung = le.hatStroemung();
        n = le.gitter.size();
        e0 = new float[n][]; e1 = new float[n][]; u0 = new float[n][]; u1 = new float[n][]; v0 = new float[n][]; v1 = new float[n][];
        b0 = new int[n];
        java.util.Arrays.fill(b0, -2);
        a = new double[n];
    }

    /** Erste und letzte Ereigniszeit der Bilder (s nach dem ersten Stoß). */
    public double[] spanne() {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int k = 0; k < n; k++) { double[] z = le.zeiten(k); if (z.length > 0) { lo = Math.min(lo, z[0]); hi = Math.max(hi, z[z.length - 1]); } }
        return new double[]{lo, hi};
    }

    /** Stellt die Ereigniszeit (s nach dem ersten Stoß) ein; lädt die zwei Bilder je Gitter, zwischen denen sie liegt. */
    public synchronized void zeit(double tt) throws IOException {
        if (tt == t) return;
        t = tt;
        for (int k = 0; k < n; k++) {
            double[] z = le.zeiten(k);
            if (z.length == 0) continue;
            int b;
            if (tt <= z[0]) { b = -1; a[k] = 0; }
            else if (tt >= z[z.length - 1]) { b = z.length - 2; a[k] = 1; }
            else {
                b = java.util.Arrays.binarySearch(z, tt);
                if (b < 0) b = -b - 2;
                b = Math.min(b, z.length - 2);
                a[k] = (tt - z[b]) / (z[b + 1] - z[b]);
            }
            if (b == -1) { e0[k] = e1[k] = null; b0[k] = -1; continue; }
            if (b != b0[k]) {
                if (b == b0[k] + 1 && e1[k] != null) { e0[k] = e1[k]; u0[k] = u1[k]; v0[k] = v1[k]; }
                else { e0[k] = le.bild(k, b); float[][] g = le.geschw(k, b); u0[k] = g == null ? null : g[0]; v0[k] = g == null ? null : g[1]; }
                e1[k] = le.bild(k, b + 1);
                float[][] g = le.geschw(k, b + 1);
                u1[k] = g == null ? null : g[0];
                v1[k] = g == null ? null : g[1];
                b0[k] = b;
            }
        }
    }

    /** Bilinear im Gitter k bei (E, N) aus dem Feld f; NaN-Zellen zählen nicht (alle NaN: NaN). */
    static double bilinear(EreignisDaten.Gitter g, float[] f, double e, double nn) {
        double fi = (e - g.e0()) / g.zelle() - 0.5, fj = (nn - g.n0()) / g.zelle() - 0.5;
        int i = (int) Math.floor(fi), j = (int) Math.floor(fj);
        double s = 0, w = 0;
        for (int dj = 0; dj <= 1; dj++)
            for (int di = 0; di <= 1; di++) {
                int ii = Math.max(0, Math.min(g.nx() - 1, i + di)), jj = Math.max(0, Math.min(g.nz() - 1, j + dj));
                float v = f[jj * g.nx() + ii];
                if (Float.isNaN(v)) continue;
                double ww = (di == 0 ? 1 - (fi - i) : fi - i) * (dj == 0 ? 1 - (fj - j) : fj - j);
                s += v * ww;
                w += ww;
            }
        return w > 1e-6 ? s / w : Double.NaN;
    }

    private int gitter(double e, double nn) {
        for (int k = n - 1; k >= 0; k--) if (le.gitter.get(k).enthaelt(e, nn) && b0[k] != -2) return k;
        return -1;
    }

    @Override public synchronized double height(double x, double z) {
        double e = x + Site.E0, nn = Site.N0 - z, b = boden.hoehe(e, nn);
        int k = gitter(e, nn);
        if (k < 0 || e0[k] == null) return Math.max(pegel, Double.isNaN(b) ? pegel : b);
        EreignisDaten.Gitter g = le.gitter.get(k);
        double h0 = bilinear(g, e0[k], e, nn), h1 = bilinear(g, e1[k], e, nn);
        double bb = Double.isNaN(b) ? pegel : b;
        if (Double.isNaN(h0)) h0 = bb;
        if (Double.isNaN(h1)) h1 = bb;
        double h = h0 + (h1 - h0) * a[k];
        return Math.max(h, bb);
    }

    @Override public synchronized double[] velocity(double x, double z) {
        double e = x + Site.E0, nn = Site.N0 - z;
        int k = gitter(e, nn);
        if (k < 0 || u0[k] == null || u1[k] == null) return new double[2];
        EreignisDaten.Gitter g = le.gitter.get(k);
        double uu0 = bilinear(g, u0[k], e, nn), uu1 = bilinear(g, u1[k], e, nn), vv0 = bilinear(g, v0[k], e, nn), vv1 = bilinear(g, v1[k], e, nn);
        double u = (Double.isNaN(uu0) ? 0 : uu0) * (1 - a[k]) + (Double.isNaN(uu1) ? 0 : uu1) * a[k];
        double v = (Double.isNaN(vv0) ? 0 : vv0) * (1 - a[k]) + (Double.isNaN(vv1) ? 0 : vv1) * a[k];
        return new double[]{u, -v};   // Szene: z nach Süd
    }

    @Override public double bed(double x, double z) {
        double b = boden.hoehe(x + Site.E0, Site.N0 - z);
        return Double.isNaN(b) ? -1e4 : b;
    }

    @Override public void close() throws IOException { le.close(); }
}
