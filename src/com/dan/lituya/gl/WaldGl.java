package com.dan.lituya.gl;

import com.dan.forest.ForestCulture;
import com.dan.forest.Species;
import com.dan.forest.TreeStates;
import com.dan.forest.gl.ForestGl;
import com.dan.forest.gl.ForestGpu;
import com.dan.lituya.scene.Band1958;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Stufe;
import com.dan.lituya.scene.Sun;
import com.dan.lituya.scene.Wald1958;
import com.dan.succession.Boden;
import com.dan.succession.Succession;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Der Wald in der Szene (Phase 14a, Werkbuch 5.6, 9.7): {@code forest.gl.ForestGpu} mit Sitka-Fichte, Westlicher
 * Hemlocktanne und Roterle über den Kern, Bestand aus der Kronenhöhe heute und im Band der Welle 1958 aus dem Gelände
 * ({@link Wald1958}); die Zustände je Platz kommen aus {@code succession} nach dem Zeitstand:
 * <ul>
 * <li>vor der Welle: der Altbestand steht überall;</li>
 * <li>während der Welle: ein Platz im Band bekommt seine Folge (gebrochen, entrindet, umgelegt, fort), sobald die Welle
 * an seinem Ufer ankommt (Ankunft aus der Kette, höchstens alle 2 s neu);</li>
 * <li>danach und bis heute: der Jahrgang der Wiederbewaldung zum Datum (Jahr 0 ab dem 10.7.1958), am Morgen danach mit
 * dem Treibgut ({@code daten/ereignis/treibgut}: Stämme als umgelegte Bäume auf dem Wasser, der Holzteppich im Wasser).</li>
 * </ul>
 * Der Boden im Band (Fels, Mineralboden, Waldboden) geht als Textur in das Gelände. Alles wird beim Start im Hintergrund
 * gerechnet (rund 20 s); bis dahin zeichnet die Szene ohne Wald.
 */
public final class WaldGl {
    public static final List<Species> ARTEN = List.of(Species.sitkaSpruce(), Species.westernHemlock(), Species.redAlder());
    /** Quadrat der Kultur über den Kern (Szene): Ecke und Kante (m), Auflösung des Geländes und der Dichte. */
    static final double X0 = -12_000, Z0 = -12_000, KANTE = 24_000;
    static final int N_GELAENDE = 4096, N_DICHTE = 2048;
    /** Texturstellen: Boden im Band, Holzteppich. */
    public static final int UNIT_BAND = 10, UNIT_TEPPICH = 11, UNIT_KRONE = 12;
    /**
     * Werkbuch Welle W0 (K3): Einzelbäume nur bis hierher (m); dahinter zeichnet das Gelände das Kronendach aus dem
     * Dichtegitter (gelaende.frag). Vorher reichten die Bäume in „Hoch“ 4,5 km weit und zerfielen dort in Bildpunkte;
     * bis 3 km glättet sie jetzt die Kantenglättung (erste Annahme 1,8 km: der Gegenhang wurde zu glatt).
     */
    public static final double NAH = 3000, AUSDUENNEN_AB = 0.55;
    /** Treibgut zeigen bis so viele Sekunden nach dem Beben (rund drei Wochen) [G]. */
    static final double TREIBGUT_BIS = 21 * 86_400;

    /** Wald zeigen, Band auf die Belege gezogen (sonst rein gerechnet), Treibgut zeigen. */
    public volatile boolean an = true, gezogen = true, treibgut = true;
    private volatile ForestGpu.Quality stufe = ForestGpu.Quality.MEDIUM;
    private volatile String status = "Wald: wird gerechnet …";

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Wald"); t.setDaemon(true); return t; });
    private Future<Daten> bau;
    private File daten;
    private Daten d;
    private ForestGpu forest;
    private boolean gezogenGebaut;
    private int kroneTex;
    private int bandTex, teppichTex, gezeigt = Integer.MIN_VALUE, bodenJahr = Integer.MIN_VALUE, tag = -1;
    private double welleT = Double.NaN;
    private boolean mitTreibgut;

