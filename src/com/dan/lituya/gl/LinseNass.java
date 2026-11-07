package com.dan.lituya.gl;

import com.dan.lens.FrontDrops;
import com.dan.lens.Lens;
import com.dan.wetglass.Wetglass;

import java.util.Locale;

/**
 * Werkbuch Welle W9c Runde 2 (W7e): Tropfen auf der Frontlinse. {@code com.dan.wetglass} rechnet die Frontlinse als kleine
 * senkrechte Scheibe im Regen (Einschläge nach Marshall-Palmer, so viel, wie der Wind den Regen gegen das Objektiv treibt;
 * Haften, Laufen, Verschmelzen, Feintropfen, Verdunsten), {@link FrontDrops} aus {@code com.dan.lens} rechnet, wie viel des
 * Bündels jedes Bildpunkts durch Wasser geht, und {@code LensGl} legt das über das Bild: weicher Schleier beim Tele, einzelne
 * Flecken beim Weitwinkel, ein Leuchten, wenn die Sonne in den Tropfen steht.
 * <p>
 * Von Hand ({@code tropfen 20}: so viele mm/h auf die Linse, {@code tropfen wischen}, {@code tropfen aus}) oder aus dem
 * Wetter (Vorgabe). 1958 ist es klar: dann bleibt die Linse trocken, bis die Gischt der Welle kommt (W8c).
 */
public final class LinseNass {
    /** Kachel der Rechnung (m): größer als jede Frontlinse, nahtlos. */
    static final double KACHEL = 0.06;
    /** Gitter des Anteils über das Bild. */
    static final int GW = 64, GH = 36;

    public final Wetglass glas = new Wetglass();
    public final FrontDrops tropfen = new FrontDrops();
    /** Tropfen aus dem Wetter (Regen und Wind gegen das Objektiv). */
    public volatile boolean ausWetter = true;
    /** Von Hand: Regen auf die Linse (mm/h); unter 0: aus dem Wetter. */
    public volatile double vonHandMmH = -1;
    /** Wind gegen die Linse, wenn von Hand (m/s) [G]. */
    public volatile double vonHandWind = 4;
    private double regenJetzt, windJetzt;
    private double gitterZeit = Double.NaN, gitterF, gitterN, gitterH;
    private float[] gitter;
    private String zeile = "trocken";

    public LinseNass() {
        glas.width = glas.height = KACHEL;
        glas.periodic = true;
        glas.drainLength = 0.05;      // die Frontlinse ist klein: was hinunterläuft, ist weg
        glas.quality = Wetglass.Quality.MEDIUM;
        glas.stepRate = 30;
        glas.slopeX = 0;
        glas.slopeY = -1;             // senkrecht, die Schwerkraft zieht nach unten
    }

    /**
     * Ein Schritt der Szene (ds s): Regen (mm/h), Wind (m/s, weht nach Richtung windNachX/Z in der Ebene), Blick der Kamera
     * (Einheitsvektor). Die Frontlinse schaut in Blickrichtung; sie fängt Regen, wenn der Wind ihn gegen sie treibt, und
     * mehr, wenn sie nach oben schaut.
     */
    public void schritt(double ds, double regenMmH, double windMs, double windNachX, double windNachZ, double[] fwd) {
        if (ds <= 0) return;
        double r = vonHandMmH >= 0 ? vonHandMmH : ausWetter ? regenMmH : 0;
        double w = vonHandMmH >= 0 ? vonHandWind : windMs;
        regenJetzt = r;
        windJetzt = w;
        double hz = Math.hypot(fwd[0], fwd[2]);
        double pitch = Math.atan2(fwd[1], hz);
        // gegen das Objektiv: der Regen kommt aus der Richtung, aus der der Wind weht
        double facing = vonHandMmH >= 0 ? 1 : Math.max(0, -(windNachX * fwd[0] + windNachZ * fwd[2]) / Math.max(1e-6, hz));
        glas.slopeY = -Math.cos(pitch);
        // quer zur Linse: Anteil des Winds in der Ebene des Glases (rechts der Kamera positiv)
        double rx = -fwd[2] / Math.max(1e-6, hz), rz = fwd[0] / Math.max(1e-6, hz);
        glas.windX = w * (windNachX * rx + windNachZ * rz);
        glas.windY = 0;
        glas.rain(r, w, facing);
        glas.evaporation = r > 0 ? 2e-8 : 6e-8;   // ohne Regen trocknet die Linse schneller [G]
        glas.step(Math.min(ds, 0.5));
    }

