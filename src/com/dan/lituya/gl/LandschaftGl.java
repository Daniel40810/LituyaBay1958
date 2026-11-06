package com.dan.lituya.gl;

import com.dan.lituya.scene.Site;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * Werkbuch Welle W6 „Landschaft“: die Hänge in den Farben aus Sentinel-2 statt sechs fester Farben, Fels mit Schichtung
 * aus {@code com.dan.cliff}, Schnee mit Form und die Gletscher mit Spalten (gelaende.frag, landschaft.glsl).
 * <p>Daten aus {@code daten/landschaft} ({@code tools/landschaft/landschaft.py}): Farbe im Kern (10 m) und in der Szene
 * (20 m) als sRGB mit Schnee im Alpha, Eis im Kern (10 m) und in der Szene (50 m) mit Spalten und geglätteter Oberfläche
 * (16 Bit), Normalen aus szene_5m im Kern (10 m) und in der Szene (20 m). Gelesen
 * beim Start, hochgeladen mit Mip-Stufen beim ersten Bild. Fehlen die Dateien, bleibt das Gelände wie
 * vorher (Farbe nach Höhe und Hang).
 * <p>Fels: {@code cliff.glsl} braucht eine 3D-Rauschtextur, die {@code CliffGl} fest auf Einheit 15 legt; dort liegt bei
 * Lituya die Flut der Vorrechnung (sampler2D). Nach {@code CliffGl.bind} wird sie deshalb auf {@link #UNIT_CLIFF_NOISE}
 * umgelegt.
 */
public final class LandschaftGl {
    public static final int UNIT_FK = 19, UNIT_FS = 20, UNIT_EK = 21, UNIT_ES = 22, UNIT_CLIFF_NOISE = 23, UNIT_CLIFF_MAP = 24, UNIT_RK = 25, UNIT_RS = 26;

    /** Ein Raster: Datei, NW-Ecke (UTM 8 N), Zelle, Größe, sRGB. */
    private record Bild(String datei, double e0, double n0, double zelle, int w, int h, boolean srgb, int kanaele, int bytes) {
        float[] rect() {
            double[] xz = Site.fromUtm(e0, n0);
            return new float[]{(float) xz[0], (float) xz[1], (float) (w * zelle), (float) (h * zelle)};
        }
    }

    private static final Bild[] BILDER = {
            new Bild("farbe_kern_10m.rgba", 340_000, 6_512_000, 10, 2400, 1800, true, 4, 4),
            new Bild("farbe_szene_20m.rgba", 322_000, 6_540_000, 20, 3300, 3000, true, 4, 4),
            new Bild("eis_kern_10m.rgba16", 340_000, 6_512_000, 10, 2400, 1800, false, 4, 8),
            new Bild("eis_szene_50m.rgba16", 322_000, 6_540_000, 50, 1320, 1200, false, 4, 8),
            // Runde 3: Normalen aus szene_5m für die Ferne (dort ist die Clipmap gröber)
            new Bild("relief_kern_10m.rgba", 340_000, 6_512_000, 10, 2400, 1800, false, 4, 4),
            new Bild("relief_szene_20m.rgba", 322_000, 6_540_000, 20, 3300, 3000, false, 4, 4)};
    private static final String[] NAMEN = {"uLandFK", "uLandFS", "uLandEK", "uLandES", "uLandRK", "uLandRS"};
    private static final int[] UNITS = {UNIT_FK, UNIT_FS, UNIT_EK, UNIT_ES, UNIT_RK, UNIT_RS};

    /** An/aus (Bedienfeld, Bildbank-Schlüssel {@code landschaft=0}). */
    public volatile boolean an = !Boolean.getBoolean("lituya.ohneLandschaft");
    /** Gewinn auf die Farbe aus Sentinel-2 (1: Reflexion wie gemessen) [G]. */
    public volatile double gewinn = Double.parseDouble(System.getProperty("lituya.landschaft.gewinn", "1.0"));
    /** Fels mit Schichtung, Klüften und Relief aus cliff (0/1). */
    public volatile boolean fels = true;
    /** Runde 2: gerippte Rinnen aus Rauschen (seit Runde 3 aus; die gemessene Normale trägt die Form). */
    public volatile boolean rippen = false;
    /** Spalten auf den Gletschern (0/1). */
    public volatile boolean spalten = true;
    /** Ausfransen der Schneekante (0: wie die 20-m-Zellen, 1: stark) [G]. */
    public volatile double kante = 0.6;

    private final ByteBuffer[] puffer = new ByteBuffer[BILDER.length];
    private final int[] tex = new int[BILDER.length];
    private volatile boolean gelesen;
    private volatile String status = "Landschaft: nicht geladen";
    private boolean hochgeladen;

    /** Fels aus com.dan.cliff: Gneis der Fairweather-Kette (Schiefer, Gneis, Amphibolit [G]). */
    public final com.dan.cliff.Cliff cliff = gneis();
    private final com.dan.cliff.gl.CliffGl cliffGl = new com.dan.cliff.gl.CliffGl();
    private boolean cliffBereit;

    private static com.dan.cliff.Cliff gneis() {
        com.dan.cliff.Cliff c = new com.dan.cliff.Cliff();
        c.quality = com.dan.cliff.Cliff.Quality.HIGH;
        c.rockSlopeDeg = 34;
        c.wallSlopeDeg = 52;
        c.foliationDipDeg = 35;          // Schieferung steil nach NO (Fairweather-Störung streicht NW–SO) [G]
        c.foliationDipDirDeg = 45;
        c.jointADeg = 135;               // Klüfte parallel und quer zur Störung [G]
        c.jointBDeg = 45;
        c.jointSpacing = 7;
        c.reliefAmp = 5.0;
        c.reliefScale = 30;
        c.gneissLight = new double[]{0.30, 0.29, 0.28};
        c.gneissDark = new double[]{0.12, 0.12, 0.125};
        c.feldspar = new double[]{0.33, 0.29, 0.26};
        c.lichenAmount = 0.35;
        c.mossAmount = 0.35;
        c.seepAmount = 0.4;
        c.feldsparAmount = 0.15;
        return c;
    }

    /** Liest die Dateien (rund 80 MB, einmal beim Start; danach liegen sie nur noch auf der Grafikkarte). */
    public void laden(File daten) {
        File d = new File(daten, "landschaft");
        try {
            for (int i = 0; i < BILDER.length; i++) puffer[i] = lesen(new File(d, BILDER[i].datei()), BILDER[i].w() * BILDER[i].h() * BILDER[i].bytes());
            gelesen = true;
            status = "Landschaft: Farbe aus Sentinel-2 (10/20 m), Eis (10/50 m), Relief aus szene_5m (10/20 m)";
        } catch (IOException e) {
            status = "Landschaft: ohne (" + e.getMessage() + "; python3 tools/landschaft/landschaft.py)";
        }
        System.out.println("LituyaBay1958: " + status);
    }

    /**
     * W6 Runde 3: wo Wald stehen darf (1) und wo Schnee oder Eis aus Sentinel-2 liegt (0), je Zelle eines Quadrats
     * (Szene x0, z0, Kante size, n × n Zellen, Zeilen nach Süden): Schnee aus dem Alpha der Farbe, Eis aus dem roten
     * Kanal von eis_*, im Kern 10 m, sonst die Szene (20 / 50 m), bilinear in der Zellmitte und vier Punkten um sie
     * (das Größte zählt). Übergang 0,15 bis 0,45 [G]. null, wenn die Dateien fehlen. Damit stehen am Rand des Walds
     * keine Bäume auf Schneefeldern (Flug zur Kette).
     */
    public static float[] waldMaske(File daten, double x0, double z0, double size, int n) {
        File d = new File(daten, "landschaft");
        try {
            ByteBuffer fk = lesen(new File(d, BILDER[0].datei()), BILDER[0].w() * BILDER[0].h() * 4);
            ByteBuffer fs = lesen(new File(d, BILDER[1].datei()), BILDER[1].w() * BILDER[1].h() * 4);
            ByteBuffer ek = lesen(new File(d, BILDER[2].datei()), BILDER[2].w() * BILDER[2].h() * 8);
            ByteBuffer es = lesen(new File(d, BILDER[3].datei()), BILDER[3].w() * BILDER[3].h() * 8);
            ek.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            es.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            float[] m = new float[n * n];
            double c = size / n;
            java.util.stream.IntStream.range(0, n).parallel().forEach(j -> {
                for (int i = 0; i < n; i++) {
                    double x = x0 + (i + 0.5) * c, z = z0 + (j + 0.5) * c, w = 0;
                    for (int k = 0; k < 5; k++) {
                        double px = x + (k == 1 ? -0.3 : k == 2 ? 0.3 : 0) * c, pz = z + (k == 3 ? -0.3 : k == 4 ? 0.3 : 0) * c;
                        double e = px + Site.E0, nn = Site.N0 - pz;
                        double sn = probe(BILDER[0], fk, e, nn, 3, false);
                        if (Double.isNaN(sn)) sn = probe(BILDER[1], fs, e, nn, 3, false);
                        double ic = probe(BILDER[2], ek, e, nn, 0, true);
                        if (Double.isNaN(ic)) ic = probe(BILDER[3], es, e, nn, 0, true);
                        w = Math.max(w, Math.max(Double.isNaN(sn) ? 0 : sn, Double.isNaN(ic) ? 0 : ic));
                    }
                    double t = Math.max(0, Math.min(1, (w - 0.15) / 0.30));
                    m[j * n + i] = (float) (1 - t * t * (3 - 2 * t));
                }
            });
            return m;
        } catch (IOException e) {
            System.out.println("LituyaBay1958: Waldmaske ohne Schnee (" + e.getMessage() + ")");
            return null;
        }
    }

    /** Kanal k eines Rasters bilinear am Ort (UTM) als 0..1; NaN außerhalb. */
    private static double probe(Bild b, ByteBuffer buf, double e, double n, int k, boolean sechzehn) {
        double fx = (e - b.e0()) / b.zelle() - 0.5, fy = (b.n0() - n) / b.zelle() - 0.5;
        if (fx < 0 || fy < 0 || fx >= b.w() - 1 || fy >= b.h() - 1) return Double.NaN;
        int ix = (int) fx, iy = (int) fy;
        double tx = fx - ix, ty = fy - iy;
        double a = kanal(b, buf, ix, iy, k, sechzehn), bb = kanal(b, buf, ix + 1, iy, k, sechzehn);
        double cc = kanal(b, buf, ix, iy + 1, k, sechzehn), dd = kanal(b, buf, ix + 1, iy + 1, k, sechzehn);
        return (a + (bb - a) * tx) * (1 - ty) + (cc + (dd - cc) * tx) * ty;
    }

    private static double kanal(Bild b, ByteBuffer buf, int i, int j, int k, boolean sechzehn) {
        int p = (j * b.w() + i) * 4 + k;
        return sechzehn ? (buf.getShort(2 * p) & 0xffff) / 65535.0 : (buf.get(p) & 0xff) / 255.0;
    }

    private static ByteBuffer lesen(File f, int n) throws IOException {
        if (!f.isFile()) throw new IOException(f.getName() + " fehlt");
        if (f.length() != n) throw new IOException(f.getName() + ": " + f.length() + " statt " + n + " Bytes");
        ByteBuffer b = ByteBuffer.allocateDirect(n);
        try (FileChannel ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
            while (b.hasRemaining()) if (ch.read(b) < 0) break;
        }
        b.flip();
        return b;
    }

    public String status() { return status; }

    public boolean bereit() { return hochgeladen; }

    /** Einmal im GL-Faden (Renderer.init). */
    public void init(GL4 gl) {
        try {
            cliffGl.init(gl);
            cliffBereit = true;
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Fels (cliff) nicht verfügbar: " + e.getMessage());
        }
    }

    private void hochladen(GL4 gl) {
        if (hochgeladen || !gelesen) return;
        gl.glGenTextures(tex.length, tex, 0);
        float[] an = new float[1];
        gl.glGetFloatv(0x84FF /* GL_MAX_TEXTURE_MAX_ANISOTROPY */, an, 0);
        for (int i = 0; i < tex.length; i++) {
            Bild b = BILDER[i];
            gl.glBindTexture(GL.GL_TEXTURE_2D, tex[i]);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, b.bytes() == 2 ? 2 : 4);
            if (b.bytes() == 8) gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_RGBA16, b.w(), b.h(), 0, GL.GL_RGBA, GL.GL_UNSIGNED_SHORT, puffer[i]);
            else if (b.kanaele() == 2) gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_RG8, b.w(), b.h(), 0, GL4.GL_RG, GL.GL_UNSIGNED_BYTE, puffer[i]);
            else gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, b.srgb() ? GL.GL_SRGB8_ALPHA8 : GL.GL_RGBA8, b.w(), b.h(), 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, puffer[i]);
            gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            if (an[0] > 1) gl.glTexParameterf(GL.GL_TEXTURE_2D, 0x84FE /* GL_TEXTURE_MAX_ANISOTROPY */, Math.min(8f, an[0]));
            puffer[i] = null;
        }
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        hochgeladen = true;
    }

    /** Uniforms und Texturen für das Gelände (Programm p gebunden). */
    public void bind(GL4 gl, int p) { bind(gl, p, false); }

    /**
     * Wie {@link #bind(GL4, int)}; im Spiegelbild (halbe Auflösung, oft verzerrt) ohne Fels aus cliff und ohne Spalten
     * (W6 Runde 2: spart die Hälfte der Kosten von W6 im Spiegel).
     */
    public void bind(GL4 gl, int p, boolean spiegel) {
        hochladen(gl);
        boolean on = an && hochgeladen;
        gl.glUniform1i(gl.glGetUniformLocation(p, "uLandAn"), on ? 1 : 0);
        for (int i = 0; i < tex.length; i++) {
            gl.glUniform1i(gl.glGetUniformLocation(p, NAMEN[i]), UNITS[i]);
            float[] r = BILDER[i].rect();
            gl.glUniform4f(gl.glGetUniformLocation(p, NAMEN[i] + "Rect"), r[0], r[1], r[2], r[3]);
            if (on) {
                gl.glActiveTexture(GL.GL_TEXTURE0 + UNITS[i]);
                gl.glBindTexture(GL.GL_TEXTURE_2D, tex[i]);
            }
        }
        gl.glActiveTexture(GL.GL_TEXTURE0);
        boolean fe = on && fels && cliffBereit && !spiegel;
        gl.glUniform4f(gl.glGetUniformLocation(p, "uLand"), (float) gewinn, fe ? (rippen ? 2f : 1f) : 0f, spalten && !spiegel ? 1f : 0f, (float) kante);
        // Fels: Uniforms von cliff.glsl; die Rauschtextur von Einheit 15 (dort liegt die Flut) nach UNIT_CLIFF_NOISE. Die
        // Sampler zeigen immer auf eigene Einheiten (ein sampler3D auf derselben Einheit wie ein sampler2D wäre ein Fehler).
        gl.glUniform1i(gl.glGetUniformLocation(p, "uCliffNoise"), UNIT_CLIFF_NOISE);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uCliffMap"), UNIT_CLIFF_MAP);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uCliffStrata"), UNIT_CLIFF_MAP);
        if (cliffBereit) {
            cliffGl.bind(gl, p, cliff, UNIT_CLIFF_MAP);
            int[] b = new int[1];
            gl.glActiveTexture(GL.GL_TEXTURE0 + com.dan.cliff.gl.CliffGl.NOISE_UNIT);
            gl.glGetIntegerv(GL4.GL_TEXTURE_BINDING_3D, b, 0);
            gl.glBindTexture(GL4.GL_TEXTURE_3D, 0);
            gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_CLIFF_NOISE);
            gl.glBindTexture(GL4.GL_TEXTURE_3D, b[0]);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glUniform1i(gl.glGetUniformLocation(p, "uCliffNoise"), UNIT_CLIFF_NOISE);
        }
    }
}
