package com.dan.lituya.tools;

import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.WellenNetz;
import com.dan.route.TravelGrid;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Schritt {@code welle} der Aufbereitung (Phase 7a, Werkbuch 7.1, F21, F22): das Netz der Welle als Datei-Abbild
 * {@code daten/netz/welle/} für {@code LB_NET_WELLE}, die App und die Prüfung ({@link WellenNetz} liest es).
 * <ul>
 * <li><b>Fläche</b> (Entscheidung 7a): die Bucht innerhalb des Eingangs und die See bis 1 km vor ihm. Der Eingang ist
 * die Strecke La Chaussee Spit – Harbor Point (GNIS), auf beiden Seiten um 300 m verlängert; das Wasser der Bucht ist
 * die Zusammenhangskomponente der Einschlaglinie ohne diese Strecke. Dazu kommen die Zellen der Strecke selbst und das
 * Wasser bis 1 000 m von ihr, das außen an ihr hängt.</li>
 * <li><b>Knoten</b>: Zellen zu 20 m (Raster wie {@code daten/grund}) mit Tiefe unter Mittelwasser (+0,59 m NAVD88 [G])
 * heute, 1940 und 1959 (heute, wo die Vermessung keine Lotungen hat), Amplitude a = 30 + 122·e^(−d/1 500) m [G] mit d
 * dem Abstand vom Einschlag. Art {@code WASSER} oder {@code EINSCHLAG} (die Einschlaglinie, {@link Isochronenbild}).
 * Dazu der virtuelle Knoten {@code QUELLE}.</li>
 * <li><b>Kanten</b>: 16 Nachbarn ({@code route.TravelGrid.Stencil.N16}), ungerichtet, Länge und vier Kosten (s):
 * {@code T_HEUTE}, {@code T_1940}, {@code T_1959} linear mit c = √(g h), {@code T_NICHTLINEAR} mit c = √(g (h + a))
 * über dem Grund heute. Tiefen unter 0,5 m zählen als 0,5 m, damit alle vier Kosten auf demselben Netz gelten. Dazu je
 * Knoten der Einschlaglinie eine Kante von {@code QUELLE} mit Kosten 0.</li>
 * <li><b>Ankunft</b> je Knoten und Kostenart mit {@code TravelGrid} auf genau diesen Kanten (die Zweitrechnung, gegen die
 * LOD in {@code ant netzdb} und {@code ant netzvergleich} rechnet), Vorgänger im Baum heute linear.</li>
 * <li><b>Prüfwege</b>: Orte und jeder 997. Knoten mit den vier Ankunftszeiten.</li>
 * </ul>
 */
public final class Welle {
    private Welle() { }

    /** Wie weit die See vor dem Eingang zum Netz gehört (m), und wie weit der Eingang über die GNIS-Punkte reicht. */
    public static final double SEE = 1000, EINGANG_DAZU = 300;
    /** Orte für die Prüfwege (wie {@link WellenNetz#ORTE}). */
    public static final Object[][] ORTE = WellenNetz.ORTE;

    /** Ergebnis für Protokoll und Prüfung. */
    public record Stand(int knoten, int kanten, int einschlag, int bucht, int see, double[] eingang, String summe) { }

    public static Stand rechnen(Rohdaten r) throws Exception { return rechnen(r.daten, r::log); }

    public interface Log { void log(String f, Object... a); }

