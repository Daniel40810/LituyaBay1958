package com.dan.lituya.tools;

import com.dan.lituya.gl.Quality;
import com.dan.lituya.gl.Renderer;
import com.dan.lituya.scene.Regie;
import com.dan.lituya.scene.SceneClock;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 20: der Dauerlauf ({@code ant dauerlauf}). Die Szene läuft ohne Fenster in Echtzeit (Zeitschritt = Wanduhr, wie
 * in der App) und spielt die mitgelieferten Regiebücher im Kino immer wieder nacheinander, mit Klang offline, Echtzeit,
 * Wiedergabe, Booten, Netzblick und Fotos. Jede Minute eine Zeile: Bilder je Sekunde, Java-Speicher nach GC, freier
 * Grafikspeicher (AMD: {@code GL_ATI_meminfo}), Fäden, Fehler. Am Ende das Urteil: kein Fehler, der Speicher wächst nach
 * der ersten Runde nicht (Gerade durch die Werte nach GC, unter 50 MB je Stunde), die Bildrate bricht nicht ein.
 * <p>Schalter (-D…): {@code minuten} (60; über Nacht 480), {@code breite}, {@code hoehe} (1920 × 1080), {@code qualitaet}
 * (MITTEL).
 */
public final class Dauerlauf {
    private Dauerlauf() { }

    static final int GL_TEXTURE_FREE_MEMORY_ATI = 0x87FC;

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        double minuten = Double.parseDouble(System.getProperty("minuten", "60").replace(',', '.'));
        int w = Integer.parseInt(System.getProperty("breite", "1920")), h = Integer.parseInt(System.getProperty("hoehe", "1080"));
        Quality q = Quality.valueOf(System.getProperty("qualitaet", "MITTEL"));
        System.out.printf(Locale.GERMAN, "=== dauerlauf LituyaBay1958 (Phase 20), %s: %.0f min, %d × %d, %s%n", java.time.LocalDateTime.now().withNano(0), minuten, w, h, q.label);
        System.setProperty("lituya.ohneSpeicher", "true");   // die Blätter und Fotos nicht ins Archiv (über Nacht wären es tausende)
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        d.setAutoSwapBufferMode(false);
        SceneClock clock = new SceneClock();
        Renderer r = new Renderer(clock);
        r.setQuality(q);
        r.autoScale = false;
        r.photoDir = new java.io.File(System.getProperty("java.io.tmpdir"), "lituya_dauerlauf");
        r.photoDir.mkdirs();
        r.klang.offline = true;
        d.addGLEventListener(r);
        d.display();
        boolean[] ati = {false};
        d.invoke(true, x -> { ati[0] = x.getGL().isExtensionAvailable("GL_ATI_meminfo"); return true; });
        System.out.println(r.glInfo());

