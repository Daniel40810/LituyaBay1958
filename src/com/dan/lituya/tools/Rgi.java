package com.dan.lituya.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Liest die Gletscherumrisse des Randolph Glacier Inventory 7.0 (Region 01 Alaska) aus dem Zip im Kartenmaterial
 * ({@code download/gletscher/rgi7/RGI2000-v7.0-G-01_alaska.zip}), ohne GDAL (Phase 16): das Shapefile (Polygon,
 * Typ 5, Länge und Breite WGS84) und die Attribute aus {@code *-attributes.csv} in derselben Folge. Nur Umrisse, die das
 * Rechteck (Grad) schneiden, werden behalten.
 */
public final class Rgi {
    private Rgi() { }

    /** Ein Gletscher: Kennung, Name (oder leer), Ringe als {lon0, lat0, lon1, lat1, …}. */
    public record Gletscher(String id, String name, double flaecheKm2, String termTyp, List<double[]> ringe) { }

    public static List<Gletscher> lesen(File zip, double west, double sued, double ost, double nord) throws IOException {
        try (ZipFile z = new ZipFile(zip)) {
            ZipEntry shp = null, csv = null;
            for (var en = z.entries(); en.hasMoreElements(); ) {
                ZipEntry e = en.nextElement();
                String n = e.getName().toLowerCase(java.util.Locale.ROOT);
                if (n.endsWith(".shp")) shp = e;
                if (n.endsWith("-attributes.csv")) csv = e;
            }
            if (shp == null) throw new IOException(zip.getName() + ": kein .shp");
            List<Map<String, String>> att = csv == null ? List.of() : csv(z.getInputStream(csv));
            byte[] b;
            try (InputStream in = z.getInputStream(shp)) { b = in.readAllBytes(); }
            ByteBuffer bb = ByteBuffer.wrap(b);
            List<Gletscher> l = new ArrayList<>();
            int pos = 100, k = 0;
            while (pos + 8 <= b.length) {
                bb.order(ByteOrder.BIG_ENDIAN);
                int len = bb.getInt(pos + 4) * 2;
                int c = pos + 8;
                pos = c + len;
                int nr = k++;
                bb.order(ByteOrder.LITTLE_ENDIAN);
                int typ = bb.getInt(c);
                if (typ != 5 && typ != 15 && typ != 25) continue;
                double x0 = bb.getDouble(c + 4), y0 = bb.getDouble(c + 12), x1 = bb.getDouble(c + 20), y1 = bb.getDouble(c + 28);
                if (x1 < west || x0 > ost || y1 < sued || y0 > nord) continue;
                int np = bb.getInt(c + 36), npt = bb.getInt(c + 40);
                int[] parts = new int[np];
                for (int i = 0; i < np; i++) parts[i] = bb.getInt(c + 44 + 4 * i);
                int o = c + 44 + 4 * np;
                List<double[]> ringe = new ArrayList<>();
                for (int p = 0; p < np; p++) {
                    int a = parts[p], e = p + 1 < np ? parts[p + 1] : npt;
                    double[] r = new double[2 * (e - a)];
                    for (int q = a; q < e; q++) { r[2 * (q - a)] = bb.getDouble(o + 16 * q); r[2 * (q - a) + 1] = bb.getDouble(o + 16 * q + 8); }
                    ringe.add(r);
                }
                Map<String, String> a = nr < att.size() ? att.get(nr) : Map.of();
                double fl = 0;
                try { fl = Double.parseDouble(a.getOrDefault("area_km2", "0")); } catch (NumberFormatException x) { /* leer */ }
                l.add(new Gletscher(a.getOrDefault("rgi_id", "RGI#" + nr), a.getOrDefault("glac_name", ""), fl, a.getOrDefault("term_type", ""), ringe));
            }
            return l;
        }
    }

    /** CSV mit Kopfzeile, Felder in Anführungszeichen erlaubt. */
    static List<Map<String, String>> csv(InputStream in) throws IOException {
        List<Map<String, String>> l = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String kopf = r.readLine();
            if (kopf == null) return l;
            List<String> k = felder(kopf.replace("﻿", ""));
            for (String z; (z = r.readLine()) != null; ) {
                List<String> f = felder(z);
                Map<String, String> m = new HashMap<>();
                for (int i = 0; i < k.size() && i < f.size(); i++) m.put(k.get(i), f.get(i));
                l.add(m);
            }
        }
        return l;
    }

    static List<String> felder(String z) {
        List<String> l = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < z.length(); i++) {
            char c = z.charAt(i);
            if (q) {
                if (c == '"' && i + 1 < z.length() && z.charAt(i + 1) == '"') { b.append('"'); i++; }
                else if (c == '"') q = false;
                else b.append(c);
            } else if (c == '"') q = true;
            else if (c == ',') { l.add(b.toString()); b.setLength(0); }
            else b.append(c);
        }
        l.add(b.toString());
        return l;
    }
}
