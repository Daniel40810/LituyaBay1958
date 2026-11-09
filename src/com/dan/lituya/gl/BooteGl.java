package com.dan.lituya.gl;

import com.dan.lituya.scene.BootBahn;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.tools.Bootstour;
import com.dan.ocean.HullMask;
import com.dan.vessel.Boat;
import com.dan.vessel.Hull;
import com.dan.vessel.Kutter;
import com.dan.vessel.Troller;
import com.dan.vessel.gl.CraftGl;
import com.dan.vessel.gl.VesselGl;
import com.dan.wake.Wake;
import com.dan.wake.gl.WakeGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die drei Troller in der Szene (Phase 15a): spielt die vorgerechneten Bahnen ({@code daten/ereignis/boote/<lauf>},
 * {@code tools.Boote1958}) zur Ereigniszeit ab und zeichnet sie mit {@code com.dan.vessel.gl.VesselGl} (Licht wie der Wald,
 * linear ins HDR, Tiefe wie die App). Vor der ersten Bahnzeit liegen die Boote am ersten Bild vor Anker, nach der letzten
 * am letzten. Zu sehen im Zeitstand VOR und WELLE; DANACH und HEUTE nicht. Ohne Bahnen: keine Boote (HUD sagt es).
 * <p>
 * Seit Werkbuch Welle W8a zeichnet {@code com.dan.vessel.gl.CraftGl} die Boote nach dem Bootsbauer
 * ({@code com.dan.vessel.Shipwright}): Kraweel-Rumpf mit Anstrich, Rost und Bewuchs, Steuerhaus mit Scheiben, Rigg,
 * Trollstangen mit Schlingerdämpfern, Stützsegel, Name an Bug und Spiegel, eigener Schatten, Ankerlicht in der
 * Dämmerung; dazu im Spiegelbild der Bucht. {@code -Dlituya.alterKutter=true}: die Kästen von Phase 15a.
 * <p>
 * <b>Werkbuch Welle W11 „Die Bootstour“:</b>
 * <ul>
 * <li><b>W11a Tour</b> ({@code daten/ereignis/boote/tour}, {@code tools.Bootstour}): die Edrie läuft am 9.7.1958 um 20:00
 * ein, fährt die Bucht hinauf und zurück und ankert, wo die Bahn des Laufs {@code kette} beginnt; zwischen dem Ende der Tour
 * und dem ersten Bild der Welle gleitet die Lage über ({@link #verketten}). Heute fährt die KITTIWAKE dieselbe Route
 * ({@link #KITTIWAKE}, Index 3). {@link #tourAn} = false: wie bis W10 (Regie {@code tour aus}).</li>
 * <li><b>W11b Wellenschlag</b>: Kielwasser aus {@code com.dan.wake} (Kelvin-Keil aus Bug und Heck, Schaumspur der Schraube;
 * {@code WakeGl} rechnet zwei Kacheln um die Kamera, die See liest sie als {@code kielTexture}); Rumpfmasken für die See
 * ({@link #ruempfe}: kein Wasser im offenen Rumpf, Schaumsaum); die Bugwelle als Netz am Steven ({@link #bugwelle}, Höhe nach
 * Froude, Kamm bei 12 %, Tal bei 50 % der Länge [G]), gezeichnet mit {@code VesselGl} durchscheinend. Nach einem Sprung der
 * Uhr wird die Spur aus der Bahn 180 s zurück nachgezeichnet.</li>
 * <li><b>W11c Standpunkte</b> an Bord ({@link #anBord}: steuerhaus, bug, achtern) und die Lage mit Fahrt für Drohne und Klang.</li>
 * </ul>
 */
public final class BooteGl {
    public static final String[] NAMEN = {"Edrie", "Badger", "Sunmore"};
    /** W11: das Boot von heute als viertes Boot (Index) für Mitfahrt, Drohne, Tour und Klang. */
    public static final int KITTIWAKE = 3;
    public static final String KITTIWAKE_NAME = "Kittiwake";
    /** W11c: die Standpunkte an Bord. */
    public static final String[] STANDPUNKTE = {"steuerhaus", "bug", "achtern"};
    /** W11b: ab dieser Fahrt (m/s) zieht ein Boot ein Kielwasser und eine Bugwelle. */
    public static final double FAHRT_AB = 0.3;
    /** W11b: Wassertiefe für das Kielwasser (m, Mitte der Bucht) und wie weit die Spur nach einem Sprung nachgezeichnet wird (s). */
    public static final double KIEL_TIEFE = 60, SPUR_ZURUECK = 180;
    /**
     * W11b, nach den Proben 1101 und 1102 (W11P1): Deckung der Bugwelle, Stärke des Schaums im Kielwasser (1 = wie wake für
     * Fähren) und der Anteil der Breite, mit dem die Schaumspur hinter der Schraube beginnt (die volle Breite war ein Band
     * so breit wie das Boot) [G].
     */
    public static final float BUGWELLE_ALPHA = 0.55f;
    public static final double SCHAUM_STAERKE = 0.55, SCHAUM_BREITE = 0.6;
    public volatile boolean an = true;
    private final VesselGl gl = new VesselGl();
    /** W8a: die Boote des Bootsbauers. */
    private final CraftGl craft = new CraftGl();
    public volatile boolean alterKutter = Boolean.getBoolean("lituya.alterKutter");
    /** W8a: Rundumbild für Spiegelungen am Boot (vorbelichtet; 0 = Himmelsfarbe), Blickfeld und Bildhöhe für die Drähte. */
    public volatile int pano;
    public volatile double tanHalb = 0.4;
    public volatile int bildHoehe = 1080;
    private final List<Object[]> modelle = new ArrayList<>();   // je gezeichnetem Boot {Rumpf, Modellmatrix}
    /**
     * W8b (WF31 A): heute liegt ein Troller vor Anker in Anchorage Cove – die Bucht ist bis heute Ankerplatz der Troller
     * [G]: „KITTIWAKE“ aus Pelican, weiß mit rotbraunem Schergang, Trollstangen abgesenkt, Ankerlicht in der Dämmerung.
     * Er schwimmt in seiner Schwimmlage am Pegel, den Bug im Wind, und schwoit und rollt leise [G].
     * {@code -Dlituya.ohneHeuteBoot=true}: heute ohne Boot.
     */
    public volatile boolean heuteAn = !Boolean.getBoolean("lituya.ohneHeuteBoot");
    /** Pegel und Wind jetzt (setzt der Renderer je Bild). */
    public volatile double pegelJetzt, windVonJetzt = 135;
    public static final double[] HEUTE_ORT = {-4440, 2470};
    /** W8b Runde 2: Höhe des Grunds (setzt der Renderer); das Boot von heute sucht sich Wasser von mindestens 6 m Tiefe. */
    public volatile java.util.function.DoubleBinaryOperator grund;
    private double[] heuteOrt;
    private Boat heute;
    private double heutePegel = Double.NaN, heuteWind = Double.NaN;
    private float[] heuteModell;
    private double[] heuteQ;
    private final List<BootBahn> bahnen = new ArrayList<>();
    private final List<Boat> boote = new ArrayList<>();
    private volatile String geladen, status = "Boote: nicht geladen";
    private boolean init;
    /** Zuletzt gezeichneter Zustand je Boot (für HUD): {x, y, z, Kurs, Stampfen, Rollen, Zustand, Fahrt m/s}. */
    private final double[][] lage = new double[NAMEN.length + 1][];

    // ------------------------------------------------------------------ W11a: die Tour
    /** Die Bahnen der Tour je Boot (Edrie, Kittiwake) und ihr Versatz zur Ereigniszeit (s): t_bahn = t_ereignis − versatz. */
    private final Map<String, BootBahn> tour = new HashMap<>();
    private final Map<String, Double> tourVersatz = new HashMap<>();
    /** Ob die Tour gespielt wird (Regie {@code tour aus}: nein). */
    public volatile boolean tourAn = true;
    private volatile String tourStatus = "Tour: nicht geladen";
    private boolean tourGeladen;

    // ------------------------------------------------------------------ W11b: der Wellenschlag
    private final Wake wake = new Wake();
    private WakeGl wakeGl;
    private boolean wakeInit;
    private final Map<String, Wake.Source> quellen = new HashMap<>();
    private double wakeT = Double.NaN;
    /** Stufe des Kielwassers (0 Niedrig … 3 Ultra: Kacheln 256² … 768²), setzt der Renderer nach der Qualität. */
    public volatile int kielStufe = 2;
    /** Kielwasser und Bugwelle an (Schalter für Proben). */
    public volatile boolean kielwasserAn = !Boolean.getBoolean("lituya.ohneKielwasser"), bugwelleAn = !Boolean.getBoolean("lituya.ohneBugwelle");
    private final List<HullMask> ruempfe = new ArrayList<>();
    private final Map<String, Object> bugSchluessel = new HashMap<>();
    private int kielQuellen;

    /** Lädt die Bahnen des Laufs (ohne GL; leise, wenn es keine gibt) und einmal die der Tour. */
    public synchronized void laden(File daten, String lauf) {
        if (!tourGeladen) tourLaden(daten);
        if (lauf == null || lauf.equals(geladen)) return;
        geladen = lauf;
        bahnen.clear();
        boote.clear();
        File dir = new File(new File(new File(daten, "ereignis"), "boote"), lauf);
        if (!dir.isDirectory() && !"kette".equals(lauf)) dir = new File(new File(new File(daten, "ereignis"), "boote"), "kette");
        for (String n : NAMEN) {
            File f = new File(dir, "bahn_" + n.toLowerCase(Locale.ROOT) + ".bin");
            if (!f.isFile()) continue;
            try {
                bahnen.add(BootBahn.lesen(f, n));
                Boat b = switch (n) { case "Edrie" -> Kutter.edrie(); case "Badger" -> Kutter.badger(); default -> Kutter.sunmore(); };
                boote.add(b);
                craft.prepare(b.hull, Troller.fuer(b.hull));   // W8a: im Hintergrund
            } catch (Exception e) {
                System.err.println("LituyaBay1958: Boote: " + f + ": " + e.getMessage());
            }
        }
        status = bahnen.isEmpty() ? "Boote: keine Bahnen in " + dir + " (ant boote1958)" : String.format(Locale.GERMAN, "Boote: %d Bahnen (%s, %.0f … %.0f s)", bahnen.size(), dir.getName(), bahnen.get(0).anfang(), bahnen.get(0).ende());
    }

    /** W11a: die Bahnen der Tour aus {@code daten/ereignis/boote/tour} (TOUR.txt mit der Basis je Boot). */
    private void tourLaden(File daten) {
        tourGeladen = true;
        tour.clear();
        tourVersatz.clear();
        File dir = new File(new File(new File(daten, "ereignis"), "boote"), Bootstour.ORDNER);
        File txt = new File(dir, Bootstour.DATEI);
        if (!txt.isFile()) { tourStatus = "Tour: keine Bahnen in " + dir + " (ant bootstour)"; return; }
        try {
            java.util.Properties p = new java.util.Properties();
            try (var in = java.nio.file.Files.newBufferedReader(txt.toPath(), java.nio.charset.StandardCharsets.UTF_8)) { p.load(in); }
            StringBuilder sb = new StringBuilder("Tour:");
            for (String n : new String[]{"Edrie", KITTIWAKE_NAME}) {
                String k = n.toLowerCase(Locale.ROOT);
                File f = new File(dir, "bahn_" + k + ".bin");
                String basis = p.getProperty(k + ".basis_ms");
                if (!f.isFile() || basis == null) continue;
                BootBahn b = BootBahn.lesen(f, n);
                tour.put(n, b);
                tourVersatz.put(n, (Long.parseLong(basis.trim()) - SceneClock.BEBEN.toEpochMilli()) / 1000.0);
                String uhr = p.getProperty(k + ".uhr", "");
                sb.append(' ').append(n).append(' ').append(uhr.replace(" ", " … ")).append(';');
            }
            tourStatus = tour.isEmpty() ? "Tour: " + txt + " ohne Bahnen" : sb.toString();
        } catch (Exception e) {
            tourStatus = "Tour: " + e.getMessage();
            System.err.println("LituyaBay1958: Tour: " + e);
        }
    }

    public String status() { return status; }

    /** W11a: „Tour: Edrie 20:00 … 21:52; Kittiwake 10:00 … 11:39;“ oder warum nicht. */
    public String tourStatus() { return tourStatus; }

    /** W11a: ob es Bahnen der Tour gibt. */
    public boolean tourDa() { return !tour.isEmpty(); }

    public int anzahl() { return bahnen.size(); }

    /** Sichtbar im Zeitstand? */
    public static boolean sichtbar(SceneClock.Zeitstand z) { return z == SceneClock.Zeitstand.VOR || z == SceneClock.Zeitstand.WELLE; }

    /**
     * W11a: der Zustand des Boots k zur Ereigniszeit t aus Tour und Bahn des Laufs: bis zum Ende der Tour die Tour (davor ihr
     * erstes Bild), ab dem Anfang der Bahn des Laufs diese, dazwischen ein weicher Übergang ({@link #verketten}).
     */
    private double[] zustandBoot(int k, double t) {
        BootBahn kb = bahnen.get(k);
        BootBahn tb = tourAn ? tour.get(kb.name) : null;
        if (tb == null) return kb.zustand(t);
        double tt = t - tourVersatz.getOrDefault(kb.name, 0.0);
        if (tt <= tb.ende()) return tb.zustand(tt);
        if (t >= kb.anfang()) return kb.zustand(t);
        double tEnde = tb.ende() + tourVersatz.getOrDefault(kb.name, 0.0);
        double f = (t - tEnde) / Math.max(1e-6, kb.anfang() - tEnde);
        return verketten(tb.zustand(tb.ende()), kb.zustand(kb.anfang()), f);
    }

    /**
     * W11a: der Übergang zwischen dem letzten Bild einer Bahn a und dem ersten einer Bahn b (Anteil f 0 … 1, weich): Lage
     * linear, Drehung als normierte Mischung der Quaternionen, Wasser linear, Zustand und Glied von b ab der Mitte.
     */
    public static double[] verketten(double[] a, double[] b, double f) {
        f = Math.max(0, Math.min(1, f));
        f = f * f * (3 - 2 * f);
        double[] r = new double[10];
        for (int i = 0; i < 3; i++) r[i] = a[i] + (b[i] - a[i]) * f;
        double dot = a[3] * b[3] + a[4] * b[4] + a[5] * b[5] + a[6] * b[6], sg = dot < 0 ? -1 : 1, l = 0;
        for (int i = 3; i < 7; i++) { r[i] = a[i] + (sg * b[i] - a[i]) * f; l += r[i] * r[i]; }
        l = Math.sqrt(Math.max(1e-12, l));
        for (int i = 3; i < 7; i++) r[i] /= l;
        r[7] = a[7] + (b[7] - a[7]) * f;
        r[8] = f < 0.5 ? a[8] : b[8];
        r[9] = f < 0.5 ? a[9] : b[9];
        return r;
    }

    /** W11a: die Kette des Boots k zur Zeit t: in der Tour keine (ankerauf), sonst die des Laufs. */
    private double[][] ketteBoot(int k, double t) {
        BootBahn kb = bahnen.get(k);
        BootBahn tb = tourAn ? tour.get(kb.name) : null;
        if (tb != null && t - tourVersatz.getOrDefault(kb.name, 0.0) <= tb.ende()) return null;
        return kb.kette(t);
    }

    /** Das Boot i zur Zeit t in seine Lage gestellt (auch das von heute), oder null. */
    private Boat boot(int i, double t) {
        if (i == KITTIWAKE) {
            if (!heuteAn) return null;
            heuteLage(t);
            return heute;
        }
        int k = index(i);
        if (k < 0) return null;
        Boat b = boote.get(k);
        double[] s = zustandBoot(k, t);
        b.pose(new double[]{s[0], s[1], s[2]}, new double[]{s[3], s[4], s[5], s[6]});
        return b;
    }

    /** Lage des Boots i zur Zeit t: {x, y, z, Kurs, Stampfen, Rollen, Zustand, Fahrt m/s} oder null. */
    public synchronized double[] lage(int i, double t) {
        if (i == KITTIWAKE) {
            if (!heuteAn) return null;
            double[] h1 = heuteLage(t - 0.5), h2 = heuteLage(t + 0.5), h = heuteLage(t);
            double v = Math.hypot(h2[0] - h1[0], h2[2] - h1[2]);
            return new double[]{h[0], h[1], h[2], h[3], h[4], h[5], h[6], v};
        }
        int k = index(i);
        if (k < 0) return null;
        Boat b = boote.get(k);
        double[] s1 = zustandBoot(k, t - 0.5), s2 = zustandBoot(k, t + 0.5), s = zustandBoot(k, t);
        b.pose(new double[]{s[0], s[1], s[2]}, new double[]{s[3], s[4], s[5], s[6]});
        return new double[]{s[0], s[1], s[2], b.heading(), b.pitch(), b.roll(), s[8], Math.hypot(s2[0] - s1[0], s2[2] - s1[2])};
    }

    /** Auge am Steuerhaus des Boots i (vor seinem Fenster, Welt) mit Kurs, Stampfen, Rollen: {x, y, z, Kurs, Stampfen, Rollen} oder null. */
    public synchronized double[] steuerhaus(int i, double t) { return anBord(i, "steuerhaus", t); }

    /**
     * W11c: Auge an Bord des Boots i am Standpunkt (Welt) mit Kurs, Stampfen, Rollen: {x, y, z, Kurs, Stampfen, Rollen} oder
     * null. „steuerhaus“: vor dem Fensterband (das Netz ist innen nicht ausgebaut), Augenhöhe am Fenster (wie 15a); „bug“: 1,2 m
     * hinter dem Steven, 1,65 m über Deck; „achtern“: 0,8 m vor dem Spiegel, 0,4 m an Steuerbord, 1,65 m über Deck [G].
     */
    public synchronized double[] anBord(int i, String stand, double t) {
        Boat b = boot(i, t);
        if (b == null) return null;
        Hull h = b.hull;
        double s, y, q;
        switch (stand == null ? "" : stand.toLowerCase(Locale.ROOT)) {
            case "bug" -> { s = 0.5 * h.loa - 1.2; y = h.deck(s) + 1.65; q = 0; }
            case "achtern" -> { s = -0.5 * h.loa + 0.8; y = h.deck(s) + 1.65; q = 0.4; }
            default -> { s = h.houseS1 + 0.35; y = h.deck(0.5 * (h.houseS0 + h.houseS1)) + 0.76 * h.houseHeight; q = 0; }
        }
        double[] e = b.toWorld(s, y, q);
        return new double[]{e[0], e[1], e[2], b.heading(), b.pitch(), b.roll()};
    }

    /** W11b: Bug (Steven in Höhe der Wasserlinie) des Boots i zur Zeit t, Welt, oder null. */
    public synchronized double[] bug(int i, double t) {
        Boat b = boot(i, t);
        if (b == null) return null;
        return b.toWorld(0.5 * b.hull.loa, b.hull.draft, 0);
    }

    /** Die Rumpfform des Boots i (für Kielwasser und Klang) oder null. */
    public synchronized Hull rumpf(int i) {
        if (i == KITTIWAKE) { heuteBoot(); return heute.hull; }
        int k = index(i);
        return k < 0 ? null : boote.get(k).hull;
    }

    private int index(int i) {
        if (i < 0 || i >= NAMEN.length) return -1;
        for (int k = 0; k < bahnen.size(); k++) if (bahnen.get(k).name.equals(NAMEN[i])) return k;
        return -1;
    }

    /** Index eines Boots nach Namen (Edrie, Badger, Sunmore, Kittiwake) oder −1. */
    public static int index(String name) {
        if (name == null) return -1;
        for (int i = 0; i < NAMEN.length; i++) if (NAMEN[i].equalsIgnoreCase(name.trim())) return i;
        return KITTIWAKE_NAME.equalsIgnoreCase(name.trim()) ? KITTIWAKE : -1;
    }

    /** Name eines Boots nach Index. */
    public static String name(int i) { return i == KITTIWAKE ? KITTIWAKE_NAME : i >= 0 && i < NAMEN.length ? NAMEN[i] : "?"; }

    /** Zeichnet in den gebundenen HDR-Bildspeicher. */
    public synchronized void zeichnen(GL4 g, float[] vp, double[] eye, boolean reversed, double[] sun, double t, SceneClock.Zeitstand z) {
        zeichnen(g, vp, eye, reversed, sun, t, z, Double.NaN);
    }

    /**
     * W8a: wie oben; pegel nicht NaN: für das Spiegelbild (alles unter dem Pegel abgeschnitten, ohne Ketten, Schatten aus
     * dem Hauptbild).
     */
    public synchronized void zeichnen(GL4 g, float[] vp, double[] eye, boolean reversed, double[] sun, double t, SceneClock.Zeitstand z, double pegel) {
        boolean spiegel = !Double.isNaN(pegel);
        if (!spiegel) modelle.clear();
        if (an && z == SceneClock.Zeitstand.HEUTE && heuteAn && !alterKutter) {
            if (!init) { gl.init(g); craft.init(g); init = true; }
            float[] mdl = heutePose(t);
            if (craft.drawOpaque(g, heute.hull, mdl, craftBlick(vp, eye, reversed, pegel), craftLicht(sun)) && !spiegel) modelle.add(new Object[]{heute.hull, mdl});
            if (!spiegel) {
                double[] h1 = heuteLage(t - 0.5), h2 = heuteLage(t + 0.5), h = heuteLage(t);
                lage[KITTIWAKE] = new double[]{h[0], h[1], h[2], h[3], h[4], h[5], h[6], Math.hypot(h2[0] - h1[0], h2[2] - h1[2])};
            }
            g.glDisable(GL.GL_DEPTH_TEST);
            g.glUseProgram(0);
            return;
        }
        if (!an || bahnen.isEmpty() || !sichtbar(z)) return;
        if (!init) { gl.init(g); craft.init(g); init = true; }
        VesselGl.View v = new VesselGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        v.reversedZ = reversed;
        v.tonemap = false;
        VesselGl.Light l = licht(sun);
        for (int k = 0; k < bahnen.size(); k++) {
            BootBahn bb = bahnen.get(k);
            Boat b = boote.get(k);
            double[] s = zustandBoot(k, t);
            b.pose(new double[]{s[0], s[1], s[2]}, new double[]{s[3], s[4], s[5], s[6]});
            float[] mdl = b.model();
            boolean neu = !alterKutter && craft.drawOpaque(g, b.hull, mdl, craftBlick(vp, eye, reversed, pegel), craftLicht(sun));
            if (!neu && !spiegel) gl.draw(g, b, v, l);
            if (neu && !spiegel) modelle.add(new Object[]{b.hull, mdl});
            double[][] kn = ketteBoot(k, t);
            if (kn != null && !spiegel) gl.draw(g, kn, (int) s[9], v, l);
            if (!spiegel) {
                double[] s1 = zustandBoot(k, t - 0.5), s2 = zustandBoot(k, t + 0.5);
                for (int i = 0; i < NAMEN.length; i++) if (NAMEN[i].equals(bb.name)) lage[i] = new double[]{s[0], s[1], s[2], b.heading(), b.pitch(), b.roll(), s[8], Math.hypot(s2[0] - s1[0], s2[2] - s1[2])};
            }
        }
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glUseProgram(0);
    }

    /**
     * W8a: Glas, Drähte und Lichthöfe der Boote nach allem Deckenden (Wasser, Gelände, Wald); pegel wie bei
     * {@link #zeichnen(GL4, float[], double[], boolean, double[], double, SceneClock.Zeitstand, double)}. W11b: dazu die
     * Bugwellen der fahrenden Boote (nicht im Spiegelbild).
     */
    public synchronized void zeichnenDurch(GL4 g, float[] vp, double[] eye, boolean reversed, double[] sun, double t, SceneClock.Zeitstand z, double pegel) {
        boolean spiegel = !Double.isNaN(pegel);
        CraftGl.View cv = craftBlick(vp, eye, reversed, pegel);
        CraftGl.Light cl = craftLicht(sun);
        if (an && z == SceneClock.Zeitstand.HEUTE && heuteAn && !alterKutter && init && heute != null) {
            craft.drawTransparent(g, heute.hull, spiegel || heuteModell == null ? heutePose(t) : heuteModell, cv, cl);
            if (!spiegel) bugwellen(g, vp, eye, reversed, sun, t, z);
            g.glDisable(GL.GL_DEPTH_TEST);
            g.glUseProgram(0);
            return;
        }
        if (!an || alterKutter || bahnen.isEmpty() || !sichtbar(z) || !init) return;
        if (spiegel) {
            for (int k = 0; k < boote.size(); k++) {
                Boat b = boote.get(k);
                double[] s = zustandBoot(k, t);
                b.pose(new double[]{s[0], s[1], s[2]}, new double[]{s[3], s[4], s[5], s[6]});
                craft.drawTransparent(g, b.hull, b.model(), cv, cl);
            }
        } else {
            for (Object[] m : modelle) craft.drawTransparent(g, m[0], (float[]) m[1], cv, cl);
            bugwellen(g, vp, eye, reversed, sun, t, z);
        }
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glUseProgram(0);
    }

    // ------------------------------------------------------------------ W11b: Bugwelle

    /** Die Boote, die im Zeitstand z zu sehen sind (Indizes). */
    private int[] sichtbare(SceneClock.Zeitstand z) {
        if (!an) return new int[0];
        if (z == SceneClock.Zeitstand.HEUTE) return heuteAn && !alterKutter ? new int[]{KITTIWAKE} : new int[0];
        if (!sichtbar(z)) return new int[0];
        int n = 0;
        int[] r = new int[NAMEN.length];
        for (int i = 0; i < NAMEN.length; i++) if (index(i) >= 0) r[n++] = i;
        return java.util.Arrays.copyOf(r, n);
    }

    /** W11b: die Bugwellen aller fahrenden Boote, durchscheinend, mit Glanz wie Wasser. */
    private void bugwellen(GL4 g, float[] vp, double[] eye, boolean reversed, double[] sun, double t, SceneClock.Zeitstand z) {
        if (!bugwelleAn || !init) return;
        VesselGl.View v = new VesselGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        v.reversedZ = reversed;
        v.tonemap = false;
        VesselGl.Light l = licht(sun);
        for (int i : sichtbare(z)) {
            double[] lg = lage(i, t);
            if (lg == null || lg[7] < FAHRT_AB) continue;
            Boat b = boot(i, t);
            if (b == null) continue;
            Object key = bugSchluessel.computeIfAbsent(name(i), k -> new Object());
            gl.upload(g, key, bugwelle(b.hull, lg[7], t));
            gl.draw(g, key, b.model(), v, l, BUGWELLE_ALPHA, 0.35f);
        }
        g.glDisable(GL.GL_BLEND);
        g.glDepthMask(true);
    }

    /**
     * W11b: das Netz der Bugwelle im Achsenkreuz des Rumpfs (s voraus, y über dem Kiel, q nach Steuerbord; 9 float je Ecke wie
     * {@code vessel.Mesh}: Ort, Normale, Farbe). Eine Schürze vom Steven bis 55 % der Länge nach achtern, beidseits: am Rumpf
     * die Höhe η(x) = h · (A(x) − 0,35 · T(x)) mit dem Kamm A(x) = (x/0,12) · e^(1 − x/0,12) bei 12 % der Länge und dem Tal
     * T(x) = e^(−((x − 0,45)/0,15)²) bei rund 50 %, h = 0,5 · v²/2g (die Hälfte der Stauhöhe, 0,43 m bei 8 kn); nach außen
     * fällt sie auf ein Viertel und läuft in einem Saum in den Pegel aus, die Breite wächst nach achtern bis 1,2 m. Schaum
     * (hell, gesprenkelt) auf dem Kamm, Wasser (grüngrau) im Tal und außen; ein leichtes Kräuseln läuft mit der Zeit nach
     * achtern [G]. Nach den Proben W11P1: schmaler und durchscheinender als die erste Form (2,2 m, Deckung 0,82).
     */
    public static float[] bugwelle(Hull h, double v, double zeit) {
        final int N = 28;
        double g = 9.81, hoehe = 0.5 * v * v / (2 * g) * Math.min(1, v / 1.5);
        double s0 = 0.5 * h.loa - 0.05, s1 = 0.5 * h.loa - 0.55 * h.loa;
        double wasser = h.draft;
        float[][][] st = new float[N][3][];   // je Station: innen, außen, Saum (s, y, q>0) und Farbe
        float[][] farbe = new float[N][];
        for (int i = 0; i < N; i++) {
            double s = s0 + (s1 - s0) * i / (N - 1), x = (s0 - s) / h.loa;
            double A = (x / 0.12) * Math.exp(1 - x / 0.12), T = Math.exp(-((x - 0.45) / 0.15) * ((x - 0.45) / 0.15));
            double eta = hoehe * (A - 0.35 * T) * (1 + 0.08 * Math.sin(2 * Math.PI * (x * 6 - zeit * 1.3)));
            double breite = Math.min(1.2, 0.15 + 0.2 * h.loa * x) * (0.4 + 0.6 * Math.min(1, v / 4.1));
            double yi = wasser + eta;
            double hb = h.halfBreadth(s, Math.min(h.deck(s) - 0.02, Math.max(h.keel(s) + 0.05, wasser + Math.max(0, eta) * 0.5)));
            double qi = hb + 0.03;
            st[i][0] = new float[]{(float) s, (float) yi, (float) qi};
            st[i][1] = new float[]{(float) s, (float) (wasser + 0.25 * eta), (float) (qi + breite)};
            st[i][2] = new float[]{(float) s, (float) (wasser - 0.03), (float) (qi + breite + 0.25)};
            // Schaum nur auf dem Kamm, gesprenkelt (zwei Rauschlagen längs, mit der Zeit wandernd) [G]
            double fleck = 0.75 + 0.25 * Math.sin(2 * Math.PI * (x * 9.3 - zeit * 0.9)) * Math.sin(2 * Math.PI * (x * 4.1 + zeit * 0.37) + 1.3);
            float schaum = (float) Math.max(0, Math.min(1, (1.1 * A - 0.15) * fleck));
            farbe[i] = new float[]{schaum, (float) Math.max(0, Math.min(1, 0.35 * A * fleck))};
        }
        float[] out = new float[(N - 1) * 2 * 4 * 3 * 9];
        int o = 0;
        float[] schaumF = {0.66f, 0.70f, 0.70f}, wasserF = {0.28f, 0.38f, 0.36f};
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i + 1 < N; i++) {
                for (int ring = 0; ring < 2; ring++) {
                    float[] a0 = st[i][ring], a1 = st[i][ring + 1], b0 = st[i + 1][ring], b1 = st[i + 1][ring + 1];
                    float fa = ring == 0 ? farbe[i][0] : farbe[i][1], fb = ring == 0 ? farbe[i + 1][0] : farbe[i + 1][1];
                    float fa1 = ring == 0 ? farbe[i][1] : 0, fb1 = ring == 0 ? farbe[i + 1][1] : 0;
                    // Normale aus der Querrichtung (nach außen ansteigend oder fallend) [G], nach oben gerichtet
                    double dy = a1[1] - a0[1], dq = a1[2] - a0[2], ln = Math.hypot(dy, dq);
                    float nx = 0, ny = (float) (dq / ln), nq = (float) (-dy / ln * side);
                    if (ny < 0) { ny = -ny; nq = -nq; }
                    o = ecke(out, o, a0, side, nx, ny, nq, mix(wasserF, schaumF, fa));
                    o = ecke(out, o, b0, side, nx, ny, nq, mix(wasserF, schaumF, fb));
                    o = ecke(out, o, b1, side, nx, ny, nq, mix(wasserF, schaumF, fb1));
                    o = ecke(out, o, a0, side, nx, ny, nq, mix(wasserF, schaumF, fa));
                    o = ecke(out, o, b1, side, nx, ny, nq, mix(wasserF, schaumF, fb1));
                    o = ecke(out, o, a1, side, nx, ny, nq, mix(wasserF, schaumF, fa1));
                }
            }
        }
        return out;
    }

    private static float[] mix(float[] a, float[] b, float f) { return new float[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f}; }

    private static int ecke(float[] out, int o, float[] p, int side, float nx, float ny, float nq, float[] c) {
        out[o++] = p[0]; out[o++] = p[1]; out[o++] = p[2] * side;
        out[o++] = nx; out[o++] = ny; out[o++] = nq;
        out[o++] = c[0]; out[o++] = c[1]; out[o++] = c[2];
        return o;
    }

    // ------------------------------------------------------------------ W11b: Kielwasser und Rumpfmasken

    /**
     * W11b: je Bild vor dem Meer: die Quellen des Kielwassers aus den Lagen der fahrenden Boote (Spur nach einem Sprung der Uhr
     * {@link #SPUR_ZURUECK} s zurück aus der Bahn nachgezeichnet), die Kacheln um die Kamera auf der Grafikkarte, die Rumpfmasken
     * aller sichtbaren Boote. dt: Schritt der Szene (0 bei stehender Uhr).
     */
    public synchronized void kielwasser(GL4 g, double t, double dt, double camX, double camZ, SceneClock.Zeitstand z) {
        ruempfe.clear();
        int[] sicht = sichtbare(z);
        if (!kielwasserAn || sicht.length == 0) {
            if (!quellen.isEmpty()) { wake.clear(); quellen.clear(); }
            wakeT = Double.NaN;
            kielQuellen = 0;
            return;
        }
        wake.depth = KIEL_TIEFE;
        wake.foamStrength = SCHAUM_STAERKE;
        wake.quality = Wake.Quality.values()[Math.max(0, Math.min(Wake.Quality.values().length - 1, kielStufe))];
        boolean sprung = Double.isNaN(wakeT) || t < wakeT - 1e-6 || t - wakeT > 3;
        // Quellen der Boote, die nicht mehr zu sehen sind, weg
        java.util.Set<String> da = new java.util.HashSet<>();
        for (int i : sicht) da.add(name(i));
        for (var it = quellen.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!da.contains(e.getKey())) { wake.remove(e.getValue()); it.remove(); sprung = true; }
        }
        for (int i : sicht) {
            double[] lg = lage(i, t);
            Hull h = rumpf(i);
            if (lg == null || h == null) continue;
            ruempfe.add(new HullMask(lg[0], lg[2], lg[3], 0.49 * h.loa, 0.49 * h.beam, 2.5));
            Wake.Source s = quellen.get(name(i));
            if (s == null) {
                s = wake.add(name(i), lg[0], lg[2], lg[3], lg[7], h.loa, h.beam * SCHAUM_BREITE, h.draft, true);
                quellen.put(name(i), s);
                sprung = true;
            }
        }
        if (sprung) {
            for (Wake.Source s : quellen.values()) s.clearTrail();
            for (double tau = t - SPUR_ZURUECK; tau < t; tau += 1) {
                for (int i : sicht) {
                    Wake.Source s = quellen.get(name(i));
                    double[] lg = s == null ? null : lage(i, tau);
                    if (lg != null) wake.moveTo(s, lg[0], lg[2], lg[3], lg[7]);
                }
                wake.step(1);
            }
        }
        for (int i : sicht) {
            Wake.Source s = quellen.get(name(i));
            double[] lg = s == null ? null : lage(i, t);
            if (lg != null) wake.moveTo(s, lg[0], lg[2], lg[3], lg[7]);
        }
        wake.step(Math.max(0, dt));
        wakeT = t;
        kielQuellen = quellen.size();
        if (wakeGl == null) wakeGl = new WakeGl();
        if (!wakeInit) {
            try { wakeGl.init(g, wake.quality); wakeInit = true; } catch (RuntimeException e) { System.err.println("LituyaBay1958: Kielwasser: " + e.getMessage()); kielwasserAn = false; return; }
        }
        wakeGl.update(g, wake, camX, camZ);
    }

    /** W11b: die Textur des Kielwassers (Array RG16F, 0 nah, 1 weit) oder 0. */
    public int kielTextur() { return kielwasserAn && wakeInit && wakeGl != null && kielQuellen > 0 ? wakeGl.texture() : 0; }

    /** W11b: Lage einer Kachel {x0, z0, Kante, an} oder aus. */
    public double[] kielRect(int schicht) { return kielwasserAn && wakeInit && wakeGl != null && kielQuellen > 0 ? wakeGl.rect(schicht) : new double[]{0, 0, 1, 0}; }

    /** W11b: die Rumpfmasken der sichtbaren Boote (nach {@link #kielwasser}). */
    public synchronized List<HullMask> ruempfe() { return new ArrayList<>(ruempfe); }

    /** W11b: „Kielwasser 1 Quelle · 2 × 512² RG16F (256 m, 2048 m)“ oder aus. */
    public String kielInfo() { return kielwasserAn && wakeInit && wakeGl != null ? wakeGl.info() : "Kielwasser aus"; }

    /**
     * W11b (Klang): die fahrenden Boote im Zeitstand z zur Zeit t: je Boot {x, y, z, Fahrt m/s, Maschine 1/0, Bug x, y, z}.
     */
    public synchronized List<double[]> maschinen(double t, SceneClock.Zeitstand z) {
        List<double[]> r = new ArrayList<>();
        for (int i : sichtbare(z)) {
            double[] lg = lage(i, t);
            if (lg == null) continue;
            boolean maschine = ((int) lg[6] & BootBahn.MASCHINE) != 0 || lg[7] >= FAHRT_AB;
            if (!maschine) continue;
            double[] b = bug(i, t);
            r.add(new double[]{lg[0], lg[1], lg[2], lg[7], 1, b[0], b[1], b[2]});
        }
        return r;
    }

    // ------------------------------------------------------------------ heute

    /** Das Boot von heute (ohne GL), beim ersten Mal gebaut. */
    private void heuteBoot() {
        if (heute != null) return;
        heute = Kutter.edrie();
        Troller tr = Troller.edrie(heute.hull);
        tr.name = "KITTIWAKE";
        tr.homeport = "PELICAN, ALASKA";
        tr.sheerStrake = new double[]{0.20, 0.045, 0.03};
        tr.houseTrim = new double[]{0.20, 0.045, 0.03};
        tr.roof = new double[]{0.36, 0.36, 0.33};
        tr.wear = 0.25; tr.rust = 0.2; tr.growth = 0.3; tr.seed = 4;
        craft.prepare(heute.hull, tr);
    }

    /**
     * W11a: die Lage des Boots von heute zur Zeit t (Ereigniszeit) als {x, y, z, Kurs, Stampfen, Rollen, Zustand}: in der
     * Tour aus der Bahn, danach 90 s lang ein Übergang zur Ruhelage, sonst vor Anker (Bug im Wind, Schwoien, Rollen, Stampfen).
     * Stellt das Boot in diese Lage.
     */
    private double[] heuteLage(double t) {
        heuteBoot();
        BootBahn tb = tourAn ? tour.get(KITTIWAKE_NAME) : null;
        double tt = tb == null ? Double.NaN : t - tourVersatz.getOrDefault(KITTIWAKE_NAME, 0.0);
        if (tb != null && tt >= tb.anfang() && tt <= tb.ende()) {
            double[] s = tb.zustand(tt);
            heute.pose(new double[]{s[0], s[1] + 0.02 * Math.sin(2 * Math.PI * t / 3.7), s[2]}, new double[]{s[3], s[4], s[5], s[6]});
            return new double[]{s[0], s[1], s[2], heute.heading(), heute.pitch(), heute.roll(), s[8]};
        }
        // vor Anker: Schwimmlage am Pegel mit dem Bug im Wind (settle), dazu Schwoien, Rollen, Stampfen [G]
        double wind = windVonJetzt;
        if (Double.isNaN(heutePegel) || Math.abs(pegelJetzt - heutePegel) > 0.02 || Math.abs(wind - heuteWind) > 1) {
            if (heuteOrt == null) {
                if (tb != null) { double[] e = tb.zustand(tb.ende()); heuteOrt = new double[]{e[0], e[2]}; }   // W11a: wo die Tour endet
                else heuteOrt = ankerplatz();
            }
            double[] ort = heuteOrt != null ? heuteOrt : HEUTE_ORT;
            heute.settle(com.dan.vessel.Water.still(pegelJetzt), ort[0], ort[1], wind);
            if (heuteOrt == null) heutePegel = Double.NaN;   // Gelände noch nicht geladen: im nächsten Bild noch einmal
            else heutePegel = pegelJetzt;
            heuteWind = wind;
            heuteQ = heute.orientation();
            heuteRuhe = heute.position();
        }
        double[] o = heuteRuhe == null ? heute.position() : heuteRuhe;
        double yaw = Math.toRadians(8 * Math.sin(2 * Math.PI * t / 300.0));
        double roll = Math.toRadians(0.6 * Math.sin(2 * Math.PI * t / 4.2)), pitch = Math.toRadians(0.3 * Math.sin(2 * Math.PI * t / 3.1 + 1.0));
        double[] qy = {Math.cos(yaw / 2), 0, Math.sin(yaw / 2), 0}, qz = {Math.cos(pitch / 2), 0, 0, Math.sin(pitch / 2)}, qx = {Math.cos(roll / 2), Math.sin(roll / 2), 0, 0};
        double[] q = qmul(qy, qmul(heuteQ, qmul(qz, qx)));
        double[] pos = {o[0], o[1] + 0.02 * Math.sin(2 * Math.PI * t / 3.7), o[2]};
        if (tb != null && tt > tb.ende() && tt < tb.ende() + 90) {   // W11a: nach der Tour weich in die Ruhelage
            double[] e = tb.zustand(tb.ende());
            double[] v = verketten(e, new double[]{pos[0], pos[1], pos[2], q[0], q[1], q[2], q[3], 0, 0, -1}, (tt - tb.ende()) / 90);
            pos = new double[]{v[0], v[1], v[2]};
            q = new double[]{v[3], v[4], v[5], v[6]};
        }
        heute.pose(pos, q);
        return new double[]{pos[0], pos[1], pos[2], heute.heading(), heute.pitch(), heute.roll(), 0};
    }

    private double[] heuteRuhe;

    /** W8b: Lage des Boots von heute zur Zeit t als Modellmatrix (W11a: in der Tour aus der Bahn). */
    private float[] heutePose(double t) {
        heuteLage(t);
        heuteModell = heute.model();
        return heuteModell;
    }

    /**
     * W8b Runde 2: In W8b Runde 1 lag der Ort (−4 440, 2 470) heute zu flach – das Boot schwebte über dem Strand (808).
     * Jetzt der nächste Punkt um ihn (Spirale, 5-m-Schritte, bis 600 m), an dem das Wasser bei Niedrigwasser (Pegel − 2 m)
     * mindestens 6 m tief ist und 25 m ringsum keine Untiefe liegt.
     */
    private double[] ankerplatz() {
        java.util.function.DoubleBinaryOperator g = grund;
        if (g == null || g.applyAsDouble(HEUTE_ORT[0], HEUTE_ORT[1]) <= Renderer.KEIN_GRUND + 1) return null;   // noch kein Gelände
        double tief = pegelJetzt - 2 - 6;
        for (int r = 0; r <= 600; r += 5)
            for (int k = 0, n = Math.max(1, (int) (2 * Math.PI * r / 5)); k < n; k++) {
                double a = 2 * Math.PI * k / n, x = HEUTE_ORT[0] + r * Math.cos(a), z = HEUTE_ORT[1] + r * Math.sin(a);
                boolean ok = true;
                for (int j = 0; j < 9 && ok; j++) {
                    double b = 2 * Math.PI * j / 8, rr = j == 8 ? 0 : 25;
                    ok = g.applyAsDouble(x + rr * Math.cos(b), z + rr * Math.sin(b)) < tief;
                }
                if (ok) return new double[]{x, z};
            }
        return HEUTE_ORT.clone();
    }

    /** Wo das Boot von heute liegt (nach der Suche), für Proben und HUD. */
    public double[] heuteOrt() { return heuteOrt == null ? HEUTE_ORT.clone() : heuteOrt.clone(); }

    private static double[] qmul(double[] a, double[] b) {
        return new double[]{a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3], a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
                a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1], a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0]};
    }

    private CraftGl.View craftBlick(float[] vp, double[] eye, boolean reversed, double pegel) {
        CraftGl.View v = new CraftGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        v.reversedZ = reversed;
        v.tanHalfFovY = tanHalb;
        v.viewportHeight = bildHoehe;
        if (!Double.isNaN(pegel)) { v.clip = new double[]{0, 1, 0, -pegel}; v.updateShadow = false; }
        return v;
    }

    /** W8a: Licht wie der Wald (Sonne, Himmel, Dunst), dazu Boden der Halbkugel (dunkles Wasser), Rundumbild, Ankerlicht nach dem Sonnenstand. */
    private CraftGl.Light craftLicht(double[] sun) {
        com.dan.forest.gl.ForestGl.Light f = WaldGl.licht(sun);
        CraftGl.Light l = new CraftGl.Light();
        l.sunDir = f.sunDir; l.sunColor = f.sunColor; l.skyColor = f.skyColor; l.fogColor = f.fogColor; l.fogDistance = f.fogDistance;
        l.groundColor = new double[]{f.skyColor[0] * 0.12, f.skyColor[1] * 0.13, f.skyColor[2] * 0.14};   // das Wasser unter dem Boot [G]
        l.panorama = pano;
        // Ankerlicht von Sonnenuntergang bis Sonnenaufgang (Kollisionsverhütungsregeln); Lichthof umso stärker, je dunkler [G]
        double h = sun == null ? 1 : sun[1];
        l.lampGlow = Math.max(0, Math.min(1, (0.05 - h) / 0.12));
        return l;
    }

    /** „Edrie schwimmt · Badger Kette gerissen · …“ für das HUD (letztes Bild); W11: mit der Fahrt in kn, wenn ein Boot fährt. */
    public String info() {
        if (bahnen.isEmpty() && lage[KITTIWAKE] == null) return status;
        StringBuilder sb = new StringBuilder("Boote:");
        for (int i = 0; i <= KITTIWAKE; i++) {
            double[] l = lage[i];
            if (l == null) continue;
            int z = (int) l[6];
            sb.append(' ').append(name(i)).append(' ').append(String.format(Locale.GERMAN, "%+.0f m", l[1]))
                    .append((z & BootBahn.GESUNKEN) != 0 ? " gesunken" : (z & BootBahn.GEKENTERT) != 0 ? " gekentert" : (z & BootBahn.AUF_GRUND) != 0 ? " auf Grund" : "")
                    .append((z & BootBahn.GERISSEN) != 0 ? ", Kette los" : "")
                    .append(l.length > 7 && l[7] >= FAHRT_AB ? String.format(Locale.GERMAN, ", %.1f kn", l[7] / 0.514444) : "")
                    .append(" ·");
        }
        if (kielQuellen > 0) sb.append(' ').append(kielInfo());
        else if (sb.toString().endsWith(" ·")) sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    static VesselGl.Light licht(double[] sun) {
        com.dan.forest.gl.ForestGl.Light f = WaldGl.licht(sun);
        VesselGl.Light l = new VesselGl.Light();
        l.sunDir = f.sunDir; l.sunColor = f.sunColor; l.skyColor = f.skyColor; l.fogColor = f.fogColor; l.fogDistance = f.fogDistance;
        return l;
    }

    public void dispose(GL4 g) {
        if (init) { gl.dispose(g); craft.dispose(g); }
        if (wakeInit && wakeGl != null) wakeGl.dispose(g);
        init = false;
        wakeInit = false;
    }
}
