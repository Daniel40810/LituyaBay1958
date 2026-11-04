package com.dan.lituya.tools;

import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Stufe;
import com.dan.lituya.scene.WasserNetz;
import com.dan.lituya.scene.WellenNetz;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Schritt {@code wasser} der Aufbereitung (Phase 16, Werkbuch 7.4): {@code LB_NET_WASSER} aus dem Gelände der Szene
 * ({@code daten/gelaende/szene_5m}, auf 10 m gemittelt), den Gletschern aus RGI 7 und der Fließgeschwindigkeit aus
 * ITS_LIVE nach {@code daten/netz/wasser} ({@link WasserNetz}).
 * <ol>
 * <li><b>Meer:</b> Zellen unter Mittelwasser (+0,59 m NAVD88), die mit dem Rand der Szene zusammenhängen. Innerhalb des
 * Tors (aus {@code daten/netz/welle/WELLE.txt}) ist es die Bucht; bis 1,2 km um die Namenspunkte von Gilbert und Crillon
 * Inlet (GNIS) zählt das Wasser zum jeweiligen Inlet [G].</li>
 * <li><b>Abfluss:</b> Priority-Flood mit ε (Barnes u. a. 2014) von der Küste und dem Rand der Szene: Senken werden
 * aufgefüllt, jede Zelle fließt zum Nachbarn, von dem aus sie geflutet wurde (dem tiefsten); Fläche stromab
 * aufsummiert. Seen: Flächen gleicher Höhe ab 0,05 km² (das IfSAR-Gelände ist wassereben gemacht).</li>
 * <li><b>Ebene 1:</b> Bäche ab 0,5 km², Knoten an Quellen, Zusammenflüssen, Gletschertoren, Seeausläufen, Teilungen,
 * Mündungen und je 200 m; Kosten = Fließzeit bei Regen nach Manning. Dazu die Fließlinien der Gletscher (ITS_LIVE, 120 m)
 * ab 2 km² Eisfläche, mit der Zeit des Eises als Kosten, und die Mündungsziele.</li>
 * <li><b>Ebene 2:</b> Teileinzugsgebiete: je Mündung eines, geteilt an Zusammenflüssen, an denen mindestens zwei Zweige je
 * 10 km² haben, dazu der Küstensaum je Ziel; Umrisse aus einem 50-m-Raster.</li>
 * </ol>
 * <p>Aufruf: {@code Wasser [daten] [rohdaten]}.
 */
public final class Wasser {
    private Wasser() { }

    public static final double ZELLE = 10;
    public static final double E0 = Site.SZENE.eMin(), N0 = Site.SZENE.nMax();
    public static final int W = (int) Math.round(Site.SZENE.breite() / ZELLE), H = (int) Math.round(Site.SZENE.hoehe() / ZELLE);
    /** Mittelwasser über NAVD88 [G] (wie die Welle). */
    public static final double MW = 0.59;
    /** Bach ab dieser Einzugsfläche (Werkbuch 7.4). */
    public static final double SCHWELLE_KM2 = 0.5;
    /** Teilgebiet an einem Zusammenfluss, wenn mindestens zwei Zweige so viel haben [G]. */
    public static final double TEILUNG_KM2 = 10;
    /** Stützknoten entlang der Bäche [G]. */
    public static final double STUETZ_M = 200;
    /** See ab dieser Fläche gleicher Höhe [G]. */
    public static final double SEE_KM2 = 0.05;
    /** Senke (aufgefüllt) ab dieser Tiefe und Fläche [G]. */
    public static final double SENKE_M = 2, SENKE_KM2 = 0.01;
    /** Inlet: Wasser bis so weit um den Namenspunkt [G]. */
    public static final double INLET_M = 1200;
    /** Regen für die Kosten: 10 mm/h [G], Abflussbeiwert Land 0,6, Eis 0,9 [G]. */
    public static final double REGEN_MM_H = 10, BEIWERT_LAND = 0.6, BEIWERT_EIS = 0.9;
    /** Manning n eines Gebirgsbachs [G]; Wassertiefe d = 0,3·Q^0,4 m (hydraulische Geometrie nach Leopold und Maddock) [G]. */
    public static final double MANNING_N = 0.045;
    /** Eis: Raster der Fließlinien, Schwelle, Stützknoten; Geschwindigkeit ohne ITS_LIVE [G]. */
    public static final double EIS_ZELLE = 120, EIS_SCHWELLE_KM2 = 2, EIS_STUETZ_M = 600, EIS_V_OHNE = 100;
    public static final double JAHR_S = 365.25 * 86400;

    static final int[] DI = {1, 1, 0, -1, -1, -1, 0, 1}, DJ = {0, -1, -1, -1, 0, 1, 1, 1};
    static final byte LAND = 0, MEER = 1;

    public static void rechnen(Rohdaten r) throws Exception { rechnen(r.daten, r.roh, r::log); }

