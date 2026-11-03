package com.dan.lituya.gl;

import com.dan.lituya.data.Speicher;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;

/**
 * Das Archiv in der Szene (Phase 19a, F51/F52): die Befunde als Marken an ihrem Ort, Farbe nach Art (belegt grün, gemessen
 * blau, gerechnet orange, gesetzt grau), ein Klick zeigt Aussage und Quellen; die Aussagen der Zeugen als Untertitel,
 * solange die Uhr in ihrer Zeit steht. Gemalt mit Java2D auf eine Tafel über dem ganzen Bild ({@link HudOverlay}), die
 * Marken 40 m über dem Grund mit der Erdkrümmung wie der Netzblick.
 */
public final class ArchivSzene {
    public volatile boolean marken, untertitel;
    /** Welche Arten als Marken erscheinen. */
    public volatile Set<String> arten = Set.of("belegt", "gemessen", "gerechnet", "gesetzt");
    private volatile List<Speicher.Befund> befunde = List.of();
    private volatile List<Speicher.Zeuge> zeugen = List.of();
    /** Der gewählte Befund (Schlüssel) oder null. */
    public volatile String gewaehlt;
    /** Wird gerufen, wenn ein Klick in der Szene einen Befund trifft (im Swing-Faden weiterreichen). */
    public volatile Consumer<Speicher.Befund> onPick = b -> { };
    public static final double UEBER = 40, R = 6_371_000 / 0.87;

    /** Wo die Marken zuletzt standen (Bildpunkte der Tafel) für den Klick. */
    private record Marke(Speicher.Befund b, double x, double y) { }
    private volatile List<Marke> zuletzt = List.of();
    private volatile int tafelW = 1, tafelH = 1;

    public void setzen(List<Speicher.Befund> b, List<Speicher.Zeuge> z) { befunde = List.copyOf(b); zeugen = List.copyOf(z); }

    public List<Speicher.Befund> befunde() { return befunde; }

    public List<Speicher.Zeuge> zeugen() { return zeugen; }

    public boolean aktiv() { return (marken && !befunde.isEmpty()) || (untertitel && !zeugen.isEmpty()); }

    public static Color farbe(String art) {
        return switch (art == null ? "" : art) {
            case "belegt" -> new Color(96, 200, 120);
            case "gemessen" -> new Color(90, 160, 235);
            case "gerechnet" -> new Color(240, 160, 60);
            default -> new Color(170, 170, 170);
        };
    }

    /** Die Aussagen, deren Zeit jetzt ist (nur in der Nacht von 1958: Zeitstand vor oder Welle). */
    public List<Speicher.Zeuge> jetzt(double simS, SceneClock.Zeitstand z) {
        List<Speicher.Zeuge> l = new ArrayList<>();
        if (z != SceneClock.Zeitstand.VOR && z != SceneClock.Zeitstand.WELLE) return l;
        for (Speicher.Zeuge g : zeugen) if (!Double.isNaN(g.vonS()) && simS >= g.vonS() && simS <= (Double.isNaN(g.bisS()) ? g.vonS() + 20 : g.bisS())) l.add(g);
        return l;
    }

    /** Bildpunkt eines Orts (UTM, Höhe über NAVD88) oder null, wenn er hinter dem Auge liegt. vp spaltenweise wie OpenGL. */
    public static double[] bild(float[] vp, double[] eye, double e, double n, double hoehe, int w, int h) {
        double[] p = Site.fromUtm(e, n);
        double d = Math.hypot(p[0] - eye[0], p[1] - eye[2]);
        double y = hoehe - d * d / (2 * R);
        double cx = vp[0] * p[0] + vp[4] * y + vp[8] * p[1] + vp[12], cy = vp[1] * p[0] + vp[5] * y + vp[9] * p[1] + vp[13], cw = vp[3] * p[0] + vp[7] * y + vp[11] * p[1] + vp[15];
        if (cw <= 1e-6) return null;
        return new double[]{(cx / cw * 0.5 + 0.5) * w, (1 - (cy / cw * 0.5 + 0.5)) * h, d};
    }

