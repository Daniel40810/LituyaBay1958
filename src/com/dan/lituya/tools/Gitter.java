package com.dan.lituya.tools;

/**
 * Ein Zielraster in UTM 8 N (Phase 2): Nordwestecke (e0, n0), Zellgröße, Breite und Höhe in Zellen; Zeilen von Nord nach
 * Süd. Die Mitte der Zelle (i, j) liegt bei e0 + (i + ½)·zelle, n0 − (j + ½)·zelle.
 */
public record Gitter(double e0, double n0, double zelle, int w, int h) {
    public double ce(double i) { return e0 + (i + 0.5) * zelle; }
    public double cn(double j) { return n0 - (j + 0.5) * zelle; }
    public double breite() { return w * zelle; }
    public double hoehe() { return h * zelle; }

    /** Das Gitter einer Kachel mit Südwestecke (e, n). */
    public static Gitter kachel(com.dan.lituya.scene.Stufe s, double e, double n) {
        return new Gitter(e, n + s.kachel(), s.zelle, com.dan.lituya.scene.Stufe.N, com.dan.lituya.scene.Stufe.N);
    }

    /** Ein Gitter über ein Fenster mit Zellgröße zelle. */
    public static Gitter ueber(com.dan.lituya.scene.Site.Fenster f, double zelle) {
        return new Gitter(f.eMin(), f.nMax(), zelle, (int) Math.round(f.breite() / zelle), (int) Math.round(f.hoehe() / zelle));
    }

    public float[] leer() {
        float[] v = new float[w * h];
        java.util.Arrays.fill(v, Float.NaN);
        return v;
    }
}
