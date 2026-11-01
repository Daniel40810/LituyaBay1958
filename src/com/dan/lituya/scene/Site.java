package com.dan.lituya.scene;

/**
 * Der Ort der Szene: Lituya Bay am Fuß der Fairweather-Kette, Alaska (Werkbuch, Abschnitte 1 und 3).
 * <p>
 * Raum (F2 A, F41 A): UTM Zone 8 Nord (NAD83, EPSG:26908), echter Maßstab, Höhen über NAVD88 (F3 A: die Lotungen der NOS
 * von MLLW über den Pegel Elfin Cove umgerechnet, ab Phase 2). Ursprung in der Mitte des Kerns bei E 352 000, N 6 503 000
 * [G], zwischen Cenotaph Island und Mudslide Creek. 1 Einheit = 1 m, x nach Osten, y nach oben, z nach Süden, Nord = −z,
 * y = 0 ist NAVD88. Für die Datenbank liegt die Lage in SRID 26908, die Sichten in 4326 (F41 A).
 * <p>
 * Gebiete (F1 A, Werkbuch 3.1, {@code geodaten/gebiete.geojson}): der <b>Kern</b> (24 × 18 km) mit der Bucht vom Eingang
 * bis zum T-Kopf, die <b>Szene</b> (66 × 60 km) mit der Fairweather-Kette und der <b>Fern</b> (260 × 240 km) bis zur Kimm
 * des Mount Fairweather. Die Orte sind die amtlichen Namen aus GNIS ({@code geodaten/namen_szene.geojson}), nur der Sporn
 * gegenüber dem Felssturz ist gesetzt [G] und wird in Phase 2 am Lidar geprüft.
 */
public final class Site {
    private Site() { }

    /** Ursprung der Szene: Mitte des Kerns, UTM 8 N [G]. */
    public static final double E0 = 352_000, N0 = 6_503_000;
    public static final int SRID = 26908, SRID_SICHT = 4326;
    /** Breite und Länge des Ursprungs (aus E0/N0 gerechnet). */
    public static final double LAT, LON;
    static {
        double[] ll = Geo.UTM8N.inverse(E0, N0);
        LAT = ll[0];
        LON = ll[1];
    }

    /** Mittlerer Erdradius in m und der wirksame Radius für die Sicht mit normaler Lichtbrechung (k = 0,13). */
    public static final double EARTH_R = 6_371_000, EARTH_R_OPTICAL = EARTH_R / (1 - 0.13);

    /** Ein rechteckiges Fenster in UTM 8 N (Ost- und Nordwerte in m). */
    public record Fenster(String name, double eMin, double nMin, double eMax, double nMax) {
        /** In der Szene: {xMin, zMin, xMax, zMax}. */
        public double[] scene() {
            double[] a = fromUtm(eMin, nMax), b = fromUtm(eMax, nMin);
            return new double[]{a[0], a[1], b[0], b[1]};
        }
        public double breite() { return eMax - eMin; }
        public double hoehe() { return nMax - nMin; }
        public boolean contains(double x, double z) {
            double[] u = toUtm(x, z);
            return u[0] >= eMin && u[0] <= eMax && u[1] >= nMin && u[1] <= nMax;
        }
    }

    /** F1 A: der Kern, 24 × 18 km: die Bucht mit Gilbert und Crillon Inlet, Cenotaph Island, Eingang, Hänge bis über die Spurlinie. */
    public static final Fenster KERN = new Fenster("Kern", 340_000, 6_494_000, 364_000, 6_512_000);
    /** F1 A: die Szene, 66 × 60 km: Fairweather-Kette mit Crillon, La Perouse und Fairweather, Küstenebene, Cape Fairweather. */
    public static final Fenster SZENE = new Fenster("Szene", 322_000, 6_480_000, 388_000, 6_540_000);
    /** F1 A: der Fern, 260 × 240 km: Cross Sound und Icy Strait bis Dry Bay und Alsek, bis zur Kimm des Fairweather. */
    public static final Fenster FERN = new Fenster("Fern", 220_000, 6_385_000, 480_000, 6_625_000);
    public static final Fenster[] GEBIETE = {KERN, SZENE, FERN};

    /** In welchem Gebiet ein Szenenpunkt liegt: „Kern“, „Szene“, „Fern“ oder „außerhalb“. */
    public static String gebiet(double x, double z) {
        for (Fenster f : GEBIETE) if (f.contains(x, z)) return f.name();
        return "außerhalb";
    }