    public static WasserNetz rechnen(File daten, File roh, Welle.Log log) throws Exception {
        long t0 = System.nanoTime();
        final int N = W * H;
        // ---------------------------------------------------------------- Gelände 10 m
        float[] z = new float[N];
        Arrays.fill(z, Float.NaN);
        int kacheln = 0;
        for (double[] k : Stufe.SZENE.kacheln()) {
            float[] t = Stufe.lesen(Stufe.SZENE.datei(daten, k[0], k[1]));
            if (t == null) continue;
            kacheln++;
            int i0 = (int) Math.round((k[0] - E0) / ZELLE), j0 = (int) Math.round((N0 - (k[1] + Stufe.SZENE.kachel())) / ZELLE), nk = Stufe.N / 2;
            for (int jj = 0; jj < nk; jj++) {
                int j = j0 + jj;
                if (j < 0 || j >= H) continue;
                for (int ii = 0; ii < nk; ii++) {
                    int i = i0 + ii;
                    if (i < 0 || i >= W) continue;
                    double s = 0;
                    int c = 0;
                    for (int q = 0; q < 4; q++) {
                        float v = t[(2 * jj + (q >> 1)) * Stufe.N + 2 * ii + (q & 1)];
                        if (!Float.isNaN(v)) { s += v; c++; }
                    }
                    if (c > 0) z[j * W + i] = (float) (s / c);
                }
            }
        }
        if (kacheln == 0) throw new IOException("kein Gelände in " + Stufe.SZENE.ordner(daten) + " (ant aufbereitung -Dschritte=gelaende)");
        int ohne = 0;
        for (int c = 0; c < N; c++) if (Float.isNaN(z[c])) { z[c] = -200; ohne++; }
        log.log("LB_NET_WASSER: Gelände Szene %d × %d zu %.0f m aus %d Kacheln (%d Zellen ohne Wert als Meer)", W, H, ZELLE, kacheln, ohne);

        // ---------------------------------------------------------------- Meer, Bucht, Ziele
        byte[] typ = new byte[N];
        {
            int[] q = new int[N];
            int qh = 0, qt = 0;
            for (int i = 0; i < W; i++) for (int j : new int[]{0, H - 1}) { int c = j * W + i; if (z[c] < MW && typ[c] == LAND) { typ[c] = MEER; q[qt++] = c; } }
            for (int j = 0; j < H; j++) for (int i : new int[]{0, W - 1}) { int c = j * W + i; if (z[c] < MW && typ[c] == LAND) { typ[c] = MEER; q[qt++] = c; } }
            while (qh < qt) {
                int c = q[qh++], i = c % W, j = c / W;
                for (int d = 0; d < 8; d += 2) {
                    int a = i + DI[d], b = j + DJ[d];
                    if (a < 0 || b < 0 || a >= W || b >= H) continue;
                    int n = b * W + a;
                    if (typ[n] == LAND && z[n] < MW) { typ[n] = MEER; q[qt++] = n; }
                }
            }
        }
        Map<String, double[]> namen = namen(daten);
        double[] tor = tor(daten);
        byte[] zielVon = zielVonMeer(typ, tor, namen);
        int nMeer = 0;
        for (byte b : typ) if (b == MEER) nMeer++;

        // ---------------------------------------------------------------- Gletscher (RGI 7)
        short[] eis = new short[N];
        List<Rgi.Gletscher> gl = new ArrayList<>();
        File rgiZip = null;
        for (File f : dateien(roh, "gletscher/rgi7", ".zip")) if (f.getName().startsWith("RGI2000-v7")) rgiZip = f;
        if (rgiZip != null) {
            double[] ll = huelle();
            gl = Rgi.lesen(rgiZip, ll[0], ll[1], ll[2], ll[3]);
            for (int g = 0; g < gl.size() && g < Short.MAX_VALUE; g++) rastern(gl.get(g).ringe(), (short) (g + 1), eis);
        } else log.log("  HINWEIS: RGI 7 fehlt (download/gletscher/rgi7/RGI2000-v7.0-G-01_alaska.zip), ohne Gletscher");
        int nEis = 0;
        for (int c = 0; c < N; c++) if (eis[c] != 0 && typ[c] == LAND) nEis++; else if (typ[c] == MEER) eis[c] = 0;
        log.log("  Meer %.1f km² (Bucht und Inlets %.1f km²), Gletscher %d aus RGI 7 mit %.1f km² in der Szene", nMeer * ZELLE * ZELLE / 1e6, zaehle(zielVon, 0, 2) * ZELLE * ZELLE / 1e6, gl.size(), nEis * ZELLE * ZELLE / 1e6);

        // ---------------------------------------------------------------- Seen (wassereben)
        int[] see = new int[N];
        List<int[]> seen = seen(z, typ, eis, see);   // {id, zellen, auslauf}

        // ---------------------------------------------------------------- Priority-Flood mit ε
        float[] f = z.clone();
        int[] down = new int[N];
        Arrays.fill(down, -2);
        int nLand = N - nMeer;
        int[] order = new int[nLand];
        int no = 0;
        {
            int[] hp = new int[nLand + 1];
            int[] pit = new int[nLand + 1];
            int hn = 0, ph = 0, pt = 0;
            for (int c = 0; c < N; c++) {
                if (typ[c] == MEER) continue;
                int i = c % W, j = c / W;
                int to = -2;
                if (i == 0 || j == 0 || i == W - 1 || j == H - 1) to = -1;
                for (int d = 0; d < 8 && to == -2; d++) {
                    int a = i + DI[d], b = j + DJ[d];
                    if (a < 0 || b < 0 || a >= W || b >= H) continue;
                    if (typ[b * W + a] == MEER) to = b * W + a;
                }
                if (to == -2) continue;
                down[c] = to;
                hn = push(hp, hn, c, f);
            }
            while (hn > 0 || ph != pt) {
                int c;
                if (ph != pt) { c = pit[ph++]; if (ph == pit.length) ph = 0; }
                else { c = hp[0]; hn = pop(hp, hn, f); }
                order[no++] = c;
                int i = c % W, j = c / W;
                for (int d = 0; d < 8; d++) {
                    int a = i + DI[d], b = j + DJ[d];
                    if (a < 0 || b < 0 || a >= W || b >= H) continue;
                    int n = b * W + a;
                    if (typ[n] == MEER || down[n] != -2) continue;
                    down[n] = c;
                    if (z[n] <= f[c]) { f[n] = Math.nextUp(f[c]); pit[pt++] = n; if (pt == pit.length) pt = 0; }
                    else { f[n] = z[n]; hn = push(hp, hn, n, f); }
                }
            }
        }
        if (no != nLand) throw new IllegalStateException("Priority-Flood: " + no + " von " + nLand + " Landzellen erreicht");
        List<double[]> senken = senken(z, f, typ, eis, see);
        float[] gefuellt = f;
        // Auslauf je See: die Seezelle, deren Abfluss den See verlässt
        for (int c = 0; c < N; c++) {
            int s = see[c];
            if (s == 0) continue;
            int d = down[c];
            if (d < 0 || typ[d] == MEER || see[d] != s) seen.get(s - 1)[2] = c;
        }

        // ---------------------------------------------------------------- Fläche stromab
        int[] acc = new int[N], ae = new int[N];
        long[] zielZellen = new long[WasserNetz.ZIELE.length], zielEis = new long[WasserNetz.ZIELE.length];
        for (int k = nLand - 1; k >= 0; k--) {
            int c = order[k];
            acc[c] += 1;
            if (eis[c] != 0) ae[c] += 1;
            int d = down[c];
            if (d >= 0 && typ[d] == LAND) { acc[d] += acc[c]; ae[d] += ae[c]; }
            else { int zl = d < 0 ? 4 : zielVon[d]; zielZellen[zl] += acc[c]; zielEis[zl] += ae[c]; }
        }
        final int S = (int) Math.round(SCHWELLE_KM2 * 1e6 / (ZELLE * ZELLE)), TEIL = (int) Math.round(TEILUNG_KM2 * 1e6 / (ZELLE * ZELLE));

        // ---------------------------------------------------------------- Ebene 1: Bäche
        Bau b = new Bau();
        for (int zl = 0; zl < WasserNetz.ZIELE.length; zl++) {
            double[] p = zielPunkt(zl, namen, tor);
            b.knoten(1, WasserNetz.ZIEL, p[0], p[1], 0, zielZellen[zl] * 1e-4, zielZellen[zl] == 0 ? 0 : zielEis[zl] / (double) zielZellen[zl], WasserNetz.ZIELE[zl], -1);
        }
        byte[] inS = new byte[N];
        int nStrom = 0;
        for (int c = 0; c < N; c++) {
            if (typ[c] != LAND || acc[c] < S) continue;
            nStrom++;
            int d = down[c];
            if (d >= 0 && typ[d] == LAND && inS[d] < 100) inS[d]++;
        }
        // Teilung: Zuflüsse eines Zusammenflusses mit mindestens zwei Zweigen ab TEIL
        boolean[] teilung = new boolean[N];
        for (int c = 0; c < N; c++) {
            if (typ[c] != LAND || acc[c] < TEIL) continue;
            int d = down[c];
            if (d < 0 || typ[d] != LAND || inS[d] < 2) continue;
            int grosse = 0;
            int di = d % W, dj = d / W;
            for (int q = 0; q < 8; q++) {
                int a = di + DI[q], bb = dj + DJ[q];
                if (a < 0 || bb < 0 || a >= W || bb >= H) continue;
                int u = bb * W + a;
                if (typ[u] == LAND && down[u] == d && acc[u] >= TEIL) grosse++;
            }
            if (grosse >= 2) teilung[c] = true;
        }
        Map<Integer, Integer> knotenBei = new HashMap<>();
        List<int[]> quellen = new ArrayList<>();
        for (int k = 0; k < nLand; k++) {
            int c = order[k];
            if (acc[c] < S) continue;
            int d = down[c];
            String art = null;
            boolean mund = d < 0 || typ[d] == MEER;
            if (mund) art = WasserNetz.MUENDUNG;
            else if (teilung[c]) art = WasserNetz.TEILUNG;
            else if (inS[c] >= 2) art = WasserNetz.ZUSAMMENFLUSS;
            else if (inS[c] == 0) art = WasserNetz.QUELLE;
            else if (eis[c] != 0 && eis[d] == 0) art = WasserNetz.GLETSCHERTOR;
            else if (see[c] != 0 && see[d] != see[c]) art = WasserNetz.SEE;
            if (art == null) continue;
            String nm = WasserNetz.GLETSCHERTOR.equals(art) && eis[c] <= gl.size() ? leer(gl.get(eis[c] - 1).name()) : null;
            knotenBei.put(c, b.knoten(1, art, E0 + (c % W + 0.5) * ZELLE, N0 - (c / W + 0.5) * ZELLE, z[c], acc[c] * 1e-4, ae[c] / (double) acc[c], nm, c));
            quellen.add(new int[]{c});
        }
        List<float[]> linien = new ArrayList<>();
        for (int[] qq : quellen) {
            int a = qq[0];
            int ka = knotenBei.get(a);
            if (WasserNetz.MUENDUNG.equals(b.art.get(ka))) {
                int d = down[a];
                int zl = d < 0 ? 4 : zielVon[d];
                b.kante(1, WasserNetz.MUENDET, ka, zl, ZELLE, 0.001, acc[a] * 1e-4, abfluss(acc[a], ae[a]), 1, ZELLE, null);
                linien.add(new float[]{(float) b.ost.get(ka).doubleValue(), (float) b.nord.get(ka).doubleValue(), (float) b.ost.get(zl).doubleValue(), (float) b.nord.get(zl).doubleValue()});
                continue;
            }
            List<Integer> weg = new ArrayList<>();
            weg.add(a);
            double L = 0;
            int c = a;
            while (true) {
                int d = down[c];
                if (d < 0 || typ[d] == MEER) throw new IllegalStateException("Bach endet ohne Mündungsknoten bei Zelle " + c);
                L += (d % W != c % W && d / W != c / W) ? ZELLE * Math.sqrt(2) : ZELLE;
                weg.add(d);
                Integer kd = knotenBei.get(d);
                if (kd == null && L >= STUETZ_M) {
                    kd = b.knoten(1, WasserNetz.STUETZ, E0 + (d % W + 0.5) * ZELLE, N0 - (d / W + 0.5) * ZELLE, z[d], acc[d] * 1e-4, ae[d] / (double) acc[d], null, d);
                    knotenBei.put(d, kd);
                }
                if (kd != null) {
                    int s = weg.get(0);
                    double gef = Math.max(0.001, (z[s] - z[d]) / L);
                    double q = abfluss(acc[s], ae[s]);
                    double v = geschw(q, gef);
                    boolean aufEis = eis[s] != 0;
                    b.kante(1, aufEis ? WasserNetz.BACH_EIS : WasserNetz.BACH, knotenBei.get(s), kd, L, gef, acc[s] * 1e-4, q, v, L / v, null);
                    linien.add(linie(weg));
                    if (WasserNetz.MUENDUNG.equals(b.art.get(kd)) || !WasserNetz.STUETZ.equals(b.art.get(kd))) break;
                    weg = new ArrayList<>();
                    weg.add(d);
                    L = 0;
                }
                c = d;
            }
        }
        int nBach = b.m();
        log.log("  Ebene 1: %,d Bachzellen ab %.1f km², %,d Knoten, %,d Kanten (%.0f km Bach)", nStrom, SCHWELLE_KM2, b.n(), nBach, b.summe(1, WasserNetz.BACH, WasserNetz.BACH_EIS) / 1000);

        // ---------------------------------------------------------------- Ebene 2: Teileinzugsgebiete
        int[] lab = new int[N];
        Arrays.fill(lab, -1);
        List<int[]> gebiete = new ArrayList<>();   // {auslaufzelle oder −1, ziel, knoten ebene 1 am Auslauf oder −1}
        int[] kueste = new int[WasserNetz.ZIELE.length];
        Arrays.fill(kueste, -1);
        for (int k = 0; k < nLand; k++) {
            int c = order[k], d = down[c];
            boolean mund = d < 0 || typ[d] == MEER;
            boolean auslauf = acc[c] >= S && (mund || teilung[c]);
            if (auslauf) {
                gebiete.add(new int[]{c, mund ? (d < 0 ? 4 : zielVon[d]) : -1, knotenBei.get(c)});
                lab[c] = gebiete.size() - 1;
            } else if (!mund) lab[c] = lab[d];
            else {
                int zl = d < 0 ? 4 : zielVon[d];
                if (kueste[zl] < 0) { gebiete.add(new int[]{-1, zl, -1}); kueste[zl] = gebiete.size() - 1; }
                lab[c] = kueste[zl];
            }
        }
        long[] eigenZ = new long[gebiete.size()], eigenEis = new long[gebiete.size()];
        for (int c = 0; c < N; c++) if (lab[c] >= 0) { eigenZ[lab[c]]++; if (eis[c] != 0) eigenEis[lab[c]]++; }
        // Fließzeit der Ebene 1 zum Ziel (für die Kosten der Ebene 2)
        WasserNetz w1 = b.netz();
        double[] t1 = new double[w1.n];
        Arrays.fill(t1, Double.NaN);
        for (int zl = 0; zl < WasserNetz.ZIELE.length; zl++) {
            double[] t = w1.zeitZum(zl, 1);
            for (int i = 0; i < w1.n; i++) if (!Double.isNaN(t[i])) t1[i] = t[i];
        }
        int[] zielE2 = new int[WasserNetz.ZIELE.length];
        for (int zl = 0; zl < WasserNetz.ZIELE.length; zl++) zielE2[zl] = b.knoten(2, WasserNetz.ZIEL, b.ost.get(zl), b.nord.get(zl), 0, zielZellen[zl] * 1e-4,
                zielZellen[zl] == 0 ? 0 : zielEis[zl] / (double) zielZellen[zl], WasserNetz.ZIELE[zl], -1);
        for (int zl = 0; zl < WasserNetz.ZIELE.length; zl++) b.parent.set(zl, zielE2[zl]);
        int[] gk = new int[gebiete.size()];
        for (int g = 0; g < gebiete.size(); g++) {
            int[] x = gebiete.get(g);
            if (x[0] >= 0) {
                int c = x[0];
                gk[g] = b.knoten(2, WasserNetz.GEBIET, E0 + (c % W + 0.5) * ZELLE, N0 - (c / W + 0.5) * ZELLE, z[c], acc[c] * 1e-4, ae[c] / (double) acc[c], null, c);
            } else {
                double[] p = zielPunkt(x[1], namen, tor);
                gk[g] = b.knoten(2, WasserNetz.KUESTE, p[0], p[1], 0, eigenZ[g] * 1e-4, eigenZ[g] == 0 ? 0 : eigenEis[g] / (double) eigenZ[g], "Küste " + WasserNetz.ZIELE[x[1]], -1);
            }
        }
        for (int g = 0; g < gebiete.size(); g++) {
            int[] x = gebiete.get(g);
            int von = gk[g], nach;
            double cost;
            if (x[0] < 0) { nach = zielE2[x[1]]; cost = 0; }
            else if (x[1] >= 0) { nach = zielE2[x[1]]; cost = t1[x[2]]; }
            else {
                int d = down[x[0]];
                int h = lab[d];
                nach = gk[h];
                int kd = gebiete.get(h)[2];
                cost = t1[x[2]] - (kd >= 0 ? t1[kd] : 0);
            }
            double L = Math.hypot(b.ost.get(von) - b.ost.get(nach), b.nord.get(von) - b.nord.get(nach));
            b.kante(2, x[0] < 0 || x[1] >= 0 ? WasserNetz.MUENDET : WasserNetz.STROMAB, von, nach, L, 0, b.flaeche.get(von), x[0] < 0 ? 0 : abfluss(acc[x[0]], ae[x[0]]),
                    cost > 0 ? L / cost : 0, Math.max(0, cost), null);
        }
        log.log("  Ebene 2: %d Teileinzugsgebiete (%d Mündungen, %d Teilungen, %d Küstensäume) und %d Ziele", gebiete.size(), zaehleGebiete(gebiete, true), zaehleGebiete(gebiete, false),
                Arrays.stream(kueste).filter(x -> x >= 0).count(), WasserNetz.ZIELE.length);

        // ---------------------------------------------------------------- Gletscher: Fließlinien
        String eisQuelle = eislinien(b, z, gefuellt, typ, eis, gl, down, knotenBei, lab, gk, zielVon, roh, log);
        gefuellt = null;
        // Eltern der Ebene 1: das Gebiet der Zelle; ein Eisknoten gehört zum Gebiet, in das sein Gletscher das Wasser abgibt
        // (das Eis fließt auch über Wasserscheiden im Gelände)
        for (int i = WasserNetz.ZIELE.length; i < b.n(); i++) if (b.ebene.get(i) == 1 && b.zelle.get(i) >= 0 && !WasserNetz.EIS.equals(b.art.get(i))) b.parent.set(i, gk[lab[b.zelle.get(i)]]);
        {
            WasserNetz we = b.netz();
            int quer = 0;
            for (int i = 0; i < we.n; i++) {
                if (!WasserNetz.EIS.equals(we.art[i])) continue;
                List<Integer> l = we.traceOut(i, 1);
                int x = l.get(l.size() - 1);
                for (int v : l) if (!WasserNetz.EIS.equals(we.art[v])) { x = v; break; }
                int p = b.parent.get(x);
                b.parent.set(i, p);
                if (p != gk[lab[b.zelle.get(i)]]) quer++;
            }
            log.log("  %d Eisknoten liefern in ein anderes Teileinzugsgebiet als das, in dem sie liegen (Eis fließt über Wasserscheiden des Geländes)", quer);
        }

        // ---------------------------------------------------------------- Namen der Bäche (GNIS)
        WasserNetz w = b.netz();
        int benannt = namenBaeche(w, b, linien, namen, nBach);
        w = b.netz();
        String fehler = w.fehler();
        if (fehler != null) throw new IllegalStateException("LB_NET_WASSER fehlerhaft: " + fehler);

        // ---------------------------------------------------------------- Dateien
        File dir = new File(daten, WasserNetz.ORDNER);
        w.schreiben(daten);
        List<String> li = new ArrayList<>();
        li.add("LINK_ID\tPUNKTE");
        for (int e = 0; e < linien.size(); e++) {
            float[] p = linien.get(e);
            StringBuilder s = new StringBuilder();
            for (int q = 0; q < p.length; q += 2) s.append(q > 0 ? "," : "").append(String.format(Locale.ROOT, "%.1f %.1f", p[q], p[q + 1]));
            li.add((e + 1) + "\t" + s);
        }
        Files.write(new File(dir, "linien.tsv").toPath(), li, StandardCharsets.UTF_8);
        // Gebiete und Umrisse (50 m)
        int W5 = W / 5, H5 = H / 5;
        int[] l50 = new int[W5 * H5];
        for (int j = 0; j < H5; j++) for (int i = 0; i < W5; i++) { int c = (5 * j + 2) * W + 5 * i + 2; l50[j * W5 + i] = lab[c] < 0 ? 0 : gk[lab[c]] + 1; }
        schreibeI32(new File(dir, "einzug_50m.i32"), l50);
        List<String> ez = new ArrayList<>(), um = new ArrayList<>();
        ez.add("NODE_ID\tART\tAUSLAUF_NODE_ID\tZIEL\tFLAECHE_EIGEN_KM2\tFLAECHE_KM2\tEIS_ANTEIL\tNAME");
        um.add("NODE_ID\tTEIL\tRING\tPUNKTE");
        Map<Integer, List<List<double[]>>> ringe = Umriss.ringe(l50, W5, H5, E0, N0, 5 * ZELLE);
        for (int g = 0; g < gebiete.size(); g++) {
            int[] x = gebiete.get(g);
            int k = gk[g];
            List<Integer> aus = w.traceOut(k, 2);
            String ziel = w.name[aus.get(aus.size() - 1)];
            ez.add(String.format(Locale.ROOT, "%d\t%s\t%s\t%s\t%.4f\t%.4f\t%.3f\t%s", k + 1, w.art[k], x[2] < 0 ? "" : String.valueOf(x[2] + 1), ziel, eigenZ[g] * 1e-4, w.flaeche[k],
                    w.eisAnteil[k], w.name[k] == null ? "" : w.name[k]));
            List<List<double[]>> rr = ringe.get(k + 1);
            if (rr == null) continue;
            int teil = 0;
            for (List<double[]> poly : rr) {
                for (int ri = 0; ri < poly.size(); ri++) {
                    double[] p = poly.get(ri);
                    StringBuilder s = new StringBuilder();
                    for (int q = 0; q < p.length; q += 2) s.append(q > 0 ? "," : "").append(String.format(Locale.ROOT, "%.0f %.0f", p[q], p[q + 1]));
                    um.add((k + 1) + "\t" + teil + "\t" + ri + "\t" + s);
                }
                teil++;
            }
        }
        Files.write(new File(dir, "einzug.tsv").toPath(), ez, StandardCharsets.UTF_8);
        Files.write(new File(dir, "umrisse.tsv").toPath(), um, StandardCharsets.UTF_8);
        // Seen und Senken
        List<String> sz = new ArrayList<>();
        sz.add("SEE_ID\tOST\tNORD\tHOEHE\tFLAECHE_KM2\tAUSLAUF_NODE_ID\tNAME");
        for (int[] s : seen) {
            int c = s[2];
            Integer k = knotenBei.get(c);
            String nm = naechster(namen, "Lake", E0 + (c % W + 0.5) * ZELLE, N0 - (c / W + 0.5) * ZELLE, 1500 + Math.sqrt(s[1]) * ZELLE);
            sz.add(String.format(Locale.ROOT, "%d\t%.1f\t%.1f\t%.2f\t%.4f\t%s\t%s", s[0], E0 + (c % W + 0.5) * ZELLE, N0 - (c / W + 0.5) * ZELLE, z[c], s[1] * 1e-4, k == null ? "" : String.valueOf(k + 1), nm == null ? "" : nm));
        }
        Files.write(new File(dir, "seen.tsv").toPath(), sz, StandardCharsets.UTF_8);
        List<String> sk = new ArrayList<>();
        sk.add("SENKE_ID\tOST\tNORD\tFLAECHE_KM2\tTIEFE_M\tVOLUMEN_M3\tAUF_EIS");
        for (int i = 0; i < senken.size(); i++) {
            double[] s = senken.get(i);
            sk.add(String.format(Locale.ROOT, "%d\t%.1f\t%.1f\t%.4f\t%.1f\t%.0f\t%s", i + 1, s[0], s[1], s[2], s[3], s[4], s[5] > 0.5 ? "J" : "N"));
        }
        Files.write(new File(dir, "senken.tsv").toPath(), sk, StandardCharsets.UTF_8);

        // ---------------------------------------------------------------- Analysen
        List<String> kopf = new ArrayList<>(), pw = new ArrayList<>();
        pw.add("ZIEL\tEBENE\tKNOTEN\tFLAECHE_KM2\tZEIT_MAX_S");
        for (int zl = 0; zl < WasserNetz.ZIELE.length; zl++) {
            boolean[] in1 = w.traceIn(zl, 1), in2 = w.traceIn(zielE2[zl], 2);
            int n1 = 0, n2 = 0;
            double fl2 = 0, tmax = 0;
            double[] t = w.zeitZum(zl, 1);
            for (int i = 0; i < w.n; i++) {
                if (in1[i]) { n1++; if (!Double.isNaN(t[i]) && !WasserNetz.EIS.equals(w.art[i])) tmax = Math.max(tmax, t[i]); }
                if (in2[i]) { n2++; if (i != zielE2[zl]) fl2 += eigenVon(w, i, gk, eigenZ); }
            }
            pw.add(String.format(Locale.ROOT, "%s\t1\t%d\t\t%.1f", WasserNetz.ZIELE[zl], n1, tmax));
            pw.add(String.format(Locale.ROOT, "%s\t2\t%d\t%.4f\t", WasserNetz.ZIELE[zl], n2, fl2));
            double fl = zielZellen[zl] * 1e-4;
            String v = String.format(Locale.GERMAN, "%.1f km²; %.0f %% Eis; Fließzeit bei Regen bis %.1f h; %d Knoten, %d Gebiete", fl, zielZellen[zl] == 0 ? 0 : 100.0 * zielEis[zl] / zielZellen[zl], tmax / 3600, n1, n2 - 1);
            kopf.add("einzug." + WasserNetz.schluessel(WasserNetz.ZIELE[zl]) + "=" + v);
            log.log("  %-16s %s", WasserNetz.ZIELE[zl], v);
        }
        // stößt ein Gebiet an den Rand der Szene? (dann ist es dort abgeschnitten)
        Map<Integer, String> zielVonGebiet = new HashMap<>();
        long[] amRand = new long[WasserNetz.ZIELE.length];
        for (int j = 0; j < H; j++)
            for (int i = 0; i < W; i += (j == 0 || j == H - 1) ? 1 : W - 1) {
                int c = j * W + i;
                if (lab[c] < 0) continue;
                int k = gk[lab[c]];
                String zz = zielVonGebiet.computeIfAbsent(k, x -> { List<Integer> l = w0(x, gebiete, gk, lab, down, zielVon); return WasserNetz.ZIELE[l.get(0)]; });
                amRand[Arrays.asList(WasserNetz.ZIELE).indexOf(zz)]++;
            }
        for (int zl = 0; zl < 3; zl++) {
            kopf.add(String.format(Locale.ROOT, "rand.%s=%d Zellen am Rand der Szene", WasserNetz.schluessel(WasserNetz.ZIELE[zl]), amRand[zl]));
            if (amRand[zl] > 0) log.log("  HINWEIS: das Einzugsgebiet von %s stößt auf %d Zellen an den Rand der Szene (dort abgeschnitten)", WasserNetz.ZIELE[zl], amRand[zl]);
        }
        // die ganze Bucht (Bucht und beide Inlets)
        long bucht = zielZellen[0] + zielZellen[1] + zielZellen[2], buchtEis = zielEis[0] + zielEis[1] + zielEis[2];
        kopf.add(String.format(Locale.GERMAN, "einzug.lituya_bay=%.1f km²; %.0f %% Eis (Gilbert, Crillon und Bucht zusammen)", bucht * 1e-4, bucht == 0 ? 0 : 100.0 * buchtEis / bucht));
        log.log("  Lituya Bay zusammen %.1f km² (%.0f %% Eis)", bucht * 1e-4, bucht == 0 ? 0 : 100.0 * buchtEis / bucht);
        // Mudslide Creek
        String mud = bach(w, "Mudslide Creek");
        if (mud != null) { kopf.add("mudslide_creek=" + mud); log.log("  Mudslide Creek: %s", mud); }
        // traceOut vom höchsten Eisknoten des Lituya-Gletschers
        String to = traceOutGletscher(w, "Lituya Glacier");
        if (to != null) { kopf.add("traceout.lituya_glacier=" + to); log.log("  traceOut Lituya Glacier: %s", to); }
        int[] kz = new int[1];
        w.komponenten(1, kz);
        int[] kz2 = new int[1];
        w.komponenten(2, kz2);
        int seeAusl = 0;
        for (int[] s : seen) if (knotenBei.containsKey(s[2])) seeAusl++;
        log.log("  %d Seen ab %.2f km² (%d mit Auslauf im Netz), %d Senken ab %.0f m und %.2f km² aufgefüllt; Komponenten Ebene 1: %d, Ebene 2: %d (je Ziel eine); %d Bäche benannt",
                seen.size(), SEE_KM2, seeAusl, senken.size(), SENKE_M, SENKE_KM2, kz[0], kz2[0], benannt);
        Files.write(new File(dir, "pruefwege.tsv").toPath(), pw, StandardCharsets.UTF_8);

        List<String> t = new ArrayList<>();
        t.add("# LB_NET_WASSER als Datei (Phase 16, Werkbuch 7.4): Gewässer und Gletscher der Szene, gerichtet stromab, zwei Ebenen (com.dan.lituya.scene.WasserNetz)");
        t.add(String.format(Locale.ROOT, "knoten=%d", w.n));
        t.add(String.format(Locale.ROOT, "kanten=%d", w.m));
        int n1 = 0, m1 = 0;
        for (int i = 0; i < w.n; i++) if (w.ebene[i] == 1) n1++;
        for (int e = 0; e < w.m; e++) if (w.kEbene[e] == 1) m1++;
        t.add(String.format(Locale.ROOT, "ebene1=%d Knoten, %d Kanten", n1, m1));
        t.add(String.format(Locale.ROOT, "ebene2=%d Knoten, %d Kanten", w.n - n1, w.m - m1));
        t.add(String.format(Locale.ROOT, "gitter=%.0f m über der Szene (%d × %d, E %.0f N %.0f NW-Ecke), Gelände szene_5m gemittelt", ZELLE, W, H, E0, N0));
        t.add(String.format(Locale.ROOT, "meer=unter %.2f m NAVD88, mit dem Rand verbunden; Inlets bis %.0f m um GNIS Gilbert Inlet und Crillon Inlet [G]", MW, INLET_M));
        t.add(String.format(Locale.ROOT, "bach=ab %.2f km², Stützknoten je %.0f m, Teilgebiete ab %.0f km² je Zweig", SCHWELLE_KM2, STUETZ_M, TEILUNG_KM2));
        t.add(String.format(Locale.ROOT, "kosten=Fließzeit (s) bei Regen %.0f mm/h, Beiwert Land %.1f, Eis %.1f, Manning n %.3f, Tiefe 0,3·Q^0,4 [G]; Gletscher: Länge / Geschwindigkeit des Eises", REGEN_MM_H, BEIWERT_LAND, BEIWERT_EIS, MANNING_N));
        t.add("gletscher=" + (rgiZip == null ? "keine (RGI 7 fehlt)" : "RGI 7.0 (" + rgiZip.getName() + "), " + gl.size() + " Umrisse"));
        t.add("eis_geschwindigkeit=" + eisQuelle);
        t.add(String.format(Locale.ROOT, "seen=%d (Flächen gleicher Höhe ab %.2f km²)", seen.size(), SEE_KM2));
        t.add(String.format(Locale.ROOT, "senken=%d (aufgefüllt ab %.0f m Tiefe und %.2f km²)", senken.size(), SENKE_M, SENKE_KM2));
        t.add(String.format(Locale.ROOT, "komponenten=%d (Ebene 1), %d (Ebene 2)", kz[0], kz2[0]));
        t.addAll(kopf);
        t.add("pruefsumme=" + WellenNetz.pruefsumme(dir));
        Files.write(new File(dir, "WASSER.txt").toPath(), t, StandardCharsets.UTF_8);
        log.log("  nach %s in %.1f s", dir, (System.nanoTime() - t0) / 1e9);
        return WasserNetz.lesen(daten);
    }

