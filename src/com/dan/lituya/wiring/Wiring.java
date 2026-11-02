package com.dan.lituya.wiring;

import java.util.Locale;

/**
 * Die Verdrahtung der App (Paketvertrag Punkt 4, aus Timmendorf, für Phase 1 neu und schlank): hier und nur hier reden
 * die Packages miteinander. Kein Package kennt ein anderes oder die Szene; die App reicht über ihre kleinen
 * Schnittstellen hindurch, was es braucht (Werkbuch 5, Abhängigkeitsbild).
 * <p>
 * Phase 1 hat noch kein Package zu verdrahten, aber die offenen Werte, an die sie anschließen, stehen schon hier:
 * <ul>
 * <li>{@link #pegel}: Wasserstand über NAVD88 (Phase 1 fest 0, ab Phase 5a aus {@code tide} nach COOPS, 11.1).</li>
 * <li>{@link #windSpeed}, {@link #windFromDeg}: Wind 10 m über Grund (ab Phase 5a aus der Wetterregie, „Sommerabend 1958“).</li>
 * </ul>
 * Die Reihenfolge der Anschlüsse (Werkbuch 5 und 14): {@code clipmap}/{@code surface} (Phase 4, 5) ← Gelände aus
 * {@code daten/}; {@code Quake.Slope} → {@code landslide} (8, 9); {@code Landslide.Water} → {@code impact} (10);
 * {@code Impact.Far} → {@code wave} (11); {@code wave.runupAt} → {@code succession} (14); {@code wave}/{@code ocean} →
 * {@code debris} (12) und {@code vessel} (15). Die Netze ({@code com.dan.lituya.netz}) gehen nie durch diese Klasse:
 * kein Package kennt Oracle (Paketvertrag 13).
 */
public final class Wiring {
    public final Clock clock;
    /** Wasserstand über NAVD88 (m): Phase 1 fest, ab Phase 5a Tide nach COOPS. */
    public volatile double pegel = 0;
    /** Wind 10 m über Grund (m/s) und Richtung, aus der er kommt (Grad); Phase 1 offene Werte [G]: ruhiger Abend. */
    public volatile double windSpeed = 2, windFromDeg = 135;

    public Wiring(Clock clock) { this.clock = clock; }

    /** Eine Zeile für das HUD: was angeschlossen ist. */
    public String zeile() {
        return String.format(Locale.GERMAN, "Verdrahtung: Pegel %+.2f m NAVD88 (fest bis Phase 5a) · Wind %.0f m/s aus %03.0f° [G] · Packages ab Phase 4",
                pegel, windSpeed, windFromDeg);
    }
}
