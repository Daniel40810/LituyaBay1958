package com.dan.lituya.scene;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die drei Stufen des Geländes in {@code daten/gelaende/} (Werkbuch 3.1 und 3.4, Phase 2), gelesen ab Phase 4 von
 * {@code clipmap}. Jede Stufe ist in Kacheln zu 2000 × 2000 Zellen geteilt, als {@code .r32}: float32, Little Endian,
 * Zeile für Zeile von Nord nach Süd, in der Zeile von West nach Ost, Höhe in m über NAVD88, NaN = kein Wert. Die Zelle
 * (i, j) einer Kachel mit der Südwestecke (E, N) hat ihre Mitte bei E + (i + ½)·Zelle, N + Kachel − (j + ½)·Zelle. Der
 * Dateiname ist {@code E<Ost>_N<Nord>.r32} der Südwestecke in m.
 * <ul>
 * <li><b>Kern</b> 0,5 m, Kacheln zu 1 km, 24 × 18 = 432 Kacheln (6,9 GB): Lidar 2019, im Wasser USACE 2019 und BlueTopo.</li>
 * <li><b>Szene</b> 5 m, Kacheln zu 10 km, 7 × 6 = 42 Kacheln: IfSAR 2012, 3DEP 1/3″, im Wasser BlueTopo.</li>
 * <li><b>Fern</b> 30 m, Kacheln zu 60 km, 5 × 4 = 20 Kacheln: 3DEP 1″, Copernicus, im Wasser BlueTopo und GEBCO.</li>
 * </ul>
 * Die Kacheln reichen an den Rändern von Szene und Fern über das Gebiet hinaus (ganze Kacheln) und sind auch dort gefüllt.
 */
public enum Stufe {
    KERN("kern_0.5m", 0.5, Site.KERN),
    SZENE("szene_5m", 5, Site.SZENE),
    FERN("fern_30m", 30, Site.FERN);

    /** Zellen je Kachelkante. */
    public static final int N = 2000;

    public final String ordner;
    public final double zelle;
    public final Site.Fenster gebiet;

    Stufe(String ordner, double zelle, Site.Fenster gebiet) {
        this.ordner = ordner;
        this.zelle = zelle;
        this.gebiet = gebiet;
    }

    /** Kantenlänge einer Kachel (m). */
    public double kachel() { return N * zelle; }

    /** Zahl der Kacheln in Ost- und Nordrichtung. */
    public int nx() { return (int) Math.ceil(gebiet.breite() / kachel() - 1e-9); }
    public int ny() { return (int) Math.ceil(gebiet.hoehe() / kachel() - 1e-9); }

    /** Südwestecken aller Kacheln, von Nord nach Süd, West nach Ost. */
    public List<double[]> kacheln() {
        List<double[]> l = new ArrayList<>();
        for (int j = ny() - 1; j >= 0; j--)
            for (int i = 0; i < nx(); i++) l.add(new double[]{gebiet.eMin() + i * kachel(), gebiet.nMin() + j * kachel()});
        return l;
    }

    public File ordner(File daten) { return new File(new File(daten, "gelaende"), ordner); }

    public File datei(File daten, double e, double n) { return new File(ordner(daten), name(e, n)); }

    public static String name(double e, double n) { return String.format(Locale.ROOT, "E%d_N%d.r32", Math.round(e), Math.round(n)); }

    /** Liest eine Kachel (N² Werte); null, wenn es sie nicht gibt. */
    public static float[] lesen(File f) throws IOException {
        if (!f.isFile()) return null;
        try (FileChannel ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
            long len = ch.size();
            ByteBuffer b = ByteBuffer.allocate((int) len).order(ByteOrder.LITTLE_ENDIAN);
            while (b.hasRemaining()) if (ch.read(b) < 0) break;
            b.flip();
            float[] v = new float[(int) (len / 4)];
            b.asFloatBuffer().get(v);
            return v;
        }
    }

    /** Schreibt eine Kachel (oder ein anderes Raster) als float32 Little Endian. */
    public static void schreiben(File f, float[] v) throws IOException {
        f.getParentFile().mkdirs();
        ByteBuffer b = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.asFloatBuffer().put(v);
        File tmp = new File(f.getPath() + ".tmp");
        try (FileChannel ch = FileChannel.open(tmp.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            while (b.hasRemaining()) ch.write(b);
        }
        java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Override public String toString() { return ordner; }
}
