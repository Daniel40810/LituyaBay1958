package com.dan.lituya.tools;

import com.dan.dustcloud.DustCloud;
import com.dan.impact.Impact;
import com.dan.impact.Presets;
import com.dan.landslide.Landslide;
import com.dan.lituya.scene.Boden1958;
import com.dan.lituya.scene.EreignisDaten;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.WellenNetz;
import com.dan.wave.Bay;
import com.dan.wave.BayGrid;
import com.dan.wave.gl.BayGpu;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.DoubleBinaryOperator;

/**
 * Die Vorrechnung der Welle (Phase 13, Werkbuch 4.4, 6.1, 9.4, 9.5; Ant-Ziel {@code ereignis}): die Kette verdrahtet bis
 * in die Bucht auf echtem Grund. Das Nahfeld rechnet {@code impact} im Schnitt (Laborfall FRITZ01 in Naturmaßen,
 * {@link Presets#gilbertInlet1958}); bei 885 m von der Wand gibt es Wasserstand und Durchfluss an eine Linie quer über den
 * Gilbert Inlet ({@link Bay#line}), dahinter ist das Nahfeld Fels. Von dort rechnet {@code wave.Bay} in drei Gittern über
 * den Kern (16 / 4 / 2 m, {@code MEDIUM}; {@code LOW} 32 / 8 / 4 m) auf dem Grund von 1958 ({@link Boden1958}), auf der
 * Grafikkarte ({@link BayGpu}) oder dem Prozessor.
 * <p>
 * Zwei Läufe (Entscheidung Phase 13): {@code kette} mit dem Rand von {@code impact} (Durchfluss gemessen) und
 * {@code messwelle} mit der Einzelwelle aus dem Versuch (152 m auf 122 m nach 16 s) an derselben Linie. Nach 60 s gibt
 * die Linie frei [G]. Lage [G]: Die Wand des Felssturzes liegt 1 640 m vom Sporn (gegenüber, Richtung 053°), die Linie 885 m
 * davor, 1 200 m lang, senkrecht zur Richtung Wand → Sporn; das Nahfeld dahinter (1 000 m tief) wird Fels.
 * <p>
 * Ausgabe: {@code daten/ereignis/<lauf>/} ({@link EreignisDaten}: je Gitter Bilder des Wasserstands, höchster Stand,
 * Ankunft) und {@code docs/status/Ereignis_<lauf>.png}; auf die Konsole Auflauf am Sporn (MILLER60: 524 m), höchster
 * Auflauf, Ankunft an Cenotaph Island, Anchorage Cove und am Eingang gegen {@code LB_NET_WELLE}, Bilanz und Rechenzeit.
 * Dazu {@link #staub}: der Felssturz und seine Staubwolke als Bilder für das Abspielen (Lauf {@code staub}).
 * Argumente: daten, Lauf (kette, messwelle, staub, linie = nur die Ganglinie der Kette für die Echtzeit, beide = die zwei Wellen, alle), Qualität (MEDIUM, LOW), Zeit (s Welle), Rechner (gpu, cpu), Pegel (m).
 * MEDIUM schreibt nach {@code daten/ereignis/<lauf>}, LOW nach {@code <lauf>_LOW} (seit 13a; vorher überschrieb ein LOW-Lauf MEDIUM).
 */
public final class Ereignis {
    private Ereignis() { }

    /** Auflösung der drei Gitter (m). */
    public enum Q {
        LOW(32, 8, 4), MEDIUM(16, 4, 2);
        public final double grob, mittel, fein;

        Q(double a, double b, double c) { grob = a; mittel = b; fein = c; }
    }

    /** Bezug der Rechnung: x = E − E0, z = N − N0 (z nach Norden). */
    public static final double E0 = 340_000, N0 = 6_494_000;
    /** Gitter: Kern; Mitte über Gilbert Inlet, T-Kopf und Crillon Inlet; fein über Inlet und Sporn (UTM 8 N). */
    public static final double[] MITTE = {351_500, 6_502_800, 357_000, 6_508_000}, FEIN = {353_200, 6_505_200, 355_800, 6_507_600};
    /** Richtung von der Wand zum Sporn (Einheitsvektor Ost, Nord) und Abstand Wand – Sporn (m) [G]. */
    public static final double[] RICHTUNG = {-0.8, -0.6};
    public static final double WAND_SPORN = 1_640, LINIE_AB = 885, LINIE_HALB = 600, NAHFELD = 1_000;
    /** Ereigniszeit der Rechenzeit 0: der Felssturz erreicht das Wasser (wie LB_NET_WELLE). */
    public static final double START = WellenNetz.START_S;
    /** Bis dahin gibt die Linie Werte, danach ist sie frei (s Rechenzeit) [G]. */
    public static final double FREI = 60;

    public static double[] wand() { return new double[]{Site.SPORN.e() - WAND_SPORN * RICHTUNG[0], Site.SPORN.n() - WAND_SPORN * RICHTUNG[1]}; }

