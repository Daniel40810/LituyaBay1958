# LituyaBay1958 – Ort des Kartenmaterials

Werkbuch 3.3, Punkt 5: Das Kartenmaterial liest nur die Aufbereitung (Phase 2); die App, die Netze und Oracle lesen aus `daten/`. Geschrieben von `ant auslagerung` am 2026-11-03. **1245 Dateien, 13,81 GB**, noch in `D:\Daten`. Titel, Adressen und Lizenzen der Quellen stehen in [Kartenmaterial.md](../Kartenmaterial.md), der Ladestand in `download/INVENTAR.md`. Um die Aufbereitung wieder laufen zu lassen: die Platte anschließen, `daten/rohdaten.properties` zeigt dorthin, dann `ant aufbereitung`.

## Übersicht

| Gruppe | Quelle | Dateien | Größe | Ort |
|---|---|---|---|---|
| bathymetrie | `etopo_2022` | 1 | 18,2 MB | `D:\Daten\bathymetrie\etopo_2022` |
| bathymetrie | `gebco_2025` | 2 | 1,2 MB | `D:\Daten\bathymetrie\gebco_2025` |
| bathymetrie | `ncei_dem_elfin_cove` | 1 | 691,8 MB | `D:\Daten\bathymetrie\ncei_dem_elfin_cove` |
| bathymetrie | `noaa_bluetopo` | 127 | 276,3 MB | `D:\Daten\bathymetrie\noaa_bluetopo` |
| bathymetrie | `nos_vermessungen` | 57 | 845,5 MB | `D:\Daten\bathymetrie\nos_vermessungen` |
| bathymetrie | `usace_topobathy_2019` | 169 | 539,1 MB | `D:\Daten\bathymetrie\usace_topobathy_2019` |
| dokumente | `dokumente` | 7 | 49,8 MB | `D:\Daten\dokumente\dokumente` |
| dokumente | `usgs_publikationen` | 36 | 147,3 MB | `D:\Daten\dokumente\usgs_publikationen` |
| ereignis | `ncei_tsunami` | 4 | 66 kB | `D:\Daten\ereignis\ncei_tsunami` |
| ereignis | `usgs_erdbeben` | 2 | 127 kB | `D:\Daten\ereignis\usgs_erdbeben` |
| geologie | `geologie_alaska` | 4 | 809,6 MB | `D:\Daten\geologie\geologie_alaska` |
| geologie | `stoerungen` | 2 | 32,4 MB | `D:\Daten\geologie\stoerungen` |
| gletscher | `glims` | 2 | 7,8 MB | `D:\Daten\gletscher\glims` |
| gletscher | `itslive_geschwindigkeit` | 5 | 8,2 MB | `D:\Daten\gletscher\itslive_geschwindigkeit` |
| gletscher | `rgi7` | 2 | 79,3 MB | `D:\Daten\gletscher\rgi7` |
| hoehe | `arcticdem_10m` | 10 | 542,6 MB | `D:\Daten\hoehe\arcticdem_10m` |
| hoehe | `arcticdem_32m` | 45 | 439,9 MB | `D:\Daten\hoehe\arcticdem_32m` |
| hoehe | `arcticdem_streifen` | 95 | 532,8 MB | `D:\Daten\hoehe\arcticdem_streifen` |
| hoehe | `copernicus_glo30` | 44 | 218,6 MB | `D:\Daten\hoehe\copernicus_glo30` |
| hoehe | `usgs_3dep_1` | 33 | 404,4 MB | `D:\Daten\hoehe\usgs_3dep_1` |
| hoehe | `usgs_3dep_13` | 9 | 519,8 MB | `D:\Daten\hoehe\usgs_3dep_13` |
| hoehe | `usgs_3dep_2` | 33 | 107,8 MB | `D:\Daten\hoehe\usgs_3dep_2` |
| hoehe | `usgs_ifsar_dtm` | 4 | 1,19 GB | `D:\Daten\hoehe\usgs_ifsar_dtm` |
| hoehe | `usgs_lidar_glacierbay` | 312 | 3,50 GB | `D:\Daten\hoehe\usgs_lidar_glacierbay` |
| karten | `noaa_enc` | 23 | 5,8 MB | `D:\Daten\karten\noaa_enc` |
| karten | `usgs_topo_historisch` | 129 | 668,2 MB | `D:\Daten\karten\usgs_topo_historisch` |
| karten | `usgs_ustopo` | 32 | 550,5 MB | `D:\Daten\karten\usgs_ustopo` |
| klima | `gezeiten` | 4 | 612 kB | `D:\Daten\klima\gezeiten` |
| luftbild | `landsat_suche` | 2 | 31,6 MB | `D:\Daten\luftbild\landsat_suche` |
| luftbild | `sentinel2` | 22 | 1,10 GB | `D:\Daten\luftbild\sentinel2` |
| namen | `gnis_namen` | 3 | 1,8 MB | `D:\Daten\namen\gnis_namen` |
| namen | `nps_grenzen` | 2 | 4,4 MB | `D:\Daten\namen\nps_grenzen` |
| texturen | `tex_eis` | 1 | 15,6 MB | `D:\Daten\texturen\tex_eis` |
| texturen | `tex_firn` | 1 | 25,2 MB | `D:\Daten\texturen\tex_firn` |
| texturen | `tex_holz_entrindet` | 1 | 29,0 MB | `D:\Daten\texturen\tex_holz_entrindet` |
| texturen | `tex_kutter_holz` | 1 | 28,0 MB | `D:\Daten\texturen\tex_kutter_holz` |
| texturen | `tex_kutter_tau` | 1 | 12,2 MB | `D:\Daten\texturen\tex_kutter_tau` |
| texturen | `tex_moos` | 1 | 39,6 MB | `D:\Daten\texturen\tex_moos` |
| texturen | `tex_moraene` | 1 | 32,5 MB | `D:\Daten\texturen\tex_moraene` |
| texturen | `tex_rinde` | 1 | 29,8 MB | `D:\Daten\texturen\tex_rinde` |
| texturen | `tex_sand` | 1 | 33,0 MB | `D:\Daten\texturen\tex_sand` |
| texturen | `tex_schiefer_frisch` | 1 | 27,1 MB | `D:\Daten\texturen\tex_schiefer_frisch` |
| texturen | `tex_schiefer_verwittert` | 1 | 14,9 MB | `D:\Daten\texturen\tex_schiefer_verwittert` |
| texturen | `tex_strandkies` | 1 | 32,0 MB | `D:\Daten\texturen\tex_strandkies` |
| texturen | `tex_waldboden` | 1 | 37,9 MB | `D:\Daten\texturen\tex_waldboden` |
| vegetation | `esa_worldcover` | 2 | 64,6 MB | `D:\Daten\vegetation\esa_worldcover` |
| vektor | `natural_earth` | 6 | 26,0 MB | `D:\Daten\vektor\natural_earth` |
| vektor | `osm` | 1 | 38,5 MB | `D:\Daten\vektor\osm` |

## Rohdateien

### bathymetrie

**`etopo_2022`**

- `bathymetrie/etopo_2022/ETOPO_2022_v1_15s_N60W150_surface.nc` · 18,2 MB

**`gebco_2025`**

- `bathymetrie/gebco_2025/GEBCO_2025.stac-item.json` · 3 kB
- `bathymetrie/gebco_2025/GEBCO_2025_fern.tif` · 1,2 MB

**`ncei_dem_elfin_cove`**

- `bathymetrie/ncei_dem_elfin_cove/elfin_cove_13_mhw_2011.nc` · 691,8 MB

**`noaa_bluetopo`**

