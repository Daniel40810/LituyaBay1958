package com.dan.lituya.tools;

import com.dan.lituya.scene.Stufe;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Schritt {@code gelaende} der Aufbereitung (Werkbuch 3.4): die drei Stufen aus {@link Stufe}, von grob nach fein, jede
 * füllt ihre Lücken aus der gröberen. Je Zelle zwei Kandidaten, das Land und der Grund:
 * <ul>
 * <li><b>Fern</b> 30 m: Land aus 3DEP 1″ (NAVD88), sonst Copernicus GLO-30 (EGM2008, im Fern ohne Versatz [G]); Grund aus
 * BlueTopo, sonst GEBCO 2025. In der Szene danach ihr Mittel ({@link Angleich}).</li>
 * <li><b>Szene</b> 5 m: Land aus dem Lidar 2019 (seit W9c Runde 2), sonst IfSAR 2012 (NAVD88), sonst 3DEP 1/3″; Grund aus
 * USACE 2019, sonst BlueTopo; Rest aus Fern. Im Kern danach das Mittel des Kerns ({@link Angleich}).</li>
 * <li><b>Kern</b> 0,5 m: Land aus dem Lidar 2019 (NAVD88), sonst IfSAR; Grund aus USACE 2019 (1 m), sonst BlueTopo (4 m);
 * Rest aus Szene.</li>
 * </ul>
 * Regel der Mischung [G]: Liegt der Grund mehr als 1 m unter dem Land und das Land unter 3 m, ist es Wasser, das das
 * Landmodell flach gelegt hat (Lidar und IfSAR „hydroflattened“), und der Grund gilt; sonst das Land. So kommen Bucht und
 * See aus den Lotungen und Strände, Nehrungen und Ufer aus dem Lidar. Höhenbezug überall NAVD88 (Lidar, IfSAR, 3DEP, USACE,
 * BlueTopo); nur Copernicus und GEBCO im Fern weichen ab, dort reicht es (Werkbuch 3.4).
 * <p>
 * Ausgabe je Stufe {@code daten/gelaende/<stufe>/E…_N….r32} und {@code STUFE.txt} mit Raster, Anteilen der Quellen und
 * der Prüfung der Naht zur gröberen Stufe.
 * <p>
 * Speicher (W9c Runde 2b, nach dem Absturz vom 7. November: 15 Fäden zu je rund 250 MB, 4 GB Speicher der Rohdaten, der
 * Heap wuchs auf 23 GB, Windows konnte nicht mehr zusagen): Eine Kachel rechnet in Schritten, ohne Kopien, die Quellen als
 * byte ({@link #bytesJeKachel}: Kern 142 MB); es laufen nur so viele Fäden, wie der Heap trägt ({@link #faeden}); der
 * Speicher der Rohdaten ({@link GeoTiff}) und der gröberen Stufe ({@link StufenQuelle}) sind begrenzt. Das Ergebnis ist
 * Bit für Bit dasselbe.
 */
public final class Gelaende {
    private Gelaende() { }

    /** Grund mehr als so viel unter dem Land … */
    static final double TIEFER = 1.0;
    /** … und Land unter dieser Höhe: dann Wasser (m) [G]. */
    public static final double FLACH = 3.0;

    /** Mischt Land und Grund nach der Regel oben; gibt 0 (Land), 1 (Grund) oder −1 (nichts) zurück. */
    public static int mischen(float land, float grund) { return mischen(land, grund, true); }

    /**
     * W6 Runde 8 (Kante am Ufer): wie {@link #mischen(float, float)}, aber Wasser nur, wo das Land glatt ist
     * ({@link com.dan.shore.HydroFlat}): Das Lidar legt das Wasser auf genau 0,800 m, ein Strand fällt. Vorher galt jedes
     * Land unter 3 m über tieferem Grund als Wasser, auch der Strand; an seiner Stelle lag der grobe Grund (BlueTopo,
     * −2 m), und vor dem Land über 3 m stand eine senkrechte Wand (docs/status/Kante_W06.png).
     */
    public static int mischen(float land, float grund, boolean glatt) {
        boolean l = !Float.isNaN(land), g = !Float.isNaN(grund);
        if (l && g) return (grund < land - TIEFER && land < FLACH && glatt) ? 1 : 0;
        return l ? 0 : g ? 1 : -1;
    }

    /**
     * W6 Runde 8: Strand und Böschung in Szene und Kern (Schalter {@code -Dlituya.ohneStrand=true}: wie vorher). Die Regel
     * oben mit glattem Wasser, dann {@link com.dan.shore.ShoreRamp} an der Naht, die bleibt (das Lidar endet bei 0,8 m, der
     * Grund davor liegt bei −2 m). Je Kachel mit einem Rand von {@link #RAMPE}.reach(), damit es an den Kanten keine Naht gibt.
     */
    public static final boolean STRAND = !Boolean.getBoolean("lituya.ohneStrand");
    /** Böschung vor dem Ufer [G]: Strand 0,10 bis −2 m, dann 0,5 bis −40 m; nur vor Land bis 4 m, Stufen ab 1 m. */
    public static com.dan.shore.ShoreRamp rampe() {
        com.dan.shore.ShoreRamp r = new com.dan.shore.ShoreRamp();
        r.stepMin = 1.0;
        r.topMax = 4.0;
        r.beachSlope = 0.10;
        r.beachFoot = -2.0;
        r.foreSlope = 0.5;
        r.floor = -40;
        return r;
    }
    static final com.dan.shore.ShoreRamp RAMPE = rampe();

    /** W9c Runde 2: die Szene ohne das Lidar (der Stand bis W9c), -Dlituya.szeneOhneLidar=true. */
    static final boolean OHNE_LIDAR_IN_SZENE = Boolean.getBoolean("lituya.szeneOhneLidar");

    /** Die Quellen einer Stufe: Land, Grund. */
    static List<List<Quelle>> quellen(Stufe s, Rohdaten r) throws IOException {
        List<Quelle> land = new ArrayList<>(), grund = new ArrayList<>();
        switch (s) {
            case FERN -> {
                land.add(new Quelle("3DEP 1″", r.dateien("hoehe/usgs_3dep_1", ".tif").stream().filter(f -> f.getName().startsWith("USGS_1_")).toList(), 0, 0, -100, 6000));
                land.add(new Quelle("Copernicus GLO-30", r.dateien("hoehe/copernicus_glo30", "_DEM.tif"), 0, 0, -100, 6000));
                grund.add(new Quelle("BlueTopo", r.dateien("bathymetrie/noaa_bluetopo", ".tiff", ".tif"), 0, 0, -3000, 200));
                grund.add(new Quelle("GEBCO 2025", r.dateien("bathymetrie/gebco_2025", ".tif"), 0, 0, -9000, 6000));
            }
            case SZENE -> {
                // W9c Runde 2 („Schwebe“): das Lidar zuerst, wie im Kern. IfSAR liegt unter dichtem Wald im Median 4,9 m zu
                // hoch; im Kern ersetzt {@link Angleich} die Szene danach durch das Mittel des Kerns, außerhalb gilt dies
                if (!OHNE_LIDAR_IN_SZENE) land.add(new Quelle("Lidar 2019", r.dateien("hoehe/usgs_lidar_glacierbay", ".tif"), 0, 0, -100, 6000));
                land.add(new Quelle("IfSAR 2012", r.dateien("hoehe/usgs_ifsar_dtm", ".tif"), 0, 0, -100, 6000));
                land.add(new Quelle("3DEP 1/3″", r.dateien("hoehe/usgs_3dep_13", ".tif").stream().filter(f -> f.getName().startsWith("USGS_13_")).toList(), 0, 0, -100, 6000));
                grund.add(new Quelle("USACE 2019", r.dateien("bathymetrie/usace_topobathy_2019", ".tif"), 0, 0, -300, 200));
                grund.add(new Quelle("BlueTopo", r.dateien("bathymetrie/noaa_bluetopo", ".tiff", ".tif"), 0, 0, -3000, 200));
            }
            case KERN -> {
                land.add(new Quelle("Lidar 2019", r.dateien("hoehe/usgs_lidar_glacierbay", ".tif"), 0, 0, -100, 6000));
                land.add(new Quelle("IfSAR 2012", r.dateien("hoehe/usgs_ifsar_dtm", ".tif"), 0, 0, -100, 6000));
                grund.add(new Quelle("USACE 2019", r.dateien("bathymetrie/usace_topobathy_2019", ".tif"), 0, 0, -300, 200));
                grund.add(new Quelle("BlueTopo", r.dateien("bathymetrie/noaa_bluetopo", ".tiff", ".tif"), 0, 0, -3000, 200));
            }
        }
        return List.of(land, grund);
    }

    /** Rand einer Kachel (Zellen) für Strand und Böschung; 0 im Fern. */
    static int rand(Stufe s) { return STRAND && s != Stufe.FERN ? (int) Math.ceil(RAMPE.reach() / s.zelle) + 2 : 0; }

    /**
     * W9c Runde 2b („Speicher“): so viel Speicher braucht eine Kachel höchstens (Bytes). Mit Rand n Zellen: Land und Grund
     * (8 n), ihre Quellen als byte (2 n), die Klassen (n), glatt (n), Kandidaten und Anker (2 n), besucht (n), in der
     * Böschung ihr Feld (4 n) und die Quellen (n); dazu die Mitte m Zellen (4 m). Gerechnet mit 18 n + 8 m. Kern: 2548² und
     * 2000², 142 MB (vorher rund 250 MB: zwei int-Felder, drei Kopien, ein Stapel je Zelle).
     */
    static long bytesJeKachel(Stufe s) {
        long kante = Stufe.N + 2L * rand(s), n = kante * kante, m = (long) Stufe.N * Stufe.N;
        return 18 * n + 8 * m;
    }

    /** Was im Speicher frei bleiben soll (Bytes): Lesen der Rohdaten, Ausgabe, JIT [G]. */
    static final long RESERVE = 1536L << 20;

    /**
     * Wie viele Fäden der Speicher trägt: höchstens faeden, so dass Kacheln, der Speicher der Rohdaten ({@link GeoTiff}) und der
     * gröberen Stufe ({@link StufenQuelle}) und die Reserve in den Heap passen; mindestens einer.
     */
    static int faeden(Stufe s, int faeden, long heap) {
        long frei = heap - GeoTiff.cacheBytes() - StufenQuelle.speicherBytes() - RESERVE;
        return (int) Math.max(1, Math.min(faeden, frei / bytesJeKachel(s)));
    }

    /** Zähler einer Stufe über alle Fäden. */
    static final class Lauf {
        final AtomicLong[] anteil;
        final AtomicLong fertig = new AtomicLong(), geschrieben = new AtomicLong(), gleich = new AtomicLong(), strand = new AtomicLong(), gehoben = new AtomicLong();
        final int kacheln;
        final long t0 = System.nanoTime();

        Lauf(int quellen, int kacheln) {
            anteil = new AtomicLong[quellen + 2];
            for (int i = 0; i < anteil.length; i++) anteil[i] = new AtomicLong();
            this.kacheln = kacheln;
        }
    }

    /** Rechnet eine Stufe; fertige Kacheln bleiben, außer neu = true. Gibt die Zahl der geschriebenen Kacheln zurück. */
    public static int stufe(Stufe s, Rohdaten r, boolean neu, int faeden) throws Exception {
        List<List<Quelle>> q = quellen(s, r);
        List<Quelle> land = q.get(0), grund = q.get(1);
        for (Quelle x : land) r.log("  %s Land: %s", s, x);
        for (Quelle x : grund) r.log("  %s Grund: %s", s, x);
        if (land.stream().mapToInt(Quelle::anzahl).sum() == 0) throw new IOException(s + ": keine Höhendaten im Kartenmaterial (" + r.roh + ")");
        StufenQuelle grob = s == Stufe.KERN ? new StufenQuelle(Stufe.SZENE, r.daten) : s == Stufe.SZENE ? new StufenQuelle(Stufe.FERN, r.daten) : null;
        int qn = land.size() + grund.size();
        if (qn + 2 > Byte.MAX_VALUE) throw new IllegalStateException(s + ": zu viele Quellen (" + qn + ")");
        List<double[]> kacheln = new ArrayList<>(s.kacheln());
        // -Dlituya.ausschnitt=OstMin,NordMin,OstMax,NordMax: nur die Kacheln, die das Rechteck berühren (Probeläufe, Nacharbeit)
        String aus = System.getProperty("lituya.ausschnitt");
        if (aus != null && !aus.isBlank()) {
            String[] p = aus.split(",");
            double ae0 = Double.parseDouble(p[0]), an0 = Double.parseDouble(p[1]), ae1 = Double.parseDouble(p[2]), an1 = Double.parseDouble(p[3]);
            kacheln.removeIf(k -> k[0] + s.kachel() <= ae0 || k[0] >= ae1 || k[1] + s.kachel() <= an0 || k[1] >= an1);
            r.log("  %s: nur Ausschnitt %s, %d Kacheln", s, aus, kacheln.size());
        }
        // W9c Runde 2b: nicht mehr Fäden, als der Speicher trägt (der Heap, unter Windows auch, was das System noch zusagt)
        long heap = Speicher.tragbar();
        int fa = faeden(s, faeden, heap);
        r.log("  %s: %d Fäden%s, je Kachel bis %.0f MB (Rand %d Zellen), Heap tragbar %.1f GB, Rohdaten bis %.1f GB, gröbere Stufe bis %.1f GB",
                s, fa, fa < faeden ? " (statt " + faeden + ", der Speicher trägt nicht mehr)" : "", bytesJeKachel(s) / 1048576.0, rand(s),
                heap / 1073741824.0, GeoTiff.cacheBytes() / 1073741824.0, StufenQuelle.speicherBytes() / 1073741824.0);
        Lauf z = new Lauf(qn, kacheln.size());
        ExecutorService ex = Executors.newFixedThreadPool(fa);
        List<Future<?>> fu = new ArrayList<>();
        for (double[] k : kacheln) fu.add(ex.submit(() -> { kachel(s, k, r, land, grund, grob, neu, z); return null; }));
        ex.shutdown();
        for (Future<?> f : fu) f.get();
        for (Quelle x : land) x.schliessen();
        for (Quelle x : grund) x.schliessen();
        // Anteile und Naht
        long summe = 0;
        AtomicLong[] anteil = z.anteil;
        for (AtomicLong a : anteil) summe += a.get();
        StringBuilder b = new StringBuilder();
        b.append("# Stufe ").append(s).append(" (Phase 2, ").append(java.time.LocalDate.now()).append(")\n");
        b.append(String.format(Locale.ROOT, "zelle=%s%nkachel_zellen=%d%nkacheln_ost=%d%nkacheln_nord=%d%nsuedwest_ost=%.0f%nsuedwest_nord=%.0f%n",
                fmt(s.zelle), Stufe.N, s.nx(), s.ny(), s.gebiet.eMin(), s.gebiet.nMin()));
        b.append("format=float32 little endian, Zeilen von Nord nach Sued, Hoehe m NAVD88, NaN = kein Wert, Datei E<Ost>_N<Nord>.r32 der Suedwestecke\n");
        if (summe > 0) {
            b.append("# Anteile der Quellen (nur neu gerechnete Kacheln):\n");
            for (int i = 0; i < qn + 2; i++) {
                String n = i < land.size() ? "Land " + land.get(i).name : i < qn ? "Grund " + grund.get(i - land.size()).name : i == qn ? "aus der groeberen Stufe" : "ohne Wert";
                b.append(String.format(Locale.ROOT, "anteil.%d=%.4f  # %s%n", i, anteil[i].get() / (double) summe, n));
                r.log("  %s: %5.1f %%  %s", s, 100.0 * anteil[i].get() / summe, n);
            }
        }
        if (grob != null) {
            String naht = naht(s, grob, r.daten);
            b.append("naht=").append(naht).append('\n');
            r.log("  %s: Naht zur gröberen Stufe: %s", s, naht);
        }
        Files.writeString(new File(s.ordner(r.daten), "STUFE.txt").toPath(), b.toString(), StandardCharsets.UTF_8);
        if (STRAND && s != Stufe.FERN) {
            b.append(String.format(Locale.ROOT, "strand=%d  # Zellen Land statt Grund (Strand, W6 Runde 8)%nboeschung=%d  # Zellen Grund gehoben (ShoreRamp)%n",
                    z.strand.get(), z.gehoben.get()));
            Files.writeString(new File(s.ordner(r.daten), "STUFE.txt").toPath(), b.toString(), StandardCharsets.UTF_8);
            r.log("  %s: Strand %d Zellen (%.2f km²), Böschung %d Zellen (%.2f km²)", s, z.strand.get(), z.strand.get() * s.zelle * s.zelle / 1e6,
                    z.gehoben.get(), z.gehoben.get() * s.zelle * s.zelle / 1e6);
        }
        r.log("  %s fertig: %d Kacheln geschrieben, %d unverändert, %d schon da, %.0f s · %s", s, z.geschrieben.get(), z.gleich.get(),
                z.fertig.get() - z.geschrieben.get() - z.gleich.get(), (System.nanoTime() - z.t0) / 1e9, Speicher.stand());
        return (int) z.geschrieben.get();
    }

    /**
     * Eine Kachel (W9c Runde 2b in Schritten, damit jedes Feld nur so lange lebt, wie es gebraucht wird; Ergebnis Bit für Bit
     * wie vorher): Land und Grund aus ihren Quellen, die Klassen, das Ergebnis im Feld des Lands, Böschung, Mitte, Lücken aus
     * der gröberen Stufe, schreiben, wenn es sich ändert.
     */
    static void kachel(Stufe s, double[] k, Rohdaten r, List<Quelle> land, List<Quelle> grund, StufenQuelle grob, boolean neu, Lauf z) throws IOException {
        File f = s.datei(r.daten, k[0], k[1]);
        if (!neu && f.isFile() && f.length() == 4L * Stufe.N * Stufe.N) { z.fertig.incrementAndGet(); return; }
        Gitter g = Gitter.kachel(s, k[0], k[1]);
        // W6 Runde 8: mit Rand rechnen (Strand und Böschung brauchen die Nachbarn), dann die Mitte nehmen
        boolean strand = STRAND && s != Stufe.FERN;
        int rand = rand(s), qn = land.size() + grund.size();
        Gitter gr = rand == 0 ? g : new Gitter(g.e0() - rand * s.zelle, g.n0() + rand * s.zelle, s.zelle, g.w() + 2 * rand, g.h() + 2 * rand);
        int W = gr.w();
        float[] lv = gr.leer(), gv = gr.leer();
        byte[] wer = fuellen(land, gr, lv, 0), werG = fuellen(grund, gr, gv, land.size());
        long[] strandZellen = new long[1];
        byte[] cls = klassen(lv, gv, wer, gr, g, rand, strand, strandZellen);
        // das Ergebnis im Feld des Lands (vorher ein eigenes Feld): Grund, wo die Klasse Grund ist; ohne Klasse sind beide NaN
        for (int i = 0; i < lv.length; i++) if (cls[i] == 1) lv[i] = gv[i];
        com.dan.shore.ShoreRamp.Result rr = strand ? RAMPE.apply(lv, cls, W, gr.h(), s.zelle) : null;
        float[] out = g.leer();
        long[] zaehl = new long[qn + 2];
        int gehoben = 0;
        for (int y = 0; y < g.h(); y++)
            for (int x = 0; x < g.w(); x++) {
                int i = (y + rand) * W + x + rand, o = y * g.w() + x;
                out[o] = lv[i];
                if (cls[i] == 0) zaehl[wer[i]]++;
                else if (cls[i] == 1) {
                    zaehl[werG[i]]++;
                    if (lv[i] != gv[i]) gehoben++;
                }
            }
        z.strand.addAndGet(strandZellen[0]);
        z.gehoben.addAndGet(gehoben);
        if (rr != null && (strandZellen[0] > 0 || gehoben > 0))
            r.log("  %s %s: Strand aus dem Land %d Zellen, Böschung %d Zellen (Rand %d Zellen: %s)", s, f.getName(), strandZellen[0], gehoben, rand, rr);
        lv = gv = null;
        wer = werG = cls = null;
        if (grob != null) {
            // füllt nur NaN: was danach nicht mehr NaN ist, kam aus der gröberen Stufe (ohne Kopie der Kachel)
            long vorher = nan(out);
            grob.proben(g, out);
            zaehl[qn] += vorher - nan(out);
        }
        zaehl[qn + 1] += nan(out);
        for (int i = 0; i < zaehl.length; i++) z.anteil[i].addAndGet(zaehl[i]);
        // W6 Runde 8: nur schreiben, was sich ändert; die alte Kachel vorher nach -Dlituya.sicherung=<Ordner>/<Stufe>
        boolean da = f.isFile();
        long n = z.fertig.incrementAndGet();
        if (da && gleich(f, out)) z.gleich.incrementAndGet();
        else {
            String sich = System.getProperty("lituya.sicherung", "");
            if (da && !sich.isBlank()) {
                File ziel = new File(new File(sich, s.ordner(r.daten).getName()), f.getName());
                if (!ziel.isFile()) {
                    ziel.getParentFile().mkdirs();
                    Files.copy(f.toPath(), ziel.toPath());
                }
            }
            Stufe.schreiben(f, out);
            z.geschrieben.incrementAndGet();
        }
        if (n % Math.max(1, z.kacheln / 20) == 0 || n == z.kacheln)
            r.log("  %s: %d von %d Kacheln (%.0f s) · %s", s, n, z.kacheln, (System.nanoTime() - z.t0) / 1e9, Speicher.stand());
    }

    /**
     * Füllt v aus den Quellen der Reihe nach (jede füllt nur, was noch NaN ist) und merkt je Zelle, welche Quelle es war
     * (erste + Nummer, −1 keine). Ohne Kopie: was noch keine Quelle hat, ist NaN.
     */
    static byte[] fuellen(List<Quelle> qs, Gitter gr, float[] v, int erste) throws IOException {
        byte[] w = new byte[v.length];
        java.util.Arrays.fill(w, (byte) -1);
        for (int qi = 0; qi < qs.size(); qi++) {
            qs.get(qi).proben(gr, v);
            byte b = (byte) (erste + qi);
            for (int i = 0; i < v.length; i++) if (w[i] < 0 && !Float.isNaN(v[i])) w[i] = b;
        }
        return w;
    }

    /**
     * Die Klassen je Zelle (0 Land, 1 Grund, −1 nichts) nach {@link #mischen(float, float, boolean)}; mit Strand: glatt nur
     * gegen Nachbarn derselben Landquelle, und Strand bleibt nur, wo er am übrigen Land hängt. strandZellen[0]: Strand in der
     * Mitte der Kachel.
     */
    static byte[] klassen(float[] lv, float[] gv, byte[] wer, Gitter gr, Gitter g, int rand, boolean strand, long[] strandZellen) {
        int W = gr.w();
        byte[] cls = new byte[lv.length];
        // glatt nur gegen Nachbarn derselben Landquelle (an der Grenze Lidar/IfSAR liegen zwei Wasserflächen nebeneinander)
        boolean[] glatt = strand ? com.dan.shore.HydroFlat.mask(lv, wer, W, gr.h(), com.dan.shore.HydroFlat.TOL, com.dan.shore.HydroFlat.MIN_EQUAL) : null;
        for (int i = 0; i < cls.length; i++) cls[i] = (byte) (glatt == null ? mischen(lv[i], gv[i]) : mischen(lv[i], gv[i], glatt[i]));
        glatt = null;
        if (!strand) return cls;
        // Strand (nach der alten Regel Wasser, jetzt Land) bleibt nur, wo er am übrigen Land hängt; unruhige Zellen
        // mitten im Wasser (IfSAR über der Bucht, Ränder des Lidars) würden sonst Inseln und Rücken mit Böschung
        boolean[] kand = new boolean[cls.length], anker = new boolean[cls.length];
        for (int i = 0; i < cls.length; i++) {
            boolean alt = mischen(lv[i], gv[i]) == 1;
            kand[i] = cls[i] == 0 && alt;
            anker[i] = cls[i] == 0 && !alt;
        }
        boolean[] dran = com.dan.shore.HydroFlat.attached(kand, anker, W, gr.h());
        anker = null;
        for (int i = 0; i < cls.length; i++) {
            if (!kand[i]) continue;
            if (!dran[i]) { cls[i] = 1; continue; }
            int x = i % W - rand, y = i / W - rand;
            if (x >= 0 && y >= 0 && x < g.w() && y < g.h()) strandZellen[0]++;
        }
        return cls;
    }

    static long nan(float[] v) {
        long n = 0;
        for (float x : v) if (Float.isNaN(x)) n++;
        return n;
    }

    /** Ob die Datei genau diese Werte hält (wie Arrays.equals: NaN gleich NaN), gelesen in Stücken statt als ganze Kachel. */
    static boolean gleich(File f, float[] v) throws IOException {
        if (f.length() != 4L * v.length) return false;
        try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(f.toPath(), java.nio.file.StandardOpenOption.READ)) {
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(1 << 20).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int i = 0;
            while (i < v.length) {
                b.clear();
                b.limit((int) Math.min(b.capacity(), 4L * (v.length - i)));
                while (b.hasRemaining()) if (ch.read(b) < 0) return false;
                b.flip();
                while (b.hasRemaining()) if (Float.floatToIntBits(b.getFloat()) != Float.floatToIntBits(v[i++])) return false;
            }
            return true;
        }
    }

    static String fmt(double z) { return z == Math.rint(z) ? String.valueOf((long) z) : String.valueOf(z); }

    /**
     * Prüft die Naht zur gröberen Stufe: an 4000 Punkten entlang des Randes der feinen Stufe (eine Zelle nach innen) der
     * Unterschied zum bilinearen Wert der groben. Gibt Median und 95-%-Wert des Betrags zurück.
     */
    static String naht(Stufe s, StufenQuelle grob, File daten) throws IOException {
        StufenQuelle fein = new StufenQuelle(s, daten);
        double e0 = s.gebiet.eMin(), n0 = s.gebiet.nMin(), w = s.nx() * s.kachel(), h = s.ny() * s.kachel(), d = s.zelle * 1.5;
        List<Double> diff = new ArrayList<>();
        for (int k = 0; k < 4000; k++) {
            double u = (k % 1000) / 1000.0, e, n;
            switch (k / 1000) {
                case 0 -> { e = e0 + d + u * (w - 2 * d); n = n0 + d; }
                case 1 -> { e = e0 + d + u * (w - 2 * d); n = n0 + h - d; }
                case 2 -> { e = e0 + d; n = n0 + d + u * (h - 2 * d); }
                default -> { e = e0 + w - d; n = n0 + d + u * (h - 2 * d); }
            }
            float a = fein.wert(e, n), b = grob.wert(e, n);
            if (!Float.isNaN(a) && !Float.isNaN(b)) diff.add((double) Math.abs(a - b));
        }
        if (diff.isEmpty()) return "keine gemeinsamen Punkte";
        java.util.Collections.sort(diff);
        return String.format(Locale.ROOT, "%d Punkte, Median %.2f m, 95 %% %.2f m", diff.size(), diff.get(diff.size() / 2), diff.get((int) (diff.size() * 0.95)));
    }
}
