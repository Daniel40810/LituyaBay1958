package com.dan.lituya.gl;

import com.dan.exposure.PreExposure;
import com.dan.horizon.Aerial;
import com.dan.horizon.Airmass;
import com.dan.horizon.Horizon;
import com.dan.horizon.gl.HorizonGl;
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
 * ({@code horizon.comp} aus com.dan.horizon) für ein feines Gitter über den Kern (24 km, 2048², 11,7 m) und ein grobes über die Szene
 * (66 km, 1024², 64 m). Neu nur, wenn das Azimut der Sonne um mehr als 0,25° wandert; ob ein Punkt Sonne hat, entscheidet
 * der Shader aus der Sonnenhöhe. So steigt das Alpenglühen beim Sinken der Sonne von selbst die Hänge hinauf.</li>
 * <li><b>Sonne am Punkt</b> ({@code sunAt} in {@code common.glsl}): Durchlässigkeit der Luft nach Höhe des Punkts und
 * Sonnenhöhe (Rayleigh 8 km, Dunst 1,2 km Skalenhöhe, streifender Strahl nach Chapman genähert): auf 4 000 m glüht der
 * Fairweather rot, wenn die Bucht schon im Schatten liegt.</li>
 * </ul>
 * Seit dem 07.11.2026 liegen die Engines dafür in Renderer.jar: Horizontkarten, Sonne am Punkt, Luftperspektive und
 * Himmelslicht in {@code com.dan.horizon} ({@link HorizonGl}, {@code horizon.glsl}), die Vorbelichtung in
 * {@code com.dan.exposure} ({@link PreExposure}). Hier bleibt, was die Bucht betrifft: Ort, Gitter, Sicht des Abends,
 * der Wald, die Wolken.
 */
public final class Licht {
    /** Szene-Gitter: Ecke x, z (Szene), Kante (m), Zellen je Kante. Fein über den Kern, grob über die Szene. */
    public static final double FEIN_KANTE = 24_000, GROB_KANTE = 66_000;
    public static final int FEIN_N = 2048, GROB_N = 1024;
    /** Texturstellen in den Shadern. */
    public static final int UNIT_PANO = 8, UNIT_SCHATTEN_F = 13, UNIT_SCHATTEN_G = 14;
    /** Wirksamer Erdradius mit Strahlenbrechung (k = 0,13, com.dan.horizon). */
    public static final double R_EFF = new Horizon().effectiveRadius();
    /** Sicht am Abend des 9.7.1958 [G, nach den Zeugen klar; ERA5 in W7]. */
    public static final double SICHT_KM = 60;

    /** An (W1) oder der Platzhalter von Phase 1. */
    public volatile boolean an = true;
    /** Exponent der Anpassung der Belichtung (1 volle, 0 keine): 0,8 lässt die Dämmerung dunkel. */
    public volatile double anpassung = 0.8;
    /** W6: Blenden am Tag (bei hoher Sonne) zusätzlich, −1 [G]. */
    public volatile double tagBlenden = -1.0;
    /**
     * Werkbuch Welle W5: Höhe (m), für die der Wald sein Sonnenlicht bekommt (vom Renderer je Bild: Grund am Zielpunkt
     * des Blicks). Vorher bekam der Wald die Sonne am Auge (com.dan.sky für die Augenhöhe), Gelände und Welle dagegen die
     * Sonne am Punkt (common.glsl, sunAt). Um 22:18 steht die Sonne knapp unter der Kimm: vom Auge auf 200 m aus war sie
     * rot, aber nicht null, und die Kronen am Sporn leuchteten im Gegenlicht orange über dem dunklen Hang („rot überzogen“).
     * Jetzt dieselbe Rechnung wie sunAt (Durchlass der Luft in dieser Höhe) [G]. NaN: wie vorher.
     */
    public volatile double waldHoehe = Double.NaN;
    /** W5: Faktor auf die Sonne des Walds (Proben). */
    public volatile double waldSonne = 1;
    /** W7 Runde 1c: Anteil der Sonne unter den Wolken für Wald, Fälle und Boote (aus {@code WetterGl.waldSonne}, ohne Wolken 1). */
    public volatile double waldWolken = 1;
    /** W5: Faktor auf den Mond des Walds (Proben). */
    public volatile double waldMond = 1;
    /** W7: wie viel heller der Himmel ist als klar (Wolken: unter 1), für die Belichtung (com.dan.cloud.brightnessFactor). */
    public volatile double hellFaktor = 1;
    /** W7: was nach dem Himmel ins Rundumbild kommt (die Wolken aus gl/WetterGl), mit gebundenem Ziel; null = nichts. */
    public volatile java.util.function.Consumer<GL4> panoZusatz;
    /** W7: Uniforms der Wolkenschatten für die Karte „Sonne“ (gl/WetterGl.uniforms); null = ohne. */
    public volatile java.util.function.Consumer<Shader> wolkenUniforms;
    /** W7: Nässe für den Wald (ForestGl.Light.wetness). */
    public volatile double nass;

