package com.dan.lituya.tools;

import com.dan.lituya.scene.Geo;
import com.dan.lituya.scene.Site;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Schritt {@code stoerung} der Aufbereitung (Phase 8, Werkbuch 5.1, 9.1): die Fairweather-Störung und der Herd des Bebens
 * von 1958 für {@code com.dan.quake}.
 * <ul>
 * <li><b>Spur:</b> aus {@code geologie/stoerungen/Qfaults_GIS.zip} (USGS Quaternary Faults 2020, Shapefile in WGS84) alle
 * Abschnitte „Fairweather fault“ im Maßstab 1:500 000 zwischen 57,4° und 60,1° N, nach UTM 8 N. Die Abschnitte liegen
 * gegeneinander versetzt; daraus eine Spur: alle Punkte auf die Hauptachse projiziert und in Stücken zu 10 km gemittelt,
 * von Südost nach Nordwest. {@code daten/ereignis/stoerung.tsv} (die Spur) und {@code stoerung_abschnitte.tsv} (die
 * Rohpunkte mit Abschnitt, Linientyp, Alter).</li>
 * <li><b>Herd:</b> aus {@code ereignis/usgs_erdbeben/erdbeben_1958_iscgem884702.geojson} (ComCat, ISC-GEM): Länge,
 * Breite, Tiefe, Magnitude, Zeit; nach {@code daten/ereignis/beben.txt}.</li>
 * </ul>
 */
public final class Stoerung {
    private Stoerung() { }

    public static final String ORDNER = "ereignis";
    static final String QFAULTS = "geologie/stoerungen/Qfaults_GIS.zip", COMCAT = "ereignis/usgs_erdbeben/erdbeben_1958_iscgem884702.geojson";
    public static final double STUECK = 10_000;

    public static void rechnen(Rohdaten r) throws Exception { rechnen(r.roh, r.daten, r::log); }

    public static void rechnen(File roh, File daten, Welle.Log log) throws Exception {
        File dir = new File(daten, ORDNER);
        dir.mkdirs();
        // Herd
        String js = Files.readString(new File(roh, COMCAT).toPath(), StandardCharsets.UTF_8);
        Matcher c = Pattern.compile("\"coordinates\"\\s*:\\s*\\[\\s*([-0-9.eE]+)\\s*,\\s*([-0-9.eE]+)\\s*,\\s*([-0-9.eE]+)").matcher(js);
        if (!c.find()) throw new IOException("ComCat: keine Koordinaten in " + COMCAT);
        double lon = Double.parseDouble(c.group(1)), lat = Double.parseDouble(c.group(2)), tiefe = Double.parseDouble(c.group(3));
        double mag = zahl(js, "mag"), zeit = zahl(js, "time");
        double[] h = Geo.UTM8N.forward(lat, lon);
        Files.writeString(new File(dir, "beben.txt").toPath(), String.format(Locale.ROOT, """
                # Beben vom 9. Juli 1958 (ComCat iscgem884702, ISC-GEM), Phase 8
                breite=%.4f
                laenge=%.4f
                tiefe_km=%.1f
                magnitude=%.1f
                zeit_ms=%.0f
                ost=%.1f
                nord=%.1f
                """, lat, lon, tiefe, mag, zeit, h[0], h[1]), StandardCharsets.UTF_8);
        // Spur aus Qfaults
        List<double[]> pts = new ArrayList<>();
        List<String> roh2 = new ArrayList<>();
        roh2.add("ABSCHNITT\tSEQ\tOST\tNORD\tLINIENTYP\tALTER");
        try (ZipFile z = new ZipFile(new File(roh, QFAULTS))) {
            byte[] shp = lies(z, "SHP/Qfaults_US_Database.shp"), shx = lies(z, "SHP/Qfaults_US_Database.shx"), dbf = lies(z, "SHP/Qfaults_US_Database.dbf");
            ByteBuffer d = ByteBuffer.wrap(dbf).order(ByteOrder.LITTLE_ENDIAN);
            int n = d.getInt(4), hl = d.getShort(8) & 0xffff, rl = d.getShort(10) & 0xffff;
            Map<String, int[]> feld = new LinkedHashMap<>();
            int o = 32, pos = 1;
            while (dbf[o] != 0x0D) {
                String name = new String(dbf, o, 11, StandardCharsets.ISO_8859_1).split("\0")[0];
                int len = dbf[o + 16] & 0xff;
                feld.put(name, new int[]{pos, len});
                pos += len;
                o += 32;
            }
            int abschnitt = 0;
            for (int i = 0; i < n; i++) {
                int base = hl + i * rl;
                String name = wert(dbf, base, feld.get("section_na")), scale = wert(dbf, base, feld.get("scale"));
                if (!name.equals("Fairweather fault") || !scale.equals("1:500,000")) continue;
                abschnitt++;
                ByteBuffer x = ByteBuffer.wrap(shx).order(ByteOrder.BIG_ENDIAN);
                int off = x.getInt(100 + 8 * i) * 2;
                ByteBuffer s = ByteBuffer.wrap(shp).order(ByteOrder.LITTLE_ENDIAN);
                int rec = off + 8, nParts = s.getInt(rec + 36), nPts = s.getInt(rec + 40), p0 = rec + 44 + 4 * nParts;
                for (int k = 0; k < nPts; k++) {
                    double lo = s.getDouble(p0 + 16 * k), la = s.getDouble(p0 + 16 * k + 8);
                    if (la < 57.4 || la > 60.1) continue;
                    double[] u = Geo.UTM8N.forward(la, lo);
                    pts.add(u);
                    roh2.add(String.format(Locale.ROOT, "%d\t%d\t%.1f\t%.1f\t%s\t%s", abschnitt, k + 1, u[0], u[1], wert(dbf, base, feld.get("linetype")), wert(dbf, base, feld.get("age"))));
                }
            }
        }
        if (pts.size() < 10) throw new IOException("Qfaults: zu wenige Punkte der Fairweather-Störung (" + pts.size() + ")");
        Files.write(new File(dir, "stoerung_abschnitte.tsv").toPath(), roh2, StandardCharsets.UTF_8);
        double[][] spur = spur(pts);
        List<String> sp = new ArrayList<>();
        sp.add("SEQ\tOST\tNORD\tX\tZ\tS");
        double s = 0;
        for (int k = 0; k < spur.length; k++) {
            if (k > 0) s += Math.hypot(spur[k][0] - spur[k - 1][0], spur[k][1] - spur[k - 1][1]);
            sp.add(String.format(Locale.ROOT, "%d\t%.1f\t%.1f\t%.1f\t%.1f\t%.1f", k + 1, spur[k][0], spur[k][1], spur[k][0] - Site.E0, -(spur[k][1] - Site.N0), s));
        }
        Files.write(new File(dir, "stoerung.tsv").toPath(), sp, StandardCharsets.UTF_8);
        log.log("  stoerung: Herd %.2f° N %.2f° W %.0f km, M %.1f; Fairweather-Störung aus %d Punkten (Qfaults 2020) → Spur %.0f km in %d Stücken zu %.0f km",
                lat, -lon, tiefe, mag, pts.size(), s / 1000, spur.length - 1, STUECK / 1000);
    }

