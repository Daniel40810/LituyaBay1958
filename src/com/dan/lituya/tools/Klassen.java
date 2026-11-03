package com.dan.lituya.tools;

import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Stufe;
import com.dan.lituya.scene.WasserNetz;
import com.dan.lituya.scene.WellenNetz;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Schritt {@code klassen} der Aufbereitung (Phase 16a, Werkbuch 3.4 und 7.5): die Geländeklassen für {@code LB_NET_WEG}
 * im Kern auf 10 m und in der Szene auf 50 m nach {@code daten/klassen/} ({@code kern_10m.u8}, {@code szene_50m.u8},
 * {@code KLASSEN.txt}). Quellen: ESA WorldCover 2021 (10 m), die Kronenhöhe aus dem Schritt {@code wald} (nur Kern), die
 * Gletscher aus RGI 7, die Spaltenzone aus ITS_LIVE (Dehnungsrate), die Bäche aus {@code LB_NET_WASSER} (Mittelwasser) und
 * das Gelände (Meer, Hangneigung, Strand).
 * <p>Aufruf: {@code Klassen [daten] [rohdaten]}.
 */
public final class Klassen {
    private Klassen() { }

    public static final String ORDNER = "klassen";
    /** Die Klassen; NaN im Zuschlag = gesperrt. */
    public static final String[] NAME = {"Meer", "Wasser", "Strand", "offen", "Moor", "Gebüsch", "Wald", "Fels", "Schutt", "Eis", "Schnee", "Spalten", "Bach"};
    public static final byte MEER = 0, WASSER = 1, STRAND = 2, OFFEN = 3, MOOR = 4, GEBUESCH = 5, WALD = 6, FELS = 7, SCHUTT = 8, EIS = 9, SCHNEE = 10, SPALTEN = 11, BACH = 12;
    /** Zuschlag auf die Gehzeit nach Tobler (Werkbuch 7.5: Wald 1,5, Gebüsch 2,5, Gletscher 2; die übrigen [G]). */
    public static final double[] ZUSCHLAG = {Double.NaN, Double.NaN, 1.0, 1.2, 2.0, 2.5, 1.5, 1.4, 1.6, 2.0, 1.8, Double.NaN, Double.NaN};
    /** Fels statt Schutt ab dieser Neigung [G]. */
    public static final double FELS_GRAD = 35;
    /** Strand: bis so hoch über Mittelwasser und so weit vom Meer [G]. */
    public static final double STRAND_HOCH = 8, STRAND_WEIT = 80;
    /** Spaltenzone: größte Hauptdehnungsrate (Zug) ab 0,05 /a; mit A = 2,4·10⁻²⁴ Pa⁻³ s⁻¹ (temperiertes Eis) rund 90 kPa, das untere Ende der Bruchspannung (Vaughan 1993) [G]. */
    public static final double SPALTEN_PRO_JAHR = 0.05;
    /** Bach gesperrt ab 0,5 m³/s bei Mittelwasser (Werkbuch 7.5): Niederschlag 3 000 mm/a [G], Beiwert Land 0,7, Eis 1,0 (mit Schmelze) [G]. */
    public static final double BACH_Q = 0.5, JAHRESNIEDERSCHLAG_MM = 3000, BEIWERT_LAND = 0.7, BEIWERT_EIS = 1.0;

    /** Ein Gitter der Klassen: NW-Ecke, Zelle, Größe. */
    public record Raster(String name, double e0, double n0, double zelle, int w, int h) {
        public double ost(int i) { return e0 + (i + 0.5) * zelle; }
        public double nord(int j) { return n0 - (j + 0.5) * zelle; }
        public File datei(File daten) { return new File(new File(daten, ORDNER), name + ".u8"); }
    }

    public static final Raster KERN = new Raster("kern_10m", Site.KERN.eMin(), Site.KERN.nMax(), 10, (int) Math.round(Site.KERN.breite() / 10), (int) Math.round(Site.KERN.hoehe() / 10));
    public static final Raster SZENE = new Raster("szene_50m", Site.SZENE.eMin(), Site.SZENE.nMax(), 50, (int) Math.round(Site.SZENE.breite() / 50), (int) Math.round(Site.SZENE.hoehe() / 50));

