package com.dan.lituya.scene;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Der Grund für die Welle von 1958 (Phase 13, Werkbuch 4.4, 10.1 „vor“): Höhe über NAVD88 an jedem Ort des Kerns,
 * zusammengesetzt aus dem, was {@code daten/} hat:
 * <ol>
 * <li>im Wasser von heute die Lotungen von 1940 ({@code grund/1940_20m.r32}), wo es sie gibt, sonst der Grund heute
 * ({@code grund/heute_4m.r32}, USACE 2019 und BlueTopo);</li>
 * <li>an Land das Gelände der Stufe Szene ({@code gelaende/szene_5m}, IfSAR 2012);</li>
 * <li><b>Rückbau des Gilbert Inlet [G]</b>, bis der Schritt {@code rueckbau1958} der Aufbereitung kommt: Heute füllt der
 * vorgerückte Lituya-Gletscher mit seinen Sandern den Kopf des Inlet; 1958 war dort Wasser von 122 m Tiefe (FRITZ01) bis
 * zur Gletscherfront. Im Tal zwischen den Wänden (E 353 600–355 700, N 6 505 900–6 507 700) wird jedes Land unter
 * {@link #RUECKBAU_UNTER} m zu Grund in {@link #RUECKBAU_TIEFE} m; die Wände darüber bleiben, wie sie heute sind.</li>
 * </ol>
 * Alle Raster bilinear (an NaN-Rändern die nächste Zelle). Ohne Daten: NaN.
 */
public final class Boden1958 {
    public static final double RUECKBAU_E0 = 353_600, RUECKBAU_E1 = 355_700, RUECKBAU_N0 = 6_505_900, RUECKBAU_N1 = 6_507_700;
    /** Land darunter wird im Rückbau Wasser (m über NAVD88) [G]. */
    public static final double RUECKBAU_UNTER = 60;
    /** Tiefe des Gilbert Inlet 1958 (m unter NAVD88), FRITZ01. */
    public static final double RUECKBAU_TIEFE = -122;

    /** Ein Raster: Westrand, Nordrand, Zelle, Breite, Höhe; Zeilen von Nord nach Süd. */
    record Raster(double e0, double n1, double zelle, int w, int h, float[] v) {
        double at(double e, double n) {
            double fi = (e - e0) / zelle - 0.5, fj = (n1 - n) / zelle - 0.5;
            int i = (int) Math.floor(fi), j = (int) Math.floor(fj);
            if (i < -1 || j < -1 || i >= w || j >= h) return Double.NaN;
            double a = fi - i, b = fj - j;
            double v00 = get(i, j), v10 = get(i + 1, j), v01 = get(i, j + 1), v11 = get(i + 1, j + 1);
            if (Double.isNaN(v00) || Double.isNaN(v10) || Double.isNaN(v01) || Double.isNaN(v11)) return get((int) Math.round(fi), (int) Math.round(fj));
            return (1 - b) * ((1 - a) * v00 + a * v10) + b * ((1 - a) * v01 + a * v11);
        }

        double get(int i, int j) { return i < 0 || j < 0 || i >= w || j >= h ? Double.NaN : v[j * w + i]; }
    }

    private final Raster heute, lot1940;
    private final Raster[] land;
    public final String quelle;

    /** Liest {@code daten/grund} und die Kacheln der Stufe Szene über den Kern. */
    public Boden1958(File daten) throws IOException {
        File g = new File(daten, "grund");
        heute = new Raster(340_000, 6_512_000, 4, 6000, 4500, need(Stufe.lesen(new File(g, "heute_4m.r32")), "grund/heute_4m.r32"));
        lot1940 = new Raster(340_000, 6_512_000, 20, 1200, 900, need(Stufe.lesen(new File(g, "1940_20m.r32")), "grund/1940_20m.r32"));
        java.util.List<Raster> l = new java.util.ArrayList<>();
        for (double[] k : Stufe.SZENE.kacheln()) {
            if (k[0] + Stufe.SZENE.kachel() <= Site.KERN.eMin() || k[0] >= Site.KERN.eMax() || k[1] + Stufe.SZENE.kachel() <= Site.KERN.nMin() || k[1] >= Site.KERN.nMax()) continue;
            float[] v = Stufe.lesen(Stufe.SZENE.datei(daten, k[0], k[1]));
            if (v != null) l.add(new Raster(k[0], k[1] + Stufe.SZENE.kachel(), Stufe.SZENE.zelle, Stufe.N, Stufe.N, v));
        }
        land = l.toArray(new Raster[0]);
        quelle = String.format(Locale.GERMAN, "Grund 1940 (Lotungen), sonst heute (4 m); Land Szene 5 m (%d Kacheln); Rückbau Gilbert Inlet bis N %.0f in %.0f m [G]",
                land.length, RUECKBAU_N1, RUECKBAU_TIEFE);
    }

    static float[] need(float[] v, String name) throws IOException {
        if (v == null) throw new IOException("daten/" + name + " fehlt: ant aufbereitung -Dschritte=grund");
        return v;
    }

    /** Höhe über NAVD88 am Ort (UTM 8 N), NaN ohne Daten. */
    public double hoehe(double e, double n) {
        double w = heute.at(e, n);
        if (!Double.isNaN(w)) {
            double a = lot1940.at(e, n);
            return Double.isNaN(a) ? w : a;
        }
        double h = Double.NaN;
        for (Raster r : land) {
            if (e < r.e0() || e >= r.e0() + r.w() * r.zelle() || n > r.n1() || n <= r.n1() - r.h() * r.zelle()) continue;
            h = r.at(e, n);
            break;
        }
        if (e >= RUECKBAU_E0 && e <= RUECKBAU_E1 && n >= RUECKBAU_N0 && n <= RUECKBAU_N1 && !(h >= RUECKBAU_UNTER)) return RUECKBAU_TIEFE;
        return h;
    }

    /** Ob der Ort heute Wasser ist (Grund heute vorhanden). */
    public boolean wasserHeute(double e, double n) { return !Double.isNaN(heute.at(e, n)); }
}
