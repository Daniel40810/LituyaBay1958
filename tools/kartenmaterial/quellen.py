"""Die Quellen: Beschreibung, Lizenz und wie man die Dateien zum Gebiet findet.

Jede Quelle ist eine Funktion plan(ctx) -> Liste von Datei. Die Funktion wird
mit @quelle(...) registriert. ctx.gebiet ist das Gebiet der Quelle (aus
geo.GEBIETE); die Vorgabe steht bei der Quelle und laesst sich mit
--gebiet ueberschreiben.

Stufen:
  standard  wird mit "laden" ohne weitere Angabe geladen
  gross     nur mit --gross oder namentlich (--quellen ...)
  manuell   braucht eine Anmeldung oder einen Klick; nur Hinweis und Adresse
"""

import csv
import io
import re
import sqlite3
import struct
import tempfile
import urllib.parse
import zipfile
from dataclasses import dataclass, field

import geo
import netz

QUELLEN = {}

TNM = "https://prd-tnm.s3.amazonaws.com"
PGC = "https://pgc-opendata-dems.s3.us-west-2.amazonaws.com"
COP30 = "https://copernicus-dem-30m.s3.amazonaws.com"
S2 = "https://sentinel-cogs.s3.us-west-2.amazonaws.com"
NOAA_BATHY = "https://noaa-ocs-nationalbathymetry-pds.s3.amazonaws.com"
WORLDCOVER = "https://esa-worldcover.s3.eu-central-1.amazonaws.com"
ITSLIVE = "https://its-live-data.s3.amazonaws.com"
NE = "https://naturalearth.s3.amazonaws.com"


@dataclass
class Datei:
    url: str
    pfad: str                 # relativ zum Zielordner der Quelle
    groesse: int = None
    sha256: str = None
    notiz: str = ""
    ausschnitt: tuple = None  # (West, Sued, Ost, Nord) in der Projektion der Datei: nur diesen Teil lesen


@dataclass
class Quelle:
    id: str
    gruppe: str
    titel: str
    anbieter: str
    lizenz: str
    aufloesung: str
    stand: str
    gebiet: str
    stufe: str
    text: str
    seite: str
    plan: object = field(repr=False, default=None)


class Ctx:
    def __init__(self, gebiet, optionen=None):
        self.gebiet = gebiet
        self.opt = optionen or {}


def quelle(id, gruppe, titel, anbieter, lizenz, aufloesung, stand, gebiet, stufe, text, seite):
    def deko(f):
        QUELLEN[id] = Quelle(id, gruppe, titel, anbieter, lizenz, aufloesung, stand, gebiet, stufe,
                             " ".join(text.split()), seite, f)
        return f
    return deko


def statisch(id, gruppe, titel, anbieter, lizenz, aufloesung, stand, gebiet, stufe, text, seite, dateien):
    """Quelle mit festen Adressen: dateien = [(url, pfad)] oder [(url, pfad, notiz)]."""
    def plan(ctx):
        out = []
        for d in dateien:
            url = d[0].format(**_bbox_felder(ctx.gebiet))
            out.append(Datei(url, d[1], notiz=d[2] if len(d) > 2 else ""))
        return out
    quelle(id, gruppe, titel, anbieter, lizenz, aufloesung, stand, gebiet, stufe, text, seite)(plan)


def _bbox_felder(g):
    w, s, o, n = g.lonlat
    return {"w": f"{w:.4f}", "s": f"{s:.4f}", "o": f"{o:.4f}", "n": f"{n:.4f}"}


# ---------------------------------------------------------------------------
# Hilfen
# ---------------------------------------------------------------------------
def _grad_kacheln(g, rand=0.0):
    """Ganzgradzellen (Sued-West-Ecke) im Gebiet."""
    import math
    w, s, o, n = g.lonlat
    for lat in range(math.floor(s - rand), math.ceil(n + rand)):
        for lon in range(math.floor(w - rand), math.ceil(o + rand)):
            yield lat, lon