    // ==================================================================== Bauteile

    /** Sammelt Knoten und Kanten. */
    static final class Bau {
        final List<Integer> ebene = new ArrayList<>(), parent = new ArrayList<>(), zelle = new ArrayList<>();
        final List<String> art = new ArrayList<>(), name = new ArrayList<>();
        final List<Double> ost = new ArrayList<>(), nord = new ArrayList<>(), hoehe = new ArrayList<>(), flaeche = new ArrayList<>(), eisAnteil = new ArrayList<>();
        final List<Integer> kEbene = new ArrayList<>(), von = new ArrayList<>(), nach = new ArrayList<>();
        final List<String> kArt = new ArrayList<>(), kName = new ArrayList<>();
        final List<Double> laenge = new ArrayList<>(), gefaelle = new ArrayList<>(), kFlaeche = new ArrayList<>(), abfluss = new ArrayList<>(), geschw = new ArrayList<>(), cost = new ArrayList<>();

        int n() { return ebene.size(); }
        int m() { return kEbene.size(); }

        int knoten(int eb, String a, double e, double nn, double h, double fl, double ea, String nm, int c) {
            ebene.add(eb); art.add(a); ost.add(e); nord.add(nn); hoehe.add(h); flaeche.add(fl); eisAnteil.add(ea); name.add(nm); parent.add(-1); zelle.add(c);
            return ebene.size() - 1;
        }

