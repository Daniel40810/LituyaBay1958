package com.dan.lituya.tools;

import com.dan.lituya.camera.Schaustuecke;
import com.dan.lituya.gl.Echtzeit;
import com.dan.lituya.gl.Quality;
import com.dan.lituya.gl.Renderer;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.io.File;
import java.util.Locale;

/**
 * Abnahme der Echtzeit (Phase 13c, Werkbuch 14: „Echtzeit über 10 Bilder je Sekunde bei LOW“): die Szene ohne Fenster in
 * Full HD, die Uhr läuft in Echtzeit ab der Auslösung (93 s), die Welle rechnet hinter ihr her. Je Sekunde Wanduhr eine
 * Zeile: Bilder je Sekunde, Ereigniszeit, gerechnet bis, Zeit der Grafikkarte; am Ende das Mittel, der größte Rückstand und
 * zwei Standbilder.
 * <pre>
 * EchtzeitTempo [sekunden] [ort 0..4] [volumen Mio. m³] [grund 1940|heute] [pegel m] [png-vorsilbe]
 * </pre>
 */
public final class EchtzeitTempo {
    private EchtzeitTempo() { }

    public static void main(String[] a) throws Exception {
        double dauer = a.length > 0 ? Double.parseDouble(a[0]) : 90;
        int ort = a.length > 1 ? Integer.parseInt(a[1]) : 0;
        double vol = a.length > 2 ? Double.parseDouble(a[2].replace(',', '.')) : 30.6;
        boolean heute = a.length > 3 && a[3].equalsIgnoreCase("heute");
        double pegel = a.length > 4 ? Double.parseDouble(a[4].replace(',', '.')) : 0;
        String vor = a.length > 5 ? a[5] : "docs/status/Echtzeit_Phase13c";
        int w = 1920, h = 1080;

        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        d.setAutoSwapBufferMode(false);
        SceneClock clock = new SceneClock();
        Renderer r = new Renderer(clock);
        r.setQuality(Quality.MITTEL);
        r.autoScale = false;
        r.hudFull = true;
        Echtzeit ez = r.echtzeit;
        ez.ort = Echtzeit.ORTE.get(ort);
        ez.volumen = vol * 1e6;
        ez.grund = heute ? Echtzeit.Grund.HEUTE : Echtzeit.Grund.G1940;
        ez.pegel = pegel;
        r.wiring.pegel = pegel;
        // Blick: Schaustück 6 (die Front) für die Wand von 1958, sonst 3 km vor dem Ort, Auge 250 m
        Runnable stellen = () -> {
            if (ort == 0) r.placeStueck(Schaustuecke.byNr(6));
            else {
                Echtzeit.Ort o = ez.ort;
                double[] p = Site.fromUtm(o.e() + Math.sin(Math.toRadians(o.richtung())) * 3000, o.n() + Math.cos(Math.toRadians(o.richtung())) * 3000);
                r.camera.place(p[0], Math.max(r.groundHeight(p[0], p[1]), pegel) + 250, p[1], (o.richtung() + 180) % 360, 6);
            }
        };
        stellen.run();
        d.addGLEventListener(r);
        d.display();
        if (r.land.da()) {
            r.land.auge(r.camera.eye[0], r.camera.eye[2]);
            r.land.warten(300_000);
            stellen.run();
            r.camera.snap();
            r.land.auge(r.camera.eye[0], r.camera.eye[2]);
            r.land.warten(300_000);
        }
        long t0 = System.nanoTime();
        ez.neu();
        while (ez.baut()) Thread.sleep(100);
        System.out.printf(Locale.GERMAN, "Aufbau %.1f s: %s%n", (System.nanoTime() - t0) / 1e9, ez.zeile());
        clock.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + 93_000L);
        clock.zeitstand = SceneClock.Zeitstand.WELLE;
        clock.lapse = 1;
        r.wiedergabe.an(true);
        long start = System.nanoTime(), sek = start;
        int bilder = 0, alle = 0;
        double rueck = 0, minFps = Double.MAX_VALUE, sumFps = 0;
        int zeilen = 0;
        boolean bild1 = false;
        while ((System.nanoTime() - start) / 1e9 < dauer) {
            d.display();
            bilder++;
            alle++;
            double te = clock.simSeconds();
            if (!Double.isNaN(ez.bis()) && te > 105) rueck = Math.max(rueck, te - ez.bis());
            long now = System.nanoTime();
            if (now - sek >= 1_000_000_000L) {
                double fps = bilder / ((now - sek) / 1e9);
                if (te > 106) { minFps = Math.min(minFps, fps); sumFps += fps; zeilen++; }
                System.out.printf(Locale.GERMAN, "%5.1f s · t = %6.1f s · gerechnet bis %6.1f s · %5.1f Bilder/s · Grafikkarte %5.1f ms · %s%n",
                        (now - start) / 1e9, te, ez.bis(), fps, r.gpuMs(), r.passTimes());
                bilder = 0;
                sek = now;
            }
            if (!bild1 && te > 93 + dauer * 0.5) { bild1 = true; foto(d, r, new File(vor + "_" + ort + "_a.png")); }
        }
        foto(d, r, new File(vor + "_" + ort + "_b.png"));
        double wand = (System.nanoTime() - start) / 1e9;
        System.out.printf(Locale.GERMAN, "%nErgebnis: %d Bilder in %.0f s, Mittel %.1f Bilder/s ab der Welle, kleinstes %.1f · größter Rückstand der Rechnung %.1f s · %s · %s%n",
                alle, wand, zeilen > 0 ? sumFps / zeilen : 0, zeilen > 0 ? minFps : 0, rueck, ez.messung(), ez.zeile());
        System.out.println(zeilen > 0 && sumFps / zeilen >= 10 && rueck < 5 ? "ABNAHME: über 10 Bilder je Sekunde, die Rechnung hält mit der Uhr Schritt"
                : "ABNAHME: NICHT erreicht (Ziel über 10 Bilder je Sekunde und Rückstand unter 5 s)");
        for (String l : r.hudLines()) System.out.println("  " + l);
        d.destroy();
        System.exit(0);
    }

    static void foto(GLOffscreenAutoDrawable d, Renderer r, File png) throws Exception {
        File dir = png.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
        d.invoke(true, x -> { img[0] = r.grab(x); return true; });
        javax.imageio.ImageIO.write(img[0], "png", png);
        System.out.println("Bild: " + png.getPath());
    }
}
