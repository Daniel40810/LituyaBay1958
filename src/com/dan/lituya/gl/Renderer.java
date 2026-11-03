package com.dan.lituya.gl;

import com.dan.lituya.app.Bibliothek;
import com.dan.lituya.camera.Ansichten;
import com.dan.lituya.camera.Camera;
import com.dan.lituya.scene.Physik;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.Sun;
import com.dan.lituya.wiring.Wiring;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Die Engine auf der Grafikkarte, übernommen aus Timmendorf und entschlackt (F6 A): alles, was zur Lübecker Bucht gehörte
 * (Gelände, Strand, Bauten, Boote, Feuerwerk, Wetter, Klang, Regie), ist heraus; geblieben ist das Gerüst, an das die
 * Phasen 4 bis 20 ihre Durchgänge hängen. Je Bild in Phase 1:
 * <ol>
 * <li><b>Himmel</b>: der Verlauf nach der Sonnenhöhe ({@code sky.frag}, Platzhalter bis {@code com.dan.sky} in
 * Phase 5a) in einen HDR-Puffer (RGBA16F, umgekehrte 32F-Tiefe).</li>
 * <li><b>Wasser</b>: die Fläche im Pegel mit Erdkrümmung bis zur Kimm ({@code meer.frag}, Platzhalter bis {@code ocean}
 * in Phase 5a), darauf die Marken: UTM-Raster, Kern, Szene und Fern, die Orte aus GNIS ({@code marken.glsl}).
 * Ab Phase 4 kommt das Gelände aus {@code clipmap} davor.</li>
 * <li><b>Welle</b> (ab Phase 13b): Welle und Felssturz der Vorrechnung als Höhenfelder ({@link EreignisGl}); im Rechteck
 * der Welle zeichnet das Wasser darunter nur einen Boden 40 m unter dem Pegel.</li>
 * <li><b>Staub</b> (ab Phase 13b): die Wolke des Felssturzes als Volumen und die Trübe, wenn das Auge unter Wasser ist.</li>
 * <li><b>Nachbearbeitung</b>: Belichtung nach der Sonnenhöhe, Filmkurve, Gamma.</li>
 * <li><b>HUD</b>: Tempo (Bilder/s, Zeit der Grafikkarte je Durchgang, Prozessor), die drei Uhren (Weltzeit,
 * Simulationszeit, Zeitstand), Sonne, Kamera, Ort in UTM 8 N, Gebiet, Kimm.</li>
 * </ol>
 * Geblieben aus Timmendorf: Qualitätsstufen mit Auflösungsregelung, Zeitmessung jedes Durchgangs auf der Grafikkarte
 * (Zeitstempel, ein Bild versetzt gelesen), Compute-Test beim Start, HUD kurz/voll/aus, Foto (F12), Shader neu laden
 * (F5), Standbilder ohne Fenster ({@code tools.Tempo}), der Faden der Physik ({@link Physik}) mit festem Schritt und der
 * Kinomodus ({@code com.dan.kino}) als Anschluss für die Regiebücher in Phase 17. Nah- und Fernebene reichen vom Kutter
 * bis zur Kimm des Fairweather (0,3 m bis 400 km), mit umgekehrter Tiefe. Im Bildlauf werden keine Dateien gelesen.
 */
public final class Renderer implements GLEventListener {
    public static final int MIN_MAJOR = 4, MIN_MINOR = 3;
    public static final String VERSION = "Phase 9";
    /** Phase des Stands (für Ansichten und Bedienfeld). */
    public static final int PHASE = 16;
    public static final String[] PASSES = {"Himmel", "Gelände", "Wald", "Boote", "Welle", "Wasser", "Staub", "Nachbearbeitung", "HUD"};
    private static final int MARKS = PASSES.length + 1;
    /** Nah- und Fernebene (m): vom Kutter bis über die Kimm des Mount Fairweather (264 km). */
    static final double NEAR = 0.3, FAR = 400_000;
    /** Höhe „kein Grund“, solange das Gelände nicht geladen ist oder fehlt: unter jedem Wasser (m über NAVD88). */
    public static final double KEIN_GRUND = -10_000;

    public final SceneClock clock;
    public final Camera camera = new Camera();
    /** Der Faden der Physik (F6 A); Phase 1 ohne Aufgaben. */
    public final Physik physik = new Physik();
    /** Die Verdrahtung der Packages (Phase 1: Pegel und Wind als offene Werte). */
    public final Wiring wiring;
    /** Phase 17: der Kinomodus (com.dan.kino); bis dahin nur der Anschluss für die Tasten der Szene. */
    public final com.dan.kino.Kino kino = new com.dan.kino.Kino();
    /** Phase 4: das Gelände aus daten/gelaende über com.dan.clipmap (vor dem Konstruktor angelegt, setQuality braucht es). */
    public final com.dan.lituya.scene.Land land = new com.dan.lituya.scene.Land();
    private final com.dan.clipmap.gl.ClipmapGl clipGl = new com.dan.clipmap.gl.ClipmapGl();
    /** Phase 7a: der Netzblick (Bild im Bild und in der Szene) mit com.dan.netview über daten/netz/welle und daten/netz/ufer. */
    public final Netzblick netzblick = new Netzblick();
    /** Das Gelände zeigen (G … im Bedienfeld TECHNIK). */
    public volatile boolean showGelaende = true;
    /** Phase 13b: das Abspielen der Vorrechnung (daten/ereignis) mit Vorrat und die Durchgänge dazu. */
    public final com.dan.lituya.scene.Wiedergabe wiedergabe = new com.dan.lituya.scene.Wiedergabe();
    final EreignisGl ereignisGl = new EreignisGl();
    /** Phase 13c: „Was wäre wenn“ in Echtzeit (wave.Bay auf der Grafikkarte, LOW). */
    public final Echtzeit echtzeit = new Echtzeit();
    /** Phase 14a: der Wald (forest mit succession) über den Kern, Zustände nach dem Zeitstand, Treibgut am Morgen. */
    public final WaldGl wald = new WaldGl();
    /** Phase 15a: die drei Troller auf ihren vorgerechneten Bahnen (tools.Boote1958). */
    public final BooteGl boote = new BooteGl();
    /** Phase 14a: Jahres-Zeitraffer (Jahre je Minute, in ganzen Jahren; Tag und Uhrzeit bleiben), hält am 9.7.2026. */
    public volatile boolean jahresRaffer;
    public volatile double jahreJeMinute = 68;
    private double rafferTage;
    /** Der Rückbau des Gilbert Inlet auf 1958 im Gelände (an, solange der Zeitstand nicht „heute“ ist). */
    public volatile boolean rueckbau = true;
    private boolean rueckbauJetzt = true;
    /** Texturstelle der Höhen der Clipmap. */
    static final int UNIT_CLIP = 3;

