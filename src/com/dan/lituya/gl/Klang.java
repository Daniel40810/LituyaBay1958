package com.dan.lituya.gl;

import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.WellenNetz;
import com.dan.lituya.tools.Ereignis;
import com.dan.soundscape.AudioOut;
import com.dan.soundscape.Soundscape;
import com.dan.soundscape.Soundscape.Kind;
import com.dan.soundscape.Torrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.function.DoubleBinaryOperator;

/**
 * Phase 18: der Klang der Szene (Werkbuch 6.7, 9.13) mit {@code com.dan.soundscape}. Ohr = Kamera. Die Ereignisse hängen
 * an der Ereigniszeit der Uhr ({@link SceneClock#simSeconds}) und gehen los, wenn sie vorwärts über ihren Zeitpunkt
 * läuft (in Zeitlupe und Echtzeit, nicht im schnellen Zeitraffer); die Laufzeit zum Ohr (rund 340 m/s) rechnet
 * {@code soundscape}. Alle Pegel und Gestalten nach Gehör [G].
 * <ul>
 * <li><b>Beben</b> (ab 0 s, rund 60 s): das Grollen als rollender Donner aus Stücken entlang der Fairweather-Störung
 * (die tiefen Frequenzen stehen für den Infraschall, hörbar gemacht), dazu tiefes Ächzen, Steinschlag an den Hängen und
 * Knacken im Wald um das Ohr, solange es bebt.</li>
 * <li><b>Sturz</b> (ab 93 s): der Fels rutscht die Wand hinab (Kanal der Kalbung, 30,6 Mio. m³) und schlägt ins Gilbert
 * Inlet (Aufschlag).</li>
 * <li><b>Welle</b> (ab 105 s): ein Wildwasser an der Front, wo sie dem Ohr am nächsten ist (aus {@code LB_NET_WELLE}),
 * Durchfluss nach dem Abklingen der Welle [G]; erreicht die Front das Ufer beim Ohr, Sturzbrecher, Auflaufen und
 * brechende Bäume.</li>
 * <li><b>Echo an den Wänden der Bucht</b>: jedes laute Ereignis kommt ein zweites und drittes Mal von den nächsten
 * steilen Hängen (Weg über die Wand, −8 und −12 dB) [G].</li>
 * <li><b>Ruhe</b>: Wind und See aus der Verdrahtung, Möwen, ab und zu das Kalben der Gletscher im Gilbert und Crillon
 * Inlet; heute dasselbe.</li>
 * </ul>
 * Grenzen: {@code soundscape} kennt keinen Doppler (die Front klingt nicht höher, wenn sie naht); das Beben ist Luftschall,
 * kein Bodenschall durch die Füße. {@link #feed} läuft im Bildlauf und in {@code tools.Hoerproben} (offline).
 */
public final class Klang {
    public static final double STURZ_S = 93, AUFSCHLAG_S = 100, BEBEN_BIS = 60;
    /** Das Volumen des Sturzes (m³, MILLER60, FRITZ01). */
    public static final double VOLUMEN = 30.6e6;

    public final Soundscape s = new Soundscape();
    private final Renderer r;
    private final Torrent front = new Torrent("Front", 0, 0, 0);
    private final SplittableRandom rng = new SplittableRandom(1958);
    private AudioOut out;
    public volatile boolean on;
    /** Offline (Hörproben, Prüfung): Ereignisse gleich im rufenden Faden. */
    public volatile boolean offline;
    /** Echo an den Wänden der Bucht. */
    public volatile boolean echo = true;
    /** Lautstärke 0 … 1 (das Kino legt seinen Ton darüber). */
    public volatile double lautstaerke = 0.8;
    private double letzteT = Double.NaN, moeweT = 8, kalbT = 120, knackT, steinT, brecherT, achzT;
    private boolean bebenAus, sturzAus, aufschlagAus;
    /** Für HUD und Prüfung: was zuletzt geschah und wie oft. */
    public volatile String note = "";
    public volatile int beben, sturz, brecher, baeume, echos, kalbungen, moewen;
    public volatile double frontAbstand = Double.NaN;
    private double[][] eimer;   // je 2 s Ankunft die Zellen der Front {x, z, …}
    private Object eimerFuer;

    Klang(Renderer r) {
        this.r = r;
        s.seed = 1958;
        s.quality = Soundscape.Quality.MEDIUM;
        s.windCarry = true;
        s.torrents.add(front);
    }

