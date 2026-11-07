package com.dan.lituya.scene;

import com.dan.cloud.CloudType;
import com.dan.weather.Weather;

/**
 * Werkbuch Welle W7 „Wetter“ (5a.1): die Wetterlagen der Bucht. Jede Lage ist ein Satz Zielwerte für die Wetterregie
 * ({@code com.dan.weather}: Wind, Bedeckung, Regen, Sicht, …) und für die Bausteine, die das Gelände kennen
 * ({@code com.dan.cloud}: Wolkenbank am Hang, Kappe über dem Fairweather, Quellwolken über Land, Lücken im Lee;
 * {@code com.dan.fog}: Seenebel, der durch den Eingang vorstößt). Gestaltet [G] nach dem Klima am Golf von Alaska:
 * im Sommer kühle, feuchte Luft vom Meer, Seenebel und tiefe Wolken an der Küste, Regen mit Südost bei Tiefs,
 * die Kette über 4 000 m oft mit Kappe.
 * <p>
 * WF13 A: Am 9.7.1958 gilt {@link #KLAR} nach den Zeugen („the weather was clear, with high scattered clouds“, glatte
 * See; Miller 1960), ebenso am Morgen danach und in den Jahren danach: Miller flog am Morgen des 10.7. über die Bucht und
 * fotografierte sie für den Vergleich mit Berduscos Bildern vom 7.7. (Runde 1b; vorher [G] ein grauer Morgen, der der
 * Quelle widerspricht). Frei wählbar sind die Lagen nur „heute“.
 */
public enum Wetterlage {
    /** 9.7.1958 nach den Zeugen, der Morgen danach und heute: klar, still, Spiegel, Dunst nur in der Tiefe; Wind und Sicht wie seit W1. */
    KLAR("Klar und still", "9.7.1958 nach den Zeugen: klar, glatte See (Miller 1960); am Morgen danach und heute dasselbe Wetter",
            Weather.Phase.KLAR, v(2.0, 135, 0.10, 0.0, 0.0, 0, 0.0, 60, 0.0, 1018, 12, 75, 0.6),
            w(Double.NaN, 0, 0, 0, 0, 0, 0, 0, 0),
            n(Double.NaN, 0, 0, Double.NaN, 0)),
    /** Heute: grauer Morgen – geschlossene tiefe Decke, die in den Hängen hängt, Dunst über dem Wasser, fahles Licht [G]. */
    GRAUER_MORGEN("Grauer Morgen", "tiefe Decke in den Hängen, Dunst über dem Wasser, fahles Licht [G]",
            Weather.Phase.INVERSION, v(1.5, 150, 0.08, 7.0, 0.10, 1, 0.0, 25, 0.10, 1012, 9, 92, 0.6),
            w(750, 0.55, 300, 900, 260, 0, 0, 0.15, 0.10),
            n(70, 1600, 0.6, Double.NaN, 0)),
    /** Heute: Sonne, Quellwolken über der Kette (über dem kalten Meer kaum), Kappe am Fairweather, Schatten ziehen über die Hänge. */
    QUELLWOLKEN("Sonne und Quellwolken", "Quellwolken bauen sich über der Kette auf, Kappe am Fairweather, Schatten auf den Hängen [G]",
            Weather.Phase.KLAR, v(4.0, 225, 0.18, 3.5, 0.15, 0, 0.0, 50, 0.0, 1016, 15, 70, 0.8),
            w(1400, 0.35, 1100, 2400, 420, 0.85, 3900, 0.9, 0.25),
            n(Double.NaN, 0, 0, Double.NaN, 0.12)),
    /** Heute: eine Seenebelbank stößt mit dem Südwest durch den Eingang in die Bucht, die Gipfel ragen in die Sonne. */
    KUESTENNEBEL("Küstennebel zieht ein", "Seenebel bis rund 230 m stößt von See durch den Eingang in die Bucht vor; darüber Sonne, Wolken an der Kette [G]",
            Weather.Phase.NEBEL, v(3.5, 225, 0.10, 1.5, 0.05, 0, 0.0, 35, 0.05, 1020, 11, 96, 0.7),
            w(1300, 0.25, 900, 1700, 380, 0.7, 3900, 1.0, 0.2),
            n(230, 140, 0.45, 1.2, 0)),
    /** Heute: Südost am Berg, geschlossene Regendecke, Wolken hängen tief in den Hängen, Regen mit Ringen, nasser Fels, volle Bäche. */
    REGEN("Regen am Berg", "Südost staut sich an der Kette: Nimbostratus, Wolken bis tief in die Hänge, Landregen, nasser Fels, die Bäche schwellen an [G]",
            Weather.Phase.STAU, v(8.0, 140, 0.22, 8.0, 0.0, 3, 3.5, 9, 0.10, 1003, 10, 97, 1.6),
            w(650, 0.9, 120, 760, 300, 0, 0, 0, 0.3),
            n(Double.NaN, 0, 0, Double.NaN, 0.9)),
    /**
     * Heute, seit W7b (WF29 A): stiller Regen am Abend – der Wind ist nach dem Durchzug eingeschlafen, unter einer
     * geschlossenen Decke fällt leiser Landregen senkrecht auf die glatte Bucht. Bft 0 im Becken: Hier stehen die Ringe
     * (com.dan.precip), die im Wind von {@link #REGEN} in den Wellen untergehen [G].
     */
    STILLER_REGEN("Stiller Regen", "Abend nach dem Durchzug: Windstille unter geschlossener Decke, leiser Landregen um 2,5 mm/h fällt senkrecht auf die glatte Bucht, jeder Tropfen zieht Ringe [G]",
            Weather.Phase.ABKLINGEND, v(0.2, 140, 0.03, 8.0, 0.0, 3, 2.5, 14, 0.08, 1006, 11, 96, 0.3),
            w(900, 0.5, 250, 1000, 300, 0, 0, 0, 0.15),
            n(Double.NaN, 0, 0, Double.NaN, 0.8)),
    /** Heute, Herbst: Sturm aus Südost, tiefe zerrissene Wolken, Regen in Schauern, hoher Seegang draußen. */
    STURM("Sturm aus Südost", "Herbststurm: Bft 8 aus Südost, tiefe Fetzen unter der Decke, Regen, hoher Seegang an der Barre [G]",
            Weather.Phase.STURM, v(19.0, 145, 0.30, 7.5, 0.0, 3, 6.0, 5, 0.15, 985, 8, 95, 4.0),
            w(480, 0.8, 100, 620, 260, 0, 0, 0, 0.4),
            n(Double.NaN, 0, 0, Double.NaN, 1.0));

