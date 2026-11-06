package com.dan.lituya.gl;

import com.dan.fall.Fall;
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
                faelle = l;
                double hmax = 0, qs = 0;
                for (Fall f : l) { hmax = Math.max(hmax, f.height); qs += f.discharge; }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(8, l.size()); i++) {
                    Fall f = l.get(i);
                    sb.append(String.format(Locale.ROOT, "%n  %s: Kante %.0f,%.0f,%.0f, %.1f m³/s, %d Strähnen", f.name, (f.lipA[0] + f.lipB[0]) / 2,
                            f.lipA[1], (f.lipA[2] + f.lipB[2]) / 2, f.discharge, f.strands.size()));
                }
                status = String.format(Locale.GERMAN, "Wasserfälle: %d (bis %.0f m hoch, zusammen %.1f m³/s), gesucht in %.1f s",
                        l.size(), hmax, qs, (System.nanoTime() - t0) / 1e9) + sb;
            } catch (Exception e) {
                status = "Wasserfälle nicht geladen: " + e.getMessage();
            }
            System.out.println("LituyaBay1958: " + status);
        }, "Wasserfälle suchen");
        t.setDaemon(true);
        t.start();
    }

    private record Kandidat(double x, double z, double y, double dx, double dz, double fall, double q, String name, double fuss) {
        /** Höhe × √Abfluss; Fälle, die bis fast in die Bucht fallen, dreifach [G]. */
        double rang() { return fall * Math.sqrt(q) * (fuss < 60 ? 3 : 1); }
    }

    /** Nur um die Bucht (Kern, Szene-m: x, z) und unter dieser Höhe (m): weiter oben ist im Juli Schnee [G]. */
    public static final double[] GEBIET = {-12_000, -9_000, 12_000, 9_000};
    public static final double KANTE_MAX = 1000;

    /**
     * Runde 2: nicht am Kopf des Gilbert Inlet (Rückbau 1958 und Gletscherzunge): dort stand 1958 das Eis, und das
     * Gelände von heute (Delta) gibt Fälle, die 1958 in der Luft hingen.
     */
    static boolean imInlet(double x, double z) {
        // Runde 2b: nur wo 1958 Eis stand (Gletscher im Westen, Zunge im Norden); vorher ein großes Rechteck um den Kopf
        // des Inlet, das auch die Fälle am Hang daneben nahm (H3)
        return in(x, z, com.dan.lituya.scene.Boden1958.GLETSCHER_E0, com.dan.lituya.scene.Boden1958.GLETSCHER_N0,
                com.dan.lituya.scene.Boden1958.GLETSCHER_E1, com.dan.lituya.scene.Boden1958.GLETSCHER_N1)
            || in(x, z, com.dan.lituya.scene.Boden1958.ZUNGE_E0, com.dan.lituya.scene.Boden1958.ZUNGE_N0,
                com.dan.lituya.scene.Boden1958.ZUNGE_E1, com.dan.lituya.scene.Boden1958.ZUNGE_N1);
    }

    /** Runde 2b: der Fuß im Rückbau (1958 Wasser, heute Delta): solche Fälle hingen 1958 vor der Eisfront in der Luft. */
    static boolean imRueckbau(double x, double z) {
        return in(x, z, com.dan.lituya.scene.Boden1958.RUECKBAU_E0, com.dan.lituya.scene.Boden1958.RUECKBAU_N0,
                com.dan.lituya.scene.Boden1958.RUECKBAU_E1, com.dan.lituya.scene.Boden1958.RUECKBAU_N1);
    }

    private static boolean in(double x, double z, double e0, double n0, double e1, double n1) {
        double[] a = Site.fromUtm(e0, n0), b = Site.fromUtm(e1, n1);
        return x > Math.min(a[0], b[0]) && x < Math.max(a[0], b[0]) && z > Math.min(a[1], b[1]) && z < Math.max(a[1], b[1]);
    }

    static List<Fall> suchen(WasserNetz w, Map<Integer, double[]> lin, BaecheGl.Hoehe hoehe) {
        double tan = Math.tan(Math.toRadians(NEIGUNG_GRAD));
        List<Kandidat> k = new ArrayList<>();
        for (int e = 0; e < w.m; e++) {
            if (w.kEbene[e] != 1 || !(WasserNetz.BACH.equals(w.kArt[e]) || WasserNetz.BACH_EIS.equals(w.kArt[e]))) continue;
            double eis = w.eisAnteil[w.von[e]];
            if (Double.isNaN(eis)) eis = 0;
            double q = com.dan.lituya.tools.Klassen.mittelwasser(w.kFlaeche[e], eis) * sommer(eis);
            if (q < Q_MIN || q > Q_MAX) continue;   // große Flüsse aus den Gletschern fallen nicht über Wände
            double[] en = lin.get(e);
            if (en == null || en.length < 4) continue;
            double[] p = BaecheGl.dicht(en, 5);
            int np = p.length / 2;
            if (np < 6) continue;
            double[] x = new double[np], z = new double[np], y = new double[np];
            boolean ok = true;
            for (int i = 0; i < np; i++) {
                double[] s = Site.fromUtm(p[2 * i], p[2 * i + 1]);
                x[i] = s[0]; z[i] = s[1];
                y[i] = hoehe.at(s[0], s[1]);
                if (Double.isNaN(y[i])) { ok = false; break; }
            }
            if (!ok) continue;
            // steile Stellen: Gefälle über 15 m (drei Schritte); zusammenhängende Strecken mit kleinen Lücken
            int i = 0;
            while (i + 3 < np) {
                double l3 = Math.hypot(x[i + 3] - x[i], z[i + 3] - z[i]);
                if ((y[i] - y[i + 3]) / Math.max(1, l3) < tan) { i++; continue; }
                int a = i, b = i;
                int luecke = 0;
                while (b + 3 < np && luecke <= 2) {
                    double lb = Math.hypot(x[b + 3] - x[b], z[b + 3] - z[b]);
                    if ((y[b] - y[b + 3]) / Math.max(1, lb) >= tan) luecke = 0; else luecke++;
                    b++;
                }
                int ende = Math.min(np - 1, b + 1);
                double fall = y[a] - y[ende];
                boolean drin = x[a] > GEBIET[0] && x[a] < GEBIET[2] && z[a] > GEBIET[1] && z[a] < GEBIET[3] && y[a] < KANTE_MAX
                        && !imInlet(x[a], z[a]);   // Runde 2c: der Fuß im Rückbau bleibt erlaubt (H3: der Fall am Westhang)
                if (fall >= FALL_MIN && drin) {
                    double dx = x[Math.min(np - 1, a + 1)] - x[a], dz = z[Math.min(np - 1, a + 1)] - z[a], l = Math.hypot(dx, dz);
                    if (l > 1e-6) k.add(new Kandidat(x[a], z[a], y[a], dx / l, dz / l, fall, q, w.kName[e], y[ende]));
                }
                i = ende + 1;
            }
        }
        k.sort((a, b) -> Double.compare(b.rang(), a.rang()));
        // nicht zwei Fälle dichter als 60 m
        List<Kandidat> wahl = new ArrayList<>();
        for (Kandidat c : k) {
            boolean frei = true;
            for (Kandidat o : wahl) if (Math.hypot(o.x - c.x, o.z - c.z) < 60) { frei = false; break; }
            if (frei) wahl.add(c);
            if (wahl.size() >= MAX) break;
        }
        List<Fall> r = new ArrayList<>();
        int nr = 0;
        for (Kandidat c : wahl) {
            Fall f = new Fall();
            double b = Math.max(3, WasserNetz.breite(c.q)) / 2;   // Runde 2: breiter, mehr Strähnen
            double sx = -c.dz, sz = c.dx;
            f.lipA = new double[]{c.x - sx * b, c.y + 0.3, c.z - sz * b};
            f.lipB = new double[]{c.x + sx * b, c.y + 0.3, c.z + sz * b};
            f.headingDeg = (Math.toDegrees(Math.atan2(c.dx, -c.dz)) + 360) % 360;
            f.discharge = c.q;
            f.overRock = true;
            f.ground = hoehe::at;
            f.waterLevel = 0;
            f.temperature = 9;
            f.quality = Fall.Quality.MEDIUM;
            f.seed = 1000 + nr;
            f.name = (c.name == null || c.name.isBlank() ? "Fall " + (nr + 1) : c.name) + String.format(Locale.GERMAN, " (%.0f m)", c.fall);
            try {
                f.update();
                r.add(f);
            } catch (RuntimeException ex) {
                System.err.println("LituyaBay1958: Fall " + f.name + ": " + ex.getMessage());
            }
            nr++;
        }
        return r;
    }

    /** Regen (mm/h) wie bei den Bächen: mehr Wasser in den Fällen. Rechnet die Fälle neu, wenn er sich geändert hat. */
    private void regen(double mmH) {
        if (Math.abs(mmH - regenGesetzt) < 0.25) return;
        double alt = regenGesetzt < 0 ? 0 : regenGesetzt;
        for (Fall f : faelle) {
            double basis = f.discharge / (1 + alt * 0.4);
            f.discharge = basis * (1 + mmH * 0.4);   // [G]: bei 10 mm/h fünfmal so viel
            f.update();
        }
        regenGesetzt = mmH;
    }

    /** In den gebundenen HDR-Puffer, Tiefe gegen das Gelände (nicht schreibend). */
    void zeichnen(GL4 gl, float[] vp, double[] eye, boolean reversed, Licht licht, double regenMmH, double windFromDeg, double windMs, double zeit) {
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
        List<Fall> alle = faelle;
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
