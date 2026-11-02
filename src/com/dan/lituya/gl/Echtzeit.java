package com.dan.lituya.gl;

import com.dan.lituya.scene.Boden1958;
import com.dan.lituya.scene.EreignisDaten;
import com.dan.lituya.scene.LiveLauf;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Wiedergabe;
import com.dan.lituya.tools.Auflauf;
import com.dan.lituya.tools.Ereignis;
import com.dan.wave.Bay;
import com.dan.wave.BayGrid;
import com.dan.wave.gl.BayGpu;
import com.jogamp.opengl.GL4;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.DoubleBinaryOperator;

/**
 * „Was wäre wenn“ in Echtzeit (Phase 13c, Werkbuch 4.4): die Welle mit {@code wave.Bay} auf der Grafikkarte, während
 * die Szene läuft, in LOW (32 / 8 / 4 m). Einstellbar: Volumen, Ort, Tide (Pegel), Grund 1940 oder heute.
 * <ul>
 * <li><b>Quelle an der Wand von 1958</b>: die Ganglinie der Kette (aus {@code daten/ereignis/linie_kette.tsv}, sonst
 * einmal mit {@code impact} gerechnet, rund 45 s) oder die Messwelle FRITZ01 an der Linie, nach dem Volumen skaliert wie
 * der Impulsparameter von Heller und Hager (2010): Höhe ∝ P^0,8, Dauer ∝ P^0,5, P ∝ V^5/12 bei ähnlicher Form [G].</li>
 * <li><b>Andere Orte</b> (feste und Strg-Klick): Volumen-Impuls-Quelle an einer Linie vor dem Ufer, Höhe und Dauer aus
 * P = F·S^½·M^¼·cos(6α/7)^½ (Heller und Hager 2010): F aus der Fallhöhe des Hangs, S aus der Mächtigkeit
 * 0,3·V^⅓, M aus Volumen, Breite und Tiefe; ein Wellenberg sin² über die Dauer T [G].</li>
 * </ul>
 * Jede Sekunde Welle (das feine Gitter in den ersten 120 s jede halbe) geht ein Bild in den {@link LiveLauf}, den
 * Rückspulpuffer: die {@link Wiedergabe} spielt ihn wie eine Vorrechnung, auch rückwärts und in Zeitlupe. Die Rechnung
 * läuft im Faden des Bildlaufs mit einem Zeitbudget je Bild hinter der Uhr her; packen und ablegen tut ein eigener Faden.
 */
public final class Echtzeit {
    /** Volumen des Felssturzes 1958 (m³), FRITZ01. */
    public static final double V1958 = 30.6e6;
    /** Rechenzeit der Welle bis Ende (s). */
    public static final double ZEIT = 600;
    /** Tiefe, ab der die Linie vor dem Ufer liegt (m) [G]. */
    public static final double LINIE_TIEFE = 20;

    public enum Grund {
        G1940("Grund 1940 mit Rückbau"), HEUTE("Grund heute");
        public final String text;
        Grund(String t) { text = t; }
        @Override public String toString() { return text; }
    }

    public enum Ganglinie {
        KETTE("Kette (impact)"), MESSWELLE("Messwelle FRITZ01");
        public final String text;
        Ganglinie(String t) { text = t; }
        @Override public String toString() { return text; }
    }

    /** Ein Ort des Sturzes: Name, Punkt am Hang oder Ufer (UTM), Richtung ins Wasser (Grad von Nord, NaN: aus dem Hang), die Wand von 1958. */
    public record Ort(String name, double e, double n, double richtung, boolean wand1958) {
        @Override public String toString() { return name; }
    }

    private static Ort versetzt(String name, Site.Ort o, double deg, double dist, double richtung) {
        double a = Math.toRadians(deg);
        return new Ort(name, o.e() + Math.sin(a) * dist, o.n() + Math.cos(a) * dist, richtung, false);
    }

