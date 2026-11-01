"""Quellen ausserhalb der AWS-Buckets: feste Adressen, Abfragen, Verzeichnisse.

Vermerk "geprueft": die Adresse wurde am 01.11.2026 abgerufen.
Vermerk "ungeprueft": die Adresse folgt dem dokumentierten Muster des Anbieters, war aber aus der
Arbeitsumgebung nicht erreichbar. Schlaegt sie fehl, steht sie im Inventar unter "fehlgeschlagen".
"""

import html
import re
import urllib.parse

import geo
import netz
from quellen import Datei, quelle, statisch, _name, _grad_kacheln  # noqa: F401

LIDAR = "https://noaa-nos-coastal-lidar-pds.s3.amazonaws.com"
GEBCO = "https://s3.us-west-2.amazonaws.com/us-west-2.opendata.source.coop"


# ===========================================================================
# HOEHE / BATHYMETRIE
# ===========================================================================
@quelle("usace_topobathy_2019", "bathymetrie", "USACE NCMP Topobathy-Lidar 2019, Block Lituya (1 m)",
        "USACE JALBTCX / NOAA OCM", "gemeinfrei", "1 m", "9.–28. Juli 2019", "szene", "standard",
        """Laserscan (CZMIL) von Ufer und Flachwasser: Strände, La Chaussee Spit, Eingang und Flachwasser der
        Bucht, Boden ohne Bewuchs. NAD83(2011) geographisch (EPSG:6318), NAVD88 (Geoid12B). Dazu
        Kachelübersicht und Metadaten. Geprüft.""",
        "https://coast.noaa.gov/dataviewer/")
def _p(ctx):
    basis = "dem/USACE_AK_DEM_2019_9055/"
    out = []
    for k, n in netz.s3_liste(LIDAR, basis + "Blk_530_Lituya/")[0]:
        out.append(Datei(netz.s3_url(LIDAR, k), "Blk_530_Lituya/" + _name(k), n))
    for k, n in netz.s3_liste(LIDAR, basis, "/")[0]:
        if k.endswith(("tileindex_USACE_AK_DEM_2019.zip", "_met.xml", "_forHumans.html")):
            out.append(Datei(netz.s3_url(LIDAR, k), _name(k), n))
    return out


@quelle("gebco_2025", "bathymetrie", "GEBCO 2025, Ausschnitt Fernfeld", "GEBCO / BODC (Spiegel: source.coop)",
        "GEBCO-Nutzungsbedingungen (frei, Namensnennung)", "15\" ≈ 460 m", "2025", "fern", "standard",
        """Weltweites Gitter Land und Meer. Geladen wird nur der Ausschnitt des Gebiets (HTTP-Range aus dem
        3,3-GB-COG), gespeichert als GeoTIFF int16, EPSG:4326. Geprüft.""",
        "https://www.gebco.net/data-products/gridded-bathymetry-data")
def _p(ctx):
    w, s, o, n = ctx.gebiet.lonlat
    url = GEBCO + "/ausantarctic/gebco/GEBCO_2025.tif"
    return [Datei(url, f"GEBCO_2025_{ctx.gebiet.name}.tif", notiz="ausschnitt", ausschnitt=(w, s, o, n)),
            Datei(GEBCO + "/ausantarctic/gebco/GEBCO_2025.stac-item.json", "GEBCO_2025.stac-item.json")]


statisch("ncei_dem_suedost_alaska", "bathymetrie", "NCEI Küsten-DEM Southeast Alaska 8/3\" (MHHW, 2010)",
         "NOAA NCEI", "gemeinfrei", "8/3\" ≈ 80 m", "2010", "fern", "gross",
         """Zusammengesetztes Land-See-Modell für Tsunami-Rechnungen, Bezug MHHW, netCDF (ganze Region).
         Ungeprüft.""",
         "https://www.ngdc.noaa.gov/thredds/catalog/regional/catalog.html",
         [("https://www.ngdc.noaa.gov/thredds/fileServer/regional/southeast_alaska_83_mhhw_2010.nc",
           "southeast_alaska_83_mhhw_2010.nc")])

