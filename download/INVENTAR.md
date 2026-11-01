# Kartenmaterial Lituya Bay – Inventar

Stand 01.11.2026 08:03, Ordner `download/`. Erzeugt von `tools/kartenmaterial/kartenmaterial.py inventar`.

**1227 Dateien, 13.45 GB.** Jede Datei mit Größe in `inventar.csv`, Adressen und Fehler je Quelle in `_status/<quelle>.json`.

## Gebiete

| Gebiet | UTM 8N (m) | Grad (W, S, O, N) | Fläche | Zweck |
|---|---|---|---|---|
| kern | O 340 000–364 000, N 6 494 000–6 512 000 | -137.7629, 58.5570, -137.3381, 58.7267 | 432 km² | Bucht mit Gilbert Inlet, Crillon Inlet, Cenotaph Island, Eingang; Hänge bis 600 m, Zunge des Lituya-Gletschers |
| szene | O 322 000–388 000, N 6 480 000–6 540 000 | -138.0956, 58.4244, -136.9189, 58.9849 | 3 960 km² | Fairweather-Kette (Crillon, La Perouse, Fairweather), Küstenebene, Cape Fairweather |
| fern | O 220 000–480 000, N 6 385 000–6 625 000 | -139.9734, 57.5207, -135.3347, 59.7624 | 62 400 km² | Horizont: Cross Sound und Icy Strait bis Dry Bay und Alsek |

## Quellen

### Höhe

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `usgs_3dep_13` | USGS 3DEP 1/3 Bogensekunde (~10 m) | standard | szene | 9/9 | 519.8 MB | 519.8 MB | vollständig |
| `usgs_3dep_1` | USGS 3DEP 1 Bogensekunde (~30 m) | standard | fern | 33/33 | 404.4 MB | 404.4 MB | vollständig |
| `usgs_3dep_2` | USGS 3DEP 2 Bogensekunden Alaska (~60 m) | standard | fern | 33/33 | 107.8 MB | 107.8 MB | vollständig |
| `usgs_ifsar_dtm` | USGS IfSAR Glacier Bay 2012, DTM 5 m | standard | szene | 4/4 | 1.19 GB | 1.19 GB | vollständig |
| `usgs_ifsar_dsm` | USGS IfSAR Glacier Bay 2012, DSM 5 m (Oberfläche) | gross | szene | 0/0 | 0 kB | – | nicht geplant |
| `usgs_lidar_glacierbay` | USGS Lidar Glacier Bay 2019/20 (QL1/QL2), DTM 1 m | standard | kern | 312/312 | 3.50 GB | 3.50 GB | vollständig |
| `copernicus_glo30` | Copernicus DEM GLO-30 | standard | fern | 44/44 | 218.6 MB | 218.6 MB | vollständig |
| `arcticdem_32m` | ArcticDEM Mosaik v4.1, 32 m | standard | fern | 45/45 | 439.9 MB | 439.9 MB | vollständig |
| `arcticdem_10m` | ArcticDEM Mosaik v4.1, 10 m | standard | szene | 10/10 | 542.6 MB | 542.6 MB | vollständig |
| `arcticdem_2m` | ArcticDEM Mosaik v4.1, 2 m | gross | kern | 0/0 | 0 kB | – | nicht geplant |
| `arcticdem_streifen` | ArcticDEM Streifen s2s041 (datierte 2-m-DSM) | standard | kern | 95/95 | 532.8 MB | 532.8 MB | vollständig |

### Wassertiefe

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `noaa_bluetopo` | NOAA BlueTopo (Wassertiefen) | standard | szene | 127/127 | 276.3 MB | 276.3 MB | vollständig |
| `usace_topobathy_2019` | USACE NCMP Topobathy-Lidar 2019, Block Lituya (1 m) | standard | szene | 169/169 | 539.1 MB | 539.1 MB | vollständig |
| `gebco_2025` | GEBCO 2025, Ausschnitt Fernfeld | standard | fern | 2/2 | 1.2 MB | 2 ohne Angabe | vollständig |
| `ncei_dem_suedost_alaska` | NCEI Küsten-DEM Southeast Alaska 8/3" (MHHW, 2010) | gross | fern | 0/0 | 0 kB | – | nicht geplant |
| `ncei_dem_elfin_cove` | NCEI Tsunami-DEM Elfin Cove 1/3" (MHW, 2011) | standard | fern | 1/1 | 691.8 MB | 1 ohne Angabe | vollständig |
| `etopo_2022` | ETOPO 2022 15" (Kachel N60W150) | standard | fern | 1/1 | 18.2 MB | 1 ohne Angabe | vollständig |
| `nos_vermessungen` | NOS-Vermessungen Lituya Bay 1926, 1940, 1959, 2025 | standard | kern | 57/57 | 845.5 MB | 57 ohne Angabe | vollständig |