    private final java.util.concurrent.ExecutorService synth = java.util.concurrent.Executors.newSingleThreadExecutor(q -> {
        Thread t = new Thread(q, "LituyaBay1958-Klang");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final java.util.concurrent.atomic.AtomicInteger queued = new java.util.concurrent.atomic.AtomicInteger();

    /** Ein Ereignis erzeugen lassen (eigener Faden; offline gleich). */
    private void run(Runnable job) {
        if (offline) { job.run(); return; }
        if (!on || queued.get() > 32) return;
        queued.incrementAndGet();
        synth.execute(() -> { try { job.run(); } catch (RuntimeException e) { note = "!" + e; } finally { queued.decrementAndGet(); } });
    }

    /** Ein Ereignis, auf Wunsch mit Echo von den Wänden. */
    private void ev(Kind k, double x, double y, double z, double str, double tone, boolean mitEcho) {
        run(() -> s.event(k, x, y, z, str, tone));
        if (mitEcho && echo) for (double[] e : echoQuellen(x, y, z)) { echos++; run(() -> s.event(k, e[0], e[1], e[2], str * e[3], tone)); }
    }

    /** Ton an oder aus (Lautsprecher im eigenen Faden). */
    /** Zählwerk und Ereignisse von vorn (für Hörproben und Prüfung); die nächste Kalbung in kalbIn s. */
    public synchronized void zuruecksetzen(double kalbIn) {
        beben = sturz = brecher = baeume = echos = kalbungen = moewen = 0;
        bebenAus = sturzAus = aufschlagAus = false;
        letzteT = Double.NaN;
        moeweT = 8;
        kalbT = kalbIn;
        frontAbstand = Double.NaN;
        note = "";
    }

    public synchronized void setOn(boolean b) {
        on = b;
        if (b && out == null) {
            try { out = new AudioOut(s); out.start(); } catch (RuntimeException | LinkageError e) { note = "!kein Ton: " + e.getMessage(); out = null; on = false; }
        } else if (!b && out != null) { out.stop(); out = null; }
    }

    public String describe() {
        return (on ? "Klang an" : offline ? "Klang offline" : "Klang aus") + String.format(Locale.GERMAN, " · Beben %d, Sturz %d, Brecher %d, Bäume %d, Echos %d, Kalbungen %d, Möwen %d", beben, sturz, brecher, baeume, echos, kalbungen, moewen)
                + (Double.isNaN(frontAbstand) ? "" : String.format(Locale.GERMAN, " · Front %.1f km", frontAbstand / 1000)) + (note.isEmpty() ? "" : " · " + note);
    }

    // ------------------------------------------------------------------ Orte

    /** Die Wand von 1958 (Szene x, z) und der Weg des Sturzes von 900 m hinab bis ins Wasser {x, y, z}. */
    static double[][] sturzWeg() {
        double[] w = Ereignis.wand();
        double[][] p = new double[12][];
        for (int i = 0; i < p.length; i++) {
            double f = i / (double) (p.length - 1);
            double e = w[0] - Ereignis.RICHTUNG[0] * 900 * (1 - f), n = w[1] - Ereignis.RICHTUNG[1] * 900 * (1 - f);
            double[] xz = Site.fromUtm(e, n);
            p[i] = new double[]{xz[0], 900 * (1 - f) + 5, xz[1]};
        }
        return p;
    }

    /** Die Fairweather-Störung durch das Gilbert Inlet (320° / 140°) als Punkte alle 4 km bis 60 km [G]. */
    static double[][] stoerung() {
        double[] w = Ereignis.wand();
        double a = Math.toRadians(320);
        List<double[]> l = new ArrayList<>();
        for (double d = -60_000; d <= 60_000; d += 4_000) {
            double[] xz = Site.fromUtm(w[0] + 2500 * Math.sin(Math.toRadians(230)) + Math.sin(a) * d, w[1] + 2500 * Math.cos(Math.toRadians(230)) + Math.cos(a) * d);
            l.add(new double[]{xz[0], 0, xz[1]});
        }
        return l.toArray(new double[0][]);
    }

    /** Höhe des Geländes (Szene) oder NaN. */
    private double grund(double x, double z) {
        DoubleBinaryOperator h = hoehe;
        if (h != null) return h.applyAsDouble(x, z);
        return r.stapelHoehe(x, z);
    }

    /** Für die Prüfung und Hörproben: Gelände von außen (null: der Kachelstapel des Renderers). */
    public volatile DoubleBinaryOperator hoehe;

    /**
     * Echoquellen: in acht Richtungen vom Ohr der erste steile Hang (über 25°) bis 4 km; je der nächste mit Weg über die
     * Wand, als gedachte Quelle in der Richtung der Wand im Abstand des ganzen Wegs {x, y, z, Faktor}.
     */
    List<double[]> echoQuellen(double sx, double sy, double sz) {
        double ex = s.earX, ey = s.earY, ez = s.earZ;
        List<double[]> w = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4, dx = Math.sin(a), dz = -Math.cos(a);
            double prev = grund(ex, ez);
            for (double d = 100; d <= 4000; d += 100) {
                double g = grund(ex + dx * d, ez + dz * d);
                if (Double.isNaN(g) || Double.isNaN(prev)) { prev = g; continue; }
                if ((g - prev) / 100 > Math.tan(Math.toRadians(25)) && g > ey - 50) { w.add(new double[]{ex + dx * d, Math.max(g, ey), ez + dz * d, d}); break; }
                prev = g;
            }
        }
        w.sort((p, q) -> Double.compare(p[3], q[3]));
        List<double[]> e = new ArrayList<>();
        double[] f = {0.40, 0.25};   // −8 und −12 dB [G]
        for (int i = 0; i < Math.min(2, w.size()); i++) {
            double[] p = w.get(i);
            double weg = Math.hypot(Math.hypot(sx - p[0], sz - p[2]), sy - p[1]) + p[3];
            double dx = (p[0] - ex) / p[3], dz = (p[2] - ez) / p[3];
            e.add(new double[]{ex + dx * weg, p[1], ez + dz * weg, f[i]});
        }
        return e;
    }

    // ------------------------------------------------------------------ je Bild

    /** Je Bild (dt echte Sekunden): Ohr, Wind, See, dann die Ereignisse, deren Zeit die Uhr gerade überschritten hat. */
    public void feed(double dt) {
        var cam = r.camera;
        s.earX = cam.eye[0]; s.earY = cam.eye[1]; s.earZ = cam.eye[2];
        s.earYawDeg = cam.heading();
        s.windSpeed = r.wiring.windSpeed;
        s.windFromDeg = r.wiring.windFromDeg;
        s.waterLevel = r.wiring.pegel;
        // W7: Regen, Temperatur und Seegang aus der Wetterregie (1958 klar und still: wie bisher)
        com.dan.lituya.wiring.Wetter.Zustand wz = r.wetter.an ? r.wetter.zustand() : null;
        s.temperatureC = wz != null ? wz.temperatur() : 13;
        s.waveHeight = wz != null ? Math.max(0.25, 0.08 * wz.wind() * wz.wind() / 9.81 * 0.3) : 0.25;
        s.wavePeriod = 5;
        s.rainRate = wz != null ? wz.regen() : 0;
        s.inside = 0;
        s.volume = lautstaerke * (r.kino.sichtbar() ? r.kino.ton() : 1);
        SceneClock c = r.clock;
        double t = c.simSeconds();
        double t0 = letzteT;
        letzteT = t;
        boolean vorwaerts = !Double.isNaN(t0) && t > t0 && t - t0 < Math.max(1.0, 4 * dt) + 1e-9;   // kein Sprung, kein schneller Raffer
        boolean welleZeit = c.zeitstand == SceneClock.Zeitstand.WELLE || c.zeitstand == SceneClock.Zeitstand.VOR;
        if (!vorwaerts || !welleZeit || t < -1) { if (t < 0) { bebenAus = sturzAus = aufschlagAus = false; } front.flow = 0; ruhe(dt, c); return; }
        // Beben
        if (t0 < 0 && t >= 0 && !bebenAus) { bebenAus = true; bebenBeginn(); }
        if (t > 0 && t < BEBEN_BIS) bebenSchritt(dt, t);
        // Sturz und Aufschlag
        if (t0 < STURZ_S && t >= STURZ_S && !sturzAus) {
            sturzAus = true;
            sturz++;
            double[][] p = sturzWeg();
            run(() -> s.calving(p, VOLUMEN));
            if (echo) for (double[] e : echoQuellen(p[5][0], p[5][1], p[5][2])) { echos++; run(() -> s.event(Kind.ABBRUCH, e[0], e[1], e[2], e[3], 1e5)); }
        }
        if (t0 < AUFSCHLAG_S && t >= AUFSCHLAG_S && !aufschlagAus) {
            aufschlagAus = true;
            double[][] p = sturzWeg();
            double[] q = p[p.length - 1];
            ev(Kind.AUFSCHLAG, q[0], 2, q[2], 1, VOLUMEN, true);
        }
        // Welle
        welle(dt, t);
    }

    /** Das Grollen: rollender Donner aus Stücken der Störung, nach dem Abstand zum Ohr. */
    private void bebenBeginn() {
        beben++;
        double[][] st = stoerung();
        double ex = s.earX, ez = s.earZ;
        double[] dist = new double[st.length], gew = new double[st.length];
        int best = 0;
        for (int i = 0; i < st.length; i++) {
            dist[i] = Math.max(200, Math.hypot(st[i][0] - ex, st[i][2] - ez));
            gew[i] = 1 / (1 + Math.abs(i - st.length / 2.0) / 6);   // die Mitte (Lituya Bay) am stärksten [G]
            if (dist[i] < dist[best]) best = i;
        }
        double[] q = st[best];
        double pegel = 96 - 12 * Math.log10(Math.max(1, dist[best] / 1000));   // am Ohr [G]
        run(() -> s.thunder(q[0], 20, q[2], pegel, dist, gew, null, true));
        note = String.format(Locale.GERMAN, "Beben, Grollen %.0f dB am Ohr", pegel);
    }

    private void bebenSchritt(double dt, double t) {
        double stark = t < 5 ? t / 5 : t < 40 ? 1 : Math.max(0, (BEBEN_BIS - t) / 20);
        double ex = s.earX, ez = s.earZ;
        if ((achzT -= dt) <= 0) {
            achzT = 1.5 + 2.5 * rng.nextDouble();
            double a = rng.nextDouble() * 2 * Math.PI, d = 300 + 1500 * rng.nextDouble();
            ev(Kind.AECHZEN, ex + Math.sin(a) * d, s.earY - 20, ez + Math.cos(a) * d, 0.6 + 0.4 * stark, 0, false);
        }
        if ((steinT -= dt) <= 0) {
            steinT = (0.8 + 2 * rng.nextDouble()) / Math.max(0.2, stark);
            double a = rng.nextDouble() * 2 * Math.PI, d = 400 + 2500 * rng.nextDouble();
            double x = ex + Math.sin(a) * d, z = ez + Math.cos(a) * d, g = grund(x, z);
            if (!Double.isNaN(g) && g > 30) ev(Kind.ABBRUCH, x, g, z, 0.5 + 0.5 * stark, 20 + 500 * rng.nextDouble(), false);
        }
        if ((knackT -= dt) <= 0) {
            knackT = (0.4 + rng.nextDouble()) / Math.max(0.2, stark);
            double a = rng.nextDouble() * 2 * Math.PI, d = 30 + 250 * rng.nextDouble();
            double x = ex + Math.sin(a) * d, z = ez + Math.cos(a) * d, g = grund(x, z);
            if (!Double.isNaN(g) && g > 2) { baeume++; ev(Kind.KNACKEN, x, g + 15, z, 0.3 + 0.4 * stark, 0, false); }
        }
    }

    /** Die Welle: Wildwasser an der Front beim Ohr, Brecher und Bäume am Ufer, wenn sie ankommt. */
    private void welle(double dt, double t) {
        front.flow = 0;
        frontAbstand = Double.NaN;
        if (t < WellenNetz.START_S || t > WellenNetz.START_S + 900) return;
        double[][] e = eimer();
        if (e == null) return;
        int b = (int) Math.floor((t - WellenNetz.START_S) / 2);
        if (b < 0 || b >= e.length || e[b] == null) return;
        double ex = s.earX, ez = s.earZ, best = Double.POSITIVE_INFINITY, bx = 0, bz = 0;
        double[] c = e[b];
        for (int q = 0; q + 1 < c.length; q += 2) {
            double d = Math.hypot(c[q] - ex, c[q + 1] - ez);
            if (d < best) { best = d; bx = c[q]; bz = c[q + 1]; }
        }
        if (!Double.isFinite(best)) return;
        frontAbstand = best;
        double tau = t - WellenNetz.START_S;
        double h = Math.max(3, 150 * Math.exp(-tau / 120));   // Höhe der Front [G]: 150 m am Einschlag, am Eingang (447 s) um 10 m
        front.x = bx; front.y = r.wiring.pegel + h / 2; front.z = bz;
        front.flow = Math.max(2_000, 4e4 * h);   // m³/s je Front [G]
        if (best < 1500) {
            if ((brecherT -= dt) <= 0) {
                brecherT = 1.2 + 1.2 * rng.nextDouble();
                brecher++;
                ev(Kind.STURZBRECHER, bx, r.wiring.pegel + 1, bz, 1, Math.min(20, h), best < 600);
                ev(Kind.AUFLAUFEN, bx, r.wiring.pegel, bz, Math.min(1, h / 2), 0, false);
            }
            if (best < 900 && (knackT -= dt) <= 0) {
                knackT = 0.25 + 0.4 * rng.nextDouble();
                double a = rng.nextDouble() * 2 * Math.PI, d = 50 * rng.nextDouble();
                double x = bx + Math.sin(a) * d, z = bz + Math.cos(a) * d, g = grund(x, z);
                if (!Double.isNaN(g) && g > 1) { baeume++; ev(Kind.KNACKEN, x, g + 10, z, 0.8, 0, false); }
            }
        }
    }

    /** Die Zellen der Front je 2 s Ankunft (aus LB_NET_WELLE, Kosten heute), einmal je Netz. */
    private double[][] eimer() {
        WellenNetz w = r.netzblick.welle();
        if (w == null) return null;
        if (eimerFuer == w) return eimer;
        float[] f = w.feld(0);
        List<List<Double>> l = new ArrayList<>();
        for (int k = 0; k < 450; k++) l.add(new ArrayList<>());
        for (int j = 0; j < WellenNetz.HOEHE; j += 2)
            for (int i = 0; i < WellenNetz.BREITE; i += 2) {
                float a = f[j * WellenNetz.BREITE + i];
                if (Float.isNaN(a) || a < 0 || a >= 900) continue;
                double[] xz = Site.fromUtm(WellenNetz.ost(i), WellenNetz.nord(j));
                List<Double> b = l.get((int) (a / 2));
                b.add(xz[0]);
                b.add(xz[1]);
            }
        double[][] e = new double[l.size()][];
        for (int k = 0; k < e.length; k++) { e[k] = new double[l.get(k).size()]; for (int q = 0; q < e[k].length; q++) e[k][q] = l.get(k).get(q); }
        eimer = e;
        eimerFuer = w;
        return e;
    }

    /** Ruhe: Möwen über der Bucht, ab und zu ein Gletscher, der kalbt (nicht in der Welle und danach). */
    private void ruhe(double dt, SceneClock c) {
        boolean still = c.zeitstand == SceneClock.Zeitstand.VOR || c.zeitstand == SceneClock.Zeitstand.HEUTE;
        if (!still) return;
        double ex = s.earX, ez = s.earZ;
        if ((moeweT -= dt) <= 0) {
            moeweT = 6 + 14 * rng.nextDouble();
            double a = rng.nextDouble() * 2 * Math.PI, d = 60 + 400 * rng.nextDouble();
            moewen++;
            ev(Kind.MOEWE, ex + Math.sin(a) * d, r.wiring.pegel + 10 + 30 * rng.nextDouble(), ez + Math.cos(a) * d, 0.7 + 0.3 * rng.nextDouble(), 0, false);
        }
        if ((kalbT -= dt) <= 0) {
            kalbT = 150 + 300 * rng.nextDouble();
            // Kalbung an der Front des Lituya- oder des North-Crillon-Gletschers [G]
            boolean li = rng.nextBoolean();
            Site.Ort bucht = li ? Site.GILBERT : Site.CRILLON_INLET, g = li ? Site.LITUYA_GLACIER : Site.NORTH_CRILLON;
            double vol = 2_000 + 30_000 * rng.nextDouble();
            double x = bucht.x() + 0.25 * (g.x() - bucht.x()), z = bucht.z() + 0.25 * (g.z() - bucht.z());   // die Front zwischen Bucht und Gletscher [G]
            double[][] p = {{x, 60, z}, {x + 20, 30, z + 10}, {x + 30, 2, z + 20}};
            kalbungen++;
            run(() -> s.calving(p, vol));
            ev(Kind.AUFSCHLAG, x + 30, 1, z + 20, 1, vol, true);
        }
    }
}
