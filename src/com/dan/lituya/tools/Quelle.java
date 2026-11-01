package com.dan.lituya.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Eine Rasterquelle aus dem Kartenmaterial (Phase 2): alle GeoTIFF einer Art (etwa die 312 Lidar-Kacheln), ihre
 * Abbildung, der Kanal, ein Versatz in der Höhe und der gültige Wertebereich. {@link #proben} tastet sie an den
 * Zellmitten eines {@link Gitter} ab und füllt nur, was dort noch fehlt (NaN): so ergibt die Reihenfolge der Quellen
 * ihren Vorrang.
 * <p>
 * Abtastung bilinear; fehlt einer der vier Nachbarn, werden die übrigen nach ihrem Gewicht gemittelt (ab einem Anteil
 * von ¼), damit Ränder und Lücken der Quelle nicht um ein Pixel wachsen oder schrumpfen. Für Quellen, die nicht in UTM
 * 8 N liegen, wird die Abbildung nur an jeder 50. Zelle gerechnet und dazwischen bilinear gemittelt (Fehler unter
 * Millimetern auf 1 km).
 */
public final class Quelle {
    public final String name;
    final List<GeoTiff> dateien = new ArrayList<>();
    final List<Abbildung> abbildungen = new ArrayList<>();
    final int band;
    final double versatz, min, max;

    public Quelle(String name, List<File> files, int band, double versatz, double min, double max) throws IOException {
        this.name = name;
        this.band = band;
        this.versatz = versatz;
        this.min = min;
        this.max = max;
        for (File f : files) {
            GeoTiff t = GeoTiff.open(f);
            dateien.add(t);
            abbildungen.add(Abbildung.von(t));
        }
    }

    public int anzahl() { return dateien.size(); }

    /** Füllt in ziel (w·h Werte, Zeilen von Nord nach Süd) die NaN-Zellen aus dieser Quelle; gibt die Zahl gefüllter Zellen zurück. */
    public int proben(Gitter g, float[] ziel) throws IOException {
        int gefuellt = 0;
        double[] o = new double[2];
        for (int f = 0; f < dateien.size(); f++) {
            GeoTiff t = dateien.get(f);
            Abbildung a = abbildungen.get(f);
            // Umriss des Gitters in der Abbildung der Quelle
            double xmin = Double.MAX_VALUE, xmax = -Double.MAX_VALUE, ymin = Double.MAX_VALUE, ymax = -Double.MAX_VALUE;
            for (int s = 0; s <= 8; s++)
                for (int[] r : new int[][]{{s, 0}, {s, 8}, {0, s}, {8, s}}) {
                    a.vonUtm(g.e0() + g.breite() * r[0] / 8.0, g.n0() - g.hoehe() * r[1] / 8.0, o);
                    xmin = Math.min(xmin, o[0]); xmax = Math.max(xmax, o[0]);
                    ymin = Math.min(ymin, o[1]); ymax = Math.max(ymax, o[1]);
                }
            double tx0 = Math.min(t.x0, t.x1()), tx1 = Math.max(t.x0, t.x1()), ty0 = Math.min(t.y0, t.y1()), ty1 = Math.max(t.y0, t.y1());
            if (xmax < tx0 || xmin > tx1 || ymax < ty0 || ymin > ty1) continue;
            gefuellt += proben(g, ziel, t, a);
        }
        return gefuellt;
    }

    private int proben(Gitter g, float[] ziel, GeoTiff t, Abbildung a) throws IOException {
        final int step = 50;
        int gw = g.w() / step + 2, gh = g.h() / step + 2;
        double[] px = null, py = null;
        boolean utm = a == Abbildung.UTM;
        if (!utm) {
            px = new double[gw * gh];
            py = new double[gw * gh];
            double[] o = new double[2];
            for (int j = 0; j < gh; j++)
                for (int i = 0; i < gw; i++) {
                    a.vonUtm(g.ce(Math.min(i * step, g.w())), g.cn(Math.min(j * step, g.h())), o);
                    px[j * gw + i] = t.col(o[0]);
                    py[j * gw + i] = t.row(o[1]);
                }
        }
        Leser l = new Leser(t, band);
        int n = 0;
        // in UTM nur die Zellen über der Datei (die Grundkarte heute läuft sonst 312-mal über 27 Mio. Zellen)
        int ia = 0, ib = g.w(), ja = 0, jb = g.h();
        if (utm) {
            ia = Math.max(0, (int) Math.floor((Math.min(t.x0, t.x1()) - g.e0()) / g.zelle()) - 1);
            ib = Math.min(g.w(), (int) Math.ceil((Math.max(t.x0, t.x1()) - g.e0()) / g.zelle()) + 1);
            ja = Math.max(0, (int) Math.floor((g.n0() - Math.max(t.y0, t.y1())) / g.zelle()) - 1);
            jb = Math.min(g.h(), (int) Math.ceil((g.n0() - Math.min(t.y0, t.y1())) / g.zelle()) + 1);
        }
        for (int j = ja; j < jb; j++) {
            for (int i = ia; i < ib; i++) {
                int k = j * g.w() + i;
                if (!Float.isNaN(ziel[k])) continue;
                double c, r;
                if (utm) {
                    c = t.col(g.ce(i));
                    r = t.row(g.cn(j));
                } else {
                    int gi = Math.min(i / step, gw - 2), gj = Math.min(j / step, gh - 2);
                    double fx = (i - gi * step) / (double) step, fy = (j - gj * step) / (double) step;
                    if (gi * step + step > g.w()) fx = (i - gi * step) / (double) Math.max(1, g.w() - gi * step);
                    if (gj * step + step > g.h()) fy = (j - gj * step) / (double) Math.max(1, g.h() - gj * step);
                    int p = gj * gw + gi;
                    c = (px[p] * (1 - fx) + px[p + 1] * fx) * (1 - fy) + (px[p + gw] * (1 - fx) + px[p + gw + 1] * fx) * fy;
                    r = (py[p] * (1 - fx) + py[p + 1] * fx) * (1 - fy) + (py[p + gw] * (1 - fx) + py[p + gw + 1] * fx) * fy;
                }
                if (c < -1 || r < -1 || c > t.width || r > t.height) continue;
                float v = l.bilinear(c, r);
                if (Float.isNaN(v)) continue;
                double h = v + versatz;
                if (h < min || h > max) continue;
                ziel[k] = (float) h;
                n++;
            }
        }
        return n;
    }

    /** Liest Werte einer Datei mit einem kleinen eigenen Speicher der letzten Kacheln (je Faden einer). */
    static final class Leser {
        final GeoTiff t;
        final int band;
        final int[] keys = {-1, -1, -1, -1};
        final float[][] tiles = new float[4][];
        int rr;

        Leser(GeoTiff t, int band) { this.t = t; this.band = band; }

        float get(int i, int j) throws IOException {
            if (i < 0 || j < 0 || i >= t.width || j >= t.height) return Float.NaN;
            int tx = i / t.tileW, ty = j / t.tileH, key = ty * t.across + tx;
            float[] a = null;
            for (int s = 0; s < 4; s++) if (keys[s] == key) { a = tiles[s]; break; }
            if (a == null) {
                a = t.tile(tx, ty);
                keys[rr] = key;
                tiles[rr] = a;
                rr = (rr + 1) & 3;
            }
            return a[((j - ty * t.tileH) * t.tileW + (i - tx * t.tileW)) * t.samples + band];
        }

        float bilinear(double c, double r) throws IOException {
            int i = (int) Math.floor(c), j = (int) Math.floor(r);
            double fx = c - i, fy = r - j;
            float a = get(i, j), b = get(i + 1, j), cc = get(i, j + 1), d = get(i + 1, j + 1);
            double wa = (1 - fx) * (1 - fy), wb = fx * (1 - fy), wc = (1 - fx) * fy, wd = fx * fy;
            double s = 0, w = 0;
            if (!Float.isNaN(a)) { s += a * wa; w += wa; }
            if (!Float.isNaN(b)) { s += b * wb; w += wb; }
            if (!Float.isNaN(cc)) { s += cc * wc; w += wc; }
            if (!Float.isNaN(d)) { s += d * wd; w += wd; }
            return w >= 0.25 ? (float) (s / w) : Float.NaN;
        }
    }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "%s (%d Dateien, Versatz %+.2f m)", name, dateien.size(), versatz);
    }

    public void schliessen() {
        for (GeoTiff t : dateien) try { t.close(); } catch (IOException e) { /* zu */ }
    }
}
