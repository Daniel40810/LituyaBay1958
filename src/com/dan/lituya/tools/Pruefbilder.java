package com.dan.lituya.tools;

import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Stufe;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Die Prüfbilder der Aufbereitung (Werkbuch 3.4, Phase 2) nach {@code docs/status/}: Schummerung je Stufe mit
 * Höhenfarben (Wasser nach Tiefe), der Grund heute als Tiefenkarte wie {@code images/Kern_BlueTopo.png} und die Änderung
 * des Grunds von 1940 bis 1959 (der Schuttfächer im Gilbert Inlet). Die Ränder von Kern und Szene und die Orte sind
 * eingezeichnet, damit man die Lage sofort prüfen kann.
 */
public final class Pruefbilder {
    private Pruefbilder() { }

    /** Eine Stufe ganz, verkleinert auf höchstens breit Bildpunkte. */
    public static File stufe(Stufe s, File daten, File ziel, int breit) throws IOException {
        int nx = s.nx() * Stufe.N, ny = s.ny() * Stufe.N, step = Math.max(1, (int) Math.ceil(nx / (double) breit));
        int w = nx / step, h = ny / step;
        float[] v = new float[w * h];
        // kachelweise, damit nicht alle Kacheln zugleich im Speicher liegen
        for (int tj = 0; tj < s.ny(); tj++)
            for (int ti = 0; ti < s.nx(); ti++) {
                double e = s.gebiet.eMin() + ti * s.kachel(), n = s.gebiet.nMin() + (s.ny() - 1 - tj) * s.kachel();
                float[] k = Stufe.lesen(s.datei(daten, e, n));
                for (int j = 0; j < h; j++) {
                    int gj = j * step + step / 2;
                    if (gj / Stufe.N != tj) continue;
                    for (int i = 0; i < w; i++) {
                        int gi = i * step + step / 2;
                        if (gi / Stufe.N != ti) continue;
                        v[j * w + i] = k == null ? Float.NaN : k[(gj - tj * Stufe.N) * Stufe.N + (gi - ti * Stufe.N)];
                    }
                }
            }
        double zelle = s.zelle * step;
        BufferedImage img = schummerung(v, w, h, zelle, Double.NaN);
        double e0 = s.gebiet.eMin(), nTop = s.gebiet.nMin() + s.ny() * s.kachel();
        rahmen(img, e0, nTop, zelle, s);
        beschriften(img, String.format(Locale.GERMAN, "LituyaBay1958 · Phase 2 · Gelände %s · %s m je Zelle, Bild %s m je Punkt · NAVD88", s, Gelaende.fmt(s.zelle), Gelaende.fmt(zelle)));
        File f = new File(ziel, "Phase2_Gelaende_" + s.name().toLowerCase(Locale.ROOT) + ".png");
        f.getParentFile().mkdirs();
        javax.imageio.ImageIO.write(img, "png", f);
        return f;
    }

