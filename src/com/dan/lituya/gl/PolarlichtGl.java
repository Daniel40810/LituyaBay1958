package com.dan.lituya.gl;

import com.dan.aurora.Aurora;
import com.dan.aurora.Emission;
import com.dan.aurora.Ort;
import com.dan.aurora.Vorhang;
import com.dan.aurora.gl.AuroraGl;
import com.dan.lituya.scene.SceneClock;
import com.dan.lituya.scene.Site;
import com.dan.sky.Sky;
import com.jogamp.opengl.GL4;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Werkbuch Welle W12 „Polarlicht“: das Nordlicht über der Bucht aus {@code com.dan.aurora}, im Bedienfeld POLARLICHT
 * zu- und abschaltbar. Ort ist Lituya Bay (magnetisch 61,9°, Inklination 75°, Deklination 29° im Jahr 1958 und 18°
 * heute), Datum und Weltzeit kommen aus der Uhr der Szene (wahlweise fest), die Stärke aus Preset oder Kp. Das Oval
 * sagt, wo die Vorhänge stehen: bei Kp 2 weit im Norden hinter der Kette, ab Kp 4 über der Bucht. Gezeichnet wird mit
 * {@link AuroraGl} additiv hinter den Himmel von {@code com.dan.sky}, in dessen Einheiten (eine Einheit =
 * {@link Sky#CD_PER_UNIT} cd/m²) und mit der Vorbelichtung der Szene, dazu ins Spiegelbild der See und ins Rundumbild.
 * Im Sommer 1958 steht die Sonne nie tiefer als −12°: das Polarlicht geht dann im Himmelslicht unter, und der Block
 * sagt es; der Knopf „Winternacht“ stellt die Uhr auf den 20.12.2026, 23 Uhr.
 * <p>
 * Die Tabellen der Vorhänge (Drift, Falten, Strahlen) rechnet ein Hilfsfaden für die nächste Szenenzeit; der GL-Faden
 * lädt, was fertig ist (rund 1 MB je Bild). Ändern sich Preset, Kp oder Datum, wird alles neu gebaut (etwa 50 ms).
 */
public final class PolarlichtGl {
    /** Was gezeigt wird. */
    public enum Art {
        AUS("aus"), RUHIG("Ruhig: ein Bogen (Kp 2, 3 keV, 5 mW/m²)"), AKTIV("Aktiv: zwei Vorhänge mit Strahlen (Kp 4, 20 mW/m²)"),
        AUFBRUCH("Aufbruch: drei Vorhänge, purpurner Saum (Kp 6, 80 mW/m²)"), ROT("Rotes Polarlicht: weiches Spektrum, 200–300 km"), KP("Nach dem Kp-Regler");
        public final String label;

        Art(String l) { label = l; }

        @Override public String toString() { return label; }

        /** Der Kp des Presets (ruhig 2, aktiv 4, Aufbruch 6, rot 7); NaN für „nach dem Kp-Regler“ und aus. */
        public double kp() {
            return switch (this) { case RUHIG -> 2; case AKTIV -> 4; case AUFBRUCH -> 6; case ROT -> 7; default -> Double.NaN; };
        }

        /** Aus einem Namen oder einer Zahl (Kp): ruhig, aktiv, aufbruch/ausbruch, rot, kp, 0..9; null, wenn nichts passt. */
        public static Art von(String s) {
            String k = s.trim().toLowerCase(Locale.ROOT);
            if (k.startsWith("ru")) return RUHIG;
            if (k.startsWith("ak")) return AKTIV;
            if (k.startsWith("au") && !k.equals("aus")) return AUFBRUCH;
            if (k.startsWith("ro")) return ROT;
            if (k.startsWith("kp")) return KP;
            if (k.equals("aus")) return AUS;
            try { Double.parseDouble(k.replace(',', '.')); return KP; } catch (NumberFormatException e) { return null; }
        }
    }

    public volatile Art art = Art.AUS;
    /** Kp 0..9: gilt immer, auch über das Preset. */
    public volatile double kp = 4;
    /** Feinregler: E₀ in keV, Q in mW/m², Faltung und Strahlen 0..1; NaN = aus dem Preset. */
    public volatile double energie = Double.NaN, fluss = Double.NaN, faltung = Double.NaN, strahlen = Double.NaN;
    /** Diffuse Aurora äquatorwärts der Bögen mitmalen; die Vorhänge bewegen sich (Drift, Falten, Strahlen). */
    public volatile boolean diffus = true, bewegt = true;
    /**
     * Farben wie die Kamera: die Nachbearbeitung der Szene lässt das Stäbchensehen aus (post.frag, uScotopic), solange das
     * Polarlicht an ist. Mit Stäbchensehen würde das Grün zu Blaugrau, wie es das Auge bei IBC II wirklich sieht.
     */
    public volatile boolean kameraFarben = true;
    /** Datum und Weltzeit aus der Uhr der Szene; sonst {@link #monat}, {@link #tag}, {@link #ut}. */
    public volatile boolean uhrAusSzene = true;
    public volatile int monat = 12, tag = 20;
    public volatile double ut = 8;
    public volatile long seed = 1;
    /** Anteil der Bildgröße für das Zwischenbild (0,4: ein Sechstel der Bildpunkte; die Strahlen sind 2,5 km breit, in 1080p über 10 px). */
    public volatile double aufloesung = 0.4;
    /** In Bewegung höchstens „Mittel“ rechnen (2,5 km Schritt); Standbilder in der Stufe der App. */
    public volatile boolean sparsam = true;

    private final Aurora aurora = new Aurora();
    private final AuroraGl gl = new AuroraGl();
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Polarlicht"); t.setDaemon(true); return t; });
    private Future<AuroraGl.Daten> naechste;
    private String schluessel = "";
    private volatile String status = "Polarlicht: aus";
    private boolean glOk;
    private double sonneHoehe = -90;
    private int jahr = 2026;

    public void init(GL4 g) {
        try {
            gl.init(g);
            glOk = true;
            status = "Polarlicht: bereit (aus)";
        } catch (RuntimeException e) {
            glOk = false;
            status = "Polarlicht: " + e.getMessage();
            System.err.println("LituyaBay1958: " + status);
        }
    }

    /** Ob gezeichnet wird. */
    public boolean an() { return art != Art.AUS && glOk; }

    /** Das Polarlicht (nach {@link #vorbereiten}), für Bedienfeld und Prüfungen. */
    public Aurora aurora() { return aurora; }

    public String status() { return status; }

    /** Deklination in Lituya Bay nach dem Jahr: 29° Ost 1958, seither rund 0,16° je Jahr weniger, 18° heute [WMM, G]. */
    public static double deklination(int jahr) { return 29 - 0.16 * (jahr - 1958); }

    /**
     * Je Bild vor dem Zeichnen: Ort, Datum und Weltzeit aus der Uhr, Preset und Regler in das Polarlicht; bei Änderung
     * alles neu, sonst die Tabellen für die Szenenzeit t im Hilfsfaden weiter und fertige laden.
     */
    public void vorbereiten(GL4 g, SceneClock clock, double t, Quality q, double sonneHoeheGrad, boolean standbild) {
        if (!an()) { status = glOk ? "Polarlicht: aus" : status; return; }
        sonneHoehe = sonneHoeheGrad;
        Aurora.Quality aq = Aurora.Quality.values()[Math.min(Aurora.Quality.values().length - 1, q.ordinal())];
        if (sparsam && !standbild && aq.ordinal() > Aurora.Quality.MEDIUM.ordinal()) aq = Aurora.Quality.MEDIUM;
        ZonedDateTime z = Instant.ofEpochMilli((long) clock.epochMillis()).atZone(ZoneOffset.UTC);
        jahr = z.getYear();
        int m = uhrAusSzene ? z.getMonthValue() : monat, d = uhrAusSzene ? z.getDayOfMonth() : tag;
        double u = uhrAusSzene ? z.getHour() + z.getMinute() / 60.0 : ut;
        String k = art + "|" + kp + "|" + energie + "|" + fluss + "|" + faltung + "|" + strahlen + "|" + diffus + "|" + aq + "|" + m + "." + d + "|" + Math.round(u * 6) + "|" + seed;
        if (!k.equals(schluessel)) {
            warten();
            einstellen(m, d, u, aq);
            aurora.stufen = false;
            aurora.vorbereiten(t);
            gl.laden(g, AuroraGl.daten(aurora));
            schluessel = k;
        } else if (bewegt || standbild) {
            if (naechste != null && naechste.isDone()) {
                try { gl.laden(g, naechste.get()); } catch (Exception e) { System.err.println("LituyaBay1958: Polarlicht: " + e); }
                naechste = null;
            }
            if (naechste == null) {
                final double tz = t;
                naechste = exec.submit(() -> { aurora.zeit(tz); return AuroraGl.daten(aurora); });
                if (standbild) { warten(); if (naechste != null) { try { gl.laden(g, naechste.get()); } catch (Exception e) { /* gemeldet */ } naechste = null; } }
            }
        }
        Vorhang v = aurora.hauptbogen();
        double hw = aurora.hoehenwinkel(v);
        String daemmerung = sonneHoehe > -12 ? String.format(Locale.GERMAN, " · Sonne %.0f°: Dämmerung, das Polarlicht geht im Himmelslicht unter (dunkel ab −12°)", sonneHoehe) : "";
        status = String.format(Locale.GERMAN, "Polarlicht %s · Kp %.1f · %.1f MLT · Bogen %.0f km %s, %s · %.0f kR (%s)%s", art.name().toLowerCase(Locale.ROOT), aurora.kp, aurora.mlt,
                Math.abs(v.mitteAmMeridian()), v.mitteAmMeridian() >= 0 ? "nördlich" : "südlich", hw < 0 ? "hinter der Kimm" : String.format(Locale.GERMAN, "%.0f° hoch", hw),
                v.saeuleKr[Emission.GRUEN], Emission.ibc(v.saeuleKr[Emission.GRUEN]), daemmerung);
    }

    private void warten() {
        if (naechste == null) return;
        try { naechste.get(); } catch (Exception e) { /* egal */ }
        naechste = null;
    }

    /** Preset und Regler in das Polarlicht. */
    private void einstellen(int m, int d, double u, Aurora.Quality aq) {
        Aurora p = switch (art) {
            case RUHIG -> Aurora.ruhig();
            case AKTIV -> Aurora.aktiv();
            case AUFBRUCH -> Aurora.ausbruch();
            case ROT -> Aurora.rot();
            default -> Aurora.kp(kp);
        };
        aurora.energie = p.energie; aurora.fluss = p.fluss; aurora.weich = p.weich; aurora.faltung = p.faltung; aurora.strahlen = p.strahlen;
        aurora.breite = p.breite; aurora.drift = p.drift; aurora.bewegung = p.bewegung; aurora.boegen = p.boegen; aurora.diffus = p.diffus && diffus;
        aurora.kp = kp;
        if (!Double.isNaN(energie)) aurora.energie = energie;
        if (!Double.isNaN(fluss)) aurora.fluss = fluss;
        if (!Double.isNaN(faltung)) aurora.faltung = faltung;
        if (!Double.isNaN(strahlen)) aurora.strahlen = strahlen;
        aurora.ort = new Ort("Lituya Bay", Site.LAT, Site.LON, deklination(jahr));
        aurora.monat = m;
        aurora.tag = d;
        aurora.ut = u;
        aurora.quality = aq;
        aurora.seed = seed;
    }

    /** Hinter den Himmel ins gebundene HDR-Ziel (auch das Spiegelbild), Mischung additiv; Vorbelichtung der Szene. */
    public void zeichnen(GL4 g, float[] invViewProj, boolean reversedZ, int w, int h, double pre) {
        if (!an() || !gl.bereit()) return;
        AuroraGl.View v = new AuroraGl.View();
        v.invViewProj = invViewProj;
        v.reversedZ = reversedZ;
        v.width = w;
        v.height = h;
        v.preExposure = pre;
        v.einheit = Sky.CD_PER_UNIT;
        v.aufloesung = aufloesung;
        gl.zeichnen(g, v);
    }

    /** Ins gebundene Rundumbild des Himmels (für Spiegelungen und Himmelslicht), additiv. */
    public void panorama(GL4 g, double pre) {
        if (!an() || !gl.bereit()) return;
        gl.panorama(g, pre, Sky.CD_PER_UNIT);
    }

    public void dispose(GL4 g) {
        exec.shutdownNow();
        if (glOk) gl.dispose(g);
    }
}