statisch("ncei_dem_elfin_cove", "bathymetrie", "NCEI Tsunami-DEM Elfin Cove 1/3\" (MHW, 2011)", "NOAA NCEI",
         "gemeinfrei", "1/3\" ≈ 10 m", "2011", "fern", "standard",
         "Nächstes hochaufgelöstes Tsunami-DEM (Cross Sound), Vorbild für die Aufbereitung. Ungeprüft.",
         "https://www.ngdc.noaa.gov/thredds/catalog/regional/catalog.html",
         [("https://www.ngdc.noaa.gov/thredds/fileServer/regional/elfin_cove_13_mhw_2011.nc",
           "elfin_cove_13_mhw_2011.nc")])

statisch("etopo_2022", "bathymetrie", "ETOPO 2022 15\" (Kachel N60W150)", "NOAA NCEI", "gemeinfrei",
         "15\" ≈ 460 m", "2022", "fern", "standard",
         "Globales Relief mit Eis-Oberfläche; Kachel 15° × 15°, enthält das ganze Fernfeld. Ungeprüft.",
         "https://www.ncei.noaa.gov/products/etopo-global-relief-model",
         [("https://www.ngdc.noaa.gov/thredds/fileServer/global/ETOPO2022/15s/15s_surface_elev_netcdf/"
           "ETOPO_2022_v1_15s_N60W150_surface.nc", "ETOPO_2022_v1_15s_N60W150_surface.nc")])


# NOS-Vermessungen in Lituya Bay (aus der Quellentabelle der BlueTopo-Kacheln)
VERMESSUNGEN = {
    "H04608": ("H04001-H06000", "1926"), "H04648": ("H04001-H06000", "1926"),
    "H06581": ("H06001-H08000", "1940"), "H06582": ("H06001-H08000", "1940"),
    "H06583": ("H06001-H08000", "1940"),
    "H08492": ("H08001-H10000", "21.7.–27.8.1959, ein Jahr nach der Welle"),
    "H14228": ("H14001-H16000", "Juli 2025, NOAA Ship Rainier, Fächerecholot"),
}


def _verzeichnis(url, tiefe=2):
    """Dateien eines Apache-Verzeichnisindex (rekursiv)."""
    out = []
    try:
        seite = netz.hole_text(url)
    except Exception:
        return out
    for href in re.findall(r'href="([^"?#]+)"', seite):
        href = html.unescape(href)
        if href.startswith(("/", "http", "..")) or href in ("./",):
            continue
        ziel = urllib.parse.urljoin(url, href)
        if href.endswith("/"):
            if tiefe > 0:
                out += _verzeichnis(ziel, tiefe - 1)
        else:
            out.append(ziel)
    return out


@quelle("nos_vermessungen", "bathymetrie", "NOS-Vermessungen Lituya Bay 1926, 1940, 1959, 2025", "NOAA NCEI",
        "gemeinfrei", "Lotungen; Blätter 1:10 000–1:20 000", "1926–2025", "kern", "standard",
        """Die hydrographischen Aufnahmen der Bucht: H04608/H04648 (1926), H06581–83 (1940, Stand vor der
        Welle), H08492 (1959, nach der Welle) und H14228 (2025). Je Vermessung alle Dateien des
        NCEI-Verzeichnisses: Beschreibender Bericht (DR), Lotungen (GEODAS xyz/h93), Blattscans, BAG.
        Ungeprüft (Verzeichnismuster von NCEI).""",
        "https://www.ncei.noaa.gov/maps/bathymetry/")
def _p(ctx):
    out = []
    for h, (bereich, jahr) in VERMESSUNGEN.items():
        url = f"https://data.ngdc.noaa.gov/platforms/ocean/nos/coast/{bereich}/{h}/"
        dateien = _verzeichnis(url)
        if not dateien:
            out.append(Datei(url, f"{h}/index.html", notiz=f"{jahr}; Verzeichnis nicht erreichbar"))
        for d in dateien:
            out.append(Datei(d, f"{h}/" + urllib.parse.unquote(d[len(url):]), notiz=jahr))
        out.append(Datei(f"https://data.noaa.gov/waf/NOAA/NESDIS/NGDC/MGG/NOS/{bereich}/iso/xml/{h}.xml",
                         f"{h}/{h}_iso.xml", notiz=jahr))
    return out


