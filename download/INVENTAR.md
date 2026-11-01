# Kartenmaterial Lituya Bay – Inventar

Stand 01.11.2026 04:00, Ordner `download/`. Erzeugt von `tools/kartenmaterial/kartenmaterial.py inventar`.

**4 Dateien, 203 kB.** Jede Datei mit Größe in `inventar.csv`, Adressen und Fehler je Quelle in `_status/<quelle>.json`.

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
| `usgs_3dep_13` | USGS 3DEP 1/3 Bogensekunde (~10 m) | standard | szene | 0/9 | 0 kB | 519.8 MB | geplant |
| `usgs_3dep_1` | USGS 3DEP 1 Bogensekunde (~30 m) | standard | fern | 0/33 | 0 kB | 404.4 MB | geplant |
| `usgs_3dep_2` | USGS 3DEP 2 Bogensekunden Alaska (~60 m) | standard | fern | 0/33 | 0 kB | 107.8 MB | geplant |
| `usgs_ifsar_dtm` | USGS IfSAR Glacier Bay 2012, DTM 5 m | standard | szene | 0/4 | 0 kB | 1.19 GB | geplant |
| `usgs_ifsar_dsm` | USGS IfSAR Glacier Bay 2012, DSM 5 m (Oberfläche) | gross | szene | 0/4 | 0 kB | 1.14 GB | geplant |
| `usgs_lidar_glacierbay` | USGS Lidar Glacier Bay 2019/20 (QL1/QL2), DTM 1 m | standard | kern | 0/312 | 0 kB | 3.50 GB | geplant |
| `copernicus_glo30` | Copernicus DEM GLO-30 | standard | fern | 0/44 | 0 kB | 218.6 MB | geplant |
| `arcticdem_32m` | ArcticDEM Mosaik v4.1, 32 m | standard | fern | 0/45 | 0 kB | 439.9 MB | geplant |
| `arcticdem_10m` | ArcticDEM Mosaik v4.1, 10 m | standard | szene | 0/10 | 0 kB | 542.6 MB | geplant |
| `arcticdem_2m` | ArcticDEM Mosaik v4.1, 2 m | gross | kern | 0/10 | 0 kB | 3.51 GB | geplant |
| `arcticdem_streifen` | ArcticDEM Streifen s2s041 (datierte 2-m-DSM) | standard | kern | 1/95 | 6 kB | 532.8 MB | teilweise |

### Wassertiefe

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `noaa_bluetopo` | NOAA BlueTopo (Wassertiefen) | standard | szene | 0/127 | 0 kB | 276.3 MB | geplant |
| `usace_topobathy_2019` | USACE NCMP Topobathy-Lidar 2019, Block Lituya (1 m) | standard | szene | 0/169 | 0 kB | 539.1 MB | geplant |
| `gebco_2025` | GEBCO 2025, Ausschnitt Fernfeld | standard | fern | 0/2 | 0 kB | 2 ohne Angabe | geplant |
| `ncei_dem_suedost_alaska` | NCEI Küsten-DEM Southeast Alaska 8/3" (MHHW, 2010) | gross | fern | 0/1 | 0 kB | 1 ohne Angabe | geplant |
| `ncei_dem_elfin_cove` | NCEI Tsunami-DEM Elfin Cove 1/3" (MHW, 2011) | standard | fern | 0/1 | 0 kB | 1 ohne Angabe | geplant |
| `etopo_2022` | ETOPO 2022 15" (Kachel N60W150) | standard | fern | 0/1 | 0 kB | 1 ohne Angabe | geplant |
| `nos_vermessungen` | NOS-Vermessungen Lituya Bay 1926, 1940, 1959, 2025 | standard | kern | 0/14 | 0 kB | 14 ohne Angabe | geplant |

### Karten

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `usgs_topo_historisch` | USGS historische topographische Karten (HTMC) | standard | fern | 1/129 | 22 kB | 668.2 MB | teilweise |
| `usgs_ustopo` | USGS US Topo (aktuell, GeoPDF) | standard | szene | 0/32 | 0 kB | 550.5 MB | geplant |
| `noaa_enc` | NOAA Seekarten ENC (S-57), Lituya Bay und Anfahrt | standard | kern | 0/4 | 0 kB | 4 ohne Angabe | geplant |
| `noaa_karte_16760` | NOAA Seekarte 16760 Cross Sound to Yakutat Bay (PDF) | standard | fern | 0/2 | 0 kB | 2 ohne Angabe | geplant |
| `noaa_karten_historisch` | Historische Seekarten C&GS 8505 „Lituya Bay“ (1910–1942) | manuell | kern | 0/0 | 0 kB | – | manuell |
| `nps_karten` | NPS-Karten Glacier Bay (Faltblatt, Seekarte 1:80 000) | manuell | fern | 0/0 | 0 kB | – | manuell |

