package com.dan.lituya.camera;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Werkbuch Welle W11c: die Drohne. Eine Kamera, die ein fahrendes Boot begleitet (WF46 A): Sie hängt in den Achsen des
 * Boots an einer Stellung (Abstand, Höhe), schaut auf das Boot (bei {@link Stellung#SEITE} knapp vor den Bug, bei
 * {@link Stellung#ACHTERN} auf das Heck und das Kielwasser) und folgt Ort und Blick mit Trägheit: ein kritisch gedämpfter
 * Federschwinger mit der Zeitkonstante {@link #tau} (Vorgabe 1,5 s) um das Soll, der die Fahrt des Solls vorsteuert (bei
 * gleichmäßiger Fahrt hält die Drohne die Stellung, in Wenden und beim Anfahren hinkt sie nach), so wie eine Drohne mit
 * Gimbal dem Boot nachfliegt.
 * Der Gimbal bleibt waagerecht (kein Rollen); Ziehen mit der Maus dreht den Blick um einen Versatz ({@link Camera#orbit}).
 * <ul>
 * <li><b>seite</b>: an Steuerbord, etwas vor der Mitte; der Bug mit der Bugwelle zieht durchs Bild.</li>
 * <li><b>achtern</b>: hinter dem Heck, hoch; der Kelvin-Keil und die Schaumspur.</li>
 * <li><b>ueberholen</b>: von achtern über das Boot hinweg nach vorn und zurück, Umlauf {@link #UEBERHOLEN_S} s.</li>
 * <li><b>kreisen</b>: einmal um das fahrende Boot in {@link #KREISEN_S} s.</li>
 * </ul>
 * Das Boot liefert je Schritt {x, y, z, Kurs, Stampfen, Rollen, …} (Welt, Kurs in Grad von Nord; x nach Ost, z nach Süd),
 * null, wenn es keins mehr gibt. Alles gestaltet [G].
 */
public final class Begleiter implements Camera.Begleiter {
    public enum Stellung {
        SEITE(35, 6), ACHTERN(90, 30), UEBERHOLEN(60, 12), KREISEN(50, 15);
        /** Vorgaben: Abstand und Höhe (m). */
        public final double abstand, hoehe;
        Stellung(double abstand, double hoehe) { this.abstand = abstand; this.hoehe = hoehe; }

        public static Stellung byName(String s) {
            if (s == null) return null;
            String k = s.trim().toLowerCase(Locale.ROOT).replace("ü", "ue");
            for (Stellung x : values()) if (x.name().toLowerCase(Locale.ROOT).equals(k)) return x;
            return null;
        }
    }

    /** Umlaufzeiten (s). */
    public static final double UEBERHOLEN_S = 35, KREISEN_S = 75;
    /** Mindesthöhe des Auges über dem Boot (m). */
    public static final double MINDESTENS_HOCH = 2.5;

    private final Supplier<double[]> boot;
    public final Stellung stellung;
    public final double abstand, hoehe;
    /** Zeitkonstante der Trägheit (s): nach rund 2 τ ist die Drohne am Ziel. */
    public volatile double tau = 1.5;
    private final double[] p = new double[3], v = new double[3], ziel = new double[3], vz = new double[3];
    private final double[] sollVorher = new double[6];
    private double zeit;
    private boolean init;
    private double[] letztes;

    public Begleiter(Supplier<double[]> boot, Stellung stellung, double abstand, double hoehe) {
        this.boot = boot;
        this.stellung = stellung;
        this.abstand = abstand > 0 ? abstand : stellung.abstand;
        this.hoehe = hoehe > 0 ? hoehe : stellung.hoehe;
    }

    /** Stellung mit ihren Vorgaben. */
    public Begleiter(Supplier<double[]> boot, Stellung stellung) { this(boot, stellung, 0, 0); }

    /** Das zuletzt gelieferte Boot {x, y, z, Kurs, …} oder null. */
    public double[] boot() { return letztes; }

    /** Wo die Drohne hin will und wohin sie schaut, ohne Trägheit: {sx, sy, sz, bx, by, bz}. */
    public double[] soll(double[] b, double zeit) {
        double k = Math.toRadians(b[3]);
        double fx = Math.sin(k), fz = -Math.cos(k);     // voraus
        double sx = Math.cos(k), sz = Math.sin(k);      // Steuerbord
        double bx = b[0], by = b[1], bz = b[2];
        double ox, oz, oy = hoehe, lx, ly = by + 1.5, lz;
        switch (stellung) {
            case SEITE -> {
                ox = sx * abstand + fx * 0.15 * abstand;
                oz = sz * abstand + fz * 0.15 * abstand;
                lx = bx + fx * 4; lz = bz + fz * 4;      // auf den Bug
                ly = by + 1.0;
            }
            case ACHTERN -> {
                ox = -fx * abstand;
                oz = -fz * abstand;
                lx = bx - fx * 0.1 * abstand; lz = bz - fz * 0.1 * abstand;   // Heck und Kielwasser, die Kimm im oberen Drittel (Probe 1102)
                ly = by;
            }
            case UEBERHOLEN -> {
                // von achtern (−Abstand) nach vorn (+Abstand) und zurück, dabei 7 m an Steuerbord vorbei, über dem Boot höher
                double u = (zeit % UEBERHOLEN_S) / UEBERHOLEN_S;
                double w = u < 0.5 ? u * 2 : 2 - u * 2;              // 0 → 1 → 0
                double s = -abstand + 2 * abstand * (0.5 - 0.5 * Math.cos(Math.PI * w));
                ox = fx * s + sx * 7;
                oz = fz * s + sz * 7;
                oy = hoehe + 6 * Math.exp(-(s * s) / (2 * 8 * 8));
                lx = bx; lz = bz;
            }
            default -> {
                double a = 2 * Math.PI * zeit / KREISEN_S;
                ox = sx * abstand * Math.cos(a) + fx * abstand * Math.sin(a);
                oz = sz * abstand * Math.cos(a) + fz * abstand * Math.sin(a);
                lx = bx; lz = bz;
            }
        }
        return new double[]{bx + ox, by + Math.max(MINDESTENS_HOCH, oy), bz + oz, lx, ly, lz};
    }

    @Override public double[] pose(double dt) {
        double[] b = boot.get();
        if (b == null) { letztes = null; return null; }
        letztes = b;
        dt = Math.max(0, Math.min(1, dt));
        zeit += dt;
        double[] s = soll(b, zeit);
        if (!init) {
            System.arraycopy(s, 0, p, 0, 3);
            System.arraycopy(s, 3, ziel, 0, 3);
            System.arraycopy(s, 0, sollVorher, 0, 6);
            init = true;
        } else if (dt > 0) {
            // kritisch gedämpfte Feder um das Soll mit Vorsteuerung seiner Fahrt: x'' = ω² (soll − x) − 2 ω (x' − soll'), ω = 2/τ;
            // in Teilschritten stabil
            double w = 2 / Math.max(0.05, tau);
            int n = Math.max(1, (int) Math.ceil(dt / 0.02));
            double h = dt / n;
            double[] vs = new double[6];
            for (int c = 0; c < 6; c++) { vs[c] = (s[c] - sollVorher[c]) / dt; if (Math.abs(vs[c]) > 60) vs[c] = 0; }   // ein Sprung ist keine Fahrt
            for (int i = 0; i < n; i++)
                for (int c = 0; c < 3; c++) {
                    v[c] += (w * w * (s[c] - p[c]) - 2 * w * (v[c] - vs[c])) * h;
                    p[c] += v[c] * h;
                    vz[c] += (w * w * (s[3 + c] - ziel[c]) - 2 * w * (vz[c] - vs[3 + c])) * h;
                    ziel[c] += vz[c] * h;
                }
            System.arraycopy(s, 0, sollVorher, 0, 6);
        }
        double dx = ziel[0] - p[0], dy = ziel[1] - p[1], dz = ziel[2] - p[2];
        double yaw = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        double pitch = Math.toDegrees(Math.atan2(-dy, Math.max(1e-6, Math.hypot(dx, dz))));
        return new double[]{p[0], p[1], p[2], yaw, pitch};
    }

    /** Für HUD und Prüfung: „Drohne seite 35 m, 6 m hoch“. */
    @Override public String toString() {
        return String.format(Locale.GERMAN, "Drohne %s %.0f m, %.0f m hoch", stellung.name().toLowerCase(Locale.ROOT), abstand, hoehe);
    }
}