# ===========================================================================
# KARTEN
# ===========================================================================
statisch("noaa_enc", "karten", "NOAA Seekarten ENC (S-57), Lituya Bay und Anfahrt", "NOAA Office of Coast Survey",
         "gemeinfrei", "Hafen (US5) und Anfahrt (US4); Karte 16762 1:20 000", "laufend", "kern", "standard",
         """Elektronische Seekarten: Küstenlinie, Tiefenlinien, Lotungen, Untiefen, Wracks, Namen. Zellen
         US5AK1NB, US5AK1OB, US5AK1OC (Bucht, aus Karte 16762) und US4AK5SE (Anfahrt). Ungeprüft.""",
         "https://charts.noaa.gov/ENCs/ENCs.shtml",
         [(f"https://www.charts.noaa.gov/ENCs/{z}.zip", f"{z}.zip")
          for z in ("US5AK1NB", "US5AK1OB", "US5AK1OC", "US4AK5SE")])

statisch("noaa_karte_16760", "karten", "NOAA Seekarte 16760 Cross Sound to Yakutat Bay (PDF)", "NOAA",
         "gemeinfrei", "1:300 000", "letzte Ausgabe", "fern", "standard",
         """Übersichtskarte der Küste. Hinweis: Die Rasterkarte 16762 „Lituya Bay“ (1:20 000) wurde 2023
         eingestellt und ist nur noch als ENC oder NOAA Custom Chart zu haben. Ungeprüft.""",
         "https://charts.noaa.gov/InteractiveCatalog/nrnc.shtml",
         [("https://www.charts.noaa.gov/PDFs/16760.pdf", "16760.pdf"),
          ("https://www.charts.noaa.gov/PDFs/16762.pdf", "16762.pdf", "eingestellt, vermutlich 404")])

statisch("noaa_karten_historisch", "karten", "Historische Seekarten C&GS 8505 „Lituya Bay“ (1910–1942)",
         "NOAA Historical Map & Chart Collection", "gemeinfrei", "1:20 000", "1910, 1914, 1929, 1942",
         "kern", "manuell",
         """Karte 8505 (1929; 1942 nach der Aufnahme 1940) zeigt die Bucht vor 1958. Suche nach „8505“ oder
         „Lituya“ im Viewer; Verzeichnis der Ausgaben bei der University of North Texas (MOCAT).""",
         "https://historicalcharts.noaa.gov/", [])


# ===========================================================================
# EREIGNIS
# ===========================================================================
HAZ = "https://www.ngdc.noaa.gov/hazel/hazard-service/api/v1"

statisch("ncei_tsunami", "ereignis", "NCEI Tsunami-Datenbank: Ereignisse und Auflaufhöhen", "NOAA NCEI / WDS",
         "gemeinfrei", "Punkte", "1853–1958 und später", "szene", "standard",
         """Die Tsunamis von Lituya Bay (1853/54, 1874, 1899, 1936, 1958) mit allen gemeldeten Auflaufhöhen
         (bis 524 m) als JSON, dazu das Erdbeben 1958 (NCEI-Nr. 4167). Ungeprüft (API-Muster).""",
         "https://www.ngdc.noaa.gov/hazel/view/hazards/tsunami/event-search",
         [(HAZ + "/tsunamis/events?minYear=1850&maxYear=2026&minLatitude={s}&maxLatitude={n}"
                 "&minLongitude={w}&maxLongitude={o}", "tsunami_ereignisse.json"),
          (HAZ + "/tsunamis/runups?minYear=1850&maxYear=2026&minLatitude={s}&maxLatitude={n}"
                 "&minLongitude={w}&maxLongitude={o}", "tsunami_auflauf.json"),
          (HAZ + "/tsunamis/events?country=USA&minYear=1850&maxYear=1960", "tsunami_ereignisse_usa_bis1960.json",
           "Rückfall, wenn die Bereichsabfrage nicht greift: nach LITUYA filtern"),
          (HAZ + "/earthquakes/4167", "erdbeben_1958_ncei_4167.json")])

FDSN = "https://earthquake.usgs.gov/fdsnws/event/1/query"
statisch("usgs_erdbeben", "ereignis", "USGS ComCat: Erdbeben 10.07.1958 (Mw 7,8) und Erdbeben der Region",
         "USGS Earthquake Hazards", "gemeinfrei", "Punkte", "1900 bis heute", "fern", "standard",
         """Das Fairweather-Beben (iscgem884702) als GeoJSON; alle Beben ab M 4 im Gebiet seit 1900. Ungeprüft.""",
         "https://earthquake.usgs.gov/earthquakes/eventpage/iscgem884702",
         [(FDSN + "?eventid=iscgem884702&format=geojson", "erdbeben_1958_iscgem884702.geojson"),
          (FDSN + "?format=geojson&starttime=1900-01-01&minmagnitude=4&minlatitude={s}&maxlatitude={n}"
                  "&minlongitude={w}&maxlongitude={o}&orderby=time-asc", "erdbeben_ab_M4.geojson")])

