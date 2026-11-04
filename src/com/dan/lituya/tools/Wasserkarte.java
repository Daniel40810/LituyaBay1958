package com.dan.lituya.tools;

import com.dan.lituya.scene.Stufe;
import com.dan.lituya.scene.WasserNetz;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code LB_NET_WASSER} als Karte (Phase 16): die Szene in 50 m mit Schummerung, die Teileinzugsgebiete der Bucht farbig
 * (Gilbert Inlet, Crillon Inlet, Bucht), die übrigen grau, die Bäche nach ihrer Fläche, die Fließlinien der Gletscher,
 * die Seen, darunter die Tafel aus {@code WASSER.txt}. Nach {@code docs/status/Wasser.png}.
 * <p>Aufruf: {@code Wasserkarte [daten] [png]}.
 */
public final class Wasserkarte {
    private Wasserkarte() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(140, 156, 170), MEER = new Color(24, 52, 86);
    static final Color[] ZIEL = {new Color(232, 96, 80), new Color(236, 168, 70), new Color(220, 210, 110), new Color(120, 130, 140), new Color(90, 96, 104)};
    static final double PX = 1.0 / 50;   // Bildpunkte je m

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        File daten = new File(a.length > 0 ? a[0] : "daten");
        File png = new File(a.length > 1 ? a[1] : "docs/status/Wasser.png");
        WasserNetz w = WasserNetz.lesen(daten);
        File d = new File(daten, WasserNetz.ORDNER);
        int W5 = Wasser.W / 5, H5 = Wasser.H / 5, tafel = 230;
        // Gelände in 50 m (Mitte des Blocks) für die Schummerung
        float[] h = new float[W5 * H5];
        for (double[] k : Stufe.SZENE.kacheln()) {
            float[] t = Stufe.lesen(Stufe.SZENE.datei(daten, k[0], k[1]));
            if (t == null) continue;
            int i0 = (int) Math.round((k[0] - Wasser.E0) / 50), j0 = (int) Math.round((Wasser.N0 - (k[1] + Stufe.SZENE.kachel())) / 50);
            for (int jj = 0; jj < Stufe.N / 10; jj++)
                for (int ii = 0; ii < Stufe.N / 10; ii++) {
                    int i = i0 + ii, j = j0 + jj;
                    if (i < 0 || j < 0 || i >= W5 || j >= H5) continue;
                    h[j * W5 + i] = t[(10 * jj + 5) * Stufe.N + 10 * ii + 5];
                }
        }
        byte[] lb = Files.readAllBytes(new File(d, "einzug_50m.i32").toPath());
        int[] lab = new int[W5 * H5];
        ByteBuffer.wrap(lb).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(lab);
        // Gebiet → Ziel
        Map<Integer, Integer> ziel = new HashMap<>();
        for (int i = 0; i < w.n; i++) if (w.ebene[i] == 2) {
            List<Integer> l = w.traceOut(i, 2);
            String z = w.name[l.get(l.size() - 1)];
            ziel.put(i + 1, java.util.Arrays.asList(WasserNetz.ZIELE).indexOf(z));
        }
        BufferedImage im = new BufferedImage(W5, H5 + tafel, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < H5; j++)
            for (int i = 0; i < W5; i++) {
                int c = j * W5 + i;
                if (lab[c] == 0) { im.setRGB(i, j, MEER.getRGB()); continue; }
                double dzx = (h[j * W5 + Math.min(W5 - 1, i + 1)] - h[j * W5 + Math.max(0, i - 1)]) / 100, dzy = (h[Math.max(0, j - 1) * W5 + i] - h[Math.min(H5 - 1, j + 1) * W5 + i]) / 100;
                double sh = Math.max(0, Math.min(1, 0.55 + (-dzx * 0.7 + dzy * 0.7) * 0.6 / Math.sqrt(1 + dzx * dzx + dzy * dzy)));
                int zl = ziel.getOrDefault(lab[c], 4);
                Color b = ZIEL[zl];
                boolean rand = (i > 0 && lab[c - 1] != lab[c]) || (j > 0 && lab[c - W5] != lab[c]);
                double m = zl <= 2 ? 0.55 : 0.25;
                int r = (int) (b.getRed() * m * sh + 40 * sh), gg = (int) (b.getGreen() * m * sh + 40 * sh), bb = (int) (b.getBlue() * m * sh + 40 * sh);
                if (rand) { r = r * 3 / 5; gg = gg * 3 / 5; bb = bb * 3 / 5; }
                im.setRGB(i, j, new Color(Math.min(255, r), Math.min(255, gg), Math.min(255, bb)).getRGB());
            }
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, H5, W5, tafel);
        // Bäche
        Map<Integer, double[]> lin = new HashMap<>();
        for (String[] z : WellenNetz.tsv(new File(d, "linien.tsv"))) {
            String[] p = z[1].split(",");
            double[] q = new double[2 * p.length];
            for (int k = 0; k < p.length; k++) { String[] xy = p[k].split(" "); q[2 * k] = Double.parseDouble(xy[0]); q[2 * k + 1] = Double.parseDouble(xy[1]); }
            lin.put(Integer.parseInt(z[0]) - 1, q);
        }
        for (int e = 0; e < w.m; e++) {
            if (w.kEbene[e] != 1) continue;
            boolean eis = WasserNetz.GLETSCHER.equals(w.kArt[e]);
            if (!eis && !WasserNetz.BACH.equals(w.kArt[e]) && !WasserNetz.BACH_EIS.equals(w.kArt[e])) continue;
            double fl = Math.max(0.5, w.kFlaeche[e]);
            float bw = (float) (0.6 + 0.55 * Math.log10(fl) * 2);
            if (eis) {
                g.setStroke(new BasicStroke(Math.max(1, bw), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{4, 3}, 0));
                g.setColor(new Color(170, 235, 255, 200));
            } else {
                g.setStroke(new BasicStroke(bw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(WasserNetz.BACH_EIS.equals(w.kArt[e]) ? new Color(120, 190, 240, 120) : new Color(90, 170, 255, 230));
            }
            Path2D p = new Path2D.Double();
            double[] q = lin.get(e);
            if (q == null) q = new double[]{w.ost[w.von[e]], w.nord[w.von[e]], w.ost[w.nach[e]], w.nord[w.nach[e]]};
            for (int k = 0; k < q.length; k += 2) { double x = (q[k] - Wasser.E0) * PX, y = (Wasser.N0 - q[k + 1]) * PX; if (k == 0) p.moveTo(x, y); else p.lineTo(x, y); }
            g.draw(p);
        }
        // Seen
        g.setColor(new Color(140, 210, 255));
        for (String[] z : WellenNetz.tsv(new File(d, "seen.tsv"))) {
            double x = (Double.parseDouble(z[1]) - Wasser.E0) * PX, y = (Wasser.N0 - Double.parseDouble(z[2])) * PX;
            g.fillOval((int) x - 3, (int) y - 3, 6, 6);
            if (!z[6].isEmpty()) { g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11)); g.drawString(z[6], (int) x + 5, (int) y - 4); }
        }
        // Ziele und Namen
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        for (int i = 0; i < w.n; i++) {
            if (w.ebene[i] != 1 || !WasserNetz.ZIEL.equals(w.art[i]) || i > 2) continue;
            int x = (int) ((w.ost[i] - Wasser.E0) * PX), y = (int) ((Wasser.N0 - w.nord[i]) * PX);
            g.setColor(Color.WHITE);
            g.fillOval(x - 4, y - 4, 8, 8);
            g.setColor(INK);
            g.drawString(w.name[i], x + 7, y - 6);
        }
        // Tafel
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        g.drawString("Welche Fläche entwässert in die Bucht?  LB_NET_WASSER, Szene 66 × 60 km, Gelände 10 m", 16, H5 + 28);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        int yy = H5 + 54;
        for (String zl : new String[]{"Gilbert Inlet", "Crillon Inlet", "Bucht", "Golf von Alaska", "Rand der Szene"}) {
            String v = w.wert("einzug." + WasserNetz.schluessel(zl));
            g.setColor(ZIEL[java.util.Arrays.asList(WasserNetz.ZIELE).indexOf(zl)]);
            g.fillRect(16, yy - 10, 10, 10);
            g.setColor(INK);
            g.drawString(String.format("%-16s %s", zl, v == null ? "" : v), 32, yy);
            yy += 18;
        }
        g.setColor(DIM);
        String to = w.wert("traceout.lituya_glacier"), mud = w.wert("mudslide_creek");
        if (to != null) { g.drawString("traceOut Lituya Glacier: " + to, 16, yy + 4); yy += 18; }
        if (mud != null) { g.drawString("Mudslide Creek: " + mud, 16, yy + 4); yy += 18; }
        g.drawString("blau: Bäche ab 0,5 km² (Strichstärke nach Fläche) · hellblau gestrichelt: Fließlinien der Gletscher · " + w.wert("eis_geschwindigkeit"), 16, yy + 4);
        g.dispose();
        png.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(im, "png", png);
        System.out.printf(Locale.GERMAN, "Wasserkarte nach %s (%d × %d)%n", png.getPath(), im.getWidth(), im.getHeight());
    }
}