- `bathymetrie/noaa_bluetopo/BC24C27L/BlueTopo_BC24C27L_20260731.tiff` · 71,1 MB
- `bathymetrie/noaa_bluetopo/BC24C27L/BlueTopo_BC24C27L_20260731.tiff.aux.xml` · 9 kB
- `bathymetrie/noaa_bluetopo/BH2RR5QC/BlueTopo_BH2RR5QC_20250708.tiff` · 1,7 MB
- `bathymetrie/noaa_bluetopo/BH2RR5QC/BlueTopo_BH2RR5QC_20250708.tiff.aux.xml` · 5 kB
- `bathymetrie/noaa_bluetopo/BH2RR5QD/BlueTopo_BH2RR5QD_20250708.tiff` · 1,5 MB
- `bathymetrie/noaa_bluetopo/BH2RR5QD/BlueTopo_BH2RR5QD_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RR5QF/BlueTopo_BH2RR5QF_20250708.tiff` · 2,0 MB
- `bathymetrie/noaa_bluetopo/BH2RR5QF/BlueTopo_BH2RR5QF_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RR5QG/BlueTopo_BH2RR5QG_20250708.tiff` · 2,2 MB
- `bathymetrie/noaa_bluetopo/BH2RR5QG/BlueTopo_BH2RR5QG_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RR5QH/BlueTopo_BH2RR5QH_20260205.tiff` · 4,7 MB
- `bathymetrie/noaa_bluetopo/BH2RR5QH/BlueTopo_BH2RR5QH_20260205.tiff.aux.xml` · 5 kB
- `bathymetrie/noaa_bluetopo/BH2RR5QJ/BlueTopo_BH2RR5QJ_20250627.tiff` · 4,5 MB
- `bathymetrie/noaa_bluetopo/BH2RR5QJ/BlueTopo_BH2RR5QJ_20250627.tiff.aux.xml` · 6 kB
- `bathymetrie/noaa_bluetopo/BH2RR5QK/BlueTopo_BH2RR5QK_20250627.tiff` · 1,1 MB
- `bathymetrie/noaa_bluetopo/BH2RR5QK/BlueTopo_BH2RR5QK_20250627.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RS5QC/BlueTopo_BH2RS5QC_20250708.tiff` · 1,7 MB
- `bathymetrie/noaa_bluetopo/BH2RS5QC/BlueTopo_BH2RS5QC_20250708.tiff.aux.xml` · 5 kB
- `bathymetrie/noaa_bluetopo/BH2RS5QD/BlueTopo_BH2RS5QD_20250708.tiff` · 1,7 MB
- `bathymetrie/noaa_bluetopo/BH2RS5QD/BlueTopo_BH2RS5QD_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RS5QF/BlueTopo_BH2RS5QF_20250708.tiff` · 2,3 MB
- `bathymetrie/noaa_bluetopo/BH2RS5QF/BlueTopo_BH2RS5QF_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RS5QG/BlueTopo_BH2RS5QG_20250708.tiff` · 3,2 MB
- `bathymetrie/noaa_bluetopo/BH2RS5QG/BlueTopo_BH2RS5QG_20250708.tiff.aux.xml` · 5 kB
- `bathymetrie/noaa_bluetopo/BH2RS5QH/BlueTopo_BH2RS5QH_20250708.tiff` · 3,3 MB
- `bathymetrie/noaa_bluetopo/BH2RS5QH/BlueTopo_BH2RS5QH_20250708.tiff.aux.xml` · 5 kB
- `bathymetrie/noaa_bluetopo/BH2RT5QB/BlueTopo_BH2RT5QB_20250708.tiff` · 1,5 MB
- `bathymetrie/noaa_bluetopo/BH2RT5QB/BlueTopo_BH2RT5QB_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RT5QC/BlueTopo_BH2RT5QC_20250708.tiff` · 1,7 MB
- `bathymetrie/noaa_bluetopo/BH2RT5QC/BlueTopo_BH2RT5QC_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RT5QD/BlueTopo_BH2RT5QD_20250708.tiff` · 2,0 MB
- `bathymetrie/noaa_bluetopo/BH2RT5QD/BlueTopo_BH2RT5QD_20250708.tiff.aux.xml` · 5 kB
- `bathymetrie/noaa_bluetopo/BH2RT5QF/BlueTopo_BH2RT5QF_20250708.tiff` · 3,2 MB
- `bathymetrie/noaa_bluetopo/BH2RT5QF/BlueTopo_BH2RT5QF_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RT5QG/BlueTopo_BH2RT5QG_20260205.tiff` · 4,1 MB
- `bathymetrie/noaa_bluetopo/BH2RT5QG/BlueTopo_BH2RT5QG_20260205.tiff.aux.xml` · 6 kB
- `bathymetrie/noaa_bluetopo/BH2RT5QH/BlueTopo_BH2RT5QH_20250708.tiff` · 826 kB
- `bathymetrie/noaa_bluetopo/BH2RT5QH/BlueTopo_BH2RT5QH_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RV5QB/BlueTopo_BH2RV5QB_20250708.tiff` · 1,4 MB
- `bathymetrie/noaa_bluetopo/BH2RV5QB/BlueTopo_BH2RV5QB_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RV5QC/BlueTopo_BH2RV5QC_20250708.tiff` · 1,9 MB
- `bathymetrie/noaa_bluetopo/BH2RV5QC/BlueTopo_BH2RV5QC_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RV5QD/BlueTopo_BH2RV5QD_20250708.tiff` · 2,3 MB
- `bathymetrie/noaa_bluetopo/BH2RV5QD/BlueTopo_BH2RV5QD_20250708.tiff.aux.xml` · 5 kB
- `bathymetrie/noaa_bluetopo/BH2RV5QF/BlueTopo_BH2RV5QF_20260731.tiff` · 4,4 MB
- `bathymetrie/noaa_bluetopo/BH2RV5QF/BlueTopo_BH2RV5QF_20260731.tiff.aux.xml` · 7 kB
- `bathymetrie/noaa_bluetopo/BH2RV5QG/BlueTopo_BH2RV5QG_20260731.tiff` · 3,2 MB
- `bathymetrie/noaa_bluetopo/BH2RV5QG/BlueTopo_BH2RV5QG_20260731.tiff.aux.xml` · 8 kB
- `bathymetrie/noaa_bluetopo/BH2RW5QB/BlueTopo_BH2RW5QB_20250708.tiff` · 1,4 MB
- `bathymetrie/noaa_bluetopo/BH2RW5QB/BlueTopo_BH2RW5QB_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RW5QC/BlueTopo_BH2RW5QC_20250708.tiff` · 1,7 MB
- `bathymetrie/noaa_bluetopo/BH2RW5QC/BlueTopo_BH2RW5QC_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RW5QD/BlueTopo_BH2RW5QD_20260731.tiff` · 3,0 MB
- `bathymetrie/noaa_bluetopo/BH2RW5QD/BlueTopo_BH2RW5QD_20260731.tiff.aux.xml` · 6 kB
- `bathymetrie/noaa_bluetopo/BH2RW5QF/BlueTopo_BH2RW5QF_20260731.tiff` · 6,5 MB
- `bathymetrie/noaa_bluetopo/BH2RW5QF/BlueTopo_BH2RW5QF_20260731.tiff.aux.xml` · 11 kB
- `bathymetrie/noaa_bluetopo/BH2RW5QG/BlueTopo_BH2RW5QG_20260731.tiff` · 855 kB
- `bathymetrie/noaa_bluetopo/BH2RW5QG/BlueTopo_BH2RW5QG_20260731.tiff.aux.xml` · 6 kB
- `bathymetrie/noaa_bluetopo/BH2RX5QB/BlueTopo_BH2RX5QB_20250708.tiff` · 1,4 MB
- `bathymetrie/noaa_bluetopo/BH2RX5QB/BlueTopo_BH2RX5QB_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RX5QC/BlueTopo_BH2RX5QC_20250708.tiff` · 2,2 MB
- `bathymetrie/noaa_bluetopo/BH2RX5QC/BlueTopo_BH2RX5QC_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RX5QD/BlueTopo_BH2RX5QD_20250708.tiff` · 5,5 MB
- `bathymetrie/noaa_bluetopo/BH2RX5QD/BlueTopo_BH2RX5QD_20250708.tiff.aux.xml` · 6 kB
- `bathymetrie/noaa_bluetopo/BH2RX5QF/BlueTopo_BH2RX5QF_20260731.tiff` · 6,0 MB
- `bathymetrie/noaa_bluetopo/BH2RX5QF/BlueTopo_BH2RX5QF_20260731.tiff.aux.xml` · 10 kB
- `bathymetrie/noaa_bluetopo/BH2RX5QG/BlueTopo_BH2RX5QG_20260731.tiff` · 496 kB
- `bathymetrie/noaa_bluetopo/BH2RX5QG/BlueTopo_BH2RX5QG_20260731.tiff.aux.xml` · 5 kB
- `bathymetrie/noaa_bluetopo/BH2RZ5QB/BlueTopo_BH2RZ5QB_20250708.tiff` · 1,5 MB
- `bathymetrie/noaa_bluetopo/BH2RZ5QB/BlueTopo_BH2RZ5QB_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RZ5QC/BlueTopo_BH2RZ5QC_20250708.tiff` · 3,8 MB
- `bathymetrie/noaa_bluetopo/BH2RZ5QC/BlueTopo_BH2RZ5QC_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2RZ5QD/BlueTopo_BH2RZ5QD_20260731.tiff` · 9,0 MB
- `bathymetrie/noaa_bluetopo/BH2RZ5QD/BlueTopo_BH2RZ5QD_20260731.tiff.aux.xml` · 5 kB
- `bathymetrie/noaa_bluetopo/BH2RZ5QF/BlueTopo_BH2RZ5QF_20260731.tiff` · 1,3 MB
- `bathymetrie/noaa_bluetopo/BH2RZ5QF/BlueTopo_BH2RZ5QF_20260731.tiff.aux.xml` · 6 kB
- `bathymetrie/noaa_bluetopo/BH2RZ5QG/BlueTopo_BH2RZ5QG_20260731.tiff` · 2,6 MB
- `bathymetrie/noaa_bluetopo/BH2RZ5QG/BlueTopo_BH2RZ5QG_20260731.tiff.aux.xml` · 8 kB
- `bathymetrie/noaa_bluetopo/BH2S25QB/BlueTopo_BH2S25QB_20250708.tiff` · 1,4 MB
- `bathymetrie/noaa_bluetopo/BH2S25QB/BlueTopo_BH2S25QB_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2S25QC/BlueTopo_BH2S25QC_20250708.tiff` · 4,4 MB
- `bathymetrie/noaa_bluetopo/BH2S25QC/BlueTopo_BH2S25QC_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2S25QD/BlueTopo_BH2S25QD_20260731.tiff` · 6,1 MB
- `bathymetrie/noaa_bluetopo/BH2S25QD/BlueTopo_BH2S25QD_20260731.tiff.aux.xml` · 5 kB
- `bathymetrie/noaa_bluetopo/BH2S25QF/BlueTopo_BH2S25QF_20250708.tiff` · 220 kB
- `bathymetrie/noaa_bluetopo/BH2S25QF/BlueTopo_BH2S25QF_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2S25QG/BlueTopo_BH2S25QG_20250708.tiff` · 523 kB
- `bathymetrie/noaa_bluetopo/BH2S25QG/BlueTopo_BH2S25QG_20250708.tiff.aux.xml` · 5 kB
- `bathymetrie/noaa_bluetopo/BH2S45QB/BlueTopo_BH2S45QB_20250708.tiff` · 2,4 MB
- `bathymetrie/noaa_bluetopo/BH2S45QB/BlueTopo_BH2S45QB_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2S45QC/BlueTopo_BH2S45QC_20260731.tiff` · 7,4 MB
- `bathymetrie/noaa_bluetopo/BH2S45QC/BlueTopo_BH2S45QC_20260731.tiff.aux.xml` · 6 kB
- `bathymetrie/noaa_bluetopo/BH2S45QD/BlueTopo_BH2S45QD_20260731.tiff` · 631 kB
- `bathymetrie/noaa_bluetopo/BH2S45QD/BlueTopo_BH2S45QD_20260731.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2S55QB/BlueTopo_BH2S55QB_20250708.tiff` · 4,2 MB
- `bathymetrie/noaa_bluetopo/BH2S55QB/BlueTopo_BH2S55QB_20250708.tiff.aux.xml` · 4 kB
- `bathymetrie/noaa_bluetopo/BH2S55QC/BlueTopo_BH2S55QC_20260731.tiff` · 4,2 MB
- `bathymetrie/noaa_bluetopo/BH2S55QC/BlueTopo_BH2S55QC_20260731.tiff.aux.xml` · 6 kB
- `bathymetrie/noaa_bluetopo/BH2S65QB/BlueTopo_BH2S65QB_20260616.tiff` · 5,0 MB
- `bathymetrie/noaa_bluetopo/BH2S65QB/BlueTopo_BH2S65QB_20260616.tiff.aux.xml` · 7 kB
- `bathymetrie/noaa_bluetopo/BH2S65QC/BlueTopo_BH2S65QC_20260731.tiff` · 2,5 MB
- `bathymetrie/noaa_bluetopo/BH2S65QC/BlueTopo_BH2S65QC_20260731.tiff.aux.xml` · 6 kB
- `bathymetrie/noaa_bluetopo/BH2S75QB/BlueTopo_BH2S75QB_20260731.tiff` · 6,9 MB
- `bathymetrie/noaa_bluetopo/BH2S75QB/BlueTopo_BH2S75QB_20260731.tiff.aux.xml` · 8 kB
- `bathymetrie/noaa_bluetopo/BH2S75QJ/BlueTopo_BH2S75QJ_20260722.tiff` · 1,7 MB
- `bathymetrie/noaa_bluetopo/BH2S75QJ/BlueTopo_BH2S75QJ_20260722.tiff.aux.xml` · 7 kB
- `bathymetrie/noaa_bluetopo/BH2S85QB/BlueTopo_BH2S85QB_20260731.tiff` · 7,4 MB
- `bathymetrie/noaa_bluetopo/BH2S85QB/BlueTopo_BH2S85QB_20260731.tiff.aux.xml` · 7 kB
- `bathymetrie/noaa_bluetopo/BH2S85QJ/BlueTopo_BH2S85QJ_20260722.tiff` · 2,3 MB
- `bathymetrie/noaa_bluetopo/BH2S85QJ/BlueTopo_BH2S85QJ_20260722.tiff.aux.xml` · 7 kB
- `bathymetrie/noaa_bluetopo/BH2S85QK/BlueTopo_BH2S85QK_20260722.tiff` · 3,3 MB
- `bathymetrie/noaa_bluetopo/BH2S85QK/BlueTopo_BH2S85QK_20260722.tiff.aux.xml` · 6 kB
- `bathymetrie/noaa_bluetopo/BH2S85QL/BlueTopo_BH2S85QL_20260205.tiff` · 623 kB
- `bathymetrie/noaa_bluetopo/BH2S85QL/BlueTopo_BH2S85QL_20260205.tiff.aux.xml` · 6 kB
- `bathymetrie/noaa_bluetopo/BH2S95QB/BlueTopo_BH2S95QB_20260731.tiff` · 7,9 MB
- `bathymetrie/noaa_bluetopo/BH2S95QB/BlueTopo_BH2S95QB_20260731.tiff.aux.xml` · 7 kB
- `bathymetrie/noaa_bluetopo/BH2S95QK/BlueTopo_BH2S95QK_20260722.tiff` · 3,8 MB
- `bathymetrie/noaa_bluetopo/BH2S95QK/BlueTopo_BH2S95QK_20260722.tiff.aux.xml` · 7 kB
- `bathymetrie/noaa_bluetopo/BH2S95QL/BlueTopo_BH2S95QL_20260722.tiff` · 4,7 MB
- `bathymetrie/noaa_bluetopo/BH2S95QL/BlueTopo_BH2S95QL_20260722.tiff.aux.xml` · 7 kB
- `bathymetrie/noaa_bluetopo/BH2SB5QB/BlueTopo_BH2SB5QB_20260731.tiff` · 4,8 MB
- `bathymetrie/noaa_bluetopo/BH2SB5QB/BlueTopo_BH2SB5QB_20260731.tiff.aux.xml` · 6 kB
- `bathymetrie/noaa_bluetopo/BH2SB5QK/BlueTopo_BH2SB5QK_20260722.tiff` · 7,0 MB
- `bathymetrie/noaa_bluetopo/BH2SB5QK/BlueTopo_BH2SB5QK_20260722.tiff.aux.xml` · 9 kB
- `bathymetrie/noaa_bluetopo/BH2SB5QL/BlueTopo_BH2SB5QL_20260722.tiff` · 5,1 MB
- `bathymetrie/noaa_bluetopo/BH2SB5QL/BlueTopo_BH2SB5QL_20260722.tiff.aux.xml` · 8 kB
- `bathymetrie/noaa_bluetopo/BlueTopo_Tile_Scheme_20261029_142421.gpkg` · 6,7 MB

**`nos_vermessungen`**

