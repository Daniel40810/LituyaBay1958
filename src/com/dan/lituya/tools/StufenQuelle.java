package com.dan.lituya.tools;

import com.dan.lituya.scene.Stufe;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eine schon geschriebene Stufe des Geländes als Rückfall für die feinere (Phase 2): Szene füllt die Lücken des Kerns,
 * Fern die der Szene. Bilinear aus den {@code .r32}-Kacheln, über Kachelgrenzen hinweg.
 */
final class StufenQuelle {
    final Stufe s;
    final File daten;
    private final Map<Long, float[]> kacheln = new ConcurrentHashMap<>();
    private static final float[] LEER = new float[0];

    StufenQuelle(Stufe s, File daten) { this.s = s; this.daten = daten; }

    float wert(double e, double n) throws IOException {
        double c = (e - s.gebiet.eMin()) / s.zelle - 0.5, r = (s.gebiet.nMin() + s.ny() * s.kachel() - n) / s.zelle - 0.5;
        int i = (int) Math.floor(c), j = (int) Math.floor(r);
        double fx = c - i, fy = r - j;
        float a = zelle(i, j), b = zelle(i + 1, j), cc = zelle(i, j + 1), d = zelle(i + 1, j + 1);
        double wa = (1 - fx) * (1 - fy), wb = fx * (1 - fy), wc = (1 - fx) * fy, wd = fx * fy, sum = 0, w = 0;
        if (!Float.isNaN(a)) { sum += a * wa; w += wa; }
        if (!Float.isNaN(b)) { sum += b * wb; w += wb; }
        if (!Float.isNaN(cc)) { sum += cc * wc; w += wc; }
        if (!Float.isNaN(d)) { sum += d * wd; w += wd; }
        return w > 1e-9 ? (float) (sum / w) : Float.NaN;
    }

    /** Wert der Zelle (i, j) im ganzen Raster der Stufe (j von Nord). */
    float zelle(int i, int j) throws IOException {
        int nx = s.nx() * Stufe.N, ny = s.ny() * Stufe.N;
        if (i < 0 || j < 0 || i >= nx || j >= ny) return Float.NaN;
        int ti = i / Stufe.N, tj = j / Stufe.N;
        long key = (long) tj * 1000 + ti;
        float[] k = kacheln.get(key);
        if (k == null) {
            double e = s.gebiet.eMin() + ti * s.kachel(), n = s.gebiet.nMin() + (s.ny() - 1 - tj) * s.kachel();
            float[] v = Stufe.lesen(s.datei(daten, e, n));
            k = v == null ? LEER : v;
            kacheln.put(key, k);
        }
        if (k.length == 0) return Float.NaN;
        return k[(j - tj * Stufe.N) * Stufe.N + (i - ti * Stufe.N)];
    }

    /** Füllt die NaN-Zellen eines Gitters. */
    int proben(Gitter g, float[] ziel) throws IOException {
        int n = 0;
        for (int j = 0; j < g.h(); j++)
            for (int i = 0; i < g.w(); i++) {
                int k = j * g.w() + i;
                if (!Float.isNaN(ziel[k])) continue;
                float v = wert(g.ce(i), g.cn(j));
                if (!Float.isNaN(v)) { ziel[k] = v; n++; }
            }
        return n;
    }
}
