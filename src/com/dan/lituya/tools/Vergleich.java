package com.dan.lituya.tools;

import com.dan.lituya.scene.Boden1958;
import com.dan.lituya.scene.EreignisDaten;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.UferNetz;
import com.dan.lituya.scene.WellenNetz;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleBinaryOperator;

/**
 * Phase 13a (Werkbuch 7.2, 9.5, 9.6, 2.2; Ant-Ziel {@code vergleich}): die vorgerechnete Welle gegen das, was belegt
 * oder anders gerechnet ist. Je Lauf aus {@code daten/ereignis/<lauf>} (höchster Stand und Ankunft je Gitter):
 * <ul>
 * <li><b>Spurlinie gerechnet</b> entlang {@code LB_NET_UFER} (Ufer heute und Cenotaph Island), alle {@link #SCHRITT} m: der
 * höchste Stand über dem Pegel in Zellen, die am Anfang Land waren, bis {@link #LANDEIN} m vom Uferpunkt [G], und die
 * früheste Ankunft (über 1 m) im Wasser bis {@link #WASSER} m. Nach {@code daten/netz/ufer/spurlinie_<lauf>.tsv}; {@code ant
 * netzdb} lädt sie als Rechnung in {@code LB_SPURLINIE_LRS} und vergleicht dort mit {@code LB_AUFLAUF}.</li>
 * <li><b>Belegte Aufläufe 1958</b> (MILLER60: Sporn 524 m, Südufer bei Mudslide Creek 208 m) gegen die gerechnete
 * Spurlinie bis {@link #UMKREIS} m entlang des Ufers.</li>
 * <li><b>Ankunft</b> an Cenotaph Island, Anchorage Cove und am Eingang gegen {@code LB_NET_WELLE} (heute, 1940,
 * nichtlinear) und die Zeitleiste 2.2.</li>
 * </ul>
 * Dazu das Bild {@code docs/status/Vergleich.png}. Argumente: daten, Läufe (mit Komma, Vorgabe kette,messwelle).
 */
public final class Vergleich {
    private Vergleich() { }

    public static final double SCHRITT = 50, LANDEIN = 400, WASSER = 200, UMKREIS = 300;

    /** Eine gerechnete Spurlinie: je Strang Maß, Lage, Auflauf (m über dem Pegel, NaN: nichts), Ankunft (s nach dem Eintauchen). */
    public static final class Spur {
        public final String lauf;
        public final List<double[]> zeilen = new ArrayList<>(); // Strang, M, Ost, Nord, Auflauf, Ankunft
        Spur(String lauf) { this.lauf = lauf; }

        /** Höchster Auflauf auf Strang s im Maß m ± d. */
        public double hoechst(int s, double m, double d) {
            double best = Double.NaN;
            for (double[] z : zeilen) if (z[0] == s && Math.abs(z[1] - m) <= d && !Double.isNaN(z[4]) && !(z[4] <= best)) best = z[4];
            return best;
        }
    }

    public static void main(String[] a) throws Exception {
        File daten = new File(a.length > 0 ? a[0] : "daten");
        String[] laeufe = (a.length > 1 ? a[1] : "kette,messwelle").split(",");
        Boden1958 boden = new Boden1958(daten);
        UferNetz ufer = UferNetz.lesen(daten);
        WellenNetz netz = null;
        try { netz = WellenNetz.lesen(daten); } catch (Exception e) { System.out.println("HINWEIS daten/netz/welle fehlt"); }
        List<Spur> spuren = new ArrayList<>();
        for (String l : laeufe) {
            File dir = EreignisDaten.ordner(daten, l);
            if (!new File(dir, "EREIGNIS.txt").isFile()) { System.out.println("FEHLER daten/ereignis/" + l + " fehlt: ant ereignis"); continue; }
            Spur s = spur(dir, l, ufer, boden::hoehe);
            schreiben(s, new File(new File(daten, UferNetz.ORDNER), "spurlinie_" + l + ".tsv"));
            spuren.add(s);
        }
        System.out.println(bericht(spuren, ufer, netz));
        File png = new File("docs/status/Vergleich.png");
        bild(spuren, ufer, png);
        System.out.println("Bild: " + png);
    }

