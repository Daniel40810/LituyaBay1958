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
    /** W2: Gletschermilch der Bucht (com.dan.ocean.GlacialMilk) [G]. */
    public final com.dan.ocean.GlacialMilk milch = new com.dan.ocean.GlacialMilk()
            .add(Site.GILBERT.x(), Site.GILBERT.z(), 5500, 1).add(Site.CRILLON_INLET.x(), Site.CRILLON_INLET.z(), 4500, 0.8);
    /** An (W2) oder {@code meer.frag}. */
    public volatile boolean an = true;
    /** Spiegelung der Berge (halbe Auflösung); mit Wald im Spiegel ab „Hoch“. */
    public volatile boolean spiegel = true, waldImSpiegel = true;
    /**
     * W5 Tempo: das Spiegelbild in diesem Bild rechnen (der Renderer schaltet es ab, wenn kaum Wasser im Bild ist: in H6
     * „524 m“ zeichnete der Spiegel Gelände und Wald ein zweites Mal für einen Streifen Wasser am unteren Rand).
     */
    public volatile boolean spiegelJetzt = true;
    /** Runde 7: Blick (VP) beim Rechnen des Spiegelbilds und im jetzigen Bild, zum Umrechnen (Spiegel jedes zweite Bild). */
    public volatile float[] spiegelVP, jetztVP;
    /** Runde 7: Spiegelbild in 1/teiler der Bildgröße (bisher 2). */
    public volatile int spiegelTeiler = 2;
    /** Runde 9: Schritte des Spiegelbilds mit der Tiefe. */
    public volatile int spiegelSchritte = 2;
    /** Runde 8: Blickrichtung jetzt (für die Entfernung aus der Tiefe des Spiegelbilds). */
    public volatile float[] spiegelVorn = {0, 0, -1};
    /**
     * W8a: Die See spiegelt selbst (planare Spiegelung in {@code com.dan.ocean}): das Spiegelbild trägt Himmel, Wolken,
     * Dunst, Gelände, Wald und Boote, und die See greift mit ihrer eigenen Normale genau hinein. Damit gibt es nur noch
     * einen Spiegel statt zweier, die an einer Zickzack-Kante aneinanderstießen (5b.4, Punkt 1), und der helle Himmel
     * scheint nicht mehr durch die dunklen Hänge im Wasser. {@code -Dlituya.alterSpiegel=true}: wie bis W7.
     */
    /** W8a: Nahebene der Kamera (umgekehrte Tiefe: Sichttiefe = nah / z). */
    public volatile double nah = 0.5;
    /** W8a: die Boote im Spiegelbild. */
    public volatile boolean booteImSpiegel = true;
    /**
     * W8b: Spiegelruhe in {@code com.dan.ocean}: unter rund 1 m/s keine Kräusel, voll ab 2,0 m/s; Böenfeld mit Flecken von
     * 45 m und einer Streuung von 0,6 (ln) [G nach Kahma und Donelan 1988, Donelan und Plant 2009]. Bei Windstille spiegelt
     * die Bucht, bei leisem Zug ziehen Katzenpfoten darüber. {@code -Dlituya.ohneRuhe=true}: aus (wie bis W8a).
     */
    /** W8b: {@code -Dlituya.spiegelTruebe=true}: die Trübe aus W7 Runde 1c auch im neuen Spiegel. */
    public volatile boolean trubeAlt = Boolean.getBoolean("lituya.spiegelTruebe");
    public volatile boolean ruheAn = !Boolean.getBoolean("lituya.ohneRuhe");
    public volatile double ruheEin = 1.0, ruheVoll = 2.0, ruheStreuung = 0.6, ruheFleck = 45;   // Runde 2: in 806 waren bei 0,45 und 2,6 kaum Katzenpfoten zu sehen
    private volatile double windAmWasser, ruheZeit;
    public volatile boolean spiegelImMeer = !Boolean.getBoolean("lituya.alterSpiegel");
    /** W8a: Auge, Blickrichtung, Pegel und tan des halben Blickfelds beim Spiegelbild; gültig, solange es gilt. */
    volatile double[] planarAuge, planarVorn;
    volatile double planarPegel, planarTan = 0.4;
    volatile boolean planarGueltig;

    /** W8a: nach dem Spiegelbild aufrufen (Kamera, mit der es entstand); erzeugt die Mip-Stufen. */
    public void spiegelFertig(GL4 g, double[] auge, double[] vorn, double pegel, double fovyDeg) {
        planarAuge = auge.clone();
        planarVorn = vorn.clone();
        planarPegel = pegel;
        planarTan = Math.tan(Math.toRadians(fovyDeg) / 2);
        planarGueltig = true;
        if (reflTex != 0) {
            g.glBindTexture(GL.GL_TEXTURE_2D, reflTex);
            g.glGenerateMipmap(GL.GL_TEXTURE_2D);
            g.glBindTexture(GL.GL_TEXTURE_2D, 0);
        }
    }

    /** W8a: kein gültiges Spiegelbild (zu wenig Wasser im Bild). */
    public void spiegelUngueltig() { planarGueltig = false; }

    /** Runde 8: Tiefe des Spiegelbilds (0 ohne). */
    public int spiegelTiefe() { return spiegel && spiegelJetzt && ok ? reflDepth : 0; }
    /** Seegang in Beaufort (klarer Abend nach den Zeugen: ruhig) [G]; seit W7 aus dem Wind der Wetterregie. */
    public volatile int beaufort = 1;
    /** W7: Ringe des Regens (com.dan.rain) als feine Störung der Fläche: Textur (0 = keine), Kachel (m), Stärke. */
    private int ringTex;
    private double ringKachel = 3, ringStaerke;

    /** W7: Ringe des Regens für das nächste Zeichnen. */
    public void ringe(int tex, double kachel, double staerke) { ringe(tex, kachel, staerke, 0, 0, 0); }

    /** W7b: Ringe aus com.dan.precip (Art 1: Wellenfeld fest in der Welt um cx, cz, echte Steigungen); Art 0 wie bisher. */
    public void ringe(int tex, double kachel, double staerke, int art, double cx, double cz) {
        ringTex = tex;
        ringKachel = kachel;
        ringStaerke = staerke;
        ringArt = art;
        ringMitte[0] = cx;
        ringMitte[1] = cz;
    }

    private int ringArt;
    private final double[] ringMitte = new double[2];
    /** W7b Runde 5: {unter dem Raster, im Raster, Anteil, Anteil} aus com.dan.precip; 0, 0 = aus. */
    private double[] ringRau = {0, 0, 1, 1};
    private int ringBild;

    /** W7b Runde 5: Rauigkeit des Regens (mittlere quadratische Steigung unter dem Raster und im Raster, Anteile der Fläche). */
    public void ringRauigkeit(double[] r) { ringRau = r == null || r.length < 4 ? new double[]{0, 0, 1, 1} : r.clone(); }

    /** W8b Runde 2: feines zweites Feld (Kaskade) und Karte der Schauer (Ringe nur, wo es regnet). */
    public void feinUndMaske(int fein, double feinKante, double[] feinMitte, double feinRau, int maske, double[] maskeRechteck) {
        feinTex = fein; feinKante2 = feinKante; feinMitte2 = feinMitte == null ? new double[2] : feinMitte.clone(); feinRau2 = feinRau;
        schauerTex = maske; schauerRect = maskeRechteck == null ? new double[]{0, 0, 1} : maskeRechteck.clone();
    }
    private int feinTex, schauerTex;
    private double feinKante2 = 0.75, feinRau2;
    private double[] feinMitte2 = new double[2], schauerRect = {0, 0, 1};

    public final Ocean ocean = Ocean.lago(1);
    /** W11b: das Kielwasser aus com.dan.wake (Textur-Array RG16F, 0 nah, 1 weit) mit den Lagen der Kacheln; 0 = keins. */
    private int kielTex;
    private double[] kielRect0 = {0, 0, 1, 0}, kielRect1 = {0, 0, 1, 0};

    /** W11b: Kielwasser für das nächste Zeichnen (aus {@code BooteGl.kielwasser}); tex 0 = aus. */
    public void kielwasser(int tex, double[] rect0, double[] rect1) {
        kielTex = tex;
        kielRect0 = rect0 == null ? new double[]{0, 0, 1, 0} : rect0.clone();
        kielRect1 = rect1 == null ? new double[]{0, 0, 1, 0} : rect1.clone();
    }

    /** W11b: die Rümpfe in der Wasserlinie (kein Wasser im Rumpf, Schaumsaum); leer = keine. */
    public void ruempfe(java.util.List<com.dan.ocean.HullMask> masken) { ocean.hulls = masken == null ? java.util.List.of() : java.util.List.copyOf(masken); }
    private final OceanGl gl = new OceanGl();
    private final OceanGl.Light light = new OceanGl.Light();
    private final OceanGl.View view = new OceanGl.View();
    final Shader spiegelShader = new Shader("Spiegel", null, "fullscreen.vert", "spiegel.frag");
    private boolean ok;
    private String status = "Meer: noch nicht gestartet";
    private int reflTex, reflDepth, reflFbo, reflColorFbo, reflW, reflH;
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
        windAmWasser = windSpeed;
        ruheZeit += dt;
        if (beaufort != bftGebaut) {
            Ocean o = Ocean.lago(beaufort);
            ocean.fetch = o.fetch;
            ocean.depth = o.depth;
            ocean.fetchLimited = true;
            ocean.swellHeight = 0;
            // spiegelglatter Abend nach den Zeugen: weiche Kräusel [G]; seit W7 mit dem Wind spitzer (Bft 1 wie bisher)
            ocean.choppiness = Math.min(1.0, 0.5 + 0.1 * (beaufort - 1));
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
        int sw = Math.max(32, w / spiegelTeiler), sh = Math.max(32, h / spiegelTeiler);
        if (sw != reflW || sh != reflH) {
            Gpu.deleteFbos(g, reflFbo, reflColorFbo);
            Gpu.deleteTextures(g, reflTex, reflDepth);
            reflTex = Gpu.texture(g, sw, sh, GL.GL_RGBA16F, spiegelImMeer);   // W8a: mit Mip-Stufen (Rauigkeit)
            reflDepth = Gpu.texture(g, sw, sh, GL4.GL_DEPTH_COMPONENT32F, false);
            reflFbo = Gpu.fbo(g, reflTex, reflDepth);
            reflColorFbo = Gpu.fbo(g, reflTex, 0);
            reflW = sw;
            reflH = sh;
        }
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, reflFbo);
        g.glViewport(0, 0, sw, sh);
    }

    /**
     * W7: das Ziel der Spiegelung ohne Tiefe binden (dieselbe Farbe), damit Wolken über das gespiegelte Gelände gelegt werden
     * können, während sie dessen Tiefe lesen ({@link #spiegelTiefeRoh}); liefert Breite und Höhe.
     */
    public int[] spiegelFarbeBinden(GL4 g) {
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, reflColorFbo);
        g.glViewport(0, 0, reflW, reflH);
        return new int[]{reflW, reflH};
    }

    /**
     * W7 Runde 1c: wie viel vom Spiegelbild im Regen im Dunst des Himmels aufgeht (0..0,9). Regen zerschlägt den Spiegel
     * mit Ringen, und zwischen Wasser und gespiegeltem Hang liegt derselbe Regen, den das Spiegelbild nicht kennt; ohne das
     * zeigte das Wasser in H14 die Hänge, die über ihm im Regen verschwunden sind.
     */
    public volatile double spiegelTruebe = 0;
    /**
     * W7 Runde 1d: wie glatt das Wasser für den Spiegel ist (1 still, kleiner mit dem Seegang). Der Spiegel ersetzt die
     * Himmelsspiegelung von com.dan.ocean mit dem Anteil F; im Seegang (Bft 3, Regen in H14) stand er als glatte Fläche
     * mit scharfer Kante neben den Wellen. 1 = wie bisher.
     */
    public volatile double spiegelGlatt = 1;

    /** W7: Tiefe des Spiegelbilds (auch während es entsteht). */
    public int spiegelTiefeRoh() { return reflDepth; }

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
        light.kielTexture = kielTex;   // W11b: Kielwasser der Boote
        light.kielRect0 = kielRect0.clone();
        light.kielRect1 = kielRect1.clone();
        light.detailTexture = ringTex;   // W7
        light.detailTile = ringKachel;
        light.detailStrength = ringTex != 0 ? ringStaerke : 0;
        light.detailRange = ringArt == 1 ? 40 : 60;
        light.detailMode = ringArt;
        light.detailCenter = ringMitte.clone();
        light.detailRoughness = ringArt == 1 ? ringRau[0] : 0;   // W7b Runde 5
        light.detailVariance = ringArt == 1 ? ringRau[1] : 0;
        light.detailShare = new double[]{ringRau[2], ringRau[3]};
        // W8b Runde 2: feines Feld und Karte der Schauer
        light.detail2Texture = ringArt == 1 ? feinTex : 0;
        light.detail2Tile = feinKante2;
        light.detail2Strength = ringStaerke;
        light.detail2Center = feinMitte2.clone();
        light.detail2Roughness = feinRau2;
        light.detailMask = ringArt == 1 ? schauerTex : 0;
        light.detailMaskRect = schauerRect.clone();
        // W8b: Spiegelruhe
        light.calm = ruheAn ? new double[]{ruheEin, ruheVoll, ruheStreuung, ruheFleck} : new double[]{0, 0, 0.45, 45};
        light.calmWind = windAmWasser;
        light.calmTime = ruheZeit;
        // W8a: die planare Spiegelung an die See (nur wenn das Spiegelbild gilt)
        boolean pl = spiegelImMeer && spiegel && spiegelJetzt && planarGueltig && reflTex != 0 && spiegelVP != null && planarAuge != null;
        light.planarTexture = pl ? reflTex : 0;
        light.planarDepth = pl ? reflDepth : 0;
        if (pl) {
            light.planarViewProj = spiegelVP;
            light.planarEye = planarAuge.clone();
            light.planarForward = planarVorn.clone();
            light.planarLevel = planarPegel;
            light.planarNear = nah;
            light.planarReversed = reversed;
            light.planarTanHalfFov = planarTan;
            // W8b: ohne die Trübe aus W7 Runde 1c – den Spiegel im Regen machen die Ringe und Facetten der See matt (5b.2),
            // nicht ein Schleier darüber; die Luft zwischen Auge und Wasser trübt der Niederschlag selbst
            light.planarMix = trubeAlt ? 1 - Math.max(0, Math.min(0.9, spiegelTruebe)) : 1;
            // verborgene Rauigkeit: still 0,25 % (Kräusel unter dem Gitter), im Seegang mehr [G]
            light.planarRough = 0.0025 + 0.02 * (1 - Math.max(0, Math.min(1, spiegelGlatt)));
            light.planarRoughPerM = 6e-6;
        }
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
        g.glActiveTexture(GL.GL_TEXTURE2);
        g.glBindTexture(GL.GL_TEXTURE_2D, reflDepth);
        g.glActiveTexture(GL.GL_TEXTURE0);
        spiegelShader.set(g, "uRefl", 0);
        spiegelShader.set(g, "uDepth", 1);
        spiegelShader.set(g, "uSpTiefe", 2);
        spiegelShader.set(g, "uSpNah", near);
        spiegelShader.set(g, "uSpSchritte", spiegelSchritte);
        { float[] v = spiegelVorn; spiegelShader.set(g, "uSpVorn", v[0], v[1], v[2]); }
        spiegelShader.set(g, "uSpiegelAn", spiegel && spiegelJetzt && reflTex != 0 && !spiegelImMeer ? 1 : 0);   // W8a: sonst spiegelt die See
        spiegelShader.set(g, "uSpTruebe", (float) Math.max(0, Math.min(0.9, spiegelTruebe)));
        spiegelShader.set(g, "uSpGlatt", (float) Math.max(0, Math.min(1, spiegelGlatt)));
        // W7b: Ringe des Regens im Spiegel
        g.glActiveTexture(GL.GL_TEXTURE0 + 27);
        g.glBindTexture(GL.GL_TEXTURE_2D, ringArt == 1 ? ringTex : 0);
        g.glActiveTexture(GL.GL_TEXTURE0);
        spiegelShader.set(g, "uSpRing", 27);
        spiegelShader.set(g, "uSpRingP", (float) Math.max(0.01, ringKachel), (float) ringStaerke, ringArt == 1 && ringTex != 0 && ringStaerke > 0 ? 1f : 0f, 40f);
        spiegelShader.set(g, "uSpRingQ", (float) ringMitte[0], (float) ringMitte[1]);
        spiegelShader.set(g, "uSpRingR", (float) ringRau[0], (float) ringRau[1], (float) ringRau[2], (float) ringRau[3]);
        spiegelShader.set(g, "uSpRingN", ringBild++);
        { float[] a = spiegelVP, j = jetztVP; if (a == null || j == null) a = j = new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
          spiegelShader.mat(g, "uSpiegelVP", a); spiegelShader.mat(g, "uJetztVP", j); }
        spiegelShader.set(g, "uNear", near);
        spiegelShader.set(g, "uFar", far);
        spiegelShader.set(g, "uWelleAn", welleRect != null ? 1 : 0);
        if (welleRect != null) spiegelShader.set(g, "uWelleRect", (float) welleRect[0], (float) welleRect[1], (float) welleRect[2], (float) welleRect[3]);
        // Gletschermilch aus com.dan.ocean (aus W2 herausgezogen): Grundton 0,35, zwei Quellen (Gilbert Inlet, Crillon Inlet) [G]
        com.dan.ocean.gl.MilkGl.bind(g, spiegelShader.id(), milch);
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glEnable(GL.GL_BLEND);
        g.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        g.glDisable(GL.GL_BLEND);
    }

    public String info() {
        return String.format(Locale.GERMAN, "%s · H_s %.2f m · Spiegel %s%s", status, ocean.state().totalHs(), spiegel ? (reflW + "×" + reflH) : "aus",
                kielTex != 0 ? " · Kielwasser" : "") + (ocean.hulls.isEmpty() ? "" : " · " + ocean.hulls.size() + " Rümpfe");
    }

    public void dispose(GL4 g) {
        if (ok) gl.dispose(g);
        spiegelShader.dispose(g);
        Gpu.deleteFbos(g, reflFbo, reflColorFbo);
        Gpu.deleteTextures(g, reflTex, reflDepth, maskTex);
    }
}