- `bathymetrie/nos_vermessungen/H04608/Bottom_Samples/H04608_BSText.txt` · 12 kB
- `bathymetrie/nos_vermessungen/H04608/DR/H04608.pdf` · 297 kB
- `bathymetrie/nos_vermessungen/H04608/GEODAS/H04608.a93.gz` · 17 kB
- `bathymetrie/nos_vermessungen/H04608/GEODAS/H04608.xyz.gz` · 16 kB
- `bathymetrie/nos_vermessungen/H04608/GEODAS/H04608_h93.htm` · 14 kB
- `bathymetrie/nos_vermessungen/H04608/H04608_iso.xml` · 95 kB
- `bathymetrie/nos_vermessungen/H04608/Smooth_Sheets/H04608.sid.gz` · 6,0 MB
- `bathymetrie/nos_vermessungen/H04608/Smooth_Sheets/H04608.tif.gz` · 61,6 MB
- `bathymetrie/nos_vermessungen/H04608/Smooth_Sheets/H04608_1.sid.gz` · 10,0 MB
- `bathymetrie/nos_vermessungen/H04648/Bottom_Samples/H04648_BSText.txt` · 89 kB
- `bathymetrie/nos_vermessungen/H04648/DR/H04648.pdf` · 920 kB
- `bathymetrie/nos_vermessungen/H04648/GEODAS/H04648.a93.gz` · 55 kB
- `bathymetrie/nos_vermessungen/H04648/GEODAS/H04648.xyz.gz` · 58 kB
- `bathymetrie/nos_vermessungen/H04648/GEODAS/H04648_h93.htm` · 14 kB
- `bathymetrie/nos_vermessungen/H04648/H04648_iso.xml` · 96 kB
- `bathymetrie/nos_vermessungen/H04648/Smooth_Sheets/H04648.sid.gz` · 10,2 MB
- `bathymetrie/nos_vermessungen/H04648/Smooth_Sheets/H04648.tif.gz` · 133,3 MB
- `bathymetrie/nos_vermessungen/H04648/Smooth_Sheets/H04648_1.sid.gz` · 21,6 MB
- `bathymetrie/nos_vermessungen/H06581/DR/H06581.pdf` · 9,3 MB
- `bathymetrie/nos_vermessungen/H06581/GEODAS/H06581.a93.gz` · 149 kB
- `bathymetrie/nos_vermessungen/H06581/GEODAS/H06581.xyz.gz` · 163 kB
- `bathymetrie/nos_vermessungen/H06581/GEODAS/H06581_h93.htm` · 14 kB
- `bathymetrie/nos_vermessungen/H06581/H06581_iso.xml` · 86 kB
- `bathymetrie/nos_vermessungen/H06581/Smooth_Sheets/H06581.sid.gz` · 18,4 MB
- `bathymetrie/nos_vermessungen/H06581/Smooth_Sheets/H06581.tif.gz` · 103,6 MB
- `bathymetrie/nos_vermessungen/H06582/DR/H06582.pdf` · 844 kB
- `bathymetrie/nos_vermessungen/H06582/GEODAS/H06582.a93.gz` · 30 kB
- `bathymetrie/nos_vermessungen/H06582/GEODAS/H06582.xyz.gz` · 30 kB
- `bathymetrie/nos_vermessungen/H06582/GEODAS/H06582_h93.htm` · 14 kB
- `bathymetrie/nos_vermessungen/H06582/H06582_iso.xml` · 86 kB
- `bathymetrie/nos_vermessungen/H06582/Smooth_Sheets/H06582.sid.gz` · 9,7 MB
- `bathymetrie/nos_vermessungen/H06582/Smooth_Sheets/H06582.tif.gz` · 54,0 MB
- `bathymetrie/nos_vermessungen/H06583/DR/H06583.pdf` · 610 kB
- `bathymetrie/nos_vermessungen/H06583/GEODAS/H06583.a93.gz` · 56 kB
- `bathymetrie/nos_vermessungen/H06583/GEODAS/H06583.xyz.gz` · 55 kB
- `bathymetrie/nos_vermessungen/H06583/GEODAS/H06583_h93.htm` · 14 kB
- `bathymetrie/nos_vermessungen/H06583/H06583_iso.xml` · 86 kB
- `bathymetrie/nos_vermessungen/H06583/Smooth_Sheets/H06583.sid.gz` · 7,9 MB
- `bathymetrie/nos_vermessungen/H06583/Smooth_Sheets/H06583.tif.gz` · 35,9 MB
- `bathymetrie/nos_vermessungen/H08492/DR/H08492.pdf` · 1,1 MB
- `bathymetrie/nos_vermessungen/H08492/GEODAS/H08492.a93.gz` · 55 kB
- `bathymetrie/nos_vermessungen/H08492/GEODAS/H08492.xyz.gz` · 53 kB
- `bathymetrie/nos_vermessungen/H08492/GEODAS/H08492_h93.htm` · 14 kB
- `bathymetrie/nos_vermessungen/H08492/H08492_iso.xml` · 99 kB
- `bathymetrie/nos_vermessungen/H08492/Smooth_Sheets/H08492.sid.gz` · 11,4 MB
- `bathymetrie/nos_vermessungen/H08492/Smooth_Sheets/H08492.tif.gz` · 55,8 MB
- `bathymetrie/nos_vermessungen/H14228/BAG/H14228_MB_VR_MLLW_1of1.bag` · 250,7 MB
- `bathymetrie/nos_vermessungen/H14228/Bottom_Samples/H14228_BottomSamples.ascii` · 961 B
- `bathymetrie/nos_vermessungen/H14228/Bottom_Samples/H14228_BottomSamples_shp_images.zip` · 1 kB
- `bathymetrie/nos_vermessungen/H14228/GeoImagePDF/H14228_GeoImage.pdf` · 12,3 MB
- `bathymetrie/nos_vermessungen/H14228/H14228_iso.xml` · 122 kB
- `bathymetrie/nos_vermessungen/H14228/MBAB/H14228_MBAB_2m_300kHz_1of3.tif` · 8,2 MB
- `bathymetrie/nos_vermessungen/H14228/MBAB/H14228_MBAB_2m_300kHz_2of3.tif` · 7,8 MB
- `bathymetrie/nos_vermessungen/H14228/MBAB/H14228_MBAB_2m_300kHz_3of3.tif` · 10,9 MB
- `bathymetrie/nos_vermessungen/H14228/Metadata/H14228_Survey_Metadata.pdf` · 1,5 MB
- `bathymetrie/nos_vermessungen/H14228/Metadata/H14228_Survey_Metadata.xml` · 32 kB
- `bathymetrie/nos_vermessungen/H14228/geojson/H14228_Outline.geojson` · 2 kB

**`usace_topobathy_2019`**

- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK4499_BareEarth_1mGrid.tif` · 64 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK4599_BareEarth_1mGrid.tif` · 2,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK4698_BareEarth_1mGrid.tif` · 1,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK4699_BareEarth_1mGrid.tif` · 5,3 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK4798_BareEarth_1mGrid.tif` · 3,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK4799_BareEarth_1mGrid.tif` · 5,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK4897_BareEarth_1mGrid.tif` · 93 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK4898_BareEarth_1mGrid.tif` · 4,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK4899_BareEarth_1mGrid.tif` · 5,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK4997_BareEarth_1mGrid.tif` · 2,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK4998_BareEarth_1mGrid.tif` · 5,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK4999_BareEarth_1mGrid.tif` · 5,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5096_BareEarth_1mGrid.tif` · 1,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5097_BareEarth_1mGrid.tif` · 5,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5098_BareEarth_1mGrid.tif` · 5,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5099_BareEarth_1mGrid.tif` · 5,2 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5195_BareEarth_1mGrid.tif` · 1,3 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5196_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5197_BareEarth_1mGrid.tif` · 5,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5198_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5199_BareEarth_1mGrid.tif` · 1,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5294_BareEarth_1mGrid.tif` · 654 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5295_BareEarth_1mGrid.tif` · 5,3 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5296_BareEarth_1mGrid.tif` · 5,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5297_BareEarth_1mGrid.tif` · 5,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5298_BareEarth_1mGrid.tif` · 2,2 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5393_BareEarth_1mGrid.tif` · 6 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5394_BareEarth_1mGrid.tif` · 4,1 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5395_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5396_BareEarth_1mGrid.tif` · 5,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5397_BareEarth_1mGrid.tif` · 2,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5493_BareEarth_1mGrid.tif` · 3,1 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5494_BareEarth_1mGrid.tif` · 5,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5495_BareEarth_1mGrid.tif` · 5,4 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5496_BareEarth_1mGrid.tif` · 2,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5497_BareEarth_1mGrid.tif` · 7 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5592_BareEarth_1mGrid.tif` · 2,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5593_BareEarth_1mGrid.tif` · 5,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5594_BareEarth_1mGrid.tif` · 5,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5595_BareEarth_1mGrid.tif` · 3,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5596_BareEarth_1mGrid.tif` · 25 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5691_BareEarth_1mGrid.tif` · 1,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5692_BareEarth_1mGrid.tif` · 5,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5693_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5694_BareEarth_1mGrid.tif` · 4,0 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5695_BareEarth_1mGrid.tif` · 83 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5790_BareEarth_1mGrid.tif` · 1,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5791_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5792_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5793_BareEarth_1mGrid.tif` · 4,2 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5794_BareEarth_1mGrid.tif` · 128 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5889_BareEarth_1mGrid.tif` · 2,0 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5890_BareEarth_1mGrid.tif` · 5,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5891_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5892_BareEarth_1mGrid.tif` · 4,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5893_BareEarth_1mGrid.tif` · 389 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5987_BareEarth_1mGrid.tif` · 156 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5988_BareEarth_1mGrid.tif` · 2,2 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5989_BareEarth_1mGrid.tif` · 5,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5990_BareEarth_1mGrid.tif` · 5,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5991_BareEarth_1mGrid.tif` · 4,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK5992_BareEarth_1mGrid.tif` · 537 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6087_BareEarth_1mGrid.tif` · 3,4 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6088_BareEarth_1mGrid.tif` · 6,0 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6089_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6090_BareEarth_1mGrid.tif` · 4,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6091_BareEarth_1mGrid.tif` · 820 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6186_BareEarth_1mGrid.tif` · 1,4 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6187_BareEarth_1mGrid.tif` · 5,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6188_BareEarth_1mGrid.tif` · 5,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6189_BareEarth_1mGrid.tif` · 5,3 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6190_BareEarth_1mGrid.tif` · 973 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6285_BareEarth_1mGrid.tif` · 181 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6286_BareEarth_1mGrid.tif` · 5,1 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6287_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6288_BareEarth_1mGrid.tif` · 5,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6289_BareEarth_1mGrid.tif` · 2,0 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6385_BareEarth_1mGrid.tif` · 3,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6386_BareEarth_1mGrid.tif` · 5,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6387_BareEarth_1mGrid.tif` · 5,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6388_BareEarth_1mGrid.tif` · 1,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6484_BareEarth_1mGrid.tif` · 2,1 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6485_BareEarth_1mGrid.tif` · 6,1 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6486_BareEarth_1mGrid.tif` · 5,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6487_BareEarth_1mGrid.tif` · 2,3 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6583_BareEarth_1mGrid.tif` · 2,2 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6584_BareEarth_1mGrid.tif` · 5,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6585_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6586_BareEarth_1mGrid.tif` · 2,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6682_BareEarth_1mGrid.tif` · 2,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6683_BareEarth_1mGrid.tif` · 6,2 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6684_BareEarth_1mGrid.tif` · 5,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6685_BareEarth_1mGrid.tif` · 3,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6686_BareEarth_1mGrid.tif` · 14 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6781_BareEarth_1mGrid.tif` · 845 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6782_BareEarth_1mGrid.tif` · 6,1 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6783_BareEarth_1mGrid.tif` · 6,0 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6784_BareEarth_1mGrid.tif` · 3,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6785_BareEarth_1mGrid.tif` · 48 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6881_BareEarth_1mGrid.tif` · 2,0 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6882_BareEarth_1mGrid.tif` · 6,0 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6883_BareEarth_1mGrid.tif` · 4,2 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6884_BareEarth_1mGrid.tif` · 98 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6980_BareEarth_1mGrid.tif` · 83 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6981_BareEarth_1mGrid.tif` · 4,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6982_BareEarth_1mGrid.tif` · 4,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK6983_BareEarth_1mGrid.tif` · 329 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7080_BareEarth_1mGrid.tif` · 3,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7081_BareEarth_1mGrid.tif` · 4,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7082_BareEarth_1mGrid.tif` · 441 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7179_BareEarth_1mGrid.tif` · 2,3 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7180_BareEarth_1mGrid.tif` · 3,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7181_BareEarth_1mGrid.tif` · 554 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7278_BareEarth_1mGrid.tif` · 2,0 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7279_BareEarth_1mGrid.tif` · 5,0 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7280_BareEarth_1mGrid.tif` · 1,1 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7377_BareEarth_1mGrid.tif` · 267 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7378_BareEarth_1mGrid.tif` · 4,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7379_BareEarth_1mGrid.tif` · 1,4 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7476_BareEarth_1mGrid.tif` · 1,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7477_BareEarth_1mGrid.tif` · 5,0 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7478_BareEarth_1mGrid.tif` · 1,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7574_BareEarth_1mGrid.tif` · 3 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7575_BareEarth_1mGrid.tif` · 2,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7576_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7577_BareEarth_1mGrid.tif` · 2,3 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7673_BareEarth_1mGrid.tif` · 898 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7674_BareEarth_1mGrid.tif` · 3,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7675_BareEarth_1mGrid.tif` · 6,0 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7676_BareEarth_1mGrid.tif` · 2,7 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7772_BareEarth_1mGrid.tif` · 102 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7773_BareEarth_1mGrid.tif` · 5,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7774_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7775_BareEarth_1mGrid.tif` · 3,2 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7776_BareEarth_1mGrid.tif` · 4 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7872_BareEarth_1mGrid.tif` · 17 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7873_BareEarth_1mGrid.tif` · 4,2 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7874_BareEarth_1mGrid.tif` · 3,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLK7875_BareEarth_1mGrid.tif` · 42 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4305_BareEarth_1mGrid.tif` · 49 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4306_BareEarth_1mGrid.tif` · 61 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4400_BareEarth_1mGrid.tif` · 2,1 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4401_BareEarth_1mGrid.tif` · 3,4 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4402_BareEarth_1mGrid.tif` · 2,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4403_BareEarth_1mGrid.tif` · 2,1 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4404_BareEarth_1mGrid.tif` · 3,4 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4405_BareEarth_1mGrid.tif` · 3,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4406_BareEarth_1mGrid.tif` · 68 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4500_BareEarth_1mGrid.tif` · 2,2 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4501_BareEarth_1mGrid.tif` · 3,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4502_BareEarth_1mGrid.tif` · 5,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4503_BareEarth_1mGrid.tif` · 5,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4504_BareEarth_1mGrid.tif` · 4,3 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4505_BareEarth_1mGrid.tif` · 253 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4600_BareEarth_1mGrid.tif` · 3,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4601_BareEarth_1mGrid.tif` · 131 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4602_BareEarth_1mGrid.tif` · 3,9 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4603_BareEarth_1mGrid.tif` · 4,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4604_BareEarth_1mGrid.tif` · 309 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4700_BareEarth_1mGrid.tif` · 3,5 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4702_BareEarth_1mGrid.tif` · 657 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4703_BareEarth_1mGrid.tif` · 592 kB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4800_BareEarth_1mGrid.tif` · 2,3 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL4900_BareEarth_1mGrid.tif` · 2,8 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/2019_NCMP_AK_08VLL5000_BareEarth_1mGrid.tif` · 1,6 MB
- `bathymetrie/usace_topobathy_2019/Blk_530_Lituya/index.html` · 36 kB
- `bathymetrie/usace_topobathy_2019/ak2019_dem_m9055_met.xml` · 79 kB
- `bathymetrie/usace_topobathy_2019/ak2019_dem_m9055_met_forHumans.html` · 72 kB
- `bathymetrie/usace_topobathy_2019/tileindex_USACE_AK_DEM_2019.zip` · 83 kB

### dokumente

**`dokumente`**

- `dokumente/dokumente/Franco_2020_NHESS.pdf` · 19,9 MB
- `dokumente/dokumente/Gonzalez-Vida_2019_NHESS.pdf` · 10,1 MB
- `dokumente/dokumente/Mertie_1931_B836B.pdf` · 1,6 MB
- `dokumente/dokumente/Miller_1960_Abschrift_Dutch.html` · 31 kB
- `dokumente/dokumente/Miller_1960_PP354C.pdf` · 8,2 MB
- `dokumente/dokumente/Miller_1960_PP354C_DGGS.pdf` · 9,9 MB
- `dokumente/dokumente/USGS_Lidar_GlacierBay_2019_Bericht.pdf` · 14 kB

**`usgs_publikationen`**

- `dokumente/usgs_publikationen/b1121K/plate-1.pdf` · 1,9 MB
- `dokumente/usgs_publikationen/b1121K/plate-2.pdf` · 2,3 MB
- `dokumente/usgs_publikationen/b1121K/report.pdf` · 9,9 MB
- `dokumente/usgs_publikationen/b1121K/seite.html` · 42 kB
- `dokumente/usgs_publikationen/b836B/report.pdf` · 1,6 MB
- `dokumente/usgs_publikationen/b836B/seite.html` · 39 kB
- `dokumente/usgs_publikationen/ofr53193/plate-1.pdf` · 1,8 MB
- `dokumente/usgs_publikationen/ofr53193/plate-2.pdf` · 2,4 MB
- `dokumente/usgs_publikationen/ofr53193/seite.html` · 44 kB
- `dokumente/usgs_publikationen/ofr61100/plate-1.pdf` · 5,4 MB
- `dokumente/usgs_publikationen/ofr61100/seite.html` · 43 kB
- `dokumente/usgs_publikationen/ofr78494/plate-1.pdf` · 10,4 MB
- `dokumente/usgs_publikationen/ofr78494/plate-2.pdf` · 5,8 MB
- `dokumente/usgs_publikationen/ofr78494/plate-3.pdf` · 16,3 MB
- `dokumente/usgs_publikationen/ofr78494/plate-4.pdf` · 12,1 MB
- `dokumente/usgs_publikationen/ofr78494/plate-5.pdf` · 14,0 MB
- `dokumente/usgs_publikationen/ofr78494/plate-6.pdf` · 4,0 MB
- `dokumente/usgs_publikationen/ofr78494/report.pdf` · 19,2 MB
- `dokumente/usgs_publikationen/ofr78494/seite.html` · 50 kB
- `dokumente/usgs_publikationen/pp354C/plate-2.pdf` · 1,4 MB
- `dokumente/usgs_publikationen/pp354C/report.pdf` · 8,2 MB
- `dokumente/usgs_publikationen/pp354C/seite.html` · 78 kB
- `dokumente/usgs_publikationen/pp632/plate-01.pdf` · 2,8 MB
- `dokumente/usgs_publikationen/pp632/plate-02.pdf` · 2,9 MB
- `dokumente/usgs_publikationen/pp632/plate-03.pdf` · 3,1 MB
- `dokumente/usgs_publikationen/pp632/plate-04.pdf` · 3,1 MB
- `dokumente/usgs_publikationen/pp632/plate-05.pdf` · 3,1 MB
- `dokumente/usgs_publikationen/pp632/plate-06.pdf` · 2,8 MB
- `dokumente/usgs_publikationen/pp632/plate-07.pdf` · 2,4 MB
- `dokumente/usgs_publikationen/pp632/plate-08.pdf` · 2,2 MB
- `dokumente/usgs_publikationen/pp632/plate-09.pdf` · 614 kB
- `dokumente/usgs_publikationen/pp632/plate-10.pdf` · 1,1 MB
- `dokumente/usgs_publikationen/pp632/plate-11.pdf` · 1,4 MB
- `dokumente/usgs_publikationen/pp632/plate-12.pdf` · 1,0 MB
- `dokumente/usgs_publikationen/pp632/report.pdf` · 3,7 MB
- `dokumente/usgs_publikationen/pp632/seite.html` · 52 kB

### ereignis

**`ncei_tsunami`**

- `ereignis/ncei_tsunami/erdbeben_1958_ncei_4167.json` · 4 kB
- `ereignis/ncei_tsunami/tsunami_auflauf.json` · 3 kB
- `ereignis/ncei_tsunami/tsunami_ereignisse.json` · 2 kB
- `ereignis/ncei_tsunami/tsunami_ereignisse_usa_bis1960.json` · 58 kB

**`usgs_erdbeben`**

- `ereignis/usgs_erdbeben/erdbeben_1958_iscgem884702.geojson` · 26 kB
- `ereignis/usgs_erdbeben/erdbeben_ab_M4.geojson` · 101 kB

### geologie

**`geologie_alaska`**

- `geologie/geologie_alaska/nps_gri_glba_kartenbeschreibung.pdf` · 4,7 MB
- `geologie/geologie_alaska/nps_gri_glba_oberflaeche.gpkg` · 8,5 MB
- `geologie/geologie_alaska/sim3340_databases.zip` · 85,6 MB
- `geologie/geologie_alaska/sim3340_shp.zip` · 710,9 MB

**`stoerungen`**

- `geologie/stoerungen/Qfaults_GIS.zip` · 32,4 MB
- `geologie/stoerungen/dggs_mp141_stoerungen.geojson` · 172 B

### gletscher

**`glims`**

- `gletscher/glims/glims_rgi70.geojson` · 421 B
- `gletscher/glims/glims_umrisse.geojson` · 7,8 MB

**`itslive_geschwindigkeit`**

- `gletscher/itslive_geschwindigkeit/ITS_LIVE_velocity_120m_RGI01A_0000_v02_landice_szene.tif` · 1,6 MB
- `gletscher/itslive_geschwindigkeit/ITS_LIVE_velocity_120m_RGI01A_0000_v02_v_error_szene.tif` · 1,6 MB
- `gletscher/itslive_geschwindigkeit/ITS_LIVE_velocity_120m_RGI01A_0000_v02_v_szene.tif` · 1,6 MB
- `gletscher/itslive_geschwindigkeit/ITS_LIVE_velocity_120m_RGI01A_0000_v02_vx_szene.tif` · 1,6 MB
- `gletscher/itslive_geschwindigkeit/ITS_LIVE_velocity_120m_RGI01A_0000_v02_vy_szene.tif` · 1,6 MB

**`rgi7`**

- `gletscher/rgi7/01_rgi60_Alaska.zip` · 17 kB
- `gletscher/rgi7/RGI2000-v7.0-G-01_alaska.zip` · 79,3 MB

### hoehe

**`arcticdem_10m`**

- `hoehe/arcticdem_10m/10m/42_05_10m_v4.1.json` · 10 kB
- `hoehe/arcticdem_10m/10m/42_05_10m_v4.1_browse.tif` · 6,1 MB
- `hoehe/arcticdem_10m/10m/42_05_10m_v4.1_count.tif` · 483 kB
- `hoehe/arcticdem_10m/10m/42_05_10m_v4.1_dem.tif` · 20,1 MB
- `hoehe/arcticdem_10m/10m/42_05_10m_v4.1_mad.tif` · 12,9 MB
- `hoehe/arcticdem_10m/10m/42_06_10m_v4.1.json` · 17 kB
- `hoehe/arcticdem_10m/10m/42_06_10m_v4.1_browse.tif` · 80,8 MB
- `hoehe/arcticdem_10m/10m/42_06_10m_v4.1_count.tif` · 4,9 MB
- `hoehe/arcticdem_10m/10m/42_06_10m_v4.1_dem.tif` · 227,1 MB
- `hoehe/arcticdem_10m/10m/42_06_10m_v4.1_mad.tif` · 190,2 MB

**`arcticdem_32m`**

- `hoehe/arcticdem_32m/32m/41_04_32m_v4.1.json` · 15 kB
- `hoehe/arcticdem_32m/32m/41_04_32m_v4.1_browse.tif` · 6,3 MB
- `hoehe/arcticdem_32m/32m/41_04_32m_v4.1_count.tif` · 535 kB
- `hoehe/arcticdem_32m/32m/41_04_32m_v4.1_dem.tif` · 19,3 MB
- `hoehe/arcticdem_32m/32m/41_04_32m_v4.1_mad.tif` · 12,4 MB
- `hoehe/arcticdem_32m/32m/41_05_32m_v4.1.json` · 16 kB
- `hoehe/arcticdem_32m/32m/41_05_32m_v4.1_browse.tif` · 10,6 MB
- `hoehe/arcticdem_32m/32m/41_05_32m_v4.1_count.tif` · 928 kB
- `hoehe/arcticdem_32m/32m/41_05_32m_v4.1_dem.tif` · 26,6 MB
- `hoehe/arcticdem_32m/32m/41_05_32m_v4.1_mad.tif` · 20,8 MB
- `hoehe/arcticdem_32m/32m/41_06_32m_v4.1.json` · 17 kB
- `hoehe/arcticdem_32m/32m/41_06_32m_v4.1_browse.tif` · 11,3 MB
- `hoehe/arcticdem_32m/32m/41_06_32m_v4.1_count.tif` · 1,0 MB
- `hoehe/arcticdem_32m/32m/41_06_32m_v4.1_dem.tif` · 29,5 MB
- `hoehe/arcticdem_32m/32m/41_06_32m_v4.1_mad.tif` · 24,0 MB
- `hoehe/arcticdem_32m/32m/41_07_32m_v4.1.json` · 15 kB
- `hoehe/arcticdem_32m/32m/41_07_32m_v4.1_browse.tif` · 13,4 MB
- `hoehe/arcticdem_32m/32m/41_07_32m_v4.1_count.tif` · 963 kB
- `hoehe/arcticdem_32m/32m/41_07_32m_v4.1_dem.tif` · 32,8 MB
- `hoehe/arcticdem_32m/32m/41_07_32m_v4.1_mad.tif` · 27,5 MB
- `hoehe/arcticdem_32m/32m/42_05_32m_v4.1.json` · 10 kB
- `hoehe/arcticdem_32m/32m/42_05_32m_v4.1_browse.tif` · 738 kB
- `hoehe/arcticdem_32m/32m/42_05_32m_v4.1_count.tif` · 94 kB
- `hoehe/arcticdem_32m/32m/42_05_32m_v4.1_dem.tif` · 2,6 MB
- `hoehe/arcticdem_32m/32m/42_05_32m_v4.1_mad.tif` · 1,3 MB
- `hoehe/arcticdem_32m/32m/42_06_32m_v4.1.json` · 17 kB
- `hoehe/arcticdem_32m/32m/42_06_32m_v4.1_browse.tif` · 10,0 MB
- `hoehe/arcticdem_32m/32m/42_06_32m_v4.1_count.tif` · 962 kB
- `hoehe/arcticdem_32m/32m/42_06_32m_v4.1_dem.tif` · 26,6 MB
- `hoehe/arcticdem_32m/32m/42_06_32m_v4.1_mad.tif` · 22,1 MB
- `hoehe/arcticdem_32m/32m/42_07_32m_v4.1.json` · 15 kB
- `hoehe/arcticdem_32m/32m/42_07_32m_v4.1_browse.tif` · 13,5 MB
- `hoehe/arcticdem_32m/32m/42_07_32m_v4.1_count.tif` · 1,0 MB
- `hoehe/arcticdem_32m/32m/42_07_32m_v4.1_dem.tif` · 32,5 MB
- `hoehe/arcticdem_32m/32m/42_07_32m_v4.1_mad.tif` · 27,9 MB
- `hoehe/arcticdem_32m/32m/43_06_32m_v4.1.json` · 9 kB
- `hoehe/arcticdem_32m/32m/43_06_32m_v4.1_browse.tif` · 785 kB
- `hoehe/arcticdem_32m/32m/43_06_32m_v4.1_count.tif` · 91 kB
- `hoehe/arcticdem_32m/32m/43_06_32m_v4.1_dem.tif` · 2,3 MB
- `hoehe/arcticdem_32m/32m/43_06_32m_v4.1_mad.tif` · 1,4 MB
- `hoehe/arcticdem_32m/32m/43_07_32m_v4.1.json` · 19 kB
- `hoehe/arcticdem_32m/32m/43_07_32m_v4.1_browse.tif` · 9,8 MB
- `hoehe/arcticdem_32m/32m/43_07_32m_v4.1_count.tif` · 963 kB
- `hoehe/arcticdem_32m/32m/43_07_32m_v4.1_dem.tif` · 25,9 MB
- `hoehe/arcticdem_32m/32m/43_07_32m_v4.1_mad.tif` · 21,4 MB

**`arcticdem_streifen`**

- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20140411_102001002EDE8A00_102001002D971000_2m_lsf_seg1.json` · 14 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20140411_102001002EDE8A00_102001002D971000_2m_lsf_seg1_dem_10m_shade.tif` · 23,0 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20141112_1020010037D38E00_1020010038C9DD00_2m_lsf_seg2.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20141112_1020010037D38E00_1020010038C9DD00_2m_lsf_seg2_dem_10m_shade.tif` · 10,9 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20151212_1020010048D14100_10200100469D1500_2m_lsf_seg2.json` · 14 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20151212_1020010048D14100_10200100469D1500_2m_lsf_seg2_dem_10m_shade.tif` · 18,7 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20160322_102001004E5C9200_102001004F00F100_2m_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20160322_102001004E5C9200_102001004F00F100_2m_seg1_dem_10m_shade.tif` · 15,0 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20170107_102001005BDF6900_102001005D6DA100_2m_lsf_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20170107_102001005BDF6900_102001005D6DA100_2m_lsf_seg1_dem_10m_shade.tif` · 20,1 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20170111_102001005C446300_1020010059273400_2m_lsf_seg2.json` · 14 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20170111_102001005C446300_1020010059273400_2m_lsf_seg2_dem_10m_shade.tif` · 12,3 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20170902_1020010065741B00_1020010069689C00_2m_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20170902_1020010065741B00_1020010069689C00_2m_seg1_dem_10m_shade.tif` · 15,7 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20171028_10200100677C9100_102001006A0FDE00_2m_lsf_seg1.json` · 12 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20171028_10200100677C9100_102001006A0FDE00_2m_lsf_seg1_dem_10m_shade.tif` · 9,1 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20171101_102001006AAA6A00_102001006A495D00_2m_lsf_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20171101_102001006AAA6A00_102001006A495D00_2m_lsf_seg1_dem_10m_shade.tif` · 13,9 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20190319_1020010081404C00_10200100868B1000_2m_lsf_seg1.json` · 14 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20190319_1020010081404C00_10200100868B1000_2m_lsf_seg1_dem_10m_shade.tif` · 19,1 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20190401_10200100803BC200_102001008468D700_2m_lsf_seg3.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20190401_10200100803BC200_102001008468D700_2m_lsf_seg3_dem_10m_shade.tif` · 14,7 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20200612_10200100988A1000_1020010097277F00_2m_lsf_seg4.json` · 14 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20200612_10200100988A1000_1020010097277F00_2m_lsf_seg4_dem_10m_shade.tif` · 15,6 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20200629_102001009517BA00_102001009991AD00_2m_lsf_seg4.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20200629_102001009517BA00_102001009991AD00_2m_lsf_seg4_dem_10m_shade.tif` · 3,8 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20200629_102001009517BA00_102001009991AD00_2m_lsf_seg5.json` · 12 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20200629_102001009517BA00_102001009991AD00_2m_lsf_seg5_dem_10m_shade.tif` · 1,5 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20201104_102001009EE74400_102001009F1F8A00_2m_seg2.json` · 14 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20201104_102001009EE74400_102001009F1F8A00_2m_seg2_dem_10m_shade.tif` · 9,3 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20211016_10200100B8037800_10200100B9C6F900_2m_lsf_seg4.json` · 14 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20211016_10200100B8037800_10200100B9C6F900_2m_lsf_seg4_dem_10m_shade.tif` · 13,4 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20211029_10200100B98F7500_10200100BA0D1700_2m_lsf_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV01_20211029_10200100B98F7500_10200100BA0D1700_2m_lsf_seg1_dem_10m_shade.tif` · 13,7 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20131108_10300100296FF600_103001002799BC00_2m_lsf_seg2.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20131108_10300100296FF600_103001002799BC00_2m_lsf_seg2_dem_10m_shade.tif` · 11,9 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20131108_103001002A338700_1030010029910C00_2m_lsf_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20131108_103001002A338700_1030010029910C00_2m_lsf_seg1_dem_10m_shade.tif` · 10,7 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20141026_10300100390DA500_1030010036B2E700_2m_lsf_seg3.json` · 14 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20141026_10300100390DA500_1030010036B2E700_2m_lsf_seg3_dem_10m_shade.tif` · 8,8 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150518_1030010041D0BB00_1030010041BD1C00_2m_lsf_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150518_1030010041D0BB00_1030010041BD1C00_2m_lsf_seg1_dem_10m_shade.tif` · 16,5 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150528_10300100412EC200_1030010043A23E00_2m_lsf_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150528_10300100412EC200_1030010043A23E00_2m_lsf_seg1_dem_10m_shade.tif` · 16,9 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150528_103001004447CD00_10300100419DF400_2m_lsf_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150528_103001004447CD00_10300100419DF400_2m_lsf_seg1_dem_10m_shade.tif` · 4,0 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150528_103001004447CD00_10300100419DF400_2m_lsf_seg2.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150528_103001004447CD00_10300100419DF400_2m_lsf_seg2_dem_10m_shade.tif` · 4,2 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150528_103001004447CD00_10300100419DF400_2m_lsf_seg3.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150528_103001004447CD00_10300100419DF400_2m_lsf_seg3_dem_10m_shade.tif` · 940 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150528_103001004447CD00_10300100419DF400_2m_lsf_seg4.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150528_103001004447CD00_10300100419DF400_2m_lsf_seg4_dem_10m_shade.tif` · 3,9 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150622_1030010044739500_1030010046CD3A00_2m_lsf_seg1.json` · 12 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20150622_1030010044739500_1030010046CD3A00_2m_lsf_seg1_dem_10m_shade.tif` · 10,1 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20151223_103001004E77A100_103001004D415600_2m_lsf_seg4.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20151223_103001004E77A100_103001004D415600_2m_lsf_seg4_dem_10m_shade.tif` · 3,3 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20160717_1030010058905D00_1030010058168400_2m_lsf_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20160717_1030010058905D00_1030010058168400_2m_lsf_seg1_dem_10m_shade.tif` · 17,1 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20161204_1030010061B57D00_10300100619F1B00_2m_lsf_seg3.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20161204_1030010061B57D00_10300100619F1B00_2m_lsf_seg3_dem_10m_shade.tif` · 15,3 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20161210_103001006016B200_10300100628CED00_2m_lsf_seg2.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20161210_103001006016B200_10300100628CED00_2m_lsf_seg2_dem_10m_shade.tif` · 9,4 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20161231_1030010062091E00_1030010063457200_2m_lsf_seg3.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20161231_1030010062091E00_1030010063457200_2m_lsf_seg3_dem_10m_shade.tif` · 10,4 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20171122_1030010073032300_103001007564F400_2m_lsf_seg2.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20171122_1030010073032300_103001007564F400_2m_lsf_seg2_dem_10m_shade.tif` · 8,9 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20190326_103001008EB7C600_103001008D955D00_2m_lsf_seg4.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20190326_103001008EB7C600_103001008D955D00_2m_lsf_seg4_dem_10m_shade.tif` · 11,1 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20200911_10300100ABA89400_10300100AC5ED200_2m_lsf_seg1.json` · 14 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20200911_10300100ABA89400_10300100AC5ED200_2m_lsf_seg1_dem_10m_shade.tif` · 17,6 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20211219_10300100CA0B5900_10300100CA61C200_2m_lsf_seg2.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20211219_10300100CA0B5900_10300100CA61C200_2m_lsf_seg2_dem_10m_shade.tif` · 13,4 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20220118_10300100CC35B700_10300100CCBA4700_2m_lsf_seg2.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV02_20220118_10300100CC35B700_10300100CCBA4700_2m_lsf_seg2_dem_10m_shade.tif` · 16,9 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20160115_1040010017051500_10400100177C1300_2m_lsf_seg2.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20160115_1040010017051500_10400100177C1300_2m_lsf_seg2_dem_10m_shade.tif` · 7,5 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20161210_1040010026348E00_1040010025828200_2m_lsf_seg2.json` · 12 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20161210_1040010026348E00_1040010025828200_2m_lsf_seg2_dem_10m_shade.tif` · 11,6 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20161212_10400100260CDB00_1040010027D62200_2m_lsf_seg2.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20161212_10400100260CDB00_1040010027D62200_2m_lsf_seg2_dem_10m_shade.tif` · 11,7 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20161231_10400100275B3A00_1040010027064D00_2m_lsf_seg1.json` · 14 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20161231_10400100275B3A00_1040010027064D00_2m_lsf_seg1_dem_10m_shade.tif` · 12,6 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20171201_10400100378BB200_1040010036938A00_2m_lsf_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20171201_10400100378BB200_1040010036938A00_2m_lsf_seg1_dem_10m_shade.tif` · 1,8 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20171221_104001003570CF00_1040010037076400_2m_lsf_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20171221_104001003570CF00_1040010037076400_2m_lsf_seg1_dem_10m_shade.tif` · 15,1 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20180902_1040010040780F00_10400100429C2300_2m_lsf_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20180902_1040010040780F00_10400100429C2300_2m_lsf_seg1_dem_10m_shade.tif` · 6,8 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20180902_10400100421FB900_1040010042ABA800_2m_seg1.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20180902_10400100421FB900_1040010042ABA800_2m_seg1_dem_10m_shade.tif` · 6,5 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20211209_1040010070654300_1040010071591900_2m_lsf_seg3.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20211209_1040010070654300_1040010071591900_2m_lsf_seg3_dem_10m_shade.tif` · 6,0 MB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20211228_1040010071742F00_1040010072B62B00_2m_lsf_seg2.json` · 13 kB
- `hoehe/arcticdem_streifen/SETSM_s2s041_WV03_20211228_1040010071742F00_1040010072B62B00_2m_lsf_seg2_dem_10m_shade.tif` · 11,4 MB
- `hoehe/arcticdem_streifen/streifen.csv` · 6 kB

