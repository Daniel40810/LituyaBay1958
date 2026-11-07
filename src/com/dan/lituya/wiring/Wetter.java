package com.dan.lituya.wiring;

import com.dan.cloud.CloudType;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Wetterlage;
import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.util.List;
import java.util.Locale;

/**
 * Werkbuch Welle W7 „Wetter“ (5a.2): die Wetterregie der Bucht – ein Zustand für alles. {@code com.dan.weather} führt
 * Regie über die Zahlen (Wind, Böen, Bedeckung, Regen, Sicht, Feuchte, Nässe); diese Klasse wählt die Lage nach dem
 * Zeitstand (WF13 A: 1958 fest, heute frei), lässt Wechsel in der eingestellten Zeit gleiten statt zu springen und führt
 * die Werte mit, die die Regie nicht kennt (Wolken am Gelände, Seenebel und sein Vorstoß, Niederschlag unter den
 * Wolken). Licht, Wasser, Wald, Bäche, Fälle, Nebel, Regen und Klang lesen denselben {@link Zustand}.
 * <p>
 * Steht die Uhr (Standbild, Zeitraffer 0), gilt eine neue Lage sofort; läuft sie, gleitet das Wetter in
 * {@link #uebergangMin} Minuten Szenenzeit hinüber (im Zeitraffer sieht man es aufziehen).
 */
public final class Wetter {
    /** Monatsmittel der Lufttemperatur am Golf von Alaska (°C, Januar bis Dezember) [G, grob nach Yakutat]. */
    public static final double[] KLIMA = {-2.0, -1.0, 0.5, 3.5, 7.0, 10.0, 12.0, 12.0, 9.5, 5.5, 1.5, -1.0};

    /** Die Regie (Renderer.jar). */
    public final Weather regie = new Weather();
    /** Aus: Wind, Sicht und Himmel wie bis W6 (Prüfungen, Vergleiche). */
    public volatile boolean an = !Boolean.getBoolean("lituya.ohneWetter");
    /** Die Lage für „heute“ (1958 gelten die festen Lagen). */
    public volatile Wetterlage heute = Wetterlage.KLAR;
    /** Dauer eines Wechsels in Minuten Szenenzeit [G]. */
    public volatile double uebergangMin = 20;
    /** Zeit in der Lage für den nächsten Start (Stunden; Bildbank „wetterzeit“), NaN = jetzt. */
    public volatile double seitStunden = Double.NaN;

    private Wetterlage aktiv;
    private double beginn = Double.NaN, letzteT = Double.NaN;
    /** Gleitende Werte der Lage: Basis, Bank (4), Kappe (2), Quellwolken, Lee, Nebelmenge, Nebelobergrenze, Sicht im Nebel, Schwaden, Niederschlag, Anteil des Wechsels. */
    private final double[] glatt = new double[16];
    private CloudType artAlt = CloudType.CUMULUS, artNeu = CloudType.CUMULUS;
    private double basisAlt = Double.NaN;
    private volatile Zustand zustand;

    public Wetter() {
        regie.zone = SceneClock.ZONE;
        regie.climate = KLIMA.clone();
        regie.refHeight = 0;
        regie.seed = 1958;
        regie.transitionH = 0.25;
        regie.wetFilm = 0.4;
    }

    /**
     * Ein Zustand für alles: Lage, wie weit der Wechsel ist, Werte der Regie, Wolken, Nebel, Regen, Nässe. Wind in 10 m,
     * Richtung, aus der er kommt; Sicht in km (mit Dunst); Seegang in der Bucht in Beaufort (geschützt, eine Stufe ruhiger
     * als draußen [G]).
     */
    public record Zustand(Wetterlage lage, double seit, double wechsel, Weather.Snapshot regie, double wind, double windVon, double boe,
                          double sichtKm, double temperatur, double nass, double regen, double abfluss, int bucht,
                          CloudType wolkenart, double achtel, double basis, double hoch, double niederschlag,
                          double bank, double bankUnten, double bankOben, double bankWeit, double kappe, double kappeAb,
                          double konvektion, double lee, double nebel, double nebelOben, double nebelSicht, double nebelSchwaden,
                          double vorstoss, boolean an) {
        /** Gibt es etwas am Himmel (Wolken oder hohe Schicht)? */
        public boolean wolken() { return an && (achtel > 0.02 || hoch > 0.01 || bank > 0.01 || kappe > 0.01); }
        /** Liegt Seenebel? */
        public boolean seenebel() { return an && nebel > 0.02 && !Double.isNaN(nebelOben); }
        public String zeile() {
            return String.format(Locale.GERMAN, "Wetter: %s%s · Wind %.0f m/s aus %03.0f° (Bft %d) · %s %.1f/8 · Sicht %.0f km · Regen %.1f mm/h · Nässe %.0f %%",
                    lage.label, wechsel < 0.99 ? String.format(Locale.GERMAN, " (Wechsel %.0f %%)", wechsel * 100) : "", wind, windVon,
                    Weather.beaufort(wind), wolkenart.label, achtel, sichtKm, regen, nass * 100);
        }
    }

    public Zustand zustand() { return zustand; }

    /** Die Lage, die jetzt gilt. */
    public Wetterlage aktiv() { return aktiv; }

    /** Lage für heute wählen (gilt sofort, wenn die Uhr steht, sonst über {@link #uebergangMin}). */
    public void waehle(Wetterlage l) { if (l != null) heute = l; }

    private volatile boolean sofort;

    /** Die gewählte Lage beim nächsten Schritt ohne Übergang setzen (Knopf „sofort“). */
    public void sofort() { sofort = true; aktiv = null; }