    /** Rechnet die Spurlinie eines Laufs. */
    public static Spur spur(File dir, String lauf, UferNetz ufer, DoubleBinaryOperator hoehe) throws Exception {
        Spur s = new Spur(lauf);
        try (EreignisDaten.Leser r = new EreignisDaten.Leser(dir)) {
            double pegel = Double.parseDouble(r.p.getProperty("pegel_m", "0").trim()), start = Double.parseDouble(r.p.getProperty("start_s", "105").trim());
            int n = r.gitter.size();
            float[][] max = new float[n][], an = new float[n][];
            boolean[][] land = new boolean[n][];
            for (int k = 0; k < n; k++) {
                max[k] = r.raster(dir, "max", k);
                an[k] = r.raster(dir, "ankunft", k);
                EreignisDaten.Gitter g = r.gitter.get(k);
                land[k] = new boolean[g.nx() * g.nz()];
                for (int j = 0; j < g.nz(); j++) for (int i = 0; i < g.nx(); i++) land[k][j * g.nx() + i] = hoehe.applyAsDouble(g.e(i), g.n(j)) > pegel;
            }
            for (UferNetz.Strang st : ufer.straenge.values()) {
                for (double m = 0; m <= st.laenge(); m += SCHRITT) {
                    double[] p = ufer.punkt(st.id(), m);
                    int k = n - 1;
                    while (k > 0 && !innen(r.gitter.get(k), p[0], p[1], LANDEIN)) k--;
                    EreignisDaten.Gitter g = r.gitter.get(k);
                    double au = Double.NaN, at = Double.NaN;
                    int ri = (int) Math.ceil(LANDEIN / g.zelle()), ci = (int) Math.floor((p[0] - g.e0()) / g.zelle()), cj = (int) Math.floor((p[1] - g.n0()) / g.zelle());
                    for (int j = cj - ri; j <= cj + ri; j++)
                        for (int i = ci - ri; i <= ci + ri; i++) {
                            if (i < 0 || j < 0 || i >= g.nx() || j >= g.nz()) continue;
                            double d = Math.hypot(g.e(i) - p[0], g.n(j) - p[1]);
                            int c = j * g.nx() + i;
                            if (land[k][c]) {
                                if (d <= LANDEIN && !Float.isNaN(max[k][c]) && !(max[k][c] - pegel <= au)) au = max[k][c] - pegel;
                            } else if (d <= WASSER && !Float.isNaN(an[k][c]) && !(an[k][c] - start >= at)) at = an[k][c] - start;
                        }
                    s.zeilen.add(new double[]{st.id(), m, p[0], p[1], au, at});
                }
            }
        }
        return s;
    }

    static boolean innen(EreignisDaten.Gitter g, double e, double n, double rand) {
        return e - rand >= g.e0() && n - rand >= g.n0() && e + rand <= g.e0() + g.nx() * g.zelle() && n + rand <= g.n0() + g.nz() * g.zelle();
    }

