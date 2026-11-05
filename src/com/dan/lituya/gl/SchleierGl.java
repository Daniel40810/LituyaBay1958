package com.dan.lituya.gl;

import com.dan.lituya.scene.Site;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Werkbuch Welle W5 (E11) „524 m“: oben reißt das Wasser ab und fällt in Schleiern zurück [G]. Wo die Zunge am Hang ihre
 * höchste Linie erreicht (im feinsten Gitter der Vorrechnung die nassen Zellen am Rand zum Trockenen, Grund über 60 m),
 * wirft sie Wasser hangauf und in die Luft; es fliegt mit Luftwiderstand im Bogen, bremst, fällt und zieht dabei Fäden.
 * Je Randpunkt (alle rund 4 m) eine Garbe aus {@link #JE} Fäden mit ähnlicher Richtung: so stehen Vorhänge statt
 * Gesprenkel in der Luft. Ort, Zeit und Richtung aus der Vorrechnung (Ankunft, höchster Stand, Gefälle der Ankunft); Wurf,
 * Streuung und Luftwiderstand gestaltet. Die Bahn rechnet der Eckenformer aus Abwurf und Zeit; der Prozessor schickt die
 * Fäden einmal zur Grafikkarte.
 */
public final class SchleierGl {
    /** Fäden je Randpunkt, Abstand der Randpunkte (Zellen des 2-m-Gitters). */
    static final int JE = 40, SCHRITT = 2;
    /** Floats je Faden: Ort x y z, Abwurf (s) · Geschwindigkeit x y z, Kennung. */
    static final int FL = 8;

    final Shader shader = new Shader("Schleier", null, "schleier.vert", "schleier.frag");
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Schleier"); t.setDaemon(true); return t; });
    private Future<float[]> bau;
    private String lauf;
    private int vao, vbo, n;
    private double t0 = Double.MAX_VALUE, t1 = -Double.MAX_VALUE;
    public volatile boolean an = true;
    String info = "Schleier: –";

    /** Im Hintergrund aus daten/ereignis/lauf die Fäden rechnen (einmal je Lauf). */
    void laden(File daten, String lauf) {
        if (lauf == null || lauf.equals(this.lauf)) return;
        this.lauf = lauf;
        n = 0;
        File dir = new File(new File(daten, "ereignis"), lauf);
        bau = exec.submit(() -> rechnen(dir));
    }

    static float[] r32(File f) throws Exception {
        byte[] b = Files.readAllBytes(f.toPath());
        float[] v = new float[b.length / 4];
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(v);
        return v;
    }

    float[] rechnen(File dir) throws Exception {
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(new File(dir, "EREIGNIS.txt").toPath())) { p.load(r); }
        int k = Integer.parseInt(p.getProperty("gitter").trim()) - 1;   // das feinste
        double e0 = Double.parseDouble(p.getProperty("g" + k + ".e0").trim()), n0 = Double.parseDouble(p.getProperty("g" + k + ".n0").trim());
        double c = Double.parseDouble(p.getProperty("g" + k + ".zelle").trim());
        int nx = Integer.parseInt(p.getProperty("g" + k + ".nx").trim()), nz = Integer.parseInt(p.getProperty("g" + k + ".nz").trim());
        float[] mx = r32(new File(dir, "max_g" + k + ".r32")), an = r32(new File(dir, "ankunft_g" + k + ".r32")), bd = r32(new File(dir, "boden_g" + k + ".r32"));
        java.util.Random rnd = new java.util.Random(524);
        float[] out = new float[FL * JE * 8192];
        int m = 0, d = 3;
        for (int j = d; j < nz - d; j += SCHRITT)
            for (int i = d; i < nx - d; i += SCHRITT) {
                int q = j * nx + i;
                if (Float.isNaN(mx[q]) || Float.isNaN(an[q]) || bd[q] < 60) continue;
                // Rand zum Trockenen: ein Nachbar in 6 m trocken
                boolean rand = Float.isNaN(mx[q + d]) || Float.isNaN(mx[q - d]) || Float.isNaN(mx[q + d * nx]) || Float.isNaN(mx[q - d * nx]);
                if (!rand) continue;
                double tief = mx[q] - bd[q];
                if (tief > 25) continue;
                // Laufrichtung: Gefälle der Ankunft (wo trocken: hangauf nach dem Grund)
                double ge = 0, gn = 0;
                float ae = an[q + 1], aw = an[q - 1], anN = an[q + nx], as = an[q - nx];
                if (!Float.isNaN(ae) && !Float.isNaN(aw)) ge = (ae - aw) / (2 * c);
                else ge = (bd[q + 1] - bd[q - 1]) / (2 * c) * 0.02;
                if (!Float.isNaN(anN) && !Float.isNaN(as)) gn = (anN - as) / (2 * c);
                else gn = (bd[q + nx] - bd[q - nx]) / (2 * c) * 0.02;
                double gl = Math.hypot(ge, gn);
                if (gl < 1e-6) continue;
                double tempo = Math.max(4, Math.min(45, 1 / Math.max(gl, 1e-3)));   // Front (m/s)
                double ux = ge / gl, uz = -gn / gl;   // Szene: x Ost, z Süd
                double[] s = Site.fromUtm(e0 + (i + 0.5) * c, n0 + (j + 0.5) * c);
                // je Randpunkt eine Garbe: gemeinsame Richtung, Streuung je Faden
                double wurf = (0.35 + 0.45 * rnd.nextDouble()) * tempo, steil = 0.7 + 0.8 * rnd.nextDouble();
                for (int f = 0; f < JE; f++) {
                    if (m + FL > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
                    double a = (rnd.nextDouble() - 0.5) * 0.5, v = wurf * (0.55 + 0.6 * rnd.nextDouble());
                    double cx = ux * Math.cos(a) - uz * Math.sin(a), cz = ux * Math.sin(a) + uz * Math.cos(a);
                    out[m++] = (float) (s[0] + (rnd.nextDouble() - 0.5) * 2 * c * SCHRITT);
                    out[m++] = (float) (mx[q] + rnd.nextDouble() * Math.min(tief, 6));
                    out[m++] = (float) (s[1] + (rnd.nextDouble() - 0.5) * 2 * c * SCHRITT);
                    out[m++] = (float) (an[q] + 0.2 + rnd.nextDouble() * 1.6);
                    out[m++] = (float) (cx * v);
                    out[m++] = (float) (v * steil * (0.6 + 0.6 * rnd.nextDouble()) + 4);
                    out[m++] = (float) (cz * v);
                    out[m++] = rnd.nextFloat();
                }
            }
        return java.util.Arrays.copyOf(out, m);
    }

    /** Zeichnet die Schleier (HDR-Puffer mit Tiefe gebunden; Tiefe nur lesen, weich gemischt). */
    void zeichnen(GL4 gl, Consumer<Shader> common, double t, boolean reversed, float kruemmung, double pxProM) {
        if (!an) return;
        if (bau != null && bau.isDone()) {
            try {
                float[] f = bau.get();
                n = f.length / FL;
                t0 = Double.MAX_VALUE; t1 = -Double.MAX_VALUE;
                for (int k = 0; k < n; k++) { t0 = Math.min(t0, f[k * FL + 3]); t1 = Math.max(t1, f[k * FL + 3]); }
                if (vao == 0) {
                    int[] b = new int[1];
                    gl.glGenVertexArrays(1, b, 0); vao = b[0];
                    gl.glGenBuffers(1, b, 0); vbo = b[0];
                }
                gl.glBindVertexArray(vao);
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
                FloatBuffer fb = Buffers.newDirectFloatBuffer(Math.max(1, f.length));
                fb.put(f).flip();
                gl.glBufferData(GL.GL_ARRAY_BUFFER, Math.max(4L, f.length * 4L), fb, GL.GL_STATIC_DRAW);
                for (int a = 0; a < 2; a++) {
                    gl.glEnableVertexAttribArray(a);
                    gl.glVertexAttribPointer(a, 4, GL.GL_FLOAT, false, FL * 4, a * 16L);
                    gl.glVertexAttribDivisor(a, 1);
                }
                gl.glBindVertexArray(0);
                info = String.format(Locale.GERMAN, "Schleier: %,d Fäden, Abwurf %.0f–%.0f s", n, t0, t1);
                System.out.println("LituyaBay1958: " + info);
            } catch (Exception e) {
                info = "Schleier: " + e;
            }
            bau = null;
        }
        if (n == 0 || t < t0 - 1 || t > t1 + 14) return;
        common.accept(shader);
        shader.set(gl, "uEreignis", (float) t);
        shader.set(gl, "uKruemmung", kruemmung);
        shader.set(gl, "uPxProM", (float) pxProM);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversed ? GL.GL_GEQUAL : GL.GL_LEQUAL);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, n);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_DEPTH_TEST);
    }

    /** Ob die Fäden gerechnet sind (oder es nichts zu rechnen gibt). */
    public boolean fertig() { return bau == null; }

    void dispose(GL4 gl) {
        shader.dispose(gl);
        if (vbo != 0) gl.glDeleteBuffers(1, new int[]{vbo}, 0);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        exec.shutdownNow();
    }
}
