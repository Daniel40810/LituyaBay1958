package com.dan.lituya.tools;

import com.dan.lituya.scene.SeeNetz;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * „Wer hätte entkommen können?“ als Karte (Phase 15a): der Eingang der Bucht mit der Front (linear, Ereigniszeit) als
 * Bänder zu 30 s, das Fahrwasser {@code LB_NET_SEE}, die Ankerplätze, das Tor und die Wege der spätesten Abfahrt
 * ({@code daten/netz/see/wege.tsv}); darunter die Tafel aus {@code flucht.tsv}. Nach {@code docs/status/Flucht.png}.
 * <p>Aufruf: {@code Fluchtkarte [daten] [png]}.
 */
public final class Fluchtkarte {
    private Fluchtkarte() { }

    static final double E0 = 343_600, E1 = 348_600, N0 = 6_498_900, N1 = 6_502_000, PX = 0.3;   // Bildpunkte je m
    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), DIM = new Color(140, 156, 170), LAND = new Color(38, 44, 40);

    static int x(double e) { return (int) Math.round((e - E0) * PX); }
    static int y(double n) { return (int) Math.round((N1 - n) * PX); }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        File daten = new File(a.length > 0 ? a[0] : "daten");
        File png = new File(a.length > 1 ? a[1] : "docs/status/Flucht.png");
        WellenNetz w = WellenNetz.lesen(daten);
        SeeNetz s = SeeNetz.lesen(daten);
        File d = new File(daten, SeeNetz.ORDNER);
        int W = x(E1), H = y(N0), tafel = 250;
        BufferedImage im = new BufferedImage(W, H + tafel, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, W, H + tafel);
        g.setColor(LAND);
        g.fillRect(0, 0, W, H);
        // Wasser nach der Front (linear, Ereigniszeit = T_1940 + 105 s)
        double lo = 300, hi = 600;
        int c = (int) Math.ceil(WellenNetz.ZELLE * PX);
        for (int k = 0; k < w.n; k++) {
            double e = WellenNetz.ost(w.i[k]), n = WellenNetz.nord(w.j[k]);
            if (e < E0 - 20 || e > E1 + 20 || n < N0 - 20 || n > N1 + 20) continue;
            double t = w.ankunft[1][k] + WellenNetz.START_S;
            double f = Math.max(0, Math.min(1, (t - lo) / (hi - lo)));
            int band = (int) Math.floor(t / 30) % 2;
            Color col = farbe(f, band == 0 ? 1.0 : 0.88);
            g.setColor(col);
            g.fillRect(x(e - WellenNetz.ZELLE / 2), y(n + WellenNetz.ZELLE / 2), c, c);
        }
        g.setClip(0, 0, W, H);
        // Fahrwasser
        g.setStroke(new BasicStroke(0.6f));
        g.setColor(new Color(255, 255, 255, 40));
        for (int e = 0; e < s.m; e++) if (s.von[e] < s.nach[e])
            g.drawLine(x(s.ost[s.von[e]]), y(s.nord[s.von[e]]), x(s.ost[s.nach[e]]), y(s.nord[s.nach[e]]));
        // Tor und die Linie 1 km hinaus
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{8, 6}, 0));
        g.setColor(INK);
        double pe = -s.hinN, pn = s.hinE;   // entlang des Tors
        g.drawLine(x(s.torE - 900 * pe), y(s.torN - 900 * pn), x(s.torE + 900 * pe), y(s.torN + 900 * pn));
        double de = s.torE + SeeNetz.DRAUSSEN * s.hinE, dn = s.torN + SeeNetz.DRAUSSEN * s.hinN;
        g.setColor(new Color(120, 230, 140));
        g.drawLine(x(de - 1400 * pe), y(dn - 1400 * pn), x(de + 1400 * pe), y(dn + 1400 * pn));
        // Wege der spätesten Abfahrt
        Map<String, List<double[]>> wege = new LinkedHashMap<>();
        for (String[] z : WellenNetz.tsv(new File(d, "wege.tsv")))
            wege.computeIfAbsent(z[0] + "|" + z[1] + "|" + z[2], q -> new ArrayList<>()).add(new double[]{Double.parseDouble(z[5]), Double.parseDouble(z[6]), Double.parseDouble(z[7])});
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        int nr = 0;
        for (Map.Entry<String, List<double[]>> en : wege.entrySet()) {
            String[] k = en.getKey().split("\\|");
            boolean lin = k[1].equals("LINEAR");
            g.setStroke(lin ? new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) : new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{6, 5}, 0));
            g.setColor(k[0].equals("Edrie") ? new Color(255, 200, 90) : new Color(120, 200, 255));
            Path2D p = new Path2D.Double();
            List<double[]> l = en.getValue();
            for (int i = 0; i < l.size(); i++) if (i == 0) p.moveTo(x(l.get(i)[0]), y(l.get(i)[1])); else p.lineTo(x(l.get(i)[0]), y(l.get(i)[1]));
            g.draw(p);
            double[] z = l.get(l.size() - 1);
            g.drawString(String.format(Locale.GERMAN, "%s %s: ab %+.0f s, draußen %.0f s", k[0], lin ? "linear" : "Vorrechnung", Double.parseDouble(k[2]), z[2]), W - 420, 24 + 18 * nr++);
        }
        // Ankerplätze
        g.setStroke(new BasicStroke(2f));
        for (Object[] p : SeeNetz.PLAETZE) {
            int px = x((double) p[1]), py = y((double) p[2]);
            g.setColor(Color.WHITE);
            g.fillOval(px - 5, py - 5, 10, 10);
            g.setColor(BG);
            g.drawOval(px - 5, py - 5, 10, 10);
            g.setColor(INK);
            g.drawString((String) p[0] + (p[0].equals("Badger") ? " (und Sunmore)" : ""), px + 8, py - 8);
        }
        g.setClip(null);
        // Legende der Front
        int lx = 16, ly = H - 46;
        for (int i = 0; i < 200; i++) { g.setColor(farbe(i / 199.0, 1)); g.fillRect(lx + i, ly, 1, 12); }
        g.setColor(INK);
        g.drawString(String.format(Locale.GERMAN, "Front linear (T_1940 + 105 s): %.0f s … %.0f s nach dem Stoß, Bänder zu 30 s", lo, hi), lx, ly - 6);
        g.setColor(DIM);
        g.drawString("weiß: LB_NET_SEE (40 m am Tor, 100 m See, 200 m Bucht) · gestrichelt: Tor und 950 m hinaus", lx, ly + 28);
        // Tafel
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString("Wer hätte entkommen können?  Abfahrt Δ s nach dem Stoß (22:16), 8 kn, jeder Knoten vor der Front", 16, H + 30);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        Map<String, StringBuilder> zeile = new LinkedHashMap<>();
        for (String[] z : WellenNetz.tsv(new File(d, "flucht.tsv")))
            zeile.computeIfAbsent(String.format("%-7s %-9s", z[0], z[1].toLowerCase(Locale.ROOT)), q -> new StringBuilder()).append(z[3].equals("J") ? z[5].isEmpty() ? "    ∞" : String.format(Locale.GERMAN, "%5.0f", Double.parseDouble(z[5])) : "    ·");
        int yy = H + 58;
        StringBuilder kopf = new StringBuilder(String.format("%-17s", "Δ ="));
        for (double dd : SeeNetz.ABFAHRT) kopf.append(String.format(Locale.GERMAN, "%5.0f", dd));
        g.setColor(DIM);
        g.drawString(kopf + "   (Reserve vor der Front am Ziel in s; ∞: die Front erreicht es nicht; · gefangen)", 16, yy);
        g.setColor(INK);
        for (Map.Entry<String, StringBuilder> en : zeile.entrySet()) { yy += 20; g.drawString(en.getKey() + " " + en.getValue(), 16, yy); }
        yy += 28;
        g.setColor(DIM);
        for (String z : java.nio.file.Files.readAllLines(new File(d, "SEE.txt").toPath(), java.nio.charset.StandardCharsets.UTF_8))
            if (z.startsWith("gezeit_stoss=") || z.startsWith("front_ereignis=") || z.startsWith("tiefe_mindest=")) { g.drawString(z.replace('=', ' ').replaceFirst("_", " "), 16, yy); yy += 18; }
        g.dispose();
        png.getParentFile().mkdirs();
        ImageIO.write(im, "png", png);
        System.out.printf("Fluchtkarte nach %s (%d × %d)%n", png, im.getWidth(), im.getHeight());
    }

    /** Früh rot, spät blau (f 0 … 1), helle Bänder k. */
    static Color farbe(double f, double k) {
        double r = 0.85 - 0.65 * f, gg = 0.25 + 0.35 * f, b = 0.25 + 0.6 * f;
        return new Color((int) (255 * r * k), (int) (255 * gg * k), (int) (255 * b * k));
    }
}
