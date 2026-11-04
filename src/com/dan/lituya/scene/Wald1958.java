package com.dan.lituya.scene;

import com.dan.succession.Art;
import com.dan.succession.Succession;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Der Wald der Bucht (Phase 14a, Werkbuch 5.6, 9.7): Bestand heute aus der Kronenhöhe ({@code daten/wald}, ArcticDEM −
 * Gelände), der Altbestand 1958 im Band, die Störung durch die Welle von 1958 und die Wiederbewaldung bis heute mit
 * {@code com.dan.succession}. Alles in Szenenkoordinaten (x Ost, z Süd), Plätze je 6 m wie {@code forest.TreeStates}.
 * Arten in der Reihenfolge des Walds: Sitka-Fichte, Westliche Hemlocktanne, Roterle.
 */
public final class Wald1958 {
    public static final double ABSTAND = 6;
    /** Jahre der Wiederbewaldung (1958 bis 2027). */
    public static final int JAHRE = 69;
    /** Raster der Kronenhöhe: Kern in 10 m, Zeilen von Nord nach Süd. */
    public static final double ZELLE = 10;
    public static final int W = 2400, H = 1800;

    public final Boden1958 boden;
    public final Band1958 band;
    /** Kronenhöhe heute (m über Grund, NaN: Wasser oder ohne Wert), null ohne {@code daten/wald}. */
    public final float[] krone;
    /** Faktor auf die Wuchsrate der Arten aus dem Abgleich (1 ohne), Höhe des Altbestands (m). */
    public double wuchs, alt;
    public final String quelle;
    private Succession su;

    public Wald1958(File daten, boolean gezogen) throws IOException {
        boden = new Boden1958(daten);
        band = new Band1958(daten, gezogen);
        File w = new File(daten, "wald");
        float[] k = Stufe.lesen(new File(w, "kronenhoehe_10m.r32"));
        krone = k != null && k.length == W * H ? k : null;
        Properties p = new Properties();
        File t = new File(w, "WALD.txt");
        if (t.isFile()) try (var r = Files.newBufferedReader(t.toPath(), StandardCharsets.UTF_8)) { p.load(r); }
        wuchs = zahl(p, "wuchs", 1);
        alt = zahl(p, "alt", 40);
        quelle = String.format(Locale.GERMAN, "%s · Wuchs ×%.2f, Altbestand %.0f m (%s)", band.quelle, wuchs, alt,
                krone != null ? "Kronenhöhe ArcticDEM − Gelände" : "ohne daten/wald: Bestand aus dem Gelände [G]");
    }

    static double zahl(Properties p, String k, double d) {
        String s = p.getProperty(k);
        if (s == null) return d;
        try { return Double.parseDouble(s.trim().split("\\s")[0]); } catch (NumberFormatException e) { return d; }
    }

    /** Die Arten mit dem Faktor auf die Wuchsrate. */
    public static List<Art> arten(double wuchs) {
        List<Art> l = List.of(Art.sitkaFichte(), Art.hemlock(), Art.roterle());
        for (Art a : l) a.k *= wuchs;
        return l;
    }

    // ------------------------------------------------------------------ Orte

    static double e(double x) { return x + Site.E0; }
    static double n(double z) { return Site.N0 - z; }

    /** Grund 1958 (mit Rückbau) in der Szene. */
    public double grund1958(double x, double z) { return boden.hoehe(e(x), n(z)); }

    /** Grund heute in der Szene (Land: Gelände Szene, Wasser: Grund heute). */
    public double grundHeute(double x, double z) { return boden.hoeheHeute(e(x), n(z)); }

    /** Höchster Stand der Welle 1958 (NaN außerhalb des Bands). */
    public double stand(double x, double z) {
        double g = grund1958(x, z);
        return g > 0 ? band.stand(e(x), n(z), g) : Double.NaN;
    }

    /** Ob der Ort im Band liegt (Grund 1958). */
    public boolean imBand(double x, double z) { double g = grund1958(x, z); return band.imBand(e(x), n(z), g); }

    /** Kronenhöhe heute (m), NaN ohne Wert. */
    public double krone(double x, double z) {
        if (krone == null) return Double.NaN;
        int i = (int) Math.floor((e(x) - Site.KERN.eMin()) / ZELLE), j = (int) Math.floor((Site.KERN.nMax() - n(z)) / ZELLE);
        return i < 0 || j < 0 || i >= W || j >= H ? Double.NaN : krone[j * W + i];
    }

    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    double hangDeg(double x, double z, boolean v1958) {
        double d = 8;
        double gx = (v1958 ? grund1958(x + d, z) - grund1958(x - d, z) : grundHeute(x + d, z) - grundHeute(x - d, z)) / (2 * d);
        double gz = (v1958 ? grund1958(x, z + d) - grund1958(x, z - d) : grundHeute(x, z + d) - grundHeute(x, z - d)) / (2 * d);
        return Double.isNaN(gx + gz) ? 0 : Math.toDegrees(Math.atan(Math.hypot(gx, gz)));
    }

    /** Anteil der Art s (0 Sitka, 1 Hemlock, 2 Roterle) bei Höhe g und Deckung c; Lichtbäume am Strand und in jungem Wald [G]. */
    static double anteil(int s, double g, double jung, double c) {
        double erle = Math.min(0.9, 0.08 + 0.4 * smooth(20, 3, g) + 0.6 * jung);
        double hem = (1 - erle) * (0.3 + 0.4 * smooth(120, 450, g));
        double sit = 1 - erle - hem;
        return c * (s == 0 ? sit : s == 1 ? hem : erle);
    }

