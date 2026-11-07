package com.dan.lituya.gl;

import com.dan.cloud.Clouds;
import com.dan.cloud.gl.CloudGl;
import com.dan.fog.TerrainFog;
import com.dan.fog.gl.TerrainFogGl;
import com.dan.lituya.scene.Site;
import com.dan.lituya.wiring.Wetter;
import com.dan.precip.Precip;
import com.dan.precip.gl.PrecipGl;
import com.dan.rain.Rain;
import com.dan.rain.gl.RainGl;
import com.dan.sky.Sky;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.Locale;
import java.util.function.DoubleBinaryOperator;

/**
 * Werkbuch Welle W7 „Wetter“ (L10, 5a): das Wetter im Bild. Liest den {@link Wetter.Zustand} und zeichnet mit den
 * Bausteinen aus {@code Renderer.jar}:
 * <ul>
 * <li><b>Wolken</b> ({@code com.dan.cloud}, seit W7 mit Gelände): die Schicht der Lage, dazu die Bank am Hang, die
 * Kappe über dem Fairweather, Quellwolken über der Kette statt über dem kalten Meer und Lücken im Lee. Gelände aus dem
 * groben Gitter von {@link Licht} (66 km, 64 m). Schattenkarte über die ganze Szene (72 km, 384²) für Gelände, Welle,
 * Wasser, Wald und Bäche ({@code sunAt} in {@code common.glsl}, {@code sonnenkarte.comp}); die Wolken im Rundumbild
 * spiegeln sich im Wasser und färben Himmelslicht und Dunst.</li>
 * <li><b>Seenebel</b> ({@code com.dan.fog.TerrainFog}, seit W7 mit Vorstoß): ein Nebelmeer bis zur Obergrenze der Lage,
 * das von See durch den Eingang in die Bucht vordringt (Weg durch Gelände unter der Obergrenze, 26 km um den Kern).</li>
 * <li><b>Regen</b> (seit W7b {@code com.dan.precip}, vorher {@code com.dan.rain}): Tropfen in echter Dichte um das Auge,
 * die mit Luftwiderstand durch den böigen Wind fallen, schwingen und auf Wasser und Fels aufschlagen (Kronen, Strahlen,
 * Tröpfchen), Schichten bis 60 m, Vorhänge bis 8 km unter den Wolken mit der Optik des Tropfens (Regenbogen, wo die
 * Sonne darauf fällt), echte Kapillarwellen auf dem Wasser (über {@link MeerGl} an {@code com.dan.ocean}); im Wald
 * fällt ein Teil frei durch, der Rest tropft groß von den Kronen. Belichtung {@link #verschluss} (1/250 s wie das Auge).</li>
 * </ul>
 * Ohne Wetter (Lage „klar“, 1958 nach den Zeugen) zeichnet nichts davon, und Licht, Wasser und Sicht bleiben wie vorher.
 */
public final class WetterGl {
    /** Texturstelle der Wolkenschatten in den Shadern ({@code wolken.glsl}). */
    public static final int UNIT_WOLKEN = 29;
    /** Seenebel: Kante der Karte um den Kern (m). */
    public static final double NEBEL_KANTE = 26_000;

    public volatile boolean wolkenAn = !Boolean.getBoolean("lituya.ohneWolken"), nebelAn = !Boolean.getBoolean("lituya.ohneNebel"),
            regenAn = !Boolean.getBoolean("lituya.ohneRegen");
    /** Für Proben (Tempo „schalter“): Stufe der Wolken (−1 nach der Szene, 0 flach, 1 Mittel, 2 Hoch, 3 Ultra), Bank und Kappe an, Anteil des letzten Bilds (NaN: nach dem Zeitraffer). */
    public volatile int wolkenStufe = -1;
    public volatile boolean wolkenBank = true, wolkenKappe = true;
    public volatile double verlaufFest = Double.NaN;
    /** Ferne Wolken gehen in den Dunst über statt durchsichtig zu werden (1) [G]. */
    public volatile double dunstHalten = 1;
    /**
     * Runde 1b/1c, je Bild gesetzt (darum als Schalter für Proben): Grundform und Feinheit der Wolken mit stetiger
     * Ableitung (ohne Gitter aus Streifen in der Ferne, Probe 711), beim Mischen nur dieselbe Geometrie (kein heller Saum
     * an Kämmen und Baumspitzen), Schritte nach der Brennweite (bis 4×), Quellen auf dem Nebelmeer (± m) [G].
     */
    public volatile boolean wolkenGlatt = true, wolkenSaumfrei = true;
    public volatile double wolkenTele = 4, nebelQuellen = 35;

