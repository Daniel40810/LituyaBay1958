package com.dan.lituya.tools;

import com.dan.dustcloud.DustCloud;
import com.dan.landslide.Landslide;
import com.dan.landslide.Presets;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Die Staubwolke des Felssturzes (LituyaBay1958 Phase 12, Werkbuch 5.4, 9.2; Ant-Ziel {@code staubwolke}). Verdrahtet
 * zwei Packages, die sich nicht kennen: {@code landslide} rechnet den Sturz am Gilbert Inlet (Preset mit Platte), und
 * jede Zelle des Stroms über dem Wasser, die schneller als 10 m/s ist, gibt Staub an {@code dustcloud}:
 * k · h · |u| · dx² Feststoff je Sekunde (Abrieb, k = 7e-6 je m [G], zusammen rund 1 % des Volumens), in Wolke mit
 * 1e-3 Staubanteil [G], halb so schnell wie der Strom. Die Wolke läuft über den Inlet an den Gegenhang, verdünnt und
 * setzt sich ab; leichter Wind talaus 2 m/s [G: die Nacht war klar und ruhig]. Gelände wie das Preset (Wand 40°,
 * Inlet 122 m tief, Gegenhang 45° ab 1 220 m, Flanken 30°), nach außen fortgesetzt. Argumente: Bild, Zeit (s),
 * Qualität des Sturzes (LOW, MEDIUM).
 */
public final class Staubwolke {
    private Staubwolke() { }

    static final double XA = -1500, XB = 2500, ZA = -2000, ZB = 2000, K = 7e-6, C0 = 1e-3;

