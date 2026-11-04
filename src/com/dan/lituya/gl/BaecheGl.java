package com.dan.lituya.gl;

import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.WasserNetz;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.File;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Die Bäche in der Szene (Phase 16c, eigener Wasser-Pass): die Kanten {@code BACH} und {@code BACH_EIS} der Ebene 1 von
 * {@code LB_NET_WASSER} ({@code daten/netz/wasser/linien.tsv}) als Bänder auf dem Gelände. Die Breite folgt dem Abfluss
 * {@link WasserNetz#abfluss} (Fläche × Regen × Beiwert über dem Mittelwasser) und der hydraulischen Geometrie
 * {@link WasserNetz#breite}; der Regen kommt vom Schieber im Bedienfeld (0 … 20 mm/h) und wirkt im Shader, ohne die Bänder
 * neu zu bauen. Der Shader ({@code baeche.vert}, {@code baeche.frag}) färbt nach dem Eisanteil (Gletschermilch), lässt
 * die Oberfläche mit der Fließgeschwindigkeit stromab laufen und spiegelt Himmel und Sonne.
 * <p>Die Höhen kommen beim Laden aus dem Kachelstapel des Geländes (5 m), die Erdkrümmung wie in der Clipmap im Shader;
 * in der Ferne hebt ein Versatz zum Auge die Bänder über die gröberen Stufen. Ohne {@code daten/netz/wasser}: keine
 * Bäche (HUD sagt es).
 */
public final class BaecheGl {
    public volatile boolean an = true;
    /** Regen (mm/h), 0 … {@link #REGEN_MAX}. */
    public volatile double regen = 0;
    public static final double REGEN_MAX = 20;
    /** Abstand der Stützpunkte längs der Bänder (m). */
    static final double SCHRITT = 10;
    /** Vier Werte je Scheitel und drei Eingänge: (x, y, z, u), (Seite x, Seite z, ±1, Eis), (Fläche, Gefälle, frei, Zufall). */
    public static final int FLOATS = 12;

    final Shader shader = new Shader("Bäche", null, "baeche.vert", "baeche.frag");
    private volatile float[] scheitel;
    private volatile int[] index;
    private volatile String status = "Bäche: nicht geladen";
    private volatile int baender;
    private volatile double km;
    private int vao, vbo, ibo, nIndex;
    private boolean init, oben;

    public String status() { return status; }

    public int baender() { return baender; }
    public double km() { return km; }
    /** Gebaute Scheitel (vor dem Hochladen) oder null. */
    public float[] scheitel() { return scheitel; }

    /** Laden fertig (mit oder ohne Bäche)? */
    public boolean bereit() { return fertig; }
    private volatile boolean fertig;

    /** Höhe des Geländes in der Szene (x, z) → m über NAVD88 oder NaN. */
    public interface Hoehe { double at(double x, double z); }

    /** Lädt die Bäche im Hintergrund und legt sie auf das Gelände. */
    public void laden(File daten, Hoehe hoehe) {
        Thread t = new Thread(() -> {
            try {
                long t0 = System.nanoTime();
                if (!WasserNetz.da(daten)) { status = "Bäche: kein daten/netz/wasser (ant aufbereitung -Dschritte=wasser)"; return; }
                WasserNetz w = WasserNetz.lesen(daten);
                Map<Integer, double[]> lin = WasserNetz.linien(daten);
                bauen(w, lin, hoehe);
                status = String.format(Locale.GERMAN, "Bäche: %,d Bänder, %,.0f km, geladen in %.1f s", baender, km, (System.nanoTime() - t0) / 1e9);
            } catch (Exception e) {
                status = "Bäche nicht geladen: " + e.getMessage();
            } finally {
                fertig = true;
            }
        }, "Bäche laden");
        t.setDaemon(true);
        t.start();
    }

    /** Baut die Bänder (ohne GL): je Kante ein Streifen, Stützpunkte alle {@link #SCHRITT} m. */
    public void bauen(WasserNetz w, Map<Integer, double[]> lin, Hoehe hoehe) {
        FloatBuilder v = new FloatBuilder();
        IntBuilder ix = new IntBuilder();
        int n = 0;
        double laenge = 0;
        for (int e = 0; e < w.m; e++) {
            if (w.kEbene[e] != 1 || !(WasserNetz.BACH.equals(w.kArt[e]) || WasserNetz.BACH_EIS.equals(w.kArt[e]))) continue;
            double[] en = lin.get(e);
            if (en == null || en.length < 4) en = new double[]{w.ost[w.von[e]], w.nord[w.von[e]], w.ost[w.nach[e]], w.nord[w.nach[e]]};
            double[] p = dicht(en, SCHRITT);
            int np = p.length / 2;
            if (np < 2) continue;
            double eis = w.eisAnteil[w.von[e]];
            if (Double.isNaN(eis)) eis = 0;
            if (WasserNetz.BACH_EIS.equals(w.kArt[e])) eis = Math.max(eis, 0.9);
            float zuf = (float) ((e * 0.6180339887) % 1.0);
            int basis = v.size() / FLOATS;
            double u = 0;
            boolean ok = true;
            for (int q = 0; q < np; q++) {
                double[] s = Site.fromUtm(p[2 * q], p[2 * q + 1]);
                double y = hoehe.at(s[0], s[1]);
                if (Double.isNaN(y)) { ok = false; break; }
                int qa = Math.max(0, q - 1), qb = Math.min(np - 1, q + 1);
                double[] a = Site.fromUtm(p[2 * qa], p[2 * qa + 1]), b = Site.fromUtm(p[2 * qb], p[2 * qb + 1]);
                double dx = b[0] - a[0], dz = b[1] - a[1], l = Math.max(1e-6, Math.hypot(dx, dz));
                double sx = -dz / l, sz = dx / l;
                if (q > 0) { double[] o = Site.fromUtm(p[2 * q - 2], p[2 * q - 1]); u += Math.hypot(s[0] - o[0], s[1] - o[1]); }
                for (int seite = -1; seite <= 1; seite += 2)
                    v.add((float) s[0], (float) y, (float) s[1], (float) u, (float) sx, (float) sz, seite, (float) eis,
                            (float) w.kFlaeche[e], (float) Math.max(0, w.gefaelle[e]), 0, zuf);
            }
            if (!ok) { v.truncate(basis * FLOATS); continue; }
            for (int q = 0; q + 1 < np; q++) {
                int a = basis + 2 * q;
                ix.add(a, a + 1, a + 2, a + 1, a + 3, a + 2);
            }
            n++;
            laenge += u;
        }
        baender = n;
        km = laenge / 1000;
        index = ix.toArray();
        scheitel = v.toArray();
    }

    /** Verdichtet einen Zug auf Stützpunkte höchstens d auseinander. */
    static double[] dicht(double[] en, double d) {
        java.util.List<Double> l = new java.util.ArrayList<>();
        l.add(en[0]); l.add(en[1]);
        for (int k = 2; k + 1 < en.length; k += 2) {
            double ax = en[k - 2], ay = en[k - 1], bx = en[k], by = en[k + 1], L = Math.hypot(bx - ax, by - ay);
            int m = Math.max(1, (int) Math.ceil(L / d));
            for (int i = 1; i <= m; i++) { l.add(ax + (bx - ax) * i / m); l.add(ay + (by - ay) * i / m); }
        }
        double[] r = new double[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    /** Im Bildlauf: laden, sobald die Bänder fertig sind, dann zeichnen (in den gebundenen HDR-Puffer, Tiefe gegen das Gelände). */
    /** W3c: Rückbau des Gilbert Inlet im Bild (Szene x0, z0, x1, z1) und ob er gerade gilt; vom Renderer je Bild gesetzt. */
    volatile double[] rueckbauRect;
    volatile boolean rueckbauAn;

    void zeichnen(GL4 gl, Consumer<Shader> common, com.dan.clipmap.Clipmap cm, boolean reversed, int h, double fovy) {
        if (!an || cm == null) return;
        if (!init) {
            int[] b = new int[3];
            gl.glGenVertexArrays(1, b, 0);
            gl.glGenBuffers(2, b, 1);
            vao = b[0]; vbo = b[1]; ibo = b[2];
            init = true;
        }
        if (!oben && scheitel != null && index != null) {
            float[] s = scheitel;
            int[] ii = index;
            gl.glBindVertexArray(vao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
            FloatBuffer fb = Buffers.newDirectFloatBuffer(s);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) s.length * 4, fb, GL.GL_STATIC_DRAW);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
            IntBuffer ib = Buffers.newDirectIntBuffer(ii);
            gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) ii.length * 4, ib, GL.GL_STATIC_DRAW);
            for (int a = 0; a < 3; a++) {
                gl.glEnableVertexAttribArray(a);
                gl.glVertexAttribPointer(a, 4, GL.GL_FLOAT, false, FLOATS * 4, (long) a * 16);
            }
            gl.glBindVertexArray(0);
            nIndex = ii.length;
            scheitel = null;
            index = null;
            oben = true;
        }
        if (!oben || nIndex == 0) return;
        common.accept(shader);
        shader.set(gl, "uRegen", (float) regen);
        // W3c: im Rückbau des Gilbert Inlet (1958) liegen die Bäche von heute unter Wasser: dort nicht zeichnen
        double[] rr = rueckbauRect;
        shader.set(gl, "uRueckbauAn", rueckbauAn && rr != null ? 1 : 0);
        if (rr != null) {
            shader.set(gl, "uRueckbauRect", (float) rr[0], (float) rr[1], (float) rr[2], (float) rr[3]);
            shader.set(gl, "uRueckbauW", (float) com.dan.lituya.scene.Boden1958.BILD_UNTEN, (float) com.dan.lituya.scene.Boden1958.BILD_OBEN,
                    (float) com.dan.lituya.scene.Boden1958.BILD_UFER);
        }
        shader.set(gl, "uKruemmung", cm.curvatureRadius > 0 ? (float) (1 / (2 * cm.curvatureRadius)) : 0f);
        // Welt je Bildpunkt in 1 m Abstand: 2·tan(fovy/2)/h
        shader.set(gl, "uPx", (float) (2 * Math.tan(Math.toRadians(fovy) / 2) / Math.max(1, h)));
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversed ? GL.GL_GEQUAL : GL.GL_LEQUAL);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, nIndex, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(0);
    }

    /** „Bäche: … · Regen 5 mm/h“ für das HUD. */
    public String info() {
        return status + (an ? String.format(Locale.GERMAN, " · Regen %.0f mm/h", regen) : " · aus");
    }

    void dispose(GL4 gl) {
        if (!init) return;
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
        init = false;
    }

    /** Wachsende float-Liste ohne Kästchen. */
    static final class FloatBuilder {
        float[] a = new float[1 << 16];
        int n;
        void add(float... v) { if (n + v.length > a.length) a = java.util.Arrays.copyOf(a, Math.max(a.length * 2, n + v.length)); System.arraycopy(v, 0, a, n, v.length); n += v.length; }
        int size() { return n; }
        void truncate(int m) { n = m; }
        float[] toArray() { return java.util.Arrays.copyOf(a, n); }
    }

    static final class IntBuilder {
        int[] a = new int[1 << 16];
        int n;
        void add(int... v) { if (n + v.length > a.length) a = java.util.Arrays.copyOf(a, Math.max(a.length * 2, n + v.length)); System.arraycopy(v, 0, a, n, v.length); n += v.length; }
        int[] toArray() { return java.util.Arrays.copyOf(a, n); }
    }
}
