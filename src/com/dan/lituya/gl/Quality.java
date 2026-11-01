package com.dan.lituya.gl;

import java.util.Locale;

/**
 * Qualitätsstufen der App (aus Timmendorf, entschlackt). Phase 1: der Anteil der Bildgröße, in dem gerechnet wird, und
 * das Zeitbudget. Jede weitere Phase hängt die Stufe ihres Packages an (4 {@code clipmap}, 5 {@code surface}, 5a
 * {@code ocean}, {@code sky}, {@code cloud}, 6 {@code forest} …); die Packages haben ihre eigene Kopie dieser Aufzählung
 * (Paketvertrag Punkt 7) und folgen der App eins zu eins über {@link #ordinal()}.
 * <p>
 * Ziel (F37 A, Werkbuch 15.1): „Mittel“ hält 60 Bilder/s in Full HD auf der Radeon 680M mit 2 GB, „Hoch“ mindestens
 * 30, auch beim abgespielten Ereignis; „Ultra“ ist für Standbilder und Kino.
 */
public enum Quality {
    NIEDRIG("Niedrig", 0.67),
    MITTEL("Mittel", 0.85),
    HOCH("Hoch", 1.0),
    ULTRA("Ultra", 1.0);

    public final String label;
    /** Anteil der Bildgröße, in dem gerechnet wird. */
    public final double renderScale;

    Quality(String label, double renderScale) {
        this.label = label;
        this.renderScale = renderScale;
    }

    /**
     * Bildzeit, die die Stufe höchstens verbrauchen darf (ms): „Mittel“ 16,5 ms (60 Bilder/s mit Rand), „Hoch“ 33 ms
     * (30 Bilder/s), „Niedrig“ die Hälfte von Mittel.
     */
    public double budgetMs() {
        return switch (this) { case NIEDRIG -> 8.0; case MITTEL -> 16.5; case HOCH -> 33.0; case ULTRA -> 50.0; };
    }

    /** Was die Stufe einstellt, als eine Zeile (Bedienfeld, README). */
    public String summary() {
        return String.format(Locale.GERMAN, "%s: %d %% der Auflösung, Budget %.1f ms (Gelände, See und Wald hängen ab Phase 4 ihre Stufen an)",
                label, Math.round(renderScale * 100), budgetMs());
    }

    @Override public String toString() { return label; }
}