    public static void rechnen(Rohdaten r) throws Exception { rechnen(r.daten, r.roh, r::log); }

    public static void rechnen(File daten, File roh, Welle.Log log) throws Exception {
        long t0 = System.nanoTime();
        File wc = null;
        for (File f : Wasser.dateien(roh, "vegetation/esa_worldcover", ".tif")) if (f.getName().contains("N57W138")) wc = f;
        if (wc == null) throw new IOException("WorldCover N57W138 fehlt in download/vegetation/esa_worldcover (python tools/kartenmaterial/kartenmaterial.py laden --quellen esa_worldcover)");
        List<Rgi.Gletscher> gl = List.of();
        for (File f : Wasser.dateien(roh, "gletscher/rgi7", ".zip")) if (f.getName().startsWith("RGI2000-v7")) { double[] ll = Wasser.huelle(); gl = Rgi.lesen(f, ll[0], ll[1], ll[2], ll[3]); }
        float[] krone = null;
        File kh = new File(new File(daten, "wald"), "kronenhoehe_10m.r32");
        if (kh.isFile()) krone = Stufe.lesen(kh);
        if (krone != null && krone.length != KERN.w() * KERN.h()) krone = null;
        // Spaltenzone (120 m über der Szene)
        int WI = Wasser.W / 12, HI = Wasser.H / 12;
        float[] ve = new float[WI * HI], vn = new float[WI * HI];
        Arrays.fill(ve, Float.NaN);
        Arrays.fill(vn, Float.NaN);
        short[] alle = new short[WI * HI];
        Arrays.fill(alle, (short) 1);
        String vq = Wasser.itsLive(roh, WI, HI, ve, vn, alle, log);
        float[] deh = dehnung(ve, vn, WI, HI, Wasser.EIS_ZELLE);
        // Bäche mit Mittelwasser über 0,5 m³/s
        List<double[]> baeche = baeche(daten);
        List<String> kopf = new ArrayList<>();
        try (GeoTiff t = GeoTiff.open(wc)) {
            for (Raster r : new Raster[]{KERN, SZENE}) {
                float[] z = r == KERN ? kern10(daten) : szene50(daten);
                byte[] k = klassen(r, z, t, r == KERN ? krone : null, gl, deh, WI, baeche);
                File f = r.datei(daten);
                f.getParentFile().mkdirs();
                Files.write(f.toPath(), k);
                Stufe.schreiben(new File(f.getParentFile(), r.name().replace("kern", "gelaende_kern").replace("szene", "gelaende_szene") + ".r32"), z);
                long[] n = new long[NAME.length];
                for (byte b : k) n[b]++;
                StringBuilder s = new StringBuilder();
                for (int c = 0; c < NAME.length; c++) if (n[c] > 0) s.append(String.format(Locale.GERMAN, "%s %.1f %%, ", NAME[c], 100.0 * n[c] / k.length));
                String z1 = String.format(Locale.GERMAN, "%s: %d × %d zu %.0f m · %s", r.name(), r.w(), r.h(), r.zelle(), s.substring(0, s.length() - 2));
                log.log("  klassen %s", z1);
                kopf.add(String.format(Locale.ROOT, "raster.%s=%.0f %.0f %.0f %d %d", r.name(), r.e0(), r.n0(), r.zelle(), r.w(), r.h()));
                kopf.add("anteile." + r.name() + "=" + s.substring(0, s.length() - 2));
            }
        }
        List<String> tx = new ArrayList<>();
        tx.add("# Geländeklassen (Phase 16a, Werkbuch 7.5): je Zelle ein Byte, Zeilen von Nord nach Süd; Klasse = Nummer in klassen=; daneben das Gelände (float32) des Rasters");
        StringBuilder kl = new StringBuilder();
        for (int c = 0; c < NAME.length; c++) kl.append(c > 0 ? ", " : "").append(c).append(' ').append(NAME[c]).append(Double.isNaN(ZUSCHLAG[c]) ? " (gesperrt)" : String.format(Locale.ROOT, " (×%.1f)", ZUSCHLAG[c]));
        tx.add("klassen=" + kl);
        tx.addAll(kopf);
        tx.add("quellen=ESA WorldCover 2021 v200 (" + wc.getName() + "); Kronenhöhe daten/wald (" + (krone == null ? "fehlt" : "Kern") + "); RGI 7 (" + gl.size() + " Umrisse); " + vq
                + "; Bäche aus LB_NET_WASSER (" + baeche.size() + " Abschnitte über 0,5 m³/s bei Mittelwasser)");
        tx.add(String.format(Locale.ROOT, "regeln=Fels ab %.0f°; Strand bis %.0f m über MW und %.0f m vom Meer; Spalten ab %.2f /a; Bach ab %.1f m³/s bei %.0f mm/a (Beiwert Land %.1f, Eis %.1f) [G]",
                FELS_GRAD, STRAND_HOCH, STRAND_WEIT, SPALTEN_PRO_JAHR, BACH_Q, JAHRESNIEDERSCHLAG_MM, BEIWERT_LAND, BEIWERT_EIS));
        Files.write(new File(new File(daten, ORDNER), "KLASSEN.txt").toPath(), tx, StandardCharsets.UTF_8);
        log.log("  klassen nach %s in %.1f s", new File(daten, ORDNER), (System.nanoTime() - t0) / 1e9);
    }

