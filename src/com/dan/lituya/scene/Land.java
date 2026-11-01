package com.dan.lituya.scene;

import com.dan.clipmap.Clipmap;
import com.dan.clipmap.TileFolder;
import com.dan.clipmap.TileStack;

import java.io.File;
import java.util.Locale;

/**
 * Das Gelände der Szene (Werkbuch 5.11, Phase 4): die drei Stufen aus {@code daten/gelaende/} ({@link Stufe}: Kern 0,5 m,
 * Szene 5 m, Fern 30 m) als Stapel für {@code com.dan.clipmap}, Ursprung E0, N0 der Szene. Das feinste Gitter liegt auf
 * den Zellmitten des Kerns, damit die feinste Stufe genau das Lidar zeigt. Ohne aufbereitete Daten bleibt das Gelände leer
 * ({@link #da()} = false), die Szene zeigt dann wie in Phase 1 nur das Wasser.
 * <p>
 * Außerhalb des Ferns gilt {@link #AUSSEN} (Meeresgrund im Golf von Alaska [G]; das Wasser deckt ihn). Die Clipmap reicht
 * {@link #REICHWEITE} weit: bis über die Kimm des Mount Fairweather (264 km, F1) liegt nur der Fern selbst (±130 km).
 */
public final class Land {
    /** Höhe außerhalb aller Daten (m über NAVD88). */
    public static final float AUSSEN = -200;
    /** Wie weit die gröbste Stufe mindestens reicht (m). */
    public static final double REICHWEITE = 140_000;

    private final TileStack stapel;
    private final TileFolder kern;
    private final String fehler;
    private volatile Clipmap clip;

    /** Liest die Beschreibungen in {@code daten/gelaende} ({@code -Dlituya.daten} oder ./daten); die Kacheln erst beim Laden. */
    public Land() { this(new File(new File(System.getProperty("lituya.daten", "daten")), "gelaende")); }

    public Land(File ordner) {
        TileStack s = null;
        TileFolder k = null;
        String f = null;
        try {
            s = TileStack.folders(ordner, Site.E0, Site.N0);
            k = (TileFolder) s.sources()[0];
        } catch (Exception e) {
            f = "kein Gelände: " + e.getMessage() + " (ant aufbereitung)";
        }
        stapel = s;
        kern = k;
        fehler = f;
    }

    public boolean da() { return stapel != null; }

    /** Die Clipmap für eine Qualitätsstufe (neu, wenn sich die Stufe ändert; die alte hält an). */
    public synchronized Clipmap clipmap(Clipmap.Quality q) {
        if (stapel == null) return null;
        Clipmap c = clip;
        if (c != null && c.grid == q.grid) return c;
        Clipmap n = new Clipmap(stapel, q, kern.cell, kern.xOfCol(0), kern.zOfRow(0), REICHWEITE);
        n.noData = AUSSEN;
        n.start();
        if (c != null) {
            n.update(lastX, lastZ);
            c.stop();
        }
        clip = n;
        return n;
    }

    private volatile double lastX, lastZ;

    /** Das Auge (je Bild). */
    public void auge(double x, double z) {
        lastX = x;
        lastZ = z;
        Clipmap c = clip;
        if (c != null) c.update(x, z);
    }

    /** Höhe am Ort aus der feinsten geladenen Stufe (m über NAVD88) oder NaN, solange nichts geladen ist. */
    public double hoehe(double x, double z) {
        Clipmap c = clip;
        return c == null ? Double.NaN : c.height(x, z);
    }

    /** Wartet, bis die Stufen um das zuletzt gemeldete Auge geladen sind (für Standbilder). */
    public boolean warten(long ms) {
        Clipmap c = clip;
        return c == null || c.await(ms);
    }

    public Clipmap clipmap() { return clip; }

    public TileStack stapel() { return stapel; }

    /** „Gelände: 13 Stufen à 193², 0,5 m bis 2 048 m … · kern_0.5m: …“ oder warum keins da ist. */
    public String zeile() {
        if (stapel == null) return "!Gelände: " + fehler;
        Clipmap c = clip;
        return c == null ? "Gelände: " + stapel.describe() : String.format(Locale.GERMAN, "Gelände (clipmap): %s", c.describe());
    }
}