    /** Gelände des Presets {@code gilbertInlet1958Terrain}, nach außen fortgesetzt, höchstens 1 200 m; Wasser auf 0. */
    static double terrain(double x, double z) {
        double ta = Math.tan(Math.toRadians(Presets.LITUYA_SLOPE)), toe = Presets.LITUYA_DEPTH / ta, rise = Presets.LITUYA_OPPOSITE - Presets.LITUYA_DEPTH;
        double w = 480 + 140 * Math.max(0, Math.min(1, x / 150));
        double b = (x <= toe ? -x * ta : x < rise ? -Presets.LITUYA_DEPTH : -Presets.LITUYA_DEPTH + (x - rise)) + Math.max(0, Math.abs(z) - w) * Math.tan(Math.toRadians(30));
        return Math.min(1200, b);
    }

    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "docs/status/Staubwolke.png");
        double tEnd = a.length > 1 ? Double.parseDouble(a[1]) : 300;
        Landslide.Quality q = a.length > 2 ? Landslide.Quality.valueOf(a[2]) : Landslide.Quality.LOW;
        Landslide s = Presets.gilbertInlet1958(q);
        DustCloud c = new DustCloud(XA, ZA, 160, 160, 25, DustCloud.rockDust());
        c.name = "Staub am Gilbert Inlet";
        c.wind = (x, z, t) -> new double[]{0, 2};
        c.build(new DustCloud.Ground() {
            public double height(double x, double z) { return Math.max(0, terrain(x, z)); }

            public boolean water(double x, double z) { return terrain(x, z) < 0; }
        });
        long t0 = System.nanoTime();
        double[] mark = {60, 120, tEnd};
        int m = 0;
        double dustTotal = 0;
        StringBuilder log = new StringBuilder();
        while (c.time() < tEnd) {
            double dt;
            if (s.time() < 60 && !s.atRest()) {
                dt = s.step();
                // Staub aus jeder schnellen Zelle über dem Wasser
                for (int j = 0; j < s.nz; j++)
                    for (int i = 0; i < s.nx; i++) {
                        int cc = s.index(i, j);
                        if (s.underWater(cc) || s.thickness(cc) < 0.5) continue;
                        double sp = s.speed(cc);
                        if (sp < 10) continue;
                        double dust = K * s.thickness(cc) * sp * s.dx * s.dx * dt;
                        double[] v = s.velocity(cc);
                        if (c.inject(s.cx(i), s.cz(j), dust, dust / C0, 0.5 * v[0], 0.5 * v[1])) dustTotal += dust;
                    }
                // die Wolke folgt in ihren eigenen Schritten
                double tc = s.time();
                while (c.time() < tc) c.step();
            } else c.step();
            if (m < mark.length && c.time() >= mark[m]) {
                double[] u = c.budget(), e = c.extent(5);
                log.append(String.format(Locale.GERMAN, "  t %4.0f s: Staub in der Luft %8.0f m³, abgesetzt an Land %8.0f m³, im Wasser %8.0f m³, hinaus %7.0f m³; Wolke über 5 m auf %.1f km², Sicht über dem Inlet (700, 0) %s, Oberkante am Gegenhang (1 500, 0) %s%n",
                        c.time(), u[0], u[1], u[2], u[3], e[1] / 1e6, vis(c.visibility(700, 0)), top(c.top(1500, 0))));
                m++;
            }
        }
        double ti = (System.nanoTime() - t0) / 1e9;
        System.out.println(c.describe());
        System.out.printf(Locale.GERMAN, "Sturz (%s, %.0f m): %.1f Mio. m³, %.0f s gerechnet; Staub eingebracht %.0f m³ (%.2f %% des Volumens, k %.0e je m [G]), Wolke mit %.0e Staubanteil%n",
                q, s.dx, s.volume() / 1e6, s.time(), dustTotal, 100 * dustTotal / Presets.LITUYA_VOLUME, K, C0);
        System.out.print(log);
        double[] u = c.budget();
        System.out.printf(Locale.GERMAN, "  Bilanz: %.9f des Eingebrachten; %d Schritte der Wolke, %.0f s Rechnung%n", (u[0] + u[1] + u[2] + u[3] + u[4]) / u[5], c.steps(), ti);
        paint(c, out);
        System.out.println("Bild: " + out);
    }

    static String vis(double v) { return Double.isInfinite(v) ? "frei" : v < 1 ? "unter 1 m" : String.format(Locale.GERMAN, "%.0f m", v); }

    static String top(double v) { return Double.isNaN(v) ? "keine Wolke" : String.format(Locale.GERMAN, "%.0f m", v); }

    static void paint(DustCloud c, File out) throws Exception {
        int w = 900, h = 900, sw = 700;
        BufferedImage im = new BufferedImage(w + sw + 30, h + 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(11, 17, 24));
        g.fillRect(0, 0, im.getWidth(), im.getHeight());
        double s = w / (XB - XA);
        for (int py = 0; py < h; py += 3)
            for (int px = 0; px < w; px += 3) {
                double x = XA + (px + 1.5) / s, z = ZB - (py + 1.5) / s, b = terrain(x, z);
                double r, gg, bb;
                if (b < 0) { r = 30; gg = 80 + b / 3; bb = 120 + b / 4; }
                else { double k = Math.min(1, b / 1200); r = 60 + 80 * k; gg = 80 + 60 * k; bb = 55 + 70 * k; }
                double tau = c.opticalDepth(x, z), al = 0.9 * (1 - Math.exp(-tau / 400)), dep = Math.min(1, c.deposit(x, z) * 500);
                r = r * (1 - al) + 210 * al; gg = gg * (1 - al) + 195 * al; bb = bb * (1 - al) + 170 * al;
                r = r * (1 - 0.4 * dep) + 150 * 0.4 * dep;
                g.setColor(new Color((int) Math.max(0, Math.min(255, r)), (int) Math.max(0, Math.min(255, gg)), (int) Math.max(0, Math.min(255, bb))));
                g.fillRect(px, py, 3, 3);
            }
        // Schnitt z = 0
        int sx = w + 20, sy = 40, sh = 500;
        g.setColor(new Color(20, 28, 38));
        g.fillRect(sx, sy, sw, sh);
        Path2D gp = new Path2D.Double(), cp = new Path2D.Double();
        for (int i = 0; i <= 200; i++) {
            double x = XA + (XB - XA) * i / 200, b = Math.max(0, terrain(x, 0)), t = c.top(x, 0);
            double X = sx + sw * i / 200.0, Y = sy + sh - sh * (b + 200) / 1600, T = Double.isNaN(t) ? Y : sy + sh - sh * (t + 200) / 1600;
            if (i == 0) { gp.moveTo(X, Y); cp.moveTo(X, T); } else { gp.lineTo(X, Y); cp.lineTo(X, T); }
        }
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(150, 170, 120));
        g.draw(gp);
        g.setColor(new Color(240, 200, 110));
        g.draw(cp);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(140, 156, 170));
        g.drawRect(sx, sy, sw, sh);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString("Schnitt durch die Mitte des Inlet (z = 0): Grund und Oberkante der Wolke, −200 bis 1 400 m", sx, sy + sh + 18);
        double[] u = c.budget();
        String[] lab = {"in der Luft", "an Land", "im Wasser", "hinaus"};
        for (int k = 0; k < 4; k++) g.drawString(String.format(Locale.GERMAN, "%-12s %,10.0f m³", lab[k], u[k]), sx, sy + sh + 50 + 20 * k);
        g.setColor(new Color(230, 234, 238));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString(String.format(Locale.GERMAN, "Staubwolke am Gilbert Inlet · landslide → dustcloud · t = %.0f s · von oben: optische Dicke, Wasser blau", c.time()), 12, h + 30);
        g.dispose();
        ImageIO.write(im, "png", out);
    }
}
