package com.dan.lituya.test;

import com.dan.lituya.app.Bibliothek;
import com.dan.lituya.camera.Ansichten;
import com.dan.lituya.camera.Camera;
import com.dan.lituya.gl.Licht;
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
 * und Paketvertrag 13 (Oracle nur in {@code data}, {@code netz} und {@code tools}); ab Phase 2 {@code daten/}, ab Phase 3
 * die Skripte in {@code db/netz}, die LOD-Analysen auf dem Probenetz im Speicher und das Herkunftsnetz, ab Phase 4 das Gelände
 * (clipmap: 1 000 Stichproben gegen die Kacheln, Naht). Endet mit „ERGEBNIS: alles in
 * Ordnung“ oder der Zahl der Fehler (Rückgabewert 1).
 */
public final class Pruefung {
    private static int bad, n, hinweise;

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        System.out.println("LituyaBay1958 " + Renderer.VERSION + " · Prüfung ohne Grafikkarte und ohne download/");

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
        check(rd.groundHeight(0, 0) == Renderer.KEIN_GRUND, "ohne geladene Stufen kein Grund (der Ladefaden der Clipmap gibt erst nach dem ersten Bild frei)");
        // Werkbuch Welle W0 (K1): Start in „Hoch“, geregelt auf 30 Bilder/s, nie unter 85 %
        check(rd.quality() == Quality.HOCH && rd.renderScale == 1.0, "W0: die App startet in „Hoch“ mit voller Auflösung");
        check(Renderer.MIN_SCALE >= 0.85 && rd.targetFps == 30, f("W0: Regelung auf %.0f Bilder/s, nie unter %.0f %%", rd.targetFps, Renderer.MIN_SCALE * 100));
        rd.regulateForTest(50, 10_000_000_000L);
        check(rd.renderScale < Quality.HOCH.renderScale && rd.renderScale >= Renderer.MIN_SCALE, f("Regelung senkt die Auflösung bei 50 ms: %.0f %%", rd.renderScale * 100));
        rd.renderScale = 1.0;
        // W0 (K2): Kantenglättung an, Versatz nach Halton, Rückprojektion umkehrbar
        check(rd.taa && Math.abs(Renderer.halton(1, 2) - 0.5) < 1e-12 && Math.abs(Renderer.halton(2, 3) - 2 / 3.0) < 1e-12, "W0: Kantenglättung (TAA) an, Versatz nach Halton 2/3");
        {
            double[] m = Renderer.mulD(Mat4.perspective(Camera.FOVY, 16 / 9.0, 0.3, 400_000, true), home.view());
            double[] e = Renderer.mulDD(m, Renderer.invertD(m));
            double err = 0;
            for (int i = 0; i < 16; i++) err = Math.max(err, Math.abs(e[i] - (i % 5 == 0 ? 1 : 0)));
            check(err < 1e-9, f("W0: Rückprojektion des Verlaufs in double umkehrbar (Fehler %.1e)", err));
        }
        // Werkbuch Welle W1: Licht aus com.dan.sky, Horizontkarten über Kern und Szene, die Gipfel im groben Gitter
        {
            double[] lfr = rd.licht.feinRect(), gr = rd.licht.grobRect();
            boolean drin = true;
            for (Site.Ort o : new Site.Ort[]{Site.FAIRWEATHER, Site.CRILLON, Site.GILBERT, Site.CENOTAPH})
                drin &= o.x() > gr[0] && o.x() < gr[0] + gr[2] && o.z() > gr[1] && o.z() < gr[1] + gr[2];
            check(rd.licht.an && drin && lfr[2] == Licht.FEIN_KANTE, f("W1: Licht an, Horizontkarten %.0f km (fein) und %.0f km (grob) decken Fairweather, Crillon, Gilbert Inlet", lfr[2] / 1000, gr[2] / 1000));
            for (String sh : new String[]{"schatten.comp", "sonnenkarte.comp"})
                check(Renderer.class.getResource("/com/dan/lituya/gl/shader/" + sh) != null, "W1: Shader " + sh + " im Klassenpfad");
            // Kimm aus 4 000 m (mit Brechung): rund 1,9°; das Alpenglühen hält dort bis gut 1,5° unter der Kimm
            double dip = Math.toDegrees(Math.sqrt(2 * 4000 / Licht.R_EFF));
            check(dip > 1.7 && dip < 2.1, f("W1: Kimm aus 4 000 m %.2f° unter der Waagerechten", dip));
        }
        // Werkbuch Welle W2: Wasser aus com.dan.ocean (Gletschersee), Spiegel der Berge
        check(rd.meer.an && rd.meer.spiegel && rd.meer.ocean.fetchLimited && rd.meer.ocean.swellHeight == 0,
                f("W2: Meer aus com.dan.ocean, Gletschersee ohne Dünung, H_s %.2f m, Spiegel an", rd.meer.ocean.state().totalHs()));
        check(Renderer.class.getResource("/com/dan/lituya/gl/shader/spiegel.frag") != null, "W2: Shader spiegel.frag im Klassenpfad");
        // Werkbuch Welle W3: Beben (Hüllkurve wie der Klang), Einschlag zur Zeit des Eintauchens
        check(Renderer.bebenStaerke(-1) == 0 && Renderer.bebenStaerke(20) == 1 && Renderer.bebenStaerke(70) == 0 && rd.zittern,
                "W3: Beben 0 … 60 s, voll von 5 bis 40 s, Kamerazittern an");
        check(com.dan.lituya.gl.EreignisGl.EINSCHLAG_S == 105 && com.dan.lituya.gl.EreignisGl.ABBRUCH_S == 93, "W3: Abbruch 93 s, Einschlag 105 s nach dem ersten Stoß");
        // Werkbuch Welle W3b: Gischt aus den Daten der Welle (keine Kuppel um einen Punkt), Texturen auf den Hängen
        try {
            String med = new String(Renderer.class.getResourceAsStream("/com/dan/lituya/gl/shader/medien.frag").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String gel = new String(Renderer.class.getResourceAsStream("/com/dan/lituya/gl/shader/gelaende.frag").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String wel = new String(Renderer.class.getResourceAsStream("/com/dan/lituya/gl/shader/welle.frag").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            check(med.contains("gischtDichte") && !med.contains("vorhangDichte") && med.contains("uFlut"), "W3b: Gischt aus Ankunft und höchstem Stand der Welle, keine Kuppel");
            check(gel.contains("/com/dan/surface/gl/surface.glsl") && gel.contains("vec3 hang(") && gel.contains("uFlut"), "W3b: Gelände mit com.dan.surface und Flut (abgeschürfte Hänge)");
            check(wel.contains("vTiefe"), "W3b: dünner Film am Hang fällt weg (K11)");
        } catch (java.io.IOException | RuntimeException e) { check(false, "W3b: Shader lesen: " + e); }
        check(com.dan.lituya.gl.EreignisGl.GISCHT_NACHLAUF > 10 && com.dan.lituya.gl.EreignisGl.UNIT_FLUT != com.dan.lituya.gl.EreignisGl.UNIT_ABRISS, "W3b: Gischt bleibt 40 s, Texturstelle der Flut frei");
        // W0 (K6, WF11 A): keine Karte und keine Netzlinien beim Start
        check(!rd.netzblick.bildImBild && !rd.netzblick.inSzene && !rd.showHud && !rd.showMarks, "W0: beim Start weder Netzblick noch HUD noch Marken");
        // W0 (Befund K8): keine leeren Versprechen im Bedienfeld
        {
            File cp = new File("src/com/dan/lituya/ui/ControlPanel.java");
            if (cp.isFile()) {
                String t = new String(java.nio.file.Files.readAllBytes(cp.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                check(!t.contains("Kommt mit Phase") && !t.contains("kommt mit Phase"), "W0: kein Block im Bedienfeld mit „kommt mit Phase …“ statt Inhalt");
            } else hinweis("ControlPanel.java nicht gefunden, leere Versprechen nicht geprüft");
        }
        for (String l : rd.hudLines()) if (l.startsWith("!")) System.out.println("    HUD: " + l.substring(1));
        check(rd.hudLines().length >= 12 && rd.uhrenZeile().contains("t = −16:00"), "HUD: " + rd.uhrenZeile());
        Wiring w = new Wiring(clock);
        check(w.pegel == 0 && w.zeile().contains("NAVD88"), w.zeile());

        teil("Renderer.jar und Shader");
        List<String> pk = Bibliothek.pakete();
        check(Bibliothek.fehlend().isEmpty(), "Renderer.jar: " + Bibliothek.kurz());
        check(Bibliothek.neuFaellig(String.valueOf(Renderer.PHASE)).isEmpty(), "neu (kommen in ihren Phasen): " + Bibliothek.neuStand());
        if (!Bibliothek.nachzuholen(String.valueOf(Renderer.PHASE)).isEmpty())
            hinweis("aus einer Phase der App nachzuholen: " + String.join(", ", Bibliothek.nachzuholen(String.valueOf(Renderer.PHASE))));
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

        teil("Aufbereitung (Phase 2): Rechenwege");
        check(com.dan.lituya.tools.Gelaende.mischen(150f, -90f) == 0 && com.dan.lituya.tools.Gelaende.mischen(0.8f, -40f) == 1
                && com.dan.lituya.tools.Gelaende.mischen(Float.NaN, -12f) == 1 && com.dan.lituya.tools.Gelaende.mischen(2f, 1.5f) == 0
                && com.dan.lituya.tools.Gelaende.mischen(Float.NaN, Float.NaN) == -1, "Mischung: hohes Land bleibt, flach gelegtes Wasser nimmt den Grund");
        double[] al = new com.dan.lituya.tools.Abbildung.Albers(50, -154, 55, 65, 0, 0).vor(-137.6, 58.64);
        check(Math.abs(al[0] - 939_594.6) < 0.5 && Math.abs(al[1] - 1_076_845.3) < 0.5, f("Alaska Albers (IfSAR) wie geo.py: %.1f / %.1f", al[0], al[1]));
        {
            java.util.Random rnd = new java.util.Random(3);
            int np = 400;
            double[] xy = new double[np * 2], zz = new double[np];
            for (int i = 0; i < np; i++) {
                xy[2 * i] = 350_000 + rnd.nextDouble() * 2000;
                xy[2 * i + 1] = 6_500_000 + rnd.nextDouble() * 2000;
                zz[i] = 0.02 * (xy[2 * i] - 350_000) - 0.03 * (xy[2 * i + 1] - 6_500_000) - 40;
            }
            com.dan.lituya.tools.Delaunay d = new com.dan.lituya.tools.Delaunay(xy);
            com.dan.lituya.tools.Gitter g = new com.dan.lituya.tools.Gitter(350_000, 6_502_000, 20, 100, 100);
            float[] v = g.leer();
            com.dan.lituya.tools.Delaunay.aufGitter(xy, zz, g, 1e9, v);
            double worst = 0;
            int voll = 0;
            for (int j = 0; j < g.h(); j++)
                for (int i = 0; i < g.w(); i++) {
                    float x = v[j * g.w() + i];
                    if (Float.isNaN(x)) continue;
                    voll++;
                    worst = Math.max(worst, Math.abs(x - (0.02 * (g.ce(i) - 350_000) - 0.03 * (g.cn(j) - 6_500_000) - 40)));
                }
            check(d.triangles.length / 3 > np && d.triangles.length / 3 < 2 * np && voll > 8000 && worst < 1e-3,
                    f("Delaunay: %d Dreiecke aus %d Punkten, Ebene auf %d Zellen genau (%.1e m)", d.triangles.length / 3, np, voll, worst));
        }

        teil("Aufbereitung (Phase 2): daten/");
        File daten = new File(System.getProperty("lituya.daten", "daten")), inhalt = new File(daten, "INHALT.txt");
        if (!inhalt.isFile()) hinweis("noch keine aufbereiteten Daten (daten/INHALT.txt fehlt): ant aufbereitung");
        else {
            int fehlt = 0, dateien = 0;
            for (String zl : Files.readAllLines(inhalt.toPath(), StandardCharsets.UTF_8)) {
                if (zl.startsWith("#") || zl.isBlank()) continue;
                String[] p = zl.split("\t");
                dateien++;
                File x = new File(daten, p[0]);
                if (!x.isFile() || x.length() != Long.parseLong(p[1])) { fehlt++; if (fehlt < 5) System.out.println("    fehlt oder anders: " + p[0]); }
            }
            check(fehlt == 0, f("daten/INHALT.txt: %d Dateien, alle da und gleich groß", dateien));
            for (com.dan.lituya.scene.Stufe st : com.dan.lituya.scene.Stufe.values()) {
                File[] kd = st.ordner(daten).listFiles((dd, nm) -> nm.endsWith(".r32"));
                int sollK = st.nx() * st.ny(), ist = kd == null ? 0 : kd.length;
                check(ist == sollK && new File(st.ordner(daten), "STUFE.txt").isFile(), f("Gelände %s: %d von %d Kacheln", st, ist, sollK));
            }
            for (String g : new String[]{"heute_4m.r32", "wasser_4m.u8", "1940_20m.r32", "1959_20m.r32", "GRUND.txt"})
                check(new File(daten, "grund/" + g).isFile(), "grund/" + g);
            check(new File(daten, "namen.tsv").isFile(), "namen.tsv");
        }

        teil("Gelände (Phase 4): clipmap über daten/gelaende");
        {
            com.dan.lituya.scene.Land land = new com.dan.lituya.scene.Land(new File(daten, "gelaende"));
            if (!land.da()) hinweis("kein Gelände in daten/gelaende: ant aufbereitung");
            else {
                com.dan.clipmap.Clipmap c = land.clipmap(com.dan.clipmap.Clipmap.Quality.MEDIUM);
                check(c.levels() >= 12 && c.half() * c.cell(c.levels() - 1) >= com.dan.lituya.scene.Land.REICHWEITE,
                        f("%d Stufen à %d², %.1f m bis %.0f m, reicht %.0f km", c.levels(), c.grid, c.cell(0), c.cell(c.levels() - 1), c.half() * c.cell(c.levels() - 1) / 1000));
                // 1 000 Stichproben an Zellmitten des Kerns, gegen Stufe.lesen (eigener Leser, nicht der des Packages)
                java.util.Random rnd = new java.util.Random(1958);
                com.dan.lituya.scene.Stufe sk = com.dan.lituya.scene.Stufe.KERN;
                java.util.List<double[]> kach = new java.util.ArrayList<>(sk.kacheln());
                kach.removeIf(t -> !sk.datei(daten, t[0], t[1]).isFile());
                int nst = 0, falsch = 0, ohne = 0;
                double worst = 0, ms = 0, naht = 0;
                for (int o = 0; o < 10; o++) {
                    double[] t = kach.get(rnd.nextInt(kach.size()));
                    float[] v = com.dan.lituya.scene.Stufe.lesen(sk.datei(daten, t[0], t[1]));
                    int ci = 100 + rnd.nextInt(1800), cj = 100 + rnd.nextInt(1800);
                    double[] cxz = Site.fromUtm(t[0] + (ci + 0.5) * sk.zelle, t[1] + sk.kachel() - (cj + 0.5) * sk.zelle);
                    long tg = System.nanoTime();
                    land.auge(cxz[0], cxz[1]);
                    if (!land.warten(180_000)) { check(false, "Gelände lädt nicht"); break; }
                    ms += (System.nanoTime() - tg) / 1e6;
                    for (int q = 0; q < 100; q++) {
                        int i = ci - 70 + rnd.nextInt(140), j = cj - 70 + rnd.nextInt(140);   // im Fenster der feinsten Stufe (Mittel: ±48 m)
                        float sollH = v[j * com.dan.lituya.scene.Stufe.N + i];
                        if (Float.isNaN(sollH)) { ohne++; continue; }
                        double[] p = Site.fromUtm(t[0] + (i + 0.5) * sk.zelle, t[1] + sk.kachel() - (j + 0.5) * sk.zelle);
                        double d = Math.abs(land.hoehe(p[0], p[1]) - sollH);
                        nst++;
                        worst = Math.max(worst, d);
                        if (d > 1e-3) falsch++;
                    }
                    // Naht: Randscheitel jeder Stufe auf den Kanten der gröberen
                    int hh = c.half(), m = c.grid;
                    for (int L = 0; L + 1 < c.levels(); L++) {
                        int[] fc = c.center(L), gc = c.center(L + 1);
                        for (int e = 0; e < m; e++) {
                            int kk = e, lz = (e & 1) == 0 ? 0 : m - 1;
                            int ii = fc[0] - hh + kk, jj = fc[1] - hh + lz;
                            int gk = Math.floorDiv(ii, 2) - (gc[0] - hh), gl = Math.floorDiv(jj, 2) - (gc[1] - hh);
                            double soll2 = (ii & 1) == 0 ? c.vertexHeight(L + 1, gk, gl) : 0.5 * (c.vertexHeight(L + 1, gk, gl) + c.vertexHeight(L + 1, gk + 1, gl));
                            naht = Math.max(naht, Math.abs(c.vertexHeight(L, kk, lz) - soll2));
                        }
                    }
                }
                check(nst >= 900 && falsch == 0, f("Höhe an %d Stichproben an Zellmitten des Kerns gleich der Kachel (größte Abweichung %.1e m, %d ohne Wert)", nst, worst, ohne));
                check(naht < 1e-2, f("Naht: Randscheitel aller Stufen auf den Kanten der gröberen (größte Abweichung %.1e m)", naht));
                System.out.printf(Locale.GERMAN, "    10 Orte im Kern in %.0f ms je Ort geladen (mit dem Lesen der Kacheln); %s%n", ms / 10, land.stapel().describe());
                c.stop();
            }
        }

        teil("Materialien (Phase 5): surface über daten/texturen");
        {
            java.util.List<com.dan.surface.Material> kat = com.dan.surface.Katalog.fjord();
            check(kat.size() == 14, "Katalog fjord(): 14 Materialien (13 CC0-Sätze von ambientCG, Seetang abgeleitet)");
            File tx = new File(daten, "texturen");
            com.dan.surface.MaterialSet ms = new com.dan.surface.MaterialSet(tx, kat);
            if (ms.layers().isEmpty()) hinweis("keine gebackenen Texturen: python tools/kartenmaterial/kartenmaterial.py laden --gruppe texturen, dann ant aufbereitung \"-Dschritte=texturen,herkunft,inhalt\"");
            else {
                check(ms.missing().isEmpty(), ms.describe());
                check(new File(tx, "LIZENZEN.txt").isFile(), "daten/texturen/LIZENZEN.txt (Paketvertrag 15)");
                long mb = ms.vram(ms.layers().get(0).size(), true) / 1_000_000;
                check(mb <= 600, f("Texturbudget gepackt bei %d²: %d MB ≤ 600 MB (F19)", ms.layers().get(0).size(), mb));
            }
        }

        teil("Wald und Unterwuchs (Phase 6): forest (ForestGpu) und understory");
        {
            com.dan.forest.Species sitka = com.dan.forest.Species.byName("Picea sitchensis"), hem = com.dan.forest.Species.byName("Tsuga heterophylla"),
                    erle = com.dan.forest.Species.byName("Alnus rubra");
            check(sitka != null && hem != null && erle != null, "Arten der Bucht in com.dan.forest: Sitka-Fichte, Westliche Hemlocktanne, Roterle (2.6)");
            if (sitka != null) {
                com.dan.forest.TreeModel lebend = com.dan.forest.TreeGenerator.grow(sitka, 7, 1),
                        weg = com.dan.forest.TreeGenerator.grow(com.dan.forest.Species.damaged(sitka, com.dan.forest.Species.Damage.FELLED), 7, 1);
                check(lebend.height >= 28 && lebend.height <= 50 && weg.leaves.isEmpty() && weg.length > 20,
                        f("Sitka-Fichte %.1f m; umgelegt %.1f m lang, entrindet, ohne Nadeln (FRITZ01: gebrochen, entrindet, geschleudert)", lebend.height, weg.length));
            }
            com.dan.forest.ForestCulture cu = new com.dan.forest.ForestCulture(3, 3, 6, 1958);
            cu.density = com.dan.forest.ForestCulture.Grid.density((px, pz, s) -> s == 0 ? 0.5 : s == 1 ? 0.3 : 0.05, 3, -3000, -3000, 6000, 64);
            com.dan.forest.TreeStates st = com.dan.forest.TreeStates.covering(-500, -500, 0, 0, 6);
            for (int j = st.j0; j < st.j0 + st.h; j++) for (int i = st.i0; i < st.i0 + st.w; i++) st.set(i, j, (i + j) % 5 == 0 ? com.dan.forest.TreeStates.GONE : com.dan.forest.TreeStates.FELLED, 180);
            cu.states = st;
            long[] n = cu.count(-1000, -1000, 1000, 1000);
            long alle = 0, stehend = 0;
            for (int t = 0; t < n.length; t++) { alle += n[t]; if (t < 9) stehend += n[t]; }
            double erwartet = 2000.0 * 2000 / 36 * 0.85;
            check(Math.abs(alle - erwartet * (1 - 0.0625 * 0.2)) < erwartet * 0.02,
                    f("Kultur 2 × 2 km, Dichte 0,85, Abstand 6 m: %,d Bäume (%,d stehend, %,d im Band umgelegt); hochgerechnet auf den Kern (24 × 18 km) %,d", alle, stehend, alle - stehend,
                            Math.round(alle / 4.0 * 432)));
            java.util.List<com.dan.understory.Kind> ks = java.util.List.of(com.dan.understory.Kind.values());
            double hell = com.dan.understory.Kind.light(0.8, com.dan.understory.Kind.K_CONIFER), dunkel = com.dan.understory.Kind.light(7.5, com.dan.understory.Kind.K_CONIFER);
            check(ks.size() == 7 && com.dan.understory.Kind.BERRY.suitability(hell, 0.6) > com.dan.understory.Kind.BERRY.suitability(dunkel, 0.6)
                            && com.dan.understory.Kind.MOSS.suitability(dunkel, 0.85) > com.dan.understory.Kind.MOSS.suitability(dunkel, 0.3),
                    f("Unterwuchs: 7 Arten (5.7); Licht am Boden unter Blattflächenindex 7,5: %.1f %%, in einer Lichtung (0,8): %.0f %%; Beeren in der Lichtung, Moos am Bach", dunkel * 100, hell * 100));
        }

        teil("Laufzeit und Netzblick (Phase 7): route (TravelGrid, Measure) und netview");
        {
            com.dan.route.TravelGrid.Stencil s16 = com.dan.route.TravelGrid.Stencil.N16, s32 = com.dan.route.TravelGrid.Stencil.N32;
            check(s16.size() == 16 && Math.abs(s16.error() - 0.0275) < 0.0005 && s32.size() == 32 && Math.abs(s32.error() - 0.0076) < 0.001,
                    f("Nachbarsterne (9.4): 16 mit Lücke %.1f° und Fehler %.2f %%, 32 mit %.1f° und %.2f %%", s16.maxGapDeg(), s16.error() * 100, s32.maxGapDeg(), s32.error() * 100));
            int n0 = 201;
            com.dan.route.TravelGrid g = new com.dan.route.TravelGrid(n0, n0, 0, 0, 20);
            java.util.Arrays.fill(g.speed, (float) com.dan.route.TravelGrid.waveSpeed(130, 0));
            g.solve(List.of(new com.dan.route.TravelGrid.Source(100, 100, 0)));
            double c = Math.sqrt(9.81 * 130), max = 0;
            for (int j = 0; j < n0; j++)
                for (int i = 0; i < n0; i++) {
                    double L = Math.hypot(i - 100, j - 100) * 20;
                    if (L > 200) max = Math.max(max, g.arrival()[j * n0 + i] / (L / c) - 1);
                }
            check(max <= s16.error() + 1e-4 && max > 0.02, f("konstante Tiefe 130 m (c = %.1f m/s): größter Fehler %.2f %% ≤ %.2f %%, 2 000 m in %.0f s", c, max * 100, s16.error() * 100,
                    g.arrival()[100 * n0 + 200]));
            com.dan.route.Measure ms = new com.dan.route.Measure(new float[]{0, 0, 300, 0, 300, 400});
            ms.segments.add(new com.dan.route.Measure.Segment("Spurlinie", 0, 700, new double[]{0, 700}, new double[]{524, 30}));
            double[] mitte = ms.locate(500);
            check(ms.length() == 700 && Math.abs(mitte[0] - 300) < 1e-9 && Math.abs(mitte[1] - 200) < 1e-9 && Math.abs(ms.project(310, 200).m() - 500) < 1e-9
                    && Math.abs(ms.valueAt(350) - 277) < 1e-9, "Messstrecke: 700 m, Maß 500 bei (300, 200), Projektion, Wert in der Mitte 277 m");
            com.dan.netview.NetLayer nl = new com.dan.netview.NetLayer("Isochronen");
            float[] kreis = new float[101 * 101];
            for (int j = 0; j <= 100; j++) for (int i = 0; i <= 100; i++) kreis[j * 101 + i] = (float) Math.hypot(i - 50, j - 50) * 10;
            int stuecke = nl.isolines(kreis, 101, 101, -500, -500, 10, 0, new double[]{100, 200, 300, 400}, 0xFFFFFF, 2);
            check(stuecke == 4, f("netview: vier Isolinien eines Kreisfelds, %d Strecken", nl.segments()));
            File gr = new File(daten, "grund/heute_4m.r32");
            if (!gr.isFile() || !new File(daten, "grund/1940_20m.r32").isFile()) hinweis("daten/grund fehlt: Laufzeit über dem Grund übersprungen");
            else {
                com.dan.lituya.tools.Isochronenbild.Lauf l = com.dan.lituya.tools.Isochronenbild.rechnen("heute", com.dan.lituya.tools.Isochronenbild.heute20(daten), false);
                double ein = com.dan.lituya.tools.Isochronenbild.ankunft(l.gitter(), Site.CHAUSSEE.e(), Site.CHAUSSEE.n(), 400),
                        ank = com.dan.lituya.tools.Isochronenbild.ankunft(l.gitter(), Site.ANCHORAGE.e(), Site.ANCHORAGE.n(), 400);
                check(ein > 280 && ein < 420 && ank < ein, f("Front über dem Grund heute (20 m, 16 Nachbarn, linear): Anchorage Cove %.0f s, Eingang %.0f s; Handwert 350 s (9.5)", ank, ein));
            }
        }

        teil("Beben (Phase 8): quake und daten/ereignis");
        {
            double p10 = com.dan.quake.Bssa14.median(com.dan.quake.Bssa14.Imt.PGA, 7.8, 10, 760, com.dan.quake.Bssa14.Mechanism.STRIKE_SLIP);
            check(Math.abs(p10 - 0.3077) < 0.001, f("BSSA14 aus Renderer.jar: M 7,8 Blattverschiebung, Rjb 10 km, Fels: %.3f g", p10));
            File ev = new File(daten, "ereignis/stoerung.tsv");
            if (!ev.isFile()) hinweis("daten/ereignis fehlt: ant aufbereitung -Dschritte=stoerung");
            else {
                com.dan.quake.Quake q = com.dan.lituya.tools.Bebenbild.beben(daten);
                double gx = Site.GILBERT.x(), gz = Site.GILBERT.z(), rjb = q.rjbKm(gx, gz), d = q.tS(gx, gz) - q.tP(gx, gz);
                check(q.fault.length() > 250_000 && rjb < 3 && Math.abs(d - 8.1) < 0.5 && q.magnitude == 7.8,
                        f("Fairweather-Störung %.0f km (Qfaults), am Gilbert Inlet Rjb %.1f km, tS − tP %.1f s, PGA %.2f g (Herd ComCat, M %.1f)", q.fault.length() / 1000, rjb, d, q.pga(gx, gz), q.magnitude));
            }
        }

        teil("Sturz (Phase 9): landslide, Übergabe aus fracture, Entfestigung in quake");
        {
            com.dan.landslide.Landslide g = com.dan.landslide.Presets.gilbertInlet1958(com.dan.landslide.Landslide.Quality.LOW);
            check(com.dan.landslide.Landslide.STAGE == 9 && Math.abs(g.volume() - 30.6e6) < 1,
                    f("landslide aus Renderer.jar: Preset gilbertInlet1958 mit %.3e m³ auf %d × %d Zellen (8 m)", g.volume(), g.nx, g.nz));
            com.dan.fracture.Fracture halt = com.dan.lituya.tools.Sturz.platte(1), los = com.dan.lituya.tools.Sturz.platte(com.dan.lituya.tools.Sturz.REST);
            for (int i = 0; i < 180; i++) { halt.step(1.0 / 120); los.step(1.0 / 120); }
            double vol = 0;
            int frei = los.pieces().size();
            com.dan.landslide.Landslide tl = com.dan.landslide.Presets.gilbertInlet1958Terrain(com.dan.landslide.Landslide.Quality.LOW);
            for (com.dan.fracture.Piece p : los.pieces()) {
                double[] c = los.handOver(p);
                for (int i = 0; i < c.length; i += 8) { tl.inject(c[i], c[i + 2], c[i + 3], c[i + 5], c[i + 7], 0.62 * Math.cbrt(c[i + 3])); vol += c[i + 3]; }
            }
            check(halt.pieces().isEmpty() && frei > 10 && Math.abs(tl.volume() / los.handedOverVolume() - 1) < 1e-9 && Math.abs(vol / los.handedOverVolume() - 1) < 1e-9 && vol > 25e6,
                    f("Platte mit voller Kohäsion der Klüfte hält; mit dem Rest %.0f %% bricht sie in %d Stücke, %.3e m³ kommen im Strom an", com.dan.lituya.tools.Sturz.REST * 100, frei, tl.volume()));
            com.dan.quake.Slope wh = com.dan.lituya.tools.Sturz.wand(com.dan.lituya.tools.Sturz.FS, 1, 0.3), wr = com.dan.lituya.tools.Sturz.wand(com.dan.lituya.tools.Sturz.FS, com.dan.lituya.tools.Sturz.REST, 0.3);
            check(wh.acAt(5) > 0 && wr.acAt(5) < 0, f("Entfestigung in quake.Slope: a_c nach 5 m ohne Rest %.3f g, mit Rest 20 %% %.3f g", wh.acAt(5) / 9.81, wr.acAt(5) / 9.81));
            if (new File(daten, "ereignis/stoerung.tsv").isFile()) {
                com.dan.quake.Quake q = com.dan.lituya.tools.Bebenbild.beben(daten);
                com.dan.quake.Slope.Result n = wr.newmark(q.record(Site.GILBERT.x(), Site.GILBERT.z()));
                check(n.runaway() && n.failTime() > 15 && n.failTime() < 60,
                        f("NE-Wand im Beben aus daten/ereignis: geht nach %.0f s durch – vor den 60–150 s der Zeugen; die Szene löst von Hand aus (Werkbuch 9.2)", n.failTime()));
            }
        }

        teil("Nahfeld (Phase 10): impact, Zufluss aus landslide");
        {
            com.dan.impact.Inflow lab = com.dan.impact.Inflow.fritz01();
            check(com.dan.impact.Impact.STAGE == 10 && Math.abs(lab.volume() / (98.5e6 / 1610) - 1) < 0.005 && Math.abs(lab.peak() - 134) < 1e-9,
                    f("impact aus Renderer.jar: Zufluss FRITZ01 %.0f m² je Meter, bis %.0f m, %.1f s", lab.volume(), lab.peak(), lab.end()));
            com.dan.impact.Impact s = com.dan.impact.Presets.gilbertInlet1958(com.dan.impact.Impact.Quality.LOW);
            s.run(3);
            double m = s.waterMass() + s.poreMass() + s.lostVolume() * 1000;
            check(Math.abs(m / s.initialWaterMass() - 1) < 1e-12 && s.releasedMass() > 0,
                    f("Laborfall im Schnitt (8 m) nach 3 s: Masse des Wassers samt Poren genau, %.2e kg/m durch die Uferlinie", s.releasedMass()));
            com.dan.landslide.Landslide st = com.dan.landslide.Presets.gilbertInlet1958(com.dan.landslide.Landslide.Quality.LOW);
            st.runToRest(120);
            com.dan.impact.Inflow in = com.dan.lituya.tools.Eintauchen.zufluss(st.entries(), "Strom");
            check(in.volume() > 5000 && in.meanSpeed() > 80 && in.meanSpeed() < 120,
                    f("Strom aus landslide je Meter Breite: %.0f m² in %.1f s, %.0f m/s, bis %.0f m (Laborfall %.0f m², %.1f s)", in.volume(), in.duration90(), in.meanSpeed(), in.peak(), lab.volume(), lab.duration90()));
        }

        teil("Welle (Phase 11): wave.Bay, Einlass und Auflauf im Schnitt");
        {
            com.dan.wave.Bay wb = new com.dan.wave.Bay(0, 0, 60, 50, 8);
            wb.nest(15, 12, 30, 25, 4);
            wb.build((wx, wz) -> -30 + 0.05 * wx, 0, new com.dan.wave.Bay.Init() { public double eta(double wx, double wz) { return 4 * Math.exp(-((wx - 240) * (wx - 240) + (wz - 200) * (wz - 200)) / 2000.0); } });
            wb.run(10);
            check(Math.abs(wb.volume() / wb.initialVolume() - 1) < 1e-12,
                    f("wave.Bay aus Renderer.jar: zwei Gitter (8 und 2 m), Volumen nach 10 s %.12f", wb.volume() / wb.initialVolume()));
            double[] r = com.dan.lituya.tools.Auflauf.lauf(com.dan.lituya.tools.Auflauf.Messwelle.INSTANCE);
            check(Math.abs(r[0] - 526) <= 50, f("Auflauf der Messwelle FRITZ01 am 45°-Gegenhang: %.0f m (526 ± 50 m), Pegel 1 100 m %.0f m nach %.1f s", r[0], r[1], r[2]));
        }

        teil("Ereignis (Phase 13): Grund 1958, Linie aus dem Nahfeld, Daten der Vorrechnung");
        {
            // Bilder schreiben und lesen (Stufen zu 0,05 m, trocken bleibt trocken)
            File et = java.nio.file.Files.createTempDirectory("ereignis").toFile();
            com.dan.lituya.scene.EreignisDaten.Gitter eg = new com.dan.lituya.scene.EreignisDaten.Gitter(350_000, 6_500_000, 10, 30, 20);
            float[] ev = new float[600];
            for (int c = 0; c < ev.length; c++) ev[c] = c % 7 == 0 ? Float.NaN : (float) (Math.sin(c * 0.1) * 300);
            try (com.dan.lituya.scene.EreignisDaten.Schreiber ew = new com.dan.lituya.scene.EreignisDaten.Schreiber(et, java.util.List.of(eg))) {
                ew.bild(0, 105, ev);
                ew.bild(0, 106, new float[600]);
                ew.raster("max", 0, ev);
                ew.beschreibung(new java.util.Properties());
            }
            double ef = 0;
            int eb;
            try (com.dan.lituya.scene.EreignisDaten.Leser er = new com.dan.lituya.scene.EreignisDaten.Leser(et)) {
                float[] back = er.bild(0, 0);
                eb = er.zeiten(0).length;
                for (int c = 0; c < ev.length; c++) ef = Math.max(ef, Float.isNaN(ev[c]) != Float.isNaN(back[c]) ? 1e9 : Float.isNaN(ev[c]) ? 0 : Math.abs(ev[c] - back[c]));
            }
            check(eb == 2 && ef <= 0.025 + 1e-6, f("EreignisDaten: 2 Bilder geschrieben und gelesen, größte Abweichung %.3f m (Stufe 0,05 m), trocken bleibt trocken", ef));
            // Lage der Linie: 885 m vor der Wand, Wand 1 640 m vom Sporn
            double[] lwd = com.dan.lituya.tools.Ereignis.wand(), lnf = com.dan.lituya.tools.Ereignis.linie();
            check(Math.abs(Math.hypot(lnf[0] - lwd[0], lnf[1] - lwd[1]) - 885) < 1e-6 && Math.abs(Math.hypot(lwd[0] - Site.SPORN.e(), lwd[1] - Site.SPORN.n()) - 1640) < 1e-6
                            && com.dan.lituya.tools.Ereignis.nahfeld(lwd[0] + 0.5 * 885 * com.dan.lituya.tools.Ereignis.RICHTUNG[0], lwd[1] + 0.5 * 885 * com.dan.lituya.tools.Ereignis.RICHTUNG[1]),
                    f("Linie aus dem Nahfeld bei E %.0f N %.0f, Wand bei E %.0f N %.0f [G]; dazwischen Fels", lnf[0], lnf[1], lwd[0], lwd[1]));
            if (!new File(daten, "grund/heute_4m.r32").isFile()) hinweis("daten/grund fehlt: ohne Prüfung des Grundes 1958");
            else {
                com.dan.lituya.scene.Boden1958 bo = new com.dan.lituya.scene.Boden1958(daten);
                double inBucht = bo.hoehe(350_641.9, 6_502_491.1), insel = bo.hoehe(Site.CENOTAPH.e(), Site.CENOTAPH.n()), inlet = bo.hoehe(lnf[0], lnf[1]), sporn = bo.hoehe(Site.SPORN.e(), Site.SPORN.n());
                check(inBucht < -20 && insel > 0 && inlet <= -100 && sporn > 100,
                        f("Grund 1958: Bucht %.0f m, Cenotaph Island %.0f m, an der Linie im Gilbert Inlet %.0f m (Rückbau [G]), Sporn %.0f m", inBucht, insel, inlet, sporn));
            }
            File em = new File(daten, "ereignis/messwelle/EREIGNIS.txt");
            if (!em.isFile()) hinweis("daten/ereignis/messwelle fehlt: ant ereignis");
            else try (com.dan.lituya.scene.EreignisDaten.Leser er = new com.dan.lituya.scene.EreignisDaten.Leser(em.getParentFile())) {
                int n = er.gitter.size(), bilder = er.zeiten(n - 1).length;
                check(n == 3 && bilder > 100, f("daten/ereignis/messwelle: %d Gitter, %d Bilder im feinen, %s", n, bilder, er.p.getProperty("bay")));
            }
        }

        teil("Vergleich (Phase 13a): Spurlinie gerechnet gegen belegt");
        {
            File sk = new File(daten, "netz/ufer/spurlinie_kette.tsv");
            if (!sk.isFile()) hinweis("daten/netz/ufer/spurlinie_kette.tsv fehlt: ant vergleich");
            else {
                List<String[]> sz = com.dan.lituya.scene.WellenNetz.tsv(sk);
                double sporn = 0, sum = 0;
                int mit = 0;
                for (String[] sv : sz) {
                    if (sv[4].isEmpty()) continue;
                    double sh = Double.parseDouble(sv[4]);
                    mit++;
                    sum += sh;
                    if (Math.hypot(Double.parseDouble(sv[2]) - Site.SPORN.e(), Double.parseDouble(sv[3]) - Site.SPORN.n()) < 1000) sporn = Math.max(sporn, sh);
                }
                check(sz.size() > 700 && mit > 0.5 * sz.size() && sporn > 100,
                        f("Spurlinie der Kette: %d Uferpunkte, an %d Auflauf, im Mittel %.0f m, am Sporn (1 km) %.0f m (MILLER60 524 m)", sz.size(), mit, sum / Math.max(1, mit), sporn));
            }
        }

        teil("Abspielen (Phase 13b): Wiedergabe mit Vorrat, Kacheln, Schaustücke");
        {
            // ein kleiner Lauf „kette“ mit zwei Gittern und vier Bildern
            File tmp = Files.createTempDirectory("lb13b").toFile();
            File dk = com.dan.lituya.scene.EreignisDaten.ordner(tmp, "kette");
            com.dan.lituya.scene.EreignisDaten.Gitter g0 = new com.dan.lituya.scene.EreignisDaten.Gitter(352_000, 6_503_000, 16, 200, 140),
                    g1 = new com.dan.lituya.scene.EreignisDaten.Gitter(353_000, 6_504_000, 4, 130, 100);
            try (com.dan.lituya.scene.EreignisDaten.Schreiber sw = new com.dan.lituya.scene.EreignisDaten.Schreiber(dk, List.of(g0, g1))) {
                for (int bi = 0; bi < 4; bi++)
                    for (int kk = 0; kk < 2; kk++) {
                        com.dan.lituya.scene.EreignisDaten.Gitter g = kk == 0 ? g0 : g1;
                        float[] eta = new float[g.nx() * g.nz()];
                        for (int c = 0; c < eta.length; c++) eta[c] = (c % g.nx()) >= g.nx() - 70 ? Float.NaN : bi * 2f;   // Osten trocken
                        sw.bild(kk, 105 + bi * 10, eta);
                    }
                for (int kk = 0; kk < 2; kk++) {
                    com.dan.lituya.scene.EreignisDaten.Gitter g = kk == 0 ? g0 : g1;
                    float[] mx = new float[g.nx() * g.nz()];
                    for (int c = 0; c < mx.length; c++) mx[c] = (c % g.nx()) >= g.nx() - 70 ? Float.NaN : 6f;
                    sw.raster("max", kk, mx);
                }
                java.util.Properties pp = new java.util.Properties();
                pp.setProperty("start_s", "105");
                sw.beschreibung(pp);
            }
            com.dan.lituya.scene.Wiedergabe wg = new com.dan.lituya.scene.Wiedergabe(tmp);
            check(wg.wellen().equals(List.of("kette")), "Läufe der Welle in daten/ereignis: " + wg.wellen());
            wg.start();
            boolean geladen = wg.warten(112, 20_000);
            List<com.dan.lituya.scene.Wiedergabe.Spur> ws = wg.spuren(com.dan.lituya.scene.Wiedergabe.Art.WELLE);
            check(geladen && ws.size() == 2, "Paar um t = 112 s geladen: " + wg.zeile());
            com.dan.lituya.scene.Wiedergabe.Spur s0 = ws.get(0);
            double[] kl = s0.klammer(112);
            check(s0.klammer(104) == null && kl[0] == 0 && kl[1] == 1 && Math.abs(kl[2] - 0.7) < 1e-9 && s0.klammer(200)[0] == 3
                    && s0.klammer(135 + com.dan.lituya.scene.Wiedergabe.NACHLAUF + 1) == null, "Klammer: vor dem Start keine, 112 s zwischen Bild 0 und 1 (0,7), danach das letzte, nach dem Nachlauf keine");
            com.dan.lituya.scene.Wiedergabe.Paar pa = wg.paar(s0, 112);
            check(pa != null && Math.abs(pa.wert(10, 10) - 1.4) < 1e-6 && Double.isNaN(pa.wert(g0.nx() - 1, 10)), f("gemischt 0 m und 2 m zu %.2f m, Osten trocken", pa == null ? Double.NaN : pa.wert(10, 10)));
            double[] xz = Site.fromUtm(353_100, 6_504_100);
            wg.warten(125, 20_000);
            check(Math.abs(wg.eta(xz[0], xz[1], 125) - 4) < 1e-6, f("Wasserstand am Ort aus dem feinsten Gitter: %.2f m", wg.eta(xz[0], xz[1], 125)));
            int nie = 0;
            for (float m : s0.kachelMax) if (Float.isNaN(m)) nie++;
            check(s0.ntx == 4 && s0.ntz == 3 && nie == 3 && s0.feiner != null, f("Kacheln zu %d Zellen: %d × %d, %d nie nass (Osten), Rechteck des feineren Gitters", com.dan.lituya.scene.Wiedergabe.KACHEL, s0.ntx, s0.ntz, nie));
            // Stufen der Feinheit: Zweierpotenzen, nah fein, weit grob; hinter dem Auge weg
            Camera kc = new Camera();
            double[] mid = Site.fromUtm(352_500, 6_503_500);
            kc.place(mid[0], 30, mid[1], 90, 5);
            float[] vpk = Mat4.mul(Mat4.perspective(Camera.FOVY, 16 / 9.0, 0.3, 400_000, true), kc.view());
            int[] st = com.dan.lituya.gl.EreignisGl.stufen(s0, vpk, kc.eye, 2 * Math.tan(Math.toRadians(Camera.FOVY) / 2) / 1080, 0);
            boolean zwei = true;
            int an = 0;
            for (int v : st) { if (v != 0) an++; zwei &= v == 0 || (Integer.bitCount(v) == 1 && v <= com.dan.lituya.gl.EreignisGl.MAX_SCHRITT); }
            check(zwei && an > 0 && an < s0.ntx * s0.ntz - nie, f("Stufen je Kachel Zweierpotenzen bis %d, %d von %d Kacheln im Blick", com.dan.lituya.gl.EreignisGl.MAX_SCHRITT, an, s0.ntx * s0.ntz));
            check(!com.dan.lituya.gl.EreignisGl.sichtbar(vpk, mid[0] - 2000, mid[0] - 1000, -10, 10, mid[1] - 100, mid[1] + 100, kc.eye)
                    && com.dan.lituya.gl.EreignisGl.sichtbar(vpk, mid[0] + 1000, mid[0] + 2000, -10, 10, mid[1] - 100, mid[1] + 100, kc.eye), "Kachel hinter dem Auge fällt weg, vor dem Auge bleibt sie");
            wg.close();
            SceneClock rk = new SceneClock();
            rk.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + 120_000);
            rk.lapse = -0.05;
            rk.step(2);
            check(Math.abs(rk.simSeconds() - 119.9) < 1e-6, f("rückwärts in Zeitlupe 1:20: 2 s später t = %.2f s", rk.simSeconds()));
            double vorher = -1e9;
            boolean folge = true, imKern = true;
            for (com.dan.lituya.camera.Schaustuecke.Stueck sk : com.dan.lituya.camera.Schaustuecke.ALLE) {
                // W5: „524 m“ (Nr. 5) zeigt den Auflauf, den die Vorrechnung erst bei 125–140 s am Sporn hat, also nach
                // „Die Front“ (112 s) und „Unter Wasser“ (115 s); die Nummer bleibt, die Folge gilt ohne ihn
                if (sk.nr() == 5) { imKern &= Site.KERN.contains(sk.x(), sk.z()); continue; }
                folge &= sk.t() >= vorher;
                vorher = sk.t();
                imKern &= Site.KERN.contains(sk.x(), sk.z());
            }
            check(com.dan.lituya.camera.Schaustuecke.ALLE.size() == 11 && folge && imKern, "Schaustücke 1 bis 11 im Kern, in der Folge der Zeitleiste (ohne Nr. 5, seit W5 beim Auflauf 126 s)");
            com.dan.lituya.camera.Schaustuecke.Stueck s7 = com.dan.lituya.camera.Schaustuecke.byNr(7), s4 = com.dan.lituya.camera.Schaustuecke.byName("Die Wand aus Wasser");
            Camera c7 = new Camera();
            c7.waterLevel = 0;
            SceneClock k7 = new SceneClock();
            com.dan.lituya.camera.Schaustuecke.apply(s7, c7, k7, (x, zz) -> -60);
            c7.snap();
            check(c7.diving && c7.eye[1] < 0 && Math.abs(k7.simSeconds() - s7.t()) < 1e-6 && k7.lapse == s7.lapse(), f("„Unter Wasser“: Auge %.1f m, t = %.0f s, %s", c7.eye[1], k7.simSeconds(), s7.tempo()));
            check(s4 != null && s4.lapse() == 0.05 && s4.tempo().equals("Zeitlupe 1:20"), "„Die Wand aus Wasser“ in Zeitlupe 1:20");
            check(Renderer.PASSES.length == 9 && List.of(Renderer.PASSES).contains("Boote") && List.of(Renderer.PASSES).contains("Wald") && List.of(Renderer.PASSES).contains("Welle") && List.of(Renderer.PASSES).contains("Staub"), "Durchgänge: " + String.join(", ", Renderer.PASSES));
            File ec = new File("daten/ereignis");
            if (ec.isDirectory()) {
                com.dan.lituya.scene.Wiedergabe echt = new com.dan.lituya.scene.Wiedergabe(new File("daten"));
                List<String> l = echt.wellen();
                check(l.contains("kette") || l.contains("messwelle"), "Vorrechnung in daten/ereignis: " + l);
                boolean bs = new File(ec, "strom/boden_g0.r32").isFile(), bd = new File(ec, "staub/boden_g0.r32").isFile();
                if (!bs || !bd) hinweis("Grund unter Strom und Staub fehlt noch (rechnet die App beim ersten Abspielen aus Boden1958, oder ant ereignis -Dlauf=staub)");
            } else hinweis("daten/ereignis fehlt (ant ereignis)");
        }

        teil("Was wäre wenn (Phase 13c): Echtzeit, Quellen, Rückspulpuffer");
        {
            // ein gerader Fjord: Wasser 150 m tief nördlich von N 6 504 000, Hang nach Süden mit 0,9
            java.util.function.DoubleBinaryOperator fj = (e, n) -> n > 6_504_000 ? -150 : (6_504_000 - n) * 0.9 - 20;
            com.dan.lituya.gl.Echtzeit.Ort o = new com.dan.lituya.gl.Echtzeit.Ort("Probe", 352_000, 6_503_000, Double.NaN, false);
            double ri = com.dan.lituya.gl.Echtzeit.richtung(o.e(), o.n(), fj, 0);
            com.dan.lituya.tools.Ereignis.Quelle qq = com.dan.lituya.gl.Echtzeit.linie(o, fj, 0, 30.6e6);
            check(Math.abs(ri) < 1e-9 && Math.abs(qq.rn() - 1) < 1e-9 && fj.applyAsDouble(qq.e(), qq.n()) <= -20 && qq.n() < 6_504_100 && Math.abs(qq.halb() - 2 * Math.cbrt(30.6e6)) < 1,
                    f("Ort am Hang: Richtung %.0f° (zum Wasser), Linie bei N %.0f in %.0f m Tiefe, halbe Länge %.0f m, Nahfeld %.0f m bis ans Ufer", ri, qq.n(), -fj.applyAsDouble(qq.e(), qq.n()), qq.halb(), qq.hinten()));
            com.dan.lituya.gl.Echtzeit.Ort ow = new com.dan.lituya.gl.Echtzeit.Ort("Probe im Wasser", 352_000, 6_505_000, Double.NaN, false);
            com.dan.lituya.tools.Ereignis.Quelle qw = com.dan.lituya.gl.Echtzeit.linie(ow, fj, 0, 30.6e6);
            check(Math.abs(qw.n() - qq.n()) < 30, f("im Wasser gesetzt: zurück ans Ufer, Linie wieder bei N %.0f", qw.n()));
            double[] hh = com.dan.lituya.gl.Echtzeit.heller(o, qq, fj, 0, 30.6e6), h2 = com.dan.lituya.gl.Echtzeit.heller(o, qq, fj, 0, 2 * 30.6e6);
            check(hh[2] == 150 && hh[0] > 60 && hh[0] < 225 && hh[1] > 20 && hh[1] < 150 && h2[0] > hh[0],
                    f("Heller/Hager bei 150 m Tiefe: P = %.2f, Wellenberg %.0f m über %.0f s; doppeltes Volumen %.0f m", hh[3], hh[0], hh[1], h2[0]));
            com.dan.lituya.tools.Ereignis.Quelle g58 = com.dan.lituya.tools.Ereignis.Quelle.gilbert1958();
            double[] l58 = com.dan.lituya.tools.Ereignis.linie();
            check(g58.e() == l58[0] && g58.n() == l58[1] && com.dan.lituya.tools.Ereignis.nahfeld(l58[0] - 500 * g58.re(), l58[1] - 500 * g58.rn())
                    && !com.dan.lituya.tools.Ereignis.nahfeld(l58[0] + 100 * g58.re(), l58[1] + 100 * g58.rn()), "Linie von 1958 als Quelle, Nahfeld dahinter wie bisher");
            Renderer rt = new Renderer(new SceneClock());
            rt.camera.place(1000, 100, -2000, 90, 45);
            rt.camera.snap();
            double[] tr = rt.treffer(400, 300, 800, 600);
            check(tr != null && Math.abs(tr[0] - 1100) < 2 && Math.abs(tr[1] + 2000) < 2, f("Strg-Klick: Strahl aus 100 m Höhe unter 45° trifft das Wasser bei x = %.1f, z = %.1f", tr == null ? Double.NaN : tr[0], tr == null ? Double.NaN : tr[1]));
            check(com.dan.lituya.gl.Echtzeit.ORTE.size() == 5 && com.dan.lituya.gl.Echtzeit.ORTE.get(0).wand1958(), "fünf feste Orte, zuerst die Wand von 1958");
            check(com.dan.lituya.ui.ControlPanel.volWert(com.dan.lituya.ui.ControlPanel.volumen(60)) == 60 && Math.abs(com.dan.lituya.ui.ControlPanel.volumen(60) / 1e6 - 30.6) < 0.5,
                    f("Regler Volumen 1 bis 300 Mio. m³, Stellung 60 = %.1f Mio. m³", com.dan.lituya.ui.ControlPanel.volumen(60) / 1e6));
            // Rückspulpuffer: drei Bilder, gelesen wie eine Vorrechnung
            com.dan.lituya.scene.EreignisDaten.Gitter gl = new com.dan.lituya.scene.EreignisDaten.Gitter(352_000, 6_503_000, 32, 100, 80);
            com.dan.lituya.scene.LiveLauf lauf13c = new com.dan.lituya.scene.LiveLauf("Probe", List.of(gl));
            File leer = Files.createTempDirectory("lb13c").toFile();
            com.dan.lituya.scene.Wiedergabe wl = new com.dan.lituya.scene.Wiedergabe(leer);
            lauf13c.wiedergabe(wl);
            wl.live(lauf13c, false);
            wl.start();
            for (int bi = 0; bi < 3; bi++) {
                float[] eta = new float[100 * 80];
                for (int c = 0; c < eta.length; c++) eta[c] = c % 100 < 30 ? Float.NaN : bi * 3f;
                lauf13c.bild(0, 105 + bi, eta);
            }
            short[] rr = lauf13c.roh(0, 2);
            boolean geladen = wl.warten(106.5, 20_000);
            double[] mx = Site.fromUtm(352_000 + 50 * 32, 6_503_000 + 40 * 32);
            check(geladen && lauf13c.spuren.get(0).t.length == 3 && rr[50] == 120 && rr[0] == com.dan.lituya.scene.EreignisDaten.TROCKEN && Math.abs(wl.eta(mx[0], mx[1], 106.5) - 4.5) < 1e-6
                    && Math.abs(lauf13c.spuren.get(0).kachelMax[1] - 6) < 1e-6 && Float.isNaN(lauf13c.spuren.get(0).kachelMax[0]) == false,
                    f("Rückspulpuffer: 3 Bilder, %d Bytes gepackt, gemischt bei 106,5 s %.2f m, höchste Stände je Kachel wachsen mit", lauf13c.bytes(), wl.eta(mx[0], mx[1], 106.5)));
            check(wl.welle().equals("Probe") && wl.spuren(com.dan.lituya.scene.Wiedergabe.Art.STROM).isEmpty(), "Wiedergabe spielt die Echtzeit, ohne Strom und Staub");
            wl.close();
            File dd = Files.createTempDirectory("lb13c-rand").toFile();
            com.dan.lituya.tools.Ereignis.schreibeRand(dd, List.of(new double[]{0, 1, 2}, new double[]{1, 3, 4}));
            List<double[]> rd2 = com.dan.lituya.tools.Ereignis.leseRand(dd);
            check(rd2 != null && rd2.size() == 2 && rd2.get(1)[2] == 4, "Ganglinie der Kette als linie_kette.tsv geschrieben und gelesen");
            File grund = new File("daten/grund/heute_4m.r32");
            if (grund.isFile()) {
                com.dan.lituya.scene.Boden1958 bd = new com.dan.lituya.scene.Boden1958(new File("daten"));
                double a = bd.hoehe(l58[0], l58[1]), hz = bd.hoeheHeute(l58[0], l58[1]);
                check(a < -50 && !(hz < a + 1), f("an der Linie von 1958: Grund 1940 %.0f m, heute %.0f m (Rückbau nur 1940)", a, hz));
                if (new File("daten/ereignis/linie_kette.tsv").isFile()) check(com.dan.lituya.tools.Ereignis.leseRand(new File("daten")) != null, "daten/ereignis/linie_kette.tsv lesbar");
                else hinweis("daten/ereignis/linie_kette.tsv fehlt (ant ereignis -Dlauf=linie, rund 1 min; sonst rechnet die App sie beim ersten Mal)");
            } else hinweis("daten/grund fehlt: Grund 1940 gegen heute nicht geprüft");
        }

        teil("Wiederbewaldung (Phase 14): succession, Nachwuchs in forest");
        {
            // ein kleiner Hang: Wasser für z > 0, Land mit 25°, Spurlinie 120 m
            com.dan.succession.Succession.Gelaende hg = (px, pz) -> pz > 0 ? -40 : -pz * Math.tan(Math.toRadians(25));
            com.dan.succession.Succession su = com.dan.succession.Succession.covering(0, -600, 600, 0, 6,
                    List.of(com.dan.succession.Art.sitkaFichte(), com.dan.succession.Art.hemlock(), com.dan.succession.Art.roterle()), 1958);
            su.stoeren(hg, (px, pz) -> { double g = hg.height(px, pz); return g < 120 ? g + 0.35 * (120 - g) + 0.5 : Double.NaN; }, (px, pz, sp) -> sp == 0 ? 0.55 : sp == 1 ? 0.37 : 0.08);
            su.rechnen(54);
            com.dan.succession.Succession.Statistik s0 = su.statistik(0), s54 = su.statistik(54);
            check(su.band() > 1000 && s0.deckung() < 0.15 && s54.deckung() > 0.5 && s54.verhaeltnis() > 0.2 && s54.verhaeltnis() < 0.9,
                    f("succession aus Renderer.jar: Band %,d Plätze, Deckung nach der Welle %.0f %%, nach 54 Jahren %.0f %%, Kronenhöhe im Band %.2f von außerhalb",
                            su.band(), 100 * s0.deckung(), 100 * s54.deckung(), s54.verhaeltnis()));
            com.dan.forest.TreeStates ts = com.dan.forest.TreeStates.covering(0, -600, 600, 0, 6);
            double[] ref = {33, 28.7, 15.2};
            int[] n = new int[2];
            su.zustand(54, (i, j, st, fall, art, hh) -> { ts.set(i, j, st, fall); if (art >= 0) { ts.setGrowth(i, j, art, Math.min(1, hh / ref[art])); n[0]++; } });
            com.dan.forest.ForestCulture cu = new com.dan.forest.ForestCulture(3, 3, 6, 1958);
            cu.states = ts;
            com.dan.forest.ForestCulture.Tree t = new com.dan.forest.ForestCulture.Tree();
            int abw = 0;
            for (int j = ts.j0; j < ts.j0 + ts.h; j++)
                for (int i = ts.i0; i < ts.i0 + ts.w; i++) {
                    int g = ts.growth(i, j);
                    if (g == 0) continue;
                    n[1]++;
                    if (!cu.at(i, j, t) || t.species != (g & 15) - 1 || t.scale > 1.07f * com.dan.forest.TreeStates.growthSize(g) + 1e-4f) abw++;
                }
            check(n[0] > 0 && n[0] == n[1] && abw == 0, f("Nachwuchs in TreeStates: %,d junge Bäume, die Kultur von forest stellt jeden mit Art und Größe (%d Abweichungen)", n[1], abw));
            check(Bibliothek.pakete().contains("succession"), "succession in Renderer.jar");
        }

        teil("Wald in der Szene (Phase 14a): Kronenhöhe, Band 1958, Wiederbewaldung, Treibgut");
        {
            // EPSG 3413 an der Mitte der ArcticDEM-Kachel 42_06 (aus ihrem STAC-Eintrag): 58,879446° N, 137,489553° W
            double[] ps = new com.dan.lituya.tools.Abbildung.Polar(70, -45).vor(-137.48955292199915, 58.87944616948069);
            check(Math.abs(ps[0] + 3_450_000) < 30 && Math.abs(ps[1] - 150_000) < 30,
                    f("Polarstereographisch EPSG 3413: Mitte der Kachel 42_06 bei %.1f / %.1f (soll −3 450 000 / 150 000)", ps[0], ps[1]));
            check(com.dan.lituya.scene.Wald1958.jahr(java.time.LocalDate.of(1958, 7, 10)) == 0 && com.dan.lituya.scene.Wald1958.jahr(java.time.LocalDate.of(1959, 7, 9)) == 0
                    && com.dan.lituya.scene.Wald1958.jahr(java.time.LocalDate.of(2026, 7, 9)) == 67, "Jahr der Wiederbewaldung zum Datum (10.7.1958: 0, 9.7.2026: 67)");
            com.dan.lituya.scene.SceneClock ck = new com.dan.lituya.scene.SceneClock();
            ck.setEpochMillis(com.dan.lituya.scene.SceneClock.BEBEN.toEpochMilli() + com.dan.lituya.camera.Schaustuecke.MORGEN * 1000);
            com.dan.lituya.camera.Schaustuecke.Stueck s10 = com.dan.lituya.camera.Schaustuecke.byNr(10), s11 = com.dan.lituya.camera.Schaustuecke.byNr(11);
            check(s10 != null && s11 != null && s11.jahre() && Math.abs(ck.hour() - 6) < 1e-6 && ck.date().getDayOfMonth() == 10,
                    f("Schaustücke 10 „%s“ und 11 „%s“: Morgen danach %s, 11 im Jahres-Zeitraffer", s10, s11, ck.label()));
            check(java.util.Arrays.asList(com.dan.lituya.gl.Renderer.PASSES).contains("Wald"), "Durchgang „Wald“ zwischen Gelände und Welle");
            File uf = new File(daten, "netz/ufer/spurlinie_kette.tsv");
            if (!uf.isFile() || !new File(daten, "grund/heute_4m.r32").isFile()) hinweis("daten/netz/ufer/spurlinie_kette.tsv oder daten/grund fehlt: Band 1958 übersprungen");
            else {
                com.dan.lituya.scene.Band1958 bg = new com.dan.lituya.scene.Band1958(daten, true), br = new com.dan.lituya.scene.Band1958(daten, false);
                double sp = bg.spur(354_234, 6_505_536), md = bg.spur(354_602.58, 6_503_599.57);
                check(bg.faktorSporn > 1 && bg.faktorSporn < 2.5 && bg.faktorMudslide > 1 && bg.faktorMudslide < 2.5 && br.faktorSporn == 1 && Math.abs(sp - 524) < 80 && Math.abs(md - 208) < 40,
                        f("Band 1958 gezogen: Sporn ×%.2f, Spurlinie dort %.0f m (Beleg 524), Mudslide Creek ×%.2f, %.0f m (Beleg 208); rein gerechnet ×1", bg.faktorSporn, sp, bg.faktorMudslide, md));
                File wt = new File(daten, "wald/WALD.txt");
                if (!wt.isFile()) hinweis("daten/wald fehlt (ant aufbereitung -Dschritte=wald): Kronenhöhe und Abgleich übersprungen");
                else {
                    com.dan.lituya.scene.Wald1958 wd = new com.dan.lituya.scene.Wald1958(daten, true);
                    java.util.Properties wp = new java.util.Properties();
                    try (var wrd = java.nio.file.Files.newBufferedReader(wt.toPath())) { wp.load(wrd); }
                    double vs = Double.parseDouble(wp.getProperty("versatz").trim().split("\\s")[0]), vh = Double.parseDouble(wp.getProperty("verhaeltnis").trim().split("\\s")[0]);
                    check(wd.krone != null && wd.wuchs > 0.5 && wd.wuchs < 2.6 && Math.abs(vs) < 30 && vh > 0.2 && vh < 1,
                            f("daten/wald: Kronenhöhe 10 m, Versatz Ellipsoid − NAVD88 %.2f m, im Band %.2f von außerhalb, Wuchs ×%.2f", vs, vh, wd.wuchs));
                    long tw0 = System.nanoTime();
                    com.dan.succession.Succession su = wd.succession();
                    com.dan.succession.Succession.Statistik s0 = su.statistik(0), s67 = su.statistik(67);
                    check(su.band() > 50_000 && s0.deckung() < 0.15 && s67.deckung() > 0.6,
                            f("Wiederbewaldung über das Band: %,d Plätze (%.1f km²), Deckung %.0f %% → %.0f %% (2025), %.1f s", su.band(), su.band() * 36 / 1e6,
                                    100 * s0.deckung(), 100 * s67.deckung(), (System.nanoTime() - tw0) / 1e9));
                    double[] dd = new double[3];
                    double[] hafen = com.dan.lituya.scene.Site.fromUtm(352_000, 6_503_000);
                    wd.dichte(hafen[0], hafen[1], dd);
                    check(dd[0] + dd[1] + dd[2] == 0, "Dichte des Walds: kein Baum im Wasser der Bucht");
                }
                File tg = new File(daten, "ereignis/treibgut/staemme.tsv");
                if (!tg.isFile()) hinweis("daten/ereignis/treibgut fehlt (ant treibgut1958): Treibgut übersprungen");
                else {
                    long nst = java.nio.file.Files.lines(tg.toPath()).count() - 1;
                    check(nst > 100 && new File(daten, "ereignis/treibgut/teppich_25m.r32").isFile(), f("Treibgut am Morgen: %,d Stämme und der Holzteppich", nst));
                }
            }
        }

        teil("Boote (Phase 15): vessel");
        {
            com.dan.vessel.Boat ed = com.dan.vessel.Kutter.edrie();
            com.dan.vessel.Water ru = com.dan.vessel.Water.still(0, 30);
            ed.settle(ru, 0, 0, 270);
            for (int i = 0; i < 1000; i++) ed.step(ru, 0.01);
            check(Math.abs(ed.draftNow(ru) - ed.hull.draft) < 0.05 && Math.abs(ed.hull.loa / 0.3048 - 40) < 2 && ed.gmDesign() > 0.5,
                    f("vessel aus Renderer.jar: %s, schwimmt auf %.2f m, Rollperiode von Hand %.2f s", ed, ed.draftNow(ru), ed.rollPeriodHand()));
            double[] kl = com.dan.vessel.Chain.catenaryTension(10, 66, com.dan.vessel.Kutter.EDRIE_KETTE, com.dan.vessel.Kutter.edrieKette().wetWeight());
            check(kl != null && kl[2] > 0 && com.dan.vessel.Kutter.edrieKette().fouled, f("Kette der Edrie: 40 Faden, Kettenlinie in 5 Faden %.0f N an der Klüse, Anker festgehakt", kl == null ? 0 : kl[2]));
            check(Bibliothek.pakete().contains("vessel"), "vessel in Renderer.jar");
        }

        teil("Boote in der Szene und LB_NET_SEE (Phase 15a): Gezeiten, Bahnen, Flucht");
        {
            // Gezeiten gegen die Vorausrechnung der NOAA für Elfin Cove am 2.11.2026 (AKST, m über MLLW; MLLW = NAVD88 − 1,157)
            long tg0 = java.time.Instant.parse("2026-11-02T09:00:00Z").toEpochMilli();
            List<double[]> ex = com.dan.lituya.scene.Gezeiten.extreme(tg0, tg0 + 86_400_000.0);
            double[][] noaa = {{0 * 60 + 22, 0.235, -1}, {7 * 60 + 15, 2.745, 1}, {13 * 60 + 1, 1.396, -1}, {18 * 60 + 47, 2.842, 1}};
            double dtMax = 0, dhMax = 0;
            boolean art = ex.size() == 4;
            for (int i = 0; i < Math.min(4, ex.size()); i++) {
                double min = (ex.get(i)[0] - tg0) / 60_000.0;
                dtMax = Math.max(dtMax, Math.abs(min - noaa[i][0]));
                dhMax = Math.max(dhMax, Math.abs(ex.get(i)[1] + com.dan.lituya.tools.Grund.MLLW_UNTER_NAVD88 - noaa[i][1]));
                art &= ex.get(i)[2] == noaa[i][2];
            }
            check(art && dtMax <= 25 && dhMax <= 0.25, f("Gezeiten (8 Tiden Elfin Cove) gegen NOAA 2.11.2026: 4 Extreme, bis %.0f min und %.2f m daneben", dtMax, dhMax));
            long q = SceneClock.BEBEN.toEpochMilli();
            double[] hw = null;
            for (double[] x : com.dan.lituya.scene.Gezeiten.extreme(q - 6 * 3_600_000.0, q)) if (x[2] > 0) hw = x;
            double hwMin = hw == null ? 1e9 : (hw[0] - (q - (22 * 60 + 16 - 20 * 60) * 60_000.0)) / 60_000.0;
            check(hw != null && Math.abs(hwMin) <= 30 && com.dan.lituya.scene.Gezeiten.rate(q) < 0,
                    f("Hochwasser am 9.7.1958 %+.0f min von 20:00 PST („last of the flood at 8 pm“, MILLER60); beim Stoß %s", hwMin, new com.dan.lituya.scene.Gezeiten().zeile(q)));
            // Bahn: schreiben, lesen, zwischen den Bildern
            File tb = File.createTempFile("bahn", ".bin");
            tb.deleteOnExit();
            com.dan.lituya.scene.BootBahn.Schreiber bsw = new com.dan.lituya.scene.BootBahn.Schreiber(2);
            bsw.bild(0, new double[]{0, 0, 0}, new double[]{1, 0, 0, 0}, 0, 0, -1, new double[][]{{0, 0, 0}, {1, 0, 0}});
            bsw.bild(1, new double[]{2, 4, 0}, new double[]{Math.cos(0.5), 0, Math.sin(0.5), 0}, 100, com.dan.lituya.scene.BootBahn.GERISSEN, 0, new double[][]{{2, 0, 0}, {3, 0, 0}});
            bsw.schreiben(tb);
            com.dan.lituya.scene.BootBahn bb = com.dan.lituya.scene.BootBahn.lesen(tb, "Probe");
            double[] zm = bb.zustand(0.5);
            check(bb.bilder() == 2 && Math.abs(zm[1] - 2) < 1e-6 && Math.abs(zm[5] - Math.sin(0.25)) < 1e-3 && bb.kette(0.5)[1][0] == 2,
                    f("BootBahn: schreiben, lesen, Lage und Drehung zwischen zwei Bildern (Hub %.1f m, Drehung %.1f°)", zm[1], Math.toDegrees(2 * Math.asin(zm[5]))));
            // die vorgerechneten Bahnen
            File bd = new File(new File(new File(daten, "ereignis"), "boote"), "kette");
            if (!new File(bd, "BOOTE.txt").isFile()) hinweis("keine Bahnen in daten/ereignis/boote/kette: ant boote1958 (vorher ant ereignis -Dlauf=kette neu, mit Strömung)");
            else {
                java.util.Properties bp = new java.util.Properties();
                try (var in = Files.newBufferedReader(new File(bd, "BOOTE.txt").toPath(), StandardCharsets.UTF_8)) { bp.load(in); }
                if (!"true".equals(bp.getProperty("stroemung"))) hinweis("Bahnen über einem Lauf ohne Strömung (vor 15a gerechnet): ant ereignis -Dlauf=kette, dann ant boote1958");
                String ek = bp.getProperty("edrie.kette", "");
                double eh = Double.parseDouble(bp.getProperty("edrie.hub_m", "0 0").split(" ")[0]), bh = Double.parseDouble(bp.getProperty("badger.hub_m", "0 0").split(" ")[0]);
                double bg = Double.parseDouble(bp.getProperty("badger.ueber_grund_m", "-1"));
                check(eh > 1 && bh > 1 && Double.isFinite(bg), f("die Welle erreicht die Ankerplätze: Edrie hebt sich %.1f m, Badger %.1f m", eh, bh));
                // gegen MILLER60 (Befund, keine Bedingung: die Welle der Vorrechnung ist am Eingang niedriger als 1958, Werkbuch 7.2)
                hinweis(f("gegen MILLER60: Edrie Kette %s – belegt: gerissen, als das Boot stieg: %s", ek, ek.contains("gerissen") ? "wie belegt" : "ANDERS"));
                hinweis(f("gegen MILLER60: Badger über Land %s – belegt: über La Chaussee Spit, mehr als 80 Fuß über den Bäumen", bg > 0 ? f("bis %.1f m über Grund", bg) : "nicht getragen (ANDERS)"));
                hinweis("Sunmore: " + bp.getProperty("sunmore.ende", "?") + ", 1 km draußen: " + bp.getProperty("sunmore.draussen", "?") + " (MILLER60: verloren)");
                int nb = 0;
                for (String nm : com.dan.lituya.gl.BooteGl.NAMEN) if (new File(bd, "bahn_" + nm.toLowerCase(Locale.ROOT) + ".bin").isFile()) nb++;
                check(nb == 3, f("drei Bahnen in %s", bd));
            }
            check(com.dan.lituya.camera.Schaustuecke.boot(com.dan.lituya.camera.Schaustuecke.byNr(8)) == 0 && com.dan.lituya.camera.Schaustuecke.boot(com.dan.lituya.camera.Schaustuecke.byNr(9)) == 1
                    && com.dan.lituya.camera.Schaustuecke.blick(com.dan.lituya.camera.Schaustuecke.byNr(9)) == -90, "Schaustück 8 an Bord der Edrie, 9 neben der Badger (Blick nach Backbord)");
            // LB_NET_SEE
            if (!com.dan.lituya.scene.SeeNetz.da(daten)) hinweis("daten/netz/see fehlt: ant aufbereitung -Dschritte=see");
            else {
                com.dan.lituya.scene.SeeNetz sn = com.dan.lituya.scene.SeeNetz.lesen(daten);
                boolean gerichtet = true;
                for (int e = 0; e + 1 < sn.m; e += 2) gerichtet &= sn.von[e] == sn.nach[e + 1] && sn.nach[e] == sn.von[e + 1];
                double[] st = com.dan.lituya.scene.SeeNetz.stromTabelle();
                int e0 = 0;
                for (int e = 0; e < sn.m; e++) if (sn.faktor[e] > sn.faktor[e0]) e0 = e;
                double hin = sn.kosten(e0, 0, st), her = sn.kosten(e0 % 2 == 0 ? e0 + 1 : e0 - 1, 0, st);
                check(sn.n > 1000 && gerichtet && Math.abs(hin - her) > 0.01, f("%s; am Tor ist eine Kante je Richtung verschieden lang (%.1f s gegen %.1f s im Ebbstrom)", sn, hin, her));
                com.dan.lituya.tools.SeeDb.Vergleich vg = com.dan.lituya.tools.SeeDb.lodImSpeicher(sn);
                check(vg.ok(), "Flucht: LOD (LinkCostCalculator nach der Zeit, Frontbedingung) = eigener Dijkstra: " + vg.text());
                System.out.println("    " + com.dan.lituya.scene.SeeNetz.fluchtZeile(daten));
            }
        }

        teil("Gewässer und Gletscher (Phase 16): LB_NET_WASSER");
        {
            // Umrisse aus einem Raster: Gebiet 1 mit Loch (Gebiet 2), Gebiet 3 an der Ecke
            int[] ur = {1, 1, 1, 1, 0,
                        1, 2, 2, 1, 0,
                        1, 2, 2, 1, 3,
                        1, 1, 1, 1, 3};
            java.util.Map<Integer, List<List<double[]>>> rg = com.dan.lituya.tools.Umriss.ringe(ur, 5, 4, 0, 40, 10);
            double a1 = 0;
            for (double[] r : rg.get(1).get(0)) a1 += com.dan.lituya.tools.Umriss.flaeche(r);
            check(rg.get(1).size() == 1 && rg.get(1).get(0).size() == 2 && Math.abs(a1 - 1200) < 1e-6 && Math.abs(com.dan.lituya.tools.Umriss.flaeche(rg.get(2).get(0).get(0)) - 400) < 1e-6
                    && Math.abs(com.dan.lituya.tools.Umriss.flaeche(rg.get(3).get(0).get(0)) - 200) < 1e-6,
                    f("Umriss: Gebiet mit Loch außen gegen, innen mit dem Uhrzeigersinn, Fläche %.0f m² (16 Zellen − 4 = 12)", a1));
            double q1 = com.dan.lituya.tools.Wasser.abfluss(10_000, 0);
            check(Math.abs(q1 - 10.0 / 1000 / 3600 * 1e6 * 0.6) < 1e-9 && com.dan.lituya.tools.Wasser.geschw(q1, 0.05) > 0.5 && com.dan.lituya.tools.Wasser.geschw(q1, 0.05) < 6,
                    f("Abfluss bei Regen 10 mm/h auf 1 km² Land %.2f m³/s, Manning bei 5 %% Gefälle %.2f m/s", q1, com.dan.lituya.tools.Wasser.geschw(q1, 0.05)));
            if (!com.dan.lituya.scene.WasserNetz.da(daten)) hinweis("daten/netz/wasser fehlt: ant aufbereitung -Dschritte=wasser");
            else {
                com.dan.lituya.scene.WasserNetz wn = com.dan.lituya.scene.WasserNetz.lesen(daten);
                File wd = new File(daten, com.dan.lituya.scene.WasserNetz.ORDNER);
                String fe = wn.fehler();
                check(fe == null && wn.n > 1000, wn + (fe == null ? "; Baum stromab, kreisfrei, jeder Knoten erreicht ein Ziel, jeder Knoten der Ebene 1 hat ein Gebiet" : "; " + fe));
                check(com.dan.lituya.scene.WellenNetz.pruefsumme(wd).equals(wn.wert("pruefsumme")), "Prüfsumme in WASSER.txt passt");
                // Flächen: Ebene 2 (Summe der Gebiete) gegen das Raster
                java.util.Map<String, Double> f2 = new java.util.HashMap<>();
                for (String[] pz : com.dan.lituya.scene.WellenNetz.tsv(new File(wd, "pruefwege.tsv"))) if (pz[1].equals("2")) f2.put(pz[0], Double.parseDouble(pz[3]));
                double dmax = 0;
                for (String zn : com.dan.lituya.scene.WasserNetz.ZIELE) {
                    String v = wn.wert("einzug." + com.dan.lituya.scene.WasserNetz.schluessel(zn));
                    double r = Double.parseDouble(v.split(" ")[0].replace(".", "").replace(',', '.'));
                    dmax = Math.max(dmax, Math.abs(r - f2.getOrDefault(zn, Double.NaN)));
                }
                check(dmax <= 0.06, f("Fläche je Ziel: Summe der Teileinzugsgebiete (traceIn auf Ebene 2) = Fläche im Raster bis %.3f km²", dmax));
                int gil = wn.ziel("Gilbert Inlet", 1);
                double gf = wn.flaeche[gil], ge = wn.eisAnteil[gil];
                String rand = wn.wert("rand.gilbert_inlet");
                check(gf > 100 && ge > 0.3 && rand != null && rand.startsWith("0 "), f("Gilbert Inlet: %.1f km², %.0f %% Eis, ganz in der Szene (%s)", gf, ge * 100, rand));
                // Ebenen passen zueinander: jedes Gebiet führt zum selben Ziel wie seine Knoten
                int falsch = 0;
                for (int i = 0; i < wn.n; i++) {
                    if (wn.ebene[i] != 1 || wn.parent[i] < 0) continue;
                    List<Integer> l1 = wn.traceOut(i, 1), l2 = wn.traceOut(wn.parent[i], 2);
                    if (!wn.name[l1.get(l1.size() - 1)].equals(wn.name[l2.get(l2.size() - 1)])) falsch++;
                }
                check(falsch == 0, f("Ebene 1 und 2: jeder Knoten fließt zum selben Ziel wie sein Gebiet (%d anders)", falsch));
                int[] k1 = new int[1], k2 = new int[1];
                wn.komponenten(1, k1);
                wn.komponenten(2, k2);
                check(k1[0] == com.dan.lituya.scene.WasserNetz.ZIELE.length && k2[0] == k1[0], f("Zusammenhangskomponenten: %d in Ebene 1, %d in Ebene 2 (je Ziel eine)", k1[0], k2[0]));
                String to = wn.wert("traceout.lituya_glacier");
                check(to != null && to.contains("bis Gilbert Inlet"), "traceOut Lituya Glacier: " + to);
                com.dan.lituya.tools.WasserDb.Vergleich vr = com.dan.lituya.tools.WasserDb.routeGegenDatei(wn);
                check(vr.ok(), "Fließzeit zum Ziel: " + vr.text());
                com.dan.lituya.tools.WasserDb.Vergleich vl = com.dan.lituya.tools.WasserDb.lodImSpeicher(wn);
                check(vl.ok(), "traceIn: LOD = Datei: " + vl.text());
                // Umrisse (50 m) gegen die Fläche aus 10 m
                java.util.Map<Integer, Double> fu = new java.util.HashMap<>();
                for (String[] uz : com.dan.lituya.scene.WellenNetz.tsv(new File(wd, "umrisse.tsv"))) {
                    String[] p = uz[3].split(",");
                    double[] r = new double[2 * p.length];
                    for (int q = 0; q < p.length; q++) { String[] xy = p[q].split(" "); r[2 * q] = Double.parseDouble(xy[0]); r[2 * q + 1] = Double.parseDouble(xy[1]); }
                    fu.merge(Integer.parseInt(uz[0]), com.dan.lituya.tools.Umriss.flaeche(r) / 1e6, Double::sum);
                }
                double um = 0;
                int ug = 0;
                for (String[] ez : com.dan.lituya.scene.WellenNetz.tsv(new File(wd, "einzug.tsv"))) {
                    double fe2 = Double.parseDouble(ez[4]);
                    if (fe2 < 5) continue;
                    ug++;
                    um = Math.max(um, Math.abs(fu.getOrDefault(Integer.parseInt(ez[0]), 0.0) - fe2) / fe2);
                }
                check(ug > 0 && um < 0.1, f("Umrisse aus 50 m: %d Gebiete ab 5 km², Fläche bis %.1f %% von der aus 10 m", ug, um * 100));
                String eq = wn.wert("eis_geschwindigkeit");
                if (eq == null || eq.startsWith("ohne")) hinweis("Gletscher ohne ITS_LIVE (Richtung aus dem Gelände, 100 m/a [G]): python tools/kartenmaterial/kartenmaterial.py laden --quellen itslive_geschwindigkeit, dann ant aufbereitung -Dschritte=wasser");
                System.out.println("    " + com.dan.lituya.scene.WasserNetz.zeile(daten));
            }
        }

        teil("Wege über Gelände (Phase 16a): Klassen und LB_NET_WEG");
        {
            double v0 = com.dan.lituya.tools.Weg.tobler(0), vmax = com.dan.lituya.tools.Weg.tobler(-0.05), vauf = com.dan.lituya.tools.Weg.tobler(0.3);
            check(Math.abs(v0 - 5.04) < 0.01 && Math.abs(vmax - 6) < 1e-9 && vauf < 2.2 && Math.abs(com.dan.lituya.tools.Weg.naismith(5, 600) - 7200) < 1e-9,
                    f("Tobler: eben %.2f km/h, leicht bergab %.2f km/h (das schnellste), 30 %% bergauf %.2f km/h; Naismith 5 km und 600 m = 2 h", v0, vmax, vauf));
            double q75 = com.dan.lituya.tools.Klassen.mittelwasser(7.5, 0);
            check(Math.abs(q75 - 0.5) < 0.02, f("Mittelwasser bei 3 000 mm/a: 7,5 km² Land geben %.2f m³/s (Sperre ab 0,5)", q75));
            byte[] kk = com.dan.lituya.tools.Klassen.lesen(daten, com.dan.lituya.tools.Klassen.KERN), ks = com.dan.lituya.tools.Klassen.lesen(daten, com.dan.lituya.tools.Klassen.SZENE);
            if (kk == null || ks == null) hinweis("daten/klassen fehlt: ant aufbereitung -Dschritte=klassen");
            else {
                long[] nk = new long[com.dan.lituya.tools.Klassen.NAME.length], ns = new long[nk.length];
                for (byte x : kk) nk[x]++;
                for (byte x : ks) ns[x]++;
                double wald = (nk[com.dan.lituya.tools.Klassen.WALD] + nk[com.dan.lituya.tools.Klassen.GEBUESCH]) / (double) kk.length;
                double spalt = ns[com.dan.lituya.tools.Klassen.SPALTEN] / (double) (ns[com.dan.lituya.tools.Klassen.SPALTEN] + ns[com.dan.lituya.tools.Klassen.EIS]);
                check(wald > 0.2 && spalt > 0.05 && spalt < 0.7 && nk[com.dan.lituya.tools.Klassen.STRAND] > 0 && ns[com.dan.lituya.tools.Klassen.BACH] > 0,
                        f("Geländeklassen: im Kern %.0f %% Wald und Gebüsch, Strand %.2f %%; in der Szene %.0f %% des Eises Spaltenzone, Bäche gesperrt %.2f %%", wald * 100,
                                100.0 * nk[com.dan.lituya.tools.Klassen.STRAND] / kk.length, spalt * 100, 100.0 * ns[com.dan.lituya.tools.Klassen.BACH] / ks.length));
            }
            if (!com.dan.lituya.scene.WegNetz.da(daten)) hinweis("daten/netz/weg fehlt: ant aufbereitung -Dschritte=klassen+weg");
            else {
                com.dan.lituya.scene.WegNetz wg = com.dan.lituya.scene.WegNetz.lesen(daten);
                check(com.dan.lituya.scene.WegNetz.pruefsumme(daten).equals(wg.wert("pruefsumme")), wg + "; Prüfsumme in WEG.txt passt");
                // gerichtet: zu jeder Kante die Gegenkante, bergauf langsamer als bergab; keine Kante zwischen den Ebenen; Eltern auf Ebene 2
                int ohneGegen = 0, quer = 0, falsch = 0, steil = 0;
                for (int e = 0; e < wg.m; e += 97) {
                    int wa0 = wg.von[e], wb0 = wg.nach[e];
                    if (wg.ebene[wa0] != wg.ebene[wb0]) quer++;
                    int g = -1;
                    for (int x = wg.aus[wb0]; x < wg.aus[wb0 + 1]; x++) if (wg.nach[x] == wa0) g = x;
                    if (g < 0) { ohneGegen++; continue; }
                    double dh = wg.hoehe[wb0] - wg.hoehe[wa0];
                    if (Math.abs(dh) > 2 && (dh > 0) != (wg.cost[e] > wg.cost[g])) { if (Math.abs(dh) < 0.6 * Math.hypot(wg.ost(wb0) - wg.ost(wa0), wg.nord(wb0) - wg.nord(wa0))) falsch++; else steil++; }
                }
                int eltern = 0;
                for (int kq = 0; kq < wg.n; kq += 101) if (wg.ebene[kq] == 1 && wg.parent[kq] >= 0 && wg.ebene[wg.parent[kq]] != 2) eltern++;
                check(ohneGegen == 0 && quer == 0 && falsch == 0 && eltern == 0,
                        f("Kanten (jede 97.): je Richtung eine, bergauf langsamer (%d anders, %d steile Gegenhänge nach Tobler), keine zwischen den Ebenen, Eltern auf Ebene 2", falsch, steil));
                // 1 h vom Strand nachgerechnet
                java.util.Map<String, String[]> pw = new java.util.HashMap<>();
                for (String[] pz : com.dan.lituya.scene.WellenNetz.tsv(new File(new File(daten, com.dan.lituya.scene.WegNetz.ORDNER), "pruefwege.tsv"))) pw.put(pz[0] + "|" + pz[1], pz);
                String[] st1 = pw.get("STUNDE_BADGER|1");
                if (st1 != null) {
                    double[] t1 = wg.dijkstra(Integer.parseInt(st1[2]) - 1, com.dan.lituya.tools.Weg.STUNDE, null);
                    int a1 = 0;
                    for (double x : t1) if (!Double.isNaN(x)) a1++;
                    check(a1 == Integer.parseInt(st1[4]) && a1 > 1000, f("1 h vom Strand in Anchorage Cove: %,d Knoten (%.2f km²) wie in pruefwege.tsv", a1, a1 * 1e-4));
                }
                String wa = wg.wert("washburn");
                check(wa != null && pw.containsKey("WASHBURN_2|2"), "Washburn 1934 (Ebene 2): " + wa);
                com.dan.lituya.tools.WegDb.Vergleich vw = com.dan.lituya.tools.WegDb.lodImSpeicher(wg, daten);
                check(vw.ok(), "LOD = eigener Dijkstra: " + vw.text());
                System.out.println("    " + com.dan.lituya.scene.WegNetz.zeile(daten));
                // Phase 16b: Flucht an Land
                float[] gm = {1, 5, 2, 0, 0, 7, 3};
                float[] go = new float[gm.length];
                com.dan.lituya.tools.Landflucht.gleitMax(gm, 0, 1, gm.length, 1, go);
                check(java.util.Arrays.equals(go, new float[]{5, 5, 5, 2, 7, 7, 7}), "Landflucht: gleitendes Maximum über ±1 Zelle " + java.util.Arrays.toString(go));
                File lf = new File(new File(daten, com.dan.lituya.scene.WegNetz.ORDNER), "landflucht.tsv");
                if (!lf.isFile()) hinweis("daten/netz/weg/landflucht.tsv fehlt: ant aufbereitung -Dschritte=landflucht");
                else if (!new File(com.dan.lituya.scene.EreignisDaten.ordner(daten, "kette"), "max_g0.r32").isFile()) hinweis("Lauf kette fehlt (ant ereignis -Dlauf=kette): Flucht an Land nicht nachgerechnet");
                else {
                    com.dan.lituya.scene.SeeNetz sn = com.dan.lituya.scene.SeeNetz.lesen(daten);
                    com.dan.lituya.tools.Landflucht.Feld lfd = com.dan.lituya.tools.Landflucht.feld(daten, "kette", com.dan.lituya.tools.Klassen.gelaende(daten, com.dan.lituya.tools.Klassen.KERN));
                    boolean[] lsi = new boolean[wg.n];
                    double[] lfw = com.dan.lituya.tools.Landflucht.frontJeKnoten(wg, lfd, lsi);
                    int[][] lum = com.dan.lituya.tools.Landflucht.umstieg(daten);
                    int gleichL = 0, alleL = 0;
                    double dL = 0;
                    for (String[] lz : com.dan.lituya.scene.WellenNetz.tsv(lf)) {
                        if (lz[0].endsWith("*") || !(lz[1].equals("0") || lz[1].equals("90") || lz[1].equals("180"))) continue;
                        Object[] pl = lz[0].equals("Edrie") ? com.dan.lituya.scene.SeeNetz.PLAETZE[0] : com.dan.lituya.scene.SeeNetz.PLAETZE[1];
                        com.dan.lituya.tools.Landflucht.Ergebnis le = com.dan.lituya.tools.Landflucht.flucht(sn, sn.knotenBei((double) pl[1], (double) pl[2]), Double.parseDouble(lz[1]),
                                com.dan.lituya.scene.SeeNetz.stromTabelle(), wg, lum, lfw, lsi);
                        alleL++;
                        boolean gl = le.gerettet() == lz[2].equals("J") && (!le.gerettet() || Math.abs(le.sicher() - Double.parseDouble(lz[7])) < 0.2);
                        if (gl) gleichL++;
                        if (le.gerettet()) dL = Math.max(dL, Math.abs(le.sicher() - Double.parseDouble(lz[7])));
                    }
                    check(alleL == 6 && gleichL == alleL, f("Flucht an Land nachgerechnet (Edrie, Badger bei 0, 90, 180 s): %d von %d wie landflucht.tsv, bis %.2f s", gleichL, alleL, dL));
                    com.dan.lituya.tools.WegDb.Vergleich vlf = com.dan.lituya.tools.WegDb.landfluchtImSpeicher(wg, daten);
                    check(vlf.ok(), "Landweg: LOD (withinCost mit Frontbedingung) = eigener Dijkstra: " + vlf.text());
                    System.out.println("    " + com.dan.lituya.scene.WegNetz.landfluchtZeile(daten));
                }
            }
        }

        teil("Netzblick für Wasser und Weg, Bäche in der Szene (Phase 16c)");
        {
            double mw = com.dan.lituya.tools.Klassen.mittelwasser(10, 0.5), q0 = com.dan.lituya.scene.WasserNetz.abfluss(10, 0.5, 0),
                    q10 = com.dan.lituya.scene.WasserNetz.abfluss(1, 0, 10);
            check(Math.abs(q0 - mw) < 1e-12 && Math.abs(q10 - (com.dan.lituya.tools.Klassen.mittelwasser(1, 0) + 0.7 * 10 / 3.6)) < 1e-9
                            && Math.abs(com.dan.lituya.scene.WasserNetz.breite(4) - 5) < 1e-9 && com.dan.lituya.scene.WasserNetz.breite(1e6) == 90,
                    f("Abfluss: ohne Regen das Mittelwasser (%.3f m³/s aus 10 km² halb Eis), 1 km² Land bei 10 mm/h %.2f m³/s; Breite 2,5·Q^0,5 (4 m³/s → 5 m, höchstens 90 m)", q0, q10));
            String vs = com.dan.lituya.gl.Shader.source("baeche.vert", null).text, fs = com.dan.lituya.gl.Shader.source("baeche.frag", null).text;
            check(vs.contains("uRegen") && vs.contains("2.5 * sqrt") && vs.contains("0.045") && fs.contains("aerial(") && !vs.matches("(?s).*\\bhalf\\b.*"),
                    "Shader baeche.vert/baeche.frag mit common.glsl: Breite und Manning wie WasserNetz und tools.Wasser, Dunst wie das Gelände");
            if (!com.dan.lituya.scene.WasserNetz.da(daten)) hinweis("daten/netz/wasser fehlt: ant aufbereitung -Dschritte=wasser");
            else {
                com.dan.lituya.gl.Netzblick nb16 = new com.dan.lituya.gl.Netzblick();
                nb16.bauenWasser(daten, null);
                com.dan.lituya.scene.WasserNetz wn = com.dan.lituya.scene.WasserNetz.lesen(daten);
                int nB = 0;
                double lB = 0;
                for (int e = 0; e < wn.m; e++)
                    if (wn.kEbene[e] == 1 && (com.dan.lituya.scene.WasserNetz.BACH.equals(wn.kArt[e]) || com.dan.lituya.scene.WasserNetz.BACH_EIS.equals(wn.kArt[e]))) { nB++; lB += wn.laenge[e]; }
                com.dan.netview.NetLayer bl = nb16.ebene("Bäche"), zl = nb16.ebene("Ziele"), ul = nb16.ebene("Einzugsgebiete");
                double[] gi16 = nb16.zuflussZiel(0, 0), gi10 = nb16.zuflussZiel(0, 10);
                String eg = wn.wert("einzug.gilbert_inlet");
                double egKm = eg == null ? Double.NaN : Double.parseDouble(eg.replaceAll("^([0-9.,]+).*$", "$1").replace(".", "").replace(',', '.'));
                check(bl.items.size() >= nB && zl.items.size() >= 3 && ul.items.size() > 10 && Math.abs(gi16[1] - egKm) < 0.02 * egKm && gi10[0] > 10 * gi16[0],
                        f("Netzblick Wasser: %,d Bäche in %,d Stücken, %,d Umrissstücke, %d Ziele; Gilbert Inlet über die Bäche %.1f km² (WASSER.txt %.1f), %.0f m³/s ohne Regen, %.0f m³/s bei 10 mm/h",
                                nB, bl.items.size(), ul.items.size(), zl.items.size(), gi16[1], egKm, gi16[0], gi10[0]));
                com.dan.lituya.gl.BaecheGl bg = new com.dan.lituya.gl.BaecheGl();
                bg.bauen(wn, com.dan.lituya.scene.WasserNetz.linien(daten), (xq, zq) -> 10);
                float[] sv = bg.scheitel();
                boolean seiten = true;
                for (int q = 0; q + 2 * com.dan.lituya.gl.BaecheGl.FLOATS <= sv.length && q < 200_000; q += 2 * com.dan.lituya.gl.BaecheGl.FLOATS)
                    seiten &= sv[q + 6] == -1 && sv[q + com.dan.lituya.gl.BaecheGl.FLOATS + 6] == 1 && Math.abs(Math.hypot(sv[q + 4], sv[q + 5]) - 1) < 1e-3;
                check(bg.baender() == nB && Math.abs(bg.km() * 1000 - lB) < 0.02 * lB && seiten,
                        f("Bäche als Bänder: %,d (alle BACH und BACH_EIS), %,.0f km (Kanten %,.0f km), %,d Scheitel, Seiten links und rechts", bg.baender(), bg.km(), lB / 1000, sv.length / com.dan.lituya.gl.BaecheGl.FLOATS));
            }
            if (!new File(new File(daten, com.dan.lituya.scene.WegNetz.ORDNER), "WEG.txt").isFile()) hinweis("daten/netz/weg fehlt: ant aufbereitung -Dschritte=klassen+weg+landflucht");
            else {
                com.dan.lituya.gl.Netzblick nw16 = new com.dan.lituya.gl.Netzblick();
                nw16.bauenWeg(daten, null);
                com.dan.netview.NetLayer sl = nw16.ebene("1 h vom Strand"), wl = nw16.ebene("Washburn 1934"), lw16 = nw16.ebene("Flucht an Land"), pl = nw16.ebene("Wegpunkte");
                boolean steigt = true;
                for (com.dan.netview.NetLayer.Item it : lw16.items) for (int q = 1; q < it.values.length; q++) steigt &= it.values[q] >= it.values[q - 1] - 1e-3;
                int wpt = pl.items.isEmpty() ? 0 : pl.items.get(0).xyz.length / 3;
                check(sl.items.size() > 0 && wl.segments() > 100 && wpt == 3 && lw16.items.size() >= 2 && steigt,
                        f("Netzblick Weg: 1 h vom Strand %d Isolinienstücke, Washburn %,d Strecken mit %d Wegpunkten, Flucht an Land %d Strahlen (Zeit steigt längs des Wegs)",
                                sl.items.size(), wl.segments(), wpt, lw16.items.size()));
            }
        }

        sicht17(daten);
        regie17a(daten);
        klang18(daten);
        szene19(daten);

        teil("Staub und Treibgut (Phase 12): dustcloud, debris");
        {
            com.dan.dustcloud.DustCloud dc = new com.dan.dustcloud.DustCloud(0, 0, 40, 20, 50, com.dan.dustcloud.DustCloud.rockDust());
            dc.build((dx2, dz2) -> Math.max(0, 300 - 0.3 * dx2));
            dc.source(new com.dan.dustcloud.DustCloud.Source(300, 500, 100, 0, 10, 10, 1e5, 0, 0));
            dc.run(60);
            double[] du = dc.budget();
            check(Math.abs((du[0] + du[1] + du[2] + du[3] + du[4]) / du[5] - 1) < 1e-9 && Math.abs(dc.stokes(50e-6) - 0.2) < 0.02,
                    f("dustcloud aus Renderer.jar: Wolke am Hang 60 s, Staub genau verbucht (%.0f m³), Stokes 50 µm %.2f m/s", du[5], dc.stokes(50e-6)));
            com.dan.debris.Debris dd = new com.dan.debris.Debris(new com.dan.debris.Debris.Water() {
                public double depth(double dx2, double dz2) { return 30; }

                public double[] current(double dx2, double dz2) { return new double[]{0.5, 0}; }
            });
            com.dan.debris.Debris.Log dl = dd.add(com.dan.debris.Debris.Kind.TRUNK, 20, 0.5, 0, 0, 1);
            dd.run(1800);
            double dw = dd.windage(dl);
            check(Math.abs(dl.vx - 0.5 / (1 + dw)) < 0.01 && Math.abs(dl.draft(1000) - 0.23) < 0.005,
                    f("debris aus Renderer.jar: Stamm 20 × 0,5 m treibt mit %.3f m/s (Strömung 0,5, ruhende Luft), Tiefgang %.3f m, Windanteil %.1f %%", dl.vx, dl.draft(1000), 100 * dw));
        }

        teil("Netze der Welle und des Ufers (Phase 7a): daten/netz/welle und daten/netz/ufer");
        {
            File wd = new File(daten, com.dan.lituya.scene.WellenNetz.ORDNER), ud = new File(daten, com.dan.lituya.scene.UferNetz.ORDNER);
            if (!new File(wd, "kanten.tsv").isFile() || !new File(ud, "punkte.tsv").isFile()) hinweis("daten/netz/welle oder ufer fehlt: ant aufbereitung \"-Dschritte=welle,ufer\"");
            else {
                com.dan.lituya.scene.WellenNetz wn = com.dan.lituya.scene.WellenNetz.lesen(daten);
                String summe = com.dan.lituya.scene.WellenNetz.pruefsumme(wd);
                check(wn.n > 60_000 && wn.n < 95_000 && wn.quelle == wn.n + 1 && wn.text.contains("pruefsumme=" + summe),
                        f("LB_NET_WELLE als Datei: %,d Knoten (F21: ≈ 75 000) und QUELLE, %,d Kanten, Prüfsumme in WELLE.txt passt", wn.n, wn.kanten));
                // Dijkstra über die Kanten der Datei (T_HEUTE) gegen ankunft.tsv: Netz und Ankunft gehören zusammen
                com.dan.lituya.tools.WelleDb.Kanten kt = com.dan.lituya.tools.WelleDb.Kanten.lesen(wd);
                double[] t = dijkstra(wn.quelle, kt.von, kt.nach, kt.kosten[0], kt.id);
                double gr = 0;
                int fehlt = 0;
                for (int i = 0; i < wn.n; i++) {
                    double ta = wn.ankunft[0][i], tb = t[i + 1];
                    if (Double.isNaN(ta) != Double.isInfinite(tb)) fehlt++; else if (!Double.isNaN(ta)) gr = Math.max(gr, Math.abs(ta - tb));
                }
                check(fehlt == 0 && gr <= 0.01, f("Ankunft heute linear aus den Kanten nachgerechnet: größte Abweichung %.4f s, %d ohne Gegenstück", gr, fehlt));
                double ein = wn.ankunftBei(0, Site.CHAUSSEE.e(), Site.CHAUSSEE.n(), 400), ein40 = wn.ankunftBei(1, Site.CHAUSSEE.e(), Site.CHAUSSEE.n(), 400),
                        einN = wn.ankunftBei(3, Site.CHAUSSEE.e(), Site.CHAUSSEE.n(), 400);
                check(ein > 280 && ein < 420 && einN < ein && Math.abs(ein40 - ein) < 30,
                        f("Front am Eingang: heute %.0f s, 1940 %.0f s, nichtlinear %.0f s (Handwert 350 / 300 s, 9.5)", ein, ein40, einN));
                com.dan.lituya.scene.UferNetz u = com.dan.lituya.scene.UferNetz.lesen(daten);
                com.dan.lituya.scene.UferNetz.Strang s1 = u.straenge.get(1);
                double[] a0 = s1.en();
                boolean start = Math.hypot(a0[0] - Site.CHAUSSEE.e(), a0[1] - Site.CHAUSSEE.n()) < 600;
                double grL = 0;
                for (com.dan.lituya.scene.UferNetz.Auflauf x : u.auflauf) {
                    double[] p = u.punkt(x.strang(), x.m());
                    grL = Math.max(grL, Math.hypot(p[0] - x.e(), p[1] - x.n()));
                }
                check(s1.laenge() > 25_000 && s1.laenge() < 60_000 && start && u.straenge.size() == 2 && u.straenge.get(2).ring() && grL < 0.05,
                        f("LB_NET_UFER als Datei: %s; Ufer beginnt an La Chaussee Spit, Aufläufe aus ihrem Maß auf %.3f m", u, grL));
            }
        }

        teil("Netze (Phase 3): Skripte, Funktionsprobe im Speicher, Herkunft");
        File netzSql = new File("db/netz");
        if (!netzSql.isDirectory()) hinweis("db/netz nicht gefunden");
        else {
            File[] sk = netzSql.listFiles((dd, nm) -> nm.endsWith(".sql"));
            java.util.Arrays.sort(sk);
            int anw = 0, fremd = 0;
            java.util.regex.Pattern obj = java.util.regex.Pattern.compile("(?i)CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:TABLE|SEQUENCE|VIEW|INDEX|TRIGGER|PROCEDURE)\\s+(\\w+)");
            for (File x : sk) {
                String t = Files.readString(x.toPath(), StandardCharsets.UTF_8);
                List<String> a = com.dan.lituya.tools.NetzDb.anweisungen(t);
                anw += a.size();
                for (String s : a) {
                    var m = obj.matcher(s);
                    while (m.find()) if (!m.group(1).toUpperCase(Locale.ROOT).startsWith("LB_")) { fremd++; System.out.println("    ohne LB_: " + m.group(1) + " in " + x.getName()); }
                }
            }
            check(sk.length == 18 && anw > 40, f("db/netz: %d Skripte, %d Anweisungen, jede mit „/“ abgeschlossen", sk.length, anw));
            check(fremd == 0, "alle Objekte mit Präfix LB_ (F40 B: Schema LITUYABAY1958)");
        }
        {
            List<String[]> pr = com.dan.lituya.netz.NdmProbeKern.analysen(com.dan.lituya.netz.NdmProbeKern.imSpeicher());
            int nein = 0;
            for (String[] zp : pr) if (!zp[4].equals("J")) { nein++; System.out.println("    " + String.join(" | ", zp)); }
            check(nein == 0 && pr.size() == 12, f("LOD im Speicher (Probenetz wie LB_PROBE_RAUM): %d Analysen, eigene Kostenfunktion und Netzbedingung, alle wie erwartet", pr.size()));
        }
        File hk = new File(daten, com.dan.lituya.tools.Herkunft.ORDNER);
        if (!new File(hk, "knoten.tsv").isFile()) hinweis("Herkunftsnetz fehlt (daten/netz/herkunft): ant aufbereitung -Dschritte=herkunft,inhalt");
        else {
            com.dan.lituya.tools.Herkunft hn = com.dan.lituya.tools.Herkunft.lesen(hk);
            String fe = hn.fehler();
            check(fe == null, f("Herkunft: %d Knoten, %d Kanten, fortlaufend und kreisfrei%s", hn.knotenListe().size(), hn.kantenListe().size(), fe == null ? "" : " – " + fe));
            check(hn.rohVorfahren().equals(com.dan.lituya.tools.Herkunft.pruefwege(hk)), "pruefwege.tsv nachgerechnet (Rohdateien je Ausgabe)");
            String txt = Files.readString(new File(hk, "HERKUNFT.txt").toPath(), StandardCharsets.UTF_8);
            check(txt.contains("pruefsumme=" + com.dan.lituya.tools.Herkunft.pruefsumme(hk)), "Prüfsumme in HERKUNFT.txt passt zu knoten.tsv und kanten.tsv");
            long aus = hn.knotenListe().stream().filter(x -> x.art().equals("AUSGABE")).count();
            long roh = hn.knotenListe().stream().filter(x -> x.art().equals("ROHDATEI")).count();
            check(roh > 1000 && aus > 400, f("je Rohdatei ein Knoten (%d), je Ausgabe einer (%d)", roh, aus));
        }

        teil("Regel 3.3 und Paketvertrag 13");
        File src = new File("src");
        if (src.isDirectory()) {
            int download = 0, oracle = 0;
            try (Stream<Path> st = Files.walk(src.toPath())) {
                for (Path p : (Iterable<Path>) st.filter(x -> x.toString().endsWith(".java"))::iterator) {
                    String txt = Files.readString(p, StandardCharsets.UTF_8), pfad = p.toString().replace('\\', '/');
                    boolean tool = pfad.contains("/lituya/tools/"), db = pfad.contains("/lituya/data/") || pfad.contains("/lituya/netz/");
                    if (!tool && txt.contains("\"download")) { download++; System.out.println("    liest download/: " + pfad); }
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

    /** Dijkstra über ungerichtete Kanten (Nummern bis max) von einem Knoten; unerreichte +∞. */
    static double[] dijkstra(int start, long[] von, long[] nach, double[] kosten, long[] id) {
        int max = start;
        for (long v : von) max = (int) Math.max(max, v);
        for (long v : nach) max = (int) Math.max(max, v);
        int[] grad = new int[max + 2];
        for (int i = 0; i < von.length; i++) { grad[(int) von[i] + 1]++; grad[(int) nach[i] + 1]++; }
        for (int i = 1; i < grad.length; i++) grad[i] += grad[i - 1];
        int[] zu = new int[2 * von.length], pos = grad.clone();
        double[] c = new double[2 * von.length];
        for (int i = 0; i < von.length; i++) {
            double w = kosten[(int) id[i]];
            int a = (int) von[i], b = (int) nach[i];
            c[pos[a]] = w; zu[pos[a]++] = b;
            c[pos[b]] = w; zu[pos[b]++] = a;
        }
        double[] t = new double[max + 1];
        java.util.Arrays.fill(t, Double.POSITIVE_INFINITY);
        t[start] = 0;
        java.util.PriorityQueue<double[]> q = new java.util.PriorityQueue<>((x, y) -> Double.compare(x[0], y[0]));
        q.add(new double[]{0, start});
        while (!q.isEmpty()) {
            double[] e = q.poll();
            int k = (int) e[1];
            if (e[0] > t[k]) continue;
            for (int j = grad[k]; j < grad[k + 1]; j++) {
                double d = t[k] + c[j];
                if (d < t[zu[j]]) { t[zu[j]] = d; q.add(new double[]{d, zu[j]}); }
            }
        }
        return t;
    }

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

    /** Phase 17: LB_NET_SICHT (eigene Methode, damit die Namen nicht mit main zusammenstoßen). */
    static void sicht17(File daten) throws Exception {
        teil("Sicht und Kamera (Phase 17): LB_NET_SICHT, Rundflug und Kamerafahrt");
        {
            double t1 = com.dan.lituya.scene.SichtNetz.flugzeit(3000, 0, 0), t2 = com.dan.lituya.scene.SichtNetz.flugzeit(0, 4000, 1200);
            com.dan.lituya.tools.Sicht.Hoehe meer = (e, n) -> 0;
            java.util.concurrent.atomic.AtomicInteger pr17 = new java.util.concurrent.atomic.AtomicInteger();
            double f12 = com.dan.lituya.tools.Sicht.frei(meer, new double[]{0, 0, 3, 0}, new double[]{12_000, 0, 3, 0}, 12_000, pr17),
                    f20 = com.dan.lituya.tools.Sicht.frei(meer, new double[]{0, 0, 3, 0}, new double[]{20_000, 0, 3, 0}, 20_000, pr17);
            double soll12 = 3 - 12_000.0 * 12_000 / (8 * com.dan.lituya.scene.SichtNetz.radius());
            check(Math.abs(t1 - 100) < 1e-9 && Math.abs(t2 - 100) < 1e-9 && Math.abs(f12 - soll12) < 0.01 && f20 < 0,
                    f("Flugzeit: 3 km am Boden 100 s, 1 200 m steigen 100 s; Sicht über das Meer in 3 m Augenhöhe: 12 km frei (%.2f m Abstand, k = 0,13), 20 km hinter der Kimm", f12));
            if (!com.dan.lituya.scene.SichtNetz.da(daten)) hinweis("daten/netz/sicht fehlt: ant aufbereitung -Dschritte=sicht");
            else {
                com.dan.lituya.scene.SichtNetz sn = com.dan.lituya.scene.SichtNetz.lesen(daten);
                check(com.dan.lituya.scene.WellenNetz.pruefsumme(new File(daten, com.dan.lituya.scene.SichtNetz.ORDNER)).equals(sn.wert("pruefsumme")), sn + "; Prüfsumme in SICHT.txt passt");
                int[] kz17 = new int[1];
                sn.komponenten(kz17);
                int falsch17 = 0, motive17 = 0;
                for (int e = 0; e < sn.m; e++) {
                    int a17 = sn.von[e], b17 = sn.nach[e];
                    double c = com.dan.lituya.scene.SichtNetz.flugzeit(sn.laenge[e], 0.5 * (sn.hoehe[a17] + sn.hoehe[b17]), sn.hoehe[b17] - sn.hoehe[a17]);
                    if (Math.abs(c - sn.cost[e]) > 0.05 || sn.frei[e] < 0 || Math.abs(Math.hypot(sn.ost[b17] - sn.ost[a17], sn.nord[b17] - sn.nord[a17]) - sn.laenge[e]) > 0.2) falsch17++;
                }
                for (boolean x : sn.motiv) if (x) motive17++;
                java.util.Map<String, Integer> arten17 = new java.util.TreeMap<>();
                for (String x : sn.art) arten17.merge(x, 1, Integer::sum);
                check(kz17[0] == 1 && falsch17 == 0 && motive17 >= 20 && sn.n >= 100,
                        f("%d Punkte (%s), eine Komponente, %d Motive; jede Sichtlinie frei, Länge und Flugzeit nachgerechnet (%d falsch)", sn.n, arten17, motive17, falsch17));
                // Fahrten: aufeinanderfolgende Knoten durch Sichtlinien verbunden, Zeit wie die Summe der Kosten, alle Motive besucht
                for (String[] fd : new String[][]{{"rundflug.tsv", "rundflug.kosten"}, {"fahrt.tsv", null}}) {
                    double[][] fo = com.dan.lituya.scene.SichtNetz.fahrt(daten, fd[0]);
                    double tt = 0;
                    int ohne = 0;
                    java.util.Set<Integer> bes = new java.util.HashSet<>();
                    for (int q = 0; q < fo.length; q++) {
                        int k = (int) fo[q][0];
                        bes.add(k);
                        if (q == 0) continue;
                        int kp = (int) fo[q - 1][0], ke = -1;
                        for (int e : sn.adj[kp]) if (sn.anderes(e, kp) == k) ke = e;
                        if (ke < 0) ohne++; else tt += sn.cost[ke];
                    }
                    int fehlt = 0;
                    for (int k = 0; k < sn.n; k++) if (sn.motiv[k] && !bes.contains(k)) fehlt++;
                    double soll = fd[1] == null ? tt : Double.parseDouble(sn.wert(fd[1]));
                    check(ohne == 0 && fehlt == 0 && Math.abs(tt - fo[fo.length - 1][1]) < 0.5 && Math.abs(tt - soll) < 0.5 && (fd[1] == null || (int) fo[0][0] == (int) fo[fo.length - 1][0]),
                            f("%s: %,d Schritte die Sichtlinien entlang, %.0f min Flugzeit, alle Motive besucht%s", fd[0], fo.length, tt / 60, fd[1] != null ? ", geschlossen" : ""));
                }
                com.dan.lituya.tools.SichtDb.Vergleich v17 = com.dan.lituya.tools.SichtDb.lodImSpeicher(sn);
                check(v17.ok(), "Zweitrechnung LOD im Speicher (mcst, tsp, nearestNeighbors) gegen die eigene: " + v17.text());
                com.dan.lituya.camera.Fahrt fr17 = com.dan.lituya.camera.SichtFahrt.bauen(daten, com.dan.lituya.camera.SichtFahrt.RUNDFLUG, 15, null);
                double[][] fo = com.dan.lituya.scene.SichtNetz.fahrt(daten, "rundflug.tsv");
                double lk = 0;
                for (int q = 1; q < fo.length; q++) { int a17 = (int) fo[q - 1][0], b17 = (int) fo[q][0]; lk += Math.hypot(sn.ost[b17] - sn.ost[a17], sn.nord[b17] - sn.nord[a17]); }
                check(fr17 != null && Math.abs(fr17.length - lk) < 0.05 * lk && Math.abs(fr17.duration - fo[fo.length - 1][1] / 15) < 1,
                        f("Fahrt Rundflug in der App: %s (Knoten %,.0f m, Rundung mit Catmull-Rom)", fr17 == null ? "–" : fr17.describe(), lk));
                com.dan.lituya.gl.Netzblick ns17 = new com.dan.lituya.gl.Netzblick();
                ns17.bauenSicht(daten);
                check(ns17.ebene("Sichtlinien").items.size() == sn.m && ns17.ebene("Spannbaum").items.size() == sn.n - 1 && !ns17.ebene("Rundflug").items.isEmpty(),
                        f("Netzblick Sicht: %,d Sichtlinien, Spannbaum %d Kanten, Rundflug %d Stücke", ns17.ebene("Sichtlinien").items.size(), ns17.ebene("Spannbaum").items.size(), ns17.ebene("Rundflug").items.size()));
            }
        }
    }

    /** Phase 17a: die Regiebücher ohne Bild (eigene Methode, damit die Namen nicht mit main zusammenstoßen). */
    static void regie17a(File daten) throws Exception {
        teil("Regiebücher (Phase 17a): fünf Bücher, Stichworte, Namen, warte");
        // warte: die Uhr des Regiebuchs steht, bis die Bedingung kommt
        com.dan.lituya.scene.Regie.Book pb = com.dan.lituya.scene.Regie.parse("Regiebuch: Probe\n0:00 zeit 09.07.1958 22:00 | Abend\n0:01 warte ereignis 30\n0:01 foto Nach dem Warten\n0:02 blatt\n");
        java.util.List<String> log = new java.util.ArrayList<>();
        int[] bereit = {0};
        com.dan.lituya.scene.Regie.Ctx pc = (com.dan.lituya.scene.Regie.Ctx) java.lang.reflect.Proxy.newProxyInstance(Pruefung.class.getClassLoader(), new Class<?>[]{com.dan.lituya.scene.Regie.Ctx.class},
                (o, m, args) -> { if (m.getName().equals("ready")) return ++bereit[0] > 3; log.add(m.getName()); return null; });
        com.dan.lituya.scene.Regie rg = new com.dan.lituya.scene.Regie();
        rg.start(pb, pc);
        for (int i = 0; i < 10 && rg.running(); i++) rg.step(1, pc);
        check(log.equals(java.util.List.of("time", "capture", "sheet")) && bereit[0] == 4 && Math.abs(rg.waitedTotal() - 3) < 1e-9 && !rg.running(),
                f("warte: drei Schritte gewartet (%.0f s), dann Foto und Blatt; Stichworte %s", rg.waitedTotal(), log));
        boolean fehlerErkannt = false;
        try { com.dan.lituya.scene.Regie.parse("Regiebuch: X\n0:00 fliegen hoch\n"); } catch (IllegalArgumentException e) { fehlerErkannt = e.getMessage().startsWith("Zeile 2"); }
        check(fehlerErkannt, "unbekannter Befehl wird mit der Zeile gemeldet");
        // die fünf Bücher: lesen, ohne Bild durchspielen, jeden Namen auflösen
        com.dan.lituya.scene.SichtNetz sn = com.dan.lituya.scene.SichtNetz.da(daten) ? com.dan.lituya.scene.SichtNetz.lesen(daten) : null;
        if (sn == null) hinweis("daten/netz/sicht fehlt: Namen aus LB_NET_SICHT und Fahrten A > B ungeprüft");
        java.util.List<com.dan.lituya.scene.Regie.Book> alle = com.dan.lituya.scene.Regie.all();
        check(alle.size() == 8, "acht Regiebücher (seit Werkbuch Welle W3 „Der Sturz“, W4 „Die Wand“, W5 „524 m“): " + alle);
        for (com.dan.lituya.scene.Regie.Book b : alle) {
            java.util.List<String> fehlt = new java.util.ArrayList<>(), befehle = new java.util.ArrayList<>();
            java.util.function.Predicate<String> punkt = n -> com.dan.lituya.camera.Ansichten.byName(n) != null || com.dan.lituya.scene.Site.byName(n) != null || com.dan.lituya.camera.Ansichten.versatz(n) != null || (sn == null ? true : sn.knoten(n) >= 0);
            com.dan.lituya.scene.Regie.Ctx c = (com.dan.lituya.scene.Regie.Ctx) java.lang.reflect.Proxy.newProxyInstance(Pruefung.class.getClassLoader(), new Class<?>[]{com.dan.lituya.scene.Regie.Ctx.class}, (o, m, args) -> {
                befehle.add(m.getName());
                switch (m.getName()) {
                    case "ready": return true;
                    case "view": for (String x : ((String) args[0]).split(">")) if (!punkt.test(x.strip())) fehlt.add("Ansicht " + x.strip()); break;
                    case "stueck": if (com.dan.lituya.camera.Schaustuecke.byName((String) args[0]) == null) fehlt.add("Schaustück " + args[0]); break;
                    case "fahrt": {
                        String a0 = (String) args[0], z0 = (String) args[1];
                        if (z0 == null) { if (!a0.toLowerCase(java.util.Locale.ROOT).startsWith("rund") && !a0.toLowerCase(java.util.Locale.ROOT).startsWith("kamera")) fehlt.add("Fahrt " + a0); }
                        else if (sn != null && com.dan.lituya.camera.SichtFahrt.zwischen(sn, a0, z0, 15, null) == null) fehlt.add("Fahrt " + a0 + " > " + z0);
                        break;
                    }
                    default: break;
                }
                return null;
            });
            com.dan.lituya.scene.Regie r = new com.dan.lituya.scene.Regie();
            r.start(b, c);
            double t = 0;
            while (r.running() && t < 3600) { r.step(0.5, c); t += 0.5; }
            boolean blattAmEnde = b.zeilen().get(b.zeilen().size() - 1).befehl().equals("blatt");
            String st = r.describe();
            // seit Werkbuch Welle W3: Abnahme-Clips (unter einer Minute) mit mindestens einem Foto
            boolean clip = b.length() < 60;
            check(fehlt.isEmpty() && !st.contains("Fehler") && (clip ? b.length() >= 10 && b.fotos() >= 1 : b.length() >= 180 && b.length() <= 6.5 * 60 && b.fotos() >= 5) && blattAmEnde && befehle.contains("capture"),
                    f("„%s“: %s, %d Stichworte, %d Fotos, Blatt am Ende%s", b.name(), com.dan.lituya.scene.Regie.zeit(b.length()), b.cues().size(), b.fotos(),
                            fehlt.isEmpty() ? "; alle Namen gefunden" : "; FEHLT: " + fehlt));
        }
    }

    static void klang18(File daten) throws Exception {
        teil("Klang (Phase 18): Laufzeit des Schalls, Ereignisse, Sprünge, Befunde 17a");
        SceneClock c = new SceneClock();
        Renderer r = new Renderer(c);
        com.dan.lituya.gl.Klang k = r.klang;
        k.offline = true;
        k.s.autoGain = false;
        // gegenüber dem Sturz (Schaustück 3): der Abbruch kommt nach der Laufzeit an
        com.dan.lituya.camera.Schaustuecke.Stueck st = com.dan.lituya.camera.Schaustuecke.byNr(3);
        double g = Math.max(0, r.groundHeight(st.x(), st.z()));
        r.camera.place(st.x(), g + st.auge(), st.z(), st.heading(), st.pitch());
        k.zuruecksetzen(1e9);
        c.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + 80_000);
        c.zeitstand = SceneClock.Zeitstand.WELLE;
        int rate = k.s.sampleRate();
        float[] ton = new float[(int) (50 * rate) * 2 + 4096];
        int n = 0;
        for (double t = 0; t < 50; t += 0.02) {
            c.setEpochMillis(c.epochMillis() + 20);
            k.feed(0.02);
            float[] b = k.s.render(0.02);
            System.arraycopy(b, 0, ton, n, Math.min(b.length, ton.length - n));
            n += b.length;
        }
        double[] imp = com.dan.lituya.scene.Site.fromUtm(com.dan.lituya.tools.Ereignis.wand()[0], com.dan.lituya.tools.Ereignis.wand()[1]);
        double d = Math.hypot(imp[0] - st.x(), imp[1] - st.z());
        double soll = com.dan.lituya.gl.Klang.STURZ_S + d / k.s.soundSpeed();
        double ist = com.dan.lituya.tools.Hoerproben.ankunft(java.util.Arrays.copyOf(ton, n), rate, com.dan.lituya.gl.Klang.STURZ_S - 80, 50) + 80;
        check(k.sturz == 1 && Math.abs(ist - soll) < 1.5, f("Sturz in %.1f km: am Ohr nach der Laufzeit bei %.1f s, hörbar ab %.1f s; %s", d / 1000, soll, ist, k.describe()));
        check(k.echos > 0, f("Echo an den Wänden: %d Quellen gespiegelt", k.echos));
        // ein Sprung über das Ereignis löst nichts aus
        k.zuruecksetzen(1e9);
        c.setEpochMillis(SceneClock.BEBEN.toEpochMilli() - 10_000);
        k.feed(0.02);
        c.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + 200_000);
        k.feed(0.02);
        k.feed(0.02);
        check(k.beben == 0 && k.sturz == 0, "Sprung der Uhr über Beben und Sturz: kein Ereignis (" + k.describe() + ")");
        // Ruhe vor dem Stoß: Möwen und eine Kalbung
        k.zuruecksetzen(5);
        c.setEpochMillis(SceneClock.BEBEN.toEpochMilli() - 900_000);
        c.zeitstand = SceneClock.Zeitstand.VOR;
        for (double t = 0; t < 30; t += 0.05) { c.setEpochMillis(c.epochMillis() + 50); k.feed(0.05); k.s.render(0.05); }
        check(k.kalbungen == 1 && k.moewen >= 1 && k.beben == 0, f("Abend vor Anker: %d Kalbung, %d Möwen in 30 s", k.kalbungen, k.moewen));
        check(java.util.Arrays.asList(com.dan.lituya.ui.ControlPanel.REIHENFOLGE).indexOf("KLANG") == java.util.Arrays.asList(com.dan.lituya.ui.ControlPanel.REIHENFOLGE).indexOf("WETTER") + 1,
                "Bedienfeld: Block KLANG nach WETTER");
        // Befunde 17a: Marken nicht im Kino und im Regiebuch, Auge über den Kronen
        r.showMarks = true;
        check(r.marksJetzt(), "Marken im Fenster wie geschaltet");
        com.dan.lituya.camera.Ansichten.Ansicht ms = com.dan.lituya.camera.Ansichten.byName("Mudslide Creek");
        double gm = r.stapelHoehe(ms.x(), ms.z());
        r.camera.place(ms.x(), gm + ms.eyeAboveGround(), ms.z(), ms.heading(), ms.pitch());
        boolean geh = r.ueberKronen();
        check(geh && r.camera.fy - gm >= Renderer.KRONEN - 0.01, f("Mudslide Creek: Auge von %.0f m auf %.0f m über dem Gelände gehoben", ms.eyeAboveGround(), r.camera.fy - gm));
        com.dan.lituya.camera.Ansichten.Ansicht ac = com.dan.lituya.camera.Ansichten.byName("Anchorage Cove");
        r.camera.place(ac.x(), ac.eyeAboveGround(), ac.z(), ac.heading(), ac.pitch());
        double y0 = r.camera.fy;
        check(!r.ueberKronen() && r.camera.fy == y0, "Anchorage Cove über dem Wasser: Auge bleibt (3 m)");
    }

    static void szene19(File daten) throws Exception {
        teil("Datenbank der Szene (Phase 19): db/szene, mitgelieferte Tabellen, Speicher, Sitzung");
        File sz = new File("db/szene");
        int anw = 0;
        boolean alle = true;
        for (String n : com.dan.lituya.tools.SzeneDb.SKRIPTE) {
            File f = new File(sz, n);
            if (!f.isFile()) { alle = false; continue; }
            anw += com.dan.lituya.tools.NetzDb.anweisungen(Files.readString(f.toPath(), StandardCharsets.UTF_8)).size();
        }
        check(alle && anw >= 55, f("db/szene: %d Skripte, %d Anweisungen, jede mit „/“ abgeschlossen", com.dan.lituya.tools.SzeneDb.SKRIPTE.length, anw));
        String tab = alle ? Files.readString(new File(sz, "01_belege.sql").toPath()) + Files.readString(new File(sz, "02_boote_laeufe.sql").toPath())
                + Files.readString(new File(sz, "03_regie_bilder.sql").toPath()) + Files.readString(new File(sz, "04_sitzung_texturen.sql").toPath()) : "";
        int da = 0;
        for (String t : com.dan.lituya.tools.SzeneDb.TABELLEN) if (tab.contains("CREATE TABLE " + t + " (")) da++;
        check(da == com.dan.lituya.tools.SzeneDb.TABELLEN.length && !tab.contains("AS IDENTITY") && !tab.contains("GENERATED"), f("%d Tabellen angelegt, Schlüssel aus Sequenz und Trigger (nie IDENTITY)", da));
        String ab = Files.readString(new File("db/netz/99_abbau.sql").toPath());
        check(ab.contains("'LB_V_BEFUND'") && ab.contains("'LB_SITZUNG')"), "db/netz/99_abbau.sql lässt Sichten und Raum-Metadaten von db/szene stehen");
        // mitgelieferte Tabellen
        java.util.List<String[]> q = com.dan.lituya.tools.SzeneDb.tsv("quellen.tsv"), b = com.dan.lituya.tools.SzeneDb.tsv("befunde.tsv"), z = com.dan.lituya.tools.SzeneDb.tsv("zeugen.tsv"),
                bo = com.dan.lituya.tools.SzeneDb.tsv("boote.tsv");
        java.util.Set<String> kurz = new java.util.HashSet<>(), keys = new java.util.HashSet<>();
        for (String[] r : q) kurz.add(r[0]);
        java.util.List<String> fehlt = new java.util.ArrayList<>();
        java.util.Map<String, Integer> arten = new java.util.TreeMap<>();
        int mitOrt = 0;
        for (String[] r : b) {
            if (r.length != 11) fehlt.add(r[0] + ": " + r.length + " Felder");
            if (!keys.add(r[0])) fehlt.add("doppelt " + r[0]);
            if (!java.util.List.of("belegt", "gemessen", "gerechnet", "gesetzt").contains(r[5])) fehlt.add(r[0] + ": Art " + r[5]);
            if (!r[7].isBlank()) { if (com.dan.lituya.tools.SzeneDb.ort(r[7]) == null) fehlt.add(r[0] + ": Ort " + r[7]); else mitOrt++; }
            for (String x : r[10].split(",")) if (!kurz.contains(x.split(":")[0].trim())) fehlt.add(r[0] + ": Quelle " + x);
            arten.merge(r[5], 1, Integer::sum);
        }
        for (String[] r : z) { if (com.dan.lituya.tools.SzeneDb.ort(r[5]) == null) fehlt.add("Zeuge " + r[0] + ": Ort " + r[5]); if (!kurz.contains(r[8].split(",")[0].split(":")[0])) fehlt.add("Zeuge " + r[0] + ": Quelle"); }
        check(q.size() >= 26 && kurz.size() == q.size(), f("quellen.tsv: %d Quellen (Werkbuch 2.9 und vier dazu), Kurzzeichen eindeutig", q.size()));
        check(fehlt.isEmpty() && b.size() >= 50, f("befunde.tsv: %d Befunde %s, %d mit Ort; jede Quelle in quellen.tsv%s", b.size(), arten, mitOrt, fehlt.isEmpty() ? "" : "; FEHLT: " + fehlt));
        check(z.size() >= 10 && bo.size() == 3, f("zeugen.tsv: %d Aussagen; boote.tsv: %d Boote", z.size(), bo.size()));
        // Kennzahlen eines vorgerechneten Laufs
        File kette = new File(new File(daten, "ereignis"), "kette");
        if (new File(kette, "max_g2.r32").isFile()) {
            com.dan.lituya.data.Speicher.Lauf l = com.dan.lituya.tools.LaufKennzahlen.lauf(kette);
            check(l.auflaufSporn() > 400 && l.auflaufSporn() < 620 && l.ankunftCenotaph() < l.ankunftAnchorage() && l.ankunftAnchorage() < l.dauerS(),
                    f("Kennzahlen „kette“: Sporn %.0f m (belegt 524), Ankunft Cenotaph %.0f s, Anchorage Cove %.0f s, Eingang %.0f s", l.auflaufSporn(), l.ankunftCenotaph(),
                            l.ankunftAnchorage(), l.ankunftEingang()));
        } else hinweis("daten/ereignis/kette ohne Raster: Kennzahlen ungeprüft (ant ereignis)");
        // Speicher ohne Datenbank: Sitzung, Lauf, Regiebuch, Bild
        File tmp = Files.createTempDirectory("lb_speicher").toFile();
        com.dan.lituya.data.DateiSpeicher ds = new com.dan.lituya.data.DateiSpeicher(tmp);
        SceneClock c = new SceneClock();
        Renderer r = new Renderer(c);
        r.camera.place(-5000, 40, 1200, 33, -4);
        c.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + 250_000);
        c.zeitstand = SceneClock.Zeitstand.WELLE;
        c.lapse = 5;
        r.showMarks = false;
        r.netzblick.netz = 2;
        r.klang.echo = false;
        r.echtzeit.volumen = 12e6;
        com.dan.lituya.data.Speicher.Sitzung s0 = com.dan.lituya.gl.Sitzungen.erfassen(r, "Probe");
        long id = ds.sitzungSpeichern(s0);
        SceneClock c2 = new SceneClock();
        Renderer r2 = new Renderer(c2);
        com.dan.lituya.data.Speicher.Sitzung s1 = ds.sitzung(id);
        int n = com.dan.lituya.gl.Sitzungen.herstellen(r2, s1);
        boolean gleich = Math.abs(r2.camera.fx + 5000) < 1e-6 && Math.abs(r2.camera.fyaw - 33) < 1e-6 && c2.zeitstand == SceneClock.Zeitstand.WELLE && c2.lapse == 5
                && Math.abs(c2.simSeconds() - 250) < 0.01 && !r2.showMarks && r2.netzblick.netz == 2 && !r2.klang.echo && r2.echtzeit.volumen == 12e6;
        check(gleich && n > 40 && ds.sitzungen().size() == 1, f("Sitzung in die Dateien und zurück: %d Werte, Kamera, Uhr, Zeitstand, Raffer, Marken, Netz, Klang, Echtzeit wie vorher", n));
        ds.laufSpeichern(new com.dan.lituya.data.Speicher.Lauf("Probe", "Echtzeit", "q", "Gilbert Inlet", 12e6, 0.5, "G1940", "KETTE", 105, null, null, 200, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, "echtzeit.volumen=1.2E7"));
        ds.regiebuchSpeichern(new com.dan.lituya.data.Speicher.Buch("Eigenes", com.dan.lituya.scene.Regie.text("nacht.txt"), false, null));
        java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(640, 360, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(im, "png", png);
        ds.bildSpeichern(new com.dan.lituya.data.Speicher.Bild("Foto", "docs/status/Probe.png", png.toByteArray(), com.dan.lituya.data.Bilder.vorschau(im), 640, 360, "9.7.1958 22:20",
                250, "WELLE", 345000, 6500000, 40, 33, -4, null, 60, "Probe"));
        check(ds.laeufe().get(0).volumen() == 12e6 && ds.regiebuecher().get(0).text().equals(com.dan.lituya.scene.Regie.text("nacht.txt")) && ds.bilder() == 1
                && ds.bildZeilen().get(0).length == 15, "Lauf, eigenes Regiebuch und Bild in daten/speicher und zurück");
        check(com.dan.lituya.data.Speicher.aktuell() instanceof com.dan.lituya.data.DateiSpeicher, "ohne Verbindung nimmt die App die Dateien: " + com.dan.lituya.data.Speicher.aktuell().wo());

        teil("Archiv und Hilfe (Phase 19a): Befunde als Marken, Zeugen als Untertitel, Galerie, Regiebuch-Editor");
        String s6 = Files.readString(new File(sz, "06_phase19a.sql").toPath(), StandardCharsets.UTF_8);
        check(com.dan.lituya.tools.SzeneDb.SKRIPTE.length == 7 && s6.contains("NAME VARCHAR2(200 CHAR)") && s6.contains("GRUND VARCHAR2(60 CHAR)"),
                "db/szene/06_phase19a.sql weitet LB_EREIGNIS_LAUF (NAME 200, GRUND 60 Zeichen), LB_SITZUNG und LB_SCHNAPPSCHUSS; Befund aus dem App-Test 19");
        java.util.List<com.dan.lituya.data.Speicher.Befund> mb = com.dan.lituya.data.Mitgeliefert.befunde();
        java.util.List<com.dan.lituya.data.Speicher.Zeuge> mz = com.dan.lituya.data.Mitgeliefert.zeugen();
        long mbOrt = mb.stream().filter(x -> !Double.isNaN(x.e())).count();
        check(mb.size() == b.size() && mz.size() == z.size() && mbOrt == mitOrt && mb.stream().allMatch(x -> !x.quellen().isBlank()),
                f("ohne Datenbank: %d Befunde (%d mit Ort) und %d Aussagen aus den mitgelieferten Tabellen, jede mit Quelle", mb.size(), mbOrt, mz.size()));
        com.dan.lituya.gl.ArchivSzene as = new com.dan.lituya.gl.ArchivSzene();
        as.setzen(mb, mz);
        com.dan.lituya.data.Speicher.Zeuge z0 = mz.stream().filter(x -> !Double.isNaN(x.vonS())).findFirst().orElse(null);
        boolean zeitOk = z0 != null && as.jetzt(z0.vonS() + 0.5, SceneClock.Zeitstand.WELLE).contains(z0) && !as.jetzt(z0.vonS() + 0.5, SceneClock.Zeitstand.HEUTE).contains(z0)
                && !as.jetzt(z0.vonS() - 30, SceneClock.Zeitstand.WELLE).contains(z0);
        check(zeitOk, "Untertitel: eine Aussage erscheint zu ihrer Zeit in der Nacht von 1958, nicht davor und nicht im Zeitstand heute" + (z0 == null ? "" : " („" + z0.name() + "“ ab " + z0.vonS() + " s)"));
        // Projektion und Klick auf einer gebauten Matrix: der Ort des Befunds liegt in der Mitte der Tafel
        com.dan.lituya.data.Speicher.Befund b0 = mb.stream().filter(x -> !Double.isNaN(x.e())).findFirst().orElseThrow();
        double[] p0 = com.dan.lituya.scene.Site.fromUtm(b0.e(), b0.n());
        float kx = 1f / 20000, ky = 1f / 1000;
        float[] vp = new float[16];
        vp[0] = kx; vp[5] = ky; vp[10] = 1; vp[15] = 1;
        vp[12] = (float) (-p0[0] * kx); vp[13] = (float) (-com.dan.lituya.gl.ArchivSzene.UEBER * ky);
        double[] eye = {p0[0], 300, p0[1]};
        double[] px = com.dan.lituya.gl.ArchivSzene.bild(vp, eye, b0.e(), b0.n(), com.dan.lituya.gl.ArchivSzene.UEBER, 800, 600);
        as.setzen(java.util.List.of(b0), mz);
        as.marken = true;
        com.dan.lituya.data.Speicher.Befund[] getroffen = {null};
        as.onPick = x -> getroffen[0] = x;
        java.awt.image.BufferedImage taf = as.malen(800, 600, vp, eye, (x, y) -> 0, 0, SceneClock.Zeitstand.WELLE, true, 0);
        boolean daneben = !as.pick(100, 100, 1600, 1200);
        boolean treffer = as.pick(800 + 6, 600 - 4, 1600, 1200);
        int farbe = taf.getRGB(400, 300);
        check(px != null && Math.abs(px[0] - 400) < 0.5 && Math.abs(px[1] - 300) < 0.5 && treffer && daneben && getroffen[0] == b0 && b0.schluessel().equals(as.gewaehlt)
                        && ((farbe >>> 24) & 0xff) > 200,
                f("Marke „%s“ auf (%.1f, %.1f) der Tafel 800 × 600, Klick auf der doppelt so großen Leinwand trifft sie, daneben nicht", b0.schluessel(), px == null ? -1 : px[0], px == null ? -1 : px[1]));
        // Regiebuch-Editor: die Prüfung nennt die Zeile
        String falsch = "Regiebuch: Probe\n0:00 zeit 09.07.1958 22:10 | Abend\n0:05 flieg hoch\n0:20 blatt\n";
        String meldung = "";
        try { com.dan.lituya.scene.Regie.parse(falsch); } catch (IllegalArgumentException e) { meldung = e.getMessage(); }
        com.dan.lituya.scene.Regie.Book gut = com.dan.lituya.scene.Regie.parse(falsch.replace("0:05 flieg hoch\n", "0:05 sage Hallo\n"));
        check(meldung.startsWith("Zeile 3:") && meldung.contains("flieg") && gut.cues().size() >= 2, "Regiebuch prüfen: der Fehler nennt die Zeile („" + meldung + "“), die berichtigte Fassung geht");
        // Galerie und Löschen in den Dateien
        java.util.List<com.dan.lituya.data.Speicher.BildKopf> gal = ds.bilderListe();
        byte[] voll = ds.bildDaten(gal.isEmpty() ? "x" : gal.get(0).datei());
        ds.sitzungLoeschen(id);
        ds.regiebuchLoeschen("Eigenes");
        check(gal.size() == 1 && gal.get(0).vorschau() != null && Math.abs(gal.get(0).e() - 345000) < 1e-6 && voll != null && voll.length == png.size()
                        && ds.sitzungen().isEmpty() && ds.regiebuecher().isEmpty(),
                "Galerie: Bild mit Vorschau, Ort der Kamera und vollem PNG; Sitzung und eigenes Regiebuch lassen sich löschen");
        String hilfe = com.dan.lituya.ui.Hilfe.markdown(java.util.List.of("## 12 Oberfläche", "| a | b |", "|---|---|", "| **x** | `y` |", "- eins"));
        check(hilfe.contains("<table><tr><td>a</td>") && hilfe.contains("<b>x</b>") && hilfe.contains("<code>y</code>") && hilfe.contains("<li>eins</li>"),
                "Hilfe: Kapitel des Werkbuchs als HTML (Tabellen, fett, Code, Listen)");
        schliff20();
    }

    /** Phase 20: Schliff (Symbol, Verknüpfung, Film, Auslagerung, Qualität, Dauerlauf), alles ohne Grafikkarte. */
    static void schliff20() throws Exception {
        teil("Schliff (Phase 20): Symbol, Verknüpfung, Film, Auslagerung, Qualitätsstufen, Dauerlauf");
        File daten = new File(System.getProperty("lituya.daten", "daten"));
        check(Renderer.PHASE == 20 && Renderer.VERSION.startsWith("1.0"), "Stand " + Renderer.VERSION + ", Phase " + Renderer.PHASE);
        // Symbol: PNGs im Jar, ICO mit sieben Größen
        int imJar = 0;
        for (int sz : com.dan.lituya.ui.AppIcon.SIZES) if (com.dan.lituya.ui.AppIcon.class.getResource("icon/lituya_" + sz + ".png") != null) imJar++;
        java.util.List<byte[]> pngs = new java.util.ArrayList<>();
        for (int sz : com.dan.lituya.ui.AppIcon.SIZES) { java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(com.dan.lituya.ui.AppIcon.paint(sz), "png", o); pngs.add(o.toByteArray()); }
        byte[] ico = com.dan.lituya.tools.Symbol.ico(com.dan.lituya.ui.AppIcon.SIZES, pngs);
        java.nio.ByteBuffer ib = java.nio.ByteBuffer.wrap(ico).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        boolean icoOk = ib.getShort(0) == 0 && ib.getShort(2) == 1 && ib.getShort(4) == com.dan.lituya.ui.AppIcon.SIZES.length;
        for (int k = 0; k < com.dan.lituya.ui.AppIcon.SIZES.length && icoOk; k++) {
            int off = ib.getInt(6 + 16 * k + 12);
            icoOk = (ico[off] & 0xff) == 0x89 && ico[off + 1] == 'P' && ico[off + 2] == 'N' && ico[off + 3] == 'G';
        }
        File icoDatei = new File("icon/LituyaBay1958.ico");
        check(imJar == com.dan.lituya.ui.AppIcon.SIZES.length && icoOk && icoDatei.isFile(),
                f("Symbol: %d PNGs im Jar, ICO mit %d Größen (16 bis 256, PNG), icon/LituyaBay1958.ico %s", imJar, com.dan.lituya.ui.AppIcon.SIZES.length, icoDatei.isFile() ? "da" : "FEHLT (ant symbol)"));
        // Verknüpfung und Ziele
        File ps = new File("tools/verknuepfung.ps1");
        String pst = ps.isFile() ? Files.readString(ps.toPath(), StandardCharsets.ISO_8859_1) : "";
        boolean ascii = !pst.isEmpty() && pst.chars().allMatch(ch -> ch < 128);
        String bx = new File("build.xml").isFile() ? Files.readString(new File("build.xml").toPath(), StandardCharsets.UTF_8) : "";
        java.util.List<String> ziele = new java.util.ArrayList<>();
        for (String z : java.util.List.of("symbol", "verknuepfung", "film", "auslagerung", "auslagerungsprobe", "qualitaet", "dauerlauf")) if (!bx.contains("<target name=\"" + z + "\"")) ziele.add(z);
        check(ascii && pst.contains("CreateShortcut") && pst.contains("IconLocation") && ziele.isEmpty(),
                "Verknüpfung: tools/verknuepfung.ps1 (nur ASCII, WScript.Shell mit Symbol); ant-Ziele der Phase 20 da" + (ziele.isEmpty() ? "" : "; FEHLEN: " + ziele));
        // Film: ohne ffmpeg PNG und WAV, mit ffmpeg ein kurzes MP4
        File ft = Files.createTempDirectory("lb_film").toFile();
        int rate = 48000;
        java.util.function.IntFunction<java.awt.image.BufferedImage> bild = k -> {
            java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = im.createGraphics();
            g.setColor(new java.awt.Color(20, 40, 60)); g.fillRect(0, 0, 320, 180);
            g.setColor(java.awt.Color.ORANGE); g.fillOval(k * 10, 60, 40, 40); g.dispose();
            return im;
        };
        float[] stueck = new float[2 * rate / 30];
        com.dan.lituya.tools.FilmSchreiber ohne = new com.dan.lituya.tools.FilmSchreiber(new File(ft, "ohne.mp4"), 320, 180, 30, rate, null, null, 20);
        for (int k = 0; k < 6; k++) { ohne.bild(bild.apply(k)); ohne.ton(stueck); }
        ohne.close();
        File[] pn = new File(ft, "ohne_bilder").listFiles();
        check(pn != null && pn.length == 6 && new File(ft, "ohne_ton.wav").length() > 44 && Math.abs(ohne.sekunden() - ohne.tonSekunden()) < 1e-9 && ohne.bericht.get(1).contains("ffmpeg -framerate 30"),
                f("Film ohne ffmpeg: %d PNG-Bilder und Ton (%.2f s), dazu die Zeile zum Nachholen", pn == null ? 0 : pn.length, ohne.tonSekunden()));
        String ff = com.dan.lituya.tools.FilmSchreiber.finden(System.getProperty("ffmpeg"));
        if (ff != null) {
            com.dan.lituya.tools.FilmSchreiber mit = new com.dan.lituya.tools.FilmSchreiber(new File(ft, "mit.mp4"), 320, 180, 30, rate, ff, "veryfast", 23);
            for (int k = 0; k < 30; k++) { mit.bild(bild.apply(k)); mit.ton(stueck); }
            mit.close();
            check(new File(ft, "mit.mp4").length() > 1000 && !new File(ft, "mit_bild.mp4").exists(), "Film mit ffmpeg (" + ff + "): " + mit.bericht.get(0));
        } else hinweis("ffmpeg nicht gefunden (PATH, -Dffmpeg=…): ant film schreibt dann PNG-Bilder und WAV");
        // Auslagerung: INHALT.txt, Regel 3.3, CSV, QUELLEN.md
        File dt = Files.createTempDirectory("lb_daten").toFile();
        Files.writeString(new File(dt, "a.txt").toPath(), "Lituya", StandardCharsets.UTF_8);
        Files.writeString(new File(dt, "b.txt").toPath(), "Bucht", StandardCharsets.UTF_8);
        String shaA = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("Lituya".getBytes(StandardCharsets.UTF_8)));
        Files.writeString(new File(dt, "INHALT.txt").toPath(), "# Probe\na.txt\t6\t" + shaA + "\nb.txt\t5\t" + shaA + "\nc.txt\t3\tx\n", StandardCharsets.UTF_8);
        com.dan.lituya.tools.Auslagerung.Stand st = com.dan.lituya.tools.Auslagerung.inhalt(dt, true);
        check(st.dateien() == 3 && st.fehlen() == 1 && st.falsch() == 1 && !st.ok(), f("Auslagerung: INHALT.txt mit Größe und SHA-256 (Probe: %d Dateien, %d fehlt, %d anders)", st.dateien(), st.fehlen(), st.falsch()));
        check(com.dan.lituya.tools.Auslagerung.regel33(new File("src")).isEmpty(), "Auslagerung: kein Laufzeitcode nennt download/ (wie Regel 3.3)");
        File dd = new File(daten, "INHALT.txt");
        if (dd.isFile()) {
            com.dan.lituya.tools.Auslagerung.Stand sd = com.dan.lituya.tools.Auslagerung.inhalt(daten, false);
            check(sd.ok(), f("daten/ gegen INHALT.txt (nur Größe): %d Dateien, %.2f GB%s", sd.dateien(), sd.bytes() / 1e9, sd.ok() ? "" : "; " + sd.meldungen().subList(0, Math.min(5, sd.meldungen().size()))));
        } else hinweis("daten/INHALT.txt fehlt: ant aufbereitung -Dschritte=inhalt");
        // Qualität und Dauerlauf
        String tab = com.dan.lituya.tools.QualitaetTabelle.tabelle(java.util.List.of("### Grundansicht · MITTEL", "     [java] 1920×1080 Mittel, 22:00: bestes Bild 10,20 ms, Mittel 12,50 ms (80 Bilder/s)",
                "### Grundansicht · HOCH", "1920×1080 Hoch, 22:00: bestes Bild 14,00 ms, Mittel 16,00 ms (63 Bilder/s)"));
        check(tab.contains("| Grundansicht | 12,50 ms (80) | 16,00 ms (63) |"), "Qualitätsstufen: Tabelle aus den Läufen von Tempo");
        java.util.List<double[]> pt = new java.util.ArrayList<>();
        for (int k = 0; k < 30; k++) pt.add(new double[]{k, 500 + 2 * k + (k % 2 == 0 ? 0.5 : -0.5)});
        check(Math.abs(com.dan.lituya.tools.Dauerlauf.steigung(pt) - 2) < 0.05, f("Dauerlauf: Steigung des Speichers aus den Minutenwerten (Probe 2 MB je Minute: %.3f)", com.dan.lituya.tools.Dauerlauf.steigung(pt)));
        File rd = new File("README.md");
        String rt = rd.isFile() ? Files.readString(rd.toPath(), StandardCharsets.UTF_8) : "";
        check(rt.contains("Phase 20") && rt.contains("ant film") && rt.contains("ant verknuepfung"), "README.md auf dem Stand von Phase 20 (Start, Verknüpfung, Film)");
    }
}