statisch("stoerungen", "geologie", "Quartäre Störungen (Fairweather-Störung)", "USGS / Alaska DGGS",
         "gemeinfrei", "1:250 000", "2017/2020", "fern", "standard",
         """Verlauf der Fairweather-Störung, die den Graben am Kopf der Bucht bildet: USGS Quaternary Fault
         and Fold Database (ganz USA, Shapefile) und Alaska DGGS MP 141. Ungeprüft.""",
         "https://www.usgs.gov/programs/earthquake-hazards/faults",
         [("https://earthquake.usgs.gov/static/lfs/nshm/qfaults/Qfaults_GIS.zip", "Qfaults_GIS.zip"),
          ("https://geoportal.dggs.dnr.alaska.gov/arcgis/rest/services/apps/mp141/MapServer/0/query"
           "?where=1%3D1&geometry={w},{s},{o},{n}&geometryType=esriGeometryEnvelope&inSR=4326"
           "&spatialRel=esriSpatialRelIntersects&outFields=*&outSR=4326&f=geojson", "dggs_mp141_stoerungen.geojson")])


# ===========================================================================
# DOKUMENTE (mit Karten und Plaenen)
# ===========================================================================
statisch("dokumente", "dokumente", "Berichte und Aufsätze zur Welle von 1958", "USGS, NHESS u. a.",
         "USGS gemeinfrei; NHESS CC BY 4.0; sonst wie angegeben", "–", "1938–2020", "kern", "standard",
         """Miller 1960 (USGS Professional Paper 354-C, mit Karte 1:50 000 der Zerstörungszone und Spurlinie);
         Mertie 1931 (USGS Bulletin 836-B, Geologie um Lituya Bay); Fritz, Hager & Minor 2001 (Science of
         Tsunami Hazards 19/1, Heft); Franco et al. 2020 (Bathymetrie vor 1958); González-Vida et al. 2019
         (Modell der Welle). Teilweise ungeprüft.""",
         "https://pubs.usgs.gov/publication/pp354C",
         [("https://pubs.usgs.gov/pp/0354c/report.pdf", "Miller_1960_PP354C.pdf"),
          ("https://pubs.usgs.gov/pp/0354c/plate-1.pdf", "Miller_1960_PP354C_Tafel1.pdf", "Name der Tafel geschätzt"),
          ("https://pubs.usgs.gov/bul/0836b/report.pdf", "Mertie_1931_B836B.pdf"),
          ("https://corpora.tika.apache.org/base/docs/govdocs1/423/423993.pdf", "Fritz_Hager_Minor_2001_STH19-1.pdf"),
          ("https://nhess.copernicus.org/articles/20/2255/2020/nhess-20-2255-2020.pdf", "Franco_2020_NHESS.pdf"),
          ("https://nhess.copernicus.org/articles/19/369/2019/nhess-19-369-2019.pdf", "Gonzalez-Vida_2019_NHESS.pdf"),
          ("https://dggs.alaska.gov/webpubs/usgs/p/text/p0354c.pdf", "Miller_1960_PP354C_DGGS.pdf", "Spiegel"),
          ("https://stevedutch.net/lituyabay/lituya0.htm", "Miller_1960_Abschrift_Dutch.html"),
          ("https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation/metadata/AK_GlacierBay_2019_B19/"
           "AK_GlacierBay_2019_B19_WP_Report.pdf", "USGS_Lidar_GlacierBay_2019_Bericht.pdf")])

# Fotos von D. J. Miller (USGS Denver Library), ScienceBase-Eintraege
MILLER_FOTOS = ["51dd90b9e4b0f72b4471cc6f", "51dd90fbe4b0f72b4471cc9d", "51dd9142e4b0f72b4471cccf",
                "51dd90e2e4b0f72b4471cc8b", "51dd90cae4b0f72b4471cc7b", "51dd90b2e4b0f72b4471cc69"]


