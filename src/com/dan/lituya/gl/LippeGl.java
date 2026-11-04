package com.dan.lituya.gl;

import com.dan.lituya.scene.Site;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Werkbuch Welle W4b (E7) „Die Röhre“: die Lippe der Front als eigenes Netz [G]. Die Wellenfläche ist ein Höhenfeld; eine
 * überhängende Lippe aus ihren Ecken staffelt sich, wo die Front schräg über die Zeilen läuft (Probe „Die Röhre“: Zickzack
 * auf der ganzen Wand). Hier wird je Bild die Linie der Front aus der Flutkarte gezogen (Ankunft = jetzt − {@link #HINTER}
 * s, die Krone gleich hinter dem Fuß), und an jedem Punkt ein Querschnitt gebaut: aus dem Wasser hinter der Krone über die
 * Krone, die Lippe vor, die Spitze nach unten und ein Stück zurück in die Röhre. Höhe aus dem höchsten Stand der
 * Vorrechnung, Laufrichtung und Tempo aus dem Gefälle der Ankunft; die Lippe reicht über den Fuß der Front hinaus (dazu
 * 0,25–0,55 der Höhe, in Abschnitten von rund 100 m verschieden); runder Querschnitt aus 15 Punkten (Bézier-Kurve);
 * nur über tiefem Wasser und bei Wellen von 8 bis rund 60 m.
 */
final class LippeGl {
    /** Die Linie der Lippe: so viele Sekunden hinter dem Fuß der Front (Krone). */
    static final double HINTER = 0.35;
    /** Nur so weit um das Auge (m). */
    static final double WEIT = 4500;
    /** Punkte des Querschnitts (seit der zweiten Runde rund: Rücken, Krone, Bézier-Kurve der Lippe, Vorhang). */
    static final int NB = 8, NQ = 2 + 1 + NB + 4;
    /** Runde 4: der Vorhang als eigener Streifen von der Spitze bis unter den Pegel (5 Punkte, u + 2 als Kennung). */
    static final int NV = 5;
    static final int FL = 14;   // x y z, nx ny nz, u, H, Tx Tz, Rand, zu, Fuß x z (Punkt der Linie, für das Rauschen)
    /** Runde 4: Tropfen (x y z, Größe m, Deckung, Kennung) und ihr Abstand längs der Front (m). */
    static final int FT = 6, TROPFEN_MAX = 40000;
    static final double TROPFEN_D = 0.5, TROPFEN_WEIT = 2200;

    final Shader shader = new Shader("Lippe", null, "lippe.vert", "lippe.frag");
    final Shader tropfen = new Shader("Tropfen", null, "tropfen.vert", "tropfen.frag");
    private int vao, vbo, cap, tvao, tvbo, tcap;
    private FloatBuffer buf, tbuf;
    int segmente, tropfenZahl;
    private double jetzt, augeX, augeZ;
    private int[] grund_ = new int[6];   // verworfen: Stand, Gefälle, Grund, Gewicht, Weite, Puffer
    String info = "Lippe: aus";

    void build(GL4 gl) { shader.build(gl); tropfen.build(gl); }

    void dispose(GL4 gl) {
        shader.dispose(gl);
        tropfen.dispose(gl);
        if (vbo != 0) gl.glDeleteBuffers(1, new int[]{vbo}, 0);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (tvbo != 0) gl.glDeleteBuffers(1, new int[]{tvbo}, 0);
        if (tvao != 0) gl.glDeleteVertexArrays(1, new int[]{tvao}, 0);
        vao = vbo = tvao = tvbo = 0;
    }

    /** Die Flutkarte (UTM, Zellmitten zu z m, Zeilen nach Norden) und der Grund der Bucht (Szene x, z → m, NaN ohne). */
    interface Grund { double at(double x, double z); }

    void zeichnen(GL4 gl, float[] A, float[] M, int nx, int nz, double e0, double n0, double zelle, float[] fMin, float[] fMax, int feld,
                  double t, double[] eye, double pegel, Grund grund, Consumer<Shader> common, float kruemmung, double pxProM) {
        segmente = 0;
        tropfenZahl = 0;
        java.util.Arrays.fill(grund_, 0);
        if (A == null) { info = "Lippe: keine Flutkarte"; return; }
        A = glatt(A, nx, nz);
        double tc = t - HINTER;
        if (buf == null) buf = Buffers.newDirectFloatBuffer(FL * PRO_STUECK * 6000);
        if (tbuf == null) tbuf = Buffers.newDirectFloatBuffer(FT * TROPFEN_MAX);
        buf.clear();
        tbuf.clear();
        jetzt = t;
        int ftx = (nx + feld - 1) / feld, ftz = (nz + feld - 1) / feld;
        double[] o = Site.fromUtm(e0, n0);   // Szene der Südwestecke
        double ex = eye[0], ez = eye[2];
        augeX = ex;
        augeZ = ez;
        double[] pa = new double[13], pb = new double[13];
        for (int fj = 0; fj < ftz; fj++)
            for (int fi = 0; fi < ftx; fi++) {
                int f = fj * ftx + fi;
                if (fMin[f] > tc + 1 || fMax[f] < tc - 1) continue;
                // grob außerhalb des Kreises um das Auge
                double cx = o[0] + (fi + 0.5) * feld * zelle, cz = o[1] - (fj + 0.5) * feld * zelle;
                if (Math.hypot(cx - ex, cz - ez) > WEIT + feld * zelle) continue;
                int i1 = Math.min(nx - 1, (fi + 1) * feld), j1 = Math.min(nz - 1, (fj + 1) * feld);
                for (int j = fj * feld; j < j1; j++)
                    for (int i = fi * feld; i < i1; i++) {
                        float a00 = A[j * nx + i], a10 = A[j * nx + i + 1], a01 = A[(j + 1) * nx + i], a11 = A[(j + 1) * nx + i + 1];
                        if (a00 > 1e5f || a10 > 1e5f || a01 > 1e5f || a11 > 1e5f) continue;
                        // Rand der Zelle gegen den Uhrzeigersinn: unten, rechts, oben, links; je Kante ein Schnitt
                        int k = 0;
                        double[] s = new double[8];
                        k = schnitt(a00, a10, i, j, i + 1, j, tc, s, k);
                        k = schnitt(a10, a11, i + 1, j, i + 1, j + 1, tc, s, k);
                        k = schnitt(a11, a01, i + 1, j + 1, i, j + 1, tc, s, k);
                        k = schnitt(a01, a00, i, j + 1, i, j, tc, s, k);
                        for (int q = 0; q + 3 < k * 2 && q + 3 < 8; q += 4) {
                            int ra = punkt(A, M, nx, nz, s[q], s[q + 1], o, zelle, pegel, grund, t, pa);
                            int rb = punkt(A, M, nx, nz, s[q + 2], s[q + 3], o, zelle, pegel, grund, t, pb);
                            if (ra >= 0 || rb >= 0) { grund_[Math.max(ra, rb)]++; continue; }
                            if (Math.hypot(pa[0] - ex, pa[1] - ez) > WEIT) { grund_[4]++; continue; }
                            if (buf.remaining() < FL * PRO_STUECK) { grund_[5]++; break; }
                            band(pa, pb, pegel);
                            segmente++;
                        }
                    }
            }
        int n = buf.position() / FL;
        info = String.format(Locale.GERMAN, "Lippe [G]: %d Stücke, %d Ecken, %d Tropfen (verworfen: Stand %d, Gefälle %d, Grund %d, Gewicht %d, Weite %d, Puffer %d)",
                segmente, n, tropfenZahl, grund_[0], grund_[1], grund_[2], grund_[3], grund_[4], grund_[5]);
        if (n == 0) return;
        buf.flip();
        if (vao == 0) {
            int[] b = new int[1];
            gl.glGenVertexArrays(1, b, 0);
            vao = b[0];
            gl.glGenBuffers(1, b, 0);
            vbo = b[0];
            gl.glBindVertexArray(vao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
            int[] size = {3, 3, 1, 1, 2, 1, 1, 2};
            long off = 0;
            for (int a = 0; a < size.length; a++) {
                gl.glEnableVertexAttribArray(a);
                gl.glVertexAttribPointer(a, size[a], GL.GL_FLOAT, false, FL * 4, off);
                off += size[a] * 4L;
            }
        }
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        long bytes = (long) buf.limit() * 4;
        if (bytes > cap) { gl.glBufferData(GL.GL_ARRAY_BUFFER, buf.capacity() * 4L, null, GL.GL_DYNAMIC_DRAW); cap = buf.capacity() * 4; }
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, bytes, buf);
        common.accept(shader);
        shader.set(gl, "uKruemmung", kruemmung);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, n);
        gl.glBindVertexArray(0);
        // Runde 4: die Tropfen, die von der Spitze fliegen, als weiche Punkte (deckend mit gestreuter Durchsicht,
        // die Kantenglättung mittelt sie; so schreiben sie Tiefe und Staub und Gischt liegen richtig davor)
        int m = tbuf.position() / FT;
        if (m == 0) return;
        tbuf.flip();
        if (tvao == 0) {
            int[] b = new int[1];
            gl.glGenVertexArrays(1, b, 0);
            tvao = b[0];
            gl.glGenBuffers(1, b, 0);
            tvbo = b[0];
            gl.glBindVertexArray(tvao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, tvbo);
            int[] size = {3, 1, 1, 1};
            long off = 0;
            for (int a = 0; a < size.length; a++) {
                gl.glEnableVertexAttribArray(a);
                gl.glVertexAttribPointer(a, size[a], GL.GL_FLOAT, false, FT * 4, off);
                off += size[a] * 4L;
            }
        }
        gl.glBindVertexArray(tvao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, tvbo);
        long tb = (long) tbuf.limit() * 4;
        if (tb > tcap) { gl.glBufferData(GL.GL_ARRAY_BUFFER, tbuf.capacity() * 4L, null, GL.GL_DYNAMIC_DRAW); tcap = tbuf.capacity() * 4; }
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, tb, tbuf);
        common.accept(tropfen);
        tropfen.set(gl, "uKruemmung", kruemmung);
        tropfen.set(gl, "uPxProM", (float) pxProM);
        gl.glEnable(GL4.GL_PROGRAM_POINT_SIZE);
        gl.glDrawArrays(GL.GL_POINTS, 0, m);
        gl.glDisable(GL4.GL_PROGRAM_POINT_SIZE);
        gl.glBindVertexArray(0);
    }

    /** Ecken je Stück: Hauptband (NQ − 1 Vierecke) und Vorhang (NV − 1), je zwei Dreiecke. */
    static final int PRO_STUECK = 6 * (NQ - 1 + NV - 1);

    private float[] glattVon, glattA;

    /**
     * Die Ankunft liegt in Stufen der Bilder der Vorrechnung; ihre Linie wäre eine Treppe. Einmal je Karte geglättet,
     * getrennt in x und z über ±3 Zellen (±24 m), nur über gültige Zellen.
     */
    private float[] glatt(float[] A, int nx, int nz) {
        if (A == glattVon && glattA != null) return glattA;
        final int r = 3;
        float[] B = new float[A.length], C = new float[A.length];
        for (int pass = 0; pass < 2; pass++) {
            float[] src = pass == 0 ? A : B, dst = pass == 0 ? B : C;
            for (int j = 0; j < nz; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    if (A[c] > 1e5f || Float.isNaN(A[c])) { dst[c] = 1e6f; continue; }
                    double sum = 0; int n = 0;
                    for (int k = -r; k <= r; k++) {
                        int ii = pass == 0 ? i + k : i, jj = pass == 0 ? j : j + k;
                        if (ii < 0 || jj < 0 || ii >= nx || jj >= nz) continue;
                        float v = src[jj * nx + ii];
                        if (v > 1e5f || Float.isNaN(v)) continue;
                        sum += v; n++;
                    }
                    dst[c] = n > 0 ? (float) (sum / n) : 1e6f;
                }
        }
        glattVon = A;
        glattA = C;
        return C;
    }

    /** Schnitt der Linie a = tc auf der Kante (i0, j0)–(i1, j1); gibt den Zähler der Schnitte zurück. */
    private static int schnitt(float a0, float a1, int i0, int j0, int i1, int j1, double tc, double[] s, int k) {
        if ((a0 < tc) == (a1 < tc) || k >= 4) return k;
        double f = (tc - a0) / (a1 - a0);
        s[k * 2] = i0 + (i1 - i0) * f;
        s[k * 2 + 1] = j0 + (j1 - j0) * f;
        return k + 1;
    }

    private static double bil(float[] R, int nx, int nz, double fi, double fj) {
        int i = (int) Math.floor(fi), j = (int) Math.floor(fj);
        if (i < 0 || j < 0 || i + 1 >= nx || j + 1 >= nz) return Double.NaN;
        double a = fi - i, b = fj - j;
        float v00 = R[j * nx + i], v10 = R[j * nx + i + 1], v01 = R[(j + 1) * nx + i], v11 = R[(j + 1) * nx + i + 1];
        return (1 - b) * ((1 - a) * v00 + a * v10) + b * ((1 - a) * v01 + a * v11);
    }

    /** Wert-Rauschen 1D (0 … 1). */
    private static double rausch(double x) {
        double i = Math.floor(x), f = x - i;
        f = f * f * (3 - 2 * f);
        return hash(i) * (1 - f) + hash(i + 1) * f;
    }

    /** Drei Lagen Wert-Rauschen längs der Front (≈ 37, 83, 190 m), die verschieden schnell seitlich wandern; 0 … 1. */
    /** Wert-Rauschen 2D (0 … 1). */
    private static double rausch2(double x, double z) {
        double i = Math.floor(x), j = Math.floor(z), f = x - i, g = z - j;
        f = f * f * (3 - 2 * f);
        g = g * g * (3 - 2 * g);
        long a = (long) i, b = (long) j;
        double v00 = hashI(a * 73856093L ^ b * 19349663L, 9), v10 = hashI((a + 1) * 73856093L ^ b * 19349663L, 9);
        double v01 = hashI(a * 73856093L ^ (b + 1) * 19349663L, 9), v11 = hashI((a + 1) * 73856093L ^ (b + 1) * 19349663L, 9);
        return (1 - g) * ((1 - f) * v00 + f * v10) + g * ((1 - f) * v01 + f * v11);
    }

    /** Drei Lagen 2D-Rauschen in der Fläche (≈ 37, 83, 190 m), die verschieden schnell wandern; 0 … 1. */
    private static double lagen2(double x, double z, double t, double o) {
        return rausch2(x / 37.0 + t * 0.21 + o, z / 37.0 + o * 0.7) * 0.30 + rausch2(x / 83.0 - t * 0.09 + o * 1.7, z / 83.0 + o * 1.3) * 0.40
                + rausch2(x / 190.0 + t * 0.04 + o * 2.3, z / 190.0 + o * 0.4) * 0.30;
    }

    private static double lagen(double laengs, double t, double o) {
        return rausch(laengs / 37.0 + t * 0.21 + o) * 0.30 + rausch(laengs / 83.0 - t * 0.09 + o * 1.7) * 0.40 + rausch(laengs / 190.0 + t * 0.04 + o * 2.3) * 0.30;
    }

    /** Ganzzahliges Rauschen (splitmix64), 0 … 1; viel billiger als sin für die vielen Plätze der Tropfen. */
    private static double hashI(long k, int salz) {
        long z = k * 0x9E3779B97F4A7C15L + salz * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    private static double hash(double i) { double v = Math.sin(i * 127.1 + 311.7) * 43758.5453; return v - Math.floor(v); }

    /**
     * Ein Punkt der Linie in Gitterzellen (fi, fj): Szene x, z, Höhe über dem Pegel H, Laufrichtung Tx, Tz (Szene), Wurf L,
     * Gewicht am Rand w. false, wo keine Lippe hingehört.
     */
    /** −1: in Ordnung, sonst der Grund (0 Stand, 1 Gefälle, 2 Grund, 3 Gewicht). */
    private static int punkt(float[] A, float[] M, int nx, int nz, double fi, double fj, double[] o, double zelle, double pegel,
                             Grund grund, double t, double[] p) {
        double m = bil(M, nx, nz, fi, fj);
        if (Double.isNaN(m)) return 0;
        double H = m - pegel;
        if (H < 8 || H > 80) return 0;
        double ax = bil(A, nx, nz, fi + 1, fj) - bil(A, nx, nz, fi - 1, fj), an = bil(A, nx, nz, fi, fj + 1) - bil(A, nx, nz, fi, fj - 1);
        if (Double.isNaN(ax + an) || Math.abs(ax) > 1e4 || Math.abs(an) > 1e4) return 1;
        double l = Math.hypot(ax, an);
        if (l < 1e-6) return 1;
        // Szene: x nach Ost, z nach Süd; Zeilen laufen nach Norden
        double tx = ax / l, tz = -an / l;
        double x = o[0] + (fi + 0.5) * zelle, z = o[1] - (fj + 0.5) * zelle;
        double g = grund.at(x, z);
        // nur über tiefem Wasser (kein Auflauf am Hang); ohne Grund (noch nicht geladen) gilt die Bucht als tief
        // (Probe 3: zwei Drittel der Linie fielen weg, solange der Grund der Gitter fehlte)
        if (g >= pegel - 10) return 2;
        double w = smooth(8, 20, H) * (1 - smooth(45, 80, H)) * (Double.isNaN(g) ? 1 : smooth(pegel - 10, pegel - 25, g));
        // Runde 4: nicht mehr −tz·x + tx·z: bei Positionen von einigen Kilometern sprang diese Koordinate, wo die Richtung
        // zwischen zwei Punkten um ein Grad drehte, um zig Meter, und mit ihr Abschnitte und Phase (daher Spitzen und
        // senkrechte Kanten). Das Rauschen liegt jetzt in der Fläche (x, z) und wandert langsam; die Front läuft hindurch,
        // so ändert sich die Phase an einem Ort der Front auch, während sie vorrückt (das Einrollen über die Zeit)
        double wx = Math.abs(tz) + 1e-3, wz = Math.abs(tx) + 1e-3;
        // Runde 3: unregelmäßig aus drei Lagen mit Perioden, die nicht zueinander passen (aus der Ferne reihten sich die
        // Abschnitte zu einem gleichmäßigen Zahnmuster)
        double ab = 0.55 + 0.45 * smooth(0.3, 0.65, lagen2(x, z, t, 3.1));
        // Runde 2: der Wurf zählt vom Fuß der Front an. Die Linie liegt HINTER s hinter dem Fuß (bei 37 m/s rund 13 m);
        // mit 0,7 × Höhe allein fiel die Lippe dort, wo der Abschnitt schwach war, hinter die steile Wand und war weg
        double v = Math.max(15, Math.min(120, 2 * zelle / l));   // Tempo der Front (m/s) aus dem Gefälle der Ankunft
        double fuss = v * HINTER;
        // Runde 4 „Einrollen“ [G]: eine Phase längs der Front (dieselben wandernden Lagen wie zu/offen): früh wirft die
        // Lippe kurz und hoch, dann wird sie länger, die Spitze sinkt und rollt unter die Lippe zurück, zuletzt schlägt sie
        // auf (Vorhang). Wo die Abschnitte seitlich wandern, durchläuft ein Ort die Phasen nacheinander
        double phase = lagen2(x, z, -t, 17.3);
        // Probe Runde 4: aus allen drei Lagen sägte die Unterkante der Lippe in Spitzen (37 m); das Einrollen folgt nur den
        // beiden langen Lagen derselben Phase, weit gespreizt
        double lang = (rausch2(x / 83.0 + t * 0.09 + 17.3 * 1.7, z / 83.0 + 17.3 * 1.3) * 0.40
                + rausch2(x / 190.0 - t * 0.04 + 17.3 * 2.3, z / 190.0 + 17.3 * 0.4) * 0.30) / 0.70;
        double roll = smooth(0.15, 0.75, lang);
        double L = (fuss + (0.25 + 0.30 * ab) * H * (0.6 + 0.6 * roll)) * (0.35 + 0.65 * w);
        // Runde 2, zweite Probe: der Vorhang überall schloss die Röhre zu einem Wasserfall. Die Front bricht in Abschnitten
        // unregelmäßiger Länge (drei Lagen), die seitlich wandern, meist offen (die Lippe fliegt noch, darunter die Röhre) oder zu (sie ist
        // aufgeschlagen, Vorhang aus Weißwasser) [G]
        double zu = smooth(0.28, 0.68, phase);   // Runde 4: breiter, damit der Vorhang über einige zehn Meter aus Strängen wächst   // rund die Hälfte geschlossen (mit 0,45–0,70 in H10 keins)   // Probe: mit 140 m lag H10 ganz in einem Abschnitt, mit 60 m Zähne in H7
        p[0] = x; p[1] = z; p[2] = H * (0.75 + 0.25 * w); p[3] = tx; p[4] = tz; p[5] = L; p[6] = w; p[7] = ab; p[8] = zu;
        p[9] = 0.74 + (0.60 - 0.74) * roll;   // Höhe der Spitze (Anteil der Höhe)
        p[10] = v; p[11] = wx >= wz ? x : z + 1e6; p[12] = roll;   // Plätze der Tropfen längs der Achse, in der die Front mehr läuft
        return w > 0.05 ? -1 : 3;
    }

    static double smooth(double a, double b, double x) { double u = Math.max(0, Math.min(1, (x - a) / (b - a))); return u * u * (3 - 2 * u); }

    /** Ein Stück Band zwischen zwei Punkten der Linie: Lippe, Vorhang, Tropfen. */
    private void band(double[] a, double[] b, double pegel) {
        float[][] va = quer(a, pegel), vb = quer(b, pegel);
        for (int k = 0; k + 1 < NQ + NV; k++) {
            if (k == NQ - 1) continue;   // zwischen dem Ende der Rolle und dem Kopf des Vorhangs
            put(va[k]); put(vb[k]); put(vb[k + 1]);
            put(va[k]); put(vb[k + 1]); put(va[k + 1]);
        }
        tropfen(a, b, pegel);
    }

    /**
     * Runde 4: Tropfen und Fetzen, die von der Spitze fliegen [G]. Feste Plätze längs der Front (alle {@link #TROPFEN_D} m,
     * nach der Koordinate längs der Front, damit ein Tropfen von Bild zu Bild derselbe bleibt); jeder Platz wirft immer
     * wieder einen Tropfen, der ballistisch fällt: etwas schneller als die Front nach vorn, ein wenig seitlich, zu Beginn
     * nach oben. Die Stelle des Abwurfs läuft mit der Front, also liegt ein Tropfen des Alters τ um (v_Tropfen − v)·τ vor
     * der Spitze. Meist an offenen Abschnitten (am Vorhang fällt das Wasser als Ganzes).
     */
    private void tropfen(double[] a, double[] b, double pegel) {
        double la = a[11], lb = b[11];
        if (Math.abs(lb - la) < 1e-6 || Math.abs(lb - la) > 30) return;
        if (Math.hypot(a[0] - augeX, a[1] - augeZ) > TROPFEN_WEIT) return;
        double lo = Math.min(la, lb), hi = Math.max(la, lb);
        for (double k = Math.ceil(lo / TROPFEN_D); k * TROPFEN_D < hi; k++) {
            if (tbuf.remaining() < FT) return;
            double f = (k * TROPFEN_D - la) / (lb - la);
            for (int j = 0; j < 3; j++) {   // drei Tropfen je Platz (Probe: einzeln sahen sie aus wie Kugeln)
                if (tbuf.remaining() < FT) return;
                long kk = (long) k * 4 + j;
                double h3 = hashI(kk, 3);
                double h1 = hashI(kk, 1);
                double zu = a[8] + (b[8] - a[8]) * f, w = a[6] + (b[6] - a[6]) * f;
                if (h3 > w * (0.45 + 0.55 * (1 - zu))) continue;
                double H = a[2] + (b[2] - a[2]) * f, L = a[5] + (b[5] - a[5]) * f, ty = a[9] + (b[9] - a[9]) * f;
                double tx = a[3] + (b[3] - a[3]) * f, tz = a[4] + (b[4] - a[4]) * f, tl = Math.hypot(tx, tz);
                if (tl < 1e-6) continue;
                tx /= tl; tz /= tl;
                double v = a[10] + (b[10] - a[10]) * f;
                double x0 = a[0] + (b[0] - a[0]) * f, z0 = a[1] + (b[1] - a[1]) * f;
                double x, y, z, gr, deck, tau;
                if (hashI(kk, 7) < 0.7 * zu) {
                    // Spritzer am Fuß des Vorhangs: wo er aufschlägt, springt Wasser nach oben und vorn (Flugzeit 2·vy/g)
                    double vy = 3.0 + 9.0 * hashI(kk, 6), life = 2 * vy / 9.81;
                    tau = (hashI(kk, 2) + jetzt / life) % 1.0 * life;
                    double dv = v * (0.05 + 0.35 * hashI(kk, 4)), seit = (hashI(kk, 5) - 0.5) * 6.0;
                    x = x0 + tx * 1.09 * L + (tx * dv - tz * seit) * tau;
                    z = z0 + tz * 1.09 * L + (tz * dv + tx * seit) * tau;
                    y = pegel + vy * tau - 4.905 * tau * tau;
                    if (y < pegel - 0.3) continue;
                    gr = (0.10 + 0.40 * h1 * h1) * (1 + 0.6 * tau);
                    deck = Math.min(1, tau / 0.08) * (1 - 0.5 * tau / life);
                } else {
                    // Tropfen von der Spitze der Lippe
                    double life = 0.35 + 0.9 * h1 * h1;   // Probe: mit bis zu 2,2 s Flug standen sie wie Schnee in der ganzen Röhre
                    tau = (hashI(kk, 2) + jetzt / life) % 1.0 * life;
                    double dv = v * (0.03 + 0.20 * hashI(kk, 4)), seit = (hashI(kk, 5) - 0.5) * 3.0, vy = 0.3 + 3.2 * hashI(kk, 6);
                    x = x0 + tx * L + (tx * dv - tz * seit) * tau;
                    z = z0 + tz * L + (tz * dv + tx * seit) * tau;
                    y = pegel + ty * H + vy * tau - 4.905 * tau * tau;
                    if (y < pegel - 0.3) continue;   // ins Wasser gefallen
                    gr = (0.08 + 0.27 * h1 * h1) * (1 + 0.8 * tau);
                    deck = Math.min(1, tau / 0.10) * Math.min(1, (y - pegel + 0.3) / 1.5) * (1 - 0.55 * tau / life);
                }
                tbuf.put((float) x).put((float) y).put((float) z).put((float) gr).put((float) deck).put((float) (kk % 997));
                tropfenZahl++;
            }
        }
    }

    /**
     * Der runde Querschnitt am Punkt p: zwei Punkte des Rückens (aus dem Wasser hinter der Krone), die Krone, die Lippe als
     * kubische Bézier-Kurve von der Krone (Tangente leicht steigend) zur Spitze (Tangente senkrecht nach unten), dann
     * vier Punkte der Rolle zurück unter die Lippe. Runde 4: danach als eigener Streifen der Vorhang von der Spitze bis
     * unter den Pegel, immer in voller Länge; ob er zu sehen ist, entscheidet lippe.frag nach „zu“ (gestreute Durchsicht,
     * keine Spitzen mehr, wo die Form zwischen Rolle und Vorhang wechselte). Weg s längs T (m), Höhe y über dem Pegel; u
     * für die Schattierung (0 Rücken, 0,12 Krone, 0,62 Spitze, bis 0,72 Rolle; Vorhang 2,62 … 3,0). Die Spitze sinkt und
     * die Rolle krümmt sich mit der Phase (p[9], p[12]). Normale aus der Tangente, nach außen.
     */
    private static float[][] quer(double[] p, double pegel) {
        double H = p[2], L = p[5], ty = p[9], roll = p[12];
        double[] ss = new double[NQ], yy = new double[NQ], uu = new double[NQ];
        int k = 0;
        ss[k] = -0.60 * H; yy[k] = 0.93 * H - 1.0; uu[k++] = 0.0;
        ss[k] = -0.25 * H; yy[k] = 0.99 * H; uu[k++] = 0.06;
        ss[k] = 0.0; yy[k] = 1.02 * H; uu[k++] = 0.12;
        double x0 = 0, y0 = 1.02 * H, x1 = 0.55 * L, y1 = 1.08 * H, x2 = 1.00 * L, y2 = 1.00 * H, x3 = L, y3 = ty * H;
        for (int b = 1; b <= NB; b++) {
            double t = b / (double) NB, mt = 1 - t;
            ss[k] = mt * mt * mt * x0 + 3 * mt * mt * t * x1 + 3 * mt * t * t * x2 + t * t * t * x3;
            yy[k] = mt * mt * mt * y0 + 3 * mt * mt * t * y1 + 3 * mt * t * t * y2 + t * t * t * y3;
            uu[k++] = 0.12 + 0.50 * t;
        }
        // die Rolle von der Spitze aus: früh ein kleiner Haken, eingerollt zurück unter die Lippe
        double[][] r0 = {{0.00, -0.04}, {-0.01, -0.07}, {-0.03, -0.09}, {-0.05, -0.10}};
        double[][] r1 = {{-0.03, -0.09}, {-0.10, -0.14}, {-0.17, -0.16}, {-0.22, -0.16}};
        double[] ur = {0.68, 0.70, 0.71, 0.72};
        for (int q = 0; q < 4; q++) {
            ss[k] = (1 + r0[q][0] + (r1[q][0] - r0[q][0]) * roll) * L;
            yy[k] = (ty + r0[q][1] + (r1[q][1] - r0[q][1]) * roll) * H;
            uu[k++] = ur[q];
        }
        float[][] r = new float[NQ + NV][];
        ecken(ss, yy, uu, NQ, p, pegel, r, 0);
        double[] vs = {1.00, 1.03, 1.06, 1.08, 1.09}, vy = {1.0, 0.68, 0.35, 0.10, -0.05 / Math.max(ty, 0.3)}, vu = {2.62, 2.72, 2.82, 2.92, 3.0};
        double[] cs = new double[NV], cy = new double[NV];
        for (int q = 0; q < NV; q++) { cs[q] = vs[q] * L; cy[q] = vy[q] * ty * H; }
        ecken(cs, cy, vu, NV, p, pegel, r, NQ);
        return r;
    }

    private static void ecken(double[] ss, double[] yy, double[] uu, int n, double[] p, double pegel, float[][] r, int ab) {
        double H = p[2];
        for (int i = 0; i < n; i++) {
            int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
            double ds = ss[i1] - ss[i0], dy = yy[i1] - yy[i0];
            // nach außen: die Tangente um 90° gedreht (oben auf der Krone nach oben, an Spitze und Vorhang nach vorn)
            double nh = -dy, nv = ds, nl = Math.hypot(nh, nv);
            if (nl < 1e-9) { nh = 0; nv = 1; nl = 1; }
            nh /= nl; nv /= nl;
            double x = p[0] + p[3] * ss[i], z = p[1] + p[4] * ss[i];
            r[ab + i] = new float[]{(float) x, (float) (pegel + yy[i]), (float) z, (float) (p[3] * nh), (float) nv, (float) (p[4] * nh), (float) uu[i],
                    (float) H, (float) p[3], (float) p[4], (float) p[6], (float) p[8], (float) p[0], (float) p[1]};
        }
    }

    private void put(float[] v) { buf.put(v); }
}