    private volatile Quality quality = Quality.MITTEL;
    public volatile double renderScale = quality.renderScale;
    /** Auflösung nach der Zeit der Grafikkarte regeln (nie unter {@link #MIN_SCALE}, nie über der Stufe). */
    public volatile boolean autoScale = true;
    public volatile double targetFps = 60;
    public static final double MIN_SCALE = 0.5;
    private long scaleNanos;
    private int scaleSteps;
    /** HUD an, kurz oder voll (H). */
    public volatile boolean showHud = true, hudFull;
    /** Die Marken zur Ausrichtung (UTM-Raster, Kern, Szene, Fern, Orte); in Phase 1 an. */
    public volatile boolean showMarks = true;
    /** Das Wasser zeigen (Platzhalter bis Phase 5a). */
    public volatile boolean showMeer = true;
    /** Szene steht (für Standbilder und die Leertaste im Kino später). */
    public volatile boolean frozen;
    /** Belichtung von Hand in Blenden. */
    public volatile double exposureBias;
    public volatile boolean photoRequest;
    /** Ein Bild abholen (einmal, im GL-Faden), etwa für Werkzeuge. */
    public volatile Consumer<BufferedImage> grabRequest;
    /** Ordner und Name des nächsten Fotos (ohne Angabe docs/status und {@code LituyaBay1958_Datum_Zeit}). */
    public volatile File photoDir;
    public volatile String photoName;

    private final Shader skyShader = new Shader("Himmel", null, "fullscreen.vert", "sky.frag"),
            meerShader = new Shader("Wasser", null, "fullscreen.vert", "meer.frag"),
            gelaendeShader = new Shader("Gelände", null, "gelaende.vert", "gelaende.frag"),
            post = new Shader("Nachbearbeitung", null, "fullscreen.vert", "post.frag"),
            overlay = new Shader("Überlagerung", null, "fullscreen.vert", "overlay.frag");
    private final Shader[] all = {skyShader, gelaendeShader, meerShader, post, overlay, ereignisGl.welle, ereignisGl.strom, ereignisGl.medien};
    private final HudOverlay hud = new HudOverlay();
    private int emptyVao, hdrTex, depthTex, hdrFbo, hdrColorFbo, hdrW, hdrH, surfW = 1, surfH = 1, defaultFbo;
    private boolean reversedZ, ready;
    private final int[][] stamp = new int[2][MARKS];
    private final double[] passMs = new double[MARKS - 1];
    private int frame;
    private long lastNanos, statsNanos;
    private int statsFrames;
    private double fps, gpuMs, cpuMs, worstMs;
    private volatile String glInfo = "", error;
    private volatile ComputeProbe.Result compute;
    private volatile boolean reloadShaders;
    private Consumer<String> statusListener = s -> { };
    private Consumer<String> errorListener = s -> { };
    private volatile String photoMsg = "", meldung = "";
    private volatile long photoMsgUntil, meldungUntil;
    /** Arbeiten aus anderen Fäden, im Bildlauf vor dem Schritt ausgeführt. */
    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> imBild = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public Renderer(SceneClock clock) {
        this.clock = clock;
        this.wiring = new Wiring(clock);
        setQuality(quality);
        camera.waterLevel = wiring.pegel;
        camera.groundLevel = groundHeight(camera.eye[0], camera.eye[2]);
        camera.targetGround = groundHeight(camera.tx, camera.tz);
        camera.snap();
    }

    /** Für Prüfungen und Werkzeuge: Uhr am Start (9.7.1958, 22:00). */
    public Renderer() { this(new SceneClock()); }

    public void setStatusListener(Consumer<String> l) { statusListener = l; }
    public void setErrorListener(Consumer<String> l) { errorListener = l; }
    public String glInfo() { return glInfo; }
    public String error() { return error; }
    public double fps() { return fps; }
    public double gpuMs() { return gpuMs; }
    public double cpuMs() { return cpuMs; }
    public boolean reversedDepth() { return reversedZ; }
    public ComputeProbe.Result compute() { return compute; }
    public Quality quality() { return quality; }
    public void requestShaderReload() { reloadShaders = true; }
    public double[] lastPassTimes() { return passMs.clone(); }
    public long frames() { return frame; }
    public double takeWorstFrameMs() { double w = worstMs; worstMs = 0; return w; }
    public double fovy() { return Camera.FOVY; }

    /** Alle Shader-Programme mit ihren Dateien (für die Prüfung). */
    public String[][] shaderFiles() {
        String[][] f = new String[all.length][];
        for (int i = 0; i < f.length; i++) f[i] = all[i].files();
        return f;
    }

    public void setQuality(Quality q) {
        quality = q;
        renderScale = q.renderScale;
        // Phase 4: die Clipmap folgt der Stufe eins zu eins (NIEDRIG 129², MITTEL 193², HOCH 257², ULTRA 385²)
        land.clipmap(com.dan.clipmap.Clipmap.Quality.values()[q.ordinal()]);
        wald.stufe(q);
    }

    /** Höhe des Geländes (m über NAVD88) aus der Clipmap; {@link #KEIN_GRUND}, solange nichts geladen ist. */
    public double groundHeight(double x, double z) {
        double h = land.hoehe(x, z);
        return Double.isNaN(h) ? KEIN_GRUND : h;
    }

    /** Stellt die Kamera auf eine Ansicht nach Namen. */
    public boolean placeView(Ansichten.Ansicht a) {
        camera.waterLevel = wiring.pegel;
        boolean ok = Ansichten.apply(a, camera, PHASE, this::groundHeight);
        if (ok) hudMeldung("Ansicht " + a);
        return ok;
    }