@quelle("fotos_miller", "fotos", "Fotos von D. J. Miller 1958 (USGS Photographic Library)", "USGS",
        "gemeinfrei", "Fotos", "August 1958", "kern", "standard",
        """Luft- und Bodenaufnahmen von Spurlinie, Waldschneise und Gilbert Inlet wenige Wochen nach der
        Welle, über ScienceBase (JSON und Bilddateien). Ungeprüft.""",
        "https://www.sciencebase.gov/catalog/")
def _p(ctx):
    out = []
    for i in MILLER_FOTOS:
        j = f"https://www.sciencebase.gov/catalog/item/{i}?format=json"
        out.append(Datei(j, f"{i}/eintrag.json"))
        try:
            item = netz.hole_json(j)
        except Exception:
            continue
        for f in item.get("files", []):
            if f.get("url"):
                out.append(Datei(f["url"], f"{i}/{f.get('name') or _name(f['url'])}", f.get("size")))
    return out

MILLER_FOTOS.append("51dd9147e4b0f72b4471ccd5")  # Kopf der Bucht

# ===========================================================================
# NAMEN UND GRENZEN
# ===========================================================================
TNM = "https://prd-tnm.s3.amazonaws.com"


@quelle("gnis_namen", "namen", "GNIS: amtliche Ortsnamen Alaska", "USGS / U.S. Board on Geographic Names",
        "gemeinfrei", "Punkte", "laufend", "szene", "standard",
        """Alle amtlichen Namen Alaskas (Gilbert Inlet, Crillon Inlet, Cenotaph Island, La Chaussee Spit,
        Harbor Point, The Paps, Fish Lake, Gletscher und Gipfel) mit Koordinaten, dazu das
        Formatblatt. Beim Laden wird ein Auszug für das Gebiet als GeoJSON geschrieben
        (gnis_<gebiet>.geojson). Geprüft.""",
        "https://www.usgs.gov/us-board-on-geographic-names")
def _p(ctx):
    out = []
    for k in ("StagedProducts/GeographicNames/DomesticNames/DomesticNames_AK_Text.zip",
              "StagedProducts/GeographicNames/GNIS_file_format.pdf"):
        n = dict(netz.s3_liste(TNM, k)[0]).get(k)
        out.append(Datei(netz.s3_url(TNM, k), _name(k), n))
    ctx.opt.setdefault("_beilagen", {})[f"gnis_namen/gnis_{ctx.gebiet.name}.geojson"] = gnis_geojson(ctx.gebiet)
    return out


def gnis_geojson(g, roh=None):
    """Auszug der GNIS-Namen im Gebiet als GeoJSON-Text."""
    import io
    import json
    import zipfile
    roh = roh or netz.hole(TNM + "/StagedProducts/GeographicNames/DomesticNames/DomesticNames_AK_Text.zip")
    z = zipfile.ZipFile(io.BytesIO(roh))
    name = [n for n in z.namelist() if n.endswith(".txt")][0]
    text = z.read(name).decode("utf-8-sig", errors="replace").splitlines()
    kopf = text[0].split("|")
    feats = []
    for zeile in text[1:]:
        r = dict(zip(kopf, zeile.split("|")))
        try:
            lat, lon = float(r["prim_lat_dec"]), float(r["prim_long_dec"])
        except (KeyError, ValueError):
            continue
        if not (lat or lon) or not g.schneidet(lon, lat, lon, lat):
            continue
        e, n = geo.utm_vorwaerts(lon, lat)
        eig = {k: r.get(k, "") for k in ("feature_id", "feature_name", "feature_class", "map_name",
                                           "elev_in_m", "date_created", "date_edited")}
        eig.update({"utm8n_ost": round(e, 1), "utm8n_nord": round(n, 1)})
        feats.append({"type": "Feature", "properties": eig,
                      "geometry": {"type": "Point", "coordinates": [lon, lat]}})
    feats.sort(key=lambda f: (f["properties"]["feature_class"], f["properties"]["feature_name"]))
    return json.dumps({"type": "FeatureCollection", "features": feats}, ensure_ascii=False, indent=1)


NPS_LRD = ("https://services1.arcgis.com/fBc8EJBxQRMcHlei/arcgis/rest/services/"
           "NPS_Land_Resources_Division_Boundary_and_Tract_Data_Service/FeatureServer")
WILD = ("https://services1.arcgis.com/ERdCHt0sNM6dENSD/arcgis/rest/services/"
        "Wilderness_Areas_in_the_United_States/FeatureServer/0/query")
