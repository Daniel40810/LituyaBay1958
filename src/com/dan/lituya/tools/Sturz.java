package com.dan.lituya.tools;

import com.dan.fracture.Fracture;
import com.dan.fracture.Piece;
import com.dan.fracture.Shape;
import com.dan.fracture.Solid;
import com.dan.landslide.Landslide;
import com.dan.landslide.Presets;
import com.dan.landslide.demo.LandslideDemo;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.WellenNetz;
import com.dan.quake.Quake;
import com.dan.quake.Slope;
import com.dan.quake.demo.QuakeDemo;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Der Felssturz vom 9. Juli 1958 von der Auslösung bis ins Wasser (Phase 9, Werkbuch 6.2, 9.2; Ant-Ziel {@code sturz}).
 * Verdrahtet drei Packages, die sich nicht kennen:
 * <ol>
 * <li><b>Auslösung</b> ({@code quake}): das Beben aus {@code daten/ereignis} am Gilbert Inlet, Newmark mit Entfestigung
 * an der NE-Wand (Sicherheitsfaktor in Ruhe, Rest der Kohäsion, Entfestigungsweg) als Tafel; wann der Hang durchgeht,
 * gegen die 60–150 s der Zeugen. Weil das Beben dafür zu früh endet, löst die Szene von Hand aus: Modus {@code zeuge}
 * (Vorgabe) so, dass der Zufluss bei {@link WellenNetz#START_S} beginnt, oder {@code newmark}.</li>
 * <li><b>Abbruch</b> ({@code fracture}): die Platte 823 × 970 m, bis 92 m, auf der Gleitfläche 40°, mit feinen Zellen an
 * der Abrissfläche ({@link Fracture#releaseZone}). Die Anker der Gleitfläche tragen nur noch den Rest der Kohäsion aus
 * der Entfestigung; die Platte bricht ab, und jedes Stück, das schneller als 2 m/s gleitet, wird übergeben
 * ({@link Fracture#handOver}).</li>
 * <li><b>Strom</b> ({@code landslide}): die Zellen kommen mit Volumen und Geschwindigkeit in den Strom über dem Gelände des
 * Gilbert Inlet ({@link Presets#gilbertInlet1958Terrain}); der läuft bis zur Ruhe und meldet den Zufluss über die
 * Uferlinie. Zum Vergleich der Strom aus der Platte des Presets ohne Bruch.</li>
 * </ol>
 * Schreibt {@code docs/status/Sturz.png} (Bogen von {@code LandslideDemo} zur Zeit t nach der Auslösung) und die Zahlen
 * auf die Konsole. Argumente: daten, Bild, t (s), Qualität (LOW, MEDIUM, HIGH), Modus (zeuge, newmark).
 */
public final class Sturz {
    private Sturz() { }

    /** Entfestigung der NE-Wand [G]: Sicherheitsfaktor in Ruhe, Rest der Kohäsion, Entfestigungsweg (m). */
    public static final double FS = 1.10, REST = 0.2, WEG = 0.3;

    public static void main(String[] a) throws Exception {
        File daten = new File(a.length > 0 ? a[0] : "daten"), aus = new File(a.length > 1 ? a[1] : "docs/status/Sturz.png");
        double tb = a.length > 2 ? Double.parseDouble(a[2]) : 18;
        Landslide.Quality q = a.length > 3 ? Landslide.Quality.valueOf(a[3]) : Landslide.Quality.MEDIUM;
        String modus = a.length > 4 ? a[4] : "zeuge";
        List<String> notiz = new ArrayList<>();

        // 1. Auslösung
        Quake beben = Bebenbild.beben(daten);
        Quake.Record r = beben.record(Site.GILBERT.x(), Site.GILBERT.z());
        System.out.printf(Locale.GERMAN, "Auslösung: Beben über daten/ereignis, am Gilbert Inlet D5–95 von %.0f bis %.0f s%n", r.significantDuration()[1], r.significantDuration()[2]);
        System.out.println("  Newmark mit Entfestigung an der NE-Wand (Versagen: durchgehen oder 1 m); Zeugen 60–150 s:");
        System.out.println("    FS     Rest 100 %        Rest 50 %         Rest 20 %         Rest 0 %   (Entfestigungsweg 0,3 m)");
        for (double fs : new double[]{1.05, 1.10, 1.15, 1.20, 1.30}) {
            StringBuilder b = new StringBuilder(String.format(Locale.GERMAN, "    %.2f", fs));
            for (double rest : new double[]{1, 0.5, 0.2, 0}) {
                Slope.Result n = wand(fs, rest, WEG).newmark(r);
                b.append(String.format(Locale.GERMAN, "   %-15s", Double.isNaN(n.failTime()) ? String.format(Locale.GERMAN, "hält (%.2f m)", n.total())
                        : String.format(Locale.GERMAN, "%s %.0f s", n.runaway() ? "geht" : "1 m", n.failTime())));
            }
            System.out.println(b);
        }
        double spaet = 0;
        String bei = "";
        int faelle = 0, spaete = 0;
        for (double fs = 1.02; fs < 1.6; fs += 0.02)
            for (double rest = 0; rest <= 0.95; rest += 0.05)
                for (double weg = 0.02; weg < 3; weg *= 1.3) {
                    Slope.Result n = wand(fs, rest, weg).newmark(r);
                    if (Double.isNaN(n.failTime())) continue;
                    faelle++;
                    if (n.failTime() >= 60) spaete++;
                    if (n.failTime() > spaet) { spaet = n.failTime(); bei = String.format(Locale.GERMAN, "FS %.2f, Rest %.0f %%, Weg %.2f m", fs, rest * 100, weg); }
                }
        System.out.printf(Locale.GERMAN, "  Suche über FS 1,02–1,60, Rest 0–95 %%, Weg 0,02–3 m: %d Fälle versagen, davon %d nach 60 s; am spätesten %.0f s (%s)%n", faelle, spaete, spaet, bei);
        Slope.Result nm = wand(FS, REST, WEG).newmark(r);
        notiz.add(String.format(Locale.GERMAN, "Auslösung: Newmark mit Entfestigung (FS %.2f, Rest %.0f %%, %.1f m) geht nach %.0f s durch; von %d versagenden Fällen nach 60 s nur %d (spätestens %.0f s) – Zeugen 60–150 s",
                FS, REST * 100, WEG, nm.failTime(), faelle, spaete, spaet));

        // 2. Abbruch und Übergabe
        Landslide strom = Presets.gilbertInlet1958Terrain(q);
        Fracture f = platte(REST);
        double tFrei = Double.NaN;
        int stuecke = 0, zellen = 0;
        long t0 = System.nanoTime();
        int[] frei = {0};
        f.onBreak = (id, v, m) -> frei[0]++;
        double dt = 1.0 / 120;
        while (f.time() < 12) {
            f.step(dt);
            for (Piece p : f.pieces()) {
                double[] v = p.velocity();
                if (Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) < 2) continue;
                double[] c = f.handOver(p);
                stuecke++;
                for (int i = 0; i < c.length; i += 8) {
                    strom.inject(c[i], c[i + 2], c[i + 3], c[i + 5], c[i + 7], 0.62 * Math.cbrt(c[i + 3]));
                    zellen++;
                }
                if (Double.isNaN(tFrei)) tFrei = f.time();
            }
            strom.step(dt);
            if (f.time() > 3 && f.pieces().isEmpty()) break;
        }
        double fertig = f.time();
        double ms = (System.nanoTime() - t0) / 1e6;
        strom.crown();
        System.out.printf(Locale.GERMAN, "Abbruch (fracture, %s): %d Stücke frei, %d übergeben mit %d Zellen, %.3e m³ (%.1f %% der Platte) bis %.1f s, erste Übergabe nach %.2f s; %.0f ms%n",
                f.material.name, frei[0], stuecke, zellen, f.handedOverVolume(), 100 * f.handedOverVolume() / Presets.LITUYA_VOLUME, fertig, tFrei, ms);
        System.out.printf(Locale.GERMAN, "  Anker der Gleitfläche: Restkohäsion %.0f von %.0f kPa, Tragkraft %.0f %% des Gebirges im Großen (%.1f MPa); mit voller Kohäsion %.0f %%: hält%n",
                wand(FS, REST, WEG).cohesionAt(1e9), wand(FS, REST, WEG).cohesion, 100 * anker, f.material.cohesion() / 1e6, 100 * TRAG);
        notiz.add(String.format(Locale.GERMAN, "Abbruch: Anker der Gleitfläche mit der Restkohäsion %.0f kPa – %d Stücke, %d Zellen, %.3e m³ an den Strom übergeben (%.1f s)",
                wand(FS, REST, WEG).cohesionAt(1e9), stuecke, zellen, f.handedOverVolume(), fertig));

        // 3. Strom
        strom.runToRest(120);
        double[] e = strom.entrySummary();
        System.out.println("Strom aus dem Bruch:");
        System.out.println(LandslideDemo.text(strom).lines().map(l -> "  " + l).reduce((x, y) -> x + "\n" + y).orElse(""));
        System.out.printf(Locale.GERMAN, "  Volumen: übergeben %.6e m³, im Strom %.6e m³ + %.0f m³ über den Rand (Unterschied %.1e)%n", f.handedOverVolume(), strom.volume(), strom.lost(),
                Math.abs(strom.volume() + strom.lost() - f.handedOverVolume()) / f.handedOverVolume());
        Landslide platte = Presets.gilbertInlet1958(q);
        platte.runToRest(120);
        double[] ep = platte.entrySummary();
        System.out.printf(Locale.GERMAN, "Zum Vergleich die Platte des Presets ohne Bruch: Aufprall %.0f m/s (Bruch: %.0f m/s), 90 %% in %.1f s (%.1f s), Mächtigkeit bis %.0f m (%.0f m)%n",
                ep[3], e[3], ep[2], e[2], ep[5], e[5]);
        double ausl = modus.equals("newmark") ? nm.failTime() : WellenNetz.START_S - e[0];
        System.out.printf(Locale.GERMAN, "Uhr der Szene (Modus %s): Auslösung bei %.0f s, Zufluss ab %.0f s, 90 %% bis %.0f s; das Wellennetz startet bei %.0f s%n", modus, ausl, ausl + e[0],
                ausl + e[1], WellenNetz.START_S);
        notiz.add(String.format(Locale.GERMAN, "Strom: Aufprall %.0f m/s nach Rate (FRITZ01 96–110), 90 %% in %.1f s, bis %.0f m am Ufer; Platte ohne Bruch %.0f m/s",
                e[3], e[2], e[5], ep[3]));
        notiz.add(String.format(Locale.GERMAN, "Uhr (Modus %s): Auslösung %.0f s, Zufluss ab %.0f s (Wellennetz %.0f s)", modus, ausl, ausl + e[0], WellenNetz.START_S));

        // Bild: den Lauf bis tb noch einmal
        Landslide bild = Presets.gilbertInlet1958Terrain(q);
        Fracture f2 = platte(REST);
        while (f2.time() < tb) {
            f2.step(dt);
            for (Piece p : f2.pieces()) {
                double[] v = p.velocity();
                if (Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) < 2) continue;
                double[] c = f2.handOver(p);
                for (int i = 0; i < c.length; i += 8) bild.inject(c[i], c[i + 2], c[i + 3], c[i + 5], c[i + 7], 0.62 * Math.cbrt(c[i + 3]));
            }
            bild.step(dt);
            if (f2.time() > 3 && f2.pieces().isEmpty()) break;
        }
        bild.crown();
        bild.run(tb);
        bild.name = "Gilbert Inlet 1958 – Bruch und Strom";
        BufferedImage im = new BufferedImage(1800, 1100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        LandslideDemo.paint(g, bild, LandslideDemo.Frame.of(bild), strom.entries(), e, String.join("\n", notiz) + "\n" + LandslideDemo.text(strom), 1800, 1100);
        g.dispose();
        ImageIO.write(im, "png", aus);
        System.out.println("Bild: " + aus.getPath() + String.format(Locale.GERMAN, " (t = %.1f s nach der Auslösung)", bild.time()));
    }

    /**
     * Tragkraft der Anker auf der Gleitfläche bei voller Kohäsion der Klüfte, als Anteil der Festigkeit des Gebirges [G]:
     * so gewählt, dass die Platte im Bruch mit voller Kohäsion hält (Auslastung 0,7) und mit dem Rest von 20 % bricht – wie
     * Newmark mit FS 1,10 in Ruhe und 0,89 nach der Entfestigung.
     */
    public static final double TRAG = 0.5;

    /** Anteil der Tragkraft der Anker im letzten {@link #platte}. */
    public static double anker;

    /** Die NE-Wand des Gilbert Inlet mit Entfestigung. */
    public static Slope wand(double fs, double rest, double weg) {
        Slope s = QuakeDemo.gilbert();
        s.cohesion = s.cohesionFor(fs);
        s.residual = rest;
        s.softening = weg;
        s.failAt = 1.0;
        return s;
    }

    /**
     * Die Platte als Körper des Bruchs auf der Gleitfläche des Presets: Form wie {@link Presets#gilbertInlet1958} (Achsen
     * x hangabwärts, y oben, z quer), Fels mit der Festigkeit des Gebirges im Großen [G], Anker auf der Gleitfläche mit der
     * Tragkraft {@link #TRAG} × c(D)/c′: der Anteil der Kohäsion der Klüfte, der nach der Entfestigung bleibt.
     */
    public static Fracture platte(double rest) {
        double ta = Math.tan(Math.toRadians(Presets.LITUYA_SLOPE)), ca = Math.cos(Math.toRadians(Presets.LITUYA_SLOPE));
        double l = Presets.LITUYA_LENGTH, w = Presets.LITUYA_WIDTH, hm = Presets.LITUYA_HMAX, p = Presets.plateExponent(38.3 / 92);
        double xc = -Presets.LITUYA_CENTROID / ta, half = l / 2 * ca;
        Fracture f = Fracture.rock();
        f.seed = 1958;
        f.material.name = "Fels im Großen";
        f.material.tensile = 8e6;
        f.material.compressive = 1.2e8;
        f.crackFront = true;
        f.maxBreaksPerStep = 100_000;
        f.crackStep = 0.02;
        f.ground = (x, z) -> -x * ta;
        Shape.Height top = (x, z) -> {
            double s = (x - xc) / ca, tu = Math.abs(2 * s / l), tv = Math.abs(2 * z / w);
            double t = tu >= 1 || tv >= 1 ? 0 : hm * (1 - Math.pow(tu, p)) * (1 - Math.pow(tv, p));
            return -x * ta + Math.max(0.5, t) / ca;
        };
        Shape sh = Shape.field(xc - half, -w / 2, xc + half, w / 2, top, (x, z) -> -x * ta + 0.05, null);
        Solid s = f.addSolid(sh, f.cellsFor(400), Fracture.releaseZone(xc, -xc * ta, 0, ta, 1, 0, 25, 0.3), null);
        Slope w0 = wand(FS, rest, WEG);
        double k = TRAG * w0.cohesionAt(1e9) / w0.cohesion;
        s.anchor(face -> face.ny < -0.3 && face.p[1] < -face.p[0] * ta + 1, k);
        anker = k;
        return f;
    }
}