    /** Mitte der Linie (UTM). */
    public static double[] linie() { double[] w = wand(); return new double[]{w[0] + LINIE_AB * RICHTUNG[0], w[1] + LINIE_AB * RICHTUNG[1]}; }

    /** Das Nahfeld hinter der Linie: bis {@link #NAHFELD} m zur Wand hin, {@link #LINIE_HALB} m zu jeder Seite. */
    public static boolean nahfeld(double e, double n) { return Quelle.gilbert1958().nahfeld(e, n); }

    /**
     * Wo die Welle in die Bucht kommt (seit Phase 13c auch anderswo, „Was wäre wenn“): Mitte der Linie (UTM), Richtung in
     * die Bucht (Einheitsvektor Ost, Nord), halbe Länge, Tiefe des Nahfelds dahinter (dort Fels), frei nach (s Rechenzeit).
     */
    public record Quelle(double e, double n, double re, double rn, double halb, double hinten, double frei) {
        /** Die Linie von 1958 vor der Wand des Gilbert Inlet. */
        public static Quelle gilbert1958() { double[] l = linie(); return new Quelle(l[0], l[1], RICHTUNG[0], RICHTUNG[1], LINIE_HALB, NAHFELD, FREI); }

        /** Liegt der Ort im Nahfeld hinter der Linie? */
        public boolean nahfeld(double e2, double n2) {
            double qe = e2 - e, qn = n2 - n, along = qe * re + qn * rn, across = -qe * rn + qn * re;
            return along < -40 && along > -hinten && Math.abs(across) < halb;
        }
    }

    /** Die Bucht in drei Gittern auf dem Grund hoehe(E, N), mit der Linie und der Quelle in (Wasserstand über dem Pegel, Durchfluss). */
    public static Bay bucht(DoubleBinaryOperator hoehe, Q q, Bay.Inlet in, double pegel) { return bucht(hoehe, q, in, pegel, Quelle.gilbert1958()); }

    /** Wie {@link #bucht(DoubleBinaryOperator, Q, Bay.Inlet, double)}, mit der Linie der Quelle s (Phase 13c). */
    public static Bay bucht(DoubleBinaryOperator hoehe, Q q, Bay.Inlet in, double pegel, Quelle s) {
        Bay b = new Bay(0, 0, (int) Math.round(Site.KERN.breite() / q.grob), (int) Math.round(Site.KERN.hoehe() / q.grob), q.grob);
        b.name = "Lituya Bay 1958 (" + q + ")";
        b.nest(MITTE[0] - E0, MITTE[1] - N0, MITTE[2] - E0, MITTE[3] - N0, (int) Math.round(q.grob / q.mittel));
        b.nest(FEIN[0] - E0, FEIN[1] - N0, FEIN[2] - E0, FEIN[3] - N0, (int) Math.round(q.mittel / q.fein));
        for (int r = 0; r < 4; r++) b.edge(r, BayGrid.Edge.OPEN);
        double te = -s.rn(), tn = s.re();
        b.line(s.e() - s.halb() * te - E0, s.n() - s.halb() * tn - N0, s.e() + s.halb() * te - E0, s.n() + s.halb() * tn - N0, s.re(), s.rn(),
                (t, x, z) -> { if (t > s.frei()) return null; double[] v = in.at(t, x, z); return new double[]{pegel + v[0], v[1]}; });
        b.gauge("Sporn", Site.SPORN.e() - E0, Site.SPORN.n() - N0);
        b.gauge("Cenotaph Island", Site.CENOTAPH.e() - E0, Site.CENOTAPH.n() - N0);
        b.gauge("Anchorage Cove", Site.ANCHORAGE.e() - E0, Site.ANCHORAGE.n() - N0);
        b.gauge("Eingang", Site.CHAUSSEE.e() - E0 + 300, Site.CHAUSSEE.n() - N0 + 200);
        b.build((x, z) -> s.nahfeld(x + E0, z + N0) ? Double.NaN : hoehe.applyAsDouble(x + E0, z + N0), pegel, null);
        return b;
    }

    /** Die Quelle aus {@code impact}: Laborfall in Naturmaßen, Rand bei 885 m; Durchfluss gemessen; nach dem Ende auf null in 5 s. */
    public static Bay.Inlet kette(Impact.Quality iq, StringBuilder log) { return kette(iq, log, new ArrayList<>()); }

    /** Wie {@link #kette(Impact.Quality, StringBuilder)}; die Randwerte {t, η, q} landen in rand (Phase 13c: linie_kette.tsv). */
    public static Bay.Inlet kette(Impact.Quality iq, StringBuilder log, List<double[]> rand) {
        Impact s = Presets.gilbertInlet1958(iq);
        s.far(LINIE_AB, (t, cell, x, z, eta, qx, qz) -> rand.add(new double[]{t, eta, qx}));
        long t0 = System.nanoTime();
        s.run(40);
        Impact.Gauge g = s.gauges().get(1);
        log.append(String.format(Locale.GERMAN, "impact (%s, %.0f m): bei 885 m %.0f m nach %.1f s, %d Randwerte bis %.0f s, %.0f s Rechnung%n", iq, s.dx, g.max(), g.timeOfMax(), rand.size(),
                rand.isEmpty() ? 0 : rand.get(rand.size() - 1)[0], (System.nanoTime() - t0) / 1e9));
        return ausRand(rand);
    }

