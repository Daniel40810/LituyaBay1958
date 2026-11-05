package com.dan.lituya.scene;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Das Band der Welle von 1958 an Land (Phase 14a, Werkbuch 5.6, 9.7): die Spurlinie als höchster Stand längs des Ufers
 * und daraus der höchste Wasserstand an jedem Ort darunter. Quelle ist die gerechnete Spurlinie der Kette
 * ({@code daten/netz/ufer/spurlinie_kette.tsv}, Phase 13a, alle 50 m: Auflauf und Ankunft), {@link #gezogen} auf die
 * belegten Aufläufe 1958 ({@code auflauf.tsv}: Sporn 524 m, Mudslide Creek 208 m): Faktor s = Beleg / gerechnet am
 * Belegort (größter Wert der Kette in ±300 m), dazwischen längs des Ufers linear, außerhalb gleich, Cenotaph Island mit
 * dem Mittel [G]. Ohne {@link #gezogen} gilt die Kette rein gerechnet (s = 1).
 * <p>
 * Ein Ort gehört zum Band, wenn sein Grund unter der Spurlinie des nächsten Uferpunkts liegt (bis {@link #WEIT} m vom
 * Ufer; über flachem Land sinkt die Spurlinie mit der Entfernung, siehe {@link #spur}). Der höchste Wasserstand dort ist g + 0,35·(T − g) + 0,5 m [G]: über dem Hang dünner werdend bis zur Spurlinie,
 * wie in der Vorführung von Phase 14. Die Ankunft je Ort ist die des nächsten Uferpunkts (s nach dem Eintauchen).
 */
public final class Band1958 {
    /** Weiteste Entfernung vom Ufer (m), bis zu der ein Ort zum Band gehören kann. */
    public static final double WEIT = 2000;
    /** Zelle des Rasters „nächster Uferpunkt“ (m) über den Kern. */
    public static final double ZELLE = 20;
    /** Anteil der Strecke bis zur Spurlinie, um den der Stand über dem Grund liegt, und Mindesttiefe (m) [G]. */
    public static final double ANTEIL = 0.35, MINDEST = 0.5;
    /** Reichweite über flachem Land: die Spurlinie T fällt bis REICH + REICH_JE·T m vom Ufer auf 0 [G]. */
    public static final double REICH = 300, REICH_JE = 10;
    /** Eintauchen des Felssturzes in Sekunden nach dem ersten Stoß (Ereignisuhr, Phase 13). */
    public static final double EINTAUCHEN = 105;

    public final boolean gezogen;
    /** Faktoren am Sporn und bei Mudslide Creek (Beleg / gerechnet), Cenotaph Island. */
    public final double faktorSporn, faktorMudslide, faktorInsel;
    public final double mSporn, mMudslide;
    public final String quelle;

    private final int np;
    private final double[] pe, pn, pt, pa;
    private final int[] naechst;
    private final int rw, rh;
    private final double re0 = Site.KERN.eMin(), rn1 = Site.KERN.nMax();
    private double eMin = Double.MAX_VALUE, eMax = -Double.MAX_VALUE, nMin = Double.MAX_VALUE, nMax = -Double.MAX_VALUE;

    /** Liest {@code daten/netz/ufer} (Punkte, Spurlinie der Kette, Aufläufe). */
    public Band1958(File daten, boolean gezogen) throws IOException {
        this.gezogen = gezogen;
        File u = new File(daten, "netz/ufer");
        List<double[]> pk = tsv(new File(u, "punkte.tsv"));
        List<double[]> kt = tsv(new File(u, "spurlinie_kette.tsv"));
        if (pk.isEmpty() || kt.isEmpty()) throw new IOException("daten/netz/ufer: punkte.tsv oder spurlinie_kette.tsv fehlt (ant aufbereitung -Dschritte=ufer, ant ereignis)");
        double ms = Double.NaN, mm = Double.NaN, hs = 524, hm = 208;
        File af = new File(u, "auflauf.tsv");
        if (af.isFile()) for (String z : Files.readAllLines(af.toPath(), StandardCharsets.UTF_8)) {
            String[] f = z.split("\t");
            if (f.length < 4 || !f[3].trim().equals("1958")) continue;
            double m = Double.parseDouble(f[1]), h = Double.parseDouble(f[2]);
            if (h >= 400) { ms = m; hs = h; } else { mm = m; hm = h; }
        }
        if (Double.isNaN(ms)) ms = 14_255.99;
        if (Double.isNaN(mm)) mm = 24_371.31;
        mSporn = ms;
        mMudslide = mm;
        // Kette je Strang nach M
        double fs = hs / Math.max(1, kmax(kt, 1, ms, 300)), fm = hm / Math.max(1, kmax(kt, 1, mm, 300));
        faktorSporn = gezogen ? fs : 1;
        faktorMudslide = gezogen ? fm : 1;
        faktorInsel = gezogen ? (fs + fm) / 2 : 1;
        np = pk.size();
        pe = new double[np]; pn = new double[np]; pt = new double[np]; pa = new double[np];
        for (int p = 0; p < np; p++) {
            double[] r = pk.get(p);
            int st = (int) r[0];
            double m = r[4];
            pe[p] = r[2];
            pn[p] = r[3];
            double s = st != 1 ? faktorInsel : m <= ms ? faktorSporn : m >= mm ? faktorMudslide : faktorSporn + (faktorMudslide - faktorSporn) * (m - ms) / (mm - ms);
            pt[p] = s * kmax(kt, st, m, 150);
            pa[p] = kna(kt, st, m);
            if (pt[p] > 0) {
                eMin = Math.min(eMin, pe[p]); eMax = Math.max(eMax, pe[p]);
                nMin = Math.min(nMin, pn[p]); nMax = Math.max(nMax, pn[p]);
            }
        }
        // Raster: nächster Uferpunkt je Zelle (über Eimer von 200 m)
        rw = (int) Math.round(Site.KERN.breite() / ZELLE);
        rh = (int) Math.round(Site.KERN.hoehe() / ZELLE);
        naechst = new int[rw * rh];
        final double B = 200;
        int bw = (int) Math.ceil(Site.KERN.breite() / B), bh = (int) Math.ceil(Site.KERN.hoehe() / B);
        int[] cnt = new int[bw * bh + 1];
        int[] bi = new int[np];
        for (int p = 0; p < np; p++) {
            int i = (int) Math.floor((pe[p] - re0) / B), j = (int) Math.floor((rn1 - pn[p]) / B);
            bi[p] = i < 0 || j < 0 || i >= bw || j >= bh ? -1 : j * bw + i;
            if (bi[p] >= 0) cnt[bi[p] + 1]++;
        }
        for (int c = 0; c < bw * bh; c++) cnt[c + 1] += cnt[c];
        int[] fill = Arrays.copyOf(cnt, cnt.length), idx = new int[np];
        for (int p = 0; p < np; p++) if (bi[p] >= 0) idx[fill[bi[p]]++] = p;
        int maxRing = (int) Math.ceil(WEIT / B) + 1;
        for (int j = 0; j < rh; j++)
            for (int i = 0; i < rw; i++) {
                double e = re0 + (i + 0.5) * ZELLE, n = rn1 - (j + 0.5) * ZELLE;
                int ci = (int) ((i + 0.5) * ZELLE / B), cj = (int) ((j + 0.5) * ZELLE / B);
                int best = -1;
                double bd = WEIT * WEIT;
                for (int r = 0; r <= maxRing; r++) {
                    if (best >= 0 && (r - 1) * B > Math.sqrt(bd)) break;
                    for (int dj = -r; dj <= r; dj++)
                        for (int di = -r; di <= r; di++) {
                            if (Math.max(Math.abs(di), Math.abs(dj)) != r) continue;
                            int a = ci + di, b = cj + dj;
                            if (a < 0 || b < 0 || a >= bw || b >= bh) continue;
                            int c = b * bw + a;
                            for (int q = cnt[c]; q < cnt[c + 1]; q++) {
                                int p = idx[q];
                                double d = (pe[p] - e) * (pe[p] - e) + (pn[p] - n) * (pn[p] - n);
                                if (d < bd) { bd = d; best = p; }
                            }
                        }
                }
                naechst[j * rw + i] = best;
            }
        String q = gezogen
                ? String.format(Locale.GERMAN, "Spurlinie der Kette auf die Belege gezogen: Sporn ×%.2f (524 m), Mudslide Creek ×%.2f (208 m), Cenotaph ×%.2f [G]", fs, fm, (fs + fm) / 2)
                : "Spurlinie der Kette rein gerechnet (Belege nicht eingerechnet)";
        int nf = feinLesen(new File(daten, "ereignis/kette"));
        quelle = nf > 0 ? q + String.format(Locale.GERMAN, " · W5: Ankunft und Auflauf aus %d Gittern der Vorrechnung", nf) : q;
    }

    // ------------------------------------------------------------------ Werkbuch Welle W5: die Gitter der Vorrechnung

    /**
     * W5 „524 m“: Die Spurlinie oben ist die des nächsten Uferpunkts (alle 50 m längs des Ufers), und jeder Ort im Band
     * bekam die Ankunft dieses Uferpunkts. Am Sporn schaltete damit der ganze Hang auf einmal um, und über rund 300–460 m
     * blieb der Wald stehen, obwohl die Vorrechnung (Gitter 2 m) dort bis 540 m nass ist. Wo ein Gitter der Vorrechnung
     * (kette: 2 m, 4 m, 16 m, feinstes zuerst) den Ort nass hat, gelten jetzt dessen Ankunft und höchster Stand: der Wald
     * knickt in Reihen, wie die Zunge den Hang hinaufläuft, und das Band reicht bis zur gerechneten Spurlinie.
     */
    private float[][] fMax, fAn;
    private double[][] fGeo;   // je Gitter e0, n0, Zelle, nx, nz

    private int feinLesen(File dir) {
        File txt = new File(dir, "EREIGNIS.txt");
        if (!txt.isFile()) return 0;
        try {
            java.util.Properties p = new java.util.Properties();
            try (var r = Files.newBufferedReader(txt.toPath(), StandardCharsets.UTF_8)) { p.load(r); }
            int n = Integer.parseInt(p.getProperty("gitter", "0").trim());
            List<float[]> mx = new ArrayList<>(), an = new ArrayList<>();
            List<double[]> geo = new ArrayList<>();
            for (int k = n - 1; k >= 0; k--) {   // feinstes zuerst
                File fm = new File(dir, "max_g" + k + ".r32"), fa = new File(dir, "ankunft_g" + k + ".r32");
                if (!fm.isFile() || !fa.isFile()) continue;
                double[] g = {Double.parseDouble(p.getProperty("g" + k + ".e0").trim()), Double.parseDouble(p.getProperty("g" + k + ".n0").trim()),
                        Double.parseDouble(p.getProperty("g" + k + ".zelle").trim()), Integer.parseInt(p.getProperty("g" + k + ".nx").trim()), Integer.parseInt(p.getProperty("g" + k + ".nz").trim())};
                mx.add(r32(fm));
                an.add(r32(fa));
                geo.add(g);
            }
            fMax = mx.toArray(new float[0][]);
            fAn = an.toArray(new float[0][]);
            fGeo = geo.toArray(new double[0][]);
            return fMax.length;
        } catch (Exception e) {
            System.err.println("LituyaBay1958: Band1958 ohne Gitter der Vorrechnung: " + e);
            fMax = null;
            return 0;
        }
    }

    static float[] r32(File f) throws IOException {
        byte[] b = Files.readAllBytes(f.toPath());
        float[] v = new float[b.length / 4];
        java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(v);
        return v;
    }

    /** W5: Index der Zelle im feinsten Gitter, das den Ort nass hat ({gitter, zelle}), sonst null. */
    private int[] fein(double e, double n) {
        if (fMax == null) return null;
        for (int k = 0; k < fMax.length; k++) {
            double[] g = fGeo[k];
            int i = (int) Math.floor((e - g[0]) / g[2]), j = (int) Math.floor((n - g[1]) / g[2]);
            if (i < 0 || j < 0 || i >= (int) g[3] || j >= (int) g[4]) continue;
            int c = j * (int) g[3] + i;
            if (Float.isNaN(fMax[k][c]) || Float.isNaN(fAn[k][c])) return null;   // das feinste Gitter hier sagt: trocken
            return new int[]{k, c};
        }
        return null;
    }

    /** W5: höchster Stand der Vorrechnung am Ort (m über NAVD88), NaN trocken oder außerhalb der Gitter. */
    public double standFein(double e, double n) { int[] f = fein(e, n); return f == null ? Double.NaN : fMax[f[0]][f[1]]; }

    /** W5: Ankunft der Vorrechnung am Ort (Ereigniszeit, s), NaN trocken oder außerhalb der Gitter. */
    public double ankunftFein(double e, double n) { int[] f = fein(e, n); return f == null ? Double.NaN : fAn[f[0]][f[1]]; }

    /** Zeilen einer TSV-Datei mit Kopfzeile als Zahlen (leere Felder NaN, Kommentare weg); nur numerische Spalten. */
    static List<double[]> tsv(File f) throws IOException {
        List<double[]> l = new ArrayList<>();
        if (!f.isFile()) return l;
        List<String> z = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        for (int k = 1; k < z.size(); k++) {
            String s = z.get(k);
            if (s.isBlank() || s.startsWith("#")) continue;
            String[] a = s.split("\t", -1);
            double[] r = new double[a.length];
            for (int c = 0; c < a.length; c++) {
                try { r[c] = a[c].isBlank() ? Double.NaN : Double.parseDouble(a[c].trim()); } catch (NumberFormatException e) { r[c] = Double.NaN; }
            }
            l.add(r);
        }
        return l;
    }

    /** Größter Auflauf der Kette auf Strang st in M ± w (0, wenn nichts). Spalten: STRANG, M, OST, NORD, AUFLAUF, ANKUNFT. */
    static double kmax(List<double[]> kt, int st, double m, double w) {
        double best = 0;
        for (double[] r : kt) if ((int) r[0] == st && Math.abs(r[1] - m) <= w && r.length > 4 && r[4] > best) best = r[4];
        return best;
    }

    /** Ankunft der Kette am nächsten Punkt des Strangs (s nach dem Eintauchen, NaN wenn keine). */
    static double kna(List<double[]> kt, int st, double m) {
        double best = Double.NaN, bd = Double.MAX_VALUE;
        for (double[] r : kt) {
            if ((int) r[0] != st || r.length < 6 || Double.isNaN(r[5])) continue;
            double d = Math.abs(r[1] - m);
            if (d < bd) { bd = d; best = r[5]; }
        }
        return bd <= 400 ? best : Double.NaN;
    }

    private int punkt(double e, double n) {
        int i = (int) Math.floor((e - re0) / ZELLE), j = (int) Math.floor((rn1 - n) / ZELLE);
        if (i < 0 || j < 0 || i >= rw || j >= rh) return -1;
        return naechst[j * rw + i];
    }

    /**
     * Spurlinie (m über NAVD88) für den Ort: die des nächsten Uferpunkts, über flachem Land mit der Entfernung vom Ufer
     * abnehmend (auf 0 in {@link #REICH} + {@link #REICH_JE}·T m) [G]; NaN weiter als {@link #WEIT} oder ohne Auflauf.
     */
    public double spur(double e, double n) {
        double f = standFein(e, n);   // W5: die gerechnete Spurlinie, wo ein Gitter den Ort nass hat
        int p = punkt(e, n);
        if (p < 0 || !(pt[p] > 0)) return f;
        double d = Math.hypot(pe[p] - e, pn[p] - n), t = pt[p] * (1 - d / (REICH + REICH_JE * pt[p]));
        t = t > 0 ? t : Double.NaN;
        return Double.isNaN(f) ? t : Double.isNaN(t) ? f : Math.max(t, f);
    }

    /** Ankunft der Welle am Ort in Sekunden der Ereignisuhr (nach dem ersten Stoß), NaN ohne; seit W5 aus dem Gitter. */
    public double ankunft(double e, double n) {
        double f = ankunftFein(e, n);
        if (!Double.isNaN(f)) return f;
        int p = punkt(e, n);
        return p < 0 || Double.isNaN(pa[p]) ? Double.NaN : EINTAUCHEN + pa[p];
    }

    /** Höchster Wasserstand über Grund g am Ort (m über NAVD88), NaN außerhalb des Bands; seit W5 mindestens der gerechnete. */
    public double stand(double e, double n, double g) {
        double t = spur(e, n);
        if (!(g < t)) return Double.NaN;
        double s = g + ANTEIL * (t - g) + MINDEST, f = standFein(e, n);
        return Double.isNaN(f) ? s : Math.max(s, Math.max(f, g + MINDEST));
    }

    /** Ob der Ort (mit Grund g) im Band liegt. */
    public boolean imBand(double e, double n, double g) { return g > 0 && g < spur(e, n); }

    /** Umriss der Uferpunkte mit Auflauf, erweitert um r (UTM: eMin, nMin, eMax, nMax), im Kern. */
    public double[] umriss(double r) {
        return new double[]{Math.max(Site.KERN.eMin(), eMin - r), Math.max(Site.KERN.nMin(), nMin - r), Math.min(Site.KERN.eMax(), eMax + r), Math.min(Site.KERN.nMax(), nMax + r)};
    }

    /** Höchste Spurlinie längs des Ufers (m). */
    public double hoechste() {
        double m = 0;
        for (double t : pt) m = Math.max(m, t);
        return m;
    }
}
