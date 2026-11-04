package com.dan.lituya.gl;

import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;
import com.dan.sky.Sky;
import com.dan.sky.gl.SkyGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.DoubleBinaryOperator;

/**
 * Werkbuch Welle W1 „Licht“ (L1–L3): Himmel, Licht und Schatten der Bucht. Ersetzt den Platzhalter-Himmel von Phase 1,
 * auf den das erste Werkbuch seit Phase 5a gewartet hat (Werkbuch Welle 2b, V1, V2).
 * <ul>
 * <li><b>Himmel</b> aus {@code com.dan.sky}: Streuung nach Hillaire mit Mehrfachstreuung, Dämmerung, Erdschatten und
 * Venusgürtel, Sonne und Mond nach Datum und Ort, Sterne. {@link SkyGl} zeichnet ihn in den HDR-Puffer und dazu ein
 * Rundumbild (512 × 256, mit Mip-Stufen), aus dem {@code common.glsl} Spiegelungen, Himmelslicht und Dunstfarbe liest
 * ({@code uSkyMode = 1}).</li>
 * <li><b>Belichtung</b>: alle Helligkeiten kommen mit der Vorbelichtung {@link #pre} multipliziert heraus (so bleibt auch
 * die Nacht im Halbfloat); die Nachbearbeitung belichtet danach mit 1.</li>
 * <li><b>Horizontkarten</b> für den Schatten der Kette: je Bodenpunkt der höchste Winkel des Geländes in Richtung der
 * Sonne (mit Erdkrümmung und Strahlenbrechung, mindestens die Kimm des Meers), gerechnet auf der Grafikkarte
 * ({@code schatten.comp}) für ein feines Gitter über den Kern (24 km, 2048², 11,7 m) und ein grobes über die Szene
 * (66 km, 1024², 64 m). Neu nur, wenn das Azimut der Sonne um mehr als 0,25° wandert; ob ein Punkt Sonne hat, entscheidet
 * der Shader aus der Sonnenhöhe. So steigt das Alpenglühen beim Sinken der Sonne von selbst die Hänge hinauf.</li>
 * <li><b>Sonne am Punkt</b> ({@code sunAt} in {@code common.glsl}): Durchlässigkeit der Luft nach Höhe des Punkts und
 * Sonnenhöhe (Rayleigh 8 km, Dunst 1,2 km Skalenhöhe, streifender Strahl nach Chapman genähert): auf 4 000 m glüht der
 * Fairweather rot, wenn die Bucht schon im Schatten liegt.</li>
 * </ul>
 */
public final class Licht {
    /** Szene-Gitter: Ecke x, z (Szene), Kante (m), Zellen je Kante. Fein über den Kern, grob über die Szene. */
    public static final double FEIN_KANTE = 24_000, GROB_KANTE = 66_000;
    public static final int FEIN_N = 2048, GROB_N = 1024;
    /** Texturstellen in den Shadern. */
    public static final int UNIT_PANO = 8, UNIT_SCHATTEN_F = 13, UNIT_SCHATTEN_G = 14;
    /** Wirksamer Erdradius mit Strahlenbrechung (k = 0,13). */
    public static final double R_EFF = 6_371_000 / (1 - 0.13);
    /** Sicht am Abend des 9.7.1958 [G, nach den Zeugen klar; ERA5 in W7]. */
    public static final double SICHT_KM = 60;

    /** An (W1) oder der Platzhalter von Phase 1. */
    public volatile boolean an = true;
    /** Exponent der Anpassung der Belichtung (1 volle, 0 keine): 0,8 lässt die Dämmerung dunkel. */
    public volatile double anpassung = 0.8;