    /** Ein Ausschnitt einer Stufe in voller Auflösung (Nahprüfung, etwa der Gilbert Inlet im Kern). */
    public static File ausschnitt(Stufe s, File daten, File ziel, String name, double e0, double nTop, int w, int h, double zelle) throws IOException {
        StufenQuelle q = new StufenQuelle(s, daten);
        float[] v = new float[w * h];
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) v[j * w + i] = q.wert(e0 + (i + 0.5) * zelle, nTop - (j + 0.5) * zelle);
        BufferedImage img = schummerung(v, w, h, zelle, Double.NaN);
        rahmen(img, e0, nTop, zelle, s);
        beschriften(img, String.format(Locale.GERMAN, "LituyaBay1958 · Phase 2 · %s · %s, %s m je Punkt", name, s, Gelaende.fmt(zelle)));
        File f = new File(ziel, "Phase2_" + name + ".png");
        javax.imageio.ImageIO.write(img, "png", f);
        return f;
    }

    /** Der Grund heute als Tiefenkarte und die Änderung 1940 → 1959. */
    public static void grund(File daten, File ziel) throws IOException {
        File dir = new File(daten, "grund");
        float[] heute = Stufe.lesen(new File(dir, "heute_4m.r32"));
        if (heute != null) {
            Gitter g = Gitter.ueber(Site.KERN, Grund.ZELLE_HEUTE);
            int step = 2, w = g.w() / step, h = g.h() / step;
            float[] v = new float[w * h];
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) v[j * w + i] = heute[(j * step) * g.w() + i * step];
            BufferedImage img = schummerung(v, w, h, g.zelle() * step, 0);
            rahmen(img, g.e0(), g.n0(), g.zelle() * step, Stufe.KERN);
            beschriften(img, "LituyaBay1958 · Phase 2 · Grund heute (USACE 2019, BlueTopo), NAVD88, 8 m je Punkt");
            javax.imageio.ImageIO.write(img, "png", new File(ziel, "Phase2_Grund_heute.png"));
        }
        float[] a = Stufe.lesen(new File(dir, "1940_20m.r32")), b = Stufe.lesen(new File(dir, "1959_20m.r32"));
        if (a != null && b != null) {
            Gitter g = Gitter.ueber(Site.KERN, Grund.ZELLE_LOT);
            BufferedImage img = new BufferedImage(g.w(), g.h(), BufferedImage.TYPE_INT_RGB);
            for (int j = 0; j < g.h(); j++)
                for (int i = 0; i < g.w(); i++) {
                    int k = j * g.w() + i;
                    float d = b[k] - a[k];
                    int rgb;
                    if (Float.isNaN(d)) rgb = Float.isNaN(a[k]) && Float.isNaN(b[k]) ? 0x1c2228 : 0x3a4048;
                    else {
                        double t = Math.max(-1, Math.min(1, d / 40));
                        int c0 = 235;
                        rgb = t >= 0 ? rgb(c0, (int) (c0 - t * 190), (int) (c0 - t * 200)) : rgb((int) (c0 + t * 200), (int) (c0 + t * 120), c0);
                    }
                    img.setRGB(i, j, rgb);
                }
            rahmen(img, g.e0(), g.n0(), g.zelle(), Stufe.KERN);
            beschriften(img, "LituyaBay1958 · Phase 2 · Grund 1959 − 1940: rot höher (Schutt), blau tiefer, ±40 m");
            javax.imageio.ImageIO.write(img, "png", new File(ziel, "Phase2_Grund_1940_1959.png"));
        }
    }

    static int rgb(int r, int g, int b) { return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /** Höhenfarben mit Schummerung (Licht aus Nordwest, 45°); unter wasserUnter (oder wo es NaN ist) Wasser nach Tiefe. */
    static BufferedImage schummerung(float[] v, int w, int h, double zelle, double wasserUnter) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double lx = -Math.sin(Math.toRadians(45)) * Math.cos(Math.toRadians(45)), ly = Math.cos(Math.toRadians(45)) * Math.cos(Math.toRadians(45)),
                lz = Math.sin(Math.toRadians(45));
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                float z = v[j * w + i];
                if (Float.isNaN(z)) { img.setRGB(i, j, 0x502030); continue; }
                float zl = at(v, w, h, i - 1, j, z), zr = at(v, w, h, i + 1, j, z), zu = at(v, w, h, i, j - 1, z), zd = at(v, w, h, i, j + 1, z);
                double dx = (zr - zl) / (2 * zelle), dy = (zu - zd) / (2 * zelle);
                double nx = -dx, ny = -dy, nz = 1, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double sh = Math.max(0, (nx * lx + ny * ly + nz * lz) / nl);
                int[] c = farbe(z, !Double.isNaN(wasserUnter) && z <= wasserUnter);
                double f = 0.35 + 0.75 * sh;
                img.setRGB(i, j, rgb((int) (c[0] * f), (int) (c[1] * f), (int) (c[2] * f)));
            }
        return img;
    }

    static float at(float[] v, int w, int h, int i, int j, float dflt) {
        if (i < 0 || j < 0 || i >= w || j >= h) return dflt;
        float z = v[j * w + i];
        return Float.isNaN(z) ? dflt : z;
    }

    /** Farbe nach Höhe: Wasser blau nach Tiefe, Land grün, braun, grau, Schnee weiß. */
    static int[] farbe(double z, boolean wasser) {
        if (z < 0 || wasser) {
            double t = Math.min(1, Math.max(0, -z / 250));
            return new int[]{(int) (150 - 125 * t), (int) (205 - 140 * t), (int) (215 - 90 * t)};
        }
        double[][] st = {{0, 120, 160, 100}, {150, 90, 135, 80}, {600, 150, 140, 105}, {1500, 165, 160, 155}, {2500, 230, 230, 235}, {5000, 255, 255, 255}};
        for (int k = 0; k < st.length - 1; k++)
            if (z <= st[k + 1][0]) {
                double t = (z - st[k][0]) / (st[k + 1][0] - st[k][0]);
                return new int[]{(int) (st[k][1] + t * (st[k + 1][1] - st[k][1])), (int) (st[k][2] + t * (st[k + 1][2] - st[k][2])), (int) (st[k][3] + t * (st[k + 1][3] - st[k][3]))};
            }
        return new int[]{255, 255, 255};
    }

    /** Ränder von Kern und Szene (gold, weiß) und die Orte (Punkte) über ein Bild mit Nordwestecke (e0, nTop). */
    static void rahmen(BufferedImage img, double e0, double nTop, double zelle, Stufe s) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(Math.max(1f, img.getWidth() / 1200f)));
        for (Site.Fenster f : new Site.Fenster[]{Site.SZENE, Site.KERN}) {
            g.setColor(f == Site.KERN ? new Color(240, 190, 90) : new Color(240, 240, 240));
            int x0 = (int) ((f.eMin() - e0) / zelle), x1 = (int) ((f.eMax() - e0) / zelle), y0 = (int) ((nTop - f.nMax()) / zelle), y1 = (int) ((nTop - f.nMin()) / zelle);
            g.drawRect(x0, y0, x1 - x0, y1 - y0);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, img.getWidth() / 140)));
        for (Site.Ort o : Site.ORTE) {
            int x = (int) ((o.e() - e0) / zelle), y = (int) ((nTop - o.n()) / zelle);
            if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) continue;
            g.setColor(o.art() == Site.Art.SPORN ? new Color(240, 80, 60) : new Color(255, 255, 255));
            g.fillOval(x - 3, y - 3, 6, 6);
            g.setColor(new Color(20, 20, 20, 200));
            g.drawString(o.name(), x + 6, y + 5);
            g.setColor(Color.WHITE);
            g.drawString(o.name(), x + 5, y + 4);
        }
        g.dispose();
    }

    static void beschriften(BufferedImage img, String text) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int fs = Math.max(12, img.getWidth() / 90);
        g.setFont(new Font("SansSerif", Font.BOLD, fs));
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, img.getWidth(), fs + 12);
        g.setColor(Color.WHITE);
        g.drawString(text, 8, fs + 4);
        g.dispose();
    }

    /** Alle Prüfbilder, soweit die Daten da sind. */
    public static void alle(File daten, File ziel, Rohdaten r) throws IOException {
        ziel.mkdirs();
        for (Stufe s : new Stufe[]{Stufe.FERN, Stufe.SZENE, Stufe.KERN})
            if (new File(s.ordner(daten), "STUFE.txt").isFile()) r.log("  Prüfbild %s", stufe(s, daten, ziel, 2400).getPath());
        if (new File(Stufe.KERN.ordner(daten), "STUFE.txt").isFile()) {
            // Gilbert Inlet mit dem Sporn und der Wand des Felssturzes: 4 × 3 km zu 1 m
            r.log("  Prüfbild %s", ausschnitt(Stufe.KERN, daten, ziel, "Gilbert_Inlet", 352_900, 6_508_500, 4000, 3000, 1.0).getPath());
            // der Eingang mit La Chaussee Spit und Anchorage Cove: 4 × 3 km zu 1 m
            r.log("  Prüfbild %s", ausschnitt(Stufe.KERN, daten, ziel, "Eingang", 343_200, 6_502_500, 4000, 3000, 1.0).getPath());
        }
        if (Files.isDirectory(new File(daten, "grund").toPath())) { grund(daten, ziel); r.log("  Prüfbilder Grund in %s", ziel.getPath()); }
    }
}
