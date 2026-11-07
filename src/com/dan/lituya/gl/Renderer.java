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
 * <li><b>Bäche</b> (ab Phase 16c): die Bäche aus {@code LB_NET_WASSER} als Bänder auf dem Gelände ({@link BaecheGl},
 * {@code baeche.vert/.frag}), Breite nach dem Regen im Bedienfeld; die Zeit steht unter „Wald“.</li>
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
    public static final String VERSION = "1.0 (Phase 20)";
    /** Phase des Stands (für Ansichten und Bedienfeld). */
    public static final int PHASE = 20;
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
    /** Phase 17a: der Kinomodus (com.dan.kino) für die Regiebücher. */
    public final com.dan.kino.Kino kino = new com.dan.kino.Kino();
    /** Phase 4: das Gelände aus daten/gelaende über com.dan.clipmap (vor dem Konstruktor angelegt, setQuality braucht es). */
    public final com.dan.lituya.scene.Land land = new com.dan.lituya.scene.Land();
    private final com.dan.clipmap.gl.ClipmapGl clipGl = new com.dan.clipmap.gl.ClipmapGl();
    /** Phase 7a: der Netzblick (Bild im Bild und in der Szene) mit com.dan.netview über daten/netz/welle und daten/netz/ufer. */
    public final Netzblick netzblick = new Netzblick();
    /** Phase 16c: die Bäche aus LB_NET_WASSER als eigener Wasser-Pass (Bänder auf dem Gelände, Breite nach Regen). */
    public final BaecheGl baeche = new BaecheGl();
    /** Werkbuch Welle W6 (L9): Wasserfälle über den Steilstufen (com.dan.fall auf den Bächen). */
    public final FaelleGl faelle = new FaelleGl();
    /** W6 Runde 3: Eisbrocken mit Höhe und Schatten um das Auge (com.dan.buoyancy, Renderer); dahinter flach in meer.frag. */
    public volatile boolean schollen = !Boolean.getBoolean("lituya.ohneSchollen");
    private final com.dan.buoyancy.Floes floes = new com.dan.buoyancy.Floes();
    private final com.dan.buoyancy.gl.FloeGl floeGl = new com.dan.buoyancy.gl.FloeGl();
    private double[] floesAuge;
    private double floesDichte = -1;
    private int floesAlter;
    /** Werkbuch Welle W1: Himmel aus com.dan.sky, Belichtung, Horizontkarten für den Schatten der Kette, Alpenglühen. */
    public final Licht licht = new Licht();
    /**
     * Werkbuch Welle W7 „Wetter“: die Wetterregie der Bucht (ein Zustand für Licht, Wasser, Wald, Bäche, Fälle, Nebel,
     * Regen und Klang) und das Wetter im Bild (Wolken am Gelände, Seenebel mit Vorstoß, Regen). 1958 fest nach den
     * Zeugen (WF13 A), heute frei.
     */
    public final com.dan.lituya.wiring.Wetter wetter = new com.dan.lituya.wiring.Wetter();
    public final WetterGl wetterGl = new WetterGl();
    /** W7: Regen von Hand auf die Bäche (Regler, Regiebuch „regen“, Bildbank „regen“), mm/h; dazu kommt der Abfluss der Wetterlage. */
    public volatile double baecheRegenHand = 0;
    private double wetterSzeneT = Double.NaN, wetterDt;
    /** Werkbuch Welle W2: das Wasser aus com.dan.ocean, Spiegel der Berge, Gletschermilch. */
    public final MeerGl meer = new MeerGl();
    /**
     * Werkbuch Welle W3b: die Materialien aus com.dan.surface (Phase 5 gebacken, bis W3 nie aufs Gelände gelegt) für Hänge
     * ohne Wald; Schichten Fels frisch, Fels verwittert, Moräne, Strandkies. Texturstellen 5, 6, 7.
     */
    public final com.dan.surface.gl.SurfaceGl surface = new com.dan.surface.gl.SurfaceGl();
    private final int[] surfMat = {0, 0, 0, 0};
    /** W6 Runde 2: Lagen für den Boden in der Nähe (Waldboden, Moos, Firn, Eis). */
    private final int[] surfMat2 = {0, 0, 0, 0};
    private String surfStatus = "Texturen: nicht geladen";
    /** Texturen der Hänge an (W3b). */
    public volatile boolean texturen = true;
    /** W1: Glühen um Helles (Sonne, Glanz, Gischt im Gegenlicht), Stärke; 0 aus. */
    public volatile double bloom = 0.35;
    private double letztesDt = 1 / 60.0;
    /** Werkbuch Welle W3 (E1): Kamerazittern beim Beben (WF8 A: in Echtzeit abschaltbar, im Kino immer, im Standbild nie). */
    public volatile boolean zittern = true;
    private final java.util.Random bebenRng = new java.util.Random(1958);
    private double ringeSchuld;

    /** Stärke des Bebens 0..1 zur Ereigniszeit t (s nach dem ersten Stoß), wie der Klang: 5 s steigend, bis 40 s voll, bis 60 s abklingend. */
    public static double bebenStaerke(double t) {
        if (t <= 0 || t >= Klang.BEBEN_BIS) return 0;
        return t < 5 ? t / 5 : t < 40 ? 1 : Math.max(0, (Klang.BEBEN_BIS - t) / 20);
    }
    /** Das Gelände zeigen (G … im Bedienfeld TECHNIK). */
    public volatile boolean showGelaende = true;
    /** Phase 13b: das Abspielen der Vorrechnung (daten/ereignis) mit Vorrat und die Durchgänge dazu. */
    public final com.dan.lituya.scene.Wiedergabe wiedergabe = new com.dan.lituya.scene.Wiedergabe();
    final EreignisGl ereignisGl = new EreignisGl();
    /** W3b: für das Bedienfeld (Gischt an der Front). */
    public EreignisGl ereignisGl() { return ereignisGl; }
    /** Phase 13c: „Was wäre wenn“ in Echtzeit (wave.Bay auf der Grafikkarte, LOW). */
    public final Echtzeit echtzeit = new Echtzeit();
    /** Phase 18: der Klang (com.dan.soundscape), Ohr = Kamera; Ton an im Bedienfeld KLANG. */
    public final Klang klang = new Klang(this);
    /** Phase 14a: der Wald (forest mit succession) über den Kern, Zustände nach dem Zeitstand, Treibgut am Morgen. */
    public final WaldGl wald = new WaldGl();
    /** Werkbuch Welle W6: die Landschaft (Farbe aus Sentinel-2, Fels aus cliff, Schnee, Gletscher mit Spalten). */
    public final LandschaftGl landschaft = new LandschaftGl();
    /** W5 (E11): Schleier am Sporn. */
    public final SchleierGl schleier = new SchleierGl(ereignisGl.surgeGl, ereignisGl::paket);   // com.dan.surge (seit 07.11.2026)
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

    /** Werkbuch Welle W0 (K1): die App startet in „Hoch“ mit voller Auflösung (vorher „Mittel“ mit 85 % und Regelung bis 50 %). */
    private volatile Quality quality = Quality.HOCH;
    public volatile double renderScale = quality.renderScale;
    /** Auflösung nach der Zeit der Grafikkarte regeln (nie unter {@link #MIN_SCALE}, nie über der Stufe). */
    public volatile boolean autoScale = true;
    /** W0 (K1): geregelt wird auf 30 Bilder/s (vorher 60) und nie unter 85 % (vorher 50 %); darunter wird das Bild Brei. */
    public volatile double targetFps = 30;
    public static final double MIN_SCALE = 0.85;
    private long scaleNanos;
    private int scaleSteps;
    /** HUD an, kurz oder voll (H). */
    public volatile boolean showHud = false, hudFull;   // 03.11.: beim Start aus
    /** Die Marken zur Ausrichtung (UTM-Raster, Kern, Szene, Fern, Orte); in Phase 1 an. */
    public volatile boolean showMarks = false;   // 03.11.: beim Start aus

    /** Phase 18 (Befund 17a): im Kino und während eines Regiebuchs keine Marken (UTM-Raster, Ellipsen) im Bild; der Schalter bleibt. */
    public boolean marksJetzt() { return showMarks && !kino.sichtbar() && !regie.running(); }
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
            overlay = new Shader("Überlagerung", null, "fullscreen.vert", "overlay.frag"),
            taaShader = new Shader("Kantenglättung", null, "fullscreen.vert", "taa.frag");
    private final Shader[] all = {skyShader, gelaendeShader, meerShader, post, overlay, taaShader, ereignisGl.welle, ereignisGl.strom, ereignisGl.medien, ereignisGl.medienHoch, baeche.shader};
    private final HudOverlay hud = new HudOverlay();
    /** Phase 19a: Befunde als Marken, Zeugen als Untertitel (Archiv). */
    public final ArchivSzene archiv = new ArchivSzene();
    private final HudOverlay archivTafel = new HudOverlay();
    private volatile float[] letzteVP;
    private int emptyVao, hdrTex, depthTex, hdrFbo, hdrColorFbo, hdrW, hdrH, surfW = 1, surfH = 1, defaultFbo;
    /**
     * Werkbuch Welle W0 (K2): Kantenglättung. {@code taa} = zeitlich (jedes Bild um einen Bruchteil eines Bildpunkts
     * versetzt, mit dem Verlauf gemischt, taa.frag); aus, dann FXAA in post.frag, wenn {@code fxaa}.
     */
    public volatile boolean taa = true, fxaa = true;
    /** Anteil des neuen Bilds je Bild (0,1: rund 20 Bilder bis zum vollen Ergebnis). */
    public volatile double taaAlpha = 0.1;
    private final int[] taaTex = new int[2], taaFbo = new int[2];
    private int taaIdx, taaFrame;
    private boolean taaValid;
    private double[] taaPrevVP;
    private final double[] taaPrevEye = new double[3];
    /** Verlauf verwerfen (Sprung der Kamera, Schnitt im Regiebuch). */
    public void taaReset() { taaValid = false; }
    private boolean reversedZ, ready;
    private final int[][] stamp = new int[2][MARKS];
    private final double[] passMs = new double[MARKS - 1];
    /** Runde 8: welcher Durchgang (Index in PASSES) im Zeitfenster zwischen Marke i und i + 1 lief. */
    private final int[] ordnung = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    /** Runde 8: die deckenden Durchgänge von vorn nach hinten (Boote, Welle, Wald, Gelände). */
    public volatile boolean vornZuerst = true;
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
    /**
     * Werkbuch Bild, B0: Brennweite in mm (Kleinbild, Bildhöhe 24 mm); 0 = der alte feste Bildwinkel {@link Camera#FOVY}.
     * Bis B1 (`lens`) nur für die Bildbank und von Hand; die App startet unverändert.
     */
    public volatile double brennweite;

    /**
     * Werkbuch Welle W9a: das Objektiv aus {@code com.dan.lens}: Tiefenschärfe mit Bokeh in der Form der Blende (Katzenauge
     * zum Rand, helle Ränder und weicher Kern aus der sphärischen Aberration, Farbsäume), Schärfezieher mit Trägheit,
     * Geisterbilder und Stern der Sonne, Verzeichnung, Farbquerfehler, Randabfall, Schleierlicht, Fokusatmung; gerechnet aus
     * einem Doppel-Gauß mit 10 Glasflächen. An mit {@link #objektivAn} (Block KAMERA, Regie „objektiv an“, Bildbank
     * objektiv=an, -Dlituya.objektiv=true), im Kino immer (WF8 A, WF37 A); die abgenommenen Heldenbilder bleiben ohne.
     */
    public final com.dan.lens.Lens objektiv = new com.dan.lens.Lens();
    public volatile boolean objektivAn = Boolean.getBoolean("lituya.objektiv");
    private com.dan.lens.gl.LensGl objektivGl;
    /** W9a: Schärfe auf einen Ort (Welt x, y, z; Regie „fokus Ort“) statt Autofokus oder Meter; null = aus. */
    public volatile double[] fokusZiel;
    private double objektivAtmung = 1;
    private double[] objektivAuge;
    private boolean regenLinse;
    /** W9: während das Bedienfeld gleitet, bleiben die Ziele in ihrer Größe (gestreckt ins Fenster, kein Neuanlegen je Bild). */
    public volatile boolean festeGroesse;

    /**
     * Werkbuch Welle W9b: der Film aus {@code com.dan.film} statt der Filmkurve der App (ACES): Negativ und Kopie mit je drei
     * Schichten und ihren Kurven, das Kopierlicht in Punkten, Korn nach Selwyn, Halation aus dem Träger, Bildstand und
     * Flackern, 24 Filmbilder je Sekunde. Nach dem Zeitstand (WF39 A): 1958 Eastman Color Negative 5248 auf Eastman Color
     * Print 5382 in CinemaScope (anamorphotisch, das Bokeh des Objektivs wird oval), heute Kodak Vision3 250D am Tag und 500T
     * am Abend und in der Nacht auf Vision Print 2383 in Super 35; das Kopierlicht nach dem Zeitstand, heute nach dem
     * Sonnenstand. An mit {@link #filmAn} (Block KAMERA, Regie „film an“, Bildbank film=an, -Dlituya.film=true), im Kino
     * immer; die abgenommenen Heldenbilder bleiben ohne (wie das Objektiv, bis W9h).
     */
    public final com.dan.film.Film film = new com.dan.film.Film();
    public volatile boolean filmAn = Boolean.getBoolean("lituya.film");
    /** Material und Format nach dem Zeitstand (true) oder wie eingestellt (Regie „film 5248“, Block KAMERA). */
    public volatile boolean filmNachZeitstand = true;
    /** Kopierlicht von Hand (Regie „kopierlicht“, Block KAMERA); null: nach dem Zeitstand. */
    public volatile com.dan.film.Grade kopierlicht;
    private com.dan.film.gl.FilmGl filmGl;
    /** Uhr des Films (s): läuft mit der Szene (auch im Film ohne Fenster, Bild für Bild), im Standbild mit der Wanduhr. */
    private double filmUhr;
    private boolean filmGeschritten, filmTag = true;
    /** Das Korn des Kinos (Phase 17a) bleibt, wo der Film aus ist; mit dem Film kommt das Korn aus dem Film. */
    private final double kinoKornVorher = kino.filmStaerke;
    /** Die Materialien (je eines: die Kopie merkt sich ihre Kalibrierung am Material). */
    public static final com.dan.film.Stock V3_250D = com.dan.film.Stock.vision3_250D(), V3_500T = com.dan.film.Stock.vision3_500T(),
            EC5248 = com.dan.film.Stock.eastmanColor5248(), P2383 = com.dan.film.Stock.print2383(), P5382 = com.dan.film.Stock.print5382();
    {
        film.exposureStops = 0.3;   // die Graukarte so hell wie mit der Filmkurve der App (sRGB rund 0,55 statt 0,48) [G]
        film.negative = V3_250D;
        film.print = P2383;
    }

    /**
     * Werkbuch Welle W9c: die Belichtung (com.dan.shutter). Die Umlaufblende der Kamera, 180° bei 24 B/s (1/48 s, WF38 A),
     * mit Öffnungsgang; mit dem Film belichtet sie den Regen und gibt die Bewegungsunschärfe in Echtzeit (die Bewegungen der
     * Kamera, McGuire 2012). Eine Bildrate der Kamera über 24 ist Zeitlupe: Die Szene läuft 24/Bildrate so schnell (Regie,
     * Kino und Vorspann in der Zeit der Vorführung), die Belichtung wird kürzer (Regie „bildrate 192“). Die Kino-Stufe:
     * {@link #teilbilder} Teilbilder je Filmbild über die Zeit der offenen Blende, die Pupille und den Bildpunkt – echte
     * Tiefenschärfe für alles (auch Gischt, Nebel, Staub, Regen, Spiegelbilder), echte Bewegungsunschärfe für alles, was sich
     * bewegt. Nur wenn der Aufrufer die Uhr führt ({@link #frozen}: ant film, Tempo, Standbilder).
     */
    public final com.dan.shutter.Shutter verschluss = new com.dan.shutter.Shutter();
    public final com.dan.shutter.MotionBlur bewegung = new com.dan.shutter.MotionBlur();
    /** Bilder je Sekunde der Vorführung. */
    public static final double VORFUEHRUNG = 24;
    /** Bilder je Sekunde der Kamera (24; mehr: Zeitlupe). */
    public volatile double kameraBildrate = 24;
    /** Teilbilder je Filmbild in der Kino-Stufe (0: aus). */
    public volatile int teilbilder = Integer.getInteger("lituya.teilbilder", 0);
    /** Zeit der Vorführung je Filmbild in der Kino-Stufe (s); der Film ohne Fenster setzt 1/Bildrate. */
    public volatile double belichtungsTakt = 1 / 24.0;
    /** Bewegungsunschärfe in Echtzeit (mit dem Film); aus: wie bis W9b. */
    public volatile boolean bewegungAn = !Boolean.getBoolean("lituya.ohneBewegung");
    /** Tempo: ein Schwenk der Kamera während der Aufnahme (Grad je Sekunde Szene). */
    public volatile double schwenkGradJeS;
    /** Belichtungszeit des Regens für das Auge (ohne Film), s. */
    public volatile double regenVerschlussAuge = 1 / 250.0;
    /** Die Szene läuft Bild für Bild, obwohl die Uhr steht (ant film): das Meer und die Ringe des Bebens laufen mit. */
    public volatile boolean szeneLaeuft;
    private com.dan.shutter.gl.ShutterGl verschlussGl;
    private com.dan.shutter.gl.MotionBlurGl bewegungGl;
    private com.dan.shutter.SubFrames.Sample teilbild;
    private double teilFokusM = 1e5, teilPupilleM;
    /** W9c Runde 2 (W7e): Tropfen auf der Frontlinse (com.dan.wetglass auf der Linse, com.dan.lens.FrontDrops ins Bild). */
    public final LinseNass linseNass = new LinseNass();
    /** W9c Runde 2: Tropfen auf der Linse im Bild (Schalter). */
    public volatile boolean tropfenAn = true;
    private static double[] tropfenPhase;
    /** W9c Runde 2: die Fehler des Objektivs für die Teilbilder (sphärische Aberration, Längsfarbfehler) und die Blende. */
    private com.dan.shutter.Aberration teilAberration;
    private com.dan.shutter.Pupil teilPupille;
    private int teilZahl;
    /** W9c Runde 2: Lichter der Kino-Stufe als ganze Kreise (Schalter, -Dlituya.kinoLichter=false: als Punkte wie Runde 1). */
    public volatile boolean kinoLichter = !"false".equals(System.getProperty("lituya.kinoLichter"));
    /** W9c Runde 2: Licht (Sonnenkarten, Horizont) in der Kino-Stufe nur im ersten Teilbild (die Sonne wandert in 1/48 s 0,004°). */
    private boolean teilLichtFertig;
    /** W9c Runde 2: Zeit des letzten Filmbilds der Kino-Stufe (ms) und die Lichter im letzten Teilbild. */
    public volatile double kinoMs;
    public volatile int kinoLichterZahl = -1;
    private long kinoBildNr;
    private double szeneDt, bildSzeneDt;
    private double[] letzteVpN, letzteSonne;
    private int teilLetztes;
    private boolean bewegungLetzt;

    /** Wie schnell die Szene gegen die Vorführung läuft (Zeitlupe bei mehr als 24 Bildern je Sekunde der Kamera). */
    public double zeitmass() { return VORFUEHRUNG / Math.max(1, kameraBildrate); }

    /** W9c: Bildrate der Kamera (Zeitlupe über 24); die Umlaufblende behält ihren Winkel, die Belichtung wird kürzer. */
    public void bildrateStellen(double fps) {
        kameraBildrate = Math.max(1, Math.min(10_000, fps));
        verschluss.fps = kameraBildrate;
    }

    /** W9c: Belichtungszeit (s): der Winkel der Umlaufblende bei dieser Bildrate, dazu die Belichtung des Regens ohne Film. */
    public void verschlussStellen(double s) {
        verschluss.angleDeg = com.dan.shutter.Shutter.angleFor(s, verschluss.fps);
        regenVerschlussAuge = s;
    }

    /** Ob die Bewegungsunschärfe in Echtzeit rechnet (mit dem Film, Kantenglättung, nicht in den Teilbildern). */
    public boolean bewegungJetzt() { return bewegungGl != null && bewegungGl.ready() && bewegungAn && bewegung.enabled && filmJetzt() && teilbild == null; }

    /** „24 B/s · 180° (1/48 s) · Öffnungsgang 4,8 ms · Bewegungsunschärfe an“ */
    public String verschlussLine() {
        String z = kameraBildrate != VORFUEHRUNG ? String.format(Locale.GERMAN, " · Zeitlupe 1/%.0f", kameraBildrate / VORFUEHRUNG) : "";
        return verschluss.describe() + z + (filmJetzt() ? (bewegungJetzt() ? " · Bewegungsunschärfe an" : " · Bewegungsunschärfe aus") : " · ohne Film: das Auge (Regen " + belichtungsText(regenVerschlussAuge) + ")")
                + (teilbilder > 0 ? " · Kino-Stufe " + teilbilder + " Teilbilder" : "");
    }

    static String belichtungsText(double s) { return s > 0 && 1 / s >= 10 ? String.format(Locale.GERMAN, "1/%.0f s", 1 / s) : String.format(Locale.GERMAN, "%.3f s", s); }

    /** Ob der Film in diesem Bild rechnet (an oder Kino, mit Kantenglättung). */
    public boolean filmJetzt() { return filmGl != null && filmGl.ready() && (filmAn || kino.laeuft()) && taa; }

    public com.dan.film.gl.FilmGl filmGl() { return filmGl; }

    /** W9b: „an“, „aus“, „zeitstand“ (Material nach dem Zeitstand) oder ein Material: „5248“, „vision3_250d“, „vision3_500t“. */
    public void filmStellen(String a) {
        String t = a.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case "aus" -> { filmAn = false; return; }
            case "an", "" -> { filmAn = true; return; }
            case "zeitstand", "auto" -> filmNachZeitstand = true;
            case "5248", "1958" -> filmMaterial(EC5248);
            case "vision3_250d", "250d" -> filmMaterial(V3_250D);
            case "vision3_500t", "500t" -> filmMaterial(V3_500T);
            default -> throw new IllegalArgumentException("film: an, aus, zeitstand, 5248, vision3_250d oder vision3_500t, nicht „" + a + "“");
        }
        filmAn = true;
    }

    /** Ein Material von Hand, mit seiner Kopie und seinem Format (5248: 5382 und CinemaScope, Vision3: 2383 und Super 35). */
    public void filmMaterial(com.dan.film.Stock n) {
        filmNachZeitstand = false;
        boolean alt = n == EC5248;
        film.print = alt ? P5382 : P2383;
        film.format = alt ? com.dan.film.Format.CINEMASCOPE : com.dan.film.Format.SUPER35_239;
        film.negative = n;
    }

    /** W9b: das Kopierlicht „zeitstand“, ein Name (neutral, abend1958, welle, morgen, heute, nacht) oder „Rot Grün Blau [Dichte]“. */
    public void kopierlichtStellen(String a) {
        String t = a.trim().toLowerCase(Locale.ROOT);
        kopierlicht = switch (t) {
            case "zeitstand", "auto" -> null;
            case "neutral" -> com.dan.film.Grade.NEUTRAL;
            case "abend1958" -> com.dan.film.Grade.ABEND_1958;
            case "welle" -> com.dan.film.Grade.WELLE;
            case "morgen" -> com.dan.film.Grade.MORGEN_DANACH;
            case "heute" -> com.dan.film.Grade.HEUTE_ABEND;
            case "nacht" -> com.dan.film.Grade.NACHT;
            default -> com.dan.film.Grade.parse(t);
        };
    }

    /**
     * Material, Format und Kopierlicht für dieses Bild (WF39 A). 1958: 5248 auf 5382, CinemaScope; Abend vor Anker warm, die
     * Welle kühl und dicht, der Morgen danach fahl. Heute: über 8° Sonnenhöhe Vision3 250D, darunter 500T (mit einem halben
     * Grad Abstand, damit es an der Grenze nicht springt), auf 2383, Super 35; das Kopierlicht gleitet mit der Sonne von
     * neutral (über 10°) über den Abend (2°) in die Nacht (unter −6°) [G].
     */
    public void filmEinrichten() {
        SceneClock.Zeitstand z = clock.zeitstand;
        double el = clock.sun()[0];
        boolean heute = z == SceneClock.Zeitstand.HEUTE;
        if (filmNachZeitstand) {
            if (heute) {
                if (filmTag && el < 7.5) filmTag = false;
                else if (!filmTag && el > 8.5) filmTag = true;
                film.negative = filmTag ? V3_250D : V3_500T;
                film.print = P2383;
                film.format = com.dan.film.Format.SUPER35_239;
            } else {
                film.negative = EC5248;
                film.print = P5382;
                film.format = com.dan.film.Format.CINEMASCOPE;
            }
        }
        com.dan.film.Grade g = kopierlicht;
        if (g == null) {
            if (!heute) g = switch (z) {
                case VOR -> com.dan.film.Grade.ABEND_1958;
                case WELLE -> com.dan.film.Grade.WELLE;
                default -> com.dan.film.Grade.MORGEN_DANACH;
            };
            else if (el >= 10) g = com.dan.film.Grade.NEUTRAL;
            else if (el >= 2) g = com.dan.film.Grade.mix(com.dan.film.Grade.HEUTE_ABEND, com.dan.film.Grade.NEUTRAL, (el - 2) / 8);
            else g = com.dan.film.Grade.mix(com.dan.film.Grade.NACHT, com.dan.film.Grade.HEUTE_ABEND, (el + 6) / 8);
        }
        film.grade = g;
    }

    /** „Eastman Color Negative 5248 → Eastman Color Print 5382 · CinemaScope · Abend 1958 · 24 B/s · Korn …“ oder „Film aus“. */
    public String filmLine() {
        if (!filmJetzt()) return filmGl == null ? "Film nicht verfügbar" : "Film aus (Filmkurve der App)";
        com.dan.film.Film f = film;
        double s = f.grainSigma(f.negative, 1, 1, f.grainArea(surfW)) * f.grainGain;
        int[] hb = filmGl.halationSize();
        return f.describe() + String.format(Locale.GERMAN, " · Korn σ_D %.3f bei %d Bildpunkten · Halation %s%s%s", s, surfW,
                f.halation ? String.format(Locale.GERMAN, "%.0f px (Puffer %d × %d)", f.halationRadiusPx(surfW), hb[0], hb[1]) : "aus",
                f.printAgeYears > 0 ? String.format(Locale.GERMAN, " · Kopie %.0f Jahre alt", f.printAgeYears) : "",
                filmNachZeitstand ? " · nach dem Zeitstand" : "");
    }

    /** Ob das Objektiv in diesem Bild rechnet (an oder Kino, und mit Kantenglättung: es sitzt hinter ihr). */
    public boolean objektivJetzt() { return objektivGl != null && objektivGl.ready() && (objektivAn || kino.laeuft()) && taa; }

    public com.dan.lens.gl.LensGl objektivGl() { return objektivGl; }

    /** Brennweite (mm, Bildhöhe 24 mm) des Bildwinkels jetzt; ohne Vorgabe die feste der App (rund 23 mm). */
    public double brennweiteJetzt() { return brennweite > 0 ? brennweite : 12.0 / Math.tan(Math.toRadians(Camera.FOVY) / 2); }

    public double fovy() {
        double f = brennweite > 0 ? Math.toDegrees(2 * Math.atan(12.0 / brennweite)) : Camera.FOVY;
        // W9a: Fokusatmung (das Objektiv fährt beim Fokussieren auf nah vom Sensor weg, der Bildwinkel wird enger)
        return objektivAtmung != 1 ? Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(f) / 2) / objektivAtmung)) : f;
    }

    /**
     * Werkbuch Welle W5: Mitte der Clipmap des Geländes. Die Stufen der Clipmap liegen ringförmig um ihre Mitte; mit dem
     * Auge als Mitte bekam ein Teleblick (H6 „524 m“: 135 mm, Hang in 2 km) die Stufe zu 16–32 m, und der Hang stand als
     * Vieleck mit großen Facetten im Bild, obwohl ein Bildpunkt dort rund 0,3 m deckt. Mit langer Brennweite wandert die
     * Mitte deshalb vom Auge zu dem Punkt, auf den die Bildmitte zielt (höchstens 6 km weit): von 50 mm (Bildwinkel 27°)
     * nichts bis voll ab rund 85 mm (16°). Die Erdkrümmung rechnet die Clipmap weiter vom Auge (uClipCam).
     */
    public double[] lodMitte() {
        // com.dan.clipmap (seit 07.11.2026): ab 27° Bildwinkel zum Zielpunkt der Bildmitte, voll ab 16°, höchstens 6 km
        if ((27.0 - fovy()) / 11.0 <= 0) return new double[]{camera.eye[0], camera.eye[2]};
        return com.dan.clipmap.Clipmap.focusCenter(camera.eye[0], camera.eye[2], treffer(0, 0, 1, 1), fovy());
    }

    /** W5: die Clipmap um {@link #lodMitte()} nachführen. */
    public void landAuge() { double[] m = lodMitte(); land.auge(m[0], m[1]); }

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
        wetterGl.stufe(q);   // W7
    }

    /**
     * W7: ist das Wetter für ein Standbild bereit? Wolken mit Gelände (sobald das grobe Gitter des Lichts steht), die Karte
     * des Seenebels (wenn er liegt) und der Himmel nach der Sicht der Lage (com.dan.sky baut im Hintergrund).
     */
    public boolean wetterBereit() {
        com.dan.lituya.wiring.Wetter.Zustand z = wetter.zustand();
        if (!wetter.an || z == null || !wetterGl.bereit()) return true;
        if (licht.sky.rebuilding()) return false;
        if (z.wolken() && (z.bank() > 0.01 || z.kappe() > 0.01 || z.konvektion() > 0.01) && wetterGl.wolken.terrain == null) return false;
        return !z.seenebel() || wetterGl.nebelBereit();
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
        if (ok) { hudMeldung("Ansicht " + a); taaReset(); }
        return ok;
    }

    /** Befund 17a: im Regiebuch steht das Auge über Land mindestens so hoch über dem Gelände (Sitka-Fichten bis rund 50 m). */
    public static final double KRONEN = 50;

    /** Hebt ein frei gestelltes Auge über Land auf {@link #KRONEN} m über dem Gelände und neigt den Blick um so viel nach unten, wie es auf 1,5 km ausmacht. */
    public boolean ueberKronen() {
        if (camera.mode != com.dan.lituya.camera.Camera.Mode.FREI || camera.mitfahrt != null) return false;
        double g = groundHeight(camera.fx, camera.fz);
        if (g <= KEIN_GRUND + 1) g = stapelHoehe(camera.fx, camera.fz);   // ohne clipmap (Prüfung): der Kachelstapel
        if (Double.isNaN(g) || g < wiring.pegel + 0.5 || camera.fy - g >= KRONEN) return false;
        double dh = g + KRONEN - camera.fy;
        camera.place(camera.fx, g + KRONEN, camera.fz, camera.fyaw, camera.fpitch + Math.toDegrees(Math.atan2(dh, 1500)));
        return true;
    }

    /** Stellt Kamera und Uhr auf ein Schaustück (Phase 13b) und schaltet das Abspielen an. */
    public void placeStueck(com.dan.lituya.camera.Schaustuecke.Stueck st) {
        camera.waterLevel = wiring.pegel;
        com.dan.lituya.camera.Schaustuecke.apply(st, camera, clock, this::groundHeight);
        jahresRaffer = st.jahre();
        rafferTage = 0;
        wiedergabe.an(true);
        netzblick.inSzene = false;   // W0 (WF11 A): keine Netzlinien im Schaustück, nur über das Menü (vorher st.netze())
        taaReset();
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
    /** W6 Runde 6 (WF19 A): Augenanpassung, Schalter (Bildbank: augenanpassung=false). */
    public static volatile boolean augenanpassung = !Boolean.getBoolean("lituya.ohneAugenanpassung");
    /**
     * W6 Runde 7, Frage WF20 A: unter Wasser keine Augenanpassung (die Belichtung bleibt fest, H8 wie abgenommen). Schalter
     * lituya.augeUnterWasser=true: wie Runde 6 (das Auge passt sich auch unter Wasser an). Bildbank: augeUnterWasser=true.
     */
    public static volatile boolean augeUnterWasser = Boolean.getBoolean("lituya.augeUnterWasser");
    public final com.dan.exposure.EyeAdaptation auge = new com.dan.exposure.EyeAdaptation();
    /**
     * W6 Runde 6: Teilzeiten der Grafikkarte innerhalb der Durchgänge (com.dan.gputime), für die Zerlegung von H6. Schalter
     * lituya.teilzeiten (aus: kein Stempel). Statisch, damit Wald und Ereignis ihre Abschnitte selbst setzen.
     */
    public static final com.dan.gputime.gl.GlClock TEIL_UHR = new com.dan.gputime.gl.GlClock();
    public static final com.dan.gputime.GpuSpans TEILE = new com.dan.gputime.GpuSpans(TEIL_UHR);
    static { TEILE.enabled = Boolean.getBoolean("lituya.teilzeiten"); }
    static void teil(String n) { TEILE.begin(n); }
    static void teilEnde(String n) { TEILE.end(n); }
    private com.dan.exposure.gl.LuminanceGl lum;
    {
        // Runde 6b [G]: Bezug 0,08 = Mittel der offenen Heldenbilder am Boden (0,066–0,226, Median 0,079); ins Dunkle bis
        // 2 Blenden (der geschlossene Wald)
        auge.key = 0.08;
        // W6 Runde 7, Frage WF20 A: ins Helle nicht dunkler (vorher eine drittel Blende): nur das Dunkle am Boden wird heller,
        // die abgenommenen hellen Bilder (H1, H2, H11, H12) bleiben, wie sie waren
        auge.maxDown = 0;
    }
    private double[] augeZuletzt;
    /** Höhe über Grund (m), ab der die Anpassung abnimmt und ganz fort ist [G]. */
    static final double AUGE_VOLL = 30, AUGE_FORT = 120;

    private void augeAnpassen(GL4 gl, int bild, int w, int h) {
        if (lum == null || !augenanpassung) { auge.weight = 0; return; }
        double[] e = camera.eye;
        // Runde 6b: im Standbild (auch Bildbank und Proben) gilt jede Messung sofort; die Zeit steht, ein Auge wäre längst angepasst
        if (augeZuletzt == null || Math.abs(e[0] - augeZuletzt[0]) + Math.abs(e[1] - augeZuletzt[1]) + Math.abs(e[2] - augeZuletzt[2]) > 30
                || frozen && !kino.laeuft()) auge.snap();
        augeZuletzt = e.clone();
        double grund = Math.max(groundHeight(e[0], e[2]), wiring.pegel);
        auge.weight = 1 - WaldGl.smooth(AUGE_VOLL, AUGE_FORT, e[1] - grund);
        if (!augeUnterWasser && e[1] < wiring.pegel) auge.weight = 0;
        // Runde 6b: im Flug (Wirkung 0) nicht messen; beim Abstieg gilt die erste Messung sofort
        if (auge.weight <= 0) { auge.snap(); return; }
        auge.update(lum.measure(gl, bild, w, h), letztesDt);
    }

    public double exposure() {
        if (licht.aktiv()) return 1;   // W1: die Szene ist mit licht.pre() vorbelichtet
        double b = Sun.skyBrightness(clock.sun()[0]);
        return 0.6 * Math.pow(b, -0.85) * Math.pow(2, exposureBias);
    }

    /** W9a: „an“, „aus“ oder „Brennweite [Blende]“ (mm, f/N), etwa „objektiv 85 2,8“. */
    public void objektivStellen(String a) {
        String t = a.trim().toLowerCase(Locale.ROOT);
        if (t.equals("aus")) { objektivAn = false; return; }
        objektivAn = true;
        if (t.equals("an") || t.isEmpty()) return;
        String[] p = t.replace("mm", " ").replace("f/", " ").trim().split("\\s+");
        brennweite = Math.max(10, Math.min(1200, Double.parseDouble(p[0].replace(',', '.'))));
        if (p.length > 1) objektiv.fNumber = Double.parseDouble(p[1].replace(',', '.'));
    }

    /** W9a: „auto“ (Bildmitte), Meter („12,5“) oder ein Ort (Ansicht, Punkt von LB_NET_SICHT, Ort): die Schärfe folgt ihm. */
    public void fokusStellen(String a) {
        String t = a.trim();
        objektivAn = true;
        if (t.equalsIgnoreCase("auto")) { objektiv.autoFocus = true; fokusZiel = null; return; }
        try {
            double m = Double.parseDouble(t.replace(',', '.').replaceFirst("\\s*m$", ""));
            objektiv.autoFocus = false;
            objektiv.focusM = Math.max(0.3, m);
            fokusZiel = null;
            return;
        } catch (NumberFormatException e) { /* ein Ort */ }
        double[] p = punkt(t);
        if (p == null) throw new IllegalArgumentException("fokus: kein Ort „" + t + "“");
        fokusZiel = new double[]{p[0], p[1], p[2]};
        objektiv.autoFocus = false;
    }

    /** Kinomodus verlassen (Esc im Kino; das Regiebuch läuft im Fenster weiter). */
    public void kinoVerlassen() { frozen = false; kino.abbrechen(); }

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
            // seit 07.11.2026: Lippe, Tropfen, Schleier (com.dan.surge) und fallende Bäume (com.dan.forest) mit dem Licht dieser App
            ereignisGl.paketeBauen(gl);
            wald.paketeBauen(gl);
        } catch (RuntimeException e) {
            fail(e.getMessage());
            return;
        }
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        emptyVao = v[0];
        for (int[] s : stamp) gl.glGenQueries(MARKS, s, 0);
        compute = ComputeProbe.run(gl);
        licht.init(gl);   // W1
        meer.init(gl);    // W2
        wetterGl.init(gl);   // W7
        wetterGl.stufe(quality);
        landschaft.init(gl);   // W6
        landschaft.laden(new java.io.File(System.getProperty("lituya.daten", "daten")));
        try {             // W3b: Texturen der Hänge (beim ersten Mal packt der Treiber, danach aus gpu_*_1024.bin)
            java.util.List<com.dan.surface.Material> kat = com.dan.surface.Katalog.fjord();
            com.dan.surface.MaterialSet set = new com.dan.surface.MaterialSet(new java.io.File(System.getProperty("lituya.daten", "daten"), "texturen"), kat);
            surface.load(gl, set, 1024, true);
            String[] namen = {"schiefer_frisch", "schiefer_verwittert", "moraene", "strandkies"};
            for (int i = 0; i < namen.length; i++) surfMat[i] = Math.max(0, set.layer(namen[i]));
            String[] namen2 = {"waldboden", "moos", "firn", "eis"};
            for (int i = 0; i < namen2.length; i++) surfMat2[i] = Math.max(0, set.layer(namen2[i]));
            surfStatus = "Texturen: " + surface.info() + (set.missing().isEmpty() ? "" : ", fehlen " + set.missing());
        } catch (java.io.IOException | RuntimeException e) {
            surfStatus = "Texturen: ohne (" + e.getMessage() + ")";
            System.err.println("LituyaBay1958: " + surfStatus);
        }
        System.out.println("LituyaBay1958: " + surfStatus);
        try {
            netzblick.init(gl);
            wiedergabe.start();
            netzblick.laden(new java.io.File(System.getProperty("lituya.daten", "daten")), this::stapelHoehe);
            baeche.laden(new java.io.File(System.getProperty("lituya.daten", "daten")), this::stapelHoehe);
            faelle.laden(new java.io.File(System.getProperty("lituya.daten", "daten")), this::stapelHoehe);   // W6
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Netzblick: " + e.getMessage());
        }
        try {   // W6 Runde 6 (WF19 A): Augenanpassung aus com.dan.exposure
            lum = new com.dan.exposure.gl.LuminanceGl(auge);
            lum.init(gl);
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Augenanpassung nicht verfügbar: " + e.getMessage());
            lum = null;
        }
        try {   // Phase 17a: der Kinomodus
            com.dan.kino.KinoMaler km = new com.dan.kino.KinoMaler();
            km.kopfFarbe = new java.awt.Color(200, 214, 222);
            kinoGl = new com.dan.kino.gl.KinoGl(kino, km);
            kinoGl.init(gl);
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Kino nicht verfügbar (ohne Balken und Blenden): " + e.getMessage());
            kinoGl = null;
        }
        try {   // W9c: die Belichtung (com.dan.shutter): Kino-Stufe und Bewegungsunschärfe
            verschlussGl = new com.dan.shutter.gl.ShutterGl(verschluss);
            verschlussGl.init(gl);
            bewegungGl = new com.dan.shutter.gl.MotionBlurGl(bewegung);
            bewegungGl.init(gl);
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Belichtung nicht verfügbar (ohne Kino-Stufe und Bewegungsunschärfe): " + e.getMessage());
            verschlussGl = null;
            bewegungGl = null;
        }
        try {   // W9b: der Film (com.dan.film)
            filmGl = new com.dan.film.gl.FilmGl(film);
            filmGl.init(gl);
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Film nicht verfügbar (Filmkurve der App): " + e.getMessage());
            filmGl = null;
        }
        try {   // W9a: das Objektiv (com.dan.lens)
            objektivGl = new com.dan.lens.gl.LensGl(objektiv);
            objektivGl.init(gl);
            objektivGl.timing = TEILE.enabled;   // W9c: die Teile des Objektivs in den Teilzeiten
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Objektiv nicht verfügbar: " + e.getMessage());
            objektivGl = null;
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
    public void step(double dt) { schritt(dt, dt * zeitmass(), true); }

    /**
     * W9c: ein Schritt um dt der Vorführung (Regie, Kino, Filmbild; nur mit vorfuehrung) und ds der Szene (Uhr, Wetter, Klang,
     * Physik, Kamera). In Zeitlupe ist ds = dt · 24 / Bildrate der Kamera; die Kino-Stufe schreitet zwischen den Teilbildern nur
     * in der Szene.
     */
    private void schritt(double dt, double ds, boolean vorfuehrung) {
        for (Runnable r; (r = imBild.poll()) != null; ) {
            try { r.run(); } catch (RuntimeException e) { System.err.println("LituyaBay1958: Arbeit im Bildlauf: " + e); }
        }
        clock.step(ds);
        if (jahresRaffer) {
            // ganze Jahre am selben Tag und zur selben Uhrzeit (die Jahreszeit bleibt; mit Tagen landete der Sprung im Winter)
            rafferTage += ds * jahreJeMinute / 60;
            java.time.LocalDate ende = SceneClock.Zeitstand.HEUTE.date;
            while (rafferTage >= 1 && jahresRaffer) {
                rafferTage -= 1;
                java.time.LocalDate n = clock.date().plusYears(1);
                if (!n.isBefore(ende)) { clock.set(ende, clock.hour()); clock.zeitstand = SceneClock.Zeitstand.HEUTE; jahresRaffer = false; hudMeldung("Jahres-Zeitraffer: heute erreicht"); }
                else clock.set(n, clock.hour());
            }
        }
        if (vorfuehrung) {
            regie.step(dt, regieCtx);   // Phase 17a: fällige Stichworte des Regiebuchs
            filmUhr += dt;   // W9b: das Filmbild wechselt mit der Vorführung
            filmGeschritten = true;
        }
        // W7: die Wetterregie bis zur Uhr; Wind und Bäche aus ihrem Zustand (1958 klar und still wie bisher)
        try {
            wetter.schritt(clock, ds, clock.lapse == 0);
            com.dan.lituya.wiring.Wetter.Zustand wz = wetter.zustand();
            boolean wa = wetter.an && wz != null;
            if (wa) { wiring.windSpeed = wz.wind(); wiring.windFromDeg = wz.windVon(); }
            baeche.regen = Math.min(BaecheGl.REGEN_MAX, Math.max(baecheRegenHand, wa ? wz.abfluss() : 0));
            double sz = clock.epochMillis() / 1000.0;
            wetterDt = Double.isNaN(wetterSzeneT) ? 0 : Math.max(0, Math.min(600, sz - wetterSzeneT));
            wetterSzeneT = sz;
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Wetter: " + e);
            wetter.an = false;
        }
        // W9c Runde 2 (W7e): die Frontlinse im Regen (so viel, wie der Wind gegen das Objektiv treibt)
        try {
            com.dan.lituya.wiring.Wetter.Zustand wz2 = wetter.zustand();
            double reg = wetter.an && wz2 != null && wz2.an() ? wz2.regen() : 0;
            double wr = Math.toRadians(wiring.windFromDeg + 180);
            linseNass.schritt(ds, reg, wiring.windSpeed, Math.sin(wr), -Math.cos(wr), camera.fwd);
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Linse: " + e);
        }
        if (regieLief && !regie.running() && netzVorher != null) {   // danach den Netzblick wie vorher
            netzblick.bildImBild = (Boolean) netzVorher[0];
            netzblick.inSzene = (Boolean) netzVorher[1];
            netzblick.netz = (Integer) netzVorher[2];
            netzVorher = null;
            kinoNetzblick = false;
        }
        regieLief = regie.running();
        if (klang.on || klang.offline) {
            try { klang.feed(ds); } catch (RuntimeException e) { klang.note = "!" + e.getMessage(); }
        }
        if (vorfuehrung) kinoSchritt(dt);
        landAuge();   // W5: mit Tele um den Zielpunkt
        physik.advance(ds);
        camera.groundLevel = groundHeight(camera.eye[0], camera.eye[2]);
        camera.waterLevel = wiring.pegel;
        camera.targetGround = Math.max(groundHeight(camera.tx, camera.tz), wiring.pegel);
        camera.step(ds);
        if (schwenkGradJeS != 0 && ds > 0) {   // W9c, Tempo: ein Schwenk während der Aufnahme
            camera.place(camera.fx, camera.fy, camera.fz, camera.fyaw + schwenkGradJeS * ds, camera.fpitch);
            camera.snap();
        }
        szeneDt += ds;
        netzblick.regen = baeche.regen;
        com.dan.clipmap.Clipmap cmk = land.clipmap();
        if (cmk != null && cmk.curvatureRadius > 0) netzblick.kruemmungR = cmk.curvatureRadius;
        netzblick.schritt(clock.simSeconds(), wiring.pegel, camera.eye[0], camera.eye[2]);
    }

    /** Phase 17: Rundflug oder Kamerafahrt aus LB_NET_SICHT starten ({@link com.dan.lituya.camera.SichtFahrt}); Rückgabe die Meldung. */
    public String sichtFahrt(String welche, double tempo) {
        try {
            com.dan.lituya.camera.Fahrt f = com.dan.lituya.camera.SichtFahrt.bauen(new java.io.File(System.getProperty("lituya.daten", "daten")), welche, tempo, this::stapelHoehe);
            if (f == null) { hudMeldung("LB_NET_SICHT fehlt (ant aufbereitung -Dschritte=sicht)"); return "LB_NET_SICHT fehlt"; }
            camera.waterLevel = wiring.pegel;
            camera.startFahrt(f);
            hudMeldung("Fahrt " + f.describe());
            return f.describe();
        } catch (java.io.IOException e) {
            hudMeldung("Fahrt: " + e.getMessage());
            return e.getMessage();
        }
    }

    // ================================================================== Phase 17a: Regiebücher und Kino

    /** Phase 17a: die Regiebücher (Text mit Blatt am Ende), ihre Fotos und was sie sagen. */
    public final com.dan.lituya.scene.Regie regie = new com.dan.lituya.scene.Regie();
    private final RegieCtx regieCtx = new RegieCtx();
    private com.dan.kino.gl.KinoGl kinoGl;
    private final java.util.List<Runnable> kinoSchnitte = new java.util.ArrayList<>();
    private double kinoYaw;
    private boolean kinoFensterAn;
    private com.dan.lituya.scene.Regie.Book kinoBuch;
    /** true, wenn das Kino beginnt (Bedienfeld weg, Zeiger weg), false, wenn es zu Ende ist; im Bildlauf gerufen. */
    public volatile Consumer<Boolean> kinoFenster = an -> { };
    private volatile String captureCaption, sheetName, regieMsg = "";
    private final java.util.List<BufferedImage> album = new java.util.ArrayList<>();
    private final java.util.List<String> albumCaps = new java.util.ArrayList<>();
    private double albumFps, albumGpu;
    private int albumSamples;
    /** Das zuletzt geschriebene Blatt (für Tempo und die Prüfung); null: noch keins. */
    public volatile File lastSheet;
    /** Ohne Blatt (Prüfung). */
    public volatile boolean ohneBlatt;
    /** Der Netzblick läuft im Kino (Stichwort netzblick). */
    private volatile boolean kinoNetzblick;
    private com.dan.lituya.scene.SichtNetz sichtNetz;

    private Object[] netzVorher;
    private boolean regieLief;

    /** Ein Regiebuch von vorn im Fenster (das Album beginnt leer). */
    public void regieStart(com.dan.lituya.scene.Regie.Book b) {
        if (netzVorher == null) netzVorher = new Object[]{netzblick.bildImBild, netzblick.inSzene, netzblick.netz};
        album.clear();
        albumCaps.clear();
        albumFps = albumGpu = 0;
        albumSamples = 0;
        regieMsg = "";
        frozen = false;
        regie.start(b, regieCtx);
    }

    public void regieStop() {
        regie.stop();
        if (kino.laeuft()) kino.abbrechen();
    }

    /** Ein Regiebuch im Kino (Vorspann mit Titel und Beschreibung, am Ende der Abspann). */
    public void kinoStart(com.dan.lituya.scene.Regie.Book b) {
        frozen = false;
        regie.beiText = kino::untertitel;
        kinoBuch = b;
        kino.beginnen("Lituya Bay · 9. Juli 1958", b.name(), b.about());
        regieStart(b);
    }

    /** Die Zeilen des Abspanns (erste Zeile groß). */
    java.util.List<String> abspann(com.dan.lituya.scene.Regie.Book b) {
        java.util.List<String> z = new java.util.ArrayList<>();
        z.add(b != null ? b.name() : "Lituya Bay 1958");
        z.add("Ort: Lituya Bay an der Küste des Golfs von Alaska, Glacier Bay National Park");
        z.add("Gelände: Lidar 2019, IfSAR 2012, 3DEP; Grund: BlueTopo, Lotungen 1940");
        z.add("Welle: Vorrechnung mit com.dan.wave nach Miller (1960) und Fritz u. a. (2001), gestaltet");
        z.add("Boote: Edrie, Badger, Sunmore nach den Zeugen, Bahnen gerechnet");
        z.add("Netze: Oracle Network Data Model, Welle, Ufer, See, Wasser, Weg, Sicht");
        if (!album.isEmpty()) z.add("Fotos: " + album.size() + " auf dem Blatt in docs/status");
        z.add("Lituya Bay 1958 · Das Projekt der Riesenwelle und des Netzes · Phase " + PHASE);
        return z;
    }

    /** Ein Schnitt (Ansicht, Schaustück, Fahrt, Mitfahrt). Im Kino wartet er auf das Merken des Bilds für die Überblendung. */
    private void schnitt(Runnable r) {
        if (kino.schnitt()) kinoSchnitte.add(r);
        else { r.run(); kinoYaw = 0; }
    }

    private void schnitteJetzt() {
        if (kinoSchnitte.isEmpty()) return;
        java.util.List<Runnable> l = new java.util.ArrayList<>(kinoSchnitte);
        kinoSchnitte.clear();
        for (Runnable x : l) x.run();
        kinoYaw = 0;
    }

    /** Kino weiter, Abspann am Ende des Regiebuchs, Schwenk. */
    private void kinoSchritt(double dt) {
        com.dan.kino.Kino.Lage l = kino.lage();
        if ((l == com.dan.kino.Kino.Lage.VORSPANN || l == com.dan.kino.Kino.Lage.FILM) && !regie.running() && !regieWantsFrame())
            kino.beenden(abspann(kinoBuch));
        kino.schritt(l == com.dan.kino.Kino.Lage.VORSPANN && regie.waiting() != null ? 0 : dt);
        if (!kino.laeuft() && !kinoSchnitte.isEmpty() && !kino.schnittFaellig()) schnitteJetzt();
        double s = kino.schwenk();
        if (camera.fahrt == null && camera.mitfahrt == null && kinoSchnitte.isEmpty()) {
            double d = s - kinoYaw;
            if (camera.mode == com.dan.lituya.camera.Camera.Mode.FREI) camera.fyaw += d; else camera.yaw += d;
        }
        kinoYaw = s;
        if (!kino.laeuft() && kinoNetzblick && !regie.running()) kinoNetzblick = false;
    }

    /** Ohne Bild (Prüfung): Schnitte ausführen, Fotos und Blatt verwerfen. */
    public void bildlos() {
        if (kino.schnittFaellig()) { schnitteJetzt(); kino.geschnitten(); }
        if (!kino.laeuft()) schnitteJetzt();
        captureCaption = null;
        sheetName = null;
    }

    /** Ob ein Foto oder das Blatt auf das nächste Bild wartet. */
    public boolean regieWantsFrame() { return captureCaption != null || sheetName != null || kino.schnittFaellig(); }

    /** „Regie: Zehn Minuten · 1:48: die Wand aus Wasser · 1:10 von 5:00 · 3 Fotos“ (HUD). */
    public String regieLine() {
        return "Regie: " + regie.describe() + (album.isEmpty() ? "" : " · " + album.size() + " Fotos") + (regieMsg.isEmpty() ? "" : " · " + regieMsg);
    }

    /** LB_NET_SICHT (einmal gelesen) oder null. */
    com.dan.lituya.scene.SichtNetz sichtNetz() {
        if (sichtNetz == null) {
            java.io.File d = new java.io.File(System.getProperty("lituya.daten", "daten"));
            try { if (com.dan.lituya.scene.SichtNetz.da(d)) sichtNetz = com.dan.lituya.scene.SichtNetz.lesen(d); } catch (java.io.IOException e) { regieMsg = "LB_NET_SICHT: " + e.getMessage(); }
        }
        return sichtNetz;
    }

    /** Ein Punkt nach Namen: Ansicht, Punkt von LB_NET_SICHT oder Ort: {x, Auge, z, Blick, Neigung} (Blick NaN: frei); null. */
    double[] punkt(String name) {
        com.dan.lituya.camera.Ansichten.Ansicht a = com.dan.lituya.camera.Ansichten.byName(name);
        if (a != null) return new double[]{a.x(), Math.max(groundHeight(a.x(), a.z()), wiring.pegel) + a.eyeAboveGround(), a.z(), a.heading(), a.pitch()};
        double[] vs = com.dan.lituya.camera.Ansichten.versatz(name);   // W4: „Ort @ Richtung, Abstand, Auge, Blick, Neigung“
        if (vs != null) return new double[]{vs[0], Math.max(groundHeight(vs[0], vs[1]), wiring.pegel) + vs[2], vs[1], vs[3], vs[4]};
        com.dan.lituya.scene.SichtNetz w = sichtNetz();
        if (w != null) {
            int k = w.knoten(name);
            if (k >= 0) return new double[]{w.x(k), w.hoehe[k], w.z(k), w.blick[k], Double.isNaN(w.neigung[k]) ? Double.NaN : w.neigung[k]};
        }
        com.dan.lituya.scene.Site.Ort o = com.dan.lituya.scene.Site.byName(name);
        if (o != null) return new double[]{o.x(), Math.max(groundHeight(o.x(), o.z()), wiring.pegel) + 5, o.z(), Double.NaN, Double.NaN};
        return null;
    }

    /** Kamera auf einen Punkt, Blick zum Ziel (null: der Blick des Punkts, sonst zur Mitte der Bucht); false ohne Punkt. */
    public boolean placeNamed(String name, String ziel) {
        double[] p = punkt(name);
        if (p == null) return false;
        com.dan.lituya.camera.Ansichten.Ansicht a = com.dan.lituya.camera.Ansichten.byName(name);
        if (a != null && ziel == null) return placeView(a);
        double[] t = ziel != null ? punkt(ziel) : null;
        if (ziel != null && t == null) return false;
        double h = p[3], ne = p[4];
        if (t != null || Double.isNaN(h)) {
            double tx = t != null ? t[0] : com.dan.lituya.scene.Site.LITUYA_BAY.x(), tz = t != null ? t[2] : com.dan.lituya.scene.Site.LITUYA_BAY.z();
            double ty = t != null ? t[1] - (Double.isNaN(t[3]) ? 0 : 0) : wiring.pegel;
            double dx = tx - p[0], dz = tz - p[2], d = Math.max(1, Math.hypot(dx, dz));
            h = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
            ne = Math.toDegrees(Math.atan2(p[1] - ty, d));
        }
        camera.stopFahrt();
        camera.stopMitfahrt();
        camera.diving = false;
        camera.waterLevel = wiring.pegel;
        camera.groundLevel = groundHeight(p[0], p[2]);
        camera.place(p[0], p[1], p[2], h, Double.isNaN(ne) ? 8 : ne);
        hudMeldung("Ansicht " + name + (ziel != null ? " → " + ziel : ""));
        return true;
    }

    /** Die Fotos des Regiebuchs als ein Blatt nach docs/status (zwei Spalten; aus Rungholt). */
    private void writeSheet(String name) {
        if (album.isEmpty() || ohneBlatt) return;
        try {
            int n = album.size(), cols = 2, rows = (n + 1) / 2, cw = 960, ch = (int) Math.round(album.get(0).getHeight() * cw / (double) album.get(0).getWidth()), cap = 50, head = 44;
            BufferedImage out = new BufferedImage(cols * cw, head + rows * (ch + cap), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(11, 18, 25));
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.setColor(new java.awt.Color(230, 236, 240));
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 20));
            g.drawString(String.format(Locale.GERMAN, "Lituya Bay 1958 · Regiebuch „%s“ · %s · Ø %.0f Bilder/s · Grafikkarte Ø %.2f ms · %s", name, quality.label,
                    albumFps / Math.max(1, albumSamples), albumGpu / Math.max(1, albumSamples), glInfo.replaceAll(" ·.*", "")), 14, 29);
            g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 16));
            for (int i = 0; i < n; i++) {
                int cx = (i % cols) * cw, cy = head + (i / cols) * (ch + cap);
                g.drawImage(album.get(i), cx, cy, cw, ch, null);
                g.setColor(new java.awt.Color(143, 162, 174));
                String[] ln = albumCaps.get(i).split("\n");
                for (int k = 0; k < ln.length; k++) g.drawString(ln[k], cx + 10, cy + ch + 21 + 20 * k);
            }
            g.dispose();
            File dir = photoDir != null ? photoDir : new File("docs/status");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
            String fn = "Regiebuch_" + name.replace("ü", "ue").replace("ö", "oe").replace("ä", "ae").replace("ß", "ss").replace("é", "e").replace(' ', '_') + "_"
                    + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png";
            File f = new File(dir, fn);
            javax.imageio.ImageIO.write(out, "png", f);
            lastSheet = f;
            hudMeldung("Regiebuch gespeichert: " + f.getPath());
            if (!Boolean.getBoolean("lituya.ohneSpeicher")) bildSichern("Blatt", f, name, albumFps / Math.max(1, albumSamples), album.size() + " Fotos");
            System.out.println("Regiebuch gespeichert: " + f.getPath());
        } catch (Exception e) {
            hudMeldung("Blatt des Regiebuchs fehlgeschlagen: " + e.getMessage());
            System.err.println("LituyaBay1958: Blatt: " + e.getMessage());
        }
    }

    /** Nach dem Bild: Fotos und Blatt des Regiebuchs, dann Schnitt mit gemerktem Bild, Balken, Schwarz, Untertitel; Fenster. */
    private void regieBild(GL4 gl, long now) {
        String cap = captureCaption;
        if (cap != null) {
            captureCaption = null;
            album.add(readDefault(gl, surfW, surfH));
            albumCaps.add(clock.label() + " · " + clock.simLabel() + " · " + cap + "\nteuer: " + topPasses(3) + String.format(Locale.GERMAN, " · %.0f Bilder/s", fps));
        }
        if (regie.running()) { albumFps += fps; albumGpu += gpuMs; albumSamples++; }
        String sn = sheetName;
        if (sn != null) { sheetName = null; writeSheet(sn); }
        if (kinoGl != null && kinoGl.bereit()) {
            try {
                if (kino.schnittFaellig()) {
                    kinoGl.merken(gl, defaultFbo, surfW, surfH);
                    schnitteJetzt();
                    kino.geschnitten();
                }
                kinoGl.zeichnen(gl, defaultFbo, surfW, surfH, now / 1e9);
            } catch (RuntimeException e) {
                System.err.println("LituyaBay1958: Kino: " + e);
                kinoGl = null;
            }
        } else if (kino.schnittFaellig()) { schnitteJetzt(); kino.geschnitten(); }
        boolean an = kino.laeuft();
        if (an != kinoFensterAn) {
            kinoFensterAn = an;
            try { kinoFenster.accept(an); } catch (RuntimeException e) { System.err.println("LituyaBay1958: Kinofenster: " + e); }
        }
        if (kino.endeMelden()) hudMeldung("Kino zu Ende" + (lastSheet != null ? " · Blatt: " + lastSheet.getName() : ""));
    }

    /** Der Zeitstand zu Datum und Uhrzeit: vor dem Stoß VOR, in den ersten 7 h 44 min WELLE, danach DANACH, ab 2026 HEUTE. */
    static SceneClock.Zeitstand zeitstandZu(double epochMs) {
        double s = (epochMs - SceneClock.BEBEN.toEpochMilli()) / 1000.0;
        if (epochMs >= java.time.ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, SceneClock.ZONE).toInstant().toEpochMilli()) return SceneClock.Zeitstand.HEUTE;
        if (s < 0) return SceneClock.Zeitstand.VOR;
        if (s < com.dan.lituya.camera.Schaustuecke.MORGEN) return SceneClock.Zeitstand.WELLE;
        return SceneClock.Zeitstand.DANACH;
    }

    /** W6 (E6): Mitte der Gletscherfront 1958 im Gilbert Inlet (Szene x, z) und Abklingen der Eisbrocken (m) [G]. */
    static final double[] EIS_MITTE = {2800, -4150};
    static final double EIS_ABKLINGEN = 900;
    /**
     * W6 Runde 3: die Brocken nah als Körper (Floes, FloeGl): Bedeckung wie meer.frag (Dichte, Abklingen von der Front),
     * nicht auf Land 1958 (Rückbau im Bild) und nicht auf dem Eis 1958; neu gewürfelt, wenn das Auge 4 m wandert oder
     * sich die Dichte ändert.
     */
    private void schollen(GL4 gl, float[] vp, double eisD) {
        com.dan.forest.gl.ForestGl.Light wl = licht.waldLicht();
        if (wl == null) return;
        if (!floeGl.ready()) { floes.slots = 20; floes.sizeMax = 11; floeGl.init(gl); }   // dichter als die Vorgabe: Brocken vor der Front [G]
        double[] e = camera.eye;
        double pegel = wiring.pegel;
        if (floesAuge == null || Math.hypot(e[0] - floesAuge[0], e[2] - floesAuge[2]) > 4 || Math.abs(eisD - floesDichte) > 0.01 || ++floesAlter > 120) {
            floes.fill(e[0], e[2], (x, z) -> {
                double rr = Math.hypot(x - EIS_MITTE[0], z - EIS_MITTE[1]);
                double c = eisD * Math.exp(-Math.max(0, rr - 250) / EIS_ABKLINGEN);
                if (c < 0.01) return 0;
                double h = stapelHoehe(x, z);
                if (Double.isNaN(h)) return 0;
                double[] u = com.dan.lituya.scene.Site.toUtm(x, z);
                double h58 = com.dan.lituya.scene.Boden1958.rueckbauBild(u[0], u[1], h);
                if (h58 > pegel - 0.6) return 0;
                if (FaelleGl.eis1958(x, z, h, 0) > 0.3) return 0;
                return c;
            }, (x, z) -> pegel);
            floeGl.update(gl, floes);
            floesAuge = e.clone();
            floesDichte = eisD;
            floesAlter = 0;
        }
        com.dan.buoyancy.gl.FloeGl.View v = new com.dan.buoyancy.gl.FloeGl.View();
        v.viewProj = vp;
        v.eye = e.clone();
        v.reversedZ = reversedZ;
        com.dan.buoyancy.gl.FloeGl.Light l = new com.dan.buoyancy.gl.FloeGl.Light();
        l.sunDir = wl.sunDir;
        l.sunColor = wl.sunColor;
        l.skyColor = wl.skyColor;
        l.fogColor = wl.fogColor;
        l.fogDistance = wl.fogDistance;
        l.shadow = 0.45;   // [G] seit W6 Runde 5: Anteil der Sonne am Licht aus dem Wasser
        l.waterShadowMap = schollenSchattenKarte;   // W6 Runde 5: Schatten je Ort einmal, nach Fresnel (Renderer)
        floeGl.draw(gl, v, l, clock.t());
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
    }

    /** W6 Runde 5: Schatten der Schollen auf dem Wasser aus der Schattenkarte (false: projizierte Deckel wie Runde 3). */
    public static volatile boolean schollenSchattenKarte = !Boolean.getBoolean("lituya.schollenSchattenAlt");

    /** W6 Runde 3: für HUD und Proben. */
    public String schollenInfo() { return String.format(java.util.Locale.GERMAN, "Schollen %,d (%,d Dreiecke)", floeGl.drawn(), floeGl.triangles()); }

    /** W6 (E6): Schalter für Proben. */
    public volatile boolean eisbrocken = true;

    /**
     * W6 (E6): Dichte der Eisbrocken auf dem Wasser zur Ereigniszeit t (s): erst wenn die Welle abgelaufen ist (die
     * Welle zeichnet das Wasser bis dahin selbst), voll ab rund 40 min, nach zwei Tagen fort (geschmolzen, verdriftet) [G].
     */
    double eisDichte(double t) {
        if (!eisbrocken || !rueckbauJetzt || clock.zeitstand == SceneClock.Zeitstand.HEUTE || clock.zeitstand == SceneClock.Zeitstand.VOR) return 0;
        double an = WaldGl.smooth(1500, 2500, t), aus = 1 - WaldGl.smooth(43_200, 172_800, t);
        return 0.85 * an * aus;
    }

    /** Was die Regiebücher in der App tun. */
    private final class RegieCtx implements com.dan.lituya.scene.Regie.Ctx {
        @Override public void time(java.time.LocalDate date, double hour) {
            jahresRaffer = false;
            clock.set(date, hour);
            clock.zeitstand = zeitstandZu(clock.epochMillis());
        }
        @Override public void ereignis(double s) {
            jahresRaffer = false;
            clock.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + s * 1000);
            clock.zeitstand = SceneClock.Zeitstand.WELLE;
            wiedergabe.an(true);
        }
        @Override public void lapse(double x) { clock.lapse = x; }
        @Override public void zeitstand(SceneClock.Zeitstand z) { jahresRaffer = false; clock.set(z); }
        @Override public void jahre(double v) {
            if (Double.isNaN(v)) { jahresRaffer = false; return; }
            jahreJeMinute = v;
            rafferTage = 0;
            jahresRaffer = true;
        }
        @Override public void view(String name) {
            String[] p = name.split(">", 2);
            String a = p[0].strip(), b = p.length > 1 ? p[1].strip() : null;
            schnitt(() -> { if (!placeNamed(a, b)) regieMsg = "Ansicht „" + name + "“ gibt es nicht"; else ueberKronen(); });
        }
        @Override public void stueck(String name) {
            com.dan.lituya.camera.Schaustuecke.Stueck s = com.dan.lituya.camera.Schaustuecke.byName(name);
            if (s == null) { regieMsg = "Schaustück „" + name + "“ gibt es nicht"; return; }
            jahresRaffer = false;
            schnitt(() -> { placeStueck(s); ueberKronen(); });
        }
        @Override public void fahrt(String name, String ziel, double tempo) {
            schnitt(() -> {
                if (ziel == null) {
                    String k = name.toLowerCase(Locale.ROOT);
                    String w = k.startsWith("rund") ? com.dan.lituya.camera.SichtFahrt.RUNDFLUG : k.startsWith("kamera") || k.startsWith("spann") ? com.dan.lituya.camera.SichtFahrt.KAMERAFAHRT : null;
                    if (w == null) { regieMsg = "Fahrt „" + name + "“ gibt es nicht"; return; }
                    sichtFahrt(w, tempo);
                    return;
                }
                com.dan.lituya.scene.SichtNetz sn = sichtNetz();
                com.dan.lituya.camera.Fahrt f = sn == null ? null : com.dan.lituya.camera.SichtFahrt.zwischen(sn, name, ziel, tempo, Renderer.this::stapelHoehe);
                if (f == null) { regieMsg = "keine Fahrt von „" + name + "“ nach „" + ziel + "“ in LB_NET_SICHT"; return; }
                camera.waterLevel = wiring.pegel;
                camera.startFahrt(f);
            });
        }
        @Override public void flug(double[][] punkte, double dauer, double neigung, double endBlick) {
            schnitt(() -> {
                com.dan.lituya.camera.Fahrt f = new com.dan.lituya.camera.Fahrt("Flug", punkte, dauer, 0, neigung);
                f.endHeading = endBlick;
                f.endTurn = Math.min(4000, f.length * 0.4);
                camera.waterLevel = wiring.pegel;
                camera.startFahrt(f);
            });
        }
        @Override public void mitfahrt(String boot, boolean nebenher) {
            schnitt(() -> {
                if (boot == null) { camera.stopMitfahrt(); return; }
                int i = boot.equals("Edrie") ? 0 : 1;
                boote.laden(new java.io.File(System.getProperty("lituya.daten", "daten")), wiedergabe.welle());
                java.util.function.Supplier<double[]> pose = mitfahrtBoot(i, nebenher);
                if (pose.get() != null) { camera.stopFahrt(); camera.startMitfahrt(pose, nebenher ? -90 : 0); }
                else regieMsg = "Mitfahrt " + boot + ": " + boote.status();
            });
        }
        @Override public void netzblick(String netz, boolean szene) {
            if (netz == null) { kinoNetzblick = false; netzblick.bildImBild = false; netzblick.inSzene = false; return; }
            netzblick.netz = Math.max(0, java.util.Arrays.asList("welle", "wasser", "weg", "sicht").indexOf(netz));
            netzblick.bildImBild = true;
            netzblick.inSzene = szene;
            kinoNetzblick = true;
        }
        @Override public void echtzeit(String ort, double mio) {
            if (ort == null) { echtzeit.aus(); wiring.pegel = 0; return; }
            int i = com.dan.lituya.scene.Regie.ECHTZEIT_ORTE.indexOf(ort);
            echtzeit.ort = Echtzeit.ORTE.get(Math.max(0, i));
            echtzeit.volumen = mio * 1e6;
            echtzeit.grund = Echtzeit.Grund.G1940;
            echtzeit.pegel = 0;
            echtzeit.neu();
            wiring.pegel = 0;
            jahresRaffer = false;
            clock.setEpochMillis(SceneClock.BEBEN.toEpochMilli() + 93_000L);
            clock.zeitstand = SceneClock.Zeitstand.WELLE;
            clock.lapse = 1;
            wiedergabe.an(true);
        }
        @Override public void wetter(com.dan.lituya.scene.Wetterlage l, double uebergangMin) {
            if (!Double.isNaN(uebergangMin)) wetter.uebergangMin = uebergangMin;
            wetter.waehle(l);
            if (uebergangMin == 0) wetter.sofort();
        }
        @Override public void regen(double mmH) { baecheRegenHand = Math.max(0, Math.min(BaecheGl.REGEN_MAX, mmH)); baeche.regen = Math.max(baeche.regen, baecheRegenHand); }
        @Override public void verschluss(double sekunden) { verschlussStellen(sekunden); }   // W7b, W9c: die Kamera und der Regen
        @Override public void umlaufblende(double grad) { verschluss.angleDeg = Math.max(0, Math.min(360, grad)); }   // W9c
        @Override public void tropfen(String a) { linseNass.befehl(a); }   // W9c Runde 2 (W7e)
        @Override public void bildrate(double fps) { bildrateStellen(fps); }
        @Override public void objektiv(String a) { objektivStellen(a); }   // W9a
        @Override public void blende(double n) { objektiv.fNumber = n; objektivAn = true; }
        @Override public void fokus(String a) { fokusStellen(a); }
        @Override public void film(String a) { filmStellen(a); }   // W9b
        @Override public void kopierlicht(String a) { kopierlichtStellen(a); filmAn = true; }
        @Override public void kopie(double jahre) { film.printAgeYears = jahre; filmAn = true; }
        @Override public void zeitlupe(double mass) { wetterGl.zeitlupe = mass; }
        @Override public boolean ready(com.dan.lituya.scene.Regie.Warte w) {
            return switch (w.was()) {
                case "uhr" -> clock.epochMillis() / 3_600_000.0 >= w.wert();
                case "ereignis" -> clock.simSeconds() >= w.wert();
                case "echtzeit" -> echtzeit.an() && !Double.isNaN(echtzeit.bis()) && echtzeit.bis() > 100;
                case "jahre" -> !jahresRaffer;
                case "boote" -> boote.anzahl() > 0;
                default -> true;
            };
        }
        @Override public void capture(String caption) { captureCaption = caption; }
        @Override public void sheet(String name) { sheetName = name; }
        @Override public void say(String text) { regieMsg = text; kino.untertitel(text); }
    }

    /** Höhe des Geländes aus dem Kachelstapel (5 m, sonst gröber) für Bänder und Netze; NaN außerhalb (Phase 16c). */
    public double stapelHoehe(double x, double z) {
        com.dan.clipmap.TileStack s = land.stapel();
        if (s == null) return Double.NaN;
        float h = s.height(x, z, 5.0);
        return Float.isNaN(h) || h <= com.dan.lituya.scene.Land.AUSSEN + 1 ? Double.NaN : h;
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
        letztesDt = frozen ? 1 / 60.0 : dt;
        lastNanos = now;
        if (!frozen) step(dt);
        if (!filmGeschritten) filmUhr += dt;   // W9b: im Standbild läuft der Projektor weiter (neues Korn)
        filmGeschritten = false;

        surfW = Math.max(1, width);
        surfH = Math.max(1, height);
        regulateScale(now);
        int w = Math.max(64, (int) Math.round(surfW * renderScale)), h = Math.max(64, (int) Math.round(surfH * renderScale));
        if (festeGroesse && hdrW > 0 && hdrH > 0) { w = hdrW; h = hdrH; }   // W9: Bedienfeld gleitet
        if (w != hdrW || h != hdrH) resizeTargets(gl, w, h);
        long rf0 = System.nanoTime();
        vorCpu = vorCpu * 0.9 + (rf0 - now) / 1e6 * 0.1;   // Runde 11: Uhr, Schritt, Marken lesen
        if (teilbilder > 0 && frozen) kinoBelichtung(gl, w, h);   // W9c: die Kino-Stufe (der Aufrufer führt die Uhr)
        else renderFrame(gl, w, h);
        long rf1 = System.nanoTime();
        regieBild(gl, now);   // Phase 17a: Fotos und Blatt des Regiebuchs, Kino über das fertige Bild; F12 und Tempo mit
        if (photoRequest) { photoRequest = false; takePhoto(gl, surfW, surfH); }
        Consumer<BufferedImage> gr = grabRequest;
        if (gr != null) { grabRequest = null; try { gr.accept(readDefault(gl, surfW, surfH)); } catch (RuntimeException e) { System.err.println("LituyaBay1958: Bild nicht abgeholt: " + e); } }
        cpuMs = cpuMs * 0.95 + (System.nanoTime() - now) / 1e6 * 0.05;
        nachCpu = nachCpu * 0.9 + (System.nanoTime() - rf1) / 1e6 * 0.1;
        frame++;
        stats(now, w, h);
    }

    /**
     * W9c: ein Filmbild in der Kino-Stufe. Erst ein Schritt der Vorführung (Regie, Kino), dann die Teilbilder nach der Zeit:
     * zwischen ihnen läuft die Szene (Wetter, Meer, Welle, Kamera, Klang), jedes wird mit der Kamera am Ort der Pupille und dem
     * Versatz im Bildpunkt gerechnet und mit seinem Zeitfenster (Öffnungsgang) und dem Katzenauge in die Summe gegeben; am Ende
     * läuft die Szene bis zum nächsten Filmbild weiter (die Umlaufblende ist zu). Dann einmal: das Objektiv ohne Tiefenschärfe
     * (die ist jetzt echt) für Reflexe, Stern, Schleier, Verzeichnung, Farbsäume, Randabfall, die Augenanpassung, der Film.
     */
    private void kinoBelichtung(GL4 gl, int w, int h) {
        if (verschlussGl == null || !verschlussGl.ready()) { renderFrame(gl, w, h); return; }
        boolean linseAn = objektivGl != null && objektivGl.ready() && (objektivAn || kino.laeuft()) && taa;
        com.dan.lens.Lens.Optics o = linseAn ? objektivGl.optics() : null;
        double fm = linseAn ? objektivGl.focusM() : Double.NaN;
        if (Double.isNaN(fm)) fm = objektiv.autoFocus ? 1e5 : objektiv.focusM;
        teilFokusM = Math.max(0.3, fm);
        teilPupilleM = o != null ? o.pupilMm() / 2000 : 0;
        com.dan.shutter.Pupil pu = o != null ? new com.dan.shutter.Pupil(objektiv.aperture.blades, objektiv.aperture.roundness, objektiv.aperture.rotationDeg,
                Math.max(1, objektiv.squeeze)) : null;
        // W9c Runde 2: sphärische Aberration und Längsfarbfehler aus den echten Strahlen des Objektivs (com.dan.lens)
        teilPupille = pu;
        teilAberration = null;
        if (o != null && o.transverseMm() != null) {
            double sMm = teilFokusM * 1000, v = o.efl() * sMm / Math.max(1, sMm - o.efl());
            teilAberration = new com.dan.shutter.Aberration(o.transverseMm(), o.dzR(), o.dzB(), o.pupilMm(), v);
            teilAberration.spherical = objektiv.sphericalAberration;
            teilAberration.chromatic = objektiv.chromatic;
        }
        long kino0 = System.nanoTime();
        teilLichtFertig = false;
        verschluss.fps = kameraBildrate;
        if (filmJetzt()) { verschluss.gateWidthMm = film.format.widthMm(); verschluss.gateHeightMm = film.format.heightMm(); }
        com.dan.shutter.SubFrames.Sample[] plan = com.dan.shutter.SubFrames.plan(verschluss, pu, teilbilder, ++kinoBildNr, true);
        teilZahl = plan.length;
        double takt = Math.max(1e-4, belichtungsTakt), ts = takt * zeitmass();
        double[] rg = verschluss.openRange();
        boolean lief = szeneLaeuft;
        szeneLaeuft = true;
        letztesDt = takt;
        schritt(takt, 0, true);   // die Vorführung: Regie, Kino, Filmbild
        double last = rg[0];
        verschlussGl.begin(gl, w, h);
        try {
            for (com.dan.shutter.SubFrames.Sample sm : plan) {
                double d = Math.min(sm.tauS() - last, ts);
                if (d > 0) schritt(0, d, false);
                last = sm.tauS();
                teilbild = sm;
                renderFrame(gl, w, h);
            }
        } finally {
            teilbild = null;
            szeneLaeuft = lief;
        }
        double rest = ts - (last - rg[0]);
        if (rest > 0) schritt(0, rest, false);
        szeneDt = 0;
        int res = verschlussGl != null ? verschlussGl.resolve(gl, teilLetztes) : teilLetztes;
        kinoLichterZahl = verschlussGl != null ? verschlussGl.lastLights : -1;
        boolean linse = false;
        if (linseAn && letzteVpN != null) {
            boolean dof = objektiv.depthOfField;
            objektiv.depthOfField = false;   // die Tiefenschärfe kam aus der Pupille
            teil("Objektiv");
            try {
                res = objektivBild(gl, res, w, h, letzteVpN, letzteSonne);
                linse = true;
            } catch (RuntimeException e) {
                System.err.println("LituyaBay1958: Objektiv: " + e);
                objektivGl = null;
            } finally {
                objektiv.depthOfField = dof;
            }
            teilEnde("Objektiv");
        }
        gl.glBindVertexArray(emptyVao);
        nachbearbeitung(gl, res, w, h, linse);
        kinoMs = (System.nanoTime() - kino0) / 1e6;
    }

    void renderFrame(GL4 gl, int w, int h) {
        bildSzeneDt = szeneDt;   // W9c: Szenenzeit seit dem letzten Bild (Meer, Ringe, Bewegungsunschärfe)
        szeneDt = 0;
        final com.dan.shutter.SubFrames.Sample tb = teilbild;
        TEIL_UHR.use(gl);
        mark(gl, 0);
        // W9a: Fokusatmung aus der Optik des letzten Bilds; Seitenverhältnis des Fensters (auch während das Bedienfeld gleitet)
        com.dan.lens.Lens.Optics lo = objektivJetzt() ? objektivGl.optics() : null;
        objektivAtmung = lo != null && objektiv.breathing ? lo.breathing() : 1;
        double aspekt = festeGroesse ? surfW / (double) surfH : w / (double) h;
        // 0 Phase 13c: die Echtzeit rechnet hinter der Uhr her (Zeitbudget je Bild), bevor gezeichnet wird
        try {
            if (echtzeit.schritt(gl, clock.simSeconds(), wiedergabe) && echtzeit.grund == Echtzeit.Grund.HEUTE) rueckbauJetzt = false;
            else rueckbauJetzt = rueckbau;
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Echtzeit: " + e);
            echtzeit.aus();
        }
        double[] sp = clock.sun();
        double[] sunPh = Sun.direction(sp[0], sp[1]);
        // W7: Wetter vor dem Licht (Wolken ins Rundumbild, ihr Schatten in die Karte „Sonne“); Sicht und Bedeckung an den Himmel
        com.dan.lituya.wiring.Wetter.Zustand wz = wetter.zustand();
        boolean wetterJetzt = wetter.an && wz != null && wz.an();
        licht.sky.visibilityKm = wetterJetzt ? Math.max(1, wz.sichtKm()) : Licht.SICHT_KM;
        licht.sky.cloudCover = wetterJetzt ? Math.min(1, wz.achtel() / 8) : 0;
        licht.sky.cloudVeil = false;   // die Wolken zeichnet com.dan.cloud
        licht.nass = wetterJetzt ? wz.nass() : 0;
        teil("Wetter");
        try {
            if (licht.grobHoehen() != null) wetterGl.gelaende(licht.grobHoehen(), licht.grobRect(), Licht.GROB_N);
            if (land.stapel() != null) wetterGl.nebelGrund(this::stapelHoehe);
            if (wetterGl.grundHoehe == null) wetterGl.grundHoehe = land::hoehe;   // W7b: worauf der Regen fällt
            if (wetterGl.kronenHalb == null) wetterGl.kronenHalb = wald::sperre;
            wetterGl.blick = camera.fwd.clone();
            wetterGl.blickFovy = fovy();
            wetterGl.regenNachTaa = taa;
            wetterGl.vorbereiten(gl, wetterJetzt ? wz : null, licht, camera.eye, wetterDt, clock.lapse == 0, wiring.pegel);
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Wetter: " + e);
            wetter.an = false;
        }
        teilEnde("Wetter");
        licht.hellFaktor = wetterGl.hellFaktor();
        licht.waldWolken = wetter.an ? wetterGl.waldSonne() : 1;   // W7 Runde 1c: Wald, Fälle und Boote unter den Wolken
        wetterGl.verlauf(Math.abs(clock.lapse) > 30 ? 0.5 : 0.85);   // im schnellen Zeitraffer weniger vom letzten Bild (keine Schlieren)
        licht.panoZusatz = wetterGl.wolkenJetzt() ? g -> wetterGl.panorama(g, camera.eye) : null;
        licht.wolkenUniforms = sh -> wetterGl.uniforms(gl, sh);
        // W1: Himmel und Licht aus com.dan.sky (Sonne mit Strahlenbrechung), Horizontkarten, Rundumbild
        teil("Licht");
        try {
            // W9c Runde 2: in der Kino-Stufe nur im ersten Teilbild (Sonnenkarten, Horizont; die Sonne wandert in 1/48 s 0,004°)
            if (tb == null || !teilLichtFertig) {
                licht.vorbereiten(gl, clock, camera.eye, camera.fwd, fovy(), aspekt, exposureBias, letztesDt, frozen,
                        this::stapelHoehe, land.stapel() != null);
                licht.sonnenKarte(gl);
                if (tb != null) teilLichtFertig = true;
            }
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Licht: " + e);
            licht.an = false;
        }
        teilEnde("Licht");
        // W5: Sonne des Walds in der Höhe des Grunds am Zielpunkt (wie sunAt), nicht am Auge
        { double[] lm = lodMitte(); double gh = groundHeight(lm[0], lm[1]); licht.waldHoehe = Double.isNaN(gh) ? Double.NaN : Math.max(gh, wiring.pegel) + 15; }
        WaldGl.himmel = licht.waldLicht();
        final double[] sun = licht.aktiv() ? licht.state().sunDir.clone() : sunPh;
        float[] proj = Mat4.perspective(fovy(), aspekt, NEAR, FAR, reversedZ);
        float[] view = camera.view();
        // W3 (E1): Zittern beim Beben, um die Kamera (Drehung um zwei Achsen, rund 0,35° bei voller Stärke) [G]
        double bst = bebenStaerke(clock.simSeconds());
        if (bst > 0 && (zittern || kino.laeuft()) && !(frozen && !kino.laeuft())) {
            double tt = clock.t();
            double ax = Math.toRadians(0.35 * bst * (Math.sin(tt * 23.1) * 0.6 + Math.sin(tt * 37.7 + 1.3) * 0.4)),
                   ay = Math.toRadians(0.25 * bst * (Math.sin(tt * 19.3 + 0.7) * 0.6 + Math.sin(tt * 41.9) * 0.4));
            float cx = (float) Math.cos(ax), sx = (float) Math.sin(ax), cy = (float) Math.cos(ay), sy = (float) Math.sin(ay);
            float[] rx = {1, 0, 0, 0, 0, cx, sx, 0, 0, -sx, cx, 0, 0, 0, 0, 1}, ry = {cy, 0, -sy, 0, 0, 1, 0, 0, sy, 0, cy, 0, 0, 0, 0, 1};
            view = Mat4.mul(Mat4.mul(rx, ry), view);
        }
        if (tb != null) {   // W9c Kino-Stufe: die Kamera am Ort der Pupille, die Schärfenebene steht, dazu der Versatz im Bildpunkt
            double ex = tb.u() * teilPupilleM, ey = tb.v() * teilPupilleM;
            view = com.dan.shutter.SubFrames.offsetView(view, ex, ey);
            proj = com.dan.shutter.SubFrames.shearProjection(proj, ex, ey, teilFokusM, tb.jx(), tb.jy(), w, h);
        }
        double[] vpN = mulD(proj, view);   // ohne Versatz (für den Verlauf)
        if (taa && tb == null) {
            // W0 (K2): Versatz nach Halton 2/3 um höchstens einen halben Bildpunkt (8 Lagen)
            int k = (taaFrame++ & 7) + 1;
            proj = proj.clone();
            proj[8] += (float) ((halton(k, 2) - 0.5) * 2.0 / w);
            proj[9] += (float) ((halton(k, 3) - 0.5) * 2.0 / h);
        }
        float[] vp = Mat4.mul(proj, view);
        letzteVP = vp;
        float[] inv = Mat4.invert(vp);
        try {
            // W9c Runde 2 („Schwebe“): der Wald auf dem Grund, den das Gelände zeigt, und mit seiner Erdkrümmung
            wald.rueckbau1958 = rueckbauJetzt && clock.zeitstand != SceneClock.Zeitstand.HEUTE;
            com.dan.clipmap.Clipmap cmw = land.clipmap();
            wald.kruemmung = cmw != null && cmw.curvatureRadius > 0 ? 1 / (2 * cmw.curvatureRadius) : 0;
            wald.vorbereiten(gl, clock);
        } catch (RuntimeException e) {
            System.err.println("LituyaBay1958: Wald: " + e);
            wald.an = false;
        }

        // 0b W2: das Meer (FFT) und das Spiegelbild der Berge, bevor das Hauptbild beginnt
        boolean unterWasser = camera.eye[1] < wiring.pegel + 0.05;
        boolean meerJetzt = showMeer && meer.aktiv(licht, unterWasser, marksJetzt());
        try { ereignisGl.sturzDaten(gl, wiedergabe, wiring.pegel); } catch (RuntimeException e) { System.err.println("LituyaBay1958: Sturz: " + e); }
        try { ereignisGl.flutDaten(gl, wiedergabe, wiring.pegel); } catch (RuntimeException e) { System.err.println("LituyaBay1958: Flut: " + e); }
        try { ereignisGl.grund58(gl, wiedergabe); } catch (RuntimeException e) { System.err.println("LituyaBay1958: Grund 1958: " + e); }
        if (meerJetzt) {
            try {
                // W3 (E1): der Spiegel zerfällt beim Beben in Ringe, rund 8 je Sekunde bei voller Stärke, im Umkreis von 3 km [G]
                double sdt = frozen && !szeneLaeuft ? 0 : bildSzeneDt;   // W9c: Szenenzeit (im Film ohne Fenster lief das Meer nicht)
                if (sdt > 0 && bst > 0) {
                    ringeSchuld += sdt * 8 * bst;
                    for (int k = 0; k < 4 && ringeSchuld >= 1; k++) {
                        ringeSchuld -= 1;
                        double a = bebenRng.nextDouble() * 2 * Math.PI, rr = 30 + 3000 * Math.sqrt(bebenRng.nextDouble());
                        double x = camera.eye[0] + Math.sin(a) * rr, z = camera.eye[2] + Math.cos(a) * rr;
                        double g = groundHeight(x, z);
                        if (g < wiring.pegel - 1) meer.ocean.addImpulse(x, z, 0.05 + 0.10 * bst);
                    }
                }
                teil("Meer");
                meer.beaufort = wetterJetzt ? wz.bucht() : 1;   // W7: Seegang in der Bucht nach dem Wind (eine Stufe ruhiger als draußen)
                meer.spiegelTruebe = wetterJetzt ? Math.min(0.75, wz.regen() / 4.5) : 0;   // W7 Runde 1c: Regen trübt den Spiegel [G]
                // W7 Runde 1d: im Seegang bricht der Spiegel (Bft 1 still wie bisher, Bft 2 0,65, Bft 3 0,4, ab 4 0,3) [G]
                meer.spiegelGlatt = !wetterJetzt || wz.bucht() <= 1 ? 1 : wz.bucht() == 2 ? 0.65 : wz.bucht() == 3 ? 0.4 : 0.3;
                meer.schritt(gl, frozen && !szeneLaeuft ? 0 : bildSzeneDt, wiring.pegel, wiring.windSpeed, wiring.windFromDeg);
                teilEnde("Meer");
                // W5 Tempo: Spiegel nur, wenn genug Wasser im Bild ist (Anteil aus Strahlen, alle 8 Bilder, im Standbild jedes)
                // im Hintergrund (Probe: im Bildfaden kostete es Spitzen, das Mittel der Bildbank stieg um 20–35 ms)
                if (frame % 8 == 0 && (wasserJob == null || wasserJob.isDone())) {
                    final double[] e = camera.eye.clone();
                    final float[] vw = camera.view().clone();
                    final double fy = fovy(), te = clock.simSeconds();
                    wasserJob = wasserExec.submit(() -> { wasserAnteil = wasserAnteil(e, vw, fy, te); });
                }
                meer.spiegelJetzt = wasserAnteil >= SPIEGEL_AB;
                // Runde 6: das Spiegelbild nur jedes zweite Bild neu; sonst gilt das vorige (es kostete in H13 rund 4 ms, und weil
                // es den Wald im eigenen Blick prüft, musste auch das Hauptbild den Wald jedes Bild neu prüfen). Neu sofort nach
                // einem Sprung des Auges (über 3 m) oder einer Drehung über 1°
                if (meer.spiegel && meer.spiegelJetzt) {
                    boolean sprung = spiegelAuge == null || Math.abs(camera.eye[0] - spiegelAuge[0]) + Math.abs(camera.eye[1] - spiegelAuge[1]) + Math.abs(camera.eye[2] - spiegelAuge[2]) > 3
                            || camera.fwd[0] * spiegelBlick[0] + camera.fwd[1] * spiegelBlick[1] + camera.fwd[2] * spiegelBlick[2] < Math.cos(Math.toRadians(1));
                    if (sprung || tb != null || ++spiegelAlter >= spiegelTakt) {   // W9c: in jedem Teilbild neu (die Pupille wandert)
                        teil("Spiegel");
                        spiegelBild(gl, w, h, proj, view, sun);
                        teilEnde("Spiegel");
                        meer.spiegelVP = Mat4.mul(proj, view);   // Runde 7: Blick, mit dem das Spiegelbild entstand
                        meer.spiegelFertig(gl, camera.eye, camera.fwd, wiring.pegel, fovy());   // W8a: für die See
                        spiegelAlter = 0;
                        spiegelAuge = camera.eye.clone();
                        spiegelBlick = camera.fwd.clone();
                    }
                } else { spiegelAuge = null; meer.spiegelUngueltig(); }
                // Runde 7: das Spiegelbild umrechnen, wenn es aus einem früheren Bild stammt (sonst hing es in Fahrten nach)
                meer.jetztVP = spiegelUmrechnen ? Mat4.mul(proj, view) : meer.spiegelVP;
                ereignisGl.spiegelVP = meer.spiegelVP;
                ereignisGl.jetztVP = meer.jetztVP;
                meer.spiegelVorn = new float[]{(float) camera.fwd[0], (float) camera.fwd[1], (float) camera.fwd[2]};
                ereignisGl.spiegelVorn = meer.spiegelVorn;
                ereignisGl.spiegelTiefe = meer.spiegelTiefe();
                ereignisGl.spiegelSchritte = meer.spiegelSchritte;
            } catch (RuntimeException e) {
                System.err.println("LituyaBay1958: Meer: " + e);
                meer.an = false;
                meerJetzt = false;
            }
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
        if (licht.aktiv()) {
            teil("Himmel");
            licht.himmel(gl, vp, inv, reversedZ, w, h, fovy(), clock.t());   // W1: com.dan.sky statt des Platzhalters
            teilEnde("Himmel");
            gl.glBindVertexArray(emptyVao);
        } else {
            common(gl, skyShader, vp, inv, sun);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        mark(gl, 1);

        // Runde 8: Reihenfolge der deckenden Durchgänge. Standard jetzt von vorn nach hinten: Boote, Welle, Wald, dann das
        // Gelände (die Tiefe der Welle und des Walds verwirft verdeckte Bildpunkte des Geländes, bevor sie gerechnet werden;
        // in H13 lag die Wand vor dem Hang, in H6 der Wald). Bäche (gemischt) danach. Die Zeiten der Durchgänge bleiben
        // nach Namen zugeordnet (ordnung). Schalter vornZuerst = false: alte Reihenfolge.
        com.dan.clipmap.Clipmap cm = land.clipmap();
        double tE = clock.simSeconds();
        double[] welleRect = null;
        final boolean vz = vornZuerst;
        int[] folge = vz ? new int[]{3, 4, 2, 1} : new int[]{1, 2, 3, 4};   // 1 Gelände, 2 Wald, 3 Boote, 4 Welle
        for (int fi = 0; fi < 4; fi++) {
            int pass = folge[fi];
            if (pass == 1) {
                // 2 Gelände (Phase 4): alle Stufen der Clipmap mit Tiefe, Rückseiten weg
                if (showGelaende && cm != null) gelaende(gl, cm, vp, inv, sun, camera.eye, false);
                if (vz) {
                    // 2a' Bäche (Phase 16c): eigener Wasser-Pass auf dem Gelände, Tiefe gegen Gelände und Wald (Zeit unter „Wald“)
                    if (showGelaende) {
                        baeche.rueckbauAn = rueckbauJetzt && clock.zeitstand != SceneClock.Zeitstand.HEUTE;
                        double[] rb0 = Site.fromUtm(com.dan.lituya.scene.Boden1958.RUECKBAU_E0, com.dan.lituya.scene.Boden1958.RUECKBAU_N0),
                                rb1 = Site.fromUtm(com.dan.lituya.scene.Boden1958.RUECKBAU_E1, com.dan.lituya.scene.Boden1958.RUECKBAU_N1);
                        baeche.rueckbauRect = new double[]{rb0[0], rb0[1], rb1[0], rb1[1]};
                        baeche.zeichnen(gl, sh -> common(gl, sh, vp, inv, sun), cm, reversedZ, h, fovy());
                    }
                }
            } else if (pass == 2) {
                // 2a Wald (Phase 14a): forest über den Kern mit den Zuständen der Welle und der Wiederbewaldung
                wald.blick = camera.fwd.clone();
                wald.fovy = fovy();
                wald.windMs = wiring.windSpeed;   // W6 (L8)
                wald.windVonGrad = wiring.windFromDeg;
                if (showGelaende) wald.zeichnen(gl, vp, camera.eye, reversedZ, sun, clock.t(), true);   // Runde 6: Prüfung des Walds wiederverwenden
                // W5 (E11): die Bäume, die gerade in der Zunge fallen
                if (showGelaende) wald.knickZeichnen(gl, sh -> common(gl, sh, vp, inv, sun), camera.eye, vp, clock.simSeconds(), reversedZ);
                gl.glBindVertexArray(emptyVao);
                if (!vz) {
                    // 2a' Bäche (Phase 16c): eigener Wasser-Pass auf dem Gelände, Tiefe gegen Gelände und Wald (Zeit unter „Wald“)
                    if (showGelaende) {
                        baeche.rueckbauAn = rueckbauJetzt && clock.zeitstand != SceneClock.Zeitstand.HEUTE;
                        double[] rb0 = Site.fromUtm(com.dan.lituya.scene.Boden1958.RUECKBAU_E0, com.dan.lituya.scene.Boden1958.RUECKBAU_N0),
                                rb1 = Site.fromUtm(com.dan.lituya.scene.Boden1958.RUECKBAU_E1, com.dan.lituya.scene.Boden1958.RUECKBAU_N1);
                        baeche.rueckbauRect = new double[]{rb0[0], rb0[1], rb1[0], rb1[1]};
                        baeche.zeichnen(gl, sh -> common(gl, sh, vp, inv, sun), cm, reversedZ, h, fovy());
                    }
                }
                gl.glBindVertexArray(emptyVao);
                gl.glUseProgram(0);
            } else if (pass == 3) {
                // 2b Boote (Phase 15a): Edrie, Badger, Sunmore auf den vorgerechneten Bahnen, vor der Welle (sie deckt, was unter Wasser liegt)
                boote.laden(new java.io.File(System.getProperty("lituya.daten", "daten")), wiedergabe.welle());
                boote.pano = licht.aktiv() ? licht.panoTextur() : 0;   // W8a: Spiegelungen am Boot
                boote.pegelJetzt = wiring.pegel;   // W8b: das Boot von heute
                if (boote.grund == null) boote.grund = this::groundHeight;
                boote.windVonJetzt = wiring.windFromDeg;
                boote.tanHalb = Math.tan(Math.toRadians(fovy()) / 2);
                boote.bildHoehe = h;
                boote.zeichnen(gl, vp, camera.eye, reversedZ, sun, tE, clock.zeitstand);
                gl.glBindVertexArray(emptyVao);
            } else {
                // 2c Welle und Strom der Vorrechnung (Phase 13b) mit Tiefe; im Rechteck der Welle zeichnet das Meer nicht
                wiedergabe.setzen(tE, clock.lapse);
                ereignisGl.fovy = fovy();
                ereignisGl.spiegelTex = meerJetzt ? meer.spiegelTextur() : 0;   // W3c: Spiegel der Berge auch auf der Welle
                ereignisGl.bildPx = new double[]{w, h};
                // W5 Runde 5: Kacheln hinter dem Gelände; Runde 6: und hinter dem stehenden Wald außerhalb des Bands
                if (ereignisGl.grund == null) ereignisGl.grund = (x, z) -> groundHeight(x, z) + wald.sperre(x, z);
                ereignisGl.zielFbo = hdrColorFbo;   // W4: Ziel des Staubs in halber Auflösung
                ereignisGl.rueckbauAn = rueckbauJetzt && clock.zeitstand != SceneClock.Zeitstand.HEUTE;
                {
                    double wr = Math.toRadians(wiring.windFromDeg + 180);   // weht nach
                    ereignisGl.windXZ = new double[]{Math.sin(wr), -Math.cos(wr)};
                }
                welleRect = ereignisGl.zeichnen(gl, wiedergabe, tE, vp, camera.eye, h, wiring.pegel, sh -> common(gl, sh, vp, inv, sun), reversedZ);
                gl.glBindVertexArray(emptyVao);
            }
            ordnung[fi + 1] = pass;
            mark(gl, fi + 2);
        }

        // 3 Wasser: die Fläche im Pegel mit Erdkrümmung bis zur Kimm (Platzhalter bis ocean in Phase 5a), mit den Marken;
        //   seit Phase 4 mit Tiefe gegen das Gelände
        if (meerJetzt) {
            // W2: com.dan.ocean; dann der Holzteppich am Morgen danach aus meer.frag als Auflage
            double[] rm = wetterGl.ringMitte();   // W7: Regen auf dem Wasser (W7b: Wellenfeld fest in der Welt)
            meer.ringe(wetterGl.ringTextur(), wetterGl.ringKachel(), wetterGl.ringStaerke(), wetterGl.ringArt(), rm[0], rm[1]);
            meer.ringRauigkeit(wetterGl.ringRauigkeit());   // W7b Runde 5: was das Feld nicht trägt, als Facetten
            meer.feinUndMaske(wetterGl.feinTextur(), wetterGl.feinKachel(), wetterGl.feinMitte(), wetterGl.feinRauigkeit(),
                    wetterGl.schauerKarte(), wetterGl.schauerRechteck());   // W8b Runde 2
            meer.zeichnen(gl, vp, camera.eye, reversedZ, licht, welleRect);
            gl.glBindVertexArray(emptyVao);
        } else if (showMeer) {
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(reversedZ ? GL.GL_GEQUAL : GL.GL_LEQUAL);
            gl.glDepthMask(false);
            common(gl, meerShader, vp, inv, sun);
            bindMarks(gl, meerShader);
            wald.meer(gl, meerShader);
            meerShader.set(gl, "uNurTeppich", 0);
            meerShader.set(gl, "uWelle", welleRect != null ? 1 : 0);
            if (welleRect != null) meerShader.set(gl, "uWelleRect", (float) welleRect[0], (float) welleRect[1], (float) welleRect[2], (float) welleRect[3]);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glDepthMask(true);
            gl.glDisable(GL.GL_DEPTH_TEST);
        }
        // 3b Netze auf dem Wasser (Phase 7a, Netzblick „in der Szene“): Linien in Bildpunkten, Tiefe gegen das Gelände
        netzblick.szene(gl, vp, camera.eye, w, h, reversedZ, exposure());
        gl.glBindVertexArray(emptyVao);
        // 3c' W2: Spiegel der Berge und Gletschermilch auf das Wasser (Tiefe als Textur, deshalb ohne Tiefe im Ziel)
        if (meerJetzt) {
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
            final double[] wr = welleRect;
            meer.spiegelLegen(gl, depthTex, sh -> common(gl, sh, vp, inv, sun), wr, (float) NEAR, (float) FAR);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glBindVertexArray(emptyVao);
            // W6 Runde 3: Holzteppich und Eisbrocken erst nach dem Spiegel (vorher legte er die Spiegelung der Front als helle
            // Streifen über das Eis, Probe 78)
            double eisD = eisDichte(clock.simSeconds());   // W6 (E6): Eisbrocken der Front im Gilbert Inlet
            if (wald.teppichJetzt() || eisD > 0.01) {
                gl.glEnable(GL.GL_DEPTH_TEST);
                gl.glDepthFunc(reversedZ ? GL.GL_GEQUAL : GL.GL_LEQUAL);
                gl.glDepthMask(false);
                gl.glEnable(GL.GL_BLEND);
                gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                common(gl, meerShader, vp, inv, sun);
                bindMarks(gl, meerShader);
                wald.meer(gl, meerShader);
                meerShader.set(gl, "uEisAn", eisD > 0.01 ? 1 : 0);
                meerShader.set(gl, "uEisDichte", (float) eisD);
                meerShader.set(gl, "uEisMitte", (float) EIS_MITTE[0], (float) EIS_MITTE[1], (float) EIS_ABKLINGEN);
                boolean sch = schollen && eisD > 0.01;
                meerShader.set(gl, "uEisNah", sch ? (float) (floes.radius * floes.fadeFrom) : 0f, sch ? (float) floes.radius : 0f);
                meerShader.set(gl, "uNurTeppich", 1);
                meerShader.set(gl, "uWelle", welleRect != null ? 1 : 0);
                if (welleRect != null) meerShader.set(gl, "uWelleRect", (float) welleRect[0], (float) welleRect[1], (float) welleRect[2], (float) welleRect[3]);
                gl.glBindVertexArray(emptyVao);
                gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
                gl.glDisable(GL.GL_BLEND);
                gl.glDepthMask(true);
                gl.glDisable(GL.GL_DEPTH_TEST);
                if (sch) schollen(gl, vp, eisD);
            }
        }
        // 3c' W8a: Glas, Drähte und Lichthöfe der Boote (nach Wasser, Gelände und Wald)
        boote.zeichnenDurch(gl, vp, camera.eye, reversedZ, sun, tE, clock.zeitstand, Double.NaN);
        gl.glBindVertexArray(emptyVao);
        // 3c'' W5 (E11): Schleier, die oben am Sporn von der Zunge abreißen und zurückfallen
        if (wiedergabe.an()) {
            schleier.laden(new java.io.File(System.getProperty("lituya.daten", "daten")), wiedergabe.welle());
teil("Schleier");
            schleier.zeichnen(gl, sh -> common(gl, sh, vp, inv, sun), tE, reversedZ, (float) (1 / (2 * Site.EARTH_R_OPTICAL)),
                    h / (2 * Math.tan(Math.toRadians(fovy()) / 2)));
teilEnde("Schleier");
            gl.glBindVertexArray(emptyVao);
        }
        // 3c''' W6 (L9): Wasserfälle über den Steilstufen
        if (showGelaende) {
            faelle.zeichnen(gl, vp, camera.eye, reversedZ, licht, baeche.regen, wiring.windFromDeg, wiring.windSpeed, clock.t(),
                    rueckbauJetzt && clock.zeitstand != SceneClock.Zeitstand.HEUTE);
            gl.glBindVertexArray(emptyVao);
        }
        mark(gl, 6);

        // 3c-W7: Wolken (mit der Tiefe der Szene), Seenebel und Regen über das fertige Bild, vor dem Staub der Welle
        if (!unterWasser && wetterJetzt) {
            try {
                wetterGl.zeichnen(gl, hdrColorFbo, hdrFbo, vp, inv, camera.eye, camera.fwd, reversedZ, depthTex, w, h, fovy(), NEAR, FAR, (int) (taaFrame & 63));
            } catch (RuntimeException e) {
                System.err.println("LituyaBay1958: Wetter im Bild: " + e);
                wetter.an = false;
            }
            gl.glBindVertexArray(emptyVao);
        }

        // 3c Staub und Trübe unter Wasser (Phase 13b): über das HDR-Bild, die Tiefe als Textur
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
        teil("Medien");
        ereignisGl.medien(gl, wiedergabe, tE, depthTex, sh -> common(gl, sh, vp, inv, sun), forward(camera.view()), reversedZ,
                licht.aktiv() ? licht.grundTextur() : 0, licht.feinRect(), wiring.pegel);
        teilEnde("Medien");
        gl.glBindVertexArray(emptyVao);
        mark(gl, 7);

        // 3d Kantenglättung (W0, K2): neues Bild mit dem Verlauf mischen; Zeit unter „Nachbearbeitung“
        int bild = hdrTex;
        // W9c: Bewegung der Kamera seit dem letzten Bild (für die Bewegungsunschärfe, vor der Glättung, die den Verlauf weiterschreibt)
        float[] mbReproj = null;
        if (bewegungJetzt() && taa && taaValid && taaPrevVP != null) {
            double ddx = camera.eye[0] - taaPrevEye[0], ddy = camera.eye[1] - taaPrevEye[1], ddz = camera.eye[2] - taaPrevEye[2];
            if (ddx * ddx + ddy * ddy + ddz * ddz < 200 * 200) {
                double[] m = mulDD(taaPrevVP, invertD(vpN));
                mbReproj = new float[16];
                for (int i = 0; i < 16; i++) mbReproj[i] = (float) m[i];
            }
        }
        if (tb != null) taaValid = false;   // W9c: Teilbilder ohne Verlauf (die Summe glättet)
        if (taa) bild = taaPass(gl, w, h, vpN);
        else taaValid = false;
        // 3d-W9c: Bewegungsunschärfe (die Kamera bewegt sich während der Belichtung), vor dem Objektiv
        bewegungLetzt = false;
        if (mbReproj != null && bildSzeneDt > 0) {
            teil("Bewegung");
            try {
                com.dan.shutter.gl.MotionBlurGl.View mv = new com.dan.shutter.gl.MotionBlurGl.View();
                mv.colorTexture = bild;
                mv.depthTexture = depthTex;
                mv.width = w;
                mv.height = h;
                mv.reproj = mbReproj;
                mv.reversedZ = reversedZ;
                mv.near = NEAR;
                mv.far = FAR;
                mv.scale = bewegung.scale(verschluss.openS(), bildSzeneDt);
                bild = bewegungGl.apply(gl, mv);
                bewegungLetzt = true;
            } catch (RuntimeException e) {
                System.err.println("LituyaBay1958: Bewegungsunschärfe: " + e);
                bewegungGl = null;
            }
            teilEnde("Bewegung");
            gl.glBindVertexArray(emptyVao);
        }
        // 3d-W9a: das Objektiv, nach der Glättung und vor dem Regen (der Regen zeichnet seine Unschärfe mit derselben Linse)
        boolean linse = false;
        if (objektivJetzt() && tb == null) {   // W9c: in den Teilbildern nicht (das Objektiv kommt nach der Summe)
            teil("Objektiv");
            try {
                bild = objektivBild(gl, bild, w, h, vpN, sun);
                linse = true;
            } catch (RuntimeException e) {
                System.err.println("LituyaBay1958: Objektiv: " + e);
                objektivGl = null;
            }
            teilEnde("Objektiv");
            gl.glBindVertexArray(emptyVao);
        } else if (regenLinse) {
            wetterGl.niederschlag.aperture = 0.003;
            wetterGl.niederschlag.focus = 4;
            regenLinse = false;
        }
        // W9c: Belichtung des Regens: im Teilbild ein Augenblick durch ein Loch (Spuren und Unschärfe kommen aus der Summe),
        // mit dem Film die Umlaufblende, sonst das Auge
        if (tb != null) { wetterGl.verschluss = 5e-5; wetterGl.niederschlag.aperture = 1e-4; wetterGl.niederschlag.focus = teilFokusM; regenLinse = true; }
        else wetterGl.verschluss = filmJetzt() ? Math.max(5e-5, verschluss.openS()) : regenVerschlussAuge;
        // 3d-W7b: Schichten, Tropfen und Spritzer des Regens nach der Glättung (der Verlauf würde die bewegten Spuren wegmischen)
        if (taa && !unterWasser && wetterJetzt) {
            try {
                float[] vpf = new float[16];
                for (int i = 0; i < 16; i++) vpf[i] = (float) vpN[i];
                wetterGl.nachGlaettung(gl, bild, depthTex, vpf, Mat4.invert(vpf), camera.eye, camera.fwd, reversedZ, w, h, fovy(), NEAR, FAR);
            } catch (RuntimeException e) {
                System.err.println("LituyaBay1958: Regen nach der Glättung: " + e);
            }
            gl.glBindVertexArray(emptyVao);
        }

        // 3d-W9c: Kino-Stufe: das Teilbild in die Summe; der Rest (Objektiv, Auge, Film, Kino) einmal nach dem letzten
        if (tb != null) {
            teilLetztes = bild;
            letzteVpN = vpN;
            letzteSonne = sun;
            try {
                com.dan.shutter.gl.ShutterGl.View sv = new com.dan.shutter.gl.ShutterGl.View();
                sv.hdrTexture = bild;
                sv.width = w;
                sv.height = h;
                sv.tauS = tb.tauS();
                sv.pupilU = tb.u();
                sv.pupilV = tb.v();
                sv.squeeze = objektiv.squeeze;
                com.dan.lens.Lens.Optics lo2 = teilPupilleM > 0 && objektivGl != null ? objektivGl.optics() : null;
                if (lo2 != null) {
                    sv.catEye = lo2.catEye();
                    sv.tanPerPx = objektiv.sensorHeightMm / h / lo2.efl() * lo2.fieldScale();
                    sv.fNumber = lo2.fNumber();
                    // W9c Runde 2: Fehler des Objektivs je Farbe, Lichter als ganze Kreise
                    double pitch = objektiv.sensorHeightMm / h;
                    com.dan.shutter.Aberration ab = teilAberration;
                    com.dan.shutter.Pupil pu = teilPupille;
                    if (ab != null) {
                        sv.shiftUv = ab.shiftUv(tb.u(), tb.v(), Math.max(1, objektiv.squeeze), pitch, w, h);
                        sv.taBestPx = ab.taBestPx(pitch, pu != null ? pu.scale() : 1);
                        sv.chromaRPx = ab.chromaticPx(0, pitch);
                        sv.chromaBPx = ab.chromaticPx(2, pitch);
                    }
                    if (pu != null) {
                        sv.blades = pu.blades;
                        sv.roundness = pu.roundness;
                        sv.rotationDeg = pu.rotationDeg;
                        sv.pupilScale = pu.scale();
                    }
                    if (kinoLichter) {
                        sv.lightThreshold = 2.0 / Math.max(1e-6, exposure() * auge.factor());
                        sv.depthTexture = depthTex;
                        sv.reversedZ = reversedZ;
                        sv.near = NEAR;
                        sv.far = FAR;
                        sv.cocPxPerDiopter = teilPupilleM * lo2.efl() / pitch;
                        sv.focusM = teilFokusM;
                        sv.subframes = teilZahl;
                    }
                }
                verschlussGl.add(gl, sv);
            } catch (RuntimeException e) {
                System.err.println("LituyaBay1958: Kino-Stufe: " + e);
                verschlussGl = null;
            }
            gl.glBindVertexArray(emptyVao);
            return;
        }
        nachbearbeitung(gl, bild, w, h, linse);
    }

    /** Nach dem Bild der Szene: Augenanpassung, Film oder Filmkurve ins Fenster, Netzblick, Archiv, HUD (W9c ausgelagert). */
    private void nachbearbeitung(GL4 gl, int bild, int w, int h, boolean linse) {
        // 3e W6 Runde 6 (WF19 A): Augenanpassung. Das Histogramm dieses Bilds wird ein bis zwei Bilder später gelesen; am
        // Boden passt sich die Belichtung an (dunkler Wald), im Flug bleibt sie fest. Sprung der Kamera: sofort.
        teil("Auge");
        augeAnpassen(gl, bild, w, h);
        teilEnde("Auge");

        // 4 Nachbearbeitung ins Fenster; W9b: mit dem Film statt der Filmkurve (Negativ, Kopie, Korn, Halation, Bildstand)
        kino.filmStaerke = filmJetzt() ? 0 : kinoKornVorher;
        boolean filmGezeigt = false;
        if (filmJetzt()) {
            teil("Film");
            try {
                filmEinrichten();
                com.dan.film.gl.FilmGl.View fv = new com.dan.film.gl.FilmGl.View();
                fv.hdrTexture = bild;
                fv.targetFbo = defaultFbo;
                fv.outWidth = surfW;
                fv.outHeight = surfH;
                fv.exposure = exposure() * auge.factor();
                fv.timeS = filmUhr;
                filmGl.apply(gl, fv);
                filmGezeigt = true;
            } catch (RuntimeException e) {
                System.err.println("LituyaBay1958: Film: " + e);
                filmGl = null;
            }
            teilEnde("Film");
            gl.glBindVertexArray(emptyVao);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, defaultFbo);
        gl.glViewport(0, 0, surfW, surfH);
        if (!filmGezeigt) {
        post.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, bild);
        post.set(gl, "uHdr", 0);
        post.set(gl, "uFxaa", !taa && fxaa ? 1 : 0);
        post.set(gl, "uTexel", 1f / w, 1f / h);
        post.set(gl, "uExposure", (float) (exposure() * auge.factor()));
        post.set(gl, "uScotopic", licht.aktiv() ? (float) licht.state().scotopic() : 0f);
        post.set(gl, "uBloom", taa && !linse ? (float) bloom : 0f);   // W9a: mit dem Objektiv streut das Schleierlicht statt des Glühens
        post.set(gl, "uRand", linse ? 0f : 1f);                          // und der Randabfall kommt aus den Fassungen
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        mark(gl, 8);

        // 4b Netzblick als Bild im Bild unten rechts (Phase 7a)
        if (!kino.laeuft() || kinoNetzblick) netzblick.bildImBild(gl, overlay, emptyVao, surfW, surfH, netzZeit(), kino.laeuft() ? kino.balkenPx(surfW, surfH) : 0);
        gl.glBindVertexArray(emptyVao);

        // 4c Phase 19a: das Archiv in der Szene (Marken nicht im Kino und nicht im Regiebuch, Untertitel der Zeugen auch dort)
        if (archiv.aktiv()) {
            final int aw = surfW, ah = surfH;
            final float[] avp = letzteVP;
            final double[] eye = camera.eye.clone();
            final double sim = clock.simSeconds();
            final SceneClock.Zeitstand zs = clock.zeitstand;
            final boolean mitMarken = !kino.laeuft() && !regie.running();
            final int unten = kino.laeuft() ? kino.balkenPx(surfW, surfH) : 0;
            archivTafel.refresh(gl, 60, () -> archiv.malen(aw, ah, avp, eye, (x, z) -> { double g = groundHeight(x, z); return g <= KEIN_GRUND + 1 ? stapelHoehe(x, z) : g; },
                    sim, zs, mitMarken, unten), frozen);
            archivTafel.draw(gl, overlay, emptyVao, 0, 0, surfW, surfH);
        }

        // 5 HUD (vorher abschicken: die Grafikkarte arbeitet, während das HUD im selben Faden gemalt wird; aus Brocken)
        gl.glFlush();
        if (showHud && !kino.laeuft() && captureCaption == null) {   // Phase 17a: nicht im Kino und nicht auf den Fotos des Regiebuchs
            hud.refresh(gl, 250, this::paintHud, frozen);
            hud.draw(gl, overlay, emptyVao, 12, Math.max(0, surfH - hud.height - 12), surfW, surfH);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        mark(gl, 9);
        TEILE.endFrame();
    }

    /** Das Gelände (Phase 4) mit Tiefe; gespiegelt (W2): Auge unter dem Spiegel, Wicklung umgekehrt, nur über dem Wasser. */
    private void gelaende(GL4 gl, com.dan.clipmap.Clipmap cm, float[] vp, float[] inv, double[] sun, double[] eye, boolean gespiegelt) {
        clipGl.upload(gl, cm);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDepthMask(true);
        gl.glEnable(GL.GL_CULL_FACE);
        gl.glCullFace(GL.GL_BACK);
        gl.glFrontFace(gespiegelt ? GL.GL_CW : GL.GL_CCW);
        if (gespiegelt) gl.glEnable(GL4.GL_CLIP_DISTANCE0);
        common(gl, gelaendeShader, vp, inv, sun, eye);
        bindMarks(gl, gelaendeShader);
        wald.gelaende(gl, gelaendeShader);
        gelaendeShader.set(gl, "uClipY", gespiegelt ? (float) (wiring.pegel - 0.3) : -1e9f);
        ereignisGl.abriss(gl, gelaendeShader.id(), clock.simSeconds(), false);   // W3 (E2)
        ereignisGl.flut(gl, gelaendeShader.id(), clock.simSeconds());            // W3b: abgeschürfte Hänge ab der Ankunft
        boolean tx = texturen && surface.ready();
        gelaendeShader.set(gl, "uSurfAn", tx ? 1 : 0);
        if (tx) surface.bind(gl, gelaendeShader.id(), 5, 6, 7, 0.35);
        else { gelaendeShader.set(gl, "uSurfA", 5); gelaendeShader.set(gl, "uSurfN", 6); gelaendeShader.set(gl, "uSurfR", 7); }
        gl.glUniform4i(gelaendeShader.loc(gl, "uMat"), surfMat[0], surfMat[1], surfMat[2], surfMat[3]);
        gl.glUniform4i(gelaendeShader.loc(gl, "uMat2"), surfMat2[0], surfMat2[1], surfMat2[2], surfMat2[3]);
        landschaft.bind(gl, gelaendeShader.id(), gespiegelt);   // W6: Farbe aus Sentinel-2, Fels, Schnee, Eis (im Spiegel ohne Feinarbeit)
        boolean rb = rueckbauJetzt && clock.zeitstand != SceneClock.Zeitstand.HEUTE;
        ereignisGl.rueckbau(gl, gelaendeShader, rb);   // W3c: weicher Rückbau, Gletscherzunge, Grund 1958 der Vorrechnung
        double[] k0 = Site.fromUtm(com.dan.lituya.scene.Boden1958.ZUNGE_E0, com.dan.lituya.scene.Boden1958.ZUNGE_N0),
                k1 = Site.fromUtm(com.dan.lituya.scene.Boden1958.ZUNGE_E1, com.dan.lituya.scene.Boden1958.ZUNGE_N1);
        gelaendeShader.set(gl, "uZungeRect", (float) k0[0], (float) k0[1], (float) k1[0], (float) k1[1]);
        gelaendeShader.set(gl, "uGletscherW", (float) com.dan.lituya.scene.Boden1958.GLETSCHER_UNTER, (float) com.dan.lituya.scene.Boden1958.GLETSCHER_FRONT);
        clipGl.draw(gl, gelaendeShader.id(), cm, vp, eye, UNIT_CLIP);
        gl.glDisable(GL4.GL_CLIP_DISTANCE0);
        gl.glFrontFace(GL.GL_CCW);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(emptyVao);
    }

    /**
     * W2: das Spiegelbild am Wasserspiegel (halbe Größe): Gelände und, ab „Hoch“, der Wald, vom Auge unter dem Spiegel.
     * Spiegelung y → 2·Pegel − y als Matrix hinter der Blickmatrix; Gelände unter dem Wasser wird abgeschnitten.
     */
    /** Runde 6: das Spiegelbild jedes wievielte Bild neu (1: jedes). */
    public volatile int spiegelTakt = 2;
    /** Runde 7: das Spiegelbild eines früheren Bilds an die jetzige Stelle umrechnen (Schalter für die Probe). */
    public volatile boolean spiegelUmrechnen = true;
    private int spiegelAlter;
    private double[] spiegelAuge, spiegelBlick;

    private void spiegelBild(GL4 gl, int w, int h, float[] proj, float[] view, double[] sun) {
        meer.spiegelZiel(gl, w, h);
        gl.glClearColor(0, 0, 0, 0);
        gl.glClearDepth(reversedZ ? 0 : 1);
        gl.glDepthMask(true);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        float L = (float) wiring.pegel;
        float[] S = {1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, 2 * L, 0, 1};
        float[] vpR = Mat4.mul(proj, Mat4.mul(view, S));
        float[] invR = Mat4.invert(vpR);
        double[] eyeR = {camera.eye[0], 2 * L - camera.eye[1], camera.eye[2]};
        com.dan.clipmap.Clipmap cm = land.clipmap();
        boolean voll = meer.spiegelImMeer;   // W8a: das ganze Spiegelbild (die See spiegelt selbst)
        meer.nah = NEAR;
        if (voll && licht.aktiv()) {
            // W8a: zuerst der Himmel (ohne Sonnen- und Mondscheibe, den Glanz legt die See), Alpha 1 überall
            int[] sz0 = meer.spiegelFarbeBinden(gl);
            gl.glDisable(GL.GL_DEPTH_TEST);
            gl.glDisable(GL.GL_BLEND);
            gl.glBindVertexArray(emptyVao);
            licht.himmel(gl, vpR, invR, reversedZ, sz0[0], sz0[1], fovy(), clock.t(), false);
            meer.spiegelZiel(gl, w, h);
            gl.glBindVertexArray(emptyVao);
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthMask(true);
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        }
        if (showGelaende && cm != null) gelaende(gl, cm, vpR, invR, sun, eyeR, true);
        if (showGelaende && meer.waldImSpiegel && quality.ordinal() >= Quality.HOCH.ordinal()) {
            gl.glFrontFace(GL.GL_CW);
            wald.zeichnen(gl, vpR, eyeR, reversedZ, sun, clock.t());
            gl.glFrontFace(GL.GL_CCW);
        }
        // W8a: die Boote im Spiegel (auf stillem Wasser steht der Kutter auf seinem Spiegelbild)
        if (voll && meer.booteImSpiegel) {
            gl.glFrontFace(GL.GL_CW);
            boote.zeichnen(gl, vpR, eyeR, reversedZ, sun, clock.simSeconds(), clock.zeitstand, L);
            boote.zeichnenDurch(gl, vpR, eyeR, reversedZ, sun, clock.simSeconds(), clock.zeitstand, L);
            gl.glFrontFace(GL.GL_CCW);
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthMask(true);
        }
        // W7 Runde 1b: Wolken über das gespiegelte Gelände (Bank, Decke); das Ziel ohne Tiefe, die Tiefe gelesen
        // W8a: über dem ganzen Spiegel (auch vor dem Himmel), damit Wasser und Himmel dieselbe Decke zeigen
        if (showGelaende && cm != null && wetter.an && (wetterGl.wolkenJetzt() || wetterGl.nebelJetzt())) {
            int[] sz = meer.spiegelFarbeBinden(gl);
            teil("Spiegelwolken");
            wetterGl.spiegel(gl, vpR, invR, eyeR, reversedZ, meer.spiegelTiefeRoh(), sz[0], sz[1], L, voll);
            if (voll) {   // W8b Runde 2: der Vorhang des Regens auch im Spiegelbild
                double[] fwdR = {camera.fwd[0], -camera.fwd[1], camera.fwd[2]};
                wetterGl.spiegelRegen(gl, vpR, invR, eyeR, fwdR, reversedZ, (float) NEAR, (float) FAR, fovy(), sz[0], sz[1], meer.spiegelTiefeRoh());
            }
            teilEnde("Spiegelwolken");
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthMask(true);
        }
        gl.glBindVertexArray(emptyVao);
        gl.glUseProgram(0);
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

    /** W5 Tempo: Anteil des Bilds (0 … 1), in dem Wasser zu sehen ist; ab {@link #SPIEGEL_AB} wird der Spiegel gerechnet. */
    public volatile double wasserAnteil = 1;
    public static final double SPIEGEL_AB = 0.08;   // Probe W5: mit 4 % blieb er in H6 an (Welle am unteren Rand)

    /**
     * W5 Tempo: Anteil der Bildpunkte, deren Strahl zuerst auf Wasser trifft (Pegel über dem Grund), aus 10 × 6 Strahlen
     * wie {@link #treffer}. Wo die Welle steht, zählt das Wasser nur nahe am Pegel (dort gilt ihr Spiegel).
     */
    private final java.util.concurrent.ExecutorService wasserExec = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Wasseranteil"); t.setDaemon(true); return t; });
    private java.util.concurrent.Future<?> wasserJob;

    double wasserAnteil(double[] eye, float[] v, double fy, double te) {
        double[] right = {v[0], v[4], v[8]}, up = {v[1], v[5], v[9]}, back = {v[2], v[6], v[10]};
        double ty = Math.tan(Math.toRadians(fy) / 2), tx = ty * 16.0 / 9.0;
        final int NX = 10, NY = 6;
        int nass = 0, n = 0;
        double[] d = new double[3];
        for (int j = 0; j < NY; j++)
            for (int i = 0; i < NX; i++) {
                double sx = (2.0 * (i + 0.5) / NX - 1) * tx, sy = (1 - 2.0 * (j + 0.5) / NY) * ty;
                for (int k = 0; k < 3; k++) d[k] = right[k] * sx + up[k] * sy - back[k];
                double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                n++;
                if (d[1] / l > 0.02) continue;   // über die Kimm: Himmel
                double t = 0, step = 4;
                for (int s = 0; s < 160 && t < 40_000; s++) {
                    double px = eye[0] + d[0] / l * t, py = eye[1] + d[1] / l * t, pz = eye[2] + d[2] / l * t;
                    double g = groundHeight(px, pz);
                    if (py <= Math.max(g, wiring.pegel)) {
                        // Wasser, das der Spiegel trifft: nahe am Pegel (welle.frag: bis rund 22 m darüber)
                        if (!(g >= wiring.pegel)) {
                            double e = wiedergabe.an() ? wiedergabe.eta(px, pz, te) : Double.NaN;
                            if (Double.isNaN(e) || e - wiring.pegel < 22) nass++;
                        }
                        break;
                    }
                    t += step;
                    step = Math.min(400, step * 1.06 + 2);
                }
            }
        return nass / (double) n;
    }

    /** Blickrichtung aus der Blickmatrix (Spalten): die dritte Zeile, umgedreht. */
    static float[] forward(float[] v) { return new float[]{-v[2], -v[6], -v[10]}; }

    private void bindMarks(GL4 gl, Shader s) {
        s.set(gl, "uMarks", marksJetzt() ? 1 : 0);
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

    private void common(GL4 gl, Shader s, float[] vp, float[] inv, double[] sun) { common(gl, s, vp, inv, sun, camera.eye); }

    /** Wie oben mit eigenem Auge (W2: das gespiegelte Auge für das Spiegelbild). */
    private void common(GL4 gl, Shader s, float[] vp, float[] inv, double[] sun, double[] eye) {
        s.use(gl);
        s.set(gl, "uSkyMode", 0);
        s.mat(gl, "uVP", vp);
        s.mat(gl, "uInvVP", inv);
        s.set(gl, "uCam", eye);
        s.set(gl, "uSun", sun);
        s.set(gl, "uTime", (float) (clock.t() % 10_000));
        s.set(gl, "uLevel", (float) wiring.pegel);
        s.set(gl, "uReversed", reversedZ ? 1 : 0);
        s.set(gl, "uBild", (float) (taaFrame & 63));
        licht.uniforms(gl, s);   // W1: uSkyMode, Rundumbild, Sonne am Punkt, Horizontkarten
        wetterGl.uniforms(gl, s);   // W7: Wolkenschatten, Nässe
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
        // W0 (K2): zwei Verläufe der Kantenglättung im Wechsel
        Gpu.deleteFbos(gl, taaFbo[0], taaFbo[1]);
        Gpu.deleteTextures(gl, taaTex[0], taaTex[1]);
        for (int i = 0; i < 2; i++) {
            taaTex[i] = Gpu.texture(gl, w, h, GL.GL_RGBA16F, true);   // W1: Mip-Stufen für das Glühen (bloom)
            taaFbo[i] = Gpu.fbo(gl, taaTex[i], 0);
        }
        taaValid = false;
        hdrW = w;
        hdrH = h;
    }

    /**
     * W9a: ein Bild durch das Objektiv. Brennweite aus dem Bildwinkel, Fokus von Hand, auf einen Ort oder aus der Bildmitte;
     * die Sonne mit ihrer Bestrahlung (vorbelichtet) und dem Anteil durch die Wolken; ein Sprung der Kamera und das Standbild
     * stellen den Schärfezieher sofort. Der Regen bekommt dieselbe Pupille und denselben Fokus.
     */
    /**
     * W9c Runde 2: steht die Sonne über dem Gelände, vom Auge aus gesehen (0 oder 1, weich über 0,3°)? Für Tropfen auf der
     * Linse, die die Sonne auch außerhalb des Bilds zum Leuchten bringt; im Bild prüft das Objektiv die Tiefe selbst.
     */
    double sonneFrei(double[] sd) {
        double hz = Math.hypot(sd[0], sd[2]);
        double alt = Math.atan2(sd[1], hz);
        if (alt < -0.01) return 0;
        double[] e = camera.eye;
        double frei = 1;
        for (double t = 20; t < 40_000; t *= 1.12) {
            double x = e[0] + sd[0] / Math.max(1e-6, hz) * t, z = e[2] + sd[2] / Math.max(1e-6, hz) * t;
            double g = groundHeight(x, z);
            if (Double.isNaN(g)) continue;
            double ray = e[1] + Math.tan(alt) * t;
            // um 0,3° weich (die Scheibe der Sonne ist 0,53° groß)
            double d = Math.atan2(ray - g, t);
            frei = Math.min(frei, Math.max(0, Math.min(1, 0.5 + d / Math.toRadians(0.53))));
            if (frei <= 0) return 0;
        }
        return frei;
    }

    private int objektivBild(GL4 gl, int bild, int w, int h, double[] vpN, double[] sun) {
        com.dan.lens.Lens L = objektiv;
        L.focalMm = brennweiteJetzt();
        L.squeeze = filmJetzt() ? film.format.squeeze() : 1;   // W9b: CinemaScope anamorphotisch, das Bokeh oval
        L.quality = switch (quality) {
            case NIEDRIG -> com.dan.lens.Lens.Quality.LOW;
            case ULTRA -> com.dan.lens.Lens.Quality.HIGH;
            default -> com.dan.lens.Lens.Quality.MEDIUM;
        };
        double[] e = camera.eye;
        boolean sprung = objektivAuge == null || Math.abs(e[0] - objektivAuge[0]) + Math.abs(e[1] - objektivAuge[1]) + Math.abs(e[2] - objektivAuge[2]) > 200;
        objektivAuge = e.clone();
        double[] fz = fokusZiel;
        if (fz != null) {
            double[] f = camera.fwd;
            L.autoFocus = false;
            L.focusM = Math.max(0.5, (fz[0] - e[0]) * f[0] + (fz[1] - e[1]) * f[1] + (fz[2] - e[2]) * f[2]);
        }
        com.dan.lens.gl.LensGl.View v = new com.dan.lens.gl.LensGl.View();
        v.colorTexture = bild;
        v.depthTexture = depthTex;
        v.width = w;
        v.height = h;
        v.reversedZ = reversedZ;
        v.near = NEAR;
        v.far = FAR;
        float[] vpf = new float[16];
        for (int i = 0; i < 16; i++) vpf[i] = (float) vpN[i];
        v.viewProj = vpf;
        if (licht.aktiv()) {
            double p = licht.pre();
            double[] sc = licht.state().sunColor;
            v.sunDir = sun;
            v.sunIrradiance = new double[]{sc[0] * p, sc[1] * p, sc[2] * p};
            v.sunVisible = wetter.an && wetterGl.wolkenJetzt() ? wetterGl.sonneAmAuge() : 1;
        }
        v.exposure = exposure() * auge.factor();
        v.dt = letztesDt;
        v.snap = sprung || frozen && !kino.laeuft();
        // W9c Runde 2 (W7e): Tropfen auf der Frontlinse
        float[] dg = tropfenAn ? linseNass.gitter(objektivGl.optics(), objektiv.sensorHeightMm, w / (double) h) : null;
        v.dropCover = dg;
        if (dg != null) {
            if (tropfenPhase == null) tropfenPhase = linseNass.tropfen.phase(32);
            v.dropW = LinseNass.GW;
            v.dropH = LinseNass.GH;
            v.dropPhase = tropfenPhase;
            v.dropMaxRad = linseNass.tropfen.maxDeflection();
            v.dropTransmit = linseNass.tropfen.transmitted();
            v.dropSunVisible = v.sunDir != null ? sonneFrei(v.sunDir) : 0;
        }
        int out = objektivGl.apply(gl, v);
        com.dan.lens.Lens.Optics o = objektivGl.optics();
        if (o != null) {
            wetterGl.niederschlag.aperture = o.pupilMm() / 1000;
            double fm = objektivGl.focusM();
            if (!Double.isNaN(fm)) wetterGl.niederschlag.focus = fm;
            regenLinse = true;
        }
        return out;
    }

    /** „50 mm · f/2,8 · Fokus 12,0 m · …“ oder „Objektiv aus“ (Bedienfeld, HUD). */
    public String objektivLine() {
        if (!objektivJetzt()) return objektivGl == null ? "Objektiv nicht verfügbar" : "Objektiv aus (Lochkamera)";
        double fm = objektivGl.focusM();
        com.dan.lens.Lens.Optics o = objektivGl.optics();
        String s = objektiv.describe(Double.isNaN(fm) ? objektiv.focusM : fm);
        if (o != null) {
            double c = 0.03;   // zulässiger Kreis wie im Kleinbild (Diagonale / 1500)
            double[] dof = o.dofM(c);
            s += String.format(Locale.GERMAN, " · scharf %.1f m bis %s · %d Geisterbilder", dof[0], Double.isInfinite(dof[1]) ? "∞" : String.format(Locale.GERMAN, "%.0f m", dof[1]), o.ghostCount());
        }
        return s;
    }

    /**
     * W0 (K2): die Kantenglättung. Schreibt in den einen Verlauf aus dem anderen und gibt die Textur des Ergebnisses
     * zurück. Der Verlauf gilt nicht nach einem Sprung der Kamera (über 200 m in einem Bild) oder neuer Größe.
     */
    private int taaPass(GL4 gl, int w, int h, double[] vpN) {
        double dx = camera.eye[0] - taaPrevEye[0], dy = camera.eye[1] - taaPrevEye[1], dz = camera.eye[2] - taaPrevEye[2];
        if (dx * dx + dy * dy + dz * dz > 200 * 200) taaValid = false;
        int dst = taaIdx ^ 1;
        float[] reproj = new float[16];
        if (taaValid && taaPrevVP != null) {
            double[] m = mulDD(taaPrevVP, invertD(vpN));
            for (int i = 0; i < 16; i++) reproj[i] = (float) m[i];
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, taaFbo[dst]);
        gl.glViewport(0, 0, w, h);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        taaShader.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, taaTex[taaIdx]);
        gl.glActiveTexture(GL.GL_TEXTURE2);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        taaShader.set(gl, "uCur", 0);
        taaShader.set(gl, "uHist", 1);
        taaShader.set(gl, "uDepth", 2);
        taaShader.mat(gl, "uReproj", reproj);
        taaShader.set(gl, "uReversed", reversedZ ? 1 : 0);
        taaShader.set(gl, "uValid", taaValid && taaPrevVP != null ? 1 : 0);
        taaShader.set(gl, "uTexel", 1f / w, 1f / h);
        taaShader.set(gl, "uAlpha", (float) taaAlpha);
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, taaTex[dst]);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        taaIdx = dst;
        taaValid = true;
        taaPrevVP = vpN;
        System.arraycopy(camera.eye, 0, taaPrevEye, 0, 3);
        return taaTex[dst];
    }

    public static double halton(int i, int b) {
        double f = 1, r = 0;
        while (i > 0) { f /= b; r += f * (i % b); i /= b; }
        return r;
    }

    /** a · b (Spalten, 4 × 4) in double. */
    public static double[] mulD(float[] a, float[] b) {
        double[] da = new double[16], db = new double[16];
        for (int i = 0; i < 16; i++) { da[i] = a[i]; db[i] = b[i]; }
        return mulDD(da, db);
    }

    public static double[] mulDD(double[] a, double[] b) {
        double[] r = new double[16];
        for (int c = 0; c < 4; c++)
            for (int row = 0; row < 4; row++) {
                double v = 0;
                for (int k = 0; k < 4; k++) v += a[k * 4 + row] * b[c * 4 + k];
                r[c * 4 + row] = v;
            }
        return r;
    }

    /** Inverse 4 × 4 (Gauß-Jordan mit Pivot) in double. */
    public static double[] invertD(double[] m) {
        double[][] a = new double[4][8];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) a[r][c] = m[c * 4 + r];
            a[r][4 + r] = 1;
        }
        for (int c = 0; c < 4; c++) {
            int p = c;
            for (int r = c + 1; r < 4; r++) if (Math.abs(a[r][c]) > Math.abs(a[p][c])) p = r;
            double[] t = a[c]; a[c] = a[p]; a[p] = t;
            double d = a[c][c];
            if (Math.abs(d) < 1e-300) return new double[16];
            for (int k = 0; k < 8; k++) a[c][k] /= d;
            for (int r = 0; r < 4; r++) {
                if (r == c) continue;
                double f = a[r][c];
                if (f != 0) for (int k = 0; k < 8; k++) a[r][k] -= f * a[c][k];
            }
        }
        double[] inv = new double[16];
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) inv[c * 4 + r] = a[r][4 + c];
        return inv;
    }

    // ------------------------------------------------------------------ Zeitmessung (Tempo)

    private void mark(GL4 gl, int i) {
        gl.glQueryCounter(stamp[frame & 1][i], GL4.GL_TIMESTAMP);
        cpuStamp[i] = System.nanoTime();
        if (i == MARKS - 1)   // Runde 11: Zeit des Prozessors je Durchgang (Aufrufe an den Treiber, Rechnen davor)
            for (int k = 0; k < MARKS - 1; k++) { int p = k < ordnung.length ? ordnung[k] : k; passCpu[p] = passCpu[p] * 0.9 + Math.max(0, cpuStamp[k + 1] - cpuStamp[k]) / 1e6 * 0.1; }
    }
    private final long[] cpuStamp = new long[MARKS];
    private final double[] passCpu = new double[MARKS - 1];
    private double vorCpu, nachCpu;

    /** Runde 11: „Vorbereitung 1,2 · Himmel 3,4 · …“ Zeit des Prozessors je Durchgang in ms (gleitendes Mittel). */
    public String passCpuTimes() {
        StringBuilder sb = new StringBuilder(String.format(Locale.GERMAN, "vor dem Bild %.2f", vorCpu));
        for (int i = 0; i < passCpu.length; i++) sb.append(" · ").append(PASSES[i]).append(' ').append(String.format(Locale.GERMAN, "%.2f", passCpu[i]));
        sb.append(String.format(Locale.GERMAN, " · nach dem Bild %.2f", nachCpu));
        return sb.toString();
    }

    /** Liest die Zeitstempel des vorigen Bilds, sobald sie da sind (kein Warten im Bildlauf). */
    private void readMarks(GL4 gl) {
        if (frame < 2) return;
        int o = (frame - 1) & 1;
        int[] av = new int[1];
        gl.glGetQueryObjectiv(stamp[o][MARKS - 1], GL4.GL_QUERY_RESULT_AVAILABLE, av, 0);
        if (av[0] == 0) return;
        long[] t = new long[MARKS];
        for (int i = 0; i < MARKS; i++) gl.glGetQueryObjecti64v(stamp[o][i], GL4.GL_QUERY_RESULT, t, i);
        for (int i = 0; i < MARKS - 1; i++) { int k = i < ordnung.length ? ordnung[i] : i; passMs[k] = passMs[k] * 0.9 + Math.max(0, t[i + 1] - t[i]) / 1e6 * 0.1; }
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
                passTimes() + (TEILE.enabled ? " · Teile (ms): " + TEILE : "")
                        + (objektivGl != null && objektivGl.timing ? " · im Objektiv (ms): " + objektivGl.timingLine() : ""),
                (c == null ? "!Compute-Test läuft …" : (c.ok() ? "" : "!") + c.line()),
                uhrenZeile(),
                (licht.aktiv() ? String.format(Locale.GERMAN, "Vorbelichtung %.3g · %s · %s · Zeitraffer %.0f×", licht.pre(), auge, licht.status(), clock.lapse)
                        : String.format(Locale.GERMAN, "Belichtung %.2g (Platzhalter-Himmel, Licht aus) · Zeitraffer %.0f×", exposure(), clock.lapse)),
                wiring.zeile(),
                wetterGl.info() + (linseNass.nass() ? " · " + linseNass.zeile() : ""),
                physik.describe(),
                land.zeile(),
                netzblick.info(),
                "Abspielen: " + wiedergabe.zeile() + " · " + ereignisGl.info(),
                echtzeit.zeile() + (echtzeit.an() ? " · " + echtzeit.messung() : ""),
                waldZeile(),
                boote.info() + " · " + fluchtZeile(),
                wasserZeile() + " · " + wegZeile(),
                baeche.info(),
                sichtZeile(),
                regieLine(),
                klang.describe(),
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

    private String wasser, weg, sicht;

    /** Phase 17: Punkte, Sichtlinien und Rundflug aus LB_NET_SICHT (einmal aus daten/netz/sicht/SICHT.txt), dazu die laufende Fahrt. */
    public String sichtZeile() {
        if (sicht == null) sicht = com.dan.lituya.scene.SichtNetz.zeile(new java.io.File(System.getProperty("lituya.daten", "daten")));
        com.dan.lituya.camera.Fahrt f = camera.fahrt;
        return sicht + (f != null ? " · Fahrt " + f.describe() : "");
    }

    /** Phase 16a: 1 h vom Strand und Washburn 1934 aus LB_NET_WEG (einmal aus daten/netz/weg/WEG.txt). */
    public String wegZeile() {
        if (weg == null) {
            java.io.File d = new java.io.File(System.getProperty("lituya.daten", "daten"));
            weg = com.dan.lituya.scene.WegNetz.zeile(d) + " · " + com.dan.lituya.scene.WegNetz.landfluchtZeile(d);
        }
        return weg;
    }

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
            if (photoDir == null && !Boolean.getBoolean("lituya.ohneSpeicher")) bildSichern("Foto", f, regie.running() ? regie.book().name() : null, fps, null);
            System.out.println(photoMsg);
        } catch (Exception e) {
            photoMsg = "Foto fehlgeschlagen: " + e.getMessage();
            System.err.println(photoMsg);
        }
        photoMsgUntil = System.currentTimeMillis() + 5000;
    }

    /** Phase 19: ein geschriebenes Bild mit Ort und Zeit der Kamera nach Oracle oder daten/speicher (im eigenen Faden). */
    private void bildSichern(String art, File f, String regiebuch, double bildrate, String notiz) {
        double[] u = com.dan.lituya.scene.Site.toUtm(camera.eye[0], camera.eye[2]);
        com.dan.lituya.data.Bilder.sichernSpaeter(art, f, clock.label(), clock.simSeconds(), clock.zeitstand.name(), u[0], u[1], camera.eye[1], camera.heading(),
                Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, camera.fwd[1])))), regiebuch, bildrate, notiz, m -> System.out.println("LituyaBay1958: " + m));
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
        schleier.dispose(gl);
        wasserExec.shutdownNow();
        boote.dispose(gl);
        com.dan.clipmap.Clipmap cm = land.clipmap();
        if (cm != null) cm.stop();
        hud.dispose(gl);
        netzblick.dispose(gl);
        surface.dispose(gl);
        if (kinoGl != null) kinoGl.dispose(gl);
        if (objektivGl != null) objektivGl.dispose(gl);
        if (filmGl != null) filmGl.dispose(gl);
        if (verschlussGl != null) verschlussGl.dispose(gl);
        if (bewegungGl != null) bewegungGl.dispose(gl);
        if (lum != null) lum.dispose(gl);
        baeche.dispose(gl);
        ereignisGl.dispose(gl);
        echtzeit.dispose(gl);
        wiedergabe.close();
        Gpu.deleteFbos(gl, hdrFbo, hdrColorFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        Gpu.deleteFbos(gl, taaFbo[0], taaFbo[1]);
        Gpu.deleteTextures(gl, taaTex[0], taaTex[1]);
        licht.dispose(gl);
        wetterGl.dispose(gl);
        meer.dispose(gl);
        if (emptyVao != 0) gl.glDeleteVertexArrays(1, new int[]{emptyVao}, 0);
        for (int[] s : stamp) if (s[0] != 0) gl.glDeleteQueries(MARKS, s, 0);
        ready = false;
    }
}