    /** Hauptachse (von Südost nach Nordwest), Mittel je Stück. */
    static double[][] spur(List<double[]> pts) {
        double mx = 0, my = 0;
        for (double[] p : pts) { mx += p[0]; my += p[1]; }
        mx /= pts.size(); my /= pts.size();
        double sxx = 0, syy = 0, sxy = 0;
        for (double[] p : pts) { sxx += (p[0] - mx) * (p[0] - mx); syy += (p[1] - my) * (p[1] - my); sxy += (p[0] - mx) * (p[1] - my); }
        double a = 0.5 * Math.atan2(2 * sxy, sxx - syy), ux = Math.cos(a), uy = Math.sin(a);
        if (uy < 0) { ux = -ux; uy = -uy; }   // nach Norden
        TreeMap<Long, double[]> bins = new TreeMap<>();
        for (double[] p : pts) {
            double s = (p[0] - mx) * ux + (p[1] - my) * uy, t = -(p[0] - mx) * uy + (p[1] - my) * ux;
            double[] b = bins.computeIfAbsent((long) Math.floor(s / STUECK), k -> new double[3]);
            b[0] += s; b[1] += t; b[2]++;
        }
        double[][] out = new double[bins.size()][];
        int i = 0;
        for (double[] b : bins.values()) {
            double s = b[0] / b[2], t = b[1] / b[2];
            out[i++] = new double[]{mx + s * ux - t * uy, my + s * uy + t * ux};
        }
        return out;
    }

    static byte[] lies(ZipFile z, String n) throws IOException {
        ZipEntry e = z.getEntry(n);
        if (e == null) throw new IOException("Qfaults: " + n + " fehlt im Zip");
        try (var in = z.getInputStream(e)) { return in.readAllBytes(); }
    }

    static String wert(byte[] dbf, int base, int[] f) { return f == null ? "" : new String(dbf, base + f[0], f[1], StandardCharsets.ISO_8859_1).trim(); }

    static double zahl(String js, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*([-0-9.eE]+)").matcher(js);
        return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
    }

    /** Liest die Spur: x, z (m um den Ursprung der Szene) abwechselnd, von Südost nach Nordwest. */
    public static double[] spurLesen(File daten) throws IOException {
        List<String> z = Files.readAllLines(new File(daten, ORDNER + "/stoerung.tsv").toPath(), StandardCharsets.UTF_8);
        double[] xz = new double[2 * (z.size() - 1)];
        for (int i = 1; i < z.size(); i++) {
            String[] f = z.get(i).split("\t");
            xz[2 * (i - 1)] = Double.parseDouble(f[3]);
            xz[2 * (i - 1) + 1] = Double.parseDouble(f[4]);
        }
        return xz;
    }

    /** Liest den Herd: Ost, Nord, Tiefe (m), Magnitude. */
    public static double[] herdLesen(File daten) throws IOException {
        java.util.Properties p = new java.util.Properties();
        try (var r = Files.newBufferedReader(new File(daten, ORDNER + "/beben.txt").toPath(), StandardCharsets.UTF_8)) { p.load(r); }
        return new double[]{Double.parseDouble(p.getProperty("ost")), Double.parseDouble(p.getProperty("nord")), Double.parseDouble(p.getProperty("tiefe_km")) * 1000,
                Double.parseDouble(p.getProperty("magnitude"))};
    }
}