    /**
     * Je Bild: Lage nach dem Zeitstand, Regie bis zur Uhr, gleitende Werte, Zustand. standbild: die Uhr steht (Bildbank,
     * Zeitraffer 0) – dann gilt eine neue Lage sofort.
     */
    public void schritt(SceneClock clock, double realDt, boolean standbild) {
        double t = clock.epochMillis() / 1000.0;
        Wetterlage soll = Wetterlage.fuer(clock.zeitstand, heute);
        boolean sprung = Double.isNaN(letzteT) || Math.abs(t - letzteT) > 6 * 3600;
        boolean steht = standbild || clock.lapse == 0 || sofort;
        sofort = false;
        regie.inertia = Math.max(0, uebergangMin) / 60.0;
        if (soll != aktiv || sprung) {
            Scenario s = szenario(soll);
            double seit = Double.isNaN(seitStunden) ? 0 : seitStunden * 3600;
            seitStunden = Double.NaN;
            if (aktiv == null || sprung || steht) {
                regie.start(s, t - seit);
                regie.update(t - seit, 0);
                setzen(soll, 1);
            } else {
                regie.change(s, t);
                artAlt = artNeu;
                basisAlt = glatt[0];
                glatt[15] = 0;
            }
            artNeu = soll.wolkenart();
            aktiv = soll;
            beginn = t - seit;
        }
        Weather.Snapshot r = regie.update(t, Math.max(0, realDt));
        double dt = Double.isNaN(letzteT) ? 0 : Math.max(0, t - letzteT);
        letzteT = t;
        // die Werte, die die Regie nicht kennt, gleiten wie die Bedeckung (0,6 h · Trägheit)
        double k = regie.inertia <= 0 || steht ? 1 : 1 - Math.exp(-dt / (0.6 * 3600 * regie.inertia));
        double[] ziel = ziel(aktiv);
        for (int i = 0; i < 15; i++) glatt[i] += (ziel[i] - glatt[i]) * k;
        glatt[15] += (1 - glatt[15]) * k;
        zustand = bauen(r, t);
    }

    /** Einmal ganz auf die Lage (Start, Sprung, Standbild). */
    private void setzen(Wetterlage l, double anteil) {
        double[] z = ziel(l);
        System.arraycopy(z, 0, glatt, 0, 15);
        glatt[15] = anteil;
        artAlt = artNeu = l.wolkenart();
        basisAlt = glatt[0];
    }

    /** Zielwerte der Lage für die gleitenden Werte (ohne Nebel: Menge 0, die Grenze bleibt, wo sie war). */
    private double[] ziel(Wetterlage l) {
        double basis = Double.isNaN(l.basis) ? l.wolkenart().base : l.basis;
        boolean nebel = !Double.isNaN(l.nebelOben);
        return new double[]{basis, l.bank, l.bankUnten > 0 ? l.bankUnten : glatt[2], l.bankOben > 0 ? l.bankOben : glatt[3],
                l.bankWeit > 0 ? l.bankWeit : Math.max(200, glatt[4]), l.kappe, l.kappeAb > 0 ? l.kappeAb : Math.max(3500, glatt[6]),
                l.konvektion, l.lee, nebel ? 1 : 0, nebel ? l.nebelOben : glatt[10], nebel ? l.nebelSicht : Math.max(100, glatt[11]),
                l.nebelSchwaden, l.niederschlag, 0};
    }

    private Zustand bauen(Weather.Snapshot r, double t) {
        Wetterlage l = aktiv;
        double w = glatt[15];
        // die Art der Schicht wechselt in der Mitte des Übergangs (eine Schicht zur Zeit); bis dahin bleibt die alte
        CloudType art = w < 0.5 ? artAlt : artNeu;
        double basis = glatt[0];
        double abfluss = abfluss(r.rain(), t);
        // Seegang in der Bucht eine Stufe ruhiger als draußen, mindestens Bft 1; bei Windstille (unter 0,3 m/s, seit W7b
        // „Stiller Regen“) Bft 0 – die Lagen bis W7 bleiben, wie sie waren
        int bft = Weather.beaufort(r.wind());
        int bucht = bft == 0 ? 0 : Math.max(1, bft - 1);
        return new Zustand(l, t - beginn, w, r, r.wind(), r.fromDeg(), r.gustNow(), r.effectiveVisibilityKm(), r.temperature(), r.wetness(),
                r.rain(), abfluss, bucht, art, r.oktas(), basis, r.high(), glatt[13], glatt[1], glatt[2], glatt[3], glatt[4], glatt[5], glatt[6],
                glatt[7], glatt[8], glatt[9], glatt[10], glatt[11], glatt[12], l.vorstoss, an);
    }

    private double abflussWert = 0, abflussT = Double.NaN;

    /** Abfluss in die Bäche: der Regen, rund 40 min verzögert (Hänge, Moos, Schnee) [G], als mm/h für {@code BaecheGl}. */
    private double abfluss(double regen, double t) {
        if (Double.isNaN(abflussT) || Math.abs(t - abflussT) > 6 * 3600) abflussWert = regen;
        else abflussWert += (regen - abflussWert) * (1 - Math.exp(-Math.max(0, t - abflussT) / 2400));
        abflussT = t;
        return abflussWert;
    }

    /** Ein Regiebuch der Regie aus einer Lage: ein Schritt von 1 000 Stunden mit allen Zielwerten. */
    public static Scenario szenario(Wetterlage l) {
        Weather.Var[] vs = Weather.Var.values();
        Object[] paare = new Object[vs.length * 2];
        for (int i = 0; i < vs.length; i++) { paare[i * 2] = vs[i]; paare[i * 2 + 1] = l.regie[i]; }
        return new Scenario(l.label, 0, 1, List.of(Scenario.Step.of(1000, l.phase, l.label, "", paare)));
    }
}