    static void schreiben(Spur s, File f) throws Exception {
        try (PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8)) {
            w.println("STRANG_ID\tM\tOST\tNORD\tAUFLAUF\tANKUNFT");
            w.println("# Spurlinie gerechnet aus daten/ereignis/" + s.lauf + " (Phase 13a): höchster Stand über dem Pegel an Land bis " + (int) LANDEIN + " m, Ankunft (über 1 m) im Wasser bis "
                    + (int) WASSER + " m, s nach dem Eintauchen; leer: nichts");
            for (double[] z : s.zeilen)
                w.printf(Locale.ROOT, "%d\t%.1f\t%.1f\t%.1f\t%s\t%s%n", (int) z[0], z[1], z[2], z[3], Double.isNaN(z[4]) ? "" : String.format(Locale.ROOT, "%.1f", z[4]),
                        Double.isNaN(z[5]) ? "" : String.format(Locale.ROOT, "%.0f", z[5]));
        }
    }

    /** Die Tabellen für die Konsole (und Abschnitt 19). */
    public static String bericht(List<Spur> spuren, UferNetz ufer, WellenNetz netz) {
        StringBuilder b = new StringBuilder();
        b.append("Belegte Aufläufe 1958 gegen die gerechnete Spurlinie: höchster Wert bis ").append((int) UMKREIS).append(" m entlang des Ufers / bis 1 km um den Ort (Lage der Belege [G])\n");
        b.append(String.format(Locale.GERMAN, "  %-34s %8s", "Ort", "belegt"));
        for (Spur s : spuren) b.append(String.format(Locale.GERMAN, " %14s", s.lauf));
        b.append('\n');
        for (UferNetz.Auflauf a : ufer.auflauf) {
            if (a.jahr() != 1958) continue;
            b.append(String.format(Locale.GERMAN, "  %-34s %6.0f m", a.name(), a.hoehe()));
            for (Spur s : spuren) {
                double h = s.hoechst(a.strang(), a.m(), UMKREIS), r = Double.NaN;
                for (double[] z : s.zeilen) if (Math.hypot(z[2] - a.e(), z[3] - a.n()) <= 1000 && !Double.isNaN(z[4]) && !(z[4] <= r)) r = z[4];
                b.append(Double.isNaN(h) ? String.format("%15s", "–") : String.format(Locale.GERMAN, " %6.0f m (%+3.0f %%)", h, 100 * (h / a.hoehe() - 1)));
                b.append(Double.isNaN(r) ? " / –" : String.format(Locale.GERMAN, " / %.0f m (%+.0f %%)", r, 100 * (r / a.hoehe() - 1)));
            }
            b.append('\n');
        }
        b.append("Spurlinie entlang des Ufers (Median, 90 %, höchst; nur wo etwas ankam)\n");
        for (Spur s : spuren)
            for (UferNetz.Strang st : ufer.straenge.values()) {
                double[] v = s.zeilen.stream().filter(z -> z[0] == st.id() && !Double.isNaN(z[4])).mapToDouble(z -> z[4]).sorted().toArray();
                long alle = s.zeilen.stream().filter(z -> z[0] == st.id()).count();
                if (v.length == 0) { b.append(String.format(Locale.GERMAN, "  %-10s %-16s nichts%n", s.lauf, st.name())); continue; }
                b.append(String.format(Locale.GERMAN, "  %-10s %-16s %.0f / %.0f / %.0f m an %d von %d Punkten%n", s.lauf, st.name(), v[v.length / 2], v[(int) (0.9 * (v.length - 1))], v[v.length - 1], v.length, alle));
            }
        b.append("Ankunft (s nach dem Eintauchen): gerechnet über 1 m, LB_NET_WELLE (heute / 1940 / nichtlinear), Zeitleiste 2.2\n");
        String[][] leiste = {{"Cenotaph Island", "150–400"}, {"Anchorage Cove", "300–600"}, {"Eingang", "300–600"}};
        for (Object[] o : WellenNetz.ORTE) {
            if (o[0].equals("Sporn")) continue;
            double e = (double) o[1], n = (double) o[2], rad = (double) o[3];
            b.append(String.format(Locale.GERMAN, "  %-16s", o[0]));
            for (Spur s : spuren) {
                double best = Double.NaN;
                for (double[] z : s.zeilen) if (Math.hypot(z[2] - e, z[3] - n) <= rad + WASSER && !Double.isNaN(z[5]) && !(z[5] >= best)) best = z[5];
                b.append(String.format(Locale.GERMAN, " %s %s", s.lauf, Double.isNaN(best) ? "–" : String.format(Locale.GERMAN, "%.0f s", best)));
            }
            if (netz != null) b.append(String.format(Locale.GERMAN, " · Netz %s / %s / %s", t(netz.ankunftBei(0, e, n, rad)), t(netz.ankunftBei(1, e, n, rad)), t(netz.ankunftBei(3, e, n, rad))));
            for (String[] l : leiste) if (l[0].equals(o[0])) b.append(" · Zeitleiste ").append(l[1]).append(" s");
            b.append('\n');
        }
        b.append("Zeugen: noch nicht ausgewertet (Phase 0b: Miller S. 51–86, Ulrich, Swanson); die Zeitleiste 2.2 ist gerechnet [G].");
        return b.toString();
    }

    static String t(double v) { return Double.isNaN(v) ? "–" : String.format(Locale.GERMAN, "%.0f s", v); }

    // ------------------------------------------------------------------ Bild

    static final Color[] FARBE = {new Color(240, 200, 110), new Color(110, 180, 240), new Color(200, 120, 200)};

    public static void bild(List<Spur> spuren, UferNetz ufer, File png) throws Exception {
        int w = 1800, h = 900;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(11, 17, 24));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(230, 234, 238));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString("Phase 13a · Spurlinie gerechnet gegen belegt · LB_NET_UFER", 20, 30);
        int y = 60;
        for (UferNetz.Strang st : ufer.straenge.values()) {
            int ph = st.ring() ? 250 : 420;
            profil(g, spuren, ufer, st, 70, y, w - 100, ph);
            y += ph + 70;
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int lx = 70;
        for (int k = 0; k < spuren.size(); k++) {
            g.setColor(FARBE[k % FARBE.length]);
            g.fillRect(lx, h - 30, 20, 4);
            g.drawString(spuren.get(k).lauf, lx + 26, h - 24);
            lx += 160;
        }
        g.setColor(Color.RED);
        g.fillOval(lx, h - 34, 10, 10);
        g.setColor(new Color(140, 156, 170));
        g.drawString("belegt (MILLER60), Höhe über dem Wasser; Auflauf gerechnet = höchster Stand an Land bis 400 m vom Uferpunkt [G]", lx + 16, h - 24);
        g.dispose();
        ImageIO.write(im, "png", png);
    }

    static void profil(Graphics2D g, List<Spur> spuren, UferNetz ufer, UferNetz.Strang st, int px, int py, int pw, int ph) {
        double L = st.laenge(), top = st.ring() ? 100 : 600;
        g.setColor(new Color(20, 28, 38));
        g.fillRect(px, py, pw, ph);
        g.setColor(new Color(50, 62, 76));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (double v = 0; v <= top; v += top / 6) {
            int yy = (int) (py + ph - ph * v / top);
            g.drawLine(px, yy, px + pw, yy);
            g.setColor(new Color(140, 156, 170));
            g.drawString(String.format(Locale.GERMAN, "%.0f m", v), px - 50, yy + 4);
            g.setColor(new Color(50, 62, 76));
        }
        for (double m = 0; m <= L; m += 2000) {
            int xx = (int) (px + pw * m / L);
            g.drawLine(xx, py, xx, py + ph);
            g.setColor(new Color(140, 156, 170));
            g.drawString(String.format(Locale.GERMAN, "%.0f km", m / 1000), xx - 10, py + ph + 14);
            g.setColor(new Color(50, 62, 76));
        }
        // Orte auf dem Strang
        g.setColor(new Color(140, 156, 170));
        for (Site.Ort o : Site.ORTE) {
            double[] pr = ufer.projektion(st.id(), o.e(), o.n());
            if (Math.abs(pr[1]) > 800) continue;
            int xx = (int) (px + pw * pr[0] / L);
            g.drawString(o.name(), xx + 2, py + 12 + (int) (Math.abs(o.name().hashCode()) % 3) * 12);
        }
        g.setStroke(new BasicStroke(1.6f));
        for (int k = 0; k < spuren.size(); k++) {
            Path2D p = new Path2D.Double();
            boolean on = false;
            for (double[] z : spuren.get(k).zeilen) {
                if (z[0] != st.id()) continue;
                if (Double.isNaN(z[4])) { on = false; continue; }
                double xx = px + pw * z[1] / L, yy = py + ph - ph * Math.min(top, z[4]) / top;
                if (on) p.lineTo(xx, yy); else p.moveTo(xx, yy);
                on = true;
            }
            g.setColor(FARBE[k % FARBE.length]);
            g.draw(p);
        }
        for (UferNetz.Auflauf a : ufer.auflauf) {
            if (a.strang() != st.id()) continue;
            int xx = (int) (px + pw * a.m() / L), yy = (int) (py + ph - ph * Math.min(top, a.hoehe()) / top);
            g.setColor(a.jahr() == 1958 ? Color.RED : new Color(160, 90, 90));
            g.fillOval(xx - 5, yy - 5, 10, 10);
            g.drawString(String.format(Locale.GERMAN, "%.0f m (%d)", a.hoehe(), a.jahr()), xx + 7, yy - 4);
        }
        g.setColor(new Color(230, 234, 238));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString(st.name() + (st.ring() ? " (Ring)" : ", ab La Chaussee Spit im Uhrzeigersinn") + String.format(Locale.GERMAN, ", %.1f km", L / 1000), px, py - 6);
    }
}