### Karten

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `usgs_topo_historisch` | USGS historische topographische Karten (HTMC) | standard | fern | 129/129 | 668.2 MB | 668.2 MB | vollständig |
| `usgs_ustopo` | USGS US Topo (aktuell, GeoPDF) | standard | szene | 32/32 | 550.5 MB | 550.5 MB | vollständig |
| `noaa_enc` | NOAA Seekarten ENC (S-57) um Lituya Bay | standard | szene | 23/23 | 5.8 MB | 23 ohne Angabe | vollständig |
| `noaa_karte_16760` | NOAA Seekarten 16760 und 16762 als Bild (NOAA Custom Chart) | manuell | fern | 0/2 | 0 kB | 2 ohne Angabe | manuell |
| `noaa_karten_historisch` | Historische Seekarten C&GS 8505 „Lituya Bay“ (1910–1942) | manuell | kern | 0/0 | 0 kB | – | manuell |
| `nps_karten` | NPS-Karten Glacier Bay (Faltblatt, Seekarte 1:80 000) | manuell | fern | 0/0 | 0 kB | – | manuell |

### Luft- und Satellitenbild

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `usgs_ifsar_ori` | USGS IfSAR Glacier Bay 2012, Radarbild (ORI) 2,5 m | gross | szene | 0/0 | 0 kB | – | nicht geplant |
| `sentinel2` | Sentinel-2 L2A, klarste Sommerszenen (Echtfarbe 10 m) | standard | kern | 22/22 | 1.10 GB | 1.10 GB | vollständig |
| `landsat_suche` | Landsat 1–9: Szenenliste 1972 bis heute (Planetary Computer) | standard | kern | 2/2 | 31.6 MB | 2 ohne Angabe | vollständig |
| `luftbilder_historisch` | Historische Luftbilder 1929–1986 und Fotos von Washburn | manuell | kern | 0/0 | 0 kB | – | manuell |

### Bewuchs

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `esa_worldcover` | ESA WorldCover 2021 (Landbedeckung 10 m) | standard | fern | 2/2 | 64.6 MB | 64.6 MB | vollständig |

### Gletscher

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `itslive_geschwindigkeit` | ITS_LIVE Gletschergeschwindigkeit (Mosaik v2, Alaska) | gross | fern | 0/0 | 0 kB | – | nicht geplant |
| `rgi7` | Randolph Glacier Inventory 7.0, Region 01 Alaska | standard | fern | 2/2 | 79.3 MB | 2 ohne Angabe | vollständig |
| `glims` | GLIMS: Gletscherumrisse aller Zeitstände im Gebiet (WFS) | standard | szene | 2/2 | 7.8 MB | 2 ohne Angabe | vollständig |
| `eisdicke` | Eisdicke: Millan et al. 2022, Farinotti et al. 2019 | manuell | szene | 0/0 | 0 kB | – | manuell |

### Geologie

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `stoerungen` | Quartäre Störungen (Fairweather-Störung) | standard | fern | 2/2 | 32.4 MB | 2 ohne Angabe | vollständig |
| `geologie_alaska` | Geologische Karte von Alaska (USGS SIM 3340) und NPS-Oberflächengeologie | standard | fern | 4/4 | 809.6 MB | 4 ohne Angabe | vollständig |
| `geologie_lituya` | Geologische Arbeiten zum Lituya-Distrikt | manuell | szene | 0/0 | 0 kB | – | manuell |

