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

    /** Rechnet eine Stufe; fertige Kacheln bleiben, außer neu = true. Gibt die Zahl der geschriebenen Kacheln zurück. */
    public static int stufe(Stufe s, Rohdaten r, boolean neu, int faeden) throws Exception {
        List<List<Quelle>> q = quellen(s, r);
        List<Quelle> land = q.get(0), grund = q.get(1);
        for (Quelle x : land) r.log("  %s Land: %s", s, x);
        for (Quelle x : grund) r.log("  %s Grund: %s", s, x);
        if (land.stream().mapToInt(Quelle::anzahl).sum() == 0) throw new IOException(s + ": keine Höhendaten im Kartenmaterial (" + r.roh + ")");
        StufenQuelle grob = s == Stufe.KERN ? new StufenQuelle(Stufe.SZENE, r.daten) : s == Stufe.SZENE ? new StufenQuelle(Stufe.FERN, r.daten) : null;
        int qn = land.size() + grund.size();
        AtomicLong[] anteil = new AtomicLong[qn + 2];
        for (int i = 0; i < anteil.length; i++) anteil[i] = new AtomicLong();
        List<double[]> kacheln = new ArrayList<>(s.kacheln());
        // -Dlituya.ausschnitt=OstMin,NordMin,OstMax,NordMax: nur die Kacheln, die das Rechteck berühren (Probeläufe, Nacharbeit)
        String aus = System.getProperty("lituya.ausschnitt");
        if (aus != null && !aus.isBlank()) {
            String[] p = aus.split(",");
            double ae0 = Double.parseDouble(p[0]), an0 = Double.parseDouble(p[1]), ae1 = Double.parseDouble(p[2]), an1 = Double.parseDouble(p[3]);
            kacheln.removeIf(k -> k[0] + s.kachel() <= ae0 || k[0] >= ae1 || k[1] + s.kachel() <= an0 || k[1] >= an1);
            r.log("  %s: nur Ausschnitt %s, %d Kacheln", s, aus, kacheln.size());
        }
        AtomicLong fertig = new AtomicLong(), geschrieben = new AtomicLong(), gleich = new AtomicLong(), strandSumme = new AtomicLong(), gehobenSumme = new AtomicLong();
        long t0 = System.nanoTime();
        ExecutorService ex = Executors.newFixedThreadPool(Math.max(1, faeden));
        List<Future<?>> fu = new ArrayList<>();
        for (double[] k : kacheln) {
            fu.add(ex.submit(() -> {
                File f = s.datei(r.daten, k[0], k[1]);
                if (!neu && f.isFile() && f.length() == 4L * Stufe.N * Stufe.N) { fertig.incrementAndGet(); return null; }
                Gitter g = Gitter.kachel(s, k[0], k[1]);
                // W6 Runde 8: mit Rand rechnen (Strand und Böschung brauchen die Nachbarn), dann die Mitte nehmen
                boolean strand = STRAND && s != Stufe.FERN;
                int rand = strand ? (int) Math.ceil(RAMPE.reach() / s.zelle) + 2 : 0;
                Gitter gr = rand == 0 ? g : new Gitter(g.e0() - rand * s.zelle, g.n0() + rand * s.zelle, s.zelle, g.w() + 2 * rand, g.h() + 2 * rand);
                int W = gr.w();
                float[] lv = gr.leer(), gv = gr.leer(), outR = gr.leer(), out = g.leer();
                int[] wer = new int[W * gr.h()];
                java.util.Arrays.fill(wer, -1);
                for (int qi = 0; qi < land.size(); qi++) {
                    float[] vor = lv.clone();
                    land.get(qi).proben(gr, lv);
                    for (int i = 0; i < lv.length; i++) if (Float.isNaN(vor[i]) && !Float.isNaN(lv[i])) wer[i] = qi;
                }
                int[] werG = new int[wer.length];
                java.util.Arrays.fill(werG, -1);
                for (int qi = 0; qi < grund.size(); qi++) {
                    float[] vor = gv.clone();
                    grund.get(qi).proben(gr, gv);
                    for (int i = 0; i < gv.length; i++) if (Float.isNaN(vor[i]) && !Float.isNaN(gv[i])) werG[i] = land.size() + qi;
                }
                // glatt nur gegen Nachbarn derselben Landquelle (an der Grenze Lidar/IfSAR liegen zwei Wasserflächen nebeneinander)
                boolean[] glatt = strand ? com.dan.shore.HydroFlat.mask(lv, wer, W, gr.h(), com.dan.shore.HydroFlat.TOL, com.dan.shore.HydroFlat.MIN_EQUAL) : null;
                byte[] cls = new byte[outR.length];
                for (int i = 0; i < outR.length; i++) cls[i] = (byte) (glatt == null ? mischen(lv[i], gv[i]) : mischen(lv[i], gv[i], glatt[i]));
                long strandZellen = 0;
                if (glatt != null) {
                    // Strand (nach der alten Regel Wasser, jetzt Land) bleibt nur, wo er am übrigen Land hängt; unruhige Zellen
                    // mitten im Wasser (IfSAR über der Bucht, Ränder des Lidars) würden sonst Inseln und Rücken mit Böschung
                    boolean[] kand = new boolean[cls.length], anker = new boolean[cls.length];
                    for (int i = 0; i < cls.length; i++) {
                        boolean alt = mischen(lv[i], gv[i]) == 1;
                        kand[i] = cls[i] == 0 && alt;
                        anker[i] = cls[i] == 0 && !alt;
                    }
                    boolean[] dran = com.dan.shore.HydroFlat.attached(kand, anker, W, gr.h());
                    for (int i = 0; i < cls.length; i++) {
                        if (!kand[i]) continue;
                        if (!dran[i]) { cls[i] = 1; continue; }
                        int x = i % W - rand, y = i / W - rand;
                        if (x >= 0 && y >= 0 && x < g.w() && y < g.h()) strandZellen++;
                    }
                }
                for (int i = 0; i < outR.length; i++) {
                    if (cls[i] == 0) outR[i] = lv[i];
                    else if (cls[i] == 1) outR[i] = gv[i];
                }
                com.dan.shore.ShoreRamp.Result rr = null;
                if (strand) rr = RAMPE.apply(outR, cls, W, gr.h(), s.zelle);
                long[] zaehl = new long[qn + 2];
                int gehoben = 0;
                for (int y = 0; y < g.h(); y++)
                    for (int x = 0; x < g.w(); x++) {
                        int i = (y + rand) * W + x + rand, o = y * g.w() + x;
                        out[o] = outR[i];
                        if (cls[i] == 0) zaehl[wer[i]]++;
                        else if (cls[i] == 1) {
                            zaehl[werG[i]]++;
                            if (outR[i] != gv[i]) gehoben++;
                        }
                    }
                strandSumme.addAndGet(strandZellen);
                gehobenSumme.addAndGet(gehoben);
                if (rr != null && (strandZellen > 0 || gehoben > 0))
                    r.log("  %s %s: Strand aus dem Land %d Zellen, Böschung %d Zellen (Rand %d Zellen: %s)", s, f.getName(), strandZellen, gehoben, rand, rr);
                if (grob != null) {
                    float[] vor = out.clone();
                    grob.proben(g, out);
                    for (int i = 0; i < out.length; i++) if (Float.isNaN(vor[i]) && !Float.isNaN(out[i])) zaehl[qn]++;
                }
                for (float v : out) if (Float.isNaN(v)) zaehl[qn + 1]++;
                for (int i = 0; i < zaehl.length; i++) anteil[i].addAndGet(zaehl[i]);
                // W6 Runde 8: nur schreiben, was sich ändert; die alte Kachel vorher nach -Dlituya.sicherung=<Ordner>/<Stufe>
                float[] alt = f.isFile() ? Stufe.lesen(f) : null;
                long n = fertig.incrementAndGet();
                if (alt != null && java.util.Arrays.equals(alt, out)) gleich.incrementAndGet();
                else {
                    String sich = System.getProperty("lituya.sicherung", "");
                    if (alt != null && !sich.isBlank()) {
                        File z = new File(new File(sich, s.ordner(r.daten).getName()), f.getName());
                        if (!z.isFile()) {
                            z.getParentFile().mkdirs();
                            Files.copy(f.toPath(), z.toPath());
                        }
                    }
                    Stufe.schreiben(f, out);
                    geschrieben.incrementAndGet();
                }
                if (n % Math.max(1, kacheln.size() / 20) == 0 || n == kacheln.size())
                    r.log("  %s: %d von %d Kacheln (%.0f s)", s, n, kacheln.size(), (System.nanoTime() - t0) / 1e9);
                return null;
            }));
        }
        ex.shutdown();
        for (Future<?> f : fu) f.get();
        for (Quelle x : land) x.schliessen();
        for (Quelle x : grund) x.schliessen();
        // Anteile und Naht
        long summe = 0;
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
                    strandSumme.get(), gehobenSumme.get()));
            Files.writeString(new File(s.ordner(r.daten), "STUFE.txt").toPath(), b.toString(), StandardCharsets.UTF_8);
            r.log("  %s: Strand %d Zellen (%.2f km²), Böschung %d Zellen (%.2f km²)", s, strandSumme.get(), strandSumme.get() * s.zelle * s.zelle / 1e6,
                    gehobenSumme.get(), gehobenSumme.get() * s.zelle * s.zelle / 1e6);
        }
        r.log("  %s fertig: %d Kacheln geschrieben, %d unverändert, %d schon da, %.0f s", s, geschrieben.get(), gleich.get(),
                fertig.get() - geschrieben.get() - gleich.get(), (System.nanoTime() - t0) / 1e9);
        return (int) geschrieben.get();
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
