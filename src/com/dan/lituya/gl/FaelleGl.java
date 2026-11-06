package com.dan.lituya.gl;

import com.dan.fall.Fall;
import com.dan.fall.FallFinder;
import com.dan.fall.gl.FallGl;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.WasserNetz;
import com.jogamp.opengl.GL4;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Werkbuch Welle W6 (L9): die Wasserfälle. Die Bäche aus {@code LB_NET_WASSER} (Phase 16c) fallen über die Steilstufen
 * der Bucht; wo ein Bach auf mindestens {@link #FALL_MIN} m steiler als {@link #NEIGUNG_GRAD}° abfällt, steht ein Fall
 * aus {@code com.dan.fall} (Lauf über Fels, Strähnen, Schleier, Nebel, Tosbecken). Abfluss: Mittelwasser des
 * Einzugsgebiets ({@link com.dan.lituya.tools.Klassen#mittelwasser}) mal {@link #sommer} für die Schneeschmelze im Juli
 * [G], dazu der Regen vom Schieber wie bei den Bächen. Die Kante liegt auf dem Gelände des Kachelstapels (5 m).
 * <p>Gezeichnet bis {@link #sichtweite} vom Auge (die Fälle kennen die Erdkrümmung der Clipmap nicht; in 6 km sind das
 * 3 m), mit dem Licht des Walds ({@link Licht#waldLicht}), nach dem Wasser und den Schleiern der Welle.
 */
public final class FaelleGl {
    public volatile boolean an = true;
    /** Bis zu dieser Entfernung (m) [G]. */
    public volatile double sichtweite = 5000;
    /** W6 Runde 2: höchstens so viele je Bild, die nächsten (Kosten) [G]. */
    public volatile int jeBild = 12;
    /** Steiler als (Grad) über ein Fenster von 15 m. */
    public static final double NEIGUNG_GRAD = 40;
    /** Mindestens so hoch (m). */
    public static final double FALL_MIN = 22;
    /** Mindestens so viel Wasser (m³/s, mit Schmelze). */
    public static final double Q_MIN = 0.04;
    /** Höchstens so viel Wasser (m³/s): darüber ein Fluss im Tal, kein Fall an der Wand [G]. */
    public static final double Q_MAX = 20;
    /**
     * Schmelze im Juli: Faktor auf das Mittelwasser, 2 ohne Eis bis 4 bei ganz vergletschertem Einzugsgebiet [G]
     * (Juli ist der Monat der größten Schmelze; in vergletscherten Gebieten Südostalaskas führen die Bäche im Juli ein
     * Mehrfaches des Jahresmittels). W6 Runde 2: vorher 1,6 für alle.
     */
    static double sommer(double eis) { return 2.0 + 2.0 * eis; }
    /** Höchstens so viele Fälle (die größten nach Höhe × √Abfluss). */
    public static final int MAX = 48;

    private volatile List<Fall> faelle = List.of();
    /**
     * W6 Runde 3: dieselben Fälle über dem Grund 1958 im Bild (Rückbau des Gilbert Inlet): wo heute das Delta liegt, war
     * 1958 Wasser. Über dem Delta von heute hingen die unteren Teile der Fälle vor der Eisfront in der Luft über dem
     * Wasser (die hellen Streifen in Probe 78); jetzt fallen sie dort ins Wasser.
     */
    private volatile List<Fall> faelle58 = List.of();
    private volatile int anders58;
    private volatile String status = "Wasserfälle: nicht geladen";
    private final FallGl fallGl = new FallGl();
    private boolean init;
    private double regenGesetzt = -1;

    public String status() { return status; }
    public int anzahl() { return faelle.size(); }

    /** Sucht die Fälle im Hintergrund. */
    public void laden(File daten, BaecheGl.Hoehe hoehe) {
        Thread t = new Thread(() -> {
            try {
                long t0 = System.nanoTime();
                if (!WasserNetz.da(daten)) { status = "Wasserfälle: kein daten/netz/wasser"; return; }
                WasserNetz w = WasserNetz.lesen(daten);
                Map<Integer, double[]> lin = WasserNetz.linien(daten);
                List<Fall> l = suchen(w, lin, hoehe);
                List<Fall> l58 = new ArrayList<>();
                int anders = 0;
                Fall.Ground g58 = (x, z) -> {
                    double h = hoehe.at(x, z);
                    double[] u = Site.toUtm(x, z);
                    return com.dan.lituya.scene.Boden1958.rueckbauBild(u[0], u[1], h);
                };
                for (Fall f : l) {
                    Fall c = f.copy();
                    c.ground = g58;
                    c.update();
                    if (Math.abs(c.height - f.height) > 0.5) anders++;
                    l58.add(c);
                }
                faelle58 = l58;
                anders58 = anders;
                // W6 Runde 3: je Fall Kante und Fuß (Mittel der Aufschläge) mit Eis 1958 und Rückbau, zur Prüfung im Protokoll
                // (-Dlituya.faelleProtokoll=true)
                StringBuilder dg = new StringBuilder();
                for (int i = 0; i < l58.size(); i++) {
                    Fall f = l58.get(i);
                    double fx = 0, fy = 0, fz = 0;
                    int n = 0;
                    for (Fall.Strand st : f.strands) { fx += st.ix; fy += st.iy; fz += st.iz; n++; }
                    if (n == 0) continue;
                    fx /= n; fy /= n; fz /= n;
                    double lx = (f.lipA[0] + f.lipB[0]) / 2, lz = (f.lipA[2] + f.lipB[2]) / 2;
                    dg.append(String.format(Locale.ROOT, "%n  Fall %d %s: Kante %.0f,%.0f,%.0f Fuss 1958 %.0f,%.0f,%.0f heute %.0f m, Eis58 Fuss %.2f, Rechteck Fuss %s",
                            i, f.name, lx, f.lipA[1], lz, fx, fy, fz, l.get(i).strands.isEmpty() ? Double.NaN : l.get(i).strands.get(0).iy,
                            eis1958(fx, fz, Math.max(fy, hoehe.at(fx, fz)), 0), imInlet(fx, fz) ? "ja" : "nein"));
                }
                if (Boolean.getBoolean("lituya.faelleProtokoll")) System.out.println("LituyaBay1958: Wasserfälle 1958 (Kante, Fuß):" + dg);
                faelle = l;
                double hmax = 0, qs = 0;
                for (Fall f : l) { hmax = Math.max(hmax, f.height); qs += f.discharge; }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(8, l.size()); i++) {
                    Fall f = l.get(i);
                    sb.append(String.format(Locale.ROOT, "%n  %s: Kante %.0f,%.0f,%.0f, %.1f m³/s, %d Strähnen", f.name, (f.lipA[0] + f.lipB[0]) / 2,
                            f.lipA[1], (f.lipA[2] + f.lipB[2]) / 2, f.discharge, f.strands.size()));
                }
                status = String.format(Locale.GERMAN, "Wasserfälle: %d (bis %.0f m hoch, zusammen %.1f m³/s, %d Kanten im Eis oder Rückbau von 1958, %d fallen 1958 ins Wasser des Rückbaus), gesucht in %.1f s",
                        l.size(), hmax, qs, imEis, anders, (System.nanoTime() - t0) / 1e9) + sb;
            } catch (Exception e) {
                status = "Wasserfälle nicht geladen: " + e.getMessage();
            }
            System.out.println("LituyaBay1958: " + status);
        }, "Wasserfälle suchen");
        t.setDaemon(true);
        t.start();
    }

    /** Nur um die Bucht (Kern, Szene-m: x, z) und unter dieser Höhe (m): weiter oben ist im Juli Schnee [G]. */
    public static final double[] GEBIET = {-12_000, -9_000, 12_000, 9_000};
    public static final double KANTE_MAX = 1000;

    /**
     * W6 Runde 3: Eis der Gletscher 1958 am Ort (0 … 1) wie {@code gelaende.vert} (vGletscher): im Westen über
     * {@link com.dan.lituya.scene.Boden1958#GLETSCHER_UNTER} m, steile Hänge über 90 bis 140 m bleiben Fels und Wald; im
     * Norden die Zunge bis rund 400 m, Wände über 80 bis 120 m bleiben Fels. h ist die Höhe heute, sl = 1 − n.y. Runde 2
     * nahm jede Kante in den beiden Rechtecken, auch die am Westhang über dem Eis (H3).
     */
    static double eis1958(double x, double z, double h, double sl) {
        double[] u = Site.toUtm(x, z);
        double w = com.dan.lituya.scene.Boden1958.GLETSCHER_UNTER;
        double g = com.dan.lituya.scene.Boden1958.gletscher(u[0], u[1]) * glatt(w - 6, w + 2, h)
                * mix(1, 1 - glatt(0.12, 0.24, sl), glatt(90, 140, h));
        double[] a = Site.fromUtm(com.dan.lituya.scene.Boden1958.ZUNGE_E0, com.dan.lituya.scene.Boden1958.ZUNGE_N0),
                b = Site.fromUtm(com.dan.lituya.scene.Boden1958.ZUNGE_E1, com.dan.lituya.scene.Boden1958.ZUNGE_N1);
        double dz = Math.min(Math.min(x - Math.min(a[0], b[0]), Math.max(a[0], b[0]) - x), Math.min(z - Math.min(a[1], b[1]), Math.max(a[1], b[1]) - z));
        if (dz > 0) {
            double zz = glatt(0, 120, dz) * glatt(w - 6, w + 2, h) * (1 - glatt(330, 450, h)) * mix(1, 1 - glatt(0.10, 0.20, sl), glatt(80, 120, h));
            g = Math.max(g, zz);
        }
        return g;
    }

    static double glatt(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
    static double mix(double a, double b, double t) { return a + (b - a) * t; }

    /** Neigung 1 − n.y am Ort aus dem Gelände (±5 m). */
    static double neigung(BaecheGl.Hoehe hoehe, double x, double z) {
        double gx = (hoehe.at(x + 5, z) - hoehe.at(x - 5, z)) / 10, gz = (hoehe.at(x, z + 5) - hoehe.at(x, z - 5)) / 10;
        if (!Double.isFinite(gx) || !Double.isFinite(gz)) return 0;
        return 1 - 1 / Math.sqrt(1 + gx * gx + gz * gz);
    }

    /** W6 Runde 3: Kante im Eis von 1958 (an der Kante oder 10 m weiter unten am Fall mehr als halb Eis). */
    static boolean imEis1958(BaecheGl.Hoehe hoehe, double x, double y, double z, double dx, double dz) {
        if (eis1958(x, z, y, neigung(hoehe, x, z)) > 0.5) return true;
        double x2 = x + dx * 10, z2 = z + dz * 10, y2 = hoehe.at(x2, z2);
        return Double.isFinite(y2) && eis1958(x2, z2, y2, neigung(hoehe, x2, z2)) > 0.5;
    }

    /** Nach {@link #suchen}: Kanten, die das Eis von 1958 nahm. */
    static volatile int imEis;

    static List<Fall> suchen(WasserNetz w, Map<Integer, double[]> lin, BaecheGl.Hoehe hoehe) {
        // W6 Runde 3: die Suche liegt jetzt in Renderer (com.dan.fall.FallFinder), hier nur die Bäche, das Gebiet und das Eis
        List<FallFinder.Stream> st = new ArrayList<>();
        for (int e = 0; e < w.m; e++) {
            if (w.kEbene[e] != 1 || !(WasserNetz.BACH.equals(w.kArt[e]) || WasserNetz.BACH_EIS.equals(w.kArt[e]))) continue;
            double eis = w.eisAnteil[w.von[e]];
            if (Double.isNaN(eis)) eis = 0;
            double q = com.dan.lituya.tools.Klassen.mittelwasser(w.kFlaeche[e], eis) * sommer(eis);
            double[] en = lin.get(e);
            if (en == null || en.length < 4) continue;
            double[] xz = new double[en.length];
            for (int k = 0; k + 1 < en.length; k += 2) {
                double[] s = Site.fromUtm(en[k], en[k + 1]);
                xz[k] = s[0];
                xz[k + 1] = s[1];
            }
            st.add(new FallFinder.Stream(w.kName[e], xz, q));
        }
        FallFinder ff = new FallFinder();
        ff.ground = hoehe::at;
        ff.slopeDeg = NEIGUNG_GRAD;
        ff.minDrop = FALL_MIN;
        ff.minDischarge = Q_MIN;
        ff.maxDischarge = Q_MAX;
        ff.max = MAX;
        ff.waterLevel = 0;
        int[] eisN = {0};
        ff.accept = (x, y, z) -> {
            if (!(x > GEBIET[0] && x < GEBIET[2] && z > GEBIET[1] && z < GEBIET[3] && y < KANTE_MAX)) return false;
            // Richtung des Bachs an der Kante: steilster Abstieg im Gelände (der Finder kennt sie, gibt sie dem Filter nicht)
            double gx = hoehe.at(x + 5, z) - hoehe.at(x - 5, z), gz = hoehe.at(x, z + 5) - hoehe.at(x, z - 5), l = Math.hypot(gx, gz);
            double dx = l > 1e-6 ? -gx / l : 0, dz = l > 1e-6 ? -gz / l : 0;
            if (imEis1958(hoehe, x, y, z, dx, dz)) { eisN[0]++; return false; }
            // die Kante selbst muss 1958 dort gewesen sein (nicht im Rückbau, der den Grund senkt)
            double[] u = Site.toUtm(x, z);
            if (Math.abs(com.dan.lituya.scene.Boden1958.rueckbauBild(u[0], u[1], y) - y) > 1) { eisN[0]++; return false; }
            return true;
        };
        List<FallFinder.Candidate> k = ff.find(st);
        imEis = eisN[0];
        return ff.toFalls(k, 1000, f -> {
            f.temperature = 9;
            f.quality = Fall.Quality.MEDIUM;
        });
    }

    /** Runde 2: Kante oder Fuß in den Rechtecken von Gletscher (West) und Zunge (Nord) 1958. */
    static boolean imInlet(double x, double z) {
        return in(x, z, com.dan.lituya.scene.Boden1958.GLETSCHER_E0, com.dan.lituya.scene.Boden1958.GLETSCHER_N0,
                com.dan.lituya.scene.Boden1958.GLETSCHER_E1, com.dan.lituya.scene.Boden1958.GLETSCHER_N1)
            || in(x, z, com.dan.lituya.scene.Boden1958.ZUNGE_E0, com.dan.lituya.scene.Boden1958.ZUNGE_N0,
                com.dan.lituya.scene.Boden1958.ZUNGE_E1, com.dan.lituya.scene.Boden1958.ZUNGE_N1);
    }

    private static boolean in(double x, double z, double e0, double n0, double e1, double n1) {
        double[] a = Site.fromUtm(e0, n0), b = Site.fromUtm(e1, n1);
        return x > Math.min(a[0], b[0]) && x < Math.max(a[0], b[0]) && z > Math.min(a[1], b[1]) && z < Math.max(a[1], b[1]);
    }

    /** Regen (mm/h) wie bei den Bächen: mehr Wasser in den Fällen. Rechnet die Fälle neu, wenn er sich geändert hat. */
    private void regen(double mmH) {
        if (Math.abs(mmH - regenGesetzt) < 0.25) return;
        double alt = regenGesetzt < 0 ? 0 : regenGesetzt;
        for (Fall f : faelle58) {
            double basis = f.discharge / (1 + (regenGesetzt < 0 ? 0 : regenGesetzt) * 0.4);
            f.discharge = basis * (1 + mmH * 0.4);
            f.update();
        }
        for (Fall f : faelle) {
            double basis = f.discharge / (1 + alt * 0.4);
            f.discharge = basis * (1 + mmH * 0.4);   // [G]: bei 10 mm/h fünfmal so viel
            f.update();
        }
        regenGesetzt = mmH;
    }

    /** In den gebundenen HDR-Puffer, Tiefe gegen das Gelände (nicht schreibend). */
    void zeichnen(GL4 gl, float[] vp, double[] eye, boolean reversed, Licht licht, double regenMmH, double windFromDeg, double windMs, double zeit, boolean jahr1958) {
        if (!an || faelle.isEmpty()) return;
        com.dan.forest.gl.ForestGl.Light wl = licht.waldLicht();
        if (wl == null) return;
        if (!init) { fallGl.init(gl); init = true; }
        if (!fallGl.ready()) return;
        regen(regenMmH);
        FallGl.View v = new FallGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        v.reversedZ = reversed;
        FallGl.Light l = new FallGl.Light();
        l.sunDir = wl.sunDir;
        l.sunColor = wl.sunColor;
        l.ambient = wl.skyColor;
        l.fogColor = wl.fogColor;
        l.fogDistance = wl.fogDistance;
        l.display = 0;
        double sw = sichtweite;
        // Runde 2: nur die nächsten jeBild Fälle in Sichtweite
        List<Fall> alle = jahr1958 && faelle58.size() == faelle.size() ? faelle58 : faelle;
        double[] ab = new double[alle.size()];
        Integer[] ord = new Integer[alle.size()];
        for (int i = 0; i < ab.length; i++) {
            Fall f = alle.get(i);
            ab[i] = Math.hypot((f.lipA[0] + f.lipB[0]) / 2 - eye[0], (f.lipA[2] + f.lipB[2]) / 2 - eye[2]);
            ord[i] = i;
        }
        java.util.Arrays.sort(ord, (a, b) -> Double.compare(ab[a], ab[b]));
        int n = 0;
        for (int oi = 0; oi < ord.length && n < jeBild; oi++) {
            Fall f = alle.get(ord[oi]);
            if (ab[ord[oi]] > sw) break;
            n++;
            if (Math.abs(f.windSpeed - windMs) > 0.5 || Math.abs(f.windFromDeg - windFromDeg) > 10) {
                f.windSpeed = windMs;
                f.windFromDeg = windFromDeg;
                f.update();
            }
            fallGl.draw(gl, f, v, l, zeit);
        }
    }
}
