package com.dan.lituya.tools;

import com.dan.lituya.scene.Boden1958;
import com.dan.lituya.scene.BootBahn;
import com.dan.lituya.scene.Gezeiten;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.SeeNetz;
import com.dan.lituya.scene.Site;
import com.dan.vessel.Boat;
import com.dan.vessel.Kutter;
import com.dan.vessel.Water;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleBinaryOperator;

/**
 * Werkbuch Welle W11a: die Bootstour. Ein Troller fährt mit voller Maschine die Bucht hinauf zum Gilbert Inlet und zurück,
 * mit Autopilot ({@link Boat#steuern}) einer Route entlang, die aus dem Fahrwasser von {@code LB_NET_SEE} kommt
 * ({@link SeeNetz#kuerzesterWeg} zwischen den Stützpunkten, vereinfacht nach Douglas-Peucker, mit Catmull-Rom geglättet).
 * Das Wasser steht still auf dem Pegel der Szene (0 m NAVD88, wie der Lauf {@code kette}), der Grund ist der von 1958
 * ({@link Boden1958#hoehe}) oder der von heute ({@link Boden1958#hoeheHeute}).
 * <ul>
 * <li><b>Edrie</b>, 9. Juli 1958 ab 20:00 PST: belegt ist das Einlaufen mit der letzten Flut (MILLER60, Hochwasser 19:52);
 * die Fahrt hinauf und zurück ist gestaltet [G]. Sie endet am Ankerplatz der Bahn des Laufs {@code kette} (erstes Bild,
 * Bug nach Ost), die um 22:15:20 beginnt; {@code gl.BooteGl} verkettet beide ohne Sprung.</li>
 * <li><b>Kittiwake</b>, heute (9.7.2026) ab 10:00: dieselbe Route vom Ankerplatz von heute aus und zurück [G].</li>
 * </ul>
 * Die Zeiten der Bahn stehen in Sekunden seit einer Basis je Boot ({@code <boot>.basis_ms} in {@code TOUR.txt}; die Edrie
 * hat die Basis des ersten Stoßes, also Ereigniszeit; float32 trägt die Zeit von heute nicht mehr). Ergebnis
 * {@code daten/ereignis/boote/tour/bahn_<boot>.bin} ({@link BootBahn}, ohne Kette) und {@code TOUR.txt}.
 * <p>Aufruf: {@code Bootstour [daten] [nur=edrie|kittiwake]}; {@code ant bootstour}.
 */
public final class Bootstour {
    public static final String ORDNER = "tour", DATEI = "TOUR.txt";
    /** Pegel der Szene (m NAVD88), wie {@code Wiring.pegel} und der Lauf kette. */
    public static final double PEGEL = 0;
    /** Start der Edrie (9.7.1958 20:00 PST) und der Kittiwake (9.7.2026 10:00) in ms seit 1970. */
    public static final long EDRIE_START_MS = ZonedDateTime.of(1958, 7, 9, 20, 0, 0, 0, SceneClock.ZONE).toInstant().toEpochMilli(),
            KITTIWAKE_START_MS = ZonedDateTime.of(2026, 7, 9, 10, 0, 0, 0, SceneClock.ZONE).toInstant().toEpochMilli();
    /** Vorhalt des Autopiloten auf der Route (m), Mindesttiefe eines Stützpunkt-Knotens (m), Schritt der Route (m), Toleranz der Vereinfachung (m). */
    public static final double VORHALT = 45, KNOTEN_TIEFE = 6, SCHRITT = 20, TOLERANZ = 25;
    /** Knoten flacher als so viel (m) nimmt die Route nicht; das Gewicht 1 + 40/(Tiefe + 4) zieht sie ins tiefe Wasser [G]. */
    public static final double ROUTE_TIEFE = 4;
    /** Tiefe, unter der die Route gemeldet wird (m unter dem Pegel). */
    public static final double TIEFE_WARNUNG = 3.0;
    /** Rechenschritt des Boots (s; still auf dem Wasser reichen 10 ms) und Takt der Bilder (jeder zweite Schritt von 1/30 s). */
    public static final double MAX_STEP = 0.01, DT = 1.0 / 30;