    public final Sky sky = new Sky();
    private final SkyGl skyGl = new SkyGl();
    private Sky.State st;
    private boolean glOk;
    private String status = "Licht: noch nicht gestartet";
    private double pre = 1, preZiel = 1;
    /** Vorbelichtung mit Teilanpassung (com.dan.exposure, aus W1 herausgezogen). */
    public final PreExposure belichtung = new PreExposure();
    /** Horizontkarten und Karte „Sonne“ (com.dan.horizon, aus W1 herausgezogen). */
    public final Horizon horizont = new Horizon();
    private final HorizonGl horizontGl = new HorizonGl(horizont);
    private final Aerial luft = new Aerial();
    private long panoNanos;
    private int panoTex, panoFbo;
    private int grundF;
    private final double[] feinRect, grobRect;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Licht"); t.setDaemon(true); return t; });
    private Future<Horizon.Grid[]> hoehen;
    private Shader sonneS;
    private boolean hoehenDa, schattenDa;
    private volatile float[] grobHoehen;

    /** W7: Höhen des groben Gitters über NAVD88 (Meer 0), GROB_N², null solange nicht gerechnet. */
    public float[] grobHoehen() { return grobHoehen; }

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
            // W7: Wolkenschatten auch in der Karte „Sonne“ für Wald und Meer (wolken.glsl als Schatten des Wirts)
            horizontGl.sunMapHost = Shader.read("wolken.glsl") + "\nfloat hzHostShadow(vec3 w, vec3 sunDir) { return wolkenSchatten(w, sunDir); }\n";
            horizontGl.sunMapUniforms = p -> {
                java.util.function.Consumer<Shader> wu = wolkenUniforms;
                if (sonneS == null || sonneS.id() != p) sonneS = Shader.fremd("Sonnenkarte", p);
                if (wu != null) wu.accept(sonneS); else sonneS.set(gl, "uWkAn", 0);
            };
            horizontGl.init(gl);
            panoTex = Gpu.texture(gl, 512, 256, GL.GL_RGBA16F, true);
            gl.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            panoFbo = Gpu.fbo(gl, panoTex, 0);
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
        // Belichtung (com.dan.exposure.PreExposure): Himmel unter 50° und Blickfeld gemittelt, Teilanpassung, geglättet (im
        // Standbild sofort); W7: unter Wolken ist der Himmel dunkler; W6: am Tag eine Blende weniger (Proben W6Q8 69/70: mit
        // −1 lesbar, vorher Schnee, Wiesen und Dunst ausgebrannt), Übergang zwischen 5° und 25° Sonnenhöhe [G]
        double[] up = {0, 1, 0};
        double sonne = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, st.sunDir[1]))));
        belichtung.adaptation = anpassung;
        belichtung.dayStops = tagBlenden;
        pre = belichtung.update(st.adaptation, st.viewLuminance(fwd, up, fovy, aspect), hellFaktor, sonne, bias, dt, standbild);
        preZiel = belichtung.target();
        // Höhen
        if (hoehen == null && stapelDa) hoehen = exec.submit(() -> hoehenRechnen(stapel));
        if (!hoehenDa && hoehen != null && hoehen.isDone()) {
            try {
                Horizon.Grid[] h = hoehen.get();
                horizontGl.heights(gl, h[0], h[1]);
                grobHoehen = h[1].h;   // W7: für die Wolken am Gelände
                grundF = hoehenTextur(gl, h[2].h, FEIN_N);
                hoehenDa = true;
            } catch (Exception e) {
                status = "Licht: Höhen nicht gerechnet (" + e + ")";
                hoehen = null;
            }
        }
        if (hoehenDa) {
            double az = Math.toDegrees(Math.atan2(st.sunDir[0], -st.sunDir[2]));
            if (horizontGl.update(gl, az)) {
                schattenDa = true;
                status = String.format(Locale.GERMAN, "Licht: Himmel com.dan.sky (%s, Sonne %.1f°), Horizontkarten (com.dan.horizon) für Azimut %.1f° in %.0f ms",
                        st.twilight(), st.sunAlt, az, horizontGl.lastMillis());
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
            java.util.function.Consumer<GL4> zus = panoZusatz;
            if (zus != null) zus.accept(gl);   // W7: die Wolken ins Rundumbild
            gl.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
            gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
            gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        }
    }

    /**
     * Höhen über NAVD88 für beide Gitter (Meer und Lücken 0) und das feine mit Wassertiefen (W2: Grund für das Meer,
     * Lücken −200), auf allen Kernen (com.dan.horizon.Horizon.Grid).
     */
    Horizon.Grid[] hoehenRechnen(DoubleBinaryOperator stapel) {
        long t0 = System.nanoTime();
        Horizon.Grid f = Horizon.Grid.sample(stapel, feinRect[0], feinRect[1], feinRect[2], FEIN_N, 0);
        Horizon.Grid g = Horizon.Grid.sample(stapel, grobRect[0], grobRect[1], grobRect[2], GROB_N, 0);
        Horizon.Grid r = Horizon.Grid.sampleRaw(stapel, feinRect[0], feinRect[1], feinRect[2], FEIN_N, -200);
        System.out.printf(Locale.GERMAN, "LituyaBay1958: Licht: Höhen %d² und %d² in %.1f s%n", FEIN_N, GROB_N, (System.nanoTime() - t0) / 1e9);
        return new Horizon.Grid[]{f, g, r};
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

    /** Himmel in den gebundenen HDR-Puffer (statt sky.frag). */
    public void himmel(GL4 gl, float[] vp, float[] inv, boolean reversed, int w, int h, double fovy, double t) {
        himmel(gl, vp, inv, reversed, w, h, fovy, t, true);
    }

    /** W8a: wie oben; scheiben = false ohne Sonnen- und Mondscheibe (für das Spiegelbild). */
    public void himmel(GL4 gl, float[] vp, float[] inv, boolean reversed, int w, int h, double fovy, double t, boolean scheiben) {
        SkyGl.View v = new SkyGl.View();
        v.discs = scheiben;
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
        if (!on) { s.set(gl, "uHzOn", 0); return; }
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
        horizontGl.bind(gl, s.id(), UNIT_SCHATTEN_F, UNIT_SCHATTEN_G);   // com.dan.horizon: Horizontkarten, uHzOn
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    /** Licht für Wald und Boote aus demselben Himmel (Sonne am Auge, Himmelslicht, Dunst, Rundumbild, Bergschatten). */
    public com.dan.forest.gl.ForestGl.Light waldLicht() {
        if (!aktiv()) return null;
        com.dan.forest.gl.ForestGl.Light l = new com.dan.forest.gl.ForestGl.Light();
        double p = pre;
        l.sunDir = st.sunDir.clone();
        l.sunColor = new double[]{st.sunColor[0] * p, st.sunColor[1] * p, st.sunColor[2] * p};
        if (!Double.isNaN(waldHoehe)) {
            double[] dl = Airmass.standard(Math.max(0, waldHoehe), st.sunDir[1]);   // com.dan.horizon
            l.sunColor = new double[]{st.sunToa[0] * dl[0] * p, st.sunToa[1] * dl[1] * p, st.sunToa[2] * dl[2] * p};
        }
        for (int c = 0; c < 3; c++) l.sunColor[c] *= waldSonne * Math.max(0, Math.min(1, waldWolken));
        l.skyColor = new double[]{st.skyIrradiance[0] / Math.PI * p, st.skyIrradiance[1] / Math.PI * p, st.skyIrradiance[2] / Math.PI * p};
        l.moonDir = st.moonDir.clone();
        l.moonColor = new double[]{st.moonColor[0] * p * waldMond, st.moonColor[1] * p * waldMond, st.moonColor[2] * p * waldMond};
        l.fogDistance = 1 / Math.max(1e-7, st.groundExtinction);
        // W5: der Dunst des Walds wie aerial() in common.glsl (Luft 8 km, Dunst 1,2 km mit 0,45, in der Höhe des Walds).
        // Vorher die volle Bodenextinktion ohne Höhe: rund dreimal so viel Dunst wie das Gelände, und der kommt aus dem
        // Rundumbild an der Kimm, zur Sonne hin glühend orange. Die fernen Kronen (ab 150 m Impostors) standen damit orange
        // über dem dunklen Hang („rot überzogen“), die nahen blieben dunkel. Belegt in der Probe W5Q: Sonne und Mond am
        // Wald 0, die Kronen trotzdem rot.
        if (!Double.isNaN(waldHoehe)) l.fogDistance = luft.fogDistanceAt(waldHoehe, st.groundExtinction);   // com.dan.horizon.Aerial
        l.skyWeight = 1;
        l.skyTexture = panoTex;
        l.wetness = Math.max(0, Math.min(1, nass));   // W7
        // Bergschatten als Karte von oben: der Wald steht im feinen Gitter (Kern)
        l.shadowTexture = schattenDa ? horizontGl.sunTexture() : 0;
        l.shadowRect = new double[]{feinRect[0], feinRect[1], feinRect[2]};
        double[] fc = l.skyColor;
        l.fogColor = new double[]{fc[0] * 2, fc[1] * 2, fc[2] * 2};
        return l;
    }

    /** Nach {@link #vorbereiten}: die Karte „Sonne“ für Wald und Meer nachführen (com.dan.horizon, ein Rechenaufruf, 2048²). */
    public void sonnenKarte(GL4 gl) {
        if (!aktiv() || !schattenDa) return;
        horizontGl.sunMap(gl, st.sunDir[1], st.sunDir, 30);
    }

    public void dispose(GL4 gl) {
        if (glOk) skyGl.dispose(gl);
        horizontGl.dispose(gl);
        Gpu.deleteFbos(gl, panoFbo);
        Gpu.deleteTextures(gl, panoTex, grundF);
        exec.shutdownNow();
    }

    /** W2: Grund mit Wassertiefen über das feine Gitter (R32F, m über NAVD88; Lücken −200), 0 solange nicht da. */
    public int grundTextur() { return hoehenDa ? grundF : 0; }
    /** W2: das Rundumbild des Himmels (vorbelichtet). */
    public int panoTextur() { return panoTex; }
    /** W2: Karte „Sonne“ für Dinge auf dem feinen Gitter (Wald, Meer), 0 solange nicht da. */
    public int sonnenTextur() { return schattenDa ? horizontGl.sunTexture() : 0; }

    /** Für Prüfungen: die Gitter (x0, z0, Kante). */
    public double[] feinRect() { return feinRect.clone(); }
    public double[] grobRect() { return grobRect.clone(); }

}
