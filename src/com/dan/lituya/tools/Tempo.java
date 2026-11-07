package com.dan.lituya.tools;

import com.dan.lituya.camera.Ansichten;
import com.dan.lituya.gl.Quality;
import com.dan.lituya.gl.Renderer;
import com.dan.lituya.scene.SceneClock;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.io.File;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Tempo-Probe und Standbild ohne Fenster (aus Sylt und Timmendorf). Rechnet die Szene unsichtbar in fester Größe,
 * misst die Wanduhr je Bild bis glFinish und die Zeit jedes Durchgangs auf der Grafikkarte und schreibt auf Wunsch ein
 * PNG. So lässt sich jede Phase vorher und nachher vergleichen und im Leistungsbudget
 * eintragen (Werkbuch 15).
 * <pre>
 * Tempo [bilder] [breite] [höhe] [png|-] [NIEDRIG|MITTEL|HOCH|ULTRA] [uhrzeit] [datum JJJJ-MM-TT]
 * Tempo 120 1920 1080 docs/status/Status_Phase1.png MITTEL 22 1958-07-09
 * </pre>
 * Schalter (als -D… aus Ant oder als Umgebungsvariable in Großbuchstaben): {@code ansicht} = Name oder Taste einer
 * Ansicht (AnchorageCove, Eingang, CenotaphIsland, GilbertInlet, Sporn, MudslideCreek, Draufsicht, VonSee, Fern; leer:
 * die Grundansicht), {@code kamera} = x,z,Richtung,Neigung,Höhe über Wasser (frei stehend), {@code hud} = kurz|voll|aus,
 * {@code marken} = an|aus (in Phase 1 an), {@code meer} = aus, {@code pegel} = m über NAVD88 (von Hand); ab Phase 13b
 * {@code schaustueck} = Nummer oder Name (1 bis 7, Kamera und Uhr), {@code ereignis} = Sekunden nach dem ersten Stoß,
 * {@code lauf} = Lauf der Welle (kette, messwelle), {@code abspielen} = aus; ab Phase 14a {@code schaustueck} auch 10 und 11,
 * {@code wald} = aus; ab Phase 16c {@code netz} (welle, wasser, weg, sicht), {@code netzszene} = an, {@code netzblick} = aus,
 * {@code regen} (mm/h); ab Phase 17 {@code fahrt} (rundflug, kamerafahrt) und {@code fahrtanteil} (0 … 1); ab Phase 17a
 * {@code regiebuch} (Name; ganz durchspielen, Blatt nach docs/status) und {@code kino} = aus; ab Werkbuch Bild B0
 * {@code brennweite} (mm, Kleinbild) und {@code zeitstand} (vor, welle, danach, heute; nur der Stand, die Uhr kommt aus den Argumenten);
 * ab Werkbuch Welle W7 {@code wetter} (Lage für heute, etwa kuestennebel, regen; aus = ohne Wetter) und {@code wetterzeit} (Stunden in der Lage).
 */
public final class Tempo {
    private Tempo() { }

    private static String opt(String prop) {
        String v = System.getProperty(prop);
        return v != null ? v : System.getenv(prop.toUpperCase(Locale.ROOT));
    }

