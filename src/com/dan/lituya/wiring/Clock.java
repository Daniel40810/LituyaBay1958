package com.dan.lituya.wiring;

/**
 * Zeit der Szene. Gibt: die App ({@code SceneClock}). Nimmt: alle Packages, vor allem {@code sky} (Sonne und Dämmerung
 * der Julinacht, 9.10), {@code tide} (Gezeiten nach COOPS, 11.1), {@code quake} und {@code landslide} (die Kette ab dem
 * ersten Stoß) und {@code succession} (68 Jahre Wiederbewaldung). Die Laufzeit {@code t} läuft auch bei stehender Uhr
 * weiter (Wellen, Strähnen), die Uhrzeit {@code epochMillis} folgt dem Zeitraffer.
 */
public interface Clock {
    /** Laufzeit der Szene in s und letzter Schritt in s. */
    double t();
    double dt();

    /** Szenenzeit in ms seit 1970 (UTC). */
    double epochMillis();

    /** Ort in Grad (Mitte des Kerns). */
    double latDeg();
    double lonDeg();
}
