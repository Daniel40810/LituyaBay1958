package com.dan.lituya.tools;

import com.dan.lituya.scene.Boden1958;
import com.dan.lituya.scene.BootBahn;
import com.dan.lituya.scene.Gezeiten;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;
import com.dan.lituya.scene.VorrechnungWasser;
import com.dan.vessel.Boat;
import com.dan.vessel.Chain;
import com.dan.vessel.Kutter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die drei Troller in der vorgerechneten Welle (Phase 15a, Werkbuch 7.3): {@code com.dan.vessel} über einem Lauf von
 * {@code daten/ereignis/<lauf>} (Höhe und, seit 15a, Strömung), Grund 1958 aus {@link Boden1958}.
 * <ul>
 * <li><b>Edrie</b> (MILLER60): kleine Bucht am Südufer rund eine Meile innerhalb des Eingangs, 5 Faden, 40 Faden Kette,
 * Anker festgehakt. Ort [G]: der Punkt mit 9,1 m Tiefe, der dem Südufer östlich von Harbor Point am nächsten liegt.</li>
 * <li><b>Badger</b>: Anchorage Cove am Nordufer, 4 Faden; 30 Faden Kette, Anker hält rund 15 kN [G].</li>
 * <li><b>Sunmore</b>: neben der Badger; geht 60 s nach dem Stoß ankerauf und läuft mit voller Maschine durch die Mitte des
 * Tors hinaus [G] (MILLER60: „under way near the entrance“, verloren).</li>
 * </ul>
 * Anker je aufwärts (Ost) des Boots, Bug nach Ost (Ebbstrom hinaus, [G]). Schritt 1/30 s, je zweiter als Bild (15 je s).
 * Ergebnis {@code daten/ereignis/boote/<lauf>/bahn_<boot>.bin} ({@link BootBahn}) und {@code BOOTE.txt}.
 * <p>Aufruf: {@code Boote1958 [daten] [lauf] [ende_s]}.
 */
public final class Boote1958 {
    /** Mitte des Tors (aus {@code daten/netz/welle/WELLE.txt}, eingang) und die Richtung hinaus (Szene). */
    static final double TOR_E = 0.5 * (344_591.8 + 346_295.0), TOR_N = 0.5 * (6_501_033.9 + 6_499_462.4);
    static final double[] HINAUS;

    static {
        double dx = 346_295.0 - 344_591.8, dn = 6_499_462.4 - 6_501_033.9, l = Math.hypot(dx, dn);
        double ne = -dn / l, nn = dx / l;                     // links der Linie
        if (nn > 0) { ne = -ne; nn = -nn; }                    // hinaus heißt nach Südwest
        HINAUS = new double[]{ne, -nn};                        // Szene: x Ost, z Süd
    }

    /** Ankerplatz: Szene x, z, Tiefe unter dem Pegel. */
    record Platz(double x, double z, double tiefe) { }

    /** Der Punkt mit Tiefe ziel ± 0,8 m, der (re, rn) am nächsten liegt; Raster 10 m im Umkreis r. */
    static Platz suchen(VorrechnungWasser w, double pegel, double re, double rn, double ziel, double r) {
        double best = Double.MAX_VALUE;
        Platz p = null;
        for (double de = -r; de <= r; de += 10)
            for (double dn = -r; dn <= r; dn += 10) {
                double x = re + de - Site.E0, z = -(rn + dn - Site.N0), t = pegel - w.bed(x, z);
                if (Math.abs(t - ziel) > 0.8) continue;
                double d = Math.hypot(de, dn) + 40 * Math.abs(t - ziel);
                if (d < best) { best = d; p = new Platz(x, z, t); }
            }
        if (p == null) throw new IllegalStateException(String.format(Locale.ROOT, "kein Ankerplatz mit %.1f m bei E %.0f N %.0f", ziel, re, rn));
        return p;
    }

    /** Ein Boot mit seiner Rechnung. */
    static final class Lauf {
        final String name;
        final Boat b;
        final Chain c;
        final BootBahn.Schreiber s;
        Platz platz;
        double y0, hubMax = -1e9, tHub, uberGrundMax = -1, wasserMax, abfahrt = Double.NaN;
        boolean draussen;
        double tDraussen = Double.NaN;
        double[] spitz;

        Lauf(String name, Boat b, Chain c) {
            this.name = name; this.b = b; this.c = c;
            s = new BootBahn.Schreiber(c == null ? 0 : c.nodes());
        }

        int zustand() {
            int z = 0;
            if (c != null && (c.broken() || !c.attached())) z |= BootBahn.GERISSEN;
            if (b.capsized()) z |= BootBahn.GEKENTERT;
            if (b.sunk()) z |= BootBahn.GESUNKEN;
            if (b.grounded()) z |= BootBahn.AUF_GRUND;
            if (b.throttle > 0) z |= BootBahn.MASCHINE;
            return z;
        }