statisch("nps_grenzen", "namen", "Glacier Bay National Park & Preserve: Grenze und Wildnisgebiet", "NPS",
         "gemeinfrei", "Polygone", "laufend", "szene", "standard",
         "Parkgrenze (Unit GLBA) und Glacier Bay Wilderness als GeoJSON aus den ArcGIS-Diensten. Ungeprüft.",
         "https://www.nps.gov/glba/",
         [(NPS_LRD + "/2/query?where=UNIT_CODE%3D%27GLBA%27&outFields=%2A&returnGeometry=true&outSR=4326&f=geojson",
           "nps_glba_grenze.geojson"),
          (WILD + "?where=NAME%3D%27Glacier%20Bay%20Wilderness%27&outFields=%2A&returnGeometry=true&outSR=4326"
                  "&f=geojson", "glacier_bay_wilderness.geojson")])

statisch("nps_karten", "karten", "NPS-Karten Glacier Bay (Faltblatt, Seekarte 1:80 000)", "NPS",
         "gemeinfrei", "–", "laufend", "fern", "manuell",
         """Faltblattkarte und Parkkarten des NPS; Tlingit-Ortsnamen („Tlingit Place Names of Huna Káawu“:
         Mount Fairweather = Tsalxaan, Lituya = „See in der Landspitze“).""",
         "https://www.nps.gov/glba/planyourvisit/maps.htm", [])

# ===========================================================================
# OPENSTREETMAP
# ===========================================================================
OVERPASS = "https://overpass-api.de/api/interpreter?data="
statisch("osm", "vektor", "OpenStreetMap: alle Objekte im Gebiet (Overpass)", "OpenStreetMap-Mitwirkende",
         "ODbL 1.0", "Vektor", "aktuell", "szene", "standard",
         """Küste, Gewässer, Gletscher, Wege, Hütten, Namen als JSON mit Geometrie. Ungeprüft.""",
         "https://www.openstreetmap.org/",
         [(OVERPASS + "%5Bout%3Ajson%5D%5Btimeout%3A300%5D%3Bnwr%28{s}%2C{w}%2C{n}%2C{o}%29%3Bout%20body%20geom%3B",
           "osm_overpass.json")])

statisch("osm_gross", "vektor", "OpenStreetMap: Alaska-Auszug und Küstenpolygone", "Geofabrik / OSM",
         "ODbL 1.0", "Vektor", "aktuell", "fern", "gross",
         "Alaska als PBF (Geofabrik) und die weltweiten Land- und Wasserpolygone (je 0,6–0,9 GB). Ungeprüft.",
         "https://osmdata.openstreetmap.de/",
         [("https://download.geofabrik.de/north-america/us/alaska-latest.osm.pbf", "alaska-latest.osm.pbf"),
          ("https://osmdata.openstreetmap.de/download/land-polygons-split-4326.zip", "land-polygons-split-4326.zip"),
          ("https://osmdata.openstreetmap.de/download/coastlines-split-4326.zip", "coastlines-split-4326.zip")])

# ===========================================================================
# GLETSCHER
# ===========================================================================
statisch("rgi7", "gletscher", "Randolph Glacier Inventory 7.0, Region 01 Alaska", "GLIMS / NSIDC",
         "CC BY 4.0", "Polygone", "um 2000", "fern", "standard",
         """Gletscherumrisse (Shapefile, Attribute, Hypsometrie), Spiegel der UNESCO ohne Anmeldung; RGI 6.0
         von GLIMS zum Vergleich und für die Dickenkarten (die auf RGI 6 beruhen). Ungeprüft.""",
         "https://www.glims.org/rgi_user_guide/",
         [("https://ihp-wins.unesco.org/dataset/33a5017a-e6e9-43cc-82d6-62da7fbb74d8/resource/"
           "87ed4714-cdcf-45bd-9d1e-827082861656/download/rgi2000-v7.0-g-01_alaska.zip", "RGI2000-v7.0-G-01_alaska.zip"),
          ("https://www.glims.org/RGI/rgi60_files/01_rgi60_Alaska.zip", "01_rgi60_Alaska.zip")])

