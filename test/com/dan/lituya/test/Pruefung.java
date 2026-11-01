package com.dan.lituya.test;

import com.dan.lituya.app.Bibliothek;
import com.dan.lituya.camera.Ansichten;
import com.dan.lituya.camera.Camera;
import com.dan.lituya.gl.Mat4;
import com.dan.lituya.gl.Quality;
import com.dan.lituya.gl.Renderer;
import com.dan.lituya.netz.Lod;
import com.dan.lituya.scene.Geo;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Sun;
import com.dan.lituya.wiring.Wiring;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Prüfung der App ohne Grafikkarte und ohne Datenbank (Ant-Target {@code pruefung}), Stand Phase 1: Raum, Gebiete und
 * Orte aus GNIS, die Uhren (Ortszeit 1958 und heute, Simulationszeit, Sonne zur Bebenzeit gegen Werkbuch 2.1),
 * Ansichten und Kamera, Kimm, Qualitätsstufen und Regelung, Renderer.jar, die Shader-Dateien, der Weg zum Network Data
 * Model ({@code sdonm.jar}, Treiber 21 in {@code lib/lod/}, F26 A), Regel 3.3 (kein Laufzeitcode liest {@code download/})
 * und Paketvertrag 13 (Oracle nur in {@code data}, {@code netz} und {@code tools}). Endet mit „ERGEBNIS: alles in
 * Ordnung“ oder der Zahl der Fehler (Rückgabewert 1).
 */
public final class Pruefung {
    private static int bad, n, hinweise;

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        System.out.println("LituyaBay1958 " + Renderer.VERSION + " · Prüfung ohne Grafikkarte");

        teil("Raum und Gebiete (F1 A, F2 A, F41 A)");
        double[] ll = Geo.UTM8N.inverse(352_000, 6_503_000), en = Geo.UTM8N.forward(ll[0], ll[1]);
        check(Math.abs(en[0] - 352_000) < 0.001 && Math.abs(en[1] - 6_503_000) < 0.001, "UTM 8 N hin und zurück auf 1 mm");
        double[] gi = Geo.UTM8N.forward(58.6733333, -137.5022222);
        check(Math.abs(gi[0] - Site.GILBERT.e()) < 1.5 && Math.abs(gi[1] - Site.GILBERT.n()) < 1.5,
                f("Gilbert Inlet aus GNIS (58,6733° N 137,5022° W): E %.1f N %.1f gegen geojson %.1f / %.1f", gi[0], gi[1], Site.GILBERT.e(), Site.GILBERT.n()));
        double[] fw = Geo.UTM8N.forward(58.9064974, -137.52658);
        check(Math.abs(fw[0] - Site.FAIRWEATHER.e()) < 1.5 && Math.abs(fw[1] - Site.FAIRWEATHER.n()) < 1.5,
                f("Mount Fairweather aus GNIS: E %.1f N %.1f", fw[0], fw[1]));
        check(Site.KERN.breite() == 24_000 && Site.KERN.hoehe() == 18_000, "Kern 24 × 18 km");
        check(Site.SZENE.breite() == 66_000 && Site.SZENE.hoehe() == 60_000, "Szene 66 × 60 km");
        check(Site.FERN.breite() == 260_000 && Site.FERN.hoehe() == 240_000, "Fern 260 × 240 km");
        check(Site.KERN.contains(0, 0) && Site.gebiet(0, 0).equals("Kern"), f("Ursprung in der Mitte des Kerns: %s", Site.dms(Site.LAT, Site.LON)));
        double[] k = Site.KERN.scene();
        check(Math.abs(k[0] + k[2]) < 1e-6 && Math.abs(k[1] + k[3]) < 1e-6, "Kern liegt symmetrisch um den Ursprung");
        for (Site.Ort o : new Site.Ort[]{Site.GILBERT, Site.CENOTAPH, Site.ANCHORAGE, Site.CHAUSSEE, Site.SPORN, Site.MUDSLIDE, Site.CRILLON_INLET})
            check(Site.KERN.contains(o.x(), o.z()), o.name() + " liegt im Kern");
        int inSzene = 0;
        for (Site.Ort o : Site.ORTE) if (Site.SZENE.contains(o.x(), o.z())) inSzene++;
        check(inSzene == Site.ORTE.length && Site.ORTE.length <= 32, f("alle %d Orte in der Szene, höchstens 32 für den Shader", Site.ORTE.length));
        double bucht = Math.hypot(Site.GILBERT.x() - Site.CHAUSSEE.x(), Site.GILBERT.z() - Site.CHAUSSEE.z());
        check(bucht > 10_000 && bucht < 13_000, f("Eingang bis Gilbert Inlet %.1f km (Bucht 12 km, FRITZ01)", bucht / 1000));
        check(Math.abs(Site.horizon(Site.FAIRWEATHER_HOEHE) / 1000 - 264) < 3, f("Kimm des Fairweather: %.0f km (Werkbuch F1: 264 km)", Site.horizon(Site.FAIRWEATHER_HOEHE) / 1000));
        check(Math.abs(Site.horizon(3) / 1000 - 6.6) < 0.2, f("Kimm vom Kutter (Auge 3 m): %.1f km", Site.horizon(3) / 1000));
        check(Site.SPORN.quelle().equals("[G]"), "Sporn als [G] markiert (Lage prüft Phase 2 am Lidar)");

