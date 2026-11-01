package com.dan.lituya.tools;

import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Stufe;
import com.dan.route.TravelGrid;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Isochronen der Welle über dem echten Grund (Phase 7, Werkbuch 7.1 und 9.5; Ant-Ziel {@code isochronenbild}): die
 * Laufzeit der Front vom Gilbert Inlet bis zum Eingang mit {@link TravelGrid} aus {@code lib/Renderer.jar} auf 20 m und 16
 * Nachbarn, über dem Grund heute ({@code daten/grund/heute_4m.r32}, auf 20 m gemittelt) und 1940 (heute, wo die
 * Vermessung von 1940 Lotungen hat, deren Fläche {@code 1940_20m.r32}), je linear (c = √(g h)) und nichtlinear
 * (c = √(g (h + a)), a = 30 + 122·e^(−d/1 500) m mit d dem Abstand von der Einschlaglinie [G]).
 * <p>
 * Wasserstand: Mittelwasser, +0,59 m über NAVD88 (MLLW 1,157 m unter NAVD88 aus {@link Grund}, MSL 1,747 m über MLLW);
 * die Tide am Abend des 9. Juli 1958 ist noch offen (Phase 0b) [G]. Quelle: alle Wasserzellen am Ufer (Land in 60 m)
 * höchstens 412 m vom Wasserpunkt am Gilbert Inlet, eine Einschlaglinie von rund 823 m (FRITZ01) [G: Lage].
 * <p>
 * Schreibt {@code docs/status/Isochronen.png} (zwei Bilder: heute, 1940; Isochronen alle 10 s, jede Minute kräftig,
 * gestrichelt nichtlinear) und die Ankunftszeiten an den Orten auf die Konsole, mit dem Handwert aus 9.5 (rund 300 bis
 * 350 s zum Eingang). Diese Rechnung ist die Zweitrechnung, gegen die {@code LB_NET_WELLE} in Phase 7a geprüft wird.
 */
public final class Isochronenbild {
    private Isochronenbild() { }

    /** Raster des Grunds: Nordwestecke, 1 200 × 900 Zellen zu 20 m (wie {@code 1940_20m.r32}). */
    public static final double E_NW = 340_000, N_NW = 6_512_000, ZELLE = 20;
    public static final int BREITE = 1200, HOEHE = 900;
    /** Mittelwasser über NAVD88 (m) und kleinste offene Tiefe. */
    public static final double MSL = 1.747 - Grund.MLLW_UNTER_NAVD88, MIN_TIEFE = 0.5;
    /** Halbe Breite der Einschlaglinie (m), FRITZ01: 823 m. */
    public static final double HALBE_LINIE = 411.5;

    /** Ein Lauf: Name, Gitter, Zahl der Quellen. */
    public record Lauf(String name, TravelGrid gitter, int quellen, double ms) { }

    /** Grund heute auf 20 m: Mittel der Wasserzellen je 5 × 5; NaN, wo weniger als die Hälfte Wasser ist. */
    public static float[] heute20(File daten) throws IOException {
        float[] g4 = Stufe.lesen(new File(daten, "grund/heute_4m.r32"));
        if (g4 == null) throw new IOException("daten/grund/heute_4m.r32 fehlt (ant aufbereitung -Dschritte=grund)");
        int w4 = BREITE * 5;
        float[] out = new float[BREITE * HOEHE];
        for (int j = 0; j < HOEHE; j++)
            for (int i = 0; i < BREITE; i++) {
                double s = 0;
                int n = 0;
                for (int b = 0; b < 5; b++)
                    for (int a = 0; a < 5; a++) {
                        float v = g4[(j * 5 + b) * w4 + i * 5 + a];
                        if (Float.isFinite(v)) { s += v; n++; }
                    }
                out[j * BREITE + i] = n >= 13 ? (float) (s / n) : Float.NaN;
            }
        return out;
    }

    /** Grund 1940: heute, wo 1940 Lotungen hat deren Fläche. */
    public static float[] mit1940(float[] heute, File daten) throws IOException {
        float[] g = Stufe.lesen(new File(daten, "grund/1940_20m.r32"));
        if (g == null) throw new IOException("daten/grund/1940_20m.r32 fehlt");
        float[] out = heute.clone();
        for (int k = 0; k < out.length; k++) if (Float.isFinite(g[k])) out[k] = g[k];
        return out;
    }

    static double cx(int i) { return E_NW + (i + 0.5) * ZELLE; }
    static double cn(int j) { return N_NW - (j + 0.5) * ZELLE; }