GLIMS_WFS = "https://www.glims.org/geoserver/GLIMS/ows?service=WFS&version=1.0.0&request=GetFeature&typeName="
statisch("glims", "gletscher", "GLIMS: Gletscherumrisse aller Zeitstände im Gebiet (WFS)", "GLIMS",
         "frei, Namensnennung", "Polygone", "mehrere Jahrzehnte", "szene", "standard",
         """Alle eingereichten Umrisse (glac_bound mit src_date) – damit lassen sich Lituya-, Nord-Crillon-
         und Cascade-Gletscher über die Jahre vergleichen; dazu RGI 7 aus demselben Dienst. Ungeprüft.""",
         "https://www.glims.org/maps/glims",
         [(GLIMS_WFS + "GLIMS:GLIMS_Glacier_Outlines&bbox={w},{s},{o},{n}&outputFormat=application/json"
                       "&maxFeatures=20000", "glims_umrisse.geojson"),
          (GLIMS_WFS + "GLIMS:RGI70&bbox={w},{s},{o},{n}&outputFormat=application/json&maxFeatures=20000",
           "glims_rgi70.geojson")])

statisch("eisdicke", "gletscher", "Eisdicke: Millan et al. 2022, Farinotti et al. 2019", "IGE Grenoble / ETH Zürich",
         "CC BY 4.0 (Millan), CC BY-NC-SA 4.0 (Farinotti)", "50 m / 25 m", "2017–2018 / 2000", "szene", "manuell",
         """Modellierte Eisdicke je Gletscher, für die Gletscherbetten unter Lituya- und Crillon-Gletscher.
         Millan: Kacheln über die OGGM-Tabelle millan22_thickness_lookup_shp_20220902.zip
         (cluster.klima.uni-bremen.de/~oggm/velocities/millan22/); Farinotti: composite_thickness_RGI60-01.zip
         in der ETH Research Collection.""",
         "https://www.research-collection.ethz.ch/handle/20.500.11850/315707", [])

# ===========================================================================
# GEOLOGIE
# ===========================================================================
statisch("geologie_alaska", "geologie", "Geologische Karte von Alaska (USGS SIM 3340) und NPS-Oberflächengeologie",
         "USGS; NPS Geologic Resources Inventory", "gemeinfrei", "1:1 584 000; Glacier Bay 1:?",
         "2015; 2012", "fern", "standard",
         """Wilson et al. 2015: Shapefiles und Einheitentabellen. NPS GRI Glacier Bay: Oberflächengeologie
         (Becker, Streveler & Mickelson 2012) als GeoPackage und Kartenbeschreibung. Ungeprüft.""",
         "https://pubs.usgs.gov/publication/sim3340",
         [("https://pubs.usgs.gov/sim/3340/sim3340_shp.zip", "sim3340_shp.zip"),
          ("https://pubs.usgs.gov/sim/3340/sim3340_databases.zip", "sim3340_databases.zip"),
          ("https://irma.nps.gov/DataStore/DownloadFile/646880?Reference=2188778", "nps_gri_glba_oberflaeche.gpkg"),
          ("https://irma.nps.gov/DataStore/DownloadFile/646956?Reference=2188778", "nps_gri_glba_kartenbeschreibung.pdf")])

statisch("geologie_lituya", "geologie", "Geologische Arbeiten zum Lituya-Distrikt", "USGS, Alaska DGGS",
         "gemeinfrei", "1:96 000 / 1:125 000", "1953–2022", "szene", "manuell",
         """Miller 1961, OFR 61-100 „Geology of the Lituya district“ (1:96 000); Miller 1953, OFR 53-193;
         Brew et al. 1978, OFR 78-494 (Glacier Bay, Tafeln 1:125 000); Rossman 1963, Bulletin 1121-K;
         MacKevett et al. 1971, PP 632; Hudson, Plafker & Rubin, DGGS RI 2022-4 (Meeresterrassen und
         Fairweather-Störung bei Lituya Bay). Abruf über die Seiten; DGGS-Liste für das Blatt Mount Fairweather.""",
         "https://dggs.alaska.gov/pubs/quad/mount-fairweather", [])

# ===========================================================================
# LUFTBILD (manuell und Suche)
# ===========================================================================
PC = "https://planetarycomputer.microsoft.com/api/stac/v1/search"
statisch("landsat_suche", "luftbild", "Landsat 1–9: Szenenliste 1972 bis heute (Planetary Computer)",
         "USGS / Microsoft Planetary Computer", "gemeinfrei", "15–60 m", "1972 bis heute", "kern", "standard",
         """STAC-Suche über die Bucht: MSS ab 1972 (landsat-c2-l1) und TM/ETM+/OLI ab 1982 (landsat-c2-l2).
         Die Bilder selbst lädt man mit einem anonymen Token (…/api/sas/v1/token/<collection>), an jede
         Adresse angehängt. Ungeprüft.""",
         "https://planetarycomputer.microsoft.com/dataset/landsat-c2-l2",
         [(PC + "?collections=landsat-c2-l1&bbox={w},{s},{o},{n}&limit=1000", "landsat_mss_1972-2013.json"),
          (PC + "?collections=landsat-c2-l2&bbox={w},{s},{o},{n}&limit=1000"
                "&datetime=1982-01-01T00:00:00Z/2026-12-31T00:00:00Z", "landsat_c2_l2.json")])

