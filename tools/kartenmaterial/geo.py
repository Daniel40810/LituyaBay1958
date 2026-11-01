"""Gebiete und Projektionen fuer Lituya Bay, ohne Fremdpakete.

Die drei Gebiete (Kern, Szene, Fern) sind in UTM Zone 8 Nord (EPSG:26908,
NAD83 / EPSG:32608, WGS84; der Unterschied liegt unter 2 m) auf volle
Kilometer gelegt. Daraus folgen die Grenzen in Laenge/Breite, mit denen die
Quellen abgefragt werden.

Projektionen (nur, was die Quellen brauchen):
  - UTM (Krueger-Reihen nach Karney 2011, GRS80)
  - Albers flaechentreu (Snyder 1987), Alaska Albers EPSG:3338 und das
    gleichartige "unnamed" der USGS-IfSAR-Kacheln
  - polarstereographisch Nord (Snyder 1987), EPSG:3413 der ArcticDEM
  - MGRS-Kennung (100-km-Quadrat) fuer die Sentinel-2-Kacheln
"""

import math

# GRS80 (fuer NAD83 und, auf diese Genauigkeit, WGS84)
A = 6378137.0
F = 1 / 298.257222101
E2 = F * (2 - F)
E = math.sqrt(E2)

# ---------------------------------------------------------------------------
# UTM
# ---------------------------------------------------------------------------
_N = F / (2 - F)
_AA = A / (1 + _N) * (1 + _N ** 2 / 4 + _N ** 4 / 64)
_ALPHA = (_N / 2 - 2 * _N ** 2 / 3 + 5 * _N ** 3 / 16,
          13 * _N ** 2 / 48 - 3 * _N ** 3 / 5,
          61 * _N ** 3 / 240)
_BETA = (_N / 2 - 2 * _N ** 2 / 3 + 37 * _N ** 3 / 96,
         _N ** 2 / 48 + _N ** 3 / 15,
         17 * _N ** 3 / 480)
_DELTA = (2 * _N - 2 * _N ** 2 / 3 - 2 * _N ** 3,
          7 * _N ** 2 / 3 - 8 * _N ** 3 / 5,
          56 * _N ** 3 / 15)
K0 = 0.9996