    /** Gitter über dem Grund (Höhen NAVD88, NaN Land), Rechnung mit Amplitude nach Abstand von (pe, pn) oder linear. */
    public static Lauf rechnen(String name, float[] grund, boolean nichtlinear) {
        long t0 = System.nanoTime();
        // Zellen im Gitter: Zeile j nach Süden, z = Süd; x0, z0 im eigenen Raster (Ost ab E_NW, Süd ab N_NW)
        TravelGrid g = new TravelGrid(BREITE, HOEHE, 0, 0, ZELLE);
        int[] p = punkt(grund);
        double pe = cx(p[0]), pn = cn(p[1]);
        for (int k = 0; k < grund.length; k++) {
            double h = MSL - grund[k];
            if (!Float.isFinite(grund[k]) || h < MIN_TIEFE) { g.speed[k] = 0; continue; }
            double a = 0;
            if (nichtlinear) {
                double d = Math.hypot(cx(k % BREITE) - pe, cn(k / BREITE) - pn);
                a = 30 + 122 * Math.exp(-d / 1500);
            }
            g.speed[k] = (float) TravelGrid.waveSpeed(h, a);
        }
        g.stencil = TravelGrid.Stencil.N16;
        List<TravelGrid.Source> q = quellen(g, p);
        g.solve(q);
        return new Lauf(name, g, q.size(), (System.nanoTime() - t0) / 1e6);
    }

    /** Die Wasserzelle am nächsten zum Gilbert Inlet (GNIS). */
    static int[] punkt(float[] grund) {
        int bi = 0, bj = 0;
        double best = Double.MAX_VALUE;
        for (int j = 0; j < HOEHE; j++)
            for (int i = 0; i < BREITE; i++) {
                float v = grund[j * BREITE + i];
                if (!Float.isFinite(v) || MSL - v < MIN_TIEFE) continue;
                double d = Math.hypot(cx(i) - Site.GILBERT.e(), cn(j) - Site.GILBERT.n());
                if (d < best) { best = d; bi = i; bj = j; }
            }
        return new int[]{bi, bj};
    }

    /** Einschlaglinie: Wasserzellen am Ufer (Land in 3 Zellen) bis HALBE_LINIE vom Punkt. */
    static List<TravelGrid.Source> quellen(TravelGrid g, int[] p) {
        List<TravelGrid.Source> s = new ArrayList<>();
        int r = (int) Math.ceil(HALBE_LINIE / ZELLE);
        for (int j = p[1] - r; j <= p[1] + r; j++)
            for (int i = p[0] - r; i <= p[0] + r; i++) {
                if (!g.open(i, j) || Math.hypot(i - p[0], j - p[1]) * ZELLE > HALBE_LINIE) continue;
                boolean ufer = false;
                for (int b = -3; b <= 3 && !ufer; b++) for (int a = -3; a <= 3 && !ufer; a++) ufer = !g.open(i + a, j + b) && i + a >= 0 && j + b >= 0 && i + a < g.w && j + b < g.h;
                if (ufer) s.add(new TravelGrid.Source(i, j, 0));
            }
        if (s.isEmpty()) s.add(new TravelGrid.Source(p[0], p[1], 0));
        return s;
    }

    /** Früheste Ankunft in Wasserzellen bis r Meter um einen Ort (s); NaN, wenn keine. */
    public static double ankunft(TravelGrid g, double e, double n, double r) {
        int ci = (int) Math.floor((e - E_NW) / ZELLE), cj = (int) Math.floor((N_NW - n) / ZELLE), k = (int) Math.ceil(r / ZELLE);
        double best = Double.NaN;
        for (int j = cj - k; j <= cj + k; j++)
            for (int i = ci - k; i <= ci + k; i++) {
                if (i < 0 || j < 0 || i >= g.w || j >= g.h || Math.hypot(cx(i) - e, cn(j) - n) > r) continue;
                float t = g.arrival()[j * g.w + i];
                if (Float.isFinite(t) && !(t >= best)) best = t;
            }
        return best;
    }

    /** Orte für die Tabelle: Name, Ost, Nord, Suchradius (m). */
    static final Object[][] ORTE = {
            {"Sporn (gegenüber)", Site.SPORN.e(), Site.SPORN.n(), 500.0},
            {"Cenotaph Island", Site.CENOTAPH.e(), Site.CENOTAPH.n(), 700.0},
            {"Anchorage Cove", Site.ANCHORAGE.e(), Site.ANCHORAGE.n(), 400.0},
            {"Eingang (La Chaussee Spit)", Site.CHAUSSEE.e(), Site.CHAUSSEE.n(), 400.0},
    };

