package com.dan.lituya.gl;

import com.dan.lituya.scene.BootBahn;
import com.dan.lituya.scene.SceneClock;
import com.dan.vessel.Boat;
import com.dan.vessel.Kutter;
import com.dan.vessel.Troller;
import com.dan.vessel.gl.CraftGl;
import com.dan.vessel.gl.VesselGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 */
public final class BooteGl {
    public static final String[] NAMEN = {"Edrie", "Badger", "Sunmore"};
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
    /** Zuletzt gezeichneter Zustand je Boot (für Mitfahrt und HUD): {x, y, z, Kurs, Stampfen, Rollen, Zustand}. */
    private final double[][] lage = new double[NAMEN.length][];

    /** Lädt die Bahnen des Laufs (ohne GL; leise, wenn es keine gibt). */
    public synchronized void laden(File daten, String lauf) {
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

    public String status() { return status; }

    public int anzahl() { return bahnen.size(); }

    /** Sichtbar im Zeitstand? */
    public static boolean sichtbar(SceneClock.Zeitstand z) { return z == SceneClock.Zeitstand.VOR || z == SceneClock.Zeitstand.WELLE; }

    /** Lage des Boots i zur Zeit t: {x, y, z, Kurs, Stampfen, Rollen, Zustand} oder null. */
    public synchronized double[] lage(int i, double t) {
        int k = index(i);
        if (k < 0) return null;
        Boat b = boote.get(k);
        double[] s = bahnen.get(k).zustand(t);
        b.pose(new double[]{s[0], s[1], s[2]}, new double[]{s[3], s[4], s[5], s[6]});
        return new double[]{s[0], s[1], s[2], b.heading(), b.pitch(), b.roll(), s[8]};
    }

    /** Auge am Steuerhaus des Boots i (vor seinem Fenster, Welt) mit Kurs, Stampfen, Rollen: {x, y, z, Kurs, Stampfen, Rollen} oder null. */
    public synchronized double[] steuerhaus(int i, double t) {
        int k = index(i);
        if (k < 0) return null;
        Boat b = boote.get(k);
        lage(i, t);
        // vor dem Fensterband des Steuerhauses (das Netz ist innen nicht ausgebaut), Augenhöhe am Fenster
        var h = b.hull;
        double[] e = b.toWorld(h.houseS1 + 0.35, h.deck(0.5 * (h.houseS0 + h.houseS1)) + 0.76 * h.houseHeight, 0);
        return new double[]{e[0], e[1], e[2], b.heading(), b.pitch(), b.roll()};
    }

    private int index(int i) {
        for (int k = 0; k < bahnen.size(); k++) if (bahnen.get(k).name.equals(NAMEN[i])) return k;
        return -1;
    }

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
            double[] s = bb.zustand(t);
            b.pose(new double[]{s[0], s[1], s[2]}, new double[]{s[3], s[4], s[5], s[6]});
            float[] mdl = b.model();
            boolean neu = !alterKutter && craft.drawOpaque(g, b.hull, mdl, craftBlick(vp, eye, reversed, pegel), craftLicht(sun));
            if (!neu && !spiegel) gl.draw(g, b, v, l);
            if (neu && !spiegel) modelle.add(new Object[]{b.hull, mdl});
            double[][] kn = bb.kette(t);
            if (kn != null && !spiegel) gl.draw(g, kn, (int) s[9], v, l);
            for (int i = 0; i < NAMEN.length; i++) if (NAMEN[i].equals(bb.name)) lage[i] = new double[]{s[0], s[1], s[2], b.heading(), b.pitch(), b.roll(), s[8]};
        }
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glUseProgram(0);
    }

    /**
     * W8a: Glas, Drähte und Lichthöfe der Boote nach allem Deckenden (Wasser, Gelände, Wald); pegel wie bei
     * {@link #zeichnen(GL4, float[], double[], boolean, double[], double, SceneClock.Zeitstand, double)}.
     */
    public synchronized void zeichnenDurch(GL4 g, float[] vp, double[] eye, boolean reversed, double[] sun, double t, SceneClock.Zeitstand z, double pegel) {
        boolean spiegel = !Double.isNaN(pegel);
        CraftGl.View cv = craftBlick(vp, eye, reversed, pegel);
        CraftGl.Light cl = craftLicht(sun);
        if (an && z == SceneClock.Zeitstand.HEUTE && heuteAn && !alterKutter && init && heute != null) {
            craft.drawTransparent(g, heute.hull, spiegel || heuteModell == null ? heutePose(t) : heuteModell, cv, cl);
            g.glDisable(GL.GL_DEPTH_TEST);
            g.glUseProgram(0);
            return;
        }
        if (!an || alterKutter || bahnen.isEmpty() || !sichtbar(z) || !init) return;
        if (spiegel) {
            for (int k = 0; k < boote.size(); k++) {
                Boat b = boote.get(k);
                double[] s = bahnen.get(k).zustand(t);
                b.pose(new double[]{s[0], s[1], s[2]}, new double[]{s[3], s[4], s[5], s[6]});
                craft.drawTransparent(g, b.hull, b.model(), cv, cl);
            }
        } else {
            for (Object[] m : modelle) craft.drawTransparent(g, m[0], (float[]) m[1], cv, cl);
        }
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glUseProgram(0);
    }

    /** W8b: Lage des Boots von heute zur Zeit t: Schwimmlage am Pegel, Bug im Wind, Schwoien ±8° (5 min), Rollen und Stampfen [G]. */
    private float[] heutePose(double t) {
        if (heute == null) {
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
        double wind = windVonJetzt;
        if (Double.isNaN(heutePegel) || Math.abs(pegelJetzt - heutePegel) > 0.02 || Math.abs(wind - heuteWind) > 1) {
            if (heuteOrt == null) heuteOrt = ankerplatz();
            double[] ort = heuteOrt != null ? heuteOrt : HEUTE_ORT;
            heute.settle(com.dan.vessel.Water.still(pegelJetzt), ort[0], ort[1], wind);
            if (heuteOrt == null) heutePegel = Double.NaN;   // Gelände noch nicht geladen: im nächsten Bild noch einmal
            heutePegel = pegelJetzt;
            heuteWind = wind;
            heuteQ = heute.orientation();
        }
        double[] o = heute.position();
        // Schwimmlage mit dem Bug im Wind (settle), dazu Schwoien, Rollen, Stampfen [G]
        double yaw = Math.toRadians(8 * Math.sin(2 * Math.PI * t / 300.0));
        double roll = Math.toRadians(0.6 * Math.sin(2 * Math.PI * t / 4.2)), pitch = Math.toRadians(0.3 * Math.sin(2 * Math.PI * t / 3.1 + 1.0));
        double[] qy = {Math.cos(yaw / 2), 0, Math.sin(yaw / 2), 0}, qz = {Math.cos(pitch / 2), 0, 0, Math.sin(pitch / 2)}, qx = {Math.cos(roll / 2), Math.sin(roll / 2), 0, 0};
        double[] q = qmul(qy, qmul(heuteQ, qmul(qz, qx)));
        heute.pose(new double[]{o[0], o[1] + 0.02 * Math.sin(2 * Math.PI * t / 3.7), o[2]}, q);
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

    /** „Edrie schwimmt · Badger Kette gerissen · …“ für das HUD (letztes Bild). */
    public String info() {
        if (bahnen.isEmpty()) return status;
        StringBuilder sb = new StringBuilder("Boote:");
        for (int i = 0; i < NAMEN.length; i++) {
            double[] l = lage[i];
            if (l == null) continue;
            int z = (int) l[6];
            sb.append(' ').append(NAMEN[i]).append(' ').append(String.format(Locale.GERMAN, "%+.0f m", l[1]))
                    .append((z & BootBahn.GESUNKEN) != 0 ? " gesunken" : (z & BootBahn.GEKENTERT) != 0 ? " gekentert" : (z & BootBahn.AUF_GRUND) != 0 ? " auf Grund" : "")
                    .append((z & BootBahn.GERISSEN) != 0 ? ", Kette los" : "").append(i + 1 < NAMEN.length ? " ·" : "");
        }
        return sb.toString();
    }

    static VesselGl.Light licht(double[] sun) {
        com.dan.forest.gl.ForestGl.Light f = WaldGl.licht(sun);
        VesselGl.Light l = new VesselGl.Light();
        l.sunDir = f.sunDir; l.sunColor = f.sunColor; l.skyColor = f.skyColor; l.fogColor = f.fogColor; l.fogDistance = f.fogDistance;
        return l;
    }

    public void dispose(GL4 g) { if (init) { gl.dispose(g); craft.dispose(g); } init = false; }
}