    /** Mittelwasser (m³/s) aus Fläche (km²) und Eisanteil. */
    public static double mittelwasser(double km2, double eis) {
        return km2 * 1e6 * JAHRESNIEDERSCHLAG_MM / 1000 / (365.25 * 86400) * (BEIWERT_LAND * (1 - eis) + BEIWERT_EIS * eis);
    }

    /** Die Verläufe der Bäche (nicht auf dem Eis) mit Mittelwasser über BACH_Q, als Punktfolgen. */
    static List<double[]> baeche(File daten) throws IOException {
        List<double[]> l = new ArrayList<>();
        if (!WasserNetz.da(daten)) return l;
        WasserNetz w = WasserNetz.lesen(daten);
        java.util.Map<Integer, double[]> lin = new java.util.HashMap<>();
        for (String[] z : WellenNetz.tsv(new File(new File(daten, WasserNetz.ORDNER), "linien.tsv"))) {
            String[] p = z[1].split(",");
            double[] q = new double[2 * p.length];
            for (int k = 0; k < p.length; k++) { String[] xy = p[k].split(" "); q[2 * k] = Double.parseDouble(xy[0]); q[2 * k + 1] = Double.parseDouble(xy[1]); }
            lin.put(Integer.parseInt(z[0]) - 1, q);
        }
        for (int e = 0; e < w.m; e++) {
            if (w.kEbene[e] != 1 || !WasserNetz.BACH.equals(w.kArt[e])) continue;
            if (mittelwasser(w.kFlaeche[e], w.eisAnteil[w.von[e]]) < BACH_Q) continue;
            double[] q = lin.get(e);
            if (q != null) l.add(q);
        }
        return l;
    }

    /** Größte Hauptdehnungsrate (Zug, 1/a) des Eises auf dem 120-m-Raster, aus den Gradienten der Geschwindigkeit (3 × 3 geglättet). */
    static float[] dehnung(float[] ve, float[] vn, int w, int h, double d) {
        float[] ge = glatt(ve, w, h), gn = glatt(vn, w, h), r = new float[w * h];
        Arrays.fill(r, Float.NaN);
        for (int j = 1; j + 1 < h; j++)
            for (int i = 1; i + 1 < w; i++) {
                int c = j * w + i;
                double exx = (ge[c + 1] - ge[c - 1]) / (2 * d), eyy = -(gn[c + w] - gn[c - w]) / (2 * d);
                double eyx = -(ge[c + w] - ge[c - w]) / (2 * d), exy = (gn[c + 1] - gn[c - 1]) / (2 * d);
                double sh = 0.5 * (eyx + exy);
                // größte Hauptdehnungsrate: Spalten reißen unter Zug auf (Stauchung und Scherung allein öffnen keine)
                double e1 = 0.5 * (exx + eyy) + Math.sqrt(0.25 * (exx - eyy) * (exx - eyy) + sh * sh);
                if (!Double.isNaN(e1)) r[c] = (float) e1;
            }
        return r;
    }