    /** Was im Hintergrund entsteht. */
    static final class Daten {
        Wald1958 wald;
        Succession su;
        float[] ankunft;
        TreeStates states;
        ForestCulture.Grid gelaende, dichte;
        float[][] staemme;        // x, z, Winkel, Länge
        float[] teppich;
        double[] teppichRect;     // x0, z0, Breite, Tiefe
        int tnx, tnz;
        String zeile;
    }

    /** Startet die Rechnung (Hintergrund) aus dem Ordner daten. */
    public synchronized void aufbauen(File daten) {
        this.daten = daten;
        boolean g = gezogen;
        gezogenGebaut = g;
        status = "Wald: wird gerechnet …";
        bau = exec.submit(() -> rechnen(daten, g));
    }

    public String status() { return status; }

    public void stufe(Quality q) { stufe = ForestGpu.Quality.values()[q.ordinal()]; if (forest != null) nah(forest); }

    /** Stufe anwenden und die Reichweite der Einzelbäume auf {@link #NAH} kürzen (W0, K3). */
    private void nah(ForestGpu f) {
        f.apply(stufe);
        f.radius = Math.min(f.radius, NAH);
        f.thinFrom = AUSDUENNEN_AB;
    }

    Daten rechnen(File daten, boolean g) throws Exception {
        long t0 = System.nanoTime();
        Daten x = new Daten();
        try {
            x.wald = new Wald1958(daten, g);
            x.su = x.wald.succession();
            Succession su = x.su;
            x.ankunft = new float[su.band()];
            for (int k = 0; k < su.band(); k++) {
                double[] l = su.lage(k);
                double[] u = Site.toUtm((l[0] + 0.5) * su.spacing, (l[1] + 0.5) * su.spacing);
                double a = x.wald.band.ankunft(u[0], u[1]);
                x.ankunft[k] = (float) (Double.isNaN(a) ? Band1958.EINTAUCHEN + 120 : a);
            }
            x.states = new TreeStates(Wald1958.ABSTAND, su.i0, su.j0, su.w, su.h);
            x.gelaende = new ForestCulture.Grid(X0, Z0, KANTE, N_GELAENDE, 1, x.wald.grundGitter(X0, Z0, KANTE, N_GELAENDE));
            x.dichte = new ForestCulture.Grid(X0, Z0, KANTE, N_DICHTE, 3, x.wald.dichteGitter(X0, Z0, KANTE, N_DICHTE));
            treibgutLesen(new File(daten, "ereignis/treibgut"), x);
            x.zeile = String.format(Locale.GERMAN, "Wald: %,d Plätze im Band, %d Jahre, %,d Stämme Treibgut (%.0f s) · %s", su.band(), su.jahre(),
                    x.staemme == null ? 0 : x.staemme.length, (System.nanoTime() - t0) / 1e9, x.wald.quelle);
            System.out.println("LituyaBay1958: " + x.zeile);
            status = x.zeile;
            return x;
        } catch (Exception e) {
            status = "Wald: " + e.getMessage();
            System.err.println("LituyaBay1958: " + status);
            throw e;
        }
    }

    static void treibgutLesen(File dir, Daten x) throws Exception {
        File st = new File(dir, "staemme.tsv"), tp = new File(dir, "teppich_25m.r32"), tx = new File(dir, "TREIBGUT.txt");
        if (st.isFile()) {
            List<String> z = Files.readAllLines(st.toPath());
            List<float[]> l = new ArrayList<>();
            for (int k = 1; k < z.size(); k++) {
                String[] f = z.get(k).split("\t");
                if (f.length < 6 || f[5].equals("SUNK")) continue;
                l.add(new float[]{Float.parseFloat(f[0]), Float.parseFloat(f[1]), Float.parseFloat(f[2]), Float.parseFloat(f[3])});
            }
            x.staemme = l.toArray(new float[0][]);
        }
        if (tp.isFile() && tx.isFile()) {
            Properties p = new Properties();
            try (var r = Files.newBufferedReader(tx.toPath())) { p.load(r); }
            x.tnx = Integer.parseInt(p.getProperty("nx").trim());
            x.tnz = Integer.parseInt(p.getProperty("nz").trim());
            double z0 = Double.parseDouble(p.getProperty("z0").trim()), x0 = Double.parseDouble(p.getProperty("x0").trim()), c = Double.parseDouble(p.getProperty("zelle").trim());
            x.teppich = Stufe.lesen(tp);
            x.teppichRect = new double[]{x0, z0, x.tnx * c, x.tnz * c};
        }
    }