def _vrt_quellen(vrt_text):
    """(projektion, [(datei, x0, y0, x1, y1)]) aus einer GDAL-VRT."""
    srs = re.search(r"<SRS[^>]*>(.*?)</SRS>", vrt_text, re.S).group(1).replace("&quot;", '"')
    gt = [float(x) for x in re.search(r"<GeoTransform>(.*?)</GeoTransform>", vrt_text).group(1).split(",")]
    proj = _projektion(srs)
    out = []
    for m in re.finditer(r'<SourceFilename[^>]*>(.*?)</SourceFilename>.*?<DstRect xOff="([-\d.e+]+)" '
                         r'yOff="([-\d.e+]+)" xSize="([-\d.e+]+)" ySize="([-\d.e+]+)"', vrt_text, re.S):
        xo, yo, xs, ys = map(float, m.groups()[1:])
        x0 = gt[0] + xo * gt[1]
        y1 = gt[3] + yo * gt[5]
        out.append((m.group(1), x0, y1 + ys * gt[5], x0 + xs * gt[1], y1))
    return proj, out


class _Utm:
    def __init__(self, zone):
        self.zone = zone

    def vorwaerts(self, lon, lat):
        return geo.utm_vorwaerts(lon, lat, self.zone)


def _projektion(wkt):
    if "Albers" in wkt:
        def p(k):
            return float(re.search(k + r'",([-\d.]+)', wkt).group(1))
        return geo.Albers(p("latitude_of_center"), p("longitude_of_center"), p("standard_parallel_1"),
                          p("standard_parallel_2"), p("false_easting"), p("false_northing"))
    m = re.search(r"UTM zone (\d+)N", wkt, re.I) or re.search(r"UTM_Zone_(\d+)N", wkt, re.I)
    if m:
        return _Utm(int(m.group(1)))
    if "Polar_Stereographic" in wkt or "3413" in wkt:
        return geo.EPSG_3413
    raise ValueError("Projektion nicht unterstuetzt: " + wkt[:120])


def _vrt_auswahl(vrt_url, g):
    proj, quellen = _vrt_quellen(netz.hole_text(vrt_url))
    treffer = []
    for f, x0, y0, x1, y1 in quellen:
        if g.schneidet_proj(x0, y0, x1, y1, proj):
            f = f.replace("/vsicurl/", "")
            if f.startswith("/vsis3/"):
                b, _, k = f[len("/vsis3/"):].partition("/")
                f = f"https://{b}.s3.amazonaws.com/{k}" if b != "pgc-opendata-dems" else f"{PGC}/{k}"
            treffer.append(f)
    return sorted(set(treffer))


def _groessen(basis, urls):
    """Groessen per S3-Liste der jeweiligen Ordner (schneller als HEAD je Datei)."""
    ordner = {}
    for u in urls:
        key = urllib.parse.unquote(u[len(basis) + 1:])
        ordner.setdefault(key.rsplit("/", 1)[0] + "/", set()).add(key)
    g = {}
    for o, keys in ordner.items():
        for k, n in netz.s3_liste(basis, o, "/")[0]:
            if k in keys:
                g[netz.s3_url(basis, k)] = n
    return g


def _name(url):
    return urllib.parse.unquote(url.rsplit("/", 1)[-1])


# ===========================================================================
# HOEHE
# ===========================================================================
def _3dep(ctx, res):
    out = []
    for lat, lon in _grad_kacheln(ctx.gebiet):
        name = f"n{lat + 1:02d}w{-lon:03d}"
        prefix = f"StagedProducts/Elevation/{res}/TIFF/current/{name}/"
        for k, n in netz.s3_liste(TNM, prefix, "/")[0]:
            if k.endswith((".tif", ".xml", ".jpg")):
                out.append(Datei(netz.s3_url(TNM, k), f"{name}/{k.rsplit('/', 1)[-1]}", n))
    return out


@quelle("usgs_3dep_13", "hoehe", "USGS 3DEP 1/3 Bogensekunde (~10 m)", "USGS The National Map",
        "gemeinfrei (US Public Domain)", "1/3\" ≈ 10 m (Nord-Süd), 5 m Ost-West", "laufend",
        "szene", "standard",
        """Nahtloses Gelände-DGM (Boden, ohne Bewuchs) in Ganzgradkacheln, NAVD88. Grundraster für die
        Szene.""",
        "https://www.usgs.gov/3d-elevation-program")
def _p(ctx):
    return _3dep(ctx, "13")


@quelle("usgs_3dep_1", "hoehe", "USGS 3DEP 1 Bogensekunde (~30 m)", "USGS The National Map",
        "gemeinfrei", "1\" ≈ 30 m", "laufend", "fern", "standard",
        "Gelände-DGM für das Fernfeld (Horizont).", "https://www.usgs.gov/3d-elevation-program")
