package com.dan.lituya.tools;

import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Stufe;
import com.dan.lituya.scene.Wald1958;
import com.dan.succession.Boden;
import com.dan.succession.Succession;
import com.dan.succession.Wellenwurf;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * Karte des Walds (Phase 14a; Ant-Ziel {@code waldkarte}): Kronenhöhe heute ({@code daten/wald}), das Band der Welle
 * 1958 (gezogen auf die Belege) mit der Folge je Platz, im Jahr {@code jahr} der Boden, das Treibgut am Morgen
 * ({@code daten/ereignis/treibgut}) und die Zahlen. Argumente: daten, PNG, Jahr (0: Folgen der Welle).
 */
public final class Waldkarte {
    private Waldkarte() { }

    static final Color[] FOLGE = {new Color(60, 150, 60), new Color(150, 190, 70), new Color(225, 225, 210), new Color(240, 150, 40),
            new Color(150, 90, 40), new Color(220, 40, 40)};

    public static void main(String[] a) throws Exception {
        File daten = new File(a.length > 0 ? a[0] : "daten");
        File png = new File(a.length > 1 ? a[1] : "docs/status/Wald1958.png");
        int jahr = a.length > 2 ? Integer.parseInt(a[2]) : 0;
        Wald1958 w = new Wald1958(daten, true);
        Succession su = w.succession();
        double px = 15;
        int W = (int) (Site.KERN.breite() / px), H = (int) (Site.KERN.hoehe() / px);
        BufferedImage img = new BufferedImage(W, H + 120, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                double e = Site.KERN.eMin() + (x + 0.5) * px, n = Site.KERN.nMax() - (y + 0.5) * px;
                double[] s = Site.fromUtm(e, n);
                double c = w.krone(s[0], s[1]), g = w.grundHeute(s[0], s[1]);
                int rgb;
                if (w.boden.wasserHeute(e, n)) rgb = 0x0c1a30;
                else if (Double.isNaN(c)) rgb = g > 600 ? 0xb4b8c0 : 0x5a5a5a;
                else {
                    double t = Math.min(1, c / 30);
                    rgb = ((int) (40 + 60 * (1 - t)) << 16) | ((int) (60 + 150 * t) << 8) | (int) (30 + 20 * t);
                }
                img.setRGB(x, y, rgb);
            }
        int[] nFolge = new int[Wellenwurf.Folge.values().length];
        for (int k = 0; k < su.band(); k++) {
            double[] l = su.lage(k);
            double xs = (l[0] + 0.5) * su.spacing, zs = (l[1] + 0.5) * su.spacing;
            double[] u = Site.toUtm(xs, zs);
            int x = (int) ((u[0] - Site.KERN.eMin()) / px), y = (int) ((Site.KERN.nMax() - u[1]) / px);
            if (x < 0 || y < 0 || x >= W || y >= H) continue;
            Wellenwurf.Folge f = su.folge(k);
            nFolge[f.ordinal()]++;
            Color col;
            if (jahr == 0) col = FOLGE[f.ordinal()];
            else {
                double h = su.hoehe(k, jahr);
                Boden b = su.bodenArt(k, jahr);
                col = h >= 2 ? new Color((int) (200 - 4 * Math.min(40, h)), (int) (120 + 3 * Math.min(40, h)), 60)
                        : b == Boden.FELS ? new Color(150, 150, 150) : b == Boden.MINERAL ? new Color(170, 130, 90) : new Color(90, 110, 60);
            }
            img.setRGB(x, y, col.getRGB());
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        File tg = new File(daten, "ereignis/treibgut");
        int stamm = 0;
        if (jahr == 0 && new File(tg, "staemme.tsv").isFile()) {
            java.util.Properties p = new java.util.Properties();
            try (var r = Files.newBufferedReader(new File(tg, "TREIBGUT.txt").toPath())) { p.load(r); }
            float[] t = Stufe.lesen(new File(tg, "teppich_25m.r32"));
            int nx = Integer.parseInt(p.getProperty("nx").trim()), nz = Integer.parseInt(p.getProperty("nz").trim());
            double x0 = Double.parseDouble(p.getProperty("x0").trim()), z0 = Double.parseDouble(p.getProperty("z0").trim()), dz = Double.parseDouble(p.getProperty("zelle").trim());
            for (int j = 0; j < nz; j++)
                for (int i = 0; i < nx; i++) {
                    float c = t[j * nx + i] + t[nx * nz + j * nx + i];
                    if (c < 0.01f) continue;
                    double[] u = Site.toUtm(x0 + (i + 0.5) * dz, z0 + (j + 0.5) * dz);
                    int x = (int) ((u[0] - Site.KERN.eMin()) / px), y = (int) ((Site.KERN.nMax() - u[1]) / px);
                    int al = (int) Math.min(220, 60 + 400 * c);
                    g.setColor(new Color(225, 200, 140, al));
                    g.fillRect(x - 1, y - 1, (int) Math.ceil(dz / px) + 1, (int) Math.ceil(dz / px) + 1);
                }
            g.setColor(Color.WHITE);
            List<String> z = Files.readAllLines(new File(tg, "staemme.tsv").toPath());
            for (int k = 1; k < z.size(); k++) {
                String[] f = z.get(k).split("\t");
                double xs = Double.parseDouble(f[0]), zs = Double.parseDouble(f[1]), yaw = Double.parseDouble(f[2]), len = Double.parseDouble(f[3]);
                double[] u = Site.toUtm(xs, zs);
                double x = (u[0] - Site.KERN.eMin()) / px, y = (Site.KERN.nMax() - u[1]) / px, hx = Math.cos(yaw) * len / 2 / px, hy = Math.sin(yaw) * len / 2 / px;
                g.drawLine((int) (x - hx), (int) (y - hy), (int) (x + hx), (int) (y + hy));
                stamm++;
            }
        }
        // Belege
        g.setStroke(new BasicStroke(2));
        g.setColor(Color.CYAN);
        for (double[] b : new double[][]{{354_234, 6_505_536, 524}, {354_602.58, 6_503_599.57, 208}}) {
            int x = (int) ((b[0] - Site.KERN.eMin()) / px), y = (int) ((Site.KERN.nMax() - b[1]) / px);
            g.drawOval(x - 7, y - 7, 14, 14);
            g.drawString(String.format(Locale.GERMAN, "%.0f m", b[2]), x + 10, y - 6);
        }
        g.setColor(new Color(20, 20, 24));
        g.fillRect(0, H, W, 120);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.setColor(Color.WHITE);
        Succession.Statistik st = su.statistik(jahr);
        g.drawString(String.format(Locale.GERMAN, "Wald der Bucht · Jahr %d nach der Welle (%d) · %s", jahr, 1958 + jahr, w.quelle), 12, H + 22);
        g.drawString(su.zeile(jahr), 12, H + 44);
        StringBuilder sb = new StringBuilder("Folgen der Welle: ");
        for (Wellenwurf.Folge f : Wellenwurf.Folge.values()) sb.append(f.text).append(String.format(Locale.GERMAN, " %,d · ", nFolge[f.ordinal()]));
        sb.append(String.format(Locale.GERMAN, "Fläche %.1f km²", su.band() * su.spacing * su.spacing / 1e6));
        g.drawString(sb.toString(), 12, H + 66);
        int lx = 12;
        for (Wellenwurf.Folge f : Wellenwurf.Folge.values()) {
            g.setColor(FOLGE[f.ordinal()]);
            g.fillRect(lx, H + 80, 14, 14);
            g.setColor(Color.WHITE);
            g.drawString(f.text, lx + 18, H + 92);
            lx += 120;
        }
        g.drawString(jahr == 0 ? String.format(Locale.GERMAN, "Treibgut am Morgen: %,d Stämme (weiß), Holzteppich (sandfarben) · Kronenhöhe heute grün · Belege 1958 türkis", stamm)
                : "Jahr > 0: Höhe der Bäume (gelb → grün), Fels grau, Mineralboden braun, Waldboden oliv", lx + 20, H + 92);
        g.dispose();
        png.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", png);
        System.out.println("waldkarte: " + png + " · " + su.zeile(jahr));
    }
}
