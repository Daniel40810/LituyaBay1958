package com.dan.lituya.tools;

import com.dan.surface.Bake;
import com.dan.surface.Katalog;
import com.dan.surface.Material;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Schritt {@code texturen} der Aufbereitung (Werkbuch 8, Phase 5): backt die CC0-Sätze aus
 * {@code download/texturen/tex_<id>/} (geladen mit {@code kartenmaterial.py laden --gruppe texturen}) mit
 * {@code com.dan.surface.Bake} nach {@code daten/texturen/<id>/} (a.png, n.png, r.png, material.txt), in der Liste
 * {@link Katalog#fjord()}. Abgeleitete Materialien (Seetang aus Moos) danach. Fertige Ordner bleiben stehen, wenn sie
 * neuer als das Zip sind und die Kantenlänge stimmt ({@code -Dneu=true} backt alles neu). Schreibt
 * {@code daten/texturen/LIZENZEN.txt} (Paketvertrag 15).
 */
public final class Texturen {
    private Texturen() { }

    /** Kantenlänge der gebackenen Karten ({@code -Dlituya.textur.kante}, Vorgabe 2048 = wie die Quelle). */
    public static int kante() { return Integer.getInteger("lituya.textur.kante", 2048); }

    public static void rechnen(Rohdaten r, boolean neu) throws Exception {
        File aus = new File(r.daten, "texturen");
        aus.mkdirs();
        int kante = kante();
        List<Material> kat = Katalog.fjord();
        List<String> lizenz = new ArrayList<>();
        lizenz.add("# Texturen in daten/texturen (Phase 5, " + java.time.LocalDate.now() + "): Material, Quelle, Lizenz, Adresse");
        int gebacken = 0, stehen = 0, fehlen = 0;
        for (Material m : kat) {
            if (m.derivedFrom != null) continue;
            File dir = new File(r.roh, "texturen/tex_" + m.id);
            File zip = new File(dir, m.assetId + "_2K-JPG.zip");
            File ziel = new File(aus, m.id), txt = new File(ziel, "material.txt");
            if (!zip.isFile()) {
                File[] z = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".zip"));
                if (z != null && z.length > 0) zip = z[0];
            }
            if (!zip.isFile()) {
                r.log("  texturen %s: kein Satz in %s (python tools/kartenmaterial/kartenmaterial.py laden --gruppe texturen)", m.id, dir.getPath());
                fehlen++;
                continue;
            }
            if (!neu && txt.isFile() && txt.lastModified() > zip.lastModified() && Files.readString(txt.toPath(), StandardCharsets.UTF_8).contains("kante=" + kante + "\n")) {
                stehen++;
            } else {
                long t0 = System.nanoTime();
                Bake.Result b = Bake.bake(m, zip, ziel, kante);
                gebacken++;
                r.log("  texturen %s: %s, %d², Farbe %.3f %.3f %.3f, Rauheit %.2f, Höhe %.2f ± %.2f (%.1f s)", m.id, b.sources(), b.size(),
                        b.meanColor()[0], b.meanColor()[1], b.meanColor()[2], b.meanRough(), b.meanHeight(), b.sdHeight(), (System.nanoTime() - t0) / 1e9);
            }
            lizenz.add(String.format("%s\t%s %s\t%s\t%s", m.id, m.provider, m.assetId, m.license, m.url));
        }
        for (Material m : kat) {
            if (m.derivedFrom == null) continue;
            File von = new File(aus, m.derivedFrom);
            if (!new File(von, "a.png").isFile()) { r.log("  texturen %s: Ausgang %s fehlt", m.id, m.derivedFrom); fehlen++; continue; }
            Bake.derive(m, von, new File(aus, m.id));
            gebacken++;
            lizenz.add(String.format("%s\tabgeleitet aus %s (Tönung [G])\twie %s\t", m.id, m.derivedFrom, m.derivedFrom));
            r.log("  texturen %s: abgeleitet aus %s", m.id, m.derivedFrom);
        }
        Files.write(new File(aus, "LIZENZEN.txt").toPath(), lizenz, StandardCharsets.UTF_8);
        r.log("  texturen: %d gebacken, %d standen schon, %d fehlen; LIZENZEN.txt geschrieben", gebacken, stehen, fehlen);
    }
}
