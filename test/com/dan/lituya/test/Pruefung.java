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
            check(sk.length == 8 && anw > 20, f("db/netz: %d Skripte, %d Anweisungen, jede mit „/“ abgeschlossen", sk.length, anw));
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