    public static Stand rechnen(File daten, Log log) throws Exception {
        long t0 = System.nanoTime();
        int W = WellenNetz.BREITE, H = WellenNetz.HOEHE;
        float[] heute = Isochronenbild.heute20(daten), g40 = Isochronenbild.mitLotung(heute, daten, "1940_20m.r32"),
                g59 = Isochronenbild.mitLotung(heute, daten, "1959_20m.r32");
        // Wasser heute
        boolean[] offen = new boolean[W * H];
        for (int k = 0; k < offen.length; k++) offen[k] = Float.isFinite(heute[k]) && Isochronenbild.MSL - heute[k] >= Isochronenbild.MIN_TIEFE;
        // Einschlaglinie (wie Isochronenbild)
        TravelGrid probe = new TravelGrid(W, H, 0, 0, WellenNetz.ZELLE);
        for (int k = 0; k < offen.length; k++) probe.speed[k] = offen[k] ? 1 : 0;
        int[] p = Isochronenbild.punkt(heute);
        List<TravelGrid.Source> q = Isochronenbild.quellen(probe, p);
        // Eingang: Strecke Chaussee – Harbor Point, verlängert
        double ce = Site.CHAUSSEE.e(), cn = Site.CHAUSSEE.n(), he = Site.HARBOR_POINT.e(), hn = Site.HARBOR_POINT.n();
        double dl = Math.hypot(he - ce, hn - cn), ue = (he - ce) / dl, un = (hn - cn) / dl;
        double ae = ce - ue * EINGANG_DAZU, an = cn - un * EINGANG_DAZU, be = he + ue * EINGANG_DAZU, bn = hn + un * EINGANG_DAZU;
        boolean[] tor = new boolean[W * H];
        for (int j = 0; j < H; j++)
            for (int i = 0; i < W; i++)
                tor[j * W + i] = abstand(WellenNetz.ost(i), WellenNetz.nord(j), ae, an, be, bn) <= WellenNetz.ZELLE * 0.75;
        // Bucht: Komponente der Einschlaglinie ohne das Tor
        boolean[] bucht = new boolean[W * H];
        int nb = flut(offen, tor, bucht, q, W, H);
        // Tor und See bis 1 km, die am Tor hängen
        boolean[] netz = bucht.clone();
        boolean[] see = new boolean[W * H];
        List<TravelGrid.Source> torZellen = new ArrayList<>();
        for (int k = 0; k < offen.length; k++)
            if (offen[k] && !bucht[k] && abstand(WellenNetz.ost(k % W), WellenNetz.nord(k / W), ae, an, be, bn) <= SEE) {
                see[k] = true;
                if (tor[k]) torZellen.add(new TravelGrid.Source(k % W, k / W, 0));
            }
        boolean[] seeNetz = new boolean[W * H];
        flut(see, new boolean[W * H], seeNetz, torZellen, W, H);
        for (int k = 0; k < netz.length; k++) netz[k] |= seeNetz[k];
        // Knoten
        int[] id = new int[W * H];
        int n = 0;
        for (int k = 0; k < netz.length; k++) if (netz[k]) id[k] = ++n;
        int quelle = n + 1;
        boolean[] einschlag = new boolean[W * H];
        double qe = 0, qn = 0;
        for (TravelGrid.Source s : q) { einschlag[s.j() * W + s.i()] = true; qe += WellenNetz.ost(s.i()); qn += WellenNetz.nord(s.j()); }
        qe /= q.size(); qn /= q.size();
        double pe = WellenNetz.ost(p[0]), pn = WellenNetz.nord(p[1]);
        float[] t40 = new float[W * H], t59 = new float[W * H], th = new float[W * H], amp = new float[W * H];
        TravelGrid[] g = new TravelGrid[4];
        for (int a = 0; a < 4; a++) { g[a] = new TravelGrid(W, H, 0, 0, WellenNetz.ZELLE); g[a].stencil = TravelGrid.Stencil.N16; }
        for (int k = 0; k < netz.length; k++) {
            if (!netz[k]) continue;
            th[k] = tiefe(heute[k]);
            t40[k] = tiefe(g40[k]);
            t59[k] = tiefe(g59[k]);
            amp[k] = (float) (30 + 122 * Math.exp(-Math.hypot(WellenNetz.ost(k % W) - pe, WellenNetz.nord(k / W) - pn) / 1500));
            g[0].speed[k] = (float) TravelGrid.waveSpeed(th[k], 0);
            g[1].speed[k] = (float) TravelGrid.waveSpeed(t40[k], 0);
            g[2].speed[k] = (float) TravelGrid.waveSpeed(t59[k], 0);
            g[3].speed[k] = (float) TravelGrid.waveSpeed(th[k], amp[k]);
        }
        File dir = new File(daten, WellenNetz.ORDNER);
        dir.mkdirs();
        // knoten.tsv
        try (BufferedWriter w = Files.newBufferedWriter(new File(dir, "knoten.tsv").toPath(), StandardCharsets.UTF_8)) {
            w.write("NODE_ID\tART\tI\tJ\tOST\tNORD\tTIEFE_HEUTE\tTIEFE_1940\tTIEFE_1959\tAMPLITUDE\n");
            for (int k = 0; k < netz.length; k++)
                if (netz[k])
                    w.write(String.format(Locale.ROOT, "%d\t%s\t%d\t%d\t%.1f\t%.1f\t%.2f\t%.2f\t%.2f\t%.2f%n", id[k], einschlag[k] ? "EINSCHLAG" : "WASSER", k % W, k / W,
                            WellenNetz.ost(k % W), WellenNetz.nord(k / W), th[k], t40[k], t59[k], amp[k]));
            w.write(String.format(Locale.ROOT, "%d\tQUELLE\t-1\t-1\t%.1f\t%.1f\t0\t0\t0\t0%n", quelle, qe, qn));
        }
        // kanten.tsv: Gitter (Reihenfolge von TravelGrid.edges), dann QUELLE
        int[] nk = {0, 0};
        try (BufferedWriter w = Files.newBufferedWriter(new File(dir, "kanten.tsv").toPath(), StandardCharsets.UTF_8)) {
            w.write("LINK_ID\tSTART_NODE_ID\tEND_NODE_ID\tLAENGE\tT_HEUTE\tT_1940\tT_1959\tT_NICHTLINEAR\n");
            StringBuilder b = new StringBuilder(1 << 16);
            g[0].edges((x, y, len, c) -> {
                int di = y % W - x % W, dj = y / W - x / W;
                int ii = x % W, jj = x / W;
                b.append(++nk[0]).append('\t').append(id[x]).append('\t').append(id[y]).append('\t').append(String.format(Locale.ROOT, "%.3f", len));
                for (int a = 0; a < 4; a++) b.append('\t').append(String.format(Locale.ROOT, "%.4f", a == 0 ? c : g[a].cost(ii, jj, di, dj)));
                b.append('\n');
                if (b.length() > 60_000) { try { w.write(b.toString()); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); } b.setLength(0); }
            });
            int gitter = nk[0];
            for (int k = 0; k < netz.length; k++)
                if (einschlag[k])
                    b.append(++nk[0]).append('\t').append(quelle).append('\t').append(id[k]).append('\t')
                            .append(String.format(Locale.ROOT, "%.3f", Math.hypot(WellenNetz.ost(k % W) - qe, WellenNetz.nord(k / W) - qn))).append("\t0\t0\t0\t0\n");
            w.write(b.toString());
            nk[1] = gitter;
        }
        int gitterKanten = nk[1], quellKanten = nk[0] - nk[1];
        // Ankunft
        float[][] t = new float[4][];
        int[] par = null;
        long ts = System.nanoTime();
        for (int a = 0; a < 4; a++) {
            t[a] = g[a].solve(q);
            if (a == 0) par = g[a].parent().clone();
        }
        double msJe = (System.nanoTime() - ts) / 4e6;
        try (BufferedWriter w = Files.newBufferedWriter(new File(dir, "ankunft.tsv").toPath(), StandardCharsets.UTF_8)) {
            w.write("NODE_ID\tT_HEUTE\tT_1940\tT_1959\tT_NICHTLINEAR\tVORGAENGER\n");
            for (int k = 0; k < netz.length; k++) {
                if (!netz[k]) continue;
                StringBuilder b = new StringBuilder().append(id[k]);
                for (int a = 0; a < 4; a++) b.append('\t').append(Float.isFinite(t[a][k]) ? String.format(Locale.ROOT, "%.3f", t[a][k]) : "");
                b.append('\t').append(par[k] >= 0 ? id[par[k]] : einschlag[k] ? quelle : 0).append('\n');
                w.write(b.toString());
            }
        }
        // Prüfwege: Orte und jeder 997. Knoten
        List<String> pw = new ArrayList<>();
        pw.add("NAME\tNODE_ID\tT_HEUTE\tT_1940\tT_1959\tT_NICHTLINEAR");
        String summe = WellenNetz.pruefsumme(dir);
        String meta = String.format(Locale.ROOT, """
                # LB_NET_WELLE als Datei (Phase 7a, Werkbuch 7.1): Bucht innerhalb des Eingangs und See bis %.0f m vor ihm, 20 m, 16 Nachbarn
                knoten=%d (+ QUELLE %d)
                kanten=%d (+ %d von QUELLE)
                einschlag=%d Zellen um E %.0f N %.0f
                bucht=%d see=%d
                eingang=%.1f %.1f %.1f %.1f
                wasserstand=%.2f m NAVD88 (Mittelwasser [G])
                amplitude=30 + 122*exp(-d/1500) m [G]
                rechnung=%.0f ms je Kostenart (route.TravelGrid)
                pruefsumme=%s
                """, SEE, n, quelle, gitterKanten, quellKanten, q.size(), pe, pn, nb, n - nb, ae, an, be, bn, Isochronenbild.MSL, msJe, summe);
        Files.writeString(new File(dir, "WELLE.txt").toPath(), meta, StandardCharsets.UTF_8);
        WellenNetz wn = WellenNetz.lesen(daten);
        for (Object[] o : ORTE) {
            int k = wn.knotenBei((double) o[1], (double) o[2], (double) o[3]);
            if (k > 0) pw.add(o[0] + "\t" + k + zeiten(wn, k));
        }
        for (int k = 997; k <= n; k += 997) pw.add("K" + k + "\t" + k + zeiten(wn, k));
        Files.write(new File(dir, "pruefwege.tsv").toPath(), pw, StandardCharsets.UTF_8);
        log.log("  welle: %,d Knoten (Bucht %,d, See %,d), %,d Kanten, Einschlag %d Zellen; %.0f ms je Kostenart; in %.1f s", n, nb, n - nb, gitterKanten, q.size(), msJe,
                (System.nanoTime() - t0) / 1e9);
        for (Object[] o : ORTE)
            log.log("    %-16s %s", o[0], ankunftText(wn, (double) o[1], (double) o[2], (double) o[3]));
        return new Stand(n, gitterKanten, q.size(), nb, n - nb, new double[]{ae, an, be, bn}, summe);
    }

    static String zeiten(WellenNetz w, int k) {
        StringBuilder b = new StringBuilder();
        for (int a = 0; a < 4; a++) b.append('\t').append(Float.isFinite(w.ankunft[a][k - 1]) ? String.format(Locale.ROOT, "%.3f", w.ankunft[a][k - 1]) : "");
        return b.toString();
    }

    static String ankunftText(WellenNetz w, double e, double n, double r) {
        StringBuilder b = new StringBuilder();
        for (int a = 0; a < 4; a++) b.append(String.format(Locale.GERMAN, "%s %.0f s  ", WellenNetz.KOSTEN_NAME[a], w.ankunftBei(a, e, n, r)));
        return b.toString().trim();
    }

    /** Tiefe unter Mittelwasser, mindestens 0,5 m. */
    static float tiefe(float z) { return (float) Math.max(Isochronenbild.MIN_TIEFE, Isochronenbild.MSL - z); }

    /**
     * Füllt von den Quellen über erlaubte Zellen (4er-Nachbarschaft: über ein Band von ¾ Zelle um eine Strecke kommt so
     * kein Weg, auch schräg nicht), nicht durch gesperrte; gibt die Zahl zurück.
     */
    static int flut(boolean[] erlaubt, boolean[] gesperrt, boolean[] aus, List<TravelGrid.Source> start, int W, int H) {
        int[] st = new int[W * H];
        int sp = 0, n = 0;
        for (TravelGrid.Source s : start) {
            int k = s.j() * W + s.i();
            if (erlaubt[k] && !gesperrt[k] && !aus[k]) { aus[k] = true; st[sp++] = k; n++; }
        }
        while (sp > 0) {
            int k = st[--sp], i = k % W, j = k / W;
            for (int[] d : VIER) {
                    int ii = i + d[0], jj = j + d[1];
                    if (ii < 0 || jj < 0 || ii >= W || jj >= H) continue;
                    int kk = jj * W + ii;
                    if (erlaubt[kk] && !gesperrt[kk] && !aus[kk]) { aus[kk] = true; st[sp++] = kk; n++; }
            }
        }
        return n;
    }

    static final int[][] VIER = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Abstand des Punkts (x, y) von der Strecke a–b. */
    static double abstand(double x, double y, double ax, double ay, double bx, double by) {
        double ux = bx - ax, uy = by - ay, l2 = ux * ux + uy * uy;
        double t = Math.max(0, Math.min(1, ((x - ax) * ux + (y - ay) * uy) / l2));
        return Math.hypot(x - ax - ux * t, y - ay - uy * t);
    }

    /** Für sich: {@code java … tools.Welle [daten]}. */
    public static void main(String[] a) throws Exception {
        rechnen(new File(a.length > 0 ? a[0] : "daten"), (f, x) -> System.out.println(String.format(Locale.GERMAN, f, x)));
    }
}
