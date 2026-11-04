package com.dan.lituya.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Umrisse von Gebieten aus einem Raster von Nummern (Phase 16): je Nummer die Ringe entlang der Zellkanten, außen gegen
 * den Uhrzeigersinn, Löcher im Uhrzeigersinn (wie Oracle 1003/2003), an Engstellen getrennt; gerade Stücke ohne
 * Zwischenpunkte. Dazu Douglas-Peucker für Linien.
 */
public final class Umriss {
    private Umriss() { }

    /**
     * Nummer (≠ 0) → Polygone, jedes als Liste von Ringen {e0, n0, e1, n1, …} (erster Ring außen, dann Löcher; geschlossen,
     * der erste Punkt am Ende wiederholt). Raster w × h, Zeilen von Nord nach Süd, NW-Ecke (e0, n0), Zelle z.
     */
    public static Map<Integer, List<List<double[]>>> ringe(int[] labIn, int w, int h, double e0, double n0, double z) {
        // Gebiete, die sich nur an einer Ecke berühren (Schachbrett im 2 × 2), über eine Nachbarzelle verbinden: dann
        // berührt kein Ring sich oder einen anderen in einem Punkt (Oracle: ungültig), die Fläche ändert sich um Zellen
        int[] lab = labIn.clone();
        for (int runde = 0; runde < 20; runde++) {
            int n = 0;
            for (int j = 0; j + 1 < h; j++)
                for (int i = 0; i + 1 < w; i++) {
                    int a = lab[j * w + i], b = lab[j * w + i + 1], c = lab[(j + 1) * w + i], d = lab[(j + 1) * w + i + 1];
                    if (a == d && b != a && c != a) { if (a != 0) lab[j * w + i + 1] = a; else lab[j * w + i] = b; n++; }
                    else if (b == c && a != b && d != b) { if (b != 0) lab[j * w + i] = b; else lab[j * w + i + 1] = a; n++; }
                }
            if (n == 0) break;
        }
        // gerichtete Kanten (Gebiet links), Ecken (i, j) mit j nach Süden; Richtungen 0 O, 1 N, 2 W, 3 S
        Map<Integer, List<long[]>> kanten = new HashMap<>();
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                int l = lab[j * w + i];
                if (l == 0) continue;
                List<long[]> k = kanten.computeIfAbsent(l, x -> new ArrayList<>());
                if (j == h - 1 || lab[(j + 1) * w + i] != l) k.add(new long[]{ecke(i, j + 1, w), 0});   // Süden, nach Osten
                if (i == w - 1 || lab[j * w + i + 1] != l) k.add(new long[]{ecke(i + 1, j + 1, w), 1}); // Osten, nach Norden
                if (j == 0 || lab[(j - 1) * w + i] != l) k.add(new long[]{ecke(i + 1, j, w), 2});       // Norden, nach Westen
                if (i == 0 || lab[j * w + i - 1] != l) k.add(new long[]{ecke(i, j, w), 3});             // Westen, nach Süden
            }
        Map<Integer, List<List<double[]>>> r = new HashMap<>();
        for (var x : kanten.entrySet()) {
            List<long[]> k = x.getValue();
            Map<Long, List<Integer>> ab = new HashMap<>();
            for (int q = 0; q < k.size(); q++) ab.computeIfAbsent(k.get(q)[0], y -> new ArrayList<>(2)).add(q);
            boolean[] benutzt = new boolean[k.size()];
            List<double[]> aussen = new ArrayList<>(), loecher = new ArrayList<>();
            for (int s = 0; s < k.size(); s++) {
                if (benutzt[s]) continue;
                List<double[]> pts = new ArrayList<>();
                int q = s;
                while (q >= 0 && !benutzt[q]) {
                    benutzt[q] = true;
                    long v = k.get(q)[0];
                    int d = (int) k.get(q)[1];
                    pts.add(new double[]{e0 + (v % (w + 1)) * z, n0 - (v / (w + 1)) * z});
                    long nv = naechste(v, d, w);
                    // an der nächsten Ecke: rechts vor geradeaus vor links (trennt Engstellen)
                    int best = -1, rang = 9;
                    List<Integer> l = ab.get(nv);
                    if (l != null)
                        for (int c : l) {
                            if (benutzt[c] && c != s) continue;
                            int dd = (int) k.get(c)[1];
                            int rg = dd == (d + 3) % 4 ? 0 : dd == d ? 1 : dd == (d + 1) % 4 ? 2 : 3;
                            if (rg < rang) { rang = rg; best = c; }
                        }
                    q = best == s ? -1 : best;
                }
                // an Ecken, die der Ring zweimal berührt (Zellen, die sich nur an einer Ecke treffen), in Schleifen teilen
                for (List<double[]> schleife : schleifen(pts)) {
                    double[] ring = gerade(schleife);
                    double a = flaeche(ring);
                    if (a > 0) aussen.add(ring); else if (a < 0) loecher.add(ring);
                }
            }
            List<List<double[]>> polys = new ArrayList<>();
            for (double[] o : aussen) { List<double[]> p = new ArrayList<>(); p.add(o); polys.add(p); }
            for (double[] lo : loecher) {
                for (List<double[]> p : polys) if (innen(p.get(0), lo[0] + 1e-3, lo[1] + 1e-3) || innen(p.get(0), lo[0], lo[1])) { p.add(lo); break; }
            }
            r.put(x.getKey(), polys);
        }
        return r;
    }

    /** Teilt einen geschlossenen Punktzug (ohne Wiederholung des Anfangs) an doppelten Punkten in einfache Schleifen. */
    static List<List<double[]>> schleifen(List<double[]> pts) {
        List<List<double[]>> r = new ArrayList<>();
        List<double[]> st = new ArrayList<>();
        Map<Long, Integer> wo = new HashMap<>();
        for (double[] p : pts) {
            long key = Double.doubleToLongBits(p[0]) * 31 + Double.doubleToLongBits(p[1]);
            Integer i = wo.get(key);
            if (i != null) {
                List<double[]> l = new ArrayList<>(st.subList(i, st.size()));
                for (int k = st.size() - 1; k > i; k--) { double[] q = st.remove(k); wo.remove(Double.doubleToLongBits(q[0]) * 31 + Double.doubleToLongBits(q[1])); }
                if (l.size() >= 3) r.add(l);
            } else {
                wo.put(key, st.size());
                st.add(p);
            }
        }
        if (st.size() >= 3) r.add(st);
        return r;
    }

    static long ecke(int i, int j, int w) { return (long) j * (w + 1) + i; }

    static long naechste(long v, int d, int w) {
        return switch (d) {
            case 0 -> v + 1;
            case 1 -> v - (w + 1);
            case 2 -> v - 1;
            default -> v + (w + 1);
        };
    }

    /** Ohne Punkte auf geraden Stücken, geschlossen. */
    static double[] gerade(List<double[]> p) {
        List<double[]> q = new ArrayList<>();
        int n = p.size();
        for (int i = 0; i < n; i++) {
            double[] a = p.get((i + n - 1) % n), b = p.get(i), c = p.get((i + 1) % n);
            double cr = (b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0]);
            if (Math.abs(cr) > 1e-9) q.add(b);
        }
        double[] r = new double[2 * q.size() + 2];
        for (int i = 0; i < q.size(); i++) { r[2 * i] = q.get(i)[0]; r[2 * i + 1] = q.get(i)[1]; }
        if (!q.isEmpty()) { r[2 * q.size()] = q.get(0)[0]; r[2 * q.size() + 1] = q.get(0)[1]; }
        return r;
    }

    /** Fläche mit Vorzeichen (gegen den Uhrzeigersinn positiv), Ring geschlossen. */
    public static double flaeche(double[] r) {
        double s = 0;
        for (int q = 0; q + 3 < r.length; q += 2) s += r[q] * r[q + 3] - r[q + 2] * r[q + 1];
        return s / 2;
    }

    public static boolean innen(double[] r, double x, double y) {
        boolean in = false;
        for (int q = 0; q + 3 < r.length; q += 2) {
            double xa = r[q], ya = r[q + 1], xb = r[q + 2], yb = r[q + 3];
            if ((ya > y) != (yb > y) && x < xa + (y - ya) / (yb - ya) * (xb - xa)) in = !in;
        }
        return in;
    }

    /** Douglas-Peucker für eine Linie {x0, y0, x1, y1, …} mit Toleranz tol. */
    public static double[] douglasPeucker(double[] p, double tol) {
        int n = p.length / 2;
        if (n <= 2) return p;
        boolean[] keep = new boolean[n];
        keep[0] = keep[n - 1] = true;
        java.util.ArrayDeque<int[]> st = new java.util.ArrayDeque<>();
        st.push(new int[]{0, n - 1});
        while (!st.isEmpty()) {
            int[] s = st.pop();
            int a = s[0], b = s[1];
            double ax = p[2 * a], ay = p[2 * a + 1], bx = p[2 * b], by = p[2 * b + 1], L = Math.hypot(bx - ax, by - ay);
            double dm = -1;
            int im = -1;
            for (int i = a + 1; i < b; i++) {
                double d = L < 1e-9 ? Math.hypot(p[2 * i] - ax, p[2 * i + 1] - ay) : Math.abs((bx - ax) * (ay - p[2 * i + 1]) - (ax - p[2 * i]) * (by - ay)) / L;
                if (d > dm) { dm = d; im = i; }
            }
            if (dm > tol) { keep[im] = true; st.push(new int[]{a, im}); st.push(new int[]{im, b}); }
        }
        int k = 0;
        for (boolean x : keep) if (x) k++;
        double[] r = new double[2 * k];
        int j = 0;
        for (int i = 0; i < n; i++) if (keep[i]) { r[j++] = p[2 * i]; r[j++] = p[2 * i + 1]; }
        return r;
    }
}
