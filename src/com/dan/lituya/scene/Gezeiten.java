package com.dan.lituya.scene;

import java.time.Instant;
import java.util.Locale;

/**
 * Die Gezeiten vor Lituya Bay (Phase 15a, Werkbuch 7.3): Vorausrechnung aus den harmonischen Konstanten der NOAA-Station
 * Elfin Cove (9452634, Cross Sound, rund 95 km südöstlich an derselben Außenküste; [G]: gleiche Phasen wie dort), acht
 * Tiden M2, S2, N2, K2, K1, O1, P1, Q1 mit Knotenfaktoren f, u und astronomischen Argumenten V₀ nach Schureman
 * (mittlere Längen von Mond, Sonne, Perigäum und Knoten nach Meeus). Höhe über NAVD88: Mittelwasser +0,59 m
 * ({@code daten/grund/GRUND.txt}).
 * <p>
 * Dazu der Strom im Eingang aus dem Füllen und Leeren der Bucht: Q = A<sub>Bucht</sub> · dη/dt durch den Querschnitt des
 * Tors A(η) = Breite · (mittlere Tiefe + η) [G]; positiv = Ebbstrom hinaus.
 */
public final class Gezeiten {
    /** Tiden: Name, Amplitude (Fuß), Phase κ' bezogen auf Greenwich (Grad), Winkelgeschwindigkeit (Grad je Stunde). */
    static final String[] NAME = {"M2", "S2", "N2", "K2", "K1", "O1", "P1", "Q1"};
    static final double[] AMP_FT = {4.071, 1.342, 0.833, 0.361, 1.555, 0.965, 0.489, 0.171};
    static final double[] PHASE = {275.5, 306.8, 250.6, 299.6, 264.0, 248.8, 259.8, 240.8};
    static final double[] SPEED = {28.984104, 30.0, 28.43973, 30.082138, 15.041069, 13.943035, 14.958931, 13.398661};
    /** Mittelwasser über NAVD88 (m). */
    public static final double MSL_NAVD88 = 0.59;
    public static final String QUELLE = "NOAA CO-OPS 9452634 Elfin Cove, harmonische Konstanten (harcon.json), 8 Tiden; Phasen wie dort [G]";

    /** Fläche der Bucht innerhalb des Tors (m²) und Breite und mittlere Tiefe des Tors unter Mittelwasser (m) [G, aus dem Netz]. */
    public double flaecheBucht = 26.6e6, torBreite = 300, torTiefe = 8;

    /** Wasserstand über NAVD88 (m) zur Zeit (ms seit 1970, UTC). */
    public static double hoehe(double epochMs) {
        double t = (epochMs - Instant.parse("2000-01-01T12:00:00Z").toEpochMilli()) / 86_400_000.0 / 36525.0;   // Jahrhunderte seit J2000
        double s = 218.3165 + 481267.8813 * t, h = 280.4661 + 36000.7698 * t, p = 83.3532 + 4069.0137 * t, N = 125.0445 - 1934.1363 * t;
        double ut = ((epochMs / 3_600_000.0) % 24 + 24) % 24;          // Stunden UT des Tags
        double T = 15 * ut + 180;                                      // Stundenwinkel der mittleren Sonne
        double tau = T + h - s;                                        // mittlere Mondzeit
        double nr = Math.toRadians(N);
        double fM2 = 1 - 0.037 * Math.cos(nr), uM2 = -2.1 * Math.sin(nr);
        double fK1 = 1.006 + 0.115 * Math.cos(nr), uK1 = -8.9 * Math.sin(nr);
        double fO1 = 1.009 + 0.187 * Math.cos(nr), uO1 = 10.8 * Math.sin(nr);
        double fK2 = 1.024 + 0.286 * Math.cos(nr), uK2 = -17.7 * Math.sin(nr);
        double[] v0 = {2 * tau, 2 * T, 2 * tau - s + p, 2 * tau + 2 * s, tau + s - 90, tau - s + 90, tau + s - 2 * h + 90, tau - 2 * s + p + 90};   // Schureman (wie NOAA)
        double[] f = {fM2, 1, fM2, fK2, fK1, fO1, 1, fO1};
        double[] u = {uM2, 0, uM2, uK2, uK1, uO1, 0, uO1};
        double eta = 0;
        for (int k = 0; k < NAME.length; k++) eta += f[k] * AMP_FT[k] * 0.3048 * Math.cos(Math.toRadians(v0[k] + u[k] - PHASE[k]));
        return MSL_NAVD88 + eta;
    }

    /** Steig- oder Fallrate (m/s). */
    public static double rate(double epochMs) { return (hoehe(epochMs + 60_000) - hoehe(epochMs - 60_000)) / 120; }

    /** Strom im Tor (m/s, positiv Ebbstrom hinaus) aus dem Füllen der Bucht. */
    public double stromTor(double epochMs) {
        double q = -flaecheBucht * rate(epochMs);
        double a = torBreite * Math.max(1, torTiefe + hoehe(epochMs) - MSL_NAVD88);
        return q / a;
    }

    /** „22:16 PST: +1,23 m NAVD88, fällt 0,31 m/h, Strom im Tor 1,8 m/s hinaus“ */
    public String zeile(double epochMs) {
        double r = rate(epochMs) * 3600, u = stromTor(epochMs);
        return String.format(Locale.GERMAN, "%+.2f m NAVD88, %s %.2f m/h, Strom im Tor %.1f m/s %s", hoehe(epochMs), r < 0 ? "fällt" : "steigt", Math.abs(r), Math.abs(u), u > 0 ? "hinaus (Ebbe)" : "herein (Flut)");
    }

    /** Hoch- und Niedrigwasser zwischen a und b (ms): {Zeit, Höhe, +1 hoch / −1 niedrig}. */
    public static java.util.List<double[]> extreme(double a, double b) {
        java.util.List<double[]> l = new java.util.ArrayList<>();
        double step = 60_000, prev = rate(a);
        for (double t = a + step; t <= b; t += step) {
            double r = rate(t);
            if (prev > 0 && r <= 0) l.add(new double[]{t, hoehe(t), 1});
            if (prev < 0 && r >= 0) l.add(new double[]{t, hoehe(t), -1});
            prev = r;
        }
        return l;
    }
}