    /** Die Ganglinie der Kette aus den Randwerten {t, η, q}; nach dem Ende auf null in 5 s. */
    public static Bay.Inlet ausRand(List<double[]> rand) {
        Bay.Inlet r = Auflauf.fromSeries(rand, false);
        double tEnd = rand.isEmpty() ? 0 : rand.get(rand.size() - 1)[0];
        return (t, x, z) -> {
            double[] v = r.at(Math.min(t, tEnd), x, z);
            double f = t <= tEnd ? 1 : Math.max(0, 1 - (t - tEnd) / 5);
            return new double[]{v[0] * f, v[1] * f};
        };
    }

    public static void main(String[] a) throws Exception {
        File daten = new File(a.length > 0 ? a[0] : "daten");
        String lauf = a.length > 1 ? a[1] : "beide";
        Q q = a.length > 2 ? Q.valueOf(a[2]) : Q.MEDIUM;
        double zeit = a.length > 3 ? Double.parseDouble(a[3]) : 600;
        boolean gpu = !(a.length > 4 && a[4].equals("cpu"));
        double pegel = a.length > 5 ? Double.parseDouble(a[5]) : 0;
        Boden1958 boden = new Boden1958(daten);
        System.out.println(boden.quelle);
        WellenNetz netz = null;
        try { netz = WellenNetz.lesen(daten); } catch (Exception e) { System.out.println("  HINWEIS daten/netz/welle fehlt, ohne Vergleich mit LB_NET_WELLE"); }
        for (String l : lauf.equals("alle") ? new String[]{"kette", "messwelle", "staub"} : lauf.equals("beide") ? new String[]{"kette", "messwelle"} : new String[]{lauf}) {
            if (l.equals("staub")) { System.out.println(staub(boden::hoehe, daten, zeit)); continue; }
            StringBuilder log = new StringBuilder();
            List<double[]> rand = new ArrayList<>();
            Bay.Inlet in = l.equals("kette") || l.equals("linie") ? kette(Impact.Quality.LOW, log, rand) : Auflauf.Messwelle.INSTANCE;
            System.out.print(log);
            if (!rand.isEmpty()) System.out.println("Ganglinie für die Echtzeit: " + schreibeRand(daten, rand));
            if (l.equals("linie")) continue;
            Bay b = bucht(boden::hoehe, q, in, pegel);
            String name = ordner(l, q);
            Ergebnis r = rechnen(b, name, zeit, gpu, EreignisDaten.ordner(daten, name), pegel, l.equals("kette") ? "impact (Laborfall, Rand bei 885 m)" : "Messwelle FRITZ01");
            if (!(b.time() >= zeit)) System.out.println("  FEHLER der Lauf ist abgebrochen (Zeit " + b.time() + " s): ein Wert wurde NaN");
            System.out.println(bericht(b, r, netz));
            File png = new File("docs/status/Ereignis_" + name + ".png");
            bild(b, r, png, boden::hoehe);
            System.out.println("Bild: " + png);
        }
    }

    /** Die Randwerte der Kette als daten/ereignis/linie_kette.tsv (t, η, q je Meter), für die Echtzeit (Phase 13c). */
    public static File schreibeRand(File daten, List<double[]> rand) throws java.io.IOException {
        File f = new File(new File(daten, "ereignis"), "linie_kette.tsv");
        f.getParentFile().mkdirs();
        StringBuilder sb = new StringBuilder("# Ganglinie der Kette an der Linie (impact LOW, Rand bei 885 m): t (s Rechenzeit), η (m über dem Pegel), q (m²/s)\n");
        for (double[] r : rand) sb.append(String.format(Locale.ROOT, "%.4f\t%.4f\t%.4f%n", r[0], r[1], r[2]));
        java.nio.file.Files.writeString(f.toPath(), sb.toString());
        return f;
    }

