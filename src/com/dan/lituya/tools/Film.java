package com.dan.lituya.tools;

import com.dan.lituya.gl.Quality;
import com.dan.lituya.gl.Renderer;
import com.dan.lituya.scene.Regie;
import com.dan.lituya.scene.SceneClock;
import com.dan.soundscape.Soundscape;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Phase 20: ein Regiebuch als Film ({@code ant film}). Die Szene läuft ohne Fenster im Kino mit festem Zeitschritt
 * (1/Bildrate), jedes Bild wird abgeholt, der Klang hört offline an der Kamera mit ({@code Klang.offline}, dieselben
 * Ereignisse wie in der App). Vor jedem Bild wartet der Film, bis Gelände und Vorrechnung um das Auge und die Zeit geladen
 * sind; so ruckelt nichts, auch wenn ein Bild länger als 1/30 s braucht. Während ein {@code warte} auf die Echtzeit oder
 * die Boote rechnet, steht die Uhr des Regiebuchs: diese Bilder fallen weg (im Kino sieht man dort ein Standbild).
 * <p>Schalter (-D…): {@code regiebuch} (Name, Pflicht), {@code film} (Datei, sonst {@code docs/film/<Name>.mp4}),
 * {@code breite}, {@code hoehe} (1920 × 1080), {@code bildrate} (24 wie im Kino, W9b, WF38 A; vorher 30), {@code qualitaet} (HOCH), {@code ffmpeg} (Pfad,
 * sonst PATH und übliche Orte), {@code preset} (veryfast, für die beste Güte slow), {@code crf} (18), {@code bis}
 * (höchstens so viele Sekunden Film, zum Probieren).
 */
public final class Film {
    private Film() { }

