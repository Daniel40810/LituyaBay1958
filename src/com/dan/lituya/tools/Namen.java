package com.dan.lituya.tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schritt {@code namen} der Aufbereitung (Werkbuch 3.4): die amtlichen Namen aus GNIS ({@code geodaten/namen_szene.geojson},
 * vom Werkzeug {@code tools/kartenmaterial} aus dem Kartenmaterial geschrieben) nach {@code daten/namen.tsv} mit Ostwert
 * und Nordwert in UTM 8 N. Namen der Tlingit kommen dazu, wenn sie belegt sind (Phase 0b).
 */
public final class Namen {
    private Namen() { }

    public static int rechnen(Rohdaten r, File geojson) throws Exception {
        String s = Files.readString(geojson.toPath(), StandardCharsets.UTF_8);
        Pattern feat = Pattern.compile("\"properties\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL);
        List<String> z = new ArrayList<>();
        z.add("name\tart\tost\tnord\tkarte\tgnis");
        Matcher m = feat.matcher(s);
        while (m.find()) {
            String p = m.group(1);
            String name = str(p, "feature_name"), art = str(p, "feature_class"), karte = str(p, "map_name"), id = str(p, "feature_id");
            String e = num(p, "utm8n_ost"), n = num(p, "utm8n_nord");
            if (name == null || e == null || n == null) continue;
            z.add(String.join("\t", name, art == null ? "" : art, e, n, karte == null ? "" : karte, id == null ? "" : id));
        }
        Files.write(new File(r.daten, "namen.tsv").toPath(), z, StandardCharsets.UTF_8);
        r.log("  namen: %d Namen aus %s", z.size() - 1, geojson.getName());
        return z.size() - 1;
    }

    static String str(String p, String k) {
        Matcher m = Pattern.compile("\"" + k + "\"\\s*:\\s*\"([^\"]*)\"").matcher(p);
        return m.find() ? m.group(1) : null;
    }

    static String num(String p, String k) {
        Matcher m = Pattern.compile("\"" + k + "\"\\s*:\\s*(-?[0-9.]+)").matcher(p);
        return m.find() ? m.group(1) : null;
    }
}
