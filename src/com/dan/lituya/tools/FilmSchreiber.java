package com.dan.lituya.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 20: schreibt einen Film aus Bildern und Ton. Mit ffmpeg gehen die Bilder roh (RGB) durch eine Röhre in ein
 * H.264-Video, der Ton als WAV daneben, am Ende werden beide zu MP4 (AAC) gemischt. Ohne ffmpeg landen die Bilder als
 * PNG in einem Ordner, dazu {@code ton.wav} und die Zeile für ffmpeg zum Nachholen. Ohne Grafikkarte prüfbar.
 */
public final class FilmSchreiber implements AutoCloseable {
    public final File ziel;
    public final int breite, hoehe, bildrate, rate;
    final String ffmpeg, preset;
    final int crf;
    final File ordner, bildMp4, tonWav, log;
    Process prozess;
    OutputStream rohr;
    final byte[] zeile;
    long bilder;
    boolean zu;
    float[] ton = new float[1 << 20];
    int tonN;
    public final List<String> bericht = new ArrayList<>();

    /**
     * @param ffmpeg Pfad zu ffmpeg oder null (dann PNG-Bilder)
     * @param preset x264-Voreinstellung (veryfast; slow für die beste Güte)
     */
    public FilmSchreiber(File ziel, int breite, int hoehe, int bildrate, int rate, String ffmpeg, String preset, int crf) throws IOException {
        this.ziel = ziel.getAbsoluteFile();
        this.breite = breite & ~1;
        this.hoehe = hoehe & ~1;
        this.bildrate = bildrate;
        this.rate = rate;
        this.ffmpeg = ffmpeg;
        this.preset = preset == null || preset.isBlank() ? "veryfast" : preset;
        this.crf = crf;
        File d = this.ziel.getParentFile();
        if (d != null && !d.isDirectory() && !d.mkdirs()) throw new IOException("Ordner " + d + " lässt sich nicht anlegen");
        String stamm = this.ziel.getName().replaceFirst("\\.[^.]+$", "");
        ordner = new File(d, stamm + "_bilder");
        bildMp4 = new File(d, stamm + "_bild.mp4");
        tonWav = new File(d, stamm + "_ton.wav");
        log = new File(d, stamm + "_ffmpeg.log");
        zeile = new byte[this.breite * 3];
        if (ffmpeg != null) {
            List<String> c = List.of(ffmpeg, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", this.breite + "x" + this.hoehe,
                    "-framerate", String.valueOf(bildrate), "-i", "-", "-c:v", "libx264", "-preset", this.preset, "-crf", String.valueOf(crf),
                    "-pix_fmt", "yuv420p", "-movflags", "+faststart", bildMp4.getPath());
            prozess = new ProcessBuilder(c).redirectErrorStream(true).redirectOutput(log).start();
            rohr = new BufferedOutputStream(prozess.getOutputStream(), 1 << 22);
        } else if (!ordner.isDirectory() && !ordner.mkdirs()) throw new IOException("Ordner " + ordner + " lässt sich nicht anlegen");
    }

    /** ffmpeg aus -Dffmpeg, FFMPEG, dem PATH oder üblichen Orten unter Windows; null, wenn keins da ist. */
    public static String finden(String vorgabe) {
        List<String> k = new ArrayList<>();
        if (vorgabe != null && !vorgabe.isBlank()) k.add(vorgabe);
        String env = System.getenv("FFMPEG");
        if (env != null && !env.isBlank()) k.add(env);
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String path = System.getenv("PATH");
        if (path != null) for (String p : path.split(File.pathSeparator)) k.add(new File(p, win ? "ffmpeg.exe" : "ffmpeg").getPath());
        if (win) {
            String lad = System.getenv("LOCALAPPDATA");
            k.add("C:\\ffmpeg\\bin\\ffmpeg.exe");
            k.add("C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe");
            if (lad != null) k.add(lad + "\\Microsoft\\WinGet\\Links\\ffmpeg.exe");
            k.add(System.getProperty("user.home") + "\\scoop\\shims\\ffmpeg.exe");
            k.add("C:\\ProgramData\\chocolatey\\bin\\ffmpeg.exe");
        }
        for (String s : k) if (new File(s).isFile()) return s;
        return null;
    }

