package com.dan.lituya.tools;

import com.dan.forest.ForestCulture;
import com.dan.forest.Ground;
import com.dan.forest.Species;
import com.dan.forest.TreeStates;
import com.dan.forest.gl.ForestGl;
import com.dan.forest.gl.ForestGpu;
import com.dan.succession.Art;
import com.dan.succession.Boden;
import com.dan.succession.Succession;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung von {@code com.dan.succession} mit {@code com.dan.forest} (LituyaBay1958 Phase 14, Werkbuch 5.6): eine
 * gerechnete Fjordbucht (16 × 12 km, wie die Bucht der Walddemo) mit dem Band der Welle am Nordufer, Spurlinie von 30 m an
 * der Mündung bis 524 m am Kopf [G]. Die Welle stört, dann wächst der Wald 68 Jahre nach (Roterle zuerst, Sitka-Fichte und
 * Hemlocktanne darunter und später); jeder Jahrgang geht als Zustand mit Nachwuchs in {@link TreeStates}, die Grafikkarte
 * zeichnet ihn. Der Boden im Band zeigt Fels, Mineralboden und Waldboden.
 * <ul>
 * <li>{@code Wiederbewaldung} – Fenster mit Jahresregler, Zeitraffer (68 Jahre in einer Minute) und vier Blicken;</li>
 * <li>{@code Wiederbewaldung --bild datei.png [blick]} – Bogen mit den Jahren 0, 10, 30 und 68 und den Zahlen.</li>
 * </ul>
 * Ein Package kennt kein anderes; darum steht die Vorführung beider zusammen hier in der App.
 */
public final class Wiederbewaldung implements GLEventListener {
    static final double X0 = -8000, X1 = 8000, Z0 = -6000, Z1 = 6000, SPACING = 6;
    public static final String[] BLICKE = {"Vom Fjord auf das Nordufer", "Über dem Band", "An der Spurlinie", "Weit über die Bucht"};
    public static final int JAHRE = 68;

    // ------------------------------------------------------------------ Gelände wie die Bucht der Walddemo (forest.demo.BayScene)

    static double trimline(double x) { return 30 + 494 * Math.pow(Math.max(0, Math.min(1, (x + 6000) / 11600)), 2.2); }

    static double noise(double x, double z) {
        return Math.sin(x / 830.0 + Math.cos(z / 1270.0) * 1.7) * 0.5 + Math.sin(z / 610.0 - x / 1900.0) * 0.3 + Math.sin((x + 2 * z) / 233.0) * 0.12;
    }

    static final Ground TERRAIN = (x, z) -> {
        double half = 1150 + 250 * Math.sin(x / 2300.0) - (x > 5600 ? (x - 5600) * 0.9 : 0);
        double d = Math.abs(z) - Math.max(0, half);
        double h = d < 0 ? -60 - Math.min(200, -d * 0.25)
                : z < 0 ? 4 + d * (0.2 + 0.06 * noise(x * 0.7, z * 0.7)) + d * d * 0.00006
                : 4 + d * (0.035 + 0.02 * noise(x * 0.7, z * 0.7)) + d * d * 0.000014;
        h += 60 * noise(x, z) * Math.min(1, Math.max(0, d) / 400);
        if (x < -6500) h = Math.min(h, -40 + (x + 6500) * 0.02 + Math.abs(z) * 0.01);
        return (float) h;
    };

    static final List<Species> SPECIES = List.of(Species.sitkaSpruce(), Species.westernHemlock(), Species.redAlder());

    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    static double density(double x, double z, int s) {
        double h = TERRAIN.height(x, z);
        if (h < 2) return 0;
        double n = 0.5 + 0.5 * noise(x * 3.1 + 77, z * 2.7 - 31);
        double lowland = smooth(450, 250, h), up = smooth(40, 200, h) * smooth(640, 520, h);
        return switch (s) {
            case 0 -> 0.75 * lowland * (0.6 + 0.4 * n);
            case 1 -> 0.55 * up * (0.5 + 0.5 * (1 - n));
            default -> 0.7 * smooth(45, 6, h) + 0.1 * smooth(300, 100, h) * n * n;
        };
    }

    /** Höchster Stand der Welle am Nordufer: über dem Hang dünner werdend bis zur Spurlinie (35 % der Höhe darunter) [G]. */
    static double hoechsterStand(double x, double z) {
        if (z > 0 || x < -6500 || x > 6200) return Double.NaN;
        double t = trimline(x) * (1 + 0.04 * noise(x * 9, z * 9)), g = TERRAIN.height(x, z);
        return g < t ? g + 0.35 * (t - g) + 0.5 : Double.NaN;
    }

