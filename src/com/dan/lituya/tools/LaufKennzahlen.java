package com.dan.lituya.tools;

import com.dan.lituya.data.Speicher;
import com.dan.lituya.scene.EreignisDaten;
import com.dan.lituya.scene.Site;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Die Kennzahlen eines vorgerechneten Laufs (Phase 19, für {@code LB_EREIGNIS_LAUF}) aus {@code daten/ereignis/<lauf>}:
 * größter Stand ({@code max}) überall und am Sporn (bis 500 m um den Beleg), erste Ankunft ({@code ankunft}, s nach dem
 * Stoß) an Cenotaph Island (bis 700 m), in der Anchorage Cove (bis 300 m) und am Eingang (bis 250 m um die Mitte zwischen
 * Harbor Point und La Chaussee Spit). Je Ort das feinste Gitter, das ihn enthält. Stand und Auflauf über NAVD88 minus
 * Pegel des Laufs.
 */
public final class LaufKennzahlen {
    private LaufKennzahlen() { }

    /** Alle Läufe unter daten/ereignis (Ordner mit EREIGNIS.txt und ankunft_g0.r32). */
    public static List<Speicher.Lauf> alle(File daten) throws IOException {
        List<Speicher.Lauf> l = new ArrayList<>();
        // nur Läufe der Welle (mit Ankunft); staub und strom haben auch eine EREIGNIS.txt, sind aber keine Läufe
        File[] fs = new File(daten, "ereignis").listFiles(f -> new File(f, "EREIGNIS.txt").isFile() && new File(f, "ankunft_g0.r32").isFile());
        if (fs == null) return l;
        java.util.Arrays.sort(fs);
        for (File f : fs) l.add(lauf(f));
        return l;
    }

    public static Speicher.Lauf lauf(File dir) throws IOException {
        java.util.Properties p = new java.util.Properties();
        try (java.io.Reader r = Files.newBufferedReader(new File(dir, "EREIGNIS.txt").toPath(), StandardCharsets.ISO_8859_1)) { p.load(r); }
        StringBuilder eb = new StringBuilder();
        for (String k : new java.util.TreeSet<>(p.stringPropertyNames())) eb.append(k).append('=').append(p.getProperty(k)).append('\n');
        String eingaben = eb.toString();
        double pegel = zahl(p, "pegel_m", 0), start = zahl(p, "start_s", Double.NaN);
        int n = (int) zahl(p, "gitter", 0);
        List<EreignisDaten.Gitter> g = new ArrayList<>();
        StringBuilder gt = new StringBuilder();
        for (int k = 0; k < n; k++) {
            g.add(new EreignisDaten.Gitter(zahl(p, "g" + k + ".e0", 0), zahl(p, "g" + k + ".n0", 0), zahl(p, "g" + k + ".zelle", 1), (int) zahl(p, "g" + k + ".nx", 0), (int) zahl(p, "g" + k + ".nz", 0)));
            gt.append(k == 0 ? "" : " · ").append(String.format(java.util.Locale.GERMAN, "%d × %d zu %.0f m", g.get(k).nx(), g.get(k).nz(), g.get(k).zelle()));
        }
        float[][] max = new float[n][], an = new float[n][];
        for (int k = 0; k < n; k++) {
            max[k] = raster(dir, "max", k);
            an[k] = raster(dir, "ankunft", k);
        }
        double alles = -Double.MAX_VALUE;
        for (int k = 0; k < n; k++) if (max[k] != null) for (float v : max[k]) if (!Float.isNaN(v) && v < 5000) alles = Math.max(alles, v);
        double sporn = extrem(g, max, Site.SPORN.e(), Site.SPORN.n(), 500, true);
        double ceno = extrem(g, an, Site.CENOTAPH.e(), Site.CENOTAPH.n(), 700, false);
        double anch = extrem(g, an, Site.ANCHORAGE.e(), Site.ANCHORAGE.n(), 300, false);
        double ee = (Site.HARBOR_POINT.e() + Site.CHAUSSEE.e()) / 2, en = (Site.HARBOR_POINT.n() + Site.CHAUSSEE.n()) / 2;
        double ein = extrem(g, an, ee, en, 250, false);
        double dauer = Double.NaN;
        for (int k = 0; k < n; k++) if (an[k] != null) for (float v : an[k]) if (!Float.isNaN(v) && v > 0 && v < 1e6) dauer = Double.isNaN(dauer) ? v : Math.max(dauer, v);
        String name = dir.getName();
        String quelle = p.getProperty("quelle", "");
        return new Speicher.Lauf(name, "vorgerechnet", quelle, p.getProperty("linie", ""), 30.6e6, pegel, "1940", name.split("_")[0],
                start, gt.toString(), p.getProperty("rechner", ""), dauer, alles == -Double.MAX_VALUE ? Double.NaN : alles - pegel, Double.isNaN(sporn) ? Double.NaN : sporn - pegel,
                ceno, anch, ein, eingaben);
    }

    static double zahl(java.util.Properties p, String k, double d) {
        String v = p.getProperty(k);
        if (v == null) return d;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return d; }
    }

    static float[] raster(File dir, String name, int k) throws IOException {
        File f = new File(dir, name + "_g" + k + ".r32");
        if (!f.isFile()) return null;
        byte[] b = Files.readAllBytes(f.toPath());
        float[] v = new float[b.length / 4];
        java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(v);
        return v;
    }

    /** Größter (max) oder kleinster gültiger Wert bis rad m um (e, n) im feinsten Gitter, das den Ort enthält; NaN ohne. */
    static double extrem(List<EreignisDaten.Gitter> gs, float[][] v, double e, double n, double rad, boolean max) {
        for (int k = gs.size() - 1; k >= 0; k--) {
            EreignisDaten.Gitter g = gs.get(k);
            if (v[k] == null || !g.enthaelt(e, n)) continue;
            double best = Double.NaN;
            int r = (int) Math.ceil(rad / g.zelle()), ci = (int) Math.floor((e - g.e0()) / g.zelle()), cj = (int) Math.floor((n - g.n0()) / g.zelle());
            for (int j = cj - r; j <= cj + r; j++)
                for (int i = ci - r; i <= ci + r; i++) {
                    if (i < 0 || j < 0 || i >= g.nx() || j >= g.nz() || Math.hypot(g.e(i) - e, g.n(j) - n) > rad) continue;
                    float x = v[k][j * g.nx() + i];
                    if (Float.isNaN(x) || Float.isInfinite(x) || (!max && (x <= 0 || x > 1e6)) || (max && x > 5000)) continue;
                    if (Double.isNaN(best) || (max ? x > best : x < best)) best = x;
                }
            if (!Double.isNaN(best)) return best;
        }
        return Double.NaN;
    }
}
