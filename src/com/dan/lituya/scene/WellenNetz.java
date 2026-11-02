package com.dan.lituya.scene;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Das Netz der Welle als Datei-Abbild (Werkbuch 7.1, Phase 7a, F25 A): {@code daten/netz/welle/} mit Knoten (Zellen zu
 * 20 m über dem Wasser der Bucht und 1 km See vor dem Eingang, Tiefen 1940, 1959 und heute, Amplitude), Kanten (16
 * Nachbarn, ungerichtet, vier Kosten) und der Ankunft der Front je Knoten und Kostenart. Geschrieben von der
 * Aufbereitung ({@code tools.Welle}, Schritt {@code welle}), gelesen von App, Prüfung und {@code tools.NetzDb}; Oracle
 * ({@code LB_NET_WELLE}) hat dieselben Nummern.
 * <p>
 * Raster wie {@code daten/grund/*_20m.r32}: Nordwestecke E 340 000, N 6 512 000, 1 200 × 900 Zellen; Knoten-Nummer =
 * fortlaufend in Zeilen von Nord nach Süd; der Knoten {@link #quelle} (die letzte Nummer) ist der virtuelle Knoten
 * {@code QUELLE} mit Kosten 0 zu allen Knoten der Einschlaglinie.
 */
public final class WellenNetz {
    public static final String ORDNER = "netz/welle";
    public static final double E_NW = 340_000, N_NW = 6_512_000, ZELLE = 20;
    public static final int BREITE = 1200, HOEHE = 900;
    /** Die vier Kostenarten (F22): linear über dem Grund heute, 1940, 1959 und nichtlinear heute. */
    public static final String[] KOSTEN = {"T_HEUTE", "T_1940", "T_1959", "T_NICHTLINEAR"};
    /** Lesbare Namen der Kostenarten. */
    public static final String[] KOSTEN_NAME = {"heute linear", "1940 linear", "1959 linear", "heute nichtlinear"};
    /**
     * Wann die Welle startet, in Sekunden der Simulationsuhr ab dem ersten Stoß: der Felssturz löst sich 60 bis 150 s
     * danach (FRITZ01), Eintauchen ≈ 7 s; hier 105 s [G].
     */
    public static final double START_S = 105;

    /**
     * Orte für Prüfwege, Strahlen und Tabellen: Name, Ost, Nord, Suchradius (m). Der Sporn liegt heute hinter der
     * Gletscherfront des Gilbert Inlet (kein Wasser in 400 m; in 1 km nur die Einschlaglinie); er kommt mit dem Ufer 1958 (Schritt rueckbau1958).
     */
    public static final Object[][] ORTE = {
            {"Sporn", Site.SPORN.e(), Site.SPORN.n(), 400.0},
            {"Cenotaph Island", Site.CENOTAPH.e(), Site.CENOTAPH.n(), 700.0},
            {"Anchorage Cove", Site.ANCHORAGE.e(), Site.ANCHORAGE.n(), 400.0},
            {"Eingang", Site.CHAUSSEE.e(), Site.CHAUSSEE.n(), 400.0},
    };

    /** Zahl der Gitterknoten (ohne {@code QUELLE}). */
    public int n;
    public int[] i, j;
    public float[] tiefeHeute, tiefe1940, tiefe1959, amplitude;
    public boolean[] einschlag;
    /** Ankunft je Kostenart und Knoten (s), NaN unerreicht; Vorgänger im Baum heute linear (−1 an der Quelle). */
    public float[][] ankunft = new float[4][];
    public int[] vorgaenger;
    /** Nummer des virtuellen Knotens QUELLE (= n + 1); seine Lage. */
    public int quelle;
    public double quelleE, quelleN;
    /** Kanten im Gitter (ohne die zur Quelle) und je Zelle die Knotennummer (0 = kein Knoten). */
    public int kanten;
    public int[] knotenJeZelle = new int[BREITE * HOEHE];
    public String text = "";

    public static double ost(int i) { return E_NW + (i + 0.5) * ZELLE; }
    public static double nord(int j) { return N_NW - (j + 0.5) * ZELLE; }

    /** Liest das Abbild (ohne kanten.tsv, die App braucht sie nicht). */
    public static WellenNetz lesen(File daten) throws IOException {
        File d = new File(daten, ORDNER);
        WellenNetz w = new WellenNetz();
        List<String[]> kn = tsv(new File(d, "knoten.tsv"));
        w.n = kn.size() - 1;   // ohne QUELLE
        w.i = new int[w.n]; w.j = new int[w.n];
        w.tiefeHeute = new float[w.n]; w.tiefe1940 = new float[w.n]; w.tiefe1959 = new float[w.n]; w.amplitude = new float[w.n];
        w.einschlag = new boolean[w.n];
        for (String[] z : kn) {
            int id = Integer.parseInt(z[0]);
            if (z[1].equals("QUELLE")) { w.quelle = id; w.quelleE = Double.parseDouble(z[4]); w.quelleN = Double.parseDouble(z[5]); continue; }
            int k = id - 1;
            w.i[k] = Integer.parseInt(z[2]); w.j[k] = Integer.parseInt(z[3]);
            w.tiefeHeute[k] = Float.parseFloat(z[6]); w.tiefe1940[k] = Float.parseFloat(z[7]); w.tiefe1959[k] = Float.parseFloat(z[8]);
            w.amplitude[k] = Float.parseFloat(z[9]);
            w.einschlag[k] = z[1].equals("EINSCHLAG");
            w.knotenJeZelle[w.j[k] * BREITE + w.i[k]] = id;
        }
        for (int a = 0; a < 4; a++) { w.ankunft[a] = new float[w.n]; Arrays.fill(w.ankunft[a], Float.NaN); }
        w.vorgaenger = new int[w.n];
        for (String[] z : tsv(new File(d, "ankunft.tsv"))) {
            int k = Integer.parseInt(z[0]) - 1;
            if (k < 0 || k >= w.n) continue;
            for (int a = 0; a < 4; a++) w.ankunft[a][k] = z[1 + a].isEmpty() ? Float.NaN : Float.parseFloat(z[1 + a]);
            w.vorgaenger[k] = Integer.parseInt(z[5]);
        }
        File t = new File(d, "WELLE.txt");
        if (t.isFile()) {
            w.text = Files.readString(t.toPath(), StandardCharsets.UTF_8);
            for (String z : w.text.split("\\R")) if (z.startsWith("kanten=")) w.kanten = Integer.parseInt(z.substring(7).split("\\s")[0]);
        }
        return w;
    }

    /** Ankunft der Kostenart a als Raster (BREITE × HOEHE, Zeilen von Nord nach Süd), NaN außerhalb des Netzes. */
    public float[] feld(int a) {
        float[] f = new float[BREITE * HOEHE];
        Arrays.fill(f, Float.NaN);
        for (int k = 0; k < n; k++) f[j[k] * BREITE + i[k]] = ankunft[a][k];
        return f;
    }

    /** Früheste Ankunft (Kostenart a) in Knoten bis r Meter um (e, n); NaN, wenn keiner. */
    public double ankunftBei(int a, double e, double nn, double r) {
        int ci = (int) Math.floor((e - E_NW) / ZELLE), cj = (int) Math.floor((N_NW - nn) / ZELLE), q = (int) Math.ceil(r / ZELLE);
        double best = Double.NaN;
        for (int jj = cj - q; jj <= cj + q; jj++)
            for (int ii = ci - q; ii <= ci + q; ii++) {
                if (ii < 0 || jj < 0 || ii >= BREITE || jj >= HOEHE || Math.hypot(ost(ii) - e, nord(jj) - nn) > r) continue;
                int id = knotenJeZelle[jj * BREITE + ii];
                if (id == 0) continue;
                float t = ankunft[a][id - 1];
                if (Float.isFinite(t) && !(t >= best)) best = t;
            }
        return best;
    }

    /** Knoten mit der frühesten Ankunft (heute linear) bis r Meter um (e, n); 0, wenn keiner. */
    public int knotenBei(double e, double nn, double r) {
        int ci = (int) Math.floor((e - E_NW) / ZELLE), cj = (int) Math.floor((N_NW - nn) / ZELLE), q = (int) Math.ceil(r / ZELLE), best = 0;
        double bt = Double.MAX_VALUE;
        for (int jj = cj - q; jj <= cj + q; jj++)
            for (int ii = ci - q; ii <= ci + q; ii++) {
                if (ii < 0 || jj < 0 || ii >= BREITE || jj >= HOEHE || Math.hypot(ost(ii) - e, nord(jj) - nn) > r) continue;
                int id = knotenJeZelle[jj * BREITE + ii];
                if (id != 0 && ankunft[0][id - 1] < bt) { bt = ankunft[0][id - 1]; best = id; }
            }
        return best;
    }

    /** Strahl von Knoten id zurück zur Einschlaglinie (Baum heute linear): Ost, Nord, Zeit je Punkt, von der Quelle her. */
    public double[] strahl(int id) {
        List<double[]> p = new ArrayList<>();
        for (int k = id; k > 0 && k <= n; k = vorgaenger[k - 1]) p.add(new double[]{ost(i[k - 1]), nord(j[k - 1]), ankunft[0][k - 1]});
        double[] out = new double[3 * p.size()];
        for (int s = 0; s < p.size(); s++) System.arraycopy(p.get(p.size() - 1 - s), 0, out, 3 * s, 3);
        return out;
    }

    /** Prüfsumme der Dateien knoten.tsv und kanten.tsv (wie LB_NETZ_STAND). */
    public static String pruefsumme(File dir) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String f : new String[]{"knoten.tsv", "kanten.tsv"}) {
            try (var in = Files.newInputStream(new File(dir, f).toPath())) {
                byte[] b = new byte[1 << 20];
                for (int r; (r = in.read(b)) > 0; ) md.update(b, 0, r);
            }
        }
        return java.util.HexFormat.of().formatHex(md.digest());
    }

    /** Tabulator-Datei ohne die Kopfzeile. */
    public static List<String[]> tsv(File f) throws IOException {
        List<String[]> l = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            String z = r.readLine();
            while ((z = r.readLine()) != null) if (!z.isBlank() && !z.startsWith("#")) l.add(z.split("\t", -1));
        }
        return l;
    }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "LB_NET_WELLE: %,d Knoten (+ QUELLE), %,d Kanten", n, kanten);
    }
}