    /** Mittlere Höhe der Bäume einer Art in forest (Modell und Streuung der Größe 0,62–1,07). */
    static double refHoehe(Species s) { return (s.heightMin + s.heightMax) / 2 * 0.845; }

    // ------------------------------------------------------------------ Szene

    final Succession su;
    final TreeStates states;
    final ForestGpu forest;
    final double[] ref = new double[SPECIES.size()];
    int sky, ground, water, groundVao, groundIdx, emptyVao, bandTex, fbo, fboColor, fboDepth, fboW, fboH;
    volatile int jahr = 0, blick = 0;
    int gezeigt = -1;
    final double[][] eyeLook = new double[4][];

    Wiederbewaldung(ForestGpu.Quality q) {
        long t0 = System.nanoTime();
        su = Succession.covering(-6500, -3200, 6200, 0, SPACING, List.of(Art.sitkaFichte(), Art.hemlock(), Art.roterle()), 1958);
        su.stoeren(TERRAIN::height, Wiederbewaldung::hoechsterStand, Wiederbewaldung::density);
        su.rechnen(JAHRE);
        System.out.printf(Locale.GERMAN, "succession: %,d Plätze im Band, %d Jahre in %.1f s%n", su.band(), JAHRE, (System.nanoTime() - t0) / 1e9);
        for (int s = 0; s < ref.length; s++) ref[s] = refHoehe(SPECIES.get(s));
        states = TreeStates.covering(-6500, -3200, 6200, 0, SPACING);
        forest = new ForestGpu(SPECIES, 3, SPACING, 1958);
        forest.apply(q);
        forest.setTerrain(TERRAIN, X0, X0, X1 - X0, 2048);
        forest.setDensity(Wiederbewaldung::density, X0, X0, X1 - X0, 1024);
        forest.setStates(states);
        // Blicke
        eyeLook[0] = new double[]{2000, 70, 900, 1800, 160, -1700};
        eyeLook[1] = new double[]{-1500, 650, 800, 300, 120, -1600};
        double sx = 3000, sz = -1150;
        double t = trimline(sx);
        while (sz > -3000 && TERRAIN.height(sx, sz) < t + 4) sz -= 4;
        double gy = TERRAIN.height(sx, sz);
        eyeLook[2] = new double[]{sx, gy + 1.8, sz, sx - 150, gy - 60, sz + 400};
        eyeLook[3] = new double[]{-7500, 900, 5500, 1500, 100, -500};
    }

    /** Schreibt den Jahrgang in die Zustände des Walds. */
    void jahrgang(int y) {
        states.clear();
        su.zustand(y, (i, j, st, fall, art, h) -> {
            states.set(i, j, st, fall);
            if (art >= 0) states.setGrowth(i, j, art, Math.min(1, h / ref[art]));
        });
        gezeigt = y;
    }

    /** Textur des Bodens im Band (je Platz: Fels grau, Mineralboden braun, Waldboden moosgrün; Deckel = im Band). */
    void bandTextur(GL4 gl, int y) {
        int w = su.w, h = su.h;
        ByteBuffer b = Buffers.newDirectByteBuffer(w * h * 4);
        byte[] px = new byte[w * h * 4];
        for (int k = 0; k < su.band(); k++) {
            double[] l = su.lage(k);
            int ii = (int) l[0] - su.i0, jj = (int) l[1] - su.j0, o = (jj * w + ii) * 4;
            Boden bo = su.bodenArt(k, y);
            int[] c = bo == Boden.FELS ? new int[]{58, 56, 54} : bo == Boden.MINERAL ? new int[]{70, 56, 40} : new int[]{22, 30, 16};
            px[o] = (byte) c[0]; px[o + 1] = (byte) c[1]; px[o + 2] = (byte) c[2]; px[o + 3] = (byte) 255;
        }
        b.put(px).flip();
        gl.glBindTexture(GL.GL_TEXTURE_2D, bandTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, w, h, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, b);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
    }