    public static void main(String[] a) throws Exception {
        int frames = a.length > 0 ? Integer.parseInt(a[0]) : 120;
        int w = a.length > 1 ? Integer.parseInt(a[1]) : 1920, h = a.length > 2 ? Integer.parseInt(a[2]) : 1080;
        File png = a.length > 3 && !a[3].equals("-") ? new File(a[3]) : null;
        Quality q = a.length > 4 ? Quality.valueOf(a[4]) : Quality.MITTEL;
        if (a.length > 0 && a[0].equals("--hilfe")) { System.out.println("Tempo [bilder] [breite] [höhe] [png|-] [stufe] [uhrzeit] [datum]"); return; }
        double hour = a.length > 5 ? Double.parseDouble(a[5].replace(',', '.')) : SceneClock.START_HOUR;
        LocalDate day = a.length > 6 && !a[6].equals("-") ? LocalDate.parse(a[6]) : SceneClock.START_DATE;

        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        d.setAutoSwapBufferMode(false);
        SceneClock clock = new SceneClock(day, hour);
        clock.lapse = 0;
        if ("aus".equals(opt("wald"))) System.setProperty("lituya.ohneWald", "true");
        Renderer r = new Renderer(clock);
        r.frozen = true;
        r.setQuality(q);
        r.autoScale = false;
        // Werkbuch Bild, B0: Brennweite und Zeitstand für die Bildbank
        String bw = opt("brennweite"), zst = opt("zeitstand");
        if (bw != null && !bw.isBlank()) r.brennweite = Double.parseDouble(bw.replace(',', '.'));
        if (zst != null && !zst.isBlank()) {
            String k = zst.trim().toLowerCase(Locale.ROOT);
            for (SceneClock.Zeitstand z : SceneClock.Zeitstand.values())
                if (z.name().toLowerCase(Locale.ROOT).equals(k) || z.label.equalsIgnoreCase(k)) clock.zeitstand = z;
        }
        // Werkbuch Welle W5: Schalter für Proben, „pfad=wert,…“ auf Felder des Renderers (etwa ereignisGl.vorhang=false,
        // wald.an=false, licht.an=false); Pfad über öffentliche oder paketweite Felder, Wert boolean, int oder double
        String sch = opt("schalter");
        if (sch != null && !sch.isBlank()) for (String kv : sch.split("[,;]")) schalter(r, kv.trim());
        String hud = opt("hud");
        if ("aus".equals(hud)) r.showHud = false;
        if ("voll".equals(hud)) r.hudFull = true;
        if ("aus".equals(opt("marken"))) r.showMarks = false;
        if ("an".equals(opt("marken"))) r.showMarks = true;
        if ("aus".equals(opt("meer"))) r.showMeer = false;
        String pg = opt("pegel");
        if (pg != null && !pg.isBlank()) r.wiring.pegel = Double.parseDouble(pg.replace(',', '.'));
        String an = opt("ansicht"), ka = opt("kamera"), sst = opt("schaustueck"), ez = opt("ereignis"), lauf = opt("lauf");
        if (lauf != null && !lauf.isBlank()) r.wiedergabe.welle(lauf);
        if ("aus".equals(opt("abspielen"))) r.wiedergabe.an(false);
        // Phase 16c: netz = welle|wasser|weg (Netzblick), netzszene = an (auch in der Szene), regen = mm/h (Bäche)
        String nz = opt("netz"), rg = opt("regen");
        if (nz != null && !nz.isBlank()) r.netzblick.netz = Math.max(0, java.util.Arrays.asList("welle", "wasser", "weg", "sicht").indexOf(nz.toLowerCase(Locale.ROOT)));
        if ("an".equals(opt("netzszene"))) r.netzblick.inSzene = true;
        // W0 (K6): der Netzblick ist von Haus aus aus; an mit netzblick=an oder wenn ein Netz gewählt ist (ant netzbilder, sichtbilder)
        String nbl = opt("netzblick");
        r.netzblick.bildImBild = "an".equals(nbl) || (nbl == null && nz != null && !nz.isBlank());
        if (rg != null && !rg.isBlank()) { r.baecheRegenHand = Double.parseDouble(rg.replace(',', '.')); r.baeche.regen = r.baecheRegenHand; }
        // Werkbuch Welle W7: wetter = Lage (klar, quellwolken, kuestennebel, regen, sturm; gilt „heute“), wetterzeit = Stunden in der
        // Lage (der Nebel ist dann so weit vorgestoßen), wetter=aus (ohne Wetter wie bis W6)
        // W7b (WF26): verschluss = Belichtungszeit des Regens (1/250 oder s), zeitlupe = Zeitmaß des Regens
        String vs = opt("verschluss"), zl = opt("zeitlupe");
        if (vs != null && !vs.isBlank()) r.wetterGl.verschluss = com.dan.lituya.scene.Regie.belichtung(vs);
        if (zl != null && !zl.isBlank()) r.wetterGl.zeitlupe = Math.max(1e-3, Math.min(1, Double.parseDouble(zl.replace(',', '.'))));
        // W9a: das Objektiv (com.dan.lens): objektiv = an | aus | Brennweite[_Blende] (mm, f/N), blende = f/N, fokus = auto |
        // Meter | Ort, lamellen = Zahl (0 rund), rundung = 0..1
        String ob = opt("objektiv"), obBl = opt("blende"), fo = opt("fokus"), la = opt("lamellen"), ru = opt("rundung");
        if (ob != null && !ob.isBlank()) r.objektivStellen(ob.replace('_', ' '));
        if (obBl != null && !obBl.isBlank()) { r.objektiv.fNumber = Double.parseDouble(obBl.trim().replaceFirst("^f/", "").replace(',', '.')); r.objektivAn = true; }
        if (la != null && !la.isBlank()) r.objektiv.aperture.blades = Integer.parseInt(la.trim());
        if (ru != null && !ru.isBlank()) r.objektiv.aperture.roundness = Double.parseDouble(ru.trim().replace(',', '.'));
        if (fo != null && !fo.isBlank()) {
            try { r.fokusStellen(fo.replace('_', ' ')); } catch (IllegalArgumentException x) { System.err.println("FEHLER: " + x.getMessage()); }
        }
        String wt = opt("wetter"), wtz = opt("wetterzeit");
        if ("aus".equals(wt)) r.wetter.an = false;
        else if (wt != null && !wt.isBlank()) {
            com.dan.lituya.scene.Wetterlage wl = com.dan.lituya.scene.Wetterlage.byName(wt);
            if (wl == null) System.err.println("FEHLER: keine Wetterlage „" + wt + "“");
            else r.wetter.heute = wl;
        }
        if (wtz != null && !wtz.isBlank()) r.wetter.seitStunden = Double.parseDouble(wtz.replace(',', '.'));
        com.dan.lituya.camera.Schaustuecke.Stueck stueck = com.dan.lituya.camera.Schaustuecke.byName(sst);
        if (sst != null && !sst.isBlank() && stueck == null) System.err.println("FEHLER: kein Schaustück „" + sst + "“");
        Runnable stellen = () -> {
            if (stueck != null) {
                r.placeStueck(stueck);
                clock.lapse = 0;
            }
            if (ez != null && !ez.isBlank()) {
                clock.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + Double.parseDouble(ez.replace(',', '.')) * 1000);
                clock.zeitstand = SceneClock.Zeitstand.WELLE;
            }
            if (stueck != null) return;
            if (ka != null && !ka.isBlank()) {
                String[] p = ka.split(",");
                double x = Double.parseDouble(p[0]), z = Double.parseDouble(p[1]);
                double hd = p.length > 2 ? Double.parseDouble(p[2]) : 0, pt = p.length > 3 ? Double.parseDouble(p[3]) : 10, up = p.length > 4 ? Double.parseDouble(p[4]) : 2;
                // W0: sechster Wert = Auge absolut (m über NAVD88)
                double y = p.length > 5 ? Double.parseDouble(p[5]) : Math.max(r.groundHeight(x, z), r.wiring.pegel) + up;
                r.camera.place(x, y, z, hd, pt);
            } else if (an != null && !an.isBlank()) {
                Ansichten.Ansicht v = Ansichten.byName(an);
                if (v == null) System.err.println("FEHLER: keine Ansicht „" + an + "“");
                else r.placeView(v);
            }
        };
        stellen.run();
        d.addGLEventListener(r);
        d.display();   // init
        // Phase 4: das Gelände um das Auge laden, dann die Ansicht noch einmal stellen (jetzt mit Boden)
        if (r.land.da()) {
            long t0 = System.nanoTime();
            r.landAuge();
            boolean ok = r.land.warten(300_000);
            stellen.run();
            r.camera.snap();
            r.landAuge();
            ok &= r.land.warten(300_000);
            System.out.printf(Locale.GERMAN, "Gelände %s in %.1f s%n", ok ? "geladen" : "NICHT geladen", (System.nanoTime() - t0) / 1e9);
        }
        // Phase 14a: den Wald abwarten (Rechnung im Hintergrund, dann Backen der Netze)
        if (!Boolean.getBoolean("lituya.ohneWald") && !"aus".equals(opt("wald"))) {
            long t0 = System.nanoTime();
            while (!r.wald.bereit() && System.nanoTime() - t0 < 300e9) { d.display(); Thread.sleep(100); }
            System.out.printf(Locale.GERMAN, "Wald %s in %.1f s: %s%n", r.wald.bereit() ? "bereit" : "NICHT bereit", (System.nanoTime() - t0) / 1e9, r.wald.status());
        } else r.wald.an = false;
        // W5: die Schleier im Hintergrund abwarten (sonst lief ihre Rechnung während der gemessenen Bilder mit)
        { long t0 = System.nanoTime(); d.display(); while (!r.schleier.fertig() && System.nanoTime() - t0 < 60e9) { d.display(); Thread.sleep(50); } }
        // W1: Himmel und Horizontkarten abwarten (Höhen im Hintergrund, dann ein Rechenaufruf)
        if (r.licht.an && !"aus".equals(opt("licht"))) {
            long t0 = System.nanoTime();
            while (!r.licht.schattenBereit() && System.nanoTime() - t0 < 180e9) { d.display(); Thread.sleep(100); }
            System.out.printf(Locale.GERMAN, "Licht %s in %.1f s: %s%n", r.licht.schattenBereit() ? "bereit" : "NICHT bereit", (System.nanoTime() - t0) / 1e9, r.licht.status());
        } else r.licht.an = false;
        // Phase 13b: die Bilder der Vorrechnung um die Zeit abwarten
        if (r.wiedergabe.an() && clock.simSeconds() > -60 && clock.simSeconds() < 3600) {
            long t0 = System.nanoTime();
            boolean ok = r.wiedergabe.warten(clock.simSeconds(), 300_000);
            System.out.printf(Locale.GERMAN, "Vorrechnung um t = %.1f s %s in %.1f s: %s%n", clock.simSeconds(), ok ? "geladen" : "NICHT geladen", (System.nanoTime() - t0) / 1e9, r.wiedergabe.zeile());
        }
        // Phase 17: fahrt = rundflug|kamerafahrt, fahrtanteil = 0 … 1 (wo auf der Fahrt das Bild steht)
        String fa = opt("fahrt");
        if (fa != null && !fa.isBlank()) {
            String welche = fa.toLowerCase(Locale.ROOT).startsWith("k") ? com.dan.lituya.camera.SichtFahrt.KAMERAFAHRT : com.dan.lituya.camera.SichtFahrt.RUNDFLUG;
            System.out.println("Fahrt: " + r.sichtFahrt(welche, 15));
            com.dan.lituya.camera.Fahrt f = r.camera.fahrt;
            String anteil = opt("fahrtanteil");
            if (f != null && anteil != null && !anteil.isBlank()) {
                f.u = Double.parseDouble(anteil.replace(',', '.'));
                double[] fp = f.pose(f.u);
                r.camera.place(fp[0], fp[1], fp[2], fp[3], fp[4]);
                r.land.auge(fp[0], fp[2]);
                r.land.warten(300_000);
            }
        }
        // W7: Wolken am Gelände (Strömungskarte aus dem groben Gitter), Karte des Seenebels und den Himmel nach der Sicht abwarten
        if (r.wetter.an) {
            long t0 = System.nanoTime();
            r.step(1 / 60.0);   // erst ein Schritt: die Regie setzt die Lage, der Himmel beginnt mit der Sicht der Lage
            d.display();
            d.display();
            while (!r.wetterBereit() && System.nanoTime() - t0 < 120e9) { r.step(1 / 60.0); d.display(); Thread.sleep(50); }
            System.out.printf(Locale.GERMAN, "Wetter %s in %.1f s: %s%n", r.wetterBereit() ? "bereit" : "NICHT bereit", (System.nanoTime() - t0) / 1e9, r.wetterGl.info());
        }
        // Phase 16c: Netze und Bäche abwarten (im Hintergrund geladen)
        {
            long t0 = System.nanoTime();
            while ((!r.netzblick.fertig || !r.baeche.bereit()) && System.nanoTime() - t0 < 300e9) { d.display(); Thread.sleep(100); }
            System.out.printf(Locale.GERMAN, "Netze in %.1f s: %s · %s%n", (System.nanoTime() - t0) / 1e9, r.netzblick.stand(), r.baeche.status());
        }
        for (int i = 0; i < 20; i++) { r.step(1 / 60.0); d.display(); }
        // W5: die Grafikkarte die 20 Vorlaufbilder abarbeiten lassen. Ohne das wartete das erste gemessene Bild auf alle
        // (500–900 ms seit W5, als die Bilder teurer wurden) und hob das Mittel von 30 Bildern um 15–30 ms über das Übliche
        d.getContext().makeCurrent();
        d.getGL().glFinish();
        d.getContext().release();
        // Runde 10: last = Sekunden: vor der Messung so lange ohne Pause rechnen (Probe zur Wärme des Rechners)
        String last = opt("last");
        if (last != null && !last.isBlank()) {
            long l0 = System.nanoTime(), ln = 0;
            double ls = Double.parseDouble(last.trim().replace(',', '.'));
            while (System.nanoTime() - l0 < ls * 1e9) {
                r.step(1 / 60.0);
                d.display();
                if (++ln % 30 == 0) { d.getContext().makeCurrent(); d.getGL().glFinish(); d.getContext().release(); }
            }
            System.out.printf(Locale.GERMAN, "Last: %.0f s, %d Bilder, bis %s%n", ls, ln, java.time.LocalTime.now().withNano(0));
        }
        // Phase 17a: regiebuch = Name (ganz durchspielen, 30 Bilder je Sekunde Regiezeit), kino = aus (im Fenster statt im Kino)
        String rbn = opt("regiebuch");
        if (rbn != null && !rbn.isBlank()) {
            com.dan.lituya.scene.Regie.Book b = com.dan.lituya.scene.Regie.byName(rbn);
            if (b == null) { System.err.println("FEHLER: kein Regiebuch „" + rbn + "“"); d.destroy(); System.exit(1); }
            boolean imKino = !"aus".equals(opt("kino"));
            if (png != null && png.getAbsoluteFile().getParentFile() != null) r.photoDir = png.getAbsoluteFile().getParentFile();
            if (imKino) r.kinoStart(b); else r.regieStart(b);
            r.frozen = true;   // Tempo schaltet die Uhr selbst (kinoStart und regieStart lösen sie)
            long t0 = System.nanoTime(), n = 0, max = 30L * 60 * 20;
            double dt = 1 / 30.0, worst = 0;
            while ((r.regie.running() || r.kino.laeuft() || r.regieWantsFrame()) && n < max) {
                long f0 = System.nanoTime();
                r.step(dt);
                d.display();
                worst = Math.max(worst, (System.nanoTime() - f0) / 1e6);
                if (++n % 900 == 0) System.out.printf(Locale.GERMAN, "  %5.0f s Regiezeit · %s%n", n * dt, r.regieLine());
            }
            double wall = (System.nanoTime() - t0) / 1e9;
            System.out.printf(Locale.GERMAN, "Regiebuch „%s“ %s: %,d Bilder (%.0f s Regiezeit, davon %.0f s gewartet) in %.0f s, im Mittel %.1f ms je Bild, längstes %.0f ms%n",
                    b.name(), imKino ? "im Kino" : "im Fenster", n, n * dt, r.regie.waitedTotal(), wall, wall * 1000 / Math.max(1, n), worst);
            System.out.println("  " + r.regieLine());
            System.out.println("Blatt: " + (r.lastSheet != null ? r.lastSheet.getPath() : "keins"));
            if (r.error() != null) System.err.println("FEHLER: " + r.error());
            d.destroy();
            System.exit(n >= max || r.lastSheet == null ? 1 : 0);
        }
        double best = Double.MAX_VALUE, sum = 0;
        StringBuilder zeiten = new StringBuilder();   // W5: jede Bildzeit, um Spitzen zu finden
        double[] alle = new double[frames];   // Runde 5: Median (die Radeon streut von Bild zu Bild um ±10 ms)
        for (int i = 0; i < frames; i++) {
            long t0 = System.nanoTime();
            r.step(1 / 60.0);
            d.display();
            d.getContext().makeCurrent();
            d.getGL().glFinish();
            d.getContext().release();
            double ms = (System.nanoTime() - t0) / 1e6;
            best = Math.min(best, ms);
            sum += ms;
            alle[i] = ms;
            zeiten.append(String.format(Locale.GERMAN, " %.0f", ms));
        }
        if (r.error() != null) System.err.println("FEHLER: " + r.error());
        System.out.println(r.glInfo());
        System.out.println(r.compute() == null ? "Compute-Test: nicht gelaufen" : r.compute().line());
        System.out.printf(Locale.GERMAN, "%d×%d %s, %s: bestes Bild %.2f ms, Mittel %.2f ms (%.0f Bilder/s)%n", w, h, q.label, clock.label(), best, sum / frames, 1000 / (sum / frames));
        System.out.println("Durchgänge (ms): " + r.passTimes());
        System.out.println("Prozessor je Durchgang (ms): " + r.passCpuTimes());
        System.out.println("Bildzeiten (ms):" + zeiten);
        java.util.Arrays.sort(alle);
        System.out.printf(Locale.GERMAN, "Median %.2f ms%n", frames == 0 ? 0 : alle[frames / 2]);
        // Runde 6: im Fluss, wie im Programm: Bilder ohne Warten je Bild (Prozessor und Grafikkarte arbeiten gleichzeitig),
        // am Ende einmal glFinish; die Zeit durch die Zahl der Bilder. Oben wartet jedes Bild auf die Grafikkarte.
        // Runde 11: jfr = Datei: 300 Bilder im Fluss mit Java Flight Recorder (Profil), danach die Datei schreiben
        String jfr = opt("jfr");
        if (jfr != null && !jfr.isBlank() && frames > 0) {
            jdk.jfr.Recording rec = new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"));
            rec.start();
            long j0 = System.nanoTime();
            for (int i = 0; i < 300; i++) { r.step(1 / 60.0); d.display(); }
            d.getContext().makeCurrent();
            d.getGL().glFinish();
            d.getContext().release();
            rec.stop();
            java.nio.file.Path jp = java.nio.file.Path.of(jfr);
            if (jp.getParent() != null) java.nio.file.Files.createDirectories(jp.getParent());
            rec.dump(jp);
            rec.close();
            System.out.printf(Locale.GERMAN, "Profil: 300 Bilder in %.0f ms, %s%n", (System.nanoTime() - j0) / 1e6, jp);
        }
        // Runde 7: drei Blöcke hintereinander, der mittlere zählt (die Radeon streut von Lauf zu Lauf); dazu die Spanne
        if (frames > 0) {
            double[] bl = new double[3];
            for (int k = 0; k < 3; k++) {
                long f0 = System.nanoTime();
                for (int i = 0; i < frames; i++) { r.step(1 / 60.0); d.display(); }
                d.getContext().makeCurrent();
                d.getGL().glFinish();
                d.getContext().release();
                bl[k] = (System.nanoTime() - f0) / 1e6 / frames;
            }
            double[] so = bl.clone();
            java.util.Arrays.sort(so);
            System.out.printf(Locale.GERMAN, "Im Fluss %.2f ms je Bild (Mitte von 3 × %d Bildern ohne Warten; %.2f–%.2f) um %s; Blöcke %.2f %.2f %.2f%n", so[1], frames, so[0], so[2], java.time.LocalTime.now().withNano(0), bl[0], bl[1], bl[2]);
        }
        // W5: Licht am Ende des Laufs (für die Suche nach dem Rot der Kronen)
        com.dan.sky.Sky.State ls = r.licht.state();
        if (ls != null) {
            com.dan.forest.gl.ForestGl.Light wl = r.licht.waldLicht();
            System.out.printf(Locale.ROOT, "Licht: Sonne %.3f %.3f %.3f (Höhe %.2f°), pre %.3g, Sonne am Auge %.3g %.3g %.3g, Wald %.3g %.3g %.3g in %.0f m, Himmel %.3g %.3g %.3g, Mond %.3g %.3g %.3g (Höhe %.1f°)%n",
                    ls.sunDir[0], ls.sunDir[1], ls.sunDir[2], Math.toDegrees(Math.asin(ls.sunDir[1])), r.licht.pre(), ls.sunColor[0], ls.sunColor[1], ls.sunColor[2],
                    wl == null ? 0 : wl.sunColor[0], wl == null ? 0 : wl.sunColor[1], wl == null ? 0 : wl.sunColor[2], r.licht.waldHoehe,
                    wl == null ? 0 : wl.skyColor[0], wl == null ? 0 : wl.skyColor[1], wl == null ? 0 : wl.skyColor[2],
                    wl == null ? 0 : wl.moonColor[0], wl == null ? 0 : wl.moonColor[1], wl == null ? 0 : wl.moonColor[2], Math.toDegrees(Math.asin(ls.moonDir[1])));
        }
        for (String l : r.hudLines()) System.out.println("  " + l);
        // Runde 7: seitwaerts = „m,grad“: vor dem Bild in 10 Bildern um m seitwärts (Szene x) und grad gedreht (Probe des
        // Spiegelbilds, das nur jedes zweite Bild neu entsteht)
        String seit = opt("seitwaerts");   // („fahrt“ ist schon die Sichtfahrt)
        if (seit != null && !seit.isBlank()) {
            String[] fw = seit.split(",");
            double fm = Double.parseDouble(fw[0].trim()), fg = fw.length > 1 ? Double.parseDouble(fw[1].trim()) : 0;
            com.dan.lituya.camera.Camera c = r.camera;
            // dritter Wert: Takt des Spiegelbilds während der Fahrt; vorher ein Bild mit frischem Spiegelbild
            if (fw.length > 2) {
                r.spiegelTakt = 1;
                r.step(1 / 60.0);
                d.display();
                r.spiegelTakt = Integer.parseInt(fw[2].trim());
            }
            int fn = fw.length > 3 ? Integer.parseInt(fw[3].trim()) : 10;   // Runde 10: vierter Wert, Zahl der Bilder
            for (int i = 0; i < fn; i++) {
                c.place(c.fx + fm / fn, c.fy, c.fz, c.fyaw + fg / fn, c.fpitch);
                c.snap();
                r.step(1 / 60.0);
                d.display();
            }
            System.out.printf(Locale.GERMAN, "Fahrt: %.1f m seitwärts, %.1f° gedreht%n", fm, fg);
        }
        if (png != null) {
            File dir = png.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            // Phase 20: .jpg schreibt JPEG (für die Bilder des README), sonst PNG
            javax.imageio.ImageIO.write(img[0], png.getName().toLowerCase(Locale.ROOT).matches(".*\\.jpe?g") ? "jpg" : "png", png);
            System.out.println("Bild: " + png.getPath());
        }
        d.destroy();
        System.exit(r.error() == null ? 0 : 1);
    }

    /** W5: ein Schalter „a.b.c=wert“ auf ein Feld unter dem Renderer (Reflexion, nur für Proben und die Bildbank). */
    static void schalter(Object wurzel, String kv) {
        int eq = kv.indexOf('=');
        if (eq < 1) return;
        String[] pfad = kv.substring(0, eq).trim().split("\\.");
        String wert = kv.substring(eq + 1).trim();
        try {
            Object o = wurzel;
            for (int k = 0; k < pfad.length - 1; k++) o = feld(o.getClass(), pfad[k]).get(o);
            java.lang.reflect.Field f = feld(o.getClass(), pfad[pfad.length - 1]);
            Class<?> t = f.getType();
            Object v = t == boolean.class ? (Object) Boolean.valueOf(wert.equals("true") || wert.equals("an"))
                    : t == int.class ? (Object) Integer.valueOf(wert) : t == double.class ? (Object) Double.valueOf(wert.replace(',', '.'))
                    : t == float.class ? (Object) Float.valueOf(wert.replace(',', '.')) : wert;
            f.set(o, v);
            System.out.println("Schalter: " + kv);
        } catch (Exception e) {
            System.err.println("FEHLER: Schalter „" + kv + "“: " + e);
        }
    }

    static java.lang.reflect.Field feld(Class<?> c, String n) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass())
            try { java.lang.reflect.Field f = k.getDeclaredField(n); f.setAccessible(true); return f; } catch (NoSuchFieldException e) { }
        throw new NoSuchFieldException(c.getName() + "." + n);
    }
}