def _p(ctx):
    return _3dep(ctx, "1")


@quelle("usgs_3dep_2", "hoehe", "USGS 3DEP 2 Bogensekunden Alaska (~60 m)", "USGS The National Map",
        "gemeinfrei", "2\" ≈ 60 m", "laufend", "fern", "standard",
        "Älteres Alaska-DGM (aus Karten 1:63 360), Vergleich und Lückenfüller für das Fernfeld.",
        "https://www.usgs.gov/3d-elevation-program")
def _p(ctx):
    return _3dep(ctx, "2")


GB_IFSAR = "StagedProducts/Elevation/OPR/Projects/Copper_River_and_Glacier_Bay_AK_Mid_Accuracy_DEM/"


def _ifsar_zellen(ctx):
    """Zellen (z. B. '337') der Glacier-Bay-IfSAR-Befliegung 2012, die das Gebiet schneiden."""
    zellen = {}
    for o in netz.s3_liste(TNM, GB_IFSAR, "/")[1]:
        m = re.search(r"GB[-_](\d{3})_2012", o)
        if not m:
            continue
        vrt = [k for k, _ in netz.s3_liste(TNM, o, "/")[0] if k.endswith(".vrt")]
        if vrt:
            treffer = _vrt_auswahl(netz.s3_url(TNM, vrt[0]), ctx.gebiet)
            if treffer:
                zellen[m.group(1)] = treffer
    return zellen


@quelle("usgs_ifsar_dtm", "hoehe", "USGS IfSAR Glacier Bay 2012, DTM 5 m", "USGS / Alaska Mapping Initiative",
        "gemeinfrei", "5 m", "2012", "szene", "standard",
        """Radar-Geländemodell (Interferometrisches SAR) der Befliegung Glacier Bay 2012, Alaska Albers
        (EPSG:3338), NAVD88/GEOID09. Zellen GB-320, 336, 337 (Bucht), 338.""",
        "https://www.usgs.gov/centers/eros/science/usgs-eros-archive-digital-elevation-interferometric-synthetic-aperture-radar")
def _p(ctx):
    out = []
    zellen = _ifsar_zellen(ctx)
    urls = sorted({u for v in zellen.values() for u in v})
    gr = _groessen(TNM, urls)
    for z, us in sorted(zellen.items()):
        # je Zelle liegen zwei fast gleiche Mosaike (_<nr>.tif und _AK_IFSAR-GB-<zelle>_2012.tif):
        # das benannte genuegt
        benannt = [u for u in us if f"GB-{z}_2012" in _name(u)]
        for u in benannt or us:
            out.append(Datei(u, f"GB_{z}/{_name(u)}", gr.get(u)))
    return out


def _ifsar_zip(ctx, ordner, muster):
    zellen = set(_ifsar_zellen(ctx))
    out = []
    for k, n in netz.s3_liste(TNM, ordner)[0]:
        m = re.search(muster, k)
        if m and m.group(1) in zellen and k.endswith(".zip"):
            out.append(Datei(netz.s3_url(TNM, k), _name(k), n))
    return out


@quelle("usgs_ifsar_dsm", "hoehe", "USGS IfSAR Glacier Bay 2012, DSM 5 m (Oberfläche)", "USGS",
        "gemeinfrei", "5 m", "2012", "szene", "gross",
        "Oberflächenmodell mit Wald: zeigt die Waldkante und damit die Spur der Welle von 1958 im heutigen Bewuchs.",
        "https://www.usgs.gov/3d-elevation-program")
def _p(ctx):
    return _ifsar_zip(ctx, "StagedProducts/Elevation/DSM/TIFF/", r"IFSAR_GB_(\d{3})_2012")


@quelle("usgs_ifsar_ori", "luftbild", "USGS IfSAR Glacier Bay 2012, Radarbild (ORI) 2,5 m", "USGS",
        "gemeinfrei", "2,5 m", "2012", "szene", "gross",
        "Orthorektifiziertes Radar-Intensitätsbild, wolkenfrei; Waldgrenzen und Schuttflächen gut sichtbar.",
        "https://www.usgs.gov/3d-elevation-program")
