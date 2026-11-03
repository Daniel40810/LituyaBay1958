package com.dan.lituya.tools;

import com.dan.lituya.scene.Stufe;
import com.dan.lituya.scene.WegNetz;
import com.dan.lituya.scene.WellenNetz;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * {@code LB_NET_WEG} als Karte (Phase 16a): oben die Szene (50 m) mit den Geländeklassen, dem Kern und Washburns Route
 * 1934, darunter der Kern (20 m je Bildpunkt) mit den Gehzeiten bis eine Stunde von den Landeplätzen in Bändern zu
 * 15 min, dann die Tafel aus {@code WEG.txt}. Nach {@code docs/status/Weg.png}.
 * <p>Aufruf: {@code Wegkarte [daten] [png]}.
 */
public final class Wegkarte {
    private Wegkarte() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(140, 156, 170);
    /** Farben der Klassen (wie Klassen.NAME). */
    static final Color[] KF = {new Color(24, 52, 86), new Color(50, 100, 160), new Color(196, 180, 140), new Color(130, 150, 90), new Color(110, 120, 80),
            new Color(80, 120, 60), new Color(36, 84, 44), new Color(120, 112, 104), new Color(150, 140, 125), new Color(200, 225, 240), new Color(240, 244, 248),
            new Color(150, 120, 200), new Color(60, 140, 230)};

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        File daten = new File(a.length > 0 ? a[0] : "daten");
        File png = new File(a.length > 1 ? a[1] : "docs/status/Weg.png");
        Klassen.Raster s = Klassen.SZENE, k = Klassen.KERN;
        byte[] ks = Klassen.lesen(daten, s), kk = Klassen.lesen(daten, k);
        float[] zs = Klassen.gelaende(daten, s), zk = Klassen.gelaende(daten, k);
        float[] zeit = Stufe.lesen(new File(new File(daten, WegNetz.ORDNER), "zeit_kern_10m.r32"));
        WegNetz w = new WegNetz();
        w.text = java.nio.file.Files.readString(new File(new File(daten, WegNetz.ORDNER), "WEG.txt").toPath());
        int H1 = s.h(), kw = k.w() / 2, kh = k.h() / 2, tafel = 190, W = s.w();
        BufferedImage im = new BufferedImage(W, H1 + kh + tafel, BufferedImage.TYPE_INT_RGB);
        // Szene
        for (int j = 0; j < s.h(); j++)
            for (int i = 0; i < s.w(); i++) im.setRGB(i, j, schatten(KF[ks[j * s.w() + i]], zs, s.w(), s.h(), i, j, s.zelle()).getRGB());
        // Kern (20 m je Bildpunkt) mit Gehzeit
        int ox = (W - kw) / 2;
        for (int j = 0; j < kh; j++)
            for (int i = 0; i < kw; i++) {
                int c = (2 * j) * k.w() + 2 * i;
                Color b = schatten(KF[kk[c]], zk, k.w(), k.h(), 2 * i, 2 * j, k.zelle());
                float t = zeit == null ? Float.NaN : zeit[c];
                if (!Float.isNaN(t)) {
                    int band = Math.min(3, (int) (t / 900));
                    Color f = new Color[]{new Color(255, 230, 90), new Color(255, 170, 60), new Color(240, 110, 60), new Color(200, 60, 90)}[band];
                    b = mix(b, f, 0.62);
                }
                im.setRGB(ox + i, H1 + j, b.getRGB());
            }
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, H1 + kh, W, tafel);
        g.fillRect(0, H1, ox, kh);
        g.fillRect(ox + kw, H1, W - ox - kw, kh);
        // Rahmen des Kerns in der Szene
        g.setColor(INK);
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{6, 4}, 0));
        g.drawRect((int) ((k.e0() - s.e0()) / s.zelle()), (int) ((s.n0() - k.n0()) / s.zelle()), (int) (k.w() * k.zelle() / s.zelle()), (int) (k.h() * k.zelle() / s.zelle()));
        // Washburn
        List<String[]> ws = WellenNetz.tsv(new File(new File(daten, WegNetz.ORDNER), "washburn.tsv"));
        Path2D p = new Path2D.Double();
        for (int q = 0; q < ws.size(); q++) {
            double x = (Double.parseDouble(ws.get(q)[2]) - s.e0()) / s.zelle(), y = (s.n0() - Double.parseDouble(ws.get(q)[3])) / s.zelle();
            if (q == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0, 0, 0, 150));
        g.draw(p);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 80, 60));
        g.draw(p);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        for (String[] z : ws)
            if (z.length > 7 && !z[7].isEmpty()) {
                int x = (int) ((Double.parseDouble(z[2]) - s.e0()) / s.zelle()), y = (int) ((s.n0() - Double.parseDouble(z[3])) / s.zelle());
                g.setColor(Color.WHITE);
                g.fillOval(x - 4, y - 4, 8, 8);
                g.setColor(Color.BLACK);
                g.drawString(z[7], x + 8, y - 5);
                g.setColor(Color.WHITE);
                g.drawString(z[7], x + 7, y - 6);
            }
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString("Kern (gestrichelt, darunter vergrößert): Gehzeit von den Landeplätzen, Bänder zu 15 min bis 1 h", 12, H1 + 18);
        // Tafel
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        g.drawString("LB_NET_WEG: Wie weit in einer Stunde vom Strand? Wie kam Washburn 1934 auf den Mount Crillon?", 14, H1 + kh + 26);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        int yy = H1 + kh + 50;
        for (String[] z : new String[][]{{"stunde.badger", "1 h Anchorage Cove"}, {"stunde.edrie", "1 h Edrie (Südufer)"}, {"washburn", "Washburn 1934"}}) {
            String v = w.wert(z[0]);
            g.setColor(INK);
            g.drawString(String.format("%-20s %s", z[1], v == null ? "–" : v), 14, yy);
            yy += 18;
        }
        g.setColor(DIM);
        int lx = 14;
        yy += 6;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int c = 0; c < Klassen.NAME.length; c++) {
            g.setColor(KF[c]);
            g.fillRect(lx, yy - 10, 12, 12);
            g.setColor(DIM);
            String t = Klassen.NAME[c] + (Double.isNaN(Klassen.ZUSCHLAG[c]) ? " (gesperrt)" : String.format(Locale.GERMAN, " ×%.1f", Klassen.ZUSCHLAG[c]));
            g.drawString(t, lx + 16, yy);
            lx += 22 + g.getFontMetrics().stringWidth(t);
            if (lx > W - 140) { lx = 14; yy += 18; }
        }
        g.dispose();
        png.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(im, "png", png);
        System.out.printf(Locale.GERMAN, "Wegkarte nach %s (%d × %d)%n", png.getPath(), im.getWidth(), im.getHeight());
    }

    static Color schatten(Color b, float[] z, int W, int H, int i, int j, double d) {
        double dzx = (z[j * W + Math.min(W - 1, i + 1)] - z[j * W + Math.max(0, i - 1)]) / (2 * d), dzy = (z[Math.max(0, j - 1) * W + i] - z[Math.min(H - 1, j + 1) * W + i]) / (2 * d);
        double sh = Math.max(0.25, Math.min(1.15, 0.8 + (-dzx * 0.7 + dzy * 0.7) * 0.5 / Math.sqrt(1 + dzx * dzx + dzy * dzy)));
        return new Color(Math.min(255, (int) (b.getRed() * sh)), Math.min(255, (int) (b.getGreen() * sh)), Math.min(255, (int) (b.getBlue() * sh)));
    }

    static Color mix(Color a, Color b, double f) {
        return new Color((int) (a.getRed() * (1 - f) + b.getRed() * f), (int) (a.getGreen() * (1 - f) + b.getGreen() * f), (int) (a.getBlue() * (1 - f) + b.getBlue() * f));
    }
}
