package com.dan.lituya.gl;

import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.UferNetz;
import com.dan.lituya.scene.WellenNetz;
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
 */
public final class Netzblick {
    /** Ausschnitt der Draufsicht (UTM). */
    static final double AE0 = 342_800, AE1 = 358_000, AN0 = 6_498_400, AN1 = 6_507_600;

    public volatile boolean bildImBild = true, inSzene = false, ganz = false, isochronen = true, strahlen = true, ufer = true;
    /** Kostenart 0 … 3 ({@link WellenNetz#KOSTEN}). */
    public volatile int kosten = 0;

    private final NetviewGl view = new NetviewGl();
    private final NetLayer iso = new NetLayer("Isochronen"), ray = new NetLayer("Wellenstrahlen"), quelle = new NetLayer("Einschlag"),
            uferL = new NetLayer("Ufer"), auflauf = new NetLayer("Auflauf");
    private final HudOverlay karte = new HudOverlay();
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

    public String stand() { return stand; }
    public WellenNetz welle() { return welle; }
    public UferNetz ufer() { return uferNetz; }

    /** Lädt die Netze im Hintergrund (einmal beim Start). */
    public void laden(File daten) {
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

    static int zelle(double e, double n) {
        int i = (int) Math.floor((e - WellenNetz.E_NW) / WellenNetz.ZELLE), j = (int) Math.floor((WellenNetz.N_NW - n) / WellenNetz.ZELLE);
        return j * WellenNetz.BREITE + i;
    }

    void init(GL4 gl) {
        view.init(gl);
        for (NetLayer l : new NetLayer[]{uferL, iso, ray, quelle, auflauf}) view.add(l);
        init = true;
    }

    /** Zeit der Welle (s nach dem Einschlag) aus der Simulationsuhr; vor dem Einschlag negativ. */
    public double zeit(double simSeconds) { return simSeconds - WellenNetz.START_S; }

    /** Im Bildlauf vor dem Zeichnen: Kostenart gewechselt? Sichtbarkeit, Zeit, Pegel. */
    void schritt(double simSeconds, double pegel) {
        this.pegel = pegel;
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
    }

    /** In der Szene: in den HDR-Puffer nach dem Wasser, Tiefe gegen das Gelände. */
    void szene(GL4 gl, float[] vp, double[] eye, int w, int h, boolean reversedZ, double exposure) {
        if (!init || !inSzene || welle == null) return;
        synchronized (iso) {
            view.gain = (float) (1 / Math.max(1e-3, exposure));
            view.depthBias = reversedZ ? -0.0005 : 0.0005;
            gl.glDepthFunc(reversedZ ? GL.GL_GEQUAL : GL.GL_LEQUAL);
            float[] m = verschoben(vp, pegel);
            view.draw(gl, m, new double[]{eye[0], eye[1] - pegel, eye[2]}, w, h, zeit);
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
        int bw = Math.max(240, (int) (surfW * 0.30)), bh = (int) Math.round(bw * (AN1 - AN0) / (AE1 - AE0));
        int x = surfW - bw - 14, y = 14;
        karte.refresh(gl, 250, () -> karte(bw, bh, zeitText), karte.width != bw || karte.height != bh);
        gl.glEnable(GL.GL_SCISSOR_TEST);
        gl.glScissor(x - 2, y - 2, bw + 4, bh + 4);
        gl.glClearColor(0.9f, 0.78f, 0.45f, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glScissor(x, y, bw, bh);
        gl.glClearColor(0.04f, 0.06f, 0.08f, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glDisable(GL.GL_SCISSOR_TEST);
        karte.draw(gl, overlay, vao, x, y, surfW, surfH);
        if (welle != null) {
            synchronized (iso) {
                gl.glViewport(x, y, bw, bh);
                gl.glDepthFunc(GL.GL_ALWAYS);
                view.gain = 1;
                view.depthBias = 0;
                double[] a = Site.fromUtm(AE0, AN1), b = Site.fromUtm(AE1, AN0);
                view.draw(gl, ortho(a[0], b[0], a[1], b[1]), new double[]{(a[0] + b[0]) / 2, 3000, (a[1] + b[1]) / 2}, bw, bh, zeit);
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

    /** Die Karte unter dem Bild im Bild: Grund, Titel, Zeit, Legende. */
    BufferedImage karte(int bw, int bh, String zeitText) {
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

    String info() {
        return stand + (welle != null ? String.format(Locale.GERMAN, " · %s, Welle t = %.0f s", WellenNetz.KOSTEN_NAME[kosten], zeit) : "");
    }

    void dispose(GL4 gl) { if (init) view.dispose(gl); karte.dispose(gl); }
}