    /** Stellt Kamera und Uhr auf ein Schaustück (Phase 13b) und schaltet das Abspielen an. */
    public void placeStueck(com.dan.lituya.camera.Schaustuecke.Stueck st) {
        camera.waterLevel = wiring.pegel;
        com.dan.lituya.camera.Schaustuecke.apply(st, camera, clock, this::groundHeight);
        jahresRaffer = st.jahre();
        rafferTage = 0;
        wiedergabe.an(true);
        netzblick.inSzene = st.netze();
        wiedergabe.setzen(clock.simSeconds(), clock.lapse);
        int bi = com.dan.lituya.camera.Schaustuecke.boot(st);
        if (bi >= 0) {
            boote.laden(new java.io.File(System.getProperty("lituya.daten", "daten")), wiedergabe.welle());
            java.util.function.Supplier<double[]> pose = mitfahrtBoot(bi, bi == 1);
            if (pose.get() != null) camera.startMitfahrt(pose, com.dan.lituya.camera.Schaustuecke.blick(st));
            else hudMeldung(boote.status());
        }
        hudMeldung("Schaustück " + com.dan.lituya.camera.Schaustuecke.describe(st));
    }

    /**
     * Phase 15a: Mitfahrt auf dem Boot i zur Ereigniszeit der Uhr: im Steuerhaus mit Kurs, Stampfen und Rollen, oder
     * nebenher (25 m an Steuerbord, 4 m über dem Schwerpunkt, nur der Kurs, ohne Stampfen und Rollen).
     */
    public java.util.function.Supplier<double[]> mitfahrtBoot(int i, boolean nebenher) {
        return () -> {
            double t = clock.simSeconds();
            if (!nebenher) return boote.steuerhaus(i, t);
            double[] l = boote.lage(i, t);
            if (l == null) return null;
            double h = Math.toRadians(l[3]);
            double sx = Math.cos(h), sz = Math.sin(h);             // Steuerbord: Kurs + 90° (x Ost, z Süd)
            double d = com.dan.lituya.camera.Schaustuecke.NEBENHER;
            return new double[]{l[0] + sx * d, l[1] + com.dan.lituya.camera.Schaustuecke.NEBENHER_HOCH, l[2] + sz * d, l[3], 0, 0};
        };
    }

    /** „Zeitlupe 1:20 rückwärts“, „Echtzeit“, „steht“ */
    public String tempoText() {
        double l = clock.lapse;
        if (l == 0) return "Uhr steht";
        double a = Math.abs(l);
        String t = a == 1 ? "Echtzeit" : a < 1 ? String.format(Locale.GERMAN, "Zeitlupe 1:%.0f", 1 / a) : String.format(Locale.GERMAN, "%.0f-fach", a);
        return l < 0 ? t + " rückwärts" : t;
    }

    /** Die Zeile des Ereignisses für das HUD (leer außerhalb der zehn Minuten). */
    public String ereignisZeile() {
        double t = clock.simSeconds();
        if (!wiedergabe.an() || t < -60 || t > 1800) return "";
        String was = (ereignisGl.zeigtWelle ? "Welle " + wiedergabe.welle() : "") + (ereignisGl.zeigtStrom ? (ereignisGl.zeigtWelle ? ", " : "") + "Strom" : "")
                + (ereignisGl.zeigtStaub ? (ereignisGl.zeigtWelle || ereignisGl.zeigtStrom ? ", " : "") + "Staub" : "");
        double u = ereignisGl.ueberAuge;
        return String.format(Locale.GERMAN, "Ereignis t = %.1f s · %s · %s%s", t, tempoText(), was.isEmpty() ? (t < 93 ? "vor dem Sturz" : "lädt …") : was,
                u > 0 ? String.format(Locale.GERMAN, " · Auge %.1f m unter Wasser", u) : "");
    }

    /** Eine Meldung für 5 s im HUD (und in der Statuszeile). */
    public void hudMeldung(String text) {
        meldung = text;
        meldungUntil = System.currentTimeMillis() + 5000;
    }

    /** Belichtung aus der Sonnenhöhe des Platzhalter-Himmels (am Tag rund 1,9), dazu die Blenden von Hand. */
    public double exposure() {
        double b = Sun.skyBrightness(clock.sun()[0]);
        return 0.6 * Math.pow(b, -0.85) * Math.pow(2, exposureBias);
    }

    /** Kinomodus verlassen (Esc im Kino; ab Phase 17 läuft das Regiebuch dabei weiter). */
    public void kinoVerlassen() { kino.abbrechen(); }

    /** Eine Arbeit im Faden des Bildlaufs (vor dem nächsten Schritt). */
    public void imBild(Runnable r) { imBild.add(r); }

    // ------------------------------------------------------------------ GL

    @Override
    public void init(GLAutoDrawable d) {
        initGl(d.getGL().getGL4(), d.getContext().getGLVersionNumber().getMajor(), d.getContext().getGLVersionNumber().getMinor(),
                d.getContext().getDefaultDrawFramebuffer());
    }

