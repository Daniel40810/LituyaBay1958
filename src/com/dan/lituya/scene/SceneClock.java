package com.dan.lituya.scene;

import com.dan.lituya.wiring.Clock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Die Uhren der Szene (Werkbuch 4.5, aus Timmendorf). Drei Uhren:
 * <ul>
 * <li><b>Weltzeit</b>: Datum und Uhrzeit vor Ort, für Sonne, Tide und Licht. Das Beben kam am 9. Juli 1958 um 22:16 Uhr
 * Ortszeit, das ist der 10. Juli 06:16 UTC (FRITZ01, COMCAT): Ortszeit war UTC−8. Die Zone {@code America/Juneau} der
 * Zeitzonendatenbank gibt genau das (bis 1983 Pacific Standard Time, seither Alaska-Zeit, im Sommer ebenfalls UTC−8).</li>
 * <li><b>Simulationszeit</b>: Sekunden ab dem ersten Stoß ({@link #BEBEN}), negativ davor; die Zeitleiste 2.2 ist
 * darin geschrieben.</li>
 * <li><b>Zeitstand</b>: vor, die Welle, danach, heute ({@link Zeitstand}, F4 A).</li>
 * </ul>
 * Start: „vor“, 9. Juli 1958, 22:00 Uhr Ortszeit, der Abend vor Anker; die Sonne geht um 22:14 unter (gerechnet; Werkbuch 2.1 nannte 22:04).
 * Zeitraffer bis 3600-fach. Die Uhr ist zugleich die Schnittstelle {@link Clock}, über die alle Packages Zeit bekommen.
 */
public final class SceneClock implements Clock {
    public static final double MAX_LAPSE = 3600;
    /** Ortszeit Lituya Bay: 1958 UTC−8 (PST), heute Alaska-Zeit (im Sommer UTC−8). */
    public static final ZoneId ZONE = ZoneId.of("America/Juneau");
    /** Der erste Stoß: 10. Juli 1958, 06:16 UTC = 9. Juli 22:16 Ortszeit (FRITZ01 S. 6, COMCAT). Sekunde 0 [zu prüfen in 0b]. */
    public static final Instant BEBEN = Instant.parse("1958-07-10T06:16:00Z");

    /** Die vier Zeitstände (F4 A, Werkbuch 10.1) mit ihrem Datum und ihrer Uhrzeit (Ortszeit). */
    public enum Zeitstand {
        VOR("vor", "Abend vor Anker, 9.7.1958", LocalDate.of(1958, 7, 9), 22.0),
        WELLE("die Welle", "das Beben um 22:16 und zehn Minuten", LocalDate.of(1958, 7, 9), 22 + 16 / 60.0),
        DANACH("danach", "Morgen des 10.7.1958 [G]", LocalDate.of(1958, 7, 10), 6.0),
        HEUTE("heute", "Sommerabend 2026 [G]", LocalDate.of(2026, 7, 9), 22.0);

        public final String label, text;
        public final LocalDate date;
        public final double hour;

        Zeitstand(String label, String text, LocalDate date, double hour) {
            this.label = label;
            this.text = text;
            this.date = date;
            this.hour = hour;
        }

        @Override public String toString() { return label; }
    }

    public static final Zeitstand START = Zeitstand.VOR;
    public static final LocalDate START_DATE = START.date;
    public static final double START_HOUR = START.hour;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.u", Locale.GERMANY),
            TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY), SEC = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.GERMANY);

    /** Szenenzeit in ms seit 1970 (UTC), als double, damit kleine Schritte nicht verloren gehen. */
    private volatile double epochMs;
    /** Zeitraffer: Szenensekunden je echter Sekunde (0 = Uhr steht; ab Phase 13b negativ = rückwärts, 0,05 = Zeitlupe 1:20). */
    public volatile double lapse = 1;
    /** Der Zeitstand, den die Szene zeigt (Phase 1: nur Uhr und Anzeige; Gelände und Wasser je Stand ab Phase 5a). */
    public volatile Zeitstand zeitstand = START;
    /** Ort für Sonne, Mond und Sterne. */
    public volatile double lat = Site.LAT, lon = Site.LON;
    /** Laufzeit der Szene in s (für Wellen und Animation; läuft auch bei stehender Uhr) und letzter Schritt. */
    private double t, dt;

    public SceneClock() { this(START_DATE, START_HOUR); }

    public SceneClock(LocalDate date, double hour) { set(date, hour); }

    /** Datum und Uhrzeit (Stunden, Ortszeit) setzen. */
    public synchronized void set(LocalDate date, double hour) {
        hour = Math.max(0, Math.min(24 - 1e-9, hour));
        int sec = (int) Math.floor(hour * 3600);
        ZonedDateTime z = ZonedDateTime.of(date, LocalTime.ofSecondOfDay(sec), ZONE);
        epochMs = z.toInstant().toEpochMilli() + (hour * 3600 - sec) * 1000;
    }

    /** Auf einen Zeitstand springen: dessen Datum und Uhrzeit. */
    public synchronized void set(Zeitstand z) {
        zeitstand = z;
        set(z.date, z.hour);
    }

    public synchronized void setHour(double hour) { set(date(), hour); }

    /** Läuft um dt echte Sekunden weiter. */
    public synchronized void step(double realDt) {
        dt = realDt;
        t += realDt;
        epochMs += realDt * Math.max(-MAX_LAPSE, Math.min(MAX_LAPSE, lapse)) * 1000;
    }

    public synchronized void shiftDays(int days) { epochMs += days * 86_400_000.0; }

    public ZonedDateTime local() { return Instant.ofEpochMilli((long) Math.floor(epochMs)).atZone(ZONE); }
    public LocalDate date() { return local().toLocalDate(); }
    public double hour() {
        ZonedDateTime z = local();
        return z.getHour() + z.getMinute() / 60.0 + (z.getSecond() + (epochMs - Math.floor(epochMs / 1000) * 1000) / 1000) / 3600.0;
    }

    /** Simulationszeit: Sekunden seit dem ersten Stoß (negativ davor). */
    public double simSeconds() { return (epochMs - BEBEN.toEpochMilli()) / 1000.0; }

    /** „t = −16:00“, „t = +1:35“, „t = +68 Jahre“ */
    public String simLabel() {
        double s = simSeconds();
        double a = Math.abs(s);
        if (a >= 2 * 86_400 * 365.25) return String.format(Locale.GERMAN, "t = %s%.0f Jahre", s < 0 ? "−" : "+", a / (86_400 * 365.25));
        if (a >= 2 * 86_400) return String.format(Locale.GERMAN, "t = %s%.1f Tage", s < 0 ? "−" : "+", a / 86_400);
        if (a >= 3600) return String.format(Locale.GERMAN, "t = %s%d:%02d:%02d", s < 0 ? "−" : "+", (long) a / 3600, ((long) a / 60) % 60, (long) a % 60);
        return String.format(Locale.GERMAN, "t = %s%d:%02d", s < 0 ? "−" : "+", (long) a / 60, (long) a % 60);
    }

    /** Sonnenhöhe und -azimut in Grad am Ursprung (Mitte des Kerns). */
    public double[] sun() { return Sun.position(epochMs, lat, lon); }

    /** Szenenzeit direkt setzen (ms seit 1970, UTC). */
    public synchronized void setEpochMillis(double ms) { epochMs = ms; }

    /** „9.7.1958 22:00 PST“ */
    public String label() { return dateLabel() + " " + timeLabel(); }
    public String dateLabel() { return DATE.format(date()); }
    public String timeLabel() { return TIME.format(local()) + " " + zoneLabel(); }
    /** Mit Sekunden, für die Zeitleiste des Ereignisses. */
    public String timeLabelSec() { return SEC.format(local()) + " " + zoneLabel(); }

    /**
     * „PST“ bis 1983 (Juneau und Lituya Bay lagen auf Pacific Standard Time, UTC−8), danach „AKDT“ (Sommer, UTC−8) oder
     * „AKST“ (UTC−9). Die Abkürzungen von Java selbst sagen für 1958 „AKST“ und wären falsch.
     */
    public String zoneLabel() {
        Instant i = Instant.ofEpochMilli((long) epochMs);
        if (local().getYear() < 1983) return ZONE.getRules().isDaylightSavings(i) ? "PDT" : "PST";
        return ZONE.getRules().isDaylightSavings(i) ? "AKDT" : "AKST";
    }

    // ------------------------------------------------------------------ Clock
    @Override public double t() { return t; }
    @Override public double dt() { return dt; }
    @Override public double epochMillis() { return epochMs; }
    @Override public double latDeg() { return lat; }
    @Override public double lonDeg() { return lon; }
}
