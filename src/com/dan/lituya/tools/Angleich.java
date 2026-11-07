package com.dan.lituya.tools;

import com.dan.lituya.scene.Stufe;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Schritt {@code angleich} der Aufbereitung (Werkbuch Welle W9c Runde 2, „Schwebe“): die gröberen Stufen dort, wo die
 * feinere liegt, als ihr Mittel.
 * <p>
 * Befund (docs/status/Schwebe_W09C.png): Die Clipmap zeichnet bis zur 4-m-Stufe (rund 380 m um das Auge) den Kern, also
 * das Lidar 2019, ab der 8-m-Stufe die Szene, also IfSAR 2012. Unter dichtem Wald liegt das IfSAR-Gelände zu hoch (das
 * Radar sieht nicht bis zum Boden; schon in Phase 14a bei der Kronenhöhe vermerkt): über 130 km² Wald im Kern im Median
 * 4,9 m, zu 25 % mehr als 12 m, zu 5 % mehr als 23 m, auf den Vorländern außen am meisten; an steilen Hängen liegt es
 * auch tiefer (Schatten des Radars). Der Wald stand auf der Szene (über {@code Boden1958} und {@code Wald1958.grundGitter})
 * und schwebte darum über dem nahen Gelände; dasselbe galt für die Bäche (Höhen aus dem 5-m-Stapel), und das Gelände
 * selbst hob und senkte sich um diese Meter, wenn eine Stelle von der 8-m- in die 4-m-Stufe kam.
 * <p>
 * Die Regel: Eine Zelle der Szene, die ganz im Kern liegt, ist das Mittel seiner 10 × 10 Zellen (mindestens die Hälfte mit
 * Wert); eine Zelle des Ferns, die ganz in der Szene liegt, das Mittel ihrer 6 × 6 Zellen. So zeigt jede Stufe der Clipmap
 * dasselbe Gelände, nur gröber, und alles, was aus der Szene liest (Wald, Bäche, Grund 1958, Netze), steht auf dem Lidar.
 * Außerhalb des Kerns nimmt die Szene seit W9c Runde 2 das Lidar selbst als erste Quelle ({@link Gelaende}); an der
 * Grenze des Kerns gibt es darum keine neue Stufe. Der Schritt ist wiederholbar (das Mittel des Kerns ist jedes Mal
 * dasselbe); mit {@code -Dlituya.sicherung=<Ordner>} kommen die alten Kacheln vorher dorthin, ohne Angleich rechnet
 * {@code ant aufbereitung -Dschritte=gelaende -Dneu=true -Dlituya.ohneAngleich=true} den alten Stand neu.
 */
public final class Angleich {
    private Angleich() { }

    /** Mindestanteil der feinen Zellen mit Wert, damit das Mittel gilt. */
    static final double MIN_ANTEIL = 0.5;
    /** Verteilung der Änderung: Fächer zu 0,1 m von −100 bis +100 m. */
    static final int FAECHER = 2001;

    public static void rechnen(Rohdaten r, int faeden) throws Exception {
        r.log("  angleich: Szene aus dem Kern (Mittel 10 × 10), Fern aus der Szene (Mittel 6 × 6)");
        String a = szeneAusKern(r, faeden);
        String b = fernAusSzene(r);
        anhaengen(r, Stufe.SZENE, a);
        anhaengen(r, Stufe.FERN, b);
    }