    public final Sky sky = new Sky();
    private final SkyGl skyGl = new SkyGl();
    private Sky.State st;
    private boolean glOk;
    private String status = "Licht: noch nicht gestartet";
    private double pre = 1, preZiel = 1;
    private long panoNanos;
    private int panoTex, panoFbo;
    private final Shader schatten = new Shader("Horizont", null, "schatten.comp");
    private int hoeheF, hoeheG, schattenF, schattenG, grundF;
    private double schattenAz = Double.NaN;
    private final double[] feinRect, grobRect;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Licht"); t.setDaemon(true); return t; });
    private Future<float[][]> hoehen;
    private boolean hoehenDa, schattenDa;

    public Licht() {
        sky.latDeg = Site.LAT;
        sky.lonDeg = Site.LON;
        sky.visibilityKm = SICHT_KM;
        sky.groundAlbedo = 0.10;
        double[] kf = Site.KERN.scene(), sz = Site.SZENE.scene();
        double cx = (kf[0] + kf[2]) / 2, cz = (kf[1] + kf[3]) / 2, gx = (sz[0] + sz[2]) / 2, gz = (sz[1] + sz[3]) / 2;
        feinRect = new double[]{cx - FEIN_KANTE / 2, cz - FEIN_KANTE / 2, FEIN_KANTE};
        grobRect = new double[]{gx - GROB_KANTE / 2, gz - GROB_KANTE / 2, GROB_KANTE};
    }

    public String status() { return status; }
    public Sky.State state() { return st; }
    /** Ob Himmel und Licht aus {@code sky} kommen (sonst der Platzhalter). */
    public boolean aktiv() { return an && glOk && st != null; }
    /** Ob die Horizontkarten stehen (für Standbilder: warten). */
    public boolean schattenBereit() { return schattenDa; }
    /** Vorbelichtung (alle Helligkeiten der Szene sind damit multipliziert). */
    public double pre() { return pre; }

    public void init(GL4 gl) {
        try {
            skyGl.init(gl);
            schatten.build(gl);
            panoTex = Gpu.texture(gl, 512, 256, GL.GL_RGBA16F, true);
            gl.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            panoFbo = Gpu.fbo(gl, panoTex, 0);
            schattenF = Gpu.texture(gl, FEIN_N, FEIN_N, GL4.GL_RG32F, false);
            schattenG = Gpu.texture(gl, GROB_N, GROB_N, GL4.GL_RG32F, false);
            glOk = true;
            status = "Licht: Himmel aus com.dan.sky, Horizontkarten werden gerechnet …";
        } catch (RuntimeException e) {
            glOk = false;
            status = "Licht: Platzhalter (" + e.getMessage() + ")";
            System.err.println("LituyaBay1958: " + status);
        }
    }