def _p(ctx):
    return _ifsar_zip(ctx, "StagedProducts/Elevation/ORI/TIFF/", r"IFSAR_GB_(\d{3})_2012")


@quelle("usgs_lidar_glacierbay", "hoehe", "USGS Lidar Glacier Bay 2019/20 (QL1/QL2), DTM 1 m", "USGS 3DEP / NPS",
        "gemeinfrei", "1 m", "Juli 2019 – Mai 2020", "kern", "standard",
        """Das genaueste Gelände: Laserscan der Glacier-Bay-Befliegung, Teilgebiete B2, B3, 4 und 5, UTM 8N,
        NAVD88. Deckt die ganze Bucht, Cenotaph Island und die Hänge mit der Spurlinie. Punktwolken (LPC)
        gibt es im selben Projekt unter LPC/ (nicht mitgeladen, sehr groß).""",
        "https://www.usgs.gov/3d-elevation-program")
def _p(ctx):
    basis = "StagedProducts/Elevation/OPR/Projects/AK_GlacierBay_2019_B19/"
    urls = []
    for o in netz.s3_liste(TNM, basis, "/")[1]:
        vrt = [k for k, _ in netz.s3_liste(TNM, o, "/")[0] if k.endswith(".vrt")]
        if vrt:
            urls += _vrt_auswahl(netz.s3_url(TNM, vrt[0]), ctx.gebiet)
    gr = _groessen(TNM, urls)
    out = []
    for u in urls:
        teil = u.split("/AK_GlacierBay_2019_B19/")[1].split("/")[0]
        out.append(Datei(u, f"{teil}/{_name(u)}", gr.get(u)))
    return out


@quelle("copernicus_glo30", "hoehe", "Copernicus DEM GLO-30", "ESA / Airbus (Copernicus)",
        "Copernicus DEM Lizenz (frei, Namensnennung © DLR/Airbus)", "1\" ≈ 30 m", "TanDEM-X 2011–2015",
        "fern", "standard",
        "Weltweites Oberflächenmodell (DSM) aus TanDEM-X; einheitlich über Land und Gletscher, für Fernfeld und Abgleich.",
        "https://spacedata.copernicus.eu/collections/copernicus-digital-elevation-model")
def _p(ctx):
    out = []
    for lat, lon in _grad_kacheln(ctx.gebiet):
        name = f"Copernicus_DSM_COG_10_N{lat:02d}_00_W{-lon:03d}_00_DEM"
        for k, n in netz.s3_liste(COP30, name + "/")[0]:
            if k.endswith("_DEM.tif") or k.endswith("_WBM.tif") or k.endswith(".xml") or k.endswith("_EDM.tif"):
                out.append(Datei(netz.s3_url(COP30, k), f"{name}/{k.rsplit('/', 1)[-1]}", n))
    return out


def _arcticdem_mosaik(ctx, res):
    vrt = f"{PGC}/arcticdem/mosaics/v4.1/{res}_dem_tiles.vrt"
    urls = _vrt_auswahl(vrt, ctx.gebiet)
    out = []
    for u in urls:
        ordner = u[len(PGC) + 1:].rsplit("/", 1)[0] + "/"
        stamm = _name(u).replace("_dem.tif", "")
        for k, n in netz.s3_liste(PGC, ordner, "/")[0]:
            nm = k.rsplit("/", 1)[-1]
            if nm.startswith(stamm) and nm.endswith(("_dem.tif", "_browse.tif", ".json", "_count.tif", "_mad.tif")):
                out.append(Datei(netz.s3_url(PGC, k), f"{res}/{nm}", n))
    return out


ARCTICDEM_TEXT = """ArcticDEM v4.1, Oberflächenmodell (DSM) aus Maxar-Stereobildern (SETSM), WGS84-Ellipsoidhöhen,
    polarstereographisch EPSG:3413. Das Mosaik mischt Aufnahmen 2009–2021."""


@quelle("arcticdem_32m", "hoehe", "ArcticDEM Mosaik v4.1, 32 m", "Polar Geospatial Center (PGC)",
        "CC BY 4.0", "32 m", "2009–2021", "fern", "standard", ARCTICDEM_TEXT,
        "https://www.pgc.umn.edu/data/arcticdem/")
def _p(ctx):
    return _arcticdem_mosaik(ctx, "32m")