    /** Start auf einer Grafikkarte ohne Fenster-Hülle (für {@link #init} und Prüfstände): Version, Bildpuffer des Ziels. */
    public void initGl(GL4 gl, int major, int minor, int targetFbo) {
        String renderer = gl.glGetString(GL.GL_RENDERER), vendor = gl.glGetString(GL.GL_VENDOR), version = gl.glGetString(GL.GL_VERSION);
        glInfo = renderer + " · OpenGL " + major + "." + minor;
        System.out.println("LituyaBay1958: " + vendor + " · " + renderer + " · " + version);
        if (major < MIN_MAJOR || (major == MIN_MAJOR && minor < MIN_MINOR)) {
            fail("Die Grafikkarte meldet OpenGL " + major + "." + minor + " (" + renderer + ", Treiber " + version
                    + "). LituyaBay1958 braucht OpenGL " + MIN_MAJOR + "." + MIN_MINOR + ".\nBitte den Grafiktreiber aktualisieren.");
            return;
        }
        gl.setSwapInterval(1);
        defaultFbo = targetFbo;
        reversedZ = (major > 4 || minor >= 5) || gl.isExtensionAvailable("GL_ARB_clip_control");
        if (reversedZ) gl.glClipControl(GL4.GL_LOWER_LEFT, GL4.GL_ZERO_TO_ONE);
        try {
            for (Shader s : all) s.build(gl);
        } catch (RuntimeException e) {
            fail(e.getMessage());
            return;
        }
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        emptyVao = v[0];
        for (int[] s : stamp) gl.glGenQueries(MARKS, s, 0);
        compute = ComputeProbe.run(gl);
        try {
            netzblick.init(gl);
            wiedergabe.start();
            netzblick.laden(new java.io.File(System.getProperty("lituya.daten", "daten")));
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Netzblick: " + e.getMessage());
        }
        if (!Boolean.getBoolean("lituya.ohneWald")) wald.aufbauen(new java.io.File(System.getProperty("lituya.daten", "daten")));
        boote.laden(new java.io.File(System.getProperty("lituya.daten", "daten")), wiedergabe.welle());
        System.out.println("LituyaBay1958: " + boote.status());
        System.out.println("LituyaBay1958: " + compute.line());
        physik.start();
        lastNanos = System.nanoTime();
        statsNanos = lastNanos;
        camera.snap();
        ready = true;
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
    }

    /** Uhr, Physik und Kamera um dt weiterrechnen (vom Bildlauf aufgerufen; Standbilder rufen es mit festem dt). */
    public void step(double dt) {
        for (Runnable r; (r = imBild.poll()) != null; ) {
            try { r.run(); } catch (RuntimeException e) { System.err.println("LituyaBay1958: Arbeit im Bildlauf: " + e); }
        }
        clock.step(dt);
        if (jahresRaffer) {
            // ganze Jahre am selben Tag und zur selben Uhrzeit (die Jahreszeit bleibt; mit Tagen landete der Sprung im Winter)
            rafferTage += dt * jahreJeMinute / 60;
            java.time.LocalDate ende = SceneClock.Zeitstand.HEUTE.date;
            while (rafferTage >= 1 && jahresRaffer) {
                rafferTage -= 1;
                java.time.LocalDate n = clock.date().plusYears(1);
                if (!n.isBefore(ende)) { clock.set(ende, clock.hour()); clock.zeitstand = SceneClock.Zeitstand.HEUTE; jahresRaffer = false; hudMeldung("Jahres-Zeitraffer: heute erreicht"); }
                else clock.set(n, clock.hour());
            }
        }
        kino.schritt(dt);
        land.auge(camera.eye[0], camera.eye[2]);
        physik.advance(dt);
        camera.groundLevel = groundHeight(camera.eye[0], camera.eye[2]);
        camera.waterLevel = wiring.pegel;
        camera.targetGround = Math.max(groundHeight(camera.tx, camera.tz), wiring.pegel);
        camera.step(dt);
        netzblick.schritt(clock.simSeconds(), wiring.pegel);
    }

    @Override
    public void display(GLAutoDrawable d) {
        displayGl(d.getGL().getGL4(), d.getSurfaceWidth(), d.getSurfaceHeight(), d.getContext().getDefaultDrawFramebuffer());
    }

    /** Ein Bild in das Ziel targetFbo der Größe w × h (für {@link #display} und Prüfstände). */
    public void displayGl(GL4 gl, int width, int height, int targetFbo) {
        if (!ready) {
            gl.glClearColor(0.06f, 0.075f, 0.08f, 1);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            return;
        }
        if (reloadShaders) {
            reloadShaders = false;
            try {
                for (Shader s : all) s.build(gl);
                hudMeldung("Shader neu geladen");
                System.out.println("Shader neu geladen.");
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                errorListener.accept(e.getMessage());
            }
        }
        defaultFbo = targetFbo;
        readMarks(gl);
        long now = System.nanoTime();
        double rawMs = (now - lastNanos) / 1e6;
        if (frame > 2 && rawMs > worstMs) worstMs = rawMs;
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (!frozen) step(dt);

        surfW = Math.max(1, width);
        surfH = Math.max(1, height);
        regulateScale(now);
        int w = Math.max(64, (int) Math.round(surfW * renderScale)), h = Math.max(64, (int) Math.round(surfH * renderScale));
        if (w != hdrW || h != hdrH) resizeTargets(gl, w, h);
        renderFrame(gl, w, h);
        if (photoRequest) { photoRequest = false; takePhoto(gl, surfW, surfH); }
        Consumer<BufferedImage> gr = grabRequest;
        if (gr != null) { grabRequest = null; try { gr.accept(readDefault(gl, surfW, surfH)); } catch (RuntimeException e) { System.err.println("LituyaBay1958: Bild nicht abgeholt: " + e); } }
        cpuMs = cpuMs * 0.95 + (System.nanoTime() - now) / 1e6 * 0.05;
        frame++;
        stats(now, w, h);
    }

