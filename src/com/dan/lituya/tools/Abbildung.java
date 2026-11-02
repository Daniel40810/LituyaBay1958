package com.dan.lituya.tools;

import com.dan.lituya.scene.Geo;

/**
 * Von UTM 8 N (die Szene, F41 A) in die Abbildung einer Quelle (Phase 2): UTM 8 N selbst (EPSG 26908, 6337 NAD83(2011),
 * 32608 WGS84; die Unterschiede liegen unter 2 m und zählen für die Szene nicht [G]), geographisch (4269 NAD83, 6318
 * NAD83(2011), 4326 WGS84, 4019 GRS80) und Albers flächentreu (3338 Alaska Albers und die benutzerdefinierte Abbildung
 * des IfSAR 2012, Snyder 1987, wie {@code tools/kartenmaterial/geo.py}).
 */
public interface Abbildung {
    /** Ost, Nord in UTM 8 N → x, y der Quelle (in out[0], out[1]). */
    void vonUtm(double e, double n, double[] out);

    Abbildung UTM = (e, n, o) -> { o[0] = e; o[1] = n; };

    /** Geographisch: x = Länge, y = Breite (Grad). */
    Abbildung GEO = (e, n, o) -> {
        double[] ll = Geo.UTM8N.inverse(e, n);
        o[0] = ll[1];
        o[1] = ll[0];
    };

    /** Die Abbildung einer GeoTIFF-Datei nach ihrem EPSG-Code. */
    static Abbildung von(GeoTiff t) {
        if (t.albers != null) return new Albers(t.albers[0], t.albers[1], t.albers[2], t.albers[3], t.albers[4], t.albers[5]);
        return switch (t.epsg) {
            case 26908, 6337, 32608, 3715 -> UTM;
            case 4269, 6318, 4326, 4019, 4617, 4759 -> GEO;
            case 3338 -> new Albers(50, -154, 55, 65, 0, 0);
            case 3413 -> new Polar(70, -45);
            default -> throw new IllegalArgumentException(t.file.getName() + ": Abbildung EPSG " + t.epsg + " unbekannt");
        };
    }

    /** Albers flächentreu auf GRS80 (Snyder 1987, S. 98 ff.). */
    final class Albers implements Abbildung {
        static final double A = Geo.A_AXIS, F = Geo.F, E2 = F * (2 - F), E = Math.sqrt(E2);
        final double n, c, rho0, lon0, x0, y0;

        public Albers(double lat0, double lon0, double lat1, double lat2, double x0, double y0) {
            double p0 = Math.toRadians(lat0), p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
            double m1 = m(p1), m2 = m(p2), q0 = q(p0), q1 = q(p1), q2 = q(p2);
            n = (m1 * m1 - m2 * m2) / (q2 - q1);
            c = m1 * m1 + n * q1;
            rho0 = A * Math.sqrt(c - n * q0) / n;
            this.lon0 = lon0;
            this.x0 = x0;
            this.y0 = y0;
        }

        static double m(double p) { double s = Math.sin(p); return Math.cos(p) / Math.sqrt(1 - E2 * s * s); }

        static double q(double p) {
            double s = Math.sin(p);
            return (1 - E2) * (s / (1 - E2 * s * s) - 1 / (2 * E) * Math.log((1 - E * s) / (1 + E * s)));
        }

        /** Länge, Breite (Grad) → x, y. */
        public double[] vor(double lon, double lat) {
            double rho = A * Math.sqrt(c - n * q(Math.toRadians(lat))) / n, th = n * Math.toRadians(lon - lon0);
            return new double[]{x0 + rho * Math.sin(th), y0 + rho0 - rho * Math.cos(th)};
        }

        @Override public void vonUtm(double e, double nn, double[] o) {
            double[] ll = Geo.UTM8N.inverse(e, nn);
            double[] xy = vor(ll[1], ll[0]);
            o[0] = xy[0];
            o[1] = xy[1];
        }
    }

    /** Polarstereographisch Nord auf WGS84 mit Bezugsbreite (EPSG 3413: 70°, −45°; ArcticDEM), Snyder 1987, S. 160 ff. */
    final class Polar implements Abbildung {
        static final double A = Geo.A_AXIS, E2 = 1 / 298.257223563 * (2 - 1 / 298.257223563), E = Math.sqrt(E2);
        final double lon0, amc;

        public Polar(double latTs, double lon0) {
            double p = Math.toRadians(latTs), s = Math.sin(p);
            this.lon0 = lon0;
            amc = A * Math.cos(p) / Math.sqrt(1 - E2 * s * s) / t(p);
        }

        static double t(double p) {
            double s = Math.sin(p);
            return Math.tan(Math.PI / 4 - p / 2) / Math.pow((1 - E * s) / (1 + E * s), E / 2);
        }

        /** Länge, Breite (Grad) → x, y. */
        public double[] vor(double lon, double lat) {
            double rho = amc * t(Math.toRadians(lat)), l = Math.toRadians(lon - lon0);
            return new double[]{rho * Math.sin(l), -rho * Math.cos(l)};
        }

        @Override public void vonUtm(double e, double nn, double[] o) {
            double[] ll = Geo.UTM8N.inverse(e, nn);
            double[] xy = vor(ll[1], ll[0]);
            o[0] = xy[0];
            o[1] = xy[1];
        }
    }
}