        teil("Uhren und Sonne (Werkbuch 2.1, 4.5)");
        SceneClock clock = new SceneClock();
        check(clock.label().equals("9.7.1958 22:00 PST"), "Start " + clock.label());
        check(clock.simLabel().equals("t = −16:00"), "Simulationszeit am Start " + clock.simLabel());
        SceneClock b = new SceneClock();
        b.set(SceneClock.Zeitstand.WELLE);
        check(Math.abs(b.simSeconds()) < 0.5 && b.timeLabel().equals("22:16 PST"), "Zeitstand „die Welle“: " + b.label() + ", " + b.simLabel());
        check(Instant.ofEpochMilli((long) b.epochMillis()).toString().startsWith("1958-07-10T06:16"), "22:16 Ortszeit = 10.7.1958 06:16 UTC");
        double day = LocalDate.of(1958, 7, 9).atStartOfDay(SceneClock.ZONE).toInstant().toEpochMilli();
        double down = Sun.crossing(day, Site.LAT, Site.LON, false);
        // Werkbuch 2.1 nannte im Entwurf 22:04; nachgerechnet (Phase 1) ist es 22:14: Juneau hat am 9. Juli Untergang gegen
        // 21:59 bei UTC−8, die Bucht liegt 3,2° weiter westlich (+13 min). Die Sonne steht beim Stoß damit 2 min nach dem
        // Untergang rund 1° unter der Kimm, wie 2.1 auch sagt.
        double minuten = (down - day) / 60_000.0, soll = 22 * 60 + 14;
        check(Math.abs(minuten - soll) < 3, f("Sonnenuntergang 9.7.1958 um %02d:%02d PST (gerechnet, Werkbuch 2.1 berichtigt: 22:14)", (int) minuten / 60, (int) minuten % 60));
        double[] sb = b.sun();
        check(sb[0] < 0 && sb[0] > -2.5, f("Sonne beim ersten Stoß %.1f° (Werkbuch 2.1: rund 1° unter der Kimm), aus %03.0f°", sb[0], sb[1]));
        double tief = 90;
        for (int m = 0; m < 24 * 60; m += 5) tief = Math.min(tief, Sun.position(day + m * 60_000.0, Site.LAT, Site.LON)[0]);
        check(tief > -10.5 && tief < -7.5, f("tiefster Stand der Sonne in der Nacht %.1f° (helle Julinacht, nautische Dämmerung)", tief));
        SceneClock h = new SceneClock();
        h.set(SceneClock.Zeitstand.HEUTE);
        check(h.zoneLabel().equals("AKDT") && h.label().startsWith("9.7.2026 22:00"), "Zeitstand „heute“: " + h.label());
        h.lapse = 3600;
        h.step(1);
        check(Math.abs(h.hour() - 23) < 1e-6, "Zeitraffer 1 h/s");

        teil("Ansichten und Kamera");
        for (Ansichten.Ansicht a : Ansichten.ALLE) {
            Camera c = new Camera();
            c.waterLevel = 0;
            Ansichten.apply(a, c, Renderer.PHASE, (x, z) -> Renderer.KEIN_GRUND);
            c.snap();
            boolean drin = Site.FERN.contains(c.eye[0], c.eye[2]);
            check(drin && c.eye[1] >= Math.min(a.eyeAboveGround(), Camera.MIN_EYE) - 0.05 && Math.abs(c.heading() - a.heading()) < 0.5,
                    f("%s: Auge %.1f m über dem Wasser, Blick %03.0f°, %s, %s", a, c.eye[1], c.heading(), Site.gebiet(c.eye[0], c.eye[2]), Site.naechsterOrt(c.eye[0], c.eye[2])));
        }
        check(Ansichten.byName("AnchorageCove") != null && Ansichten.byName("Sporn") != null && Ansichten.byName("7") != null && Ansichten.byName("VonSee") != null,
                "Namen ohne Leerzeichen und Tasten");
        Camera home = new Camera();
        home.snap();
        check(home.heading() > 30 && home.heading() < 80 && home.eye[1] > 1000 && Site.gebiet(home.eye[0], home.eye[2]).equals("Kern"),
                f("Grundansicht von Südwest die Bucht hinauf: Blick %03.0f°, Auge %.0f m", home.heading(), home.eye[1]));
        double[] fr = Site.FERN.scene();
        check(Camera.X_MIN == fr[0] && Camera.X_MAX == fr[2] && Camera.Z_MIN == fr[1] && Camera.Z_MAX == fr[3], "Drehpunkt bleibt im Fern");
        Camera z = new Camera();
        z.zoom(200);
        check(z.dist == Camera.MAX_DIST, f("weitester Abstand %.0f km", z.dist / 1000));
        float[] vp = Mat4.mul(Mat4.perspective(Camera.FOVY, 16 / 9.0, 0.3, 400_000, true), home.view());
        check(!Float.isNaN(Mat4.invert(vp)[0]), "Projektion bis 400 km umkehrbar (umgekehrte Tiefe)");