    void renderFrame(GL4 gl, int w, int h) {
        mark(gl, 0);
        // 0 Phase 13c: die Echtzeit rechnet hinter der Uhr her (Zeitbudget je Bild), bevor gezeichnet wird
        try {
            if (echtzeit.schritt(gl, clock.simSeconds(), wiedergabe) && echtzeit.grund == Echtzeit.Grund.HEUTE) rueckbauJetzt = false;
            else rueckbauJetzt = rueckbau;
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Echtzeit: " + e);
            echtzeit.aus();
        }
        double[] sp = clock.sun();
        double[] sun = Sun.direction(sp[0], sp[1]);
        float[] proj = Mat4.perspective(fovy(), w / (double) h, NEAR, FAR, reversedZ);
        float[] vp = Mat4.mul(proj, camera.view());
        float[] inv = Mat4.invert(vp);
        try {
            wald.vorbereiten(gl, clock);
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Wald: " + e);
            wald.an = false;
        }

        // 1 Himmel: der Verlauf nach der Sonnenhöhe (Platzhalter bis com.dan.sky in Phase 5a)
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearDepth(reversedZ ? 0 : 1);
        gl.glClearColor(0, 0, 0, 1);
        gl.glDepthMask(true);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(emptyVao);
        common(gl, skyShader, vp, inv, sun);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        mark(gl, 1);

        // 2 Gelände (Phase 4): alle Stufen der Clipmap mit Tiefe, Rückseiten weg
        com.dan.clipmap.Clipmap cm = land.clipmap();
        if (showGelaende && cm != null) {
            clipGl.upload(gl, cm);
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            gl.glDepthMask(true);
            gl.glEnable(GL.GL_CULL_FACE);
            gl.glCullFace(GL.GL_BACK);
            gl.glFrontFace(GL.GL_CCW);
            common(gl, gelaendeShader, vp, inv, sun);
            bindMarks(gl, gelaendeShader);
            wald.gelaende(gl, gelaendeShader);
            boolean rb = rueckbauJetzt && clock.zeitstand != SceneClock.Zeitstand.HEUTE;
            gelaendeShader.set(gl, "uRueckbauAn", rb ? 1 : 0);
            double[] r0 = Site.fromUtm(com.dan.lituya.scene.Boden1958.RUECKBAU_E0, com.dan.lituya.scene.Boden1958.RUECKBAU_N0),
                    r1 = Site.fromUtm(com.dan.lituya.scene.Boden1958.RUECKBAU_E1, com.dan.lituya.scene.Boden1958.RUECKBAU_N1);
            gelaendeShader.set(gl, "uRueckbauRect", (float) r0[0], (float) r0[1], (float) r1[0], (float) r1[1]);
            gelaendeShader.set(gl, "uRueckbau", (float) com.dan.lituya.scene.Boden1958.RUECKBAU_UNTER, (float) com.dan.lituya.scene.Boden1958.RUECKBAU_TIEFE);
            clipGl.draw(gl, gelaendeShader.id(), cm, vp, camera.eye, UNIT_CLIP);
            gl.glDisable(GL.GL_CULL_FACE);
            gl.glDisable(GL.GL_DEPTH_TEST);
            gl.glBindVertexArray(emptyVao);
        }
        mark(gl, 2);

        // 2a Wald (Phase 14a): forest über den Kern mit den Zuständen der Welle und der Wiederbewaldung
        if (showGelaende) wald.zeichnen(gl, vp, camera.eye, reversedZ, sun, clock.t());
        gl.glBindVertexArray(emptyVao);
        gl.glUseProgram(0);
        mark(gl, 3);

        // 2b Boote (Phase 15a): Edrie, Badger, Sunmore auf den vorgerechneten Bahnen, vor der Welle (sie deckt, was unter Wasser liegt)
        double tE = clock.simSeconds();
        boote.laden(new java.io.File(System.getProperty("lituya.daten", "daten")), wiedergabe.welle());
        boote.zeichnen(gl, vp, camera.eye, reversedZ, sun, tE, clock.zeitstand);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 4);