    /** Was ein Ort ist (Farbe der Marke). */
    public enum Art { BUCHT, INSEL, KAP, GEWAESSER, GLETSCHER, GIPFEL, SPORN }

    /** Ein benannter Ort mit Lage in UTM 8 N und Herkunft (GNIS-Kennung oder [G]). */
    public record Ort(String name, double e, double n, Art art, String quelle) {
        public double x() { return e - E0; }
        public double z() { return -(n - N0); }
    }

    /** Die Orte aus GNIS (Kennung in {@link Ort#quelle}); Ostwerte und Nordwerte wie in {@code namen_szene.geojson}. */
    public static final Ort LITUYA_BAY = new Ort("Lituya Bay", 350_641.9, 6_502_491.1, Art.BUCHT, "GNIS 1423375"),
            GILBERT = new Ort("Gilbert Inlet", 354_903.5, 6_506_385.5, Art.BUCHT, "GNIS 1422142"),
            CRILLON_INLET = new Ort("Crillon Inlet", 356_876.9, 6_504_362.0, Art.BUCHT, "GNIS 1421280"),
            ANCHORAGE = new Ort("Anchorage Cove", 345_252.6, 6_500_936.8, Art.BUCHT, "GNIS 1420097"),
            CENOTAPH = new Ort("Cenotaph Island", 350_204.8, 6_502_879.5, Art.INSEL, "GNIS 1420950"),
            CHAUSSEE = new Ort("La Chaussee Spit", 344_812.3, 6_500_830.5, Art.KAP, "GNIS 1423200"),
            HARBOR_POINT = new Ort("Harbor Point", 346_074.5, 6_499_665.8, Art.KAP, "GNIS 1422445"),
            CENOTAPH_POINT = new Ort("Cenotaph Point", 350_326.1, 6_502_255.6, Art.KAP, "GNIS 1420951"),
            THREE_SAINTS = new Ort("Three Saints Point", 348_462.0, 6_503_721.0, Art.KAP, "GNIS 1415297"),
            CAPE_FAIRWEATHER = new Ort("Cape Fairweather", 329_932.8, 6_522_139.8, Art.KAP, "GNIS 1420851"),
            CORMORANT = new Ort("Cormorant Rock", 345_729.7, 6_499_524.6, Art.KAP, "GNIS 1421221"),
            PAPS = new Ort("The Paps", 346_833.9, 6_500_344.7, Art.GIPFEL, "GNIS 1424957"),
            FISH_LAKE = new Ort("Fish Lake", 347_172.4, 6_504_296.0, Art.GEWAESSER, "GNIS 1421941"),
            MUDSLIDE = new Ort("Mudslide Creek", 354_605.1, 6_503_579.4, Art.GEWAESSER, "GNIS 1423829"),
            CRILLON_LAKE = new Ort("Crillon Lake", 361_559.0, 6_493_711.9, Art.GEWAESSER, "GNIS 1421281"),
            LITUYA_GLACIER = new Ort("Lituya Glacier", 359_669.5, 6_510_116.4, Art.GLETSCHER, "GNIS 1423376"),
            NORTH_CRILLON = new Ort("North Crillon Glacier", 365_938.1, 6_506_113.4, Art.GLETSCHER, "GNIS 1423971"),
            CASCADE = new Ort("Cascade Glacier", 356_690.2, 6_505_420.6, Art.GLETSCHER, "GNIS 1420904"),
            LA_PEROUSE_GLACIER = new Ort("La Perouse Glacier", 372_488.4, 6_492_760.9, Art.GLETSCHER, "GNIS 1423201"),
            FAIRWEATHER = new Ort("Mount Fairweather", 354_470.8, 6_532_388.9, Art.GIPFEL, "GNIS 1420601"),
            CRILLON = new Ort("Mount Crillon", 374_040.8, 6_504_480.7, Art.GIPFEL, "GNIS 1421283"),
            LA_PEROUSE = new Ort("Mount La Perouse", 378_850.7, 6_493_264.2, Art.GIPFEL, "GNIS 1423202"),
            LITUYA_MOUNTAIN = new Ort("Lituya Mountain", 359_244.2, 6_520_935.2, Art.GIPFEL, "GNIS 1423377"),
            BAY_PEAK = new Ort("Bay Peak", 357_766.8, 6_509_243.8, Art.GIPFEL, "GNIS 1893529"),
            SOLOMON = new Ort("Solomon Railroad", 351_962.9, 6_505_622.5, Art.GIPFEL, "GNIS 1414856"),
            /** Der Sporn am Südwestufer des Gilbert Inlet, gegenüber dem Felssturz: Auflauf bis 524 m (MILLER60). Lage [G], Phase 2 prüft am Lidar. */
            SPORN = new Ort("Sporn (524 m)", 353_900, 6_505_900, Art.SPORN, "[G]");

