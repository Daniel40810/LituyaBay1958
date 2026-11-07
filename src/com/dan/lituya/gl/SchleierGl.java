package com.dan.lituya.gl;

import com.dan.surge.Veil;
import com.dan.surge.gl.SurgeGl;
import com.dan.lituya.scene.Site;
import com.jogamp.opengl.GL4;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Werkbuch Welle W5 (E11) „524 m“: oben reißt das Wasser ab und fällt in Schleiern zurück [G]. Seit dem 07.11.2026 aus
 * com.dan.surge: die Fäden rechnet {@link Veil} (je Randpunkt der Zunge am Hang eine Garbe aus 40 Fäden, Bahn mit
 * Luftwiderstand), gezeichnet von {@link SurgeGl} mit dem Licht dieser App. Hier bleibt das Lesen der Vorrechnung: das
 * feinste Gitter der Welle (höchster Stand, Ankunft, Grund) aus {@code daten/ereignis/<lauf>}.
 */
public final class SchleierGl {
    private final SurgeGl surgeGl;
    private final java.util.function.IntFunction<Shader> paket;
    private final Veil veil = new Veil();
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Schleier"); t.setDaemon(true); return t; });
    private Future<float[]> bau;
    private String lauf;
    public volatile boolean an = true;
    String info = "Schleier: –";

    /** surgeGl: die von EreignisGl (gebaut mit dem Licht dieser App); paket: Programm → Shader für Renderer.common. */
    SchleierGl(SurgeGl surgeGl, java.util.function.IntFunction<Shader> paket) { this.surgeGl = surgeGl; this.paket = paket; }

    /** Im Hintergrund aus daten/ereignis/lauf die Fäden rechnen (einmal je Lauf). */
    void laden(File daten, String lauf) {
        if (lauf == null || lauf.equals(this.lauf)) return;
        this.lauf = lauf;
        File dir = new File(new File(daten, "ereignis"), lauf);
        bau = exec.submit(() -> rechnen(dir));
    }

    static float[] r32(File f) throws Exception {
        byte[] b = Files.readAllBytes(f.toPath());
        float[] v = new float[b.length / 4];
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(v);
        return v;
    }

    /** Das feinste Gitter der Vorrechnung lesen und die Fäden rechnen (com.dan.surge.Veil). */
    float[] rechnen(File dir) throws Exception {
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(new File(dir, "EREIGNIS.txt").toPath())) { p.load(r); }
        int k = Integer.parseInt(p.getProperty("gitter").trim()) - 1;   // das feinste
        double e0 = Double.parseDouble(p.getProperty("g" + k + ".e0").trim()), n0 = Double.parseDouble(p.getProperty("g" + k + ".n0").trim());
        double c = Double.parseDouble(p.getProperty("g" + k + ".zelle").trim());
        int nx = Integer.parseInt(p.getProperty("g" + k + ".nx").trim()), nz = Integer.parseInt(p.getProperty("g" + k + ".nz").trim());
        float[] mx = r32(new File(dir, "max_g" + k + ".r32")), an = r32(new File(dir, "ankunft_g" + k + ".r32")), bd = r32(new File(dir, "boden_g" + k + ".r32"));
        return veil.threads(mx, an, bd, nx, nz, c, (i, j) -> Site.fromUtm(e0 + (i + 0.5) * c, n0 + (j + 0.5) * c));
    }

    /** Zeichnet die Schleier (HDR-Puffer mit Tiefe gebunden; Tiefe nur lesen, weich gemischt). */
    void zeichnen(GL4 gl, Consumer<Shader> common, double t, boolean reversed, float kruemmung, double pxProM) {
        if (!an || !surgeGl.ready()) return;
        if (bau != null && bau.isDone()) {
            try {
                surgeGl.setVeil(gl, bau.get());
                info = surgeGl.info;
                System.out.println("LituyaBay1958: " + info);
            } catch (Exception e) {
                info = "Schleier: " + e;
            }
            bau = null;
        }
        SurgeGl.View v = new SurgeGl.View();
        v.reversedZ = reversed;
        v.curvature = kruemmung;
        v.pxPerM = pxProM;
        surgeGl.hostUniforms = p -> common.accept(paket.apply(p));
        surgeGl.drawVeil(gl, v, null, t);
    }

    /** Ob die Fäden gerechnet sind (oder es nichts zu rechnen gibt). */
    public boolean fertig() { return bau == null; }

    void dispose(GL4 gl) { exec.shutdownNow(); }
}