    void init(GL4 gl) {
        forest.init(gl);
        forest.setSeason(190, 0);
        forest.awaitBake();
        sky = prog(gl, FULL, SKY);
        ground = prog(gl, GROUND_V, GROUND_F);
        water = prog(gl, WATER_V, WATER_F);
        int[] v = new int[2];
        gl.glGenVertexArrays(2, v, 0);
        groundVao = v[0];
        emptyVao = v[1];
        int nx = 640, nz = 480;
        FloatBuffer vb = Buffers.newDirectFloatBuffer(nx * nz * 3);
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double x = X0 + (X1 - X0) * i / (nx - 1), z = Z0 + (Z1 - Z0) * j / (nz - 1);
                vb.put((float) x).put(TERRAIN.height(x, z)).put((float) z);
            }
        vb.flip();
        IntBuffer ib = Buffers.newDirectIntBuffer((nx - 1) * (nz - 1) * 6);
        for (int j = 0; j < nz - 1; j++)
            for (int i = 0; i < nx - 1; i++) {
                int a = j * nx + i;
                ib.put(a).put(a + nx).put(a + 1).put(a + 1).put(a + nx).put(a + nx + 1);
            }
        ib.flip();
        groundIdx = (nx - 1) * (nz - 1) * 6;
        int[] bufs = new int[2];
        gl.glGenBuffers(2, bufs, 0);
        gl.glBindVertexArray(groundVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, bufs[0]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, vb.capacity() * 4L, vb, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, bufs[1]);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, ib.capacity() * 4L, ib, GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        bandTex = t[0];
    }

    static ForestGl.View kamera(double[] e, int w, int h) {
        ForestGl.View v = new ForestGl.View();
        v.eye = new double[]{e[0], e[1], e[2]};
        v.viewProj = mul(perspective(55, w / (double) h, 0.5, 40000), lookAt(new double[]{e[0], e[1], e[2]}, new double[]{e[3], e[4], e[5]}));
        v.tonemap = true;
        v.exposure = 1.0;
        return v;
    }

    /** Ein Bild des Jahrs y im Blick b in den gebundenen Bildspeicher (w × h). */
    void render(GL4 gl, int w, int h, int y, int b, double t) {
        if (y != gezeigt) { jahrgang(y); bandTextur(gl, y); }
        ForestGl.View v = kamera(eyeLook[b], w, h);
        ForestGl.Light l = evening();
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(0.5f, 0.6f, 0.7f, 1);
        gl.glClearDepth(1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        forest.update(gl, v);
        float[] inv = invert(v.viewProj);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(sky);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(sky, "uInv"), 1, false, inv, 0);
        light(gl, sky, v, l);
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glUseProgram(ground);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(ground, "uVP"), 1, false, v.viewProj, 0);
        light(gl, ground, v, l);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, bandTex);
        gl.glUniform1i(gl.glGetUniformLocation(ground, "uBand"), 0);
        gl.glUniform4f(gl.glGetUniformLocation(ground, "uBandRect"), (float) (su.i0 * SPACING), (float) (su.j0 * SPACING), (float) (su.w * SPACING), (float) (su.h * SPACING));
        gl.glBindVertexArray(groundVao);
        gl.glDrawElements(GL.GL_TRIANGLES, groundIdx, GL.GL_UNSIGNED_INT, 0);
        gl.glUseProgram(water);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(water, "uVP"), 1, false, v.viewProj, 0);
        light(gl, water, v, l);
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 6);
        forest.draw(gl, v, l, t);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    // ------------------------------------------------------------------ Bilder ohne Fenster

    /** Ein eigener Bildspeicher (RGBA8, Tiefe 24), gebunden; liest danach mit {@link #lesen}. */
    void ziel(GL4 gl, int w, int h) {
        if (fbo != 0 && w == fboW && h == fboH) { gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo); return; }
        int[] t = new int[2];
        gl.glGenTextures(2, t, 0);
        fboColor = t[0];
        fboDepth = t[1];
        gl.glBindTexture(GL.GL_TEXTURE_2D, fboColor);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, w, h, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, null);
        gl.glBindTexture(GL.GL_TEXTURE_2D, fboDepth);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_DEPTH_COMPONENT32F, w, h, 0, GL4.GL_DEPTH_COMPONENT, GL.GL_FLOAT, null);
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        fbo = f[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, fboColor, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, fboDepth, 0);
        fboW = w;
        fboH = h;
    }

    static BufferedImage lesen(GL4 gl, int w, int h) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                img.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return img;
    }

    /** Der Bogen: Jahre 0, 10, 30, 68 im Blick b, je w × h, mit Zahlen; als PNG. */
    void bogen(GL4 gl, int b, int w, int h, File png) throws Exception {
        int[] jahre = {0, 10, 30, JAHRE};
        BufferedImage out = new BufferedImage(2 * w, 2 * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int k = 0; k < 4; k++) {
            ziel(gl, w, h);
            render(gl, w, h, jahre[k], b, 10);
            BufferedImage im = lesen(gl, w, h);
            g.drawImage(im, (k % 2) * w, (k / 2) * h, null);
            g.setColor(new Color(10, 14, 16, 190));
            g.fillRect((k % 2) * w + 8, (k / 2) * h + 8, w - 16, 44);
            g.setColor(new Color(232, 236, 234));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            g.drawString(String.format(Locale.GERMAN, "%d (Jahr %d nach der Welle) · %s", 1958 + jahre[k], jahre[k], BLICKE[b]), (k % 2) * w + 16, (k / 2) * h + 27);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            String z = su.zeile(jahre[k]);
            g.drawString(z.substring(z.indexOf("Jahr")), (k % 2) * w + 16, (k / 2) * h + 45);
            System.out.println(su.zeile(jahre[k]) + " · " + forest.info());
        }
        g.dispose();
        File dir = png.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        javax.imageio.ImageIO.write(out, "png", png);
        System.out.println("Bild: " + png.getPath());
    }

    // ------------------------------------------------------------------ Fenster

    long tStart = System.nanoTime();
    volatile boolean spielt;
    double spielJahr;
    long spielNs;

    @Override public void init(GLAutoDrawable d) { init(d.getGL().getGL4()); }
    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) { forest.dispose(d.getGL().getGL4()); }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        if (spielt) {
            long now = System.nanoTime();
            if (spielNs != 0) spielJahr += (now - spielNs) / 1e9 * JAHRE / 60.0;   // 68 Jahre in einer Minute
            spielNs = now;
            if (spielJahr >= JAHRE) { spielJahr = JAHRE; spielt = false; }
            jahr = (int) spielJahr;
        } else spielNs = 0;
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        render(gl, d.getSurfaceWidth(), d.getSurfaceHeight(), jahr, blick, (System.nanoTime() - tStart) / 1e9);
    }

    public static void main(String[] a) throws Exception {
        List<String> l = List.of(a);
        int bi = l.indexOf("--bild");
        if (bi >= 0) {
            File png = new File(a[bi + 1]);
            int b = a.length > bi + 2 ? Integer.parseInt(a[bi + 2]) : 0;
            Wiederbewaldung wb = new Wiederbewaldung(ForestGpu.Quality.HIGH);
            GLProfile prof = GLProfile.get(GLProfile.GL4);
            GLCapabilities caps = new GLCapabilities(prof);
            caps.setOnscreen(false);
            caps.setFBO(true);
            GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, 64, 64);
            d.display();
            Exception[] err = {null};
            d.invoke(true, (GLAutoDrawable x) -> {
                try {
                    GL4 gl = x.getGL().getGL4();
                    wb.init(gl);
                    wb.bogen(gl, b, 960, 540, png);
                    wb.forest.dispose(gl);
                } catch (Exception e) { err[0] = e; }
                return true;
            });
            d.destroy();
            if (err[0] != null) throw err[0];
            System.exit(0);
        }
        Wiederbewaldung wb = new Wiederbewaldung(ForestGpu.Quality.MEDIUM);
        javax.swing.SwingUtilities.invokeLater(() -> fenster(wb));
    }

    static void fenster(Wiederbewaldung wb) {
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        com.jogamp.opengl.awt.GLCanvas canvas = new com.jogamp.opengl.awt.GLCanvas(new GLCapabilities(prof));
        canvas.addGLEventListener(wb);
        javax.swing.JFrame f = new javax.swing.JFrame("LituyaBay1958 · Wiederbewaldung (succession + forest)");
        f.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
        javax.swing.JPanel p = new javax.swing.JPanel();
        p.setBackground(new Color(14, 20, 24));
        javax.swing.JLabel lbl = new javax.swing.JLabel();
        lbl.setForeground(new Color(220, 228, 232));
        com.dan.fslider.FSlider jahr = new com.dan.fslider.FSlider(0, JAHRE, 0);
        com.dan.fbutton.FButton spiel = new com.dan.fbutton.FButton("Zeitraffer ▶");
        com.dan.fcombobox.FComboBox blick = new com.dan.fcombobox.FComboBox(BLICKE);
        jahr.addChangeListener(e -> { if (!wb.spielt) wb.jahr = jahr.getValue(); });
        spiel.addActionListener(e -> { wb.spielJahr = wb.jahr >= JAHRE ? 0 : wb.jahr; wb.spielt = !wb.spielt; });
        blick.addActionListener(e -> wb.blick = Math.max(0, blick.getSelectedIndex()));
        p.add(blick);
        p.add(jahr);
        p.add(spiel);
        p.add(lbl);
        f.setLayout(new BorderLayout());
        f.add(canvas, BorderLayout.CENTER);
        f.add(p, BorderLayout.SOUTH);
        f.setSize(1600, 960);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new javax.swing.Timer(250, e -> {
            if (wb.spielt) jahr.setValue(wb.jahr);
            String z = wb.su.zeile(wb.jahr);
            lbl.setText(String.format(Locale.GERMAN, "%d · %s", 1958 + wb.jahr, z.substring(z.indexOf("Jahr"))));
        }).start();
        com.jogamp.opengl.util.Animator an = new com.jogamp.opengl.util.Animator(canvas);
        an.start();
    }

    // ------------------------------------------------------------------ Licht, Matrizen, Shader (wie forest.demo.BayScene)

    static ForestGl.Light evening() {
        ForestGl.Light l = new ForestGl.Light();
        double el = Math.toRadians(9), az = Math.toRadians(300);
        l.sunDir = new double[]{Math.cos(el) * Math.sin(az), Math.sin(el), -Math.cos(el) * Math.cos(az)};
        l.sunColor = new double[]{2.4, 1.9, 1.35};
        l.skyColor = new double[]{0.30, 0.38, 0.52};
        l.fogColor = new double[]{0.62, 0.66, 0.72};
        l.fogDistance = 24_000;
        return l;
    }

    static void light(GL4 gl, int p, ForestGl.View v, ForestGl.Light l) {
        gl.glUniform3f(gl.glGetUniformLocation(p, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSunDir"), (float) l.sunDir[0], (float) l.sunDir[1], (float) l.sunDir[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSunColor"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSkyColor"), (float) l.skyColor[0], (float) l.skyColor[1], (float) l.skyColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uFogColor"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2]);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uExposure"), (float) v.exposure);
    }

    static int prog(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (String[] s : new String[][]{{vs, "v"}, {COMMON + fs, "f"}}) {
            int sh = gl.glCreateShader(s[1].equals("v") ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + s[0]}, null, 0);
            gl.glCompileShader(sh);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[8192];
                int[] len = new int[1];
                gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
                throw new IllegalStateException("Shader: " + new String(b, 0, len[0]));
            }
            gl.glAttachShader(p, sh);
        }
        gl.glLinkProgram(p);
        return p;
    }

    static float[] invert(float[] m) { return com.dan.lituya.gl.Mat4.invert(m); }

    static float[] perspective(double fovy, double aspect, double near, double far) {
        float t = (float) (1 / Math.tan(Math.toRadians(fovy) / 2));
        return new float[]{t / (float) aspect, 0, 0, 0, 0, t, 0, 0, 0, 0, (float) ((far + near) / (near - far)), -1, 0, 0, (float) (2 * far * near / (near - far)), 0};
    }

    static float[] lookAt(double[] e, double[] c) {
        double fx = c[0] - e[0], fy = c[1] - e[1], fz = c[2] - e[2], l = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= l; fy /= l; fz /= l;
        double sx = -fz, sy = 0, sz = fx;
        l = Math.sqrt(sx * sx + sz * sz);
        sx /= l; sz /= l;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        return new float[]{(float) sx, (float) ux, (float) -fx, 0, (float) sy, (float) uy, (float) -fy, 0, (float) sz, (float) uz, (float) -fz, 0,
                (float) -(sx * e[0] + sy * e[1] + sz * e[2]), (float) -(ux * e[0] + uy * e[1] + uz * e[2]), (float) (fx * e[0] + fy * e[1] + fz * e[2]), 1};
    }

    static float[] mul(float[] a, float[] b) { return com.dan.lituya.gl.Mat4.mul(a, b); }

    static final String COMMON = """
            uniform vec3 uCam, uSunDir, uSunColor, uSkyColor, uFogColor;
            uniform float uFogDist, uExposure;
            vec3 skyCol(vec3 d) {
                float up = clamp(d.y, 0.0, 1.0);
                vec3 c = mix(uFogColor, uSkyColor * 1.6, pow(up, 0.45));
                float s = max(dot(d, uSunDir), 0.0);
                return c + uSunColor * (pow(s, 600.0) * 4.0 + pow(s, 8.0) * 0.12);
            }
            vec3 tone(vec3 c) { vec3 x = c * uExposure; return pow(clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0), vec3(1.0 / 2.2)); }
            vec3 fog(vec3 col, vec3 w) {
                vec3 d = w - uCam;
                return mix(col, skyCol(normalize(vec3(d.x, 0.02 * length(d.xz), d.z))), 1.0 - exp(-length(d) / uFogDist));
            }
            """;
    static final String FULL = "out vec2 vNdc;\nvoid main(){ vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); vNdc = p * 2.0 - 1.0; gl_Position = vec4(vNdc, 1.0, 1.0); }\n";
    static final String SKY = """
            in vec2 vNdc;
            out vec4 frag;
            uniform mat4 uInv;
            void main() {
                vec4 a = uInv * vec4(vNdc, -1.0, 1.0), b = uInv * vec4(vNdc, 1.0, 1.0);
                vec3 d = normalize(b.xyz / b.w - a.xyz / a.w);
                frag = vec4(tone(skyCol(d)), 1.0);
            }
            """;
    static final String GROUND_V = "layout(location = 0) in vec3 aPos;\nuniform mat4 uVP;\nout vec3 vW;\nvoid main(){ vW = aPos; gl_Position = uVP * vec4(aPos, 1.0); }\n";
    static final String GROUND_F = """
            in vec3 vW;
            out vec4 frag;
            uniform sampler2D uBand;
            uniform vec4 uBandRect;   // x0, z0, Breite, Tiefe (m)
            void main() {
                vec3 n = normalize(cross(dFdx(vW), dFdy(vW)));
                if (n.y < 0.0) n = -n;
                float h = vW.y, slope = 1.0 - n.y;
                vec3 forest = vec3(0.035, 0.05, 0.022), rock = vec3(0.10, 0.095, 0.085), snow = vec3(0.7, 0.72, 0.76), beach = vec3(0.16, 0.15, 0.12);
                vec3 alb = mix(forest, rock, smoothstep(520.0, 700.0, h + slope * 300.0));
                alb = mix(alb, rock, smoothstep(0.55, 0.75, slope));
                alb = mix(alb, snow, smoothstep(1050.0, 1250.0, h) * (1.0 - smoothstep(0.6, 0.8, slope)));
                alb = mix(beach, alb, smoothstep(2.0, 8.0, h));
                // Boden im Band aus succession: Fels, Mineralboden, Waldboden
                vec2 uv = (vW.xz - uBandRect.xy) / uBandRect.zw;
                if (all(greaterThan(uv, vec2(0.0))) && all(lessThan(uv, vec2(1.0)))) {
                    vec4 b = texture(uBand, uv);
                    alb = mix(alb, b.rgb * 1.6, b.a);
                }
                vec3 col = alb * (uSunColor * max(dot(n, uSunDir), 0.0) + uSkyColor * (0.5 + 0.5 * n.y));
                frag = vec4(tone(fog(col, vW)), 1.0);
            }
            """;
    static final String WATER_V = """
            uniform mat4 uVP;
            out vec3 vW;
            void main() {
                vec2 c[6] = vec2[](vec2(-1, -1), vec2(1, -1), vec2(1, 1), vec2(-1, -1), vec2(1, 1), vec2(-1, 1));
                vW = vec3(c[gl_VertexID].x * 20000.0, 0.0, c[gl_VertexID].y * 20000.0);
                gl_Position = uVP * vec4(vW, 1.0);
            }
            """;
    static final String WATER_F = """
            in vec3 vW;
            out vec4 frag;
            void main() {
                vec3 v = normalize(vW - uCam);
                vec3 n = normalize(vec3(sin(vW.x * 0.11 + vW.z * 0.07) * 0.02, 1.0, cos(vW.z * 0.13 - vW.x * 0.05) * 0.02));
                vec3 r = reflect(v, n);
                float f = 0.02 + 0.98 * pow(1.0 - max(dot(-v, n), 0.0), 5.0);
                vec3 deep = vec3(0.03, 0.07, 0.07) * (uSkyColor + uSunColor * 0.15);
                vec3 col = mix(deep, skyCol(vec3(r.x, abs(r.y), r.z)), f);
                frag = vec4(tone(fog(col, vW)), 1.0);
            }
            """;
}