    static String opt(String k, String v) {
        String s = System.getProperty(k);
        return s == null || s.isBlank() ? v : s.strip();
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        String name = opt("regiebuch", a.length > 0 ? a[0] : "");
        Regie.Book b = Regie.byName(name);
        if (b == null) {
            System.err.println("FEHLER: kein Regiebuch „" + name + "“. Bekannt: " + Regie.all().stream().map(Regie.Book::name).toList());
            System.exit(1);
        }
        int w = Integer.parseInt(opt("breite", "1920")), h = Integer.parseInt(opt("hoehe", "1080")), fps = Integer.parseInt(opt("bildrate", "24"));
        Quality q = Quality.valueOf(opt("qualitaet", "HOCH"));
        double bis = Double.parseDouble(opt("bis", "0").replace(',', '.'));
        File ziel = new File(opt("film", "docs/film/" + DateiSpeicherName.von(b.name()) + ".mp4"));
        String ff = FilmSchreiber.finden(opt("ffmpeg", null));
        System.out.printf(Locale.GERMAN, "Film „%s“: %d × %d, %d Bilder/s, %s, %s%n", b.name(), w, h, fps, q.label, ff != null ? "ffmpeg " + ff : "ohne ffmpeg (PNG-Bilder)");

        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        d.setAutoSwapBufferMode(false);
        SceneClock clock = new SceneClock();
        clock.lapse = 0;
        Renderer r = new Renderer(clock);
        r.frozen = true;
        r.setQuality(q);
        r.autoScale = false;
        // W5: Brennweite des Films (mm, 0 = der feste Bildwinkel der App)
        try { double bw = Double.parseDouble(System.getProperty("brennweite", "0").replace(',', '.')); if (bw > 0) r.brennweite = bw; } catch (NumberFormatException e) { }
        r.showHud = false;
        r.film.fps = fps;   // W9b: jedes Bild des Films ein Filmbild (neues Korn, neuer Bildstand)
        r.photoDir = ziel.getAbsoluteFile().getParentFile();
        r.klang.offline = true;
        r.klang.s.quality = Soundscape.Quality.HIGH;
        d.addGLEventListener(r);
        d.display();
        long t0 = System.nanoTime();
        if (r.land.da()) { r.landAuge(); r.land.warten(300_000); }
        while (!r.wald.bereit() && System.nanoTime() - t0 < 300e9 && r.wald.an) { d.display(); Thread.sleep(100); }
        while ((!r.netzblick.fertig || !r.baeche.bereit()) && System.nanoTime() - t0 < 300e9) { d.display(); Thread.sleep(100); }
        while (!r.schleier.fertig() && System.nanoTime() - t0 < 300e9) { d.display(); Thread.sleep(50); }   // W5
        System.out.printf(Locale.GERMAN, "vorbereitet in %.0f s: %s · %s%n", (System.nanoTime() - t0) / 1e9, r.wald.status(), r.netzblick.stand());

        double dt = 1.0 / fps;
        int rate = r.klang.s.sampleRate();
        long gezeigt = 0, weg = 0, max = (long) (fps * 3600L);
        double laengstes = 0;
        long w0 = System.nanoTime();
        final BufferedImage[] img = new BufferedImage[1];
        try (FilmSchreiber fs = new FilmSchreiber(ziel, w, h, fps, rate, ff, opt("preset", "veryfast"), Integer.parseInt(opt("crf", "18")))) {
            r.kinoStart(b);
            r.frozen = true;   // der Film schaltet die Uhr selbst
            while ((r.regie.running() || r.kino.laeuft() || r.regieWantsFrame()) && gezeigt < max && (bis <= 0 || fs.sekunden() < bis)) {
                long f0 = System.nanoTime();
                Regie.Warte wt = r.regie.waiting();
                boolean rechnet = wt != null && (wt.was().equals("echtzeit") || wt.was().equals("boote"));
                r.step(dt);
                float[] ton = r.klang.s.render(dt);
                if (rechnet) { weg++; d.display(); Thread.sleep(5); continue; }
                if (r.land.da()) r.land.warten(20_000);
                if (r.wiedergabe.an() && clock.simSeconds() > -60 && clock.simSeconds() < 3600) r.wiedergabe.warten(clock.simSeconds(), 20_000);
                d.display();
                d.invoke(true, x -> { img[0] = r.grab(x); return true; });
                fs.bild(img[0]);
                fs.ton(ton);
                laengstes = Math.max(laengstes, (System.nanoTime() - f0) / 1e6);
                if (++gezeigt % (fps * 30L) == 0)
                    System.out.printf(Locale.GERMAN, "  %5.0f s Film in %4.0f s · %s%n", fs.sekunden(), (System.nanoTime() - w0) / 1e9, r.regieLine());
            }
            r.regie.stop();
            fs.close();
            double wall = (System.nanoTime() - w0) / 1e9;
            System.out.printf(Locale.GERMAN, "Regiebuch „%s“ als Film: %,d Bilder (%.1f s), %,d Bilder beim Rechnen ausgelassen, Ton %.1f s; gerechnet in %.0f s (%.1f s je Filmsekunde, längstes Bild %.0f ms)%n",
                    b.name(), gezeigt, fs.sekunden(), weg, fs.tonSekunden(), wall, wall / Math.max(1e-9, fs.sekunden()), laengstes);
            for (String s : fs.bericht) System.out.println(s);
            System.out.println("Klang: " + r.klang.describe());
            if (r.error() != null) System.err.println("FEHLER: " + r.error());
            boolean ok = gezeigt > 0 && Math.abs(fs.sekunden() - fs.tonSekunden()) < 0.5 && r.error() == null && gezeigt < max;
            System.out.println(ok ? "=== ok" : "=== FEHLER");
            d.destroy();
            System.exit(ok ? 0 : 1);
        }
    }

    /** Dateiname aus dem Namen des Regiebuchs (ohne Leer- und Sonderzeichen). */
    static final class DateiSpeicherName {
        static String von(String n) {
            String s = n.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss");
            return s.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
        }
    }
}