    /** Liest daten/ereignis/linie_kette.tsv (oder null). */
    public static List<double[]> leseRand(File daten) {
        File f = new File(new File(daten, "ereignis"), "linie_kette.tsv");
        if (!f.isFile()) return null;
        try {
            List<double[]> r = new ArrayList<>();
            for (String z : java.nio.file.Files.readAllLines(f.toPath())) {
                if (z.isBlank() || z.startsWith("#")) continue;
                String[] p = z.split("\t");
                r.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])});
            }
            return r.isEmpty() ? null : r;
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    /** Ordner eines Laufs: MEDIUM heißt wie der Lauf (das Abspielen liest ihn), andere Qualitäten bekommen sie angehängt. */
    public static String ordner(String lauf, Q q) { return q == Q.MEDIUM ? lauf : lauf + "_" + q; }

    /** Was ein Lauf liefert: höchster Stand und Ankunft je Gitter, Bytes, Rechenzeit. */
    public static final class Ergebnis {
        public final List<float[]> max = new ArrayList<>(), ankunft = new ArrayList<>();
        public final List<EreignisDaten.Gitter> gitter = new ArrayList<>();
        public long bytes;
        public double sekunden;
        public String rechner, lauf;
    }

    /** Rechnet bis zeit (s Welle) und schreibt je Sekunde ein Bild je Gitter (das feine in den ersten 120 s alle 0,5 s). */
    public static Ergebnis rechnen(Bay b, String lauf, double zeit, boolean gpu, File dir, double pegel, String quelle) throws Exception {
        Ergebnis r = new Ergebnis();
        r.lauf = lauf;
        for (BayGrid g : b.grids()) r.gitter.add(new EreignisDaten.Gitter(g.x0 + E0, g.z0 + N0, g.dx, g.nx, g.nz));
        for (EreignisDaten.Gitter g : r.gitter) { float[] v = new float[g.nx() * g.nz()]; Arrays.fill(v, Float.NaN); r.ankunft.add(v); }
        long t0 = System.nanoTime();
        try (EreignisDaten.Schreiber w = new EreignisDaten.Schreiber(dir, r.gitter)) {
            if (gpu) {
                Exception[] err = {null};
                GLProfile prof = GLProfile.get(GLProfile.GL4);
                GLCapabilities caps = new GLCapabilities(prof);
                caps.setOnscreen(false);
                caps.setFBO(true);
                GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, 64, 64);
                dr.display();
                dr.invoke(true, (GLAutoDrawable d) -> {
                    try { gpuLauf(d.getGL().getGL4(), b, zeit, w, r, pegel); } catch (Exception e) { err[0] = e; }
                    return true;
                });
                dr.destroy();
                if (err[0] != null) throw err[0];
            } else cpuLauf(b, zeit, w, r, pegel);
            Properties p = new Properties();
            p.setProperty("lauf", lauf);
            p.setProperty("quelle", quelle);
            p.setProperty("start_s", Double.toString(START));
            p.setProperty("pegel_m", Double.toString(pegel));
            p.setProperty("rechner", r.rechner);
            p.setProperty("bay", b.describe());
            double[] l = linie();
            p.setProperty("linie", String.format(Locale.ROOT, "E %.0f N %.0f, Richtung %.2f %.2f, halbe Länge %.0f m, frei nach %.0f s", l[0], l[1], RICHTUNG[0], RICHTUNG[1], LINIE_HALB, FREI));
            for (int k = 0; k < r.gitter.size(); k++) { w.raster("max", k, r.max.get(k)); w.raster("ankunft", k, r.ankunft.get(k)); }
            w.beschreibung(p);
            r.bytes = w.bytes();
        }
        r.sekunden = (System.nanoTime() - t0) / 1e9;
        return r;
    }

    static double naechstesBild(int k, double t, int grids) { double s = k == grids - 1 && t < 120 ? 0.5 : 1; return (Math.floor(t / s + 1e-9) + 1) * s; }

    static void gpuLauf(GL4 gl, Bay b, double zeit, EreignisDaten.Schreiber w, Ergebnis r, double pegel) throws Exception {
        BayGpu g = new BayGpu(b);
        g.upload(gl);
        r.rechner = "Grafikkarte: " + BayGpu.renderer(gl);
        int n = b.grids().size();
        double[] next = new double[n];
        for (int k = 0; k < n; k++) { bild(w, r, k, 0, g.surface(gl, k), pegel); w.geschw(k, START, g.velocities(gl, k)); next[k] = naechstesBild(k, 0, n); }
        long w0 = System.nanoTime();
        double melde = 10;
        while (g.time() < zeit) {
            g.step(gl);
            if (g.time() >= melde) { fortschritt(g.time(), g.steps(), w0); melde += 10; }
            for (int k = 0; k < n; k++) if (g.time() >= next[k]) {
                bild(w, r, k, g.time(), g.surface(gl, k), pegel);
                w.geschw(k, START + g.time(), g.velocities(gl, k));   // seit 15a: die Strömung für Boote und Treibgut
                next[k] = naechstesBild(k, g.time(), n);
            }
        }
        for (int k = 0; k < n; k++) r.max.add(g.maxSurface(gl, k));
        g.sync(gl);
        r.rechner += " · " + g.info();
        g.dispose(gl);
    }

    static void cpuLauf(Bay b, double zeit, EreignisDaten.Schreiber w, Ergebnis r, double pegel) throws Exception {
        r.rechner = "Prozessor";
        int n = b.grids().size();
        double[] next = new double[n];
        for (int k = 0; k < n; k++) { bild(w, r, k, 0, surface(b.grids().get(k)), pegel); w.geschw(k, START, velocities(b.grids().get(k))); next[k] = naechstesBild(k, 0, n); }
        long w0 = System.nanoTime();
        double melde = 10;
        while (b.time() < zeit) {
            b.step();
            if (b.time() >= melde) { fortschritt(b.time(), b.steps(), w0); melde += 10; }
            for (int k = 0; k < n; k++) if (b.time() >= next[k]) {
                bild(w, r, k, b.time(), surface(b.grids().get(k)), pegel);
                w.geschw(k, START + b.time(), velocities(b.grids().get(k)));
                next[k] = naechstesBild(k, b.time(), n);
            }
        }
        for (BayGrid g : b.grids()) {
            float[] m = new float[g.nx * g.nz];
            double[] em = g.etaMaxCells();
            for (int j = 0; j < g.nz; j++) for (int i = 0; i < g.nx; i++) m[j * g.nx + i] = (float) em[g.id(i, j)];
            r.max.add(m);
        }
    }

    static void fortschritt(double t, long steps, long w0) {
        double s = (System.nanoTime() - w0) / 1e9;
        System.out.printf(Locale.GERMAN, "  … %.0f s Welle, %d Schritte, %.0f s Rechnung%n", t, steps, s);
        System.out.flush();
    }

    static float[] surface(BayGrid g) {
        float[] v = new float[g.nx * g.nz];
        for (int j = 0; j < g.nz; j++) for (int i = 0; i < g.nx; i++) { double h = g.depth(i, j); v[j * g.nx + i] = h > 0.01 ? (float) g.eta(i, j) : Float.NaN; }
        return v;
    }

    static float[][] velocities(BayGrid g) {
        float[] u = new float[g.nx * g.nz], v = new float[g.nx * g.nz];
        for (int j = 0; j < g.nz; j++)
            for (int i = 0; i < g.nx; i++) {
                if (g.depth(i, j) <= 0.05) continue;
                double[] q = g.velocity(i, j);
                u[j * g.nx + i] = (float) q[0];
                v[j * g.nx + i] = (float) q[1];
            }
        return new float[][]{u, v};
    }

    static void bild(EreignisDaten.Schreiber w, Ergebnis r, int k, double t, float[] eta, double pegel) throws Exception {
        w.bild(k, START + t, eta);
        float[] an = r.ankunft.get(k);
        for (int c = 0; c < eta.length; c++) if (Float.isNaN(an[c]) && eta[c] - pegel > 1) an[c] = (float) (START + t);
    }

    // ------------------------------------------------------------------ Strom und Staub

    /** Gitter für den Strom (UTM, 8 m) und den Staub (25 m). */
    public static final double[] STROM = {353_400, 6_505_400, 356_600, 6_508_600}, STAUB = {349_000, 6_501_000, 359_000, 6_511_000};
    /** Abrieb (Staub je m Weg und m Mächtigkeit) und Staubanteil der Wolke [G], wie {@code tools.Staubwolke}. */
    public static final double ABRIEB = 7e-6, STAUBANTEIL = 1e-3;

    /** Vom Schnitt des Presets (x hangab ins Wasser, z quer, Ursprung an der Uferlinie der Wand) nach UTM. */
    public static double[] utm(double x, double z) {
        double[] w = wand();
        return new double[]{w[0] + x * RICHTUNG[0] - z * RICHTUNG[1], w[1] + x * RICHTUNG[1] + z * RICHTUNG[0]};
    }

    /** Von UTM in den Schnitt des Presets {x, z}. */
    public static double[] schnitt(double e, double n) {
        double[] w = wand();
        double qe = e - w[0], qn = n - w[1];
        return new double[]{qe * RICHTUNG[0] + qn * RICHTUNG[1], -qe * RICHTUNG[1] + qn * RICHTUNG[0]};
    }

    /**
     * Der Felssturz des Presets ({@link com.dan.landslide.Presets#gilbertInlet1958}, auf seinem eigenen, gerechneten
     * Gelände [G], an die Wand gelegt) und seine Staubwolke über dem Grund von 1958: je 0,5 s ein Bild der Mächtigkeit des
     * Stroms (8 m), je 2 s die Oberkante der Wolke und ihre optische Dicke (25 m), nach {@code daten/ereignis/strom} und
     * {@code staub}. Der Sturz beginnt mit der Auslösung bei 93 s (wie {@code tools.Sturz}, Modus zeuge).
     */
    public static String staub(DoubleBinaryOperator hoehe, File daten, double zeit) throws Exception {
        double ab = 93;
        long t0 = System.nanoTime();
        Landslide ls = com.dan.landslide.Presets.gilbertInlet1958(Landslide.Quality.LOW);
        DustCloud c = new DustCloud(STAUB[0], STAUB[1], (int) Math.round((STAUB[2] - STAUB[0]) / 25), (int) Math.round((STAUB[3] - STAUB[1]) / 25), 25, DustCloud.rockDust());
        c.name = "Staub des Felssturzes";
        c.wind = (x, z, t) -> new double[]{0.5, -1.0};
        c.build(new DustCloud.Ground() {
            public double height(double e, double n) { double h = hoehe.applyAsDouble(e, n); return Double.isNaN(h) ? 2000 : Math.max(0, h); }

            public boolean water(double e, double n) { return hoehe.applyAsDouble(e, n) < 0; }
        });
        EreignisDaten.Gitter gs = new EreignisDaten.Gitter(STROM[0], STROM[1], 8, (int) Math.round((STROM[2] - STROM[0]) / 8), (int) Math.round((STROM[3] - STROM[1]) / 8));
        EreignisDaten.Gitter gd = new EreignisDaten.Gitter(STAUB[0], STAUB[1], 25, c.nx, c.nz);
        double dust = 0, strom = 0, wolke = 0;
        try (EreignisDaten.Schreiber ws = new EreignisDaten.Schreiber(EreignisDaten.ordner(daten, "strom"), List.of(gs));
             EreignisDaten.Schreiber wd = new EreignisDaten.Schreiber(EreignisDaten.ordner(daten, "staub"), List.of(gd, gd))) {
            double nextS = 0, nextD = 0;
            while (c.time() < zeit) {
                if (ls.time() < 90 && !(ls.time() > 20 && ls.atRest())) {
                    double dt = ls.step();
                    for (int j = 0; j < ls.nz; j++)
                        for (int i = 0; i < ls.nx; i++) {
                            int cc = ls.index(i, j);
                            if (ls.underWater(cc) || ls.thickness(cc) < 0.5) continue;
                            double sp = ls.speed(cc);
                            if (sp < 10) continue;
                            double d = ABRIEB * ls.thickness(cc) * sp * ls.dx * ls.dx * dt;
                            double[] v = ls.velocity(cc), p = utm(ls.cx(i), ls.cz(j));
                            double ve = 0.5 * (v[0] * RICHTUNG[0] - v[1] * RICHTUNG[1]), vn = 0.5 * (v[0] * RICHTUNG[1] + v[1] * RICHTUNG[0]);
                            if (c.inject(p[0], p[1], d, d / STAUBANTEIL, ve, vn)) dust += d;
                        }
                    while (c.time() < ls.time()) c.step();
                    if (ls.time() >= nextS) {
                        float[] v = new float[gs.nx() * gs.nz()];
                        for (int j = 0; j < gs.nz(); j++)
                            for (int i = 0; i < gs.nx(); i++) {
                                double[] x = schnitt(gs.e(i), gs.n(j));
                                double h = ls.thicknessAt(x[0], x[1]);
                                v[j * gs.nx() + i] = h > 0.1 ? (float) h : Float.NaN;
                            }
                        ws.bild(0, ab + ls.time(), v);
                        nextS += 0.5;
                    }
                    strom = ls.time();
                } else c.step();
                if (c.time() >= nextD) {
                    float[][] sn = c.snapshot();
                    float[] top = new float[sn[0].length], tau = sn[1];
                    for (int j = 0; j < c.nz; j++) for (int i = 0; i < c.nx; i++) top[j * c.nx + i] = (float) c.top(c.cx(i), c.cz(j));
                    for (int k = 0; k < tau.length; k++) { if (tau[k] < 0.05f) tau[k] = Float.NaN; wolke = Math.max(wolke, Float.isNaN(top[k]) ? 0 : sn[0][k]); }
                    wd.bild(0, ab + c.time(), top);
                    wd.bild(1, ab + c.time(), tau);
                    nextD += 2;
                }
            }
            // ab Phase 13b: der Grund unter Strom und Staub für das Abspielen (Staub: Wasser auf 0 wie die Wolke)
            float[] bs = new float[gs.nx() * gs.nz()], bd = new float[gd.nx() * gd.nz()];
            for (int j = 0; j < gs.nz(); j++) for (int i = 0; i < gs.nx(); i++) { double h = hoehe.applyAsDouble(gs.e(i), gs.n(j)); bs[j * gs.nx() + i] = Double.isNaN(h) ? -200f : (float) h; }
            for (int j = 0; j < gd.nz(); j++) for (int i = 0; i < gd.nx(); i++) { double h = hoehe.applyAsDouble(gd.e(i), gd.n(j)); bd[j * gd.nx() + i] = Double.isNaN(h) ? 0f : (float) Math.max(0, h); }
            ws.raster("boden", 0, bs);
            wd.raster("boden", 0, bd);
            Properties p = new Properties();
            p.setProperty("lauf", "strom");
            p.setProperty("quelle", "landslide Preset gilbertInlet1958 (LOW, gerechnetes Gelände [G]), an die Wand gelegt; g0 Mächtigkeit (m)");
            p.setProperty("start_s", Double.toString(ab));
            ws.beschreibung(p);
            Properties q = new Properties();
            q.setProperty("lauf", "staub");
            q.setProperty("quelle", String.format(Locale.ROOT, "dustcloud über dem Grund von 1958; Abrieb %.0e je m, Staubanteil %.0e [G]; g0 Oberkante (m NAVD88), g1 optische Dicke", ABRIEB, STAUBANTEIL));
            q.setProperty("start_s", Double.toString(ab));
            wd.beschreibung(q);
        }
        double[] u = c.budget();
        return String.format(Locale.GERMAN, "Strom und Staub: Sturz %.0f s gerechnet (%.1f Mio. m³), Staub %.0f m³ (%.2f %% des Volumens); nach %.0f s in der Luft %.0f m³, an Land %.0f m³, im Wasser %.0f m³, hinaus %.0f m³; Wolke bis %.0f m mächtig; %.0f s Rechnung",
                strom, ls.volume() / 1e6, dust, 100 * dust / com.dan.landslide.Presets.LITUYA_VOLUME, c.time(), u[0], u[1], u[2], u[3], wolke, (System.nanoTime() - t0) / 1e9);
    }

    // ------------------------------------------------------------------ Bericht

    /** Höchster Stand über dem Pegel an Land (Auflauf) im Kreis um (E, N), aus dem feinsten Gitter, das den Ort hat. */
    static double[] auflauf(Ergebnis r, double e, double n, double rad, DoubleBinaryOperator hoehe, double pegel) {
        double best = Double.NEGATIVE_INFINITY, be = 0, bn = 0;
        for (int k = 0; k < r.gitter.size(); k++) {
            EreignisDaten.Gitter g = r.gitter.get(k);
            float[] m = r.max.get(k);
            for (int j = 0; j < g.nz(); j++)
                for (int i = 0; i < g.nx(); i++) {
                    double x = g.e(i), y = g.n(j);
                    if (Math.hypot(x - e, y - n) > rad || Float.isNaN(m[j * g.nx() + i])) continue;
                    if (k + 1 < r.gitter.size() && r.gitter.get(k + 1).enthaelt(x, y)) continue;
                    if (!(hoehe.applyAsDouble(x, y) > pegel)) continue;
                    if (m[j * g.nx() + i] - pegel > best) { best = m[j * g.nx() + i] - pegel; be = x; bn = y; }
                }
        }
        return new double[]{best, be, bn};
    }

    /** Früheste Ankunft (Ereigniszeit) im Kreis, aus dem feinsten Gitter, das den Ort hat. */
    static double ankunft(Ergebnis r, double e, double n, double rad) {
        double best = Double.NaN;
        for (int k = r.gitter.size() - 1; k >= 0; k--) {
            EreignisDaten.Gitter g = r.gitter.get(k);
            if (!g.enthaelt(e, n)) continue;
            float[] an = r.ankunft.get(k);
            for (int j = 0; j < g.nz(); j++)
                for (int i = 0; i < g.nx(); i++) {
                    if (Math.hypot(g.e(i) - e, g.n(j) - n) > rad) continue;
                    float t = an[j * g.nx() + i];
                    if (!Float.isNaN(t) && !(t >= best)) best = t;
                }
            return best;
        }
        return best;
    }

    public static String bericht(Bay b, Ergebnis r, WellenNetz netz) {
        StringBuilder s = new StringBuilder();
        s.append(String.format(Locale.GERMAN, "Lauf %s: %s, %.0f s Welle, %d Schritte, %s%n", r.lauf, b.describe(), b.time(), b.steps(), r.rechner));
        DoubleBinaryOperator h = (e, n) -> {
            BayGrid g = b.gridAt(e - E0, n - N0);
            return g == null ? Double.NaN : g.bedAt(g.cellI(e - E0), g.cellJ(n - N0));
        };
        double pegel = b.level;
        double[] sp = auflauf(r, Site.SPORN.e(), Site.SPORN.n(), 1500, h, pegel), all = auflauf(r, Site.KERN.eMin() + Site.KERN.breite() / 2, Site.KERN.nMin() + Site.KERN.hoehe() / 2, 1e7, h, pegel);
        s.append(String.format(Locale.GERMAN, "  Auflauf am Sporn (1,5 km um E %.0f N %.0f): %.0f m bei E %.0f N %.0f (MILLER60: 524 m)%n", Site.SPORN.e(), Site.SPORN.n(), sp[0], sp[1], sp[2]));
        s.append(String.format(Locale.GERMAN, "  höchster Auflauf im Kern: %.0f m bei E %.0f N %.0f (%s)%n", all[0], all[1], all[2], Site.naechsterOrt(all[1] - Site.E0, -(all[2] - Site.N0))));
        Object[][] orte = WellenNetz.ORTE;
        for (Object[] o : orte) {
            if (o[0].equals("Sporn")) continue;
            double e = (double) o[1], n = (double) o[2], rad = (double) o[3];
            double t = ankunft(r, e, n, rad), tn = netz == null ? Double.NaN : netz.ankunftBei(0, e, n, rad);
            double[] au = auflauf(r, e, n, rad + 500, h, pegel);
            s.append(String.format(Locale.GERMAN, "  %-16s Ankunft (über 1 m) %s nach dem Stoß, %s nach dem Eintauchen; LB_NET_WELLE heute %s; Auflauf in %.0f m Umkreis %s%n", o[0],
                    Double.isNaN(t) ? "–" : String.format(Locale.GERMAN, "%.0f s", t), Double.isNaN(t) ? "–" : String.format(Locale.GERMAN, "%.0f s", t - START),
                    Double.isNaN(tn) ? "–" : String.format(Locale.GERMAN, "%.0f s", tn), rad + 500, Double.isInfinite(au[0]) ? "–" : String.format(Locale.GERMAN, "%.0f m", au[0])));
        }
        for (Bay.Gauge g : b.gauges()) s.append(String.format(Locale.GERMAN, "  Pegel %-16s höchstens %s%n", g.name, Double.isInfinite(g.max()) ? "trocken" : String.format(Locale.GERMAN, "%.1f m nach %.0f s", g.max(), START + g.timeOfMax())));
        s.append(String.format(Locale.GERMAN, "  Volumen %.6f des Anfangs (über die Linie herein und hinaus), abgeschnitten %.0f m³; %,d Bytes in daten/ereignis/%s; %.0f s Rechnung (%.2f s Welle je Sekunde)",
                b.volume() / b.initialVolume(), b.clipped(), r.bytes, r.lauf, r.sekunden, b.time() / r.sekunden));
        return s.toString();
    }

    // ------------------------------------------------------------------ Bild

    public static void bild(Bay b, Ergebnis r, File png, DoubleBinaryOperator hoehe) throws Exception {
        int w = 1500, hh = (int) (w * Site.KERN.hoehe() / Site.KERN.breite()), zx = w + 20, zw = 900, zh = (int) (zw * (FEIN[3] - FEIN[1]) / (FEIN[2] - FEIN[0]));
        BufferedImage im = new BufferedImage(w + zw + 40, Math.max(hh, zh) + 70, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(11, 17, 24));
        g.fillRect(0, 0, im.getWidth(), im.getHeight());
        karte(g, r, b.level, hoehe, Site.KERN.eMin(), Site.KERN.nMin(), Site.KERN.eMax(), Site.KERN.nMax(), 0, 0, w, hh);
        karte(g, r, b.level, hoehe, FEIN[0], FEIN[1], FEIN[2], FEIN[3], zx, 0, zw, zh);
        // Linie und Sporn im Ausschnitt
        double[] l = linie();
        double sx = zw / (FEIN[2] - FEIN[0]);
        double te = -RICHTUNG[1], tn = RICHTUNG[0];
        g.setColor(Color.RED);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new Line2D.Double(zx + (l[0] - LINIE_HALB * te - FEIN[0]) * sx, (FEIN[3] - (l[1] - LINIE_HALB * tn)) * sx, zx + (l[0] + LINIE_HALB * te - FEIN[0]) * sx, (FEIN[3] - (l[1] + LINIE_HALB * tn)) * sx));
        g.fillOval((int) (zx + (Site.SPORN.e() - FEIN[0]) * sx) - 5, (int) ((FEIN[3] - Site.SPORN.n()) * sx) - 5, 10, 10);
        g.setColor(new Color(230, 234, 238));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString(String.format(Locale.GERMAN, "Ereignis · Lauf %s · %s · höchster Stand je Ort (an Land: Auflauf), %.0f s Welle", r.lauf, b.describe(), b.time()), 12, Math.max(hh, zh) + 25);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(new Color(140, 156, 170));
        g.drawString("Farben: Stand über dem Pegel 0 (blau) bis 100 m (gelb) bis 500 m (rot); grau Land, das trocken blieb. Rechts der Gilbert Inlet (fein): rot die Linie aus dem Nahfeld, Punkt der Sporn.", 12, Math.max(hh, zh) + 45);
        g.dispose();
        ImageIO.write(im, "png", png);
    }

    static void karte(Graphics2D g, Ergebnis r, double pegel, DoubleBinaryOperator hoehe, double e0, double n0, double e1, double n1, int px, int py, int pw, int ph) {
        BufferedImage im = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < ph; y++)
            for (int x = 0; x < pw; x++) {
                double e = e0 + (x + 0.5) * (e1 - e0) / pw, n = n1 - (y + 0.5) * (n1 - n0) / ph;
                double m = Double.NaN;
                for (int k = r.gitter.size() - 1; k >= 0; k--) {
                    int c = r.gitter.get(k).index(e, n);
                    if (c >= 0) { m = r.max.get(k)[c]; break; }
                }
                double b = hoehe.applyAsDouble(e, n);
                int rgb;
                if (Double.isNaN(m)) { double s = Double.isNaN(b) ? 0.3 : Math.min(1, Math.max(0, b / 1500)); int v = (int) (60 + 110 * s); rgb = (v << 16) | (v << 8) | v; }
                else rgb = farbe(m - pegel).getRGB();
                im.setRGB(x, y, rgb);
            }
        g.drawImage(im, px, py, null);
    }

    static Color farbe(double a) {
        double t = Math.max(0, Math.min(1, a <= 100 ? a / 200 : 0.5 + (a - 100) / 800));
        float h = (float) (0.62 - 0.62 * t);
        return Color.getHSBColor(h, 0.85f, (float) (0.45 + 0.5 * t));
    }
}
