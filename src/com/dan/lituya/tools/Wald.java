package com.dan.lituya.tools;

import com.dan.lituya.scene.Band1958;
import com.dan.lituya.scene.Boden1958;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Stufe;
import com.dan.lituya.scene.Wald1958;
import com.dan.succession.Succession;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Schritt {@code wald} der Aufbereitung (Phase 14a, Werkbuch 3.4, 5.6): die Kronenhöhe heute als Oberfläche minus Gelände.
 * Oberfläche ist das ArcticDEM-Mosaik 10 m (Kachel 42_06, Streifen 2009–2021, SETSM aus Maxar-Stereopaaren, Höhe über dem
 * WGS84-Ellipsoid, EPSG 3413), Gelände die Stufe Szene (IfSAR 2012, NAVD88). Den Versatz zwischen Ellipsoid und NAVD88
 * (Geoid) und den Rest beider Höhenbezüge misst der Schritt selbst: Median der Differenz auf kahlem Fels (steiler als 35°,
 * 800–1 800 m, kein Eis in der Fläche [G]); zur Probe auch am Strand (1–4 m).
 * <p>
 * Abgleich mit der Wiederbewaldung (Verhältnis, denn die gemessenen Kronen sind zu niedrig, siehe {@link #ALT}): mittlere Kronenhöhe im Band der Welle (Grund 1958 unter der Spurlinie, gezogen auf
 * die Belege) gegen außerhalb (gleiche Höhenlage, bis 1,5 km vom Ufer). Die Wiederbewaldung rechnet dasselbe Band bis zum
 * mittleren Jahr der Streifen; der Faktor auf die Wuchsrate aller Arten wird so gesucht (Halbierung), dass ihre mittlere
 * Höhe im Band (Lücken als 0) die gemessene trifft. Altbestand = mittlere Kronenhöhe außerhalb, wo Wald ist (über 12 m).
 * Ergebnis: {@code daten/wald/kronenhoehe_10m.r32} (Kern, 2400 × 1800, float32, Zeilen Nord → Süd, NaN = Wasser oder
 * ohne Wert) und {@code daten/wald/WALD.txt}.
 */
public final class Wald {
    private Wald() { }

    /** Mittleres Jahr der Streifen im Mosaik (die meisten Paare 2009–2011, einige 2021) [G]. */
    public static final int JAHR_DSM = 2011;
    /** Höhe des Altbestands (m) [G]: Sitka-Fichte und Hemlock im Küstenregenwald der Bucht. */
    public static final double ALT = 40;

    public static void rechnen(Rohdaten r) throws Exception {
        List<File> f = r.noetig("hoehe/arcticdem_10m", "_dem.tif");
        Quelle q = new Quelle("ArcticDEM 10 m", f, 0, 0, -50, 6500);
        r.log("  wald: %s", q);
        Gitter g = new Gitter(Site.KERN.eMin(), Site.KERN.nMax(), Wald1958.ZELLE, Wald1958.W, Wald1958.H);
        float[] dsm = g.leer();
        int n = q.proben(g, dsm);
        q.schliessen();
        r.log("  wald: Oberfläche %,d von %,d Zellen", n, dsm.length);
        Boden1958 b = new Boden1958(r.daten);
        float[] dtm = g.leer();
        boolean[] wasser = new boolean[dtm.length];
        java.util.stream.IntStream.range(0, g.h()).parallel().forEach(j -> {
            for (int i = 0; i < g.w(); i++) {
                int k = j * g.w() + i;
                double e = g.ce(i), nn = g.cn(j);
                wasser[k] = b.wasserHeute(e, nn);
                if (!wasser[k]) dtm[k] = (float) b.hoeheHeute(e, nn);
            }
        });
        // Versatz auf Fels und am Strand
        List<Float> fels = new ArrayList<>(), strand = new ArrayList<>();
        for (int j = 1; j < g.h() - 1; j++)
            for (int i = 1; i < g.w() - 1; i++) {
                int k = j * g.w() + i;
                float d = dsm[k] - dtm[k];
                if (Float.isNaN(d) || wasser[k]) continue;
                double hang = Math.toDegrees(Math.atan(Math.hypot(dtm[k + 1] - dtm[k - 1], dtm[k + g.w()] - dtm[k - g.w()]) / (2 * g.zelle())));
                if (dtm[k] > 800 && dtm[k] < 1800 && hang > 35 && Math.abs(d) < 60) fels.add(d);
                if (dtm[k] > 1 && dtm[k] < 4 && hang < 5 && Math.abs(d) < 60) strand.add(d);
            }
        double vf = median(fels), vs = median(strand);
        double versatz = Double.isNaN(vf) ? vs : vf;
        if (Double.isNaN(versatz)) throw new IllegalStateException("wald: kein Versatz messbar (keine Überdeckung von ArcticDEM und Gelände)");
        r.log("  wald: Versatz Ellipsoid − NAVD88 auf Fels %.2f m (%,d Zellen), am Strand %.2f m (%,d Zellen)", vf, fels.size(), vs, strand.size());
        float[] chm = g.leer();
        int gueltig = 0, ueber = 0;
        for (int k = 0; k < chm.length; k++) {
            if (wasser[k] || Float.isNaN(dsm[k]) || Float.isNaN(dtm[k])) continue;
            double c = dsm[k] - dtm[k] - versatz;
            if (c < -4 || c > 85) { ueber++; continue; }    // Gletscher, Rutschungen, Fehler: kein Wald messbar
            chm[k] = (float) Math.max(0, c);
            gueltig++;
        }
        File out = new File(r.daten, "wald");
        if (!out.isDirectory() && !out.mkdirs()) throw new java.io.IOException("kann " + out + " nicht anlegen");
        Stufe.schreiben(new File(out, "kronenhoehe_10m.r32"), chm);
        r.log("  wald: Kronenhöhe %,d Zellen (außer Bereich %,d), kronenhoehe_10m.r32 geschrieben", gueltig, ueber);

        // Band gegen außerhalb
        Band1958 band = new Band1958(r.daten, true);
        double sb = 0, sa = 0, sw = 0;
        int nb = 0, na = 0, nw = 0, wb = 0;
        float[] hb = new float[chm.length];
        int hbn = 0;
        for (int j = 0; j < g.h(); j++)
            for (int i = 0; i < g.w(); i++) {
                int k = j * g.w() + i;
                if (Float.isNaN(chm[k])) continue;
                double e = g.ce(i), nn = g.cn(j), g58 = b.hoehe(e, nn), t = band.spur(e, nn);
                if (!(g58 > 1.5) || Double.isNaN(t)) continue;
                if (g58 < t) { sb += chm[k]; nb++; hb[hbn++] = chm[k]; if (chm[k] > 2) wb++; }
                else if (g58 < Math.min(650, t + 250)) {
                    sa += chm[k]; na++;
                    if (chm[k] > 12) { sw += chm[k]; nw++; }
                }
            }
        double mb = nb > 0 ? sb / nb : Double.NaN, ma = na > 0 ? sa / na : Double.NaN, alt = nw > 0 ? sw / nw : 40;
        double p90 = quantil(Arrays.copyOf(hb, hbn), 0.9);
        r.log("  wald: im Band %.1f m (%,d Zellen, Deckung über 2 m %.0f %%, 90 %% %.1f m), außerhalb %.1f m (%,d), Wald außerhalb %.1f m",
                mb, nb, 100.0 * wb / Math.max(1, nb), p90, ma, na, alt);

        // Wiederbewaldung bis JAHR_DSM, Faktor auf die Wuchsrate
        // Die gemessene Kronenhöhe ist zu niedrig (Wald außerhalb im Mittel rund 17 m, 99 % unter 30 m, wo Sitka-Fichten 40–60 m
        // erreichen): das IfSAR-Gelände liegt unter dichtem Wald zu hoch, und das Mosaik glättet die Kronen auf 10 m. Darum
        // gilt das Verhältnis Band zu Wald außerhalb, nicht die Höhe: Ziel = gemessen im Band · ALT / Wald außerhalb [G].
        int y = JAHR_DSM - 1958;
        double skala = ALT / alt, ziel = mb * skala;
        r.log("  wald: gemessene Kronen zu niedrig: Ziel im Band %.1f m · %.0f / %.1f = %.1f m", mb, ALT, alt, ziel);
        Wald1958 w = new Wald1958(r.daten, true);
        w.alt = ALT;
        double lo = 0.4, hi = 2.5, fw = 1, best = Double.NaN;
        String zeile = "";
        Succession su = null;
        for (int it = 0; it < 8; it++) {
            fw = it == 0 ? 1 : (lo + hi) / 2;
            su = w.succession(fw, y);
            double hs = su.statistik(y).hoeheBand();
            if (it == 0) { zeile = su.zeile(y); r.log("  wald: Wiederbewaldung ohne Abgleich: %s", zeile); }
            r.log("  wald: Wuchs ×%.3f → im Band %.2f m (Ziel %.2f m)", fw, hs, ziel);
            if (Double.isNaN(ziel)) break;
            if (it > 0) { if (hs < ziel) lo = fw; else hi = fw; }
            else if (hs < ziel) lo = 1; else hi = 1;
            best = fw;
        }
        double wuchs = Double.isNaN(best) ? 1 : best;
        List<String> z = new ArrayList<>();
        z.add("# Wald (Phase 14a, " + java.time.LocalDate.now() + "): Kronenhöhe = ArcticDEM 10 m − Gelände Szene − Versatz; Abgleich mit com.dan.succession");
        z.add("quelle=" + f.size() + " Dateien ArcticDEM v4.1 (" + String.join(", ", f.stream().map(File::getName).toList()) + "), Streifen 2009–2021, Jahr " + JAHR_DSM + " [G]");
        z.add(String.format(Locale.ROOT, "versatz=%.3f  # Median auf Fels (%d Zellen); am Strand %.3f (%d Zellen)", versatz, fels.size(), vs, strand.size()));
        z.add(String.format(Locale.ROOT, "zellen=%d  # gültig; außer Bereich (Gletscher, Rutschung) %d", gueltig, ueber));
        z.add(String.format(Locale.ROOT, "band_m=%.2f  # mittlere Kronenhöhe im Band 1958 (gezogen), Lücken als 0; %d Zellen, 90 %% %.1f m, Deckung über 2 m %.3f", mb, nb, p90, wb / (double) Math.max(1, nb)));
        z.add(String.format(Locale.ROOT, "aussen_m=%.2f  # gleiche Höhenlage außerhalb, %d Zellen", ma, na));
        z.add(String.format(Locale.ROOT, "verhaeltnis=%.3f", mb / ma));
        z.add(String.format(Locale.ROOT, "alt_gemessen=%.1f  # Mittel außerhalb, wo Wald über 12 m (%d Zellen): zu niedrig (Gelände unter Wald zu hoch, Kronen geglättet)", alt, nw));
        z.add(String.format(Locale.ROOT, "alt=%.1f  # Altbestand [G]; Skala %.3f auf die gemessenen Höhen", ALT, ALT / alt));
        z.add(String.format(Locale.ROOT, "ziel_m=%.2f  # Höhe im Band nach Skala", mb * ALT / alt));
        z.add(String.format(Locale.ROOT, "wuchs=%.3f  # Faktor auf die Wuchsrate k aller Arten, damit die Wiederbewaldung im Jahr %d das Verhältnis Band/außen trifft", wuchs, y));
        z.add("ohne_abgleich=" + zeile);
        if (su != null) z.add("mit_abgleich=" + su.zeile(y));
        z.add(String.format(Locale.ROOT, "band=%s", band.quelle));
        Files.write(new File(out, "WALD.txt").toPath(), z, StandardCharsets.UTF_8);
        r.log("  wald: Wuchs ×%.3f, Altbestand %.0f m, WALD.txt geschrieben", wuchs, ALT);
    }

    static double median(List<Float> l) {
        if (l.isEmpty()) return Double.NaN;
        float[] a = new float[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return quantil(a, 0.5);
    }

    static double quantil(float[] a, double q) {
        if (a.length == 0) return Double.NaN;
        Arrays.sort(a);
        return a[(int) Math.min(a.length - 1, Math.round(q * (a.length - 1)))];
    }
}