    /**
     * Je Bild vor dem Zeichnen: Zustand des Himmels nach der Uhr, Augenhöhe und Blick; Belichtung; Höhen im Hintergrund
     * aus dem Kachelstapel, sobald er da ist; Horizontkarten, wenn die Sonne weitergewandert ist; Rundumbild.
     */
    public void vorbereiten(GL4 gl, SceneClock clock, double[] eye, double[] fwd, double fovy, double aspect, double bias, double dt,
                            boolean standbild, DoubleBinaryOperator stapel, boolean stapelDa) {
        if (!an || !glOk) return;
        sky.epochMillis = clock.epochMillis();
        sky.eyeHeight = Math.max(1, eye[1]);
        st = sky.step(dt);
        // Belichtung: Himmel unter 50° und Blickfeld gemittelt, geglättet (im Standbild sofort)
        double[] up = {0, 1, 0};
        double a = Math.sqrt(Math.max(1e-14, st.adaptation) * Math.max(1e-14, st.viewLuminance(fwd, up, fovy, aspect)));
        preZiel = Sky.State.exposureFor(a, anpassung) * Math.pow(2, bias);
        pre = standbild || !(pre > 0) ? preZiel : Math.exp(Math.log(pre) + (Math.log(preZiel) - Math.log(pre)) * Math.min(1, dt * 1.5));
        // Höhen
        if (hoehen == null && stapelDa) hoehen = exec.submit(() -> hoehenRechnen(stapel));
        if (!hoehenDa && hoehen != null && hoehen.isDone()) {
            try {
                float[][] h = hoehen.get();
                hoeheF = hoehenTextur(gl, h[0], FEIN_N);
                hoeheG = hoehenTextur(gl, h[1], GROB_N);
                grundF = hoehenTextur(gl, h[2], FEIN_N);
                hoehenDa = true;
            } catch (Exception e) {
                status = "Licht: Höhen nicht gerechnet (" + e + ")";
                hoehen = null;
            }
        }
        if (hoehenDa) {
            double az = Math.toDegrees(Math.atan2(st.sunDir[0], -st.sunDir[2]));
            if (Double.isNaN(schattenAz) || Math.abs(((az - schattenAz) % 360 + 540) % 360 - 180) > 0.25) {
                long t0 = System.nanoTime();
                horizont(gl, schattenF, FEIN_N, feinRect, az);
                horizont(gl, schattenG, GROB_N, grobRect, az);
                gl.glFinish();
                schattenAz = az;
                schattenDa = true;
                status = String.format(Locale.GERMAN, "Licht: Himmel com.dan.sky (%s, Sonne %.1f°), Horizontkarten für Azimut %.1f° in %.0f ms",
                        st.twilight(), st.sunAlt, az, (System.nanoTime() - t0) / 1e6);
            }
        }
        // Rundumbild: im Standbild jedes Bild, sonst höchstens 4-mal je Sekunde
        long now = System.nanoTime();
        if (standbild || now - panoNanos > 250_000_000L) {
            panoNanos = now;
            skyGl.update(gl, st);
            int[] prev = new int[1], vp = new int[4];
            gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
            gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, panoFbo);
            gl.glViewport(0, 0, 512, 256);
            skyGl.drawPanorama(gl, st, pre);
            gl.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
            gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
            gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        }
    }

    /** Höhen über NAVD88 für beide Gitter (Meer und Lücken 0), auf allen Kernen. */
    float[][] hoehenRechnen(DoubleBinaryOperator stapel) {
        long t0 = System.nanoTime();
        float[] f = gitter(stapel, feinRect, FEIN_N, false), g = gitter(stapel, grobRect, GROB_N, false), r = gitter(stapel, feinRect, FEIN_N, true);
        System.out.printf(Locale.GERMAN, "LituyaBay1958: Licht: Höhen %d² und %d² in %.1f s%n", FEIN_N, GROB_N, (System.nanoTime() - t0) / 1e9);
        return new float[][]{f, g, r};
    }

    /** Höhen eines Gitters; roh = mit Wassertiefen (W2: Grund für das Meer), sonst Meer und Lücken 0. */
    static float[] gitter(DoubleBinaryOperator stapel, double[] r, int n, boolean roh) {
        float[] v = new float[n * n];
        double c = r[2] / n;
        java.util.stream.IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                double h = stapel.applyAsDouble(r[0] + (i + 0.5) * c, r[1] + (j + 0.5) * c);
                v[j * n + i] = (float) (roh ? (Double.isNaN(h) ? -200 : h) : (Double.isNaN(h) ? 0 : Math.max(0, h)));
            }
        });
        return v;
    }

    private static int hoehenTextur(GL4 gl, float[] h, int n) {
        int t = Gpu.texture(gl, n, n, GL4.GL_R32F, false);
        FloatBuffer b = Buffers.newDirectFloatBuffer(h);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL4.GL_RED, GL.GL_FLOAT, b);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t;
    }

    /** Horizontkarte eines Gitters für das Azimut az (Grad von Nord) auf der Grafikkarte. */
    private void horizont(GL4 gl, int ziel, int n, double[] rect, double az) {
        schatten.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hoeheF);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hoeheG);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        schatten.set(gl, "uHoeheF", 0);
        schatten.set(gl, "uHoeheG", 1);
        schatten.set(gl, "uFein", (float) feinRect[0], (float) feinRect[1], (float) feinRect[2], 0f);
        schatten.set(gl, "uGrob", (float) grobRect[0], (float) grobRect[1], (float) grobRect[2], 0f);
        schatten.set(gl, "uZiel", (float) rect[0], (float) rect[1], (float) rect[2], (float) n);
        double a = Math.toRadians(az);
        schatten.set(gl, "uDir", (float) Math.sin(a), (float) -Math.cos(a));
        schatten.set(gl, "uR", (float) R_EFF);
        gl.glBindImageTexture(0, ziel, 0, false, 0, GL4.GL_WRITE_ONLY, GL4.GL_RG32F);
        gl.glDispatchCompute((n + 15) / 16, (n + 15) / 16, 1);
        gl.glMemoryBarrier(GL4.GL_TEXTURE_FETCH_BARRIER_BIT | GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        gl.glUseProgram(0);
    }

    /** Himmel in den gebundenen HDR-Puffer (statt sky.frag). */
    public void himmel(GL4 gl, float[] vp, float[] inv, boolean reversed, int w, int h, double fovy, double t) {
        SkyGl.View v = new SkyGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.reversedZ = reversed;
        v.width = w;
        v.height = h;
        v.fovyDeg = fovy;
        v.preExposure = pre;
        v.time = t;
        skyGl.draw(gl, st, v);
    }

    /** Die Uniforms von {@code common.glsl} für ein gebundenes Programm (uSkyMode, Rundumbild, Sonne, Horizontkarten). */
    public void uniforms(GL4 gl, Shader s) {
        boolean on = aktiv();
        s.set(gl, "uSkyMode", on ? 1 : 0);
        if (!on) { s.set(gl, "uSchattenAn", 0); return; }
        gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_PANO);
        gl.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
        s.set(gl, "uPano", UNIT_PANO);
        double p = pre;
        s.set(gl, "uSunColor", (float) (st.sunColor[0] * p), (float) (st.sunColor[1] * p), (float) (st.sunColor[2] * p));
        s.set(gl, "uSunToa", (float) (st.sunToa[0] * p), (float) (st.sunToa[1] * p), (float) (st.sunToa[2] * p));
        s.set(gl, "uMoonColor", (float) (st.moonColor[0] * p), (float) (st.moonColor[1] * p), (float) (st.moonColor[2] * p));
        s.set(gl, "uMoonDir", (float) st.moonDir[0], (float) st.moonDir[1], (float) st.moonDir[2]);
        s.set(gl, "uSkyAmb", (float) (st.skyIrradiance[0] / Math.PI * p), (float) (st.skyIrradiance[1] / Math.PI * p), (float) (st.skyIrradiance[2] / Math.PI * p));
        s.set(gl, "uFogDist", (float) (1 / Math.max(1e-7, st.groundExtinction)));
        s.set(gl, "uPre", (float) p);
        s.set(gl, "uSchattenAn", schattenDa ? 1 : 0);
        if (schattenDa) {
            gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_SCHATTEN_F);
            gl.glBindTexture(GL.GL_TEXTURE_2D, schattenF);
            gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_SCHATTEN_G);
            gl.glBindTexture(GL.GL_TEXTURE_2D, schattenG);
            s.set(gl, "uSchattenF", UNIT_SCHATTEN_F);
            s.set(gl, "uSchattenG", UNIT_SCHATTEN_G);
            s.set(gl, "uSchattenFRect", (float) feinRect[0], (float) feinRect[1], (float) feinRect[2], 0f);
            s.set(gl, "uSchattenGRect", (float) grobRect[0], (float) grobRect[1], (float) grobRect[2], 0f);
        }
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    /** Licht für Wald und Boote aus demselben Himmel (Sonne am Auge, Himmelslicht, Dunst, Rundumbild, Bergschatten). */
    public com.dan.forest.gl.ForestGl.Light waldLicht() {
        if (!aktiv()) return null;
        com.dan.forest.gl.ForestGl.Light l = new com.dan.forest.gl.ForestGl.Light();
        double p = pre;
        l.sunDir = st.sunDir.clone();
        l.sunColor = new double[]{st.sunColor[0] * p, st.sunColor[1] * p, st.sunColor[2] * p};
        l.skyColor = new double[]{st.skyIrradiance[0] / Math.PI * p, st.skyIrradiance[1] / Math.PI * p, st.skyIrradiance[2] / Math.PI * p};
        l.moonDir = st.moonDir.clone();
        l.moonColor = new double[]{st.moonColor[0] * p, st.moonColor[1] * p, st.moonColor[2] * p};
        l.fogDistance = 1 / Math.max(1e-7, st.groundExtinction);
        l.skyWeight = 1;
        l.skyTexture = panoTex;
        // Bergschatten als Karte von oben: der Wald steht im feinen Gitter (Kern)
        l.shadowTexture = schattenDa ? sonnenKarte : 0;
        l.shadowRect = new double[]{feinRect[0], feinRect[1], feinRect[2]};
        double[] fc = l.skyColor;
        l.fogColor = new double[]{fc[0] * 2, fc[1] * 2, fc[2] * 2};
        return l;
    }

    /** R8-Karte „Sonne ja/nein“ für den Wald (aus der feinen Horizontkarte und der Sonnenhöhe, je Bild billig). */
    private int sonnenKarte;
    private final Shader sonne = new Shader("Sonnenkarte", null, "sonnenkarte.comp");
    private boolean sonneGebaut;

    /** Nach {@link #vorbereiten}: die Karte für den Wald nachführen (ein Rechenaufruf, 2048²). */
    public void sonnenKarte(GL4 gl) {
        if (!aktiv() || !schattenDa) return;
        if (!sonneGebaut) {
            sonne.build(gl);
            sonnenKarte = Gpu.texture(gl, FEIN_N, FEIN_N, GL.GL_R8, false);
            sonneGebaut = true;
        }
        sonne.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, schattenF);
        sonne.set(gl, "uSchatten", 0);
        sonne.set(gl, "uSonneH", (float) Math.asin(Math.max(-1, Math.min(1, st.sunDir[1]))));
        sonne.set(gl, "uBaum", 30f);
        gl.glBindImageTexture(0, sonnenKarte, 0, false, 0, GL4.GL_WRITE_ONLY, GL.GL_R8);
        gl.glDispatchCompute(FEIN_N / 16, FEIN_N / 16, 1);
        gl.glMemoryBarrier(GL4.GL_TEXTURE_FETCH_BARRIER_BIT);
        gl.glUseProgram(0);
    }

    public void dispose(GL4 gl) {
        if (glOk) skyGl.dispose(gl);
        schatten.dispose(gl);
        sonne.dispose(gl);
        Gpu.deleteFbos(gl, panoFbo);
        Gpu.deleteTextures(gl, panoTex, hoeheF, hoeheG, schattenF, schattenG, sonnenKarte, grundF);
        exec.shutdownNow();
    }

    /** W2: Grund mit Wassertiefen über das feine Gitter (R32F, m über NAVD88; Lücken −200), 0 solange nicht da. */
    public int grundTextur() { return hoehenDa ? grundF : 0; }
    /** W2: das Rundumbild des Himmels (vorbelichtet). */
    public int panoTextur() { return panoTex; }
    /** W2: Karte „Sonne“ für Dinge auf dem feinen Gitter (Wald, Meer), 0 solange nicht da. */
    public int sonnenTextur() { return schattenDa && sonneGebaut ? sonnenKarte : 0; }

    /** Für Prüfungen: die Gitter (x0, z0, Kante). */
    public double[] feinRect() { return feinRect.clone(); }
    public double[] grobRect() { return grobRect.clone(); }
}