    /** Läuft die Rechnung s Sekunden voraus (für Standbilder: die Linse wird erst nass). */
    public void vorlauf(double s, double regenMmH, double windMs, double windNachX, double windNachZ, double[] fwd) {
        for (double t = 0; t < s; t += 0.1) schritt(0.1, regenMmH, windMs, windNachX, windNachZ, fwd);
    }

    /** Alles abwischen. */
    public void wischen() { glas.wipeAll(); gitterZeit = Double.NaN; }

    /** „tropfen 20“, „tropfen aus“, „tropfen wetter“, „tropfen wischen“. */
    public void befehl(String a) {
        String t = a.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case "aus" -> { vonHandMmH = -1; ausWetter = false; glas.wipeAll(); }
            case "wetter", "an" -> { vonHandMmH = -1; ausWetter = true; }
            case "wischen" -> wischen();
            default -> {
                double v = Double.parseDouble(t.replace(',', '.').replace("mm/h", "").trim());
                if (v < 0 || v > 200) throw new IllegalArgumentException("tropfen: 0 bis 200 mm/h");
                vonHandMmH = v;
            }
        }
    }

    /** Ist etwas auf der Linse? */
    public boolean nass() { return glas.count() > 0 || glas.fineMean() > 1e-4; }

    /**
     * Der Anteil unter Wasser je Bildpunkt (Gitter GW × GH, Zeilen von unten) für das Objektiv jetzt, oder null, wenn die Linse
     * trocken ist. Neu gerechnet, wenn sich die Tropfen geändert haben oder das Objektiv (Brennweite, Blende).
     */
    public float[] gitter(Lens.Optics o, double sensorHmm, double aspekt) {
        if (o == null || !nass()) { gitter = null; zeile = "trocken"; return null; }
        Wetglass.Snapshot s = glas.snapshot();
        if (s == null) return gitter;
        double rf = o.frontRadiusMm(), z = o.pupilBehindFrontMm(), rp = o.pupilMm() / 2;
        if (gitter != null && s.time() == gitterZeit && o.efl() == gitterF && rp == gitterN && sensorHmm * aspekt == gitterH) return gitter;
        // Tropfen auf der Linse: um die Mitte der Kachel, so weit die Frontlinse reicht (nahtlos über den Rand der Kachel)
        float[] d = s.drops();
        int n = s.count();
        double[] x = new double[n], y = new double[n], r = new double[n];
        int k = 0;
        double c = KACHEL / 2;
        for (int i = 0; i < n; i++) {
            double px = d[i * Wetglass.Snapshot.FLOATS] - c, py = d[i * Wetglass.Snapshot.FLOATS + 1] - c, a = d[i * Wetglass.Snapshot.FLOATS + 2];
            px -= KACHEL * Math.rint(px / KACHEL);
            py -= KACHEL * Math.rint(py / KACHEL);
            double xm = px * 1000, ym = py * 1000, am = a * 1000;
            if (Math.hypot(xm, ym) > rf + am) continue;
            x[k] = xm; y[k] = ym; r[k] = am; k++;
        }
        tropfen.set(x, y, r, k);
        tropfen.mist = Math.min(0.9, glas.fineMean());
        gitter = tropfen.coverage(GW, GH, sensorHmm * aspekt, sensorHmm, o.efl(), z, rp);
        gitterZeit = s.time();
        gitterF = o.efl();
        gitterN = rp;
        gitterH = sensorHmm * aspekt;
        zeile = String.format(Locale.GERMAN, "Linse: %s auf %.0f mm, Pupille %.0f mm hinter der Frontlinse · Regen auf die Linse %.1f mm/h, Wind %.0f m/s%s",
                tropfen.describe(), 2 * rf, z, regenJetzt, windJetzt, vonHandMmH >= 0 ? " (von Hand)" : "");
        return gitter;
    }

    /** Für das HUD. */
    public String zeile() { return zeile; }
}
