package com.dan.lituya.gl;

import com.dan.forest.Season;
import com.dan.forest.TreeMesh;
import com.dan.forest.TreeModel;
import com.dan.forest.gl.ForestGl;
import com.dan.forest.gl.ForestGpu;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Werkbuch Welle W5 (E11) „524 m“: Bäume knicken in Reihen [G]. Bis W4 schaltete jeder Platz im Band in dem Augenblick,
 * in dem die Welle ihn erreichte, von „stehend“ auf seine Folge aus {@code succession} um (gebrochen, entrindet, umgelegt,
 * fort). Jetzt fällt er: in den Sekunden um seine Ankunft (seit W5 aus dem Gitter der Vorrechnung, Band1958) nimmt
 * {@link WaldGl} ihn aus {@code ForestGpu} und reicht ihn hierher; gezeichnet wird derselbe Baum (Art, Variante, Ort,
 * Drehung, Größe aus {@code ForestCulture}) mit seinem Netz aus {@code forest}, aber im Bildpunktformer gekippt:
 * <ul>
 * <li>ein Luftstoß vor der Wasserzunge neigt ihn um einige Grad (ab 0,6 s vorher);</li>
 * <li>umgelegt und fort: er kippt um den Fuß in Laufrichtung der Zunge, erst langsam, dann schnell (wie ein fallender
 * Körper), bis er auf dem Hang liegt (90° minus der Neigung des Hangs in Fallrichtung), federt nach; fort: dann treibt
 * er mit dem Wasser hangauf und sinkt in die Zunge;</li>
 * <li>gebrochen: der Stamm bricht in 15–45 % der Höhe, die Spitze kippt, der Stumpf bleibt stehen;</li>
 * <li>entrindet: er bleibt stehen, schwingt, die Krone reißt ab und fliegt mit dem Wasser fort.</li>
 * </ul>
 * Danach zeichnet wieder {@code ForestGpu} den Platz in seiner Folge. Nah das Netz der mittleren Stufe mit gelichtetem
 * Laub (wie Stufe 2 in forest), fern die Silhouette (wie Stufe 3). Licht wie Gelände und Welle (common.glsl).
 */
final class KnickGl {
    /** Vorlauf des Luftstoßes vor der Ankunft (s). */
    static final double VOR = 0.6;
    /** Floats je Baum: x y z Drehung · Maßstab Tönung Ankunft Bruch · Fx Fz Winkel Falldauer · Folge Drift Höhe Kennung. */
    static final int FL = 16;
    /**
     * Bis hierher (m) das Netz der mittleren Stufe, dahinter die Silhouette; höchstens {@link #NAH_MAX} Bäume nah (Abnahme
     * W5: mit 450 m und ohne Grenze fielen um deine Aufnahmen am Sporn bis 9 Mio. Dreiecke, bis 250 ms je Bild; mit 700 Bäumen bis 220 m noch 16 Mio., das Netz der mittleren Stufe hat rund 20 000 Dreiecke).
     */
    static final double NAH = 150;
    static final int NAH_MAX = 120;
    static final int MAX = 60000;

    final Shader shader = new Shader("Knick", null, "knick.vert", "knick.frag");
    private int types;
    private int[] vao, vbo, ibo, nIdx, ibuf, icap;
    private boolean gebaut;
    private int tag = -1;
    private float[] baeume = new float[FL * 1024];
    private int n;
    private float[][] bin;
    private int[] binN;
    private FloatBuffer fb;
    String info = "Knick: –";
    int gezeichnet;

    /** Dauer, solange ein Baum hier fällt (s nach seiner Ankunft), je Folge (0 stehend … 4 fort). */
    static double dauer(int folge, double tf) {
        switch (folge) {
            case 1: return tf + 0.6;                 // gebrochen: dann der Stumpf von forest
            case 2: return 3.5;                      // entrindet: Krone fort, Stamm schwingt aus
            case 3: return tf + 1.2;                 // umgelegt: liegt, wie forest ihn legt
            case 4: return tf + 6.0;                 // fort: treibt und sinkt
            default: return 0;
        }
    }

    /** Neue Liste (aus WaldGl.welle): b mit n Bäumen zu {@link #FL} Floats. */
    void setzen(float[] b, int n) { this.baeume = b; this.n = n; }

    int zahl() { return n; }

    /** Die Netze der stehenden Typen aus forest (einmal, und neu bei einem anderen Tag der Jahreszeit). */
    private void bauen(GL4 gl, ForestGpu f, int day) throws Exception {
        Method pack = ForestGl.class.getDeclaredMethod("pack", TreeMesh.class, Season.class, long.class, float.class, float.class);
        pack.setAccessible(true);
        int T = f.species() * f.variants();   // stehende Typen
        if (!gebaut) {
            types = T;
            vao = new int[2 * T]; vbo = new int[2 * T]; ibo = new int[2 * T]; nIdx = new int[2 * T]; ibuf = new int[2 * T]; icap = new int[2 * T];
            gl.glGenVertexArrays(2 * T, vao, 0);
            gl.glGenBuffers(2 * T, vbo, 0);
            gl.glGenBuffers(2 * T, ibo, 0);
            gl.glGenBuffers(2 * T, ibuf, 0);
            bin = new float[2 * T][FL * 256];
            binN = new int[2 * T];
        }
        for (int t = 0; t < T; t++) {
            TreeModel m = f.model(t);
            Season se = Season.of(m.species, day, 0f);
            float[][] nah = (float[][]) pack.invoke(null, TreeMesh.build(m, 1, 2), se, 1958L + t * 31L + 2, 0.15f, 2.0f);
            float[][] fern = (float[][]) pack.invoke(null, TreeMesh.build(m, 3), se, 1958L + t * 31L + 3, 1f, 1f);
            netz(gl, t, nah);
            netz(gl, T + t, fern);
        }
        gebaut = true;
        tag = day;
    }

