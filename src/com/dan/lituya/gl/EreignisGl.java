package com.dan.lituya.gl;

import com.dan.lituya.scene.EreignisDaten;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Wiedergabe;
import com.jogamp.common.nio.Buffers;
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
    /**
     * W5 Tempo: Kante einer Stufe der Kacheln in Bildpunkten (vorher fest {@link #PIXEL}). In H6 lag der Hang in 1,5–2 km
     * mit 50 mm in voller Auflösung (2-m-Zellen, 3 Bildpunkte je Kante): 1,6 Mio. Dreiecke der Welle, 18 ms.
     */
    public volatile double kachelPixel = 5;   // Probe W5E: 6 sparte rund 3 ms in H6 ohne sichtbaren Unterschied
    /** W5 Tempo: Schritte des Strahls durch Staub und Gischt (vorher fest 64). */
    public volatile int medienSchritte = 48;   // Probe W5E: 40 sparte rund 3 ms
    public static final int MAX_SCHRITT = 16;

    final Shader welle = new Shader("Welle", null, "spur.vert", "welle.frag"),
            strom = new Shader("Strom", new String[]{"STROM"}, "spur.vert", "strom.frag"),
            medien = new Shader("Staub", null, "fullscreen.vert", "medien.frag"),
            medienHoch = new Shader("Staub hoch", null, "fullscreen.vert", "medien_hoch.frag");
    final Shader[] all = {welle, strom, medien, medienHoch};

    /**
     * Werkbuch Welle W4: Staub, Fahne, Gischt und Trübe in halber Auflösung (der Durchgang kostete seit W3c 17–28 ms) und
     * mit der Tiefe hochgerechnet ({@code medien_hoch.frag}): an Kanten zählt nur, was in derselben Tiefe liegt, so gibt es
     * keinen Saum um Berge und Wellen. Aus: wie bisher in voller Auflösung.
     */
    public volatile boolean medienHalb = true;
    private int halbTex, halbFbo, halbW, halbH;

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

    // ------------------------------------------------------------------ Werkbuch Welle W3: Abriss, Einschlag, Vorhang

    /** Werkbuch Welle W4 (E7): die Lippe an der Front (Scheitel kurz hinter der Front wandern nach vorn) [G]. */
    public volatile boolean lippe = true;
    /** W4b: die Lippe als eigenes Netz längs der Front (LippeGl); die Ecken der Wellenfläche ziehen dann nicht mehr vor. */
    public volatile boolean lippeNetz = true;
    final LippeGl lippeGl = new LippeGl();
    private volatile float[] flutA, flutM;
    private volatile double flutE0, flutN0;

    /** W3c: ob der Rückbau des Gilbert Inlet gilt (vom Renderer je Bild, wie beim Gelände). */
    public volatile boolean rueckbauAn;

    /**
     * W3c: der Grund 1958 der Vorrechnung (feinstes Gitter der Welle, R32F), damit Gelände und Felssturz dort, wo die
     * Welle Wasser hat, nicht darüber stehen (dritte Probe: ein Sander aus dem Gelände von heute stand als Tafelberg im
     * Wasser, wo der Grund 1958 aus den Lotungen −122 m hat). Einmal je Lauf hochgeladen; Stelle {@link #UNIT_GRUND58}.
     */
    private int grund58Tex;
    private volatile double[] grund58Rect;
    private String grund58Lauf;
    public static final int UNIT_GRUND58 = 18;

    void grund58(GL4 gl, Wiedergabe w) {
        if (w == null) return;
        List<Wiedergabe.Spur> ws = w.spuren(Wiedergabe.Art.WELLE);
        if (ws.isEmpty()) return;
        Wiedergabe.Spur s = ws.get(ws.size() - 1);
        if (s.boden == null) return;
        String key = s.toString();
        if (key.equals(grund58Lauf)) return;
        grund58Lauf = key;
        if (grund58Tex == 0) {
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            grund58Tex = t[0];
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, grund58Tex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R32F, s.g.nx(), s.g.nz(), 0, GL4.GL_RED, GL.GL_FLOAT, FloatBuffer.wrap(s.boden));
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        double[] o = Site.fromUtm(s.g.e0(), s.g.n0());
        grund58Rect = new double[]{o[0], o[1], s.g.nx() * s.g.zelle(), s.g.nz() * s.g.zelle()};
        System.out.println(String.format(Locale.GERMAN, "LituyaBay1958: Grund 1958 für Gelände und Strom aus %s", key));
    }

    /** W3c: Uniforms des Rückbaus im Bild ({@code rueckbau.glsl}) für ein gebundenes Programm. */
    public void rueckbau(GL4 gl, Shader sh, boolean an) {
        double[] gr = grund58Rect;
        boolean g = an && gr != null && grund58Tex != 0;
        sh.set(gl, "uGrund58An", g ? 1 : 0);
        sh.set(gl, "uGrund58", UNIT_GRUND58);
        if (g) {
            gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_GRUND58);
            gl.glBindTexture(GL.GL_TEXTURE_2D, grund58Tex);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            sh.set(gl, "uGrund58Rect", (float) gr[0], (float) gr[1], (float) gr[2], (float) gr[3]);
        }
        sh.set(gl, "uRueckbauAn", an ? 1 : 0);
        double[] r0 = Site.fromUtm(com.dan.lituya.scene.Boden1958.RUECKBAU_E0, com.dan.lituya.scene.Boden1958.RUECKBAU_N0),
                r1 = Site.fromUtm(com.dan.lituya.scene.Boden1958.RUECKBAU_E1, com.dan.lituya.scene.Boden1958.RUECKBAU_N1);
        sh.set(gl, "uRueckbauRect", (float) r0[0], (float) r0[1], (float) r1[0], (float) r1[1]);
        sh.set(gl, "uRueckbau", (float) com.dan.lituya.scene.Boden1958.RUECKBAU_UNTER, (float) com.dan.lituya.scene.Boden1958.RUECKBAU_TIEFE);
        sh.set(gl, "uRueckbauW", (float) com.dan.lituya.scene.Boden1958.BILD_UNTEN, (float) com.dan.lituya.scene.Boden1958.BILD_OBEN,
                (float) com.dan.lituya.scene.Boden1958.BILD_UFER);
        double[] g0 = Site.fromUtm(com.dan.lituya.scene.Boden1958.GLETSCHER_E0, com.dan.lituya.scene.Boden1958.GLETSCHER_N0),
                g1 = Site.fromUtm(com.dan.lituya.scene.Boden1958.GLETSCHER_E1, com.dan.lituya.scene.Boden1958.GLETSCHER_N1);
        sh.set(gl, "uGletscherRect", (float) g0[0], (float) g0[1], (float) g1[0], (float) g1[1]);
        sh.set(gl, "uGletscherW", (float) com.dan.lituya.scene.Boden1958.GLETSCHER_UNTER, (float) com.dan.lituya.scene.Boden1958.GLETSCHER_FRONT);
    }

    /** W3c: Spiegelbild der Berge aus W2 (0: keins), Größe des HDR-Bilds, Richtung, in die der Wind weht (Szene x, z). */
    public int spiegelTex;
    public double[] bildPx = {1920, 1080}, windXZ = {0.8, 0.6};
    static final int UNIT_SPIEGEL = 16;
    /** W3c: Wasser im Nahfeld hinter der Linie der Quelle zeigen (sonst ein leeres Becken). */
    public volatile boolean nahfeldAn = true;
    private double zeitJetzt;

    /** Zeit des Abbruchs und des Einschlags (s nach dem ersten Stoß; Strom ab 93 s, Eintauchen 105 s). */
    public static final double ABBRUCH_S = 93, EINSCHLAG_S = com.dan.lituya.scene.Band1958.EINTAUCHEN;
    /** Gischt an der Front zeigen (E4, E5; seit W3b aus den Daten der Welle statt einer Kuppel). */
    public volatile boolean vorhang = true;
    /** W5: Staub der Vorrechnung zeigen (für Proben abschaltbar). */
    public volatile boolean staubAn = true;
    /** W3b: wie lange die Gischt nach der Ankunft der Front in der Luft bleibt (s) [G]. */
    public static final double GISCHT_NACHLAUF = 40;
    /** W3b: Texturstelle der Flutkarte (Ankunft, höchster Stand) im Gelände. */
    public static final int UNIT_FLUT = 15;
    /** W3b: Flutkarte zu 8 m über der Bucht (RG32F: Ankunft s, höchster Stand m), Rechteck x0, z0 (Süden), Breite, Tiefe. */
    private int flutTex;
    private volatile double[] flutRect;
    private String flutLauf;
    /** Je Feld zu 32 × 32 Zellen der Flutkarte: früheste und späteste Ankunft, höchster Stand (für die Hülle der Gischt). */
    private float[] feldMinA, feldMaxA, feldMax;
    private int flutNx, flutNz;
    private static final int FELD = 32;
    private static final double FLUT_ZELLE = 8;
    /** Texturstelle der Abrissfläche im Gelände. */
    public static final int UNIT_ABRISS = 9;
    private int abrissTex, rauschTex;
    /** Texturstelle des 3D-Rauschens für Staub und Vorhang. */
    static final int UNIT_RAUSCH = 4;

    /** Kachelbares Wertrauschen 64³ (R8, trilinear, wiederholt), einmal gebaut. */
    private int rausch(GL4 gl) {
        if (rauschTex != 0) return rauschTex;
        int n = 64;
        float[] v = new float[n * n * n];
        java.util.Random r = new java.util.Random(1958);
        for (int i = 0; i < v.length; i++) v[i] = r.nextFloat();
        // einmal weich (3er-Kasten, kachelbar), damit die Lagen nicht körnig werden
        float[] w = new float[v.length];
        for (int z = 0; z < n; z++)
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    float sum = 0;
                    for (int dz = -1; dz <= 1; dz++)
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dx = -1; dx <= 1; dx++)
                                sum += v[(((z + dz) & 63) * n + ((y + dy) & 63)) * n + ((x + dx) & 63)];
                    w[(z * n + y) * n + x] = sum / 27;
                }
        float lo = 1, hi = 0;
        for (float f : w) { lo = Math.min(lo, f); hi = Math.max(hi, f); }
        java.nio.ByteBuffer b = Buffers.newDirectByteBuffer(w.length);
        for (float f : w) b.put((byte) Math.round((f - lo) / (hi - lo) * 255));
        b.flip();
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, t[0]);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage3D(GL4.GL_TEXTURE_3D, 0, GL.GL_R8, n, n, n, 0, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, b);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glGenerateMipmap(GL4.GL_TEXTURE_3D);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        for (int pn : new int[]{GL.GL_TEXTURE_WRAP_S, GL.GL_TEXTURE_WRAP_T, GL4.GL_TEXTURE_WRAP_R}) gl.glTexParameteri(GL4.GL_TEXTURE_3D, pn, GL.GL_REPEAT);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, 0);
        rauschTex = t[0];
        return rauschTex;
    }
    private String abrissLauf;
    /** Rechteck der Abrissfläche: Szene x0, z0 (Süden), Breite, Tiefe; Einschlag x, z und Richtung des Sturzes (x, z). */
    private volatile double[] abrissRect, einschlag;
    private volatile String sturzInfo = "Sturz: noch keine Daten";

    public String sturzInfo() { return sturzInfo; }

    /**
     * Werkbuch Welle W3c, Frage WF15 B: über dem Abriss steigt eine Staubfahne bis rund {@link #FAHNE_HOCH} m auf [G]. Die
     * Wolke der Vorrechnung reicht nur bis 226 m; die Fahne ist gestaltet. Oben steht sie noch in der Sonne (1° unter der
     * Kimm, sichtbar ab rund 1 000 m) und glüht, unten bleibt sie blaugrau. Platte: Mitte x, z, Halbmesser, mittlere Höhe.
     */
    public volatile boolean fahne = true;
    public static final double FAHNE_HOCH = 1_200, FAHNE_STEIG = 38, FAHNE_DAUER = 900;
    private volatile double[] platte;
    private volatile String fahneInfo = "Fahne: aus";
    private long fahneGedruckt;
    public String fahneInfo() { return fahneInfo; }
    /** Einschlag {x, z, Richtung x, Richtung z} aus den Daten (oder null). */
    public double[] einschlag() { return einschlag; }

    /**
     * Einmal je Lauf aus der Spur des Stroms: Abrissfläche = Zellen mit Fels im ersten Bild (die Platte vor dem Abgang);
     * Einschlag = Schwerpunkt der Zellen, in denen beim Eintauchen Fels unter dem Pegel liegt; Richtung = vom Schwerpunkt
     * der Platte dorthin.
     */
    void sturzDaten(GL4 gl, Wiedergabe w, double pegel) {
        if (w == null) return;
        Wiedergabe.Spur so = null;
        for (Wiedergabe.Spur sp : w.spuren(Wiedergabe.Art.STROM)) so = sp;
        if (so == null || so.boden == null || so.t.length == 0) return;
        String key = so.toString();
        if (key.equals(abrissLauf)) return;
        abrissLauf = key;
        try {
            int nx = so.g.nx(), nz = so.g.nz();
            short[] r0 = so.roh(0);
            double[] tt = so.t;
            int bE = 0;
            for (int b = 0; b < tt.length; b++) if (tt[b] <= EINSCHLAG_S + 2) bE = b;
            short[] rE = so.roh(bE);
            java.nio.ByteBuffer m = Buffers.newDirectByteBuffer(nx * nz);
            double sx = 0, sz = 0, sn = 0, ex = 0, ez = 0, en = 0;
            float[] boden = so.boden;
            for (int j = 0; j < nz; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    boolean fels = r0[c] != EreignisDaten.TROCKEN && r0[c] * EreignisDaten.STUFE > 0.3;
                    m.put((byte) (fels ? 255 : 0));
                    double[] xz = Site.fromUtm(so.g.e(i), so.g.n(j));
                    if (fels) { sx += xz[0]; sz += xz[1]; sn++; }
                    if (rE[c] != EreignisDaten.TROCKEN && rE[c] * EreignisDaten.STUFE > 0.3 && boden[c] < pegel) { ex += xz[0]; ez += xz[1]; en++; }
                }
            m.flip();
            if (abrissTex == 0) abrissTex = Gpu.texture(gl, nx, nz, GL.GL_R8, false);
            gl.glBindTexture(GL.GL_TEXTURE_2D, abrissTex);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, nx, nz, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, m);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            double[] o = Site.fromUtm(so.g.e0(), so.g.n0());
            abrissRect = new double[]{o[0], o[1], nx * so.g.zelle(), nz * so.g.zelle()};
            if (en > 0 && sn > 0) {
                double cx = ex / en, cz = ez / en, dx = cx - sx / sn, dz = cz - sz / sn, l = Math.max(1, Math.hypot(dx, dz));
                einschlag = new double[]{cx, cz, dx / l, dz / l};
            } else {
                einschlag = new double[]{Site.GILBERT.x(), Site.GILBERT.z(), -0.7, 0.7};
            }
            // W3c (WF15 B): Mitte, Halbmesser und mittlere Höhe der Platte für die Staubfahne
            if (sn > 0) {
                double hs = 0;
                for (int j = 0; j < nz; j++)
                    for (int i = 0; i < nx; i++) {
                        int c = j * nx + i;
                        if (r0[c] != EreignisDaten.TROCKEN && r0[c] * EreignisDaten.STUFE > 0.3) hs += boden[c];
                    }
                platte = new double[]{sx / sn, sz / sn, Math.sqrt(sn * so.g.zelle() * so.g.zelle() / Math.PI), hs / sn};
            }
            sturzInfo = String.format(Locale.GERMAN, "Sturz: Abriss %.0f Zellen (%.2f km²), Einschlag %.0f m O, %.0f m S, Richtung %.0f°",
                    sn, sn * so.g.zelle() * so.g.zelle() / 1e6, einschlag[0], einschlag[1], (Math.toDegrees(Math.atan2(einschlag[2], -einschlag[3])) + 360) % 360);
            System.out.println("LituyaBay1958: " + sturzInfo);
        } catch (java.io.IOException | RuntimeException e) {
            sturzInfo = "Sturz: " + e;
        }
    }

    /**
     * W3b: einmal je Lauf die Flutkarte aus {@code ankunft_g<k>} und {@code max_g<k>} der Welle (das feinste Gitter, das den
     * Ort enthält), zu 8 m über dem Rechteck, in dem die Welle mehr als 1 m über dem Pegel stand. Trockene Zellen bekommen
     * die Ankunft ihrer nassen Nachbarn (zwei Runden), damit die Spurlinie beim Mischen der Texel nicht zerfällt.
     */
    void flutDaten(GL4 gl, Wiedergabe w, double pegel) {
        if (w == null) return;
        List<Wiedergabe.Spur> ws = w.spuren(Wiedergabe.Art.WELLE);
        if (ws.isEmpty() || ws.get(0).max == null || ws.get(0).ankunft == null) return;
        boolean stromBereit = false;
        for (Wiedergabe.Spur sp : w.spuren(Wiedergabe.Art.STROM)) stromBereit = sp.boden != null && sp.t.length > 0;
        String key = ws.get(0).toString() + "/" + ws.size() + "/" + stromBereit;   // W3c: neu, sobald der Strom bereit ist
        if (key.equals(flutLauf)) return;
        flutLauf = key;
        Wiedergabe.Spur g0 = ws.get(0);
        // Rechteck (UTM) der Zellen von Gitter 0 mit Ankunft
        double e0 = Double.MAX_VALUE, n0 = Double.MAX_VALUE, e1 = -Double.MAX_VALUE, n1 = -Double.MAX_VALUE;
        for (int j = 0; j < g0.g.nz(); j++)
            for (int i = 0; i < g0.g.nx(); i++) {
                int c = j * g0.g.nx() + i;
                if (Float.isNaN(g0.ankunft[c]) || !(g0.max[c] > pegel + 1)) continue;
                e0 = Math.min(e0, g0.g.e(i)); e1 = Math.max(e1, g0.g.e(i));
                n0 = Math.min(n0, g0.g.n(j)); n1 = Math.max(n1, g0.g.n(j));
            }
        if (e0 > e1) { flutRect = null; return; }
        double z = FLUT_ZELLE, rand = 3 * g0.g.zelle();
        e0 -= rand; n0 -= rand; e1 += rand; n1 += rand;
        int nx = (int) Math.ceil((e1 - e0) / z), nz = (int) Math.ceil((n1 - n0) / z);
        float[] A = new float[nx * nz], M = new float[nx * nz];
        final double fe0 = e0, fn0 = n0;
        java.util.stream.IntStream.range(0, nz).parallel().forEach(j -> {
            double n = fn0 + (j + 0.5) * z;
            for (int i = 0; i < nx; i++) {
                double e = fe0 + (i + 0.5) * z;
                float a = Float.NaN, m = Float.NaN;
                for (int k = ws.size() - 1; k >= 0; k--) {
                    Wiedergabe.Spur s = ws.get(k);
                    if (s.max == null || s.ankunft == null) continue;
                    int c = s.g.index(e, n);
                    if (c < 0) continue;
                    a = s.ankunft[c];
                    m = s.max[c];
                    break;
                }
                A[j * nx + i] = a;
                M[j * nx + i] = m;
            }
        });
        // W3c: das Nahfeld hinter der Linie (dort rechnet die Vorrechnung nicht): Ankunft vom Einschlag aus mit rund 60 m/s,
        // Stand 160 m über dem Pegel (Nahfeld des Laborfalls) [G]; für die Gischt und die abgeschürften Wände
        // Seit der zweiten Probe nicht mehr als Ring um einen Punkt (das gab wieder den „Ball im Becken“), sondern dort, wo
        // der Fels ins Wasser fährt: Ankunft = erstes Bild des Stroms mit mehr als 3 m Fels über Grund unter dem Pegel.
        double[] nf = g0.nahfeld;
        Wiedergabe.Spur so = null;
        for (Wiedergabe.Spur sp : w.spuren(Wiedergabe.Art.STROM)) so = sp;
        if (nf != null && so != null && so.boden != null && so.t.length > 0) {
            int snx = so.g.nx(), snz = so.g.nz();
            float[] sa = new float[snx * snz];
            java.util.Arrays.fill(sa, Float.NaN);
            try {
                for (int b = 0; b < so.t.length; b++) {
                    short[] r = so.roh(b);
                    for (int c = 0; c < r.length; c++)
                        if (Float.isNaN(sa[c]) && r[c] != EreignisDaten.TROCKEN && r[c] * EreignisDaten.STUFE > 3 && so.boden[c] < pegel - 2) sa[c] = (float) so.t[b];
                }
            } catch (java.io.IOException e) {
                System.err.println("LituyaBay1958: Flut: Strom " + e);
            }
            int gesetzt = 0;
            for (int j = 0; j < nz; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    if (!Float.isNaN(A[c])) continue;
                    double e = fe0 + (i + 0.5) * z, n = fn0 + (j + 0.5) * z;
                    if (!Wiedergabe.imNahfeld(nf, e, n)) continue;
                    int sc = so.g.index(e, n);
                    if (sc < 0 || Float.isNaN(sa[sc])) continue;
                    A[c] = sa[sc];
                    M[c] = (float) (pegel + 160);
                    gesetzt++;
                }
            System.out.println(String.format(Locale.GERMAN, "LituyaBay1958: Flut: Nahfeld %d Zellen mit Gischt, wo der Fels ins Wasser fährt [G]", gesetzt));
        }
        // trockene Zellen: Ankunft der nassen Nachbarn, Stand weit unten
        for (int runde = 0; runde < 2; runde++) {
            float[] B = A.clone();
            for (int j = 0; j < nz; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    if (!Float.isNaN(A[c])) continue;
                    float best = Float.NaN;
                    for (int dj = -1; dj <= 1; dj++)
                        for (int di = -1; di <= 1; di++) {
                            int ii = i + di, jj = j + dj;
                            if (ii < 0 || jj < 0 || ii >= nx || jj >= nz) continue;
                            float v = A[jj * nx + ii];
                            if (!Float.isNaN(v) && !(v >= best)) best = v;
                        }
                    B[c] = best;
                }
            System.arraycopy(B, 0, A, 0, A.length);
        }
        java.nio.FloatBuffer b = Buffers.newDirectFloatBuffer(nx * nz * 2);
        int ftx = (nx + FELD - 1) / FELD, ftz = (nz + FELD - 1) / FELD;
        float[] fa = new float[ftx * ftz], fb = new float[ftx * ftz], fm = new float[ftx * ftz];
        java.util.Arrays.fill(fa, Float.MAX_VALUE);
        java.util.Arrays.fill(fb, -Float.MAX_VALUE);
        java.util.Arrays.fill(fm, -Float.MAX_VALUE);
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                float a = Float.isNaN(A[c]) ? 1e6f : A[c], m = Float.isNaN(M[c]) ? -1000f : M[c];
                b.put(a).put(m);
                if (a < 1e5f && m > pegel + 2) {
                    int f = (j / FELD) * ftx + i / FELD;
                    fa[f] = Math.min(fa[f], a);
                    fb[f] = Math.max(fb[f], a);
                    fm[f] = Math.max(fm[f], m);
                }
            }
        b.flip();
        if (flutTex == 0) {
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            flutTex = t[0];
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, flutTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_RG32F, nx, nz, 0, GL4.GL_RG, GL.GL_FLOAT, b);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        feldMinA = fa; feldMaxA = fb; feldMax = fm;
        flutA = A; flutM = M; flutE0 = e0; flutN0 = n0;
        flutNx = nx; flutNz = nz;
        double[] o = Site.fromUtm(e0, n0);
        flutRect = new double[]{o[0], o[1], nx * z, nz * z};
        System.out.println(String.format(Locale.GERMAN, "LituyaBay1958: Flutkarte %d × %d zu %.0f m (%s)", nx, nz, z, key));
    }

    /** W3b: Uniforms der Flut für das Gelände (Programm gebunden); an, solange die Welle läuft (danach WaldGl). */
    public void flut(GL4 gl, int p, double t) {
        double[] r = flutRect;
        boolean on = r != null && flutTex != 0 && t < 3600;
        gl.glUniform1i(gl.glGetUniformLocation(p, "uFlutAn"), on ? 1 : 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uFlut"), UNIT_FLUT);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uEreignis"), (float) t);
        if (!on) return;
        gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_FLUT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, flutTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uFlutRect"), (float) r[0], (float) r[1], (float) r[2], (float) r[3]);
    }

    /** W3b: Hülle der Gischt zur Zeit t (Szene x0, z0, x1, z1, y0, y1) oder null. */
    double[] gischtHuelle(double t, double pegel) {
        double[] r = flutRect;
        float[] fa = feldMinA, fb = feldMaxA, fm = feldMax;
        if (r == null || fa == null) return null;
        int ftx = (flutNx + FELD - 1) / FELD, ftz = (flutNz + FELD - 1) / FELD;
        double x0 = 1e30, x1 = -1e30, zS = -1e30, zN = 1e30, top = -1e30;
        double fz = FELD * FLUT_ZELLE;
        for (int j = 0; j < ftz; j++)
            for (int i = 0; i < ftx; i++) {
                int f = j * ftx + i;
                if (!(fa[f] <= t + 0.5) || !(fb[f] >= t - GISCHT_NACHLAUF)) continue;
                double xa = r[0] + i * fz, za = r[1] - j * fz;
                x0 = Math.min(x0, xa); x1 = Math.max(x1, xa + fz);
                zS = Math.max(zS, za); zN = Math.min(zN, za - fz);
                top = Math.max(top, fm[f]);
            }
        if (x0 > x1) return null;
        return new double[]{x0, zN, x1, zS, pegel - 10, top + 140};
    }

    /** Uniforms der Abrissfläche für das Gelände (Programm gebunden); an ab dem Abbruch. */
    public void abriss(GL4 gl, int p, double t, boolean nach1958) {
        double[] r = abrissRect;
        boolean on = r != null && abrissTex != 0 && (t >= ABBRUCH_S || nach1958);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uAbrissAn"), on ? 1 : 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uAbriss"), UNIT_ABRISS);
        if (!on) return;
        gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_ABRISS);
        gl.glBindTexture(GL.GL_TEXTURE_2D, abrissTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uAbrissRect"), (float) r[0], (float) r[1], (float) r[2], (float) r[3]);
        // frisch hell, mit den Jahren (Zeitstand heute) verwittert [G]
        gl.glUniform1f(gl.glGetUniformLocation(p, "uAbrissFrisch"), t < 86_400 * 30 ? 1f : 0.45f);
    }

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
        zeitJetzt = t;
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
            ladeBoden(gl, x, s);
            common.accept(welle);
            // W3c: Spiegel der Berge aus W2, Flutkarte (Schaum, Treibgut, Schlamm hinter der Front), Wind
            gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_SPIEGEL);
            gl.glBindTexture(GL.GL_TEXTURE_2D, spiegelTex);
            welle.set(gl, "uRefl", UNIT_SPIEGEL);
            welle.set(gl, "uSpiegelAn", spiegelTex != 0 ? 1 : 0);
            welle.set(gl, "uBildPx", (float) bildPx[0], (float) bildPx[1]);
            welle.set(gl, "uWindXZ", (float) windXZ[0], (float) windXZ[1]);
            welle.set(gl, "uRueckbauAn", 0);
            welle.set(gl, "uLippe", lippe ? 1f : 0f);   // W4 (E7)
            welle.set(gl, "uLipVor", lippe && lippeNetz ? 0f : 1f);   // W4b: Vorlauf der Ecken nur ohne das Netz der Lippe
            welle.set(gl, "uLipNetz", lippe && lippeNetz ? 1 : 0);
            flut(gl, welle.id(), t);
            // W3b (K11): Grund unter dem Gitter, sobald gerechnet (dünner Film am Hang fällt weg)
            gl.glActiveTexture(GL.GL_TEXTURE2);
            gl.glBindTexture(GL.GL_TEXTURE_2D, x.boden);
            welle.set(gl, "uBoden", 2);
            welle.set(gl, "uBodenAn", x.boden != 0 ? 1 : 0);
            spur(gl, welle, s, p, x, vp, eye, kf, pegel);
            zeigtWelle = true;
        }
        // W4b: die Lippe als eigenes Netz über der Wand (E7) [G]
        lippeGl.info = "Lippe: aus";
        if (zeigtWelle && lippe && lippeNetz && flutA != null) {
            final List<Wiedergabe.Spur> wsf = ws;
            LippeGl.Grund grund = (gx, gz) -> {
                double e = gx + Site.E0, n = Site.N0 - gz;
                for (int k = wsf.size() - 1; k >= 0; k--) {
                    Wiedergabe.Spur sp = wsf.get(k);
                    float[] b = sp.boden;
                    if (b == null) continue;
                    int c = sp.g.index(e, n);
                    if (c >= 0) return b[c];
                }
                return Double.NaN;
            };
            lippeGl.zeichnen(gl, flutA, flutM, flutNx, flutNz, flutE0, flutN0, FLUT_ZELLE, feldMinA, feldMaxA, FELD, t, eye, pegel, grund,
                    common, (float) (1 / (2 * Site.EARTH_R_OPTICAL)), 1 / kf);
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
            strom.set(gl, "uBodenAn", 1);
            rueckbau(gl, strom, rueckbauAn);   // W3c: auf demselben Grund wie das Gelände
            spur(gl, strom, s, p, x, vp, eye, kf, -1000);
            zeigtStrom = true;
        }
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glDisable(GL.GL_DEPTH_TEST);
        double e = w.eta(eye[0], eye[2], t);
        ueberAuge = Double.isNaN(e) ? Double.NaN : e - eye[1];
        info = String.format(Locale.GERMAN, "%d Kacheln, %.1f Mio. Dreiecke, %d Bilder geladen%s · %s", kacheln, dreiecke / 1e6, hochgeladen,
                zeigtStrom ? " · Strom" : "", lippeGl.info);
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
        // W3c: Wasser im Nahfeld hinter der Linie der Quelle [G] (nur die Welle)
        double[] nf = s.nahfeld;
        sh.set(gl, "uNahAn", nf != null && nahfeldAn && s.art == Wiedergabe.Art.WELLE ? 1 : 0);
        if (nf != null) {
            double[] m = Site.fromUtm(nf[0], nf[1]);
            sh.set(gl, "uNah", (float) m[0], (float) m[1], (float) nf[2], (float) -nf[3]);
            sh.set(gl, "uNahMass", (float) nf[4], (float) nf[5]);
        }
        sh.set(gl, "uEreignisT", (float) zeitJetzt);
        int[] schritt = stufen(s, vp, eye, kf * kachelPixel / PIXEL, pegel);
        if (s.art == Wiedergabe.Art.WELLE && lippe) frontFein(s, schritt, eye, pegel);
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

    /**
     * W4b „Die Röhre“: Kacheln, durch die die Front gerade läuft, in voller Auflösung bis 2,5 km (halber bis 5 km); sonst
     * zeichnete die Stufe die steile Wand in der Ferne mit Dreiecken zu 16 m, ihre Diagonalen standen als Sägezahn auf der
     * ganzen Wand (Probe Röhre). Aus den Feldern der Flutkarte (früheste, späteste Ankunft, höchster Stand je 256 m).
     */
    private void frontFein(Wiedergabe.Spur s, int[] schritt, double[] eye, double pegel) {
        float[] fa = feldMinA, fb = feldMaxA, fm = feldMax;
        double[] fr = flutRect;
        if (fa == null || fr == null) return;
        int ftx = (flutNx + FELD - 1) / FELD, ftz = (flutNz + FELD - 1) / FELD;
        double fz = FELD * FLUT_ZELLE, t = zeitJetzt;
        int K = Wiedergabe.KACHEL;
        double[] o = Site.fromUtm(s.g.e(0), s.g.n(0));
        double c = s.g.zelle();
        for (int tj = 0; tj < s.ntz; tj++)
            for (int ti = 0; ti < s.ntx; ti++) {
                int k = tj * s.ntx + ti;
                if (schritt[k] <= 1) continue;
                double x0 = o[0] + ti * K * c, x1 = x0 + K * c, zS = o[1] - tj * K * c, zN = zS - K * c;
                double dx = Math.max(0, Math.max(x0 - eye[0], eye[0] - x1)), dz = Math.max(0, Math.max(zN - eye[2], eye[2] - zS));
                double d = Math.hypot(dx, dz);
                if (d > 5000) continue;
                // Felder der Flutkarte unter der Kachel (Flutkarte: x ab fr[0], Zeilen nach Norden ab fr[1])
                int i0 = (int) Math.floor((x0 - fr[0]) / fz), i1 = (int) Math.floor((x1 - fr[0]) / fz);
                int j0 = (int) Math.floor((fr[1] - zS) / fz), j1 = (int) Math.floor((fr[1] - zN) / fz);
                boolean front = false;
                for (int j = Math.max(0, j0); j <= Math.min(ftz - 1, j1) && !front; j++)
                    for (int i = Math.max(0, i0); i <= Math.min(ftx - 1, i1) && !front; i++) {
                        int f = j * ftx + i;
                        front = fa[f] <= t + 3 && fb[f] >= t - 3 && fm[f] > pegel + 8 && fm[f] < pegel + 80;   // wie die Lippe: nicht im Inlet
                    }
                if (front) schritt[k] = Math.min(schritt[k], d < 1500 ? 1 : 2);
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
    /** W4: das HDR-Ziel ohne Tiefe, in das {@link #medien} zeichnet (vom Renderer je Bild gesetzt). */
    public int zielFbo;

    public void medien(GL4 gl, Wiedergabe w, double t, int depthTex, Consumer<Shader> common, float[] fwd, boolean reversed, int grundTex, double[] grundRect, double pegel) {
        zeigtStaub = false;
        Wiedergabe.Paar po = null, pd = null;
        Wiedergabe.Spur so = null;
        if (w != null && w.an()) {
            for (Wiedergabe.Spur s : w.spuren(Wiedergabe.Art.STAUB_OBEN)) { so = s; po = w.paar(s, t); }
            for (Wiedergabe.Spur s : w.spuren(Wiedergabe.Art.STAUB_DICHTE)) pd = w.paar(s, t);
        }
        boolean staub = staubAn && po != null && pd != null && so.boden != null && po.a() == pd.a() && po.b() == pd.b();
        boolean unter = ueberAuge > 0;
        double[] gh = vorhang && w != null && w.an() && grundTex != 0 ? gischtHuelle(t, pegel) : null;
        boolean vh = gh != null;
        double[] pl = platte, es = einschlag;
        double alter = t - ABBRUCH_S;
        boolean fa = fahne && w != null && w.an() && pl != null && es != null && alter > 0 && alter < FAHNE_DAUER;
        if (!staub && !unter && !vh && !fa) return;
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
        int rt = rausch(gl);
        common.accept(medien);
        gl.glActiveTexture(GL.GL_TEXTURE0 + UNIT_RAUSCH);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, rt);
        medien.set(gl, "uRausch", UNIT_RAUSCH);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        medien.set(gl, "uDepth", 0);
        medien.set(gl, "uNear", (float) Renderer.NEAR);
        medien.set(gl, "uFar", (float) Renderer.FAR);
        medien.set(gl, "uFwd", fwd[0], fwd[1], fwd[2]);
        medien.set(gl, "uSchritte", medienSchritte);
        medien.set(gl, "uUnter", unter ? 1 : 0);
        medien.set(gl, "uStaub", staub ? 1 : 0);
        medien.set(gl, "uGischt", vh ? 1f : 0f, (float) t, (float) GISCHT_NACHLAUF);
        medien.set(gl, "uFlut", 5);
        medien.set(gl, "uGrund", 6);
        if (vh) {
            double[] r = flutRect;
            gl.glActiveTexture(GL.GL_TEXTURE5);
            gl.glBindTexture(GL.GL_TEXTURE_2D, flutTex);
            gl.glActiveTexture(GL.GL_TEXTURE6);
            gl.glBindTexture(GL.GL_TEXTURE_2D, grundTex);
            medien.set(gl, "uFlutRect", (float) r[0], (float) r[1], (float) r[2], (float) r[3]);
            medien.set(gl, "uGrundRect", (float) grundRect[0], (float) grundRect[1], (float) grundRect[2]);
            medien.set(gl, "uGischtBox", (float) gh[0], (float) gh[1], (float) gh[2], (float) gh[3]);
            medien.set(gl, "uGischtY", (float) gh[4], (float) gh[5]);
        }
        medien.set(gl, "uFahneAn", fa ? 1 : 0);
        if (fa) {
            // Achse über der Platte, ein Stück zum Einschlag hin; Fuß am Pegel
            double cx = pl[0] + (es[0] - pl[0]) * 0.3, cz = pl[1] + (es[1] - pl[1]) * 0.3;
            double top = FAHNE_HOCH * (1 - Math.exp(-alter * FAHNE_STEIG / FAHNE_HOCH));
            medien.set(gl, "uFahne", (float) cx, (float) cz, (float) Math.max(top, 30), (float) alter);
            medien.set(gl, "uFahneR", (float) pl[2], (float) windXZ[0], (float) windXZ[1]);
            fahneInfo = String.format(Locale.GERMAN, "Fahne [G]: Achse %.0f m O, %.0f m S, Kopf %.0f m, Halbmesser %.0f m, %.0f s alt", cx, cz, top, pl[2], alter);
            long jetzt = System.currentTimeMillis();
            if (jetzt - fahneGedruckt > 5000) { fahneGedruckt = jetzt; System.out.println("LituyaBay1958: " + fahneInfo); }
        }
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
        if (!medienHalb) {
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_SRC_ALPHA);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glDisable(GL.GL_BLEND);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            return;
        }
        // W4: in halber Auflösung in ein eigenes Ziel (L, T), dann mit der Tiefe hochgerechnet über das HDR-Bild
        // Ziel und Bildgröße vom Renderer; das Hochrechnen baut der Renderer mit seinen Shadern (Renderer.all), sonst
        // bleibt das Programm leer und Staub, Fahne und Gischt fehlen im Bild (Probe 1 und 2)
        int[] fb = {zielFbo}, vpv = {0, 0, (int) bildPx[0], (int) bildPx[1]};
        int hw = Math.max(16, (vpv[2] + 1) / 2), hh = Math.max(16, (vpv[3] + 1) / 2);
        if (hw != halbW || hh != halbH || halbFbo == 0) {
            Gpu.deleteFbos(gl, halbFbo);
            Gpu.deleteTextures(gl, halbTex);
            halbTex = Gpu.texture(gl, hw, hh, GL.GL_RGBA16F, false);
            halbFbo = Gpu.fbo(gl, halbTex, 0);
            halbW = hw;
            halbH = hh;
            gl.glActiveTexture(GL.GL_TEXTURE0);   // das Anlegen bindet auf Stelle 0, dort liegt die Tiefe für medien.frag
            gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, halbFbo);
        gl.glViewport(0, 0, hw, hh);
        gl.glDisable(GL.GL_BLEND);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fb[0]);
        gl.glViewport(vpv[0], vpv[1], vpv[2], vpv[3]);
        medienHoch.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, halbTex);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        medienHoch.set(gl, "uHalb", 0);
        medienHoch.set(gl, "uDepth", 1);
        medienHoch.set(gl, "uHalbPx", (float) hw, (float) hh);
        medienHoch.set(gl, "uNear", (float) Renderer.NEAR);
        medienHoch.set(gl, "uFar", (float) Renderer.FAR);
        medienHoch.set(gl, "uRev", reversed ? 1 : 0);
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
        lippeGl.dispose(gl);
        if (abrissTex != 0) gl.glDeleteTextures(1, new int[]{abrissTex}, 0);
        if (rauschTex != 0) gl.glDeleteTextures(1, new int[]{rauschTex}, 0);
        if (flutTex != 0) gl.glDeleteTextures(1, new int[]{flutTex}, 0);
        if (grund58Tex != 0) gl.glDeleteTextures(1, new int[]{grund58Tex}, 0);
        Gpu.deleteFbos(gl, halbFbo);
        Gpu.deleteTextures(gl, halbTex);
        for (Tex t : tex.values()) Gpu.deleteTextures(gl, t.a, t.b, t.boden);
        tex.clear();
    }
}