        // 2c Welle und Strom der Vorrechnung (Phase 13b) mit Tiefe; im Rechteck der Welle zeichnet das Meer nicht
        wiedergabe.setzen(tE, clock.lapse);
        double[] welleRect = ereignisGl.zeichnen(gl, wiedergabe, tE, vp, camera.eye, h, wiring.pegel, sh -> common(gl, sh, vp, inv, sun), reversedZ);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 5);

        // 3 Wasser: die Fläche im Pegel mit Erdkrümmung bis zur Kimm (Platzhalter bis ocean in Phase 5a), mit den Marken;
        //   seit Phase 4 mit Tiefe gegen das Gelände
        if (showMeer) {
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(reversedZ ? GL.GL_GEQUAL : GL.GL_LEQUAL);
            gl.glDepthMask(false);
            common(gl, meerShader, vp, inv, sun);
            bindMarks(gl, meerShader);
            wald.meer(gl, meerShader);
            meerShader.set(gl, "uWelle", welleRect != null ? 1 : 0);
            if (welleRect != null) meerShader.set(gl, "uWelleRect", (float) welleRect[0], (float) welleRect[1], (float) welleRect[2], (float) welleRect[3]);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glDepthMask(true);
            gl.glDisable(GL.GL_DEPTH_TEST);
        }
        // 3b Netze auf dem Wasser (Phase 7a, Netzblick „in der Szene“): Linien in Bildpunkten, Tiefe gegen das Gelände
        netzblick.szene(gl, vp, camera.eye, w, h, reversedZ, exposure());
        gl.glBindVertexArray(emptyVao);
        mark(gl, 6);

        // 3c Staub und Trübe unter Wasser (Phase 13b): über das HDR-Bild, die Tiefe als Textur
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
        ereignisGl.medien(gl, wiedergabe, tE, depthTex, sh -> common(gl, sh, vp, inv, sun), forward(camera.view()), reversedZ);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 7);

        // 4 Nachbearbeitung ins Fenster
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, defaultFbo);
        gl.glViewport(0, 0, surfW, surfH);
        post.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        post.set(gl, "uHdr", 0);
        post.set(gl, "uExposure", (float) exposure());
        post.set(gl, "uScotopic", 0f);
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        mark(gl, 8);

        // 4b Netzblick als Bild im Bild unten rechts (Phase 7a)
        if (!kino.laeuft()) netzblick.bildImBild(gl, overlay, emptyVao, surfW, surfH, netzZeit());
        gl.glBindVertexArray(emptyVao);

        // 5 HUD (vorher abschicken: die Grafikkarte arbeitet, während das HUD im selben Faden gemalt wird; aus Brocken)
        gl.glFlush();
        if (showHud && !kino.laeuft()) {
            hud.refresh(gl, 250, this::paintHud, frozen);
            hud.draw(gl, overlay, emptyVao, 12, Math.max(0, surfH - hud.height - 12), surfW, surfH);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        mark(gl, 9);
    }

    /**
     * Phase 13c: der Ort in der Szene unter dem Bildpunkt (x, y) eines Bilds w × h (Strg-Klick): Strahl vom Auge bis aufs
     * Gelände oder Wasser, {x, z} oder null.
     */
    public double[] treffer(int x, int y, int w, int h) {
        float[] v = camera.view();
        double[] right = {v[0], v[4], v[8]}, up = {v[1], v[5], v[9]}, back = {v[2], v[6], v[10]};
        double ty = Math.tan(Math.toRadians(fovy()) / 2), tx = ty * w / Math.max(1.0, h);
        double sx = (2.0 * (x + 0.5) / w - 1) * tx, sy = (1 - 2.0 * (y + 0.5) / h) * ty;
        double[] d = new double[3];
        for (int i = 0; i < 3; i++) d[i] = right[i] * sx + up[i] * sy - back[i];
        double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        for (int i = 0; i < 3; i++) d[i] /= l;
        double t = 0, step = 2, prev = 0;
        for (int i = 0; i < 4000 && t < 60_000; i++) {
            double px = camera.eye[0] + d[0] * t, py = camera.eye[1] + d[1] * t, pz = camera.eye[2] + d[2] * t;
            if (py <= Math.max(groundHeight(px, pz), wiring.pegel)) {
                double a = prev, b = t;
                for (int k = 0; k < 30; k++) {
                    double m = (a + b) / 2, qx = camera.eye[0] + d[0] * m, qy = camera.eye[1] + d[1] * m, qz = camera.eye[2] + d[2] * m;
                    if (qy <= Math.max(groundHeight(qx, qz), wiring.pegel)) b = m; else a = m;
                }
                return new double[]{camera.eye[0] + d[0] * b, camera.eye[2] + d[2] * b};
            }
            prev = t;
            t += step;
            step = Math.min(200, step * 1.02 + 0.5);
        }
        return null;
    }

    /** Blickrichtung aus der Blickmatrix (Spalten): die dritte Zeile, umgedreht. */
    static float[] forward(float[] v) { return new float[]{-v[2], -v[6], -v[10]}; }

    private void bindMarks(GL4 gl, Shader s) {
        s.set(gl, "uMarks", showMarks ? 1 : 0);
        rect(gl, s, "uRectKern", Site.KERN);
        rect(gl, s, "uRectSzene", Site.SZENE);
        rect(gl, s, "uRectFern", Site.FERN);
        float[] pts = points();
        s.vec4Array(gl, "uPoints", pts);
        s.set(gl, "uPointCount", pts.length / 4);
        s.set(gl, "uGridOff", (float) Math.floorMod((long) Site.E0, 10_000L), (float) Math.floorMod(-(long) Site.N0, 10_000L));
    }

    private static void rect(GL4 gl, Shader s, String u, Site.Fenster f) {
        double[] r = f.scene();
        s.set(gl, u, (float) r[0], (float) r[1], (float) r[2], (float) r[3]);
    }

    /** Die Orte als vec4 (x, z, Halbmesser m, Art) für marken.glsl (höchstens 32). */
    static float[] points() {
        Site.Ort[] o = Site.ORTE;
        float[] p = new float[Math.min(32, o.length) * 4];
        for (int i = 0; i < p.length / 4; i++) {
            p[i * 4] = (float) o[i].x();
            p[i * 4 + 1] = (float) o[i].z();
            p[i * 4 + 2] = o[i].art() == Site.Art.GIPFEL ? 250f : o[i].art() == Site.Art.SPORN ? 160f : 120f;
            p[i * 4 + 3] = o[i].art().ordinal();
        }
        return p;
    }

    private void common(GL4 gl, Shader s, float[] vp, float[] inv, double[] sun) {
        s.use(gl);
        s.set(gl, "uSkyMode", 0);
        s.mat(gl, "uVP", vp);
        s.mat(gl, "uInvVP", inv);
        s.set(gl, "uCam", camera.eye);
        s.set(gl, "uSun", sun);
        s.set(gl, "uTime", (float) (clock.t() % 10_000));
        s.set(gl, "uLevel", (float) wiring.pegel);
        s.set(gl, "uReversed", reversedZ ? 1 : 0);
    }

    private void resizeTargets(GL4 gl, int w, int h) {
        Gpu.deleteFbos(gl, hdrFbo, hdrColorFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        hdrTex = Gpu.texture(gl, w, h, GL.GL_RGBA16F, false);
        depthTex = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        hdrFbo = Gpu.fbo(gl, hdrTex, depthTex);
        hdrColorFbo = Gpu.fbo(gl, hdrTex, 0);
        hdrW = w;
        hdrH = h;
    }

    // ------------------------------------------------------------------ Zeitmessung (Tempo)

    private void mark(GL4 gl, int i) { gl.glQueryCounter(stamp[frame & 1][i], GL4.GL_TIMESTAMP); }

    /** Liest die Zeitstempel des vorigen Bilds, sobald sie da sind (kein Warten im Bildlauf). */
    private void readMarks(GL4 gl) {
        if (frame < 2) return;
        int o = (frame - 1) & 1;
        int[] av = new int[1];
        gl.glGetQueryObjectiv(stamp[o][MARKS - 1], GL4.GL_QUERY_RESULT_AVAILABLE, av, 0);
        if (av[0] == 0) return;
        long[] t = new long[MARKS];
        for (int i = 0; i < MARKS; i++) gl.glGetQueryObjecti64v(stamp[o][i], GL4.GL_QUERY_RESULT, t, i);
        for (int i = 0; i < MARKS - 1; i++) passMs[i] = passMs[i] * 0.9 + Math.max(0, t[i + 1] - t[i]) / 1e6 * 0.1;
        gpuMs = gpuMs * 0.9 + Math.max(0, t[MARKS - 1] - t[0]) / 1e6 * 0.1;
    }

    /** „Himmel 0,10 · Gelände 0,60 · …“ in ms (gleitendes Mittel). */
    public String passTimes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < passMs.length; i++)
            sb.append(i > 0 ? " · " : "").append(PASSES[i]).append(' ').append(String.format(Locale.GERMAN, "%.2f", passMs[i]));
        return sb.toString();
    }

    /** Die n teuersten Durchgänge: „Gelände 1,20 · Meer 0,40“. */
    public String topPasses(int n) {
        Integer[] idx = new Integer[passMs.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(passMs[b], passMs[a]));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, idx.length); i++)
            sb.append(i > 0 ? " · " : "").append(PASSES[idx[i]]).append(' ').append(String.format(Locale.GERMAN, "%.2f", passMs[idx[i]]));
        return sb.toString();
    }

    private void stats(long now, int w, int h) {
        statsFrames++;
        if (now - statsNanos < 500_000_000L) return;
        fps = statsFrames / ((now - statsNanos) / 1e9);
        statsFrames = 0;
        statsNanos = now;
        statusListener.accept(statusLine(w, h));
    }

    /** Auflösung nach der Zeit der Grafikkarte regeln (siehe {@link #autoScale}). */
    private void regulateScale(long now) {
        if (!autoScale || frame < 60 || now - scaleNanos < 1_500_000_000L) return;
        scaleNanos = now;
        double budget = 1000 / Math.max(10, targetFps), top = quality.renderScale, s = renderScale;
        double want = s * Math.sqrt(budget * 0.9 / Math.max(0.1, gpuMs));
        double next = s;
        if (gpuMs > budget * 1.05) next = Math.max(MIN_SCALE, Math.max(want, s - 0.15));
        else if (gpuMs < budget * 0.7 && s < top) next = Math.min(top, Math.min(want, s + 0.05));
        next = Math.round(next * 20) / 20.0;
        if (Math.abs(next - s) >= 0.045) { renderScale = next; scaleSteps++; }
    }

    /** Wie oft die Regelung die Auflösung geändert hat. */
    public int scaleSteps() { return scaleSteps; }

    /** Für Prüfungen: die Regelung mit vorgegebener Grafikkartenzeit und Bildnummer. */
    public void regulateForTest(double gpuMsNow, long nowNanos) {
        gpuMs = gpuMsNow;
        frame = Math.max(frame, 60);
        regulateScale(nowNanos);
    }

    public String statusLine(int w, int h) {
        String s = String.format(Locale.GERMAN, "%s · %.0f Bilder/s · Grafikkarte %.2f ms · Prozessor %.2f ms · %d×%d (%s%s) · %s",
                clock.label(), fps, gpuMs, cpuMs, w, h, quality.label, autoScale ? String.format(Locale.GERMAN, ", %.0f %%", renderScale * 100) : "", glInfo);
        long nowMs = System.currentTimeMillis();
        if (nowMs < meldungUntil) s = meldung + " · " + s;
        if (nowMs < photoMsgUntil) s = photoMsg + " · " + s;
        return s;
    }

    // ------------------------------------------------------------------ HUD

    BufferedImage paintHud() {
        String[] lines = hudFull ? hudLines() : hudShort();
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        java.awt.FontMetrics fm = pg.getFontMetrics(font);
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, fm.stringWidth(l.startsWith("!") ? l.substring(1) : l));
        pg.dispose();
        int w = Math.max(200, tw + 22), lh = 17, h = 16 + lines.length * lh;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(14, 20, 22, 190));
        g.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
        g.setColor(new Color(110, 168, 170, 225));   // Gletschermilch, wie das Werkbuch
        g.setStroke(new BasicStroke(2f));
        g.drawLine(8, 1, 60, 1);
        g.setColor(new Color(232, 178, 92, 225));    // Goldkante der Spurlinie
        g.drawLine(62, 1, 80, 1);
        g.setFont(font);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            boolean warn = l.startsWith("!");
            if (warn) l = l.substring(1);
            g.setColor(i == 0 ? new Color(233, 236, 234) : warn ? new Color(224, 163, 63) : new Color(186, 196, 196));
            g.drawString(l, 10, 18 + i * lh);
        }
        g.dispose();
        return im;
    }

    /** Das kurze HUD: Tempo, Zeiten der Durchgänge, die drei Uhren und die Sonne, Kamera. */
    String[] hudShort() {
        java.util.List<String> l = new java.util.ArrayList<>();
        String q = autoScale ? String.format(Locale.GERMAN, "%s %.0f %%", quality.label, renderScale * 100) : quality.label;
        l.add(String.format(Locale.GERMAN, "%.0f Bilder/s · Grafikkarte %.1f ms · Prozessor %.1f ms · %s %d×%d", fps, gpuMs, cpuMs, q, hdrW, hdrH));
        l.add(passTimes());
        l.add(uhrenZeile());
        String ez = ereignisZeile();
        if (!ez.isEmpty()) l.add(ez);
        if (echtzeit.an() || echtzeit.baut()) l.add(echtzeit.zeile());
        l.add(cameraLine() + " · H: volles HUD, F1: Tasten");
        if (System.currentTimeMillis() < meldungUntil) l.add(meldung);
        if (error != null) l.add("!" + error.split("\n")[0]);
        return l.toArray(new String[0]);
    }

    /** „9.7.1958 22:00 PST · t = −16:00 · Zeitstand vor · Sonne 0,4° aus 318°“ */
    public String uhrenZeile() {
        double[] sp = clock.sun();
        return String.format(Locale.GERMAN, "%s · %s · Zeitstand %s · Sonne %.1f° aus %03.0f°", clock.label(), clock.simLabel(), clock.zeitstand.label, sp[0], sp[1]);
    }

    /** „Kreisen · Auge 1 905 m über dem Wasser · Blick 052° · Kern · Cenotaph Island 9,0 km“ */
    public String cameraLine() {
        double g = groundHeight(camera.eye[0], camera.eye[2]);
        String ueber = g <= KEIN_GRUND / 2 ? String.format(Locale.GERMAN, "%,.1f m über dem Wasser", camera.eye[1] - wiring.pegel)
                : String.format(Locale.GERMAN, "%,.1f m über NAVD88, %,.1f m über Grund", camera.eye[1], camera.eye[1] - Math.max(g, wiring.pegel));
        return String.format(Locale.GERMAN, "%s · Auge %s · Blick %03.0f° · %s · %s", camera.modeName(), ueber, camera.heading(),
                Site.gebiet(camera.eye[0], camera.eye[2]), Site.naechsterOrt(camera.eye[0], camera.eye[2]));
    }

    /** Das volle HUD (H): alles, was die Phase weiß. */
    public String[] hudLines() {
        ComputeProbe.Result c = compute;
        double[] ll = Site.latLon(camera.eye[0], camera.eye[2]);
        double[] utm = Site.toUtm(camera.eye[0], camera.eye[2]);
        double kimm = Site.horizon(camera.eye[1] - wiring.pegel);
        return new String[]{
                String.format(Locale.GERMAN, "%.0f Bilder/s   Grafikkarte %.2f ms   Prozessor %.2f ms   Budget %.1f ms", fps, gpuMs, cpuMs, quality.budgetMs()),
                passTimes(),
                (c == null ? "!Compute-Test läuft …" : (c.ok() ? "" : "!") + c.line()),
                uhrenZeile(),
                String.format(Locale.GERMAN, "Belichtung %.2g (Platzhalter-Himmel bis Phase 5a) · Zeitraffer %.0f×", exposure(), clock.lapse),
                wiring.zeile(),
                physik.describe(),
                land.zeile(),
                netzblick.info(),
                "Abspielen: " + wiedergabe.zeile() + " · " + ereignisGl.info(),
                echtzeit.zeile() + (echtzeit.an() ? " · " + echtzeit.messung() : ""),
                waldZeile(),
                boote.info() + " · " + fluchtZeile(),
                wasserZeile(),
                clipGl.info(),
                cameraLine(),
                String.format(Locale.GERMAN, "UTM 8 N  E %.0f  N %.0f · %s · Kimm %.1f km", utm[0], utm[1], Site.dms(ll[0], ll[1]), kimm / 1000),
                "Renderer.jar: " + Bibliothek.kurz(),
                "Neu für LituyaBay1958: " + Bibliothek.neuStand(),
                String.format(Locale.GERMAN, "%s · %d×%d · %s", quality.label, hdrW, hdrH, reversedZ ? "umgekehrte Tiefe" : "klassische Tiefe"),
                glInfo};
    }

    private String flucht;

    /** Phase 15a: das Ergebnis von LB_NET_SEE (einmal aus daten/netz/see/SEE.txt). */
    public String fluchtZeile() {
        if (flucht == null) flucht = com.dan.lituya.scene.SeeNetz.fluchtZeile(new java.io.File(System.getProperty("lituya.daten", "daten")));
        return flucht;
    }

    private String wasser;

    /** Phase 16: die Einzugsgebiete aus LB_NET_WASSER (einmal aus daten/netz/wasser/WASSER.txt). */
    public String wasserZeile() {
        if (wasser == null) wasser = com.dan.lituya.scene.WasserNetz.zeile(new java.io.File(System.getProperty("lituya.daten", "daten")));
        return wasser;
    }

    /** „Wald: Jahr 12 nach der Welle (1970) · …“ */
    public String waldZeile() {
        int g = wald.gezeigt();
        String j = g == -1 ? "vor der Welle" : g == -2 ? "die Welle läuft" : g >= 0 ? String.format(Locale.GERMAN, "Jahr %d nach der Welle (%d)", g, 1958 + g) : "";
        return "Wald: " + (wald.an ? "" : "aus · ") + j + (jahresRaffer ? String.format(Locale.GERMAN, " · Jahres-Zeitraffer %.0f Jahre/min", jahreJeMinute) : "") + " · " + wald.info();
    }

    /** Die Zeile mit der Zeit der Welle für das Bild im Bild. */
    String netzZeit() {
        double t = netzblick.zeit(clock.simSeconds());
        if (netzblick.ganz) return "alle Isochronen (ohne Zeit) · jede Minute kräftig";
        if (t < 0) return String.format(Locale.GERMAN, "vor dem Einschlag (%.0f s); Welle %.0f s nach dem ersten Stoß [G]", t, com.dan.lituya.scene.WellenNetz.START_S);
        if (t > 3600) return String.format(Locale.GERMAN, "Welle vor %.0f min · alle Isochronen", t / 60);
        return String.format(Locale.GERMAN, "Welle t = %.0f s nach dem Einschlag", t);
    }

    // ------------------------------------------------------------------ Bilder

    private void takePhoto(GL4 gl, int w, int h) {
        try {
            BufferedImage img = readDefault(gl, w, h);
            File dir = photoDir != null ? photoDir : new File("docs/status");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
            String name = photoName != null ? photoName : "LituyaBay1958_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            photoName = null;
            File f = new File(dir, name + ".png");
            javax.imageio.ImageIO.write(img, "png", f);
            photoMsg = "Foto gespeichert: " + f.getPath();
            System.out.println(photoMsg);
        } catch (Exception e) {
            photoMsg = "Foto fehlgeschlagen: " + e.getMessage();
            System.err.println(photoMsg);
        }
        photoMsgUntil = System.currentTimeMillis() + 5000;
    }

    /** Liest das fertige Fensterbild (nach Nachbearbeitung und HUD). */
    BufferedImage readDefault(GL4 gl, int w, int h) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, defaultFbo);
        gl.glReadBuffer(defaultFbo == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            int o = (h - 1 - y) * w * 4;
            for (int x = 0; x < w; x++) {
                int i = o + x * 4;
                row[x] = ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255);
            }
            img.setRGB(0, y, w, 1, row, 0, w);
        }
        return img;
    }

    /** Für Werkzeuge: das letzte Bild als Bild (im GL-Faden aufrufen). */
    public BufferedImage grab(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        return readDefault(gl, d.getSurfaceWidth(), d.getSurfaceHeight());
    }

    // ------------------------------------------------------------------ Ende

    private void fail(String msg) {
        error = msg;
        System.err.println(msg);
        errorListener.accept(msg);
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        physik.stop();
        GL4 gl = d.getGL().getGL4();
        for (Shader s : all) s.dispose(gl);
        clipGl.dispose(gl);
        wald.dispose(gl);
        boote.dispose(gl);
        com.dan.clipmap.Clipmap cm = land.clipmap();
        if (cm != null) cm.stop();
        hud.dispose(gl);
        netzblick.dispose(gl);
        ereignisGl.dispose(gl);
        echtzeit.dispose(gl);
        wiedergabe.close();
        Gpu.deleteFbos(gl, hdrFbo, hdrColorFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        if (emptyVao != 0) gl.glDeleteVertexArrays(1, new int[]{emptyVao}, 0);
        for (int[] s : stamp) if (s[0] != 0) gl.glDeleteQueries(MARKS, s, 0);
        ready = false;
    }
}