    public static void main(String[] a) throws Exception {
        File daten = new File(a.length > 0 ? a[0] : "daten"), aus = new File(a.length > 1 ? a[1] : "docs/status/Isochronen.png");
        System.out.println("Isochronen über dem Grund (Phase 7): com.dan.route.TravelGrid, 20 m, 16 Nachbarn (Gitterfehler höchstens "
                + String.format(Locale.GERMAN, "%.2f %%", TravelGrid.Stencil.N16.error() * 100) + ")");
        System.out.printf(Locale.GERMAN, "Wasserstand %.2f m über NAVD88 (Mittelwasser [G]), offen ab %.1f m Tiefe%n", MSL, MIN_TIEFE);
        float[] heute = heute20(daten), g1940 = mit1940(heute, daten);
        int lot = 0;
        float[] roh = Stufe.lesen(new File(daten, "grund/1940_20m.r32"));
        for (float v : roh) if (Float.isFinite(v)) lot++;
        Lauf[] l = {rechnen("heute linear", heute, false), rechnen("heute nichtlinear", heute, true),
                rechnen("1940 linear", g1940, false), rechnen("1940 nichtlinear", g1940, true)};
        int[] p = punkt(heute);
        System.out.printf(Locale.GERMAN, "Einschlaglinie: %d Wasserzellen am Ufer um E %.0f N %.0f (Gilbert Inlet, GNIS E %.0f N %.0f); 1940: %,d Zellen aus Lotungen%n",
                l[0].quellen(), cx(p[0]), cn(p[1]), Site.GILBERT.e(), Site.GILBERT.n(), lot);
        System.out.printf("%n  %-28s", "Ankunft (s)");
        for (Lauf x : l) System.out.printf("%20s", x.name());
        System.out.println();
        for (Object[] o : ORTE) {
            System.out.printf("  %-28s", o[0]);
            for (Lauf x : l) System.out.printf(Locale.GERMAN, "%20.0f", ankunft(x.gitter(), (double) o[1], (double) o[2], (double) o[3]));
            System.out.println();
        }
        System.out.printf("  %-28s", "Rechenzeit (ms)");
        for (Lauf x : l) System.out.printf(Locale.GERMAN, "%20.0f", x.ms());
        System.out.println();
        System.out.printf("  %-28s", "offene Zellen");
        for (Lauf x : l) System.out.printf(Locale.GERMAN, "%,20d", x.gitter().openCount());
        System.out.println();
        double ein = ankunft(l[0].gitter(), Site.CHAUSSEE.e(), Site.CHAUSSEE.n(), 400), einN = ankunft(l[1].gitter(), Site.CHAUSSEE.e(), Site.CHAUSSEE.n(), 400);
        System.out.printf(Locale.GERMAN, "%nHandwert (Werkbuch 9.5): 12 km bei h ≈ 130 m linear 350 s, nichtlinear 300 s. Gerechnet heute: %.0f s linear, %.0f s nichtlinear (%+.0f %% / %+.0f %%).%n",
                ein, einN, (ein / 350 - 1) * 100, (einN / 300 - 1) * 100);
        ImageIO.write(bild(l, heute, g1940), "png", aus);
        System.out.println("Bild: " + aus.getPath());
    }

    // ------------------------------------------------------------------ Bild

    /** Ausschnitt: die Bucht mit Eingang, E 342 500 bis 357 500, N 6 497 500 bis 6 508 500. */
    static final double AE0 = 342_500, AE1 = 357_500, AN0 = 6_497_500, AN1 = 6_508_500;
    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(140, 156, 170);

    static BufferedImage bild(Lauf[] l, float[] heute, float[] g1940) {
        int pw = 1050, ph = (int) Math.round(pw * (AN1 - AN0) / (AE1 - AE0));
        BufferedImage im = new BufferedImage(2 * pw, ph + 110, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, im.getWidth(), im.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int k = 0; k < 2; k++) {
            g.translate(k * pw, 0);
            tafel(g, l[2 * k], l[2 * k + 1], k == 0 ? heute : g1940, pw, ph, k == 0 ? "heute (USACE 2019, BlueTopo)" : "1940 (Lotungen H06581–83, sonst heute)");
            g.translate(-k * pw, 0);
        }
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString("LituyaBay1958 · Laufzeit der Front nach Fermat über dem Grund (com.dan.route.TravelGrid, 20 m, 16 Nachbarn, Gitterfehler ≤ 2,75 %). Isochronen alle 10 s, jede Minute kräftig;", 12, ph + 30);
        g.drawString(String.format(Locale.GERMAN, "durchgezogen linear c = √(g h), gestrichelt nichtlinear c = √(g (h + a)), a = 30 + 122·e^(−d/1 500) m [G]. Wasserstand Mittelwasser (%.2f m über NAVD88) [G]. Rot: Einschlaglinie am Gilbert Inlet (823 m).", MSL), 12, ph + 54);
        g.setColor(DIM);
        g.drawString("Keine Reflexion, kein Brechen, kein Auflaufen: nur die erste Ankunft. Zweitrechnung für LB_NET_WELLE (Phase 7a), Vergleich mit wave in Phase 13a.", 12, ph + 78);
        g.dispose();
        return im;
    }

