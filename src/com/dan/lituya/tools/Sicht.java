package com.dan.lituya.tools;

import com.dan.clipmap.TileStack;
import com.dan.lituya.camera.Ansichten;
import com.dan.lituya.camera.Schaustuecke;
import com.dan.lituya.scene.SeeNetz;
import com.dan.lituya.scene.SichtNetz;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.UferNetz;
import com.dan.lituya.scene.WellenNetz;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schritt {@code sicht} der Aufbereitung (Phase 17, Werkbuch 7.6): {@code LB_NET_SICHT} nach {@code daten/netz/sicht}
 * ({@link SichtNetz}).
 * <ul>
 * <li><b>Knoten</b> (Augenhöhe über Grund oder Wasser [G]): die Ansichten (wie in der App), die Schaustücke, die benannten
 * Orte (Gipfel und Gletscher 10 m, sonst 5 m), die Ankerplätze 1958 (3 m), die Aufläufe (2 m), Punkte am Ufer alle 1,5 km
 * (5 m), Gipfel aus dem Gelände (höchster Punkt im Umkreis von 1,5 km, ab 400 m, höchstens 60, 10 m) und Kamerapunkte über
 * dem Wasser der Bucht und vor dem Eingang alle 1 km (150 m). Punkte näher als 60 m und 30 m in der Höhe an einem früheren fallen weg.</li>
 * <li><b>Kanten</b>: freie Sicht als Strahl im Gelände (Kachelstapel 5 m, alle 10 m, die ersten und letzten 50 m frei),
 * Erdkrümmung mit Refraktion k = 0,13 [G], bis 30 km, zwischen zwei Gipfeln bis 60 km; ungerichtet;
 * {@code COST} = Flugzeit der Kamera ({@link SichtNetz#flugzeit}).</li>
 * <li><b>Netz</b>: nur die größte Komponente; die übrigen Punkte stehen in {@code SICHT.txt}.</li>
 * <li><b>Rundflug</b>: geschlossene Rundreise über die Motive (Ansichten außer Draufsicht und Fern, Schaustücke, Orte,
 * Ankerplätze, Aufläufe) ab der Anchorage Cove ({@link SichtNetz#rundreise}); <b>Kamerafahrt</b>: der Spannbaum (Kruskal),
 * ohne die Äste, an denen kein Motiv hängt, in Tiefensuche ab demselben Punkt, jede Kante hin und zurück.</li>
 * </ul>
 * <p>Aufruf: {@code Sicht [daten]}.
 */
public final class Sicht {
    private Sicht() { }

    public static final double DMAX = 30_000, DMAX_GIPFEL = 60_000, SCHRITT = 10, RAND = 50, DOPPELT_M = 60, DOPPELT_H = 30;
    public static final double GIPFEL_UMKREIS = 1_500, GIPFEL_AB = 400, KAMERA_RASTER = 1_000, KAMERA_HOCH = 150, UFER_ALLE = 1_500;
    public static final int GIPFEL_MAX = 60, NACHBARN = 5;

    public static void rechnen(Rohdaten r) throws Exception { rechnen(r.daten, r::log); }

    /** Höhe des Geländes in UTM (m) oder NaN. */
    public interface Hoehe { double at(double e, double n); }

    /** Die Punkte, bevor die Sicht gerechnet ist. */
    static final class Punkte {
        final List<String> art = new ArrayList<>(), name = new ArrayList<>();
        final List<double[]> p = new ArrayList<>();   // {E, N, Auge, Grund, Blick, Neigung, Motiv}

        boolean add(String a, String nm, double e, double n, double auge, double grund, double blick, double neigung, boolean motiv) {
            if (Double.isNaN(auge)) return false;
            for (int i = 0; i < p.size(); i++) {
                double[] q = p.get(i);
                if (Math.hypot(q[0] - e, q[1] - n) < DOPPELT_M && Math.abs(q[2] - auge) < DOPPELT_H) {
                    if (motiv) q[6] = 1;
                    return false;
                }
            }
            art.add(a);
            name.add(nm);
            p.add(new double[]{e, n, auge, grund, blick, neigung, motiv ? 1 : 0});
            return true;
        }
    }

    public static SichtNetz rechnen(File daten, Welle.Log log) throws Exception {
        long t0 = System.nanoTime();
        TileStack st;
        try {
            st = TileStack.folders(new File(daten, "gelaende"), Site.E0, Site.N0);
        } catch (IOException e) {
            throw new IOException("daten/gelaende fehlt: " + e.getMessage() + " (ant aufbereitung -Dschritte=gelaende)");
        }
        Hoehe h = (e, n) -> {
            float v = st.height(e - Site.E0, -(n - Site.N0), 5.0);
            return Float.isNaN(v) || v < -199 ? Double.NaN : v;
        };
        Punkte pk = new Punkte();
        int[] zahl = new int[SichtNetz.ARTEN.length];
        // Ansichten und Schaustücke (Augenhöhe über Grund oder Wasser wie in der App)
        for (Ansichten.Ansicht a : Ansichten.ALLE) {
            double e = a.x() + Site.E0, n = Site.N0 - a.z(), g = boden(h, e, n);
            if (pk.add(SichtNetz.ANSICHT, a.name(), e, n, (Double.isNaN(g) ? 0 : Math.max(g, 0)) + a.eyeAboveGround(), g, a.heading(), a.pitch(), a.key() != 7 && a.key() != 9)) zahl[0]++;
        }
        for (Schaustuecke.Stueck s : Schaustuecke.ALLE) {
            double e = s.x() + Site.E0, n = Site.N0 - s.z(), g = boden(h, e, n);
            if (pk.add(SichtNetz.SCHAUSTUECK, s.name(), e, n, Math.max(g, 0) + Math.max(2, s.auge()), g, s.heading(), s.pitch(), !s.unter())) zahl[1]++;
        }
        for (Site.Ort o : Site.ORTE) {
            double g = boden(h, o.e(), o.n());
            double auf = o.art() == Site.Art.GIPFEL || o.art() == Site.Art.GLETSCHER ? 10 : 5;
            if (pk.add(SichtNetz.ORT, o.name(), o.e(), o.n(), Math.max(g, 0) + auf, g, Double.NaN, Double.NaN, true)) zahl[2]++;
        }
        for (Object[] p : SeeNetz.PLAETZE) {
            double e = (double) p[1], n = (double) p[2];
            if (pk.add(SichtNetz.ANKER, (String) p[0] + " vor Anker", e, n, 3, boden(h, e, n), Double.NaN, Double.NaN, true)) zahl[3]++;
        }
        UferNetz u = new File(daten, UferNetz.ORDNER + "/punkte.tsv").isFile() ? UferNetz.lesen(daten) : null;
        if (u != null) {
            for (UferNetz.Auflauf a : u.auflauf) {
                double g = boden(h, a.e(), a.n());
                if (pk.add(SichtNetz.AUFLAUF, a.name() + " (" + Math.round(a.hoehe()) + " m)", a.e(), a.n(), Math.max(g, 0) + 2, g, Double.NaN, Double.NaN, true)) zahl[4]++;
            }
            for (UferNetz.Strang s : u.straenge.values())
                for (double m = UFER_ALLE / 2; m < s.laenge(); m += UFER_ALLE) {
                    double[] en = u.punkt(s.id(), m);
                    double g = boden(h, en[0], en[1]);
                    if (pk.add(SichtNetz.UFER, String.format(Locale.GERMAN, "%s km %.0f", s.name(), m / 1000), en[0], en[1], Math.max(g, 0) + 5, g, Double.NaN, Double.NaN, false)) zahl[5]++;
                }
        } else log.log("  HINWEIS: daten/netz/ufer fehlt (ant aufbereitung -Dschritte=ufer): keine Aufläufe und Ufer");
        // Gipfel aus dem Gelände der Szene (50 m)
        Klassen.Raster sz = Klassen.SZENE;
        float[] zs = Klassen.gelaende(daten, sz);
        if (zs == null) throw new IOException("daten/klassen fehlt (ant aufbereitung -Dschritte=klassen)");
        int rr = (int) Math.round(GIPFEL_UMKREIS / sz.zelle());
        float[] mx = maxFilter(zs, sz.w(), sz.h(), rr);
        List<int[]> gp = new ArrayList<>();
        for (int j = rr; j < sz.h() - rr; j++)
            for (int i = rr; i < sz.w() - rr; i++) {
                int c = j * sz.w() + i;
                if (!Float.isNaN(zs[c]) && zs[c] >= GIPFEL_AB && zs[c] >= mx[c]) gp.add(new int[]{c});
            }
        gp.sort((a, b) -> Float.compare(zs[b[0]], zs[a[0]]));
        List<double[]> gip = new ArrayList<>();
        for (int[] g : gp) {
            if (gip.size() >= GIPFEL_MAX) break;
            int i = g[0] % sz.w(), j = g[0] / sz.w();
            double e = sz.ost(i), n = sz.nord(j);
            boolean nah = false;
            for (double[] q : gip) nah |= Math.hypot(q[0] - e, q[1] - n) < GIPFEL_UMKREIS * 1.25;
            for (Site.Ort o : Site.ORTE) nah |= o.art() == Site.Art.GIPFEL && Math.hypot(o.e() - e, o.n() - n) < GIPFEL_UMKREIS * 1.25;
            if (nah) continue;
            gip.add(new double[]{e, n});
            double gr = boden(h, e, n);
            if (Double.isNaN(gr)) gr = zs[g[0]];
            if (pk.add(SichtNetz.GIPFEL, String.format(Locale.GERMAN, "Gipfel %.0f m", gr), e, n, gr + 10, gr, Double.NaN, Double.NaN, false)) zahl[6]++;
        }
        // Kamerapunkte über dem Wasser der Bucht
        Klassen.Raster kr = Klassen.KERN;
        byte[] kk = Klassen.lesen(daten, kr);
        if (kk != null)
            for (double n = 6_498_400 + KAMERA_RASTER / 2; n < 6_507_600; n += KAMERA_RASTER)
                for (double e = 342_800 + KAMERA_RASTER / 2; e < 358_000; e += KAMERA_RASTER) {
                    int i = (int) ((e - kr.e0()) / kr.zelle()), j = (int) ((kr.n0() - n) / kr.zelle());
                    if (i < 0 || j < 0 || i >= kr.w() || j >= kr.h()) continue;
                    int c = kk[j * kr.w() + i];
                    if (c != 0 && c != 1) continue;
                    if (pk.add(SichtNetz.KAMERA, String.format(Locale.ROOT, "Kamera E%.0f N%.0f", e / 100, n / 100), e, n, KAMERA_HOCH, boden(h, e, n), Double.NaN, Double.NaN, false)) zahl[7]++;
                }
        int n0 = pk.p.size();
        StringBuilder ze = new StringBuilder();
        for (int a = 0; a < zahl.length; a++) ze.append(a > 0 ? ", " : "").append(SichtNetz.ARTEN[a]).append(' ').append(zahl[a]);
        log.log("  Punkte: %d (%s)", n0, ze);

        // Sichtlinien (parallel je Anfangspunkt)
        long t1 = System.nanoTime();
        List<double[]> kanten = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger proben = new AtomicInteger(), geprueft = new AtomicInteger();
        java.util.stream.IntStream.range(0, n0).parallel().forEach(a -> {
            double[] pa = pk.p.get(a);
            for (int b = a + 1; b < n0; b++) {
                double[] pb = pk.p.get(b);
                double L = Math.hypot(pb[0] - pa[0], pb[1] - pa[1]);
                boolean gg = pk.art.get(a).equals(SichtNetz.GIPFEL) && pk.art.get(b).equals(SichtNetz.GIPFEL);
                if (L > (gg ? DMAX_GIPFEL : DMAX) || L < 1) continue;
                geprueft.incrementAndGet();
                double fr = frei(h, pa, pb, L, proben);
                if (fr < 0) continue;
                double hm = 0.5 * (pa[2] + pb[2]);
                kanten.add(new double[]{a, b, L, SichtNetz.flugzeit(L, hm, pb[2] - pa[2]), fr});
            }
        });
        kanten.sort((x, y) -> x[0] != y[0] ? Double.compare(x[0], y[0]) : Double.compare(x[1], y[1]));
        log.log("  Sichtlinien: %,d von %,d Paaren frei (%,d Proben im Gelände) in %.1f s", kanten.size(), geprueft.get(), proben.get(), (System.nanoTime() - t1) / 1e9);

        // größte Komponente
        SichtNetz roh = netz(pk, kanten, null);
        int[] kz = new int[1];
        int[] komp = roh.komponenten(kz);
        int[] gr = new int[kz[0]];
        for (int c : komp) gr[c]++;
        int best = 0;
        for (int c = 1; c < gr.length; c++) if (gr[c] > gr[best]) best = c;
        boolean[] behalten = new boolean[n0];
        List<String> ohne = new ArrayList<>();
        for (int k = 0; k < n0; k++) { behalten[k] = komp[k] == best; if (!behalten[k]) ohne.add(roh.name[k] == null ? roh.art[k] : roh.name[k]); }
        SichtNetz w = netz(pk, kanten, behalten);
        log.log("  Netz: %s (%d Komponenten; ohne Sicht zur größten: %s)", w, kz[0], ohne.isEmpty() ? "keine" : String.join(", ", ohne));

        // Rundflug über die Motive ab der Anchorage Cove
        File dir = new File(daten, SichtNetz.ORDNER);
        dir.mkdirs();
        w.schreiben(daten);
        int start = w.knoten("Anchorage Cove");
        if (start < 0) start = 0;
        List<Integer> mot = new ArrayList<>();
        mot.add(start);
        for (int k = 0; k < w.n; k++) if (w.motiv[k] && k != start) mot.add(k);
        int[] ziele = mot.stream().mapToInt(Integer::intValue).toArray();
        double[] tk = new double[1];
        int[] folge = w.rundreise(ziele, tk);
        List<String> rf = new ArrayList<>();
        rf.add("SEQ\tNODE_ID\tZEIT_S\tMOTIV");
        double tt = 0, lr = 0;
        int seq = 0;
        for (int q = 0; q < folge.length; q++) {
            int a = ziele[folge[q]], b = ziele[folge[(q + 1) % folge.length]];
            int[] vor = new int[w.n];
            w.dijkstra(a, vor);
            List<Integer> p = w.weg(a, b, vor);
            for (int i = q == 0 ? 0 : 1; i < p.size(); i++) {
                int k = p.get(i);
                if (i > 0) { int e = kante(w, p.get(i - 1), k); tt += w.cost[e]; lr += w.laenge[e]; }
                rf.add(String.format(Locale.ROOT, "%d\t%d\t%.1f\t%s", ++seq, k + 1, tt, (i == 0 || i == p.size() - 1) && w.motiv[k] ? w.name[k] : ""));
            }
        }
        Files.write(new File(dir, "rundflug.tsv").toPath(), rf, StandardCharsets.UTF_8);
        String rund = String.format(Locale.GERMAN, "%d Motive, %.1f km, Flugzeit %.0f min (geschlossen ab %s)", ziele.length, lr / 1000, tk[0] / 60, w.name[start]);
        log.log("  Rundflug: %s", rund);

        // Spannbaum und Kamerafahrt
        int[] mst = w.spannbaum();
        double sm = 0;
        List<String> sb = new ArrayList<>();
        sb.add("LINK_ID\tCOST");
        for (int e : mst) { sm += w.cost[e]; sb.add(String.format(Locale.ROOT, "%d\t%.3f", e + 1, w.cost[e])); }
        Files.write(new File(dir, "spannbaum.tsv").toPath(), sb, StandardCharsets.UTF_8);
        List<List<Integer>> baum = new ArrayList<>();
        for (int k = 0; k < w.n; k++) baum.add(new ArrayList<>());
        for (int e : mst) { baum.get(w.von[e]).add(e); baum.get(w.nach[e]).add(e); }
        // Äste ohne Motiv abschneiden (Blätter, die kein Motiv und nicht der Anfang sind, bis keins mehr bleibt)
        int geschnitten = 0;
        for (boolean weiter = true; weiter; ) {
            weiter = false;
            for (int k = 0; k < w.n; k++)
                if (k != start && !w.motiv[k] && baum.get(k).size() == 1) {
                    int e = baum.get(k).get(0), o = w.anderes(e, k);
                    baum.get(k).clear();
                    baum.get(o).remove(Integer.valueOf(e));
                    geschnitten++;
                    weiter = true;
                }
        }
        for (List<Integer> l : baum) l.sort((x, y) -> Double.compare(w.cost[x], w.cost[y]));
        List<String> fa = new ArrayList<>();
        fa.add("SEQ\tNODE_ID\tZEIT_S\tMOTIV");
        double[] ft = {0, 0};
        int[] fs = {0};
        fa.add(String.format(Locale.ROOT, "%d\t%d\t%.1f\t%s", ++fs[0], start + 1, 0.0, w.motiv[start] ? w.name[start] : ""));
        tiefe(w, baum, start, -1, fa, ft, fs);
        Files.write(new File(dir, "fahrt.tsv").toPath(), fa, StandardCharsets.UTF_8);
        String span = String.format(Locale.GERMAN, "%d Kanten, Summe %.0f min; Kamerafahrt (%d Äste ohne Motiv weg) hin und zurück %.1f km, %.0f min", mst.length, sm / 60, geschnitten, ft[1] / 1000, ft[0] / 60);
        log.log("  Spannbaum: %s", span);

        // Nachbarn der Ansichten
        List<String> nb = new ArrayList<>();
        nb.add("NODE_ID\tRANG\tNACHBAR_NODE_ID\tCOST");
        for (int k = 0; k < w.n; k++) {
            if (!SichtNetz.ANSICHT.equals(w.art[k])) continue;
            double[] c = new double[NACHBARN];
            int[] x = w.naechste(k, NACHBARN, c);
            for (int i = 0; i < x.length; i++) nb.add(String.format(Locale.ROOT, "%d\t%d\t%d\t%.3f", k + 1, i + 1, x[i] + 1, c[i]));
        }
        Files.write(new File(dir, "nachbarn.tsv").toPath(), nb, StandardCharsets.UTF_8);

        List<String> t = new ArrayList<>();
        t.add("# LB_NET_SICHT als Datei (Phase 17, Werkbuch 7.6): Kamerapunkte und freie Sicht im Gelände, ungerichtet (com.dan.lituya.scene.SichtNetz)");
        t.add(String.format(Locale.ROOT, "regeln=Strahl alle %.0f m im Kachelstapel 5 m, Ränder %.0f m frei, Erdkrümmung mit k = %.2f; bis %.0f km, Gipfel unter sich bis %.0f km [G]",
                SCHRITT, RAND, SichtNetz.REFRAKTION, DMAX / 1000, DMAX_GIPFEL / 1000));
        t.add(String.format(Locale.ROOT, "kosten=Flugzeit: Länge / (%.0f m/s + %.3f m/s je m Höhe, höchstens %.0f m/s) + Höhenunterschied / %.0f m/s [G]", SichtNetz.V0, SichtNetz.V_JE_M, SichtNetz.V_MAX, SichtNetz.STEIGEN));
        t.add("punkte=" + n0 + " (" + ze + ")");
        t.add(String.format(Locale.GERMAN, "netz=%,d Punkte, %,d Sichtlinien (größte von %d Komponenten)", w.n, w.m, kz[0]));
        t.add("ohne_sicht=" + (ohne.isEmpty() ? "keine" : String.join("; ", ohne)));
        t.add("rundflug=" + rund);
        t.add(String.format(Locale.ROOT, "rundflug.kosten=%.3f", tk[0]));
        t.add("spannbaum=" + span);
        t.add(String.format(Locale.ROOT, "spannbaum.kosten=%.3f", sm));
        t.add("pruefsumme=" + WellenNetz.pruefsumme(dir));
        Files.write(new File(dir, "SICHT.txt").toPath(), t, StandardCharsets.UTF_8);
        log.log("  sicht nach %s in %.1f s", dir, (System.nanoTime() - t0) / 1e9);
        return SichtNetz.lesen(daten);
    }

    /** Tiefensuche den Spannbaum entlang: jede Kante hin und zurück, billigste zuerst. */
    static void tiefe(SichtNetz w, List<List<Integer>> baum, int k, int her, List<String> fa, double[] ft, int[] fs) {
        for (int e : baum.get(k)) {
            if (e == her) continue;
            int o = w.anderes(e, k);
            ft[0] += w.cost[e];
            ft[1] += w.laenge[e];
            fa.add(String.format(Locale.ROOT, "%d\t%d\t%.1f\t%s", ++fs[0], o + 1, ft[0], w.motiv[o] ? w.name[o] : ""));
            tiefe(w, baum, o, e, fa, ft, fs);
            ft[0] += w.cost[e];
            ft[1] += w.laenge[e];
            fa.add(String.format(Locale.ROOT, "%d\t%d\t%.1f\t", ++fs[0], k + 1, ft[0]));
        }
    }

    static int kante(SichtNetz w, int a, int b) {
        for (int e : w.adj[a]) if (w.anderes(e, a) == b) return e;
        throw new IllegalStateException("keine Kante " + a + " – " + b);
    }

    /** Netz aus den Punkten und Kanten, nur die behaltenen Punkte (null: alle), neu nummeriert. */
    static SichtNetz netz(Punkte pk, List<double[]> kanten, boolean[] behalten) {
        int n0 = pk.p.size();
        int[] neu = new int[n0];
        int n = 0;
        for (int k = 0; k < n0; k++) neu[k] = behalten == null || behalten[k] ? n++ : -1;
        SichtNetz w = new SichtNetz();
        w.n = n;
        w.art = new String[n]; w.name = new String[n]; w.ost = new double[n]; w.nord = new double[n]; w.hoehe = new double[n]; w.grund = new double[n];
        w.blick = new double[n]; w.neigung = new double[n]; w.motiv = new boolean[n];
        for (int k = 0; k < n0; k++) {
            int i = neu[k];
            if (i < 0) continue;
            double[] p = pk.p.get(k);
            w.art[i] = pk.art.get(k); w.name[i] = pk.name.get(k); w.ost[i] = p[0]; w.nord[i] = p[1]; w.hoehe[i] = p[2]; w.grund[i] = p[3]; w.blick[i] = p[4]; w.neigung[i] = p[5];
            w.motiv[i] = p[6] > 0;
        }
        List<double[]> ka = new ArrayList<>();
        for (double[] e : kanten) if (neu[(int) e[0]] >= 0 && neu[(int) e[1]] >= 0) ka.add(e);
        w.m = ka.size();
        w.von = new int[w.m]; w.nach = new int[w.m]; w.laenge = new double[w.m]; w.cost = new double[w.m]; w.frei = new double[w.m];
        for (int e = 0; e < w.m; e++) {
            double[] x = ka.get(e);
            w.von[e] = neu[(int) x[0]]; w.nach[e] = neu[(int) x[1]]; w.laenge[e] = x[2]; w.cost[e] = x[3]; w.frei[e] = x[4];
        }
        w.index();
        return w;
    }

    /** Grund (m) am Ort; NaN außerhalb aller Daten. */
    static double boden(Hoehe h, double e, double n) { return h.at(e, n); }

    /**
     * Kleinster Abstand (m) des Strahls von a nach b über dem Gelände (Meer als 0), mit Erdkrümmung und Refraktion; negativ,
     * sobald das Gelände die Sicht nimmt (dann sofort).
     */
    public static double frei(Hoehe h, double[] a, double[] b, double L, AtomicInteger proben) {
        double R2 = 2 * SichtNetz.radius(), min = Double.POSITIVE_INFINITY;
        int n = 0;
        for (double s = RAND; s <= L - RAND; s += SCHRITT) {
            double f = s / L;
            double g = h.at(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f);
            n++;
            if (Double.isNaN(g)) g = 0;
            g = Math.max(g, 0) + s * (L - s) / R2;   // die Erde wölbt sich unter der Sehne auf
            double c = a[2] + (b[2] - a[2]) * f - g;
            if (c < min) min = c;
            if (c < 0) break;
        }
        proben.addAndGet(n);
        return n == 0 ? Math.min(a[2] - Math.max(0, a[3]), b[2] - Math.max(0, b[3])) : min;
    }

    /** Größter Wert im Quadrat (2r + 1)² je Zelle (zwei Durchgänge, NaN wie −∞). */
    static float[] maxFilter(float[] z, int W, int H, int r) {
        float[] a = new float[z.length], b = new float[z.length];
        for (int j = 0; j < H; j++)
            for (int i = 0; i < W; i++) {
                float m = Float.NEGATIVE_INFINITY;
                for (int k = Math.max(0, i - r); k <= Math.min(W - 1, i + r); k++) { float v = z[j * W + k]; if (v > m) m = v; }
                a[j * W + i] = m;
            }
        for (int i = 0; i < W; i++)
            for (int j = 0; j < H; j++) {
                float m = Float.NEGATIVE_INFINITY;
                for (int k = Math.max(0, j - r); k <= Math.min(H - 1, j + r); k++) { float v = a[k * W + i]; if (v > m) m = v; }
                b[j * W + i] = m;
            }
        return b;
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        rechnen(new File(a.length > 0 ? a[0] : "daten"), (f, x) -> System.out.println(String.format(Locale.GERMAN, f, x)));
    }
}
