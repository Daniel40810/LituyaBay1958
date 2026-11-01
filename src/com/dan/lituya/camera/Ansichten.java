package com.dan.lituya.camera;

import com.dan.lituya.scene.Site;

import java.util.List;
import java.util.Locale;

/**
 * Die neun Ansichten nach Namen auf den Tasten 1 bis 9 (Werkbuch 10.3 Schaustücke, 11.2). Seit Phase 1 über dem
 * Wasser (ohne Gelände), ab Phase 4 auf dem Gelände; ab Phase 17 kommen die Presets aus {@code LB_NET_SICHT} dazu
 * (Zeugenstandorte, Millers Fotostandorte). Lagen aus den GNIS-Orten in {@link Site}, Blickrichtungen gestaltet [G]:
 * <ol>
 * <li><b>Anchorage Cove</b> (Schaustück 1 „Abend vor Anker“): wo die Edrie lag, Auge 3 m über dem Wasser, Blick die
 * Bucht hinauf nach Nordost.</li>
 * <li><b>Eingang</b>: vor La Chaussee Spit auf See, Auge 6 m, Blick durch den Eingang in die Bucht (Schaustück 9).</li>
 * <li><b>Cenotaph Island</b>: über der Insel in der Mitte der Bucht, Auge 40 m, Blick nach Nordost zum T-Kopf.</li>
 * <li><b>Gilbert Inlet</b> (Schaustück 3 „Der Sturz“): im Inlet, Auge 60 m, Blick nach Nordost auf die Wand des
 * Felssturzes.</li>
 * <li><b>Sporn 524 m</b> (Schaustück 5): auf der Höhe des Auflaufs über dem Sporn [G], Blick über das Inlet.</li>
 * <li><b>Mudslide Creek</b>: Südufer, wo die Spurlinie 208 m erreichte (MILLER60), Auge 20 m, Blick nach Nordwest.</li>
 * <li><b>Draufsicht</b>: 14 km über Cenotaph Island, fast senkrecht, Norden oben: der ganze Kern.</li>
 * <li><b>Von See</b>: 18 km vor dem Eingang auf dem Golf von Alaska, Auge 15 m, Blick nach Nordost auf die Küste und
 * die Fairweather-Kette; die Kimm liegt bei 15 m Augenhöhe 14,8 km weit.</li>
 * <li><b>Fern</b>: 70 km südwestlich über dem Golf, 6 km hoch, Blick nach Nordost über den Fern.</li>
 * </ol>
 */
public final class Ansichten {
    /**
     * Eine Ansicht: Taste, Name, Lage (x, z), Blickrichtung (Grad von Nord), Neigung (Grad nach unten, negativ nach
     * oben), Augenhöhe über Grund oder Wasser (m) und ab welcher Phase sie einen Ort hat.
     */
    public record Ansicht(int key, String name, double x, double z, double heading, double pitch, double eyeAboveGround, int phase) {
        @Override public String toString() { return key + " " + name; }
    }

    /** Ein Szenenpunkt dist m vom Ort in Richtung deg (Grad von Nord). */
    static double[] versetzt(Site.Ort o, double deg, double dist) {
        double a = Math.toRadians(deg);
        return new double[]{o.x() + Math.sin(a) * dist, o.z() - Math.cos(a) * dist};
    }

    private static Ansicht at(int key, String name, double[] xz, double heading, double pitch, double above) {
        return new Ansicht(key, name, xz[0], xz[1], heading, pitch, above, 1);
    }

    private static double[] xz(Site.Ort o) { return new double[]{o.x(), o.z()}; }

    public static final List<Ansicht> ALLE = List.of(
            at(1, "Anchorage Cove", xz(Site.ANCHORAGE), 62, 1, 3),
            at(2, "Eingang", versetzt(Site.CHAUSSEE, 225, 1500), 45, 1, 6),
            at(3, "Cenotaph Island", xz(Site.CENOTAPH), 52, 2, 40),
            at(4, "Gilbert Inlet", versetzt(Site.GILBERT, 225, 900), 40, -2, 60),
            at(5, "Sporn 524 m", xz(Site.SPORN), 50, 10, 524),
            at(6, "Mudslide Creek", xz(Site.MUDSLIDE), 300, 1, 20),
            at(7, "Draufsicht", xz(Site.CENOTAPH), 0, 89, 14_000),
            at(8, "Von See", versetzt(Site.CHAUSSEE, 215, 18_000), 40, 0, 15),
            at(9, "Fern", versetzt(Site.LITUYA_BAY, 225, 70_000), 45, 6, 6000));

    /** Taste der Ansicht „Anchorage Cove“ (Schaustück 1). */
    public static final int ANCHORAGE = 1;

    /** Ob die Ansicht schon einen Ort hat. */
    public static boolean ready(Ansicht a, int phase) { return a.phase() <= phase; }

    public static Ansicht byName(String name) {
        for (Ansicht a : ALLE) if (a.name().equalsIgnoreCase(name) || a.name().replace(" ", "").equalsIgnoreCase(name)) return a;
        String n = name.replace(" ", "").replace("-", "").toLowerCase(Locale.ROOT);
        for (Ansicht a : ALLE) {
            String k = a.name().replace(" ", "").replace("-", "").toLowerCase(Locale.ROOT).replace("ü", "ue").replace("ö", "oe").replace("ä", "ae").replace("ß", "ss");
            if (k.equals(n) || k.replaceAll("[0-9m]+$", "").equals(n)) return a;
        }
        try { return byKey(Integer.parseInt(name.trim())); } catch (NumberFormatException e) { return null; }
    }

    public static Ansicht byKey(int key) {
        for (Ansicht a : ALLE) if (a.key() == key) return a;
        return null;
    }

    /** Stellt die Kamera auf die Ansicht über dem Grund {@code ground}; false, wenn sie in dieser Phase noch keinen Ort hat. */
    public static boolean apply(Ansicht a, Camera c, int phase, java.util.function.DoubleBinaryOperator ground) {
        if (!ready(a, phase)) return false;
        c.stopFahrt();
        c.stopMitfahrt();
        c.diving = false;
        double g = Math.max(ground.applyAsDouble(a.x(), a.z()), c.waterLevel);
        c.groundLevel = ground.applyAsDouble(a.x(), a.z());
        c.place(a.x(), g + a.eyeAboveGround(), a.z(), a.heading(), a.pitch());
        return true;
    }

    /** „1 Anchorage Cove (Auge 3,0 m über Grund, Blick 062°)“ */
    public static String describe(Ansicht a, int phase) {
        if (!ready(a, phase)) return a + " (folgt in Phase " + a.phase() + ")";
        return String.format(Locale.GERMAN, "%s (Auge %.1f m über Grund oder Wasser, Blick %03.0f°)", a, a.eyeAboveGround(), a.heading());
    }

    private Ansichten() { }
}