@quelle("arcticdem_10m", "hoehe", "ArcticDEM Mosaik v4.1, 10 m", "PGC", "CC BY 4.0", "10 m", "2009–2021",
        "szene", "standard", ARCTICDEM_TEXT, "https://www.pgc.umn.edu/data/arcticdem/")
def _p(ctx):
    return _arcticdem_mosaik(ctx, "10m")


@quelle("arcticdem_2m", "hoehe", "ArcticDEM Mosaik v4.1, 2 m", "PGC", "CC BY 4.0", "2 m", "2009–2021",
        "kern", "gross", ARCTICDEM_TEXT, "https://www.pgc.umn.edu/data/arcticdem/")
def _p(ctx):
    return _arcticdem_mosaik(ctx, "2m")


@quelle("arcticdem_streifen", "hoehe", "ArcticDEM Streifen s2s041 (datierte 2-m-DSM)", "PGC", "CC BY 4.0",
        "2 m", "2009 bis heute", "kern", "standard",
        """Einzelne datierte Stereo-Höhenmodelle über der Bucht. Standard: Liste (streifen.csv), STAC-JSON und
        Schummerung je Streifen (klein). Mit --gross auch das 2-m-DSM je Streifen – damit lassen sich
        Gletscherzungen, Rutschungen und Strandwälle über die Jahre vergleichen.""",
        "https://www.pgc.umn.edu/data/arcticdem/")
def _p(ctx):
    import concurrent.futures as cf
    zellen = {f"n{lat:02d}w{-lon:03d}" for lat, lon in _grad_kacheln(ctx.gebiet)}
    alle = []
    for z in sorted(zellen):
        alle += netz.s3_liste(PGC, f"arcticdem/strips/s2s041/2m/{z}/", "/")[0]
    groessen = dict(alle)
    jsons = [k for k, _ in alle if k.endswith(".json")]

    def pruefe(k):
        item = netz.hole_json(netz.s3_url(PGC, k))
        w, s, o, n = item["bbox"]
        if not ctx.gebiet.schneidet(w, s, o, n):
            return None
        return k, item

    out, zeilen = [], []
    with cf.ThreadPoolExecutor(16) as ex:
        for r in ex.map(pruefe, jsons):
            if not r:
                continue
            k, item = r
            stamm = k[:-len(".json")]
            p = item["properties"]
            zeilen.append([stamm.rsplit("/", 1)[-1], p.get("datetime", ""), p.get("pgc:avg_sun_elevs", ""),
                           p.get("pgc:valid_area_percent", ""), p.get("pgc:rmse", "")])
            for endung, gross in ((".json", False), ("_dem_10m_shade.tif", False), ("_dem.tif", True),
                                  ("_bitmask.tif", True), ("_matchtag.tif", True)):
                key = stamm + endung
                if key in groessen and (not gross or ctx.opt.get("gross")):
                    out.append(Datei(netz.s3_url(PGC, key), key.rsplit("/", 1)[-1], groessen[key]))
    zeilen.sort(key=lambda z: z[1])
    ctx.opt.setdefault("_beilagen", {})["arcticdem_streifen/streifen.csv"] = _csv(
        ["streifen", "datum", "sonnenhoehe", "gueltig_prozent", "rmse"], zeilen)
    return out


def _csv(kopf, zeilen):
    s = io.StringIO()
    w = csv.writer(s, lineterminator="\n")
    w.writerow(kopf)
    w.writerows(zeilen)
    return s.getvalue()


# ===========================================================================
# BATHYMETRIE
# ===========================================================================
def _gpkg_huellen(pfad):
    """Alle Zeilen einer GeoPackage-Tabelle mit Huellrechteck (minx, maxx, miny, maxy)."""
    c = sqlite3.connect(pfad)
    t = c.execute("select table_name from gpkg_contents where data_type='features'").fetchone()[0]
    spalten = [r[1] for r in c.execute(f"pragma table_info('{t}')")]
    gi = spalten.index("geom") if "geom" in spalten else spalten.index("geometry")
    for r in c.execute(f"select * from '{t}'"):
        b = r[gi]
        if not b:
            continue
        flags = b[3]
        e = "<" if flags & 1 else ">"
        if (flags >> 1) & 7:
            huelle = struct.unpack(e + "4d", b[8:40])
        else:
            continue
        yield huelle, {spalten[i]: r[i] for i in range(len(spalten)) if i != gi}
    c.close()