        teil("Renderer, Qualität, Verdrahtung");
        Renderer rd = new Renderer(clock);
        check(rd.groundHeight(0, 0) == Renderer.KEIN_GRUND, "Phase 1 ohne Gelände");
        rd.setQuality(Quality.MITTEL);
        rd.regulateForTest(30, 10_000_000_000L);
        check(rd.renderScale < Quality.MITTEL.renderScale && rd.renderScale >= Renderer.MIN_SCALE, f("Regelung senkt die Auflösung bei 30 ms: %.0f %%", rd.renderScale * 100));
        for (String l : rd.hudLines()) if (l.startsWith("!")) System.out.println("    HUD: " + l.substring(1));
        check(rd.hudLines().length >= 12 && rd.uhrenZeile().contains("t = −16:00"), "HUD: " + rd.uhrenZeile());
        Wiring w = new Wiring(clock);
        check(w.pegel == 0 && w.zeile().contains("NAVD88"), w.zeile());

        teil("Renderer.jar und Shader");
        List<String> pk = Bibliothek.pakete();
        check(Bibliothek.fehlend().isEmpty(), "Renderer.jar: " + Bibliothek.kurz());
        check(Bibliothek.neuFaellig(Renderer.PHASE).isEmpty(), "neu (kommen in ihren Phasen): " + Bibliothek.neuStand());
        check(pk.contains("kino") && pk.contains("store") && pk.contains("route"), "kino, store und route da");
        for (String[] prog : rd.shaderFiles())
            for (String file : prog)
                check(Renderer.class.getResource("/com/dan/lituya/gl/shader/" + file) != null, "Shader " + file + " im Klassenpfad");
        for (String inc : new String[]{"common.glsl", "marken.glsl"})
            check(Renderer.class.getResource("/com/dan/lituya/gl/shader/" + inc) != null, "Baustein " + inc + " im Klassenpfad");

        teil("Network Data Model: sdonm.jar und Treiber 21 (F26 A)");
        check(new File(Lod.lib(), "sdonm.jar").isFile(), "lib/sdonm.jar da (" + new File(Lod.lib(), "sdonm.jar").length() / 1024 + " KB)");
        check(new File(Lod.lodDir(), "LIESMICH.txt").isFile(), "lib/lod/LIESMICH.txt da");
        if (Lod.treiber() == null) hinweis("kein Treiber in lib/lod: ant lod holt ojdbc11.jar aus ORACLE_HOME\\jdbc\\lib (gebraucht ab Phase 3)");
        else {
            Lod.Probe p = Lod.probe();
            check(p.ok(), p.text());
        }

        teil("Regel 3.3 und Paketvertrag 13");
        File src = new File("src");
        if (src.isDirectory()) {
            int download = 0, oracle = 0;
            try (Stream<Path> st = Files.walk(src.toPath())) {
                for (Path p : (Iterable<Path>) st.filter(x -> x.toString().endsWith(".java"))::iterator) {
                    String txt = Files.readString(p, StandardCharsets.UTF_8), pfad = p.toString().replace('\\', '/');
                    boolean tool = pfad.contains("/lituya/tools/"), db = pfad.contains("/lituya/data/") || pfad.contains("/lituya/netz/");
                    if (!tool && txt.contains("new File(\"download")) { download++; System.out.println("    liest download/: " + pfad); }
                    if (!tool && !db && (txt.contains("import java.sql") || txt.contains("import oracle.") || txt.contains("oracle.spatial."))) {
                        oracle++;
                        System.out.println("    kennt Oracle: " + pfad);
                    }
                }
            }
            check(download == 0, "kein Laufzeitcode liest aus download/ (Regel 3.3; nur die Aufbereitung in Phase 2)");
            check(oracle == 0, "Oracle nur in com.dan.lituya.data, .netz und .tools (Paketvertrag 13)");
        } else System.out.println("    (src nicht gefunden, übersprungen)");

        System.out.printf(Locale.GERMAN, "%n%d Prüfungen in %.1f s%s%n", n, (System.nanoTime() - t0) / 1e9, hinweise > 0 ? ", " + hinweise + " Hinweis(e)" : "");
        System.out.println(bad == 0 ? "ERGEBNIS: alles in Ordnung" : "ERGEBNIS: " + bad + " Fehler");
        System.exit(bad == 0 ? 0 : 1);
    }

    static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    static void teil(String t) { System.out.println("\n— " + t); }

    static void hinweis(String t) {
        hinweise++;
        System.out.println("  HINWEIS " + t);
    }

    static void check(boolean c, String what) {
        n++;
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private Pruefung() { }
}
