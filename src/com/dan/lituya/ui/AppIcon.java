package com.dan.lituya.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Das Programmsymbol (F43 A): die Bucht vor der Fairweather-Kette in der hellen Julinacht, an den Hängen der dunkle
 * Wald, darin die helle Spurlinie der Welle, darüber eine Goldkante wie bei {@code heidelberg.ico}. Phase 1 malt es,
 * Phase 20 legt die PNGs ins Jar ({@code com/dan/lituya/ui/icon/lituya_NN.png}) und schreibt {@code LituyaBay1958.ico}
 * für die Verknüpfung. Fehlt eine Größe, malt die App sie selbst.
 */
public final class AppIcon {
    public static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private AppIcon() { }

    public static List<Image> images() {
        List<Image> l = new ArrayList<>();
        for (int s : SIZES) l.add(load(s));
        return l;
    }

    public static BufferedImage load(int size) {
        try (InputStream in = AppIcon.class.getResourceAsStream("icon/lituya_" + size + ".png")) {
            if (in != null) {
                BufferedImage b = javax.imageio.ImageIO.read(in);
                if (b != null) return b;
            }
        } catch (java.io.IOException e) { /* gemaltes Symbol */ }
        return paint(size);
    }

    /** Das Symbol, in jeder Größe neu gemalt. */
    public static BufferedImage paint(int s) {
        BufferedImage im = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double r = s * 0.18, inset = Math.max(1, s * 0.035);
        // Goldkante
        g.setPaint(new GradientPaint(0, 0, new Color(240, 210, 120), s, s, new Color(150, 105, 30)));
        g.fill(new RoundRectangle2D.Double(0, 0, s, s, r * 2, r * 2));
        RoundRectangle2D inner = new RoundRectangle2D.Double(inset, inset, s - 2 * inset, s - 2 * inset, r * 1.7, r * 1.7);
        g.setClip(inner);
        // helle Julinacht: oben Dämmerblau, unten Alpenglühen
        double kimm = s * 0.66;
        g.setPaint(new GradientPaint(0, 0, new Color(40, 56, 96), 0, (float) kimm, new Color(236, 160, 128)));
        g.fill(new Rectangle2D.Double(0, 0, s, kimm + 1));
        // die Kette mit dem Fairweather, Schnee oben
        Path2D berg = new Path2D.Double();
        berg.moveTo(-1, kimm);
        berg.lineTo(s * 0.12, s * 0.46);
        berg.lineTo(s * 0.26, s * 0.40);
        berg.lineTo(s * 0.42, s * 0.18);
        berg.lineTo(s * 0.55, s * 0.34);
        berg.lineTo(s * 0.70, s * 0.28);
        berg.lineTo(s * 0.86, s * 0.42);
        berg.lineTo(s + 1, s * 0.38);
        berg.lineTo(s + 1, kimm);
        berg.closePath();
        g.setPaint(new GradientPaint(0, (float) (s * 0.18), new Color(240, 236, 232), 0, (float) kimm, new Color(122, 118, 124)));
        g.fill(berg);
        // die Hänge der Bucht: links und rechts dunkler Wald bis ans Wasser
        Path2D links = new Path2D.Double(), rechts = new Path2D.Double();
        links.moveTo(-1, s * 0.50);
        links.lineTo(s * 0.30, s * 0.62);
        links.lineTo(s * 0.44, kimm + 1);
        links.lineTo(-1, kimm + 1);
        links.closePath();
        rechts.moveTo(s + 1, s * 0.48);
        rechts.lineTo(s * 0.68, s * 0.60);
        rechts.lineTo(s * 0.56, kimm + 1);
        rechts.lineTo(s + 1, kimm + 1);
        rechts.closePath();
        g.setColor(new Color(44, 72, 56));
        g.fill(links);
        g.fill(rechts);
        // die Spurlinie: der Wald darunter abrasiert, heller Fels, oben die goldene Linie
        Path2D band = new Path2D.Double();
        band.moveTo(s + 1, s * 0.53);
        band.lineTo(s * 0.64, s * 0.62);
        band.lineTo(s * 0.56, kimm + 1);
        band.lineTo(s + 1, kimm + 1);
        band.closePath();
        g.setColor(new Color(176, 166, 150));
        g.fill(band);
        g.setColor(new Color(244, 200, 100));
        g.setStroke(new BasicStroke((float) Math.max(1, s * 0.03), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D spur = new Path2D.Double();
        spur.moveTo(s * 0.62, s * 0.63);
        spur.lineTo(s * 0.80, s * 0.555);
        spur.lineTo(s + 1, s * 0.53);
        g.draw(spur);
        if (s >= 32) {
            Path2D spurL = new Path2D.Double();
            spurL.moveTo(s * 0.38, s * 0.64);
            spurL.lineTo(s * 0.18, s * 0.585);
            spurL.lineTo(-1, s * 0.56);
            g.setColor(new Color(244, 200, 100, 200));
            g.draw(spurL);
        }
        // Gletschermilch
        g.setPaint(new GradientPaint(0, (float) kimm, new Color(118, 176, 172), 0, s, new Color(30, 70, 76)));
        g.fill(new Rectangle2D.Double(0, kimm, s, s - kimm));
        if (s >= 24) {
            g.setStroke(new BasicStroke((float) Math.max(1, s * 0.02), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(236, 242, 240, 170));
            Path2D w = new Path2D.Double();
            w.moveTo(s * 0.2, s * 0.80);
            w.curveTo(s * 0.4, s * 0.77, s * 0.6, s * 0.83, s * 0.8, s * 0.79);
            g.draw(w);
        }
        g.setClip(null);
        g.setColor(new Color(255, 236, 180, 120));
        g.setStroke(new BasicStroke((float) Math.max(0.6, s * 0.012)));
        g.draw(inner);
        g.dispose();
        return im;
    }

    /** Setzt das Symbol am Fenster und, wo unterstützt, in der Taskleiste. */
    public static void install(java.awt.Window w) {
        List<Image> l = images();
        w.setIconImages(l);
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar tb = java.awt.Taskbar.getTaskbar();
                if (tb.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) tb.setIconImage(l.get(l.size() - 1));
            }
        } catch (Exception | Error ignored) {
            // Windows nimmt ohnehin die Fenstersymbole
        }
    }
}
