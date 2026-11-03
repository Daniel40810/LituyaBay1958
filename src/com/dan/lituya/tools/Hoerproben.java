package com.dan.lituya.tools;

import com.dan.lituya.camera.Ansichten;
import com.dan.lituya.camera.Schaustuecke;
import com.dan.lituya.gl.Klang;
import com.dan.lituya.gl.Renderer;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;
import com.dan.soundscape.Soundscape;
import com.dan.soundscape.Wav;
import com.dan.soundscape.demo.Analysis;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 18: die Hörproben als WAV mit Spektrogramm nach {@code docs/hoerproben}, ohne Grafikkarte. Die Uhr läuft in
 * Schritten von 20 ms Echtzeit, der Klang hört an der Kamera zu ({@link Klang} offline), ohne automatische Lautstärke;
 * zum Anhören wird jede Datei auf −1 dBFS Spitze angehoben (Angabe im Bericht). Im Bericht dazu, wann Beben, Sturz und
 * Welle am Ohr ankommen, gegen die Laufzeit des Schalls.
 * <ol>
 * <li><b>Anchorage Cove</b> (Auge 3 m, rund 11 km vom Sturz): von −20 s bis 7 min nach dem Stoß.</li>
 * <li><b>Cenotaph Island</b> (40 m): von −10 s bis 4 min 20 s.</li>
 * <li><b>Gegenüber dem Sturz</b> (Schaustück 3, 150 m über dem Wasser, 1,5 km): von 80 s bis 3 min 20 s.</li>
 * <li><b>Abend vor Anker</b> (vor dem Stoß, 90 s): Möwen, ein Gletscher kalbt.</li>
 * <li><b>Heute</b> (Sommerabend 2026, 90 s).</li>
 * </ol>
 * Aufruf: {@code Hoerproben [Ordner] [nur]}, etwa {@code docs/hoerproben sturz}.
 */
public final class Hoerproben {
    private Hoerproben() { }

    static final double DT = 0.02;
    static final List<String> bericht = new ArrayList<>();
    /** Je Probe {Soll, Ist} des Sturzes am Ohr (s nach dem Stoß), für die Prüfung. */
    public static final java.util.Map<String, double[]> einsatz = new java.util.LinkedHashMap<>();

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        File dir = new File(a.length > 0 ? a[0] : "docs/hoerproben");
        String nur = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        dir.mkdirs();
        SceneClock clock = new SceneClock();
        Renderer r = new Renderer(clock);
        File daten = new File(System.getProperty("lituya.daten", "daten"));
        r.netzblick.laden(daten, null);
        long t0 = System.nanoTime();
        while (!r.netzblick.fertig && System.nanoTime() - t0 < 120e9) Thread.sleep(100);
        System.out.println("Netze: " + r.netzblick.stand());
        Klang k = r.klang;
        k.offline = true;
        k.s.autoGain = false;
        k.lautstaerke = 1;
        k.s.quality = Soundscape.Quality.HIGH;
        if (nur.isEmpty() || nur.contains("anchorage")) probe(r, clock, dir, "1_Anchorage_Cove", Ansichten.byName("Anchorage Cove"), null, -20, 420,
                "Anchorage Cove, Auge 3 m über dem Wasser, wo die Badger lag");
        if (nur.isEmpty() || nur.contains("cenotaph")) probe(r, clock, dir, "2_Cenotaph_Island", Ansichten.byName("Cenotaph Island"), null, -10, 260,
                "Cenotaph Island, Auge 40 m");
        if (nur.isEmpty() || nur.contains("sturz")) probe(r, clock, dir, "3_Gegenueber_dem_Sturz", null, Schaustuecke.byNr(3), 80, 200,
                "Gegenüber dem Sturz (Schaustück 3), 150 m über dem Gilbert Inlet");
        if (nur.isEmpty() || nur.contains("abend")) probe(r, clock, dir, "4_Abend_vor_Anker", Ansichten.byName("Anchorage Cove"), null, -900, -810,
                "Abend vor Anker, 22:01 Uhr: Möwen, ein Gletscher kalbt");
        if (nur.isEmpty() || nur.contains("heute")) {
            clock.set(SceneClock.Zeitstand.HEUTE);
            probe(r, clock, dir, "5_Heute", Ansichten.byName("Anchorage Cove"), null, Double.NaN, 90, "Sommerabend 2026 in der Anchorage Cove");
        }
        Files.writeString(new File(dir, "hoerproben.txt").toPath(), String.join(System.lineSeparator(), bericht) + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("FERTIG");
        System.exit(0);
    }

