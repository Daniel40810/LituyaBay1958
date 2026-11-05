package com.dan.lituya.camera;

import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;

import java.util.List;
import java.util.Locale;

/**
 * Die Schaustücke 1 bis 11 (Werkbuch 10.3; 8 und 9 auf den Booten seit Phase 15a) als Vorgaben aus Kamera und Uhr (Phase 13b): Ort, Blick, Ereigniszeit und
 * Tempo. Fahrten kommen mit Phase 17 (Regiebücher); bis dahin steht die Kamera, und die Uhr läuft. Zeiten in Sekunden seit
 * dem ersten Stoß (Zeitleiste 2.2): Auslösung des Sturzes bei 93 s, Rechnung der Welle ab 105 s [G]. Lagen [G] aus den
 * GNIS-Orten in {@link Site}:
 * <ol>
 * <li><b>Abend vor Anker</b>: Ansicht 1, 22:00 Uhr, Echtzeit.</li>
 * <li><b>Das Beben</b>: über Cenotaph Island, Blick die Bucht hinauf, 5 s vor dem Stoß, Echtzeit.</li>
 * <li><b>Der Sturz</b>: 1,5 km südwestlich des Gilbert Inlet, Auge 150 m, Blick auf die Wand, 88 s, Zeitlupe 1:2.</li>
 * <li><b>Die Wand aus Wasser</b>: 1 km vor dem Inlet, Auge 80 m, 104,5 s, Zeitlupe 1:20.</li>
 * <li><b>524 m</b>: seit Werkbuch Welle W5 von Süden den Sporn hinauf (1,6 km, Auge 120 m), 126 s, Zeitlupe 1:5: die Zunge läuft
 * den Hang hinauf, die Bäume knicken (vorher von Südost auf die trockene Südflanke, 106 s, noch vor dem Auflauf).</li>
 * <li><b>Die Front</b>: 2,5 km nordöstlich von Cenotaph Island, Auge 60 m, Blick der Front entgegen, 112 s, Echtzeit, mit
 * den Netzen in der Szene (Isochronen).</li>
 * <li><b>Unter Wasser</b>: 1,5 km nordöstlich von Cenotaph Island, 3 m unter dem Pegel, Blick der Front entgegen und 35° hinauf zur Fläche (seit 13c; mit 15° lag das Snellsche Fenster über dem Bildrand), 115 s,
 * Zeitlupe 1:2 (bis Phase 17 knapp unter der Fläche, ohne Geröll und Stämme).</li>
 * </ol>
 * Seit Phase 14a (8 und 9 kommen mit späteren Phasen):
 * <ol start="10">
 * <li><b>Der Morgen danach</b>: 10.7.1958, 6:00 Uhr, vom Wasser 1,2 km vor dem Nordufer östlich von Cenotaph Island, Auge 20 m,
 * Blick nach Norden auf das Band: Fels, entrindete Stämme an der Spurlinie, der Holzteppich auf dem Wasser.</li>
 * <li><b>68 Jahre</b>: über der Bucht nordöstlich von Cenotaph Island, Auge 160 m, Blick die Bucht hinauf (062°), vom Morgen
 * danach im Jahres-Zeitraffer (68 Jahre je Minute) bis heute: Roterle zuerst, dann Fichte und Hemlock.</li>
 * </ol>
 */
public final class Schaustuecke {
    /**
     * Ein Schaustück: Nummer, Name, Lage (x, z), Blick (Grad von Nord), Neigung (Grad nach unten), Auge über Grund oder
     * Wasser (m; unter Wasser: m unter dem Pegel), Ereigniszeit (s seit dem ersten Stoß), Zeitraffer, unter Wasser,
     * Netze in der Szene.
     */
    public record Stueck(int nr, String name, double x, double z, double heading, double pitch, double auge, double t, double lapse,
                         boolean unter, boolean netze, boolean jahre) {
        @Override public String toString() { return nr + " " + name; }

        /** „1:20“, „Echtzeit“ */
        public String tempo() {
            if (jahre) return "68 Jahre je Minute";
            if (lapse == 1) return "Echtzeit";
            if (lapse < 1 && lapse > 0) return String.format(Locale.GERMAN, "Zeitlupe 1:%.0f", 1 / lapse);
            return String.format(Locale.GERMAN, "%.0f-fach", lapse);
        }
    }

    private static Stueck at(int nr, String name, double[] xz, double heading, double pitch, double auge, double t, double lapse, boolean unter, boolean netze) {
        return new Stueck(nr, name, xz[0], xz[1], heading, pitch, auge, t, lapse, unter, netze, false);
    }

    private static double[] xz(Site.Ort o) { return new double[]{o.x(), o.z()}; }

    /** Der Morgen danach: 10.7.1958, 6:00 Uhr Ortszeit, in Sekunden nach dem ersten Stoß (22:16). */
    public static final double MORGEN = (7 * 60 + 44) * 60;

