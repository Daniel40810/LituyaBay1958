package com.dan.lituya.ui;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JLabel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Gemalte Zeichen für die Knöpfe (W9c, Lesbarkeit_W09): Rückwärts, Halt, Vorwärts, Spielen als Vektor statt als Zeichen
 * aus der Schrift. Die Schrift der FStyle-Knöpfe ist eine echte Schrift (kein logischer Font wie „Dialog“), Java sucht
 * fehlende Zeichen dort nicht in einer anderen; ◀ und ▶ (U+25C0, U+25B6) fehlen ihr, ■ hat sie. Darum stand im Block
 * EREIGNIS „▯ J ■ K ▯ L“.
 * <p>Dazu {@link #lesbar}: geht alle Beschriftungen eines Fensters durch, ersetzt Zeichen, die die Schrift nicht hat,
 * durch lesbare ({@code ◀ → «}, {@code ▶ → »} …) und meldet sie einmal auf der Konsole, damit so etwas nicht wieder
 * unbemerkt bleibt.
 */
public final class Zeichen implements Icon {
    public enum Art { RUECK, HALT, VOR, SPIEL }

    private final Art art;
    private final int size;

    public Zeichen(Art art, int size) { this.art = art; this.size = size; }

    public static Zeichen von(Art a) { return new Zeichen(a, 10); }

    @Override public int getIconWidth() { return size; }

    @Override public int getIconHeight() { return size; }

    @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        Color col = c.getForeground();
        if (c instanceof com.dan.fbutton.FButton b && b.normalTextColor != null) col = b.normalTextColor;
        if (!c.isEnabled()) col = new Color(col.getRed(), col.getGreen(), col.getBlue(), 110);
        g.setColor(col);
        double s = size, m = s * 0.08;
        Path2D.Double p = new Path2D.Double();
        switch (art) {
            case RUECK -> { p.moveTo(x + s - m, y + m); p.lineTo(x + m, y + s / 2); p.lineTo(x + s - m, y + s - m); p.closePath(); }
            case VOR, SPIEL -> { p.moveTo(x + m, y + m); p.lineTo(x + s - m, y + s / 2); p.lineTo(x + m, y + s - m); p.closePath(); }
            case HALT -> { double q = s * 0.14; p.moveTo(x + q, y + q); p.lineTo(x + s - q, y + q); p.lineTo(x + s - q, y + s - q); p.lineTo(x + q, y + s - q); p.closePath(); }
        }
        g.fill(p);
        g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);   // weiche Kante wie die Schrift
        g.dispose();
    }

    /** Ersatz für Zeichen, die einer Schrift fehlen können (Pfeile und Dreiecke aus den „Geometric Shapes“, Symbole). */
    static final Map<Integer, String> ERSATZ = Map.ofEntries(
            Map.entry(0x25C0, "«"), Map.entry(0x25B6, "»"), Map.entry(0x25B8, "›"), Map.entry(0x25C2, "‹"), Map.entry(0x25BA, "»"),
            Map.entry(0x25C4, "«"), Map.entry(0x23F5, "»"), Map.entry(0x23F4, "«"), Map.entry(0x23F8, "||"), Map.entry(0x25A0, "#"),
            Map.entry(0x270E, "*"), Map.entry(0x2713, "+"), Map.entry(0x2717, "x"), Map.entry(0x2192, "->"), Map.entry(0x2190, "<-"),
            Map.entry(0x221E, "unendlich"), Map.entry(0x2009, " "), Map.entry(0x202F, " "), Map.entry(0x2212, "-"), Map.entry(0x2013, "-"),
            Map.entry(0x2014, "-"), Map.entry(0x201E, "\""), Map.entry(0x201C, "\""), Map.entry(0x2026, "..."), Map.entry(0x2248, "~"));

    private static final Set<String> GEMELDET = new LinkedHashSet<>();

    /** Text in einer Schrift lesbar machen: fehlende Zeichen ersetzen (oder, ohne Ersatz, als „?“). Null bleibt null. */
    public static String lesbar(Font f, String t) {
        if (t == null || f == null || f.canDisplayUpTo(t) < 0) return t;
        StringBuilder b = new StringBuilder();
        StringBuilder fehlt = new StringBuilder();
        t.codePoints().forEach(cp -> {
            if (f.canDisplay(cp)) { b.appendCodePoint(cp); return; }
            String e = ERSATZ.get(cp);
            if (e != null && f.canDisplayUpTo(e) < 0) b.append(e);
            else b.append('?');
            fehlt.appendCodePoint(cp);
        });
        String key = f.getFontName() + "|" + fehlt;
        if (GEMELDET.add(key))
            System.err.println("LituyaBay1958: der Schrift „" + f.getFontName() + "“ fehlen „" + fehlt + "“ (in „" + kurz(t) + "“), ersetzt");
        return b.toString();
    }

    /** Alle Beschriftungen und Hinweise unter root lesbar machen (Knöpfe, Felder, Ankreuzfelder). Gibt die Zahl der geänderten zurück. */
    public static int lesbar(Container root) {
        int n = 0;
        for (Component c : root.getComponents()) {
            Font f = c.getFont();
            if (c instanceof AbstractButton b) {
                String t = b.getText(), u = lesbar(f, t);
                if (u != null && !u.equals(t)) { b.setText(u); n++; }
            } else if (c instanceof JLabel l) {
                String t = l.getText(), u = lesbar(f, t);
                if (u != null && !u.equals(t)) { l.setText(u); n++; }
            }
            if (c instanceof Container k) n += lesbar(k);
        }
        return n;
    }

    private static String kurz(String t) {
        String s = t.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return s.length() > 60 ? s.substring(0, 57) + "…" : s;
    }
}