    /** Eine Probe von t0 bis t1 (s nach dem Stoß; t0 NaN: ab jetzt, t1 = Länge). */
    static void probe(Renderer r, SceneClock c, File dir, String name, Ansichten.Ansicht an, Schaustuecke.Stueck st, double t0, double t1, String text) throws Exception {
        Klang k = r.klang;
        double[] auge;
        if (an != null) {
            double g = Math.max(0, nz(r, an.x(), an.z()));
            auge = new double[]{an.x(), g + an.eyeAboveGround(), an.z(), an.heading(), an.pitch()};
        } else {
            double g = Math.max(0, nz(r, st.x(), st.z()));
            auge = new double[]{st.x(), g + st.auge(), st.z(), st.heading(), st.pitch()};
        }
        r.camera.place(auge[0], auge[1], auge[2], auge[3], auge[4]);
        r.camera.snap();
        k.zuruecksetzen(name.startsWith("4_") ? 40 : 120);
        boolean jetzt = Double.isNaN(t0);
        if (!jetzt) {
            c.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + t0 * 1000);
            c.zeitstand = t0 < 0 ? SceneClock.Zeitstand.VOR : SceneClock.Zeitstand.WELLE;
        }
        double dauer = jetzt ? t1 : t1 - t0;
        int rate = k.s.sampleRate();
        float[] ton = new float[(int) Math.ceil(dauer / DT + 1) * (int) Math.round(rate * DT) * 2];
        int n = 0;
        long w0 = System.nanoTime();
        for (double t = 0; t < dauer; t += DT) {
            c.setEpochMillis(c.epochMillis() + DT * 1000);
            if (!jetzt && c.simSeconds() >= 0 && c.zeitstand == SceneClock.Zeitstand.VOR && t0 > -100) c.zeitstand = SceneClock.Zeitstand.WELLE;
            k.feed(DT);
            float[] b = k.s.render(DT);
            System.arraycopy(b, 0, ton, n, Math.min(b.length, ton.length - n));
            n += b.length;
        }
        float[] out = java.util.Arrays.copyOf(ton, Math.min(n, ton.length));
        double peak = 1e-9;
        for (float v : out) peak = Math.max(peak, Math.abs(v));
        double gain = Math.min(Math.pow(10, 24 / 20.0), Math.pow(10, -1 / 20.0) / peak);   // höchstens +24 dB
        for (int i = 0; i < out.length; i++) out[i] *= gain;
        File wav = new File(dir, name + ".wav");
        Wav.write(wav, out, rate);
        // Ankunft am Ohr: erste 50-ms-Spanne nach dem Ereignis, die 10 dB über dem Grund davor liegt
        double c0 = r.klang.s.soundSpeed();
        double[] imp = Site.fromUtm(Ereignis.wand()[0], Ereignis.wand()[1]);
        double dSturz = Math.hypot(imp[0] - auge[0], imp[1] - auge[2]);
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.GERMAN, "%s: %s · %.0f s, %d Hz, Anhebung %+.1f dB, gerechnet in %.0f s · %s", name, text, dauer, rate, 20 * Math.log10(gain),
                (System.nanoTime() - w0) / 1e9, k.describe()));
        if (!jetzt && t0 < Klang.AUFSCHLAG_S && t1 > Klang.AUFSCHLAG_S + dSturz / c0 + 2) {
            double soll = Klang.STURZ_S + dSturz / c0;
            double ist = ankunft(out, rate, Klang.STURZ_S - t0, t1 - t0) + t0;
            einsatz.put(name, new double[]{soll, ist});
            b.append(String.format(Locale.GERMAN, "%n    Sturz in %.1f km: Abbruch bei %.0f s, am Ohr nach der Laufzeit (%.0f m/s) bei %.1f s; hörbarer Einsatz ab %.1f s (Aufschlag bei %.0f s)", dSturz / 1000, Klang.STURZ_S, c0, soll, ist, Klang.AUFSCHLAG_S));
        }
        bericht.add(b.toString());
        System.out.println(b);
        spektrogramm(new File(dir, name + ".png"), out, rate, name.replace('_', ' ') + " · " + text);
    }

    static double nz(Renderer r, double x, double z) {
        double g = r.stapelHoehe(x, z);
        return Double.isNaN(g) ? 0 : g;
    }

    /** Zeit (s ab Beginn) der ersten 50-ms-Spanne nach von, die 10 dB über dem Mittel der 5 s davor liegt; NaN ohne. */
    public static double ankunft(float[] s, int rate, double von, double bis) {
        int w = rate / 20;
        double grund = 0;
        int a0 = Math.max(0, (int) ((von - 5) * rate)), a1 = Math.max(a0 + 1, (int) (von * rate));
        for (int i = a0; i < a1; i++) grund += s[2 * i] * s[2 * i];
        grund = Math.sqrt(grund / (a1 - a0)) + 1e-9;
        for (int i = (int) (von * rate); i + w < s.length / 2 && i < bis * rate; i += w) {
            double e = 0;
            for (int q = i; q < i + w; q++) e += s[2 * q] * s[2 * q];
            if (Math.sqrt(e / w) > grund * Math.pow(10, 10 / 20.0)) return i / (double) rate;
        }
        return Double.NaN;
    }

    static void spektrogramm(File f, float[] s, int rate, String titel) throws Exception {
        int W = 1600, H = 520;
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(11, 17, 24));
        g.fillRect(0, 0, W, H);
        Analysis.spectrogram(g, s, rate, 10, 40, W - 20, H - 70, -45);   // letzter Wert: Boden in dB, Bereich 70 dB, 30 Hz bis 16 kHz logarithmisch
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString(titel, 12, 24);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString(String.format(Locale.GERMAN, "%.0f s · 30 Hz bis 16 kHz (log.), 70 dB · Zeit nach rechts", s.length / 2.0 / rate), 12, H - 10);
        g.dispose();
        ImageIO.write(im, "png", f);
    }
}