    public static final List<Stueck> ALLE = List.of(
            at(1, "Abend vor Anker", xz(Site.ANCHORAGE), 62, 1, 3, -16 * 60, 1, false, false),
            at(2, "Das Beben", xz(Site.CENOTAPH), 52, 2, 40, -5, 1, false, false),
            at(3, "Der Sturz", Ansichten.versetzt(Site.GILBERT, 225, 1500), 40, -12, 150, 88, 0.5, false, false),
            at(4, "Die Wand aus Wasser", Ansichten.versetzt(Site.GILBERT, 215, 1000), 35, -4, 80, 104.5, 0.05, false, false),
            at(5, "524 m", Ansichten.versetzt(Site.SPORN, 185.5, 1557), 352, -8, 120, 126, 0.2, false, false),
            at(6, "Die Front", Ansichten.versetzt(Site.CENOTAPH, 60, 2500), 50, 2, 60, 112, 1, false, true),
            at(7, "Unter Wasser", Ansichten.versetzt(Site.CENOTAPH, 60, 1500), 50, -35, 3, 115, 0.5, true, false),
            at(8, "An Bord der Edrie", new double[]{347_545 - Site.E0, -(6_500_506 - Site.N0)}, 90, 6, 4, 200, 1, false, false),
            at(9, "Neben der Badger", xz(Site.ANCHORAGE), 90, 6, 4, 200, 1, false, false),
            new Stueck(10, "Der Morgen danach", 1000, -1300, 2, 4, 20, MORGEN, 1, false, false, false),
            new Stueck(11, "68 Jahre", -600, -400, 62, -6, 160, MORGEN, 1, false, false, true));

    /**
     * Phase 15a: Schaustücke auf einem Boot (Mitfahrt auf der vorgerechneten Bahn): Index in {@code gl.BooteGl.NAMEN}
     * (0 Edrie, 1 Badger) oder −1. 8: im Steuerhaus der Edrie, Blick voraus, mit Stampfen und Rollen; 9: nebenher, 25 m
     * an Steuerbord der Badger, Blick nach Backbord auf das Boot.
     */
    public static int boot(Stueck s) { return s.nr() == 8 ? 0 : s.nr() == 9 ? 1 : -1; }

    /** Blick der Mitfahrt zur Seite (Grad, negativ nach Backbord). */
    public static double blick(Stueck s) { return s.nr() == 9 ? -90 : 0; }

    /** Abstand des Auges an Steuerbord (m) und Höhe über dem Schwerpunkt bei „nebenher“. */
    public static final double NEBENHER = 25, NEBENHER_HOCH = 4;

    public static Stueck byNr(int nr) {
        for (Stueck s : ALLE) if (s.nr() == nr) return s;
        return null;
    }

    public static Stueck byName(String name) {
        if (name == null || name.isBlank()) return null;
        try { return byNr(Integer.parseInt(name.trim())); } catch (NumberFormatException e) { /* Name */ }
        String n = name.replace(" ", "").toLowerCase(Locale.ROOT);
        for (Stueck s : ALLE) if (s.name().replace(" ", "").toLowerCase(Locale.ROOT).equals(n)) return s;
        return null;
    }

    /** Stellt Kamera und Uhr auf das Schaustück (Grund aus ground, Pegel aus c.waterLevel). */
    public static void apply(Stueck s, Camera c, SceneClock clock, java.util.function.DoubleBinaryOperator ground) {
        c.stopFahrt();
        c.stopMitfahrt();
        double g = ground.applyAsDouble(s.x(), s.z());
        c.groundLevel = g;
        double y;
        if (s.unter()) {
            c.diving = true;
            y = c.waterLevel - s.auge();
        } else {
            c.diving = false;
            y = Math.max(g, c.waterLevel) + s.auge();
        }
        c.place(s.x(), y, s.z(), s.heading(), s.pitch());
        clock.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + s.t() * 1000);
        clock.zeitstand = s.t() < 0 && s.nr() == 1 ? SceneClock.Zeitstand.VOR : s.t() >= 3600 ? SceneClock.Zeitstand.DANACH : SceneClock.Zeitstand.WELLE;
        clock.lapse = s.lapse();
    }

    /** „4 Die Wand aus Wasser (t = 104,5 s, Zeitlupe 1:20)“ */
    public static String describe(Stueck s) {
        if (s.t() >= 3600) return String.format(Locale.GERMAN, "%s (10.7.1958 6:00, %s)", s, s.tempo());
        return String.format(Locale.GERMAN, "%s (t = %s s, %s%s)", s, s.t() % 1 == 0 ? String.format(Locale.GERMAN, "%.0f", s.t()) : String.format(Locale.GERMAN, "%.1f", s.t()),
                s.tempo(), s.unter() ? ", unter Wasser" : "");
    }

    private Schaustuecke() { }
}