    /** Die festen Orte [G]: die Wand von 1958 und vier andere Hänge über der Bucht. */
    public static final List<Ort> ORTE = List.of(
            new Ort("Gilbert Inlet, Wand von 1958", Ereignis.wand()[0], Ereignis.wand()[1], Math.toDegrees(Math.atan2(Ereignis.RICHTUNG[0], Ereignis.RICHTUNG[1])), true),
            versetzt("Gilbert Inlet, Südwestufer (Sporn)", Site.SPORN, 225, 300, 53),
            versetzt("Crillon Inlet, Nordhang", Site.CRILLON_INLET, 0, 700, 180),
            versetzt("Südufer bei Mudslide Creek", Site.MUDSLIDE, 180, 500, 0),
            versetzt("Nordufer gegenüber Cenotaph Island", Site.CENOTAPH, 0, 2000, 180));

    // ---------------------------------------------------------------- Einstellungen (Bedienfeld)
    public volatile double volumen = V1958, pegel = 0;
    public volatile Ort ort = ORTE.get(0);
    public volatile Grund grund = Grund.G1940;
    public volatile Ganglinie ganglinie = Ganglinie.KETTE;
    /** Zeit je Bild für die Rechnung (ms). */
    public volatile double budgetMs = 25;

    // ---------------------------------------------------------------- Zustand
    private final File daten;
    private static Boden1958 boden;
    private volatile Bay fertig;
    private volatile Ereignis.Quelle quelle;
    private volatile String quelleText = "", stand = "aus";
    private volatile boolean an, baut, stop;
    private BayGpu gpu;
    private LiveLauf lauf;
    private double[] next;
    private final ExecutorService packer = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "LituyaBay1958-Echtzeit-Puffer"); t.setDaemon(true); return t; });
    private volatile double bis = Double.NaN, letzteMs, rechnetMs;
    private long schritte;

    public Echtzeit(File daten) { this.daten = daten; }

    public Echtzeit() { this(new File(System.getProperty("lituya.daten", "daten"))); }

    public boolean an() { return an; }

    public boolean baut() { return baut; }

    /** Ereigniszeit, bis zu der gerechnet ist (NaN vor dem ersten Bild). */
    public double bis() { return bis; }

    public Ereignis.Quelle quelle() { return quelle; }

    /** „Echtzeit: Gilbert Inlet … · 30,6 Mio. m³ · Grund 1940 · Pegel +0,0 m · gerechnet bis 214 s · 12 ms je Bild · Puffer 41 MB“ */
    public String zeile() {
        if (!an && !baut) return "Echtzeit aus";
        LiveLauf l = lauf;
        return String.format(Locale.GERMAN, "%s · %s%s", stand, quelleText,
                l != null && !Double.isNaN(bis) ? String.format(Locale.GERMAN, " · gerechnet bis t = %.0f s, %.1f ms je Bild, Puffer %.0f MB", bis, letzteMs, l.bytes() / 1048576.0) : "");
    }

    /** Name des Laufs für die Wiedergabe. */
    String name() {
        return String.format(Locale.GERMAN, "Echtzeit: %s, %.1f Mio. m³, %s, Pegel %+.1f m", ort.name(), volumen / 1e6, grund, pegel);
    }

    // ---------------------------------------------------------------- Aufbau (eigener Faden)

    /** Rechnet die Bucht mit den Einstellungen neu auf (im Hintergrund); danach übernimmt der Bildlauf. */
    public synchronized void neu() {
        if (baut) return;
        baut = true;
        stop = false;
        stand = "baut die Bucht …";
        Thread t = new Thread(() -> {
            try {
                fertig = aufbauen();
                stand = "bereit";
            } catch (Exception e) {
                stand = "Fehler: " + e.getMessage();
                System.err.println("LituyaBay1958: Echtzeit: " + e);
            } finally {
                baut = false;
            }
        }, "LituyaBay1958-Echtzeit-Aufbau");
        t.setDaemon(true);
        t.start();
    }

    /** Hält die Echtzeit an und gibt die Grafikkarte frei (im nächsten Bild); die Wiedergabe kehrt zur Vorrechnung zurück. */
    public void aus() { stop = true; }

    static synchronized Boden1958 boden(File daten) throws java.io.IOException {
        if (boden == null) boden = new Boden1958(daten);
        return boden;
    }

    /** Für Prüfstände ohne daten/grund: ein eigener Grund (UTM → m über NAVD88), sonst Boden1958. */
    public volatile DoubleBinaryOperator probeGrund;

    Bay aufbauen() throws Exception {
        long t0 = System.nanoTime();
        DoubleBinaryOperator h = probeGrund;
        if (h == null) {
            Boden1958 b = boden(daten);
            h = grund == Grund.G1940 ? b::hoehe : b::hoeheHeute;
        }
        Ort o = ort;
        double v = volumen, p = pegel;
        Ereignis.Quelle q;
        Bay.Inlet in;
        if (o.wand1958() && grund == Grund.G1940) q = Ereignis.Quelle.gilbert1958();
        else q = linie(o, h, p, v);
        if (o.wand1958()) {
            Bay.Inlet basis;
            if (ganglinie == Ganglinie.KETTE) {
                List<double[]> rand = Ereignis.leseRand(daten);
                if (rand == null) {
                    stand = "impact rechnet die Ganglinie der Kette (einmal, rund 45 s) …";
                    rand = new ArrayList<>();
                    Ereignis.kette(com.dan.impact.Impact.Quality.LOW, new StringBuilder(), rand);
                    try { Ereignis.schreibeRand(daten, rand); } catch (java.io.IOException e) { System.err.println("LituyaBay1958: linie_kette.tsv: " + e.getMessage()); }
                }
                basis = Ereignis.ausRand(rand);
            } else basis = Auflauf.Messwelle.INSTANCE;
            double fa = Math.pow(v / V1958, 1.0 / 3), ft = Math.pow(v / V1958, 5.0 / 24);
            in = (t, x, z) -> { double[] w = basis.at(t / ft, x, z); return new double[]{w[0] * fa, w[1] * fa}; };
            q = new Ereignis.Quelle(q.e(), q.n(), q.re(), q.rn(), q.halb(), q.hinten(), Math.max(Ereignis.FREI, Ereignis.FREI * ft));
            quelleText = String.format(Locale.GERMAN, "%s an der Linie, Höhe ×%.2f, Dauer ×%.2f (Heller/Hager [G])", ganglinie, fa, ft);
        } else {
            double[] hh = heller(o, q, h, p, v);
            double a = hh[0], T = hh[1], tiefe = hh[2];
            in = (t, x, z) -> {
                if (t <= 0 || t >= T) return new double[]{0, 0};
                double s = Math.sin(Math.PI * t / T), e = a * s * s;
                return new double[]{e, e * Math.sqrt(9.81 * Math.max(1, tiefe + e))};
            };
            q = new Ereignis.Quelle(q.e(), q.n(), q.re(), q.rn(), q.halb(), q.hinten(), T + 10);
            quelleText = String.format(Locale.GERMAN, "Impuls-Quelle: P = %.2f, Wellenberg %.0f m über %.0f s bei %.0f m Tiefe (Heller/Hager [G])", hh[3], a, T, tiefe);
        }
        quelle = q;
        stand = "baut die Gitter …";
        Bay bay = Ereignis.bucht(h, Ereignis.Q.LOW, in, p, q);
        bay.name = name();
        stand = String.format(Locale.GERMAN, "Bucht in %.1f s gebaut", (System.nanoTime() - t0) / 1e9);
        return bay;
    }

    /**
     * Legt die Linie für einen Ort: vom Punkt in Blickrichtung ins Wasser bis zur Tiefe {@link #LINIE_TIEFE}; das Nahfeld
     * dahinter reicht bis ans Ufer. Halbe Länge 2·V^⅓ in 200 bis 800 m [G].
     */
    public static Ereignis.Quelle linie(Ort o, DoubleBinaryOperator h, double pegel, double v) {
        double deg = Double.isNaN(o.richtung()) ? richtung(o.e(), o.n(), h, pegel) : o.richtung();
        double re = Math.sin(Math.toRadians(deg)), rn = Math.cos(Math.toRadians(deg));
        double e = o.e(), n = o.n();
        // im Wasser gestartet: zurück bis an Land
        for (int i = 0; i < 300 && nass(h, e, n, pegel); i++) { e -= re * 10; n -= rn * 10; }
        // vorwärts bis ans Wasser (Ufer)
        int i = 0;
        for (; i < 800 && !nass(h, e, n, pegel); i++) { e += re * 10; n += rn * 10; }
        if (i >= 800) throw new IllegalStateException("kein Wasser in Richtung " + Math.round(deg) + "° von " + o.name());
        double se = e, sn = n, best = -1, be = e, bn = n;
        for (int k = 0; k < 150; k++) {
            double d = pegel - h.applyAsDouble(e, n);
            if (!(d > -1)) break;               // wieder an Land
            if (d > best) { best = d; be = e; bn = n; }
            if (d >= LINIE_TIEFE) break;
            e += re * 10;
            n += rn * 10;
        }
        double halb = Math.max(200, Math.min(800, 2 * Math.cbrt(v)));
        double hinten = Math.max(60, Math.hypot(be - se, bn - sn) + 40);
        return new Ereignis.Quelle(be, bn, re, rn, halb, hinten, Ereignis.FREI);
    }

    static boolean nass(DoubleBinaryOperator h, double e, double n, double pegel) {
        double z = h.applyAsDouble(e, n);
        return !Double.isNaN(z) && z < pegel - 0.5;
    }

    /** Richtung ins Wasser für einen Punkt (Grad von Nord): an Land zum nächsten Wasser, im Wasser vom nächsten Land weg. */
    public static double richtung(double e, double n, DoubleBinaryOperator h, double pegel) {
        boolean imWasser = nass(h, e, n, pegel);
        double bestD = Double.MAX_VALUE, bestA = 0;
        for (int k = 0; k < 36; k++) {
            double a = Math.toRadians(k * 10), se = Math.sin(a), cn = Math.cos(a);
            for (int s = 1; s <= 300; s++) {
                double x = e + se * s * 10, y = n + cn * s * 10;
                if (nass(h, x, y, pegel) != imWasser) {
                    if (s < bestD) { bestD = s; bestA = k * 10; }
                    break;
                }
            }
        }
        return imWasser ? (bestA + 180) % 360 : bestA;
    }

    /**
     * Höhe und Dauer der Welle nach Heller und Hager (2010): {a, T, Tiefe, P}; Tiefe des Beckens bis 1,5 km vor der Linie. Fallhöhe: der höchste Punkt des Hangs
     * hinter der Linie bis 2 km, die Mitte des Sturzes auf halber Höhe [G]; Hangneigung daraus, 20° bis 70°.
     */
    public static double[] heller(Ort o, Ereignis.Quelle q, DoubleBinaryOperator h, double pegel, double v) {
        // Tiefe des Beckens vor dem Ufer: die größte bis 1,5 km voraus (an der Linie selbst ist es flacher)
        double tiefe = Math.max(5, pegel - h.applyAsDouble(q.e(), q.n()));
        for (int s = 1; s <= 150; s++) {
            double z = h.applyAsDouble(q.e() + q.re() * s * 10, q.n() + q.rn() * s * 10);
            if (Double.isNaN(z) || z > pegel) break;
            tiefe = Math.max(tiefe, pegel - z);
        }
        double top = pegel, dist = 1;
        for (int s = 1; s <= 200; s++) {
            double z = h.applyAsDouble(q.e() - q.re() * s * 10, q.n() - q.rn() * s * 10);
            if (!Double.isNaN(z) && z > top) { top = z; dist = s * 10; }
        }
        double fall = Math.max(20, (top - pegel) / 2);
        double alpha = Math.max(20, Math.min(70, Math.toDegrees(Math.atan2(top - pegel, Math.max(1, dist - q.hinten())))));
        double vs = Math.min(120, Math.sqrt(2 * 9.81 * fall));
        double s = 0.3 * Math.cbrt(v), b = 2 * q.halb();
        double F = vs / Math.sqrt(9.81 * tiefe), S = s / tiefe, M = 1.6 * v / (b * tiefe * tiefe);
        double P = F * Math.sqrt(S) * Math.pow(M, 0.25) * Math.sqrt(Math.cos(Math.toRadians(alpha) * 6 / 7));
        double a = Math.min(1.5 * tiefe, tiefe * 4.0 / 9 * Math.pow(P, 0.8));
        double T = Math.max(10, 9 * Math.sqrt(tiefe / 9.81) * Math.sqrt(P));
        return new double[]{a, T, tiefe, P};
    }

    // ---------------------------------------------------------------- Bildlauf

    /**
     * Im Faden des Bildlaufs vor dem Zeichnen: übernimmt eine fertig gebaute Bucht, rechnet bis zur Ereigniszeit t
     * (höchstens budgetMs), legt die Bilder in den Puffer. Gibt true zurück, solange die Echtzeit läuft.
     */
    public boolean schritt(GL4 gl, double t, Wiedergabe w) {
        if (stop) { stop = false; beenden(gl, w); return false; }
        Bay neu = fertig;
        if (neu != null) {
            fertig = null;
            beenden(gl, null);
            gpu = new BayGpu(neu);
            gpu.upload(gl);
            List<EreignisDaten.Gitter> gg = new ArrayList<>();
            for (BayGrid g : neu.grids()) gg.add(new EreignisDaten.Gitter(g.x0 + Ereignis.E0, g.z0 + Ereignis.N0, g.dx, g.nx, g.nz));
            lauf = new LiveLauf(neu.name, gg);
            lauf.wiedergabe(w);
            next = new double[gg.size()];
            for (int k = 0; k < gg.size(); k++) { ablegen(k, 0, gpu.surface(gl, k)); next[k] = naechstes(k, 0, gg.size()); }
            schritte = 0;
            w.live(lauf, ort.wand1958() && grund == Grund.G1940);
            an = true;
            stand = "rechnet";
        }
        if (!an || gpu == null) return an;
        double ziel = Math.min(ZEIT, t - Ereignis.START);
        long t0 = System.nanoTime(), grenze = t0 + (long) (budgetMs * 1e6);
        int n = next.length;
        int st = 0;
        while (gpu.time() < ziel && System.nanoTime() < grenze) {
            gpu.step(gl);
            st++;
            for (int k = 0; k < n; k++)
                if (gpu.time() >= next[k]) { ablegen(k, gpu.time(), gpu.surface(gl, k)); next[k] = naechstes(k, gpu.time(), n); }
        }
        schritte += st;
        double ms = (System.nanoTime() - t0) / 1e6;
        if (st > 0) letzteMs = letzteMs * 0.9 + ms * 0.1;
        rechnetMs += ms;
        stand = gpu.time() >= ZEIT ? "fertig gerechnet" : gpu.time() + 0.5 < ziel ? "rechnet, hinter der Uhr" : "rechnet mit der Uhr";
        return true;
    }

    /** Zeitpunkte der Bilder wie in der Vorrechnung: je Sekunde, das feine Gitter in den ersten 120 s je halbe. */
    static double naechstes(int k, double t, int grids) { double s = k == grids - 1 && t < 120 ? 0.5 : 1; return (Math.floor(t / s + 1e-9) + 1) * s; }

    private void ablegen(int k, double tw, float[] eta) {
        LiveLauf l = lauf;
        double te = Ereignis.START + tw;
        packer.submit(() -> {
            try {
                l.bild(k, te, eta);
                if (k == 0) bis = l.bis();
            } catch (RuntimeException e) {
                System.err.println("LituyaBay1958: Echtzeit-Puffer: " + e);
            }
        });
    }

    private void beenden(GL4 gl, Wiedergabe w) {
        if (gpu != null) gpu.dispose(gl);
        gpu = null;
        if (w != null) {
            w.live(null, true);
            lauf = null;
            an = false;
            bis = Double.NaN;
            stand = "aus";
        }
    }

    /** Für die Prüfung: Schritte und Rechenzeit bisher. */
    public String messung() { return String.format(Locale.GERMAN, "%d Schritte, %.1f s auf der Grafikkarte", schritte, rechnetMs / 1000); }

    public void dispose(GL4 gl) {
        if (gpu != null) gpu.dispose(gl);
        gpu = null;
        packer.shutdownNow();
    }
}