        List<Regie.Book> buecher = Regie.all();
        List<double[]> speicher = new ArrayList<>();   // {Minute, MB nach GC}
        List<Double> fpsListe = new ArrayList<>();
        long start = System.nanoTime(), ende = start + (long) (minuten * 60e9), letzte = start, bilderMinute = 0, bilder = 0;
        int buch = 0, runden = 0, fehler = 0;
        String letzterFehler = null;
        long t = System.nanoTime();
        System.out.println("Minute · Bilder/s · Java MB nach GC · Grafik frei MB · Fäden · Regiebuch");
        while (System.nanoTime() < ende) {
            if (!r.regie.running() && !r.kino.laeuft() && !r.regieWantsFrame()) {
                Regie.Book b = buecher.get(buch);
                java.io.File[] alt = r.photoDir.listFiles();
                if (alt != null) for (java.io.File f : alt) if (f.isFile() && !f.delete()) f.deleteOnExit();
                r.kinoStart(b);
                buch = (buch + 1) % buecher.size();
                if (buch == 0) runden++;
            }
            long n = System.nanoTime();
            double dt = Math.min(0.1, (n - t) / 1e9);
            t = n;
            try {
                d.display();          // der Renderer schreitet selbst mit der Wanduhr fort (frozen = false, wie in der App)
                r.klang.s.render(dt);
            } catch (RuntimeException e) {
                fehler++;
                letzterFehler = e.toString();
            }
            if (r.error() != null && !r.error().equals(letzterFehler)) { fehler++; letzterFehler = r.error(); }
            bilder++;
            bilderMinute++;
            if (n - letzte >= 60e9) {
                double fps = bilderMinute / ((n - letzte) / 1e9);
                System.gc();
                double mb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1e6;
                int[] frei = {-1};
                if (ati[0]) d.invoke(true, x -> { int[] v = new int[4]; x.getGL().glGetIntegerv(GL_TEXTURE_FREE_MEMORY_ATI, v, 0); frei[0] = v[0] / 1024; return true; });
                double min = (n - start) / 60e9;
                speicher.add(new double[]{min, mb});
                fpsListe.add(fps);
                System.out.printf(Locale.GERMAN, "%6.0f · %6.1f · %7.0f · %7s · %4d · %s%n", min, fps, mb, frei[0] < 0 ? "–" : String.valueOf(frei[0]), Thread.activeCount(), r.regieLine());
                letzte = n;
                bilderMinute = 0;
            }
        }
        // Urteil
        double stunden = (System.nanoTime() - start) / 3.6e12;
        List<double[]> nachRunde = speicher.subList(Math.min(speicher.size(), Math.max(1, speicher.size() / 4)), speicher.size());
        double steigung = steigung(nachRunde) * 60;   // MB je Stunde
        double fpsAnfang = fpsListe.isEmpty() ? 0 : mittel(fpsListe.subList(0, Math.max(1, fpsListe.size() / 4)));
        double fpsEnde = fpsListe.isEmpty() ? 0 : mittel(fpsListe.subList(fpsListe.size() * 3 / 4, fpsListe.size()));
        boolean speicherOk = nachRunde.size() < 3 || steigung < 50, fpsOk = fpsEnde >= 0.8 * fpsAnfang, fehlerOk = fehler == 0;
        System.out.printf(Locale.GERMAN, "%,d Bilder in %.2f h, %d volle Runden über %d Regiebücher%n", bilder, stunden, runden, buecher.size());
        System.out.printf(Locale.GERMAN, "  %-7s Fehler: %d%s%n", fehlerOk ? "ok" : "FEHLER", fehler, letzterFehler == null ? "" : " (zuletzt " + letzterFehler + ")");
        System.out.printf(Locale.GERMAN, "  %-7s Java-Speicher nach dem ersten Viertel: %+.1f MB je Stunde (Grenze 50)%n", speicherOk ? "ok" : "FEHLER", steigung);
        System.out.printf(Locale.GERMAN, "  %-7s Bilder je Sekunde: erstes Viertel %.1f, letztes Viertel %.1f%n", fpsOk ? "ok" : "FEHLER", fpsAnfang, fpsEnde);
        System.out.println("  Klang: " + r.klang.describe());
        boolean ok = fehlerOk && speicherOk && fpsOk && stunden * 60 >= minuten * 0.95;
        System.out.println(ok ? "=== ok" : "=== FEHLER");
        d.destroy();
        System.exit(ok ? 0 : 1);
    }

    /** Steigung der Geraden durch (x, y) nach kleinsten Quadraten. */
    public static double steigung(List<double[]> p) {
        int n = p.size();
        if (n < 2) return 0;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (double[] v : p) { sx += v[0]; sy += v[1]; sxx += v[0] * v[0]; sxy += v[0] * v[1]; }
        double den = n * sxx - sx * sx;
        return Math.abs(den) < 1e-12 ? 0 : (n * sxy - sx * sy) / den;
    }

    static double mittel(List<Double> l) { return l.stream().mapToDouble(Double::doubleValue).average().orElse(0); }
}