    public final Clouds wolken = new Clouds();
    final CloudGl wolkenGl = new CloudGl();
    public final TerrainFog nebel = new TerrainFog();
    final TerrainFogGl nebelGl = new TerrainFogGl();
    public final Rain regen = Rain.moderate();
    final RainGl regenGl = new RainGl();
    /** W7b: der neue Niederschlag (com.dan.precip); false (oder -Dlituya.alterRegen=true) = der Regen bis W7 (com.dan.rain). */
    public volatile boolean regenNeu = !Boolean.getBoolean("lituya.alterRegen");
    /** W7b (WF26): Belichtungszeit des Regens in s (Tempo, Bildbank, Regie „verschluss“); 1/250 wie das Auge [G]. */
    public volatile double verschluss = 1 / 250.0;
    /** W7b: Zeitmaß des Regens (1 Echtzeit, 0,05 Zeitlupe), Tempo „zeitlupe“. */
    public volatile double zeitlupe = 1;
    /** W7b: Stufe des Niederschlags für Proben (−1 nach der Szene, 0 LOW … 3 ULTRA). */
    public volatile int regenStufe = -1;
    /** W7b: Gelände (m über NAVD88, NaN ohne Daten) und halbe Kronenhöhe des Walds (m, 0 ohne) für den Regen um das Auge. */
    public volatile DoubleBinaryOperator grundHoehe, kronenHalb;
    /** W7b: Blickrichtung und Bildwinkel (Grad, senkrecht) für das Fenster der Wellen auf dem Wasser. */
    public volatile double[] blick;
    public volatile double blickFovy = 50;
    /**
     * W7b: zeitliche Kantenglättung an (der Renderer setzt es je Bild). Dann zeichnet {@link #zeichnen} nur die Vorhänge;
     * Schichten, Tropfen und Spritzer kommen nach der Glättung ({@link #nachGlaettung}), sonst mischt der Verlauf (ein
     * Zehntel je Bild) die bewegten Spuren weg – das war der Hauptgrund, warum der Regen bis W7 so schwach war.
     */
    public volatile boolean regenNachTaa;
    /** W7b: Bilder, seit die Uhr steht (Standbild: 1,5 s einschwingen, dann zwei Schritte zu 1/60 s, dann still). */
    private int standBilder;
    private final java.util.Map<Integer, Integer> regenFbos = new java.util.HashMap<>();
    public final Precip niederschlag = Precip.moderate();
    final PrecipGl niederschlagGl = new PrecipGl();
    private boolean niederschlagOk;
    private String niederschlagFehler = "";
    private volatile double pegelJetzt;
    private Precip.Ground grund;
    private Licht lichtJetzt;

    private boolean ok;
    private String status = "Wetter: noch nicht gestartet";
    private Clouds.Snapshot snap;
    private final CloudGl.Light licht = new CloudGl.Light();
    private Wetter.Zustand z;
    private double sonneAmAuge = 1, sonneHoch;
    private com.dan.lituya.scene.Wetterlage nebelFuer;
    private double[] nebelRect;
    private int tiefeKopie, tiefeFbo, tiefeW, tiefeH;
    private double[] sonne = {0, 1, 0};
    private Sky.State st;
    private double pre = 1;
    /** Wie viel heller der Himmel unter den Wolken ist (für die Belichtung): 1 klar. */
    private double hell = 1;

    public String status() { return status; }
    public boolean bereit() { return ok; }
    public Clouds.Snapshot wolkenStand() { return snap; }

    public void init(GL4 gl) {
        try {
            wolkenGl.init(gl);
            nebelGl.init(gl);
            regenGl.init(gl);
            regenGl.crownBand = 0.4;   // auf Kies und Fels spritzt es nicht weiß (wie Sylt)
            regenGl.curtainSmooth = true;   // Runde 1b: Vorhänge ohne senkrechte Balken (Clip, 48 s)
            double[] sz = Site.SZENE.scene(), kf = Site.KERN.scene();
            wolkenGl.shadowSize = 72_000;
            wolkenGl.shadowN = 384;
            wolkenGl.shadowCenter = new double[]{(sz[0] + sz[2]) / 2, (sz[1] + sz[3]) / 2};
            wolken.quality = Clouds.Quality.MEDIUM;
            wolken.maxDistance = 75_000;
            wolken.terrainSmooth = 700;
            double cx = (kf[0] + kf[2]) / 2, cz = (kf[1] + kf[3]) / 2;
            nebelRect = new double[]{cx - NEBEL_KANTE / 2, cz - NEBEL_KANTE / 2, NEBEL_KANTE};
            nebel.mapRect = nebelRect;
            nebel.quality = TerrainFog.Quality.MEDIUM;
            nebel.seaRough = 30;
            nebel.bankSize = 2600;
            nebel.burnRate = 40;   // Seenebel über kaltem Wasser hält sich (ein Sommer-Vormittag am Golf von Alaska) [G]
            nebel.advanceSoft = 450;
            nebel.advanceFingers = 0.6;
            nebel.advancePass = 30;
            nebel.billowSize = 240;   // Runde 1d: kleiner und höher (35 m), von 420 m aus waren 22 m auf 300 m nicht zu sehen [G]
            regen.quality = Rain.Quality.MEDIUM;
            niederschlag.quality = Precip.Quality.MEDIUM;
            try {
                niederschlagGl.init(gl);
                niederschlagOk = true;
            } catch (RuntimeException e) {
                niederschlagOk = false;
                niederschlagFehler = e.getMessage();
                System.err.println("LituyaBay1958: Niederschlag (com.dan.precip) nicht verfügbar, alter Regen: " + e.getMessage());
            }
            ok = true;
            status = "Wetter: Wolken (" + wolkenGl.info() + "), Nebel, Regen bereit";
        } catch (RuntimeException e) {
            ok = false;
            status = "Wetter: nicht verfügbar (" + e.getMessage() + ")";
            System.err.println("LituyaBay1958: " + status);
        }
    }

    /** Stufe der Szene: Wolken und Nebel in einer Auflösung, die ins Budget passt (Echtzeit: Mittel, Ultra: Hoch). */
    public void stufe(Quality q) {
        wolken.quality = q == Quality.ULTRA ? Clouds.Quality.HIGH : q == Quality.NIEDRIG ? Clouds.Quality.FLAT : Clouds.Quality.MEDIUM;
        nebel.quality = q == Quality.ULTRA ? TerrainFog.Quality.HIGH : q == Quality.NIEDRIG ? TerrainFog.Quality.LOW : TerrainFog.Quality.MEDIUM;
        regen.quality = q == Quality.ULTRA ? Rain.Quality.HIGH : q == Quality.NIEDRIG ? Rain.Quality.LOW : Rain.Quality.MEDIUM;
        niederschlag.quality = q == Quality.ULTRA ? Precip.Quality.HIGH : q == Quality.NIEDRIG ? Precip.Quality.LOW : Precip.Quality.MEDIUM;
    }