@quelle("noaa_bluetopo", "bathymetrie", "NOAA BlueTopo (Wassertiefen)", "NOAA Office of Coast Survey",
        "gemeinfrei (US Public Domain)", "4–16 m", "Auslieferung 2025/26", "szene", "standard",
        """Zusammengesetzte Bathymetrie aus den besten verfügbaren Vermessungen (Fächerecholot, Lidar,
        Seekarten), GeoTIFF mit Unsicherheit und Quellenkennung je Zelle, NAVD88, UTM 8N. Das Kachelschema
        (GeoPackage) wird mitgeladen.""",
        "https://nauticalcharts.noaa.gov/data/bluetopo.html")
def _p(ctx):
    dateien, _ = netz.s3_liste(NOAA_BATHY, "BlueTopo/_BlueTopo_Tile_Scheme/", "/")
    gpkg = max(k for k, _ in dateien if k.endswith(".gpkg"))
    out = [Datei(netz.s3_url(NOAA_BATHY, gpkg), _name(gpkg), dict(dateien)[gpkg])]
    with tempfile.TemporaryDirectory() as d:
        p = d + "/schema.gpkg"
        with open(p, "wb") as f:
            f.write(netz.hole(netz.s3_url(NOAA_BATHY, gpkg)))
        for (w, o, s, n), z in _gpkg_huellen(p):
            if ctx.gebiet.schneidet(w, s, o, n) and z.get("GeoTIFF_Link"):
                out.append(Datei(z["GeoTIFF_Link"], f"{z['tile']}/{_name(z['GeoTIFF_Link'])}",
                                 sha256=z.get("GeoTIFF_SHA256_Checksum"), notiz=z.get("Resolution", "")))
                if z.get("RAT_Link"):
                    out.append(Datei(z["RAT_Link"], f"{z['tile']}/{_name(z['RAT_Link'])}",
                                     sha256=z.get("RAT_SHA256_Checksum")))
    gr = _groessen(NOAA_BATHY, [d.url for d in out[1:]])
    for d in out[1:]:
        d.groesse = gr.get(d.url)
    return out


# ===========================================================================
# KARTEN
# ===========================================================================
def _topo_csv(zipname):
    roh = netz.hole(f"{TNM}/StagedProducts/Maps/Metadata/{zipname}")
    z = zipfile.ZipFile(io.BytesIO(roh))
    name = [n for n in z.namelist() if n.endswith(".csv")][0]
    csv.field_size_limit(1 << 30)
    return csv.DictReader(io.TextIOWrapper(z.open(name), encoding="utf-8", errors="replace"))


def _im_gebiet(r, g):
    try:
        w, o, n, s = (float(r[k]) for k in ("westbc", "eastbc", "northbc", "southbc"))
    except (KeyError, ValueError):
        return False
    return g.schneidet(w, s, o, n)


@quelle("usgs_topo_historisch", "karten", "USGS historische topographische Karten (HTMC)", "USGS",
        "gemeinfrei", "1:250 000 (Fernfeld) und 1:63 360 (Szene)", "1951–1982", "fern", "standard",
        """Alle Ausgaben der Blätter um Lituya Bay als GeoTIFF: Mt Fairweather 1:250 000 von 1951
        (Luftbilder 1948, also der Stand vor der Welle) und 1961, die Blätter Mount Fairweather 1:63 360
        (C-5, C-6 mit der Bucht; Luftbilder 1948/1954). Dazu die Liste aller Ausgaben (karten.csv). Mit
        --gross auch die GeoPDFs.""",
        "https://ngmdb.usgs.gov/topoview/")