    /**
     * Altbestand 1958 (Anteil der Art, Summe ≤ 1) aus dem Gelände [G]: geschlossener Küstenregenwald bis rund 650 m,
     * darüber lichter bis 900 m, nicht an Hängen über 45°, nicht auf dem Strand unter 1,5 m.
     */
    public double bestand1958(double x, double z, int s) {
        double g = grund1958(x, z);
        if (!(g > 1.5)) return 0;
        double c = 0.92 * smooth(900, 600, g) * (1 - smooth(42, 58, hangDeg(x, z, true))) * smooth(1.5, 4, g);
        return anteil(s, g, 0, c);
    }

    /**
     * Dichte des Walds für {@code forest} (Anteil je Art): im Band 1958 der Altbestand (die Zustände der Wiederbewaldung
     * gehen darüber), sonst aus der Kronenhöhe heute (Deckung ab 2 bis 12 m, niedrige Kronen eher Erle), ohne Kronenhöhe
     * wie der Altbestand. Kein Wald im Wasser und im Rückbau des Gilbert Inlet.
     */
    public double dichte(double x, double z, int s) {
        double[] d = new double[3];
        dichte(x, z, d);
        return d[s];
    }

    /** Wie {@link #dichte(double, double, int)}, alle drei Arten auf einmal nach d. */
    public void dichte(double x, double z, double[] d) {
        d[0] = d[1] = d[2] = 0;
        double e = e(x), n = n(z);
        if (boden.wasserHeute(e, n)) return;
        double gh = grundHeute(x, z);
        // W3c: Rückbau im Bild weich (Boden1958.rueckbauBild); kein Wald, wo er den Grund merklich senkt oder unter Wasser legt
        if (Boden1958.rueckbauGewicht(e, n, gh) > 0.04 || Boden1958.rueckbauBild(e, n, gh) < 2) return;
        if (Boden1958.gletscher(e, n) > 0.2 && gh > Boden1958.GLETSCHER_UNTER - 6 && gh < 160) return;   // W3c: auf der Zunge Eis, kein Wald
        double c = krone(x, z);
        if (imBand(x, z) || Double.isNaN(c)) {
            for (int s = 0; s < 3; s++) d[s] = bestand1958(x, z, s);
            return;
        }
        if (!(gh > 1)) return;
        double cov = 0.95 * smooth(2, 12, c), jung = smooth(20, 6, c) * 0.6;
        for (int s = 0; s < 3; s++) d[s] = anteil(s, gh, jung, cov);
    }

    /** Dichte über ein Quadrat (Zellmitten, Schichten = Arten, 8 Bit wie die Textur), auf allen Kernen. */
    public float[] dichteGitter(double x0, double z0, double size, int n) {
        float[] v = new float[n * n * 3];
        java.util.stream.IntStream.range(0, n).parallel().forEach(j -> {
            double[] d = new double[3];
            for (int i = 0; i < n; i++) {
                dichte(x0 + (i + 0.5) * size / n, z0 + (j + 0.5) * size / n, d);
                for (int s = 0; s < 3; s++) v[(s * n + j) * n + i] = Math.round(Math.max(0, Math.min(1, d[s])) * 255) / 255f;
            }
        });
        return v;
    }

    /** Grund heute über ein Quadrat (Zellmitten; im Wasser der Pegel 0), auf allen Kernen. */
    public float[] grundGitter(double x0, double z0, double size, int n) {
        float[] v = new float[n * n];
        java.util.stream.IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                double x = x0 + (i + 0.5) * size / n, z = z0 + (j + 0.5) * size / n, g = grundHeute(x, z);
                v[j * n + i] = (float) (Double.isNaN(g) ? 0 : Math.max(0, g));
            }
        });
        return v;
    }

    // ------------------------------------------------------------------ Wiederbewaldung

    /** Rechnet Störung und Jahre über das Band (Szene, Plätze je 6 m); einmal, dann aus dem Speicher. */
    public synchronized Succession succession(double wuchsFaktor, int jahre) {
        if (su != null && su.jahre() >= jahre && Math.abs(su.arten.get(0).k / Art.sitkaFichte().k - wuchsFaktor) < 1e-9) return su;
        double[] u = band.umriss(800);
        double[] a = Site.fromUtm(u[0], u[3]), b = Site.fromUtm(u[2], u[1]);
        Succession s = Succession.covering(a[0], a[1], b[0], b[1], ABSTAND, arten(wuchsFaktor), 1958);
        s.altHoehe = alt;
        s.stoeren(this::grund1958, this::stand, this::bestand1958);
        s.rechnen(jahre);
        su = s;
        return s;
    }

    public Succession succession() { return succession(wuchs, JAHRE); }

    /** Jahr der Wiederbewaldung zu einem Datum (0 ab dem 10.7.1958, ganze Jahre). */
    public static int jahr(java.time.LocalDate d) {
        int y = d.getYear() - 1958;
        if (d.getMonthValue() < 7 || (d.getMonthValue() == 7 && d.getDayOfMonth() < 10)) y--;
        return Math.max(0, y);
    }
}