**`copernicus_glo30`**

- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N57_00_W136_00_DEM/Copernicus_DSM_10_N57_00_W136_00.xml` · 45 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N57_00_W136_00_DEM/Copernicus_DSM_COG_10_N57_00_W136_00_DEM.tif` · 25,5 MB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N57_00_W136_00_DEM/Copernicus_DSM_COG_10_N57_00_W136_00_EDM.tif` · 663 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N57_00_W136_00_DEM/Copernicus_DSM_COG_10_N57_00_W136_00_WBM.tif` · 147 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N57_00_W137_00_DEM/Copernicus_DSM_10_N57_00_W137_00.xml` · 45 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N57_00_W137_00_DEM/Copernicus_DSM_COG_10_N57_00_W137_00_DEM.tif` · 4,9 MB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N57_00_W137_00_DEM/Copernicus_DSM_COG_10_N57_00_W137_00_EDM.tif` · 204 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N57_00_W137_00_DEM/Copernicus_DSM_COG_10_N57_00_W137_00_WBM.tif` · 120 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W136_00_DEM/Copernicus_DSM_10_N58_00_W136_00.xml` · 45 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W136_00_DEM/Copernicus_DSM_COG_10_N58_00_W136_00_DEM.tif` · 22,6 MB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W136_00_DEM/Copernicus_DSM_COG_10_N58_00_W136_00_EDM.tif` · 598 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W136_00_DEM/Copernicus_DSM_COG_10_N58_00_W136_00_WBM.tif` · 124 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W137_00_DEM/Copernicus_DSM_10_N58_00_W137_00.xml` · 45 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W137_00_DEM/Copernicus_DSM_COG_10_N58_00_W137_00_DEM.tif` · 20,5 MB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W137_00_DEM/Copernicus_DSM_COG_10_N58_00_W137_00_EDM.tif` · 553 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W137_00_DEM/Copernicus_DSM_COG_10_N58_00_W137_00_WBM.tif` · 162 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W138_00_DEM/Copernicus_DSM_10_N58_00_W138_00.xml` · 45 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W138_00_DEM/Copernicus_DSM_COG_10_N58_00_W138_00_DEM.tif` · 12,5 MB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W138_00_DEM/Copernicus_DSM_COG_10_N58_00_W138_00_EDM.tif` · 424 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W138_00_DEM/Copernicus_DSM_COG_10_N58_00_W138_00_WBM.tif` · 70 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W139_00_DEM/Copernicus_DSM_10_N58_00_W139_00.xml` · 45 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W139_00_DEM/Copernicus_DSM_COG_10_N58_00_W139_00_DEM.tif` · 292 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W139_00_DEM/Copernicus_DSM_COG_10_N58_00_W139_00_EDM.tif` · 65 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N58_00_W139_00_DEM/Copernicus_DSM_COG_10_N58_00_W139_00_WBM.tif` · 62 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W136_00_DEM/Copernicus_DSM_10_N59_00_W136_00.xml` · 45 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W136_00_DEM/Copernicus_DSM_COG_10_N59_00_W136_00_DEM.tif` · 28,3 MB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W136_00_DEM/Copernicus_DSM_COG_10_N59_00_W136_00_EDM.tif` · 743 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W136_00_DEM/Copernicus_DSM_COG_10_N59_00_W136_00_WBM.tif` · 61 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W137_00_DEM/Copernicus_DSM_10_N59_00_W137_00.xml` · 45 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W137_00_DEM/Copernicus_DSM_COG_10_N59_00_W137_00_DEM.tif` · 28,4 MB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W137_00_DEM/Copernicus_DSM_COG_10_N59_00_W137_00_EDM.tif` · 444 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W137_00_DEM/Copernicus_DSM_COG_10_N59_00_W137_00_WBM.tif` · 29 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W138_00_DEM/Copernicus_DSM_10_N59_00_W138_00.xml` · 45 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W138_00_DEM/Copernicus_DSM_COG_10_N59_00_W138_00_DEM.tif` · 28,9 MB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W138_00_DEM/Copernicus_DSM_COG_10_N59_00_W138_00_EDM.tif` · 542 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W138_00_DEM/Copernicus_DSM_COG_10_N59_00_W138_00_WBM.tif` · 32 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W139_00_DEM/Copernicus_DSM_10_N59_00_W139_00.xml` · 45 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W139_00_DEM/Copernicus_DSM_COG_10_N59_00_W139_00_DEM.tif` · 25,7 MB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W139_00_DEM/Copernicus_DSM_COG_10_N59_00_W139_00_EDM.tif` · 465 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W139_00_DEM/Copernicus_DSM_COG_10_N59_00_W139_00_WBM.tif` · 83 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W140_00_DEM/Copernicus_DSM_10_N59_00_W140_00.xml` · 45 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W140_00_DEM/Copernicus_DSM_COG_10_N59_00_W140_00_DEM.tif` · 14,4 MB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W140_00_DEM/Copernicus_DSM_COG_10_N59_00_W140_00_EDM.tif` · 304 kB
- `hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N59_00_W140_00_DEM/Copernicus_DSM_COG_10_N59_00_W140_00_WBM.tif` · 104 kB

**`usgs_3dep_1`**

- `hoehe/usgs_3dep_1/n58w136/USGS_1_n58w136.jpg` · 17 kB
- `hoehe/usgs_3dep_1/n58w136/USGS_1_n58w136.tif` · 48,9 MB
- `hoehe/usgs_3dep_1/n58w136/USGS_1_n58w136.xml` · 12 kB
- `hoehe/usgs_3dep_1/n58w137/USGS_1_n58w137.jpg` · 5 kB
- `hoehe/usgs_3dep_1/n58w137/USGS_1_n58w137.tif` · 9,4 MB
- `hoehe/usgs_3dep_1/n58w137/USGS_1_n58w137.xml` · 11 kB
- `hoehe/usgs_3dep_1/n59w136/USGS_1_n59w136.jpg` · 16 kB
- `hoehe/usgs_3dep_1/n59w136/USGS_1_n59w136.tif` · 43,3 MB
- `hoehe/usgs_3dep_1/n59w136/USGS_1_n59w136.xml` · 12 kB
- `hoehe/usgs_3dep_1/n59w137/USGS_1_n59w137.jpg` · 14 kB
- `hoehe/usgs_3dep_1/n59w137/USGS_1_n59w137.tif` · 40,3 MB
- `hoehe/usgs_3dep_1/n59w137/USGS_1_n59w137.xml` · 12 kB
- `hoehe/usgs_3dep_1/n59w138/USGS_1_n59w138.jpg` · 7 kB
- `hoehe/usgs_3dep_1/n59w138/USGS_1_n59w138.tif` · 23,8 MB
- `hoehe/usgs_3dep_1/n59w138/USGS_1_n59w138.xml` · 12 kB
- `hoehe/usgs_3dep_1/n59w139/USGS_1_n59w139.jpg` · 950 B
- `hoehe/usgs_3dep_1/n59w139/USGS_1_n59w139.tif` · 580 kB
- `hoehe/usgs_3dep_1/n59w139/USGS_1_n59w139.xml` · 12 kB
- `hoehe/usgs_3dep_1/n60w136/USGS_1_n60w136.jpg` · 17 kB
- `hoehe/usgs_3dep_1/n60w136/USGS_1_n60w136.tif` · 54,1 MB
- `hoehe/usgs_3dep_1/n60w136/USGS_1_n60w136.xml` · 11 kB
- `hoehe/usgs_3dep_1/n60w137/USGS_1_n60w137.jpg` · 15 kB
- `hoehe/usgs_3dep_1/n60w137/USGS_1_n60w137.tif` · 53,9 MB
- `hoehe/usgs_3dep_1/n60w137/USGS_1_n60w137.xml` · 12 kB
- `hoehe/usgs_3dep_1/n60w138/USGS_1_n60w138.jpg` · 13 kB
- `hoehe/usgs_3dep_1/n60w138/USGS_1_n60w138.tif` · 54,4 MB
- `hoehe/usgs_3dep_1/n60w138/USGS_1_n60w138.xml` · 12 kB
- `hoehe/usgs_3dep_1/n60w139/USGS_1_n60w139.jpg` · 12 kB
- `hoehe/usgs_3dep_1/n60w139/USGS_1_n60w139.tif` · 48,4 MB
- `hoehe/usgs_3dep_1/n60w139/USGS_1_n60w139.xml` · 12 kB
- `hoehe/usgs_3dep_1/n60w140/USGS_1_n60w140.jpg` · 5 kB
- `hoehe/usgs_3dep_1/n60w140/USGS_1_n60w140.tif` · 26,9 MB
- `hoehe/usgs_3dep_1/n60w140/USGS_1_n60w140.xml` · 12 kB

**`usgs_3dep_13`**

- `hoehe/usgs_3dep_13/n59w137/USGS_13_n59w137.jpg` · 14 kB
- `hoehe/usgs_3dep_13/n59w137/USGS_13_n59w137.tif` · 322,7 MB
- `hoehe/usgs_3dep_13/n59w137/USGS_13_n59w137.xml` · 12 kB
- `hoehe/usgs_3dep_13/n59w138/USGS_13_n59w138.jpg` · 7 kB
- `hoehe/usgs_3dep_13/n59w138/USGS_13_n59w138.tif` · 189,6 MB
- `hoehe/usgs_3dep_13/n59w138/USGS_13_n59w138.xml` · 12 kB
- `hoehe/usgs_3dep_13/n59w139/USGS_13_n59w139.jpg` · 922 B
- `hoehe/usgs_3dep_13/n59w139/USGS_13_n59w139.tif` · 7,5 MB
- `hoehe/usgs_3dep_13/n59w139/USGS_13_n59w139.xml` · 12 kB

**`usgs_3dep_2`**

- `hoehe/usgs_3dep_2/n58w136/USGS_2_n58w136.jpg` · 17 kB
- `hoehe/usgs_3dep_2/n58w136/USGS_2_n58w136.tif` · 13,1 MB
- `hoehe/usgs_3dep_2/n58w136/USGS_2_n58w136.xml` · 12 kB
- `hoehe/usgs_3dep_2/n58w137/USGS_2_n58w137.jpg` · 5 kB
- `hoehe/usgs_3dep_2/n58w137/USGS_2_n58w137.tif` · 2,6 MB
- `hoehe/usgs_3dep_2/n58w137/USGS_2_n58w137.xml` · 11 kB
- `hoehe/usgs_3dep_2/n59w136/USGS_2_n59w136.jpg` · 16 kB
- `hoehe/usgs_3dep_2/n59w136/USGS_2_n59w136.tif` · 11,6 MB
- `hoehe/usgs_3dep_2/n59w136/USGS_2_n59w136.xml` · 12 kB
- `hoehe/usgs_3dep_2/n59w137/USGS_2_n59w137.jpg` · 14 kB
- `hoehe/usgs_3dep_2/n59w137/USGS_2_n59w137.tif` · 10,9 MB
- `hoehe/usgs_3dep_2/n59w137/USGS_2_n59w137.xml` · 12 kB
- `hoehe/usgs_3dep_2/n59w138/USGS_2_n59w138.jpg` · 7 kB
- `hoehe/usgs_3dep_2/n59w138/USGS_2_n59w138.tif` · 6,3 MB
- `hoehe/usgs_3dep_2/n59w138/USGS_2_n59w138.xml` · 12 kB
- `hoehe/usgs_3dep_2/n59w139/USGS_2_n59w139.jpg` · 953 B
- `hoehe/usgs_3dep_2/n59w139/USGS_2_n59w139.tif` · 191 kB
- `hoehe/usgs_3dep_2/n59w139/USGS_2_n59w139.xml` · 12 kB
- `hoehe/usgs_3dep_2/n60w136/USGS_2_n60w136.jpg` · 18 kB
- `hoehe/usgs_3dep_2/n60w136/USGS_2_n60w136.tif` · 14,5 MB
- `hoehe/usgs_3dep_2/n60w136/USGS_2_n60w136.xml` · 11 kB
- `hoehe/usgs_3dep_2/n60w137/USGS_2_n60w137.jpg` · 15 kB
- `hoehe/usgs_3dep_2/n60w137/USGS_2_n60w137.tif` · 14,2 MB
- `hoehe/usgs_3dep_2/n60w137/USGS_2_n60w137.xml` · 12 kB
- `hoehe/usgs_3dep_2/n60w138/USGS_2_n60w138.jpg` · 13 kB
- `hoehe/usgs_3dep_2/n60w138/USGS_2_n60w138.tif` · 14,3 MB
- `hoehe/usgs_3dep_2/n60w138/USGS_2_n60w138.xml` · 12 kB
- `hoehe/usgs_3dep_2/n60w139/USGS_2_n60w139.jpg` · 12 kB
- `hoehe/usgs_3dep_2/n60w139/USGS_2_n60w139.tif` · 12,7 MB
- `hoehe/usgs_3dep_2/n60w139/USGS_2_n60w139.xml` · 12 kB
- `hoehe/usgs_3dep_2/n60w140/USGS_2_n60w140.jpg` · 5 kB
- `hoehe/usgs_3dep_2/n60w140/USGS_2_n60w140.tif` · 7,2 MB
- `hoehe/usgs_3dep_2/n60w140/USGS_2_n60w140.xml` · 12 kB

**`usgs_ifsar_dtm`**

- `hoehe/usgs_ifsar_dtm/GB_320/USGS_AK5M_Copper_River_and_Glacier_Bay_AK_Mid_Accuracy_DEM_AK_IFSAR-GB-320_2012.tif` · 189,0 MB
- `hoehe/usgs_ifsar_dtm/GB_336/USGS_AK5M_Copper_River_and_Glacier_Bay_AK_Mid_Accuracy_DEM_AK_IFSAR-GB-336_2012.tif` · 18,0 MB
- `hoehe/usgs_ifsar_dtm/GB_337/USGS_AK5M_Copper_River_and_Glacier_Bay_AK_Mid_Accuracy_DEM_AK_IFSAR-GB-337_2012.tif` · 410,1 MB
- `hoehe/usgs_ifsar_dtm/GB_338/USGS_AK5M_Copper_River_and_Glacier_Bay_AK_Mid_Accuracy_DEM_AK_IFSAR-GB-338_2012.tif` · 574,7 MB

**`usgs_lidar_glacierbay`**

- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02162.tif` · 1,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02163.tif` · 11,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02226.tif` · 1,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02227.tif` · 13,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02290.tif` · 311 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02291.tif` · 4,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02292.tif` · 14,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02356.tif` · 311 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02357.tif` · 2,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02358.tif` · 8,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02359.tif` · 10,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02360.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02361.tif` · 14,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02420.tif` · 312 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02421.tif` · 3,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02422.tif` · 13,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02423.tif` · 15,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02424.tif` · 15,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02425.tif` · 15,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02484.tif` · 3,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02485.tif` · 1,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02486.tif` · 8,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02487.tif` · 8,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02488.tif` · 5,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02544.tif` · 6,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02545.tif` · 9,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02546.tif` · 608 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02595.tif` · 5,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02596.tif` · 15,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02597.tif` · 9,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02639.tif` · 5,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02640.tif` · 13,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02641.tif` · 12,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02686.tif` · 313 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02687.tif` · 8,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02688.tif` · 15,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02729.tif` · 508 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02730.tif` · 14,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02731.tif` · 15,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02766.tif` · 308 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02767.tif` · 9,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02768.tif` · 16,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02807.tif` · 311 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02808.tif` · 6,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02809.tif` · 16,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02849.tif` · 3,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02850.tif` · 15,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02895.tif` · 2,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02896.tif` · 14,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02897.tif` · 16,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02948.tif` · 1,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02949.tif` · 13,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_02950.tif` · 15,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_03000.tif` · 11,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_5_2019/USGS_OPR_AK_GlacierBay_2019_B19_be_GB_03001.tif` · 15,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B2_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02547.tif` · 9 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B2_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02548.tif` · 15 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B2_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02599.tif` · 1,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B2_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02600.tif` · 2,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B2_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02644.tif` · 876 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B2_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02645.tif` · 899 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02042.tif` · 36 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02043.tif` · 6,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02044.tif` · 15,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02045.tif` · 14,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02046.tif` · 14,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02047.tif` · 14,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02048.tif` · 14,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02049.tif` · 14,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02050.tif` · 2,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02051.tif` · 13,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02052.tif` · 12,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02102.tif` · 1,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02103.tif` · 10,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02104.tif` · 14,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02105.tif` · 14,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02106.tif` · 14,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02107.tif` · 14,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02108.tif` · 14,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02109.tif` · 13,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02110.tif` · 13,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02111.tif` · 5,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02112.tif` · 12,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02113.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02164.tif` · 14,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02165.tif` · 14,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02166.tif` · 14,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02167.tif` · 13,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02168.tif` · 13,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02169.tif` · 13,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02170.tif` · 13,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02171.tif` · 13,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02172.tif` · 5,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02173.tif` · 11,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02174.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02228.tif` · 15,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02229.tif` · 14,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02230.tif` · 13,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02231.tif` · 14,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02232.tif` · 14,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02233.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02234.tif` · 12,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02235.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02236.tif` · 13,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02237.tif` · 5,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02238.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02239.tif` · 14,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02293.tif` · 15,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02294.tif` · 14,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02295.tif` · 14,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02296.tif` · 14,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02297.tif` · 13,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02298.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02299.tif` · 12,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02300.tif` · 13,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02301.tif` · 13,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02302.tif` · 14,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02303.tif` · 11,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02304.tif` · 13,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02305.tif` · 13,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02362.tif` · 15,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02363.tif` · 14,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02364.tif` · 13,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02365.tif` · 13,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02366.tif` · 13,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02367.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02368.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02369.tif` · 12,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02370.tif` · 13,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02371.tif` · 14,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02372.tif` · 13,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02373.tif` · 12,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02374.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02426.tif` · 14,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02427.tif` · 14,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02428.tif` · 14,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02429.tif` · 13,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02430.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02431.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02432.tif` · 13,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02433.tif` · 13,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02434.tif` · 12,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02435.tif` · 13,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02436.tif` · 15,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02437.tif` · 13,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02438.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02439.tif` · 11,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02489.tif` · 6,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02490.tif` · 11,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02491.tif` · 14,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02492.tif` · 13,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02493.tif` · 13,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02494.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02495.tif` · 13,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02496.tif` · 13,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02497.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02498.tif` · 13,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02499.tif` · 14,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02500.tif` · 13,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02501.tif` · 2,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02502.tif` · 1,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02503.tif` · 126 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02548.tif` · 16 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02549.tif` · 4,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02550.tif` · 13,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02551.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02552.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02553.tif` · 13,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02554.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02555.tif` · 14,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02556.tif` · 14,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02557.tif` · 14,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02558.tif` · 7,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02598.tif` · 836 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02601.tif` · 2,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02602.tif` · 9,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02603.tif` · 13,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02604.tif` · 13,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02605.tif` · 14,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02606.tif` · 15,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02607.tif` · 14,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02608.tif` · 13,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02609.tif` · 7,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02642.tif` · 14,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02643.tif` · 5,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02644.tif` · 722 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02645.tif` · 1,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02646.tif` · 23 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02647.tif` · 28 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02648.tif` · 7,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02649.tif` · 13,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02650.tif` · 15,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02651.tif` · 15,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02652.tif` · 14,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02653.tif` · 13,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02654.tif` · 3,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02689.tif` · 13,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02690.tif` · 8,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02691.tif` · 15,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02692.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02693.tif` · 15,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02694.tif` · 6,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02695.tif` · 434 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02696.tif` · 30 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02697.tif` · 122 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02698.tif` · 10,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02699.tif` · 16,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02700.tif` · 14,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02701.tif` · 13,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02702.tif` · 9,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02703.tif` · 10 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02732.tif` · 14,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02733.tif` · 14,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02734.tif` · 14,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02735.tif` · 14,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02736.tif` · 13,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02737.tif` · 13,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02738.tif` · 13,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02739.tif` · 10,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02740.tif` · 11,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02741.tif` · 10,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02742.tif` · 14,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02743.tif` · 13,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02744.tif` · 12,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02745.tif` · 3,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02769.tif` · 15,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02770.tif` · 14,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02771.tif` · 14,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02772.tif` · 14,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02773.tif` · 13,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02774.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02775.tif` · 13,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02776.tif` · 12,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02777.tif` · 13,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02778.tif` · 16,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02779.tif` · 14,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02780.tif` · 13,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02781.tif` · 13,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02782.tif` · 9,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02810.tif` · 15,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02811.tif` · 14,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02812.tif` · 14,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02813.tif` · 14,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02814.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02815.tif` · 13,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02816.tif` · 13,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02817.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02818.tif` · 13,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02819.tif` · 15,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02820.tif` · 15,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02821.tif` · 14,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02822.tif` · 12,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02823.tif` · 12,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02824.tif` · 2,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02851.tif` · 15,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02852.tif` · 15,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02853.tif` · 14,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02854.tif` · 13,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02855.tif` · 14,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02856.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02857.tif` · 13,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02858.tif` · 13,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02859.tif` · 13,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02860.tif` · 14,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02861.tif` · 15,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02862.tif` · 15,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02863.tif` · 14,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02864.tif` · 12,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02865.tif` · 8,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02898.tif` · 15,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02899.tif` · 14,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02900.tif` · 13,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02901.tif` · 12,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02902.tif` · 13,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02903.tif` · 13,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02904.tif` · 13,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02905.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02906.tif` · 13,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02907.tif` · 11,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02908.tif` · 14,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02909.tif` · 14,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02910.tif` · 12,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02911.tif` · 9,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02912.tif` · 706 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02951.tif` · 15,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02952.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02953.tif` · 14,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02954.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02955.tif` · 12,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02956.tif` · 12,9 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02957.tif` · 13,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02958.tif` · 13,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02959.tif` · 13,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02960.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02961.tif` · 1,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02962.tif` · 7,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02963.tif` · 12,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02964.tif` · 10,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_02965.tif` · 1,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03002.tif` · 15,0 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03003.tif` · 14,3 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03004.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03005.tif` · 13,4 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03006.tif` · 13,8 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03007.tif` · 13,6 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03008.tif` · 13,5 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03009.tif` · 13,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03010.tif` · 13,1 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03011.tif` · 13,7 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03012.tif` · 8,2 MB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03013.tif` · 294 kB
- `hoehe/usgs_lidar_glacierbay/AK_GlacierBay_B3_2019/USGS_OPR_AK_GlacierBay_2019_B19_BE_GB_03014.tif` · 1,1 MB