        int kante(int eb, String a, int v, int n, double l, double g, double fl, double q, double gw, double c, String nm) {
            kEbene.add(eb); kArt.add(a); von.add(v); nach.add(n); laenge.add(l); gefaelle.add(g); kFlaeche.add(fl); abfluss.add(q); geschw.add(gw); cost.add(c); kName.add(nm);
            return kEbene.size() - 1;
        }

        double summe(int eb, String... arten) {
            double s = 0;
            for (int e = 0; e < m(); e++) if (kEbene.get(e) == eb && Arrays.asList(arten).contains(kArt.get(e))) s += laenge.get(e);
            return s;
        }

        WasserNetz netz() {
            WasserNetz w = new WasserNetz();
            w.n = n();
            w.ebene = ebene.stream().mapToInt(Integer::intValue).toArray();
            w.parent = parent.stream().mapToInt(Integer::intValue).toArray();
            w.art = art.toArray(new String[0]);
            w.name = name.toArray(new String[0]);
            w.ost = ost.stream().mapToDouble(Double::doubleValue).toArray();
            w.nord = nord.stream().mapToDouble(Double::doubleValue).toArray();
            w.hoehe = hoehe.stream().mapToDouble(Double::doubleValue).toArray();
            w.flaeche = flaeche.stream().mapToDouble(Double::doubleValue).toArray();
            w.eisAnteil = eisAnteil.stream().mapToDouble(Double::doubleValue).toArray();
            w.m = m();
            w.kEbene = kEbene.stream().mapToInt(Integer::intValue).toArray();
            w.von = von.stream().mapToInt(Integer::intValue).toArray();
            w.nach = nach.stream().mapToInt(Integer::intValue).toArray();
            w.kArt = kArt.toArray(new String[0]);
            w.kName = kName.toArray(new String[0]);
            w.laenge = laenge.stream().mapToDouble(Double::doubleValue).toArray();
            w.gefaelle = gefaelle.stream().mapToDouble(Double::doubleValue).toArray();
            w.kFlaeche = kFlaeche.stream().mapToDouble(Double::doubleValue).toArray();
            w.abfluss = abfluss.stream().mapToDouble(Double::doubleValue).toArray();
            w.geschw = geschw.stream().mapToDouble(Double::doubleValue).toArray();
            w.cost = cost.stream().mapToDouble(Double::doubleValue).toArray();
            w.index();
            return w;
        }
    }