    /** W7b: ob in diesem Bild der neue Niederschlag rechnet und zeichnet. */
    boolean neuJetzt() { return regenNeu && niederschlagOk; }

    /** W7b: worauf der Regen um das Auge fällt (Gelände über dem Pegel, Wasser darunter, Kronendach im Wald [G]). */
    private Precip.Ground grund() {
        if (grund == null && grundHoehe != null) {
            grund = new Precip.Ground() {
                @Override public double height(double x, double z) {
                    double g = grundHoehe.applyAsDouble(x, z), p = pegelJetzt;
                    return Double.isNaN(g) ? p : Math.max(g, p);
                }
                @Override public boolean water(double x, double z) {
                    double g = grundHoehe.applyAsDouble(x, z);
                    return Double.isNaN(g) || g < pegelJetzt - 0.1;
                }
                @Override public double canopy(double x, double z) {
                    DoubleBinaryOperator k = kronenHalb;
                    double c = k == null ? 0 : k.applyAsDouble(x, z);
                    return c > 5 ? height(x, z) + 2 * c : Double.NaN;
                }
            };
        }
        return grund;
    }

    /** Das Gelände für die Wolken (grobes Gitter aus {@link Licht}: Höhen über NAVD88, Meer 0). */
    public void gelaende(float[] hoehen, double[] rect, int n) {
        if (hoehen == null || wolken.terrain != null) return;
        wolken.terrain = new Clouds.Terrain(rect[0], rect[1], rect[2], n, hoehen);
    }

    /** Der Boden für den Seenebel (Kachelstapel; Meer und Lücken −30 m). */
    public void nebelGrund(DoubleBinaryOperator stapel) {
        if (nebel.ground != null || stapel == null) return;
        nebel.ground = (x, zz) -> { double h = stapel.applyAsDouble(x, zz); return Double.isNaN(h) ? -30 : h; };
        nebel.mapVersion++;
    }

