package com.dan.lituya.tools;

import com.dan.impact.Granulate;
import com.dan.impact.Impact;
import com.dan.impact.Inflow;
import com.dan.impact.Presets;
import com.dan.impact.demo.ImpactDemo;
import com.dan.landslide.Landslide;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Das Eintauchen vom 9. Juli 1958 im Schnitt (Phase 10, Werkbuch 5.3, 9.3; Ant-Ziel {@code eintauchen}). Verdrahtet zwei
 * Packages, die sich nicht kennen: Der Strom aus {@code landslide} ({@link com.dan.landslide.Presets#gilbertInlet1958})
 * meldet je Schritt den Zufluss über die Uferlinie ({@link Landslide.Entry}); daraus wird je Meter Breite ein
 * {@link Inflow} (Mächtigkeit = Rate / (Breite · Geschwindigkeit), so bleibt das Volumen je Meter), und {@code impact}
 * rechnet damit den Schnitt durch den Gilbert Inlet wie im Laborfall, mit Fels ρ 2 700 ohne Auflockerung. Zum Vergleich
 * der Laborfall FRITZ01 selbst. Damit entscheidet sich die offene Frage aus Werkbuch 9.2, ob das Profil des Stroms
 * (16 s, 31 m) gestaucht werden muss. Schreibt den Bogen von {@code ImpactDemo} für den Strom und die Tafel auf die
 * Konsole. Argumente: Bild, t (s), Qualität von impact (LOW, MEDIUM, HIGH), Qualität von landslide (LOW, MEDIUM, HIGH).
 */
public final class Eintauchen {
    private Eintauchen() { }

    public static void main(String[] a) throws Exception {
        File aus = new File(a.length > 0 ? a[0] : "docs/status/Eintauchen.png");
        double t = a.length > 1 ? Double.parseDouble(a[1]) : 12;
        Impact.Quality qi = a.length > 2 ? Impact.Quality.valueOf(a[2]) : Impact.Quality.LOW;
        Landslide.Quality ql = a.length > 3 ? Landslide.Quality.valueOf(a[3]) : Landslide.Quality.MEDIUM;

        Landslide strom = com.dan.landslide.Presets.gilbertInlet1958(ql);
        strom.runToRest(120);
        Inflow in = zufluss(strom.entries(), "Strom aus landslide (" + ql + ")");
        System.out.printf(Locale.GERMAN, "Strom aus landslide: %.3e m³ über die Uferlinie, je Meter Breite: %s%n", strom.entrySummary()[8], in);

        Inflow lab = Inflow.fritz01();
        System.out.printf(Locale.GERMAN, "Laborfall: %s%n", lab);

        double tEnd = Math.max(26, in.end() + 14);
        ImpactDemo.Film fs = new ImpactDemo.Film(Presets.gilbertInlet1958(qi, in, Granulate.rock()), tEnd);
        fs.s.name = "Gilbert Inlet 1958 im Schnitt, Strom aus landslide";
        ImpactDemo.Film fl = new ImpactDemo.Film(qi, 26);

        System.out.println("                                   Laborfall FRITZ01     Strom aus landslide");
        zeile("Volumen je Meter (m²)", fl.s.inflow().volume(), fs.s.inflow().volume(), "%.0f");
        zeile("Masse je Meter (t)", fl.s.releasedMass() / 1000, fs.s.releasedMass() / 1000, "%.0f");
        zeile("Dauer für 90 % (s)", fl.s.inflow().duration90(), fs.s.inflow().duration90(), "%.1f");
        zeile("Mächtigkeit bis (m, normal)", fl.s.inflow().peak(), fs.s.inflow().peak(), "%.0f");
        zeile("Geschwindigkeit nach Rate (m/s)", fl.s.inflow().meanSpeed(), fs.s.inflow().meanSpeed(), "%.0f");
        for (int g = 0; g < 3; g++) {
            Impact.Gauge gl = fl.s.gauges().get(g), gs = fs.s.gauges().get(g);
            System.out.printf(Locale.GERMAN, "  Pegel %-28s %5.0f m nach %4.1f s     %5.0f m nach %4.1f s%n", gl.name, gl.max(), gl.timeOfMax(), gs.max(), gs.timeOfMax());
        }
        double el = energie(fl.s), es = energie(fs.s);
        System.out.printf(Locale.GERMAN, "  Energie der Welle / Gleitkörper  %5.1f %%                %5.1f %%%n", 100 * el / fl.s.releasedEnergy(), 100 * es / fs.s.releasedEnergy());
        System.out.printf(Locale.GERMAN, "  Rechnung                         %5.1f s                 %5.1f s%n", fl.seconds, fs.seconds);

        BufferedImage im = new BufferedImage(1800, 1100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        ImpactDemo.paint(g, fs, t, 1800, 1100);
        g.dispose();
        aus.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(im, "png", aus);
        System.out.println("Bild: " + aus + " (Strom aus landslide, t = " + String.format(Locale.GERMAN, "%.1f", t) + " s)");
    }

    /** Zufluss je Meter Breite aus den Schritten des Stroms; die Zeit beginnt mit dem ersten Zufluss. */
    public static Inflow zufluss(List<Landslide.Entry> es, String name) {
        List<double[]> p = new ArrayList<>();
        double t0 = Double.NaN;
        for (Landslide.Entry e : es) {
            if (e.rate() <= 0 || e.width() <= 0 || e.speed() <= 0) continue;
            if (Double.isNaN(t0)) t0 = e.t();
            double tt = e.t() - t0;
            if (!p.isEmpty() && tt <= p.get(p.size() - 1)[0] + 1e-6) continue;
            p.add(new double[]{tt, e.rate() / (e.width() * e.speed()), e.speed()});
        }
        if (p.size() < 2) throw new IllegalStateException("kein Zufluss über die Uferlinie");
        double[] t = new double[p.size() + 1], s = new double[p.size() + 1], v = new double[p.size() + 1];
        for (int i = 0; i < p.size(); i++) { t[i] = p.get(i)[0]; s[i] = p.get(i)[1]; v[i] = p.get(i)[2]; }
        t[p.size()] = t[p.size() - 1] + 0.1;
        v[p.size()] = v[p.size() - 1];
        return new Inflow(name, t, s, v);
    }

    static double energie(Impact s) {
        double tm = s.gauges().get(1).timeOfMax(), best = 0, d = Double.POSITIVE_INFINITY;
        for (double[] e : s.energySeries()) if (Math.abs(e[0] - tm) < d) { d = Math.abs(e[0] - tm); best = e[1]; }
        return best;
    }

    static void zeile(String was, double l, double s, String f) {
        System.out.printf(Locale.GERMAN, "  %-32s %10s              %10s%n", was, String.format(Locale.GERMAN, f, l), String.format(Locale.GERMAN, f, s));
    }
}