    public final String label, beschreibung;
    public final Weather.Phase phase;
    /** Zielwerte der Regie in der Reihenfolge von {@link Weather.Var} (ohne Gewitter: 0). */
    public final double[] regie;
    /** Wolken am Gelände: Basis der Schicht (m, NaN = aus der Art), Bank, ihre Unter- und Obergrenze, Reichweite, Kappe, ihr Kondensationsniveau, Quellwolken über Land, Lee. */
    public final double basis, bank, bankUnten, bankOben, bankWeit, kappe, kappeAb, konvektion, lee;
    /** Nebel: Obergrenze des Seenebels (m, NaN = keiner), Sicht darin (m), Schwaden, Vorstoß in m/s (NaN: liegt schon überall), Niederschlag unter den Wolken 0..1. */
    public final double nebelOben, nebelSicht, nebelSchwaden, vorstoss, niederschlag;

    Wetterlage(String label, String beschreibung, Weather.Phase phase, double[] regie, double[] wolken, double[] nebel) {
        this.label = label;
        this.beschreibung = beschreibung;
        this.phase = phase;
        this.regie = regie;
        basis = wolken[0]; bank = wolken[1]; bankUnten = wolken[2]; bankOben = wolken[3]; bankWeit = wolken[4];
        kappe = wolken[5]; kappeAb = wolken[6]; konvektion = wolken[7]; lee = wolken[8];
        nebelOben = nebel[0]; nebelSicht = nebel[1]; nebelSchwaden = nebel[2]; vorstoss = nebel[3]; niederschlag = nebel[4];
    }

    /** Wolkenart der Lage (wie {@link Weather.CloudKind}). */
    public CloudType wolkenart() { return CloudType.values()[(int) Math.round(regie[Weather.Var.CLOUD.ordinal()])]; }

    /** Die Lage, die zum Zeitstand gehört (WF13 A): 1958 fest, heute die gewählte. */
    public static Wetterlage fuer(SceneClock.Zeitstand z, Wetterlage heute) {
        return switch (z) {
            case VOR, WELLE, DANACH -> KLAR;
            case HEUTE -> heute == null ? KLAR : heute;
        };
    }

    /** Die Lagen, die „heute“ wählbar sind. */
    public static Wetterlage[] heute() { return new Wetterlage[]{KLAR, QUELLWOLKEN, KUESTENNEBEL, GRAUER_MORGEN, REGEN, STILLER_REGEN, STURM}; }

    /** Name (Teil des Labels oder der Konstante, ohne Groß- und Kleinschreibung, ü = ue) oder null. */
    public static Wetterlage byName(String s) {
        if (s == null || s.isBlank()) return null;
        String k = norm(s);
        for (Wetterlage l : values()) if (norm(l.name()).equals(k) || norm(l.label).equals(k)) return l;
        for (Wetterlage l : values()) if (norm(l.name()).startsWith(k) || norm(l.label).startsWith(k)) return l;
        return null;
    }

    static String norm(String s) {
        return s.trim().toLowerCase(java.util.Locale.ROOT).replace("ü", "ue").replace("ä", "ae").replace("ö", "oe").replace("ß", "ss").replace('_', ' ').replace('-', ' ');
    }

    @Override public String toString() { return label; }

    // ------------------------------------------------------------------ Tabellenhelfer
    /** Wind, Richtung, Böigkeit, Achtel, hohe Schicht, Wolkenart, Regen, Sicht, Dunst, Druck, Temperatur, Feuchte, Dünung. */
    private static double[] v(double wind, double dir, double gust, double oktas, double high, int cloud, double rain, double vis, double fog,
                              double p, double t, double rh, double swell) {
        return new double[]{wind, dir, gust, oktas, high, cloud, rain, vis, fog, p, t, rh, swell, 0};
    }

    private static double[] w(double basis, double bank, double bankUnten, double bankOben, double bankWeit, double kappe, double kappeAb,
                              double konvektion, double lee) {
        return new double[]{basis, bank, bankUnten, bankOben, bankWeit, kappe, kappeAb, konvektion, lee};
    }

    private static double[] n(double oben, double sicht, double schwaden, double vorstoss, double niederschlag) {
        return new double[]{oben, sicht, schwaden, vorstoss, niederschlag};
    }
}
