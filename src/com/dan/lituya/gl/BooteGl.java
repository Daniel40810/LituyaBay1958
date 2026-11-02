package com.dan.lituya.gl;

import com.dan.lituya.scene.BootBahn;
import com.dan.lituya.scene.SceneClock;
import com.dan.vessel.Boat;
import com.dan.vessel.Kutter;
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
 */
public final class BooteGl {
    public static final String[] NAMEN = {"Edrie", "Badger", "Sunmore"};
    public volatile boolean an = true;
    private final VesselGl gl = new VesselGl();
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
                boote.add(switch (n) { case "Edrie" -> Kutter.edrie(); case "Badger" -> Kutter.badger(); default -> Kutter.sunmore(); });
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
        if (!an || bahnen.isEmpty() || !sichtbar(z)) return;
        if (!init) { gl.init(g); init = true; }
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
            gl.draw(g, b, v, l);
            double[][] kn = bb.kette(t);
            if (kn != null) gl.draw(g, kn, (int) s[9], v, l);
            for (int i = 0; i < NAMEN.length; i++) if (NAMEN[i].equals(bb.name)) lage[i] = new double[]{s[0], s[1], s[2], b.heading(), b.pitch(), b.roll(), s[8]};
        }
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glUseProgram(0);
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

    public void dispose(GL4 g) { if (init) gl.dispose(g); init = false; }
}