    /**
     * Die Stützpunkte der Route (UTM 8 N: Name, E, N) [G]: 1,5 km vor La Chaussee Spit auf See (Ansicht „Eingang“), die
     * Mitte des Tors, nördlich an Cenotaph Island vorbei, in den Gilbert Inlet bis vor die Schwelle (im Gelände der Szene liegt
     * vor dem Rückbau des Inlet der Sander von heute als Land, N 6 505 550 bis 6 505 900, davor Untiefen von 2 m; die
     * Gletscherfront von 1958 steht rund 1,9 km weiter, Werkbuch Welle W3c), vor Mudslide Creek, durch den Südkanal, der Anlauf nordwestlich des
     * Ankerplatzes (die kleine Bucht am Südufer öffnet sich nach Norden; zum Schluss dreht das Boot den Bug nach Ost wie in
     * der Bahn der Welle).
     */
    public static final Object[][] STUETZPUNKTE = {
            {"Eingang", 343_750.0, 6_499_770.0},
            {"Tor", 345_443.0, 6_500_248.0},
            {"Cenotaph Island Nord", 350_200.0, 6_503_750.0},
            {"Gilbert Inlet (Schwelle)", 354_350.0, 6_505_050.0},
            {"Mudslide Creek", 354_600.0, 6_504_150.0},
            {"Südkanal", 350_300.0, 6_501_650.0},
            {"Anlauf", 347_250.0, 6_500_850.0}};

    /** Die Route als dichter Linienzug in der Szene mit Bogenlängen und den Bogenlängen der Stützpunkte. */
    public static final class Route {
        public final double[][] p;      // {x, z}
        public final double[] cum;
        public final double length;
        public final String[] namen;
        public final double[] stationen;
        /** Die Knoten des Netzes, die der Weg nahm (für TOUR.txt). */
        public final int knoten;

        Route(double[][] p, String[] namen, double[][] stuetz, int knoten) {
            this.p = p;
            this.knoten = knoten;
            cum = new double[p.length];
            for (int i = 1; i < p.length; i++) cum[i] = cum[i - 1] + Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
            length = cum[p.length - 1];
            this.namen = namen;
            stationen = new double[stuetz.length];
            double vorher = 0;   // der Reihe nach, damit eine Schleife (Anfang = Ende) den letzten Punkt am Ende findet
            for (int k = 0; k < stuetz.length; k++) { stationen[k] = projektion(stuetz[k][0], stuetz[k][1], vorher, length); vorher = stationen[k]; }
        }

        /** Ort bei Bogenlänge s. */
        public double[] at(double s) {
            s = Math.max(0, Math.min(length, s));
            int lo = 0, hi = cum.length - 1;
            while (hi - lo > 1) { int mid = (lo + hi) >>> 1; if (cum[mid] <= s) lo = mid; else hi = mid; }
            double f = (s - cum[lo]) / Math.max(1e-9, cum[hi] - cum[lo]);
            return new double[]{p[lo][0] + (p[hi][0] - p[lo][0]) * f, p[lo][1] + (p[hi][1] - p[lo][1]) * f};
        }

        /** Kurs der Route bei s (Grad von Nord). */
        public double kurs(double s) {
            double[] a = at(s - 5), b = at(s + 15);
            return (Math.toDegrees(Math.atan2(b[0] - a[0], -(b[1] - a[1]))) + 360) % 360;
        }