### Vektordaten

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `natural_earth` | Natural Earth 10 m (Küste, Land, Gletscher, Grenzen) | standard | fern | 6/6 | 26.0 MB | 26.0 MB | vollständig |
| `osm` | OpenStreetMap: alle Objekte im Gebiet (Overpass) | standard | szene | 1/1 | 38.5 MB | 1 ohne Angabe | vollständig |
| `osm_gross` | OpenStreetMap: Alaska-Auszug und Küstenpolygone | gross | fern | 0/0 | 0 kB | – | nicht geplant |

### Namen und Grenzen

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `gnis_namen` | GNIS: amtliche Ortsnamen Alaska | standard | szene | 3/3 | 1.8 MB | 1.8 MB | vollständig |
| `nps_grenzen` | Glacier Bay National Park & Preserve: Grenze und Wildnisgebiet | standard | szene | 2/2 | 4.4 MB | 2 ohne Angabe | vollständig |

### Ereignis 1958

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `ncei_tsunami` | NCEI Tsunami-Datenbank: Ereignisse und Auflaufhöhen | standard | szene | 4/4 | 66 kB | 4 ohne Angabe | vollständig |
| `usgs_erdbeben` | USGS ComCat: Erdbeben 10.07.1958 (Mw 7,8) und Erdbeben der Region | standard | fern | 2/2 | 127 kB | 2 ohne Angabe | vollständig |

### Berichte

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `dokumente` | Berichte und Aufsätze zur Welle von 1958 | standard | kern | 7/8 | 49.8 MB | 8 ohne Angabe | teilweise |
| `usgs_publikationen` | USGS-Berichte mit Tafeln und Karten (Lituya-Distrikt) | standard | kern | 36/36 | 147.3 MB | 36 ohne Angabe | vollständig |

### Fotos

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `fotos_miller` | Fotos von D. J. Miller 1958 (USGS Photographic Library) | manuell | kern | 0/7 | 0 kB | 7 ohne Angabe | manuell |

### Gezeiten und Klima

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `gezeiten` | Gezeiten Lituya Bay (9452928) und Elfin Cove (9452634), Boje Fairweather Ground | standard | kern | 4/4 | 612 kB | 4 ohne Angabe | vollständig |
| `era5_juli1958` | ERA5 Reanalyse Juli 1958 (Wind, Luftdruck, Temperatur) | gross | fern | 0/0 | 0 kB | – | nicht geplant |

## Fehlgeschlagen

| Quelle | Adresse | Fehler |
|---|---|---|
| `noaa_karte_16760` | https://www.charts.noaa.gov/PDFs/16762.pdf | HTTPError: HTTP Error 404: Not Found |
| `noaa_karte_16760` | https://www.charts.noaa.gov/PDFs/16760.pdf | HTTPError: HTTP Error 404: Not Found |
| `dokumente` | https://corpora.tika.apache.org/base/docs/govdocs1/423/423993.pdf | URLError: <urlopen error [Errno 11001] getaddrinfo failed> |
| `fotos_miller` | https://www.sciencebase.gov/catalog/item/51dd9142e4b0f72b4471cccf?format=json | HTTPError: HTTP Error 403: Forbidden |
| `fotos_miller` | https://www.sciencebase.gov/catalog/item/51dd90fbe4b0f72b4471cc9d?format=json | HTTPError: HTTP Error 403: Forbidden |
| `fotos_miller` | https://www.sciencebase.gov/catalog/item/51dd90e2e4b0f72b4471cc8b?format=json | HTTPError: HTTP Error 403: Forbidden |
| `fotos_miller` | https://www.sciencebase.gov/catalog/item/51dd90b9e4b0f72b4471cc6f?format=json | HTTPError: HTTP Error 403: Forbidden |
| `fotos_miller` | https://www.sciencebase.gov/catalog/item/51dd90cae4b0f72b4471cc7b?format=json | HTTPError: HTTP Error 403: Forbidden |
| `fotos_miller` | https://www.sciencebase.gov/catalog/item/51dd90b2e4b0f72b4471cc69?format=json | HTTPError: HTTP Error 403: Forbidden |
| `fotos_miller` | https://www.sciencebase.gov/catalog/item/51dd9147e4b0f72b4471ccd5?format=json | HTTPError: HTTP Error 403: Forbidden |
