package com.dan.lituya.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Die mitgelieferten Tabellen in {@code com/dan/lituya/data} (Phase 19): Quellen, Befunde, Zeugen, Boote. Aus ihnen lädt
 * {@code tools.SzeneDb} Oracle; ohne Datenbank liest das Archiv sie direkt ({@link DateiSpeicher#befunde}).
 */
public final class Mitgeliefert {
    private Mitgeliefert() { }

    /** Zeilen ohne #, Felder mit Tab (leere Felder bleiben). */
    public static List<String[]> tsv(String name) throws IOException {
        try (InputStream in = Mitgeliefert.class.getResourceAsStream(name)) {
            if (in == null) throw new IOException("data/" + name + " fehlt im Build");
            List<String[]> l = new ArrayList<>();
            for (String z : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) if (!z.isBlank() && !z.startsWith("#")) l.add(z.split("\t", -1));
            return l;
        }
    }

    /** Ort aus Name (Site) oder „E N“; null ohne. */
    public static double[] ort(String s) {
        if (s == null || s.isBlank()) return null;
        String[] p = s.trim().split("\\s+");
        if (p.length == 2) try { return new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1])}; } catch (NumberFormatException e) { /* Name */ }
        com.dan.lituya.scene.Site.Ort o = com.dan.lituya.scene.Site.byName(s.trim());
        return o == null ? null : new double[]{o.e(), o.n()};
    }

    public static double zahl(String s) {
        if (s == null || s.isBlank()) return Double.NaN;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return Double.NaN; }
    }

    /** Die mitgelieferten Befunde als Datensätze (Quellen als Text). */
    public static List<Speicher.Befund> befunde() throws IOException {
        List<Speicher.Befund> l = new ArrayList<>();
        for (String[] z : tsv("befunde.tsv")) {
            double[] o = ort(z[7]);
            l.add(new Speicher.Befund(z[0], z[1], z[2], zahl(z[3]), z[4], z[5], z[6], o == null ? null : z[7], o == null ? Double.NaN : o[0], o == null ? Double.NaN : o[1],
                    zahl(z[8]), z[9].isBlank() ? "beide" : z[9], z[10].replace(":", " S. ").replace(",", ", "), "mitgeliefert"));
        }
        return l;
    }

    public static List<Speicher.Zeuge> zeugen() throws IOException {
        List<Speicher.Zeuge> l = new ArrayList<>();
        for (String[] z : tsv("zeugen.tsv")) {
            double[] o = ort(z[5]);
            l.add(new Speicher.Zeuge(z[0], z[1], z[2], zahl(z[3]), zahl(z[4]), z[5], o == null ? Double.NaN : o[0], o == null ? Double.NaN : o[1], z[6], z[7], z[8]));
        }
        return l;
    }
}