def _p(ctx):
    g = ctx.gebiet
    szene = geo.SZENE
    out, zeilen = [], []
    for r in _topo_csv("historicaltopo.zip"):
        if not _im_gebiet(r, g):
            continue
        massstab = r.get("map_scale", "")
        if massstab not in ("250000",) and not _im_gebiet(r, szene):
            continue
        name = f"{r['map_name']}_{r['date_on_map']}_{r['imprint_year']}_{massstab}_{r['scan_id']}".replace(" ", "_")
        zeilen.append([r["map_name"], massstab, r["date_on_map"], r["imprint_year"], r["aerial_photo_year"],
                       r["survey_year"], r["edition"], r["westbc"], r["southbc"], r["eastbc"], r["northbc"],
                       r["geotiff_url"], r["product_url"]])
        if r.get("geotiff_url"):
            out.append(Datei(r["geotiff_url"], f"{massstab}/{name}.tif"))
        if r.get("product_url") and ctx.opt.get("gross"):
            out.append(Datei(r["product_url"], f"{massstab}/pdf/{name}.pdf",
                             int(r["product_filesize"]) if r.get("product_filesize", "").isdigit() else None))
        if r.get("thumbnail_url"):
            out.append(Datei(r["thumbnail_url"], f"{massstab}/vorschau/{name}.jpg"))
    gr = _groessen(TNM, [d.url for d in out if d.url.startswith(TNM) and d.groesse is None])
    for d in out:
        if d.groesse is None:
            d.groesse = gr.get(d.url) or gr.get(netz.s3_url(TNM, urllib.parse.unquote(d.url[len(TNM) + 1:])))
    zeilen.sort(key=lambda z: (z[1], z[0], z[2]))
    ctx.opt.setdefault("_beilagen", {})["usgs_topo_historisch/karten.csv"] = _csv(
        ["blatt", "massstab", "datum", "druck", "luftbild", "aufnahme", "ausgabe", "west", "sued", "ost", "nord",
         "geotiff", "pdf"], zeilen)
    return out


@quelle("usgs_ustopo", "karten", "USGS US Topo (aktuell, GeoPDF)", "USGS", "gemeinfrei", "1:25 000",
        "2010er", "szene", "standard",
        "Aktuelle Karten mit Höhenlinien, Gewässern, Namen und Luftbild als Ebenen (GeoPDF).",
        "https://www.usgs.gov/programs/national-geospatial-program/us-topo-maps-america")
def _p(ctx):
    out = []
    for r in _topo_csv("ustopo_current.zip"):
        if _im_gebiet(r, ctx.gebiet) and r.get("product_url"):
            n = r.get("product_filesize", "")
            out.append(Datei(r["product_url"], _name(r["product_url"]), int(n) if n.isdigit() else None))
    return out


# ===========================================================================
# LUFTBILD / SATELLIT
# ===========================================================================
@quelle("sentinel2", "luftbild", "Sentinel-2 L2A, klarste Sommerszenen (Echtfarbe 10 m)", "ESA Copernicus / Element 84",
        "Copernicus Sentinel Data (frei, Namensnennung)", "10 m", "2017 bis heute", "kern", "standard",
        """Für jede Sentinel-2-Kachel über dem Gebiet (08VLK, 08VLL) die klarsten Szenen von Juni bis
        September (Wolken und fehlende Pixel aus dem STAC-JSON). Je Szene das Echtfarbenbild TCI, die
        Szenenklassen SCL, das Vorschaubild und das JSON. Anzahl je Kachel: --szenen (Vorgabe 3).""",
        "https://registry.opendata.aws/sentinel-2-l2a-cogs/")
def _p(ctx):
    import concurrent.futures as cf
    kacheln = set()
    for e, n in ctx.gebiet.rand_utm(10):
        kacheln.add(geo.mgrs_kachel(*geo.utm_rueckwaerts(e, n)))
    jahre = ctx.opt.get("jahre") or range(2017, 2027)
    kandidaten = []
    for k in sorted(kacheln):
        z, b, q = k[:2].lstrip("0"), k[2], k[3:]
        for j in jahre:
            for m in (6, 7, 8, 9):
                kandidaten += netz.s3_liste(S2, f"sentinel-s2-l2a-cogs/{z}/{b}/{q}/{j}/{m}/", "/")[1]

    def lies(o):
        name = o.rstrip("/").rsplit("/", 1)[-1]
        try:
            p = netz.hole_json(netz.s3_url(S2, o + name + ".json"))["properties"]
        except Exception:
            return None
        return o, name, p.get("eo:cloud_cover", 100), p.get("s2:nodata_pixel_percentage", 100), p.get("datetime", "")

    with cf.ThreadPoolExecutor(16) as ex:
        szenen = [s for s in ex.map(lies, kandidaten) if s]
    zeilen = sorted([[s[1], s[4], s[2], s[3]] for s in szenen], key=lambda z: z[1])
    ctx.opt.setdefault("_beilagen", {})["sentinel2/szenen.csv"] = _csv(
        ["szene", "zeit", "wolken_prozent", "ohne_daten_prozent"], zeilen)
    out = []
    for k in sorted(kacheln):
        eigene = [s for s in szenen if f"_{k.lstrip('0')}_" in s[1] and s[3] < 5]
        eigene.sort(key=lambda s: s[2])
        for o, name, wolken, leer, zeit in eigene[:int(ctx.opt.get("szenen", 3))]:
            dateien = dict(netz.s3_liste(S2, o, "/")[0])
            for f in ("TCI.tif", "SCL.tif", "thumbnail.jpg", name + ".json"):
                if o + f in dateien:
                    out.append(Datei(netz.s3_url(S2, o + f), f"{name}/{f}", dateien[o + f],
                                     notiz=f"{zeit[:10]} Wolken {wolken:.1f} %"))
    return out


