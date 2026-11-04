package com.dan.lituya.gl;

import com.dan.lituya.scene.EreignisDaten;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Wiedergabe;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Die Vorrechnung auf der Grafikkarte (Phase 13b): Welle und Strom als Höhenfelder ({@code spur.vert} mit
 * {@code welle.frag} und {@code strom.frag}), Staub und Trübe unter Wasser als Bild darüber ({@code medien.frag}).
 * <p>
 * Je Spur zwei Texturen mit den rohen Bildern a und b (R16I, Staub als RG32F mit Oberkante und Dicke); wechselt das
 * Paar, wird nur das neue Bild geladen (aus b wird a). Die Welle zeichnet je Gitter Kacheln zu
 * {@link Wiedergabe#KACHEL} Zellen: nie nasse und ganz vom feineren Gitter gedeckte fallen weg, ebenso solche außerhalb
 * des Blicks; die übrigen bekommen eine Stufe der Feinheit (1 bis 16 Zellen je Scheitel) nach dem Fehler in Bildpunkten,
 * und ihre Ränder folgen der gröberen Nachbarkachel.
 */
public final class EreignisGl {
    /** Größter Fehler einer Stufe in Bildpunkten (die Kante einer Zelle der Stufe). */
    public static final double PIXEL = 3;
    public static final int MAX_SCHRITT = 16;

    final Shader welle = new Shader("Welle", null, "spur.vert", "welle.frag"),
            strom = new Shader("Strom", new String[]{"STROM"}, "spur.vert", "strom.frag"),
            medien = new Shader("Staub", null, "fullscreen.vert", "medien.frag");
    final Shader[] all = {welle, strom, medien};

    /** Texturen einer Spur: Bild a und b, welche Bilder sie halten, Grund. */
    static final class Tex {
        int a, b, boden;
        int ia = -1, ib = -1;
        String lauf;
        /** Staub: Hülle in der Höhe für das Paar hA, hB. */
        int hA = -1, hB = -1;
        float unten, oben;
    }

    private final Map<Wiedergabe.Spur, Tex> tex = new HashMap<>();
    private int kacheln, dreiecke, hochgeladen;
    private String info = "";
    /** Wasserstand über dem Auge (m; positiv: Auge unter Wasser), NaN ohne Welle. */
    public volatile double ueberAuge = Double.NaN;
    /** Seit dem letzten Bild gezeichnet: Welle, Strom, Staub. */
    public boolean zeigtWelle, zeigtStrom, zeigtStaub;

    /** Werkbuch Bild, B0: Bildwinkel senkrecht (Grad), vom Renderer vor jedem Bild gesetzt (Brennweite). */
    public double fovy = com.dan.lituya.camera.Camera.FOVY;

    public void build(GL4 gl) { for (Shader s : all) s.build(gl); }

    public String info() { return info; }

    // ------------------------------------------------------------------ Texturen

    private Tex tex(GL4 gl, Wiedergabe.Spur s) {
        Tex t = tex.get(s);
        if (t != null) return t;
        // Spuren eines anderen Laufs freigeben
        tex.entrySet().removeIf(e -> {
            if (e.getKey().k != s.k || e.getKey().art != s.art) return false;
            Gpu.deleteTextures(gl, e.getValue().a, e.getValue().b, e.getValue().boden);
            return true;
        });
        t = new Tex();
        t.lauf = s.lauf;
        boolean staub = s.art == Wiedergabe.Art.STAUB_OBEN;
        t.a = neu(gl, s.g.nx(), s.g.nz(), staub);
        t.b = neu(gl, s.g.nx(), s.g.nz(), staub);
        tex.put(s, t);
        return t;
    }

    private static int neu(GL4 gl, int w, int h, boolean rg) {
        int[] id = new int[1];
        gl.glGenTextures(1, id, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, id[0]);
        if (rg) gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_RG32F, w, h, 0, GL4.GL_RG, GL.GL_FLOAT, null);
        else gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R16I, w, h, 0, GL4.GL_RED_INTEGER, GL.GL_SHORT, null);
        int f = rg ? GL.GL_LINEAR : GL.GL_NEAREST;
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, f);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, f);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return id[0];
    }

    private void lade(GL4 gl, int id, Wiedergabe.Spur s, short[] r) {
        gl.glBindTexture(GL.GL_TEXTURE_2D, id);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 2);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, s.g.nx(), s.g.nz(), GL4.GL_RED_INTEGER, GL.GL_SHORT, ShortBuffer.wrap(r));
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        hochgeladen++;
    }

    /** Staub: Oberkante und Dicke in eine RG32F-Textur (trocken: keine Wolke). */
    private void ladeStaub(GL4 gl, int id, Wiedergabe.Spur s, short[] oben, short[] dichte) {
        int n = s.g.nx() * s.g.nz();
        float[] v = new float[2 * n];
        for (int c = 0; c < n; c++) {
            boolean leer = oben[c] == EreignisDaten.TROCKEN || dichte[c] == EreignisDaten.TROCKEN;
            v[2 * c] = leer ? -1000f : (float) (oben[c] * EreignisDaten.STUFE);
            v[2 * c + 1] = leer ? 0f : (float) Math.max(0, dichte[c] * EreignisDaten.STUFE);
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, id);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, s.g.nx(), s.g.nz(), GL4.GL_RG, GL.GL_FLOAT, FloatBuffer.wrap(v));
        hochgeladen++;
    }

    private void ladeBoden(GL4 gl, Tex t, Wiedergabe.Spur s) {
        if (t.boden != 0 || s.boden == null) return;
        int[] id = new int[1];
        gl.glGenTextures(1, id, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, id[0]);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R32F, s.g.nx(), s.g.nz(), 0, GL4.GL_RED, GL.GL_FLOAT, FloatBuffer.wrap(s.boden));
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        t.boden = id[0];
    }

    /** Bringt das Paar in die Texturen (aus b wird a, wenn das passt). */
    private Tex paar(GL4 gl, Wiedergabe.Paar p) {
        Tex t = tex(gl, p.spur());
        if (t.ia == p.a() && t.ib == p.b()) return t;
        if (t.ib == p.a() && t.ib >= 0) { int x = t.a; t.a = t.b; t.b = x; t.ia = t.ib; t.ib = -1; }
        else if (t.ia == p.b() && t.ia >= 0) { int x = t.a; t.a = t.b; t.b = x; t.ib = t.ia; t.ia = -1; }
        if (t.ia != p.a()) { lade(gl, t.a, p.spur(), p.ra()); t.ia = p.a(); }
        if (t.ib != p.b()) {
            if (p.b() == p.a()) { gl.glCopyImageSubData(t.a, GL.GL_TEXTURE_2D, 0, 0, 0, 0, t.b, GL.GL_TEXTURE_2D, 0, 0, 0, 0, p.spur().g.nx(), p.spur().g.nz(), 1); hochgeladen++; }
            else lade(gl, t.b, p.spur(), p.rb());
            t.ib = p.b();
        }
        return t;
    }

    // ------------------------------------------------------------------ Welle und Strom

    /**
     * Welle und Strom mit Tiefe in den HDR-Puffer (nach dem Gelände, vor dem Meer). common setzt die gemeinsamen Werte
     * eines Programms (aus dem Renderer). Gibt das Rechteck des gröbsten Gitters (Szene) zurück, in dem das Meer nicht
     * zeichnet, oder null.
     */
    public double[] zeichnen(GL4 gl, Wiedergabe w, double t, float[] vp, double[] eye, int hoehe, double pegel, Consumer<Shader> common, boolean reversed) {
        kacheln = 0;
        dreiecke = 0;
        hochgeladen = 0;
        zeigtWelle = zeigtStrom = false;
        double[] rect = null;
        if (w == null || !w.an()) { ueberAuge = Double.NaN; info = "aus"; return null; }
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversed ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        double kf = 2 * Math.tan(Math.toRadians(fovy) / 2) / Math.max(1, hoehe);
        List<Wiedergabe.Spur> ws = w.spuren(Wiedergabe.Art.WELLE);
        boolean erste = true;
        for (Wiedergabe.Spur s : ws) {
            Wiedergabe.Paar p = w.paar(s, t);
            if (p == null || s.kachelMax == null) { erste = false; continue; }
            Tex x = paar(gl, p);
            if (erste) {
                double[] r = s.szene();
                double c = s.g.zelle();
                rect = new double[]{r[0] + c, r[1] - c, r[2] - c, r[3] + c};
            }
            erste = false;
            common.accept(welle);
            spur(gl, welle, s, p, x, vp, eye, kf, pegel);
            zeigtWelle = true;
        }
        for (Wiedergabe.Spur s : w.spuren(Wiedergabe.Art.STROM)) {
            Wiedergabe.Paar p = w.paar(s, t);
            if (p == null || s.boden == null) continue;
            Tex x = paar(gl, p);
            ladeBoden(gl, x, s);
            common.accept(strom);
            gl.glActiveTexture(GL.GL_TEXTURE2);
            gl.glBindTexture(GL.GL_TEXTURE_2D, x.boden);
            strom.set(gl, "uBoden", 2);
            spur(gl, strom, s, p, x, vp, eye, kf, -1000);
            zeigtStrom = true;
        }
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glDisable(GL.GL_DEPTH_TEST);
        double e = w.eta(eye[0], eye[2], t);
        ueberAuge = Double.isNaN(e) ? Double.NaN : e - eye[1];
        info = String.format(Locale.GERMAN, "%d Kacheln, %.1f Mio. Dreiecke, %d Bilder geladen%s", kacheln, dreiecke / 1e6, hochgeladen,
                zeigtStrom ? " · Strom" : "");
        return rect;
    }

    /** Zeichnet die Kacheln einer Spur. */
    private void spur(GL4 gl, Shader sh, Wiedergabe.Spur s, Wiedergabe.Paar p, Tex x, float[] vp, double[] eye, double kf, double pegel) {
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, x.a);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, x.b);
        sh.set(gl, "uA", 0);
        sh.set(gl, "uB", 1);
        sh.set(gl, "uF", (float) p.f());
        sh.set(gl, "uStufe", (float) EreignisDaten.STUFE);
        sh.set(gl, "uKruemmung", (float) (1 / (2 * Site.EARTH_R_OPTICAL)));
        gl.glUniform2i(sh.loc(gl, "uN"), s.g.nx(), s.g.nz());
        double[] o = Site.fromUtm(s.g.e(0), s.g.n(0));
        sh.set(gl, "uGeo", (float) o[0], (float) o[1], (float) s.g.zelle());
        double[] f = s.feiner;
        if (f != null) sh.set(gl, "uFeiner", (float) f[0], (float) f[1], (float) f[2], (float) f[3]);
        else sh.set(gl, "uFeiner", 1, 0, -1, 0);
        int[] schritt = stufen(s, vp, eye, kf, pegel);
        int K = Wiedergabe.KACHEL;
        for (int tj = 0; tj < s.ntz; tj++)
            for (int ti = 0; ti < s.ntx; ti++) {
                int st = schritt[tj * s.ntx + ti];
                if (st == 0) continue;
                int q = K / st;
                gl.glUniform2i(sh.loc(gl, "uKachel"), ti * K, tj * K);
                sh.set(gl, "uSchritt", st);
                sh.set(gl, "uQuads", q);
                gl.glUniform4i(sh.loc(gl, "uNachbar"), nb(schritt, s, ti - 1, tj), nb(schritt, s, ti + 1, tj), nb(schritt, s, ti, tj - 1), nb(schritt, s, ti, tj + 1));
                gl.glDrawArrays(GL.GL_TRIANGLES, 0, q * q * 6);
                kacheln++;
                dreiecke += q * q * 2;
            }
    }

    private static int nb(int[] schritt, Wiedergabe.Spur s, int ti, int tj) {
        if (ti < 0 || tj < 0 || ti >= s.ntx || tj >= s.ntz) return 0;
        return schritt[tj * s.ntx + ti];
    }

    /**
     * Stufe je Kachel: 0 weg (nie nass, ganz unter dem feineren Gitter, außerhalb des Blicks), sonst Zellen je Scheitel.
     * pegel ≤ −999: keine Hülle aus kachelMax (Strom), die ganze Spur zählt.
     */
    public static int[] stufen(Wiedergabe.Spur s, float[] vp, double[] eye, double kf, double pegel) {
        int K = Wiedergabe.KACHEL;
        int[] r = new int[s.ntx * s.ntz];
        double[] o = Site.fromUtm(s.g.e(0), s.g.n(0));
        double c = s.g.zelle();
        double[] f = s.feiner;
        for (int tj = 0; tj < s.ntz; tj++)
            for (int ti = 0; ti < s.ntx; ti++) {
                double y0, y1;
                if (pegel > -999) {
                    float m = s.kachelMax[tj * s.ntx + ti];
                    if (Float.isNaN(m)) continue;
                    y0 = pegel - 60;
                    y1 = m + 5;
                } else { y0 = -200; y1 = 1500; }
                double x0 = o[0] + ti * K * c, x1 = o[0] + Math.min((ti + 1) * K, s.g.nx() - 1) * c;
                double zS = o[1] - tj * K * c, zN = o[1] - Math.min((tj + 1) * K, s.g.nz() - 1) * c;
                if (f != null && x0 > Math.min(f[0], f[2]) + c && x1 < Math.max(f[0], f[2]) - c && zN > Math.min(f[1], f[3]) + c && zS < Math.max(f[1], f[3]) - c) continue;
                if (!sichtbar(vp, x0, x1, y0, y1, zN, zS, eye)) continue;
                double dx = Math.max(0, Math.max(x0 - eye[0], eye[0] - x1)), dz = Math.max(0, Math.max(zN - eye[2], eye[2] - zS));
                double dy = Math.max(0, Math.max(y0 - eye[1], eye[1] - y1));
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                // Kante einer Stufe in Bildpunkten: st · c / (d · kf) ≤ PIXEL
                double st = PIXEL * d * kf / c;
                int s2 = 1;
                while (s2 * 2 <= st && s2 * 2 <= MAX_SCHRITT) s2 *= 2;
                r[tj * s.ntx + ti] = s2;
            }
        return r;
    }

    /** Liegt die Kachel (Quader) ganz außerhalb einer Ebene des Blicks? Sonst sichtbar. */
    public static boolean sichtbar(float[] m, double x0, double x1, double y0, double y1, double z0, double z1, double[] eye) {
        if (eye[0] >= x0 && eye[0] <= x1 && eye[2] >= z0 && eye[2] <= z1) return true;
        int[] aus = new int[6];
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? x0 : x1, y = (i & 2) == 0 ? y0 : y1, z = (i & 4) == 0 ? z0 : z1;
            double cx = m[0] * x + m[4] * y + m[8] * z + m[12], cy = m[1] * x + m[5] * y + m[9] * z + m[13];
            double cw = m[3] * x + m[7] * y + m[11] * z + m[15];
            if (cx < -cw) aus[0]++;
            if (cx > cw) aus[1]++;
            if (cy < -cw) aus[2]++;
            if (cy > cw) aus[3]++;
            if (cw < 0) aus[4]++;
        }
        for (int k = 0; k < 5; k++) if (aus[k] == 8) return false;
        return true;
    }

    // ------------------------------------------------------------------ Staub und unter Wasser

    /**
     * Staub und Trübe als Bild über dem HDR-Puffer (Ziel ohne Tiefe, die Tiefe als Textur). Zeichnet nichts, wenn weder
     * Staub noch Wasser über dem Auge.
     */
    public void medien(GL4 gl, Wiedergabe w, double t, int depthTex, Consumer<Shader> common, float[] fwd, boolean reversed) {
        zeigtStaub = false;
        Wiedergabe.Paar po = null, pd = null;
        Wiedergabe.Spur so = null;
        if (w != null && w.an()) {
            for (Wiedergabe.Spur s : w.spuren(Wiedergabe.Art.STAUB_OBEN)) { so = s; po = w.paar(s, t); }
            for (Wiedergabe.Spur s : w.spuren(Wiedergabe.Art.STAUB_DICHTE)) pd = w.paar(s, t);
        }
        boolean staub = po != null && pd != null && so.boden != null && po.a() == pd.a() && po.b() == pd.b();
        boolean unter = ueberAuge > 0;
        if (!staub && !unter) return;
        // erst laden (bindet auf der aktiven Stelle), dann die Stellen belegen
        Tex x = null;
        if (staub) {
            gl.glActiveTexture(GL.GL_TEXTURE0);
            x = tex(gl, so);
            if (x.ia != po.a() || x.ib != po.b()) {
                if (x.ib == po.a() && x.ib >= 0) { int s = x.a; x.a = x.b; x.b = s; x.ia = x.ib; x.ib = -1; }
                if (x.ia != po.a()) { ladeStaub(gl, x.a, so, po.ra(), pd.ra()); x.ia = po.a(); }
                if (x.ib != po.b()) { ladeStaub(gl, x.b, so, po.rb(), pd.rb()); x.ib = po.b(); }
            }
            ladeBoden(gl, x, so);
        }
        common.accept(medien);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        medien.set(gl, "uDepth", 0);
        medien.set(gl, "uNear", (float) Renderer.NEAR);
        medien.set(gl, "uFar", (float) Renderer.FAR);
        medien.set(gl, "uFwd", fwd[0], fwd[1], fwd[2]);
        medien.set(gl, "uUnter", unter ? 1 : 0);
        medien.set(gl, "uStaub", staub ? 1 : 0);
        medien.set(gl, "uStaubA", 1);
        medien.set(gl, "uStaubB", 2);
        medien.set(gl, "uBoden", 3);
        if (staub) {
            gl.glActiveTexture(GL.GL_TEXTURE1);
            gl.glBindTexture(GL.GL_TEXTURE_2D, x.a);
            gl.glActiveTexture(GL.GL_TEXTURE2);
            gl.glBindTexture(GL.GL_TEXTURE_2D, x.b);
            gl.glActiveTexture(GL.GL_TEXTURE3);
            gl.glBindTexture(GL.GL_TEXTURE_2D, x.boden);
            medien.set(gl, "uF", (float) po.f());
            double[] o = Site.fromUtm(so.g.e(0), so.g.n(0));
            medien.set(gl, "uStaubGeo", (float) o[0], (float) o[1], (float) so.g.zelle(), (float) so.g.nx());
            if (x.hA != po.a() || x.hB != po.b()) {
                float bmin = Float.MAX_VALUE;
                for (float b : so.boden) bmin = Math.min(bmin, b);
                x.unten = Math.max(-200f, bmin);
                x.oben = (float) Math.max(hoechste(po.ra()), hoechste(po.rb()));
                x.hA = po.a();
                x.hB = po.b();
            }
            medien.set(gl, "uStaubY", x.unten, x.oben);
            zeigtStaub = true;
        }
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_SRC_ALPHA);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glDisable(GL.GL_BLEND);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    /** Höchste Oberkante eines Bilds der Wolke (m). */
    static double hoechste(short[] r) {
        int m = Short.MIN_VALUE;
        for (short v : r) if (v > m) m = v;
        return m == Short.MIN_VALUE ? 0 : m * EreignisDaten.STUFE + 10;
    }

    public void dispose(GL4 gl) {
        for (Shader s : all) s.dispose(gl);
        for (Tex t : tex.values()) Gpu.deleteTextures(gl, t.a, t.b, t.boden);
        tex.clear();
    }
}