### Luft- und Satellitenbild

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `usgs_ifsar_ori` | USGS IfSAR Glacier Bay 2012, Radarbild (ORI) 2,5 m | gross | szene | 0/4 | 0 kB | 1.60 GB | geplant |
| `sentinel2` | Sentinel-2 L2A, klarste Sommerszenen (Echtfarbe 10 m) | standard | kern | 1/22 | 133 kB | 1.10 GB | teilweise |
| `landsat_suche` | Landsat 1–9: Szenenliste 1972 bis heute (Planetary Computer) | standard | kern | 0/2 | 0 kB | 2 ohne Angabe | geplant |
| `luftbilder_historisch` | Historische Luftbilder 1929–1986 und Fotos von Washburn | manuell | kern | 0/0 | 0 kB | – | manuell |

### Bewuchs

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `esa_worldcover` | ESA WorldCover 2021 (Landbedeckung 10 m) | standard | fern | 0/2 | 0 kB | 64.6 MB | geplant |

### Gletscher

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `itslive_geschwindigkeit` | ITS_LIVE Gletschergeschwindigkeit (Mosaik v2, Alaska) | gross | fern | 0/5 | 0 kB | 793.7 MB | geplant |
| `rgi7` | Randolph Glacier Inventory 7.0, Region 01 Alaska | standard | fern | 0/2 | 0 kB | 2 ohne Angabe | geplant |
| `glims` | GLIMS: Gletscherumrisse aller Zeitstände im Gebiet (WFS) | standard | szene | 0/2 | 0 kB | 2 ohne Angabe | geplant |
| `eisdicke` | Eisdicke: Millan et al. 2022, Farinotti et al. 2019 | manuell | szene | 0/0 | 0 kB | – | manuell |

### Geologie

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `stoerungen` | Quartäre Störungen (Fairweather-Störung) | standard | fern | 0/2 | 0 kB | 2 ohne Angabe | geplant |
| `geologie_alaska` | Geologische Karte von Alaska (USGS SIM 3340) und NPS-Oberflächengeologie | standard | fern | 0/4 | 0 kB | 4 ohne Angabe | geplant |
| `geologie_lituya` | Geologische Arbeiten zum Lituya-Distrikt | manuell | szene | 0/0 | 0 kB | – | manuell |

### Vektordaten

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `natural_earth` | Natural Earth 10 m (Küste, Land, Gletscher, Grenzen) | standard | fern | 0/6 | 0 kB | 26.0 MB | geplant |
| `osm` | OpenStreetMap: alle Objekte im Gebiet (Overpass) | standard | szene | 0/1 | 0 kB | 1 ohne Angabe | geplant |
| `osm_gross` | OpenStreetMap: Alaska-Auszug und Küstenpolygone | gross | fern | 0/3 | 0 kB | 3 ohne Angabe | geplant |

### Namen und Grenzen

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `gnis_namen` | GNIS: amtliche Ortsnamen Alaska | standard | szene | 1/3 | 42 kB | 1.8 MB | teilweise |
| `nps_grenzen` | Glacier Bay National Park & Preserve: Grenze und Wildnisgebiet | standard | szene | 0/2 | 0 kB | 2 ohne Angabe | geplant |

### Ereignis 1958

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `ncei_tsunami` | NCEI Tsunami-Datenbank: Ereignisse und Auflaufhöhen | standard | szene | 0/4 | 0 kB | 4 ohne Angabe | geplant |
| `usgs_erdbeben` | USGS ComCat: Erdbeben 10.07.1958 (Mw 7,8) und Erdbeben der Region | standard | fern | 0/2 | 0 kB | 2 ohne Angabe | geplant |

### Berichte

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `dokumente` | Berichte und Aufsätze zur Welle von 1958 | standard | kern | 0/9 | 0 kB | 9 ohne Angabe | geplant |

### Fotos

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `fotos_miller` | Fotos von D. J. Miller 1958 (USGS Photographic Library) | standard | kern | 0/7 | 0 kB | 7 ohne Angabe | geplant |

### Gezeiten und Klima

| Quelle | Titel | Stufe | Gebiet | Dateien | geladen | geplant | Zustand |
|---|---|---|---|---|---|---|---|
| `gezeiten` | Gezeiten Lituya Bay (9452928) und Elfin Cove (9452634), Boje Fairweather Ground | standard | kern | 0/4 | 0 kB | 4 ohne Angabe | geplant |
| `era5_juli1958` | ERA5 Reanalyse Juli 1958 (Wind, Luftdruck, Temperatur) | gross | fern | 0/5 | 0 kB | 5.32 GB | geplant |