    static void tafel(Graphics2D g, Lauf lin, Lauf non, float[] grund, int pw, int ph, String titel) {
        double sc = pw / (AE1 - AE0);
        int i0 = (int) ((AE0 - E_NW) / ZELLE), j0 = (int) ((N_NW - AN1) / ZELLE), w = (int) ((AE1 - AE0) / ZELLE), h = (int) ((AN1 - AN0) / ZELLE);
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                float v = grund[(j0 + j) * BREITE + i0 + i];
                int rgb;
                if (!Float.isFinite(v) || MSL - v < MIN_TIEFE) rgb = 0x262b25;
                else {
                    double t = Math.min(1, (MSL - v) / 220);
                    rgb = ((int) (34 - 26 * t) << 16) | ((int) (78 - 44 * t) << 8) | (int) (104 - 36 * t);
                }
                map.setRGB(i, j, rgb);
            }
        g.drawImage(map, 0, 0, pw, ph, null);
        java.awt.Shape clip = g.getClip();
        g.clipRect(0, 0, pw, ph);
        for (int pass = 0; pass < 2; pass++) {
            TravelGrid tg = (pass == 0 ? lin : non).gitter();
            for (int t = 10; t <= 900; t += 10) {
                boolean minute = t % 60 == 0;
                g.setColor(pass == 0 ? new Color(240, 200, 110, minute ? 255 : 120) : new Color(120, 210, 255, minute ? 230 : 90));
                g.setStroke(pass == 0 ? new BasicStroke(minute ? 2f : 0.9f) : new BasicStroke(minute ? 1.5f : 0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{5, 4}, 0));
                for (float[] z : tg.isochrones(t)) {
                    Path2D p = new Path2D.Double();
                    for (int k = 0; k < z.length; k += 2) {
                        double x = (E_NW + z[k] - AE0) * sc, y = (AN1 - (N_NW - z[k + 1])) * sc;
                        if (k == 0) p.moveTo(x, y); else p.lineTo(x, y);
                    }
                    g.draw(p);
                }
            }
        }
        // Einschlaglinie
        g.setColor(new Color(255, 80, 70));
        int[] p = punkt(grund);
        for (TravelGrid.Source s : quellen(lin.gitter(), p)) {
            double x = (cx(s.i()) - AE0) * sc, y = (AN1 - cn(s.j())) * sc;
            g.fillOval((int) x - 2, (int) y - 2, 5, 5);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (Object[] o : ORTE) {
            double e = (double) o[1], n = (double) o[2], r = (double) o[3];
            double x = (e - AE0) * sc, y = (AN1 - n) * sc;
            g.setColor(Color.WHITE);
            g.drawOval((int) x - 4, (int) y - 4, 8, 8);
            String s = String.format(Locale.GERMAN, "%s: %.0f s / %.0f s", o[0], ankunft(lin.gitter(), e, n, r), ankunft(non.gitter(), e, n, r));
            int tw = g.getFontMetrics().stringWidth(s);
            int tx = (int) Math.min(pw - tw - 6, x + 8), ty = (int) y + (((String) o[0]).startsWith("Anchorage") ? 18 : -6);
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(tx - 3, ty - 12, tw + 6, 16);
            g.setColor(INK);
            g.drawString(s, tx, ty);
        }
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(8, 8, 520, 44);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.drawString("Grund " + titel, 16, 28);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(INK);
        g.drawString(String.format(Locale.GERMAN, "%,d Zellen Wasser, %d Quellen · Ankunft linear / nichtlinear", lin.gitter().openCount(), lin.quellen()), 16, 46);
        g.setColor(new Color(255, 255, 255, 60));
        g.drawRect(0, 0, pw - 1, ph - 1);
        g.setClip(clip);
    }
}