### karten

**`noaa_enc`**

- `karten/noaa_enc/US1GLBDC.zip` · 887 kB
- `karten/noaa_enc/US2PACYU.zip` · 215 kB
- `karten/noaa_enc/US2PACZU.zip` · 331 kB
- `karten/noaa_enc/US3AK1FW.zip` · 1,2 MB
- `karten/noaa_enc/US3AK1GU.zip` · 43 kB
- `karten/noaa_enc/US3AK1GV.zip` · 223 kB
- `karten/noaa_enc/US3AK1GW.zip` · 1,2 MB
- `karten/noaa_enc/US3AK1HU.zip` · 310 kB
- `karten/noaa_enc/US3AK1HV.zip` · 246 kB
- `karten/noaa_enc/US3AK1HW.zip` · 315 kB
- `karten/noaa_enc/US4AK5RF.zip` · 171 kB
- `karten/noaa_enc/US4AK5SD.zip` · 22 kB
- `karten/noaa_enc/US4AK5SE.zip` · 115 kB
- `karten/noaa_enc/US4AK5SF.zip` · 24 kB
- `karten/noaa_enc/US4AK5TD.zip` · 43 kB
- `karten/noaa_enc/US4AK5TE.zip` · 27 kB
- `karten/noaa_enc/US4AK5TF.zip` · 183 kB
- `karten/noaa_enc/US5AK1NB.zip` · 117 kB
- `karten/noaa_enc/US5AK1NC.zip` · 20 kB
- `karten/noaa_enc/US5AK1OB.zip` · 18 kB
- `karten/noaa_enc/US5AK1OC.zip` · 30 kB
- `karten/noaa_enc/US5AK1QE.zip` · 26 kB
- `karten/noaa_enc/US5AK1QG.zip` · 25 kB

