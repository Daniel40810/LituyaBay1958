package com.dan.lituya.ui;

import com.dan.fstyle.FTheme;

import java.awt.Color;

/**
 * Die Farben der Oberfläche aus FTheme (Bedienelemente in FTheme-Farben, die Szene hat ihren eigenen Look; aus
 * Timmendorf). Liefert das Thema keine dunklen Farben, nimmt die App die Julinacht über der Bucht, damit der Rand um die
 * Szene nicht hell aufleuchtet. Dazu die Farben des Werkbuchs [G]: Gletschermilch, Eis, Schiefer, Sitka-Fichte,
 * Alpenglühen am Fairweather, Dämmerung und die Goldkante der Spurlinie.
 */
public final class Theme {
    private Theme() { }

    public static final Color NACHT = new Color(12, 17, 22), MILCH = new Color(118, 176, 172), EIS = new Color(208, 228, 236),
            FELS = new Color(128, 124, 116), WALD = new Color(74, 112, 84), GLUEHEN = new Color(236, 152, 122),
            DAEMMER = new Color(86, 104, 150), GOLD = new Color(232, 178, 92);

    public static Color bg() { return pick(FTheme::getBackground, NACHT, Role.DARK); }
    public static Color surface() { return pick(FTheme::getSurface, new Color(19, 26, 33), Role.DARK); }
    public static Color ink() { return pick(FTheme::getText, new Color(233, 236, 234), Role.LIGHT); }
    public static Color muted() { return pick(FTheme::getTextMuted, new Color(147, 160, 164), Role.LIGHT); }
    public static Color accent() { return pick(FTheme::getAccent, MILCH, Role.ANY); }
    public static Color status() { return new Color(8, 12, 16); }
    public static Color line() { return new Color(36, 48, 60); }

    private enum Role { DARK, LIGHT, ANY }

    private static Color pick(java.util.function.Function<FTheme, Color> f, Color fallback, Role role) {
        try {
            Color c = f.apply(FTheme.getInstance());
            if (c == null) return fallback;
            int lum = (c.getRed() * 3 + c.getGreen() * 6 + c.getBlue()) / 10;
            if (role == Role.DARK && lum > 70) return fallback;
            if (role == Role.LIGHT && lum < 120) return fallback;
            return c;
        } catch (RuntimeException | Error e) {
            return fallback;
        }
    }

    /** Die Farbleiste des Werkbuchs (tiefe Bucht bis Alpenglühen) mit ihren Anteilen, für Startbild und Symbol. */
    public static final Color[] BAND = {new Color(28, 62, 70), MILCH, EIS, FELS, WALD, GOLD, FELS, EIS, DAEMMER, GLUEHEN};
    public static final double[] BAND_AT = {0, 0.16, 0.28, 0.36, 0.48, 0.60, 0.64, 0.74, 0.84, 0.92, 1};
}
