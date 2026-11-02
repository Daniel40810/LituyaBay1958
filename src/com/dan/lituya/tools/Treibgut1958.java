package com.dan.lituya.tools;

import com.dan.debris.Carpet;
import com.dan.debris.Debris;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Stufe;
import com.dan.lituya.scene.Wald1958;
import com.dan.succession.Succession;
import com.dan.succession.Wellenwurf;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Das Treibgut der Welle von 1958 bis zum Morgen (Phase 14a, Werkbuch 5.5, 9.7; Ant-Ziel {@code treibgut1958}). Verdrahtet
 * {@code succession} (welche Bäume die Welle fortgerissen hat, Band gezogen auf die Belege) mit {@code debris}: Jeder
 * fortgerissene Baum geht hangab ins Wasser (der Rückstrom trägt ihn 50–600 m hinaus [G]), dann treiben die Stämme
 * acht Stunden bis 6 Uhr morgens im Fallwind von den Gletschern (3 m/s buchtabwärts nach Westen [G]) und einem
 * schwachen Ausstrom (5 cm/s [G]). Die Stämme sind eine Auswahl (höchstens {@link #STAEMME}); alle zusammen gehen als
 * Fläche in den Holzteppich ({@link Carpet}, 25 m), der ebenso treibt und strandet.
 * <p>
 * Ergebnis in {@code daten/ereignis/treibgut/}: {@code staemme.tsv} (x, z Szene, Winkel, Länge, Durchmesser, Zustand),
 * {@code teppich_25m.r32} (zwei Schichten: schwimmend, gestrandet; Bedeckung 0 … 1, Zeilen in +z) und {@code TREIBGUT.txt}.
 */
public final class Treibgut1958 {
    private Treibgut1958() { }

    public static final int STAEMME = 4000;
    public static final double DAUER = 8 * 3600, WIND = 3, STROM = 0.05, ZELLE = 25;
    /** Bucht in der Szene: x, z von … bis (m). */
    public static final double X0 = -12_000, X1 = 6_000, Z0 = -8_000, Z1 = 6_000;

    /** Kastenfilter 3 × 3 (Menge bleibt). */
    static void glaetten(float[] v, int nx, int nz) {
        float[] o = v.clone();
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double s = 0;
                int n = 0;
                for (int b = -1; b <= 1; b++)
                    for (int a = -1; a <= 1; a++) {
                        int x = i + a, y = j + b;
                        if (x < 0 || y < 0 || x >= nx || y >= nz) continue;
                        s += o[y * nx + x];
                        n++;
                    }
                v[j * nx + i] = (float) (s / n);
            }
    }

    public static void main(String[] a) throws Exception {
        File daten = new File(a.length > 0 ? a[0] : "daten");
        long t0 = System.nanoTime();
        Wald1958 w = new Wald1958(daten, true);
        Succession su = w.succession();
        System.out.printf(Locale.GERMAN, "treibgut1958: %s · %,d Plätze im Band (%.1f s)%n", w.quelle, su.band(), (System.nanoTime() - t0) / 1e9);
        // Tiefe 1958 auf 10 m
        final double dz = 10;
        int nx = (int) ((X1 - X0) / dz), nz = (int) ((Z1 - Z0) / dz);
        float[] tiefe = new float[nx * nz];
        java.util.stream.IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                double g = w.grund1958(X0 + (i + 0.5) * dz, Z0 + (j + 0.5) * dz);
                tiefe[j * nx + i] = Double.isNaN(g) ? 0 : (float) Math.max(0, -g);
            }
        });
        Debris.Water wasser = new Debris.Water() {
            public double depth(double x, double z) {
                int i = (int) Math.floor((x - X0) / dz), j = (int) Math.floor((z - Z0) / dz);
                return i < 0 || j < 0 || i >= nx || j >= nz ? 50 : tiefe[j * nx + i];
            }

            public double[] current(double x, double z) { return new double[]{-STROM, 0}; }
        };
        Debris.Wind wind = (x, z, t) -> new double[]{-WIND, 0};
        // fortgerissene Bäume
        List<double[]> fort = new ArrayList<>();
        for (int k = 0; k < su.band(); k++) {
            if (su.folge(k) != Wellenwurf.Folge.FORT || su.altArt(k) < 0) continue;
            double[] l = su.lage(k);
            fort.add(new double[]{(l[0] + 0.5) * su.spacing, (l[1] + 0.5) * su.spacing, l[4], su.altHoehe(k), su.altArt(k)});
        }
        int stride = Math.max(1, (int) Math.ceil(fort.size() / (double) STAEMME));
        Debris d = new Debris(wasser);
        d.name = "Treibgut 1958";
        d.wind = wind;
        d.contactsOn = false;
        d.maxSub = 2;
        Carpet c = new Carpet(X0, Z0, (int) ((X1 - X0) / ZELLE), (int) ((Z1 - Z0) / ZELLE), ZELLE, wasser);
        c.wind = wind;
        Random r = new Random(1958);
        int ins = 0, ohne = 0;
        double flaeche = 0;
        for (int n = 0; n < fort.size(); n++) {
            double[] f = fort.get(n);
            double fx = Math.sin(Math.toRadians(f[2])), fz = -Math.cos(Math.toRadians(f[2]));
            double x = f[0], z = f[1];
            int s = 0;
            while (s < 400 && wasser.depth(x, z) < 2) { x += fx * 10; z += fz * 10; s++; }
            if (s >= 400) { ohne++; continue; }
            double weg = 50 + 550 * r.nextDouble();
            x += fx * weg + (r.nextDouble() - 0.5) * 120;
            z += fz * weg + (r.nextDouble() - 0.5) * 120;
            if (wasser.depth(x, z) < 1) { x -= fx * weg; z -= fz * weg; }
            double len = Math.max(8, f[3] * (0.6 + 0.3 * r.nextDouble())), dia = 0.02 * f[3] * (0.8 + 0.4 * r.nextDouble());
            double area = len * dia * 2.5;   // Stamm mit Ästen und Wurzelteller [G]
            flaeche += area;
            c.addArea(x, z, area);
            if (n % stride == 0) {
                d.add(r.nextDouble() < 0.3 ? Debris.Kind.CROWN : Debris.Kind.TRUNK, len, dia, x, z, r.nextDouble() * Math.PI);
                ins++;
            }
        }
        if (!d.logs().isEmpty()) c.like(d, d.logs().get(0));
        System.out.printf(Locale.GERMAN, "treibgut1958: %,d Bäume fort (%,d ohne Weg ins Wasser), %,d Stämme treiben, Fläche %.2f km²%n", fort.size(), ohne, ins, flaeche / 1e6);
        long t1 = System.nanoTime();
        while (d.time() < DAUER) d.step(60);
        c.run(DAUER);
        System.out.printf(Locale.GERMAN, "treibgut1958: %.0f h getrieben in %.1f s · %s%n", DAUER / 3600, (System.nanoTime() - t1) / 1e9, c.describe());
        int[] cen = d.census();
        File out = new File(daten, "ereignis/treibgut");
        if (!out.isDirectory() && !out.mkdirs()) throw new java.io.IOException("kann " + out + " nicht anlegen");
        List<String> z = new ArrayList<>();
        z.add("X\tZ\tWINKEL\tLAENGE\tDURCHMESSER\tZUSTAND");
        for (Debris.Log l : d.logs())
            z.add(String.format(Locale.ROOT, "%.1f\t%.1f\t%.4f\t%.1f\t%.2f\t%s", l.x, l.z, l.yaw, l.length, l.diameter, l.state));
        Files.write(new File(out, "staemme.tsv").toPath(), z, StandardCharsets.UTF_8);
        float[][] sn = c.snapshot();
        // ein wenig ausgleichen: der Teppich treibt im gleichförmigen Wind in Streifen der Zellen [G]
        for (float[] l : sn) for (int it = 0; it < 2; it++) glaetten(l, c.nx, c.nz);
        float[] beide = new float[sn[0].length * 2];
        System.arraycopy(sn[0], 0, beide, 0, sn[0].length);
        System.arraycopy(sn[1], 0, beide, sn[0].length, sn[1].length);
        Stufe.schreiben(new File(out, "teppich_25m.r32"), beide);
        List<String> t = new ArrayList<>();
        t.add("# Treibgut der Welle 1958 bis 6 Uhr morgens (Phase 14a, " + java.time.LocalDate.now() + ", Treibgut1958)");
        t.add(String.format(Locale.ROOT, "x0=%.0f%nz0=%.0f%nnx=%d%nnz=%d%nzelle=%.0f", X0, Z0, c.nx, c.nz, ZELLE));
        t.add(String.format(Locale.ROOT, "baeume_fort=%d%nstaemme=%d%nflaeche_km2=%.3f", fort.size(), ins, flaeche / 1e6));
        t.add(String.format(Locale.ROOT, "zustand=schwimmend %d, auf Grund %d, an Land %d, gesunken %d", cen[0], cen[1], cen[2], cen[3]));
        t.add(String.format(Locale.ROOT, "wind=%.1f m/s nach Westen [G]%nstrom=%.2f m/s nach Westen [G]%ndauer_h=%.0f", WIND, STROM, DAUER / 3600));
        t.add("teppich=" + c.describe());
        Files.write(new File(out, "TREIBGUT.txt").toPath(), t, StandardCharsets.UTF_8);
        System.out.printf(Locale.GERMAN, "treibgut1958: schwimmend %d, auf Grund %d, an Land %d, gesunken %d → %s%n", cen[0], cen[1], cen[2], cen[3], out);
    }
}
