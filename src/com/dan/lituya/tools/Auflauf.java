package com.dan.lituya.tools;

import com.dan.impact.Impact;
import com.dan.impact.Presets;
import com.dan.wave.Bay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Der Auflauf am Sporn im Schnitt (Phase 11, Werkbuch 6.1, 9.5; Ant-Ziel {@code auflauf}). Verdrahtet zwei Packages, die
 * sich nicht kennen: {@code impact} rechnet den Laborfall FRITZ01 und meldet am Schnitt 885 m Wasserstand und Durchfluss
 * ({@code Impact.Far}); {@code wave} ({@link Bay}) übernimmt dort als Einlass und lässt die Welle über den Grund bis an
 * den 45°-Gegenhang laufen. Zum Vergleich die Messwelle aus dem Versuch (Einzelwelle 152 m auf 122 m nach 16 s) als
 * Einlass – das ist die Abnahme von {@code wave} (526 ± 50 m), dieser Fall erbt die zu hohe Welle aus Phase 10.
 * Durchfluss am Einlass wahlweise gemessen oder aus der rechtslaufenden Welle (Phase 10: am Rand kippt die Wand noch).
 * Argumente: Qualität von impact (LOW, MEDIUM).
 */
public final class Auflauf {
    private Auflauf() { }

    public static void main(String[] a) {
        Impact.Quality q = a.length > 0 ? Impact.Quality.valueOf(a[0]) : Impact.Quality.LOW;
        Impact s = Presets.gilbertInlet1958(q);
        List<double[]> rand = new ArrayList<>();
        s.far(885, (t, cell, x, z, eta, qx, qz) -> rand.add(new double[]{t, eta, qx}));
        long t0 = System.nanoTime();
        s.run(30);
        double ti = (System.nanoTime() - t0) / 1e9;
        Impact.Gauge g885 = s.gauges().get(1);
        System.out.printf(Locale.GERMAN, "impact (%s, %.0f m): bei 885 m %.0f m nach %.1f s, %d Randwerte, %.0f s Rechnung%n", q, s.dx, g885.max(), g885.timeOfMax(), rand.size(), ti);

        double[] mess = lauf(Messwelle.INSTANCE);
        double[] ausEta = lauf(fromSeries(rand, true));
        double[] gemessen = lauf(fromSeries(rand, false));
        System.out.println("                                       Auflauf    Pegel 1 100 m");
        System.out.printf(Locale.GERMAN, "  Messwelle FRITZ01 (Abnahme)        %5.0f m    %5.0f m nach %4.1f s%n", mess[0], mess[1], mess[2]);
        System.out.printf(Locale.GERMAN, "  impact, Durchfluss aus der Welle   %5.0f m    %5.0f m nach %4.1f s%n", ausEta[0], ausEta[1], ausEta[2]);
        System.out.printf(Locale.GERMAN, "  impact, Durchfluss gemessen        %5.0f m    %5.0f m nach %4.1f s%n", gemessen[0], gemessen[1], gemessen[2]);
        System.out.println("  FRITZ01: Auflauf 526 m (Hall-Watts 524 m); impact liegt bei 885 m rund 30 % über dem Versuch (Phase 10)");
    }

    /** Schnitt ab 885 m (2 m Zellen), Grund 122 m tief, Gegenhang 45° ab 1 220 m: {Auflauf, Pegel 1 100 m, Zeit}. */
    public static double[] lauf(Bay.Inlet in) {
        double h = 122, xa = 885, dx = 2;
        int nx = (int) Math.ceil((1342 + 700 - xa) / dx);
        Bay b = new Bay(xa, 0, nx, 3, dx);
        b.inlet(Bay.W, in);
        b.build((x, z) -> x < 1220 ? -h : Math.min(650, -h + (x - 1220)), 0, null);
        Bay.Gauge g = b.gauge("1 100 m", 1100, 3);
        b.run(50);
        return new double[]{b.runup(1220, -1, 1e9, 1e9), g.max(), g.timeOfMax()};
    }

    /** Die Messwelle aus dem Versuch: Einzelwelle 152 m auf 122 m, Kamm bei 885 m nach 16 s. */
    public enum Messwelle implements Bay.Inlet {
        INSTANCE;

        public double[] at(double t, double x, double z) {
            double h = 122, hh = 152, c = Math.sqrt(9.81 * (h + hh)), k = Math.sqrt(3 * hh / (4 * h * h * h)), ch = 1 / Math.cosh(k * c * (t - 16)), e = hh * ch * ch;
            return new double[]{e, c * e};
        }
    }

    /** Einlass aus den Randwerten von impact {t, η, q}; Durchfluss gemessen oder aus der rechtslaufenden Welle. */
    public static Bay.Inlet fromSeries(List<double[]> r, boolean ausWelle) {
        double h = 122, g = 9.81;
        return (t, x, z) -> {
            double e = 0, qq = 0;
            if (!r.isEmpty()) {
                if (t <= r.get(0)[0]) { e = r.get(0)[1]; qq = r.get(0)[2]; }
                else if (t >= r.get(r.size() - 1)[0]) { e = r.get(r.size() - 1)[1]; qq = r.get(r.size() - 1)[2]; }
                else {
                    int lo = 0, hi = r.size() - 1;
                    while (hi - lo > 1) { int m = (lo + hi) / 2; if (r.get(m)[0] <= t) lo = m; else hi = m; }
                    double[] p = r.get(lo), n = r.get(hi);
                    double f = (t - p[0]) / (n[0] - p[0]);
                    e = p[1] + f * (n[1] - p[1]);
                    qq = p[2] + f * (n[2] - p[2]);
                }
            }
            if (ausWelle) { double d = Math.max(0, h + e); qq = 2 * (Math.sqrt(g * d) - Math.sqrt(g * h)) * d; }
            return new double[]{e, qq};
        };
    }
}