@quelle("esa_worldcover", "vegetation", "ESA WorldCover 2021 (Landbedeckung 10 m)", "ESA",
        "CC BY 4.0", "10 m", "2021", "fern", "standard",
        "Klassen Wald, Gebüsch, Grasland, Schnee/Eis, offener Boden, Wasser – für Bewuchs und Gletscherflächen.",
        "https://esa-worldcover.org/")
def _p(ctx):
    import math
    out = []
    w, s, o, n = ctx.gebiet.lonlat
    gesehen = set()
    for lat, lon in _grad_kacheln(ctx.gebiet):
        la, lo = math.floor(lat / 3) * 3, math.floor(lon / 3) * 3
        name = f"ESA_WorldCover_10m_2021_v200_N{la:02d}W{-lo:03d}_Map.tif"
        if name in gesehen:
            continue
        gesehen.add(name)
        for k, sz in netz.s3_liste(WORLDCOVER, "v200/2021/map/" + name)[0]:
            out.append(Datei(netz.s3_url(WORLDCOVER, k), name, sz))
    return out


# ===========================================================================
# GLETSCHER
# ===========================================================================
@quelle("itslive_geschwindigkeit", "gletscher", "ITS_LIVE Gletschergeschwindigkeit (Mosaik v2, Alaska)",
        "NASA MEaSUREs ITS_LIVE", "CC BY 4.0", "120 m", "1985–2022 (Mittel)", "fern", "gross",
        """Fließgeschwindigkeit der Gletscher (Betrag v, Komponenten vx/vy, Fehler), ganz Alaska
        (RGI-Region 01), Alaska Albers. Für Lituya-, Nord-Crillon- und Cascade-Gletscher.""",
        "https://its-live.jpl.nasa.gov/")
def _p(ctx):
    out = []
    for k, n in netz.s3_liste(ITSLIVE, "velocity_mosaic/v2/static/cog/ITS_LIVE_velocity_120m_RGI01A_0000_v02_")[0]:
        if k.endswith(("_v.tif", "_vx.tif", "_vy.tif", "_v_error.tif", "_landice.tif")):
            out.append(Datei(netz.s3_url(ITSLIVE, k), _name(k), n))
    return out


# ===========================================================================
# VEKTOR / UEBERSICHT
# ===========================================================================
@quelle("natural_earth", "vektor", "Natural Earth 10 m (Küste, Land, Gletscher, Grenzen)", "Natural Earth",
        "gemeinfrei", "1:10 Mio.", "v5", "fern", "standard",
        "Küstenlinie, Landfläche, vergletscherte Flächen, Bundesstaaten – grobe Übersicht und Kartenrahmen. Geprüft.",
        "https://www.naturalearthdata.com/")
def _p(ctx):
    out = []
    for o in ("10m_physical/ne_10m_coastline.zip", "10m_physical/ne_10m_land.zip",
              "10m_physical/ne_10m_glaciated_areas.zip", "10m_physical/ne_10m_minor_islands.zip",
              "10m_cultural/ne_10m_admin_1_states_provinces.zip", "10m_cultural/ne_10m_populated_places.zip"):
        d = dict(netz.s3_liste(NE, o)[0])
        if o in d:
            out.append(Datei(netz.s3_url(NE, o), _name(o), d[o]))
    return out


# Feste Adressen (ausserhalb von AWS) stehen in quellen_web.py.
import quellen_web  # noqa: E402,F401  (registriert die Web-Quellen)
