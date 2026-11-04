package com.dan.lituya.scene;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Das Abspielen der Vorrechnung (Phase 13b, Werkbuch 4.4): liest die Läufe aus {@code daten/ereignis} mit Vorrat. Ein
 * eigener Faden packt die Bilder um die Ereigniszeit aus, erst das Paar um die Zeit, dann einige in Laufrichtung voraus
 * (vorwärts wie rückwärts); der Bildlauf nimmt nur, was schon da ist, und mischt zwei Bilder weich (Zeitlupe bis 1:20).
 * Was noch fehlt, bleibt auf dem letzten Paar stehen, nie wartet der Bildlauf auf die Platte.
 * <ul>
 * <li><b>Welle</b>: der gewählte Lauf ({@code kette} oder {@code messwelle}), je Gitter (16, 4, 2 m) eine Spur. Kacheln
 * zu {@link #KACHEL} Zellen, die nie nass werden ({@code max_g<k>.r32} NaN), fallen weg; ihre höchsten Stände geben die
 * Hülle für das Wegschneiden außerhalb des Blicks.</li>
 * <li><b>Strom</b>: die Mächtigkeit des Felssturzes (8 m, je 0,5 s) über dem Grund von 1958.</li>
 * <li><b>Staub</b>: Oberkante und optische Dicke der Wolke (25 m, je 2 s) über dem Grund von 1958.</li>
 * </ul>
 * Den Grund unter Strom und Staub nimmt die Wiedergabe aus {@code boden_g0.r32} im Ordner des Laufs; fehlt er, rechnet sie
 * ihn einmal aus {@link Boden1958} und legt ihn dort ab.
 */
public final class Wiedergabe implements AutoCloseable {
    public static final String[] WELLEN = {"kette", "messwelle"};
    /** Kantenlänge einer Kachel in Zellen (zum Wegschneiden und für die Stufen der Feinheit). */
    public static final int KACHEL = 64;
    /** So viele Bilder packt der Faden in Laufrichtung voraus. */
    public static final int VORAUS = 3;
    /** So lange (s) bleibt das letzte Bild nach dem Ende eines Laufs stehen, dann ist die Spur aus. */
    public static final double NACHLAUF = 300;
    /** Obergrenze des Vorrats (Bytes). */
    public static final long VORRAT = 160L << 20;

    public enum Art { WELLE, STROM, STAUB_OBEN, STAUB_DICHTE }

    /** Woher eine Spur ihre Bilder hat: aus einer Datei ({@link EreignisDaten.Leser}) oder aus der Echtzeit ({@link LiveLauf}). */
    public interface Bilder { short[] roh(int k, int b) throws IOException; }

    /** Ein Gitter eines Laufs mit seinen Bildzeiten. */
    public static final class Spur {
        public final Art art;
        public final String lauf;
        public final int k;
        public final EreignisDaten.Gitter g;
        /** Ereigniszeiten der Bilder (in der Echtzeit wächst das Feld; ersetzt, nie verändert). */
        public volatile double[] t;
        final Bilder quelle;
        final int id;
        /** Grund (m über NAVD88) je Zelle für Strom und Staub, sonst null. */
        public volatile float[] boden;
        /** Je Kachel der höchste Stand (NaN: nie nass), nur für die Welle. */
        public volatile float[] kachelMax;
        /**
         * Werkbuch Welle W3b, nur für die Welle: je Zelle der höchste Stand (m, NaN nie nass) und die Ankunft (Ereigniszeit,
         * zu der der Stand zuerst 1 m über dem Pegel war, NaN nie) aus {@code max_g<k>.r32} und {@code ankunft_g<k>.r32}.
         */
        public volatile float[] max, ankunft;
        public int ntx, ntz;
        /** Rechteck des nächstfeineren Gitters (Szene x0, z0, x1, z1) oder null. */
        public double[] feiner;

        Spur(int id, Art art, String lauf, int k, EreignisDaten.Leser leser) { this(id, art, lauf, k, leser.gitter.get(k), leser.zeiten(k), leser::roh); }

        Spur(int id, Art art, String lauf, int k, EreignisDaten.Gitter g, double[] t, Bilder quelle) {
            this.id = id;
            this.art = art;
            this.lauf = lauf;
            this.k = k;
            this.quelle = quelle;
            this.g = g;
            this.t = t;
            ntx = (g.nx() - 2) / KACHEL + 1;
            ntz = (g.nz() - 2) / KACHEL + 1;
        }

        /** Werkbuch Welle W3: ein Bild direkt aus der Quelle (am Vorrat vorbei, für einmalige Auswertungen). */
        public short[] roh(int b) throws java.io.IOException { return quelle.roh(k, b); }

        public double t0() { double[] t = this.t; return t.length == 0 ? Double.NaN : t[0]; }

        public double t1() { double[] t = this.t; return t.length == 0 ? Double.NaN : t[t.length - 1]; }

        /** Szenenrechteck der Zellmitten (x0, z0 im Süden, x1, z1 im Norden). */
        public double[] szene() {
            double[] a = Site.fromUtm(g.e(0), g.n(0)), b = Site.fromUtm(g.e(g.nx() - 1), g.n(g.nz() - 1));
            return new double[]{a[0], a[1], b[0], b[1]};
        }

        /**
         * Bild a liegt bei t[a] ≤ t, b = a + 1; f der Anteil von b. Vor dem ersten Bild null, nach dem letzten noch
         * {@link #NACHLAUF} s das letzte, dann null.
         */
        public double[] klammer(double zeit) {
            double[] t = this.t;
            if (t.length == 0 || !(zeit >= t[0]) || zeit > t[t.length - 1] + NACHLAUF) return null;
            if (zeit >= t[t.length - 1]) return new double[]{t.length - 1, t.length - 1, 0};
            int i = Arrays.binarySearch(t, zeit);
            if (i < 0) i = -i - 2;
            int b = Math.min(t.length - 1, i + 1);
            double f = b == i ? 0 : (zeit - t[i]) / (t[b] - t[i]);
            return new double[]{i, b, Math.max(0, Math.min(1, f))};
        }

        @Override public String toString() { return lauf + " g" + k + " (" + g.zelle() + " m)"; }
    }

    /** Zwei Bilder einer Spur und ihr Mischanteil. */
    public record Paar(Spur spur, int a, int b, double f, short[] ra, short[] rb) {
        /** Wert der Zelle (i, j) gemischt; NaN trocken. Ist nur ein Bild nass, gilt es ab der Hälfte. */
        public double wert(int i, int j) {
            int c = j * spur.g.nx() + i;
            short x = ra[c], y = rb[c];
            if (x == EreignisDaten.TROCKEN && y == EreignisDaten.TROCKEN) return Double.NaN;
            if (x == EreignisDaten.TROCKEN) return f >= 0.5 ? y * EreignisDaten.STUFE : Double.NaN;
            if (y == EreignisDaten.TROCKEN) return f < 0.5 ? x * EreignisDaten.STUFE : Double.NaN;
            return (x + (y - x) * f) * EreignisDaten.STUFE;
        }
    }

    private final File daten;
    private final List<EreignisDaten.Leser> offen = new ArrayList<>();
    private volatile List<Spur> spuren = List.of();
    private volatile String welle = WELLEN[0], geladen, stand = "noch nichts geladen";
    /** Phase 13c: der Lauf der Echtzeit statt einer Datei (null: aus daten/ereignis). */
    private volatile LiveLauf live;
    /** Ob Strom und Staub der Vorrechnung mitspielen (in der Echtzeit nur an der Wand von 1958). */
    private volatile boolean sturz = true;
    private volatile double zeit = Double.NaN, richtung = 1;
    private volatile boolean an = true;
    private final Object lock = new Object();
    private final Map<Long, short[]> vorrat = new LinkedHashMap<>(64, 0.75f, true);
    private long bytes;
    private Thread faden;
    private volatile boolean laeuft;
    private long ausgepackt, auspackNanos;
    private Boden1958 boden1958;

    public Wiedergabe(File daten) { this.daten = daten; }

    /** Wiedergabe aus {@code -Dlituya.daten} (Vorgabe {@code daten}). */
    public Wiedergabe() { this(new File(System.getProperty("lituya.daten", "daten"))); }

    /** Startet den Faden (einmal). */
    public synchronized void start() {
        if (faden != null) return;
        laeuft = true;
        faden = new Thread(this::arbeiten, "LituyaBay1958-Wiedergabe");
        faden.setDaemon(true);
        faden.setPriority(Thread.NORM_PRIORITY - 1);
        faden.start();
    }

    /** Abspielen an oder aus (aus: keine Welle, kein Strom, kein Staub in der Szene). */
    public void an(boolean b) { an = b; wecken(); }

    public boolean an() { return an; }

    /** Den Lauf der Welle wählen ({@code kette}, {@code messwelle} oder ein anderer Ordner in daten/ereignis). */
    public void welle(String lauf) {
        if (lauf == null || lauf.equals(welle)) return;
        welle = lauf;
        wecken();
    }

    public String welle() { LiveLauf l = live; return l != null ? l.name : welle; }

    /** Phase 13c: die Welle aus der Echtzeit (null: zurück zur Vorrechnung); mitSturz: Strom und Staub der Vorrechnung zeigen. */
    public void live(LiveLauf l, boolean mitSturz) {
        live = l;
        sturz = mitSturz;
        wecken();
    }

    public LiveLauf live() { return live; }

    /** Legt ein frisches Bild der Echtzeit gleich in den Vorrat (ohne Auspacken). */
    void einlegen(Spur s, int b, short[] r) {
        long k = key(s, b);
        synchronized (lock) {
            short[] alt = vorrat.put(k, r);
            if (alt == null) bytes += r.length * 2L;
            // das Älteste fliegt (der Vorrat ordnet nach Zugriff; das Paar im Bild wurde eben gelesen)
            var it = vorrat.entrySet().iterator();
            while (bytes > VORRAT && it.hasNext()) {
                var e = it.next();
                if (e.getKey() == k) continue;
                bytes -= e.getValue().length * 2L;
                it.remove();
            }
        }
    }

    private String schluessel() { LiveLauf l = live; return l != null ? "echtzeit#" + l.nummer + (sturz ? "+" : "") : welle; }

    /** Die Läufe der Welle, die in daten/ereignis liegen. */
    public List<String> wellen() {
        List<String> l = new ArrayList<>();
        File[] f = new File(daten, "ereignis").listFiles(File::isDirectory);
        if (f != null) {
            Arrays.sort(f);
            for (String w : WELLEN) for (File x : f) if (x.getName().equals(w)) l.add(w);
            for (File x : f) if (!l.contains(x.getName()) && x.getName().matches("(kette|messwelle).*") && new File(x, "EREIGNIS.txt").isFile()) l.add(x.getName());
        }
        return l;
    }

    /** Vom Bildlauf: Ereigniszeit (s seit dem ersten Stoß) und Laufrichtung (Vorzeichen des Zeitraffers). */
    public void setzen(double t, double lapse) {
        double alt = zeit;
        zeit = t;
        if (lapse != 0) richtung = Math.signum(lapse);
        if (!(Math.abs(t - alt) < 0.05)) wecken();
    }

    public double zeit() { return zeit; }

    /** Die Spuren des Augenblicks (Welle fein nach grob ist nicht garantiert; nach k sortiert je Art). */
    public List<Spur> spuren() { return spuren; }

    public List<Spur> spuren(Art art) {
        List<Spur> l = new ArrayList<>();
        for (Spur s : spuren) if (s.art == art) l.add(s);
        return l;
    }

    /** Das Paar um die Zeit, wenn beide Bilder im Vorrat sind; sonst null (und der Faden holt sie). */
    public Paar paar(Spur s, double t) {
        if (!an) return null;
        double[] k = s.klammer(t);
        if (k == null) return null;
        int a = (int) k[0], b = (int) k[1];
        short[] ra, rb;
        synchronized (lock) {
            ra = vorrat.get(key(s, a));
            rb = vorrat.get(key(s, b));
        }
        if (ra == null || rb == null) { wecken(); return null; }
        return new Paar(s, a, b, k[2], ra, rb);
    }

    /** Ein einzelnes Bild aus dem Vorrat (oder null). */
    public short[] bild(Spur s, int b) {
        synchronized (lock) { return vorrat.get(key(s, b)); }
    }

    /** Wasserstand der Welle am Szenenort (feinstes Gitter mit Daten), NaN: trocken, außerhalb oder noch nicht da. */
    public double eta(double x, double z, double t) {
        double[] u = Site.toUtm(x, z);
        List<Spur> w = spuren(Art.WELLE);
        for (int i = w.size() - 1; i >= 0; i--) {
            Spur s = w.get(i);
            int c = s.g.index(u[0], u[1]);
            if (c < 0) continue;
            Paar p = paar(s, t);
            if (p == null) continue;
            return p.wert(c % s.g.nx(), c / s.g.nx());
        }
        return Double.NaN;
    }

    /** Wartet, bis die Paare aller Spuren um t im Vorrat sind (für Standbilder); false nach ms. */
    public boolean warten(double t, long ms) {
        if (!an) return true;
        setzen(t, 1);
        long bis = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < bis) {
            if (!schluessel().equals(geladen)) { schlafen(); continue; }
            boolean alle = true;
            for (Spur s : spuren) if (s.klammer(t) != null && (paar(s, t) == null || (s.art != Art.WELLE && s.boden == null))) alle = false;
            if (alle) return true;
            schlafen();
        }
        return false;
    }

    private static void schlafen() { try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    /** „kette: 3 Gitter, 601/601/721 Bilder, 105–705 s · Strom 93–183 s · Staub 93–693 s · Vorrat 61 MB, 9 ms je Bild“ */
    public String zeile() {
        if (!an) return "Abspielen aus";
        long mb;
        synchronized (lock) { mb = bytes >> 20; }
        return String.format(Locale.GERMAN, "%s · Vorrat %d MB%s", stand, mb, ausgepackt > 0 ? String.format(Locale.GERMAN, ", %.0f ms je Bild ausgepackt", auspackNanos / 1e6 / ausgepackt) : "");
    }

    // ------------------------------------------------------------------ Faden

    private void wecken() { synchronized (lock) { lock.notifyAll(); } }

    private static long key(Spur s, int b) { return ((long) s.id << 32) | b; }

    private void arbeiten() {
        while (laeuft) {
            try {
                if (!schluessel().equals(geladen)) oeffnen();
                if (!arbeit()) synchronized (lock) { lock.wait(200); }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                stand = "Fehler: " + e.getMessage();
                System.err.println("LituyaBay1958: Wiedergabe: " + e);
                try { Thread.sleep(1000); } catch (InterruptedException ie) { return; }
            }
        }
    }

    /** Öffnet Welle, Strom und Staub neu. */
    private void oeffnen() throws IOException {
        String w = welle, key = schluessel();
        LiveLauf lv = live;
        boolean mitSturz = sturz;
        for (EreignisDaten.Leser l : offen) try { l.close(); } catch (IOException e) { /* zu */ }
        offen.clear();
        synchronized (lock) { vorrat.clear(); bytes = 0; }
        List<Spur> neu = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        File dw = EreignisDaten.ordner(daten, w);
        int id = 0;
        if (lv != null) {
            neu.addAll(lv.spuren);
            sb.append(lv.name);
            id = 100 + lv.spuren.size();
        } else if (new File(dw, "EREIGNIS.txt").isFile()) {
            EreignisDaten.Leser l = new EreignisDaten.Leser(dw);
            offen.add(l);
            List<Spur> ws = new ArrayList<>();
            for (int k = 0; k < l.gitter.size(); k++) ws.add(new Spur(id++, Art.WELLE, w, k, l));
            for (int k = 0; k < ws.size(); k++) {
                Spur s = ws.get(k);
                float[] mx = l.raster(dw, "max", k);
                kacheln(s, mx);
                s.max = mx;
                try { s.ankunft = l.raster(dw, "ankunft", k); } catch (IOException e) { s.ankunft = null; }
                if (k + 1 < ws.size()) s.feiner = ws.get(k + 1).szene();
            }
            neu.addAll(ws);
            sb.append(String.format(Locale.GERMAN, "%s: %d Gitter, %.0f–%.0f s", w, ws.size(), ws.get(0).t0(), ws.get(0).t1()));
        } else sb.append(w).append(": fehlt (ant ereignis)");
        File ds = EreignisDaten.ordner(daten, "strom"), dd = EreignisDaten.ordner(daten, "staub");
        Spur strom = null, oben = null, dichte = null;
        if (!mitSturz) sb.append(" · ohne Strom und Staub");
        else if (new File(ds, "EREIGNIS.txt").isFile()) {
            EreignisDaten.Leser l = new EreignisDaten.Leser(ds);
            offen.add(l);
            strom = new Spur(id++, Art.STROM, "strom", 0, l);
            neu.add(strom);
            sb.append(String.format(Locale.GERMAN, " · Strom %.0f–%.0f s", strom.t0(), strom.t1()));
        } else sb.append(" · Strom fehlt");
        if (!mitSturz) { /* ohne Staub */ }
        else if (new File(dd, "EREIGNIS.txt").isFile()) {
            EreignisDaten.Leser l = new EreignisDaten.Leser(dd);
            offen.add(l);
            oben = new Spur(id++, Art.STAUB_OBEN, "staub", 0, l);
            dichte = new Spur(id++, Art.STAUB_DICHTE, "staub", 1, l);
            neu.add(oben);
            neu.add(dichte);
            sb.append(String.format(Locale.GERMAN, " · Staub %.0f–%.0f s", oben.t0(), oben.t1()));
        } else sb.append(" · Staub fehlt");
        spuren = List.copyOf(neu);
        stand = sb.toString();
        geladen = key;
        // der Grund unter Strom und Staub (einmal gerechnet, dann aus dem Ordner)
        if (strom != null) strom.boden = boden(ds, strom.g, 0, false);
        if (oben != null) { float[] b = boden(dd, oben.g, 0, true); oben.boden = b; dichte.boden = b; }
        // W3b: Grund unter den Gittern der Welle (dünner Film am Hang, K11); einmal gerechnet, dann aus dem Ordner
        List<Spur> wsb = new ArrayList<>();
        if (lv == null) for (Spur s : neu) if (s.art == Art.WELLE) wsb.add(s);
        if (!wsb.isEmpty()) {
            Thread th = new Thread(() -> { for (Spur s : wsb) s.boden = boden(dw, s.g, s.k, false); }, "Wiedergabe-Grund");
            th.setDaemon(true);
            th.start();
        }
    }

    /** Je Kachel der höchste Stand aus max_g<k>.r32. */
    static void kacheln(Spur s, float[] max) {
        float[] m = new float[s.ntx * s.ntz];
        Arrays.fill(m, Float.NaN);
        int nx = s.g.nx(), nz = s.g.nz();
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                float v = max[j * nx + i];
                if (Float.isNaN(v)) continue;
                // eine Zelle gehört zu allen Kacheln, deren Rand sie berührt
                int ti = Math.min(s.ntx - 1, i / KACHEL), tj = Math.min(s.ntz - 1, j / KACHEL);
                for (int dj = -1; dj <= 0; dj++)
                    for (int di = -1; di <= 0; di++) {
                        int a = ti + di, b = tj + dj;
                        if (a < 0 || b < 0) continue;
                        if (di == -1 && i % KACHEL != 0) continue;
                        if (dj == -1 && j % KACHEL != 0) continue;
                        int c = b * s.ntx + a;
                        if (!(m[c] >= v)) m[c] = v;
                    }
            }
        s.kachelMax = m;
    }

    /**
     * Grund je Zelle des Gitters k: aus boden_g&lt;k&gt;.r32 (ab Phase 13b schreibt ihn {@code tools.Ereignis}) oder einmal aus
     * Boden1958 gerechnet und abgelegt; wasserNull: Wasser zählt als 0 (die Wolke liegt dort auf dem Wasser).
     */
    private float[] boden(File dir, EreignisDaten.Gitter g, int k, boolean wasserNull) {
        File f = new File(dir, "boden_g" + k + ".r32");
        int n = g.nx() * g.nz();
        try {
            if (f.isFile() && f.length() == n * 4L) {
                byte[] b = Files.readAllBytes(f.toPath());
                float[] v = new float[n];
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(v);
                return v;
            }
            Boden1958 bo;
            synchronized (this) {
                if (boden1958 == null) boden1958 = new Boden1958(daten);
                bo = boden1958;
            }
            float[] v = new float[n];
            java.util.stream.IntStream.range(0, g.nz()).parallel().forEach(j -> {
                for (int i = 0; i < g.nx(); i++) {
                    double h = bo.hoehe(g.e(i), g.n(j));
                    v[j * g.nx() + i] = Double.isNaN(h) ? (wasserNull ? 0f : -200f) : (float) (wasserNull ? Math.max(0, h) : h);
                }
            });
            ByteBuffer b = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN);
            b.asFloatBuffer().put(v);
            try { Files.write(f.toPath(), b.array()); } catch (IOException e) { System.err.println("LituyaBay1958: Grund nicht abgelegt: " + e.getMessage()); }
            return v;
        } catch (IOException | RuntimeException e) {
            System.err.println("LituyaBay1958: Grund für " + dir.getName() + ": " + e.getMessage());
            float[] v = new float[n];
            Arrays.fill(v, -200f);
            return v;
        }
    }

    /** Packt ein fehlendes Bild aus (erst die Paare, dann voraus); false, wenn alles da ist. */
    private boolean arbeit() throws IOException {
        if (!an) return false;
        double t = zeit;
        if (Double.isNaN(t)) return false;
        List<Spur> ss = spuren;
        List<long[]> noetig = new ArrayList<>();
        for (int stufe = 0; stufe <= VORAUS; stufe++)
            for (Spur s : ss) {
                double[] k = s.klammer(t);
                if (k == null) {
                    // vor dem ersten Bild: das erste schon bereitlegen, wenn es bald kommt
                    if (stufe == 0 && s.t.length > 0 && t > s.t0() - 30) { noetig.add(new long[]{s.id, 0}); noetig.add(new long[]{s.id, Math.min(1, s.t.length - 1)}); }
                    continue;
                }
                int a = (int) k[0], b = (int) k[1];
                if (stufe == 0) { noetig.add(new long[]{s.id, a}); noetig.add(new long[]{s.id, b}); }
                else {
                    int c = richtung >= 0 ? b + stufe : a - stufe;
                    if (c >= 0 && c < s.t.length) noetig.add(new long[]{s.id, c});
                }
            }
        // was gebraucht wird, nach vorn im Vorrat; das Älteste fliegt
        java.util.Set<Long> behalten = new java.util.HashSet<>();
        for (long[] x : noetig) behalten.add((x[0] << 32) | x[1]);
        for (long[] x : noetig) {
            long key = (x[0] << 32) | x[1];
            boolean da;
            synchronized (lock) { da = vorrat.get(key) != null; }
            if (da) continue;
            Spur s = null;
            for (Spur y : ss) if (y.id == x[0]) s = y;
            if (s == null) continue;
            long t0 = System.nanoTime();
            short[] r = s.quelle.roh(s.k, (int) x[1]);
            auspackNanos += System.nanoTime() - t0;
            ausgepackt++;
            if (spuren != ss) return true;
            synchronized (lock) {
                vorrat.put(key, r);
                bytes += r.length * 2L;
                var it = vorrat.entrySet().iterator();
                while (bytes > VORRAT && it.hasNext()) {
                    var e = it.next();
                    if (behalten.contains(e.getKey())) continue;
                    bytes -= e.getValue().length * 2L;
                    it.remove();
                }
            }
            if (Math.abs(zeit - t) > 1e-9) return true;   // die Zeit lief weiter: neu ordnen
        }
        return false;
    }

    @Override
    public void close() {
        laeuft = false;
        Thread f = faden;
        if (f != null) f.interrupt();
        for (EreignisDaten.Leser l : offen) try { l.close(); } catch (IOException e) { /* zu */ }
    }
}