        /** Bogenlänge des Punkts der Route zwischen von und bis, der (x, z) am nächsten liegt. */
        public double projektion(double x, double z, double von, double bis) {
            int i0 = index(von), i1 = Math.min(p.length - 2, index(bis) + 1);
            double best = Double.MAX_VALUE, sBest = von;
            for (int i = i0; i <= i1; i++) {
                double ax = p[i][0], az = p[i][1], bx = p[i + 1][0], bz = p[i + 1][1];
                double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
                double u = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2));
                double px = ax + dx * u, pz = az + dz * u, d = Math.hypot(x - px, z - pz);
                if (d < best) { best = d; sBest = cum[i] + Math.sqrt(l2) * u; }
            }
            return sBest;
        }

        int index(double s) {
            int lo = 0, hi = cum.length - 1;
            s = Math.max(0, Math.min(length, s));
            while (hi - lo > 1) { int mid = (lo + hi) >>> 1; if (cum[mid] <= s) lo = mid; else hi = mid; }
            return lo;
        }
    }

    /**
     * Baut die Route durch die Stützpunkte (Szene x, z; je Punkt {x, z}) aus dem Fahrwasser: je Abschnitt der kürzeste Weg
     * im Netz zwischen den nächsten Knoten mit {@link #KNOTEN_TIEFE} m Wasser, dann vereinfacht und geglättet. Zwischen zwei
     * Stützpunkten ohne Weg im Netz läuft die Route gerade.
     */
    public static Route route(SeeNetz netz, String[] namen, double[][] stuetz, DoubleBinaryOperator grund) {
        List<double[]> roh = new ArrayList<>();
        int knoten = 0;
        // Gewicht je Knoten nach der Tiefe: die des Netzes (1940) und die des Grunds der Szene (am Kopf des Gilbert Inlet liegt
        // heute Sander, wo 1940 Wasser war); flacher als ROUTE_TIEFE gesperrt
        double[] gewicht = new double[netz.n];
        for (int i = 0; i < netz.n; i++) {
            double tiefe = netz.tiefe[i];
            if (grund != null) {
                double g = grund.applyAsDouble(netz.ost[i] - Site.E0, -(netz.nord[i] - Site.N0));
                if (!Double.isNaN(g)) tiefe = Math.min(tiefe, PEGEL - g);
            }
            gewicht[i] = tiefe < ROUTE_TIEFE ? Double.POSITIVE_INFINITY : 1 + 40 / (tiefe + 4);
        }
        for (int k = 0; k + 1 < stuetz.length; k++) {
            double[] a = stuetz[k], b = stuetz[k + 1];
            int ka = netz.knotenBei(a[0] + Site.E0, Site.N0 - a[1], KNOTEN_TIEFE), kb = netz.knotenBei(b[0] + Site.E0, Site.N0 - b[1], KNOTEN_TIEFE);
            int[] weg = ka >= 0 && kb >= 0 ? netz.kuerzesterWeg(ka, kb, gewicht) : null;
            if (roh.isEmpty()) roh.add(a.clone());
            if (weg != null) {
                for (int i = 0; i < weg.length; i++) {
                    double[] q = {netz.ost[weg[i]] - Site.E0, -(netz.nord[weg[i]] - Site.N0)};
                    if (Math.hypot(q[0] - roh.get(roh.size() - 1)[0], q[1] - roh.get(roh.size() - 1)[1]) > 1) roh.add(q);
                }
                knoten += weg.length;
            }
            roh.add(b.clone());
        }
        List<double[]> einfach = douglasPeucker(roh, TOLERANZ);
        // die Stützpunkte selbst bleiben Teil der vereinfachten Folge
        double[][] glatt = catmullRom(einfach, SCHRITT);
        return new Route(glatt, namen, stuetz, knoten);
    }

    /** Douglas-Peucker mit der Toleranz tol (m); erster und letzter Punkt bleiben. */
    static List<double[]> douglasPeucker(List<double[]> p, double tol) {
        if (p.size() < 3) return new ArrayList<>(p);
        boolean[] keep = new boolean[p.size()];
        keep[0] = keep[p.size() - 1] = true;
        dp(p, 0, p.size() - 1, tol, keep);
        List<double[]> r = new ArrayList<>();
        for (int i = 0; i < p.size(); i++) if (keep[i]) r.add(p.get(i));
        return r;
    }

    private static void dp(List<double[]> p, int a, int b, double tol, boolean[] keep) {
        if (b - a < 2) return;
        double ax = p.get(a)[0], az = p.get(a)[1], bx = p.get(b)[0], bz = p.get(b)[1];
        double dx = bx - ax, dz = bz - az, l = Math.hypot(dx, dz);
        int best = -1;
        double dmax = 0;
        for (int i = a + 1; i < b; i++) {
            double x = p.get(i)[0], z = p.get(i)[1];
            double d = l < 1e-9 ? Math.hypot(x - ax, z - az) : Math.abs(dx * (az - z) - (ax - x) * dz) / l;
            if (d > dmax) { dmax = d; best = i; }
        }
        if (best >= 0 && dmax > tol) { keep[best] = true; dp(p, a, best, tol, keep); dp(p, best, b, tol, keep); }
    }

    /** Zentripetale Catmull-Rom-Kurve (α = 0,5) durch die Punkte, alle schritt m ein Punkt; ohne Schleifen an engen Ecken. */
    static double[][] catmullRom(List<double[]> k, double schritt) {
        if (k.size() < 2) return k.toArray(new double[0][]);
        List<double[]> out = new ArrayList<>();
        for (int s = 0; s + 1 < k.size(); s++) {
            double[] p0 = k.get(Math.max(0, s - 1)), p1 = k.get(s), p2 = k.get(s + 1), p3 = k.get(Math.min(k.size() - 1, s + 2));
            if (s == 0) p0 = new double[]{2 * p1[0] - p2[0], 2 * p1[1] - p2[1]};
            if (s + 2 >= k.size()) p3 = new double[]{2 * p2[0] - p1[0], 2 * p2[1] - p1[1]};
            double t0 = 0, t1 = t0 + Math.pow(Math.max(1e-6, Math.hypot(p1[0] - p0[0], p1[1] - p0[1])), 0.5),
                    t2 = t1 + Math.pow(Math.max(1e-6, Math.hypot(p2[0] - p1[0], p2[1] - p1[1])), 0.5),
                    t3 = t2 + Math.pow(Math.max(1e-6, Math.hypot(p3[0] - p2[0], p3[1] - p2[1])), 0.5);
            double L = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
            int n = Math.max(1, (int) Math.ceil(L / schritt));
            for (int q = 0; q < n; q++) {
                double t = t1 + (t2 - t1) * q / n;
                double[] a1 = lerp(p0, p1, (t1 - t) / (t1 - t0), (t - t0) / (t1 - t0)), a2 = lerp(p1, p2, (t2 - t) / (t2 - t1), (t - t1) / (t2 - t1)),
                        a3 = lerp(p2, p3, (t3 - t) / (t3 - t2), (t - t2) / (t3 - t2));
                double[] b1 = lerp(a1, a2, (t2 - t) / (t2 - t0), (t - t0) / (t2 - t0)), b2 = lerp(a2, a3, (t3 - t) / (t3 - t1), (t - t1) / (t3 - t1));
                out.add(lerp(b1, b2, (t2 - t) / (t2 - t1), (t - t1) / (t2 - t1)));
            }
        }
        out.add(k.get(k.size() - 1).clone());
        return out.toArray(new double[0][]);
    }

    private static double[] lerp(double[] a, double[] b, double wa, double wb) { return new double[]{a[0] * wa + b[0] * wb, a[1] * wa + b[1] * wb}; }

    /** Szene (x, z) aus UTM (E, N). */
    static double[] szene(double e, double n) { return new double[]{e - Site.E0, -(n - Site.N0)}; }

    /** Das erste Bild einer Bahn (Lage und Kurs) oder null. */
    static double[] erstesBild(File bahn) {
        try {
            BootBahn b = BootBahn.lesen(bahn, "kette");
            double[] s = b.zustand(b.anfang());
            Boat k = Kutter.edrie();
            k.pose(new double[]{s[0], s[1], s[2]}, new double[]{s[3], s[4], s[5], s[6]});
            return new double[]{s[0], s[2], k.heading()};
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Der Ankerplatz von heute wie {@code gl.BooteGl.ankerplatz}: um (−4 440, 2 470) der nächste Punkt, an dem das Wasser bei
     * Niedrigwasser (Pegel − 2 m) mindestens 6 m tief ist und 25 m ringsum keine Untiefe liegt.
     */
    static double[] ankerplatzHeute(DoubleBinaryOperator grund) {
        double[] o = {-4440, 2470};
        double tief = PEGEL - 2 - 6;
        for (int r = 0; r <= 600; r += 5)
            for (int k = 0, n = Math.max(1, (int) (2 * Math.PI * r / 5)); k < n; k++) {
                double a = 2 * Math.PI * k / n, x = o[0] + r * Math.cos(a), z = o[1] + r * Math.sin(a);
                boolean ok = true;
                for (int j = 0; j < 9 && ok; j++) {
                    double b = 2 * Math.PI * j / 8, rr = j == 8 ? 0 : 25;
                    ok = grund.applyAsDouble(x + rr * Math.cos(b), z + rr * Math.sin(b)) < tief;
                }
                if (ok) return new double[]{x, z};
            }
        return o;
    }

    /** Ergebnis einer Fahrt. */
    record Fahrt(String name, long basisMs, double t0, double t1, double laenge, double[] stationen, double tiefeMin, double[] tiefeMinOrt,
                 int flach, double[] ende, double fahrtMax, int bilder, boolean festgefahren) { }

    /** Fährt ein Boot die Route ab und schreibt die Bahn; Zeiten in s seit basisMs; t0 = (startMs − basisMs)/1000. */
    static Fahrt fahren(String name, Route r, DoubleBinaryOperator grund, long basisMs, long startMs, double endKurs, File ziel, boolean laut) throws IOException {
        Boat b = Kutter.edrie();
        b.maxStep = MAX_STEP;
        Water w = new Water() {
            public double height(double x, double z) { return PEGEL; }
            public double[] velocity(double x, double z) { return new double[2]; }
            public double bed(double x, double z) { double g = grund.applyAsDouble(x, z); return Double.isNaN(g) ? -1e4 : g; }
        };
        double[] p0 = r.at(0);
        b.settle(w, p0[0], p0[1], r.kurs(0));
        BootBahn.Schreiber s = new BootBahn.Schreiber(0);
        double t0 = (startMs - basisMs) / 1000.0, t = t0, sProj = 0, tiefeMin = 1e9, fahrtMax = 0, still = 0;
        double[] tiefeOrt = {0, 0};
        double[] stationen = new double[r.stationen.length];
        java.util.Arrays.fill(stationen, Double.NaN);
        int flach = 0, schritt = 0, bilder = 0;
        boolean ankern = false, fest = false;
        double sVor120 = -1e9;
        long w0 = System.nanoTime();
        for (; t - t0 < 4 * 3600; t += DT, schritt++) {
            double[] o = b.position();
            sProj = r.projektion(o[0], o[2], Math.max(0, sProj - 40), Math.min(r.length, sProj + 250));
            double rest = r.length - sProj, zielAbstand = Math.hypot(o[0] - r.at(r.length)[0], o[2] - r.at(r.length)[1]);
            if (!ankern && sProj > 0.5 * r.length && (rest < 15 || zielAbstand < 12)) ankern = true;
            boolean steht = false;
            if (schritt % 3600 == 0) { steht = schritt > 0 && !ankern && sProj - sVor120 < 5; sVor120 = sProj; }   // alle 120 s: kam es voran?
            if ((b.grounded() && !ankern) || steht) {   // festgefahren: abbrechen und melden (beim Ankern darf der Kiel den Grund berühren)
                System.out.printf(Locale.GERMAN, "  %s FESTGEFAHREN bei %s, km %.1f, E %.0f N %.0f (Grund %.1f m)%n", name, uhr(basisMs, t), sProj / 1000, o[0] + Site.E0, Site.N0 - o[2], grund.applyAsDouble(o[0], o[2]));
                fest = true;
                break;
            }
            if (ankern) {
                b.throttle = 0;
                // ausdrehen zum Endkurs (die Bahn der Welle beginnt mit dem Bug nach Ost)
                double d = Double.isNaN(endKurs) ? 0 : ((endKurs - b.heading()) + 540) % 360 - 180;
                b.rudder = Math.max(-1, Math.min(1, d / 30));
                double v = Math.hypot(b.velocity()[0], b.velocity()[2]);
                if (v < 0.12) still += DT; else still = 0;
                if (still > 8) break;
            } else {
                double[] zt = r.at(Math.min(r.length, sProj + VORHALT));
                b.steuern(zt[0], zt[1]);
                b.throttle = rest > 400 ? 1 : rest > 150 ? 0.45 : rest > 50 ? 0.2 : 0;   // ausgleiten: ein Troller läuft noch gut 50 m aus [G]
            }
            b.step(w, DT);
            double[] p = b.position(), v = b.velocity();
            double vh = Math.hypot(v[0], v[2]);
            fahrtMax = Math.max(fahrtMax, vh);
            double tiefe = PEGEL - w.bed(p[0], p[2]);
            if (tiefe < tiefeMin) { tiefeMin = tiefe; tiefeOrt = new double[]{p[0] + Site.E0, Site.N0 - p[2]}; }
            if (tiefe < TIEFE_WARNUNG) flach++;
            for (int k = 0; k < stationen.length; k++) if (Double.isNaN(stationen[k]) && sProj >= r.stationen[k] - 1) stationen[k] = t;
            if (schritt % 2 == 0) {
                int z = b.throttle > 0 ? BootBahn.MASCHINE : 0;
                if (b.grounded()) z |= BootBahn.AUF_GRUND;
                s.bild(t, p, b.orientation(), 0, z, -1, null);
                bilder++;
            }
            if (laut && schritt % 9000 == 0)
                System.out.printf(Locale.GERMAN, "  %s  %s  %5.1f km von %.1f, %.1f kn, Tiefe %.0f m  (%.0f s Rechnung)%n", name, uhr(basisMs, t), sProj / 1000, r.length / 1000, vh / 0.514444, tiefe, (System.nanoTime() - w0) / 1e9);
        }
        s.schreiben(ziel);
        double[] e = b.position();
        return new Fahrt(name, basisMs, t0, t, r.length, stationen, tiefeMin, tiefeOrt, flach, new double[]{e[0] + Site.E0, Site.N0 - e[2], b.heading()}, fahrtMax, bilder, fest);
    }

    static String uhr(long basisMs, double t) {
        return java.time.Instant.ofEpochMilli(basisMs + (long) (t * 1000)).atZone(SceneClock.ZONE).toLocalTime().withNano(0).toString();
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        File daten = new File(a.length > 0 ? a[0] : "daten");
        String nur = a.length > 1 ? a[1].toLowerCase(Locale.ROOT).replace("nur=", "") : "";
        long t0 = System.nanoTime();
        if (!SeeNetz.da(daten)) throw new IOException("daten/netz/see fehlt: ant aufbereitung -Dschritte=see");
        SeeNetz netz = SeeNetz.lesen(daten);
        Boden1958 boden = new Boden1958(daten);
        File out = new File(new File(new File(daten, "ereignis"), "boote"), ORDNER);
        File kette = new File(new File(new File(new File(daten, "ereignis"), "boote"), "kette"), "bahn_edrie.bin");
        double[] anker = erstesBild(kette);
        if (anker == null) throw new IOException(kette + " fehlt: ant boote1958 (die Tour endet, wo die Bahn der Welle beginnt)");
        System.out.printf("Bootstour über %s; Ankerplatz der Edrie (Bahn kette, erstes Bild) E %.1f N %.1f, Kurs %.0f°; Pegel %+.2f m NAVD88%n",
                netz, anker[0] + Site.E0, Site.N0 - anker[1], anker[2], PEGEL);
        System.out.printf("Gezeit 9.7.1958 20:00: %s%n", new Gezeiten().zeile(EDRIE_START_MS));
        DoubleBinaryOperator grund1958 = (x, z) -> boden.hoehe(x + Site.E0, Site.N0 - z), grundHeute = (x, z) -> boden.hoeheHeute(x + Site.E0, Site.N0 - z);
        List<String> txt = new ArrayList<>();
        txt.add("# Bootstour (LituyaBay1958 Werkbuch Welle W11a), com.dan.lituya.tools.Bootstour; Bahnen: com.dan.lituya.scene.BootBahn (ohne Kette)");
        txt.add("# Zeiten der Bahn in s seit <boot>.basis_ms (Edrie: der erste Stoß, also Ereigniszeit; Kittiwake: 9.7.2026 10:00)");
        txt.add(String.format(Locale.ROOT, "pegel_m=%.2f", PEGEL));
        txt.add(String.format(Locale.ROOT, "fahrt=volle Maschine (%.1f kN), Vorhalt %.0f m, Route aus LB_NET_SEE (Knoten ab %.0f m Tiefe), vereinfacht %.0f m, Catmull-Rom %.0f m", Kutter.edrie().maxThrust / 1000, VORHALT, KNOTEN_TIEFE, TOLERANZ, SCHRITT));
        txt.add("gezeit_2000=" + new Gezeiten().zeile(EDRIE_START_MS));
        List<String> boote = new ArrayList<>();
        // Edrie 1958: von See durch das Tor, hinauf, zurück, zum Ankerplatz der Bahn kette
        if (nur.isEmpty() || nur.equals("edrie")) {
            String[] namen = new String[STUETZPUNKTE.length + 1];
            double[][] st = new double[STUETZPUNKTE.length + 1][];
            for (int i = 0; i < STUETZPUNKTE.length; i++) { namen[i] = (String) STUETZPUNKTE[i][0]; st[i] = szene((Double) STUETZPUNKTE[i][1], (Double) STUETZPUNKTE[i][2]); }
            namen[STUETZPUNKTE.length] = "Ankerplatz";
            st[STUETZPUNKTE.length] = new double[]{anker[0], anker[1]};
            Route r = route(netz, namen, st, grund1958);
            System.out.printf("%nEdrie: Route %.1f km über %d Knoten des Netzes, %d Punkte; %s%n", r.length / 1000, r.knoten, r.p.length, routenTiefe(r, grund1958));
            Fahrt f = fahren("Edrie", r, grund1958, SceneClock.BEBEN.toEpochMilli(), EDRIE_START_MS, anker[2], new File(out, "bahn_edrie.bin"), true);
            bericht(txt, f, r, boote);
        }
        // Kittiwake heute: vom Ankerplatz von heute nach Norden um Cenotaph Island, hinauf, zurück
        if (nur.isEmpty() || nur.equals("kittiwake")) {
            double[] ah = ankerplatzHeute(grundHeute);
            List<String> namen = new ArrayList<>();
            List<double[]> st = new ArrayList<>();
            namen.add("Ankerplatz heute"); st.add(ah.clone());
            for (int i = 2; i < STUETZPUNKTE.length; i++) { namen.add((String) STUETZPUNKTE[i][0]); st.add(szene((Double) STUETZPUNKTE[i][1], (Double) STUETZPUNKTE[i][2])); }
            namen.add("Ankerplatz heute"); st.add(ah.clone());
            Route r = route(netz, namen.toArray(new String[0]), st.toArray(new double[0][]), grundHeute);
            System.out.printf("%nKittiwake: Ankerplatz heute E %.1f N %.1f; Route %.1f km über %d Knoten, %d Punkte; %s%n", ah[0] + Site.E0, Site.N0 - ah[1], r.length / 1000, r.knoten, r.p.length, routenTiefe(r, grundHeute));
            Fahrt f = fahren("Kittiwake", r, grundHeute, KITTIWAKE_START_MS, KITTIWAKE_START_MS, Double.NaN, new File(out, "bahn_kittiwake.bin"), true);
            bericht(txt, f, r, boote);
        }
        txt.add(2, "boote=" + String.join(" ", boote));
        out.mkdirs();
        Files.write(new File(out, DATEI).toPath(), txt, StandardCharsets.UTF_8);
        System.out.printf("%nnach %s (%.0f s)%n", out, (System.nanoTime() - t0) / 1e9);
    }

    /** Die flachste Stelle der Route selbst (Pegel − Grund an jedem Punkt): „Route mindestens 5,2 m tief (E … N …), 0 Punkte unter 3,0 m“. */
    static String routenTiefe(Route r, DoubleBinaryOperator grund) {
        double min = 1e9, me = 0, mn = 0;
        int flach = 0;
        for (double[] q : r.p) {
            double g = grund.applyAsDouble(q[0], q[1]);
            double tiefe = PEGEL - (Double.isNaN(g) ? -1e4 : g);
            if (tiefe < min) { min = tiefe; me = q[0] + Site.E0; mn = Site.N0 - q[1]; }
            if (tiefe < TIEFE_WARNUNG) flach++;
        }
        return String.format(Locale.GERMAN, "Route mindestens %.1f m tief (E %.0f N %.0f), %d Punkte flacher als %.1f m", min, me, mn, flach, TIEFE_WARNUNG);
    }

    static void bericht(List<String> txt, Fahrt f, Route r, List<String> boote) {
        String k = f.name().toLowerCase(Locale.ROOT);
        boote.add(f.name());
        txt.add(String.format(Locale.ROOT, "%s.basis_ms=%d", k, f.basisMs()));
        txt.add(String.format(Locale.ROOT, "%s.zeit=%.1f %.1f", k, f.t0(), f.t1()));
        txt.add(String.format(Locale.ROOT, "%s.uhr=%s %s", k, uhr(f.basisMs(), f.t0()), uhr(f.basisMs(), f.t1())));
        txt.add(String.format(Locale.ROOT, "%s.laenge_m=%.0f", k, f.laenge()));
        txt.add(String.format(Locale.ROOT, "%s.dauer_s=%.0f", k, f.t1() - f.t0()));
        txt.add(String.format(Locale.ROOT, "%s.fahrt_max_kn=%.1f", k, f.fahrtMax() / 0.514444));
        txt.add(String.format(Locale.ROOT, "%s.tiefe_min_m=%.1f bei E %.0f N %.0f, %d Bilder flacher als %.1f m", k, f.tiefeMin(), f.tiefeMinOrt()[0], f.tiefeMinOrt()[1], f.flach(), TIEFE_WARNUNG));
        txt.add(String.format(Locale.ROOT, "%s.ende=%.1f %.1f Kurs %.0f", k, f.ende()[0], f.ende()[1], f.ende()[2]));
        txt.add(String.format(Locale.ROOT, "%s.bilder=%d", k, f.bilder()));
        txt.add(String.format(Locale.ROOT, "%s.festgefahren=%s", k, f.festgefahren()));
        System.out.printf("%-10s %s bis %s, %.1f km in %.0f min, bis %.1f kn, Tiefe mindestens %.1f m (E %.0f N %.0f), %d Bilder flacher als %.1f m; Ende E %.1f N %.1f, Kurs %.0f°%n",
                f.name(), uhr(f.basisMs(), f.t0()), uhr(f.basisMs(), f.t1()), f.laenge() / 1000, (f.t1() - f.t0()) / 60, f.fahrtMax() / 0.514444, f.tiefeMin(), f.tiefeMinOrt()[0], f.tiefeMinOrt()[1], f.flach(), TIEFE_WARNUNG,
                f.ende()[0], f.ende()[1], f.ende()[2]);
        for (int i = 0; i < r.namen.length; i++) {
            double ts = f.stationen()[i];
            txt.add(String.format(Locale.ROOT, "%s.station.%s=%s %.1f km", k, r.namen[i].replace(' ', '_'), Double.isNaN(ts) ? "" : String.format(Locale.ROOT, "%.1f", ts), r.stationen[i] / 1000));
            System.out.printf("   %-22s km %5.1f  %s%n", r.namen[i], r.stationen[i] / 1000, Double.isNaN(ts) ? "nicht erreicht" : uhr(f.basisMs(), ts) + String.format(Locale.GERMAN, " (t = %.0f s)", ts));
        }
    }
}
