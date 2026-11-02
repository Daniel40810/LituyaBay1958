package com.dan.lituya.tools;

import com.dan.lituya.scene.EreignisDaten;
import com.dan.lituya.scene.Gezeiten;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.SeeNetz;
import com.dan.lituya.scene.WellenNetz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Schritt {@code see} der Aufbereitung (Phase 15a, Werkbuch 7.3): {@code LB_NET_SEE} aus dem Netz der Welle
 * ({@code daten/netz/welle}) nach {@code daten/netz/see} ({@link SeeNetz}), dazu „Wer hätte entkommen können?“: je
 * Ankerplatz und Abfahrt Δ = 0 … 180 s die Flucht vor der Front ({@code flucht.tsv}), die späteste Abfahrt, mit der es
 * noch geht, und ihr Weg ({@code wege.tsv}), Gezeiten des Abends ({@code gezeiten.tsv}), Kopf {@code SEE.txt}.
 * <p>Aufruf: {@code See [daten] [lauf]} (Lauf für die Front aus der Vorrechnung, Vorgabe kette; fehlt er, nur linear).
 */
public final class See {
    private See() { }

    public static void rechnen(Rohdaten r) throws Exception { rechnen(r.daten, "kette", r::log); }

    public static SeeNetz rechnen(File daten, String lauf, Welle.Log log) throws Exception {
        long t0 = System.nanoTime();
        WellenNetz w = WellenNetz.lesen(daten);
        SeeNetz s = new SeeNetz();
        // Tor aus WELLE.txt (eingang)
        double[] tor = null;
        for (String z : w.text.split("\\R")) if (z.startsWith("eingang=")) {
            String[] p = z.substring(8).trim().split("\\s+");
            tor = new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])};
        }
        if (tor == null) throw new IllegalStateException("daten/netz/welle/WELLE.txt ohne eingang (ant aufbereitung -Dschritte=welle)");
        s.torE = 0.5 * (tor[0] + tor[2]);
        s.torN = 0.5 * (tor[1] + tor[3]);
        double dx = tor[2] - tor[0], dn = tor[3] - tor[1], l = Math.hypot(dx, dn);
        double ne = -dn / l, nn = dx / l;
        if (nn > 0) { ne = -ne; nn = -nn; }               // hinaus = nach Südwest
        s.hinE = ne;
        s.hinN = nn;
        long q = SceneClock.BEBEN.toEpochMilli();
        s.gezeit = Gezeiten.hoehe(q) - Gezeiten.MSL_NAVD88;
        double mindest = SeeNetz.TIEFGANG + SeeNetz.RESERVE - s.gezeit;   // Tiefe unter Mittelwasser, die es braucht

        // Knoten wählen
        List<Integer> sel = new ArrayList<>();
        int[] knotenJeZelle = new int[WellenNetz.BREITE * WellenNetz.HOEHE];
        for (int k = 0; k < w.n; k++) {
            if (w.einschlag[k] || !(w.tiefe1940[k] >= mindest)) continue;
            double e = WellenNetz.ost(w.i[k]), n = WellenNetz.nord(w.j[k]);
            double dt = Math.hypot(e - s.torE, n - s.torN), aus = (e - s.torE) * s.hinE + (n - s.torN) * s.hinN;
            int st = dt <= SeeNetz.NAH ? 2 : aus > 0 ? 5 : 10;
            if (w.i[k] % st != 0 || w.j[k] % st != 0) continue;
            sel.add(k);
        }
        s.n = sel.size();
        s.ost = new double[s.n]; s.nord = new double[s.n]; s.tiefe = new double[s.n]; s.abstand = new int[s.n]; s.faktorKnoten = new double[s.n];
        s.draussen = new double[s.n]; s.frontLinear = new double[s.n]; s.frontEreignis = new double[s.n];
        int[] wk = new int[s.n];
        for (int i = 0; i < s.n; i++) {
            int k = sel.get(i);
            wk[i] = k;
            s.ost[i] = WellenNetz.ost(w.i[k]); s.nord[i] = WellenNetz.nord(w.j[k]); s.tiefe[i] = w.tiefe1940[k];
            double dt = Math.hypot(s.ost[i] - s.torE, s.nord[i] - s.torN);
            s.draussen[i] = (s.ost[i] - s.torE) * s.hinE + (s.nord[i] - s.torN) * s.hinN;
            s.abstand[i] = dt <= SeeNetz.NAH ? 40 : s.draussen[i] > 0 ? 100 : 200;
            s.faktorKnoten[i] = Math.exp(-Math.pow(Math.max(0, Math.abs(s.draussen[i])) / SeeNetz.STROM_BREITE, 2)) * Math.exp(-Math.pow(dt / (3 * SeeNetz.STROM_BREITE), 2));
            s.frontLinear[i] = w.ankunft[1][k] + WellenNetz.START_S;
            knotenJeZelle[w.j[k] * WellenNetz.BREITE + w.i[k]] = i + 1;
        }
        // Front aus der Vorrechnung
        java.util.Arrays.fill(s.frontEreignis, Double.NaN);
        File ld = EreignisDaten.ordner(daten, lauf);
        String fq = "keine (kein Lauf " + lauf + ")";
        if (new File(ld, "EREIGNIS.txt").isFile()) {
            try (EreignisDaten.Leser le = new EreignisDaten.Leser(ld)) {
                int gesetzt = 0;
                for (int i = 0; i < s.n; i++) {
                    for (int k = le.gitter.size() - 1; k >= 0; k--) {
                        EreignisDaten.Gitter g = le.gitter.get(k);
                        int id = g.index(s.ost[i], s.nord[i]);
                        if (id < 0) continue;
                        float[] a = an(le, ld, k);
                        if (a == null) break;
                        s.frontEreignis[i] = a[id];
                        if (!Float.isNaN(a[id])) gesetzt++;
                        break;
                    }
                }
                fq = String.format(Locale.ROOT, "%s (ankunft_g*.r32, %d von %d Knoten)", lauf, gesetzt, s.n);
            }
        }
        s.frontQuelle = fq;

        // Kanten: Nachbarn bis 1,5 Abstände, gerichtet in beiden Richtungen, nur über tiefes Wasser
        double[] tiefe = new double[WellenNetz.BREITE * WellenNetz.HOEHE];
        java.util.Arrays.fill(tiefe, Double.NaN);
        for (int k = 0; k < w.n; k++) if (!w.einschlag[k]) tiefe[w.j[k] * WellenNetz.BREITE + w.i[k]] = w.tiefe1940[k];
        Set<Long> paare = new HashSet<>();
        List<int[]> ka = new ArrayList<>();
        for (int a = 0; a < s.n; a++) {
            double r = 1.5 * s.abstand[a];
            int ia = w.i[wk[a]], ja = w.j[wk[a]], qq = (int) Math.ceil(r / WellenNetz.ZELLE);
            for (int jj = ja - qq; jj <= ja + qq; jj++)
                for (int ii = ia - qq; ii <= ia + qq; ii++) {
                    if (ii < 0 || jj < 0 || ii >= WellenNetz.BREITE || jj >= WellenNetz.HOEHE) continue;
                    int b = knotenJeZelle[jj * WellenNetz.BREITE + ii] - 1;
                    if (b < 0 || b == a) continue;
                    if (Math.hypot(s.ost[a] - s.ost[b], s.nord[a] - s.nord[b]) > r + 1e-6) continue;
                    long key = Math.min(a, b) * 1_000_000L + Math.max(a, b);
                    if (paare.contains(key)) continue;
                    if (!frei(tiefe, ia, ja, ii, jj, mindest)) continue;
                    paare.add(key);
                    ka.add(new int[]{a, b});
                    ka.add(new int[]{b, a});
                }
        }
        s.m = ka.size();
        s.von = new int[s.m]; s.nach = new int[s.m]; s.laenge = new double[s.m]; s.re = new double[s.m]; s.rn = new double[s.m]; s.faktor = new double[s.m];
        for (int e = 0; e < s.m; e++) {
            int a = ka.get(e)[0], b = ka.get(e)[1];
            s.von[e] = a; s.nach[e] = b;
            double ddx = s.ost[b] - s.ost[a], ddn = s.nord[b] - s.nord[a], L = Math.hypot(ddx, ddn);
            s.laenge[e] = L; s.re[e] = ddx / L; s.rn[e] = ddn / L;
            s.faktor[e] = 0.5 * (s.faktorKnoten[a] + s.faktorKnoten[b]);
        }
        s.schreiben(daten);

        // Gezeiten des Abends und der Strom im Tor
        Gezeiten g = new Gezeiten();
        List<String> gz = new ArrayList<>();
        gz.add("ZEIT_PST\tT_SEIT_STOSS_S\tHOEHE_NAVD88\tSTROM_TOR");
        for (int min = -6 * 60; min <= 3 * 60; min += 10) {
            double ms = q + min * 60_000.0;
            gz.add(String.format(Locale.ROOT, "%s\t%d\t%.3f\t%.3f", pst(ms), min * 60, Gezeiten.hoehe(ms), g.stromTor(ms)));
        }
        File d = new File(daten, SeeNetz.ORDNER);
        Files.write(new File(d, "gezeiten.tsv").toPath(), gz, StandardCharsets.UTF_8);
        List<String> ex = new ArrayList<>();
        for (double[] x : Gezeiten.extreme(q - 12 * 3_600_000.0, q + 12 * 3_600_000.0))
            ex.add(String.format(Locale.GERMAN, "%s %s %.2f m NAVD88", x[2] > 0 ? "HW" : "NW", pst(x[0]), x[1]));

        // Flucht
        double[] strom = SeeNetz.stromTabelle();
        List<String> fl = new ArrayList<>(), wg = new ArrayList<>(), kopf = new ArrayList<>();
        fl.add("BOOT\tFRONT\tABFAHRT_S\tENTKOMMEN\tANKUNFT_S\tRESERVE_S\tZIEL_NODE_ID");
        wg.add("BOOT\tFRONT\tABFAHRT_S\tSEQ\tNODE_ID\tOST\tNORD\tANKUNFT_S\tFRONT_S");
        String[] fronten = s.frontEreignis != null && fq.startsWith(lauf) ? new String[]{"LINEAR", "EREIGNIS"} : new String[]{"LINEAR"};
        log.log("LB_NET_SEE: %,d Knoten (40 m am Tor, 100 m See, 200 m Bucht), %,d Kanten gerichtet; Tiefe ≥ %.2f m unter MW (Gezeit %+.2f m über MW beim Stoß)", s.n, s.m, mindest, s.gezeit);
        log.log("  Front linear (T_1940 + 105 s), aus der Vorrechnung: %s", fq);
        for (Object[] p : SeeNetz.PLAETZE) {
            String boot = (String) p[0];
            int st = s.knotenBei((double) p[1], (double) p[2]);
            for (String fr : fronten) {
                double[] front = fr.equals("LINEAR") ? s.frontLinear : s.frontEreignis;
                StringBuilder b = new StringBuilder();
                for (double delta : SeeNetz.ABFAHRT) {
                    SeeNetz.Flucht f = s.flucht(st, delta, front, strom);
                    fl.add(String.format(Locale.ROOT, "%s\t%s\t%.0f\t%s\t%s\t%s\t%s", boot, fr, delta, f.entkommen() ? "J" : "N",
                            f.entkommen() ? String.format(Locale.ROOT, "%.1f", f.ankunft()[f.ziel()]) : "", f.entkommen() && !Double.isInfinite(f.reserve()) ? String.format(Locale.ROOT, "%.1f", f.reserve()) : "",
                            f.entkommen() ? String.valueOf(f.ziel() + 1) : ""));
                    b.append(f.entkommen() ? "J" : "·");
                }
                double sp = s.spaetesteAbfahrt(st, front, strom, -3600, 1200);
                SeeNetz.Flucht f = Double.isNaN(sp) ? null : s.flucht(st, sp, front, strom);
                if (f != null) {
                    int[] wgk = s.weg(f);
                    for (int i = 0; i < wgk.length; i++) {
                        int v = wgk[i];
                        wg.add(String.format(Locale.ROOT, "%s\t%s\t%.0f\t%d\t%d\t%.1f\t%.1f\t%.1f\t%s", boot, fr, sp, i + 1, v + 1, s.ost[v], s.nord[v], f.ankunft()[v],
                                Double.isNaN(front[v]) ? "" : String.format(Locale.ROOT, "%.1f", front[v])));
                    }
                }
                String zeile = String.format(Locale.GERMAN, "%-7s Front %-8s Abfahrt 0 … 180 s: %s · späteste Abfahrt %s", boot, fr.toLowerCase(Locale.ROOT), b,
                        Double.isNaN(sp) ? "keine (auch eine Stunde vorher nicht)" : sp >= 1200 ? "nach 1200 s (die Front kommt nicht vor)"
                                : String.format(Locale.GERMAN, "%+.0f s nach dem Stoß, draußen nach %.0f s, %s", sp, f.ankunft()[f.ziel()],
                                        Double.isInfinite(f.reserve()) ? "die Front erreicht das Ziel in der Rechnung nicht" : String.format(Locale.GERMAN, "Reserve %.0f s", f.reserve())));
                kopf.add(String.format(Locale.ROOT, "flucht.%s.%s=%s", boot.toLowerCase(Locale.ROOT), fr.toLowerCase(Locale.ROOT), Double.isNaN(sp) ? "keine" : String.format(Locale.ROOT, "%.0f", sp)));
                log.log("  %s", zeile);
            }
        }
        Files.write(new File(d, "flucht.tsv").toPath(), fl, StandardCharsets.UTF_8);
        Files.write(new File(d, "wege.tsv").toPath(), wg, StandardCharsets.UTF_8);
        // Orte
        List<String> o = new ArrayList<>();
        o.add("ORT\tOST\tNORD\tNODE_ID");
        for (Object[] p : SeeNetz.PLAETZE) o.add(String.format(Locale.ROOT, "%s\t%.1f\t%.1f\t%d", p[0], (double) p[1], (double) p[2], s.knotenBei((double) p[1], (double) p[2]) + 1));
        o.add(String.format(Locale.ROOT, "Tor\t%.1f\t%.1f\t%d", s.torE, s.torN, s.knotenBei(s.torE, s.torN) + 1));
        Files.write(new File(d, "orte.tsv").toPath(), o, StandardCharsets.UTF_8);

        List<String> t = new ArrayList<>();
        t.add("# LB_NET_SEE als Datei (Phase 15a, Werkbuch 7.3): Fahrwasser eines Trollers, gerichtet, Kosten nach der Zeit (com.dan.lituya.scene.SeeNetz)");
        t.add(String.format(Locale.ROOT, "knoten=%d", s.n));
        t.add(String.format(Locale.ROOT, "kanten=%d", s.m));
        t.add(String.format(Locale.ROOT, "tor=%.1f %.1f %.5f %.5f", s.torE, s.torN, s.hinE, s.hinN));
        t.add(String.format(Locale.ROOT, "fahrt=%.3f m/s (8 kn) [G]", SeeNetz.FAHRT));
        t.add(String.format(Locale.ROOT, "tiefe_mindest=%.2f m unter MW (Tiefgang %.2f + %.2f, Gezeit beim Stoß %+.2f m über MW)", mindest, SeeNetz.TIEFGANG, SeeNetz.RESERVE, s.gezeit));
        t.add(String.format(Locale.ROOT, "gezeit_msl=%.3f", s.gezeit));
        t.add("gezeit_stoss=" + g.zeile(q));
        t.add("gezeit_quelle=" + Gezeiten.QUELLE);
        t.add("gezeit_extreme=" + String.join("; ", ex));
        t.add("front_linear=T_1940 aus daten/netz/welle + 105 s");
        t.add("front_ereignis=" + fq);
        t.add(String.format(Locale.ROOT, "draussen=%.0f m jenseits des Tors", SeeNetz.DRAUSSEN));
        t.addAll(kopf);
        t.add("pruefsumme=" + WellenNetz.pruefsumme(d));
        Files.write(new File(d, "SEE.txt").toPath(), t, StandardCharsets.UTF_8);
        log.log("  Gezeit beim Stoß: %s; %s", g.zeile(q), String.join("; ", ex));
        log.log("  nach %s in %.1f s", d, (System.nanoTime() - t0) / 1e9);
        return SeeNetz.lesen(daten);
    }

    private static final java.util.Map<String, float[]> AN = new java.util.HashMap<>();

    static float[] an(EreignisDaten.Leser le, File ld, int k) {
        return AN.computeIfAbsent(ld + "#" + k, x -> {
            try { return le.raster(ld, "ankunft", k); } catch (Exception e) { return null; }
        });
    }

    /** Jede Zelle auf der Linie (Schritte zu 10 m) hat Wasser mit Tiefe ≥ mindest. */
    static boolean frei(double[] tiefe, int ia, int ja, int ib, int jb, double mindest) {
        double L = Math.hypot(ib - ia, jb - ja) * WellenNetz.ZELLE;
        int n = Math.max(1, (int) Math.ceil(L / 10));
        for (int s = 0; s <= n; s++) {
            double f = s / (double) n;
            int i = (int) Math.round(ia + (ib - ia) * f), j = (int) Math.round(ja + (jb - ja) * f);
            double t = tiefe[j * WellenNetz.BREITE + i];
            if (!(t >= mindest)) return false;
        }
        return true;
    }

    static String pst(double ms) {
        return java.time.Instant.ofEpochMilli((long) ms).atOffset(java.time.ZoneOffset.ofHours(-8)).format(java.time.format.DateTimeFormatter.ofPattern("d.M. HH:mm"));
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        File daten = new File(a.length > 0 ? a[0] : "daten");
        rechnen(daten, a.length > 1 ? a[1] : "kette", (f, x) -> System.out.println(String.format(Locale.GERMAN, f, x)));
    }
}
