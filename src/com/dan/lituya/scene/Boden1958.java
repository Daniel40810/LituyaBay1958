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
    /**
     * Werkbuch Welle W3c („Schwimmbecken“): im Bild läuft der Rückbau weich aus. Die Vorrechnung kennt ihn hart (Land unter
     * 60 m wird −122 m), das gab im Bild senkrechte Wände von bis zu 180 m an der 60-m-Linie und gerade Kanten am Rand des
     * Rechtecks. Für Gelände, Wald und Bäche gilt jetzt {@link #rueckbauBild}: Gewicht nach der Höhe heute (voll unter
     * {@link #BILD_UNTEN} m, keins über {@link #BILD_OBEN} m) mal Saum am Rand des Rechtecks ({@link #BILD_SAUM} m) [G].
     * Die Uferlinie liegt so bei rund 65 m heute, nahe an der der Vorrechnung (60 m). Die Physik bleibt unverändert.
     */
    public static final double BILD_UNTEN = 25, BILD_OBEN = 80, BILD_SAUM = 220, BILD_UFER = 60;
    /** W3c: außerhalb des Rechtecks läuft der Rückbau als Strand aus, nie tiefer als so weit über den Pegel (m). */
    public static final double BILD_STRAND = 1;

    /**
     * W3c: die Hochfläche im Nordwesten des Rückbaus (heute 60–155 m, flach) blieb mit der 60-m-Regel stehen und stand im
     * Bild als brauner Tafelberg im Wasser. Sie ist die Zunge des Lituya-Gletschers; 1958 endete er hier mit einer
     * Eiswand im Inlet [G, zu prüfen mit Karte 1951/C-5]. In diesem Rechteck bleibt die Regel der Vorrechnung (Land über
     * {@link #GLETSCHER_UNTER} m steht, darunter Wasser, Übergang über 8 m: eine steile Front), und das Gelände wird Eis.
     */
    public static final double GLETSCHER_E0 = 353_600, GLETSCHER_E1 = 355_000, GLETSCHER_N0 = 6_506_700, GLETSCHER_N1 = RUECKBAU_N1;
    public static final double GLETSCHER_UNTER = 54, GLETSCHER_FRONT = 8;
    /**
     * W3c: nur für die Farbe: die Zunge im Norden des Rückbaus. Am Nordrand (N 6 507 700) bricht der Grund der Vorrechnung
     * senkrecht ab; im Bild ist das die Front des Lituya-Gletschers 1958 [G], darüber Eis bis rund 400 m.
     */
    public static final double ZUNGE_E0 = 354_300, ZUNGE_E1 = 355_700, ZUNGE_N0 = RUECKBAU_N1 - 300, ZUNGE_N1 = RUECKBAU_N1 + 2_000;

    /** W3c: Anteil der Gletscherzunge 1958 am Ort (UTM): 1 innen, über 120 m am Rand (außer im Norden) auf 0. */
    public static double gletscher(double e, double n) {
        double d = Math.min(Math.min(e - GLETSCHER_E0, GLETSCHER_E1 - e), n - GLETSCHER_N0);
        return n >= GLETSCHER_N1 ? 0 : glatt(0, 120, d);
    }

    /**
     * W3c: Gewicht des Rückbaus im Bild am Ort (UTM) bei Höhe heute h (0 … 1). Innen wie die Vorrechnung (die Uferlinie
     * liegt bei rund 60 m heute; darüber flacht der Rückbau bis {@link #BILD_OBEN} m aus, statt eine senkrechte Wand zu
     * lassen); außen ein Ufer von {@link #BILD_UFER} m bis {@link #BILD_STRAND} m über den Pegel, wo innen Wasser ist.
     * (Proben: ein Saum innen ließ Sander stehen, wo die Vorrechnung Wasser hat; ein breiter Saum außen machte Hügel flach.)
     */
    public static double rueckbauGewicht(double e, double n, double h) {
        if (Double.isNaN(h)) return 0;
        double d = Math.min(Math.min(e - RUECKBAU_E0, RUECKBAU_E1 - e), Math.min(n - RUECKBAU_N0, RUECKBAU_N1 - n));
        if (d <= -BILD_UFER) return 0;
        if (d < 0) return (1 - glatt(0, BILD_UFER, -d)) * (1 - glatt(60, 80, h)) * 0.2;
        double g = gletscher(e, n);
        double weich = 1 - glatt(BILD_UNTEN, BILD_OBEN, h);
        double eis = 1 - glatt(GLETSCHER_UNTER, GLETSCHER_UNTER + GLETSCHER_FRONT, h);
        return glatt(0, 4, d) * (weich + (eis - weich) * g);
    }

    /** W3c: Höhe 1958 im Bild aus der Höhe heute h (wie {@code gelaende.vert}). */
    public static double rueckbauBild(double e, double n, double h) {
        if (Double.isNaN(h)) return h;
        double d = Math.min(Math.min(e - RUECKBAU_E0, RUECKBAU_E1 - e), Math.min(n - RUECKBAU_N0, RUECKBAU_N1 - n));
        if (d <= -BILD_UFER) return h;
        if (d < 0) {
            double w = (1 - glatt(0, BILD_UFER, -d)) * (1 - glatt(60, 80, h));
            return h + (Math.min(h, BILD_STRAND) - h) * w;
        }
        double w = rueckbauGewicht(e, n, h);
        return h + (RUECKBAU_TIEFE - h) * w;
    }

    static double glatt(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

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

    /**
     * Der Grund von heute (Phase 13c, „Was wäre wenn“): im Wasser BlueTopo/USACE (4 m), an Land die Stufe Szene, ohne
     * Lotungen 1940 und ohne Rückbau (der Kopf des Gilbert Inlet ist heute Gletscher und Sander).
     */
    public double hoeheHeute(double e, double n) {
        double w = heute.at(e, n);
        if (!Double.isNaN(w)) return w;
        for (Raster r : land) {
            if (e < r.e0() || e >= r.e0() + r.w() * r.zelle() || n > r.n1() || n <= r.n1() - r.h() * r.zelle()) continue;
            return r.at(e, n);
        }
        return Double.NaN;
    }

    /** Ob der Ort heute Wasser ist (Grund heute vorhanden). */
    public boolean wasserHeute(double e, double n) { return !Double.isNaN(heute.at(e, n)); }
}