    /** Ein Bild (wird auf breite × hoehe beschnitten oder schwarz ergänzt). */
    public void bild(BufferedImage im) throws IOException {
        if (rohr != null) {
            int[] px = new int[breite];
            for (int y = 0; y < hoehe; y++) {
                java.util.Arrays.fill(px, 0);
                if (y < im.getHeight()) im.getRGB(0, y, Math.min(breite, im.getWidth()), 1, px, 0, breite);
                for (int x = 0, i = 0; x < breite; x++) {
                    int p = px[x];
                    zeile[i++] = (byte) (p >> 16);
                    zeile[i++] = (byte) (p >> 8);
                    zeile[i++] = (byte) p;
                }
                rohr.write(zeile);
            }
        } else {
            ImageIO.write(im, "png", new File(ordner, String.format("bild_%06d.png", bilder + 1)));
        }
        bilder++;
    }

    /** Ton (Stereo, abwechselnd links und rechts, wie {@code Soundscape.render}). */
    public void ton(float[] b) {
        if (b == null) return;
        if (tonN + b.length > ton.length) ton = java.util.Arrays.copyOf(ton, Math.max(ton.length * 2, tonN + b.length));
        System.arraycopy(b, 0, ton, tonN, b.length);
        tonN += b.length;
    }

    public long bilder() { return bilder; }

    public double sekunden() { return bilder / (double) bildrate; }

    public double tonSekunden() { return tonN / 2.0 / rate; }

    /** Schließt ab: Ton schreiben, Video fertig, mischen. Gibt die fertige Datei (MP4 oder den Bilderordner). */
    @Override public void close() throws IOException {
        if (zu) return;
        zu = true;
        float[] t = java.util.Arrays.copyOf(ton, tonN);
        com.dan.soundscape.Wav.write(tonWav, t, rate);
        if (rohr == null) {
            String cmd = String.format(Locale.ROOT, "ffmpeg -framerate %d -i \"%s%sbild_%%06d.png\" -i \"%s\" -c:v libx264 -preset %s -crf %d -pix_fmt yuv420p -c:a aac -b:a 192k -shortest \"%s\"",
                    bildrate, ordner.getPath(), File.separator, tonWav.getPath(), preset, crf, ziel.getPath());
            bericht.add("ohne ffmpeg: " + bilder + " Bilder als PNG in " + ordner.getPath() + ", Ton in " + tonWav.getPath());
            bericht.add("nachholen mit: " + cmd);
            return;
        }
        rohr.close();
        rohr = null;
        int e = warten(prozess);
        if (e != 0 || !bildMp4.isFile()) throw new IOException("ffmpeg (Bild) endete mit " + e + ", siehe " + log.getPath());
        Process m = new ProcessBuilder(ffmpeg, "-y", "-loglevel", "error", "-i", bildMp4.getPath(), "-i", tonWav.getPath(), "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                "-shortest", "-movflags", "+faststart", ziel.getPath()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log)).start();
        m.getOutputStream().close();
        e = warten(m);
        if (e != 0 || !ziel.isFile()) throw new IOException("ffmpeg (Mischen) endete mit " + e + ", siehe " + log.getPath());
        if (!bildMp4.delete()) bildMp4.deleteOnExit();
        if (log.length() == 0 && !log.delete()) log.deleteOnExit();
        bericht.add(String.format(Locale.GERMAN, "Film: %s (%.1f MB), %d Bilder zu %d/s = %.1f s, Ton %.1f s · H.264 %s, CRF %d, AAC 192 kbit/s; Ton auch als %s",
                ziel.getPath(), ziel.length() / 1e6, bilder, bildrate, sekunden(), tonSekunden(), preset, crf, tonWav.getName()));
    }

    static int warten(Process p) throws IOException {
        try { return p.waitFor(); } catch (InterruptedException x) { Thread.currentThread().interrupt(); throw new IOException(x); }
    }
}
