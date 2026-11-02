package com.dan.lituya.tools;

import com.dan.lituya.scene.Geo;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Stufe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Schritt {@code grund} der Aufbereitung (Werkbuch 3.4, F5 A): der Grund der Bucht heute und zu drei Zeiten der
 * Vermessung, über den Kern.
 * <ul>
 * <li>{@code grund/heute_4m.r32} und {@code grund/wasser_4m.u8}: der Grund aus USACE 2019 und BlueTopo, gemischt mit dem
 * Lidar nach der Regel von {@link Gelaende#mischen}; die Maske ist 1, wo Wasser ist (NaN im Grund, wo Land).</li>
 * <li>{@code grund/1926_20m.r32}, {@code 1940_20m.r32}, {@code 1959_20m.r32}: aus den Lotungen der NOS-Vermessungen
 * (H04608/H04648 1926, H06581–83 1940, H08492 Juli/August 1959; GEODAS xyz, Tiefe in m unter MLLW) als lineare
 * Delaunay-Fläche, nur wo die Lotungen dicht genug liegen (Kanten bis 600 m [G]) und heute Wasser ist.</li>
 * </ul>
 * Höhenbezug: Die Lotungen beziehen sich auf MLLW. Die Bezugshöhen der Station Elfin Cove
 * ({@code klima/gezeiten/bezugshoehen_elfin_cove.json}) geben MLLW 1,747 m unter MSL, nennen aber keinen Wert für NAVD88.
 * Befund des Volllaufs (Phase 2): In den tiefen Teilen der Bucht ist BlueTopo genau die Vermessung von 1940 – 25 602 Zellen
 * weichen alle um −0,59 m ab (5–95 % −0,6 bis −0,6 m). BlueTopo hat sie mit VDatum (GEOID17B) nach NAVD88 gebracht; damit
 * liegt MLLW in der Bucht {@link #MLLW_UNTER_NAVD88} = 1,747 − 0,590 = 1,157 m unter NAVD88 (gemessen). Dieser Wert gilt
 * jetzt für alle Vermessungen; Elfin Cove steht nur noch im Protokoll.
 */
public final class Grund {
    private Grund() { }

    public static final double ZELLE_HEUTE = 4, ZELLE_LOT = 20;
    /** Längste Kante der Delaunay-Dreiecke, die noch gefüllt wird (m) [G]. */
    public static final double MAX_KANTE = 600;
    /** Die Vermessungen je Jahr. */
    /** MLLW unter NAVD88 in der Bucht (m), gemessen an BlueTopo über den Lotungen von 1940 (VDatum, GEOID17B). */
    public static final double MLLW_UNTER_NAVD88 = 1.157;
    public static final String[][] JAHRE = {{"1926", "H04608", "H04648"}, {"1940", "H06581", "H06582", "H06583"}, {"1959", "H08492"}};

    public static void rechnen(Rohdaten r) throws Exception {
        File dir = new File(r.daten, "grund");
        dir.mkdirs();
        StringBuilder txt = new StringBuilder("# Grund der Bucht (Phase 2, " + java.time.LocalDate.now() + ")\n");
        // heute
        Gitter g = Gitter.ueber(Site.KERN, ZELLE_HEUTE);
        List<List<Quelle>> q = Gelaende.quellen(Stufe.KERN, r);
        float[] land = g.leer(), grund = g.leer();
        for (Quelle x : q.get(0)) x.proben(g, land);
        for (Quelle x : q.get(1)) x.proben(g, grund);
        for (List<Quelle> l : q) for (Quelle x : l) x.schliessen();
        byte[] wasser = new byte[g.w() * g.h()];
        float[] heute = g.leer();
        int nw = 0;
        for (int i = 0; i < heute.length; i++)
            // Wasser, wo der Grund gilt und unter 3 m liegt (BlueTopo trägt auch Landwerte, etwa vor dem Lituya-Gletscher)
            if (Gelaende.mischen(land[i], grund[i]) == 1 && grund[i] < Gelaende.FLACH) { heute[i] = grund[i]; wasser[i] = 1; nw++; }
        Stufe.schreiben(new File(dir, "heute_4m.r32"), heute);
        Files.write(new File(dir, "wasser_4m.u8").toPath(), wasser);
        txt.append(gitterText("heute_4m", g)).append(String.format(Locale.ROOT, "heute_4m.wasser_km2=%.1f%n", nw * ZELLE_HEUTE * ZELLE_HEUTE / 1e6));
        r.log("  grund heute: %d×%d zu %.0f m, Wasser %.1f km² (Bucht und Meer im Kern)", g.w(), g.h(), ZELLE_HEUTE, nw * 16 / 1e6);
        // MLLW → NAVD88
        double msl = mllwUnterMsl(r), mllw = MLLW_UNTER_NAVD88;
        txt.append(String.format(Locale.ROOT, "mllw_unter_navd88=%.3f  # gemessen: BlueTopo (VDatum) ueber den Lotungen 1940; Elfin Cove MSL - MLLW = %.3f%n", mllw, msl));
        r.log("  grund: MLLW %.3f m unter NAVD88 (gemessen an BlueTopo/VDatum über 1940); Elfin Cove: MLLW %.3f m unter MSL", mllw, msl);
        Gitter gl = Gitter.ueber(Site.KERN, ZELLE_LOT);
        for (String[] jahr : JAHRE) {
            List<double[]> p = new ArrayList<>();
            for (int k = 1; k < jahr.length; k++) p.addAll(lotungen(r, jahr[k]));
            if (p.size() < 3) { r.log("  grund %s: keine Lotungen (%s)", jahr[0], String.join(", ", java.util.Arrays.copyOfRange(jahr, 1, jahr.length))); continue; }
            double[] xy = new double[p.size() * 2], z = new double[p.size()];
            for (int i = 0; i < p.size(); i++) {
                xy[2 * i] = p.get(i)[0];
                xy[2 * i + 1] = p.get(i)[1];
                z[i] = -p.get(i)[2] - mllw;
            }
            float[] v = gl.leer();
            Delaunay.aufGitter(xy, z, gl, MAX_KANTE, v);
            // nur wo heute Wasser ist (die Ufer haben sich wenig verschoben; Gletscherfronten zählt rueckbau1958)
            int n = 0;
            for (int j = 0; j < gl.h(); j++)
                for (int i = 0; i < gl.w(); i++) {
                    int k = j * gl.w() + i;
                    if (Float.isNaN(v[k])) continue;
                    if (!wasserBei(wasser, g, gl.ce(i), gl.cn(j))) v[k] = Float.NaN;
                    else n++;
                }
            Stufe.schreiben(new File(dir, jahr[0] + "_20m.r32"), v);
            int imKern = 0;
            for (double[] x : p) if (Site.KERN.contains(x[0] - Site.E0, -(x[1] - Site.N0))) imKern++;
            String vergleich = vergleich(v, gl, heute, g);
            txt.append(gitterText(jahr[0] + "_20m", gl)).append(String.format(Locale.ROOT, "%s_20m.lotungen=%d  # davon im Kern %d%n%s_20m.flaeche_km2=%.1f%n%s_20m.gegen_heute=%s%n",
                    jahr[0], p.size(), imKern, jahr[0], n * ZELLE_LOT * ZELLE_LOT / 1e6, jahr[0], vergleich));
            r.log("  grund %s: %d Lotungen (%d im Kern), %.1f km² gefüllt; gegen heute (BlueTopo, tiefer als 60 m): %s", jahr[0], p.size(), imKern,
                    n * ZELLE_LOT * ZELLE_LOT / 1e6, vergleich);
        }
        Files.writeString(new File(dir, "GRUND.txt").toPath(), txt.toString(), StandardCharsets.UTF_8);
    }

    static String gitterText(String name, Gitter g) {
        return String.format(Locale.ROOT, "%s.zelle=%s%n%s.breite=%d%n%s.hoehe=%d%n%s.nordwest_ost=%.0f%n%s.nordwest_nord=%.0f%n",
                name, Gelaende.fmt(g.zelle()), name, g.w(), name, g.h(), name, g.e0(), name, g.n0());
    }

    static boolean wasserBei(byte[] wasser, Gitter g, double e, double n) {
        int i = (int) Math.floor((e - g.e0()) / g.zelle()), j = (int) Math.floor((g.n0() - n) / g.zelle());
        if (i < 0 || j < 0 || i >= g.w() || j >= g.h()) return false;
        return wasser[j * g.w() + i] == 1;
    }

    /** Median und 90-%-Spanne von (Jahr − heute) in Zellen, wo heute tiefer als 60 m ist: der Restversatz des Höhenbezugs. */
    static String vergleich(float[] v, Gitter gl, float[] heute, Gitter g) {
        List<Double> d = new ArrayList<>();
        for (int j = 0; j < gl.h(); j++)
            for (int i = 0; i < gl.w(); i++) {
                float a = v[j * gl.w() + i];
                if (Float.isNaN(a)) continue;
                int hi = (int) Math.floor((gl.ce(i) - g.e0()) / g.zelle()), hj = (int) Math.floor((g.n0() - gl.cn(j)) / g.zelle());
                float b = heute[hj * g.w() + hi];
                if (Float.isNaN(b) || b > -60) continue;
                d.add((double) (a - b));
            }
        if (d.size() < 20) return "zu wenige tiefe Zellen";
        java.util.Collections.sort(d);
        return String.format(Locale.ROOT, "Median %+.2f m, 5..95 %% %+.1f..%+.1f m (%d Zellen)", d.get(d.size() / 2), d.get(d.size() / 20), d.get(d.size() * 19 / 20), d.size());
    }

    /** MSL − MLLW an der Station Elfin Cove (m); 1,747 m, wenn die Datei fehlt. */
    static double mllwUnterMsl(Rohdaten r) throws IOException {
        File f = new File(r.roh, "klima/gezeiten/bezugshoehen_elfin_cove.json");
        if (!f.isFile()) return 1.747;
        String s = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        double msl = wert(s, "MSL"), mllw = wert(s, "MLLW");
        return Double.isNaN(msl) || Double.isNaN(mllw) ? 1.747 : msl - mllw;
    }

    static double wert(String json, String name) {
        int i = json.indexOf("\"name\": \"" + name + "\"");
        if (i < 0) i = json.indexOf("\"name\":\"" + name + "\"");
        if (i < 0) return Double.NaN;
        int v = json.indexOf("\"value\"", i);
        if (v < 0) return Double.NaN;
        int a = json.indexOf(':', v) + 1, b = a;
        while (b < json.length() && "+-.0123456789eE ".indexOf(json.charAt(b)) >= 0) b++;
        return Double.parseDouble(json.substring(a, b).trim());
    }

    /** Die Lotungen einer Vermessung: {Ost, Nord, Tiefe unter MLLW} in UTM 8 N, nur aktive. */
    static List<double[]> lotungen(Rohdaten r, String survey) throws IOException {
        List<double[]> l = new ArrayList<>();
        File f = new File(r.roh, "bathymetrie/nos_vermessungen/" + survey + "/GEODAS/" + survey + ".xyz.gz");
        if (!f.isFile()) return l;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(f)), StandardCharsets.UTF_8))) {
            String head = in.readLine();
            if (head == null) return l;
            String[] h = head.split(",");
            int ilat = idx(h, "lat"), ilon = idx(h, "long"), idep = idx(h, "depth"), iact = idx(h, "active");
            for (String z; (z = in.readLine()) != null; ) {
                String[] p = z.split(",");
                if (p.length <= Math.max(ilat, Math.max(ilon, idep))) continue;
                if (iact >= 0 && iact < p.length && !p[iact].trim().equals("1")) continue;
                try {
                    double lat = Double.parseDouble(p[ilat]), lon = Double.parseDouble(p[ilon]), d = Double.parseDouble(p[idep]);
                    double[] en = Geo.UTM8N.forward(lat, lon);
                    l.add(new double[]{en[0], en[1], d});
                } catch (NumberFormatException e) { /* Zeile überspringen */ }
            }
        }
        return l;
    }

    static int idx(String[] h, String n) {
        for (int i = 0; i < h.length; i++) if (h[i].trim().equalsIgnoreCase(n)) return i;
        return -1;
    }
}