statisch("luftbilder_historisch", "luftbild", "Historische Luftbilder 1929–1986 und Fotos von Washburn",
         "USGS EROS; Museum of Science Boston; NARA", "gemeinfrei (USGS); Washburn urheberrechtlich geschützt",
         "Einzelbilder", "1929–1986", "kern", "manuell",
         """USGS EarthExplorer (kostenloses Konto): „Aerial Photo Single Frames“ – darunter die Senkrechtbilder
         1948 und 1954, aus denen die Blätter C-6 und C-5 entstanden –, AHAP 1978–86, CORONA („Declass 1“,
         1960–72). Bradford Washburn: Crillon-Expedition 1934, Luftbilder 1937 (Museum of Science Boston,
         Negative 1888–1960, 2604–2866). NOAA Photo Library: C&GS-Fotos der Bucht 1940; NOAA
         Hazard Images Nr. 287 (Juli 1958). ShoreZone 2019: Schrägbilder der Küste.""",
         "https://earthexplorer.usgs.gov/", [])

# ===========================================================================
# GEZEITEN UND KLIMA
# ===========================================================================
COOPS = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter"
statisch("gezeiten", "klima", "Gezeiten Lituya Bay (9452928) und Elfin Cove (9452634), Boje Fairweather Ground",
         "NOAA CO-OPS / NDBC", "gemeinfrei", "Zeitreihen", "1958 und heute", "kern", "standard",
         """Vorausberechnete Hoch- und Niedrigwasser für Lituya Bay am 9./10. Juli 1958 (die Welle kam um
         22:17 Ortszeit bei Ebbe) und für Juli 2026; Bezugshöhen von Elfin Cove; Seegang der Boje 46083.
         Ungeprüft.""",
         "https://tidesandcurrents.noaa.gov/stationhome.html?id=9452928",
         [(COOPS + "?station=9452928&product=predictions&begin_date=19580705&end_date=19580715&datum=MLLW"
                   "&interval=hilo&time_zone=gmt&units=metric&format=json", "gezeiten_lituya_juli1958.json"),
          (COOPS + "?station=9452928&product=predictions&begin_date=20260701&end_date=20260731&datum=MLLW"
                   "&interval=6&time_zone=gmt&units=metric&format=json", "gezeiten_lituya_juli2026.json"),
          ("https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations/9452634/datums.json?units=metric",
           "bezugshoehen_elfin_cove.json"),
          ("https://www.ndbc.noaa.gov/data/realtime2/46083.txt", "boje_46083_aktuell.txt")])

ERA5 = "https://nsf-ncar-era5.s3.amazonaws.com/e5.oper.an.sfc/195807/e5.oper.an.sfc."


@quelle("era5_juli1958", "klima", "ERA5 Reanalyse Juli 1958 (Wind, Luftdruck, Temperatur)", "ECMWF / NSF NCAR",
        "Copernicus-Lizenz (frei, Namensnennung)", "0,25°", "Juli 1958", "fern", "gross",
        """Das Wetter am Abend der Welle (klar, ruhig): Bodendruck, Wind 10 m, Temperatur 2 m, Schnee;
        stündlich, ganze Erde je Datei (1–1,5 GB). Geprüft.""",
        "https://registry.opendata.aws/nsf-ncar-era5/")
def _p(ctx):
    out = []
    for var in ("128_151_msl", "128_165_10u", "128_166_10v", "128_167_2t", "128_141_sd"):
        prefix = "e5.oper.an.sfc/195807/e5.oper.an.sfc." + var
        for k, n in netz.s3_liste("https://nsf-ncar-era5.s3.amazonaws.com", prefix)[0]:
            out.append(Datei("https://nsf-ncar-era5.s3.amazonaws.com/" + k, _name(k), n))
    return out

