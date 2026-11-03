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
 * {@code regen} (mm/h); ab Phase 17 {@code fahrt} (rundflug, kamerafahrt) und {@code fahrtanteil} (0 … 1).
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
        if ("aus".equals(opt("netzblick"))) r.netzblick.bildImBild = false;
        if (rg != null && !rg.isBlank()) r.baeche.regen = Double.parseDouble(rg.replace(',', '.'));
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
                r.camera.place(x, Math.max(r.groundHeight(x, z), r.wiring.pegel) + up, z, hd, pt);
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
            r.land.auge(r.camera.eye[0], r.camera.eye[2]);
            boolean ok = r.land.warten(300_000);
            stellen.run();
            r.camera.snap();
            r.land.auge(r.camera.eye[0], r.camera.eye[2]);
            ok &= r.land.warten(300_000);
            System.out.printf(Locale.GERMAN, "Gelände %s in %.1f s%n", ok ? "geladen" : "NICHT geladen", (System.nanoTime() - t0) / 1e9);
        }
        // Phase 14a: den Wald abwarten (Rechnung im Hintergrund, dann Backen der Netze)
        if (!Boolean.getBoolean("lituya.ohneWald") && !"aus".equals(opt("wald"))) {
            long t0 = System.nanoTime();
            while (!r.wald.bereit() && System.nanoTime() - t0 < 300e9) { d.display(); Thread.sleep(100); }
            System.out.printf(Locale.GERMAN, "Wald %s in %.1f s: %s%n", r.wald.bereit() ? "bereit" : "NICHT bereit", (System.nanoTime() - t0) / 1e9, r.wald.status());
        } else r.wald.an = false;
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
        // Phase 16c: Netze und Bäche abwarten (im Hintergrund geladen)
        {
            long t0 = System.nanoTime();
            while ((!r.netzblick.fertig || !r.baeche.bereit()) && System.nanoTime() - t0 < 300e9) { d.display(); Thread.sleep(100); }
            System.out.printf(Locale.GERMAN, "Netze in %.1f s: %s · %s%n", (System.nanoTime() - t0) / 1e9, r.netzblick.stand(), r.baeche.status());
        }
        for (int i = 0; i < 20; i++) { r.step(1 / 60.0); d.display(); }
        double best = Double.MAX_VALUE, sum = 0;
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
        }
        if (r.error() != null) System.err.println("FEHLER: " + r.error());
        System.out.println(r.glInfo());
        System.out.println(r.compute() == null ? "Compute-Test: nicht gelaufen" : r.compute().line());
        System.out.printf(Locale.GERMAN, "%d×%d %s, %s: bestes Bild %.2f ms, Mittel %.2f ms (%.0f Bilder/s)%n", w, h, q.label, clock.label(), best, sum / frames, 1000 / (sum / frames));
        System.out.println("Durchgänge (ms): " + r.passTimes());
        for (String l : r.hudLines()) System.out.println("  " + l);
        if (png != null) {
            File dir = png.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            javax.imageio.ImageIO.write(img[0], "png", png);
            System.out.println("Bild: " + png.getPath());
        }
        d.destroy();
        System.exit(r.error() == null ? 0 : 1);
    }
}