def utm_zone(lon):
    return int((lon + 180) // 6) + 1


def utm_vorwaerts(lon, lat, zone=8):
    """Laenge/Breite (Grad) -> (Ost, Nord) in Metern, Nordhalbkugel."""
    lam = math.radians(lon - (zone * 6 - 183))
    phi = math.radians(lat)
    t = math.sinh(math.atanh(math.sin(phi)) - E * math.atanh(E * math.sin(phi)))
    xi_ = math.atan2(t, math.cos(lam))
    eta_ = math.atanh(math.sin(lam) / math.sqrt(1 + t * t))
    xi, eta = xi_, eta_
    for j, a in enumerate(_ALPHA, 1):
        xi += a * math.sin(2 * j * xi_) * math.cosh(2 * j * eta_)
        eta += a * math.cos(2 * j * xi_) * math.sinh(2 * j * eta_)
    return 500000.0 + K0 * _AA * eta, K0 * _AA * xi


def utm_rueckwaerts(ost, nord, zone=8):
    """(Ost, Nord) in Metern -> Laenge/Breite (Grad), Nordhalbkugel."""
    xi = nord / (K0 * _AA)
    eta = (ost - 500000.0) / (K0 * _AA)
    xi_, eta_ = xi, eta
    for j, b in enumerate(_BETA, 1):
        xi_ -= b * math.sin(2 * j * xi) * math.cosh(2 * j * eta)
        eta_ -= b * math.cos(2 * j * xi) * math.sinh(2 * j * eta)
    chi = math.asin(math.sin(xi_) / math.cosh(eta_))
    phi = chi
    for j, d in enumerate(_DELTA, 1):
        phi += d * math.sin(2 * j * chi)
    lam = math.atan2(math.sinh(eta_), math.cos(xi_))
    return math.degrees(lam) + (zone * 6 - 183), math.degrees(phi)


# ---------------------------------------------------------------------------
# Albers flaechentreu (Snyder 1987, S. 101 ff.)
# ---------------------------------------------------------------------------
def _q(phi):
    s = math.sin(phi)
    return (1 - E2) * (s / (1 - E2 * s * s) - 1 / (2 * E) * math.log((1 - E * s) / (1 + E * s)))


def _m(phi):
    s = math.sin(phi)
    return math.cos(phi) / math.sqrt(1 - E2 * s * s)


class Albers:
    def __init__(self, lat0, lon0, lat1, lat2, x0=0.0, y0=0.0):
        p0, p1, p2 = map(math.radians, (lat0, lat1, lat2))
        m1, m2 = _m(p1), _m(p2)
        q0, q1, q2 = _q(p0), _q(p1), _q(p2)
        self.n = (m1 * m1 - m2 * m2) / (q2 - q1)
        self.C = m1 * m1 + self.n * q1
        self.rho0 = A * math.sqrt(self.C - self.n * q0) / self.n
        self.lon0, self.x0, self.y0 = lon0, x0, y0

    def vorwaerts(self, lon, lat):
        rho = A * math.sqrt(self.C - self.n * _q(math.radians(lat))) / self.n
        th = self.n * math.radians(lon - self.lon0)
        return self.x0 + rho * math.sin(th), self.y0 + self.rho0 - rho * math.cos(th)

    def rueckwaerts(self, x, y):
        x, y = x - self.x0, self.rho0 - (y - self.y0)
        rho = math.hypot(x, y)
        th = math.atan2(x, y)
        q = (self.C - (rho * self.n / A) ** 2) / self.n
        phi = math.asin(q / 2)
        for _ in range(15):
            s = math.sin(phi)
            phi += (1 - E2 * s * s) ** 2 / (2 * math.cos(phi)) * (
                q / (1 - E2) - s / (1 - E2 * s * s)
                + 1 / (2 * E) * math.log((1 - E * s) / (1 + E * s)))
        return self.lon0 + math.degrees(th / self.n), math.degrees(phi)


ALASKA_ALBERS = Albers(50, -154, 55, 65)  # EPSG:3338


# ---------------------------------------------------------------------------
# Polarstereographisch Nord (Snyder 1987, S. 160 ff.), EPSG:3413
# ---------------------------------------------------------------------------
class PolarStereo:
    def __init__(self, lat_ts=70.0, lon0=-45.0):
        pc = math.radians(lat_ts)
        self.mc = _m(pc)
        self.tc = self._t(pc)
        self.lon0 = lon0

    @staticmethod
    def _t(phi):
        s = math.sin(phi)
        return math.tan(math.pi / 4 - phi / 2) / ((1 - E * s) / (1 + E * s)) ** (E / 2)

    def vorwaerts(self, lon, lat):
        rho = A * self.mc * self._t(math.radians(lat)) / self.tc
        lam = math.radians(lon - self.lon0)
        return rho * math.sin(lam), -rho * math.cos(lam)

    def rueckwaerts(self, x, y):
        rho = math.hypot(x, y)
        t = rho * self.tc / (A * self.mc)
        phi = math.pi / 2 - 2 * math.atan(t)
        for _ in range(15):
            s = math.sin(phi)
            phi = math.pi / 2 - 2 * math.atan(t * ((1 - E * s) / (1 + E * s)) ** (E / 2))
        return self.lon0 + math.degrees(math.atan2(x, -y)), math.degrees(phi)


EPSG_3413 = PolarStereo(70.0, -45.0)


# ---------------------------------------------------------------------------
# MGRS (nur Kennung des 100-km-Quadrats, fuer Sentinel-2)
# ---------------------------------------------------------------------------
_BAND = "CDEFGHJKLMNPQRSTUVWX"


def mgrs_kachel(lon, lat):
    """z. B. (-137.6, 58.62) -> '08VLK' (Zone, Breitenband, 100-km-Quadrat)."""
    zone = utm_zone(lon)
    ost, nord = utm_vorwaerts(lon, lat, zone)
    band = _BAND[int((lat + 80) // 8)]
    spalten = ("ABCDEFGH", "JKLMNPQR", "STUVWXYZ")[(zone - 1) % 3]
    zeilen = "ABCDEFGHJKLMNPQRSTUV"
    sp = spalten[int(ost // 100000) - 1]
    versatz = 5 if zone % 2 == 0 else 0
    ze = zeilen[(int(nord // 100000) + versatz) % 20]
    return f"{zone:02d}{band}{sp}{ze}"


# ---------------------------------------------------------------------------
# Gebiete
# ---------------------------------------------------------------------------
class Gebiet:
    """Rechteck in UTM 8N (volle km) mit seinen Grenzen in Laenge/Breite."""

    def __init__(self, name, ost0, nord0, ost1, nord1, zweck):
        self.name, self.zweck = name, zweck
        self.ost0, self.nord0, self.ost1, self.nord1 = ost0, nord0, ost1, nord1

    def rand_utm(self, schritte=40):
        """Punkte entlang des Randes, als geschlossener Ring im Uhrzeigersinn."""
        e0, n0, e1, n1 = self.ost0, self.nord0, self.ost1, self.nord1
        ecken = ((e0, n0), (e0, n1), (e1, n1), (e1, n0))
        for k in range(4):
            (xa, ya), (xb, yb) = ecken[k], ecken[(k + 1) % 4]
            for i in range(schritte):
                f = i / schritte
                yield xa + f * (xb - xa), ya + f * (yb - ya)

    @property
    def lonlat(self):
        """(West, Sued, Ost, Nord) in Grad, umschliessend."""
        pts = [utm_rueckwaerts(e, n) for e, n in self.rand_utm()]
        return (min(p[0] for p in pts), min(p[1] for p in pts),
                max(p[0] for p in pts), max(p[1] for p in pts))

    def schneidet(self, w, s, o, n):
        a = self.lonlat
        return w < a[2] and o > a[0] and s < a[3] and n > a[1]

    def schneidet_proj(self, x0, y0, x1, y1, proj):
        """Rechteck in einer anderen Projektion (proj.vorwaerts) gegen das Gebiet."""
        pts = [proj.vorwaerts(*utm_rueckwaerts(e, n)) for e, n in self.rand_utm()]
        gx0, gx1 = min(p[0] for p in pts), max(p[0] for p in pts)
        gy0, gy1 = min(p[1] for p in pts), max(p[1] for p in pts)
        return min(x0, x1) < gx1 and max(x0, x1) > gx0 and min(y0, y1) < gy1 and max(y0, y1) > gy0

    def flaeche_km2(self):
        return (self.ost1 - self.ost0) * (self.nord1 - self.nord0) / 1e6

    def __repr__(self):
        w, s, o, n = self.lonlat
        return (f"{self.name}: O {self.ost0:.0f}-{self.ost1:.0f}, N {self.nord0:.0f}-{self.nord1:.0f} "
                f"(UTM 8N), {w:.4f} {s:.4f} {o:.4f} {n:.4f} (Grad)")


# Kern: die Bucht vom Eingang (La Chaussee Spit) bis Gilbert und Crillon Inlet,
#       mit den Hangen bis ueber die Spurlinie von 524 m.
# Szene: die Fairweather-Kette mit Mt Crillon, Mt La Perouse, Mt Fairweather
#        und Cape Fairweather, der Kuestensaum davor - der Blick aus der Bucht.
# Fern: der Horizont, Cross Sound bis Dry Bay, rund 250 x 230 km.
KERN = Gebiet("kern", 340000, 6494000, 364000, 6512000,
              "Bucht mit Gilbert Inlet, Crillon Inlet, Cenotaph Island, Eingang; Hänge bis 600 m, Zunge des Lituya-Gletschers")
SZENE = Gebiet("szene", 322000, 6480000, 388000, 6540000,
               "Fairweather-Kette (Crillon, La Perouse, Fairweather), Küstenebene, Cape Fairweather")
FERN = Gebiet("fern", 220000, 6385000, 480000, 6625000,
              "Horizont: Cross Sound und Icy Strait bis Dry Bay und Alsek")

GEBIETE = {g.name: g for g in (KERN, SZENE, FERN)}


def gebiet(name):
    return GEBIETE[name]


if __name__ == "__main__":
    for g in GEBIETE.values():
        print(g, f"{g.flaeche_km2():.0f} km2")
    # Probe: Gilbert Inlet und Eingang
    for name, lon, lat in (("Gilbert Inlet", -137.475, 58.66), ("Eingang", -137.65, 58.61)):
        e, n = utm_vorwaerts(lon, lat)
        print(name, f"{e:.1f} {n:.1f}", utm_rueckwaerts(e, n), mgrs_kachel(lon, lat))