    /** Ob der Wald gezeichnet werden kann. */
    public boolean bereit() { return forest != null && forest.ready(); }

    /** Die Wiederbewaldung (null, solange sie rechnet). */
    public Succession succession() { return d == null ? null : d.su; }

    /** Jahr der Wiederbewaldung, das gerade gezeigt wird (−1 vor der Welle, −2 während). */
    public int gezeigt() { return gezeigt; }

    // ------------------------------------------------------------------ je Bild

    /** Übernimmt Fertiges aus dem Hintergrund und setzt die Zustände nach der Uhr; im GL-Faden vor dem Gelände. */
    public void vorbereiten(GL4 gl, SceneClock clock) {
        if (d == null && bau != null && bau.isDone()) {
            try {
                d = bau.get();
                forest = new ForestGpu(ARTEN, 3, Wald1958.ABSTAND, 1958);
                nah(forest);
                forest.setTerrain(d.gelaende);
                forest.setDensity(d.dichte);
                forest.setStates(d.states);
                forest.init(gl);
                int[] t = new int[3];
                gl.glGenTextures(3, t, 0);
                bandTex = t[0];
                teppichTex = t[1];
                kroneTex = t[2];
                kroneTextur(gl);
                if (d.teppich != null) {
                    FloatBuffer fb = Buffers.newDirectFloatBuffer(d.tnx * d.tnz * 2);
                    int n = d.tnx * d.tnz;
                    for (int k = 0; k < n; k++) fb.put(d.teppich[k]).put(d.teppich[n + k]);
                    fb.flip();
                    gl.glBindTexture(GL.GL_TEXTURE_2D, teppichTex);
                    gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_RG16F, d.tnx, d.tnz, 0, GL4.GL_RG, GL.GL_FLOAT, fb);
                    parameter(gl);
                }
            } catch (Exception e) {
                status = "Wald: " + e;
                bau = null;
            }
        }
        if (d != null && gezogen != gezogenGebaut && daten != null) {
            // Schalter „gezogen / rein gerechnet“: neu rechnen, bis dahin ohne Wald
            forest.dispose(gl);
            forest = null;
            d = null;
            gezeigt = bodenJahr = Integer.MIN_VALUE;
            tag = -1;
            aufbauen(daten);
        }
        if (d == null || forest == null) return;
        int day = clock.local().getDayOfYear();
        if (day != tag) { forest.setSeason(day, 0); tag = day; }
        double t = clock.simSeconds();
        if (t < Band1958.EINTAUCHEN) {
            if (gezeigt != -1) { d.states.clear(); gezeigt = -1; mitTreibgut = false; }
        } else if (t < 3600) {
            if (gezeigt != -2 || Math.abs(t - welleT) >= 2) { welle(t); gezeigt = -2; welleT = t; mitTreibgut = false; }
        } else {
            int y = Wald1958.jahr(clock.date());
            boolean tg = treibgut && y == 0 && t < TREIBGUT_BIS && d.staemme != null;
            if (y != gezeigt || tg != mitTreibgut) { jahrgang(y, tg); gezeigt = y; mitTreibgut = tg; }
        }
        int bj = gezeigt >= 0 ? gezeigt : Integer.MIN_VALUE;
        if (bj != bodenJahr) { if (bj >= 0) bandTextur(gl, bj); bodenJahr = bj; }
    }

    /** Während der Welle: Plätze, deren Ufer die Welle erreicht hat, bekommen ihre Folge (Jahr 0 ohne Nachwuchs). */
    void welle(double t) {
        TreeStates s = d.states;
        s.clear();
        float[] an = d.ankunft;
        int[] k = {0};
        d.su.zustand(0, (i, j, st, fall, art, h) -> {
            if (an[k[0]++] <= t) {
                s.set(i, j, st, fall);
                if (art >= 0) s.setGrowth(i, j, art, Math.min(1, h / refHoehe(art)));
            }
        });
    }

    /** Der Jahrgang y, im Jahr 0 auf Wunsch mit dem Treibgut auf dem Wasser. */
    void jahrgang(int y, boolean tg) {
        TreeStates s = d.states;
        s.clear();
        d.su.zustand(y, (i, j, st, fall, art, h) -> {
            s.set(i, j, st, fall);
            if (art >= 0) s.setGrowth(i, j, art, Math.min(1, h / refHoehe(art)));
        });
        if (tg) for (float[] l : d.staemme) {
            int i = s.slotI(l[0]), j = s.slotJ(l[1]);
            if (!s.contains(i, j)) continue;
            double fall = (Math.toDegrees(Math.atan2(Math.cos(l[2]), -Math.sin(l[2]))) + 360) % 360;
            s.set(i, j, TreeStates.FELLED, fall);
            s.setGrowth(i, j, 0, Math.min(1, l[3] / refHoehe(0)));
        }
    }

    /** Mittlere Höhe einer Art im Wald (Modell und Streuung der Größe 0,62–1,07), wie in der Vorführung von Phase 14. */
    static double refHoehe(int s) { Species sp = ARTEN.get(s); return (sp.heightMin + sp.heightMax) / 2 * 0.845; }

    static void parameter(GL4 gl) {
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    }

    /** Boden im Band im Jahr y: je Platz Fels, Mineralboden oder Waldboden (linear), Deckel 1 im Band, je 2 × 2 Plätze. */
    void bandTextur(GL4 gl, int y) {
        Succession su = d.su;
        int w = (su.w + 1) / 2, h = (su.h + 1) / 2;
        byte[] px = new byte[w * h * 4];
        for (int k = 0; k < su.band(); k++) {
            double[] l = su.lage(k);
            int ii = ((int) l[0] - su.i0) / 2, jj = ((int) l[1] - su.j0) / 2, o = (jj * w + ii) * 4;
            Boden bo = su.bodenArt(k, y);
            int[] c = bo == Boden.FELS ? new int[]{46, 44, 42} : bo == Boden.MINERAL ? new int[]{52, 40, 28} : new int[]{14, 20, 10};
            px[o] = (byte) c[0]; px[o + 1] = (byte) c[1]; px[o + 2] = (byte) c[2]; px[o + 3] = (byte) 255;
        }
        ByteBuffer b = Buffers.newDirectByteBuffer(px.length);
        b.put(px).flip();
        gl.glBindTexture(GL.GL_TEXTURE_2D, bandTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, w, h, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, b);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        parameter(gl);
    }

    /** W0 (K3): das Dichtegitter (drei Arten) als RGB-Textur mit Mip-Stufen für das Kronendach im Gelände. */
    void kroneTextur(GL4 gl) {
        ForestCulture.Grid g = d.dichte;
        int n = g.n, L = Math.min(3, g.layers);
        ByteBuffer b = Buffers.newDirectByteBuffer(n * n * 4);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                for (int s = 0; s < 3; s++) b.put((byte) (s < L ? Math.round(Math.max(0, Math.min(1, g.v[(s * n + j) * n + i])) * 255) : 0));
                b.put((byte) 255);
            }
        b.flip();
        gl.glBindTexture(GL.GL_TEXTURE_2D, kroneTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, n, n, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, b);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    }

    /** Uniforms des Bodens im Band für das Gelände (Shader ist gebunden). */
    public void gelaende(GL4 gl, Shader s) { gelaende(gl, s.id()); }

    /** Wie {@link #gelaende(GL4, Shader)} für ein Programm p (gebunden). */
    public void gelaende(GL4 gl, int p) {
        // W0 (K3): Kronendach hinter den Einzelbäumen
        boolean kr = an && d != null && kroneTex != 0 && forest != null;
        gl.glUniform1i(gl.glGetUniformLocation(p, "uKroneAn"), kr ? 1 : 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uKrone"), UNIT_KRONE);
        if (kr) {
            gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_KRONE);
            gl.glBindTexture(GL.GL_TEXTURE_2D, kroneTex);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glUniform4f(gl.glGetUniformLocation(p, "uKroneRect"), (float) X0, (float) Z0, (float) KANTE, (float) KANTE);
            gl.glUniform2f(gl.glGetUniformLocation(p, "uKroneAb"), (float) (forest.radius * forest.thinFrom), (float) forest.radius);
        }
        boolean on = an && d != null && bodenJahr >= 0;
        gl.glUniform1i(gl.glGetUniformLocation(p, "uBandAn"), on ? 1 : 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uBand"), UNIT_BAND);
        if (!on) return;
        Succession su = d.su;
        gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_BAND);
        gl.glBindTexture(GL.GL_TEXTURE_2D, bandTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        int w = (su.w + 1) / 2, h = (su.h + 1) / 2;
        gl.glUniform4f(gl.glGetUniformLocation(p, "uBandRect"), (float) (su.i0 * su.spacing), (float) (su.j0 * su.spacing), (float) (w * 2 * su.spacing), (float) (h * 2 * su.spacing));
    }

    /** Uniforms des Holzteppichs für das Wasser (Shader ist gebunden). */
    public void meer(GL4 gl, Shader s) {
        boolean on = an && d != null && mitTreibgut && d.teppich != null;
        s.set(gl, "uTeppichAn", on ? 1 : 0);
        s.set(gl, "uTeppich", UNIT_TEPPICH);
        if (!on) return;
        gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_TEPPICH);
        gl.glBindTexture(GL.GL_TEXTURE_2D, teppichTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        double[] r = d.teppichRect;
        s.set(gl, "uTeppichRect", (float) r[0], (float) r[1], (float) r[2], (float) r[3]);
    }

    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    /** Licht wie der Platzhalter-Himmel der App (common.glsl): Sonne, Himmel im Zenit, Dunst an der Kimm. */
    /** W1: das Licht aus com.dan.sky (gl/Licht), je Bild vom Renderer gesetzt; null = der Platzhalter unten. */
    static volatile ForestGl.Light himmel;

    static ForestGl.Light licht(double[] sun) {
        ForestGl.Light hl = himmel;
        if (hl != null) return hl;
        ForestGl.Light l = new ForestGl.Light();
        double e = sun[1], el = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, e))));
        double f = smooth(0.02, 0.35, e), k = 2.6 * smooth(-0.015, 0.01, e) * smooth(-0.02, 0.12, e);
        l.sunDir = sun.clone();
        l.sunColor = new double[]{k, (0.55 + 0.40 * f) * k, (0.28 + 0.60 * f) * k};
        double B = Sun.skyBrightness(el) / 0.32;
        double day = smooth(-2, 12, el), night = 1 - smooth(-16, -8, el), tw = Math.max(0, Math.min(1, 1 - day - night));
        l.skyColor = new double[]{(0.16 * day + 0.10 * tw + 0.05 * night) * B, (0.34 * day + 0.17 * tw + 0.08 * night) * B, (0.72 * day + 0.42 * tw + 0.20 * night) * B};
        l.fogColor = new double[]{(0.62 * day + 0.40 * tw + 0.07 * night) * B, (0.74 * day + 0.36 * tw + 0.09 * night) * B, (0.88 * day + 0.40 * tw + 0.18 * night) * B};
        l.fogDistance = 120_000;
        l.skyWeight = 1;
        return l;
    }

    /** Kultur und Zeichnen in den gebundenen HDR-Bildspeicher (mit Tiefe). */
    public void zeichnen(GL4 gl, float[] vp, double[] eye, boolean reversed, double[] sun, double t) {
        if (!an || forest == null) return;
        ForestGl.View v = new ForestGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        v.reversedZ = reversed;
        v.tonemap = false;
        forest.update(gl, v);
        forest.draw(gl, v, licht(sun), t);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    /** Für HUD und Prüfung: sichtbare Bäume, Stufen, Speicher. */
    public String info() {
        if (forest == null) return status;
        return String.format(Locale.GERMAN, "%s · %,d sichtbar, %.0f MB", forest.info(), forest.visible(), forest.vramBytes() / 1e6);
    }

    public void dispose(GL4 gl) {
        if (forest != null) forest.dispose(gl);
        if (kroneTex != 0) gl.glDeleteTextures(1, new int[]{kroneTex}, 0);
        exec.shutdownNow();
    }
}