    /** Alle Orte für Marken, HUD und Prüfung (höchstens 32, so viele nimmt der Shader). */
    public static final Ort[] ORTE = {LITUYA_BAY, GILBERT, CRILLON_INLET, ANCHORAGE, CENOTAPH, CHAUSSEE, HARBOR_POINT, CENOTAPH_POINT, THREE_SAINTS,
            CAPE_FAIRWEATHER, CORMORANT, PAPS, FISH_LAKE, MUDSLIDE, CRILLON_LAKE, LITUYA_GLACIER, NORTH_CRILLON, CASCADE, LA_PEROUSE_GLACIER,
            FAIRWEATHER, CRILLON, LA_PEROUSE, LITUYA_MOUNTAIN, BAY_PEAK, SOLOMON, SPORN};

    /** Höhe des Mount Fairweather (m, Werkbuch Abschnitt 1). */
    public static final double FAIRWEATHER_HOEHE = 4_671;

    /** Der nächste Ort und sein Abstand: „Cenotaph Island 1,2 km“. */
    public static String naechsterOrt(double x, double z) {
        Ort best = null;
        double bd = Double.MAX_VALUE;
        for (Ort o : ORTE) {
            double d = Math.hypot(o.x() - x, o.z() - z);
            if (d < bd) { bd = d; best = o; }
        }
        if (best == null) return "";
        return bd < 1000 ? String.format(java.util.Locale.GERMAN, "%s %.0f m", best.name(), bd)
                : String.format(java.util.Locale.GERMAN, "%s %.1f km", best.name(), bd / 1000);
    }

    public static Ort byName(String name) {
        for (Ort o : ORTE) if (o.name().equalsIgnoreCase(name)) return o;
        return null;
    }

    /** Szenenpunkt (x, z) aus UTM 8 Nord (Ost- und Nordwert in m). */
    public static double[] fromUtm(double east, double north) { return new double[]{east - E0, -(north - N0)}; }

    /** UTM 8 Nord (Ost, Nord) aus einem Szenenpunkt (x, z). */
    public static double[] toUtm(double x, double z) { return new double[]{x + E0, N0 - z}; }

    /** Breite und Länge (Grad) eines Szenenpunkts. */
    public static double[] latLon(double x, double z) { return Geo.UTM8N.inverse(x + E0, N0 - z); }

    /** Szenenpunkt (x, z) aus Breite und Länge (Grad, als {lat, lon}). */
    public static double[] xz(double[] latLon) {
        double[] en = Geo.UTM8N.forward(latLon[0], latLon[1]);
        return fromUtm(en[0], en[1]);
    }

    /** Absenkung der Oberfläche in d m Entfernung durch die Erdkrümmung: d²/2R. */
    public static double curvatureDrop(double d) { return d * d / (2 * EARTH_R); }

    /** Entfernung der Kimm für ein Auge h m über der Ebene, mit normaler Lichtbrechung (m). */
    public static double horizon(double h) { return Math.sqrt(2 * EARTH_R_OPTICAL * Math.max(0, h)); }

    /** „58°38′13″ N · 137°34′23″ W“ */
    public static String dms(double lat, double lon) {
        return dms(lat, lat >= 0 ? "N" : "S") + " · " + dms(lon, lon >= 0 ? "O" : "W");
    }

    private static String dms(double v, String h) {
        v = Math.abs(v);
        int d = (int) v;
        double mm = (v - d) * 60;
        int m = (int) mm;
        int s = (int) Math.round((mm - m) * 60);
        if (s == 60) { s = 0; m++; }
        if (m == 60) { m = 0; d++; }
        return String.format("%d°%02d′%02d″ %s", d, m, s, h);
    }
}
