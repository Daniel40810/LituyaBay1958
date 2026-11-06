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
    /** W6 (L8): Wind 10 m über Grund (m/s) und woher (Grad), vom Renderer je Bild aus der Verdrahtung. */
    public volatile double windMs = 2, windVonGrad = 135;
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
    /** W5 (E11): die Bäume, die gerade in der Zunge fallen. */
    final KnickGl knick = new KnickGl();
    /** W5: Bäume knicken in Reihen (sonst schalten sie wie bis W4 um). */
    public volatile boolean knicken = true;

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
        float[] sperre;           // Runde 6: Höhe, bis zu der der stehende Wald Sicht sperrt (m über Grund), 20-m-Raster
        long maskeZellen = -1;    // W6 Runde 3: Zellen mit Wald, die Schnee oder Eis (überwiegend) wegnimmt
        com.dan.tree.gl.TreeGl erlen;   // W6 Runde 3: Roterle aus der Nähe (Schwarzerle aus Nature, com.dan.tree)
    }

    /** Runde 6: Raster der Sichtsperre durch den Wald (Szene, Ecke Nordwest, Zelle, Breite, Höhe). */
    static final double SPERRE_ZELLE = 20;
    private static final int SPERRE_W = (int) (Wald1958.W * Wald1958.ZELLE / SPERRE_ZELLE), SPERRE_H = (int) (Wald1958.H * Wald1958.ZELLE / SPERRE_ZELLE);

    /**
     * Runde 6: bis zu welcher Höhe über Grund der Wald hier die Sicht sperrt (für die Verdeckung der Welle): außerhalb
     * des Bands der Welle (dort steht der Altbestand die ganze Nacht), wo die Kronen heute mindestens 25 m hoch sind, die
     * halbe Kronenhöhe (Lücken zwischen den Kronen; vorsichtig). 0 sonst, und solange der Wald nicht gerechnet ist.
     */
    public double sperre(double x, double z) {
        Daten x0 = d;
        if (!an || x0 == null || x0.sperre == null) return 0;
        double e = x + Site.E0, n = Site.N0 - z;
        int i = (int) Math.floor((e - Site.KERN.eMin()) / SPERRE_ZELLE), j = (int) Math.floor((Site.KERN.nMax() - n) / SPERRE_ZELLE);
        return i < 0 || j < 0 || i >= SPERRE_W || j >= SPERRE_H ? 0 : x0.sperre[j * SPERRE_W + i];
    }

    static double sperreFlaeche(float[] r) {
        if (r == null) return 0;
        long n = 0;
        for (float v : r) if (v > 0) n++;
        return n * SPERRE_ZELLE * SPERRE_ZELLE / 1e6;
    }

    static float[] sperreRechnen(Wald1958 w) {
        if (w.krone == null) return null;
        float[] r = new float[SPERRE_W * SPERRE_H];
        for (int j = 0; j < SPERRE_H; j++)
            for (int i = 0; i < SPERRE_W; i++) {
                double e = Site.KERN.eMin() + (i + 0.5) * SPERRE_ZELLE, n = Site.KERN.nMax() - (j + 0.5) * SPERRE_ZELLE;
                double x = e - Site.E0, z = Site.N0 - n;
                double k = w.krone(x, z);
                if (Double.isNaN(k) || k < 25 || w.imBand(x, z)) continue;
                r[j * SPERRE_W + i] = (float) (0.5 * k);
            }
        return r;
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
        stufeRadius = f.radius;
        f.radius = Math.min(f.radius, reichweite);
        f.thinFrom = duennAb;
        if (f.fineNeedles) f.lod0 = Math.min(f.lod0, LOD0_FEIN);   // W6 Runde 3: feine Nadeln, dafür Stufe 0 näher
        updateNoetig = true;   // Runde 11: neue Stufe, neue Größe der Impostoren
    }

    /** W6 Runde 3 (WF18 B): feine Nadelbürsten in Stufe 0 (forest, Renderer); Stufe 0 dafür bis 5 statt 12 Höhen je 20 m [G]. */
    public static volatile boolean feineNadeln = !Boolean.getBoolean("lituya.grobeNadeln");
    static final double LOD0_FEIN = 5;   // Runde 3c: 7 kostete in nahen Blicken 1–2,5 ms im Wald
    /**
     * W6 Runde 3 (WF18 A): Roterlen näher als so viele Meter (Art 2) zeichnet forest nicht (Nahschnitt in Renderer); an
     * ihrem Platz steht die Schwarzerle aus com.dan.tree (Nature) mit gleicher Höhe und Drehung. 0 = aus [G].
     */
    public volatile double erleNah = Double.parseDouble(System.getProperty("lituya.erleNah", "30"));
    private final List<float[]> nahListe = new ArrayList<>();
    static final int ERLEN_MAX = 24;
    private double[] erlenAuge;
    private int erlenTag = -1, erlenZahl, erlenAlter;

    /** W6 Runde 3: Schnee und Eis aus Sentinel-2 nehmen den Wald (Schalter vor dem Aufbau, -Dlituya.waldOhneSchneemaske). */
    public static volatile boolean mitSchneeMaske = !Boolean.getBoolean("lituya.waldOhneSchneemaske");

    /** Runde 7: Reichweite der Einzelbäume (m) und Beginn des Ausdünnens (Anteil der Reichweite) als Schalter. */
    public volatile double reichweite = NAH, duennAb = AUSDUENNEN_AB;
    private double stufeRadius = NAH;

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
            float[] dg = x.wald.dichteGitter(X0, Z0, KANTE, N_DICHTE);
            // W6 Runde 3: kein Wald auf Schnee und Eis aus Sentinel-2 (im Flug standen am Rand des Walds einzelne Bäume auf Schnee)
            float[] maske = mitSchneeMaske ? LandschaftGl.waldMaske(daten, X0, Z0, KANTE, N_DICHTE) : null;
            if (maske != null) {
                long weg = 0;
                for (int s = 0; s < 3; s++)
                    for (int c = 0; c < maske.length; c++) {
                        float v = dg[s * maske.length + c], m = maske[c];
                        if (v > 0 && m < 1) { if (m < 0.5) weg++; dg[s * maske.length + c] = Math.round(v * m * 255) / 255f; }
                    }
                x.maskeZellen = weg;
            }
            x.dichte = new ForestCulture.Grid(X0, Z0, KANTE, N_DICHTE, 3, dg);
            treibgutLesen(new File(daten, "ereignis/treibgut"), x);
            x.sperre = sperreRechnen(x.wald);
            try {
                com.dan.tree.TreeSpecies erle = com.dan.tree.blackalder.BlackAlder.species();
                erle.rootCut = 0.4f;   // W6 Runde 4: ohne die waagerechten Röhren am Fuß (Wurzelringe am Hang, Probe 82)
                x.erlen = new com.dan.tree.gl.TreeGl(erle);
                x.erlen.minLevel = 1;                              // Stufe 0 hat bis 2,8 Mio. Dreiecke
                x.erlen.lodDistance = new float[]{1.0f, 1.0f, 14f};  // Stufe 2 ab einer Baumhöhe [G] (Probe 82: 106 Erlen mit 6,6 Mio. Dreiecken, dann 40 mit 2,9 Mio.)
            } catch (RuntimeException ex) {
                System.err.println("LituyaBay1958: Erlen aus com.dan.tree nicht geladen: " + ex.getMessage());
            }
            x.zeile = String.format(Locale.GERMAN, "Wald: %,d Plätze im Band, %d Jahre, %,d Stämme Treibgut (%.0f s) · %s", su.band(), su.jahre(),
                    x.staemme == null ? 0 : x.staemme.length, (System.nanoTime() - t0) / 1e9, x.wald.quelle)
                    + String.format(Locale.GERMAN, " · Sichtsperre des Walds %.1f km²", sperreFlaeche(x.sperre))
                    + (x.maskeZellen >= 0 ? String.format(Locale.GERMAN, " · Schnee und Eis nehmen %.1f km² Wald (Art·Zelle)", x.maskeZellen * Math.pow(KANTE / N_DICHTE, 2) / 1e6) : " · ohne Schneemaske");
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
                forest.fineNeedles = feineNadeln;   // W6 Runde 3 (WF18 B)
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
            if (d.erlen != null && erlenAuge != null) d.erlen.dispose(gl);
            erlenAuge = null;
            forest = null;
            d = null;
            gezeigt = bodenJahr = Integer.MIN_VALUE;
            tag = -1;
            aufbauen(daten);
        }
        if (d == null || forest == null) return;
        int day = clock.local().getDayOfYear();
        if (day != tag) { forest.setSeason(day, 0); tag = day; zustandNeu = true; updateNoetig = true; }
        // W6 (L8): Wind im Wald aus der Verdrahtung (vorher stand der Wald still; forest bewegt Äste und Laub im Shader)
        double wr = Math.toRadians(windVonGrad + 180);
        forest.setWind(Math.sin(wr), -Math.cos(wr), windMs, 0.35);
        if (forest.ready()) knick.vorbauen(gl, forest, tag);   // W5 Runde 4
        double t = clock.simSeconds();
        if (!(t >= Band1958.EINTAUCHEN - 1 && t < KNICK_BIS)) knick.setzen(knick_leer, 0);
        if (t < Band1958.EINTAUCHEN) {
            if (gezeigt != -1) { d.states.clear(); gezeigt = -1; mitTreibgut = false; zustandNeu = true; updateNoetig = true; }
        } else if (t < 3600) {
            // W5: solange Bäume fallen, je 0,1 s neu (vorher alle 2 s)
            double takt = knicken && t < 400 ? 0.1 : knicken ? 1 : 2;
            if (gezeigt != -2 || Math.abs(t - welleT) >= takt) { welle(t); gezeigt = -2; welleT = t; mitTreibgut = false; zustandNeu = true; updateNoetig = true; }
        } else {
            int y = Wald1958.jahr(clock.date());
            boolean tg = treibgut && y == 0 && t < TREIBGUT_BIS && d.staemme != null;
            if (y != gezeigt || tg != mitTreibgut) { jahrgang(y, tg); gezeigt = y; mitTreibgut = tg; zustandNeu = true; updateNoetig = true; }
        }
        int bj = gezeigt >= 0 ? gezeigt : Integer.MIN_VALUE;
        if (bj != bodenJahr) { if (bj >= 0) bandTextur(gl, bj); bodenJahr = bj; }
    }

    /** W5: bis zu dieser Ereigniszeit (s) können Bäume fallen (die Ankünfte im Band liegen davor). */
    static final double KNICK_BIS = 3600;   // Runde 3: umgelegte Stämme liegen bis zum Ende der Welle in KnickGl
    private static final float[] knick_leer = new float[0];
    private float[] knickBuf = new float[KnickGl.FL * 4096];
    private final com.dan.forest.ForestCulture.Tree baum = new com.dan.forest.ForestCulture.Tree();

    /**
     * Während der Welle: Plätze, die die Welle erreicht hat, bekommen ihre Folge (Jahr 0 ohne Nachwuchs). Seit W5 (E11)
     * liegt die Ankunft je Platz im Gitter der Vorrechnung (Band1958), und in den Sekunden um sie fällt der Baum in
     * {@link KnickGl}: forest zeichnet den Platz so lange nicht.
     */
    void welle(double t) {
        TreeStates s = d.states;
        s.clear();
        float[] an = d.ankunft;
        int[] k = {0}, nk = {0};
        boolean kn = knicken && forest != null && t < KNICK_BIS;
        com.dan.forest.ForestCulture cu = forest != null ? forest.culture() : null;
        d.su.zustand(0, (i, j, st, fall, art, h) -> {
            double a = an[k[0]++];
            if (kn && st != TreeStates.STANDING && a - KnickGl.VOR <= t && t < a + KnickGl.dauer(st, 3.0)) {
                if (knickBaum(cu, i, j, st, fall, a, t, nk)) { s.set(i, j, TreeStates.GONE, 0); return; }
            }
            if (a <= t) {
                s.set(i, j, st, fall);
                if (art >= 0) s.setGrowth(i, j, art, Math.min(1, h / refHoehe(art)));
            }
        });
        knick.setzen(knickBuf, nk[0]);
    }

    static float hash(int i, int j, int k) { return com.dan.forest.ForestCulture.u01(com.dan.forest.ForestCulture.slotHash(i, j, 0x5EED + k), k); }

    /** W5: legt den fallenden Baum des Platzes in die Liste (false, wenn dort keiner steht oder er schon liegt). */
    private boolean knickBaum(com.dan.forest.ForestCulture cu, int i, int j, int st, double fallDeg, double a, double t, int[] nk) {
        if (cu == null || nk[0] >= KnickGl.MAX || !cu.at(i, j, baum)) return false;
        if (baum.state != TreeStates.STANDING) return false;
        com.dan.forest.TreeModel m = forest.model(baum.type);
        double H = Math.max(m.height, 1) * baum.scale;
        double tf = (0.8 + 0.022 * H) * (0.85 + 0.3 * hash(i, j, 1));
        if (t >= a + KnickGl.dauer(st, tf)) return false;
        // Fallrichtung: umgelegt wie succession ihn legt (dort zeichnet forest ihn danach), sonst die Laufrichtung der Zunge
        double fx, fz;
        if (st == TreeStates.FELLED) {
            double r = Math.toRadians(fallDeg);
            fx = Math.sin(r); fz = -Math.cos(r);
        } else {
            double[] u = Site.toUtm(baum.x, baum.z);
            Band1958 b = d.wald.band;
            double ae = b.ankunft(u[0] + 6, u[1]) - b.ankunft(u[0] - 6, u[1]), anN = b.ankunft(u[0], u[1] + 6) - b.ankunft(u[0], u[1] - 6);
            if (Double.isNaN(ae) || Double.isNaN(anN) || Math.hypot(ae, anN) < 1e-6) { fx = 1; fz = 0; }
            else { double l = Math.hypot(ae, anN); fx = ae / l; fz = -anN / l; }
            double q = (hash(i, j, 2) - 0.5) * 0.5;   // etwas Streuung
            double c = Math.cos(q), sn = Math.sin(q), x2 = fx * c - fz * sn;
            fz = fx * sn + fz * c; fx = x2;
        }
        // liegen auf dem Hang: 90° minus die Neigung in Fallrichtung
        com.dan.forest.ForestCulture.Grid g = d.gelaende;
        double h1 = g.sample(0, (float) (baum.x + fx * 6), (float) (baum.z + fz * 6)), h0 = g.sample(0, (float) (baum.x - fx * 6), (float) (baum.z - fz * 6));
        double wend = Math.max(0.5, Math.min(1.9, Math.PI / 2 - Math.atan((h1 - h0) / 12)));
        double hb = st == TreeStates.BROKEN ? (0.15 + 0.3 * hash(i, j, 3)) * H : 0;
        if (KnickGl.FL * (nk[0] + 1) > knickBuf.length) knickBuf = java.util.Arrays.copyOf(knickBuf, knickBuf.length * 2);
        float[] o = knickBuf;
        int q = KnickGl.FL * nk[0]++;
        o[q] = baum.x; o[q + 1] = baum.y; o[q + 2] = baum.z; o[q + 3] = baum.yaw;
        o[q + 4] = baum.scale; o[q + 5] = baum.tint; o[q + 6] = (float) a; o[q + 7] = (float) hb;
        o[q + 8] = (float) fx; o[q + 9] = (float) fz; o[q + 10] = (float) wend; o[q + 11] = (float) tf;
        o[q + 12] = st; o[q + 13] = (float) (8 + 8 * hash(i, j, 4)); o[q + 14] = (float) H; o[q + 15] = baum.type;
        return true;
    }

    /** W5: die fallenden Bäume zeichnen (nach dem Wald, vor der Welle). */
    public void knickZeichnen(GL4 gl, java.util.function.Consumer<Shader> common, double[] eye, float[] vp, double t, boolean reversed) {
        if (!an || forest == null || knick.zahl() == 0) return;
        gl.glDepthFunc(reversed ? GL.GL_GREATER : GL.GL_LESS);
        knick.zeichnen(gl, forest, tag, common, eye, vp, t, (float) (1 / (2 * Site.EARTH_R_OPTICAL)));
        gl.glDisable(GL.GL_DEPTH_TEST);
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
        // W6: Jahre nach 1958 (die Aufnahme aus Sentinel-2 zeigt das Band, wie es 2020–2023 aussah)
        gl.glUniform1f(gl.glGetUniformLocation(p, "uBandJahre"), (float) Math.max(0, bodenJahr));
        gl.glUniform1i(gl.glGetUniformLocation(p, "uBand"), UNIT_BAND);
        if (!on) return;
        Succession su = d.su;
        gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_BAND);
        gl.glBindTexture(GL.GL_TEXTURE_2D, bandTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        int w = (su.w + 1) / 2, h = (su.h + 1) / 2;
        gl.glUniform4f(gl.glGetUniformLocation(p, "uBandRect"), (float) (su.i0 * su.spacing), (float) (su.j0 * su.spacing), (float) (w * 2 * su.spacing), (float) (h * 2 * su.spacing));
    }

    /** W2: ob gerade der Holzteppich auf dem Wasser liegt (am Morgen danach). */
    public boolean teppichJetzt() { return an && d != null && mitTreibgut && d.teppich != null; }

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

    /**
     * Runde 6: forest prüft bei jedem update rund eine Million Plätze gegen den Blick. Jetzt mit einem um 15 % weiteren
     * Blick, und das Ergebnis gilt weiter, solange das Auge weniger als 2 m wandert, der Blick sich um weniger als
     * 80 % des Rands dreht, die Zustände gleich bleiben und es nicht älter als 30 Bilder ist. Das Spiegelbild prüft
     * immer neu (eigener Blick) und macht das Ergebnis des Hauptbilds ungültig. Schalter wald.cullStets = alte Weise.
     */
    public volatile boolean cullStets = false;
    /** Blickrichtung und senkrechter Öffnungswinkel (Grad) des Hauptbilds, vom Renderer je Bild gesetzt. */
    public volatile double[] blick;
    public volatile double fovy = 30;
    static final double CULL_WEIT = 1.15;
    private double[] cullAuge, cullBlick;
    private int cullAlter, cullGespart;
    private volatile boolean zustandNeu = true;

    /**
     * Runde 11: forest.update lädt geänderte Zustände hoch, prüft den Blick und liest jedes dreißigste Mal die Zählung von
     * der Grafikkarte zurück (glGetBufferSubData: der Prozessor wartet, bis die Grafikkarte fertig ist; im Profil von H13
     * drei Viertel der Wartezeit des Bildfadens, mit dem Spiegel jedes zweite Bild doppelt so oft). Jetzt update nur,
     * wenn sich Zustände, Jahreszeit oder Stufe geändert haben, forest noch backt oder 300 Prüfungen vergangen sind;
     * sonst nur die Prüfung des Blicks (forest.cull). Schalter wald.updateStets = alte Weise.
     */
    public volatile boolean updateStets = false;
    private int seitUpdate = 1_000_000;
    private boolean updateNoetig = true;

    private void pruefen(GL4 gl, ForestGl.View v) {
        if (updateStets || updateNoetig || !forest.ready() || seitUpdate >= 300) {
            forest.update(gl, v);
            updateNoetig = !forest.ready();
            seitUpdate = 0;
        } else {
            forest.cull(gl, v.viewProj, v.eye);
            seitUpdate++;
        }
    }

    /** Kultur und Zeichnen in den gebundenen HDR-Bildspeicher (mit Tiefe). */
    public void zeichnen(GL4 gl, float[] vp, double[] eye, boolean reversed, double[] sun, double t) { zeichnen(gl, vp, eye, reversed, sun, t, false); }

    public void zeichnen(GL4 gl, float[] vp, double[] eye, boolean reversed, double[] sun, double t, boolean haupt) {
        if (!an || forest == null) return;
        ForestGl.View v = new ForestGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        v.reversedZ = reversed;
        v.tonemap = false;
        double rw = Math.min(stufeRadius, reichweite);
        if (forest.radius != rw || forest.thinFrom != duennAb) { forest.radius = rw; forest.thinFrom = duennAb; zustandNeu = true; updateNoetig = true; }
        nahSchnitt(haupt);
        double[] b = blick;
        if (!haupt || b == null || cullStets) {
            pruefen(gl, v);
            cullAuge = null;
        } else {
            boolean neu = zustandNeu || cullAuge == null || cullAlter >= 30 || !forest.ready();
            if (!neu) {
                double dx = eye[0] - cullAuge[0], dy = eye[1] - cullAuge[1], dz = eye[2] - cullAuge[2];
                double h = Math.toRadians(fovy) / 2, rand = Math.atan(CULL_WEIT * Math.tan(h)) - h;
                double c = (b[0] * cullBlick[0] + b[1] * cullBlick[1] + b[2] * cullBlick[2])
                        / Math.sqrt((b[0] * b[0] + b[1] * b[1] + b[2] * b[2]) * (cullBlick[0] * cullBlick[0] + cullBlick[1] * cullBlick[1] + cullBlick[2] * cullBlick[2]));
                neu = dx * dx + dy * dy + dz * dz > 4 || Math.acos(Math.min(1, c)) > 0.8 * rand;
            }
            if (neu) {
                float[] w = vp.clone();   // Zeilen x und y des Clipraums gestaucht: der Kegel wird um CULL_WEIT weiter
                for (int k = 0; k < 4; k++) { w[k * 4] /= (float) CULL_WEIT; w[k * 4 + 1] /= (float) CULL_WEIT; }
                v.viewProj = w;
                pruefen(gl, v);
                v.viewProj = vp;
                cullAuge = eye.clone();
                cullBlick = b.clone();
                cullAlter = 0;
                zustandNeu = false;
            } else {
                cullAlter++;
                cullGespart++;
            }
        }
        forest.draw(gl, v, licht(sun), t);
        if (haupt) erlen(gl, v, licht(sun), t, reversed);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    /** W6 Runde 3: Nahschnitt der Roterle setzen (im Spiegel aus: dort zeichnet forest alle, ohne die Erlen aus Nature). */
    private void nahSchnitt(boolean haupt) {
        double e = d != null && d.erlen != null ? erleNah : 0;
        double[] want = haupt && e > 0 ? new double[]{0, 0, e} : null;
        double[] cur = forest.nearCut;
        if (want == null ? cur != null : cur == null || cur[2] != want[2]) { forest.nearCut = want; zustandNeu = true; }
    }

    /** W6 Runde 3: die Erlen im Nahschnitt mit com.dan.tree zeichnen (gleiche Plätze, Höhe und Drehung wie forest). */
    private void erlen(GL4 gl, ForestGl.View v, ForestGl.Light l, double t, boolean reversed) {
        com.dan.tree.gl.TreeGl eg = d.erlen;
        if (eg == null || forest.nearCut == null) { erlenZahl = 0; return; }
        if (erlenAuge == null) eg.init(gl);
        if (erlenTag != tag) { eg.setSeason(tag, 0); erlenTag = tag; }
        double[] eye = v.eye;
        if (erlenAuge == null || Math.hypot(eye[0] - erlenAuge[0], eye[2] - erlenAuge[2]) > 1.5 || Math.abs(eye[1] - erlenAuge[1]) > 1.5 || ++erlenAlter > 60) {
            erlenAlter = 0;
            forest.nearTrees(eye, nahListe);
            eg.clear();
            // höchstens ERLEN_MAX, die nächsten (Kosten) [G]
            nahListe.removeIf(b -> (int) b[0] != 2);
            nahListe.sort((p, q) -> Double.compare(Math.hypot(p[2] - eye[0], p[4] - eye[2]), Math.hypot(q[2] - eye[0], q[4] - eye[2])));
            if (nahListe.size() > ERLEN_MAX) nahListe.subList(ERLEN_MAX, nahListe.size()).clear();
            ForestCulture.Grid gg = d.gelaende;
            for (float[] b : nahListe) {
                int var = (int) (ForestCulture.u01(ForestCulture.slotHash((int) Math.floor(b[2] / Wald1958.ABSTAND), (int) Math.floor(b[4] / Wald1958.ABSTAND), 0xE71E), 1) * 4) & 3;
                double hv = eg.height(var);
                if (!(hv > 0.5) || !(b[7] > 0.5)) continue;
                // Fuß: am Hang unter den tiefsten Punkt im Umkreis von 2,5 m, dazu 0,3 m (die Wurzeln der Erlen aus Nature
                // standen am Hang in der Luft, Probe 82)
                float y = b[3];
                if (gg != null)
                    for (int k = 0; k < 4; k++) {
                        double a = k * Math.PI / 2;
                        y = Math.min(y, gg.sample(0, (float) (b[2] + 2.5 * Math.cos(a)), (float) (b[4] + 2.5 * Math.sin(a))));
                    }
                eg.add(var, b[2], y - 0.3f, b[4], b[5], (float) (b[7] / hv));
            }
            erlenZahl = eg.trees().size();
            erlenAuge = eye.clone();
        }
        com.dan.tree.TreeWind w = new com.dan.tree.TreeWind();
        w.speed = (float) windMs;
        w.fromDeg = (float) windVonGrad;
        w.gustiness = 0.35f;
        eg.setWind(w);
        eg.update(gl, eye);
        com.dan.tree.gl.TreeGl.View tv = new com.dan.tree.gl.TreeGl.View();
        tv.viewProj = v.viewProj;
        tv.eye = eye;
        tv.tonemap = false;
        com.dan.tree.gl.TreeGl.Light tl = new com.dan.tree.gl.TreeGl.Light();
        tl.sunDir = l.sunDir;
        tl.sunColor = l.sunColor;
        tl.skyColor = l.skyColor;
        tl.fogColor = l.fogColor;
        tl.fogDistance = l.fogDistance;
        tl.groundColor = new double[]{l.skyColor[0] * 0.25, l.skyColor[1] * 0.22, l.skyColor[2] * 0.16};
        // W6 Runde 4: dasselbe Licht wie forest (Schatten des Geländes von oben, Himmel wie forest, Dunst aus dem Rundumbild)
        tl.shadowTexture = l.shadowTexture;
        tl.shadowRect = l.shadowRect;
        tl.sunShadowTexture = l.sunShadowTexture;
        tl.sunShadowMatrix = l.sunShadowMatrix;
        tl.skyTexture = l.skyTexture;
        tl.skyWeight = l.skyWeight;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversed ? GL.GL_GEQUAL : GL.GL_LEQUAL);
        gl.glDepthMask(true);
        boolean cull = gl.glIsEnabled(GL.GL_CULL_FACE);
        eg.draw(gl, tv, tl, t);
        if (cull) gl.glEnable(GL.GL_CULL_FACE);
    }

    /** Für HUD und Prüfung: sichtbare Bäume, Stufen, Speicher. */
    public String info() {
        if (forest == null) return status;
        return String.format(Locale.GERMAN, "%s · %,d sichtbar, %.0f MB · Prüfung %d× gespart · %s · Erlen aus Nature %d (%,d Dreiecke)", forest.info(), forest.visible(), forest.vramBytes() / 1e6, cullGespart, knick.info,
                erlenZahl, d != null && d.erlen != null ? d.erlen.triangles() : 0);
    }

    public void dispose(GL4 gl) {
        knick.dispose(gl);
        if (forest != null) forest.dispose(gl);
        if (d != null && d.erlen != null && erlenAuge != null) d.erlen.dispose(gl);
        if (kroneTex != 0) gl.glDeleteTextures(1, new int[]{kroneTex}, 0);
        exec.shutdownNow();
    }
}
