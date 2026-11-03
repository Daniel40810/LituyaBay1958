package com.dan.lituya.camera;

import com.dan.lituya.scene.SichtNetz;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

/**
 * Die Kamerafahrten aus {@code LB_NET_SICHT} (Phase 17, Werkbuch 11.2): der <b>Rundflug</b> über die Motive (tsp) und die
 * <b>Kamerafahrt</b> den Spannbaum entlang (mcst), aus {@code daten/netz/sicht/rundflug.tsv} und {@code fahrt.tsv}. Die
 * Knoten in Augenhöhe werden mit Catmull-Rom alle {@link #SCHRITT} m verbunden; das Auge bleibt mindestens {@link #UEBER}
 * m über dem Gelände (die Sichtlinien selbst sind frei, die Rundung kann eine Kuppe schneiden). Die Dauer ist die
 * Flugzeit des Netzes geteilt durch das Tempo.
 */
public final class SichtFahrt {
    private SichtFahrt() { }

    public static final String RUNDFLUG = "Rundflug (tsp)", KAMERAFAHRT = "Kamerafahrt (Spannbaum)";
    public static final double SCHRITT = 25, UEBER = 30, NEIGUNG = 6;
    /** Tempo gegenüber der Flugzeit des Netzes. */
    public static final double[] TEMPI = {1, 5, 15, 50};

    /** Baut eine der beiden Fahrten (null, wenn die Dateien fehlen). */
    public static Fahrt bauen(File daten, String welche, double tempo, DoubleBinaryOperator grund) throws IOException {
        if (!SichtNetz.da(daten)) return null;
        SichtNetz w = SichtNetz.lesen(daten);
        double[][] f = SichtNetz.fahrt(daten, welche.equals(RUNDFLUG) ? "rundflug.tsv" : "fahrt.tsv");
        if (f == null || f.length < 2) return null;
        return bauen(w, f, welche, tempo, grund);
    }

    /** Fahrt aus der Folge {Knoten, Zeit} des Netzes w. */
    public static Fahrt bauen(SichtNetz w, double[][] folge, String name, double tempo, DoubleBinaryOperator grund) {
        List<double[]> k = new ArrayList<>();
        for (double[] r : folge) {
            int i = (int) r[0];
            double[] p = {w.x(i), w.hoehe[i], w.z(i)};
            if (!k.isEmpty()) {
                double[] q = k.get(k.size() - 1);
                if (Math.hypot(p[0] - q[0], p[2] - q[2]) < 1 && Math.abs(p[1] - q[1]) < 1) continue;
            }
            k.add(p);
        }
        List<double[]> d = new ArrayList<>();
        for (int s = 0; s + 1 < k.size(); s++) {
            double[] p0 = k.get(Math.max(0, s - 1)), p1 = k.get(s), p2 = k.get(s + 1), p3 = k.get(Math.min(k.size() - 1, s + 2));
            double L = Math.hypot(p2[0] - p1[0], p2[2] - p1[2]);
            int n = Math.max(1, (int) Math.ceil(L / SCHRITT));
            for (int q = 0; q < n; q++) {
                double t = q / (double) n;
                double[] p = new double[3];
                for (int c = 0; c < 3; c++) p[c] = catmull(p0[c], p1[c], p2[c], p3[c], t);
                d.add(p);
            }
        }
        d.add(k.get(k.size() - 1).clone());
        if (grund != null)
            for (double[] p : d) {
                double g = grund.applyAsDouble(p[0], p[2]);
                if (!Double.isNaN(g) && g > -1e5) p[1] = Math.max(p[1], Math.max(g, 0) + UEBER);
            }
        double dauer = Math.max(10, folge[folge.length - 1][1] / Math.max(0.1, tempo));
        Fahrt f = new Fahrt(name, d.toArray(new double[0][]), dauer, 0, NEIGUNG);
        return f;
    }

    static double catmull(double a, double b, double c, double d, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
    }
}