    private void netz(GL4 gl, int b, float[][] m) {
        float[] v = m[0], tf = m[1];
        int[] tri = new int[tf.length];
        for (int i = 0; i < tf.length; i++) tri[i] = Float.floatToRawIntBits(tf[i]);
        gl.glBindVertexArray(vao[b]);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[b]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, Math.max(4L, v.length * 4L), Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        int st = 11 * 4;
        gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, st, 0);
        gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, st, 12);
        gl.glEnableVertexAttribArray(2); gl.glVertexAttribPointer(2, 3, GL.GL_FLOAT, false, st, 24);
        gl.glEnableVertexAttribArray(3); gl.glVertexAttribPointer(3, 2, GL.GL_FLOAT, false, st, 36);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo[b]);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, Math.max(4L, tri.length * 4L), Buffers.newDirectIntBuffer(tri), GL.GL_STATIC_DRAW);
        nIdx[b] = tri.length;
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ibuf[b]);
        for (int a = 0; a < 4; a++) {
            gl.glEnableVertexAttribArray(4 + a);
            gl.glVertexAttribPointer(4 + a, 4, GL.GL_FLOAT, false, FL * 4, a * 16L);
            gl.glVertexAttribDivisor(4 + a, 1);
        }
        gl.glBindVertexArray(0);
    }

    /** Zeichnet die fallenden Bäume (HDR-Puffer mit Tiefe gebunden). t: Ereigniszeit (s). */
    void zeichnen(GL4 gl, ForestGpu f, int day, Consumer<Shader> common, double[] eye, double t, float kruemmung) {
        gezeichnet = 0;
        if (n == 0 || f == null || !f.ready()) { info = "Knick: keine"; return; }
        try {
            if (!gebaut || day != tag) bauen(gl, f, day);
        } catch (Exception e) {
            info = "Knick: " + e;
            n = 0;
            return;
        }
        java.util.Arrays.fill(binN, 0);
        int T = types, nah = 0;
        for (int k = 0; k < n; k++) {
            int o = k * FL;
            int typ = (int) baeume[o + 15];
            if (typ < 0 || typ >= T) continue;
            double dx = baeume[o] - eye[0], dz = baeume[o + 2] - eye[2];
            boolean nh = dx * dx + dz * dz < NAH * NAH && nah < NAH_MAX;
            if (nh) nah++;
            int b = nh ? typ : T + typ;
            if (FL * (binN[b] + 1) > bin[b].length) bin[b] = java.util.Arrays.copyOf(bin[b], bin[b].length * 2);
            System.arraycopy(baeume, o, bin[b], FL * binN[b], FL);
            binN[b]++;
        }
        common.accept(shader);
        shader.set(gl, "uEreignis", (float) t);
        shader.set(gl, "uKruemmung", kruemmung);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDepthMask(true);
        long tri = 0;
        for (int b = 0; b < 2 * T; b++) {
            int m = binN[b];
            if (m == 0 || nIdx[b] == 0) continue;
            if (fb == null || fb.capacity() < m * FL) fb = Buffers.newDirectFloatBuffer(Math.max(m * FL, FL * 4096));
            fb.clear();
            fb.put(bin[b], 0, m * FL).flip();
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ibuf[b]);
            long bytes = (long) m * FL * 4;
            if (bytes > icap[b]) { gl.glBufferData(GL.GL_ARRAY_BUFFER, bytes * 2, null, GL.GL_DYNAMIC_DRAW); icap[b] = (int) (bytes * 2); }
            gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, bytes, fb);
            gl.glBindVertexArray(vao[b]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, nIdx[b], GL.GL_UNSIGNED_INT, 0, m);
            tri += (long) nIdx[b] / 3 * m;
            gezeichnet += m;
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        info = String.format(Locale.GERMAN, "Knick: %,d Bäume fallen, %.1f Mio. Dreiecke", gezeichnet, tri / 1e6);
    }

    void dispose(GL4 gl) {
        shader.dispose(gl);
        if (!gebaut) return;
        gl.glDeleteVertexArrays(vao.length, vao, 0);
        gl.glDeleteBuffers(vbo.length, vbo, 0);
        gl.glDeleteBuffers(ibo.length, ibo, 0);
        gl.glDeleteBuffers(ibuf.length, ibuf, 0);
        gebaut = false;
    }
}