    /** Abfluss bei Regen (m³/s) aus der Einzugsfläche (Zellen, davon Eis). */
    public static double abfluss(int zellen, int eisZellen) {
        return REGEN_MM_H / 1000 / 3600 * ZELLE * ZELLE * (BEIWERT_LAND * (zellen - eisZellen) + BEIWERT_EIS * eisZellen);
    }

    /** Fließgeschwindigkeit (m/s) nach Manning mit d = 0,3·Q^0,4, begrenzt auf 0,1 … 6 m/s. */
    public static double geschw(double q, double gefaelle) {
        double d = 0.3 * Math.pow(Math.max(q, 1e-3), 0.4);
        return Math.max(0.1, Math.min(6, Math.pow(d, 2.0 / 3) * Math.sqrt(gefaelle) / MANNING_N));
    }

    static float[] linie(List<Integer> weg) {
        double[] p = new double[2 * weg.size()];
        for (int i = 0; i < weg.size(); i++) { int c = weg.get(i); p[2 * i] = E0 + (c % W + 0.5) * ZELLE; p[2 * i + 1] = N0 - (c / W + 0.5) * ZELLE; }
        double[] q = Umriss.douglasPeucker(p, 4);
        float[] r = new float[q.length];
        for (int i = 0; i < q.length; i++) r[i] = (float) q[i];
        return r;
    }

