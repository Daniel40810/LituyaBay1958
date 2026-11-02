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
                folge &= sk.t() >= vorher;
                vorher = sk.t();
                imKern &= Site.KERN.contains(sk.x(), sk.z());
            }
            check(com.dan.lituya.camera.Schaustuecke.ALLE.size() == 9 && folge && imKern, "Schaustücke 1 bis 7, 10 und 11 im Kern, in der Folge der Zeitleiste");
            com.dan.lituya.camera.Schaustuecke.Stueck s7 = com.dan.lituya.camera.Schaustuecke.byNr(7), s4 = com.dan.lituya.camera.Schaustuecke.byName("Die Wand aus Wasser");
            Camera c7 = new Camera();
            c7.waterLevel = 0;
            SceneClock k7 = new SceneClock();
            com.dan.lituya.camera.Schaustuecke.apply(s7, c7, k7, (x, zz) -> -60);
            c7.snap();
            check(c7.diving && c7.eye[1] < 0 && Math.abs(k7.simSeconds() - s7.t()) < 1e-6 && k7.lapse == s7.lapse(), f("„Unter Wasser“: Auge %.1f m, t = %.0f s, %s", c7.eye[1], k7.simSeconds(), s7.tempo()));
            check(s4 != null && s4.lapse() == 0.05 && s4.tempo().equals("Zeitlupe 1:20"), "„Die Wand aus Wasser“ in Zeitlupe 1:20");
            check(Renderer.PASSES.length == 8 && List.of(Renderer.PASSES).contains("Wald") && List.of(Renderer.PASSES).contains("Welle") && List.of(Renderer.PASSES).contains("Staub"), "Durchgänge: " + String.join(", ", Renderer.PASSES));
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
            check(sk.length == 9 && anw > 40, f("db/netz: %d Skripte, %d Anweisungen, jede mit „/“ abgeschlossen", sk.length, anw));
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
}