    /** Strahldichte des Himmels in Richtung d (vorbelichtet), höchstens dreimal so hell wie das Himmelslicht (wie hazeColor im Shader). */
    private void dunst(double[] d, double[] out) {
        double[] c = st.radiance(d);
        double cap = 3 * (0.2126 * st.skyIrradiance[0] + 0.7152 * st.skyIrradiance[1] + 0.0722 * st.skyIrradiance[2]) / Math.PI + 1e-12;
        double lum = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2], f = lum > cap ? cap / lum : 1;
        for (int k = 0; k < 3; k++) out[k] = c[k] * f * pre;
    }

    /** Licht und Himmel dieses Bilds ({@link Licht}), damit Wolken, Nebel und Regen dieselbe Sonne sehen. */
    private void lichtSetzen(Licht l) {
        st = l.state();
        pre = l.pre();
        if (st == null) return;
        boolean tag = st.sunDir[1] > -0.12;
        double[] dir = tag ? st.sunDir : st.moonDir;
        sonne = st.sunDir.clone();
        licht.dir = dir.clone();
        for (int i = 0; i < 8; i++) {
            double[] c = tag ? st.sunColorAt(CloudGl.LIGHT_HEIGHTS[i]) : st.moonColorAt(CloudGl.LIGHT_HEIGHTS[i]);
            for (int k = 0; k < 3; k++) licht.colorAt[i][k] = c[k] * pre;
        }
        for (int k = 0; k < 3; k++) {
            licht.ambTop[k] = st.skyZenith[k] * pre;
            // Licht vom Boden (Meer und dunkler Wald, Albedo um 0,08) [G]
            licht.ambBot[k] = (st.skyIrradiance[k] + st.sunColor[k] * Math.max(0, st.sunDir[1])) * 0.08 / Math.PI * pre;
        }
        licht.haze = 0.45 * st.groundExtinction;
        // Dunst wie hazeColor() in common.glsl (der Himmel 3° über der Kimm, höchstens dreimal so hell wie das Himmelslicht):
        // ferne Wolken werden zu ihm wie das Gelände hinter ihnen; quer zum Gestirn, zu ihm hin und von ihm weg
        double ax = dir[0], az = dir[2], al = Math.hypot(ax, az);
        if (al < 1e-6) { ax = 1; az = 0; } else { ax /= al; az /= al; }
        double ce = Math.cos(Math.toRadians(3)), se = Math.sin(Math.toRadians(3));
        dunst(new double[]{-az * ce, se, ax * ce}, licht.hazeColor);
        dunst(new double[]{ax * ce, se, az * ce}, licht.hazeToward);
        dunst(new double[]{-ax * ce, se, -az * ce}, licht.hazeAway);
        licht.hazeKeep = dunstHalten;
        sonneHoch = st.sunDir[1];
    }

    /**
     * Je Bild vor dem Zeichnen: Wolken, Nebel und Regen nach dem Zustand, Schattenkarte der Wolken, Karte des Nebels,
     * Ringkarte des Regens. standbild: deterministisch nach der Zeit in der Lage (Bildbank).
     */
    public void vorbereiten(GL4 gl, Wetter.Zustand zz, Licht l, double[] eye, double dt, boolean standbild, double pegel) {
        z = zz;
        if (!ok || zz == null || !zz.an() || !l.aktiv()) { snap = null; hell = 1; sonneAmAuge = 1; return; }
        lichtSetzen(l);
        // Wolken
        wolken.type = zz.wolkenart();
        wolken.oktas = zz.achtel();
        wolken.baseHeight = zz.basis();
        wolken.highCover = zz.hoch();
        wolken.precipitation = zz.niederschlag();
        // Wind in Wolkenhöhe: stärker und nach rechts gedreht (Ekman) [G]
        wolken.windSpeed = Math.max(3, zz.wind() * 1.6);
        wolken.windFromDeg = zz.windVon() + 15;
        if (wolkenStufe >= 0 && wolkenStufe < Clouds.Quality.values().length) wolken.quality = Clouds.Quality.values()[wolkenStufe];
        wolken.bank = wolkenBank ? zz.bank() : 0;
        wolken.bankBase = zz.bankUnten();
        wolken.bankTop = zz.bankOben();
        wolken.bankReach = zz.bankWeit();
        wolken.cap = wolkenKappe ? zz.kappe() : 0;
        wolken.capBase = zz.kappeAb();
        wolken.convection = zz.konvektion();
        wolken.lee = zz.lee();
        wolken.shadowHeight = 0;
        wolken.smoothNoise = wolkenGlatt;
        wolkenGl.geometryHistoryTest = wolkenSaumfrei;
        wolkenGl.teleMax = wolkenTele;
        nebel.billows = nebelQuellen;
        if (standbild) wolken.setTime(600 + zz.seit()); else wolken.step(dt);
        snap = wolkenAn ? wolken.snapshot() : null;
        boolean himmel = snap != null && zz.wolken();
        if (himmel) {
            wolkenGl.update(gl, snap, licht, eye);
            sonneAmAuge = Clouds.shadow(snap, eye[0], Math.max(eye[1], 2), eye[2], sonne);
            hell = wolken.brightnessFactor();
            // Runde 1c: Sonne für Wald, Fälle und Boote (eine Farbe für alle): das Mittel über 3 × 3 Orte um das Auge
            // (± 1,5 km, 30 m über dem Meer), alle 12 Bilder, im Standbild jedes. Vorher schien auf sie im Regen die volle Sonne
            if (standbild || ++waldSonneAlter >= 12) {
                double sum = 0;
                for (int j = -1; j <= 1; j++)
                    for (int i = -1; i <= 1; i++) sum += Clouds.shadow(snap, eye[0] + i * 1500, 30, eye[2] + j * 1500, sonne);
                waldSonneWert = sum / 9;
                waldSonneAlter = 0;
            }
        } else { sonneAmAuge = 1; hell = 1; waldSonneWert = 1; }
        // Seenebel
        boolean seen = nebelAn && zz.seenebel();
        nebel.windSpeed = Math.max(0.5, zz.wind());
        nebel.windFromDeg = zz.windVon();
        nebel.seaBaseTarget = seen ? -40 : Double.NaN;
        nebel.seaTopTarget = seen ? zz.nebelOben() : Double.NaN;
        nebel.seaVisibility = zz.nebelSicht() / Math.max(0.05, zz.nebel());
        nebel.wisps = zz.nebelSchwaden();
        nebel.heating = Math.max(0, sonneHoch) * sonneAmAuge * 0.8;
        // Vorstoß: beim Beginn der Lage 2 km vor dem Eingang auf See, dann mit der Geschwindigkeit der Lage [G]. Geht die
        // Lage danach in eine ohne Nebel über, bleibt die Front stehen, während der Nebel sich auflöst (sonst läge er für
        // die Zeit des Übergangs plötzlich überall)
        if (seen && !Double.isNaN(zz.vorstoss())) {
            if (nebelFuer != zz.lage() || standbild) {
                nebel.advance = vorstossStart() + zz.vorstoss() * Math.max(0, zz.seit());
                nebelFuer = zz.lage();
            }
            nebel.advanceRate = zz.vorstoss();
        } else if (seen && nebelFuer != null) {
            nebel.advanceRate = 0;
        } else {
            nebel.advance = Double.NaN;
            nebel.advanceRate = 0;
            nebelFuer = null;
        }
        if (standbild) { nebel.settle(); nebel.step(0); } else nebel.step(dt);
        if (seen) nebelGl.update(gl, nebel);
        // Regen
        regen.rate = regenAn ? zz.regen() : 0;
        regen.windSpeed = zz.wind();
        regen.windFromDeg = zz.windVon();
        regen.gust = zz.regie().gustiness();
        regen.cloudBase = Math.max(150, zz.basis());
        regen.eyeX = eye[0];
        regen.eyeY = eye[1];
        regen.eyeZ = eye[2];
        regen.waterLevel = pegel;
        final Clouds.Snapshot s0 = snap;
        final double ns = Math.max(0.05, zz.niederschlag());
        regen.curtain = s0 == null ? null : (x, zq) -> Math.min(1, Clouds.rain(s0, x, zq) / ns);
        if (regen.rate > 0.02 && !neuJetzt()) {
            regen.step(standbild ? 1 / 30.0 : dt);
            regenGl.update(gl, regen.snapshot());
        }
        // W7b: der neue Niederschlag (dieselbe Lage); Physik höchstens 1/15 s je Bild (im Zeitraffer bleibt Regen Regen)
        lichtJetzt = l;
        if (neuJetzt()) {
            Precip n = niederschlag;
            n.rate = regen.rate;
            n.windSpeed = regen.windSpeed;
            n.windFromDeg = regen.windFromDeg;
            n.gust = regen.gust;
            n.cloudBase = regen.cloudBase;
            n.eyeX = eye[0];
            n.eyeY = eye[1];
            n.eyeZ = eye[2];
            n.waterLevel = pegel;
            pegelJetzt = pegel;
            n.curtain = s0 == null ? null : (x, zq) -> Math.min(1, Clouds.rain(s0, x, zq) / ns);
            n.ground = grund();
            n.shutter = verschluss;
            n.timeScale = zeitlupe;
            if (regenStufe >= 0 && regenStufe < Precip.Quality.values().length) n.quality = Precip.Quality.values()[regenStufe];
            // Standbild: erst 1,5 s in Schritten zu 0,15 s einschwingen (Wellen, Spritzer), dann zwei echte Schritte zu
            // 1/60 s, dann steht alles still (die Kantenglättung sammelt ein ruhendes Bild, wie bei angehaltener Uhr)
            standBilder = standbild ? standBilder + 1 : 0;
            double sdt = standBilder == 0 ? Math.min(dt, 1 / 15.0) : standBilder <= 10 ? 0.15 : standBilder <= 12 ? 1 / 60.0 : 0;
            if (standBilder == 1) niederschlagGl.reseed = true;
            if (n.rate > 0.02) {
                n.step(sdt * zeitlupe);
                double[] b = blick;
                niederschlagGl.aim = b == null ? null : b.clone();
                niederschlagGl.aimFovy = blickFovy;
                Renderer.teil("Regen rechnen");
                niederschlagGl.update(gl, n.snapshot(), niederschlagLicht());
                Renderer.teilEnde("Regen rechnen");
            }
        }
    }

    /** Weg des Vorstoßes 2 km vor dem Eingang (zwischen La Chaussee Spit und Harbor Point), wie TerrainFog ihn außen zählt. */
    double vorstossStart() {
        double ex = (Site.CHAUSSEE.x() + Site.HARBOR_POINT.x()) / 2, ez = (Site.CHAUSSEE.z() + Site.HARBOR_POINT.z()) / 2;
        double[] to = nebel.advanceTo(), r = nebelRect;
        double cx = r[0] + r[2] / 2, cz = r[1] + r[2] / 2, half = r[2] / 2 * (Math.abs(to[0]) + Math.abs(to[1]));
        return Math.max(0, (ex - cx) * to[0] + (ez - cz) * to[1] + half) - 2000;
    }

    /** Ist die Karte des Seenebels gebaut (mit Weg des Vorstoßes)? */
    public boolean nebelBereit() { return nebelGl.mapReady() && nebel.ground != null; }

    /** Runde 1c: liegt in diesem Bild Seenebel (mit Karte)? */
    public boolean nebelJetzt() { return ok && z != null && z.an() && nebelAn && z.seenebel() && nebelGl.mapReady(); }

    /** Ob in diesem Bild Wolken gezeichnet werden. */
    public boolean wolkenJetzt() { return snap != null && z != null && z.wolken(); }

    /** W7 Runde 1b: Wolken (seit 1c auch der Seenebel) im Spiegelbild über dem gespiegelten Gelände (Bank am Hang, Decke vor den Gipfeln). */
    public volatile boolean wolkenImSpiegel = !Boolean.getBoolean("lituya.ohneSpiegelWolken");

    /**
     * W7 Runde 1b: die Wolken ins gebundene Spiegelbild (Ziel ohne Tiefe), nur über dem gespiegelten Gelände: der Himmel
     * im Spiegel kommt aus dem Rundumbild, das die Wolken schon trägt. vp und inv: gespiegelte Projektion, eye: das Auge
     * unter dem Spiegel; eigene zeitliche Mischung (Ansicht 1). Ohne das zeigte das Wasser im Regen die Gipfel, die am
     * Himmel in der Decke verschwunden sind (Befund Runde 1, H14 und Clip).
     */
    public void spiegel(GL4 gl, float[] vp, float[] inv, double[] eye, boolean reversed, int depthTex, int w, int h, double pegel) {
        spiegel(gl, vp, inv, eye, reversed, depthTex, w, h, pegel, false);
    }

    /** W8a: voll = true auch vor dem gespiegelten Himmel (das ganze Spiegelbild, die See spiegelt selbst). */
    public void spiegel(GL4 gl, float[] vp, float[] inv, double[] eye, boolean reversed, int depthTex, int w, int h, double pegel, boolean voll) {
        if (!wolkenImSpiegel || depthTex == 0 || z == null || !z.an() || !ok) return;
        // Runde 1c: auch der Seenebel über dem gespiegelten Gelände (volle Auflösung des Spiegelbilds, ohne Zittern); sein
        // Meer beginnt hier am Spiegel, denn der Strahl kommt von unter dem Wasser (sonst Nebel, den es nicht gibt)
        if (nebelAn && z.seenebel() && nebelGl.mapReady()) {
            TerrainFogGl.View f = new TerrainFogGl.View();
            f.invViewProj = inv;
            f.eye = eye.clone();
            f.reversedZ = reversed;
            f.width = w;
            f.height = h;
            f.depthTexture = depthTex;
            f.maxDistance = 80_000;
            f.curvature = 1 / (2 * Site.EARTH_R_OPTICAL);
            f.scale = 1;
            f.geometryOnly = !voll;
            TerrainFog.Snapshot fs = nebel.snapshot();
            nebelGl.draw(gl, fs.sea() ? fs.withSeaBase(Math.max(fs.seaBase(), pegel)) : fs, f, nebelLicht());
        }
        if (!wolkenJetzt()) return;
        CloudGl.View v = new CloudGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.eye = eye.clone();
        v.reversedZ = reversed;
        v.width = w;
        v.height = h;
        v.depthTexture = depthTex;
        v.slot = 1;
        v.geometryOnly = !voll;
        wolkenGl.draw(gl, snap, licht, v);
    }

    /** Die Wolken ins gebundene Rundumbild von {@link Licht} (Spiegelung, Himmelslicht, Dunst). */
    public void panorama(GL4 gl, double[] eye) {
        if (!wolkenJetzt()) return;
        wolkenGl.drawPanorama(gl, snap, licht, eye);
    }

    /**
     * Nach Gelände, Wald, Wasser und Fällen, vor dem Staub: Wolken (mit der Tiefe der Szene), Seenebel, Regen. Das HDR-Ziel
     * ohne Tiefe ist gebunden; der Regen zeichnet seine Streifen mit Tiefentest ins Ziel mit Tiefe (Kopie der Tiefe für Vorhänge und Krönchen).
     */
    public void zeichnen(GL4 gl, int hdrColorFbo, int hdrFbo, float[] vp, float[] inv, double[] eye, double[] fwd, boolean reversed,
                         int depthTex, int w, int h, double fovy, double near, double far, int bild) {
        if (z == null || !z.an() || !ok) return;
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
        gl.glViewport(0, 0, w, h);
        if (wolkenJetzt()) {
            CloudGl.View v = new CloudGl.View();
            v.viewProj = vp;
            v.invViewProj = inv;
            v.eye = eye.clone();
            v.reversedZ = reversed;
            v.width = w;
            v.height = h;
            v.depthTexture = depthTex;
            Renderer.teil("Wolken");
            wolkenGl.draw(gl, snap, licht, v);
            Renderer.teilEnde("Wolken");
        }
        if (nebelAn && z.seenebel() && nebelGl.mapReady()) {
            TerrainFogGl.View v = new TerrainFogGl.View();
            v.invViewProj = inv;
            v.eye = eye.clone();
            v.reversedZ = reversed;
            v.width = w;
            v.height = h;
            v.depthTexture = depthTex;
            v.frame = bild;
            v.maxDistance = 80_000;
            v.curvature = 1 / (2 * Site.EARTH_R_OPTICAL);
            Renderer.teil("Nebel");
            nebelGl.draw(gl, nebel.snapshot(), v, nebelLicht());
            Renderer.teilEnde("Nebel");
        }
        if (regenAn && regen.rate > 0.02 && neuJetzt()) {
            Renderer.teil("Regen Tiefe");
            tiefeKopieren(gl, hdrFbo, w, h);
            Renderer.teilEnde("Regen Tiefe");
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glDepthFunc(reversed ? GL.GL_GEQUAL : GL.GL_LEQUAL);
            PrecipGl.View v = new PrecipGl.View();
            v.viewProj = vp;
            v.invViewProj = inv;
            v.eye = eye.clone();
            v.forward = fwd.clone();
            v.reversedZ = reversed;
            v.near = near;
            v.far = far;
            v.fovyDeg = fovy;
            v.width = w;
            v.height = h;
            v.depthTexture = tiefeKopie;
            Renderer.teil("Regen");
            niederschlagGl.draw(gl, v, 1.0, regenNachTaa ? PrecipGl.CURTAIN : PrecipGl.ALL);
            Renderer.teilEnde("Regen");
            gl.glDisable(GL.GL_DEPTH_TEST);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
        } else if (regenAn && regen.rate > 0.02) {
            tiefeKopieren(gl, hdrFbo, w, h);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glDepthFunc(reversed ? GL.GL_GEQUAL : GL.GL_LEQUAL);
            RainGl.View v = new RainGl.View();
            v.viewProj = vp;
            v.invViewProj = inv;
            v.eye = eye.clone();
            v.forward = fwd.clone();
            v.reversedZ = reversed;
            v.near = near;
            v.far = far;
            v.fovyDeg = fovy;
            v.width = w;
            v.height = h;
            v.depthTexture = tiefeKopie;
            Renderer.teil("Regen");
            regenGl.draw(gl, v, regenLicht(), 1.0);
            Renderer.teilEnde("Regen");
            gl.glDisable(GL.GL_DEPTH_TEST);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
        }
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
    }

    /**
     * W7b: Schichten, Tropfen und Spritzer nach der zeitlichen Kantenglättung ins geglättete Bild (bild, HDR), mit der
     * Tiefe der Szene als Tiefe (Test, ohne Schreiben) und der Kopie für die Schichten; vp und inv ohne Versatz. Danach
     * sind die Mip-Stufen des Bilds neu (für das Glühen).
     */
    public void nachGlaettung(GL4 gl, int bild, int depthTex, float[] vp, float[] inv, double[] eye, double[] fwd, boolean reversed,
                              int w, int h, double fovy, double near, double far) {
        if (!regenNachTaa || z == null || !z.an() || !ok || !regenAn || regen.rate <= 0.02 || !neuJetzt() || tiefeKopie == 0) return;
        Integer fbo = regenFbos.get(bild);
        if (fbo == null) {
            if (regenFbos.size() > 4) { for (int f : regenFbos.values()) Gpu.deleteFbos(gl, f); regenFbos.clear(); }
            fbo = Gpu.fbo(gl, bild, depthTex);
            regenFbos.put(bild, fbo);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glDepthFunc(reversed ? GL.GL_GEQUAL : GL.GL_LEQUAL);
        PrecipGl.View v = new PrecipGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.eye = eye.clone();
        v.forward = fwd.clone();
        v.reversedZ = reversed;
        v.near = near;
        v.far = far;
        v.fovyDeg = fovy;
        v.width = w;
        v.height = h;
        v.depthTexture = tiefeKopie;
        Renderer.teil("Regen Schichten");
        niederschlagGl.draw(gl, v, 1.0, PrecipGl.LAYERS);
        Renderer.teilEnde("Regen Schichten");
        Renderer.teil("Regen Tropfen");
        niederschlagGl.draw(gl, v, 1.0, PrecipGl.DROPS);
        Renderer.teilEnde("Regen Tropfen");
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, bild);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    /** Kopie der Tiefe für den Regen (Vorhänge und Krönchen lesen sie, die Streifen testen gegen das Original). */
    private void tiefeKopieren(GL4 gl, int hdrFbo, int w, int h) {
        if (tiefeKopie == 0 || tiefeW != w || tiefeH != h) {
            Gpu.deleteFbos(gl, tiefeFbo);
            Gpu.deleteTextures(gl, tiefeKopie);
            tiefeKopie = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
            gl.glBindTexture(GL.GL_TEXTURE_2D, tiefeKopie);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            tiefeFbo = Gpu.depthFbo(gl, tiefeKopie);
            tiefeW = w;
            tiefeH = h;
        }
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, hdrFbo);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, tiefeFbo);
        gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
    }

    /** Licht im Nebel: Sonne durch die Wolken am Auge gedämpft, Himmel unter Wolken grauer und dunkler [G]. */
    private TerrainFogGl.Light nebelLicht() {
        TerrainFogGl.Light l = new TerrainFogGl.Light();
        if (st == null) return l;
        double bed = Math.min(1, z.achtel() / 8);
        for (int k = 0; k < 3; k++) {
            l.sunColor[k] = st.sunColor[k] * pre * sonneAmAuge;
            l.moonColor[k] = st.moonColor[k] * pre;
            double amb = st.skyIrradiance[k] / Math.PI * pre, grau = (st.skyIrradiance[0] + st.skyIrradiance[1] + st.skyIrradiance[2]) / 3 / Math.PI * pre;
            double a = (amb + (grau - amb) * bed) * (1 - 0.55 * bed);
            l.ambientTop[k] = a;
            l.ambientHorizon[k] = a * 1.15;
        }
        l.sunDir = st.sunDir.clone();
        l.moonDir = st.moonDir.clone();
        return l;
    }

    private RainGl.Light regenLicht() {
        RainGl.Light l = new RainGl.Light();
        if (st == null) return l;
        double bed = Math.min(1, z.achtel() / 8);
        for (int k = 0; k < 3; k++) {
            l.sunColor[k] = st.sunColor[k] * pre * sonneAmAuge;
            l.moonColor[k] = st.moonColor[k] * pre;
            double amb = st.skyIrradiance[k] / Math.PI * pre * (1 - 0.55 * bed);
            l.skyTop[k] = amb;
            l.skyHorizon[k] = amb * 1.2;
        }
        l.sunDir = st.sunDir.clone();
        l.moonDir = st.moonDir.clone();
        l.fogDistance = 1 / Math.max(1e-7, 0.45 * st.groundExtinction);
        return l;
    }

    /**
     * W7b: Licht für den Niederschlag: Sonne und Mond als Bestrahlung, Himmel unter den Wolken (wie für den alten Regen),
     * Grund (Meer und dunkler Wald, Albedo 0,06 [G]), das Rundumbild mit den Wolken, die Sonne am Auge und die
     * Schattenkarte der Wolken (für Schauer, auf die die Sonne fällt), Dunst.
     */
    private PrecipGl.Light niederschlagLicht() {
        PrecipGl.Light l = new PrecipGl.Light();
        if (st == null || z == null) return l;
        double bed = Math.min(1, z.achtel() / 8);
        DoubleBinaryOperator gh = grundHoehe;
        double ge = gh == null ? Double.NaN : gh.applyAsDouble(niederschlag.eyeX, niederschlag.eyeZ);
        boolean ueberWasser = Double.isNaN(ge) || ge < pegelJetzt - 0.1;
        for (int k = 0; k < 3; k++) {
            l.sunIrradiance[k] = st.sunColor[k] * pre;
            l.moonIrradiance[k] = st.moonColor[k] * pre;
            double amb = st.skyIrradiance[k] / Math.PI * pre * (1 - 0.55 * bed);
            l.skyTop[k] = amb;
            l.skyHorizon[k] = amb * 1.2;
            // unter der Kimm: über Wasser vor allem der streifend gespiegelte Himmel (Fresnel um 0,25 im Mittel des
            // Vorwärtskegels), über Land und Wald der dunkle Grund (Albedo 0,06) [G]
            l.ground[k] = ueberWasser ? amb * 1.2 * 0.25 + st.skyIrradiance[k] * 0.01 / Math.PI * pre
                    : (st.skyIrradiance[k] * (1 - 0.55 * bed) + st.sunColor[k] * Math.max(0, st.sunDir[1]) * sonneAmAuge) * 0.06 / Math.PI * pre;
            l.fogColor[k] = licht.hazeColor[k];
        }
        l.sunDir = st.sunDir.clone();
        l.moonDir = st.moonDir.clone();
        l.sunVisible = sonneAmAuge;
        Licht li = lichtJetzt;
        l.panorama = li == null ? 0 : li.panoTextur();
        if (wolkenJetzt()) {
            l.shadowTexture = wolkenGl.shadowTexture();
            double[] r = wolkenGl.shadowRect();
            l.shadowRect = new double[]{r[0], r[1], r[2], snap.shadowHeight()};
        }
        l.fogDistance = 1 / Math.max(1e-7, 0.45 * st.groundExtinction);
        return l;
    }

    /** Die Uniforms von {@code wolken.glsl} (Wolkenschatten, Nässe) für ein gebundenes Programm. */
    public void uniforms(GL4 gl, Shader s) {
        boolean an = wolkenJetzt();
        s.set(gl, "uWkAn", an ? 1 : 0);
        s.set(gl, "uNass", z != null && z.an() ? (float) z.nass() : 0f);
        if (!an) return;
        gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_WOLKEN);
        gl.glBindTexture(GL.GL_TEXTURE_2D, wolkenGl.shadowTexture());
        gl.glActiveTexture(GL.GL_TEXTURE0);
        s.set(gl, "uWkSchatten", UNIT_WOLKEN);
        double[] r = wolkenGl.shadowRect();
        s.set(gl, "uWkRect", (float) r[0], (float) r[1], (float) r[2], (float) snap.shadowHeight());
        s.set(gl, "uWkH", (float) snap.shellLow(), (float) snap.lowHigh(), 0f, 0f);
    }

    /** Anteil des letzten Bilds der Wolken (Wiederverwendung; im schnellen Zeitraffer weniger). */
    public void verlauf(double anteil) { wolkenGl.historyBlend = Double.isNaN(verlaufFest) ? anteil : verlaufFest; }

    /** Helligkeit des Himmels unter den Wolken gegen den klaren (für die Belichtung). */
    public double hellFaktor() { return hell; }
    /** Direktes Sonnenlicht am Auge durch die Wolken (0..1). */
    public double sonneAmAuge() { return sonneAmAuge; }

    private double waldSonneWert = 1;
    private int waldSonneAlter = 999;

    /** Runde 1c: Anteil der Sonne, der im Mittel um das Auge unter den Wolken ankommt (für Wald, Fälle, Boote; 1 ohne Wolken). */
    public double waldSonne() { return wolkenJetzt() ? waldSonneWert : 1; }
    /** Ringe des Regens für das Meer: Textur, Kachel (m), Stärke; Textur 0 ohne Regen. */
    public int ringTextur() { return regenAn && regen.rate > 0.02 ? (neuJetzt() ? niederschlagGl.rippleTexture() : regenGl.rippleTexture()) : 0; }
    public double ringKachel() { return neuJetzt() ? niederschlagGl.rippleSize() : regenGl.rippleTile(); }
    public double ringStaerke() { return neuJetzt() ? niederschlagGl.rippleStrength() : regenGl.rippleStrength(); }
    /** W7b: Art der Ringe für das Meer (1: Wellenfeld fest in der Welt) und Mitte seines Fensters. */
    public int ringArt() { return neuJetzt() ? 1 : 0; }
    public double[] ringMitte() { return niederschlagGl.rippleCenter(); }
    /** W7b Runde 5: Rauigkeit des Regens aufs Wasser legen (Vorgabe an; aus nur zum Vergleich, {@code schalter=wetterGl.ringRauAn=false}). */
    public volatile boolean ringRauAn = true;

    /**
     * W7b Runde 5: Rauigkeit des Regens auf dem Wasser {unter dem Raster, im Raster (mittlere quadratische Steigung),
     * Anteil der Fläche dazu, ebenso}; 0 ohne neuen Regen.
     */
    public double[] ringRauigkeit() {
        if (!ringRauAn || !neuJetzt() || ringTextur() == 0) return new double[]{0, 0, 1, 1};
        double[] p = niederschlagGl.rippleShare();
        return new double[]{niederschlagGl.rippleRoughness(), niederschlagGl.rippleVariance(), p[0], p[1]};
    }
    /** W7b: Zeile des Niederschlags (Status, Info). */
    public String niederschlagInfo() { return neuJetzt() ? niederschlag.describe() + " · " + niederschlagGl.info() : "alter Regen (com.dan.rain)" + (niederschlagFehler.isEmpty() ? "" : ": " + niederschlagFehler); }
    /** W7b: Selbsttest Grafikkarte = Prozessor des Niederschlags (für die Prüfung auf dem Gerät). */
    public String niederschlagPruefen(GL4 gl) { return neuJetzt() ? niederschlagGl.selfCheck(gl) : ""; }

    public String info() {
        if (z == null || !z.an()) return "Wetter: aus";
        StringBuilder b = new StringBuilder(z.zeile());
        if (wolkenJetzt()) b.append(" · ").append(wolken.describe());
        if (z.seenebel()) b.append(" · ").append(nebel.describe());
        if (regen.rate > 0.02) b.append(neuJetzt() ? " · " + niederschlag.describe() : String.format(Locale.GERMAN, " · Regen %.1f mm/h", regen.rate));
        return b.toString();
    }

    public void dispose(GL4 gl) {
        if (!ok) return;
        wolkenGl.dispose(gl);
        nebelGl.dispose(gl);
        regenGl.dispose(gl);
        if (niederschlagOk) niederschlagGl.dispose(gl);
        for (int f : regenFbos.values()) Gpu.deleteFbos(gl, f);
        regenFbos.clear();
        Gpu.deleteFbos(gl, tiefeFbo);
        Gpu.deleteTextures(gl, tiefeKopie);
    }
}