    static int push(int[] hp, int hn, int c, float[] f) {
        int i = hn++;
        float k = f[c];
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (f[hp[p]] <= k) break;
            hp[i] = hp[p];
            i = p;
        }
        hp[i] = c;
        return hn;
    }

    static int pop(int[] hp, int hn, float[] f) {
        int last = hp[--hn];
        float k = f[last];
        int i = 0;
        while (true) {
            int l = 2 * i + 1;
            if (l >= hn) break;
            int r = l + 1, m = r < hn && f[hp[r]] < f[hp[l]] ? r : l;
            if (f[hp[m]] >= k) break;
            hp[i] = hp[m];
            i = m;
        }
        if (hn > 0) hp[i] = last;
        return hn;
    }

    static long zaehle(byte[] zv, int lo, int hi) { long n = 0; for (byte b : zv) if (b >= lo && b <= hi) n++; return n; }

    static long zaehleGebiete(List<int[]> g, boolean mund) { return g.stream().filter(x -> x[0] >= 0 && (mund ? x[1] >= 0 : x[1] < 0)).count(); }

    static String leer(String s) { return s == null || s.isBlank() ? null : s; }

    static double eigenVon(WasserNetz w, int k, int[] gk, long[] eigenZ) {
        for (int g = 0; g < gk.length; g++) if (gk[g] == k) return eigenZ[g] * 1e-4;
        return 0;
    }

    /** Rastert Ringe (Länge, Breite) gerade-ungerade auf die Zellmitten. */
    static void rastern(List<double[]> ringe, short wert, short[] eis) {
        List<double[]> xy = new ArrayList<>();
        double ymin = 1e18, ymax = -1e18;
        for (double[] r : ringe) {
            double[] p = new double[r.length];
            for (int q = 0; q < r.length; q += 2) {
                double[] en = com.dan.lituya.scene.Geo.UTM8N.forward(r[q + 1], r[q]);
                p[q] = (en[0] - E0) / ZELLE - 0.5;
                p[q + 1] = (N0 - en[1]) / ZELLE - 0.5;
                ymin = Math.min(ymin, p[q + 1]);
                ymax = Math.max(ymax, p[q + 1]);
            }
            xy.add(p);
        }
        int j0 = Math.max(0, (int) Math.ceil(ymin)), j1 = Math.min(H - 1, (int) Math.floor(ymax));
        double[] xs = new double[64];
        for (int j = j0; j <= j1; j++) {
            int nx = 0;
            for (double[] p : xy)
                for (int q = 0; q < p.length; q += 2) {
                    int r = (q + 2) % p.length;
                    double ya = p[q + 1], yb = p[r + 1];
                    if ((ya <= j && yb > j) || (yb <= j && ya > j)) {
                        if (nx == xs.length) xs = Arrays.copyOf(xs, nx * 2);
                        xs[nx++] = p[q] + (j - ya) / (yb - ya) * (p[r] - p[q]);
                    }
                }
            Arrays.sort(xs, 0, nx);
            for (int k = 0; k + 1 < nx; k += 2) {
                int i0 = Math.max(0, (int) Math.ceil(xs[k])), i1 = Math.min(W - 1, (int) Math.floor(xs[k + 1]));
                for (int i = i0; i <= i1; i++) eis[j * W + i] = wert;
            }
        }
    }

    /** Seen: zusammenhängende Flächen exakt gleicher Höhe (wassereben) ab SEE_KM2, nicht auf Eis. {id, zellen, auslauf (tiefste Randzelle)} */
    static List<int[]> seen(float[] z, byte[] typ, short[] eis, int[] see) {
        int min = (int) Math.round(SEE_KM2 * 1e6 / (ZELLE * ZELLE));
        List<int[]> l = new ArrayList<>();
        boolean[] gesehen = new boolean[z.length];
        int[] q = new int[1 << 16];
        int id = 0;
        for (int s = 0; s < z.length; s++) {
            if (gesehen[s] || typ[s] != LAND) continue;
            gesehen[s] = true;
            int qt = 0, qh = 0, eisZ = 0;
            q[qt++] = s;
            float h = z[s];
            List<Integer> zellen = null;
            while (qh < qt) {
                int c = q[qh++];
                if (eis[c] != 0) eisZ++;
                int i = c % W, j = c / W;
                for (int d = 0; d < 8; d += 2) {
                    int a = i + DI[d], b = j + DJ[d];
                    if (a < 0 || b < 0 || a >= W || b >= H) continue;
                    int n = b * W + a;
                    if (gesehen[n] || typ[n] != LAND || z[n] != h) continue;
                    gesehen[n] = true;
                    if (qt == q.length) q = Arrays.copyOf(q, q.length * 2);
                    q[qt++] = n;
                }
            }
            if (qt < min || eisZ * 2 > qt) continue;
            id++;
            for (int k = 0; k < qt; k++) see[q[k]] = id;
            l.add(new int[]{id, qt, q[0]});
        }
        return l;
    }

    /** Senken: zusammenhängende Zellen mit Auffüllung über SENKE_M ab SENKE_KM2. {ost, nord, km², tiefe, m³, eisanteil} */
    static List<double[]> senken(float[] z, float[] f, byte[] typ, short[] eis, int[] see) {
        int min = (int) Math.round(SENKE_KM2 * 1e6 / (ZELLE * ZELLE));
        List<double[]> l = new ArrayList<>();
        boolean[] gesehen = new boolean[z.length];
        int[] q = new int[1 << 16];
        for (int s = 0; s < z.length; s++) {
            if (gesehen[s] || typ[s] != LAND || f[s] - z[s] < SENKE_M) continue;
            gesehen[s] = true;
            int qt = 0, qh = 0, tief = s, eisZ = 0;
            double vol = 0;
            q[qt++] = s;
            while (qh < qt) {
                int c = q[qh++];
                vol += (f[c] - z[c]) * ZELLE * ZELLE;
                if (f[c] - z[c] > f[tief] - z[tief]) tief = c;
                if (eis[c] != 0) eisZ++;
                int i = c % W, j = c / W;
                for (int d = 0; d < 8; d += 2) {
                    int a = i + DI[d], b = j + DJ[d];
                    if (a < 0 || b < 0 || a >= W || b >= H) continue;
                    int n = b * W + a;
                    if (gesehen[n] || typ[n] != LAND || f[n] - z[n] < SENKE_M) continue;
                    gesehen[n] = true;
                    if (qt == q.length) q = Arrays.copyOf(q, q.length * 2);
                    q[qt++] = n;
                }
            }
            if (qt < min) continue;
            l.add(new double[]{E0 + (tief % W + 0.5) * ZELLE, N0 - (tief / W + 0.5) * ZELLE, qt * ZELLE * ZELLE / 1e6, f[tief] - z[tief], vol, eisZ / (double) qt});
        }
        return l;
    }

    /** Ziel je Meerzelle: 0 Gilbert, 1 Crillon, 2 Bucht, 3 Golf (das Tor trennt Bucht und Golf). */
    static byte[] zielVonMeer(byte[] typ, double[] tor, Map<String, double[]> namen) {
        int N = typ.length;
        byte[] zv = new byte[N];
        Arrays.fill(zv, (byte) 3);
        boolean[] sperre = new boolean[N];
        double ax = tor[0], ay = tor[1], bx = tor[2], by = tor[3];
        int i0 = (int) Math.floor((Math.min(ax, bx) - E0) / ZELLE) - 2, i1 = (int) Math.ceil((Math.max(ax, bx) - E0) / ZELLE) + 2;
        int j0 = (int) Math.floor((N0 - Math.max(ay, by)) / ZELLE) - 2, j1 = (int) Math.ceil((N0 - Math.min(ay, by)) / ZELLE) + 2;
        for (int j = Math.max(0, j0); j <= Math.min(H - 1, j1); j++)
            for (int i = Math.max(0, i0); i <= Math.min(W - 1, i1); i++) {
                double x = E0 + (i + 0.5) * ZELLE, y = N0 - (j + 0.5) * ZELLE;
                double dx = bx - ax, dy = by - ay, t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
                if (Math.hypot(x - ax - t * dx, y - ay - t * dy) <= ZELLE) sperre[j * W + i] = true;
            }
        double[] lb = namen.getOrDefault("Lituya Bay", new double[]{350641.9, 6502491.1});
        int s = naechsteMeerzelle(typ, sperre, lb[0], lb[1]);
        if (s < 0) return zv;
        int[] q = new int[N / 4];
        int qh = 0, qt = 0;
        q[qt++] = s;
        zv[s] = 2;
        boolean[] bucht = new boolean[N];
        bucht[s] = true;
        while (qh < qt) {
            int c = q[qh++], i = c % W, j = c / W;
            for (int d = 0; d < 8; d += 2) {
                int a = i + DI[d], b = j + DJ[d];
                if (a < 0 || b < 0 || a >= W || b >= H) continue;
                int n = b * W + a;
                if (bucht[n] || sperre[n] || typ[n] != MEER) continue;
                bucht[n] = true;
                if (qt == q.length) q = Arrays.copyOf(q, q.length * 2);
                q[qt++] = n;
            }
        }
        double[] g = namen.getOrDefault("Gilbert Inlet", new double[]{354903.5, 6506385.5}), cr = namen.getOrDefault("Crillon Inlet", new double[]{356876.9, 6504362.0});
        for (int k = 0; k < qt; k++) {
            int c = q[k];
            double x = E0 + (c % W + 0.5) * ZELLE, y = N0 - (c / W + 0.5) * ZELLE;
            double dg = Math.hypot(x - g[0], y - g[1]), dc = Math.hypot(x - cr[0], y - cr[1]);
            zv[c] = (byte) (Math.min(dg, dc) > INLET_M ? 2 : dg <= dc ? 0 : 1);
        }
        return zv;
    }

    static int naechsteMeerzelle(byte[] typ, boolean[] sperre, double e, double n) {
        int i0 = (int) ((e - E0) / ZELLE), j0 = (int) ((N0 - n) / ZELLE);
        for (int r = 0; r < 300; r++)
            for (int j = j0 - r; j <= j0 + r; j++)
                for (int i = i0 - r; i <= i0 + r; i++) {
                    if (Math.max(Math.abs(i - i0), Math.abs(j - j0)) != r || i < 0 || j < 0 || i >= W || j >= H) continue;
                    int c = j * W + i;
                    if (typ[c] == MEER && !sperre[c]) return c;
                }
        return -1;
    }

    static double[] zielPunkt(int zl, Map<String, double[]> namen, double[] tor) {
        return switch (zl) {
            case 0 -> namen.getOrDefault("Gilbert Inlet", new double[]{354903.5, 6506385.5});
            case 1 -> namen.getOrDefault("Crillon Inlet", new double[]{356876.9, 6504362.0});
            case 2 -> namen.getOrDefault("Lituya Bay", new double[]{350641.9, 6502491.1});
            case 3 -> new double[]{0.5 * (tor[0] + tor[2]) - 3000, 0.5 * (tor[1] + tor[3]) - 3000};
            default -> new double[]{E0, N0};
        };
    }

    /** Das Tor (Strecke von La Chaussee Spit bis Harbor Point) aus WELLE.txt, sonst der Wert aus Phase 7a. */
    static double[] tor(File daten) throws IOException {
        File t = new File(new File(daten, WellenNetz.ORDNER), "WELLE.txt");
        if (t.isFile())
            for (String z : Files.readAllLines(t.toPath(), StandardCharsets.UTF_8))
                if (z.startsWith("eingang=")) {
                    String[] p = z.substring(8).trim().split("\\s+");
                    return new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])};
                }
        return new double[]{344591.8, 6501033.9, 346295.0, 6499462.4};
    }

    /** Namen aus daten/namen.tsv: Name → {ost, nord, art-index}; dazu Name + "|" + Art. */
    static Map<String, double[]> namen(File daten) throws IOException {
        Map<String, double[]> m = new HashMap<>();
        File f = new File(daten, "namen.tsv");
        if (!f.isFile()) return m;
        for (String z : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            String[] p = z.split("\t");
            if (p.length < 4 || p[0].equals("name")) continue;
            try {
                double[] v = {Double.parseDouble(p[2]), Double.parseDouble(p[3])};
                m.putIfAbsent(p[0], v);
                m.put(p[0] + "|" + p[1], v);
            } catch (NumberFormatException e) { /* übergehen */ }
        }
        return m;
    }

    /** Der nächste Name einer Art (Stream, Lake, Glacier) bis r m, oder null. */
    static String naechster(Map<String, double[]> namen, String art, double e, double n, double r) {
        String best = null;
        double bd = r;
        for (var x : namen.entrySet()) {
            if (!x.getKey().endsWith("|" + art)) continue;
            double d = Math.hypot(x.getValue()[0] - e, x.getValue()[1] - n);
            if (d < bd) { bd = d; best = x.getKey().substring(0, x.getKey().length() - art.length() - 1); }
        }
        return best;
    }

    /** Gibt jedem GNIS-Bach die Kanten von seiner nächsten Kante (bis 400 m) bis zur Mündung oder bis ein größerer Bach dazukommt. */
    static int namenBaeche(WasserNetz w, Bau b, List<float[]> linien, Map<String, double[]> namen, int nBach) {
        int n = 0;
        for (var x : namen.entrySet()) {
            if (!x.getKey().endsWith("|Stream")) continue;
            String nm = x.getKey().substring(0, x.getKey().length() - 7);
            double[] p = x.getValue();
            int best = -1;
            double bd = 400;
            for (int e = 0; e < nBach; e++) {
                if (w.kEbene[e] != 1 || !(WasserNetz.BACH.equals(w.kArt[e]) || WasserNetz.BACH_EIS.equals(w.kArt[e]))) continue;
                float[] l = linien.get(e);
                for (int q = 0; q < l.length; q += 2) {
                    double d = Math.hypot(l[q] - p[0], l[q + 1] - p[1]);
                    if (d < bd) { bd = d; best = e; }
                }
            }
            if (best < 0) continue;
            n++;
            int e = best;
            while (e >= 0) {
                if (b.kName.get(e) == null) b.kName.set(e, nm);
                int v = w.nach[e];
                if (b.name.get(v) == null && (WasserNetz.MUENDUNG.equals(w.art[v]) || WasserNetz.ZUSAMMENFLUSS.equals(w.art[v]))) b.name.set(v, nm);
                int f = -1;
                for (int j = w.ausAnf[v]; j < w.ausAnf[v + 1]; j++) if (w.kEbene[w.ausK[j]] == 1 && !WasserNetz.MUENDET.equals(w.kArt[w.ausK[j]])) f = w.ausK[j];
                if (f < 0) break;
                // ein größerer Zufluss am Knoten v übernimmt
                boolean groesser = false;
                for (int j = w.einAnf[v]; j < w.einAnf[v + 1]; j++) { int g = w.einK[j]; if (g != e && w.kFlaeche[g] > w.kFlaeche[e]) groesser = true; }
                if (groesser) break;
                e = f;
            }
        }
        // Gebiete nach dem Namen am Auslauf
        for (int i = 0; i < b.n(); i++) {
            if (b.ebene.get(i) != 2 || !WasserNetz.GEBIET.equals(b.art.get(i)) || b.name.get(i) != null) continue;
            int c = b.zelle.get(i);
            for (int k = 0; k < b.n(); k++) if (b.ebene.get(k) == 1 && b.zelle.get(k) == c) {
                String nm = b.name.get(k);
                if (nm == null) for (int j = w.einAnf[k]; j < w.einAnf[k + 1]; j++) if (b.kName.get(w.einK[j]) != null) nm = b.kName.get(w.einK[j]);
                b.name.set(i, nm);
                break;
            }
        }
        return n;
    }

    /** „1,2 km², Mündung in Bucht, Abfluss bei Regen 3,4 m³/s, Fließzeit 0,3 h“ für den Bach mit diesem Namen. */
    static String bach(WasserNetz w, String nm) {
        int best = -1;
        for (int e = 0; e < w.m; e++) if (nm.equals(w.kName[e]) && w.kEbene[e] == 1 && (best < 0 || w.kFlaeche[e] > w.kFlaeche[best])) best = e;
        if (best < 0) return null;
        List<Integer> l = w.traceOut(w.von[best], 1);
        int z = l.get(l.size() - 1);
        double t = 0;
        double fl = 0, q = 0;
        for (int i = 0; i + 1 < l.size(); i++) {
            for (int j = w.ausAnf[l.get(i)]; j < w.ausAnf[l.get(i) + 1]; j++) {
                int e = w.ausK[j];
                if (w.kEbene[e] == 1) { t += w.cost[e]; if (WasserNetz.MUENDET.equals(w.kArt[e])) { fl = w.kFlaeche[e]; q = w.abfluss[e]; } }
            }
        }
        boolean[] in = w.traceIn(w.von[best], 1);
        double tq = 0;
        double[] tt = w.zeitZum(w.von[best], 1);
        for (int i = 0; i < w.n; i++) if (in[i] && !Double.isNaN(tt[i])) tq = Math.max(tq, tt[i]);
        return String.format(Locale.GERMAN, "%.2f km² an der Mündung in %s, Abfluss bei Regen %.1f m³/s, Fließzeit von der Quelle bis zur Mündung %.2f h", fl, w.name[z], q, (tq + t) / 3600);
    }

    /** traceOut vom höchsten Eisknoten eines Gletschers: Ziel und Zeit. */
    static String traceOutGletscher(WasserNetz w, String gn) {
        int best = -1;
        for (int i = 0; i < w.n; i++) if (WasserNetz.EIS.equals(w.art[i]) && gn.equals(w.name[i]) && (best < 0 || w.hoehe[i] > w.hoehe[best])) best = i;
        if (best < 0) return null;
        List<Integer> l = w.traceOut(best, 1);
        double eisT = 0, wasT = 0, eisL = 0;
        for (int i = 0; i + 1 < l.size(); i++)
            for (int j = w.ausAnf[l.get(i)]; j < w.ausAnf[l.get(i) + 1]; j++) {
                int e = w.ausK[j];
                if (w.kEbene[e] != 1) continue;
                if (WasserNetz.GLETSCHER.equals(w.kArt[e])) { eisT += w.cost[e]; eisL += w.laenge[e]; } else wasT += w.cost[e];
            }
        return String.format(Locale.GERMAN, "vom höchsten Eisknoten (%.0f m) %d Knoten bis %s: %.1f km Eis in %.0f Jahren, dann %.2f h Wasser", w.hoehe[best], l.size(), w.name[l.get(l.size() - 1)],
                eisL / 1000, eisT / JAHR_S, wasT / 3600);
    }

    /** Das Ziel (Index) eines Gebiets, über die Ausläufe stromab. */
    static List<Integer> w0(int knoten, List<int[]> gebiete, int[] gk, int[] lab, int[] down, byte[] zielVon) {
        int g = -1;
        for (int q = 0; q < gk.length; q++) if (gk[q] == knoten) g = q;
        for (int s = 0; s < gebiete.size() + 1; s++) {
            int[] x = gebiete.get(g);
            if (x[1] >= 0) return List.of(x[1]);
            g = lab[down[x[0]]];
        }
        throw new IllegalStateException("Gebiet ohne Ziel");
    }

    static void schreibeI32(File f, int[] v) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.asIntBuffer().put(v);
        Files.write(f.toPath(), b.array());
    }

    // ==================================================================== Gletscher

    /**
     * Fließlinien der Gletscher auf 120 m: Richtung aus ITS_LIVE (vx, vy, in UTM gedreht), nur zu einem tieferen Nachbarn
     * (so bleibt es kreisfrei), ohne ITS_LIVE stromab im Gelände; Linien ab EIS_SCHWELLE_KM2 Eisfläche, Knoten an Anfang,
     * Zusammenfluss, Ende und je 600 m. Am Ende führt eine Kante zum Bachknoten oder Ziel, das das Wasser der Endzelle erreicht.
     */
    static String eislinien(Bau b, float[] z, float[] f, byte[] typ, short[] eis, List<Rgi.Gletscher> gl, int[] down, Map<Integer, Integer> knotenBei, int[] lab, int[] gk,
                            byte[] zielVon, File roh, Welle.Log log) throws Exception {
        int k = (int) Math.round(EIS_ZELLE / ZELLE), WI = W / k, HI = H / k, NI = WI * HI;
        float[] zi = new float[NI];
        short[] gi = new short[NI];
        int[] mitte = new int[NI];
        for (int j = 0; j < HI; j++)
            for (int i = 0; i < WI; i++) {
                double s = 0;
                int n = 0;
                for (int jj = 0; jj < k; jj += 2) for (int ii = 0; ii < k; ii += 2) { s += f[(j * k + jj) * W + i * k + ii]; n++; }
                zi[j * WI + i] = (float) (s / n);
                int c = (j * k + k / 2) * W + i * k + k / 2;
                mitte[j * WI + i] = c;
                gi[j * WI + i] = typ[c] == LAND ? eis[c] : 0;
            }
        // Geschwindigkeit
        float[] ve = new float[NI], vn = new float[NI];
        Arrays.fill(ve, Float.NaN);
        Arrays.fill(vn, Float.NaN);
        String quelle = itsLive(roh, WI, HI, ve, vn, gi, log);
        // Richtung
        int[] dn = new int[NI];
        Arrays.fill(dn, -2);
        List<Integer> eisZ = new ArrayList<>();
        for (int c = 0; c < NI; c++) {
            if (gi[c] == 0) continue;
            eisZ.add(c);
            int i = c % WI, j = c / WI;
            double vx = ve[c], vy = vn[c];
            boolean mitV = !Float.isNaN(ve[c]) && Math.hypot(vx, vy) >= 1;
            int best = -1;
            double bw = -1e18;
            for (int d = 0; d < 8; d++) {
                int a = i + DI[d], bb = j + DJ[d];
                if (a < 0 || bb < 0 || a >= WI || bb >= HI) continue;
                int n = bb * WI + a;
                if (zi[n] >= zi[c]) continue;
                double dl = d % 2 == 0 ? 1 : Math.sqrt(2);
                double wv = mitV ? (DI[d] * vx - DJ[d] * vy) / (dl * Math.hypot(vx, vy)) : (zi[c] - zi[n]) / dl;
                if (wv > bw) { bw = wv; best = n; }
            }
            if (best < 0) {
                // Mulde im Mittel: dem Wasser folgen, bis es die Zelle verlässt
                int x = mitte[c];
                while (x >= 0 && typ[x] == LAND && (x / W) / k == j && (x % W) / k == i) x = down[x];
                best = x < 0 || typ[x] != LAND || (x / W) / k >= HI || (x % W) / k >= WI ? -1 : ((x / W) / k) * WI + (x % W) / k;
            }
            dn[c] = best;   // −1: Ende (Meer oder Rand)
        }
        // Kreise (aus dem Wasserweg) auflösen: die tiefste Zelle eines Kreises endet
        byte[] farbe = new byte[NI];
        int kreise = 0;
        for (int c0 : eisZ) {
            if (farbe[c0] != 0) continue;
            List<Integer> pfad = new ArrayList<>();
            int c = c0;
            while (c >= 0 && gi[c] != 0 && farbe[c] == 0) { farbe[c] = 1; pfad.add(c); c = dn[c]; }
            if (c >= 0 && gi[c] != 0 && farbe[c] == 1) {
                int t = c, tief = c;
                do { if (zi[t] < zi[tief]) tief = t; t = dn[t]; } while (t != c);
                dn[tief] = -1;
                kreise++;
            }
            for (int x : pfad) farbe[x] = 2;
        }
        // Fläche stromab: in der Folge des Baums (Zellen ohne Zufluss zuerst)
        int[] ai = new int[NI], zu = new int[NI];
        for (int c : eisZ) { int d = dn[c]; if (d >= 0 && gi[d] != 0) zu[d]++; }
        ArrayDeque<Integer> bereit = new ArrayDeque<>();
        for (int c : eisZ) if (zu[c] == 0) bereit.add(c);
        List<Integer> folge = new ArrayList<>();
        while (!bereit.isEmpty()) {
            int c = bereit.poll();
            folge.add(c);
            ai[c]++;
            int d = dn[c];
            if (d >= 0 && gi[d] != 0) { ai[d] += ai[c]; if (--zu[d] == 0) bereit.add(d); }
        }
        if (folge.size() != eisZ.size()) throw new IllegalStateException("Eislinien: Kreis übrig");
        eisZ = folge;
        int S = (int) Math.round(EIS_SCHWELLE_KM2 * 1e6 / (EIS_ZELLE * EIS_ZELLE));
        byte[] inS = new byte[NI];
        for (int c : eisZ) if (ai[c] >= S) { int d = dn[c]; if (d >= 0 && gi[d] != 0 && ai[d] >= S && inS[d] < 100) inS[d]++; }
        Map<Integer, Integer> kb = new HashMap<>();
        double[] vBetrag = new double[NI];
        for (int c = 0; c < NI; c++) vBetrag[c] = Float.isNaN(ve[c]) ? EIS_V_OHNE : Math.max(1, Math.hypot(ve[c], vn[c]));
        List<Integer> start = new ArrayList<>();
        java.util.Set<Integer> startS = new java.util.HashSet<>();
        for (int c : eisZ) {
            if (ai[c] < S) continue;
            int d = dn[c];
            boolean ende = d < 0 || gi[d] == 0 || ai[d] < S;
            if (inS[c] == 1 && !ende) continue;
            kb.put(c, eisKnoten(b, c, mitte, z, ai, gl, gi, lab, gk));
            start.add(c);
            startS.add(c);
        }
        int nl = 0;
        double laenge = 0;
        for (int a : start) {
            int ka = kb.get(a);
            int c = a;
            double L = 0, tt = 0;
            boolean weiter = true;
            while (weiter) {
                int d = dn[c];
                boolean ende = d < 0 || gi[d] == 0 || ai[d] < S;
                if (ende) {
                    // zum Wasser: von der Endzelle (oder der Zelle danach) mit dem Abfluss bis zu einem Bachknoten oder dem Meer
                    int w10 = mitte[d >= 0 ? d : c];
                    int ziel = wasserZiel(w10, down, typ, knotenBei, zielVon);
                    double dl = Math.hypot(b.ost.get(ka) - b.ost.get(ziel), b.nord.get(ka) - b.nord.get(ziel));
                    if (L > 0) throw new IllegalStateException("Eislinie ohne Endknoten");
                    b.kante(1, WasserNetz.MUENDET, ka, ziel, Math.max(dl, 1), 0, ai[c] * EIS_ZELLE * EIS_ZELLE / 1e6, 0, 1, Math.max(dl, 1), null);
                    nl++;
                    break;
                }
                double dl = (d % WI != c % WI && d / WI != c / WI) ? EIS_ZELLE * Math.sqrt(2) : EIS_ZELLE;
                L += dl;
                tt += dl / (0.5 * (vBetrag[c] + vBetrag[d]) / JAHR_S);
                Integer kd = kb.get(d);
                if (kd == null && L >= EIS_STUETZ_M) { kd = eisKnoten(b, d, mitte, z, ai, gl, gi, lab, gk); kb.put(d, kd); }
                if (kd != null) {
                    double gef = Math.max(0, (b.hoehe.get(ka) - b.hoehe.get(kd)) / L);
                    b.kante(1, WasserNetz.GLETSCHER, ka, kd, L, gef, ai[c] * EIS_ZELLE * EIS_ZELLE / 1e6, 0, L / tt, tt, b.name.get(ka));
                    laenge += L;
                    nl++;
                    boolean stuetz = !startS.contains(d);
                    if (!stuetz) break;
                    ka = kd;
                    L = 0;
                    tt = 0;
                }
                c = d;
            }
        }
        long mitV = 0;
        for (int c : eisZ) if (!Float.isNaN(ve[c])) mitV++;
        log.log("  Gletscher: %d Eiszellen zu %.0f m (%d mit Geschwindigkeit, %d Kreise gelöst), %d Eisknoten, %d Kanten, %.0f km Fließlinien ab %.0f km² – %s", eisZ.size(), EIS_ZELLE, mitV, kreise,
                kb.size(), nl, laenge / 1000, EIS_SCHWELLE_KM2, quelle);
        return quelle;
    }

    static int eisKnoten(Bau b, int c, int[] mitte, float[] z, int[] ai, List<Rgi.Gletscher> gl, short[] gi, int[] lab, int[] gk) {
        int m = mitte[c];
        String nm = gi[c] > 0 && gi[c] <= gl.size() ? leer(gl.get(gi[c] - 1).name()) : null;
        int k = b.knoten(1, WasserNetz.EIS, E0 + (m % W + 0.5) * ZELLE, N0 - (m / W + 0.5) * ZELLE, z[m], ai[c] * EIS_ZELLE * EIS_ZELLE / 1e6, 1, nm, m);
        return k;
    }

    /** Der Knoten, den Wasser von Zelle c aus erreicht: der erste Bachknoten stromab oder das Ziel. */
    static int wasserZiel(int c, int[] down, byte[] typ, Map<Integer, Integer> knotenBei, byte[] zielVon) {
        int x = c;
        for (int s = 0; s < W * H; s++) {
            if (typ[x] == MEER) return zielVon[x];
            Integer k = knotenBei.get(x);
            if (k != null) return k;
            int d = down[x];
            if (d < 0) return 4;
            x = d;
        }
        return 4;
    }

    /**
     * ITS_LIVE (download/gletscher/itslive_geschwindigkeit/*_vx_*.tif, *_vy_*.tif): je Eiszelle die Geschwindigkeit in UTM
     * (m/a), gedreht mit der Ableitung der Abbildung. Gibt die Quelle als Text zurück.
     */
    static String itsLive(File roh, int WI, int HI, float[] ve, float[] vn, short[] gi, Welle.Log log) throws IOException {
        File vxF = null, vyF = null;
        for (File f : dateien(roh, "gletscher/itslive_geschwindigkeit", ".tif")) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.contains("_vx")) vxF = f;
            if (n.contains("_vy")) vyF = f;
        }
        if (vxF == null || vyF == null) {
            log.log("  HINWEIS: ITS_LIVE fehlt (download/gletscher/itslive_geschwindigkeit: python tools/kartenmaterial/kartenmaterial.py laden --quellen itslive_geschwindigkeit); Richtung aus dem Gelände, %.0f m/a [G]", EIS_V_OHNE);
            return String.format(Locale.ROOT, "ohne ITS_LIVE: Richtung aus dem Gelände, %.0f m/a [G]", EIS_V_OHNE);
        }
        try (GeoTiff tx = GeoTiff.open(vxF); GeoTiff ty = GeoTiff.open(vyF)) {
            Abbildung ab = Abbildung.von(tx);
            double[] p = new double[2], q = new double[2], r = new double[2];
            int ok = 0;
            for (int c = 0; c < WI * HI; c++) {
                if (gi[c] == 0) continue;
                double e = E0 + (c % WI + 0.5) * EIS_ZELLE, n = N0 - (c / WI + 0.5) * EIS_ZELLE;
                ab.vonUtm(e, n, p);
                double fx = probe(tx, p[0], p[1]), fy = probe(ty, p[0], p[1]);
                if (Double.isNaN(fx) || Double.isNaN(fy)) continue;
                ab.vonUtm(e + 100, n, q);
                ab.vonUtm(e, n + 100, r);
                // J = d(x, y)/d(e, n); v_utm = J⁻¹ · v_datei
                double a11 = (q[0] - p[0]) / 100, a21 = (q[1] - p[1]) / 100, a12 = (r[0] - p[0]) / 100, a22 = (r[1] - p[1]) / 100;
                double det = a11 * a22 - a12 * a21;
                ve[c] = (float) ((a22 * fx - a12 * fy) / det);
                vn[c] = (float) ((-a21 * fx + a11 * fy) / det);
                ok++;
            }
            return String.format(Locale.ROOT, "ITS_LIVE v02 (%s, %s; EPSG %d), %d Eiszellen mit Wert", vxF.getName(), vyF.getName(), tx.epsg, ok);
        }
    }

    /** Bilinear aus einer GeoTIFF-Datei (Koordinaten der Datei), NaN außerhalb oder ohne Wert. */
    static double probe(GeoTiff t, double x, double y) throws IOException {
        double ci = t.col(x), cj = t.row(y);
        int i = (int) Math.floor(ci), j = (int) Math.floor(cj);
        if (i < 0 || j < 0 || i + 1 >= t.width || j + 1 >= t.height) return Double.NaN;
        double fx = ci - i, fy = cj - j, s = 0, ws = 0;
        for (int q = 0; q < 4; q++) {
            int a = i + (q & 1), b = j + (q >> 1);
            double v = t.get(a, b, 0);
            if (Double.isNaN(v) || v == t.nodata || Math.abs(v) > 1e5) continue;
            double w = ((q & 1) == 1 ? fx : 1 - fx) * ((q >> 1) == 1 ? fy : 1 - fy);
            s += w * v;
            ws += w;
        }
        return ws < 0.25 ? Double.NaN : s / ws;
    }

    /** Dateien unter roh/pfad mit dieser Endung (rekursiv, sortiert). */
    static List<File> dateien(File roh, String pfad, String ende) throws IOException {
        File d = new File(roh, pfad);
        List<File> l = new ArrayList<>();
        if (!d.isDirectory()) return l;
        try (var st = Files.walk(d.toPath())) {
            st.filter(Files::isRegularFile).map(java.nio.file.Path::toFile).filter(x -> x.getName().toLowerCase(Locale.ROOT).endsWith(ende)).sorted().forEach(l::add);
        }
        return l;
    }

    /** Die Szene in Grad (West, Süd, Ost, Nord), umschließend, mit 0,05° Rand. */
    static double[] huelle() {
        double w = 1e9, s = 1e9, o = -1e9, n = -1e9;
        for (int k = 0; k <= 40; k++)
            for (double[] en : new double[][]{{E0 + k * W * ZELLE / 40, N0}, {E0 + k * W * ZELLE / 40, N0 - H * ZELLE}, {E0, N0 - k * H * ZELLE / 40}, {E0 + W * ZELLE, N0 - k * H * ZELLE / 40}}) {
                double[] ll = com.dan.lituya.scene.Geo.UTM8N.inverse(en[0], en[1]);
                s = Math.min(s, ll[0]); n = Math.max(n, ll[0]); w = Math.min(w, ll[1]); o = Math.max(o, ll[1]);
            }
        return new double[]{w - 0.05, s - 0.05, o + 0.05, n + 0.05};
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        File daten = new File(a.length > 0 ? a[0] : "daten"), roh = new File(a.length > 1 ? a[1] : "download");
        rechnen(daten, roh, (f, x) -> System.out.println(String.format(Locale.GERMAN, f, x)));
    }
}
