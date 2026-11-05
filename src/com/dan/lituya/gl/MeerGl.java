package com.dan.lituya.gl;

import com.dan.lituya.scene.Site;
import com.dan.ocean.Ocean;
import com.dan.ocean.gl.OceanGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Werkbuch Welle W2 „Spiegel“ (L7, Versprechen V3): das Wasser der Bucht aus {@code com.dan.ocean} statt des Rauschens
 * von Phase 1 ({@code meer.frag}, Platzhalter „bis ocean mit Gletschermilch in Phase 5a“).
 * <ul>
 * <li><b>See:</b> {@link Ocean#lago} (Gletschersee aus Glacier: kurze Streichlänge, keine Dünung) bei Bft 2, Wind aus der
 * Verdrahtung; Farben milchig-türkis ({@link Ocean#LAGO_WATER}, {@link Ocean#LAGO_SSS}). Grund aus dem feinen Gitter von
 * {@link Licht} mit Wassertiefen: Durchsicht im Flachen, Schaumsaum am Ufer, über Land kein Wasser. Bergschatten von
 * oben (keine Sonnenglitzer im Schatten der Kette), Himmel aus dem Rundumbild.</li>
 * <li><b>Spiegel der Berge:</b> Gelände und Wald werden am Wasserspiegel gespiegelt in halber Auflösung gezeichnet
 * ({@link #spiegelZiel}); {@code spiegel.frag} legt sie auf das Wasser: Fresnel, leichte Verzerrung durch die Kräusel,
 * statt der Himmelsspiegelung, wo im Spiegel Gelände steht.</li>
 * <li><b>Gletschermilch:</b> zum Gilbert und Crillon Inlet hin mehr milchiges Türkis im Wasser [G], in der ganzen Bucht
 * ein Grundton.</li>
 * <li><b>Welle:</b> im Rechteck der Vorrechnung zeichnet das Meer nicht (Maske), solange die Welle läuft; dort zeichnet
 * {@code welle.frag} (wie bisher {@code meer.frag} mit Boden 40 m unter dem Pegel).</li>
 * </ul>
 * Unter Wasser, mit Marken und ohne Licht aus {@code sky} zeichnet weiter {@code meer.frag}.
 */
public final class MeerGl {
    /** An (W2) oder {@code meer.frag}. */
    public volatile boolean an = true;
    /** Spiegelung der Berge (halbe Auflösung); mit Wald im Spiegel ab „Hoch“. */
    public volatile boolean spiegel = true, waldImSpiegel = true;
    /**
     * W5 Tempo: das Spiegelbild in diesem Bild rechnen (der Renderer schaltet es ab, wenn kaum Wasser im Bild ist: in H6
     * „524 m“ zeichnete der Spiegel Gelände und Wald ein zweites Mal für einen Streifen Wasser am unteren Rand).
     */
    public volatile boolean spiegelJetzt = true;
    /** Seegang in Beaufort (klarer Abend nach den Zeugen: ruhig) [G]. */
    public volatile int beaufort = 1;

    public final Ocean ocean = Ocean.lago(1);
    private final OceanGl gl = new OceanGl();
    private final OceanGl.Light light = new OceanGl.Light();
    private final OceanGl.View view = new OceanGl.View();
    final Shader spiegelShader = new Shader("Spiegel", null, "fullscreen.vert", "spiegel.frag");
    private boolean ok;
    private String status = "Meer: noch nicht gestartet";
    private int reflTex, reflDepth, reflFbo, reflW, reflH;
    private int maskTex;
    private double[] maskFuer;
    private int bftGebaut = -1;

    public String status() { return status; }
    /** W3c: das Spiegelbild der Berge (für die Welle), 0 ohne. */
    public int spiegelTextur() { return spiegel && spiegelJetzt && ok ? reflTex : 0; }
    public boolean bereit() { return ok; }

    public void init(GL4 g) {
        try {
            gl.init(g);
            spiegelShader.build(g);
            ocean.windFromDeg = 250;
            light.waterColor = Ocean.LAGO_WATER.clone();
            light.sssColor = Ocean.LAGO_SSS.clone();
            light.bedColor = new double[]{0.16, 0.15, 0.13};
            light.bedTurbidity = 0.93;
            light.bedOutside = -200;
            light.tileBreak = 0.6;
            ok = true;
            status = "Meer: com.dan.ocean (Gletschersee, Bft " + beaufort + ")";
        } catch (RuntimeException e) {
            ok = false;
            status = "Meer: meer.frag (" + e.getMessage() + ")";
            System.err.println("LituyaBay1958: " + status);
        }
    }

    /** Ob in diesem Bild com.dan.ocean zeichnet. */
    public boolean aktiv(Licht licht, boolean unterWasser, boolean marken) {
        return an && ok && licht.aktiv() && !unterWasser && !marken;
    }

    /** Je Bild: Seegang, Wind, Wasserstand, FFT auf der Grafikkarte. */
    public void schritt(GL4 g, double dt, double pegel, double windSpeed, double windFromDeg) {
        if (!ok) return;
        if (beaufort != bftGebaut) {
            Ocean o = Ocean.lago(beaufort);
            ocean.fetch = o.fetch;
            ocean.depth = o.depth;
            ocean.fetchLimited = true;
            ocean.swellHeight = 0;
            ocean.choppiness = 0.5;   // spiegelglatter Abend nach den Zeugen: weiche Kräusel [G]
            ocean.foamAmount = o.foamAmount;
            ocean.windSpeed = Math.max(0.8, o.windSpeed);
            bftGebaut = beaufort;
        }
        ocean.windFromDeg = windFromDeg;
        ocean.waterLevel = pegel;
        ocean.step(dt);
        gl.update(g, ocean);
    }

    /** Ziel der Spiegelung (halbe Größe des HDR-Bilds), gebunden und gelöscht; danach zeichnet der Renderer gespiegelt. */
    public void spiegelZiel(GL4 g, int w, int h) {
        int sw = Math.max(32, w / 2), sh = Math.max(32, h / 2);
        if (sw != reflW || sh != reflH) {
            Gpu.deleteFbos(g, reflFbo);
            Gpu.deleteTextures(g, reflTex, reflDepth);
            reflTex = Gpu.texture(g, sw, sh, GL.GL_RGBA16F, false);
            reflDepth = Gpu.texture(g, sw, sh, GL4.GL_DEPTH_COMPONENT32F, false);
            reflFbo = Gpu.fbo(g, reflTex, reflDepth);
            reflW = sw;
            reflH = sh;
        }
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, reflFbo);
        g.glViewport(0, 0, sw, sh);
    }

    /** Das Meer mit Tiefe in den gebundenen HDR-Puffer. welleRect: Rechteck der laufenden Welle (oder null). */
    public void zeichnen(GL4 g, float[] vp, double[] eye, boolean reversed, Licht licht, double[] welleRect) {
        com.dan.sky.Sky.State st = licht.state();
        double p = licht.pre();
        view.viewProj = vp;
        view.eye = eye.clone();
        view.reversedZ = reversed;
        light.sunDir = st.sunDir.clone();
        light.sunColor = new double[]{st.sunColor[0] * p, st.sunColor[1] * p, st.sunColor[2] * p};
        light.moonDir = st.moonDir.clone();
        light.moonColor = new double[]{st.moonColor[0] * p, st.moonColor[1] * p, st.moonColor[2] * p};
        light.skyTexture = licht.panoTextur();
        light.fogDistance = 1 / Math.max(1e-7, st.groundExtinction) / 0.45;
        double[] fr = licht.feinRect();
        int sun = licht.sonnenTextur();
        light.shadowTexture = sun;
        light.shadowRect = new double[]{fr[0], fr[1], fr[2]};
        int grund = licht.grundTextur();
        light.bedTexture = grund;
        light.bedRect = new double[]{fr[0], fr[1], fr[2]};
        light.bedScale = 1;
        light.bedOffset = 0;
        maske(g, welleRect);
        g.glEnable(GL.GL_DEPTH_TEST);
        g.glDepthMask(true);
        gl.draw(g, ocean, view, light);
        g.glDepthMask(true);
        g.glDisable(GL.GL_DEPTH_TEST);
    }

    /** Maske gegen das Rechteck der laufenden Welle (R8, 1 = dort zeichnet die Welle, das Meer nicht). */
    private void maske(GL4 g, double[] r) {
        if (r == null) { light.maskTexture = 0; light.maskMode = 0; return; }
        if (maskFuer == null || !java.util.Arrays.equals(maskFuer, r)) {
            double x0 = Math.min(r[0], r[2]), x1 = Math.max(r[0], r[2]), z0 = Math.min(r[1], r[3]), z1 = Math.max(r[1], r[3]);
            double kante = Math.max(x1 - x0, z1 - z0);
            int n = 512;
            ByteBuffer b = Buffers.newDirectByteBuffer(n * n);
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    double x = x0 + (i + 0.5) * kante / n, z = z0 + (j + 0.5) * kante / n;
                    b.put((byte) (x < x1 && z < z1 ? 255 : 0));
                }
            b.flip();
            if (maskTex == 0) maskTex = Gpu.texture(g, n, n, GL.GL_R8, false);
            g.glBindTexture(GL.GL_TEXTURE_2D, maskTex);
            g.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            g.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, b);
            g.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
            g.glBindTexture(GL.GL_TEXTURE_2D, 0);
            light.maskRect = new double[]{x0, z0, kante};
            maskFuer = r.clone();
        }
        light.maskTexture = maskTex;
        light.maskMode = -1;
    }

    /**
     * Spiegel der Berge und Gletschermilch über das HDR-Bild (Mischung: addieren). common setzt Kamera, Himmel und Licht;
     * die Tiefe der Szene als Textur. welleRect wie bei {@link #zeichnen}.
     */
    public void spiegelLegen(GL4 g, int depthTex, java.util.function.Consumer<Shader> common, double[] welleRect, float near, float far) {
        common.accept(spiegelShader);
        g.glActiveTexture(GL.GL_TEXTURE0);
        g.glBindTexture(GL.GL_TEXTURE_2D, reflTex);
        g.glActiveTexture(GL.GL_TEXTURE1);
        g.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        g.glActiveTexture(GL.GL_TEXTURE0);
        spiegelShader.set(g, "uRefl", 0);
        spiegelShader.set(g, "uDepth", 1);
        spiegelShader.set(g, "uSpiegelAn", spiegel && spiegelJetzt && reflTex != 0 ? 1 : 0);
        spiegelShader.set(g, "uNear", near);
        spiegelShader.set(g, "uFar", far);
        spiegelShader.set(g, "uWelleAn", welleRect != null ? 1 : 0);
        if (welleRect != null) spiegelShader.set(g, "uWelleRect", (float) welleRect[0], (float) welleRect[1], (float) welleRect[2], (float) welleRect[3]);
        // Gletschermilch: zwei Quellen (Gilbert Inlet, Crillon Inlet) [G]
        spiegelShader.set(g, "uMilchA", (float) Site.GILBERT.x(), (float) Site.GILBERT.z(), 5500f, 1f);
        spiegelShader.set(g, "uMilchB", (float) Site.CRILLON_INLET.x(), (float) Site.CRILLON_INLET.z(), 4500f, 0.8f);
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glEnable(GL.GL_BLEND);
        g.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        g.glDisable(GL.GL_BLEND);
    }

    public String info() {
        return String.format(Locale.GERMAN, "%s · H_s %.2f m · Spiegel %s", status, ocean.state().totalHs(), spiegel ? (reflW + "×" + reflH) : "aus");
    }

    public void dispose(GL4 g) {
        if (ok) gl.dispose(g);
        spiegelShader.dispose(g);
        Gpu.deleteFbos(g, reflFbo);
        Gpu.deleteTextures(g, reflTex, reflDepth, maskTex);
    }
}