    /** Malt die Tafel (Bildpunkte w × h). */
    public BufferedImage malen(int w, int h, float[] vp, double[] eye, DoubleBinaryOperator grund, double simS, SceneClock.Zeitstand zs, boolean mitMarken, int unten) {
        BufferedImage im = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        List<Marke> m = new ArrayList<>();
        Speicher.Befund wahl = null;
        double[] wahlPunkt = null;
        if (marken && mitMarken && vp != null) {
            Font f = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            g.setFont(f);
            // je Ort nur eine Reihe: Befunde am selben Ort stapeln
            java.util.Map<String, Integer> stapel = new java.util.HashMap<>();
            for (Speicher.Befund b : befunde) {
                if (Double.isNaN(b.e()) || !arten.contains(b.art())) continue;
                double gr = grund.applyAsDouble(Site.fromUtm(b.e(), b.n())[0], Site.fromUtm(b.e(), b.n())[1]);
                if (Double.isNaN(gr) || gr < -1000) gr = 0;
                double[] s = bild(vp, eye, b.e(), b.n(), Math.max(gr, 0) + UEBER, w, h);
                if (s == null || s[0] < -50 || s[1] < -50 || s[0] > w + 50 || s[1] > h + 50) continue;
                String key = Math.round(b.e() / 50) + ":" + Math.round(b.n() / 50);
                int k = stapel.merge(key, 1, Integer::sum) - 1;
                double x = s[0], y = s[1] - k * 16;
                double[] fuss = bild(vp, eye, b.e(), b.n(), Math.max(gr, 0), w, h);
                Color c = farbe(b.art());
                if (k == 0 && fuss != null) {
                    g.setColor(new Color(255, 255, 255, 110));
                    g.setStroke(new BasicStroke(1f));
                    g.drawLine((int) x, (int) y, (int) fuss[0], (int) fuss[1]);
                }
                boolean sel = b.schluessel().equals(gewaehlt);
                double r = sel ? 7 : 5;
                g.setColor(new Color(10, 14, 18, 200));
                g.fill(new Ellipse2D.Double(x - r - 1.5, y - r - 1.5, 2 * r + 3, 2 * r + 3));
                g.setColor(c);
                g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
                String t = b.thema() + (Double.isNaN(b.wert()) ? "" : String.format(Locale.GERMAN, " %s %s", zahl(b.wert()), b.einheit() == null ? "" : b.einheit()));
                if (s[2] < 15000 || sel) {
                    g.setColor(new Color(10, 14, 18, 170));
                    FontMetrics fm = g.getFontMetrics();
                    g.fillRoundRect((int) x + 9, (int) y - 9, fm.stringWidth(t) + 8, 17, 6, 6);
                    g.setColor(Color.WHITE);
                    g.drawString(t, (int) x + 13, (int) y + 4);
                }
                m.add(new Marke(b, x, y));
                if (sel) { wahl = b; wahlPunkt = new double[]{x, y}; }
            }
        }
        zuletzt = m;
        tafelW = w;
        tafelH = h;
        if (wahl != null) karte(g, wahl, wahlPunkt, w, h);
        if (untertitel) {
            List<Speicher.Zeuge> z = jetzt(simS, zs);
            int y = h - Math.max(unten, 0) - 60;
            for (int i = z.size() - 1; i >= 0; i--) {
                Speicher.Zeuge a = z.get(i);
                String kopf = a.name() + (a.boot() == null || a.boot().isBlank() ? "" : " (" + a.boot() + ")") + " · " + a.quelle();
                y = untertitel(g, kopf, a.aussage(), w, y) - 8;
            }
        }
        g.dispose();
        return im;
    }

    static String zahl(double v) {
        if (Math.abs(v) >= 1e6) return String.format(Locale.GERMAN, "%.1f Mio.", v / 1e6);
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.format(Locale.GERMAN, "%,.0f", v);
        return String.format(Locale.GERMAN, "%.2f", v).replaceAll("0+$", "").replaceAll(",$", "");
    }

