package com.dan.lituya.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.function.Consumer;

/**
 * Werkbuch Welle W9 (vorgemerkt am 07.11.): das Bedienfeld als Schublade am rechten Rand. Ein Klick auf die schmale Lasche
 * (oder F9) lässt es nach rechts aus dem Fenster gleiten; die Szene wächst mit, bis nur die Lasche bleibt. Ein zweiter
 * Klick holt es zurück. Die Bewegung dauert {@link #dauerS} Sekunden und folgt einer weichen Kurve (schnell los, sanft
 * an); der Inhalt behält seine Breite und wird am Fensterrand abgeschnitten, so sieht es aus, als schöbe man ihn hinaus.
 * Während des Gleitens meldet {@link #gleitet} true: Der Renderer legt seine Ziele dann nicht je Bild neu an. Offen oder
 * zu wird je Rechner gemerkt.
 */
public final class Schublade extends JPanel {
    /** Breite der Lasche (Bildpunkte). */
    public static final int LASCHE = 14;
    public volatile double dauerS = 0.32;
    private final JComponent inhalt;
    private final int breite;
    private final Lasche lasche = new Lasche();
    private double auf, ziel;
    private long t0;
    private Timer timer;
    private final Consumer<Boolean> gleitet;
    /** Wird mit dem neuen Zustand gerufen (offen true), wenn umgeschaltet wird; zum Merken. */
    public volatile Consumer<Boolean> geschaltet = b -> { };

    public Schublade(JComponent inhalt, int breite, boolean offen, Consumer<Boolean> gleitet) {
        super(null);
        this.inhalt = inhalt;
        this.breite = breite;
        this.gleitet = gleitet == null ? b -> { } : gleitet;
        this.auf = offen ? 1 : 0;
        this.ziel = auf;
        setOpaque(true);
        setBackground(Theme.bg());
        add(lasche);
        add(inhalt);
    }

    public boolean offen() { return ziel > 0.5; }

    /** Auf- oder zuschieben. */
    public void umschalten() { schieben(!offen()); }

    public void schieben(boolean offen) {
        double z = offen ? 1 : 0;
        if (z == ziel && timer != null && timer.isRunning()) return;
        ziel = z;
        try { geschaltet.accept(offen); } catch (RuntimeException e) { /* nicht gemerkt */ }
        if (Math.abs(auf - ziel) < 1e-6) return;
        t0 = System.nanoTime();
        double start = auf;
        gleitet.accept(true);
        if (timer != null) timer.stop();
        timer = new Timer(12, e -> {
            double u = Math.min(1, (System.nanoTime() - t0) / 1e9 / Math.max(0.01, dauerS * Math.abs(ziel - start)));
            double k = 1 - Math.pow(1 - u, 3);   // schnell los, sanft an
            auf = start + (ziel - start) * k;
            revalidate();
            if (getParent() != null) getParent().revalidate();
            repaint();
            lasche.repaint();
            if (u >= 1) {
                auf = ziel;
                ((Timer) e.getSource()).stop();
                gleitet.accept(false);
                if (getParent() != null) getParent().revalidate();
            }
        });
        timer.start();
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(LASCHE + (int) Math.round(breite * auf), Math.max(200, inhalt.getPreferredSize().height));
    }

    @Override public Dimension getMinimumSize() { return new Dimension(LASCHE, 100); }

    @Override public void doLayout() {
        int h = getHeight();
        lasche.setBounds(0, 0, LASCHE, h);
        inhalt.setBounds(LASCHE, 0, breite, h);
        inhalt.setVisible(auf > 0.002);
    }

    /** Die Lasche: ein schmaler Streifen mit einem Winkel, der zeigt, wohin es geht. */
    private final class Lasche extends JComponent {
        private boolean ueber;

        Lasche() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Bedienfeld ein- und ausklappen (F9)");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { umschalten(); }
                @Override public void mouseEntered(MouseEvent e) { ueber = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { ueber = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Color bg = Theme.bg(), hi = Theme.accent();
            g.setPaint(new GradientPaint(0, 0, ueber ? mix(bg, hi, 0.22) : mix(bg, Color.BLACK, 0.25), w, 0, bg));
            g.fillRect(0, 0, w, h);
            g.setColor(Theme.line());
            g.drawLine(0, 0, 0, h);
            // drei Punkte als Griff und ein Winkel in der Mitte
            int cy = h / 2;
            g.setColor(ueber ? hi : mix(hi, bg, 0.45));
            for (int k = -1; k <= 1; k++) g.fillOval(w / 2 - 1, cy - 44 + k * 7, 3, 3);
            for (int k = -1; k <= 1; k++) g.fillOval(w / 2 - 1, cy + 40 + k * 7, 3, 3);
            Path2D p = new Path2D.Double();
            double d = auf > 0.5 ? 1 : -1;   // offen: Winkel nach rechts (hinaus), zu: nach links (herein)
            p.moveTo(w / 2.0 - 2.5 * d, cy - 7);
            p.lineTo(w / 2.0 + 2.5 * d, cy);
            p.lineTo(w / 2.0 - 2.5 * d, cy + 7);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(p);
            g.dispose();
        }
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