        void bild(double t) {
            double[][] kn = null;
            if (c != null) { kn = new double[c.nodes()][]; for (int i = 0; i < kn.length; i++) kn[i] = c.node(i); }
            s.bild(t, b.position(), b.orientation(), b.flood(), zustand(), c == null ? -1 : c.brokenAt(), kn);
        }
    }

    public static void main(String[] a) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        File daten = new File(a.length > 0 ? a[0] : "daten");
        String lauf = a.length > 1 ? a[1] : "kette";
        long t0 = System.nanoTime();
        Boden1958 boden = new Boden1958(daten);
        try (VorrechnungWasser w = new VorrechnungWasser(daten, lauf, boden)) {
            double[] sp = w.spanne();
            double ende = a.length > 2 ? Double.parseDouble(a[2]) : sp[1];
            double pegel = w.pegel;
            System.out.printf("Boote 1958 über dem Lauf %s: Bilder %.0f … %.0f s, Pegel %+.2f m NAVD88, Strömung %s%n", lauf, sp[0], sp[1], pegel,
                    w.mitStroemung ? "ja" : "NEIN (Lauf vor 15a gerechnet: ant ereignis -Dlauf=" + lauf + " neu, sonst heben die Boote nur)");
            long q = SceneClock.BEBEN.toEpochMilli();
            System.out.printf("Gezeit beim Stoß (22:16 PST): %s [Elfin Cove harmonisch]%n", new Gezeiten().zeile(q));

            Lauf edrie = new Lauf("Edrie", Kutter.edrie(), Kutter.edrieKette());
            Chain bk = Chain.proofCoil(30 * 1.8288, 0.0095);
            bk.holding = 15_000;
            Lauf badger = new Lauf("Badger", Kutter.badger(), bk);
            Lauf sunmore = new Lauf("Sunmore", Kutter.sunmore(), null);
            List<Lauf> alle = List.of(edrie, badger, sunmore);

            edrie.platz = suchen(w, pegel, Site.HARBOR_POINT.e() + 1100, Site.HARBOR_POINT.n() + 150, Kutter.EDRIE_TIEFE, 700);
            badger.platz = suchen(w, pegel, Site.ANCHORAGE.e(), Site.ANCHORAGE.n(), Kutter.BADGER_TIEFE, 500);
            sunmore.platz = new Platz(badger.platz.x() - 15, badger.platz.z() + 45, pegel - w.bed(badger.platz.x() - 15, badger.platz.z() + 45));

            double tStart = Math.min(sp[0], 0) - 40;
            w.zeit(tStart);
            for (Lauf l : alle) {
                l.b.settle(w, l.platz.x(), l.platz.z(), 90);
                l.y0 = l.b.position()[1];
                if (l.c != null) {
                    double[] k = l.b.hull.fairlead(), f = l.b.toWorld(k[0], k[1], k[2]);
                    double h = l.platz.tiefe(), L = l.c.length, X = Math.sqrt(Math.max(1, (0.88 * L) * (0.88 * L) - h * h));
                    l.c.lay(w, f[0] + X, f[2], f);
                    l.b.attach(l.c);
                }
            }
            double dt = 1.0 / 30, tAb = 60, rueck = 1000;
            double[] ziel1 = {TOR_E - Site.E0, -(TOR_N - Site.N0)}, ziel2 = {ziel1[0] + rueck * HINAUS[0], ziel1[1] + rueck * HINAUS[1]};
            int schritt = 0;
            for (double t = tStart; t <= ende + 1e-9; t += dt, schritt++) {
                w.zeit(t);
                // Sunmore: ankerauf und hinaus
                Boat s = sunmore.b;
                if (t >= tAb) {
                    if (Double.isNaN(sunmore.abfahrt)) sunmore.abfahrt = t;
                    s.throttle = 1;
                    double[] p = s.position();
                    boolean ueberTor = (p[0] - ziel1[0]) * HINAUS[0] + (p[2] - ziel1[1]) * HINAUS[1] > 0;
                    if (!ueberTor) s.steuern(ziel1[0], ziel1[1]); else s.steuern(ziel2[0], ziel2[1]);
                    if (!sunmore.draussen && (p[0] - ziel1[0]) * HINAUS[0] + (p[2] - ziel1[1]) * HINAUS[1] > rueck && !s.sunk()) { sunmore.draussen = true; sunmore.tDraussen = t; }
                }
                for (Lauf l : alle) {
                    l.b.step(w, dt);
                    double[] p = l.b.position();
                    double hub = p[1] - l.y0;
                    if (hub > l.hubMax) { l.hubMax = hub; l.tHub = t; }
                    l.wasserMax = Math.max(l.wasserMax, l.b.flood());
                    double bed = w.bed(p[0], p[2]);
                    if (bed > pegel + 1) {                                     // über Land
                        double kiel = p[1] - l.b.hull.draft - 0.3;
                        if (kiel - bed > l.uberGrundMax) { l.uberGrundMax = kiel - bed; l.spitz = new double[]{p[0] + Site.E0, Site.N0 - p[2], t}; }
                    }
                    if (schritt % 2 == 0) l.bild(t);
                }
                if (schritt % 900 == 0)
                    System.out.printf("  t %6.0f s  Edrie %+6.1f m  Badger %+6.1f m  Sunmore %+6.1f m  (%.0f s Rechnung)%n", t, edrie.hubMax, badger.hubMax, sunmore.hubMax, (System.nanoTime() - t0) / 1e9);
            }

            File out = new File(new File(new File(daten, "ereignis"), "boote"), lauf);
            List<String> txt = new ArrayList<>();
            txt.add("# Boote 1958 (LituyaBay1958 Phase 15a), com.dan.lituya.tools.Boote1958; Bahnen: com.dan.lituya.scene.BootBahn");
            txt.add("lauf=" + lauf);
            txt.add("stroemung=" + w.mitStroemung);
            txt.add(String.format(Locale.ROOT, "pegel_m=%.2f", pegel));
            txt.add(String.format(Locale.ROOT, "zeit=%.1f %.1f", tStart, ende));
            txt.add("gezeit=" + new Gezeiten().zeile(q));
            txt.add("boote=Edrie Badger Sunmore");
            System.out.println();
            for (Lauf l : alle) {
                l.s.schreiben(new File(out, "bahn_" + l.name.toLowerCase(Locale.ROOT) + ".bin"));
                double[] p = l.b.position();
                String k = l.name.toLowerCase(Locale.ROOT);
                txt.add(String.format(Locale.ROOT, "%s.anker=%.1f %.1f %.1f", k, l.platz.x() + Site.E0, Site.N0 - l.platz.z(), l.platz.tiefe()));
                txt.add(String.format(Locale.ROOT, "%s.hub_m=%.1f %.1f", k, l.hubMax, l.tHub));
                txt.add(String.format(Locale.ROOT, "%s.ueber_grund_m=%.1f", k, l.uberGrundMax));
                txt.add(String.format(Locale.ROOT, "%s.ende=%.1f %.1f %s", k, p[0] + Site.E0, Site.N0 - p[2], zustand(l)));
                if (l.c != null) txt.add(String.format(Locale.ROOT, "%s.kette=%.1f m, Spitze %.1f kN, %s", k, l.c.length, l.c.peak() / 1000,
                        l.c.broken() ? String.format(Locale.ROOT, "gerissen %.1f s", l.c.brokenTime() + tStart) : l.c.attached() ? "hält" : "los"));
                if (l == sunmore) txt.add(String.format(Locale.ROOT, "sunmore.draussen=%s %.1f", l.draussen, l.tDraussen));

                System.out.printf("%-8s Anker E %.0f N %.0f in %.1f m: höchster Hub %+.1f m bei %.0f s, Wasser im Rumpf bis %.1f t, Ende %s%n",
                        l.name, l.platz.x() + Site.E0, Site.N0 - l.platz.z(), l.platz.tiefe(), l.hubMax, l.tHub, l.wasserMax / 1000, zustand(l));
                if (l.c != null) System.out.printf("         Kette %.0f m: Spitze %.1f kN (Bruch %.1f kN), %s%n", l.c.length, l.c.peak() / 1000, l.c.breakingLoad / 1000,
                        l.c.broken() ? String.format("gerissen bei %.1f s", l.c.brokenTime() + tStart) : l.c.attached() ? "hält" : "Anker slippt");
                if (l.spitz != null) System.out.printf("         über Land: Kiel bis %.1f m über Grund (E %.0f N %.0f, %.0f s)%n", l.uberGrundMax, l.spitz[0], l.spitz[1], l.spitz[2]);
                if (l == sunmore) System.out.printf("         ab %.0f s unter Maschine; %s%n", l.abfahrt,
                        l.draussen ? String.format("1 km jenseits des Tors bei %.0f s", l.tDraussen) : "nicht 1 km jenseits des Tors");
            }
            Files.write(new File(out, "BOOTE.txt").toPath(), txt, StandardCharsets.UTF_8);
            System.out.printf("%nnach %s (%.0f s)%n", out, (System.nanoTime() - t0) / 1e9);
        }
    }

    static String zustand(Lauf l) {
        Boat b = l.b;
        return b.sunk() ? "gesunken" : b.capsized() ? "gekentert" : b.grounded() ? "auf Grund" : "schwimmt";
    }
}
