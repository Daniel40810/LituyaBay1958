package com.dan.lituya.gl;

import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Stufe;
import com.dan.lituya.scene.UferNetz;
import com.dan.lituya.scene.WasserNetz;
import com.dan.lituya.scene.WegNetz;
import com.dan.lituya.scene.WellenNetz;
import com.dan.lituya.tools.Klassen;
import com.dan.lituya.tools.Wegkarte;
import com.dan.netview.Contour;
import com.dan.netview.NetLayer;
import com.dan.netview.Ramp;
import com.dan.netview.gl.NetviewGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Der Netzblick (Werkbuch 7.10, F38 A, Phase 7a): die Netze der Welle und des Ufers mit {@code com.dan.netview}, als Bild
 * im Bild (Draufsicht der Bucht unten rechts) und wahlweise in der Szene auf dem Wasser. Ebenen: Isochronen alle 10 s
 * (jede Minute kräftig), die mit der Simulationsuhr laufen (Start der Welle {@link WellenNetz#START_S} nach dem ersten
 * Stoß), Wellenstrahlen zu den Orten, die mit der Front wachsen, die Einschlaglinie, das Ufer (LRS) und die Aufläufe als
 * Punkte in der Farbe ihrer Höhe. Die Daten kommen aus {@code daten/netz/welle} und {@code daten/netz/ufer} (F25 A), im
 * Hintergrund geladen; die Kostenart (heute, 1940, 1959, nichtlinear) wechselt im Bedienfeld NETZE.
 * <p>Seit Phase 16c wählbar: {@link #netz} 0 Welle ({@code LB_NET_WELLE}, Ausschnitt Bucht), 1 Wasser
 * ({@code LB_NET_WASSER}, Ausschnitt Szene: Bäche nach Einzugsfläche, Fließlinien der Gletscher, Teileinzugsgebiete in der
 * Farbe ihres Ziels, Mündungsziele, Abfluss beim Regen des Bedienfelds) und 2 Weg ({@code LB_NET_WEG}, Ausschnitt Kern: eine
 * Stunde vom Strand in Isolinien zu 15 min, Washburns Route 1934 mit den drei Wegpunkten, die Flucht an Land der spätesten
 * Abfahrt, die mit der Simulationsuhr wächst, und die Geländeklassen als Farbfläche im Bild im Bild). Je Netz ein eigener
 * {@link NetviewGl}; in der Szene liegen Wasser und Weg auf dem Gelände (Höhen aus dem Kachelstapel).
 */
public final class Netzblick {
    /** Ausschnitt der Draufsicht (UTM). */
    static final double AE0 = 342_800, AE1 = 358_000, AN0 = 6_498_400, AN1 = 6_507_600;

    public volatile boolean bildImBild = true, inSzene = false, ganz = false, isochronen = true, strahlen = true, ufer = true;
    /** Phase 16c: welches Netz (0 Welle, 1 Wasser, 2 Weg; seit Phase 17 3 Sicht). */
    public static final String[] NETZE = {"Welle", "Wasser", "Weg", "Sicht"};
    public volatile int netz = 0;
    /** Ebenen Wasser (Phase 16c). */
    public volatile boolean baeche = true, gletscher = true, einzug = true;
    /** Ebenen Weg (Phase 16c). */
    public volatile boolean stunde = true, washburn = true, landflucht = true, klassen = true;
    /** Ebenen Sicht (Phase 17). */
    public volatile boolean sichtlinien = false, rundflug = true, spannbaum = true;
    /** Farben der Knotenarten (wie SichtNetz.ARTEN). */
    static final int[] ART_RGB = {0xFFFFFF, 0xFFD040, 0xFF8A50, 0x60FF90, 0xFF4060, 0x80C0FF, 0xC8A0FF, 0x40E0E0};
    /** Regen (mm/h) für den Abfluss in der Karte (vom Bedienfeld, wie die Bäche in der Szene). */
    public volatile double regen = 0;
    /** Farben der Ziele (wie WasserNetz.ZIELE). */
    static final int[] ZIEL_RGB = {0xFF9060, 0x60D0A0, 0xFFE070, 0xA0A0FF, 0x9A9A9A};
    /** Kostenart 0 … 3 ({@link WellenNetz#KOSTEN}). */
    public volatile int kosten = 0;

    private final NetviewGl view = new NetviewGl();
    private final NetLayer iso = new NetLayer("Isochronen"), ray = new NetLayer("Wellenstrahlen"), quelle = new NetLayer("Einschlag"),
            uferL = new NetLayer("Ufer"), auflauf = new NetLayer("Auflauf");
    private final NetviewGl viewWasser = new NetviewGl(), viewWeg = new NetviewGl(), viewSicht = new NetviewGl();
    private final NetLayer sichtLinienL = new NetLayer("Sichtlinien"), spannbaumL = new NetLayer("Spannbaum"), rundflugL = new NetLayer("Rundflug"), sichtPunkteL = new NetLayer("Kamerapunkte");
    private volatile com.dan.lituya.scene.SichtNetz sicht;
    private volatile BufferedImage grundSicht;
    private volatile String standSicht = "LB_NET_SICHT: nicht geladen";
    private final NetLayer baecheL = new NetLayer("Bäche"), eisL = new NetLayer("Gletscher"), einzugL = new NetLayer("Einzugsgebiete"), zielL = new NetLayer("Ziele"),
            stundeL = new NetLayer("1 h vom Strand"), washburnL = new NetLayer("Washburn 1934"), wegpunktL = new NetLayer("Wegpunkte"), landwegL = new NetLayer("Flucht an Land"),
            landzielL = new NetLayer("Sicher");
    private final HudOverlay karte = new HudOverlay();
    private volatile String standWasser = "LB_NET_WASSER: nicht geladen", standWeg = "LB_NET_WEG: nicht geladen";
    private volatile BufferedImage grundWasser, grundWeg, grundWegGrau;
    private volatile WasserNetz wasser;
    private volatile String wegText = "", landText = "";
    /** Je Ziel die Kanten der Ebene 1 hinein: {Fläche km², Eisanteil} (Abfluss beim Regen). */
    private volatile double[][][] zielZufluss;
    private volatile double simSeconds;
    private volatile WellenNetz welle;
    private volatile UferNetz uferNetz;
    private volatile String stand = "Netze: nicht geladen";
    private volatile BufferedImage grund;
    private volatile int gebaut = -1;
    private volatile boolean baut;
    private boolean init;
    /** Höhe der Linien über dem Pegel (m). */
    public double ueberWasser = 1.5;
    private double pegel;
    private double zeit;

    /** Netz n geladen (0 Welle, 1 Wasser, 2 Weg)? Oder das Laden ist zu Ende ({@link #fertig}). */
    public boolean bereit(int n) { return n == 1 ? wasser != null : n == 2 ? !wegText.isEmpty() : n == 3 ? sicht != null : welle != null; }
    public volatile boolean fertig;

    public String stand() { return stand + " · " + standWasser + " · " + standWeg + " · " + standSicht; }
    public WellenNetz welle() { return welle; }
    public UferNetz ufer() { return uferNetz; }

    /** Lädt die Netze im Hintergrund (einmal beim Start), ohne Gelände. */
    public void laden(File daten) { laden(daten, null); }

    /** Lädt die Netze im Hintergrund (einmal beim Start); Wasser und Weg legen sich mit {@code hoehe} auf das Gelände. */
    public void laden(File daten, BaecheGl.Hoehe hoehe) {
        Thread t = new Thread(() -> {
            try {
                long t0 = System.nanoTime();
                WellenNetz w = WellenNetz.lesen(daten);
                UferNetz u = new File(daten, UferNetz.ORDNER + "/punkte.tsv").isFile() ? UferNetz.lesen(daten) : null;
                welle = w;
                uferNetz = u;
                grund = grundbild(w, u);
                bauen(kosten);
                stand = String.format(Locale.GERMAN, "%s; %s; geladen in %.1f s", w, u == null ? "Ufer fehlt" : u.toString(), (System.nanoTime() - t0) / 1e9);
            } catch (Exception e) {
                stand = "Netze nicht geladen: " + e.getMessage() + " (ant aufbereitung \"-Dschritte=welle,ufer\")";
            }
            try {
                long t0 = System.nanoTime();
                if (WasserNetz.da(daten)) {
                    bauenWasser(daten, hoehe);
                    standWasser = String.format(Locale.GERMAN, "%s; geladen in %.1f s", wasser, (System.nanoTime() - t0) / 1e9);
                } else standWasser = "LB_NET_WASSER fehlt (ant aufbereitung -Dschritte=wasser)";
            } catch (Exception e) {
                standWasser = "LB_NET_WASSER nicht geladen: " + e.getMessage();
            }
            try {
                long t0 = System.nanoTime();
                if (new File(new File(daten, WegNetz.ORDNER), "WEG.txt").isFile()) {
                    bauenWeg(daten, hoehe);
                    standWeg = String.format(Locale.GERMAN, "LB_NET_WEG: Ebenen geladen in %.1f s", (System.nanoTime() - t0) / 1e9);
                } else standWeg = "LB_NET_WEG fehlt (ant aufbereitung -Dschritte=klassen+weg+landflucht)";
            } catch (Exception e) {
                standWeg = "LB_NET_WEG nicht geladen: " + e.getMessage();
            }
            try {
                long t0 = System.nanoTime();
                if (com.dan.lituya.scene.SichtNetz.da(daten)) {
                    bauenSicht(daten);
                    standSicht = String.format(Locale.GERMAN, "%s; geladen in %.1f s", sicht, (System.nanoTime() - t0) / 1e9);
                } else standSicht = "LB_NET_SICHT fehlt (ant aufbereitung -Dschritte=sicht)";
            } catch (Exception e) {
                standSicht = "LB_NET_SICHT nicht geladen: " + e.getMessage();
            }
            fertig = true;
        }, "Netzblick");
        t.setDaemon(true);
        t.start();
    }

    /** Baut die Ebenen für eine Kostenart (im Hintergrund, auch beim Wechsel). */
    void bauen(int a) {
        WellenNetz w = welle;
        if (w == null) return;
        baut = true;
        try {
            double y = ueberWasser;
            float[] f = w.feld(a);
            List<Double> zehn = new ArrayList<>(), minute = new ArrayList<>();
            for (int t = 10; t <= 900; t += 10) (t % 60 == 0 ? minute : zehn).add((double) t);
            NetLayer neu = new NetLayer("Isochronen");
            neu.ramp = Ramp.TIME;
            // Raster: Zellmitte (i, j) → Szene x = E − E0, z = −(N − N0)
            double ox = WellenNetz.E_NW + WellenNetz.ZELLE / 2 - Site.E0, oz = -(WellenNetz.N_NW - WellenNetz.ZELLE / 2 - Site.N0);
            neu.isolines(f, WellenNetz.BREITE, WellenNetz.HOEHE, ox, oz, WellenNetz.ZELLE, (float) y, zehn.stream().mapToDouble(Double::doubleValue).toArray(), 0xE8C070, 1.3f);
            neu.isolines(f, WellenNetz.BREITE, WellenNetz.HOEHE, ox, oz, WellenNetz.ZELLE, (float) y, minute.stream().mapToDouble(Double::doubleValue).toArray(), 0xFFE6A0, 2.6f);
            synchronized (iso) {
                iso.clear();
                iso.items.addAll(neu.items);
                ray.clear();
                for (Object[] o : WellenNetz.ORTE) {
                    int k = w.knotenBei((double) o[1], (double) o[2], (double) o[3]);
                    if (k <= 0) continue;
                    double[] s = w.strahl(k);
                    if (s.length < 6) continue;
                    float[] xyz = new float[s.length], tt = new float[s.length / 3];
                    for (int q = 0; q < s.length / 3; q++) {
                        double[] p = Site.fromUtm(s[3 * q], s[3 * q + 1]);
                        xyz[3 * q] = (float) p[0]; xyz[3 * q + 1] = (float) (y + 0.5); xyz[3 * q + 2] = (float) p[1];
                        tt[q] = a == 0 ? (float) s[3 * q + 2] : w.ankunft[a][w.knotenJeZelle[zelle(s[3 * q], s[3 * q + 1])] - 1];
                    }
                    ray.ray(xyz, tt, 0xFF7860, 2.2f);
                }
                quelle.clear();
                List<Float> q = new ArrayList<>();
                for (int k = 0; k < w.n; k++)
                    if (w.einschlag[k]) {
                        double[] p = Site.fromUtm(WellenNetz.ost(w.i[k]), WellenNetz.nord(w.j[k]));
                        q.add((float) p[0]); q.add((float) (y + 1)); q.add((float) p[1]);
                    }
                float[] qa = new float[q.size()];
                for (int k = 0; k < qa.length; k++) qa[k] = q.get(k);
                if (qa.length >= 3) quelle.points(qa, null, 0xFF4040, 5);
                UferNetz u = uferNetz;
                uferL.clear();
                auflauf.clear();
                if (u != null) {
                    for (UferNetz.Strang s : u.straenge.values()) {
                        double[] en = s.en();
                        float[] xyz = new float[en.length / 2 * 3];
                        for (int k = 0; k < en.length / 2; k++) {
                            double[] p = Site.fromUtm(en[2 * k], en[2 * k + 1]);
                            xyz[3 * k] = (float) p[0]; xyz[3 * k + 1] = (float) (y + 2); xyz[3 * k + 2] = (float) p[1];
                        }
                        uferL.line(xyz, 0x9FD8FF, 1.6f);
                    }
                    if (!u.auflauf.isEmpty()) {
                        float[] p = new float[3 * u.auflauf.size()], v = new float[u.auflauf.size()];
                        for (int k = 0; k < v.length; k++) {
                            UferNetz.Auflauf x = u.auflauf.get(k);
                            double[] s = Site.fromUtm(x.e(), x.n());
                            p[3 * k] = (float) s[0]; p[3 * k + 1] = (float) (y + 3); p[3 * k + 2] = (float) s[1];
                            v[k] = (float) x.hoehe();
                        }
                        auflauf.points(p, v, -1, 13);
                        auflauf.ramp = Ramp.HEIGHT;
                    }
                }
            }
            gebaut = a;
        } finally {
            baut = false;
        }
    }

    // ------------------------------------------------------------------ Phase 16c: LB_NET_WASSER

    /** Ausschnitt der Draufsicht je Netz (UTM): {E0, E1, N0, N1}. Welle: Bucht, Wasser: Szene, Weg: Kern. */
    static double[] ausschnitt(int netz) {
        return switch (netz) {
            case 1 -> new double[]{Site.SZENE.eMin(), Site.SZENE.eMax(), Site.SZENE.nMin(), Site.SZENE.nMax()};
            case 2 -> new double[]{Site.KERN.eMin(), Site.KERN.eMax(), Site.KERN.nMin(), Site.KERN.nMax()};
            case 3 -> new double[]{Site.SZENE.eMin(), Site.SZENE.eMax(), Site.SZENE.nMin(), Site.SZENE.nMax()};
            default -> new double[]{AE0, AE1, AN0, AN1};
        };
    }

    /** Höhe am Ort (Szene) aus dem Gelände oder {@code sonst}. */
    static double y(BaecheGl.Hoehe h, double x, double z, double sonst) {
        if (h == null) return sonst;
        double v = h.at(x, z);
        return Double.isNaN(v) ? sonst : v;
    }

    /** Ein Zug aus UTM-Punkten {E, N, …} als Szene mit Höhen (Gelände + ueber, sonst linear von h0 nach h1). */
    static float[] zug(double[] en, BaecheGl.Hoehe h, double h0, double h1, double ueber) {
        int n = en.length / 2;
        float[] xyz = new float[3 * n];
        for (int k = 0; k < n; k++) {
            double[] p = Site.fromUtm(en[2 * k], en[2 * k + 1]);
            double f = n == 1 ? 0 : k / (double) (n - 1), lin = Double.isNaN(h0) || Double.isNaN(h1) ? 0 : h0 + (h1 - h0) * f;
            xyz[3 * k] = (float) p[0]; xyz[3 * k + 1] = (float) (y(h, p[0], p[1], lin) + ueber); xyz[3 * k + 2] = (float) p[1];
        }
        return xyz;
    }

    /** Teilt einen Zug in Stücke zu höchstens {@code max} Punkten (die Stufe gilt je Stück, README von netview). */
    static List<float[]> stuecke(float[] xyz, int max) {
        List<float[]> l = new ArrayList<>();
        int n = xyz.length / 3;
        for (int a = 0; a < n - 1; a += max - 1) {
            int b = Math.min(n, a + max);
            l.add(java.util.Arrays.copyOfRange(xyz, 3 * a, 3 * b));
        }
        return l;
    }

    static void linie(NetLayer l, float[] xyz, int rgb, float breite) {
        for (float[] s : stuecke(xyz, 64)) if (s.length >= 6) l.line(s, rgb, breite);
    }

    /** Baut die Ebenen von LB_NET_WASSER (im Hintergrund, einmal). */
    public void bauenWasser(File daten, BaecheGl.Hoehe hoehe) throws java.io.IOException {
        WasserNetz w = WasserNetz.lesen(daten);
        java.util.Map<Integer, double[]> lin = WasserNetz.linien(daten);
        NetLayer b = new NetLayer("Bäche"), g = new NetLayer("Gletscher"), u = new NetLayer("Einzugsgebiete"), z = new NetLayer("Ziele");
        int[] zielVon = new int[w.n];
        java.util.Arrays.fill(zielVon, -1);
        for (int k = 0; k < w.n; k++)
            if (WasserNetz.ZIEL.equals(w.art[k]) && w.name[k] != null) zielVon[k] = java.util.Arrays.asList(WasserNetz.ZIELE).indexOf(w.name[k]);
        List<List<double[]>> zu = new ArrayList<>();
        for (int q = 0; q < WasserNetz.ZIELE.length; q++) zu.add(new ArrayList<>());
        for (int e = 0; e < w.m; e++) {
            if (w.kEbene[e] != 1) continue;
            int zl = zielVon[w.nach[e]];
            if (zl >= 0 && w.ebene[w.von[e]] == 1) zu.get(zl).add(new double[]{w.kFlaeche[e], Double.isNaN(w.eisAnteil[w.von[e]]) ? 0 : w.eisAnteil[w.von[e]]});
            String a = w.kArt[e];
            boolean bach = WasserNetz.BACH.equals(a), bachEis = WasserNetz.BACH_EIS.equals(a), eis = WasserNetz.GLETSCHER.equals(a);
            if (!bach && !bachEis && !eis) continue;
            double[] en = lin.get(e);
            if (en == null || en.length < 4) en = new double[]{w.ost[w.von[e]], w.nord[w.von[e]], w.ost[w.nach[e]], w.nord[w.nach[e]]};
            float[] xyz = zug(en, hoehe, w.hoehe[w.von[e]], w.hoehe[w.nach[e]], eis ? 6 : 3);
            if (eis) linie(g, xyz, 0xD8D0FF, 1.2f);
            else linie(b, xyz, bachEis ? 0xBFEFFF : 0x4FA8FF, (float) Math.max(1, Math.min(4.5, 1 + 0.9 * Math.log10(1 + w.kFlaeche[e]))));
        }
        for (int q = 0; q < WasserNetz.ZIELE.length; q++) {
            List<Float> p = new ArrayList<>();
            for (int k = 0; k < w.n; k++)
                if (zielVon[k] == q && w.ebene[k] == 1 && !Double.isNaN(w.ost[k]) && !Double.isNaN(w.nord[k])) {
                    double[] s = Site.fromUtm(w.ost[k], w.nord[k]);
                    p.add((float) s[0]); p.add((float) (y(hoehe, s[0], s[1], 0) + 4)); p.add((float) s[1]);
                }
            float[] pa = new float[p.size()];
            for (int k = 0; k < pa.length; k++) pa[k] = p.get(k);
            if (pa.length >= 3) z.points(pa, null, ZIEL_RGB[q], 11);
        }
        File dir = new File(daten, WasserNetz.ORDNER);
        java.util.Map<String, Integer> zielJe = new java.util.HashMap<>();
        File ef = new File(dir, "einzug.tsv"), uf = new File(dir, "umrisse.tsv");
        if (ef.isFile()) for (String[] r : WellenNetz.tsv(ef)) zielJe.put(r[0], java.util.Arrays.asList(WasserNetz.ZIELE).indexOf(r[3]));
        if (uf.isFile())
            for (String[] r : WellenNetz.tsv(uf)) {
                String[] pp = r[3].split(",");
                double[] en = new double[2 * pp.length];
                for (int k = 0; k < pp.length; k++) { String[] xy = pp[k].split(" "); en[2 * k] = Double.parseDouble(xy[0]); en[2 * k + 1] = Double.parseDouble(xy[1]); }
                int zl = zielJe.getOrDefault(r[0], -1);
                linie(u, zug(en, hoehe, Double.NaN, Double.NaN, 8), zl < 0 ? 0x9A9A9A : ZIEL_RGB[zl], 1.3f);
            }
        double[][][] zz = new double[zu.size()][][];
        for (int q = 0; q < zz.length; q++) zz[q] = zu.get(q).toArray(new double[0][]);
        grundWasser = klassenBild(daten, Klassen.SZENE, 1, 0.62, true);
        synchronized (viewWasser) {
            for (NetLayer[] x : new NetLayer[][]{{baecheL, b}, {eisL, g}, {einzugL, u}, {zielL, z}}) { x[0].clear(); x[0].items.addAll(x[1].items); }
        }
        zielZufluss = zz;
        wasser = w;
    }

    /** Eine Ebene von Wasser oder Weg nach Namen (für die Prüfung) oder null. */
    public NetLayer ebene(String name) {
        for (NetLayer l : new NetLayer[]{baecheL, eisL, einzugL, zielL, stundeL, washburnL, wegpunktL, landwegL, landzielL, sichtLinienL, spannbaumL, rundflugL, sichtPunkteL})
            if (l.name.equals(name)) return l;
        return null;
    }

    /** Abfluss (m³/s) zum Ziel q beim Regen r (mm/h) und die Fläche (km²). */
    public double[] zuflussZiel(int q, double r) {
        double[][][] zz = zielZufluss;
        if (zz == null || q >= zz.length) return new double[]{Double.NaN, Double.NaN};
        double s = 0, a = 0;
        for (double[] x : zz[q]) { s += WasserNetz.abfluss(x[0], x[1], r); a += x[0]; }
        return new double[]{s, a};
    }

    /** Die Klassen eines Rasters als Farbfläche mit Schummerung (oder grau: Wasser blau, Eis, Schnee und Spalten hell), je {@code schritt} Zellen ein Bildpunkt. */
    static BufferedImage klassenBild(File daten, Klassen.Raster r, int schritt, double hell, boolean grau) throws java.io.IOException {
        byte[] kl = Klassen.lesen(daten, r);
        float[] z = Klassen.gelaende(daten, r);
        if (kl == null || z == null) return null;
        int w = r.w() / schritt, h = r.h() / schritt;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Color gr = new Color(118, 122, 126);
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                int c = (j * schritt) * r.w() + i * schritt, k = kl[c] & 0xFF;
                Color b = grau ? (k <= 1 ? new Color(30, 52, 74) : k >= 9 && k <= 11 ? new Color(205, 214, 224) : gr) : Wegkarte.KF[Math.min(k, Wegkarte.KF.length - 1)];
                Color s = Wegkarte.schatten(b, z, r.w(), r.h(), i * schritt, j * schritt, r.zelle());
                im.setRGB(i, j, 0xFF000000 | ((int) (s.getRed() * hell) << 16) | ((int) (s.getGreen() * hell) << 8) | (int) (s.getBlue() * hell));
            }
        return im;
    }

    // ------------------------------------------------------------------ Phase 16c: LB_NET_WEG

    /** Baut die Ebenen von LB_NET_WEG (im Hintergrund, einmal). */
    public void bauenWeg(File daten, BaecheGl.Hoehe hoehe) throws java.io.IOException {
        File dir = new File(daten, WegNetz.ORDNER);
        NetLayer st = new NetLayer("1 h vom Strand"), wb = new NetLayer("Washburn 1934"), wp = new NetLayer("Wegpunkte"), lw = new NetLayer("Flucht an Land"),
                lz = new NetLayer("Sicher");
        Klassen.Raster K = Klassen.KERN;
        float[] zeitF = Stufe.lesen(new File(dir, "zeit_kern_10m.r32"));
        float[] zk = Klassen.gelaende(daten, K);
        if (zeitF != null) {
            double ox = K.ost(0) - Site.E0, oz = -(K.nord(0) - Site.N0);
            double[] lev = {900, 1800, 2700, 3600};
            int[] rgb = {0xFFE65A, 0xFFAA3C, 0xF06E3C, 0xC83C5A};
            float[] br = {1.5f, 1.7f, 1.9f, 2.6f};
            for (int q = 0; q < lev.length; q++)
                for (float[] xz : Contour.lines(zeitF, K.w(), K.h(), ox, oz, K.zelle(), lev[q])) {
                    int n = xz.length / 2;
                    float[] xyz = new float[3 * n];
                    for (int k = 0; k < n; k++) {
                        double x = xz[2 * k], zz = xz[2 * k + 1];
                        xyz[3 * k] = (float) x; xyz[3 * k + 1] = (float) (y(hoehe, x, zz, raster(zk, K, x, zz)) + 2); xyz[3 * k + 2] = (float) zz;
                    }
                    linie(st, xyz, rgb[q], br[q]);
                }
        }
        File wf = new File(dir, "washburn.tsv");
        if (wf.isFile()) {
            List<String[]> ws = WellenNetz.tsv(wf);
            float[] xyz = new float[3 * ws.size()];
            List<Float> pp = new ArrayList<>();
            for (int k = 0; k < ws.size(); k++) {
                String[] r = ws.get(k);
                double[] p = Site.fromUtm(Double.parseDouble(r[2]), Double.parseDouble(r[3]));
                xyz[3 * k] = (float) p[0]; xyz[3 * k + 1] = (float) (Double.parseDouble(r[4]) + 3); xyz[3 * k + 2] = (float) p[1];
                if (r.length > 7 && !r[7].isEmpty()) { pp.add(xyz[3 * k]); pp.add(xyz[3 * k + 1] + 2); pp.add(xyz[3 * k + 2]); }
            }
            linie(wb, xyz, 0xFF503C, 2.4f);
            float[] pa = new float[pp.size()];
            for (int k = 0; k < pa.length; k++) pa[k] = pp.get(k);
            if (pa.length >= 3) wp.points(pa, null, 0xFFFFFF, 10);
        }
        File lf = new File(dir, "landwege.tsv");
        if (lf.isFile()) {
            java.util.Map<String, List<String[]>> je = new java.util.LinkedHashMap<>();
            for (String[] r : WellenNetz.tsv(lf)) je.computeIfAbsent(r[0], q -> new ArrayList<>()).add(r);
            List<Float> ziel = new ArrayList<>();
            for (var en : je.entrySet()) {
                int farbe = en.getKey().startsWith("Edrie") ? 0xFFC85A : 0x78C8FF;
                List<float[]> see = new ArrayList<>(), land = new ArrayList<>();
                for (String[] r : en.getValue()) {
                    boolean s = r[3].equals("SEE");
                    double[] p = Site.fromUtm(Double.parseDouble(r[5]), Double.parseDouble(r[6]));
                    double yy = s ? 2 : (r[7].isEmpty() ? y(hoehe, p[0], p[1], 0) : Double.parseDouble(r[7])) + 2.5;
                    float[] v = {(float) p[0], (float) yy, (float) p[1], Float.parseFloat(r[8])};
                    (s ? see : land).add(v);
                }
                if (!see.isEmpty() && !land.isEmpty()) land.add(0, see.get(see.size() - 1));
                strahl(lw, see, farbe & 0xB0B0B0 | 0x303030, 1.8f);
                strahl(lw, land, farbe, 2.8f);
                if (!land.isEmpty()) { float[] v = land.get(land.size() - 1); ziel.add(v[0]); ziel.add(v[1] + 2); ziel.add(v[2]); }
            }
            float[] pa = new float[ziel.size()];
            for (int k = 0; k < pa.length; k++) pa[k] = ziel.get(k);
            if (pa.length >= 3) lz.points(pa, null, 0x70FF90, 11);
        }
        WegNetz wt = new WegNetz();
        File t = new File(dir, "WEG.txt");
        if (t.isFile()) wt.text = java.nio.file.Files.readString(t.toPath());
        WegNetz lt = new WegNetz();
        File l2 = new File(dir, "LANDFLUCHT.txt");
        if (l2.isFile()) lt.text = java.nio.file.Files.readString(l2.toPath());
        grundWeg = klassenBild(daten, K, 2, 0.9, false);
        grundWegGrau = klassenBild(daten, K, 2, 0.9, true);
        synchronized (viewWeg) {
            for (NetLayer[] x : new NetLayer[][]{{stundeL, st}, {washburnL, wb}, {wegpunktL, wp}, {landwegL, lw}, {landzielL, lz}}) { x[0].clear(); x[0].items.addAll(x[1].items); }
        }
        wegText = wt.text;
        landText = lt.text;
    }

    // ------------------------------------------------------------------ Phase 17: LB_NET_SICHT

    /** Baut die Ebenen von LB_NET_SICHT (im Hintergrund, einmal): Punkte nach Art, alle Sichtlinien, Spannbaum, Rundflug. */
    public void bauenSicht(File daten) throws java.io.IOException {
        com.dan.lituya.scene.SichtNetz w = com.dan.lituya.scene.SichtNetz.lesen(daten);
        NetLayer li = new NetLayer("Sichtlinien"), sb = new NetLayer("Spannbaum"), rf = new NetLayer("Rundflug"), pu = new NetLayer("Kamerapunkte");
        for (int e = 0; e < w.m; e++) li.line(new float[]{(float) w.x(w.von[e]), (float) w.hoehe[w.von[e]], (float) w.z(w.von[e]), (float) w.x(w.nach[e]), (float) w.hoehe[w.nach[e]], (float) w.z(w.nach[e])}, 0x5A6A78, 0.7f);
        File dir = new File(daten, com.dan.lituya.scene.SichtNetz.ORDNER);
        File sf = new File(dir, "spannbaum.tsv");
        if (sf.isFile())
            for (String[] r : WellenNetz.tsv(sf)) {
                int e = Integer.parseInt(r[0]) - 1;
                sb.line(new float[]{(float) w.x(w.von[e]), (float) w.hoehe[w.von[e]] + 1, (float) w.z(w.von[e]), (float) w.x(w.nach[e]), (float) w.hoehe[w.nach[e]] + 1, (float) w.z(w.nach[e])}, 0x40D8FF, 1.6f);
            }
        double[][] fo = com.dan.lituya.scene.SichtNetz.fahrt(daten, "rundflug.tsv");
        if (fo != null) {
            float[] xyz = new float[3 * fo.length];
            for (int q = 0; q < fo.length; q++) { int k = (int) fo[q][0]; xyz[3 * q] = (float) w.x(k); xyz[3 * q + 1] = (float) w.hoehe[k] + 2; xyz[3 * q + 2] = (float) w.z(k); }
            linie(rf, xyz, 0xFF9A30, 2.6f);
        }
        for (int a = 0; a < com.dan.lituya.scene.SichtNetz.ARTEN.length; a++) {
            List<Float> p = new ArrayList<>();
            for (int k = 0; k < w.n; k++)
                if (com.dan.lituya.scene.SichtNetz.ARTEN[a].equals(w.art[k])) { p.add((float) w.x(k)); p.add((float) w.hoehe[k] + 3); p.add((float) w.z(k)); }
            float[] pa = new float[p.size()];
            for (int k = 0; k < pa.length; k++) pa[k] = p.get(k);
            if (pa.length >= 3) pu.points(pa, null, ART_RGB[a], a == 0 || a == 1 ? 11 : 8);
        }
        BufferedImage g = grundWasser;
        grundSicht = g != null ? g : klassenBild(daten, Klassen.SZENE, 1, 0.62, true);
        synchronized (viewSicht) {
            for (NetLayer[] x : new NetLayer[][]{{sichtLinienL, li}, {spannbaumL, sb}, {rundflugL, rf}, {sichtPunkteL, pu}}) { x[0].clear(); x[0].items.addAll(x[1].items); }
        }
        sicht = w;
    }

    BufferedImage karteSicht(int bw, int bh) {
        BufferedImage im = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        com.dan.lituya.scene.SichtNetz w = sicht;
        Graphics2D g = kopf(im, grundSicht, "NETZBLICK · LB_NET_SICHT · Szene", w == null ? "" : w.wert("netz") + " · Kosten = Flugzeit der Kamera");
        List<Object[]> z = new ArrayList<>();
        if (w != null) {
            z.add(new Object[]{"Rundflug (tsp): " + w.wert("rundflug"), new Color(0xFF9A30)});
            z.add(new Object[]{"Spannbaum (mcst): " + w.wert("spannbaum"), new Color(0x40D8FF)});
            int[] c = new int[com.dan.lituya.scene.SichtNetz.ARTEN.length];
            for (int k = 0; k < w.n; k++) c[java.util.Arrays.asList(com.dan.lituya.scene.SichtNetz.ARTEN).indexOf(w.art[k])]++;
            StringBuilder b = new StringBuilder();
            for (int a = 0; a < c.length; a++) b.append(a > 0 ? " · " : "").append(com.dan.lituya.scene.SichtNetz.ARTEN[a].charAt(0)).append(com.dan.lituya.scene.SichtNetz.ARTEN[a].substring(1).toLowerCase(Locale.ROOT)).append(' ').append(c[a]);
            z.add(new Object[]{b.toString(), Color.WHITE});
        }
        zeilen(g, bw, bh, z);
        g.dispose();
        return im;
    }

    /** Ein Strahl (wächst mit der Uhr) aus Punkten {x, y, z, Zeit}, in Stücken. */
    static void strahl(NetLayer l, List<float[]> p, int rgb, float breite) {
        if (p.size() < 2) return;
        for (int a = 0; a < p.size() - 1; a += 63) {
            int b = Math.min(p.size(), a + 64);
            float[] xyz = new float[3 * (b - a)], t = new float[b - a];
            for (int k = a; k < b; k++) { float[] v = p.get(k); xyz[3 * (k - a)] = v[0]; xyz[3 * (k - a) + 1] = v[1]; xyz[3 * (k - a) + 2] = v[2]; t[k - a] = v[3]; }
            l.ray(xyz, t, rgb, breite);
        }
    }

    /** Bilinear aus einem Klassenraster (Szene x, z) oder 0. */
    static double raster(float[] z, Klassen.Raster r, double x, double zz) {
        if (z == null) return 0;
        double fi = (x + Site.E0 - r.e0()) / r.zelle() - 0.5, fj = (r.n0() - (Site.N0 - zz)) / r.zelle() - 0.5;
        int i = (int) Math.floor(fi), j = (int) Math.floor(fj);
        if (i < 0 || j < 0 || i + 1 >= r.w() || j + 1 >= r.h()) return 0;
        double a = fi - i, b = fj - j;
        double v = (1 - a) * (1 - b) * z[j * r.w() + i] + a * (1 - b) * z[j * r.w() + i + 1] + (1 - a) * b * z[(j + 1) * r.w() + i] + a * b * z[(j + 1) * r.w() + i + 1];
        return Double.isNaN(v) ? 0 : v;
    }

    static int zelle(double e, double n) {
        int i = (int) Math.floor((e - WellenNetz.E_NW) / WellenNetz.ZELLE), j = (int) Math.floor((WellenNetz.N_NW - n) / WellenNetz.ZELLE);
        return j * WellenNetz.BREITE + i;
    }

    void init(GL4 gl) {
        view.init(gl);
        for (NetLayer l : new NetLayer[]{uferL, iso, ray, quelle, auflauf}) view.add(l);
        viewWasser.init(gl);
        for (NetLayer l : new NetLayer[]{einzugL, eisL, baecheL, zielL}) viewWasser.add(l);
        viewWeg.init(gl);
        for (NetLayer l : new NetLayer[]{stundeL, washburnL, landwegL, wegpunktL, landzielL}) viewWeg.add(l);
        viewSicht.init(gl);
        for (NetLayer l : new NetLayer[]{sichtLinienL, spannbaumL, rundflugL, sichtPunkteL}) viewSicht.add(l);
        init = true;
    }

    /** Zeit der Welle (s nach dem Einschlag) aus der Simulationsuhr; vor dem Einschlag negativ. */
    public double zeit(double simSeconds) { return simSeconds - WellenNetz.START_S; }

    /** Im Bildlauf vor dem Zeichnen: Kostenart gewechselt? Sichtbarkeit, Zeit, Pegel. */
    void schritt(double simSeconds, double pegel) {
        this.pegel = pegel;
        this.simSeconds = simSeconds;
        double t = zeit(simSeconds);
        zeit = ganz ? 1e9 : t;
        if (welle != null && gebaut != kosten && !baut) {
            int a = kosten;
            baut = true;
            Thread th = new Thread(() -> { baut = false; bauen(a); }, "Netzblick bauen");
            th.setDaemon(true);
            th.start();
        }
        boolean an = ganz || t >= 0;
        iso.visible = isochronen && an;
        ray.visible = strahlen && an;
        quelle.visible = isochronen;
        uferL.visible = ufer;
        auflauf.visible = ufer;
        baecheL.visible = baeche;
        eisL.visible = gletscher;
        einzugL.visible = einzug;
        zielL.visible = einzug || baeche;
        stundeL.visible = stunde;
        washburnL.visible = washburn;
        wegpunktL.visible = washburn;
        landwegL.visible = landflucht;
        landzielL.visible = landflucht;
        sichtLinienL.visible = sichtlinien;
        spannbaumL.visible = spannbaum;
        rundflugL.visible = rundflug;
    }

    /** Ansicht, Sperre und Zeit des gewählten Netzes. */
    private NetviewGl ansicht() { return netz == 1 ? viewWasser : netz == 2 ? viewWeg : netz == 3 ? viewSicht : view; }
    private Object sperre() { return netz == 1 ? viewWasser : netz == 2 ? viewWeg : netz == 3 ? viewSicht : iso; }
    private double zeitJe() { return netz == 1 || netz == 3 ? 1e9 : netz == 2 ? simSeconds : zeit; }
    private boolean geladen() { return bereit(netz); }

    /** In der Szene: in den HDR-Puffer nach dem Wasser, Tiefe gegen das Gelände. */
    void szene(GL4 gl, float[] vp, double[] eye, int w, int h, boolean reversedZ, double exposure) {
        if (!init || !inSzene || !geladen()) return;
        NetviewGl v = ansicht();
        synchronized (sperre()) {
            v.gain = (float) (1 / Math.max(1e-3, exposure));
            v.depthBias = reversedZ ? -0.0005 : 0.0005;
            gl.glDepthFunc(reversedZ ? GL.GL_GEQUAL : GL.GL_LEQUAL);
            // die Welle liegt auf dem Pegel, Wasser und Weg auf dem Gelände (Höhen schon in den Ebenen)
            if (netz == 0) v.draw(gl, verschoben(vp, pegel), new double[]{eye[0], eye[1] - pegel, eye[2]}, w, h, zeit);
            else v.draw(gl, vp, eye, w, h, zeitJe());
            gl.glDisable(GL.GL_DEPTH_TEST);
        }
    }

    /** VP mit dem Pegel als Höhe 0 der Ebenen (die Linien liegen ueberWasser darüber). */
    static float[] verschoben(float[] vp, double pegel) {
        float[] m = vp.clone();
        // m · T(0, pegel, 0): vierte Spalte += zweite Spalte · pegel
        for (int r = 0; r < 4; r++) m[12 + r] += (float) (vp[4 + r] * pegel);
        return m;
    }

    /** Bild im Bild unten rechts im Fenster (nach der Nachbearbeitung). */
    void bildImBild(GL4 gl, Shader overlay, int vao, int surfW, int surfH, String zeitText) {
        if (!init || !bildImBild) return;
        int nz = netz;
        double[] au = ausschnitt(nz);
        double asp = (au[3] - au[2]) / (au[1] - au[0]);
        int bw0 = Math.max(240, (int) (surfW * 0.30)), bh0 = (int) Math.round(bw0 * asp);
        if (bh0 > surfH * 0.55) { bh0 = (int) (surfH * 0.55); bw0 = (int) Math.round(bh0 / asp); }
        int bw = bw0, bh = bh0;
        int x = surfW - bw - 14, y = 14;
        karte.refresh(gl, 250, () -> karte(bw, bh, zeitText), karte.width != bw || karte.height != bh || karteNetz != nz);
        karteNetz = nz;
        gl.glEnable(GL.GL_SCISSOR_TEST);
        gl.glScissor(x - 2, y - 2, bw + 4, bh + 4);
        gl.glClearColor(0.9f, 0.78f, 0.45f, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glScissor(x, y, bw, bh);
        gl.glClearColor(0.04f, 0.06f, 0.08f, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glDisable(GL.GL_SCISSOR_TEST);
        karte.draw(gl, overlay, vao, x, y, surfW, surfH);
        if (geladen()) {
            NetviewGl v = ansicht();
            synchronized (sperre()) {
                gl.glViewport(x, y, bw, bh);
                gl.glDepthFunc(GL.GL_ALWAYS);
                v.gain = 1;
                v.depthBias = 0;
                double[] a = Site.fromUtm(au[0], au[3]), b = Site.fromUtm(au[1], au[2]);
                double hoch = nz == 0 ? 3000 : nz == 1 || nz == 3 ? 9000 : 5000;
                v.draw(gl, ortho(a[0], b[0], a[1], b[1]), new double[]{(a[0] + b[0]) / 2, hoch, (a[1] + b[1]) / 2}, bw, bh, nz == 0 ? zeit : zeitJe());
                gl.glDisable(GL.GL_DEPTH_TEST);
                gl.glDepthFunc(GL.GL_LESS);
                gl.glViewport(0, 0, surfW, surfH);
            }
        }
    }

    /** Draufsicht: x von x0 bis x1 nach links/rechts, z von z0 (Nord, oben) bis z1 (Süd, unten); Spaltenordnung. */
    static float[] ortho(double x0, double x1, double z0, double z1) {
        float[] m = new float[16];
        m[0] = (float) (2 / (x1 - x0));
        m[12] = (float) (-(x1 + x0) / (x1 - x0));
        m[9] = (float) (-2 / (z1 - z0));
        m[13] = (float) ((z1 + z0) / (z1 - z0));
        m[15] = 1;
        return m;
    }

    private int karteNetz = -1;

    /** Die Karte unter dem Bild im Bild: Grund, Titel, Zeit, Legende (je Netz). */
    BufferedImage karte(int bw, int bh, String zeitText) {
        if (netz == 1) return karteWasser(bw, bh);
        if (netz == 2) return karteWeg(bw, bh);
        if (netz == 3) return karteSicht(bw, bh);
        BufferedImage im = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        BufferedImage gr = grund;
        if (gr != null) g.drawImage(gr, 0, 0, bw, bh, null);
        else { g.setColor(new Color(10, 15, 20)); g.fillRect(0, 0, bw, bh); }
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, bw, 38);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString("NETZBLICK · LB_NET_WELLE · " + WellenNetz.KOSTEN_NAME[kosten], 8, 16);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(new Color(230, 230, 230));
        g.drawString(zeitText, 8, 31);
        WellenNetz w = welle;
        if (w != null) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            int yy = bh - 8;
            for (int i = WellenNetz.ORTE.length - 1; i >= 1; i--) {
                Object[] o = WellenNetz.ORTE[i];
                double t = w.ankunftBei(kosten, (double) o[1], (double) o[2], (double) o[3]);
                String s = String.format(Locale.GERMAN, "%s %.0f s", o[0], t);
                g.setColor(new Color(0, 0, 0, 140));
                g.fillRect(6, yy - 11, g.getFontMetrics().stringWidth(s) + 6, 14);
                g.setColor(new Color(255, 200, 170));
                g.drawString(s, 9, yy);
                yy -= 15;
            }
        }
        g.dispose();
        return im;
    }

    /** Der Grund der Bucht für die Karte (einmal): Tiefe aus dem Netz, Land dunkel, Ufer. */
    static BufferedImage grundbild(WellenNetz w, UferNetz u) {
        int iw = (int) ((AE1 - AE0) / WellenNetz.ZELLE), ih = (int) ((AN1 - AN0) / WellenNetz.ZELLE);
        BufferedImage im = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
        int i0 = (int) ((AE0 - WellenNetz.E_NW) / WellenNetz.ZELLE), j0 = (int) ((WellenNetz.N_NW - AN1) / WellenNetz.ZELLE);
        for (int j = 0; j < ih; j++)
            for (int i = 0; i < iw; i++) {
                int id = w.knotenJeZelle[(j0 + j) * WellenNetz.BREITE + i0 + i];
                int rgb;
                if (id == 0) rgb = 0xFF262B25;
                else {
                    double t = Math.min(1, w.tiefeHeute[id - 1] / 220);
                    rgb = 0xFF000000 | ((int) (34 - 26 * t) << 16) | ((int) (78 - 44 * t) << 8) | (int) (104 - 36 * t);
                }
                im.setRGB(i, j, rgb);
            }
        return im;
    }

    /** Grund, Titel und Zeile der Karte (gemeinsam für Wasser und Weg). */
    private Graphics2D kopf(BufferedImage im, BufferedImage gr, String titel, String zeile) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int bw = im.getWidth(), bh = im.getHeight();
        if (gr != null) g.drawImage(gr, 0, 0, bw, bh, null);
        else { g.setColor(new Color(10, 15, 20)); g.fillRect(0, 0, bw, bh); }
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, bw, 38);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString(titel, 8, 16);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(new Color(230, 230, 230));
        g.drawString(zeile, 8, 31);
        return g;
    }

    /** Zeilen unten links auf dunklem Grund (von unten nach oben), auf die Breite gekürzt. */
    static void zeilen(Graphics2D g, int bw, int bh, List<Object[]> z) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        int yy = bh - 8;
        for (int i = z.size() - 1; i >= 0; i--) {
            String s = kurz(g, (String) z.get(i)[0], bw - 30);
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(6, yy - 11, g.getFontMetrics().stringWidth(s) + 18, 14);
            g.setColor((Color) z.get(i)[1]);
            g.fillRect(9, yy - 8, 8, 8);
            g.setColor(new Color(235, 235, 235));
            g.drawString(s, 21, yy);
            yy -= 15;
        }
    }

    static String kurz(Graphics2D g, String s, int w) {
        if (s == null) return "–";
        if (g.getFontMetrics().stringWidth(s) <= w) return s;
        while (s.length() > 4 && g.getFontMetrics().stringWidth(s + " …") > w) s = s.substring(0, s.length() - 1);
        return s + " …";
    }

    BufferedImage karteWasser(int bw, int bh) {
        BufferedImage im = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        double r = regen;
        Graphics2D g = kopf(im, grundWasser, "NETZBLICK · LB_NET_WASSER · Szene",
                String.format(Locale.GERMAN, "Regen %.0f mm/h · Abfluss = Fläche × Regen × Beiwert + Mittelwasser [G]", r));
        List<Object[]> z = new ArrayList<>();
        for (int q = 0; q < 4; q++) {
            double[] a = zuflussZiel(q, r);
            if (Double.isNaN(a[0]) || a[1] <= 0) continue;
            z.add(new Object[]{String.format(Locale.GERMAN, "%s: %,.0f m³/s aus %,.0f km²", WasserNetz.ZIELE[q], a[0], a[1]), new Color(ZIEL_RGB[q])});
        }
        WasserNetz w = wasser;
        if (w != null && w.wert("einzug.lituya_bay") != null) z.add(new Object[]{"Lituya Bay: " + w.wert("einzug.lituya_bay"), new Color(0x4FA8FF)});
        zeilen(g, bw, bh, z);
        g.dispose();
        return im;
    }

    BufferedImage karteWeg(int bw, int bh) {
        BufferedImage im = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        WegNetz wt = new WegNetz(), lt = new WegNetz();
        wt.text = wegText;
        lt.text = landText;
        Graphics2D g = kopf(im, klassen ? grundWeg : grundWegGrau, "NETZBLICK · LB_NET_WEG · Kern",
                String.format(Locale.GERMAN, "t = %+.0f s nach dem Stoß · Gehzeit 15 / 30 / 45 / 60 min vom Strand", simSeconds));
        List<Object[]> z = new ArrayList<>();
        z.add(new Object[]{"1 h Anchorage Cove: " + wt.wert("stunde.badger"), new Color(0xFFE65A)});
        z.add(new Object[]{"1 h Edrie: " + wt.wert("stunde.edrie"), new Color(0xF06E3C)});
        z.add(new Object[]{"Washburn 1934: " + wt.wert("washburn"), new Color(0xFF503C)});
        z.add(new Object[]{"an Land Edrie: " + lt.wert("landflucht.edrie.text"), new Color(0xFFC85A)});
        z.add(new Object[]{"an Land Badger: " + lt.wert("landflucht.badger.text"), new Color(0x78C8FF)});
        zeilen(g, bw, bh, z);
        g.dispose();
        return im;
    }

    String info() {
        if (netz == 1) return standWasser + String.format(Locale.GERMAN, " · Regen %.0f mm/h", regen);
        if (netz == 2) return standWeg + String.format(Locale.GERMAN, " · t = %+.0f s nach dem Stoß", simSeconds);
        if (netz == 3) return standSicht;
        return stand + (welle != null ? String.format(Locale.GERMAN, " · %s, Welle t = %.0f s", WellenNetz.KOSTEN_NAME[kosten], zeit) : "");
    }

    void dispose(GL4 gl) { if (init) { view.dispose(gl); viewWasser.dispose(gl); viewWeg.dispose(gl); viewSicht.dispose(gl); } karte.dispose(gl); }
}