    /** Ein Untertitel unten mittig (zwei Zeilen: wer und Quelle, die Aussage); gibt die Oberkante. */
    static int untertitel(Graphics2D g, String kopf, String text, int w, int unterkante) {
        Font fk = new Font(Font.SANS_SERIF, Font.BOLD, 13), ft = new Font(Font.SERIF, Font.ITALIC, 19);
        List<String> zeilen = umbrechen(text, g.getFontMetrics(ft), Math.min(w - 80, 900));
        int lh = g.getFontMetrics(ft).getHeight(), hk = g.getFontMetrics(fk).getHeight();
        int bw = Math.min(w - 40, 940), bh = hk + zeilen.size() * lh + 16, x = (w - bw) / 2, y = unterkante - bh;
        g.setColor(new Color(8, 12, 16, 185));
        g.fillRoundRect(x, y, bw, bh, 12, 12);
        g.setColor(new Color(232, 178, 92));
        g.setFont(fk);
        g.drawString(kopf, x + 16, y + 6 + g.getFontMetrics().getAscent());
        g.setColor(Color.WHITE);
        g.setFont(ft);
        int yy = y + 8 + hk;
        for (String z : zeilen) { g.drawString(z, x + 16, yy + g.getFontMetrics().getAscent()); yy += lh; }
        return y;
    }

    static List<String> umbrechen(String t, FontMetrics fm, int breite) {
        List<String> l = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        for (String w : t.split("\\s+")) {
            if (b.length() > 0 && fm.stringWidth(b + " " + w) > breite) { l.add(b.toString()); b.setLength(0); }
            if (b.length() > 0) b.append(' ');
            b.append(w);
        }
        if (b.length() > 0) l.add(b.toString());
        return l;
    }

    /** Die Karte des gewählten Befunds neben seiner Marke. */
    static void karte(Graphics2D g, Speicher.Befund b, double[] p, int w, int h) {
        Font fk = new Font(Font.SANS_SERIF, Font.BOLD, 13), ft = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        List<String> z = umbrechen(b.aussage(), g.getFontMetrics(ft), 380);
        String fuss = b.art() + (b.abschnitt() == null ? "" : " · Werkbuch " + b.abschnitt()) + (b.quellen() == null || b.quellen().isBlank() ? "" : " · " + b.quellen());
        List<String> zf = umbrechen(fuss, g.getFontMetrics(ft), 380);
        int lh = g.getFontMetrics(ft).getHeight(), bw = 410, bh = 30 + (z.size() + zf.size()) * lh + 12;
        int x = (int) Math.min(w - bw - 10, p[0] + 16), y = (int) Math.max(10, Math.min(h - bh - 10, p[1] - bh / 2.0));
        g.setColor(new Color(8, 12, 16, 220));
        g.fillRoundRect(x, y, bw, bh, 12, 12);
        g.setColor(farbe(b.art()));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, bw, bh, 12, 12);
        g.setFont(fk);
        g.drawString(b.thema() + (b.ortName() == null ? "" : " · " + b.ortName()), x + 12, y + 20);
        g.setColor(Color.WHITE);
        g.setFont(ft);
        int yy = y + 28;
        for (String s : z) { g.drawString(s, x + 12, yy + g.getFontMetrics().getAscent()); yy += lh; }
        g.setColor(new Color(190, 200, 205));
        for (String s : zf) { g.drawString(s, x + 12, yy + g.getFontMetrics().getAscent()); yy += lh; }
    }

    /**
     * Ein Klick in die Szene (Bildpunkte der Leinwand cw × ch): trifft er eine Marke (12 px), wird ihr Befund gewählt und
     * {@link #onPick} gerufen; sonst wird die Wahl aufgehoben. Gibt true bei Treffer.
     */
    public boolean pick(int x, int y, int cw, int ch) {
        if (!marken) return false;
        double sx = x * tafelW / (double) Math.max(1, cw), sy = y * tafelH / (double) Math.max(1, ch);
        Marke best = null;
        double bd = 12 * tafelW / (double) Math.max(1, cw);
        for (Marke m : zuletzt) {
            double d = Math.hypot(m.x() - sx, m.y() - sy);
            if (d < bd) { bd = d; best = m; }
        }
        gewaehlt = best == null ? null : best.b().schluessel();
        if (best != null) onPick.accept(best.b());
        return best != null;
    }
}
