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
    /** Runde 4: ab hier (m) ein Kegel statt der Silhouette (ein Baum deckt dort mit 50 mm rund 10–20 Bildpunkte). */
    static final double KEGEL_AB = 1500;
    static final int MAX = 150000;
    /** Weiter als hier (m) wird kein fallender Baum gezeichnet (wie die Einzelbäume in WaldGl.NAH). */
    static final double WEIT = 3000;

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
            // umgelegt: bleibt hier liegen, längs des Hangs (forest legt ihn waagerecht: am steilen Hang standen die
            // Stämme wie Stacheln vom Hang ab, W5 Runde 3); erst nach der Welle (Jahrgänge) wieder forest
            case 3: return 1e9;
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
            int B = 2 * T + 2;   // nah je Typ, fern je Typ, ein Stamm, ein Kegel (Runde 4: über 1,5 km)
            vao = new int[B]; vbo = new int[B]; ibo = new int[B]; nIdx = new int[B]; ibuf = new int[B]; icap = new int[B];
            gl.glGenVertexArrays(B, vao, 0);
            gl.glGenBuffers(B, vbo, 0);
            gl.glGenBuffers(B, ibo, 0);
            gl.glGenBuffers(B, ibuf, 0);
            bin = new float[B][FL * 256];
            binN = new int[B];
            netz(gl, 2 * T, stamm());
            netz(gl, 2 * T + 1, kegel());
        }
        for (int t = 0; t < T; t++) {
            TreeModel m = f.model(t);
            Season se = Season.of(m.species, day, 0f);
            float[][] nah = (float[][]) pack.invoke(null, TreeMesh.build(m, 1, 2), se, 1958L + t * 31L + 2, 0.15f, 2.0f);
            // fern: Silhouette mit 10 Seiten und 12 Ringen statt 6 × 7 (Runde 3: grobe Kegel in der Ferne)
            float[][] fern = (float[][]) pack.invoke(null, TreeMesh.silhouette(m, 10, 12), se, 1958L + t * 31L + 3, 1f, 1f);
            netz(gl, t, nah);
            netz(gl, T + t, fern);
        }
        gebaut = true;
        tag = day;
    }

    /**
     * Runde 3: ein Stamm ohne Krone (Höhe 1, Halbmesser 0,011 der Höhe, sechs Seiten, Holz geschält und nass), für
     * umgelegte und fortgerissene Bäume, sobald die Krone fort ist: liegt längs des Hangs, Maßstab = Höhe des Baums.
     */
    static float[][] stamm() {
        int S = 6;
        float[] v = new float[(S + 1) * 2 * 11];
        float[] tri = new float[S * 6];
        for (int k = 0; k <= S; k++) {
            double a = 2 * Math.PI * k / S;
            float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
            for (int e = 0; e < 2; e++) {
                int o = ((k * 2) + e) * 11;
                float r = e == 0 ? 0.011f : 0.006f;
                v[o] = cx * r; v[o + 1] = e; v[o + 2] = cz * r;
                v[o + 3] = cx; v[o + 4] = 0; v[o + 5] = cz;
                v[o + 6] = 0.42f; v[o + 7] = 0.34f; v[o + 8] = 0.25f;
                v[o + 9] = e; v[o + 10] = 0;
            }
        }
        int q = 0;
        for (int k = 0; k < S; k++) {
            int a = k * 2, b = a + 1, c = a + 2, d = a + 3;
            // gegen den Uhrzeigersinn von außen gesehen
            tri[q++] = Float.intBitsToFloat(a); tri[q++] = Float.intBitsToFloat(b); tri[q++] = Float.intBitsToFloat(c);
            tri[q++] = Float.intBitsToFloat(b); tri[q++] = Float.intBitsToFloat(d); tri[q++] = Float.intBitsToFloat(c);
        }
        return new float[][]{v, tri};
    }

    /**
     * Runde 4: ein Nadelbaum aus der Ferne als Kegel (Höhe 1, Krone ab 0,22 der Höhe, Halbmesser 0,17 unten; sechs Seiten,
     * dazu der Stamm als Strich), 18 Dreiecke statt rund 240 der Silhouette. Laub = 1, damit Krone und Licht wie beim Baum.
     */
    static float[][] kegel() {
        int S = 6;
        java.util.List<float[]> v = new java.util.ArrayList<>();
        java.util.List<Integer> t = new java.util.ArrayList<>();
        float ru = 0.17f, y0 = 0.22f;
        for (int k = 0; k < S; k++) {
            double a0 = 2 * Math.PI * k / S, a1 = 2 * Math.PI * (k + 1) / S, am = (a0 + a1) / 2;
            float nx = (float) (Math.cos(am) * 0.86), ny = 0.5f, nz = (float) (Math.sin(am) * 0.86);
            int b = v.size();
            v.add(new float[]{(float) Math.cos(a0) * ru, y0, (float) Math.sin(a0) * ru, nx, ny, nz, 0.035f, 0.060f, 0.040f, y0, 1});
            v.add(new float[]{(float) Math.cos(a1) * ru, y0, (float) Math.sin(a1) * ru, nx, ny, nz, 0.035f, 0.060f, 0.040f, y0, 1});
            v.add(new float[]{0, 1, 0, nx, ny, nz, 0.045f, 0.075f, 0.048f, 1, 1});
            t.add(b); t.add(b + 2); t.add(b + 1);
            // Boden der Krone
            int c = v.size();
            v.add(new float[]{(float) Math.cos(a0) * ru, y0, (float) Math.sin(a0) * ru, 0, -1, 0, 0.02f, 0.035f, 0.024f, y0, 1});
            v.add(new float[]{(float) Math.cos(a1) * ru, y0, (float) Math.sin(a1) * ru, 0, -1, 0, 0.02f, 0.035f, 0.024f, y0, 1});
            v.add(new float[]{0, y0, 0, 0, -1, 0, 0.02f, 0.035f, 0.024f, y0, 1});
            t.add(c); t.add(c + 1); t.add(c + 2);
        }
        // Stamm als schmaler Keil
        int c = v.size();
        v.add(new float[]{-0.012f, 0, 0, 0, 0, 1, 0.12f, 0.09f, 0.07f, 0, 0});
        v.add(new float[]{0.012f, 0, 0, 0, 0, 1, 0.12f, 0.09f, 0.07f, 0, 0});
        v.add(new float[]{0, y0 + 0.02f, 0, 0, 0, 1, 0.12f, 0.09f, 0.07f, y0, 0});
        t.add(c); t.add(c + 1); t.add(c + 2);
        float[] vv = new float[v.size() * 11];
        for (int i = 0; i < v.size(); i++) System.arraycopy(v.get(i), 0, vv, i * 11, 11);
        float[] tt = new float[t.size()];
        for (int i = 0; i < t.size(); i++) tt[i] = Float.intBitsToFloat(t.get(i));
        return new float[][]{vv, tt};
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

    /** Runde 4: die Netze gleich bauen, wenn forest bereit ist (vorher im ersten Bild der Welle: ein Ruck von rund 0,5 s). */
    void vorbauen(GL4 gl, ForestGpu f, int day) {
        if (gebaut && day == tag) return;
        try { bauen(gl, f, day); } catch (Exception e) { info = "Knick: " + e; }
    }

    /** Zeichnet die fallenden Bäume (HDR-Puffer mit Tiefe gebunden). t: Ereigniszeit (s). */
    void zeichnen(GL4 gl, ForestGpu f, int day, Consumer<Shader> common, double[] eye, float[] vp, double t, float kruemmung) {
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
        double[] pl = ebenen(vp);
        for (int k = 0; k < n; k++) {
            int o = k * FL;
            int typ = (int) baeume[o + 15];
            if (typ < 0 || typ >= T) continue;
            double dx = baeume[o] - eye[0], dz = baeume[o + 2] - eye[2], d2 = dx * dx + dz * dz;
            if (d2 > WEIT * WEIT) continue;
            // Runde 3: Sichtkegel (Kugel um den Baum, Halbmesser seine Höhe)
            double H = baeume[o + 14], cy = baeume[o + 1] + 0.5 * H;
            boolean drin = true;
            for (int e = 0; e < 4 && drin; e++) drin = pl[4 * e] * baeume[o] + pl[4 * e + 1] * cy + pl[4 * e + 2] * baeume[o + 2] + pl[4 * e + 3] >= -H;
            if (!drin) continue;
            int folge = (int) baeume[o + 12];
            boolean liegt = (folge == 3 || folge == 4) && t > baeume[o + 6] + baeume[o + 11] + 2.6;
            boolean nh = !liegt && d2 < NAH * NAH && nah < NAH_MAX;
            if (nh) nah++;
            boolean weit = !liegt && d2 > KEGEL_AB * KEGEL_AB;
            int b = liegt ? 2 * T : weit ? 2 * T + 1 : nh ? typ : T + typ;
            if (FL * (binN[b] + 1) > bin[b].length) bin[b] = java.util.Arrays.copyOf(bin[b], bin[b].length * 2);
            System.arraycopy(baeume, o, bin[b], FL * binN[b], FL);
            if (liegt || weit) bin[b][FL * binN[b] + 4] = (float) H;   // Stamm und Kegel haben die Höhe 1
            binN[b]++;
        }
        common.accept(shader);
        shader.set(gl, "uEreignis", (float) t);
        shader.set(gl, "uKruemmung", kruemmung);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDepthMask(true);
        long tri = 0;
        for (int b = 0; b < 2 * T + 2; b++) {
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

    /** Die vier seitlichen Ebenen des Blicks (links, rechts, unten, oben) aus vp (Spalten zuerst), normiert. */
    static double[] ebenen(float[] m) {
        double[] p = new double[16];
        for (int e = 0; e < 4; e++) {
            int r = e / 2, sg = e % 2 == 0 ? 1 : -1;   // Zeile 0 (x) für links/rechts, Zeile 1 (y) für unten/oben
            double a = m[3] + sg * m[r], b = m[7] + sg * m[4 + r], c = m[11] + sg * m[8 + r], d = m[15] + sg * m[12 + r];
            double l = Math.sqrt(a * a + b * b + c * c);
            p[4 * e] = a / l; p[4 * e + 1] = b / l; p[4 * e + 2] = c / l; p[4 * e + 3] = d / l;
        }
        return p;
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
