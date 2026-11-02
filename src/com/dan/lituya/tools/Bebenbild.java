package com.dan.lituya.tools;

import com.dan.lituya.scene.Site;
import com.dan.quake.Basin;
import com.dan.quake.Fault;
import com.dan.quake.Quake;
import com.dan.quake.Slope;
import com.dan.quake.demo.QuakeDemo;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Das Beben über den aufbereiteten Daten (Phase 8, Werkbuch 9.1; Ant-Ziel {@code bebenbild}): {@code com.dan.quake} mit
 * der Spur der Fairweather-Störung und dem Herd aus {@code daten/ereignis} (Schritt {@code stoerung}), Bruch ¾ nach
 * Nordwesten [G]. Schreibt {@code docs/status/Beben.png} (der Bogen von {@code QuakeDemo} zur Zeit t) und auf die
 * Konsole: Laufzeiten, Stärke, Arias und Dauer an den Orten der Bucht, Newmark an der NE-Wand des Gilbert Inlet für
 * Sicherheitsfaktoren von 1,05 bis 1,30 (wann sie versagt, gegen die 60 bis 150 s der Zeugen), die Seiche.
 */
public final class Bebenbild {
    private Bebenbild() { }

    /** Das Beben aus daten/ereignis. */
    public static Quake beben(File daten) throws Exception {
        double[] xz = Stoerung.spurLesen(daten), h = Stoerung.herdLesen(daten);
        Fault f = new Fault(xz);
        double[] p = Site.fromUtm(h[0], h[1]);
        f.hx = p[0];
        f.hz = p[1];
        f.hdepth = h[2];
        f.forward = 0.75;
        Quake q = new Quake(f, h[3]);
        q.seed = 1958;
        return q;
    }

    public static void main(String[] a) throws Exception {
        File daten = new File(a.length > 0 ? a[0] : "daten"), aus = new File(a.length > 1 ? a[1] : "docs/status/Beben.png");
        double t = a.length > 2 ? Double.parseDouble(a[2]) : 30;
        Quake q = beben(daten);
        System.out.printf(Locale.GERMAN, "Beben über daten/ereignis (Phase 8): Spur %.0f km (Qfaults 2020), Herd E %.0f N %.0f in %.0f km Tiefe%n", q.fault.length() / 1000,
                q.fault.hx + Site.E0, Site.N0 - q.fault.hz, q.fault.hdepth / 1000);
        System.out.println(QuakeDemo.text(q));
        double gx = Site.GILBERT.x(), gz = Site.GILBERT.z();
        Quake.Record r = q.record(gx, gz);
        System.out.println("  Newmark an der NE-Wand des Gilbert Inlet (40°, φ' 35°, Platte 92 m) je Sicherheitsfaktor in Ruhe [G]:");
        for (double fs : new double[]{1.05, 1.10, 1.15, 1.20, 1.25, 1.30}) {
            Slope s = QuakeDemo.gilbert();
            s.cohesion = s.cohesionFor(fs);
            Slope.Result n = s.newmark(r);
            System.out.printf(Locale.GERMAN, "    FS %.2f (c' %3.0f kPa, a_c %.3f g): Verschiebung %5.2f m, versagt %s; JIBSON93 %.2f m%n", fs, s.cohesion, s.ac() / 9.81, n.total(),
                    Double.isNaN(n.failTime()) ? "nicht" : String.format(Locale.GERMAN, "nach %.0f s", n.failTime()), Slope.jibson93(r.arias(), s.ac() / 9.81));
        }
        Basin b = new Basin();
        System.out.printf(Locale.GERMAN, "  Seiche: T1 %.0f s geschlossen, %.0f s offen%n", b.period(1), open(b));
        BufferedImage im = new BufferedImage(1800, 1100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        QuakeDemo.paint(g, q, 1800, 1100, t);
        g.dispose();
        ImageIO.write(im, "png", aus);
        System.out.println("Bild: " + aus.getPath());
    }

    static double open(Basin b) { b.open = true; double t = b.period(1); b.open = false; return t; }
}