**`usgs_topo_historisch`**

- `karten/usgs_topo_historisch/250000/Juneau_1951_1954_250000_360983.tif` · 10,3 MB
- `karten/usgs_topo_historisch/250000/Juneau_1951_1954_250000_360986.tif` · 10,0 MB
- `karten/usgs_topo_historisch/250000/Juneau_1951__250000_360984.tif` · 10,3 MB
- `karten/usgs_topo_historisch/250000/Juneau_1951__250000_360985.tif` · 9,6 MB
- `karten/usgs_topo_historisch/250000/Juneau_1962_1966_250000_360987.tif` · 10,5 MB
- `karten/usgs_topo_historisch/250000/Juneau_1962_1972_250000_360988.tif` · 11,8 MB
- `karten/usgs_topo_historisch/250000/Juneau_1962_1981_250000_360989.tif` · 11,4 MB
- `karten/usgs_topo_historisch/250000/Juneau_1962_1982_250000_360990.tif` · 12,3 MB
- `karten/usgs_topo_historisch/250000/Juneau_1962_1985_250000_360981.tif` · 13,0 MB
- `karten/usgs_topo_historisch/250000/Juneau_1962_1992_250000_360980.tif` · 12,9 MB
- `karten/usgs_topo_historisch/250000/Juneau_1962_1992_250000_360982.tif` · 12,4 MB
- `karten/usgs_topo_historisch/250000/Mt_Fairweather_1951_1953_250000_361174.tif` · 9,8 MB
- `karten/usgs_topo_historisch/250000/Mt_Fairweather_1951_1955_250000_361175.tif` · 10,4 MB
- `karten/usgs_topo_historisch/250000/Mt_Fairweather_1961_1966_250000_361179.tif` · 9,7 MB
- `karten/usgs_topo_historisch/250000/Mt_Fairweather_1961_1969_250000_361178.tif` · 10,8 MB
- `karten/usgs_topo_historisch/250000/Mt_Fairweather_1961_1979_250000_361177.tif` · 11,9 MB
- `karten/usgs_topo_historisch/250000/Mt_Fairweather_1961_1982_250000_361176.tif` · 12,7 MB
- `karten/usgs_topo_historisch/250000/Sitka_1951_1958_250000_361436.tif` · 13,2 MB
- `karten/usgs_topo_historisch/250000/Sitka_1951_1960_250000_361435.tif` · 12,4 MB
- `karten/usgs_topo_historisch/250000/Sitka_1951_1972_250000_361437.tif` · 13,8 MB
- `karten/usgs_topo_historisch/250000/Sitka_1951_1988_250000_361432.tif` · 16,0 MB
- `karten/usgs_topo_historisch/250000/Sitka_1951_1988_250000_361433.tif` · 16,0 MB
- `karten/usgs_topo_historisch/250000/Sitka_1951_1988_250000_361442.tif` · 15,5 MB
- `karten/usgs_topo_historisch/250000/Sitka_1952_1953_250000_361439.tif` · 13,9 MB
- `karten/usgs_topo_historisch/250000/Sitka_1952_1953_250000_361440.tif` · 12,8 MB
- `karten/usgs_topo_historisch/250000/Sitka_1952_1955_250000_361438.tif` · 13,7 MB
- `karten/usgs_topo_historisch/250000/Sitka_1952_1958_250000_361441.tif` · 11,9 MB
- `karten/usgs_topo_historisch/250000/Sitka_1964_1964_250000_464115.tif` · 15,5 MB
- `karten/usgs_topo_historisch/250000/Skagway_1952_1953_250000_361444.tif` · 14,4 MB
- `karten/usgs_topo_historisch/250000/Skagway_1952_1953_250000_361445.tif` · 16,1 MB
- `karten/usgs_topo_historisch/250000/Skagway_1952_1956_250000_361446.tif` · 16,2 MB
- `karten/usgs_topo_historisch/250000/Skagway_1952_1956_250000_361447.tif` · 15,1 MB
- `karten/usgs_topo_historisch/250000/Skagway_1961_1967_250000_361448.tif` · 16,4 MB
- `karten/usgs_topo_historisch/250000/Skagway_1961_1974_250000_361449.tif` · 16,1 MB
- `karten/usgs_topo_historisch/250000/Skagway_1961_1982_250000_361450.tif` · 16,3 MB
- `karten/usgs_topo_historisch/250000/Skagway_1961_1993_250000_361451.tif` · 17,8 MB
- `karten/usgs_topo_historisch/250000/Yakutat_1951_1952_250000_361680.tif` · 9,7 MB
- `karten/usgs_topo_historisch/250000/Yakutat_1951_1952_250000_708046.tif` · 11,0 MB
- `karten/usgs_topo_historisch/250000/Yakutat_1951_1954_250000_361681.tif` · 10,6 MB
- `karten/usgs_topo_historisch/250000/Yakutat_1951_1954_250000_464292.tif` · 8,7 MB
- `karten/usgs_topo_historisch/250000/Yakutat_1959_1966_250000_361675.tif` · 10,0 MB
- `karten/usgs_topo_historisch/250000/Yakutat_1959_1972_250000_361676.tif` · 11,3 MB
- `karten/usgs_topo_historisch/250000/Yakutat_1959_1982_250000_361677.tif` · 13,5 MB
- `karten/usgs_topo_historisch/250000/Yakutat_1959_1982_250000_707911.tif` · 15,1 MB
- `karten/usgs_topo_historisch/250000/vorschau/Juneau_1951_1954_250000_360983.jpg` · 23 kB
- `karten/usgs_topo_historisch/250000/vorschau/Juneau_1951_1954_250000_360986.jpg` · 19 kB
- `karten/usgs_topo_historisch/250000/vorschau/Juneau_1951__250000_360984.jpg` · 23 kB
- `karten/usgs_topo_historisch/250000/vorschau/Juneau_1951__250000_360985.jpg` · 19 kB
- `karten/usgs_topo_historisch/250000/vorschau/Juneau_1962_1966_250000_360987.jpg` · 23 kB
- `karten/usgs_topo_historisch/250000/vorschau/Juneau_1962_1972_250000_360988.jpg` · 24 kB
- `karten/usgs_topo_historisch/250000/vorschau/Juneau_1962_1981_250000_360989.jpg` · 24 kB
- `karten/usgs_topo_historisch/250000/vorschau/Juneau_1962_1982_250000_360990.jpg` · 24 kB
- `karten/usgs_topo_historisch/250000/vorschau/Juneau_1962_1985_250000_360981.jpg` · 24 kB
- `karten/usgs_topo_historisch/250000/vorschau/Juneau_1962_1992_250000_360980.jpg` · 24 kB
- `karten/usgs_topo_historisch/250000/vorschau/Juneau_1962_1992_250000_360982.jpg` · 24 kB
- `karten/usgs_topo_historisch/250000/vorschau/Mt_Fairweather_1951_1953_250000_361174.jpg` · 15 kB
- `karten/usgs_topo_historisch/250000/vorschau/Mt_Fairweather_1951_1955_250000_361175.jpg` · 17 kB
- `karten/usgs_topo_historisch/250000/vorschau/Mt_Fairweather_1961_1966_250000_361179.jpg` · 16 kB
- `karten/usgs_topo_historisch/250000/vorschau/Mt_Fairweather_1961_1969_250000_361178.jpg` · 14 kB
- `karten/usgs_topo_historisch/250000/vorschau/Mt_Fairweather_1961_1979_250000_361177.jpg` · 13 kB
- `karten/usgs_topo_historisch/250000/vorschau/Mt_Fairweather_1961_1982_250000_361176.jpg` · 15 kB
- `karten/usgs_topo_historisch/250000/vorschau/Sitka_1951_1958_250000_361436.jpg` · 20 kB
- `karten/usgs_topo_historisch/250000/vorschau/Sitka_1951_1960_250000_361435.jpg` · 20 kB
- `karten/usgs_topo_historisch/250000/vorschau/Sitka_1951_1972_250000_361437.jpg` · 18 kB
- `karten/usgs_topo_historisch/250000/vorschau/Sitka_1951_1988_250000_361432.jpg` · 18 kB
- `karten/usgs_topo_historisch/250000/vorschau/Sitka_1951_1988_250000_361433.jpg` · 18 kB
- `karten/usgs_topo_historisch/250000/vorschau/Sitka_1951_1988_250000_361442.jpg` · 19 kB
- `karten/usgs_topo_historisch/250000/vorschau/Sitka_1952_1953_250000_361439.jpg` · 17 kB
- `karten/usgs_topo_historisch/250000/vorschau/Sitka_1952_1953_250000_361440.jpg` · 14 kB
- `karten/usgs_topo_historisch/250000/vorschau/Sitka_1952_1955_250000_361438.jpg` · 17 kB
- `karten/usgs_topo_historisch/250000/vorschau/Sitka_1952_1958_250000_361441.jpg` · 14 kB
- `karten/usgs_topo_historisch/250000/vorschau/Sitka_1964_1964_250000_464115.jpg` · 11 kB
- `karten/usgs_topo_historisch/250000/vorschau/Skagway_1952_1953_250000_361444.jpg` · 17 kB
- `karten/usgs_topo_historisch/250000/vorschau/Skagway_1952_1953_250000_361445.jpg` · 20 kB
- `karten/usgs_topo_historisch/250000/vorschau/Skagway_1952_1956_250000_361446.jpg` · 20 kB
- `karten/usgs_topo_historisch/250000/vorschau/Skagway_1952_1956_250000_361447.jpg` · 17 kB
- `karten/usgs_topo_historisch/250000/vorschau/Skagway_1961_1967_250000_361448.jpg` · 17 kB
- `karten/usgs_topo_historisch/250000/vorschau/Skagway_1961_1974_250000_361449.jpg` · 16 kB
- `karten/usgs_topo_historisch/250000/vorschau/Skagway_1961_1982_250000_361450.jpg` · 16 kB
- `karten/usgs_topo_historisch/250000/vorschau/Skagway_1961_1993_250000_361451.jpg` · 16 kB
- `karten/usgs_topo_historisch/250000/vorschau/Yakutat_1951_1952_250000_361680.jpg` · 14 kB
- `karten/usgs_topo_historisch/250000/vorschau/Yakutat_1951_1952_250000_708046.jpg` · 15 kB
- `karten/usgs_topo_historisch/250000/vorschau/Yakutat_1951_1954_250000_361681.jpg` · 15 kB
- `karten/usgs_topo_historisch/250000/vorschau/Yakutat_1951_1954_250000_464292.jpg` · 8 kB
- `karten/usgs_topo_historisch/250000/vorschau/Yakutat_1959_1966_250000_361675.jpg` · 14 kB
- `karten/usgs_topo_historisch/250000/vorschau/Yakutat_1959_1972_250000_361676.jpg` · 13 kB
- `karten/usgs_topo_historisch/250000/vorschau/Yakutat_1959_1982_250000_361677.jpg` · 15 kB
- `karten/usgs_topo_historisch/250000/vorschau/Yakutat_1959_1982_250000_707911.jpg` · 16 kB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_B-3_1961_1966_63360_357587.tif` · 4,2 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_B-3_1961_1978_63360_357586.tif` · 5,1 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_C-3_1961_1966_63360_357595.tif` · 3,9 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_C-4_1961_1966_63360_357596.tif` · 6,2 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_C-5_1961_1966_63360_357597.tif` · 5,9 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_C-5_1961_1977_63360_357598.tif` · 6,2 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_C-6_1961_1966_63360_357599.tif` · 2,7 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_C-6_1961_1975_63360_357600.tif` · 2,9 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_D-3_1961_1966_63360_357607.tif` · 5,6 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_D-3_1961_1972_63360_357608.tif` · 6,8 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_D-4_1961_1966_63360_357609.tif` · 7,1 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_D-4_1961_1977_63360_357610.tif` · 7,9 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_D-5_1961_1966_63360_357612.tif` · 6,7 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_D-5_1961_1975_63360_357611.tif` · 4,9 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_D-6_1961_1966_63360_357613.tif` · 6,8 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_D-6_1961_1977_63360_357614.tif` · 5,9 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_D-7_1961_1965_63360_357616.tif` · 2,7 MB
- `karten/usgs_topo_historisch/63360/Mount_Fairweather_D-7_1961_1978_63360_357615.tif` · 4,7 MB
- `karten/usgs_topo_historisch/63360/Mt_Fairweather_B-4_1961_1966_63360_357588.tif` · 3,6 MB
- `karten/usgs_topo_historisch/63360/Mt_Fairweather_B-4_1961_1979_63360_357589.tif` · 4,5 MB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_B-3_1961_1966_63360_357587.jpg` · 14 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_B-3_1961_1978_63360_357586.jpg` · 19 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_C-3_1961_1966_63360_357595.jpg` · 14 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_C-4_1961_1966_63360_357596.jpg` · 19 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_C-5_1961_1966_63360_357597.jpg` · 21 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_C-5_1961_1977_63360_357598.jpg` · 16 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_C-6_1961_1966_63360_357599.jpg` · 13 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_C-6_1961_1975_63360_357600.jpg` · 10 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_D-3_1961_1966_63360_357607.jpg` · 19 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_D-3_1961_1972_63360_357608.jpg` · 19 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_D-4_1961_1966_63360_357609.jpg` · 20 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_D-4_1961_1977_63360_357610.jpg` · 21 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_D-5_1961_1966_63360_357612.jpg` · 19 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_D-5_1961_1975_63360_357611.jpg` · 16 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_D-6_1961_1966_63360_357613.jpg` · 22 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_D-6_1961_1977_63360_357614.jpg` · 16 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_D-7_1961_1965_63360_357616.jpg` · 12 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mount_Fairweather_D-7_1961_1978_63360_357615.jpg` · 9 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mt_Fairweather_B-4_1961_1966_63360_357588.jpg` · 15 kB
- `karten/usgs_topo_historisch/63360/vorschau/Mt_Fairweather_B-4_1961_1979_63360_357589.jpg` · 11 kB
- `karten/usgs_topo_historisch/karten.csv` · 22 kB