    static float[] glatt(float[] v, int w, int h) {
        float[] r = new float[w * h];
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                double s = 0;
                int n = 0;
                for (int b = Math.max(0, j - 1); b <= Math.min(h - 1, j + 1); b++)
                    for (int a = Math.max(0, i - 1); a <= Math.min(w - 1, i + 1); a++) { float x = v[b * w + a]; if (!Float.isNaN(x)) { s += x; n++; } }
                r[j * w + i] = n >= 5 ? (float) (s / n) : Float.NaN;
            }
        return r;
    }

    static byte[] klassen(Raster r, float[] z, GeoTiff wc, float[] krone, List<Rgi.Gletscher> gl, float[] deh, int WI, List<double[]> baeche) throws IOException {
        int W = r.w(), H = r.h(), N = W * H;
        byte[] k = new byte[N];
        // Meer: unter Mittelwasser und mit dem Rand verbunden (wie LB_NET_WASSER)
        boolean[] meer = new boolean[N];
        int[] q = new int[N];
        int qh = 0, qt = 0;
        for (int c = 0; c < N; c++) {
            int i = c % W, j = c / W;
            if ((i == 0 || j == 0 || i == W - 1 || j == H - 1) && z[c] < Wasser.MW) { meer[c] = true; q[qt++] = c; }
        }
        while (qh < qt) {
            int c = q[qh++], i = c % W, j = c / W;
            for (int d = 0; d < 8; d += 2) {
                int a = i + Wasser.DI[d], b = j + Wasser.DJ[d];
                if (a < 0 || b < 0 || a >= W || b >= H) continue;
                int n = b * W + a;
                if (!meer[n] && z[n] < Wasser.MW) { meer[n] = true; q[qt++] = n; }
            }
        }
        // Abstand zum Meer (Zellen, bis zur Strandweite)
        int weit = (int) Math.ceil(STRAND_WEIT / r.zelle());
        int[] ab = new int[N];
        Arrays.fill(ab, Integer.MAX_VALUE);
        qh = 0; qt = 0;
        for (int c = 0; c < N; c++) if (meer[c]) { ab[c] = 0; q[qt++] = c; }
        while (qh < qt) {
            int c = q[qh++], i = c % W, j = c / W;
            if (ab[c] >= weit) continue;
            for (int d = 0; d < 8; d++) {
                int a = i + Wasser.DI[d], b = j + Wasser.DJ[d];
                if (a < 0 || b < 0 || a >= W || b >= H) continue;
                int n = b * W + a;
                if (ab[n] > ab[c] + 1) { ab[n] = ab[c] + 1; q[qt++] = n; }
            }
        }
        // Gletscher
        short[] eis = new short[N];
        for (int g = 0; g < gl.size() && g < Short.MAX_VALUE; g++) rastern(gl.get(g).ringe(), (short) (g + 1), eis, r);
        // WorldCover: im Kern die Zellmitte, in der Szene die häufigste von 5 × 5 Proben
        int proben = r.zelle() > 10 ? 5 : 1;
        Abbildung ab4326 = Abbildung.von(wc);
        double[] p = new double[2];
        int[] zahl = new int[256];
        for (int j = 0; j < H; j++)
            for (int i = 0; i < W; i++) {
                int c = j * W + i;
                if (meer[c]) { k[c] = MEER; continue; }
                Arrays.fill(zahl, 0);
                int best = 0;
                for (int b = 0; b < proben; b++)
                    for (int a = 0; a < proben; a++) {
                        double e = r.e0() + i * r.zelle() + (a + 0.5) * r.zelle() / proben, n = r.n0() - j * r.zelle() - (b + 0.5) * r.zelle() / proben;
                        ab4326.vonUtm(e, n, p);
                        int ci = (int) Math.floor(wc.col(p[0]) + 0.5), cj = (int) Math.floor(wc.row(p[1]) + 0.5);
                        int v = ci < 0 || cj < 0 || ci >= wc.width || cj >= wc.height ? 0 : (int) wc.get(ci, cj, 0) & 0xFF;
                        if (++zahl[v] > zahl[best]) best = v;
                    }
                double gr = Math.toDegrees(Math.atan(neigung(z, W, H, i, j, r.zelle())));
                byte kl = switch (best) {
                    case 10 -> WALD;
                    case 20, 95 -> GEBUESCH;
                    case 60 -> gr >= FELS_GRAD ? FELS : SCHUTT;
                    case 70 -> SCHNEE;
                    case 80 -> WASSER;
                    case 90 -> MOOR;
                    default -> OFFEN;
                };
                if (krone != null) {
                    float kh = krone[c];
                    if (!Float.isNaN(kh) && (kl == WALD || kl == GEBUESCH || kl == OFFEN || kl == MOOR)) kl = kh >= 5 ? WALD : kh >= 1 ? GEBUESCH : kl == WALD ? GEBUESCH : kl;
                }
                if ((kl == SCHUTT || kl == OFFEN || kl == FELS) && ab[c] <= weit && z[c] < Wasser.MW + STRAND_HOCH) kl = STRAND;
                if (eis[c] != 0 && kl != WASSER) {
                    kl = EIS;
                    double e = r.ost(i), n = r.nord(j);
                    int ii = (int) ((e - Wasser.E0) / Wasser.EIS_ZELLE), jj = (int) ((Wasser.N0 - n) / Wasser.EIS_ZELLE);
                    if (ii >= 0 && jj >= 0 && ii < WI && jj < deh.length / WI) { float x = deh[jj * WI + ii]; if (!Float.isNaN(x) && x >= SPALTEN_PRO_JAHR) kl = SPALTEN; }
                }
                k[c] = kl;
            }
        // Bäche über 0,5 m³/s (Mittelwasser): die Zellen ihres Verlaufs, in Schritten von einer halben Zelle
        for (double[] l : baeche)
            for (int s = 0; s + 3 < l.length; s += 2) {
                double L = Math.hypot(l[s + 2] - l[s], l[s + 3] - l[s + 1]);
                int n = Math.max(1, (int) Math.ceil(L / (r.zelle() / 2)));
                for (int t = 0; t <= n; t++) {
                    double e = l[s] + (l[s + 2] - l[s]) * t / n, nn = l[s + 1] + (l[s + 3] - l[s + 1]) * t / n;
                    int i = (int) Math.floor((e - r.e0()) / r.zelle()), j = (int) Math.floor((r.n0() - nn) / r.zelle());
                    if (i < 0 || j < 0 || i >= W || j >= H) continue;
                    int c = j * W + i;
                    if (k[c] != MEER) k[c] = BACH;
                }
            }
        return k;
    }

    /** tan der Neigung aus den zentralen Differenzen. */
    static double neigung(float[] z, int W, int H, int i, int j, double d) {
        int a = Math.max(0, i - 1), b = Math.min(W - 1, i + 1), c = Math.max(0, j - 1), e = Math.min(H - 1, j + 1);
        double gx = (z[j * W + b] - z[j * W + a]) / ((b - a) * d), gy = (z[e * W + i] - z[c * W + i]) / ((e - c) * d);
        return Math.hypot(gx, gy);
    }

    static void rastern(List<double[]> ringe, short wert, short[] out, Raster r) {
        List<double[]> xy = new ArrayList<>();
        double ymin = 1e18, ymax = -1e18;
        for (double[] g : ringe) {
            double[] p = new double[g.length];
            for (int q = 0; q < g.length; q += 2) {
                double[] en = com.dan.lituya.scene.Geo.UTM8N.forward(g[q + 1], g[q]);
                p[q] = (en[0] - r.e0()) / r.zelle() - 0.5;
                p[q + 1] = (r.n0() - en[1]) / r.zelle() - 0.5;
                ymin = Math.min(ymin, p[q + 1]);
                ymax = Math.max(ymax, p[q + 1]);
            }
            xy.add(p);
        }
        int j0 = Math.max(0, (int) Math.ceil(ymin)), j1 = Math.min(r.h() - 1, (int) Math.floor(ymax));
        double[] xs = new double[64];
        for (int j = j0; j <= j1; j++) {
            int nx = 0;
            for (double[] p : xy)
                for (int q = 0; q < p.length; q += 2) {
                    int s = (q + 2) % p.length;
                    double ya = p[q + 1], yb = p[s + 1];
                    if ((ya <= j && yb > j) || (yb <= j && ya > j)) {
                        if (nx == xs.length) xs = Arrays.copyOf(xs, nx * 2);
                        xs[nx++] = p[q] + (j - ya) / (yb - ya) * (p[s] - p[q]);
                    }
                }
            Arrays.sort(xs, 0, nx);
            for (int t = 0; t + 1 < nx; t += 2) {
                int i0 = Math.max(0, (int) Math.ceil(xs[t])), i1 = Math.min(r.w() - 1, (int) Math.floor(xs[t + 1]));
                for (int i = i0; i <= i1; i++) out[j * r.w() + i] = wert;
            }
        }
    }

    /** Das Gelände des Kerns auf 10 m (Mittel aus 20 × 20 Zellen von kern_0.5m). */
    public static float[] kern10(File daten) throws IOException {
        Raster r = KERN;
        float[] z = new float[r.w() * r.h()];
        Arrays.fill(z, Float.NaN);
        int f = (int) Math.round(r.zelle() / Stufe.KERN.zelle), nk = Stufe.N / f;
        for (double[] kk : Stufe.KERN.kacheln()) {
            float[] t = Stufe.lesen(Stufe.KERN.datei(daten, kk[0], kk[1]));
            if (t == null) continue;
            int i0 = (int) Math.round((kk[0] - r.e0()) / r.zelle()), j0 = (int) Math.round((r.n0() - (kk[1] + Stufe.KERN.kachel())) / r.zelle());
            mitteln(t, f, nk, i0, j0, z, r);
        }
        for (int c = 0; c < z.length; c++) if (Float.isNaN(z[c])) z[c] = -50;
        return z;
    }

    /** Das Gelände der Szene auf 50 m (Mittel aus 10 × 10 Zellen von szene_5m). */
    public static float[] szene50(File daten) throws IOException {
        Raster r = SZENE;
        float[] z = new float[r.w() * r.h()];
        Arrays.fill(z, Float.NaN);
        int f = (int) Math.round(r.zelle() / Stufe.SZENE.zelle), nk = Stufe.N / f;
        for (double[] kk : Stufe.SZENE.kacheln()) {
            float[] t = Stufe.lesen(Stufe.SZENE.datei(daten, kk[0], kk[1]));
            if (t == null) continue;
            int i0 = (int) Math.round((kk[0] - r.e0()) / r.zelle()), j0 = (int) Math.round((r.n0() - (kk[1] + Stufe.SZENE.kachel())) / r.zelle());
            mitteln(t, f, nk, i0, j0, z, r);
        }
        for (int c = 0; c < z.length; c++) if (Float.isNaN(z[c])) z[c] = -200;
        return z;
    }

    static void mitteln(float[] t, int f, int nk, int i0, int j0, float[] z, Raster r) {
        for (int jj = 0; jj < nk; jj++) {
            int j = j0 + jj;
            if (j < 0 || j >= r.h()) continue;
            for (int ii = 0; ii < nk; ii++) {
                int i = i0 + ii;
                if (i < 0 || i >= r.w()) continue;
                double s = 0;
                int c = 0;
                for (int b = 0; b < f; b++) for (int a = 0; a < f; a++) { float v = t[(f * jj + b) * Stufe.N + f * ii + a]; if (!Float.isNaN(v)) { s += v; c++; } }
                if (c > 0) z[j * r.w() + i] = (float) (s / c);
            }
        }
    }

    /** Liest ein Klassenraster (null, wenn es fehlt). */
    public static byte[] lesen(File daten, Raster r) throws IOException {
        File f = r.datei(daten);
        return f.isFile() ? Files.readAllBytes(f.toPath()) : null;
    }

    /** Liest das Gelände eines Klassenrasters. */
    public static float[] gelaende(File daten, Raster r) throws IOException {
        return Stufe.lesen(new File(new File(daten, ORDNER), r.name().replace("kern", "gelaende_kern").replace("szene", "gelaende_szene") + ".r32"));
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        rechnen(new File(a.length > 0 ? a[0] : "daten"), new File(a.length > 1 ? a[1] : "download"), (f, x) -> System.out.println(String.format(Locale.GERMAN, f, x)));
    }
}
