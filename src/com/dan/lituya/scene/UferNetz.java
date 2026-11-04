package com.dan.lituya.scene;

import com.dan.route.Measure;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Das Ufer als Messstrecke (Werkbuch 7.2, Phase 7a): Datei-Abbild {@code daten/netz/ufer/} von {@code LB_NET_UFER}
 * (LRS). Stränge mit Punkten (Ost, Nord, Maß m in Metern), Knoten und Kanten des LRS-Netzes (alle 500 m und an den
 * Enden), Ereignisse (Auflauf als Punkt mit Maß und Höhe). Geschrieben von {@code tools.Ufer} (Schritt {@code ufer}),
 * gelesen von App, Prüfung und {@code tools.NetzDb}. Die Rechnung entlang der Linie macht {@link Measure} aus
 * {@code com.dan.route} (Zweitrechnung zu {@code SDO_LRS}).
 */
public final class UferNetz {
    public static final String ORDNER = "netz/ufer";

    /** Ein Strang: Nummer, Name, ob Ring, Punkte (Ost, Nord abwechselnd), Messstrecke (x = Ost, z = Nord). */
    public record Strang(int id, String name, boolean ring, double[] en, Measure measure) {
        public double laenge() { return measure.length(); }
    }

    /** Ein Auflauf: Strang, Maß, Höhe (m), Jahr, Name, Quelle, Art (belegt, [G]), Lage (Ost, Nord). */
    public record Auflauf(int strang, double m, double hoehe, int jahr, String name, String quelle, String art, double e, double n) { }

    public final Map<Integer, Strang> straenge = new LinkedHashMap<>();
    public final List<Auflauf> auflauf = new ArrayList<>();
    public int knoten, kanten;

    public static UferNetz lesen(File daten) throws IOException {
        File d = new File(daten, ORDNER);
        UferNetz u = new UferNetz();
        Map<Integer, List<double[]>> pkt = new LinkedHashMap<>();
        for (String[] z : WellenNetz.tsv(new File(d, "punkte.tsv")))
            pkt.computeIfAbsent(Integer.parseInt(z[0]), k -> new ArrayList<>()).add(new double[]{Double.parseDouble(z[2]), Double.parseDouble(z[3])});
        for (String[] z : WellenNetz.tsv(new File(d, "straenge.tsv"))) {
            int id = Integer.parseInt(z[0]);
            List<double[]> p = pkt.get(id);
            double[] en = new double[2 * p.size()];
            float[] xz = new float[2 * p.size()];
            for (int k = 0; k < p.size(); k++) {
                en[2 * k] = p.get(k)[0]; en[2 * k + 1] = p.get(k)[1];
                // Measure rechnet in float: relativ zum Ursprung der Szene (E0, N0), sonst gehen Zentimeter verloren
                xz[2 * k] = (float) (p.get(k)[0] - Site.E0); xz[2 * k + 1] = (float) (p.get(k)[1] - Site.N0);
            }
            u.straenge.put(id, new Strang(id, z[1], z[2].equals("J"), en, new Measure(xz)));
        }
        for (String[] z : WellenNetz.tsv(new File(d, "auflauf.tsv")))
            u.auflauf.add(new Auflauf(Integer.parseInt(z[0]), Double.parseDouble(z[1]), Double.parseDouble(z[2]), Integer.parseInt(z[3]), z[4], z[5], z[6],
                    Double.parseDouble(z[7]), Double.parseDouble(z[8])));
        u.knoten = WellenNetz.tsv(new File(d, "knoten.tsv")).size();
        u.kanten = WellenNetz.tsv(new File(d, "kanten.tsv")).size();
        return u;
    }

    /** Punkt beim Maß m eines Strangs: Ost, Nord. */
    public double[] punkt(int strang, double m) {
        double[] p = straenge.get(strang).measure().locate(m);
        return new double[]{p[0] + Site.E0, p[1] + Site.N0};
    }

    /** Projektion von (Ost, Nord) auf einen Strang: Maß, Abstand (links +). */
    public double[] projektion(int strang, double e, double n) {
        Measure.Projection p = straenge.get(strang).measure().project(e - Site.E0, n - Site.N0);
        return new double[]{p.m(), p.offset()};
    }

    @Override public String toString() {
        StringBuilder b = new StringBuilder("LB_NET_UFER:");
        for (Strang s : straenge.values()) b.append(String.format(Locale.GERMAN, " %s %.1f km%s;", s.name(), s.laenge() / 1000, s.ring() ? " (Ring)" : ""));
        return b.append(String.format(Locale.GERMAN, " %d Knoten, %d Kanten, %d Aufläufe", knoten, kanten, auflauf.size())).toString();
    }

    static String text(File f) throws IOException { return Files.readString(f.toPath(), StandardCharsets.UTF_8); }
}
