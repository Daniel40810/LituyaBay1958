package com.dan.lituya.tools;

import com.dan.debris.Debris;
import com.dan.wave.Bay;
import com.dan.wave.demo.BayDemo;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.Random;

/**
 * Treibgut aus der Welle (LituyaBay1958 Phase 12, Werkbuch 5.5, 9.7; Ant-Ziel {@code treibgut}). Verdrahtet zwei
 * Packages, die sich nicht kennen: {@code wave.Bay} rechnet die Fjordbucht der Demo (drei Gitter, Hügel 100 m im
 * nördlichen Arm wie nach einem Felssturz), {@code debris} bekommt daraus Tiefe und Strömung je Ort ({@link Debris.Water}).
 * An den Hängen des Arms liegen 300 Stämme an Land (1–30 m über dem Spiegel, wie die gebrochenen Bäume im Band); die
 * Welle läuft auf, hebt sie an und reißt sie mit, der Rückstrom trägt sie in den Arm. Argumente: Bild, Zeit (s).
 */
public final class Treibgut {
    private Treibgut() { }

    static final double XA = 4500, XB = 6600, ZA = 1100, ZB = 3000;

    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "docs/status/Treibgut.png");
        double tEnd = a.length > 1 ? Double.parseDouble(a[1]) : 60;
        Bay bay = BayDemo.fjordBay();
        Debris d = new Debris(new Debris.Water() {
            public double depth(double x, double z) { double h = bay.depthAt(x, z); return Double.isNaN(h) || h < 0.01 ? 0 : h; }

            public double[] current(double x, double z) { return bay.velocityAt(x, z); }
        });
        d.name = "Stämme am Arm";
        Random r = new Random(1958);
        int n = 0;
        while (n < 300) {
            double x = 5150 + 850 * r.nextDouble(), z = 1750 + 1050 * r.nextDouble(), b = BayDemo.fjord(x, z);
            if (b < 1 || b > 30) continue;
            double len = 15 + 20 * r.nextDouble(), dia = 0.4 + 0.5 * r.nextDouble();
            d.add(n % 10 == 0 ? Debris.Kind.CROWN : Debris.Kind.TRUNK, len, dia, x, z, Math.PI * r.nextDouble());
            n++;
        }
        double[] x0 = new double[n], z0 = new double[n], vmax = new double[n];
        for (int i = 0; i < n; i++) { x0[i] = d.logs().get(i).x; z0[i] = d.logs().get(i).z; }
        long t0 = System.nanoTime();
        while (bay.time() < tEnd) {
            double dt = bay.step();
            d.step(dt);
            for (int i = 0; i < n; i++) { Debris.Log l = d.logs().get(i); vmax[i] = Math.max(vmax[i], Math.hypot(l.vx, l.vz)); }
        }
        double ti = (System.nanoTime() - t0) / 1e9;
        int moved = 0, far = 0;
        double best = 0, sumV = 0;
        for (int i = 0; i < n; i++) {
            Debris.Log l = d.logs().get(i);
            double s = Math.hypot(l.x - x0[i], l.z - z0[i]);
            if (s > 5) moved++;
            if (s > 200) far++;
            best = Math.max(best, vmax[i]);
            sumV += vmax[i];
        }
        int[] c = d.census();
        System.out.println(bay.describe());
        System.out.printf(Locale.GERMAN, "%s nach %.0f s (%.1f s Rechnung, %d Teilschritte, %d Berührungen)%n", d.describe(), bay.time(), ti, d.steps(), d.contacts());
        System.out.printf(Locale.GERMAN, "  bewegt (über 5 m): %d von %d, über 200 m: %d; schnellstes Stück %.1f m/s, im Mittel höchstens %.1f m/s%n", moved, n, far, best, sumV / n);
        System.out.printf(Locale.GERMAN, "  am Ende: %d schwimmen, %d auf Grund, %d an Land; Pegel Arm %.1f m nach %.1f s%n", c[0], c[1], c[2], bay.gauges().get(0).max(), bay.gauges().get(0).timeOfMax());
        paint(bay, d, x0, z0, out);
        System.out.println("Bild: " + out);
    }

    static void paint(Bay bay, Debris d, double[] x0, double[] z0, File out) throws Exception {
        int w = 1400, h = (int) (w * (ZB - ZA) / (XB - XA));
        BufferedImage im = new BufferedImage(w, h + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double s = w / (XB - XA);
        for (int py = 0; py < h; py += 2)
            for (int px = 0; px < w; px += 2) {
                double x = XA + (px + 1) / s, z = ZB - (py + 1) / s, b = BayDemo.fjord(x, z), dep = bay.depthAt(x, z);
                Color col;
                if (!Double.isNaN(dep) && dep > 0.05) col = new Color(30, (int) Math.max(50, 110 - dep / 3), (int) Math.max(80, 150 - dep / 4));
                else { double k = Math.min(1, Math.max(0, b / 300)); col = new Color((int) (60 + 50 * k), (int) (80 + 30 * k), (int) (50 + 30 * k)); }
                g.setColor(col);
                g.fillRect(px, py, 2, 2);
            }
        for (int i = 0; i < d.logs().size(); i++) {
            Debris.Log l = d.logs().get(i);
            double[] e = l.ends();
            g.setColor(new Color(255, 255, 255, 50));
            g.setStroke(new BasicStroke(1f));
            g.draw(new Line2D.Double((x0[i] - XA) * s, (ZB - z0[i]) * s, (l.x - XA) * s, (ZB - l.z) * s));
            g.setColor(l.state == Debris.State.FLOATING ? new Color(230, 200, 150) : l.state == Debris.State.GROUNDED ? new Color(240, 200, 110) : new Color(200, 110, 90));
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double((e[0] - XA) * s, (ZB - e[1]) * s, (e[2] - XA) * s, (ZB - e[3]) * s));
        }
        g.setColor(new Color(11, 17, 24));
        g.fillRect(0, h, w, 60);
        g.setColor(new Color(230, 234, 238));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        int[] c = d.census();
        g.drawString(String.format(Locale.GERMAN, "Treibgut aus der Welle · wave.Bay → debris · t = %.0f s · %d schwimmen, %d auf Grund, %d an Land", bay.time(), c[0], c[1], c[2]), 12, h + 24);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(new Color(140, 156, 170));
        g.drawString("Fjordbucht der Demo (Arm, 2,5 m); Stämme hell = schwimmen, gelb = auf Grund, rot = an Land; feine Linie: Weg vom Hang", 12, h + 44);
        g.dispose();
        ImageIO.write(im, "png", out);
    }
}