    /** Hängt die Zeile {@code angleich=…} an STUFE.txt der Stufe (eine alte ersetzt). */
    static void anhaengen(Rohdaten r, Stufe s, String zeile) throws IOException {
        File f = new File(s.ordner(r.daten), "STUFE.txt");
        if (!f.isFile()) return;
        List<String> l = new ArrayList<>(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
        l.removeIf(x -> x.startsWith("angleich="));
        l.add("angleich=" + zeile);
        Files.write(f.toPath(), l, StandardCharsets.UTF_8);
    }

    /** Globale Zelle (i von West, j von Nord) der Stufe s am Ort (e, n) der Südwestecke einer Zelle der feineren Stufe. */
    static long[] zelle(Stufe s, double e, double nNord) {
        double i = (e - s.gebiet.eMin()) / s.zelle, j = (s.gebiet.nMin() + s.ny() * s.kachel() - nNord) / s.zelle;
        long ii = Math.round(i), jj = Math.round(j);
        if (Math.abs(i - ii) > 1e-6 || Math.abs(j - jj) > 1e-6) throw new IllegalStateException("angleich: Raster von " + s + " nicht ausgerichtet bei E " + e + ", N " + nNord);
        return new long[]{ii, jj};
    }

    static String szeneAusKern(Rohdaten r, int faeden) throws Exception {
        Stufe S = Stufe.SZENE, K = Stufe.KERN;
        int f = (int) Math.round(S.zelle / K.zelle);   // 10
        // die Kacheln der Szene über dem Kern
        Map<Long, float[]> sz = new LinkedHashMap<>();
        Map<Long, double[]> ecke = new LinkedHashMap<>();
        for (double[] k : S.kacheln()) {
            if (k[0] + S.kachel() <= K.gebiet.eMin() || k[0] >= K.gebiet.eMax() || k[1] + S.kachel() <= K.gebiet.nMin() || k[1] >= K.gebiet.nMax()) continue;
            float[] v = Stufe.lesen(S.datei(r.daten, k[0], k[1]));
            if (v == null) continue;
            long key = Math.round(k[0]) * 10_000_000L + Math.round(k[1]);
            sz.put(key, v);
            ecke.put(key, k);
        }
        Map<Long, float[]> alt = new LinkedHashMap<>();
        for (Map.Entry<Long, float[]> e : sz.entrySet()) alt.put(e.getKey(), e.getValue().clone());
        AtomicLongArray hist = new AtomicLongArray(FAECHER);
        long t0 = System.nanoTime();
        ExecutorService ex = Executors.newFixedThreadPool(Math.max(1, faeden));
        List<Future<long[]>> fu = new ArrayList<>();
        for (double[] k : K.kacheln()) {
            fu.add(ex.submit(() -> {
                float[] v = Stufe.lesen(K.datei(r.daten, k[0], k[1]));
                if (v == null) return new long[]{0, 0};
                int nb = Stufe.N / f;   // 200 Zellen der Szene je Kachel des Kerns
                long gesetzt = 0, gleich = 0;
                for (int bj = 0; bj < nb; bj++)
                    for (int bi = 0; bi < nb; bi++) {
                        double sum = 0;
                        int n = 0;
                        for (int y = 0; y < f; y++) {
                            int o = (bj * f + y) * Stufe.N + bi * f;
                            for (int x = 0; x < f; x++) {
                                float h = v[o + x];
                                if (!Float.isNaN(h)) { sum += h; n++; }
                            }
                        }
                        if (n < MIN_ANTEIL * f * f) continue;
                        // Südwestecke dieser Szenezelle, ihre Kachel und Stelle
                        double e = k[0] + bi * S.zelle, nNord = k[1] + K.kachel() - bj * S.zelle;
                        double te = S.gebiet.eMin() + Math.floor((e - S.gebiet.eMin()) / S.kachel()) * S.kachel();
                        double tn = S.gebiet.nMin() + Math.floor((nNord - S.zelle - S.gebiet.nMin()) / S.kachel()) * S.kachel();
                        float[] t = sz.get(Math.round(te) * 10_000_000L + Math.round(tn));
                        if (t == null) continue;
                        int ci = (int) Math.round((e - te) / S.zelle), cj = (int) Math.round((tn + S.kachel() - nNord) / S.zelle);
                        int p = cj * Stufe.N + ci;
                        float neu = (float) (sum / n), war = t[p];
                        if (!Float.isNaN(war)) {
                            int b = (int) Math.round((neu - war) * 10) + FAECHER / 2;
                            hist.incrementAndGet(Math.max(0, Math.min(FAECHER - 1, b)));
                        }
                        if (neu == war) gleich++;
                        t[p] = neu;   // jede Zelle gehört genau einer Kachel des Kerns: kein Wettlauf
                        gesetzt++;
                    }
                return new long[]{gesetzt, gleich};
            }));
        }
        ex.shutdown();
        long gesetzt = 0, gleich = 0;
        for (Future<long[]> x : fu) { long[] g = x.get(); gesetzt += g[0]; gleich += g[1]; }
        int geschrieben = schreiben(r, S, sz, alt, ecke);
        String z = String.format(Locale.ROOT, "Szene aus dem Kern: %d Zellen (%.1f km²), Änderung %s; %d Kacheln geschrieben, %.0f s",
                gesetzt, gesetzt * S.zelle * S.zelle / 1e6, verteilung(hist), geschrieben, (System.nanoTime() - t0) / 1e9);
        r.log("  angleich: %s", z);
        return z;
    }

    static String fernAusSzene(Rohdaten r) throws Exception {
        Stufe F = Stufe.FERN, S = Stufe.SZENE;
        int f = (int) Math.round(F.zelle / S.zelle);   // 6
        StufenQuelle q = new StufenQuelle(S, r.daten);
        // die ganze Fläche der Kacheln der Szene (nicht nur das Fenster: die Kacheln reichen darüber hinaus)
        double e0 = S.gebiet.eMin(), e1 = S.gebiet.eMin() + S.nx() * S.kachel(), n0 = S.gebiet.nMin(), n1 = S.gebiet.nMin() + S.ny() * S.kachel();
        AtomicLongArray hist = new AtomicLongArray(FAECHER);
        long t0 = System.nanoTime(), gesetzt = 0;
        Map<Long, float[]> fe = new LinkedHashMap<>(), alt = new LinkedHashMap<>();
        Map<Long, double[]> ecke = new LinkedHashMap<>();
        for (double[] k : F.kacheln()) {
            double ke0 = Math.max(k[0], e0), ke1 = Math.min(k[0] + F.kachel(), e1), kn0 = Math.max(k[1], n0), kn1 = Math.min(k[1] + F.kachel(), n1);
            if (ke0 >= ke1 || kn0 >= kn1) continue;
            float[] v = Stufe.lesen(F.datei(r.daten, k[0], k[1]));
            if (v == null) continue;
            long key = Math.round(k[0]) * 10_000_000L + Math.round(k[1]);
            fe.put(key, v);
            alt.put(key, v.clone());
            ecke.put(key, k);
            // Zellen des Ferns ganz in der Szene
            int i0 = (int) Math.ceil((ke0 - k[0]) / F.zelle - 1e-9), i1 = (int) Math.floor((ke1 - k[0]) / F.zelle + 1e-9);
            int j0 = (int) Math.ceil((k[1] + F.kachel() - kn1) / F.zelle - 1e-9), j1 = (int) Math.floor((k[1] + F.kachel() - kn0) / F.zelle + 1e-9);
            for (int j = j0; j < j1; j++)
                for (int i = i0; i < i1; i++) {
                    double e = k[0] + i * F.zelle, nNord = k[1] + F.kachel() - j * F.zelle;
                    long[] c = zelle(S, e, nNord);
                    double sum = 0;
                    int n = 0;
                    for (int y = 0; y < f; y++)
                        for (int x = 0; x < f; x++) {
                            float h = q.zelle((int) c[0] + x, (int) c[1] + y);
                            if (!Float.isNaN(h)) { sum += h; n++; }
                        }
                    if (n < MIN_ANTEIL * f * f) continue;
                    int p = j * Stufe.N + i;
                    float neu = (float) (sum / n), war = v[p];
                    if (!Float.isNaN(war)) {
                        int b = (int) Math.round((neu - war) * 10) + FAECHER / 2;
                        hist.incrementAndGet(Math.max(0, Math.min(FAECHER - 1, b)));
                    }
                    v[p] = neu;
                    gesetzt++;
                }
        }
        int geschrieben = schreiben(r, F, fe, alt, ecke);
        String z = String.format(Locale.ROOT, "Fern aus der Szene: %d Zellen (%.0f km²), Änderung %s; %d Kacheln geschrieben, %.0f s",
                gesetzt, gesetzt * F.zelle * F.zelle / 1e6, verteilung(hist), geschrieben, (System.nanoTime() - t0) / 1e9);
        r.log("  angleich: %s", z);
        return z;
    }

    /** Schreibt die geänderten Kacheln (die alten vorher nach -Dlituya.sicherung, wenn gesetzt). */
    static int schreiben(Rohdaten r, Stufe s, Map<Long, float[]> neu, Map<Long, float[]> alt, Map<Long, double[]> ecke) throws IOException {
        int n = 0;
        String sich = System.getProperty("lituya.sicherung", "");
        for (Map.Entry<Long, float[]> e : neu.entrySet()) {
            if (java.util.Arrays.equals(e.getValue(), alt.get(e.getKey()))) continue;
            double[] k = ecke.get(e.getKey());
            File f = s.datei(r.daten, k[0], k[1]);
            if (!sich.isBlank() && f.isFile()) {
                File z = new File(new File(sich, s.ordner(r.daten).getName()), f.getName());
                if (!z.isFile()) {
                    z.getParentFile().mkdirs();
                    Files.copy(f.toPath(), z.toPath());
                }
            }
            Stufe.schreiben(f, e.getValue());
            n++;
        }
        return n;
    }

    /** „Median −0,4 m, 5 % −18,2 m, 95 % +3,1 m, |Δ| > 1 m: 42 %“ aus den Fächern. */
    static String verteilung(AtomicLongArray h) {
        long sum = 0, gross = 0;
        for (int i = 0; i < h.length(); i++) {
            sum += h.get(i);
            if (Math.abs(i - FAECHER / 2) > 10) gross += h.get(i);
        }
        if (sum == 0) return "keine";
        return String.format(Locale.ROOT, "Median %+.1f m, 5 %% %+.1f m, 95 %% %+.1f m, |Δ| > 1 m: %.0f %%",
                quantil(h, sum, 0.5), quantil(h, sum, 0.05), quantil(h, sum, 0.95), 100.0 * gross / sum);
    }

    static double quantil(AtomicLongArray h, long sum, double q) {
        long ziel = (long) Math.ceil(q * sum), acc = 0;
        for (int i = 0; i < h.length(); i++) {
            acc += h.get(i);
            if (acc >= ziel) return (i - FAECHER / 2) / 10.0;
        }
        return (h.length() - 1 - FAECHER / 2) / 10.0;
    }

    /** Für die Prüfung: Szene und Kern an Orten (UTM), die Kacheln einmal gelesen. */
    public static final class Vergleich {
        private final StufenQuelle s, k;

        public Vergleich(File daten) { s = new StufenQuelle(Stufe.SZENE, daten); k = new StufenQuelle(Stufe.KERN, daten); }

        /** Wert der Szenezelle am Ort und Mittel der 10 × 10 Zellen des Kerns darin (NaN ohne Daten). */
        public double[] zelle(double e, double n) throws IOException {
            double ce = Stufe.SZENE.gebiet.eMin() + Math.floor((e - Stufe.SZENE.gebiet.eMin()) / 5) * 5;
            double cn = Stufe.SZENE.gebiet.nMin() + Math.ceil((n - Stufe.SZENE.gebiet.nMin()) / 5) * 5;
            long[] cs = Angleich.zelle(Stufe.SZENE, ce, cn), ck = Angleich.zelle(Stufe.KERN, ce, cn);
            double sum = 0;
            int m = 0;
            for (int y = 0; y < 10; y++)
                for (int x = 0; x < 10; x++) {
                    float h = k.zelle((int) ck[0] + x, (int) ck[1] + y);
                    if (!Float.isNaN(h)) { sum += h; m++; }
                }
            return new double[]{s.zelle((int) cs[0], (int) cs[1]), m > 0 ? sum / m : Double.NaN};
        }

        /** Das Lidar (Kern, bilinear) am Ort. */
        public double kern(double e, double n) throws IOException { return k.wert(e, n); }
    }
}