**`usgs_ustopo`**

- `karten/usgs_ustopo/AK_Mount_Fairweather_B-3_NW.pdf` · 22,8 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_B-4_NE.pdf` · 20,4 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_B-4_NW.pdf` · 14,4 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_B-4_OE_W_NE.pdf` · 6,4 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-3_NW.pdf` · 15,1 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-3_SW.pdf` · 14,6 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-4_NE.pdf` · 18,5 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-4_NW.pdf` · 19,8 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-4_SE.pdf` · 16,3 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-4_SW.pdf` · 19,5 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-5_NE.pdf` · 20,2 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-5_NW.pdf` · 17,9 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-5_SE.pdf` · 18,9 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-5_SW.pdf` · 12,4 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-6_NE.pdf` · 15,6 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-6_NW.pdf` · 6,3 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_C-6_SE.pdf` · 6,1 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-3_NW.pdf` · 20,7 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-3_SW.pdf` · 18,3 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-4_NE.pdf` · 22,7 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-4_NW.pdf` · 19,4 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-4_SE.pdf` · 21,9 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-4_SW.pdf` · 21,8 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-5_NE.pdf` · 13,5 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-5_NW.pdf` · 18,3 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-5_SE.pdf` · 21,1 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-5_SW.pdf` · 20,4 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-6_NE.pdf` · 18,1 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-6_NW.pdf` · 19,0 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-6_SE.pdf` · 19,3 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-6_SW.pdf` · 17,4 MB
- `karten/usgs_ustopo/AK_Mount_Fairweather_D-7_NE.pdf` · 13,3 MB

### klima

**`gezeiten`**

- `klima/gezeiten/bezugshoehen_elfin_cove.json` · 2 kB
- `klima/gezeiten/boje_46083_aktuell.txt` · 607 kB
- `klima/gezeiten/gezeiten_lituya_juli1958.json` · 2 kB
- `klima/gezeiten/gezeiten_lituya_juli2026.json` · 99 B

### luftbild

**`landsat_suche`**

- `luftbild/landsat_suche/landsat_c2_l2.json` · 22,9 MB
- `luftbild/landsat_suche/landsat_mss_1972-2013.json` · 8,8 MB

**`sentinel2`**

- `luftbild/sentinel2/S2A_8VLK_20180929_1_L2A/S2A_8VLK_20180929_1_L2A.json` · 23 kB
- `luftbild/sentinel2/S2A_8VLK_20180929_1_L2A/SCL.tif` · 636 kB
- `luftbild/sentinel2/S2A_8VLK_20180929_1_L2A/TCI.tif` · 194,3 MB
- `luftbild/sentinel2/S2A_8VLK_20200730_1_L2A/S2A_8VLK_20200730_1_L2A.json` · 23 kB
- `luftbild/sentinel2/S2A_8VLK_20200730_1_L2A/SCL.tif` · 577 kB
- `luftbild/sentinel2/S2A_8VLK_20200730_1_L2A/TCI.tif` · 161,6 MB
- `luftbild/sentinel2/S2A_8VLK_20200730_1_L2A/thumbnail.jpg` · 47 kB
- `luftbild/sentinel2/S2A_8VLL_20190927_1_L2A/S2A_8VLL_20190927_1_L2A.json` · 23 kB
- `luftbild/sentinel2/S2A_8VLL_20190927_1_L2A/SCL.tif` · 2,6 MB
- `luftbild/sentinel2/S2A_8VLL_20190927_1_L2A/TCI.tif` · 194,4 MB
- `luftbild/sentinel2/S2A_8VLL_20210608_0_L2A/S2A_8VLL_20210608_0_L2A.json` · 23 kB
- `luftbild/sentinel2/S2A_8VLL_20210608_0_L2A/SCL.tif` · 2,4 MB
- `luftbild/sentinel2/S2A_8VLL_20210608_0_L2A/TCI.tif` · 144,4 MB
- `luftbild/sentinel2/S2A_8VLL_20210608_0_L2A/thumbnail.jpg` · 122 kB
- `luftbild/sentinel2/S2A_8VLL_20230728_0_L2A/S2A_8VLL_20230728_0_L2A.json` · 23 kB
- `luftbild/sentinel2/S2A_8VLL_20230728_0_L2A/SCL.tif` · 2,5 MB
- `luftbild/sentinel2/S2A_8VLL_20230728_0_L2A/TCI.tif` · 221,3 MB
- `luftbild/sentinel2/S2A_8VLL_20230728_0_L2A/thumbnail.jpg` · 134 kB
- `luftbild/sentinel2/S2B_8VLK_20260902_0_L2A/S2B_8VLK_20260902_0_L2A.json` · 22 kB
- `luftbild/sentinel2/S2B_8VLK_20260902_0_L2A/SCL.tif` · 567 kB
- `luftbild/sentinel2/S2B_8VLK_20260902_0_L2A/TCI.tif` · 175,5 MB
- `luftbild/sentinel2/szenen.csv` · 133 kB

### namen

**`gnis_namen`**

- `namen/gnis_namen/DomesticNames_AK_Text.zip` · 1,5 MB
- `namen/gnis_namen/GNIS_file_format.pdf` · 284 kB
- `namen/gnis_namen/gnis_szene.geojson` · 42 kB

**`nps_grenzen`**

- `namen/nps_grenzen/glacier_bay_wilderness.geojson` · 4,2 MB
- `namen/nps_grenzen/nps_glba_grenze.geojson` · 266 kB

### texturen

**`tex_eis`**

- `texturen/tex_eis/Ice003_2K-JPG.zip` · 15,6 MB

**`tex_firn`**

- `texturen/tex_firn/Snow014_2K-JPG.zip` · 25,2 MB

**`tex_holz_entrindet`**

- `texturen/tex_holz_entrindet/Wood035_2K-JPG.zip` · 29,0 MB

**`tex_kutter_holz`**

- `texturen/tex_kutter_holz/PaintedWood009C_2K-JPG.zip` · 28,0 MB

**`tex_kutter_tau`**

- `texturen/tex_kutter_tau/Rope001_2K-JPG.zip` · 12,2 MB

**`tex_moos`**

- `texturen/tex_moos/Moss002_2K-JPG.zip` · 39,6 MB

**`tex_moraene`**

- `texturen/tex_moraene/Ground108_2K-JPG.zip` · 32,5 MB

**`tex_rinde`**

- `texturen/tex_rinde/Bark014_2K-JPG.zip` · 29,8 MB

**`tex_sand`**

- `texturen/tex_sand/Ground054_2K-JPG.zip` · 33,0 MB

**`tex_schiefer_frisch`**

- `texturen/tex_schiefer_frisch/Rock058_2K-JPG.zip` · 27,1 MB

**`tex_schiefer_verwittert`**

- `texturen/tex_schiefer_verwittert/Rock063_2K-JPG.zip` · 14,9 MB

**`tex_strandkies`**

- `texturen/tex_strandkies/Gravel041_2K-JPG.zip` · 32,0 MB

**`tex_waldboden`**

- `texturen/tex_waldboden/Ground037_2K-JPG.zip` · 37,9 MB

### vegetation

**`esa_worldcover`**

- `vegetation/esa_worldcover/ESA_WorldCover_10m_2021_v200_N57W138_Map.tif` · 51,6 MB
- `vegetation/esa_worldcover/ESA_WorldCover_10m_2021_v200_N57W141_Map.tif` · 12,9 MB

### vektor

**`natural_earth`**

- `vektor/natural_earth/ne_10m_admin_1_states_provinces.zip` · 14,9 MB
- `vektor/natural_earth/ne_10m_coastline.zip` · 3,1 MB
- `vektor/natural_earth/ne_10m_glaciated_areas.zip` · 1,6 MB
- `vektor/natural_earth/ne_10m_land.zip` · 3,3 MB
- `vektor/natural_earth/ne_10m_minor_islands.zip` · 315 kB
- `vektor/natural_earth/ne_10m_populated_places.zip` · 2,8 MB

**`osm`**

- `vektor/osm/osm_overpass.json` · 38,5 MB
