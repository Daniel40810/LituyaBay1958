# LituyaBay1958 Werkbuch

**Das Projekt der Riesenwelle und des Netzes.** Lituya Bay, Alaska, 9. Juli 1958, 22:16 Uhr Ortszeit.

> **Stand: 01.11.2026. Fragen F1–F43 beantwortet: alle A (Entscheidung vom 01.11.2026, Abschnitt 16). Phase 1 (Gerüst) erledigt; Phase 2 (Aufbereitung): Werkzeuge fertig, Volllauf mit `ant aufbereitung` auf dem Rechner steht aus; Phase 0b (Recherche) läuft parallel.**
> Erstellt aus `docs/claude/LITUYABAY1958_WERKBUCH_PROMPT.md`. Das Werkbuch wird über das ganze Projekt fortgeschrieben (Abschnitt 19). Jede Zahl ist **belegt** (Quelle in Abschnitt 2.9), **gemessen** (aus den Daten in `download/`), **gerechnet** (hier hergeleitet, Formel in Abschnitt 9) oder **gestaltet [G]**. Was noch keine Quelle hat, trägt **[zu prüfen]**.
>
> Vorlage: Sylt Werkbuch (Projekt der Brandung) und Timmendorf (Projekt des Segels). Geschwisterdateien: `Kartenmaterial.md` (48 Quellen), `download/INVENTAR.md` (zweiter Lauf: 1 227 Dateien, 13,5 GB), `geodaten/gebiete.geojson`, `geodaten/namen_szene.geojson`.

---

## Inhalt

1. Auftrag und Leitidee
2. Recherche und was daraus folgt
3. Daten: Gebiete, Kartenmaterial, Aufbereitung
4. Grundsatzentscheidungen
5. Neue Packages
6. Optimierung bestehender Engines
7. Die Netze (Oracle Network Data Model)
8. Texturen
9. Physik in Kurzform
10. Die Szene
11. Regie
12. Oberfläche
13. Datenbank
14. Phasenplan
15. Leistungsbudget und Risiken
16. Offene Fragen
17. Ideenspeicher
18. Rückfluss
19. Fortschritt

---

## 1. Auftrag und Leitidee

Eine animierte 3D-Echtzeitszene der Lituya Bay am Fuß der Fairweather-Kette. Das Beben an der Fairweather-Störung löst 1 bis 2½ Minuten nach dem ersten Stoß einen Felssturz von 30,6 Mio. m³ in den Gilbert Inlet aus. Der Fels trifft mit rund 110 m/s auf das Wasser, die Welle wird 162 m hoch, läuft am Sporn gegenüber bis 524 m hinauf, die höchste je gemessene Auflaufhöhe, rasiert auf 10 km² den Wald von den Ufern und läuft die 12 km lange Bucht hinab bis zum Eingang, wo drei Fischkutter vor Anker liegen. Die Szene zeigt die Bucht **vor**, **während** und **nach** der Welle und **heute** (Zeitstände, Abschnitt 10.1).

Drei Säulen, gleichrangig:

1. **Die Ereigniskette als Physik:** Beben → Hangversagen → Felssturz → Eintauchen → Impulswelle → Auflauf → Wellenwurf → Treibgut → Wiederbewaldung. Jedes Glied ist ein eigenes Package im Renderer (Abschnitt 5), geprüft gegen Laborversuche und Belege (Abschnitt 9).
2. **Das Oracle Network Data Model, ausgereizt:** sieben Netze der Natur und des Ereignisses (Abschnitt 7). Jedes beantwortet eine Frage, die man in der Szene sieht, und wird gegen eine zweite Rechnung oder einen Beleg geprüft.
3. **Das Bild, episch:** Mt Fairweather (4 671 m) steigt in 24 km von null auf; die Welle ist höher als jedes Gebäude Europas; die Julinacht ist hell. Gelände bis 0,5 m aus dem Lidar, Fernsicht bis zum Horizont, echte Texturen, Wald in Millionen Bäumen, Kino mit Zeitlupe und Unterwasserblick.

### 1.1 Was „episch“ heißt (Leitplanken)

- **Maßstab zeigen:** Menschen, Boote und Bäume als Maßstab im Bild; atmosphärische Perspektive über 60 km; Erdkrümmung am Horizont. Die 524 m sind beeindruckend genug: episch durch Physik und Belege, nicht durch Übertreibung.
- **Zeit dehnen:** Die ersten zehn Minuten nach dem Beben in Echtzeit, in Zeitlupe bis 1:20 und im Zeitraffer; dazu 68 Jahre Wiederbewaldung in einer Minute.
- **Alle Sinne:** Grollen des Bebens, Donner des Sturzes (der Schall braucht 30 s bis zur Anchorage Cove), Brüllen der Welle, brechender Wald; Alpenglühen am Fairweather, Staub im Gegenlicht; Gletschermilch, Gischt, Unterwasser.
- **Wahr bleiben:** Was gestaltet ist, trägt [G], im Werkbuch und im Code.

---

## 2. Recherche und was daraus folgt

### 2.1 Das Ereignis in Zahlen

| Größe | Wert | Art | Quelle |
|---|---|---|---|
| Beben, Zeit | 9. Juli 1958, 22:16 Ortszeit (= 10.07. 06:16 UTC) | belegt | FRITZ01 (S. 6), COMCAT |
| Beben, Magnitude | 8,3 (Richter, 1959); Mw 7,8 (ISC-GEM) | belegt | FRITZ01, COMCAT |
| Beben, Versatz an der Fairweather-Störung | bis 6,4 m horizontal, 1 m vertikal; SW-Seite nach NW und hoch | belegt | TOCHER59 über FRITZ01 |
| Dauer des starken Schüttelns | 1 bis 4 min (zwei Zeugen auf Booten) | belegt | FRITZ01 |
| Zeit bis zum Felssturz | 1 bis 2½ min nach dem ersten Stoß | belegt | FRITZ01 |
| Felssturz, Ort | NE-Wand des Gilbert Inlet, Hang im Mittel 40°, bis 915 m | belegt | MILLER60 über FRITZ01 |
| Felssturz, Gestein | Amphibol- und Biotitschiefer, ρ ≈ 2,7 t/m³ | belegt | FRITZ01 |
| Felssturz, Maße | Breite 732–915 m (Mittel 823 m), Länge am Hang 970 m, Dicke bis 92 m, Schwerpunkt 610 m | belegt | MILLER60, SLINGERLAND79 über FRITZ01 |
| Felssturz, Volumen | 30,6 · 10⁶ m³ (≈ 83 Mio. t) | belegt | MILLER60 |
| Aufschlaggeschwindigkeit | 110 m/s (freier Fall vom Schwerpunkt, obere Grenze) | belegt/gerechnet | FRITZ01; Abschnitt 9.2 |
| Froude-Zahl des Aufschlags | F = 3,18 bei h = 122 m | belegt | FRITZ01 |
| Gilbert Inlet | Breite 1 342 m, Tiefe 122 m, beide Hänge ≈ 45° | belegt | FRITZ01 |
| Welle im Inlet (Modell 1:675) | Amplitude 152 m bei x = 885 m nach 16 s, Höhe 162 m, H/h = 1,33; über 200 m bei x = 600 m nach 11 s | belegt | FRITZ01 |
| Auflauf am Sporn | 524 m (Natur), 526 m (Modell, nach 35 s); überspült bis 550 m | belegt | MILLER60, FRITZ01 |
| Periode der Folgewellen im Inlet | 76 bis 91 s | belegt | FRITZ01 |
| Spurlinie Südufer | 208 m bei Mudslide Creek | belegt | MILLER60 |
| Zerstörter Wald | rund 10 km² zwischen Spurlinie und Hochwasserlinie; bei Fish Lake 1 100 m landeinwärts | belegt | FRITZ01 |
| Gletscherfront | bis 400 m Eis abgeschert, Deltas fortgeschoben; danach senkrechte Wand quer zum Inlet | belegt | FRITZ01 |
| Bucht | Stamm 12 km, 1,2–3,3 km breit, Eingang 300 m, Tiefe bis 220 m, Eingang 10 m; U-Trog | belegt | FRITZ01 |
| Sonne zur Bebenzeit | 1° unter dem Horizont, 2 min nach Sonnenuntergang (22:14; der Entwurf nannte 22:04, in Phase 1 nachgerechnet mit `scene.Sun`, Prüfung 2.1); bürgerliche Dämmerung, Himmel hell; tiefster Stand der Nacht −9° | gerechnet | Abschnitt 9.10 |
| Tide zur Bebenzeit | [zu prüfen] aus `download/klima/gezeiten/gezeiten_lituya_juli1958.json` | gemessen (Vorausberechnung) | COOPS |
| Wetter | [zu prüfen] aus ERA5 (`--gross`) und Zeugen; Zeugen berichten ruhige See [zu prüfen] | | ERA5, MILLER60 |

### 2.2 Zeitleiste der Nacht (Simulationszeit t in Sekunden ab dem ersten Stoß)

| t | Ereignis | Art |
|---|---|---|
| 0 | erster Stoß; Schütteln beginnt, hält 60 bis 240 s | belegt |
| 60 … 150 | Felssturz löst sich an der NE-Wand des Gilbert Inlet | belegt |
| +0 … 7 | Eintauchen (Gleitdauer am Wasser ≈ 6,8 s im Modell) | belegt |
| +11 | Amplitude über 200 m bei 600 m vom Aufschlag | belegt |
| +16 | 152 m bei 885 m | belegt |
| +23 … 35 | Auflauf am Sporn gegenüber bis 524 m, Höchststand nach 35 s | belegt |
| +35 … 240 | Nachschwingen im Inlet, Reflexionen (Zeugen: „jumping and shaking“) | belegt |
| +150 … 400 | Front läuft die Bucht hinab; Cenotaph Island; Südufer bis 208 m | gerechnet (7.1, 9.4) |
| +300 … 600 | Front am Eingang, die drei Boote; Badger über die Nehrung | gerechnet, Zeugen [zu prüfen] |
| +600 … 3 600 | Seiche der Bucht (Grundperiode ≈ 11 min), Treibholz sammelt sich | gerechnet [G] |
| Tage | Holzteppiche treiben aus, Staub setzt sich | gestaltet [G] |
| Jahre bis 2026 | Wiederbewaldung, Gletscherrückzug | gemessen (DSM 2012, ArcticDEM 2013–2022, Lidar 2019) |

Die gerechneten Zeiten sind Vorgaben für Phase 13a, wo `wave`, `LB_NET_WELLE` und die Zeugen gegeneinander gehalten werden.

### 2.3 Die drei Boote

| Boot | Besatzung | Lage | Schicksal | Art |
|---|---|---|---|---|
| Edrie | Howard Ulrich mit Sohn | kleine Bucht am **Süd**ufer, rund 1 Meile innerhalb des Eingangs, vor Anker in „about 5 fathoms“, rund 40 Faden Kette gesteckt, der Anker saß fest | Front „50 to 75 feet high“; „The anchor chain snapped as the boat rose with the wave“; zum Südufer und wohl darüber getragen, im Rückstrom zur Mitte der Bucht; um 23:00 hinaus | belegt (MILLER60) |
| Badger | Bill und Vivian Swanson | Anchorage Cove am **Nord**ufer beim Eingang, vor Anker in „about 4 fathoms“ neben der Sunmore | Heck voran unter dem Kamm „like a surfboard“ über La Chaussee Spit getragen, „about 2 boat lengths (more than 80 feet)“ über den Bäumen; draußen auf Grund gestoßen und gesunken; im Beiboot nach rund 2 Stunden gerettet | belegt (MILLER60) |
| Sunmore | Orville und Mickey Wagner | erst vor Anker neben der Badger, dann in Fahrt zum Eingang | am Eingang von der Welle überrollt, verloren | belegt (MILLER60) |

Bauart: alle drei Troller, „about 40 feet long“ (MILLER60). **Berichtigt in Phase 15:** Die Edrie lag nicht in Anchorage Cove, sondern in einer kleinen Bucht am Südufer; in Anchorage Cove ankerten Badger und Sunmore. Unbelegt und gestaltet [G] (in `vessel.Kutter`): Rumpfform, Breite 3,6–3,8 m, Tiefgang 1,55–1,65 m mit Kiel, Masse 21–25 t, GM 1,0 m, Kette 3/8″ Güte 30 (Bruchlast 42 kN), Maschine 9 kN Schub. Quellen: [MILLER60 über stevedutch.net](https://stevedutch.net/lituyabay/lituya1.htm), [Proceedings 1960](https://www.usni.org/magazines/proceedings/1960/may/day-bay-ran-over).

### 2.4 Frühere Wellen

| Jahr | Auflauf | Ursache (Miller) | Art |
|---|---|---|---|
| 1853 oder 1854 | 120 m, Nordufer gegenüber Mudslide Creek | Felssturz von der Südwand | belegt (FRITZ01 nach MILLER60) |
| 1874 | [zu prüfen] | | |
| 1899 | [zu prüfen] (Beben Yakutat Bay) | | |
| 1936 | 150 m, NE-Wand des Crillon Inlet; drei Wellen wachsender Höhe | Felssturz vom SW-Ufer des Crillon Inlet (unbestätigt) | belegt |
| 1958 | 524 m | Felssturz Gilbert Inlet | belegt |

Vier, wahrscheinlich fünf Riesenwellen in zwei Jahrhunderten: Die Bucht ist der Ort der Riesenwellen. Das Regiebuch „Die Bucht der Wellen“ (Abschnitt 11.3) und das Ufer-LRS (7.2) tragen alle Spurlinien.

### 2.5 Vergleichsfälle

| Ort | Jahr | Auflauf | Bemerkung |
|---|---|---|---|
| Loen, Norwegen | 1936 | 75 m | höchster Auflauf in Norwegen |
| Vajont, Italien | 1963 | 270 m über dem Stausee, 245 m über der Dammkrone | 0,24 km³, fast waagerecht mit bis 30 m/s |
| Spirit Lake, Mt. St. Helens | 1980 | 260 m | Lawine mit 70–80 m/s |
| Lituya Bay | 1958 | 524 m | siebenmal Loen |

Alle belegt über FRITZ01. Sie sind die Grenzfälle für `impact` und `wave` (Abschnitt 9.4).

### 2.6 Bucht, Grund, Wald, Gletscher

- **Grund 1958:** Vermessungen H06581–83 (1940) sind der Stand vor der Welle, H08492 (Juli/August 1959) der Stand danach; beide liegen in `download/bathymetrie/nos_vermessungen/` (57 Dateien). Franco et al. 2020 haben daraus die Bathymetrie vor 1958 rekonstruiert (FRANCO20, zum Abgleich). Heute: BlueTopo (mit H14228, 2025) und USACE-Topobathy 2019.
- **Wald:** Sitka-Fichte (*Picea sitchensis*) und Westliche Hemlocktanne (*Tsuga heterophylla*) als Hauptarten, Erle an Ufern und im Vorfeld [zu prüfen]; Höhen aus dem IfSAR-DSM 2012 (`--gross`) und dem Lidar 2019 (Boden) gegen ArcticDEM (Oberfläche). Miller: Bäume gebrochen, entrindet und bis 10 m über die Spurlinie geschleudert (FRITZ01, S. 14).
- **Gletscher:** Lituya-, Nord-Crillon- und Cascade-Gletscher sind Reste des Talgletschers, der die Bucht geformt hat; Fronten 1958 aus der Karte 1951 (Luftbilder 1948) und C-5 (Luftbilder 1954), heute aus Lidar und ITS_LIVE.
- **Die Fairweather-Störung** bildet den Graben, dessen Teil die beiden Arme am Kopf der Bucht sind (FRITZ01, S. 4); Verlauf aus `download/geologie/stoerungen/`.

### 2.7 Tiere, Menschen, Namen

- Küstenbraunbär, Seehund, Seeotter, Möwen, Weißkopfseeadler [zu prüfen, NPS].
- Tlingit: Mount Fairweather = Tsalxaan; „Lituya“ = See in der Landspitze (NPS „Tlingit Place Names of Huna Káawu“, Kartenmaterial `nps_karten`).
- 94 amtliche Namen in der Szene, 42 im Kern (`geodaten/namen_szene.geojson`).

### 2.8 NDM-Funktionsprobe (Phase 3)

Vor dem ersten Netz wird gegen die installierte Datenbank geprüft, je mit einem Block SQL (`db/netz/03_probe.sql`) oder Java (`netz.NdmProbeKern` mit LOD im eigenen Klassenlader), Ergebnis in die Tabelle `LB_NDM_PROBE` (Sicht `LB_V_NDM_PROBE`) und hierher. `ant netzdb` legt dafür Probenetze `LB_PROBE_*` an und räumt sie danach weg (`98_probe_abbau.sql`). Probenetz `LB_PROBE_RAUM` (UTM 8 N, mit Höhe): Quadrat 1–2–3–4 mit Kanten zu 1000 m, Diagonale 1–3 (Kante 5) zu 1414,2 m, getrennt davon 6–7 zu 500 m, alle in beide Richtungen. Die Erwartungswerte prüft `ant pruefung` vorab an demselben Netz im Speicher (LOD ohne Datenbank: alle zwölf Analysen wie erwartet).

| Nr | Fähigkeit | Prüfung | Weg | Ergebnis |
|---|---|---|---|---|
| 1 | Version | `select banner_full from v$version` | SQL | **ja**: 21c Enterprise Edition 21.3.0.0.0 |
| 2 | räumliches Netz mit Höhe | `SDO_NET.CREATE_SDO_NETWORK` + `VALIDATE_NETWORK` auf 3001/3002, SRID 26908 | SQL | **ja**: 6 Knoten, 6 Kanten, `VALIDATE_NETWORK` = TRUE |
| 3 | logisches Netz | `CREATE_LOGICAL_NETWORK`, gerichtet | SQL | **ja**: TRUE |
| 4 | LRS-Netz | `CREATE_LRS_NETWORK`; `SDO_LRS.LOCATE_PT` (750 m auf 1000 m) | SQL | **ja**: TRUE; `LOCATE_PT(750)` = 350 750 / 6 500 000. Braucht Raum-Metadaten auch für `_PATH$` |
| 5 | hierarchisch | `no_of_hierarchy_levels = 2`, `PARENT_NODE_ID`, `LINK_LEVEL` | SQL | **ja**: TRUE |
| 6 | Feature-Layer | `SDO_NET.ADD_FEATURE_LAYER` (`FEAT_TYPE_MPOINT`) | SQL | **ja**: Layer angelegt |
| 7 | Partitionen | `SPATIAL_PARTITION` (3D), `LOGICAL_PARTITION`, `GENERATE_PARTITION_BLOBS` | SQL | **ja**: räumlich (3D) und logisch, BLOBs erzeugt. Nur mit `LINK_LEVEL` = 1 in `LINK$`, sonst ORA-06502 in MDSYS.SDO_NET |
| 8 | Zusammenhangskomponenten | `FIND_CONNECTED_COMPONENTS` (erwartet 2) | SQL | **ja**: 2 Komponenten (ohne `LINK_LEVEL` hält SDO_NET das Netz für mehrstufig) |
| 9 | Property Graph, SQL/PGQ | nur ab 23ai: `CREATE PROPERTY GRAPH`, `GRAPH_TABLE` | SQL | **nein**: 21c; Wunsch für 23ai (F39) |
| 10 | LOD-Zugang | `LODNetworkManager.getCachedNetworkIO` aus den Partition-BLOBs, Treiber 21 | Java | **ja**: aus den Partition-BLOBs, Treiber 21 im eigenen Lader |
| 11 | Dijkstra | `shortestPathDijkstra` 1 → 3 (1414,2) | Java | **ja**: 1414,2 über [1, 3] |
| 12 | `NetworkConstraint` | eigene Klasse sperrt Kante 5 (2000) | Java | **ja**: 2000 über Kanten [1, 2] |
| 13 | `LinkCostCalculator` | eigene Klasse, doppelte Kosten (2828,4) | Java | **ja**: 2828,4 |
| 14 | `withinCost` | von 1 bis 1100 (2 und 4) | Java | **ja**: [2, 4] |
| 15 | `nearestNeighbors` | von 1, drei (2, 4, dann 3) | Java | **ja**: 2 und 4 zu 1000, 3 zu 1414,2 |
| 16 | *k* kürzeste Wege | `kShortestPaths` 1 → 3, k = 3 | Java | **ja**: 1414,2 / 2000 / 2000 |
| 17 | TSP | `tsp` über 1–4, geschlossen (4000) | Java | **ja**: 4000 |
| 18 | MCST | `mcst` (Spannbaum einer Komponente; LOD 21 spannt keinen Wald) | Java | **ja**, mit Befund: Kanten [1, 4, 2]; `mcst` spannt nur eine Komponente, keinen Wald |
| 19 | `traceOut` | von 1 bis 1500 (2, 3, 4) | Java | **ja**: [2, 3, 4] |
| 20 | `traceIn` | nach 3 bis 1100 (2, 4) | Java | **ja**: [2, 4] |
| 21 | Network Buffer | `networkBuffer` um 1, 1100 | Java | **ja**: 3 Knoten, 5 Kanten |
| 22 | Zusammenhangskomponenten | `findConnectedComponents` | Java | **ja**: [1, 2, 3, 4] und [6, 7] |
| 23 | Partition `LB_NET_HERKUNFT` | `LOGICAL_PARTITION`, `GENERATE_PARTITION_BLOBS` | SQL | **ja**: 1 Partition, BLOBs erzeugt |
| 24 | Partition `LB_NET_WELLE` (Phase 7a) | `SPATIAL_PARTITION` (5 000), `GENERATE_PARTITION_BLOBS` mit Benutzerdaten | SQL | **ja**: 16 Partitionen, BLOBs mit Benutzerdaten |
| 25 | Partition `LB_NET_UFER` (Phase 7a) | `LOGICAL_PARTITION` eines LRS-Netzes, BLOBs | SQL | **ja** |
| 26 | `LinkCostCalculator` mit Benutzerdaten (Phase 7a) | vier Kosten `T_*` aus den Benutzerdaten der Kanten, `withinCost` von `QUELLE` | Java | **nein**: LOD liefert die Benutzerdaten nicht (`getUserData(0)` leer, beim Laden 3,8 Mio. `NullPointerException` in LOD, auch mit `CATEGORY_ID` 0); der Kostenrechner nimmt die Kosten aus der Datei, Ergebnis gleich [zu prüfen in Phase 19] |
| 27 | `withinCost` gegen `route` (Phase 7a) | Ankunft aller Knoten je Kostenart gegen `ankunft.tsv` (≤ 0,01 s) | Java | **ja**: 74 483 Knoten, größte Abweichung 0,0016 bis 0,0031 s je Kostenart |
| 28 | `fastWithinCostPolygon` (Phase 7a, F23) | Isochronen-Flächen nach 60 … 360 s | Java | **nein**: `LODNetworkException: Invalid number of nodes`; Isochronen als Konturen (256 in `LB_ISOCHRONE`, F23 „sonst Konturen“) |
| 29 | `kShortestPaths` auf `LB_NET_WELLE` (Phase 7a) | Wellenstrahlen `QUELLE` → Orte, k = 3 | Java | **ja**: Cenotaph 139,9 s, Anchorage 320,2 s, Eingang 364,7 s wie `route` (im Protokoll noch N, weil der Sporn keinen Knoten hat; seit Phase 8 zählt er nicht mehr) |
| 30 | Dynamische Segmentierung (Phase 7a) | `LOCATE_PT`, `PROJECT_PT`, `CLIP_GEOM_SEGMENT` auf `LB_NET_UFER` gegen `route.Measure` (≤ 5 cm) | SQL | **ja**: `LOCATE_PT` 4,8 mm, `PROJECT_PT` 0,0 mm, `CLIP` 2 000,000 m |

Geprüft am 01.11.2026 mit `ant netzdb` (`docs/status/netzdb_Phase3.txt`): 22 von 23 ja; es fehlt nur Property Graph (21c). Befunde: `LINK_LEVEL` muss auch in einstufigen Netzen gefüllt sein. `Db` aus `Renderer.jar` wartet sonst 30 s auf eine Antwort; `NetzDb` hebt die Grenze auf. `mcst` spannt keinen Wald.

Was fehlt, wird nicht geplant, sondern als Wunsch notiert (F39).

### 2.9 Quellen (Kurzzeichen)

| Kurz | Quelle |
|---|---|
| FRITZ01 | Fritz, Hager & Minor 2001: Lituya Bay case: rockslide impact and wave run-up. Science of Tsunami Hazards 19(1), 3–22. `download/dokumente/dokumente/Fritz_LituyaBay_STHVol19N1Y2001.pdf` |
| MILLER60 | Miller 1960: Giant waves in Lituya Bay, Alaska. USGS Professional Paper 354-C, mit Karte 1:50 000. `download/dokumente/usgs_publikationen/pp354C/` |
| TOCHER59 | Tocher & Miller 1959: Field observations on effects of Alaskan earthquake of 10 July 1958. Science 129, 394–395 |
| SLINGERLAND79 | Slingerland & Voight 1979: Occurrences, properties and predictive models of landslide-generated impulse waves |
| HALLWATTS53 | Hall & Watts 1953: Laboratory investigation of the vertical rise of solitary waves on impermeable slopes |
| SYNOLAKIS87 | Synolakis 1987: The run-up of solitary waves. J. Fluid Mech. 185 |
| MUELLER95 | Müller 1995: Auflaufen und Überschwappen von Impulswellen an Talsperren. VAW-Mitteilung 137 |
| HUBERHAGER97 | Huber & Hager 1997: Forecasting impulse waves in reservoirs |
| HELLER10 | Heller & Hager 2010: Impulse product parameter in landslide-generated impulse waves. J. Waterw. Port Coast. Ocean Eng. [zu prüfen am Original] |
| WIEGEL64 | Wiegel 1964: Oceanographical engineering (Modell 1:1000 der Bucht in MILLER60, S. 65 f.) |
| MADER99 | Mader 1999: Modelling the 1958 Lituya Bay mega-tsunami. STH 17(2) |
| FRANCO20 | Franco et al. 2020: NHESS 20, 2255–2279 (Bathymetrie vor 1958). `download/dokumente/dokumente/Franco_2020_NHESS.pdf` |
| GONZALEZ19 | González-Vida et al. 2019: NHESS 19, 369 |
| NEWMARK65 | Newmark 1965: Effects of earthquakes on dams and embankments. Géotechnique 15 |
| JIBSON93 | Jibson 1993: Predicting earthquake-induced landslide displacements using Newmark's sliding block analysis [zu prüfen] |
| WELLS94 | Wells & Coppersmith 1994: New empirical relationships among magnitude, rupture length, … BSSA 84 [zu prüfen] |
| SAVAGEHUTTER89 | Savage & Hutter 1989: The motion of a finite mass of granular material down a rough incline. J. Fluid Mech. 199 |
| VOELLMY55 | Voellmy 1955: Über die Zerstörungskraft von Lawinen |
| HEIM32 | Heim 1932: Bergsturz und Menschenleben (Fahrböschung) |
| WOODHB | USDA Forest Products Laboratory: Wood Handbook (Festigkeit Sitka-Fichte) [zu prüfen] |
| COMCAT | USGS ComCat, Ereignis iscgem884702. `download/ereignis/usgs_erdbeben/` |
| NCEI | NCEI Global Historical Tsunami Database. `download/ereignis/ncei_tsunami/` |
| COOPS | NOAA CO-OPS, Station 9452928. `download/klima/gezeiten/` |
| ERA5 | ECMWF ERA5 Juli 1958 (NSF NCAR, `--gross`) |
| DATEN | Kartenmaterial nach `Kartenmaterial.md`: Lidar 2019, IfSAR 2012, BlueTopo, USACE 2019, Karten 1951/1961, Sentinel-2, WorldCover, GNIS, … |
| APP | eigene Rechnung und Gestaltung [G] |

---

## 3. Daten: Gebiete, Kartenmaterial, Aufbereitung

### 3.1 Gebiete

UTM Zone 8 Nord (EPSG:26908, NAD83; EPSG:32608 weicht unter 2 m ab), volle Kilometer, Höhen NAVD88 (F3).

| Gebiet | Ost (m) | Nord (m) | Größe | Raster der App | Quelle |
|---|---|---|---|---|---|
| Kern | 340 000 – 364 000 | 6 494 000 – 6 512 000 | 24 × 18 km | 0,5–1 m Lidar, Wasser 4 m BlueTopo, 1 m USACE | DATEN |
| Szene | 322 000 – 388 000 | 6 480 000 – 6 540 000 | 66 × 60 km | 5 m IfSAR, 10 m 3DEP | DATEN |
| Fern | 220 000 – 480 000 | 6 385 000 – 6 625 000 | 260 × 240 km | 30 m Copernicus, 460 m GEBCO | DATEN |

Warum 260 km: Vom Meer aus ist der Gipfel des Mt Fairweather bis zur Entfernung $d \approx 3{,}86\sqrt{H}$ km $= 264$ km sichtbar (mit Refraktion k = 0,13, Abschnitt 9.11). Das Fernfeld reicht genau so weit.

### 3.2 Kartenmaterial

Katalog in `Kartenmaterial.md`, geladen mit `tools/kartenmaterial`, Stand in `download/INVENTAR.md`. Für das Werkbuch zählen: Lidar 2019 (312 Kacheln im Kern), IfSAR 2012 (DTM; DSM und Radarbild mit `--gross`), BlueTopo, USACE 2019, NOS-Vermessungen 1926/1940/1959/2025, USGS-Karten 1951 und 1961, ENC (23 Zellen), Sentinel-2, WorldCover, GNIS, RGI/GLIMS, ITS_LIVE (`--gross`), NCEI-Tsunami, ComCat, Gezeiten, die USGS-Berichte mit Tafeln (36 Dateien), FRITZ01 (von Hand). Fotos von Miller: von Hand aus ScienceBase.

### 3.3 Regel: Kartenmaterial nur in Phase 2, nach Abschluss ausgelagert

Verbindlich, Abnahme-Bedingung (Prompt 2.3):

1. Kartenmaterial liest nur die Aufbereitung `com.dan.lituya.tools`, einmal; Pfad in `daten/rohdaten.properties` (in `.gitignore`), Vorgabe `download/`.
2. Die Aufbereitung schreibt alles, was die App braucht, nach `daten/` mit `daten/INHALT.txt` (Prüfsumme je Datei).
3. App, Netze und Oracle lesen nie aus `download/`.
4. Abnahme Phase 2: `download/` umbenannt, App und Prüfungen laufen vollständig. Fehlt `daten/`, sagt das Startbild, welches Werkzeug zu laufen hat.
5. Nach Projektabschluss bleiben `tools/kartenmaterial/`, `Kartenmaterial.md`, `download/INVENTAR.md`, `geodaten/`; `docs/QUELLEN.md` nennt den Ort jeder Rohdatei auf der Wechselfestplatte.
6. Neue Rohdaten über den Katalog und die Aufbereitung, nie über den Laufzeitcode.

### 3.4 Aufbereitung (Phase 2): Schritte und Ausgaben

| Schritt | Eingang (`download/`) | Ausgabe (`daten/`) | Prüfbild |
|---|---|---|---|
| `gelaende` | Lidar 2019 (Kern), IfSAR-DTM (Szene), 3DEP 1″ und Copernicus (Fern) | `gelaende/kern_0.5m/*.r32` (Kacheln 1 km, für `clipmap`), `szene_5m`, `fern_30m`; NAVD88 | Schummerung je Stufe, Nahtprüfung |
| `grund` | BlueTopo, USACE 2019, NOS 1940 und 1959 (GEODAS xyz), GEBCO | `grund/heute_4m`, `grund/1940_20m`, `grund/1959_20m` (Lotungen → Raster mit Nachbarschaftsinterpolation, Trog nach FRANCO20 geprüft), Land-Wasser-Maske | Tiefenkarte wie `images/Kern_BlueTopo.png`, Differenz 1940–1959 |
| `rueckbau1958` | Karte 1951 und C-5 (georef.), Miller-Karte, Lidar | Gletscherfronten 1958, Deltas, Rutschnarbe aufgefüllt (Prisma 823 × 970 × 92 m) → `gelaende/kern_1958_delta.r32` [G an den Nähten] | Vorher/Nachher Gilbert Inlet |
| `klassen` | Sentinel-2 (klarste Szenen), WorldCover, IfSAR-Radarbild | `klassen/kern_10m.u8` (Wald, Gebüsch, Fels, Schutt, Eis, Schnee, Wasser, Strand), Makrofarbe `farbe/*.rgb` | Klassenbild |
| `wald` | DSM 2012 − DTM, Lidar (Boden), ArcticDEM-Streifen | Kronenhöhe 5 m, Waldalter aus der Spurlinie [G außerhalb des Bands] | Kronenhöhe |
| `spurlinie` | Miller-Karte 1:50 000 (georef., digitalisiert), NCEI-Auflaufhöhen, Lidar-Kante | `ereignis/spurlinie_1958.geojson` (Linie mit Höhe je Stützpunkt), `auflauf.csv` | Spurlinie über Lidar |
| `stoerung` (Phase 8) | Qfaults 2020 (`geologie/stoerungen/Qfaults_GIS.zip`), ComCat (`ereignis/usgs_erdbeben/`) | `ereignis/stoerung.tsv` (Spur der Fairweather-Störung, 10 km), `stoerung_abschnitte.tsv`, `beben.txt` (Herd, M) | `Beben.png` (`ant bebenbild`) |
| `netz` | alle obigen | `netz/<netz>/knoten.tsv`, `kanten.tsv`, `pruefwege.tsv` (Abschnitt 7) | Netzbilder |
| `namen` | GNIS, Tlingit | `namen.tsv` mit UTM | |
| `gletscher` | RGI 7, GLIMS, ITS_LIVE, Fronten 1958 | Umrisse je Zeitstand, Geschwindigkeit | |
| `ereignis` (Phase 13) | — | `ereignis/<lauf>/…` (Höhenfelder, Strom, Zustände je Zeitschritt), vorgerechnet | Bildfolge |
| `inhalt` | — | `INHALT.txt` | |

Höhenbezug: Lidar und USACE sind NAVD88; BlueTopo NAVD88 (VDatum, GEOID17B); NOS-Lotungen beziehen sich auf MLLW und werden mit der Bezugshöhe der Station Elfin Cove (`bezugshoehen_elfin_cove.json`) umgerechnet; GEBCO und Copernicus (Ellipsoid bzw. EGM) nur im Fern, dort reicht ein Versatz [G].

**Befund Phase 2:** `bezugshoehen_elfin_cove.json` nennt MSL − MLLW = 1,747 m, aber keinen Wert für NAVD88. Der Volllauf zeigt: In der tiefen Bucht ist BlueTopo genau die Vermessung von 1940 (alle 25 602 Zellen −0,59 m gegen die Umrechnung mit Elfin Cove). BlueTopo hat sie mit VDatum (GEOID17B) nach NAVD88 gebracht, also liegt MLLW in der Bucht 1,157 m unter NAVD88 (gemessen); das gilt jetzt für alle Vermessungen. Folge für F5: „heute“ ist in der tiefen Bucht der Grund von 1940, nicht neu vermessen. 1926 und 1940 stimmen an gemeinsamen Lotpunkten auf 1,8 m überein (Einheit Meter bestätigt).

**Mischung von Land und Grund [G]:** Liegt der Grund mehr als 1 m unter dem Land und das Land unter 3 m, hat das Landmodell Wasser flach gelegt (Lidar und IfSAR „hydroflattened“), und der Grund gilt; sonst das Land. Wasser in `grund/wasser_4m.u8` heißt außerdem Grund unter 3 m (BlueTopo trägt Landwerte, etwa auf dem Schwemmfächer vor dem Lituya-Gletscher; dort sagt BlueTopo noch −80 bis −98 m aus alten Lotungen, das Lidar 14 bis 32 m: das Lidar gilt).

---

## 4. Grundsatzentscheidungen

### 4.1 Renderer-Ablauf

Wie Sylt und Timmendorf: Package-Arbeit nur in `Renderer/src`, `check-packages` grün, `Renderer/dist/Renderer.jar` nach `LituyaBay1958/lib/`, Datum und Größe im Fortschritt (Abschnitt 19). Die App enthält nie Kopien von Element-Packages. `sdonm.jar` und `lib/lod/` (Treiber 21) wie Timmendorf (F26).

### 4.2 Engine

GL-Engine aus Timmendorf (F6), erweitert um `clipmap` (Gelände bis zum Horizont, 5.11) und `surface` (Materialien, 5.10), Kaskadenschatten bis 60 km (6.10). FFrame als Hülle, FStyle für die Bedienung, FPS- und GPU-Zeit ab Phase 1.

### 4.3 Paketvertrag (15 Punkte)

Sylt Abschnitt 4, Punkte 1–12, unverändert. Dazu:

13. **Kein Package kennt Oracle.** NDM, JDBC, `sdonm.jar` nur in `com.dan.lituya.netz` und `com.dan.lituya.tools`. Was NDM rechnet, rechnet `route` unabhängig nach.
14. **Optimierung nur mit Messung** vorher/nachher (Zeit je Bild, GPU-Zeit, Speicher) und Vergleichsbildern. Alle Szenen, die das Package nutzen, rechnen danach gleich; die alte Rechnung bleibt als Rückfall, bis die neue überall abgenommen ist.
15. **Texturen:** kleine gebackene Karten (≤ 4 MB) als Ressource im Package wie in `com.dan.beach`; große Sätze von der App aus `daten/texturen/`; Herkunft und Lizenz je Textur in `texturen.txt` bzw. `daten/texturen/LIZENZEN.txt`.

### 4.4 Vorrechnung und Echtzeit (F10)

Das Ereignis in voller Auflösung (Granularstrom auf 2 m, Nahfeld mit Partikeln, Welle in drei Gittern, Millionen Bäume mit Zustand) ist auf der Radeon 680M nicht in Echtzeit zu rechnen (Abschnitt 15). Deshalb **beides**:

- **Vorgerechnet** in der Aufbereitung (`ereignis`), hohe Auflösung, nach `daten/ereignis/<lauf>/`: je Zeitschritt (0,1 s im Nahfeld, 0,5 s in der Bucht) Höhenfeld der Welle, Nässe, Strom des Felssturzes, Staub, Zustände der Bäume (als Bitfelder je Kachel), Lage der Boote. Die App spielt ab: vorwärts, rückwärts, Zeitlupe bis 1:20, frei mit der Kamera.
- **Echtzeit** in geringer Auflösung (Welle 16 m im Kern, 4 m im Inlet, Felssturz 8 m) für „Was wäre wenn“: Volumen, Ort, Tide, Grund 1940 gegen heute. Bedienfeld EREIGNIS.

**4.4 umgesetzt in Phase 13 (Vorrechnung; Abspielen 13b, Echtzeit 13c)** (Entscheidungen: geteilt in 13 / 13b / 13c; zwei Läufe, Kette und Messwelle; Grafikkarte; Grund 1940 mit heute aufgefüllt):
- **Grund 1958** (`scene.Boden1958`): im Wasser von heute die Lotungen 1940, sonst BlueTopo/USACE (4 m); an Land IfSAR (Szene 5 m). **Rückbau des Gilbert Inlet [G]**, bis der Schritt `rueckbau1958` kommt: Heute füllen der vorgerückte Lituya-Gletscher und seine Sander den Kopf des Inlet (der Sporn liegt hinter der Gletscherfront, 7.1); im Tal E 353 600–355 700, N 6 505 900–6 507 700 wird Land unter 60 m zu Grund in −122 m (FRITZ01).
- **Kette:** `impact` (Laborfall in Naturmaßen, LOW) gibt bei 885 m Wasserstand und Durchfluss an eine **Linie quer über den Gilbert Inlet** (`Bay.line`, neu in `wave`); Lage [G]: Wand 1 640 m vom Sporn in Richtung 053°, Linie 885 m davor, 1 200 m lang, das Nahfeld dahinter (1 000 m) ist Fels; nach 60 s gibt die Linie frei. Der Durchfluss gilt für die tiefste Stelle der Linie, flachere Zellen bekommen dieselbe Geschwindigkeit (sonst rasten dünne Schichten am Ufer mit 10⁵ m/s davon). Zweiter Lauf mit der Messwelle FRITZ01 an derselben Linie. Uhr: Rechenzeit 0 = 105 s nach dem Stoß (wie `LB_NET_WELLE`).
- **Bucht:** `wave.Bay` in drei Gittern über den Kern: grob 24 × 18 km, Mitte E 351 500–357 000 / N 6 502 800–6 508 000, fein E 353 200–355 800 / N 6 505 200–6 507 600; MEDIUM 16 / 4 / 2 m (5,0 Mio. Zellen), LOW 32 / 8 / 4 m (1,3 Mio.). Ränder offen. Auf der Grafikkarte (`BayGpu`, mit der Linie als `bay_force.comp`) oder dem Prozessor.
- **Strom und Staub:** der Sturz des Presets (gerechnetes Gelände [G]) an die Wand gelegt, je 0,5 s seine Mächtigkeit; seine Staubwolke über dem Grund 1958 (Abrieb 7e-6 je m, Staubanteil 1e-3 [G]), je 2 s Oberkante und optische Dicke.
- **Daten** (`scene.EreignisDaten`, `daten/ereignis/<lauf>/`): je Gitter Bilder des Wasserstands (je 1 s, das feine in den ersten 120 s je 0,5 s), als `short` in Stufen zu 5 cm mit Deflate (ruhiges Wasser packt sich zu nichts), dazu höchster Stand und Ankunft (über 1 m) als Raster. LOW, 420 s: 166 MB je Lauf.
- **Abspielen (Phase 13b)** (Entscheidungen: Höhenfeld je Gitter; Strom mit Vorrat; Strom als Netz, Staub als Volumen; Kamera + Uhr je Stück): `scene.Wiedergabe` liest den gewählten Lauf der Welle (`kette`, `messwelle`, auch `_LOW`), `strom` und `staub`; ein Faden packt das Paar um die Ereigniszeit und drei Bilder in Laufrichtung voraus aus (Vorrat bis 160 MB), der Bildlauf nimmt nur, was da ist, und mischt zwei Bilder weich. Nach dem letzten Bild bleibt es 300 s stehen. `gl.EreignisGl` zeichnet die Welle je Gitter in Kacheln zu 64 Zellen aus den rohen Stufen (R16I, `spur.vert` ohne Puffer aus `gl_VertexID`): nie nasse Kacheln (`max_g<k>`) und ganz vom feineren Gitter gedeckte fallen weg, ebenso solche außerhalb des Blicks; je Kachel 1 bis 16 Zellen je Scheitel nach 3 Bildpunkten Fehler, die Ränder folgen der gröberen Nachbarkachel (keine Risse). Trockene Scheitel sinken 1,5 m unter das Mittel der nassen Nachbarn, das Gelände deckt sie. `welle.frag`: Wasser wie `meer.frag`, Gischt an steilen Flanken und schnell steigender Front, Trübe hoch über dem Pegel [G]; von unten Snellsches Fenster. Der Strom liegt mit seiner Mächtigkeit auf dem Grund von 1958 (`strom.frag`, Felsfarbe [G]). Staub: `medien.frag` läuft in 48 Schritten durch die Schicht zwischen Grund und Oberkante, Dichte = optische Dicke durch Mächtigkeit, Sonne mit Vorwärtsstreuung (g = 0,6 [G]) und Himmel, Albedo 0,8; dazu Trübe mit 25 m Sicht, wenn das Auge unter der Welle ist. Den Grund unter Strom und Staub schreibt `tools.Ereignis` ab jetzt mit (`boden_g0.r32`); fehlt er, rechnet die App ihn einmal aus `Boden1958`. Im Rechteck der Welle zeichnet das Meer nur einen Boden 40 m unter dem Pegel (Lücken, wo die Welle trocken ist, das Gelände von heute aber tiefer); der Rückbau des Gilbert Inlet gilt im Gelände (`gelaende.vert`) für alle Zeitstände außer „heute“.
- **Echtzeit „Was wäre wenn“ (Phase 13c)** (Entscheidungen: beide Quellen; feste Orte und Strg-Klick; vorwärts mit Rückspulpuffer; Rechnung und Gelände umschalten): `gl.Echtzeit` baut die Bucht in LOW (32 / 8 / 4 m) in einem eigenen Faden und rechnet sie mit `BayGpu` im Bildlauf mit einem Zeitbudget (25 ms je Bild) hinter der Uhr her, bis 600 s. **An der Wand von 1958** die Ganglinie der Kette (`daten/ereignis/linie_kette.tsv`, `ant ereignis -Dlauf=linie`, sonst einmal mit `impact` gerechnet) oder die Messwelle an der Linie, nach dem Volumen skaliert: Höhe × (V/V₁₉₅₈)^⅓, Dauer × (V/V₁₉₅₈)^⁵⁄₂₄ (Impulsparameter P ∝ V^⁵⁄₁₂ bei ähnlicher Form, a ∝ P^0,8, T ∝ P^0,5; Heller und Hager 2010) [G]. **Andere Orte** (Gilbert Inlet Südwestufer, Crillon Inlet Nordhang, Südufer bei Mudslide Creek, Nordufer gegenüber Cenotaph Island [G], dazu Strg-Klick mit der Richtung zum nächsten Wasser): eine Linie vor dem Ufer in 20 m Tiefe, halbe Länge 2·V^⅓ (200–800 m), dahinter Fels bis ans Ufer; Volumen-Impuls-Quelle als Wellenberg a·sin²(πt/T) mit Durchfluss η·√(g(h+η)), a und T aus P = F·S^½·M^¼·cos(6α/7)^½ (Heller und Hager 2010) mit der Tiefe des Beckens bis 1,5 km voraus, der halben Hanghöhe als Fallhöhe, S aus 0,3·V^⅓ [G]. **Grund:** 1940 wie die Vorrechnung, heute `Boden1958.hoeheHeute` (BlueTopo, Gelände von heute, kein Rückbau – auch im gezeigten Gelände); liegt die Linie von 1958 heute an Land, rückt sie in Richtung Bucht bis 20 m Tiefe. **Tide:** Pegel −3 bis +4 m, auch für Wasser und Kamera. **Rückspulpuffer** `scene.LiveLauf`: je Sekunde (das feine Gitter in den ersten 120 s je halbe) ein Bild aus der Grafikkarte, gepackt wie in `daten/ereignis` (ein eigener Faden); die Wiedergabe spielt ihn wie eine Vorrechnung, rückwärts und in Zeitlupe, soweit gerechnet. Strom und Staub der Vorrechnung nur an der Wand von 1958 mit Grund 1940.

### 4.5 Zeit

Drei Uhren: **Simulationszeit** t ab dem ersten Stoß (Zeitleiste 2.2), **Weltzeit** (9.7.1958 22:16 PST, UTC−8 [zu prüfen: Zeitzone Alaskas 1958], Sonnenstand, Tide), **Zeitstand** (vor, Welle, danach, heute). Der Zeitraffer der Wiederbewaldung läuft über Jahre.

---

## 5. Neue Packages

Alle entstehen in `Renderer/src` aus `muster`, mit Kern (nur Java 21), `gl/` (JOGL), `demo/` (FStyle), `…Check` („Alles in Ordnung.“), README, Statusbild. Schnittstellen sind schmal und werden in `com.dan.lituya.wiring` verdrahtet.

### Abhängigkeitsbild

```
                         com.dan.lituya (App, wiring, netz, tools)
                                        │
   ┌──────────┬──────────┬──────────┬───┴──────┬──────────┬──────────┬──────────┐
 quake    landslide    impact     wave      debris   succession  vessel    netview
   │          │           │          │           │          │          │
   └── Quake.Slope ─►     │          │           │          │          │
              └── Landslide.Water ─► impact ─► Impact.Far ─► wave      │
                          └── Landslide.Dust ─► dustcloud               │
                                     wave.runupAt ─► succession ─► forest/tree, understory
                                     wave/ocean (Strömung) ─► debris, vessel (über buoyancy)
   clipmap ◄── surface ──► cliff, ice, scree, forest, vessel          route ◄── (Zweitrechnung zu NDM)
```

Kein Package ruft ein anderes direkt: Die Pfeile sind Schnittstellen (`Landslide.Water`, `Impact.Far`, `Wave.Bed` …), die die App mit dem jeweils anderen Package füllt.

### 5.1 `com.dan.quake` — Erdbeben

- **Quelle:** Störung als Linie (aus `LB_NET_WASSER`-fremd: eigene Datei `stoerung.tsv`), Bruchlänge und Versatz aus der Magnitude (WELLS94, 9.1), Bruch läuft mit $v_r \approx 0{,}8\,v_S$ entlang der Störung ab dem Hypozentrum.
- **Bodenbewegung je Ort:** Laufzeiten P und S, Hüllkurve der Beschleunigung aus einem Abminderungsgesetz (9.1), Dauer, synthetische Zeitreihe aus gefiltertem Rauschen mit dieser Hülle (`accelerationAt(x, z, t)` → Vektor), Arias-Intensität $I_A$.
- **Folgen:** `Quake.Slope` (kritische Beschleunigung je Hang → Newmark-Verschiebung → Auslösung an `landslide`), `Quake.Basin` (Anregung einer Seiche an `wave`), Kamerazittern, Schnee- und Eisabbrüche als Ereignisse.
- **Klang:** Grollen (Infraschall hörbar gemacht), Knacken im Wald.
- **Presets:** `fairweather1958()`, `magnitude(M, Abstand)`.
- **Check:** $t_S - t_P = D\,(1/v_S - 1/v_P)$ gegen Handrechnung; Abminderung gegen Tabelle; Newmark-Block auf schiefer Ebene gegen geschlossene Lösung für Rechteckimpuls.
- **Später für:** Geiranger (Åkerneset), Glacier, jede Gebirgsszene.
- **Umgesetzt in Phase 8** (Renderer, Branch `lituya-phase-8`; Entscheidungen: BSSA14, Package und Schritt `stoerung`, alles aus 5.1, Demo als Karte mit Zeitreihen):
  - **Kern:** `Fault` (Spur, Herd, WELLS94, Bruch mit `vr` und Anteil nach vorn, Rjb), `Bssa14` (PGA, PGV, SA 0,2 s und 1,0 s, Boden linear und nichtlinear, σ), `Quake` (Laufzeiten, Stärke, synthetische Zeitreihe aus Teilflächen, Arias, D5–95, Presets `fairweather1958()` und `magnitude(M, Abstand)`), `Slope` (FS, pseudo-statisch, a_c, Newmark, JIBSON93), `Basin` (Merian, Anregung), `Shake`, `Rumble` (PCM, WAV).
  - **Demo und Prüfung:** `demo/QuakeDemo` (Karte mit PGA-Feld, Störung, Bruchfront, P- und S-Front; Zeitreihen an fünf Orten; Newmark; Seiche; `--bild`, `--wav`), `demo/QuakeCheck`.
  - **Prüfung:** tS − tP = D (1/vS − 1/vP); WELLS94 166,7 km und 10,1 m; BSSA14 gegen die Formel von Hand (0,308 g Fels, 0,391 g bei Vs30 400 m/s); Newmark-Rechteckimpuls gleich der geschlossenen Lösung; Merian 11,2 min; Zeitreihe still vor P, Spitze = BSSA14.
  - **App-Daten:** Schritt `stoerung` (`tools.Stoerung`): Spur aus Qfaults 2020 (228 Punkte der Abschnitte „Fairweather fault“ 1:500 000 → 345 km, gemittelt alle 10 km), Herd aus ComCat (`daten/ereignis/stoerung.tsv`, `beben.txt`); `tools.Bebenbild` (`ant bebenbild`).

### 5.2 `com.dan.landslide` — Fels-, Eis- und Schneelawinen *(Prunkstück)*

- **Ablösen:** Platte an Kluftflächen (Material aus `cliff`), Auslösung durch `quake` (Newmark) oder von Hand; Übergabe von `fracture` nach dem Bruch.
- **Strom:** tiefengemittelte Granularströmung nach Savage-Hutter auf dem Gelände (9.2), Reibung nach Voellmy ($\mu$, $\xi$) wählbar auch $\mu(I)$; Materialien Fels (Schiefer), Eis, Schnee, Schutt; Rechnung auf der GPU (Compute), Gitter 2 m im Nahbereich.
- **Eintauchen:** je Zeitschritt Masse, Geschwindigkeit und Querschnitt am Ufer an `Landslide.Water` (→ `impact`, `wave`); Dicke am Wasser im Lituya-Fall bis 134 m (Modellprofil FRITZ01).
- **Ablagerung:** Schuttkegel ins Gelände, Blöcke an `scree`, Staub an `dustcloud`.
- **Bild:** Strom als Höhenfeld mit Material, nahe der Kamera Blöcke und Splitter als Instanzen.
- **Presets:** `gilbertInlet1958()` (Prisma 823 × 970 × 92 m, 40°, ρ = 2 700, Schwerpunkt 610 m), `vajont1963()`, `spiritLake1980()` als Grenzfälle.
- **Check:** Block auf schiefer Ebene gegen $v = \sqrt{2 g L (\sin\alpha - \mu\cos\alpha)}$; Fahrböschung (HEIM32) gegen Literaturwerte; Volumen erhalten; Aufschlaggeschwindigkeit des Lituya-Presets im Bereich 96–110 m/s (9.2).
- **Später für:** Geiranger, Glacier, Brocken (Lawinen im Winter).
- **Umgesetzt in Phase 9** (Renderer, Branch `lituya-phase-9`; Entscheidungen: Kern jetzt in Java, Compute Shader mit der Welle in Phase 11; Newmark mit Entfestigung; Übergabe aus `fracture` mit Demo; Preset auf gerechnetem Gelände, Demo in Java2D):
  - **Kern:** `Landslide` (Gitter über der Gleitbahn, waagerecht projiziert mit g′ = g cos²α; Masse und Impuls nach HLL mit trockenen Zuständen; Erddruck nach Savage-Hutter aktiv/passiv aus der Divergenz; Hangabtrieb zentral; Turbulenz nach Voellmy implizit, Coulomb als Bremse bis zum Halt; Wasser mit Auftrieb und Formwiderstand; Uferlinie mit `Entry` je Schritt: Rate, Volumen, Geschwindigkeit, Mächtigkeit senkrecht und normal, Breite; `addPlate`, `scaleTo`, `inject` auch auf einen Kreis; Fahrböschung mit Abrisskante, `scheidegger(V)`; `settled()`; Qualität 8/4/2 m; parallel über die Zeilen, wiederholbar), `Material` (ρ, μ, ξ, φ; Fels, Eis, Schnee, Schutt), `Presets` (`gilbertInlet1958`, `gilbertInlet1958Terrain`, `vajont1963`, `spiritLake1980`).
  - **Gilbert Inlet auf gerechnetem Gelände:** Hang 40° bis zur Sohle −122 m, flacher Grund, Gegenhang 45° mit der Uferlinie bei 1 342 m; quer eine Rinne (±480 m, Flanken 30°, unter Wasser ±620 m) [G]; Platte 823 × 970 m, bis 92 m, Form (1 − |ũ|^1,82)(1 − |ṽ|^1,82) (Mittel 38,3 m), 30,6 Mio. m³, Fels ρ 2 700, μ 0,06, ξ 8 000 m/s², φ 35° [G, auf FRITZ01 gerechnet].
  - **Demo und Prüfung:** `demo/LandslideDemo` (Draufsicht mit Mächtigkeit, Schnitt mit Geschwindigkeit, Zufluss s(t) mit dem Band 96–110 m/s; Fenster mit Fall und Qualität; `--bild`), `demo/LandslideCheck` (siehe 9.2).
  - **Grenzen:** keine Blöcke, keine Auflockerung (siehe 9.2); dicke Schollen (Vajont) fallen zusammen wie ein Damm.

### 5.3 `com.dan.impact` — das Nahfeld des Eintauchens

- Würfel um den Aufschlag (Lituya: 1 500 × 1 500 × 600 m), darin Partikel (SPH, Wasser und Granulat als zwei Phasen, Luft als Hohlraum) auf der GPU; Wasserkrater, Wasserwand (Splash Curtain), Strahl, Spritzer, Luftblasen, Weißwasser.
- Die zwei Stufen nach FRITZ01: Aufschlag und Eindringen mit Strömungsabriss und **Luftkaverne** (wenn die Eindringgeschwindigkeit die Wellengeschwindigkeit übersteigt, F > 1), dann Zusammenbruch der Kaverne mit Auslauf des Gesteins und Phasenmischung (9.3).
- Übergabe an `wave` am Rand des Würfels: Wasserstand und Durchfluss je Randzelle (`Impact.Far`).
- **Check:** Lituya im Modell 1:675 (FRITZ01): Amplitude 152 m bei 885 m nach 16 s, über 200 m bei 600 m nach 11 s, Höhe 162 m; Energieanteil der Welle an der Gleitenergie zwischen 2 und 30 % (9.3); Volumen erhalten.
- **Später für:** Glacier (Kalbung), Edersee, Stapellauf.
- **Umgesetzt in Phase 10** (Renderer, Quellbaum, Branch `lituya-phase-10` anzulegen; Entscheidungen: Schnitt (2D, je Meter Breite) in Java, Würfel und Compute mit der Welle; Laborfall FRITZ01 und Strom aus `landslide`; `Impact.Far` mit HLL-Kanal im Check; Demo in Java2D; **Abnahme mit Hinweis**, Poren im Inneren als Phase 10b):
  - **Kern `Impact`:** schwach kompressibles SPH, Wendland C2 (h = √2 dx), Kontinuität in Volumenform, Tait je Teilchen mit eigener Ruhedichte, Zug abgeschnitten (Hohlraum), Druckkraft nach Hu-Adams, Monaghan-Zähigkeit, δ-SPH nach Fourtakis nur in einer Phase; Zeitschritt Dichte → Druck → Geschwindigkeit → Ort; Wände nach Adami mit hartem Schutz am Linienzug; parallel, zwei Läufe gleich. Qualität 8/5/4/2 m.
  - **Granulat (`Granulate`):** Mohr-Coulomb viskoplastisch, implizit (16 Jacobi-Schritte, ε = 1e-5), Druck des Gerüsts = SPH-Druck ohne Wasserdruck; **Poren** (FRITZ01 39 %): Wasser, das Körner berührt, geht ganz in deren Poren (Masse, Impuls), die Körner werden schwerer, ihr Volumen bleibt; Schalter `capLoad`, `dynamicBed`, `grainAlpha`, `poreTime`.
  - **Zulauf (`Inflow`, `feed`):** der ganze Gleitkörper frei auf einer Rampe hangauf (Hangabtrieb aufgehoben, solange er mit der Zuflussgeschwindigkeit fährt, keine Reibung), Profil so, dass durch die Uferlinie genau s(t)·v(t) geht; `Inflow.fritz01()` (98,5 · 10³ t je Meter, 1,61 t/m³, bis 134 m, 110 m/s, 6,8 s).
  - **Messen:** Pegel als Säule aus Wasser und Granulat, Kamm im geglätteten Profil, Kaverne (Luft unter dem Ruhespiegel unter Wasser oder Granulat), Energie der führenden Welle ρ g ∫ η² dx, Auflauf, Rand `Far` (Wasserstand, Durchfluss).
  - **`Channel`:** 1D-Flachwasser mit HLL hinter `Impact.Far`; `fromEta` setzt den Durchfluss der rechtslaufenden Welle (der gemessene enthält die überkippende Wand).
  - **Demo und Prüfung:** `demo/ImpactDemo` (Schnitt, Pegel mit FRITZ01 und Kanal, Zufluss und Kaverne, Energiebalken; `--bild`), `demo/ImpactCheck` (siehe 9.3).
  - **Grenzen:** nur der Schnitt; Wasser füllt nur die Poren am Rand des Gleitkörpers; Phasenmischung nach dem Zusammenbruch der Kaverne fehlt; die Höhe im Laborfall liegt rund 30 % über FRITZ01.

### 5.4 `com.dan.dustcloud` — Staubwolke

- Dichtestrom aus Gesteinsstaub (Partikelgröße-Verteilung, Sinkgeschwindigkeit nach Stokes), fließt hangab, hebt sich über dem Wasser, verdünnt im Wind (`wind`), setzt sich ab; Front nach Benjamin $u_f \approx 0{,}5\sqrt{g' h}$ mit $g' = g\,\Delta\rho/\rho$ (9.2).
- Licht: Mie-Streuung, Eigenschatten, Sonne als Scheibe durch den Staub.
- **Später für:** Sylt (Abbruch), Steinbruch, Vulkan.

**5.4 umgesetzt in Phase 12** (Renderer; Entscheidungen: Schwerestrom im Gitter, Demo Java2D): `DustCloud` – Schicht staubiger Luft (Mächtigkeit, Geschwindigkeit, Staub je Kornklasse), Flachwasser mit g′h = g R Q, HLL erster Ordnung, Grund als Quelle; Einmischung nach Parker, die eingemischte Luft bringt den Impuls des Windes; Wind mit τ = 20 s (1 + g′h/|W − u|²) [G]; Sinken nach Ferguson-Church (2 / 10 / 50 / 200 µm: 0,3 mm/s bis 1,7 m/s); Ablagerung getrennt nach Land und Wasser, Bilanz genau; optische Dicke und Sicht (Koschmieder). Quellen als Kreis oder `inject` je Schritt. **Abweichung:** keine eigene Frontbedingung nach Benjamin; die Nase einer Schleuse läuft trotzdem wie das Kastenmodell mit Fr 1,19–√2, der Kopf ist flacher [G]. Licht (Mie, Sonnenscheibe) kommt mit Phase 13.

### 5.5 `com.dan.debris` — Treibgut

- Stämme (mit und ohne Krone, entrindet), Äste, Eisbrocken, Schaummatten; Auftrieb über `buoyancy` (Holz ρ ≈ 0,45 t/m³ → 45 % unter Wasser), Drift mit der Strömung aus `wave`/`ocean` und 2–3 % des Windes, Verkeilen zu Holzteppichen (Dichte der Packung), Stranden, Vergrauen über Jahre (`weathering`).
- **Check:** Drift gegen Strömung, Volumen, Teppich bleibt bei Wind zusammen.
- **Später für:** Rungholt, Sylt (Sturmflut), Hochwasser.

**5.5 umgesetzt in Phase 12** (Renderer; Entscheidungen: Stämme als Körper und Teppich als Feld, Strömung aus `wave.Bay` in der App): `Debris` – liegende Zylinder (Stamm, mit Krone, Ast, Eis), Tiefgang aus dem Kreisabschnitt, Widerstand quer und längs an drei Stellen gegen Wasser und Luft, halb implizit (eine Welle mit 30 m/s reißt mit, ohne Überschwingen); Grund, wo das Wasser flacher ist als der Tiefgang; Stöße als Impulse zwischen Kapseln mit Reibung; Vollsaugen (τ 150 Tage, 1 100 kg/m³ [G]: sinkt nach 281 Tagen), Vergrauen. `Carpet` – Bedeckung je Zelle, höchstens 0,6, staut und strandet, Fläche genau. Windanteil gerechnet 3,5 % (Holz 450, Freibord gegen Tiefgang), mit Krone 4,5 % [G] – etwas über den 2–3 % oben.

### 5.6 `com.dan.succession` — Störung und Wiederbewaldung

- **Störung:** Schadensband mit Zuständen je Fläche (bis zum Fels abgeräumt, Boden freigelegt, Bäume gebrochen, entrindet, umgelegt in Strömungsrichtung), Kante aus der belegten Spurlinie (7.2) oder aus `wave.runupAt` und der Strömungsgeschwindigkeit (9.7), umschaltbar (F17).
- **Wiederbesiedlung:** Pioniere (Erle, Weide) ab Jahr 2, Fichte und Hemlock ab Jahr 10 [G nach Literatur, zu prüfen], Höhe je Alter nach Wuchskurve, das Band bleibt Jahrzehnte sichtbar; Abgleich mit dem DSM 2012 (Kronenhöhe im Band gegen außerhalb) und ArcticDEM 2013–2022.
- Liefert an `forest`/`tree` je Baum Art, Alter, Zustand; an `understory` die Bodenklasse.
- **Später für:** Brocken (Windwurf, Borkenkäfer), Glacier (Gletschervorfeld).
- **Umgesetzt in Phase 14** (Renderer, Entscheidungen: Zellautomat auf 6 m; Strömung aus der Tiefe; Abgleich am DSM in 14a; Vorführung in GL mit `forest`, wegen des Paketvertrags in der App):
  - **Kern:** `Art` (Chapman-Richards, Samen, Flugweite, Schattentoleranz, Lebensdauer, Anwuchs je Boden; Roterle, Sitka-Fichte, Hemlocktanne [G]), `Boden` (Fels, Mineralboden, Waldboden), `Wellenwurf` (9.7), `Succession` (Störung aus dem höchsten Wasserstand, Jahre, Verlauf, Ausgabe je Platz, Statistik).
  - **Zellautomat:** je Platz Haupt- und Unterstandsbaum; Samen auf einem groben Gitter (24 m) mit drei Kastenfiltern ≈ Gauß, 15 % fünfmal so weit; Anwuchs nach Samen, Boden und Licht^(2·(1 − Toleranz)); Wuchs über die Höhe aufs Alter zurück, im Schatten gebremst; Tausch, wenn der Unterstand durchwächst; Sterben nach Lebensdauer und (Lichtbäume) im Schatten; Fels verwittert, Mineralboden wird unter Kronen Waldboden. Fester Startwert (PCG je Platz und Jahr).
  - **Ausgabe:** Verlauf je Jahr (Art, Höhe, Boden; 3 Bytes je Platz und Jahr); die App setzt `TreeStates` mit Nachwuchs (6.4).
  - **Prüfung** (`SuccessionCheck`, 18): Wuchskurve, u<sub>krit</sub> 1,42 m/s, Folgen mit der Tiefe und am steilen Hang, Moment springt an der Krone, Kastenfilter; ein Hang über einem Fjord (2,4 × 1,5 km, Spurlinie 40–300 m) über 68 Jahre: scharfe Kante, Fels nur in tiefer schneller Strömung, Roterle zuerst (58 % nach 30 Jahren), Nadelbäume holen auf, Deckung 12 / 49 / 78 / 81 % nach 10 / 30 / 54 / 68 Jahren, Kronenhöhe im Band nach 54 Jahren 0,36 von außerhalb, Fels 4 % gegen Mineralboden 33 % nach 15 Jahren, Rand 49 % gegen innen 1 % nach 8 Jahren, zwei Läufe gleich. 0,7 s für 23 000 Plätze und 68 Jahre.

### 5.7 `com.dan.understory` — Unterwuchs des Regenwalds

- Moospolster auf Boden, Stämmen, Ästen; Bartflechten; Farne; Teufelskeule; Beerensträucher; Totholz mit Moos; Nadelstreu. Dichte aus Licht unter dem Kronendach (Beer-Lambert mit Blattflächenindex) und Feuchte.
- **Später für:** Brocken, Haithabu, jede Waldszene.
- **Umgesetzt in Phase 6** (Renderer, Branch `lituya-phase-6`):
  - **Kern:** `Kind` (sieben Arten mit Licht- und Feuchteanspruch, Eignung als Glocke in ln(Licht) und Feuchte, Beer-Lambert mit k = 0,45 [G]), `PlantMesh` (prozedurale Netze, zwei Stufen, vier Varianten), `Understory` (Kacheln zu 16 m, Gitter je Art mit Wahrscheinlichkeit, Bartflechten an Ankern der App, Reichweite, Stufen, Ausblenden).
  - **GPU:** `gl/UnderstoryGl` mit Wind, Licht unter dem Kronendach als Textur, Schatten, Laubfall der Teufelskeule und Beeren.
  - **Demo und Prüfung:** `demo/UnderstoryDemo` (Waldboden 200 × 200 m mit Lichtung, Bach und Rücken; `--bild`), `demo/UnderstoryCheck`.
  - **Prüfung:** gepflanzte Dichte gleich der erwarteten (Moos 5 753 erwartet, 5 757 gepflanzt), Ansprüche in der richtigen Reihenfolge, Bartflechten 3 606 an Ankern (erwartet 3 612).
  - **Offen für 6a:** Blattflächenindex aus der Dichte des Walds und Anker an den Ästen aus `ForestCulture`, Feuchte aus Bächen und Hang.

### 5.8 `com.dan.scree` — Geröll, Blöcke, Moränen

- Blöcke als Instanzen nach Korngrößenverteilung (Fuller-Kurve), Grundkörper mit Verformung, Material aus `surface`; Platzierung nach Hang und Schüttwinkel (≈ 35–40°); Moränenwälle, Schuttkegel, Geröllstrände; Ablagerung aus `landslide`.
- **Später für:** Geiranger, Glacier, Brocken (Blockmeere).

### 5.9 `com.dan.vessel` — Motorboote und Kutter

- Rümpfe der 1950er Fischkutter (Maße aus der Recherche), Aufbau, Masten, Licht; Anker mit Kette als Kettenlinie, Schwojen; Rollen und Stampfen über `buoyancy` auf `wave` und `ocean`; Kielwasser über `wake`; Kentern, Volllaufen, Sinken; Fahrt mit Motor und Ruder; Menschen an Bord über `figure`.
- **Check:** Kettenlinie gegen Handrechnung, Rollperiode gegen metazentrische Höhe.
- **Später für:** Timmendorf (Niendorfer Hafen), Lighthouse, Kiel.
- **Umgesetzt in Phase 15** (Entscheidungen: Starrkörper mit sechs Freiheiten statt Streifenmodell, `buoyancy` bleibt unberührt; Kette aus Massenpunkten; Boote nach MILLER60 recherchiert; Demo in GL mit Welle):
  - `Water` (Höhe, Strömung, Grund, Wind), `Hull` (Form aus Maßen: Wasserlinienriss, V-Spant mit flachem Unterwasserkörper, durchgehender Kiel mit Vorfuß, Sprung, Steuerhaus; Proben 30 cm; KB, BM, Völligkeit), `Boat` (Auftrieb je Probe, Widerstand linear, quadratisch und Wellenwiderstand, senkrechte Wassergeschwindigkeit aus der Oberfläche, zusätzliche Masse, Volllaufen über das Deck und gekentert durch die Tür, Wasser im Rumpf mit freier Oberfläche, Sinken, Grund, Maschine und Ruder, Ketten an der Klüse, GZ-Kurve, Handrechnungen), `Chain` (XPBD, Zug aus dem Lagrange-Faktor, Grund mit Reibung, Anker hält, slippt oder sitzt fest, Reißen, Kettenlinie mit Grundberührung), `Kutter` (Edrie, Badger, Sunmore), `Mesh`, `gl/VesselGl`, `demo/Bucht`, `VesselCheck`, `VesselDemo`.
  - Zahlen siehe Abschnitt 19 (Phase 15). Teilschritt 4 ms, rund 0,1 s Rechnung je Sekunde Welle für ein Boot mit Kette.

### 5.10 `com.dan.surface` — PBR-Materialien und Texturensätze

- Materialbibliothek: Albedo, Normale, Rauheit, Umgebungsverdeckung, Höhe; stochastische Kachelung (Hex-Tiling), triplanar an Wänden, Makrovariation aus Luftbild; Mischen über die Höhe; Nässe, Schnee, Staub als Auflage; BC7/BC5, Mipmaps, Budget; Werkzeug zum Backen aus Asset-Paketen (Verallgemeinerung von `tools/beach/SandAsset.java`).
- **Check:** Kontaktbogen, „1 km Kiesstrand ohne Wiederholung“ (Autokorrelation der Textur unter einer Schwelle).
- **Umgesetzt in Phase 5** (Renderer, Branch `lituya-phase-5` auf `lituya-phase-4`):
  - **Kern:** `Material` (Herkunft, offene Werte), `Katalog.fjord()` (14 Materialien, Tabelle 8), `Bake` (Backen aus Zip oder Ordner, Ableiten), `MaterialSet`, `HexTiling` (CPU-Spiegel des Shaders).
  - **Karten je Material:** `a.png` (Farbe sRGB + Höhe), `n.png` (Normale xy, OpenGL; DirectX wird umgedreht), `r.png` (Rauheit, Verdeckung), `material.txt` (Mittelwerte, Herkunft, Lizenz).
  - **GPU:** `gl/SurfaceGl` lädt drei `sampler2DArray` mit Mip-Stufen. Der Treiber packt beim ersten Laden BC7 (Farbe + Höhe, sRGB) und zweimal BC5; das Ergebnis liegt als `gpu_*.bin` neben dem Material und wird beim nächsten Start direkt geladen. Ohne BPTC/RGTC ungepackt.
  - **Baustein `gl/surface.glsl`:** Hex-Kachelung nach Mikkelsen 2022 (PCG-Hash, in Java gleich), dreiseitig für Fels und Eis (|n|⁴, Whiteout), sonst Ebene der größten Achse; Höhenmischung von bis zu drei Materialien; `surfWet`, `surfSnow`, `surfDust`; in der Ferne nur der Mittelwert.
  - **Backwerkzeug in der App:** Schritt `texturen` der Aufbereitung (`tools.Texturen`) aus `download/texturen` (geladen mit `kartenmaterial.py laden --gruppe texturen`) nach `daten/texturen`, mit `LIZENZEN.txt`; `herkunft` verknüpft Zip → Material, Seetang ← Moos.
  - **Prüfung:** `SurfaceCheck` auf gerechneten Probesätzen: 1 km Kiesstrand mit Autokorrelation bei 1 bis 10 Kacheln Abstand **hex 0,02, einfach gekachelt 0,71** (Schwelle 0,3; das Maß wurde in Phase 6 berichtigt, siehe 19); Budget 14 × 2048² gepackt 235 MB, × 1024² 59 MB. Mit Mesa: 14 Schichten 512² gepackt in 0,5 s geladen, Kontaktbogen wie erwartet.

### 5.11 `com.dan.clipmap` — Gelände bis zum Horizont

- Geometrie-Clipmaps um das Auge (z. B. 8 Stufen à 256², Zellweite ab 0,5 m), Kacheln aus `daten/gelaende/` in einem eigenen Faden nachgeladen; virtuelle Textur für Material und Farbe; Erdkrümmung und Refraktion (9.11), Nähte ohne Risse, Höhen NAVD88.
- **Check:** Höhe an 1 000 Stichproben gleich der Kachel; Nahtprüfung; Zeit je Bild.
- **Umgesetzt in Phase 4** (Renderer, Branch `lituya-phase-4`):
  - **Kern:** `Clipmap`, `Source`, `TileFolder` (`.r32` + `STUFE.txt`, Ursprung e0/n0), `TileStack` (fein vor grob).
  - **GPU:** `gl/ClipmapGl` und der Baustein `gl/clipmap.glsl`.
  - **Demo und Prüfung:** `demo/ClipmapDemo`, `demo/ClipmapCheck` (`--daten`, `--gpu`).
  - **Stufen:** Qualität Niedrig/Mittel/Hoch/Ultra = 129²/193²/257²/385² Punkte je Stufe. Bei 0,5 m und 140 km Reichweite sind das 12 bis 14 Stufen bis 1 024 bzw. 4 096 m Zellweite.
  - **Übergang:** im Band eines Zehntels am Rand, nach Losasso und Hoppe; die grobe Stufe verwirft, was die feine zeichnet.
  - **Laden und Zeichnen:** Streifen werden ringförmig nachgeladen; je Stufe 8 × 8 Blöcke, ausgewählt nach dem Sichtfeld.
  - **Erdkrümmung:** mit Refraktion k = 0,13, wie das Wasser.
  - **App:** `scene.Land` legt die Stufen von `daten/gelaende` mit dem Ursprung E0/N0 auf die Zellmitten des Kerns.
  - **Prüfung:** Gegen die Kachel (eigener Leser `Stufe.lesen`) waren 1 000 Stichproben genau, Abweichung 0. Die Naht ist dicht: Ränder auf den Kanten der gröberen Stufe, größte Abweichung 7,6e-6 m.
  - **Befund:** `%` mit negativen Zahlen ist in GLSL undefiniert; Mesa verschob dadurch Zeilen. Der Baustein rechnet den Rest jetzt selbst (`clipMod`), auf Mesa gegen Java auf 1,2e-4 m geprüft.

### 5.12 `com.dan.netview` — Netze und Felder zeichnen

- Graphen, Wege, Pfeile, Isolinien, laufende Isochronen, Strahlen, Messstrecken mit Werten, in der Ferne ausgedünnt; kennt keine Datenbank, bekommt Knoten, Kanten, Werte.
- **Check:** Konturen eines bekannten Feldes (Kreis) treffen den Kreis.
- **Umgesetzt in Phase 7** (Renderer, Branch `lituya-phase-7`):
  - **Kern:** `NetLayer` (Ebene mit Stücken: Linie, Pfeil, Punkte, Isolinie mit Wert, Strahl mit Zeit je Punkt, Band mit Werten, Graph; `isolines` aus einem Rasterfeld), `Contour` (Marching Squares, NaN ausgelassen), `Simplify` (Douglas-Peucker, geschlossene Züge bleiben Ringe), `Ramp` (`TIME`, `HEIGHT`). Drei Stufen je Stück beim Einfügen (0, 4, 25 m), Wechsel bei 800 m und 4 km ab dem Rand der Hülle.
  - **GPU:** `gl/NetviewGl`, jede Strecke ein Viereck in Bildpunkten (Breite unabhängig vom Abstand), Pfeilspitzen, runde Punkte; Isolinien erscheinen, wenn die Zeit ihren Wert erreicht (die vorderste leuchtet), Strahlen wachsen mit der Front; je Ebene ein `glMultiDrawArraysIndirect`. Tiefentest gegen die Szene mit kleinem Versatz, Mischung an.
  - **Demo und Prüfung:** `demo/NetviewDemo` (gerechnete Bucht mit Insel, eigene Laufzeit über 16 Nachbarn, alle Arten; Fenster mit Zeit, Abstand und Ebenen; `--bild`), `demo/NetviewCheck`.
  - **Prüfung:** Isolinien eines Kreisfelds auf 10 m liegen höchstens step²/(8 r) neben dem Kreis (r = 250 m: 0,035 m) und sind geschlossen; Douglas-Peucker hält Enden und Toleranz; Stufen, Rampe und Instanzen stimmen.
  - **Offen für 7a:** Netzblick als Bild im Bild in der App, Ebenen aus `LB_NET_WELLE` und `LB_NET_UFER`; Beschriftung macht die App.

### 5.13 `com.dan.aurora` — Polarlicht (Zugabe)

- Vorhänge und Bögen entlang des Magnetfelds, Farben nach Höhe (9.12), Bewegung, Kp-Stärke; nur im Zeitstand „heute“ in dunklen Monaten (F35).

### 5.14 `com.dan.bear` — Küstenbraunbär (Zugabe)

- Gang, Laufen, Graben, Fischen, Schnüffeln, Flucht; Fell im Wind und nass; Verhalten wie `herd`.

---

## 6. Optimierung bestehender Engines

Nach Paketvertrag 14: messen, vergleichen, alle Szenen gleich, alter Weg als Rückfall. Die Messtabelle wird im Fortschritt je Phase gefüllt.

| Nr. | Package | Warum | Was | Messung | Abnahme |
|---|---|---|---|---|---|
| 6.1 | `wave` | Kern 24 × 18 km; H/h = 1,33; Auflauf an 45°-Hängen; CPU zu langsam (9.4) | Compute-Shader neben der CPU-Rechnung (gleiche Gleichungen, Arakawa C, Flussform); **drei Gitter** (16 m Kern, 4 m Mitte, 2 m Inlet) mit Durchfluss am Rand; **Impulsquelle** aus `impact`/`landslide` (Wasserstand und Durchfluss am Rand, nicht nur Volumen); Nass-trocken-Rand an Steilhängen (dünne Schichten nicht abschneiden, Reibung begrenzt); Stoßerfassung (HLL-Flüsse statt zentraler Differenzen, F12); Front als Bore an `spray`; Pegel und `runupAt` wie bisher | Sekunden Welle je Sekunde Rechenzeit, CPU gegen GPU, je Gitter | `WaveCheck` alt = neu (CPU = GPU auf 1 mm); Laborfall Lituya (FRITZ01) mit Auflauf 526 ± 50 m; Rungholt und Edersee-Demo pixelgleich |
| 6.2 | `fracture` | Millionen Tonnen, nicht Tausende Stücke | nach dem Bruch Übergabe der Zellen als Masse und Geschwindigkeit an `landslide`; mehr Zellen in der Ablösezone | Zeit bis zum Bruch, Zellen | Glacier-Kalbung und Sylt-Abbruch unverändert |
| 6.3 | `ice` | Fronten 1958, Kalben durch Beben und Welle; Eis bis 400 m abgeschert | Spalten und Seracs als Feinform, Schuttbänder, blaues Eis in Spalten (Absorption im Roten), Kalben über `fracture`, Abscheren durch `impact` (Druck auf die Front) | Dreiecke, Zeit | Glacier unverändert |
| 6.4 | `forest`, `tree` | Millionen Bäume im Kern mit Zustand je Baum | GPU-Kultur (Verteilung auf der GPU aus Dichte und Startwert), Oktaeder-Impostors (Richtungen aus 8 × 8 Ansichten), Zustände aus `succession` (stehend, gebrochen, entrindet, umgelegt mit Richtung, fort), Arten Sitka-Fichte und Hemlock. **Umgesetzt in Phase 6** (siehe unten) | Bäume je Bild, Zeit, VRAM | fps-Tabelle; Haithabu, Timmendorf, Brocken unverändert |
| 6.5 | `ocean` | Gletschermilch | Trübung und Farbe aus Schwebstoffkonzentration (9.9), Übergang `ocean` ↔ `wave` am Eingang (gemeinsame Höhe, `Sea`), Gezeitenstrom im Eingang | | Farbe gegen Sentinel-2; andere Szenen mit Trübung 0 unverändert |
| 6.6 | `sky`, `cloud`, `fog` | 58,6° N, Juli, Sonne 1° unter dem Horizont, Berge bis 4 671 m | Dämmerung nach Sonnentiefe (bürgerlich, nautisch), Alpenglühen (Rot der tiefen Sonne auf Schnee), atmosphärische Perspektive über 60 km mit Höhenabhängigkeit der Dichte, Wolkenkappe und Wolkenbank an der Kette | | Sonnenstand gegen Abschnitt 9.10; andere Szenen unverändert |
| 6.7 | `soundscape` | Beben, Sturz, Welle, Wald | tiefe Frequenzen, Laufzeit des Schalls (340 m/s) über Kilometer, Echo an den Wänden der Bucht, Doppler der Front | | Hörproben |
| 6.8 | `kino` | episch | Zeitlupe bis 1:20 mit Zwischenbildern, Zeitleiste der Ereignisse, Bild im Bild (Netzblick), Kamerafahrten mit Trägheit | | Kino läuft durch |
| 6.9 | `route` | Zweitrechnung zu NDM | Gitternetze (4/8/16/32 Nachbarn), Laufzeitbaum mit mehreren Quellen und Startzeiten, Isochronen als Linien, Messstrecke (m entlang einer Linie, Abschnitte mit Werten) | Knoten je Sekunde | `RouteCheck`: $T = L/\sqrt{gh}$ auf konstantem Grund; Fehler des Gitters gegen den Kreis: 16 Nachbarn ≤ 2,75 %, 32 Nachbarn ≤ 0,76 % (9.4). **Umgesetzt in Phase 7** (siehe unten) |
| 6.10 | GL-Engine | Fernsicht, große Schatten | Kaskadenschatten bis 60 km, Gelände über `clipmap`, Materialien über `surface`, HDR mit Belichtung wie das Auge aus Sylt | fps je Stufe | Tabelle in Abschnitt 15 gefüllt |

**6.1 umgesetzt in Phase 11 (Prozessor) und 11b (Grafikkarte)** (Renderer, Quellbaum; Entscheidungen: geteilt in 11 Prozessor und 11b Grafikkarte; neuer Löser als eigene Klasse neben `Wave`; Auflauf mit der Messwelle FRITZ01 abgenommen, aus `impact` als Hinweis; Demo in Java2D):
- **`BayGrid`:** finite Volumen mit den Werten in der Zellmitte, HLL, MUSCL (minmod) und Heun, hydrostatische Rekonstruktion nach Audusse (ruhender See über steilem Grund genau in Ruhe, nass und trocken ohne Sonderfälle), Manning halb implizit; Seiten Wand, offen, Einlass, Gittergrenze; parallel, zwei Läufe gleich. **Abweichung von der Tabelle:** zellzentriert statt Arakawa C, weil HLL so gerechnet wird.
- **`Bay`:** geschachtelte Gitter beliebiger Zahl (Lituya 16 / 4 / 2 m), Unterschritte, Randzellen aus dem gröberen Gitter (nur nasse Zellen), Rückgabe der Mittel (halb trockene Zellen mit passendem Grund), Abgleich der Flüsse; Einlass `Bay.Inlet` (Wasserstand, Durchfluss je Randzelle); Pegel, `runup`, `maxAt`, `bores` für `spray`.
- **`Wave` unverändert** (Rungholt, Glacier, Edersee): alle alten Zeilen von `WaveCheck` wie vorher; „pixelgleich“ ist damit erfüllt, ohne dass die Szenen umgestellt sind.
- **Grenze:** An Trockenfronten, die über eine Gittergrenze laufen, zieht das feine Gitter mehr Wasser, als die grobe Zelle davor hat; das wird abgeschnitten und gezählt (Fjordbucht der Demo +1,4e-5 nach 50 s). Den Fehlbetrag den feinen Randzellen zu nehmen hielt das Volumen genau, machte die Bucht aber nach gut 30 s instabil.
- **Rechenzeit Prozessor:** rund 200 ns je Zelle und Schritt (zwei Kerne hier); Lituya mit drei Gittern (1,7 + 1 + 1 Mio. Zellen, Unterschritte 4 und 8) wären Stunden je 10 min Ereignis: dafür 11b.
- **Grafikkarte (Phase 11b):** `wave.gl.BayGpu` rechnet dieselbe Bucht in float, acht Compute Shader `bay_*.comp` (GLSL 430): Flüsse je Kante (MUSCL, Audusse, HLL), Stufe nach Heun mit Reibung und Höchststand (lesen aus einem Puffer, schreiben in den anderen), Ränder und Einlass, Randzellen aus dem gröberen Gitter, Rückgabe der Mittel, Abgleich der Flüsse, größte Geschwindigkeit. Alles auf der Karte, auch die Verschachtelung; je Schritt eine Rückfrage (Zeitschritt), die Pegel lesen im Takt ab, `sync` holt den Stand in die Felder der Gitter. Fels als Grund 1e30. Die Einlasswerte rechnet der Prozessor (eine Spalte Randzellen je Stufe). `Bay`/`BayGrid` bekommen nur Zugriffe (`bedCells` …, `placement`, `takeOver`, `record`, `addGpu`), ihre Rechnung bleibt.
- **Spielraum float gegen double** (Entscheidung 11b): glatte Fälle 1 mm, Auflauf 1 %, Volumen 1e-6 des Anfangs; mit Einlass 1e-5 [G] (in 140 m Wasser ist ein float-Schritt der Tiefe 15 µm, über 5 000 Schritte mit Ein- und Ausstrom 2,6e-6). An Trockenfronten weichen einzelne Zellen um Meter ab (die Front steht eine Zelle anders) [G]; gemessen werden Median und 99 % der nassen Zellen.

**6.4 umgesetzt in Phase 6** (Renderer, Branch `lituya-phase-6` auf `lituya-phase-5`; `ForestGl`, `ForestField`, `TreeMesh`, alle alten Shader und `com.dan.tree` unverändert):
- **Arten** [G]:
  - Sitka-Fichte (28–50 m) und Westliche Hemlocktanne (24–44 m, nickender Leittrieb), beide auch jung.
  - Roterle (Ufer).
  - Schäden je Art mit `Species.damaged`: gebrochen (Stumpf 20–40 %), entrindet (helles Holz, wenige Stummel), umgelegt.
- **Zustand je Baum:** `TreeStates`, ein Byte je Platz des Pflanzgitters (6 m): Zustand und Fallrichtung in 11,25°-Schritten. Die Welle (Phase 13) und `succession` (Phase 14) schreiben hinein. **Seit Phase 14** ein zweites Byte: Nachwuchs (Art + 1, Größe in 16 Stufen); ein Platz mit Nachwuchs trägt immer einen Baum dieser Art, Kultur auf Prozessor und Grafikkarte gleich (R16UI), `ForestGpuCheck` prüft es im fünften Blick (Mesa: 14 651 junge Bäume, Art und Größe ohne Abweichung).
- **Kultur:** `ForestCulture` auf dem Prozessor, `forestgpu_cull.comp` auf der Grafikkarte, beide mit demselben PCG-Hash und derselben Rechnung in float. Je Bild wird jeder Platz in Reichweite neu ausgewürfelt (Qualität LOW 2 km … ULTRA 6 km). Es gibt weder Kacheln noch einen Speicher je Baum, und es wird nichts hochgeladen.
- **Zeichnen:** drei `glMultiDrawElementsIndirect`, für die Netze der Stufen 0–2, für die Gerippe geschädigter Bäume und für die Impostors.
- **Oktaeder-Impostors:** 8 × 8 Ansichten der oberen Halbkugel, Farbe und Normale, 64 bis 160 Bildpunkte je Ansicht. Vier Ansichten werden gemischt, das Licht ist wie bei den nahen Bäumen.
- **Prüfung:** `ForestGpuCheck` vergleicht in vier Ansichten jede Instanz der Grafikkarte mit dem Spiegel auf dem Prozessor. Mit Mesa waren das 12 687 bis 58 166 Bäume ohne Abweichung (größte 1e-6). Gefunden wurde damit ein Fehler: Die Zähler wurden zwischen zwei Bildern nicht genullt.
- **Unverändert:** `ant waldvergleich` vergleicht das alte und das neue Jar Byte für Byte sowie die Netze aller 26 alten Arten. Hier gleich mit gleichem Startwert.
- **Demo:** gerechnete Bucht 16 × 12 km mit 2 038 471 Bäumen (1 914 834 stehend) und dem Band der Welle am Nordufer: 102 581 Plätze fort, 84 693 umgelegt, 33 196 gebrochen, 31 376 entrindet.

**6.9 umgesetzt in Phase 7** (Renderer, Branch `lituya-phase-7` auf `lituya-phase-6`; `Route`, `Crowd`, `Line` und `RouteGl` unverändert):
- **`TravelGrid`:** Ankunftszeit nach Fermat als kürzester Weg über die Zellmitten (Dijkstra mit Haufen). Sterne `N4`, `N8`, `N16`, `N32`; Kosten einer Kante = Länge × mittlere Langsamkeit der überquerten Zellen (Trapez), gesperrt, wenn eine davon Land ist. Mehrere Quellen mit eigener Startzeit, Laufzeitbaum (`parent`), Strahlen, Isochronen als Linienzüge, `arrivalAt` bilinear, `edges` gibt dieselben Knoten und Kanten mit Länge und Kosten für `LB_NET_WELLE` aus (7a: NDM = `route`). `waveSpeed(h, a)` = √(g (h + a)).
- **`Measure`:** Messstrecke wie LRS: `locate`, `project` (Maß und Abstand mit Seite), `clip`, Abschnitte mit Werten an Stützstellen, `valueAt`, `sample`.
- **`RouteCheck`:** Lücke und Fehler der Sterne: 16 Nachbarn 26,6° und 2,75 %, 32 Nachbarn 14,0° und 0,75 % (gerechnet 1/cos(7,0°) − 1 = 0,751 %; die 0,76 % im Werkbuch sind aufgerundet). Konstante Tiefe 100 m: größter Fehler je Stern gleich der Theorie (41,42 / 8,24 / 2,75 / 0,75 %), entlang der Achse 0,000 %; 16 Nachbarn über 160 801 Zellen in 182 ms (hier). Isochrone 60 s geschlossen, Radius 1 829–1 879 m (innen, da das Gitter nie schneller ist). Zwei Quellen mit Startzeit, Insel (Umweg 82,7 s statt 76,6 s), Kanten, Messstrecke.
- **`IsochroneDemo`:** gerechnete Fjordbucht 14 × 6 km mit T-Kopf, Insel und Schwelle; 16 und 32 Nachbarn, linear und nichtlinear (a = 30 m), Strahlen zu drei Booten (Eingang 337 s linear, 292 s nichtlinear).
- **Über dem echten Grund** (`tools.Isochronenbild`, `ant isochronenbild`): 20 m, 16 Nachbarn, Wasserstand Mittelwasser +0,59 m NAVD88 [G], Einschlaglinie 198 Wasserzellen am Ufer bis 412 m um den Wasserpunkt am Gilbert Inlet (E 354 810, N 6 505 710; der Punkt aus GNIS liegt heute unter dem Gletscher). Ankunft (heute linear / nichtlinear; 1940 linear / nichtlinear): Cenotaph Island 140 / 110; 135 / 108 s, Anchorage Cove 321 / 268; 316 / 266 s, **Eingang 365 / 292; 356 / 289 s**. Der Handwert aus 9.5 (350 s linear, 300 s nichtlinear) liegt 4 % und 3 % daneben. Rechenzeit je Lauf 0,4–0,5 s für 1,08 Mio. Zellen. Die Zahlen sind hier aus dem auf 20 m gemittelten Grund heute gerechnet; der Lauf auf dem Rechner liest `heute_4m.r32` selbst.

**6.2 umgesetzt in Phase 9** (Renderer, Branch `lituya-phase-9`; Eis, Fels, Beton, Klei und Lockergestein rechnen wie zuvor):
- **Übergabe:** `Fracture.handOver(piece)` nimmt das Stück heraus (mit seinen Lasten auf dem Körper) und gibt `Piece.cellsInWorld()`: je Zelle x, y, z, Volumen, Masse, vx, vy, vz aus Lage und Drehung; `handedOverVolume()` zählt. Das Package kennt `landslide` nicht, die App verdrahtet.
- **Ablösezone:** `Fracture.releaseZone(Punkt, Normale, Breite, Grundwert)` als `Density`: feine Zellen um die Abrissfläche.
- **Prüfung:** `FractureCheck` mit neuem Abschnitt (Volumen, Masse, Impuls und Schwerpunkt der Zellen gleich dem Stück auf 1e-15; Ablösezone). Alle alten Zeilen gleich, die Gletscherfront der Vorführung Stück für Stück wie vor Phase 11 (Glacier), Lockergestein wie in Sylt; das Bild von `FractureDemo --bild` Byte für Byte gleich (nur die Zeiten je Schritt ändern sich).
- **Messung:** Platte des Gilbert Inlet mit 400 Zellen: löst sich mit dem Rest der Kohäsion sofort in 342 Stücke, übergeben von 1,3 bis 3,0 s, 5 s Rechenzeit (MEDIUM).

---

## 7. Die Netze (Oracle Network Data Model)

Schema `LITUYABAY1958` (F40 B, Entscheidung Phase 3: der vorhandene Benutzer aus `db/info/USER.sql`), Präfix `LB_`, Netze `LB_NET_*`, SRID 26908 mit Höhe (3001/3002), Toleranz 5 mm, Index `SPATIAL_INDEX_V2`, Raum-Metadaten auch für `_PATH$` (Lehre aus Timmendorf). Jedes Netz hat ein Datei-Abbild in `daten/netz/<netz>/` (`knoten.tsv`, `kanten.tsv`, `pruefwege.tsv`), ein Ladeskript `db/netz/NN_<netz>.sql`, Prüfwege, eine Zweitrechnung in `route`, eine Prüfsumme in `LB_NETZ_STAND`, und mindestens eine Frage, die es für die Szene beantwortet.

### 7.1 `LB_NET_WELLE` — Laufzeit der Welle *(Herzstück)*

**Frage:** Wann erreicht die Front welchen Ort? Wie viel Zeit hatte jedes Boot? Wie teilt sich die Front an Cenotaph Island?

**Modell:** Nach Fermat ist die Ankunftszeit der Front die kürzeste Laufzeit $T(\mathbf{x}) = \min\int ds/c$, die Eikonalgleichung $\lvert\nabla T\rvert = 1/c$. Auf einem Gitter wird daraus ein kürzester Weg (9.4).

| Element | Festlegung |
|---|---|
| Knoten | Gitter 20 m über dem Wasser des Kerns (F21): rund 75 000 Knoten; Attribute `TIEFE_1940`, `TIEFE_1959`, `TIEFE_HEUTE`, `AMPLITUDE` (aus `wave`, je Lauf) |
| Kanten | 16 Nachbarn (Königszüge und Springerzüge), ungerichtet, `LAENGE` |
| Kosten | `COST` = Laufzeit linear heute; weitere über `LinkCostCalculator` (F22): `T_1940`, `T_1959`, `T_HEUTE` mit $c = \sqrt{g\,h}$, `T_NICHTLINEAR` mit $c = \sqrt{g\,(h + a)}$ |
| Quelle | virtueller Knoten `QUELLE`, Kosten 0 zu allen Knoten der Einschlaglinie (Ufer des Gilbert Inlet unter der Rutschnarbe, Breite 823 m) |
| Partitionen | `SPATIAL_PARTITION` mit 5 000 Knoten, Partition-BLOBs, LOD |
| Analysen | kürzester-Wege-Baum von `QUELLE` → `ANKUNFT` je Knoten; `withinCost(QUELLE, t)` für t = 10, 20, … s → Isochronen (Network Buffer oder Konturen, F23); `nearestNeighbors(Boot)` zur Front; `kShortestPaths` zu einem Boot als Wellenstrahlen |
| Ergebnis | `LB_ISOCHRONE` (Polygone je Kostenart), `LB_ANKUNFT` (Zeit je Knoten) |
| Zweitrechnung | `route.gridTravelTime` auf denselben Knoten und Kosten: gleiche Ankunftszeiten bis auf Rundung |
| Prüfung | gegen `wave` (Pegel an 20 Orten, Zeit des ersten Anstiegs über 1 m), gegen Zeugen (Anchorage Cove, Eingang), gegen den Handwert (9.5) |

Abweichungen zwischen Eikonal und `wave` sind kein Fehler, sondern das Ergebnis: Die Eikonalgleichung kennt keine Amplitudenabhängigkeit außer über `T_NICHTLINEAR`, keine Reflexion und kein Brechen. Die Tabelle in Phase 13a zeigt, wo das zählt.

**Umgesetzt in Phase 7a** (Entscheidung: Bucht und 1 km See):
- **Datei** (`ant aufbereitung "-Dschritte=welle,ufer"`, `tools.Welle`, gelesen von `scene.WellenNetz`): `daten/netz/welle/` mit `knoten.tsv`, `kanten.tsv`, `ankunft.tsv`, `pruefwege.tsv`, `WELLE.txt`. Fläche: die Bucht innerhalb des Eingangs (Strecke La Chaussee Spit – Harbor Point, je 300 m verlängert, als Tor) und das Wasser bis 1 km vor dem Tor. **75 695 Knoten** (Bucht 67 403, See 8 292) und `QUELLE`, **594 689 Kanten** und 198 von `QUELLE` (Einschlaglinie). Tiefen unter Mittelwasser (+0,59 m NAVD88 [G]), mindestens 0,5 m; 1940 und 1959 aus den Lotungen, wo sie dicht liegen, sonst heute. `AMPLITUDE` = 30 + 122·e^(−d/1 500) m [G], bis `wave` (Phase 11) sie liefert.
- **Oracle** (`ant netzdb`): `LB_NET_WELLE` räumlich, ungerichtet, 3001/3002 in der Höhe des Mittelwassers; die vier Kosten als Spalten und als Benutzerdaten (Kategorie 0); `SPATIAL_PARTITION` mit 5 000 Knoten, BLOBs mit Benutzerdaten (Probe 24). Linien der Kanten auf dem Server aus den Punkten der Knoten.
- **LOD** (`netz.WelleLod`): je Kostenart ein `LinkCostCalculator`, der die Kosten aus den Benutzerdaten liest (Index aus den Metadaten; fehlen sie, aus der Datei, und das Protokoll sagt es); `withinCost(QUELLE)` gibt die Ankunft aller Knoten. Ergebnis `LB_ANKUNFT` (`T_LOD` und `T_ROUTE` je Knoten und Kostenart, Sicht `LB_V_ANKUNFT_VERGLEICH`). Im Speicher (ohne Oracle) stimmt LOD mit `route` auf 0,003 s überein (Rundung der Datei).
- **Isochronen** (F23): `LB_ISOCHRONE` mit Konturen alle 30 s bis 10 min je Kostenart aus der Ankunft von LOD (Marching Squares, `route.Contour`) und Flächen aus `fastWithinCostPolygon` (Network Buffer) jede Minute bis 6 min.
- **Wellenstrahlen:** `kShortestPaths(QUELLE → Ort, 3)`; in der App der Baum aus `ankunft.tsv` (Vorgänger).
- **Ankunft** (Datei, heute / 1940 / 1959 linear / heute nichtlinear): Cenotaph Island 140 / 135 / 128 / 110 s, Anchorage Cove 321 / 316 / 304 / 268 s, **Eingang 365 / 356 / 345 / 292 s**.
- **Befund:** Der Sporn (524 m) und der Gilbert Inlet selbst liegen heute hinter der Gletscherfront; im Netz heute gibt es dort kein Wasser. Die Fläche von 1958 (Inlet, T-Kopf mit Crillon Inlet) kommt mit dem Schritt `rueckbau1958` (F32); bis dahin startet die Front an der heutigen Gletscherfront, rund 700 m südlich des GNIS-Punkts.

### 7.2 `LB_NET_UFER` — das Ufer als Messstrecke (LRS)

**Frage:** Wie hoch lief die Welle an jedem Ufermeter auf? Wo fehlt der Wald, und seit wann?

| Element | Festlegung |
|---|---|
| Netz | LRS-Netz (`CREATE_LRS_NETWORK`), zwei Stränge: Ufer 1958 (Karte 1951/C-5, Vermessung 1940) und Ufer heute (Lidar), dazu Cenotaph Island als Ring; Maß m in Metern ab La Chaussee Spit im Uhrzeigersinn |
| Ereignisse | `LB_AUFLAUF` (Punkt-Ereignis: m, Höhe, Jahr, Quelle), `LB_SPURLINIE_LRS` (Linien-Ereignis: m₁–m₂, Höhe je Stützpunkt, Jahr 1853/1874/1899/1936/1958, Rechnung), `LB_FOTO` (Miller-Standorte), Breite des Schadensbands |
| Dynamische Segmentierung | `SDO_LRS.LOCATE_PT`, `PROJECT_PT`, `CLIP_GEOM_SEGMENT`: je Ufermeter Höhe der Spurlinie durch lineare Interpolation zwischen den Stützpunkten |
| Feature-Layer | Auflaufpunkte und Fotos als Features auf den Kanten (wenn 2.8 es erlaubt) |
| Für die Szene | `succession` fragt je Ufermeter die belegte Höhe; Vergleich belegt gegen `wave.runupAt` als Band um das Ufer |
| Zweitrechnung | `route.measure` entlang der Uferlinie |

**Umgesetzt in Phase 7a** (Entscheidung: Netz jetzt, Werte später):
- **Datei** (`tools.Ufer`, gelesen von `scene.UferNetz`): `daten/netz/ufer/` mit `straenge.tsv`, `punkte.tsv`, `knoten.tsv`, `kanten.tsv`, `auflauf.tsv`, `UFER.txt`. **Ufer heute**: der Rand von `grund/wasser_4m.u8` um die Bucht ohne den Eingang (Marching Squares auf 4 m, Douglas-Peucker 2 m), 36,8 km, Maß ab La Chaussee Spit im Uhrzeigersinn bis Harbor Point. **Cenotaph Island** als Ring, 4,9 km, ab dem nördlichsten Punkt. Knoten alle 500 m (85), Kanten mit Maß von–bis (84); der Ring hat Anfang und Ende als zwei Knoten am selben Ort.
- **Oracle**: `LB_NET_UFER` mit `CREATE_LRS_NETWORK` auf `LB_UFER_GEOM` (Linien mit Maß aus `LB_UFER_PUNKT` über `CONVERT_TO_LRS_GEOM`, `06_ufer.sql`), Ereignisse `LB_AUFLAUF` (Lage aus `LOCATE_PT`), `LB_SPURLINIE_LRS` mit `LB_SPURLINIE_STUETZ` (noch leer). Prüfung: `LOCATE_PT`, `PROJECT_PT`, `CLIP_GEOM_SEGMENT` gegen `route.Measure` auf 5 cm (Probe 30).
- **Auflauf (belegt, Lage [G]):** 524 m (1958, Sporn), 208 m (1958, Südufer bei Mudslide Creek), 120 m (1853/54, Nordufer gegenüber), 150 m (1936, NE-Wand des Crillon Inlet), auf das Ufer heute projiziert.
- **Noch nicht:** der Strang **Ufer 1958**. Die Lotungen 1940 liegen in der Bucht nur in Flecken; ihr Rand ist kein Ufer. Er kommt mit `rueckbau1958` (Karte 1951/C-5), die Spurlinie je Ufermeter mit `spurlinie`.

### 7.3 `LB_NET_SEE` — Fahrwasser 1958 mit Gezeiten

**Frage:** Wer hätte entkommen können? Welche Fahrt hat La Pérouse 1786 im Eingang gekostet?

| Element | Festlegung |
|---|---|
| Netz | gerichtet mit Gegenrichtung, Knoten auf dem Wasser (50 m im Eingang, 200 m in der Bucht), Ankerplätze der Boote, Anchorage Cove, Eingang, offene See |
| Kosten | zeitabhängig über `LinkCostCalculator`: Fahrzeit = Länge / (Bootsgeschwindigkeit ± Strom im Eingang zur Zeit t); Tiefe $h(t) = h_{1940} + \eta_{\text{Tide}}(t)$ aus COOPS |
| Einschränkung | `LODNetworkConstraint`: Kante gesperrt, wenn $h(t) < $ Tiefgang + 0,5 m; Eingang bei Ebbstrom gesperrt für Boote unter 6 kn [G] |
| Analysen | kürzester Weg von jedem Ankerplatz zur offenen See für t = 22:16 + Δ; Vergleich mit `LB_ANKUNFT`: entkommen, wenn Fahrzeit bis zum Ort x kleiner als Ankunft der Front dort; `kShortestPaths` als Varianten |
| Prüfung | gegen `route` mit denselben Kosten (zu festen Zeiten) |

**Umsetzung (Phase 15a).** Entscheidungen: „Vor der Front überall“ – entkommen heißt, jeder Knoten des Wegs wird vor der Front erreicht und das Boot ist 1 km jenseits des Tors (950 m auf der Linie hinaus); Abfahrt Δ = 0 … 180 s nach dem Stoß, dazu die späteste Abfahrt, mit der es noch geht (auf 1 s).

| Element | gebaut |
|---|---|
| Gezeiten | `scene.Gezeiten`: acht Tiden (M2, S2, N2, K2, K1, O1, P1, Q1) der NOAA-Station **Elfin Cove 9452634** (harmonische Konstanten), V₀ nach Schureman wie NOAA (eintägige Tiden ±90°), f und u aus der Länge des Mondknotens, MW = NAVD88 + 0,59 m. Gegen die Vorausrechnung der NOAA am 2.11.2026: rund 15–21 min früh, bis 0,18 m. **9.7.1958: Hochwasser 19:52 PST, 1,70 m NAVD88** (Ulrich: „the last of the flood at 8 pm“), beim Stoß +1,18 m und fallend (0,39 m/h). Strom im Tor aus dem Füllen der Bucht, Q = A·dη/dt (26,6 km², Tor 300 × 8 m [G]): **1,1 m/s Ebbstrom hinaus**. Station [G]: 95 km südöstlich an derselben Außenküste; die Tide nach COOPS (Lituya selbst) bleibt offen. |
| Netz | `tools.See` (Schritt `see`) → `daten/netz/see`: Knoten aus `LB_NET_WELLE` (40 m im Umkreis von 800 m um das Tor, 100 m See, 200 m Bucht), nur wo die Tiefe 1940 + Gezeit ≥ 1,65 + 0,5 m. Kanten zu den Nachbarn bis 1,5 Abstände, wenn jede 20-m-Zelle dazwischen tief genug ist; **gerichtet**, je Richtung eine Kante. Im Testlauf (LOW) 1 832 Knoten, 14 606 Kanten. |
| Kosten | Länge / (8 kn + Strom · Richtung hinaus); Strom = Strom im Tor zur Zeit t mal einem Faktor je Kante (Gauß um das Tor, 450 m [G]). In LOD ein `LinkCostCalculator`, der die Zeit am Anfang der Kante aus `getCurrentCosts()` + Δ nimmt. Die Sperre bei Ebbstrom „unter 6 kn“ entfällt: der Strom geht in die Fahrzeit ein. |
| Front | zweimal je Knoten: **linear** (T_1940 aus `LB_NET_WELLE` + 105 s) und **aus der Vorrechnung** (`ankunft_g*.r32` des Laufs `kette`: erstmals 1 m über dem Pegel). In LOD eine `LODNetworkConstraint`, die den nächsten Knoten mit `getNextCosts()` + Δ gegen seine Front prüft. |
| Rechnung | eigener zeitabhängiger Dijkstra (`scene.SeeNetz.flucht`, FIFO) und LOD `withinCost` (`netz.SeeLod`), im Speicher und in Oracle. **Befund:** In `createLogicalNetLink(id, a, b, cost, x, y, …)` ist x „beide Richtungen“, y „aktiv“; mit `true, false` fand LOD keinen Weg. Im Speicher: 12 Läufe, 8 792 Ankünfte gleich bis 0,0000 s, kein Knoten anders. |
| Ergebnis | `flucht.tsv`, `wege.tsv`, `SEE.txt`, `gezeiten.tsv`, `orte.tsv`; Karte `ant fluchtkarte` → `Flucht.png`; im HUD eine Zeile. Testlauf LOW: **Edrie** linear erst bei Abfahrt 268 s *vor* dem Stoß, **Badger** bis 216 s danach (Front linear am Tor nach rund 460 s). |
| Oracle | `db/netz/07_see.sql` (Netz gerichtet, `LB_FLUCHT` mit dem Weg als Linie, Z = Ankunft), `08_see_nach_dem_laden.sql` (Indizes, Partition, BLOBs, Sicht `LB_V_FLUCHT`, Probe 30), `tools.SeeDb`; `ant netzdb` lädt, prüft (`VALIDATE`, LOD in Oracle = Dijkstra) und trägt `LB_NETZ_STAND` ein. |

### 7.4 `LB_NET_WASSER` — Gewässer und Gletscher (hierarchisch)

**Frage:** Welche Fläche entwässert in den Gilbert Inlet? Wie viel Wasser führen Mudslide Creek und die Bäche nach Regen?

| Element | Festlegung |
|---|---|
| Ebene 1 | Bäche aus dem Abfluss im Lidar/IfSAR (D8-Abfluss, Schwelle 0,5 km²), gerichtet stromab, `COST` = Fließzeit (Manning) |
| Ebene 2 | Einzugsgebiete als Elternknoten, Fließlinien der Gletscher (ITS_LIVE) als Kanten mit Jahresgeschwindigkeit |
| Analysen | `traceIn(Gilbert Inlet)` → Einzugsgebiet; `traceOut(Quelle)`; Zusammenhangskomponenten (abflusslose Senken = Seen); Fließzeit nach Regen |
| Für die Szene | `fall`/`river` bekommen die Wasserführung je Kante aus Fläche × Niederschlag × Abflussbeiwert [G] |
| Zweitrechnung | `route` auf dem gerichteten Graphen |

**Umsetzung (Phase 16).** Entscheidungen: 16 nur Wasser, `LB_NET_WEG` als 16a; ITS_LIVE nachladen (Ausschnitt der Szene über den Katalog); Geländeklassen für 16a aus WorldCover, Wald und RGI.

| Element | gebaut |
|---|---|
| Gelände | Szene (`szene_5m`, IfSAR 2012) auf 10 m gemittelt, 6 600 × 6 000 Zellen. **Meer** = unter Mittelwasser (+0,59 m NAVD88), mit dem Rand verbunden. Ziele der Ebene 1 und 2: **Gilbert Inlet**, **Crillon Inlet** (Wasser bis 1,2 km um den GNIS-Punkt [G]), **Bucht** (innerhalb des Tors aus `WELLE.txt`), **Golf von Alaska**, **Rand der Szene**. Stand des Geländes heute (2012), nicht 1958. |
| Abfluss | Priority-Flood mit ε (Barnes u. a. 2014) von Küste und Rand: Senken aufgefüllt, jede Zelle fließt zum Nachbarn, von dem aus sie geflutet wurde (dem tiefsten), Fläche stromab aufsummiert. **Seen** = Flächen exakt gleicher Höhe ab 0,05 km² (IfSAR ist wassereben); **Senken** = Auffüllung ab 2 m und 0,01 km² (`LB_SENKE`). |
| Ebene 1 | Bäche ab 0,5 km²; Knoten an Quelle, Zusammenfluss, Gletschertor, Seeauslauf, Teilung, Mündung und je 200 m; Kanten mit dem Verlauf (`linien.tsv`, Douglas-Peucker 4 m). Kosten = Fließzeit bei Regen 10 mm/h, Beiwert Land 0,6 / Eis 0,9, Manning n = 0,045, Tiefe 0,3·Q^0,4 [G]. Gletscher aus **RGI 7** (`tools.Rgi`, Shapefile im Zip ohne GDAL). **Fließlinien des Eises** auf 120 m ab 2 km² Eisfläche, Richtung aus **ITS_LIVE** (vx, vy in der Abbildung der Datei, mit der Ableitung der Abbildung nach UTM gedreht; ohne ITS_LIVE stromab im Gelände, 100 m/a [G]), Kosten = Zeit des Eises; am Ende eine Kante zum Bachknoten oder Ziel, den das Wasser der Endzelle erreicht. |
| Ebene 2 | Teileinzugsgebiete als Elternknoten: je Mündung eins, geteilt an Zusammenflüssen mit mindestens zwei Zweigen ab 10 km², dazu der Küstensaum je Ziel; Kanten stromab, Kosten = Fließzeit zwischen den Ausläufen. `PARENT_NODE_ID` je Knoten der Ebene 1; **ein Eisknoten gehört zum Gebiet, in das sein Gletscher das Wasser abgibt** (Befund: Cascade- und Fairweather-Gletscher fließen über Wasserscheiden des Geländes). Umrisse aus einem 50-m-Raster (`tools.Umriss`: außen gegen, Löcher mit dem Uhrzeigersinn). |
| Rechnung | `scene.WasserNetz`: traceIn, traceOut, Fließzeit zum Ziel (Dijkstra rückwärts), Komponenten, Baumprüfung. Zweitrechnung: LOD `traceIn` je Ziel und `findConnectedComponents` (`netz.WasserLod`, im Speicher und in Oracle), `route.within` auf den umgedrehten Kanten (Befund: `route.edge` nimmt die Länge aus den Koordinaten; die Fließzeit kommt über die Kostenfunktion, deren Flag „vorwärts“ heißt), in Oracle `CONNECT BY` auf Ebene 2 (`LB_V_WASSER_ZIEL`) und `SDO_AREA` der Umrisse. |
| Ergebnis | `daten/netz/wasser`: `knoten.tsv`, `kanten.tsv`, `linien.tsv`, `einzug.tsv`, `umrisse.tsv`, `einzug_50m.i32`, `seen.tsv`, `senken.tsv`, `pruefwege.tsv`, `WASSER.txt`; Karte `ant wasserkarte` → `Wasser.png`; im HUD eine Zeile. **Testlauf** (Gelände auf 0,1 m gerundet, ohne ITS_LIVE): **Gilbert Inlet 274,7 km² (59 % Eis), Crillon Inlet 110,2 km² (60 %), Bucht 50,0 km² (12 %), zusammen 434,9 km²**, keines stößt an den Rand der Szene; Fließzeit bei Regen bis 5,5 h zum Gilbert Inlet; traceOut vom Lituya-Gletscher endet im Gilbert Inlet; 18 115 Knoten, 18 105 Kanten. |
| Oracle | `db/netz/09_wasser.sql` (`CREATE_SDO_NETWORK(…, 2, TRUE, FALSE)`, **2D** statt 3001/3002, Höhe in `HOEHE`; `LB_EINZUG` mit Umriss, `LB_SEE`, `LB_SENKE`), `10_wasser_nach_dem_laden.sql` (Indizes, `SPATIAL_PARTITION` Ebene 1, Ebene 2 eine Partition, BLOBs je Ebene, Probe 31, Sichten `LB_V_EINZUG`, `LB_V_WASSER_ZIEL`), `tools.WasserDb`; `ant netzdb` lädt und prüft. |

### 7.5 `LB_NET_WEG` — Wege über Gelände, mehrere Kosten, mehrere Modi

**Frage:** Wie kommt man von der Bucht zum Mt Crillon (Washburn 1934)? Wie weit kommt man in einer Stunde vom Strand?

| Element | Festlegung |
|---|---|
| Ebene 1 | Gitter 10 m im Kern (≈ 3,3 Mio. Knoten auf Land), Ebene 2: 50 m in der Szene (≈ 1,6 Mio. Knoten); hierarchisch verknüpft |
| Kosten | Gehzeit nach Tobler $v = 6\,e^{-3{,}5\,\lvert \tan\theta + 0{,}05\rvert}$ km/h (9.8) × Zuschlag je Klasse (Wald 1,5, Gebüsch 2,5, Gletscher 2 mit Spaltenzone gesperrt, Bach > 0,5 m³/s gesperrt) [G] |
| Modi | Umsteigekanten Boot → Strand zu `LB_NET_SEE` an den Stränden |
| Analysen | `kShortestPaths`, `withinCost(Strand, 1 h)`, Washburns Route nachgerechnet (falls belegt, F31) |
| Leistung | Millionen Kanten: Partitionen, LOD, Speicher und Zeit in die Tabelle 15.3 |

**Flucht an Land (Phase 16b).** Entscheidungen: gerettet = über dem Auflauf der Vorrechnung plus 10 m [G]; mit dem Boot an den Strand. Ablauf: das Boot fährt wie in 15a durch `LB_NET_SEE` (Gezeitenstrom, jeder Knoten vor der Front des Laufs `kette`); an jedem Umstieg (`LB_WEG_UMSTIEG`, Strand bis 300 m), den es erreicht, 3 min anlanden [G]; dann zu Fuß über Ebene 1 von `LB_NET_WEG`, jeder Knoten vor der Front an Land (`ankunft_g*` aus dem feinsten Gitter, nie nass = keine Front), bis zum ersten **sicheren** Knoten: trocken und mindestens 10 m über dem höchsten Stand (`max_g*`, Meer eingeschlossen) bis 300 m um ihn [G]; Landweg höchstens 1 h. Abfahrt Δ = 0 … 180 s und die späteste. **Probelauf:** Edrie bei jeder Abfahrt 0 … 180 s gerettet, **späteste Abfahrt +194 s** (an Land nach 540 s, 600 m westlich, sicher nach 713 s auf 20 m, 82 m zu Fuß) – über See ging es nur bis −251 s; Badger bis +254 s (wie über See: um die Nehrung herum, an deren Außenseite). Die Front erreicht die Landwege nicht; die Vorrechnung bleibt am Eingang niedriger als 1958 (13a), das Ergebnis ist also eher zu günstig. Zweitrechnung: eigener Dijkstra nachgerechnet, LOD `withinCost` mit Frontbedingung (`netz.WegLod.landflucht`) = eigen auf 0,000 s.

**Umsetzung (Phase 16a).** Entscheidungen: Gitter 10 m im Kern und 50 m in der Szene wie geplant; gerichtet, je Richtung eine Kante; Spaltenzone aus ITS_LIVE; Bäche nach Mittelwasser gesperrt; Washburns Route 1934 nachrechnen. Belegt (F31, [Dartmouth Alumni Magazine 1935](https://archive.dartmouthalumnimagazine.com/article/1935/4/1/the-harvard-dartmouth-expeditions-to-alaska), [NPS](https://www.nps.gov/glba/learn/historyculture/crillon.htm)): Basislager 1934 am **Crillon Lake**, Arbeiten auf dem **South Crillon Glacier**, Gipfel 19.7.1934 über die **Südwand** (Washburn, Carter); die Linie dazwischen [G].

| Element | gebaut |
|---|---|
| Klassen | Schritt `klassen` (`tools.Klassen`) → `daten/klassen/kern_10m.u8`, `szene_50m.u8` (dazu das Gelände des Rasters, `KLASSEN.txt`): Meer, Wasser, Strand, offen, Moor, Gebüsch, Wald, Fels, Schutt, Eis, Schnee, Spalten, Bach. Quellen: WorldCover 2021 (Kern Zellmitte, Szene häufigste von 5 × 5), Kronenhöhe aus `wald` im Kern (≥ 5 m Wald, 1–5 m Gebüsch), RGI 7, Fels ab 35°, Strand bis 8 m über MW und 80 m vom Meer [G]. **Spalten:** größte Hauptdehnungsrate (Zug) aus ITS_LIVE ab 0,05 /a (≈ 90 kPa mit A = 2,4·10⁻²⁴, unteres Ende der Bruchspannung nach Vaughan 1993) [G]. **Bäche** aus `LB_NET_WASSER` mit Mittelwasser über 0,5 m³/s (3 000 mm/a, Beiwert Land 0,7, Eis 1,0 [G]; ab rund 7,5 km²), nicht auf dem Eis. |
| Netz | Schritt `weg` (`tools.Weg`, `scene.WegNetz`) → `daten/netz/weg` **binär** (`knoten.bin`, `kanten.bin`; Format in `WEG.txt`), `umstieg.tsv`, `washburn.tsv`, `pruefwege.tsv`, `zeit_kern_10m.r32`. Knoten = begehbare Zellen; Kanten zu 8 Nachbarn, je Richtung eine, keine Diagonale zwischen zwei Sperren, steiler als 45° gesperrt [G]; Kosten = Tobler × Mittel der Zuschläge. Ebene 1 und 2 ohne Kanten zueinander, Elternknoten = 50-m-Zelle. Umstieg: je Knoten von `LB_NET_SEE` der nächste Strand bis 300 m. Landeplätze und Wegpunkte im größten zusammenhängenden Teil (Befund: der Strand am Ankerplatz der Edrie und der GNIS-Punkt des South Crillon Glacier liegen in kleinen Taschen zwischen Sperren). |
| Rechnung | eigener Dijkstra (`WegNetz`); LOD `withinCost`, `shortestPathDijkstra`, `kShortestPaths` (`netz.WegLod`) im Speicher auf einem Ausschnitt und in Oracle auf dem ganzen Netz. |
| Ergebnis (Probelauf mit deinen Daten) | **3 506 647 Knoten** (Ebene 1 2 711 312, Ebene 2 795 335), **26 412 374 Kanten**; 1 255 014 zu steil. Im Kern 37 % Wald und Gebüsch, in der Szene 43 % des Eises Spaltenzone. **1 h vom Strand:** Anchorage Cove 4,10 km² (bis 4,2 km Luftlinie), Edrie 7,59 km² (bis 2,9 km); auf 50 m 3,58 / 7,86 km². **Washburn 1934:** 28,4 km, 4 629 m Aufstieg, 875 m Abstieg, **20,2 h Gehzeit** (Naismith 13,4 h), Gipfel auf 50 m 3 854 m. LOD im Speicher = Dijkstra (28 804 Knoten bis 0,000 s, Abschnitte gleich, k = 3: 42 885 / 42 885 / 42 886 s). |
| Oracle | `db/netz/11_weg.sql` (`CREATE_LOGICAL_NETWORK(…, 2, TRUE, FALSE)`: **logisch**, weil Kanten ohne Linie; Lage als Punkt in `LAGE` mit Höhe; `LB_WEG_UMSTIEG`, `LB_WEG_ROUTE`), `12_weg_nach_dem_laden.sql` (Lage, Indizes, **Partitionen in festen Kacheln** 700 m / 3,5 km, BLOBs je Ebene, Probe 32, Sicht `LB_V_WEG_KLASSE`), `tools.WegDb` (Direktpfad in Blöcken zu 50 000); `ant netzdb` mit 12 GB, `-Dlituya.weg=false` lässt das Netz aus. |

### 7.6 `LB_NET_SICHT` — Sicht und Kamera

| Element | Festlegung |
|---|---|
| Knoten | Aussichtspunkte, Gipfel, Ankerplätze, Kamerapunkte (≈ 200) |
| Kanten | freie Sicht im Höhenmodell (Strahl mit Erdkrümmung), Kosten = Flugzeit der Kamera |
| Analysen | `tsp` für den Rundflug über alle Motive, `mcst` (minimaler Spannbaum) für die Kamerafahrt, `nearestNeighbors` |
| Für die Szene | Regiebücher und Kamera-Presets (11) |

### 7.7 `LB_NET_HERKUNFT` — woher jede Zahl kommt (logisch)

Quelle → Rohdatei → Aufbereitung → Befund → Szenenobjekt, gerichtet, ohne Geometrie. `traceIn(Szenenobjekt)` zeigt die Quellen; Befunde ohne Weg zu einer Quelle sind [G]; nach Abschluss der Nachweis, welche Datei auf der Wechselfestplatte wofür gebraucht wurde. Erstes Netz in Phase 3, weil klein.

Umsetzung Phase 3: Schritt `herkunft` der Aufbereitung (`tools.Herkunft`) schreibt `daten/netz/herkunft/` (`knoten.tsv`, `kanten.tsv`, `pruefwege.tsv`, `HERKUNFT.txt`). Knoten: je Quelle des Kartenmaterials (34), je Rohdatei aus `download/inventar.csv` (Entscheidung Phase 3: je Datei, nicht je Quelle), je Schritt, je Ausgabe in `daten/` (mit SHA-256), je Szenenobjekt, je Befund. Kanten: Quelle enthält Rohdatei; Rohdatei liefert Ausgabe (welche Datei unter welcher Kachel lag, mit denselben Quellen und derselben Überdeckung wie die Aufbereitung, ohne den Vorrang); Schritt erzeugt Ausgabe; gröbere Stufe → feinere (`GROB`), Wassermaske → Lotungsraster (`MASKE`); Rohdatei belegt Befund, Befund gilt für Ausgabe; Ausgabe zeigt Szenenobjekt. Kosten 1 je Kante. `ant netzdb` lädt, partitioniert logisch, prüft mit `VALIDATE_NETWORK`, CONNECT BY (alle Ausgaben) und `traceIn` mit LOD (Auswahl) gegen `pruefwege.tsv` und trägt die Prüfsumme in `LB_NETZ_STAND` ein.

### 7.8 Abnahme-Matrix

| Fähigkeit | Netz |
|---|---|
| räumliches Netz mit Höhe, gerichtet und ungerichtet | 7.1–7.6 |
| logisches Netz | 7.7 |
| LRS-Netz, dynamische Segmentierung | 7.2 |
| hierarchisches Netz | 7.4, 7.5 |
| mehrere Kosten, eigener `LinkCostCalculator` | 7.1, 7.3, 7.5 |
| zeitabhängige Kosten, Einschränkungen | 7.3 |
| kürzester Weg (Dijkstra, A*), *k* kürzeste Wege | 7.1, 7.3, 7.5 |
| `withinCost`, Isochronen, Network Buffer | 7.1, 7.5 |
| `nearestNeighbors` | 7.1, 7.6 |
| TSP, minimaler Spannbaum | 7.6 |
| `traceIn`/`traceOut`, Erreichbarkeit, Zusammenhangskomponenten | 7.4, 7.7 |
| Feature-Layer | 7.2 |
| Partitionen, Partition-BLOBs, LOD | 7.1, 7.5 |
| `VALIDATE_NETWORK`, Prüfwege gegen `route` | alle |
| Property Graph, SQL/PGQ | alle, nur 23ai (F39) |

### 7.9 Ladeablauf (wie Timmendorf)

`db/netz/00_sys.sql` (Rechte direkt, `DATA_PUMP_DIR`; einmal als SYS in SQL Developer), `01_tabellen.sql`, `02_netze.sql` (`CREATE_*_NETWORK`, Zusatzspalten, Raum-Metadaten inkl. `_PATH$`), Laden aus `daten/netz/` über `tools.NetzDb` (JDBC, Batches), `04_nach_dem_laden.sql` (Indizes, Partitionen, BLOBs), `05_sichten.sql` (QGIS), `99_abbau.sql`; dazu `03_probe.sql` und `98_probe_abbau.sql` für die Funktionsprobe (2.8). Jede Anweisung endet mit einer Zeile `/` (SQL Developer, SQL*Plus und `NetzDb` lesen dasselbe). `ant netzdb` (Phase 3: 99, 01, 02, Laden, 03, Java-Probe, 98, 04, 05, Prüfungen, `LB_NETZ_STAND`), `ant netzvergleich` (Prüfwege LOD gegen `route`, mit Phase 7a), `ant dbpruefung`. Seit Phase 7a lädt `ant netzdb` nach dem Herkunftsnetz auch `LB_NET_WELLE` und `LB_NET_UFER` (`tools.WelleDb`), baut die Linien des Ufers mit `06_ufer.sql`, partitioniert in `04` und rechnet nach (LOD gegen `route`, `SDO_LRS` gegen `Measure`); `ant netzvergleich` rechnet nur nach.

### 7.10 Netzblick

Bedienfeld NETZE: Ebenen Isochronen (laufen mit der Welle), Wellenstrahlen, Ufer mit Spurlinien und Auflaufhöhen, Gewässer, Wege, Sicht, Herkunft einer angeklickten Zahl; gezeichnet mit `netview`, im Kino als Bild im Bild.

**Umgesetzt in Phase 7a** (Entscheidungen: Bild im Bild und in der Szene; die App liest die Dateien, Oracle wahlweise): `gl.Netzblick` mit `netview.gl.NetviewGl`. Bild im Bild unten rechts (30 % der Breite, Taste N): Draufsicht der Bucht mit dem Grund, Isochronen alle 10 s (jede Minute kräftig), Wellenstrahlen zu Cenotaph Island, Anchorage Cove und Eingang, Einschlaglinie, Ufer und Aufläufe in der Farbe ihrer Höhe, Zeit und Ankunftszeiten. In der Szene dieselben Ebenen 1,5 m über dem Pegel, mit Tiefe gegen das Gelände, im HDR-Puffer mit `gain` = 1/Belichtung (neu in `netview`). Die Zeit kommt aus der Simulationsuhr: die Welle startet 105 s nach dem ersten Stoß [G] (`WellenNetz.START_S`). Bedienfeld NETZE: Bild im Bild, in der Szene, Kostenart (heute, 1940, 1959, nichtlinear), alle Isochronen ohne Zeit, Ebenen, „Welle: Einschlag“ und „+ 6 min“. Die Werte aus Oracle (`LB_ANKUNFT`) zeigt die App erst mit Phase 19 (Fragenblock II).

---

## 8. Texturen

Grundsatz: echte Texturen, wo das Auge nah ist; Luftbild und Klassen in der Ferne; nirgends sichtbare Kachelung (Hex-Tiling in `surface`).

**Quellen** (Herkunft und Lizenz je Textur, Paketvertrag 15): CC0-Bibliotheken (Poly Haven, ambientCG, Lizenz in Phase 0b geprüft), eigene Assets in `templates/` (gebacken wie `SandAsset`), Makrofarbe aus Sentinel-2 und Radarbild, Kronenhöhe aus dem DSM; Fotos von Miller nur als Vorlage für Farbe und Zustand.

| Material | wo | Hinweis |
|---|---|---|
| Schiefer, verwittert (Amphibol-, Biotitschiefer) | Wände, Grate | Schieferung sichtbar, Flechten, Nässestreifen |
| Schiefer, frischer Bruch | Rutschnarbe, abgeräumte Hänge | hell, scharfkantig; 1958 frisch, heute angewittert |
| Gletschereis | Fronten, Spalten | blau in Spalten, weiß oben, Schuttbänder |
| Firn und Schnee | Gipfel, Firnfelder | Sastrugi |
| Moräne, Schutt, Geröll | Vorfeld, Kegel | mit `scree` |
| Strandkies und Gerölle | Ufer, La Chaussee Spit | nass und trocken |
| Sand und Gletschermehl | Flachwasser, Deltas, Fish Lake | |
| Waldboden mit Nadeln und Moos | Regenwald | mit `understory` |
| Moos, Bartflechte | Boden, Stämme, Steine | |
| Rinde Sitka-Fichte, Hemlock | Bäume | |
| entrindetes Holz | Stämme im Band 1958 | hell, splittrig; Treibholz silbergrau |
| Seetang, Kelp | Ufer, Wasser | |
| Kutter: Holz, Farbe, Metall, Tau | `vessel` | 1950er |

Budget: VRAM 2 GB; Texturen ≤ 600 MB (F19), gestreamt über `clipmap`; Kontaktbogen `docs/status/Texturen.png`.

**Sätze (Phase 5, geprüft 01.11.2026 über die ambientCG-API, alle CC0 1.0, je 2K-JPG):** Strandkies `Gravel041`, Sand `Ground054`, Schiefer verwittert `Rock063`, Schiefer frisch `Rock058`, Moräne `Ground108`, Waldboden `Ground037`, Moos `Moss002`, Firn `Snow014`, Gletschereis `Ice003` (blau getönt [G]), Rinde `Bark014`, entrindetes Holz `Wood035` (silbergrau [G]), Kutter Holz `PaintedWood009C`, Kutter Tau `Rope001`. **Seetang:** kein CC0-Satz gefunden; aus Moos abgeleitet, olivbraun getönt [G]. Kutter-Metall und -Farbe folgen mit `vessel` (Phase 15). Quelle `tex_<kurzname>` in `tools/kartenmaterial` (Gruppe `texturen`), Lizenzliste `daten/texturen/LIZENZEN.txt`.

---

## 9. Physik in Kurzform

Formeln mit Quelle; Zahlen aus 2.1; Annahmen [G]. Die Kette folgt der Zeitleiste.

### 9.1 Das Beben (`quake`)

**Bruch.** Empirische Skalierung nach WELLS94 für Blattverschiebungen [zu prüfen]: $\log_{10} L_{\text{Oberfl.}} = -3{,}55 + 0{,}74\,M$, $\log_{10} D_{\max} = -7{,}03 + 1{,}03\,M$. Für $M = 7{,}8$: $L \approx 170$ km, $D_{\max} \approx 10$ m; gemessen am Kopf der Bucht 6,4 m (TOCHER59). Der Bruch läuft mit $v_r \approx 0{,}8\,v_S \approx 2{,}8$ km/s.

**Laufzeiten.** $t_P = D/v_P$, $t_S = D/v_S$ mit $v_P \approx 6{,}0$, $v_S \approx 3{,}5$ km/s in der Kruste [G]; die S-Welle bringt das Schütteln, $t_S - t_P \approx D/8{,}4$ km/s. Der Abstand $D$ zum Hypozentrum kommt aus COMCAT.

**Stärke am Ort.** Abminderungsgesetz der Form $\ln \text{PGA} = c_1 + c_2 M - c_3 \ln\sqrt{R^2 + h_0^2} - c_4 R$ (Koeffizienten aus einer aktuellen GMPE für Kruste [zu prüfen]). Arias-Intensität $I_A = \frac{\pi}{2g}\int a^2\,dt$. Dauer des starken Schüttelns 60–240 s (Zeugen).

**Hangversagen nach Newmark (NEWMARK65).** Kritische Beschleunigung eines Blocks auf dem Hang $\alpha$ mit Sicherheitsfaktor FS: $a_c = (\text{FS} - 1)\,g\sin\alpha$. Verschiebung $D$ durch doppelte Integration von $a(t) - a_c$, solange $a > a_c$. Faustformel JIBSON93 [zu prüfen]: $\log_{10} D_{\text{cm}} = 1{,}521\log_{10} I_A - 1{,}993\log_{10} a_c - 1{,}546$. Ab $D \gtrsim 10$ cm gilt der Hang als versagt → `landslide`.

**Sicherheitsfaktor des Hangs** (unendlicher Hang, Kluftfläche fallend mit $\alpha = 40°$, Schiefer $\varphi' \approx 35°$, Kohäsion $c'$ an den Klüften [G]):
$$\text{FS} = \frac{c' + \gamma z \cos^2\alpha\,\tan\varphi'}{\gamma z \sin\alpha\cos\alpha}, \qquad \frac{\tan\varphi'}{\tan\alpha} = 0{,}83 < 1$$
Ohne Kohäsion wäre die Platte schon in Ruhe instabil; die Kohäsion der Klüfte hält sie, das Beben nimmt sie ihr (pseudo-statisch mit $k$: Zähler um $k\sin\alpha\cos\alpha$ vermindert, Nenner um $k\cos^2\alpha$ vermehrt). Dass der Sturz erst 1–2½ min nach dem ersten Stoß kam, passt zur Newmark-Verschiebung, die sich über die Dauer aufsummiert.

**Befund Phase 8 (`quake`):** Mit dem Bruch nach WELLS94 (167 km, ¾ nach Nordwesten [G]) läuft die Bruchfront nach 24 s am Gilbert Inlet vorbei: PGA 0,51 g (Rjb 0,5 km), Arias 4,8 m/s, starkes Schütteln (D5–95) von 22 bis 51 s. Ein Newmark-Block mit FS 1,05 bis 1,15 versagt schon nach 23 bis 27 s, mit FS ≥ 1,20 gar nicht. Die 1 bis 2½ min der Zeugen erklärt das Modell so nicht: Das Schütteln ist zu kurz (Zeugen 1 bis 4 min). Möglich sind ein längerer Bruch (für 1958 werden auch rund 260 km genannt [zu prüfen]), Abnahme der Festigkeit beim Gleiten (progressives Versagen) oder Wasser in den Klüften; das bleibt für Phase 9 (`landslide`, Auslösung) offen. **Phase 9:** auch mit Entfestigung nicht (Befund in 9.2).

**Seiche der Bucht** (Merian): $T_n = \frac{2L}{n\sqrt{g\bar h}}$; mit $L = 12$ km, $\bar h \approx 130$ m: $T_1 \approx 11$ min [G]. Das Beben regt sie schwach an, die Welle stark; sie ist das lange Nachschwingen nach t = 600 s.

### 9.2 Der Felssturz bis zum Wasser (`landslide`)

**Energie.** $E_{\text{pot}} = m\,g\,\Delta h = 8{,}3\cdot10^{10}\,\text{kg}\cdot 9{,}81\cdot 610\,\text{m} = 4{,}9\cdot10^{14}$ J ≈ 118 kt TNT (gerechnet).

**Geschwindigkeit am Wasser.** Freier Fall vom Schwerpunkt: $v = \sqrt{2 g\,\Delta h} = \sqrt{2\cdot 9{,}81\cdot 610} = 109$ m/s, die obere Grenze (FRITZ01 nimmt 110 m/s). Gleiten mit Reibung $\mu$ über $L = 970$ m bei $\alpha = 40°$:
$$v = \sqrt{2 g L\,(\sin\alpha - \mu\cos\alpha)}$$
$\mu = 0$: 111 m/s; $\mu = 0{,}1$: 104 m/s; $\mu = 0{,}2$: 96 m/s. `landslide.gilbertInlet1958()` muss zwischen 96 und 110 m/s landen; die Froude-Zahl dann 2,8–3,2.

**Granularstrom** (SAVAGEHUTTER89, tiefengemittelt, Höhe $h_s$, Geschwindigkeit $\mathbf{u}$ entlang des Hangs):
$$\partial_t h_s + \nabla\cdot(h_s\mathbf{u}) = 0, \qquad \partial_t(h_s\mathbf{u}) + \nabla\cdot(h_s\mathbf{u}\otimes\mathbf{u}) + \nabla\!\left(\tfrac{1}{2}k_{a/p}\,g_\perp h_s^2\right) = h_s\,\mathbf{g}_\parallel - \mathbf{S}_f$$
mit Erddruckbeiwert $k_{a/p}$ (aktiv bei Dehnung, passiv bei Stauchung) und Reibung nach VOELLMY55: $S_f = \mu\,g_\perp h_s + g\,\lvert\mathbf{u}\rvert^2/\xi$, Fels $\mu \approx 0{,}1$–$0{,}2$, $\xi \approx 500$–$2\,000$ m/s² [G nach Literatur]. **Fahrböschung** (HEIM32): $\tan\varphi_f = H/L_{\text{Reichweite}}$, für $30\cdot10^6$ m³ typisch $\varphi_f \approx 15$–$20°$; hier endet der Strom im Wasser, die Reichweite zeigt sich als Schuttfächer auf dem Grund (H08492 gegen H06581: Prüfung in Phase 2).

**Profil am Wasser.** Im Modell (FRITZ01) wächst die Dicke des Stroms am Ufer langsam auf 134 m (1,4 × Plattendicke 92 m, Auflockerung 39 %) und fällt schnell ab; Länge 748 m, Dauer 6,8 s. `landslide` liefert genau dieses Profil $s(t)$ an `impact`.

**Befund Phase 9 (`landslide`, Werte auf gerechnetem Gelände, 4 m):**
- **Prüfung (`LandslideCheck`, 16 Prüfungen):** starrer Block auf 30° und 40° gegen $\sqrt{2gL(\sin\alpha - \mu\cos\alpha)}$ auf 0,2 %; eine Schicht steht am 10°-Hang bei δ 14° und beschleunigt am 20°-Hang wie von Hand; `inject` bringt Volumen und Impuls genau; Erddruck bei φ = δ gleich (1 + sin²φ)/cos²φ; Volumen bleibt auf 1e-9; zwei Läufe gleich.
- **Fahrböschung:** die Platte der Lituya trocken über 35° in die Ebene mit Fels (μ 0,15, ξ 1 000) 16,2° gegen SCHEIDEGGER73 15,8° und HEIM32 15–20°.
- **Aufprall:** 105 m/s im Mittel nach Rate über die Uferlinie, höchstens 112 m/s (FRITZ01 96–110 m/s); bei 8 m 102 m/s. Dafür braucht es wenig Reibung (μ 0,06, ξ 8 000 [G]); mit μ 0,10 und ξ 2 000 waren es bei 8 m rund 86 m/s.
- **Profil am Wasser:** 90 % des Volumens in 15,7 s (12 bis 27 s nach dem Abbruch), Mächtigkeit am Ufer bis 31 m senkrecht (24 m normal), Rate bis 2,2 Mio. m³/s, Breite bis 1,5 km. FRITZ01: 6,8 s, bis 134 m. Der tiefengemittelte Strom läuft länger und dünner ein als der kompakte Körper im Versuch: Die Platte ist 970 m lang und braucht bei rund 100 m/s schon 10 s über die Uferlinie; dazu verbreitert sie sich auf dem Hang (Erddruck), und Auflockerung (39 % bei FRITZ01) fehlt. Für die Welle (Phase 11) zählen Impuls und Rate; ob das Profil gestaucht werden muss, entscheidet der Laborfall in 6.1. **Phase 10 (`tools.Eintauchen`):** Je Meter Breite bringt der Strom 25 480 m² über 20 s statt 61 090 m² über 5 s; im Schnitt gibt das bei 885 m 69 m statt rund 200 m. Ohne Stauchung trägt die Kette die Welle nicht.
- **Vajont, Spirit Lake:** laufen, Volumen bleibt; Vajont rund 56 m/s gegen 20–30 m/s (die 250 m dicke Scholle fällt als Strom zusammen), Spirit Lake 88 m/s gegen 50–70 m/s. Grenzfälle, keine Rückrechnung.
- **Auslösung:** Newmark mit Entfestigung (`Slope.residual`, `softening`): die NE-Wand geht mit FS 1,10, Rest 20 %, 0,3 m nach 24 s durch. Über 3 226 versagende Fälle (FS 1,02–1,60, Rest 0–95 %, Weg 0,02–3 m) kommen nur 2 nach 60 s (spätestens 62 s, auf einer Messerschneide). Newmark ist starr-plastisch und endet mit dem Schütteln (D95 bei 51 s); 60–150 s brauchen Zeit im Stoff (Porenwasser, Kriechen). Die Szene löst darum von Hand aus (`tools.Sturz`, Modus `zeuge`): Auslösung bei 93 s, Zufluss ab 105 s = Start des Wellennetzes (7.1).
- **Bruch und Strom (`tools.Sturz`):** Platte als Körper in `fracture` (400 Zellen, feiner an der Abrissfläche), die Anker der Gleitfläche tragen 50 % × c(D)/c′ der Festigkeit des Gebirges [G]: mit voller Kohäsion hält die Platte, mit dem Rest 20 % bricht sie in 342 Stücke; jedes Stück über 2 m/s geht als Zellen (Kreis mit 0,62 ∛V) an `landslide`. 3,074e7 m³ übergeben, im Strom auf 1e-14 wieder; Aufprall 105 m/s wie die Platte des Presets.

**Befund Phase 13 (`tools.Ereignis`, hier auf dem Prozessor, LOW 32 / 8 / 4 m, 420 s, Messwelle):** Auflauf am Sporn **390 m** bei E 353 682 N 6 505 962 (MILLER60 524 m), mit der Kette nach 25 s schon 408 m. In der Bucht bleibt die Welle klein: Ankunft über 1 m an Cenotaph Island 143 s, am Eingang 396 s nach dem Eintauchen, Pegel am Eingang nur 1,4 m, Auflauf um Cenotaph Island 10 m (Miller: Spurlinie in der Bucht 30–200 m). Gründe [G]: grob 32 m in der Hauptbucht, die Linie zielt auf den Sporn und nicht in den T-Kopf, der Rückbau des Inlet ist gesetzt, abgeschnittenes Volumen an Trockenfronten über Gittergrenzen (0,5 Mio. m³). MEDIUM auf der Radeon und 13a (Vergleich mit Miller) entscheiden. Rechenzeit hier 2 228 s (0,19 s Welle je Sekunde, zwei Kerne).

**Befund Phase 13 auf der Radeon (MEDIUM 16 / 4 / 2 m, 600 s, Grafikkarte, 5,0–5,7 ns je Zelle, 0,66–0,74 s Welle je Sekunde Rechnung, 15 min je Lauf):**

| | Kette (`impact`) | Messwelle | Bezug |
|---|---|---|---|
| Auflauf am Sporn | **562 m** | 409 m | MILLER60 524 m |
| Ankunft über 1 m an Cenotaph Island (s nach dem Eintauchen) | 122 | 142 | `LB_NET_WELLE` heute 140 |
| … Anchorage Cove | 301 | 351 | 320 |
| … Eingang | 342 | 389 | 365 |
| Auflauf um Cenotaph Island / Anchorage / Eingang | 24 / 13 / 13 m | 12 / 6 / 6 m | Spurlinie 30–200 m [zu prüfen, 13a] |
| Pegel am Eingang | 7,4 m | 3,2 m | |
| abgeschnitten | 0,18 Mio. m³ | 0,14 Mio. m³ | |

Die Kette trifft den Sporn auf 7 %, die Front kommt 15–25 s vor der linearen Rechnung des Netzes (nichtlinear schneller, erwartet). Daten 853 MB (Kette) und 644 MB (Messwelle). **LOW auf der Grafikkarte brach ab** (NaN nach dem Freigeben der Linie, Auflauf 10¹⁵ m): dünne Filme hoch am Hang bekommen in float Geschwindigkeiten bis 10⁵ m/s; seit 13a begrenzt `BayGrid.maxVelocity` auf 150 m/s und macht NaN zu trocken. Der LOW-Lauf hatte außerdem die MEDIUM-Daten überschrieben; seit 13a schreibt LOW nach `<lauf>_LOW`.

**Befund Phase 12 (`tools.Staubwolke`):** Der Sturz des Presets (8 m) gibt mit Abrieb k = 7e-6 je m [G] rund 0,2 Mio. m³ Staub (0,7 % des Volumens) in Wolke mit 1e-3 Anteil; die Wolke füllt den Inlet als dichte Schicht von rund 100–200 m, nach 5 min liegen 25 % im Wasser und 11 % an Land, der Rest hängt noch in der Luft (Sicht im Inlet unter 1 m). Wieviel Staub der Sturz wirklich machte, ist nicht belegt [G].

**Staub** (`dustcloud`): Schwerestrom mit $g' = g\,\Delta\rho/\rho_{\text{Luft}}$, Frontgeschwindigkeit $u_f \approx 0{,}5\sqrt{g' h_{\text{Wolke}}}$ (Benjamin); Sinkgeschwindigkeit der Körner nach Stokes $w = \frac{(\rho_s - \rho)\,g\,d^2}{18\,\eta}$, für $d = 50$ µm rund 0,2 m/s, für 5 µm 2 mm/s: Die Wolke bleibt Stunden [G].

### 9.3 Das Eintauchen (`impact`)

**Kennzahlen** (FRITZ01, HELLER10): Froude-Zahl $F = v_s/\sqrt{g h} = 3{,}18$; relative Dicke $S = s/h = 0{,}75$ (Platte) bis 1,1 (Strom); relative Masse $M = m'/(\rho_w h^2) = 98{,}5\cdot10^3/(1\cdot 122^2) = 6{,}6$; Aufschlagwinkel $\alpha = 45°$.

**Impulsprodukt-Parameter** (HELLER10, [zu prüfen am Original]):
$$P = F\,S^{1/2}\,M^{1/4}\,\left[\cos\!\left(\tfrac{6}{7}\alpha\right)\right]^{1/2}, \qquad \frac{a_M}{h} = \frac{4}{9}\,P^{4/5}$$
Mit $S = 0{,}75$: $P = 3{,}9$, $a_M = 162$ m. **Gemessen 152 m (Amplitude) bzw. 162 m (Höhe)**: Die Skalierung trifft. Mit $S = 1{,}1$ (Strom): $a_M = 188$ m, auch das im Bereich (über 200 m bei x = 600 m). Die ältere Formel HUBERHAGER97 $H = 0{,}88\sin\alpha\,(\rho_s/\rho_w)^{1/4}(V_s/b)^{1/2}(h/x)^{1/4}$ gibt nur 94 m (Faktor 1,8 zu klein, FRITZ01), weil sie flachere Gleitkörper voraussetzt.

**Energie.** Gleitenergie je Breite $E'_s = \tfrac{1}{2} m' v_s^2 = 6{,}0\cdot10^{11}$ J/m. Energie der Solitärwelle (Boussinesq) $E'_w = \frac{8}{3\sqrt 3}\,\rho g h^3 (H/h)^{3/2} = 4{,}2\cdot10^{10}$ J/m: **7 %** der Gleitenergie gehen in die Welle (gerechnet; Literatur 2–30 %). Der Rest: Reibung, Kaverne, Phasenmischung, Wärme. `impact` muss diesen Anteil treffen (Check 5.3).

**Zwei Stufen** (FRITZ01, PIV): (a) Aufschlag und Eindringen, Strömungsabriss an der Schulter des Stroms, **Luftkaverne** hinter dem Strom, weil die Eindringgeschwindigkeit die Wellengeschwindigkeit übersteigt; die Welle läuft aus dem Aufschlagbereich, bevor die Kaverne zusammenbricht; (b) Zusammenbruch der Kaverne, Auslauf des Gesteins am Grund, Lufteinschluss, Blasen, Phasenmischung. Die führende Welle bleibt davon unberührt: Das ist der Grund, warum das Fernfeld (`wave`) sauber am Rand des Würfels abgeholt werden kann.

**Modellgesetz.** Froude-Ähnlichkeit 1:675: Längen $\lambda$, Zeiten $\sqrt\lambda$, Geschwindigkeiten $\sqrt\lambda$. Maßstabseffekte durch Zähigkeit und Oberflächenspannung unter 5 % bei $H_{\text{Modell}} = 0{,}24$ m (FRITZ01). `ImpactCheck` rechnet in Naturmaßen und vergleicht mit den auf Natur skalierten Messwerten.

**Befund Phase 10 (`impact`, Schnitt, Laborfall in Naturmaßen):**
- **Wasser:** Becken 40 m: Druck am Grund im Mittel 405 gegen 402 kPa. Einzelwelle 8 m auf 40 m läuft mit 21,7 m/s = √(g(h + H)); Höhe bei 2 m Abstand 70 % nach 24 s, bei 1 m 92 % (Auflösung).
- **Granulat:** Schüttung 25° steht (φ 35°), 55° sackt auf 34,5°.
- **Laborfall (5 m):** bei 600 m über 200 m ab 9,6 s (FRITZ01 11,1 s); **bei 885 m 205 m nach 15,8 s (FRITZ01 152 m nach 16 s)**; Kaverne bis 2 900 m² je Meter; Energie der führenden Welle 13,6 % der Gleitenergie an der Uferlinie (4,2e11 J/m; ungebremst wären es 6,0e11, das Wasser bremst schon auf der Rampe), im Band 2–30 %; Masse genau.
- **Höhe bei 885 m über die Läufe:** ohne Poren 229 / 206 / 221 m (8 / 5 / 4 m, konvergiert); mit Poren 167–210 m je nach Einstellung. Kleine Änderungen verschieben ±20 %; im Mittel rund 30 % über FRITZ01. **Abnahme mit Hinweis** (Nutzer, 02.11.2026): Ankunft ±2 s, Höhe −20 % bis +50 %. Vermutete Ursache: Nur ein Zehntel des Porenraums füllt sich (nur Körner am Rand berühren Wasser); Phase 10b.
- **Übergabe:** am Schnitt 885 m an einen HLL-Kanal: bei 1 100 m 190 m gegen 180 m im SPH. Der gemessene Durchfluss enthält am Rand noch die überkippende Wand; für `wave` (Phase 11) entweder den Rand hinter die Wand legen oder den Durchfluss aus der Welle nehmen.

### 9.4 Die Welle in der Bucht (`wave`)

**Ist sie lang?** Ursell-Zahl $U = H L^2/h^3 = 162\cdot 1100^2/122^3 \approx 110 \gg 1$: nichtlineare lange Welle, Dispersion schwach → Flachwassergleichungen mit Stoßerfassung (F12 A), Boussinesq-Korrektur nur als Prüfung.

**Gleichungen** (Flussform, Arakawa C wie bisher in `wave`, $D = h + \eta$):
$$\partial_t \eta + \nabla\cdot\mathbf{q} = 0, \qquad \partial_t\mathbf{q} + \nabla\cdot\!\left(\frac{\mathbf{q}\otimes\mathbf{q}}{D}\right) + g D\,\nabla\eta = -\frac{g\,n^2\,\lvert\mathbf{q}\rvert\,\mathbf{q}}{D^{7/3}}$$
Manning $n \approx 0{,}03$ über Fels, 0,1 über Wald [G]. Stoßerfassung mit HLL-Flüssen, damit die Bore am Eingang nicht oszilliert. CFL: $\Delta t \le 0{,}45\,\Delta x/(c\sqrt2)$; mit $c = \sqrt{g(h+a)} \le 61$ m/s: 16 m → 0,08 s, 4 m → 0,02 s, 2 m → 0,01 s. Zehn Minuten Ereignis sind 7 500 Schritte auf dem groben und 60 000 auf dem feinen Gitter: GPU (6.1).

**Solitärwelle als Grenzfall.** $\eta = H\,\mathrm{sech}^2\!\left[\sqrt{\tfrac{3H}{4h^3}}\,(x - ct)\right]$, $c = \sqrt{g(h + H)}$. Im Inlet: linear $\sqrt{g h} = 34{,}6$ m/s, nichtlinear mit $a = 152$ m: 51,8 m/s; **beobachtet** 885 m in 16 s = 55 m/s (FRITZ01). Die Amplitude macht die Welle anderthalbmal so schnell: Darum hat `LB_NET_WELLE` die Kostenart `T_NICHTLINEAR`.

**Laufzeit nach Fermat.** $T = \min\int ds/c(\mathbf{x})$, Eikonal $\lvert\nabla T\rvert = 1/c$. Auf einem Gitter ersetzt der kürzeste Weg die Eikonalgleichung; der Fehler ist der Winkelfehler des Nachbarsterns: $\epsilon = 1/\cos(\theta_{\max}/2) - 1$ mit $\theta_{\max} = 26{,}6°$ bei 16 Nachbarn → 2,75 %, 14,0° bei 32 Nachbarn → 0,76 %. `RouteCheck` prüft genau diese Werte auf konstantem Grund (6.9). Handwert für die Bucht: 12 km bei $\bar h \approx 130$ m linear → 350 s; nichtlinear mit $a \approx 30$ m im Stamm → 300 s: **Die Front braucht rund fünf Minuten bis zum Eingang** [gerechnet, Prüfung in 13a].

**Shoaling und Einschnürung** (linear, Green): $H \propto h^{-1/4} b^{-1/2}$. Vom Stamm (h ≈ 150 m, b ≈ 2,5 km) zum Eingang (10 m, 300 m): Faktor $(150/10)^{1/4}(2500/300)^{1/2} \approx 5{,}7$, weit über dem Brechkriterium $H/h \approx 0{,}78$ (McCowan): **Die Welle bricht lange vor dem Eingang zur Bore**, die Badger wird darauf über die Nehrung getragen. Bore-Geschwindigkeit aus den Sprungbedingungen $c_b = \sqrt{g\,h_2\,(h_1 + h_2)/(2 h_1)}$.

**Diffraktion und Reflexion.** Cenotaph Island teilt die Front; hinter der Insel schließt sie sich (Zeugen: „jumping and shaking“ durch Reflexionen im Inlet, FRITZ01). Die Eikonal-Isochronen zeigen die Teilung, `wave` die Reflexionen; der Unterschied ist das Lehrbild im Netzblick.

### 9.5 Der Auflauf am Sporn (`wave`, `impact`)

**Empirisch** (FRITZ01), $H = 162$ m, $h = 122$ m, $\beta = 45°$:

| Formel | Quelle | $R$ |
|---|---|---|
| $R/h = 3{,}1\,(H/h)^{1{,}15}$ | HALLWATTS53 | 524 m |
| $R/h = 2{,}831\sqrt{\cot\beta}\,(H/h)^{5/4}$ | SYNOLAKIS87 (nicht brechend, gültig für $H/h \le 0{,}818\,(\cot\beta)^{-9/10}$) | 492 m |
| $R/h = 1{,}25\,(\pi/2\beta)^{0{,}2}(H/h)^{1{,}25}(H/L)^{-0{,}15}$ | MUELLER95 (gültig $H/h \le 0{,}5$) | 333 m |

Hall & Watts trifft die Natur (524 m) und das Modell (526 m); Synolakis liegt 6 % darunter; Müller ist außerhalb seines Bereichs. **Energiegrenze:** Ein Teilchen mit $v$ am Fuß steigt höchstens $v^2/2g$; für 524 m wären 101 m/s nötig, was die PIV-Felder (bis 50 m/s Pfeile, Spitzen höher) plausibel machen: Der Auflauf ist Umwandlung von Bewegungs- in Lageenergie, mit Reibung am Hang und dünner werdender Schicht (Halbsattel als Stagnationspunkt nach 30 s, Höchststand nach 35 s, FRITZ01).

**Für `wave`:** Nass-trocken-Rand auf 45°; die Schicht wird dünner als 5 m (Pegel fallen aus, FRITZ01); Miller: Wasser 10 m über die höchsten umgelegten Bäume. Abnahme 6.1: Auflauf 526 ± 50 m im Laborfall.

**Befund Phase 11 (`wave.Bay`, Schnitt mit 2 m):** Einzelwelle 152 m auf 122 m als Einlass bei 885 m (Kamm nach 16 s), Grund eben bis 1 220 m, Gegenhang 45°: **Auflauf 542 m** (FRITZ01 526 m, Hall-Watts 524 m), Pegel bei 1 100 m 137 m nach 18,7 s. Aus `impact` (Laborfall 8 m, bei 885 m 235 m) über `Impact.Far`: 684 m mit dem gemessenen Durchfluss, 759 m mit dem Durchfluss aus der Welle (`tools.Auflauf`); das ist die zu hohe Welle aus Phase 10. Synolakis zur Kontrolle: R/d 0,0917 gegen 0,0890.

### 9.6 Die Welle am Südufer und am Eingang

Südufer bei Mudslide Creek 208 m (belegt): Die Welle schwenkt um den Sporn, läuft quer über die Bucht und am Gegenufer auf; WIEGEL64 beobachtete das im Modell 1:1000. Das ist der Prüfpunkt für das mittlere Gitter. Am Eingang: Bore über die Barre (10 m), die Nehrung La Chaussee Spit wird überspült (Badger).

### 9.7 Wellenwurf (`succession`, `forest`)

**Kraft auf einen Baum** im Wasser mit $u$: $F_D = \tfrac{1}{2}\rho_w C_D A\,u^2$, Krone $A \approx 100$ m², $C_D \approx 1$ (durchströmt). **Bruch** am Stammfuß, Biegemoment $M = F_D h_c$ ($h_c \approx 20$ m), Widerstandsmoment $W = \pi d^3/32$, Bruchspannung grün $\sigma_b \approx 40$ MPa (Sitka-Fichte, WOODHB [zu prüfen]). Kritische Geschwindigkeit:
$$u_{\text{krit}} = \sqrt{\frac{2\,\sigma_b\,W}{\rho_w\,C_D\,A\,h_c}} \approx 1{,}4\ \text{m/s} \quad (d = 0{,}8\ \text{m})$$
Zum Vergleich: Orkan mit 40 m/s in Luft ($\rho = 1{,}2$) entspricht 1,4 m/s in Wasser. **Jede Strömung, die die Krone erfasst, bricht den Baum**; WIEGEL64 schätzte die Kräfte auf das Zehnfache des Nötigen. Deshalb ist die Spurlinie so scharf: Sie ist die Linie des höchsten Wasserstands, nicht einer Kraft. Oberhalb bleibt der Wald unberührt. Entwurzeln gegen Brechen entscheidet die Wurzelplatte (dünne Böden auf Fels → Entwurzeln, Boden bis zum Fels abgeräumt, FRITZ01 Abb. 5a). **Entrindung** durch Abrasion mit Sediment im Strom; **Richtung** der umgelegten Stämme = Strömungsrichtung beim Rückfluss [G].

**Umgesetzt in Phase 14** (`succession.Wellenwurf`): Stamm bis zum Kronenansatz (Sitka 0,15 H, Roterle 0,3 H), Krone ∝ H² (100 m² bei 40 m) über die Kronenhöhe verteilt; Wurzelplatte 1,2-fach des Stamms auf tiefem Boden (der Stamm bricht), 0,6-fach über 30° (entwurzelt) [G]; entrindet ab 4 m/s und 1 m Tiefe, bis zum Fels abgeräumt ab 20 m Tiefe und 20 m/s [G]; u = 1,5·√(g h) [G]. Sitka-Fichte 40 m: 0,5 m Wasser steht, 3 m entrindet, 10 m gebrochen (40°: umgelegt), 30 m fort.

**Wiederbewaldung:** Höhe $H(t)$ nach einer Wuchskurve (Chapman-Richards) $H = H_\infty\,(1 - e^{-kt})^p$ mit Artparametern [G nach Ertragstafeln, zu prüfen]; Abgleich: Kronenhöhe im Band (DSM 2012, 54 Jahre) gegen außerhalb.

### 9.8 Wege über Gelände (`LB_NET_WEG`)

Tobler: $v = 6\,\exp\!\left(-3{,}5\,\lvert\tan\theta + 0{,}05\rvert\right)$ km/h; Naismith zum Vergleich: 1 h je 5 km + 1 h je 600 m Aufstieg.

### 9.9 Gletschermilch (`ocean`)

Schwebende Gletschermehl-Körner (1–10 µm) streuen nach Mie fast farbneutral, das Wasser absorbiert Rot ($a_w(650\,\text{nm}) \approx 0{,}35$ m⁻¹ gegen $0{,}02$ im Blaugrün); die Rückstreuung aus den obersten Metern ergibt das milchige Türkis. Abschwächung $K_d \approx a + b_b$ mit Trübung; Sichttiefe $z_{\text{Secchi}} \approx 1{,}7/K_d$. Eichung: Farbe aus Sentinel-2 (B2/B3/B4) über der Bucht gegen das offene Meer.

### 9.10 Licht der Julinacht (`sky`)

Sonnenhöhe $\sin h_\odot = \sin\varphi\sin\delta + \cos\varphi\cos\delta\cos H$ mit $\varphi = 58{,}62°$, $\delta = 22{,}3°$ (10. Juli), Stundenwinkel aus der Sonnenzeit (Länge −137,55° → Sonnenmittag 13:10 PST). **Zur Bebenzeit 22:16 PST steht die Sonne 1° unter dem Horizont im NNW**, Sonnenuntergang war um 22:04, bürgerliche Dämmerung bis etwa 23:25, nautische Dämmerung die ganze Nacht (die Sonne sinkt in 58,6° N im Juli nicht unter −12°): **Die Welle kam im hellen Abendlicht**, der Himmel über dem Fairweather noch rot. [gerechnet; gegen die Zeugen und die Zeitzone 1958 zu prüfen]

### 9.11 Horizont und Ferne (`clipmap`, `sky`)

Sichtweite bis zur Kimm mit Refraktion $d \approx 3{,}86\sqrt{H}$ km: 500 m → 86 km, 4 671 m → 264 km. Erdkrümmung senkt ein Objekt in $d$ um $d^2/(2R)\,(1 - k)$, $k = 0{,}13$. Atmosphärische Perspektive: Rayleigh ($\propto\lambda^{-4}$, Skalenhöhe 8 km) und Mie (Dunst, Skalenhöhe 1,2 km); über 60 km verblassen die Berge zu Blau, der Schnee bleibt hell.

### 9.12 Polarlicht (`aurora`)

Emissionen: Sauerstoff 557,7 nm (grün, 100–150 km), 630,0 nm (rot, > 200 km), Stickstoff N₂⁺ 427,8 nm (violett, unten). Lituya Bay liegt bei rund 60° geomagnetischer Breite [zu prüfen]: Polarlicht ab Kp ≈ 4 am Nordhimmel, in Sommernächten unsichtbar (9.10), im Herbst und Winter häufig.

### 9.13 Klang (`soundscape`)

Laufzeit $t = d/340$ m/s: Der Sturz ist in der Anchorage Cove (≈ 10 km) nach 30 s zu hören, die Welle nach 5 min zu sehen. Beben als Infraschall (unter 20 Hz, hörbar gemacht durch Transposition), Felssturz als Breitbandrauschen mit tiefem Grundton, Welle als Rauschen mit Doppler.

---

## 10. Die Szene (`com.dan.lituya`)

### 10.1 Zeitstände (F4)

| Zeitstand | Gelände | Wasser | Wald | Gletscher | Boote |
|---|---|---|---|---|---|
| **vor** (9.7.1958, 22:00) | Lidar + Rückbau 1958 | Grund 1940, ruhig, Tide nach COOPS | bis ans Ufer, alt | Fronten 1958 | drei vor Anker, Licht |
| **die Welle** (22:16 + 10 min) | wie vor, Rutschnarbe entsteht | `wave`, `impact`, Bore am Eingang | Wellenwurf | 400 m abgeschert | Edrie reitet, Badger über die Nehrung, Sunmore verloren |
| **danach** (10.7., Morgen) | Narbe, Schuttfächer | Grund 1959, Holzteppiche, Seiche klingt ab | Band bis 524 m, nackter Fels | neue Front | Wrack |
| **heute** (2026) | Lidar | BlueTopo 2025 | Band 68 Jahre jünger | heutige Fronten | keine |

### 10.2 Bauteile

Gelände über `clipmap` und `surface`; Wasser `ocean` (Gletschermilch) + `wave` + `surf` (Barre); Gletscher `ice`; Wald `forest`/`tree` + `understory` + `succession`; Boden `scree`; Boote `vessel`; Leben `flock`, `seal`, `bear`, `figure`; Himmel `sky`, `cloud`, `fog`, `aurora`; Klang `soundscape`.

### 10.3 Schaustücke

1. **Abend vor Anker** — Anchorage Cove, Spiegel aus Gletschermilch, Alpenglühen am Fairweather, 22:00
2. **Das Beben** — die Bucht zittert, Lawinen an allen Hängen, Eis bricht von der Front
3. **Der Sturz** — die NE-Wand des Gilbert Inlet löst sich, 30 Mio. m³ in 7 s ins Wasser, Staub im Gegenlicht
4. **Die Wand aus Wasser** — Nahfeld in Zeitlupe: Krater, Kaverne, Wasserwand, Strahl
5. **524 m** — die Welle steigt den Sporn hinauf, der Wald verschwindet; ein Mensch, ein Baum, ein Kutter als Maßstab
6. **Die Front** — vor der Front die Bucht hinab, Isochronen im Netzblick
7. **Unter Wasser** — die Front von unten, Geröll und Stämme im Strom
8. **Mit der Edrie** — aus dem Boot, die Ankerkette reißt
9. **Über die Nehrung** — die Badger über La Chaussee Spit
10. **Der Morgen danach** — Spurlinie, Holzteppiche, Staub, Stille
11. **68 Jahre** — Zeitraffer bis heute, die Kante bleibt
12. **Heute bei Nacht** — Polarlicht über dem Fairweather
13. **Die Bucht der Wellen** — 1853, 1874, 1899, 1936, 1958 als Spurlinien
14. **Das Netz** — alle Analysen als Bilder
15. Freie Kamera: Orbit, Zoom, Rundblick, Bodennähe, Boot, Drohne

Seit Phase 13b stehen 1 bis 7 als Vorgaben aus Kamera und Uhr (`camera.Schaustuecke`, Bedienfeld EREIGNIS; Lagen [G]): 1 Ansicht 1 um 22:00; 2 über Cenotaph Island, −5 s; 3 1,5 km südwestlich des Inlet, Auge 150 m, 88 s, 1:2; 4 1 km vor dem Inlet, Auge 80 m, 104,5 s, 1:20; 5 von Südost auf den Sporn, Auge 200 m, 106 s, 1:5; 6 2,5 km nordöstlich von Cenotaph Island, Auge 60 m, 112 s, Echtzeit, Netze in der Szene; 7 1,5 km nordöstlich von Cenotaph Island 3 m unter dem Pegel, Blick 35° hinauf, 115 s, 1:2 (13c: mit 15° lag das Snellsche Fenster über dem Bildrand). Fahrten mit Phase 17; im Beben (2) zittert noch nichts, Geröll und Stämme unter Wasser (7) kommen mit 14.

---

## 11. Regie

### 11.1 Uhrzeit, Tide, Wetter

Weltzeit aus 4.5; Sonne aus 9.10; Tide aus COOPS (Vorausberechnung Juli 1958 und 2026); Wetter: Lage „Sommerabend 1958“ nach ERA5 und Zeugen [zu prüfen], dazu Lagen für „heute“ (Regen, Nebel, Herbststurm, Winter).

### 11.2 Kamera-Presets

Aus `LB_NET_SICHT` (7.6): Rundflug (TSP), Kamerafahrt (Spannbaum), Standorte der Zeugen und Millers Fotostandorte (7.2).

### 11.3 Regiebücher

- **„Die Nacht der Welle“** — 9. Juli 1958 in Echtzeit vom Abend vor Anker bis zum Morgen
- **„Zehn Minuten“** — die Ereigniskette in Zeitlupe mit Netzblick als Bild im Bild
- **„Die drei Boote“** — Edrie, Badger, Sunmore, je aus ihrer Sicht; am Ende „Wer hätte entkommen können?“ aus `LB_NET_SEE`
- **„68 Jahre“** — vom Morgen danach bis heute
- **„Die Bucht der Wellen“** — von La Pérouse 1786 bis heute

Kino (`kino`): Balken 2,39:1, Vor- und Abspann, Untertitel, Zeitlupe, Bild im Bild.

---

## 12. Oberfläche

FFrame maximiert, Bedienfelder in FTheme-Farben: **ANSICHT** (Presets, Fahrten), **ZEIT** (Zeitstand, Weltzeit, Simulationszeit, Zeitlupe, Zeitraffer), **EREIGNIS** (Abspielen/Echtzeit, Volumen, Ort, Tide, Grund 1940/heute), **NETZE** (Ebenen des Netzblicks, Abfragen), **WETTER**, **REGIEBÜCHER**, **DATEN** (Einrichten, Speichern, Laden, Stand der Netze). HUD mit FPS, GPU-Zeit, Simulationszeit, Pegeln. Tasten wie Sylt (F1 Hilfe, F11 nur Szene, F12 Foto).

---

## 13. Datenbank (Phase 19, eigener Fragenblock)

| Tabelle | Inhalt |
|---|---|
| `LB_QUELLE`, `LB_BEFUND`, `LB_BEFUND_QUELLE` | wie Sylt; Befund belegt/gemessen/gerechnet/[G] |
| `LB_NET_*` | sieben Netze (7) |
| `LB_NETZ_STAND` | Prüfsumme je Netz gegen `daten/netz/` |
| `LB_ZEUGE` | Aussagen mit Zeit, Ort, Quelle |
| `LB_AUFLAUF`, `LB_SPURLINIE`, `LB_SPURLINIE_LRS` | Auflaufhöhen und Spurlinien je Jahr und Herkunft |
| `LB_ISOCHRONE`, `LB_ANKUNFT` | Ergebnisse 7.1 |
| `LB_BOOT`, `LB_BOOT_LAGE` | Boote, Ankerplätze, Bahnen |
| `LB_EREIGNIS_LAUF` | Läufe (vorgerechnet und Echtzeit): Eingaben und Kennzahlen (Auflauf, Ankunftszeiten, Energieanteil) |
| `LB_TEXTUR` | Herkunft und Lizenz |
| `LB_SZENARIO`, `LB_SZENARIO_SCHRITT`, `LB_KAMERA_FAHRT`, `LB_SCHNAPPSCHUSS` | Regie |

Sequence + Trigger, nie IDENTITY; SRID 26908, Sichten in 4326.

---

## 14. Phasenplan

„R“ Renderer, „L“ LituyaBay1958, „D“ Datenbank. Je Phase ein Statusbild `docs/status/Status_PhaseN.png` und ein Eintrag in Abschnitt 19.

| Phase | Ort | Inhalt | Abnahme |
|---|---|---|---|
| 0 | L | **erledigt:** Gebiete, Kartenmaterial (48 Quellen), `tools/kartenmaterial`; Werkbuch-Prompt; dieses Werkbuch | `Kartenmaterial.md`, `INVENTAR.md` (zweiter Lauf 1 227 Dateien, 13,5 GB) |
| 0b | L | Recherche 2.1–2.7 vervollständigen (Miller S. 51–86 lesen, Zeugen, Boote, Tide, Zeitzone), Texturquellen und Lizenzen, `docs/QUELLEN.md` anlegen | alle [zu prüfen] in Abschnitt 2 und 9 aufgelöst oder als [G] erklärt |
| 1 | L | **erledigt:** Grundgerüst: FFrame, GL-Engine aus Timmendorf, Kamera, HUD, `wiring`, Startbild mit DB-Anmeldung, `sdonm.jar`, `lib/lod/`, Git | leere Szene mit Horizont |
| 2 | L | Aufbereitung nach 3.4 (**Werkzeuge fertig:** `gelaende`, `grund`, `namen`, `bilder`, `inhalt`; offen `rueckbau1958`, `spurlinie`, `klassen`, `wald`, `gletscher`) | Prüfbilder; **Start mit umbenanntem `download/`** |
| 3 | D | Schema, Rechte, **NDM-Funktionsprobe (2.8)**, Ladeskripte, `LB_NET_HERKUNFT` | Tabelle 2.8 gefüllt, `VALIDATE_NETWORK` grün |
| 4 | R | **gebaut:** `clipmap`; in der App das Gelände vor dem Wasser | Demo Lidar bis zum Horizont, Nahtprüfung, Zeit je Bild |
| 5 | R | **gebaut:** `surface` + Backwerkzeug (Schritt `texturen`) | Kontaktbogen, Kiesstrand ohne Wiederholung |
| 5a | L | Grundbild: Gelände, Texturen, Himmel (6.6), Wasser mit Gletschermilch (6.5) | „Abend vor Anker“ ohne Wald, Sonne nach 9.10 |
| 6 | R | **gebaut:** `forest` mit `ForestGpu` (6.4), `understory` (5.7) | Millionen Bäume, fps-Tabelle, andere Szenen unverändert |
| 6a | L | Regenwald, Unterwuchs, Gletscher (6.3), `scree` (R) | „Abend vor Anker“ vollständig |
| 7 | R | **gebaut:** `route` erweitern (6.9), `netview` (5.12) | `RouteCheck` (Gitterfehler 2,75 % / 0,76 %), Demo Isochronen |
| 7a | L+D | **gebaut:** **`LB_NET_WELLE`**, **`LB_NET_UFER`**, Netzblick | NDM = `route`; Isochronen 1940 und heute; Spurlinie je Ufermeter |
| 8 | R | **gebaut:** `quake` | `QuakeCheck`, Demo |
| 9 | R | **gebaut:** `landslide` (+ `fracture` 6.2) | `LandslideCheck`: Block, Fahrböschung, Lituya-Preset 96–110 m/s |
| 10 | R | **gebaut (Abnahme mit Hinweis):** `impact` | Laborfall FRITZ01: 152 m bei 885 m nach 16 s, Energieanteil 2–30 % (Höhe rund 30 % darüber, siehe 9.3) |
| 10b | R | `impact`: Wasser durch den ganzen Gleitkörper (Poren im Inneren, Sickern), Phasenmischung nach der Kaverne | Laborfall 885 m im Spielraum ±20 %, über mehrere Läufe |
| 11 | R | **gebaut (Prozessor):** `wave.Bay` (6.1) | `WaveCheck` alte Zeilen gleich, Auflauf 526 ± 50 m (542 m), Rungholt unverändert (`Wave` unberührt) |
| 11b | R | **gebaut:** `wave.Bay` als Compute Shader neben dem Prozessor (`wave.gl.BayGpu`) | `BayGpuCheck` CPU = GPU auf 1 mm (glatt), Auflauf 1 %, Volumen 1e-6; Sekunden Welle je Sekunde Rechnung je Gitter (Tabelle 15.2) |
| 12 | R | **gebaut:** `dustcloud`, `debris` (5.4, 5.5) | `DustcloudCheck`, `DebrisCheck`, Demos; in der App `treibgut` (aus `wave.Bay`) und `staubwolke` (aus `landslide`) |
| 13 | L | **gebaut:** **Das Ereignis, Vorrechnung:** Kette `impact` → Linie → `wave.Bay` auf dem Grund 1958 (4.4), Lauf `kette` und `messwelle`, Strom und Staub, `daten/ereignis` | `ant ereignis`: Auflauf am Sporn gegen 524 m, Ankunft gegen `LB_NET_WELLE`, Daten lesbar (`Pruefung`) |
| 13b | L | **gebaut, abgenommen:** Abspielen in GL: Welle als Höhenfeld je Gitter, Strom als Netz, Staub als Volumen (Treibgut mit 14), vorwärts, rückwärts, Zeitlupe bis 1:20; Schaustücke 1–7 als Kamera + Uhr | `ant schaustuecke`: Standbilder 2–7 mit den Zeiten je Durchgang; `ant pruefung` (Abschnitt „Abspielen“) |
| 13c | L | **gebaut, abgenommen (Messung in 14 wiederholt):** Echtzeit „Was wäre wenn“ (Volumen, Ort, Tide, Grund 1940 gegen heute), Bedienfeld EREIGNIS, Rückspulpuffer | `ant echtzeit`: über 10 Bilder je Sekunde bei LOW, Rückstand der Rechnung unter 5 s |
| 13a | L+D | **gebaut, abgenommen:** Vergleich `wave` ↔ `LB_NET_WELLE` ↔ Zeitleiste ↔ Miller (Spurlinie je 50 Ufermeter, 524 / 208 m), Spurlinie als Rechnung in `LB_SPURLINIE_LRS`; Zeugen erst mit 0b | `ant vergleich`, `ant netzdb`: Tabelle der Ankunftszeiten und Spurlinien in Abschnitt 19 |
| 14 | R | **gebaut, abgenommen:** `succession` (Zellautomat 6 m, Wellenwurf 9.7), in `forest` der Nachwuchs je Platz | `SuccessionCheck`, `ForestGpuCheck` mit Nachwuchs; Vorführung in der App: `ant wiederbewaldung`, `ant wiederbewaldungbild` (Band und Wiederbewaldung) |
| 14a | L | **gebaut, abgenommen:** Wald in der Szene (`gl.WaldGl`: `forest` über den Kern, Zustände aus `succession` je Zeitstand), Band 1958 auf die Belege gezogen (`scene.Band1958`), Kronenhöhe ArcticDEM − Gelände und Abgleich (Schritt `wald`), Treibgut am Morgen (`tools.Treibgut1958`), Jahres-Zeitraffer, Boden im Band, Holzteppich | `ant aufbereitung -Dschritte=wald`, `ant treibgut1958`, `ant waldkarte`, `ant waldszene` (Schaustücke 10, 11), `ant pruefung` (Abschnitt „Wald in der Szene“) |
| 15 | R | **gebaut, abgenommen:** `vessel` (5.9): Kutter als Starrkörper, Kette aus Massenpunkten, Edrie, Badger, Sunmore | `ant vesselcheck` (21 Prüfungen), `ant vesselbild`: Kutter vor Anker in der Einzelwelle, die Kette reißt |
| 15a | L+D | **gebaut (wartet auf die Radeon):** Boote auf der Vorrechnung (`tools.Boote1958`, `gl.BooteGl`, Pass „Boote“), Strömung in der Vorrechnung, Gezeiten (`scene.Gezeiten`), **`LB_NET_SEE`** gerichtet mit Kosten nach der Zeit, `LB_FLUCHT` | Schaustücke 8, 9; „Wer hätte entkommen können?“ (`flucht.tsv`, `Flucht.png`); LOD = eigener Dijkstra |
| 16 | L+D | **gebaut, abgenommen:** **`LB_NET_WASSER`** (7.4): Bäche, Gletscher mit ITS_LIVE, Teileinzugsgebiete als zweite Ebene, Seen, Senken; Katalog ITS_LIVE als Ausschnitt | `ant aufbereitung -Dschritte=wasser`, `ant wasserkarte` (`Wasser.png`), `ant pruefung` (Abschnitt „Gewässer und Gletscher“), `ant netzdb`; Leistungstabelle 15.3 (WASSER) |
| 16a | L+D | **gebaut, abgenommen:** **`LB_NET_WEG`** (7.5): Gitter 10 m im Kern und 50 m in der Szene, hierarchisch, gerichtet; Schritt `klassen` (WorldCover, Kronenhöhe aus `wald`, RGI 7, Spalten aus ITS_LIVE); Bäche aus `LB_NET_WASSER` als Sperre | `ant aufbereitung -Dschritte=klassen+weg+inhalt`, `ant wegkarte` (`Weg.png`), `ant pruefung` (Abschnitt „Wege über Gelände“), `ant netzdb`; Washburn 1934, `withinCost(Strand, 1 h)`, Leistungstabelle 15.3 (WEG) |
| 16b | L+D | **gebaut (wartet auf die Radeon):** **Flucht an Land** (Boot durch `LB_NET_SEE` zum Strand, 3 min anlanden, zu Fuß durch `LB_NET_WEG` bis 10 m über den Auflauf der Vorrechnung, alles vor der Front; `tools.Landflucht`, `LB_LANDFLUCHT`); **`ant wegdb`** als eigenes Ziel, `netzdb` lässt `LB_NET_WEG` stehen; 12 mit Kacheln 1 km / 5 km, Index und Statistik, Zeit je Anweisung | `ant aufbereitung -Dschritte=landflucht+inhalt`, `ant wegkarte`, `ant pruefung`, `ant wegdb` (Zeiten je Anweisung), wahlweise `ant netzdb` |
| 16c | L | Netzblick für `LB_NET_WASSER` und `LB_NET_WEG` (Bäche, Einzugsgebiete, 1-h-Fläche, Washburns Route, Landwege) und die Bäche mit Wasserführung nach Regen in der Szene (7.4 „Für die Szene“) | Standbilder, Bildrate |
| 17 | L+D | **`LB_NET_SICHT`**, Regiebücher, Kino (6.8) | Kino läuft durch |
| 18 | R+L | Klang (6.7), Wetter, Leben; `aurora`, `bear` | Hörproben, Schaustück 12 |
| 19 | L+D | Oberfläche und Datenbank (Fragenblock II) | Tabellen freigegeben, Speichern/Laden |
| 20 | L | Schliff: Qualitätsstufen, READMEs, GitHub-README, Icon, Verknüpfungen, Video, Dauerlauf; **Kartenmaterial ausgelagert, Start ohne `download/`** | „fertig“ |

---

## 15. Leistungsbudget und Risiken

### 15.1 Ziel

Ryzen 7 7735HS, Radeon 680M (2 GB VRAM), 64 GB RAM, Full HD. Hoch ≥ 30 fps, Mittel 60 fps; Ereignis abgespielt in Hoch ≥ 30 fps; Netzanalysen außerhalb des Bildfadens (F37).

### 15.2 Abschätzung Ereignis (Vorrechnung)

| Teil | Gitter | Zellen | Schritte je 10 min | Rechnung |
|---|---|---|---|---|
| `wave` grob | 16 m, 1 500 × 1 125 | 1,7 M | 7 500 | GPU: Minuten |
| `wave` mittel | 4 m, 1 024² | 1,0 M | 30 000 | GPU: Minuten |
| `wave` fein | 2 m, 1 024² | 1,0 M | 60 000 | GPU: zehn Minuten |
| `landslide` | 2 m, 1 000 × 600 | 0,6 M | 7 s Eintauchen, CFL 0,02 s → 350; Ablösen und Gleiten 30 s → 1 500 | GPU: Sekunden |
| `impact` | Partikel 2–5 M | — | 60 s à 5 ms = 12 000 | GPU: Stunden (das Teure) |
| Bäume | 6 M (Kern) | Zustände als Bits je Kachel | 600 Schritte | CPU: Minuten |

**Gemessen (Phase 11b, `ant baygpucheck`, Radeon 680M gegen Ryzen 7 7735HS):**

| Fall | Gitter | Prozessor ns je Zelle | Grafikkarte ns je Zelle | Welle je Sekunde Rechnung (Prozessor / Grafikkarte) |
|---|---|---|---|---|
| 512² zu 4 m, 100 m tief | 1 | 101 | 12,6 | 1,40 s / 9,57 s (6,8×) |
| Fjordbucht | 20 m, 310 × 160 | 38 | 7,8 | 5,02 s / 21,40 s (4,3×) |
| | 5 m, 200 × 200 | 49 | 8,1 | |
| | 2,5 m, 200 × 160 | 52 | 7,6 | |

Hochgerechnet auf Lituya (Tabelle oben, 8 ns je Zelle): grob 1,7 M × 7 500 ≈ 100 s, mittel 1 M × 30 000 ≈ 4 min, fein 1 M × 60 000 ≈ 8 min, zusammen gut 12 min Rechnung für 10 min Ereignis (Vorrechnung, keine Echtzeit) [G: kleine Gitter tragen die Rückfragen je Schritt mit].

Speicher der Vorrechnung: Höhenfelder grob als 16-Bit je 0,5 s → 1,7 M × 2 B × 1 200 ≈ 4 GB; mit Kachelung auf das bewegte Fenster deutlich weniger. Ziel: `daten/ereignis/` ≤ 10 GB [G].

### 15.3 Netze (wird in Phase 7a, 13a, 16 gefüllt)

| Netz | Knoten | Kanten | Partitionen | Ladezeit | Analyse (ms) | Speicher LOD |
|---|---|---|---|---|---|---|
| WELLE | 74 484 (mit `QUELLE`) | 585 257 (mit 187 von `QUELLE`) | 16 (`SPATIAL_PARTITION`) | Knoten 1,0 s, Kanten 5,7 s, Linien 19,6 s; Partitionen und BLOBs in `04` 80 s | `withinCost` je Kostenart 1,3 bis 5,7 s; LOD laden 174 s (mit den Meldungen aus 26) | |
| UFER | 85 | 84 | 1 | | | |
| SEE | ≈ 10 000 | | | | | |
| WASSER | 18 466 (18 206 Ebene 1, 260 Ebene 2) | 18 456 (18 201, 255) | Ebene 1 `SPATIAL_PARTITION` 5 000, Ebene 2 eine | 17,0 s mit Linien, Umrissen und `10` (6,4 s) | traceIn × 5 in Oracle 1,3 s, im Speicher 0,4 s | |
| WEG | 3 506 647 (Probelauf: 2 711 312 Ebene 1, 795 335 Ebene 2) | 26 412 374 gerichtet | Kacheln: Ebene 1 ≈ 900 + Ebene 2 ≈ 340 | | im Speicher (Ausschnitt 135 144 Knoten): 1 h, Washburn, k = 3 zusammen 20 s; Dateien 4 s; Dijkstra 1 h 0,1 s | |
| SICHT | ≈ 200 | | | | | |
| HERKUNFT | ≈ 2 000 | | | | | |

Befund Phase 7: Auf 20 m hat der Kern 305 305 Wasserzellen, davon der größte Teil vor der Küste. Die ≈ 75 000 Knoten von WELLE gelten nur für die Bucht innerhalb des Eingangs (mit einem Streifen See davor); das Netz in 7a wird auf diese Fläche beschnitten. Ein Lauf von `TravelGrid` über alle 1,08 Mio. Zellen dauert 0,4–0,5 s.

### 15.4 Risiken

| Risiko | Folge | Gegenmittel |
|---|---|---|
| `impact` mit Partikeln zu teuer oder instabil | kein Nahfeld | Rückfall: Volumen-Impuls-Quelle in `wave` nach dem Modellprofil (F13 B), kalibriert auf 152 m bei 885 m. **Stand Phase 10:** stabil, im Schnitt 8 m rund 2 min, 5 m rund 10 min für 24 s; Höhe rund 30 % zu hoch (10b), F13 B bleibt Reserve |
| Gilbert Inlet 1958 nur gesetzt (Rückbau [G]), Lage von Wand und Linie geschätzt | Auflauf am Sporn und Welle in der Bucht hängen daran | Schritt `rueckbau1958` (Phase 2, offen) mit Karten von 1958; Vergleich 13a mit Miller |
| `wave` auf 45° instabil (dünne Schichten) | Auflauf falsch | Schichtschwelle, Reibungsbegrenzung, Vergleich mit Hall & Watts. **Stand Phase 11:** `Bay` stabil am 45°-Hang, Auflauf 542 m gegen 526 m |
| `wave` in float auf der Grafikkarte, Lituya bis 220 m tief | Volumen und flache Ufer leiden unter der Auflösung | **Stand 11b:** wie der Prozessor bis 2,6e-6 (Laborfall, 140 m). Reicht es bei Lituya nicht: Tiefe als Abweichung vom Ruhespiegel speichern [G] |
| NDM-Funktionen fehlen in der installierten Version | Netze schrumpfen | Funktionsprobe zuerst (2.8); PL/SQL-Ersatz; `route` rechnet alles ohnehin |
| `sdonm.jar` gegen Treiber 23 | LOD läuft nicht | Klassenlader wie Timmendorf (F26) |
| VRAM 2 GB | Texturen und Bäume | Budget 600 MB, Impostors, Clipmap-Streaming |
| Belege zu Booten und Zeugen dünn | [G] statt belegt | Phase 0b: Miller vollständig lesen; was fehlt, bleibt [G] und wird gesagt |
| Zeitzone 1958 | Sonnenstand um 1 h falsch | in 0b prüfen; die Rechnung 9.10 ist parametrisiert |
- **Abspielen (13b):** Die Welle in MEDIUM hat 5 Mio. Zellen; nah zeichnet jede Kachel 8 192 Dreiecke, jeder Scheitel liest 10 bis 20 Texel. Auf der Radeon zu messen (`ant schaustuecke`, Durchgänge Welle und Staub); Rückfall: 4 Bildpunkte Fehler, weniger Schritte im Staub, Auflösung herunter. Schnelle Zeitraffer (über 10-fach) überholen das Auspacken (rund 10 ms je Bild und Gitter): die Welle bleibt dann stehen, bis das Paar da ist.
- **Echtzeit (13c):** LOW brauchte auf der Radeon allein 92 s für 600 s Welle (15.2), im Mittel also 0,15 s je Sekunde; in den ersten 30 s (schnelles Wasser im Inlet, kleiner Zeitschritt) mehr. Jeder Schritt liest die größte Geschwindigkeit zurück (Warten auf die Grafikkarte). Rückfall: Budget je Bild senken (`Echtzeit.budgetMs`), dann rechnet sie langsamer als die Uhr, und die Szene zeigt das letzte gerechnete Bild. Das Lesen der Bilder (rund 5 MB je Sekunde Welle) kostet einige ms je Sekunde.

### 15.5 Wald (Phase 6, gemessen auf der Radeon 680M, `ant waldzeiten`)

Gesamtzeit auf der Grafikkarte je Bild (ms, Full HD: Boden, Wald, Rest), in Klammern die fps daraus:

| Qualität | Waldrand | Bucht | Weitblick | Im Wald | Grafikspeicher |
|---|---|---|---|---|---|
| LOW | 2,08 | 0,97 | 3,73 | 10,18 (98) | 129 MB |
| MEDIUM | 3,60 | 2,13 | 8,42 | 16,79 (60) | 177 MB |
| HIGH | 5,91 | 4,55 | 13,99 | 29,91 (33) | 243 MB |
| ULTRA | 7,22 | 6,61 | 18,14 (283 336 Bäume) | 49,74 (20) | 328 MB |

Die Kultur (Compute) kostet überall ≤ 1,7 ms. Teuer ist „Im Wald“: viele Bäume in Stufe 0 und 1 nah an der Kamera. Für Hoch ≥ 30 fps im Wald in 6a die Reichweite der nahen Stufen senken oder die Netze der Stufe 0 leichter machen.

---

## 16. Fragen und Entscheidungen

**Entscheidung vom 01.11.2026: alle A.** Die Spalte A ist damit nicht mehr Empfehlung, sondern Beschluss; B und C bleiben als Rückfall stehen, falls eine Phase A nicht einlöst (dann wird die Frage neu gestellt und hier vermerkt).

### 16.1 Was aus „alle A“ folgt (verbindlich)

- **Raum und Zeit:** Gebiete wie 3.1, echte Lage in UTM 8N, Höhen NAVD88 (NOS-Lotungen über Elfin Cove umgerechnet); vier Zeitstände umschaltbar; Grund 1958 aus der Vermessung 1940, abgeglichen mit FRANCO20 und 1959.
- **Daten:** Kartenmaterial nur in Phase 2, Auslagerung in Phase 20 geprüft; Datei-Abbild der Netze in `daten/netz/`, die App läuft ohne Datenbank.
- **Renderer:** GL-Engine aus Timmendorf mit `clipmap` und `surface`; alle 14 neuen Packages (Zugaben `aurora` und `bear` zuletzt); alle Optimierungen aus Abschnitt 6 mit Messung und Rückfall; LOD mit eigenem Klassenlader und Treiber 21.
- **Ereignis:** vorgerechnet in hoher Auflösung und Echtzeit in geringer; `quake` mit Newmark; `landslide` nach Savage-Hutter mit Voellmy; `impact` mit Partikeln und Übergabe an `wave` (Volumen-Impuls-Quelle als Rückfall); `wave` auf der GPU mit Flachwasser und Stoßerfassung, Dispersion nur als Prüfung; Gletschermilch aus Schwebstoff; Fronten 1958 aus Karte 1951/C-5.
- **Wald:** Millionen Bäume mit Impostors und Zuständen; Spurlinie belegt und gerechnet nebeneinander; Texturen aus CC0, eigenen Assets und Daten mit Lizenz, Budget 600 MB.
- **Netze:** alle sieben; `LB_NET_WELLE` auf 20 m mit 16 Nachbarn und vier Kosten in einem Netz; Isochronen über Network Buffer, sonst Konturen; Netzblick mit allen Ebenen als Bild im Bild; Property Graph nur bei 23ai; Schema `LITUYA`, Präfix `LB_`, SRID 26908.
- **Szene und Regie:** drei Boote nach Miller mit [G], Menschen an Bord ohne Gesicht, La Pérouse 1786 als Szenario, Tiere mit Bär, Washburn 1934 falls belegt; Zeitlupe bis 1:20, Unterwasserblick, Polarlicht nur „heute“ und belegt; Ton erzeugt mit tiefen Frequenzen.
- **Ziel:** Hoch ≥ 30 fps, Mittel 60 fps, Ereignis abgespielt ≥ 30 fps; Rückfluss als Wunschliste; Programmsymbol mit Goldkante und Spurlinie.

### 16.2 Die Fragen

A ist gewählt (Entscheidung vom 01.11.2026).

| Nr. | Frage | A (gewählt) | B | C | Begründung für A |
|---|---|---|---|---|---|
| F1 | Gebiete | Kern/Szene/Fern wie 3.1 | Kern enger | größer | Kern deckt Bucht und Spurlinie, Fern reicht bis zur Kimm des Fairweather (264 km) |
| F2 | Maßstab und Lage | echt, UTM 8N, NAVD88 | Lage frei | — | Netze und Belege sitzen auf echten Koordinaten |
| F3 | Höhenbezug | NAVD88, NOS von MLLW über Elfin Cove umgerechnet | MLLW für Wasser, NAVD88 für Land | — | ein Bezug für Welle und Auflauf; 524 m sind über Meeresspiegel |
| F4 | Zeitstände | vor, Welle, danach, heute, umschaltbar | nur 1958 | nur heute | die Geschichte ist der Kontrast |
| F5 | Grund 1958 | Vermessung 1940, Abgleich FRANCO20 und 1959 | BlueTopo heute [G] | — | die Welle lief auf dem Grund von 1940; 1959 zeigt den Schuttfächer |
| F6 | Engine | Timmendorf + `clipmap` + `surface` | neu | ohne `clipmap` | Lidar 0,5 m bis 260 km geht nur mit Streaming |
| F7 | Kartenmaterial | nur Phase 2, Auslagerung in Phase 20 geprüft | zur Laufzeit lesen | — | Regel 3.3 |
| F8 | Neue Packages | alle 14, Zugaben zuletzt | ohne Zugaben | nur Ereigniskette | jedes hat ein „später für“ |
| F9 | Optimierungen | alle aus 6 mit Messung und Rückfall | nur `wave`, `forest` | keine | Paketvertrag 14 schützt die alten Szenen |
| F10 | Ereignis | vorgerechnet und Echtzeit | nur vorgerechnet | nur Echtzeit | hohe Auflösung fürs Kino, Echtzeit fürs Spielen |
| F11 | `wave` auf der GPU | Compute neben CPU, CPU = GPU geprüft | nur CPU | neue Engine | 60 000 Schritte auf 1 M Zellen sind CPU-fremd |
| F12 | Nichtlinearität | Flachwasser mit Stoßerfassung | Boussinesq | linear | Ursell 110: lang und nichtlinear; Dispersion schwach |
| F13 | Nahfeld | `impact` mit Partikeln, Übergabe an `wave` | Volumen-Impuls-Quelle | gestaltet | Kaverne und Wasserwand sind das Schaustück 4; B als Rückfall |
| F14 | Lawinenmodell | Savage-Hutter, Voellmy | µ(I) | Partikel | Stand der Praxis, auf dem Gelände rechenbar |
| F15 | Beben | `quake` mit Newmark | nur Auslöser | — | erklärt die 1–2½ min Verzögerung |
| F16 | Wald | Millionen Bäume mit Impostors und Zuständen | nur nah echt | — | die Spurlinie lebt von jedem Baum |
| F17 | Spurlinie | belegt und gerechnet nebeneinander | nur belegt | nur gerechnet | der Vergleich ist die Prüfung |
| F18 | Texturquellen | CC0 + eigene + Daten | prozedural | nur eigene | echte Scans tragen das Nahbild |
| F19 | Texturbudget | 600 MB | 400 MB | 1 GB | Rest für Gelände, Bäume, Ereignis |
| F20 | Gletschermilch | aus Schwebstoff, gegen Sentinel-2 | feste Farbe | — | 9.9 |
| F21 | Gitter WELLE | 20 m, 16 Nachbarn | 10 m, 8 Nachbarn | 50 m | 75 000 Knoten reichen für LOD, Fehler 2,75 % |
| F22 | Kosten WELLE | vier Kosten in einem Netz | je Zeitstand ein Netz | nur linear | `LinkCostCalculator` ist die Fähigkeit, die gezeigt werden soll |
| F23 | Isochronen | Network Buffer, sonst Konturen | Konturen | — | 2.8 entscheidet |
| F24 | Netze | alle sieben | Welle, Ufer, See | nur Welle | Abnahme-Matrix 7.8 |
| F25 | Datei-Abbild | `daten/netz/`, App ohne DB lauffähig | nur Oracle | nur Datei | wie Timmendorf |
| F26 | LOD und Treiber | Klassenlader, Treiber 21 in `lib/lod/` | neues `sdonm.jar` | PL/SQL | bewährt |
| F27 | Boote | drei nach Miller, [G] wo unbelegt | ohne | nur Edrie | die Boote sind der Maßstab und die Geschichte |
| F28 | Menschen | an Bord, klein, ohne Gesicht | keine | — | Maßstab |
| F29 | La Pérouse 1786 | Szenario im Seenetz und Regiebuch | Ideenspeicher | weg | zeigt die zeitabhängigen Kosten |
| F30 | Tiere | Möwen, Seehunde, Adler, Bär | ohne Bär | nur Möwen | Zugabe, klein |
| F31 | Washburn 1934 | falls belegt | frei | weg | Wegenetz braucht einen echten Weg |
| F32 | Gletscher 1958 | Karte 1951/C-5, Miller | heutige Front | — | 400 m Eis abgeschert: die Front 1958 ist Teil des Ereignisses |
| F33 | Zeitlupe | bis 1:20, frei mit Kamera | nur Echtzeit | — | episch |
| F34 | Unterwasser | ja | nein | — | Schaustück 7 |
| F35 | Polarlicht | nur „heute“, wenn belegt | immer [G] | weg | Sommer ist zu hell (9.10) |
| F36 | Ton | erzeugt, tiefe Frequenzen | keiner | Dateien | wie alle Szenen |
| F37 | Leistungsziel | Hoch ≥ 30, Mittel 60, Ereignis ≥ 30 | nur 30 | frei | 15.1 |
| F38 | Netzblick | alle Ebenen, Bild im Bild | nur Isochronen | keiner | Säule 2 muss sichtbar sein |
| F39 | Property Graph | nur bei 23ai | nicht | Upgrade | 2.8 |
| F40 | Schema | `LITUYA`, `LB_` | **`LITUYABAY1958`** | DEMO | kurz, wie `SY_`, `TD_`; **Phase 3: B**, der vorhandene Benutzer, Präfix `LB_` bleibt |
| F41 | SRID | 26908, Sichten 4326 | 32608 | — | NAD83 wie die Daten |
| F42 | Rückfluss | Wünsche als Liste | Phase | keiner | Abschnitt 18 |
| F43 | Programmsymbol | Goldkante, Spurlinie über der Bucht | Welle | — | wie `heidelberg.ico` |

Fragenblock II (Oberfläche, Datenbank) kommt in Phase 19 und wird hier als 16.3 angefügt.

---

## 17. Ideenspeicher

- **Rückwärts rechnen:** Schnitt der Isochronen von den Booten aus (`withinCost` rückwärts): Trifft er den Gilbert Inlet?
- **Wellenstrahlen** an Cenotaph Island: Teilung und Schließen der Front im Netzblick.
- **Die Uhr der Welle:** Laufzeituhr je Ort im Kino.
- **1940 gegen heute:** dieselbe Welle auf dem heutigen Grund (BlueTopo: die Bucht ist flacher geworden).
- **Was wäre wenn:** halbes oder doppeltes Volumen; Sturz von der Crillon-Seite (wie 1936); Flut statt Ebbe; die Spurlinie wandert im Wald mit; `impact` liefert $a_M \propto P^{4/5}$: halbes Volumen → Welle nur um ein Fünftel kleiner ($M^{1/4}$), das überrascht und ist erklärbar.
- **Frühere Wellen** als Ereignisse auf dem Ufer-LRS, wie Miller sie am Wald gelesen hat.
- **Der Wald als Kalender:** Nachwuchs aus ArcticDEM 2013–2022.
- **Maßstabsfiguren:** Kölner Dom (157 m), Freiheitsstatue, Kampener Leuchtturm neben der Welle.
- **Energiebilanz als Fenster:** Gleitenergie, Wellenenergie, Reibung, Kaverne, als Balken live aus `impact`. *(Anfang in `ImpactDemo`: Gleitkörper gegen führende Welle.)*
- **Wege der Tlingit**, wenn belegt.
- **Herkunft im Bild:** Klick auf eine Wassertiefe zeigt den Weg bis zur Vermessung von 1940.

---

## 18. Rückfluss (Wünsche, keine Pflicht, F42)

| Was | wohin | warum |
|---|---|---|
| `clipmap`, `surface` | Sylt, Timmendorf | Fernsicht und Texturen ohne eigene Geländeklassen |
| `wave` auf der GPU, drei Gitter | Rungholt | Sturmflut feiner und größer |
| `landslide`, `quake` | Geiranger (Åkerneset), Glacier | der Felssturz, der dort immer geplant war |
| `impact` | Glacier | Kalbungswellen |
| `debris` | Rungholt, Sylt | Treibgut der Sturmflut |
| `succession`, `understory` | Brocken | Windwurf, Borkenkäfer, Regenwald der Hochlagen |
| `vessel` | Timmendorf, Lighthouse | Kutter im Hafen, Versorger am Turm |
| `netview` | Timmendorf | Netze sichtbar machen |
| `route` III (Gitter, Laufzeit, Messstrecke) | alle mit NDM | Zweitrechnung |

---

## 19. Fortschritt

| Datum | Phase | Stand | Statusbild | `Renderer.jar` |
|---|---|---|---|---|
| 01.11.2026 | 0 | Gebiete festgelegt; Kartenmaterial-Werkzeug mit 48 Quellen; erster Lauf 1 170 Dateien, zweiter Lauf 1 227 Dateien (13,5 GB); Fehler berichtigt (Fortsetzen, Verzeichnisse, ENC-Katalog, USGS-Publikationen); Werkbuch-Prompt; **dieses Werkbuch als Entwurf** | `images/Gebiete_Szene.jpg`, `images/Gebiete_Fern.jpg`, `images/Kern_BlueTopo.png` | — |
| 01.11.2026 | — | **Fragen F1–F43 beantwortet: alle A** (Abschnitt 16.1) | | |
| 01.11.2026 | 1 | **Gerüst** (Freigabe „Phase1, los“): `com.dan.lituya` mit `app`, `camera`, `gl`, `scene`, `wiring`, `ui`, `data`, `netz`, `tools`; GL-Engine aus Timmendorf entschlackt (Himmel, Wasser mit Erdkrümmung bis zur Kimm, Nachbearbeitung, HUD; Zeitmessung je Durchgang, Qualitätsstufen, Foto, Shader neu laden, Physik-Faden, Kino als Anschluss); Raum UTM 8 N mit Ursprung E 352 000, N 6 503 000 [G]; Gebiete und 25 Orte aus GNIS als Marken, der Sporn [G]; drei Uhren (Ortszeit `America/Juneau`: 1958 PST = UTC−8, Simulationszeit ab 06:16 UTC, Zeitstand vor/Welle/danach/heute); neun Ansichten; Startbild mit Anmeldung; Bedienfeld mit allen Blöcken aus 12; `lib/sdonm.jar`, `lib/lod/` mit Klassenlader `netz.Lod` und `ant lod`; `ant pruefung` (64 Prüfungen, alles in Ordnung), `ant statusbild`, `check-packages`. Befunde: Sonnenuntergang 22:14 statt 22:04 (2.1 berichtigt); Sichtstrahl und Schnitt mit der gekrümmten Wasserfläche in float neu gelöst (aus 1,9 km Höhe zitterten Raster und Marken); `db/db.properties` aus Git genommen | `docs/status/Vorschau_Phase1_Mesa.png` (Mesa, ohne HUD); `Status_Phase1.png` mit `ant statusbild` auf der Radeon | Stand 31.10.2026, 55,7 MB, unverändert |
| 01.11.2026 | 1 | Nachtrag: `ant lod` sucht ORACLE_HOME auch in der Registry, im PATH und unter `C:\app` | | |
| 01.11.2026 | 2 | **Aufbereitung, Werkzeuge** (Freigabe „Phase2, los“): `com.dan.lituya.tools` mit `GeoTiff` (TIFF und BigTIFF, LZW, Deflate, Prädiktor 2 und 3, Kacheln, GeoKeys mit Albers), `Abbildung` (UTM 8 N, geographisch, Alaska Albers), `Quelle`, `Gitter`, `Gelaende` (Fern 30 m, Szene 5 m, Kern 0,5 m, Kacheln 2000² als `.r32`, Format in `scene.Stufe`), `Grund` (heute 4 m mit Wassermaske; 1926, 1940, 1959 zu 20 m aus den Lotungen über `Delaunay`), `Namen`, `Pruefbilder`, `Aufbereitung` (`ant aufbereitung`, `daten/INHALT.txt` mit SHA-256). Probelauf hier an 21 Dateien des Kartenmaterials (Lidar um den Gilbert Inlet und am Eingang, BlueTopo, USACE, 3DEP 1″, Copernicus, GEBCO, IfSAR GB-336, NOS): alle Formate gelesen, 0,6 s je Kern-Kachel; Befunde Höhenbezug und Mischung in 3.4. `ant pruefung` 77 Prüfungen. Offen aus 3.4: `rueckbau1958` und `spurlinie` (brauchen die von Hand eingepassten Karten 1951/C-5 und Millers Karte), `klassen` (Sentinel-2, WorldCover), `wald` (DSM 2012 nicht geladen, Stufe „gross“), `gletscher` (RGI, GLIMS; Fronten 1958 aus der Karte); `netz` mit Phase 7a, `ereignis` mit Phase 13 | Prüfbilder des Probelaufs; die echten schreibt `ant aufbereitung` nach `docs/status/Phase2_*.png` | unverändert |
| 01.11.2026 | 2 | **Volllauf und Abnahme:** `ant aufbereitung` in 3,7 min (Kern 432 Kacheln in 124 s: Lidar 55,9 %, IfSAR 16,1 %, USACE 0,3 %, BlueTopo 27,8 %, ohne Wert 0 %; Naht Kern–Szene Median 0,01 m, 95 % 16,5 m; Szene–Fern Median 0,21 m, 95 % 59 m an steilen Hängen), 504 Dateien, 8,05 GB; Wasser im Kern 121,4 km². `ant pruefung` 78 Prüfungen, alles in Ordnung; App und Prüfung mit umbenanntem `download/` gelaufen. **Befund:** In der tiefen Bucht ist BlueTopo die Vermessung von 1940 (25 602 Zellen alle −0,59 m); daraus MLLW 1,157 m unter NAVD88 (gemessen, VDatum), gilt jetzt in `grund` statt Elfin Cove | `docs/status/Phase2_*.png`, `aufbereitung_Phase2.txt`, `pruefung_Phase2.txt` | unverändert |
| 01.11.2026 | 3 | **Schema, Rechte, Funktionsprobe, Herkunft** (Freigabe „Phase3, los?“; Entscheidungen: Schema `LITUYABAY1958` (F40 B), Probenetze im Schema und danach weg, Herkunft je Rohdatei, `00_sys.sql` als SYS in SQL Developer): `db/netz/00`–`05`, `98`, `99`; `LB_NETZ_STAND`, `LB_NDM_PROBE` mit `LB_PROBE_SETZEN`; Schritt `herkunft` (`tools.Herkunft`), `tools.NetzDb` mit `ant netzdb` und `ant dbpruefung`; `netz.NdmProbeKern` (LOD im eigenen Lader: Dijkstra, eigene Netzbedingung und Kostenfunktion, `withinCost`, `nearestNeighbors`, *k* kürzeste Wege, TSP, MCST, `traceIn`/`traceOut`, Network Buffer, Komponenten). Hier geprüft: LOD im Speicher alle zwölf wie erwartet (Befund: `mcst` spannt nur eine Komponente, keinen Wald); Herkunft an einem Ausschnitt (15 Rohdateien) 1 781 Knoten, 3 035 Kanten, kreisfrei; `ant pruefung` 84 Prüfungen. **Ausstehend auf dem Rechner:** `00_sys.sql`, `ant aufbereitung "-Dschritte=grund,herkunft,bilder,inhalt"`, `ant netzdb`; dann Tabelle 2.8 füllen | `docs/status/netzdb_Phase3.txt` | unverändert |
| 01.11.2026 | 3 | **Abnahme:** `ant netzdb` grün: `VALIDATE_NETWORK(LB_NET_HERKUNFT)` = TRUE. Das Netz hat 1 781 Knoten und 8 062 Kanten, davon 5 294 von einer Rohdatei zu einer Kachel; 554 Rohdateien werden genutzt. Gegenrechnung: CONNECT BY 504 Ausgaben ohne Abweichung, `traceIn` mit LOD 32 Ausgaben ohne Abweichung. Funktionsprobe 22 von 23 ja (Tabelle 2.8). Befunde: `LINK_LEVEL` = 1 nötig (sonst ORA-06502 beim Partitionieren und „inconsistent link_level“ bei `VALIDATE`); Antwortgrenze von 30 s in `Db`; unter Windows `"-Dschritte=…"` in Anführungszeichen | `docs/status/netzdb_Phase3.txt` | unverändert |
| 01.11.2026 | 4 | **Clipmap** (Freigabe „Phase4, los!“; Entscheidungen: Gelände schon in der App, Package mit Schnittstelle und Kachelordner, im Renderer auf einem eigenen Branch).<br>• **Package `com.dan.clipmap`** im Renderer (Branch `lituya-phase-4`): siehe 5.11. `ClipmapCheck` ist auf einem gerechneten Gelände grün (Schachtelung, Streifen gleich vollem Laden, Naht 7,6e-6 m, Kachelordner zur Probe).<br>• **Hier mit Mesa geprüft:** Baustein gegen Java auf 1,2e-4 m; Bilder des Gilbert Inlet aus vier Kern-, zwei Szene- und zwei Fern-Kacheln.<br>• **App:** Durchgang „Gelände“ vor dem Wasser. Das Wasser schreibt seine Tiefe (`gl_FragDepth`); `groundHeight` kommt aus der Clipmap, damit stehen Kamera und Ansichten auf dem Boden. Farben nach Höhe und Hang [G] bis `surface`; die Qualität der App wählt die Gittergröße. Dazu `scene.Land`, Bedienfeld „Gelände zeigen“, HUD-Zeilen. `Tempo` wartet auf das Gelände. `ant pruefung`: 1 000 Stichproben an Zellmitten gleich der Kachel, Naht.<br>• **Neu in `build.xml`:** `ant clipmapcheck`, `ant clipmapdemo`, `ant clipmapbild`.<br>• **Ausstehend auf dem Rechner:** `Renderer.jar` bauen und kopieren; `ant pruefung`, `ant clipmapcheck`, `ant clipmapbild`, `ant statusbild`; Zeit je Bild auf der Radeon | `docs/status/Vorschau_Phase4_Mesa.png`, `Vorschau_Phase4_Clipmap_Mesa.png` (Mesa, Ausschnitt) | neu zu bauen (mit `clipmap`) |
| 01.11.2026 | 4 | **Abnahme** auf der Radeon: `ant pruefung` 90 Prüfungen, alles in Ordnung. `ant clipmapcheck` grün, mit Grafikkarte (Baustein gegen Java auf 1,2e-4 m) und 1 000 Stichproben gleich der Kachel. `ant clipmapbild` 1,03–1,70 ms je Ansicht auf der Grafikkarte. `ant statusbild` (1920 × 1080, Mittel): bestes Bild 8,14 ms, Mittel 12,61 ms (79 fps); Himmel 0,59, Gelände 6,37, Wasser 2,81, Nachbearbeitung 0,18, HUD 2,18 ms; Gelände in 7,0 s geladen. **Befunde:** Der Durchgang Gelände kostet in der App 6 ms gegen rund 1 ms in der Demo (vermutlich Marken und Luftperspektive in `gelaende.frag`; mit Phase 5a ansehen). Im Bogen von `clipmapbild` ist die untere Hälfte leer (Höhe doppelt). Der Kachelspeicher hält im Kern 50 Kacheln (762 MB) | `docs/status/*_Phase4.*`, `Status_Phase4.png` | mit `clipmap` |
| 01.11.2026 | 5 | **Materialien** (Freigabe „Phase5, los!“; Entscheidungen: Ladeschritt im Werkzeug, alle 13 aus Tabelle 8, der Treiber packt und das Ergebnis wird gemerkt, Renderer-Branch `lituya-phase-5` auf `lituya-phase-4`).<br>• **Package `com.dan.surface`** im Renderer: siehe 5.10. `SurfaceCheck` grün; `check-packages` grün.<br>• **Kartenmaterial:** Gruppe `texturen` mit 13 Quellen `tex_*` (ambientCG, CC0), zusammen rund 330 MB.<br>• **Aufbereitung:** Schritt `texturen` (`tools.Texturen`, Kante `-Dlituya.textur.kante`, Vorgabe 2048) vor `herkunft`; `inhalt` ohne `gpu_*.bin`.<br>• **Neu in `build.xml`:** `ant surfacecheck`, `ant texturbild` (Kontaktbogen `docs/status/Texturen.png`), `ant surfacedemo`. `ant pruefung` mit Abschnitt Materialien (Katalog, gebackene Sätze, Lizenzliste, Budget).<br>• **Hier mit Mesa geprüft:** Kontaktbogen aus Probesätzen, gepackt geladen.<br>• **Ausstehend auf dem Rechner:** `Renderer.jar` bauen und kopieren; Texturen laden und backen; `ant pruefung`, `ant surfacecheck`, `ant texturbild` | `docs/status/Vorschau_Phase5_Mesa.png` (Mesa, Probesätze) | neu zu bauen (mit `surface`) |
| 01.11.2026 | 5 | **Abnahme** auf dem Rechner:<br>• **Laden und Backen:** 13 Sätze geladen. `ant aufbereitung "-Dschritte=texturen,herkunft,inhalt"` in 0,9 min: 14 Materialien gebacken (je rund 3 s bei 2048²), dazu `LIZENZEN.txt`. Herkunft 1 866 Knoten, 8 243 Kanten; `INHALT.txt` 565 Dateien, 8,40 GB.<br>• **`ant pruefung`:** 94 Prüfungen, alles in Ordnung; Budget gepackt bei 2048² 234 MB.<br>• **`ant texturbild`:** 42 Karten vom Treiber in 14,6 s gepackt (1024², 59 MB); Bogen 2,92 ms auf der Grafikkarte.<br>• **Befund 1:** Mit dem echten Strandkies gab `SurfaceCheck` auch der einfach gekachelten Fläche nur 0,02. Das Maß war wertlos: Die schräge Messlinie trifft nie dieselbe Stelle der Kachel, und das feine Korn ist Rauschen. In Phase 6 berichtigt: Linien, auf denen sich die Kachel genau wiederholt, Helligkeit auf 64² und 8² je Kachel gemittelt. Mit dem echten Strandkies (Gravel041) jetzt hex 0,01 / 0,03 gegen einfach 1,00 / 1,00 (hier mit der gebackenen `a.png` gerechnet).<br>• **Befund 2:** Im Bogen zeigten die Strandstreifen fast nur Himmel; die Kamera steht jetzt 4 m hoch, und Platz für die Zeile ist da.<br>• **Befund 3:** Schiefer, verwittert (Rock063) ist sehr gelb (Flechten); mit 5a tönen | `docs/status/*_Phase5.*`, `Texturen.png` | mit `surface` |
| 01.11.2026 | 6 | **Wald und Unterwuchs** (Freigabe „Phase6, los!“; Entscheidungen: Arten prozedural in `forest`; der GPU-Weg neu als `forest/gl/ForestGpu` neben dem alten; Prüfung der anderen Szenen mit Demos und Vergleich vorher/nachher; `understory` voll nach 5.7).<br>• **`com.dan.forest`:** siehe 6.4 (unter der Tabelle). Neu: `TreeStates`, `ForestCulture`, `Octahedral`, `gl/ForestGpu` mit fünf Shadern, `demo/BayScene`, `demo/ForestGpuDemo`, `demo/ForestGpuCheck`. `ForestCheck` hat neue Prüfungen.<br>• **`com.dan.understory`** (neu): siehe 5.7.<br>• **`com.dan.surface`:** das Maß des Kiesstrands berichtigt (Befund 1 aus Phase 5) und der Bogen.<br>• **Hier geprüft:** `ForestCheck`, `UnderstoryCheck` und `SurfaceCheck` grün; `ForestGpuCheck` und beide Demos über eine Brücke zu Mesa (die Java-Klassen selbst, die GL-Aufrufe an llvmpipe); `check-packages` grün.<br>• **LituyaBay:** `PHASE` 6. `ant pruefung` hat einen Abschnitt Wald und Unterwuchs. Die Phase von `scree` ist jetzt „6a“ (`Bibliothek.NEU_PHASE` und `tools/JarPruefung` als Text).<br>• **Neu in `build.xml`:** `ant waldcheck`, `ant waldbild`, `ant waldzeiten`, `ant walddemo`, `ant waldvergleich` (`tools/WaldVergleich.java`), `ant unterwuchscheck`, `ant unterwuchsbild`, `ant unterwuchsdemo`.<br>• **Ausstehend auf dem Rechner:** das alte Jar sichern, das neue bauen und kopieren; `ant waldvergleich`, `ant waldcheck`, `ant waldzeiten` (fps-Tabelle), `ant waldbild`, `ant unterwuchscheck`, `ant unterwuchsbild`, `ant surfacecheck`, `ant pruefung` | `docs/status/Vorschau_Phase6_Wald_Mesa.png`, `Vorschau_Phase6_Unterwuchs_Mesa.png` (Mesa) | neu zu bauen (mit `understory`) |
| 01.11.2026 | 6 | **Abnahme** auf der Radeon: `ant pruefung` 98 Prüfungen, alles in Ordnung. `ant waldcheck` grün (ForestCheck und ForestGpuCheck), `ant unterwuchscheck` und `ant surfacecheck` grün, `ant waldvergleich` 26 von 26 alten Arten unverändert. `ant waldzeiten`: Tabelle 15.5 (LOW „Im Wald“ 10,2 ms, ULTRA 49,7 ms; Kultur ≤ 1,7 ms; 129–328 MB).<br>• **Befund 1:** `lib/FStyle.jar` bringt alte Kopien von `com.dan.forest`, `ground`, `river` und `road` mit. Steht es im Klassenpfad vor `Renderer.jar`, fehlen neue Felder (`NoSuchFieldError: Species.lying`). Die Ziele der Phase 6 legen `Renderer.jar` jetzt zuerst; in NetBeans steht `Renderer.jar` über `FStyle.jar` (Project Properties → Libraries).<br>• **Befund 2:** Auf der Radeon wichen 0,1–1,3 % der Bäume der Grafikkarte im letzten Bit vom Prozessor ab (FMA und `atan`, größte Abweichung 0,017). `ForestGpuCheck` vergleicht jetzt je Platz mit Toleranz (Lage 5 cm, Winkel 0,05 rad, sonst 1e-3).<br>• **Befund 3:** `Unterwuchs.png` war auf der Radeon schwarz (gelesen wurde der nicht getauschte Puffer), die Zahlen in `Wald.png` gehörten zur vorigen Ansicht. Beides in Phase 7 berichtigt.<br>• **Befund 4:** „Im Wald“ kostet die nahe Stufe viel (HIGH 33 fps); Gegenmittel in 6a (15.5) | `docs/status/Wald.png`, `waldzeiten_Phase6.txt` | neu gebaut |
| 01.11.2026 | 7 | **Laufzeit und Netzblick** (Freigabe „Phase7, los!“; Entscheidungen: eigene Gitterklasse `route.TravelGrid`, die dieselben Knoten und Kanten für NDM ausgeben kann; Demo im Renderer mit gerechneter Bucht und dazu ein Ziel über dem echten Grund heute und 1940, linear und nichtlinear; `netview` ganz nach 5.12 als Package mit Demo, in die App mit 7a).<br>• **`com.dan.route`:** siehe 6.9 (unter der Tabelle). Neu: `TravelGrid`, `Contour`, `Measure`, `demo/IsochroneDemo`; `RouteCheck` mit neuen Prüfungen.<br>• **`com.dan.netview`** (neu): siehe 5.12.<br>• **Berichtigt aus Phase 6:** `UnderstoryDemo --bild` einfach gepuffert und liest den gezeichneten Puffer; `ForestGpuDemo --bild` liest die Zahlen je Ansicht.<br>• **Hier geprüft:** `RouteCheck` und `NetviewCheck` grün, `check-packages` grün (netview kennt nur sich und JOGL); `NetviewDemo` über die Brücke zu Mesa gezeichnet; `Isochronenbild` über dem Grund heute (auf 20 m gemittelt) und 1940.<br>• **LituyaBay:** `PHASE` 7. `tools.Isochronenbild`. `ant pruefung` hat einen Abschnitt Laufzeit und Netzblick (Sterne, konstante Tiefe 130 m, Messstrecke, Isolinien, Front über dem Grund heute gegen den Handwert). Packages einer a-Phase (`scree` in 6a) sind bis zu ihrer Phase ein Hinweis statt eines Fehlers (`Bibliothek.nachzuholen`, `tools/JarPruefung`), weil die Phasen der App nach den Renderer-Phasen nachgeholt werden.<br>• **Neu in `build.xml`:** `ant routecheck`, `ant isochronendemo`, `ant isochronenfenster`, `ant isochronenbild`, `ant netviewcheck`, `ant netviewbild`, `ant netviewdemo`.<br>• **Ausstehend auf dem Rechner:** Renderer bauen und kopieren; `ant routecheck`, `ant netviewcheck`, `ant isochronenbild`, `ant isochronendemo`, `ant netviewbild`, `ant unterwuchsbild`, `ant pruefung` | `docs/status/Vorschau_Phase7_Netzblick_Mesa.png` (Mesa), `Vorschau_Phase7_Isochronen.png`, `Vorschau_Phase7_Isochronen_Demo.png` | neu zu bauen (mit `netview`) |
| 01.11.2026 | 7 | **Abnahme** auf der Radeon: `ant pruefung` 103 Prüfungen, alles in Ordnung (ein Hinweis: `scree` aus 6a nachzuholen). `ant routecheck` und `ant netviewcheck` grün: Gitterfehler 2,75 % / 0,75 % wie die Theorie, 16 Nachbarn über 160 801 Zellen in 72 ms. `ant isochronenbild` (Grund heute auf 4 m gelesen): Eingang 365 s linear, 291 s nichtlinear; 1940 355 / 288 s; 0,2 s je Lauf. `ant netviewbild` zeigt auf der Radeon dasselbe wie mit Mesa; `Unterwuchs.png` ist nicht mehr schwarz.<br>• **Befund:** Am Sporn gibt es heute in 500 m kein Wasser (NaN): Der Gilbert Inlet ist vom Gletscher gefüllt (siehe 7.1, Phase 7a) | `docs/status/Isochronen.png`, `Netzblick.png`, `routecheck_Phase7.txt` | neu gebaut |
| 01.11.2026 | 7a | **Netz der Welle, Ufer, Netzblick** (Freigabe „Phase7a, los!“; Entscheidungen: Bucht und 1 km See; Ufer als Netz jetzt, Werte später; Netzblick als Bild im Bild und in der Szene; die App liest die Dateien, Oracle wahlweise).<br>• **Aufbereitung:** Schritte `welle` (`tools.Welle`) und `ufer` (`tools.Ufer`), siehe 7.1 und 7.2; Leser `scene.WellenNetz`, `scene.UferNetz`.<br>• **Oracle:** `01` mit `LB_ANKUNFT`, `LB_ISOCHRONE`, `LB_UFER_GEOM`, `LB_UFER_PUNKT`, `LB_AUFLAUF`, `LB_SPURLINIE_LRS`/`_STUETZ`; `02` mit `LB_NET_WELLE` (räumlich, Benutzerdaten) und `LB_NET_UFER` (LRS); `04` Partitionen und Raumindizes; `05` Sichten; neu `06_ufer.sql`; `99` räumt alles ab. `tools.WelleDb` lädt und rechnet nach; `netz.WelleLod` (LOD). Neu `ant netzvergleich`. Proben 24 bis 30 in 2.8.<br>• **App:** `gl.Netzblick`, Bedienfeld NETZE, Taste N, Zeile im vollen HUD.<br>• **Renderer** (Branch `lituya-phase-7a`): `NetviewGl.gain`.<br>• **Hier geprüft:** beide Schritte über dem Grund (heute auf 20 m gemittelt hochgerechnet); LOD im Speicher gegen `route` auf 0,003 s; `Pruefung` mit neuem Abschnitt grün; die App mit dem Netzblick über die Brücke zu Mesa gezeichnet. Oracle nicht (hier keine Datenbank).<br>• **Ausstehend auf dem Rechner:** Renderer bauen und kopieren; `ant aufbereitung "-Dschritte=welle,ufer"`, `ant netzdb`, `ant pruefung`, App starten (NETZE) | `docs/status/Vorschau_Phase7a_Netzblick_Mesa.png` (Mesa) | neu zu bauen (`gain`) |
| 01.11.2026 | 7a | **Erster Lauf `ant netzdb`** (7 min): Welle und Ufer geladen, Partitionen ja (24: `SPATIAL_PARTITION` 16 Partitionen, BLOBs mit Benutzerdaten; 25 ja). LOD auf `LB_NET_WELLE` gegen `route`: alle 74 483 Knoten, größte Abweichung 0,0016 bis 0,0031 s je Kostenart; Wellenstrahlen (k = 3) mit denselben Zeiten wie `route` (Cenotaph 139,9 s, Anchorage 320,2 s, Eingang 364,7 s).<br>• **Befund 1:** `SDO_UTIL.FROM_WKTGEOMETRY` hat in 21c kein SRID-Argument (PLS-306); jetzt `SDO_GEOMETRY(CLOB, 26908)`.<br>• **Befund 2:** Die Kosten kamen aus der Tabelle, nicht aus den Benutzerdaten, und LOD schrieb beim Laden rund 9 000-mal `NullPointerException` (Laden 286 s). Vermutete Ursache: die Einträge in `USER_SDO_NETWORK_USER_DATA` ohne `CATEGORY_ID`; jetzt mit 0. LOD fragt nach einer Kostenart ohne Benutzerdaten nicht weiter danach, die Meldungen werden gezählt statt ausgegeben.<br>• **Befund 3:** Das Protokoll von `NetzDb` und die umgeleitete Konsole schrieben in dieselbe Datei; das Protokoll heißt jetzt `netzdb_protokoll_Phase7a.txt`.<br>• Der Sporn bekam mit 1 km Suchradius einen Knoten der Einschlaglinie (0 s); jetzt 400 m, also keiner | `netzdb_Phase7a.txt` | — |
| 01.11.2026 | 7a | **Abnahme** auf der Radeon: `ant netzdb` „ok“ (Protokoll `netzdb_protokoll_Phase7a.txt`): `VALIDATE_NETWORK` TRUE für Herkunft, Welle und Ufer; LOD = `route` auf 0,003 s (Probe 27), Ufer `SDO_LRS` = `Measure` auf 5 mm (30), Partitionen ja (24, 25); `LB_ISOCHRONE` 256 Konturen. `ant pruefung` alles in Ordnung. App mit Netzblick (Fotos `Status_Phase7a1` bis `3`). Aufbereitung auf 4 m: 74 483 Knoten, 585 070 Kanten; Eingang 365 s linear, 291 s nichtlinear.<br>• **Befund 1:** Benutzerdaten in LOD gehen nicht (Probe 26, siehe 2.8).<br>• **Befund 2:** `fastWithinCostPolygon` geht nicht (28); Isochronen als Konturen.<br>• **Befund 3:** Laden von LOD dauert 174 s, wohl wegen der Meldungen aus 26 | `netzdb_protokoll_Phase7a.txt`, `Status_Phase7a1.png` | neu gebaut |
| 01.11.2026 | 8 | **Erdbeben** (Freigabe „Phase8, los!“; Entscheidungen: BSSA14; Package und Aufbereitungsschritt `stoerung`; alles aus 5.1 mit Newmark, Seiche, Zittern und Grollen; Demo als Karte mit Zeitreihen).<br>• **`com.dan.quake`** (neu): siehe 5.1.<br>• **LituyaBay:** Schritt `stoerung`, `tools.Bebenbild`, `PHASE` 8, Abschnitt Beben in `ant pruefung`, `Herkunft` kennt `daten/ereignis`; Probe 29 zählt Orte ohne Knoten nicht.<br>• **Neu in `build.xml`:** `ant quakecheck`, `ant quakebild`, `ant quakedemo`, `ant grollen`, `ant bebenbild`.<br>• **Hier geprüft:** `QuakeCheck` grün, `check-packages` grün, Schritt `stoerung` an Qfaults und ComCat, `Bebenbild`.<br>• **Befund:** Newmark versagt nach 23 bis 27 s, nicht nach 60 bis 150 s (siehe 9.1).<br>• **Ausstehend auf dem Rechner:** Renderer bauen und kopieren; `ant aufbereitung -Dschritte=stoerung,herkunft,inhalt`, `ant quakecheck`, `ant quakebild`, `ant bebenbild`, `ant grollen`, `ant pruefung` | `docs/status/Vorschau_Phase8_Quake.png`, `Vorschau_Phase8_Beben.png` | neu zu bauen (mit `quake`) |
| 01.11.2026 | 8 | **Abnahme** auf der Radeon: `ant pruefung` 109 Prüfungen, alles in Ordnung (ein Hinweis). `ant quakecheck` grün. `ant aufbereitung "-Dschritte=stoerung,herkunft,inhalt"`: Spur 345 km aus 228 Punkten, Herd 58,23° N 136,71° W, 10 km, M 7,8. `ant bebenbild`: am Gilbert Inlet Rjb 0,5 km, PGA 0,51 g, Arias 4,77 m/s, D5–95 22 bis 51 s; Newmark FS 1,05–1,15 versagt nach 23–27 s; Seiche 11,2 min. `ant quakebild`, `ant grollen` | `docs/status/Beben.png`, `Quake_Demo.png`, `Grollen.wav`, `*_Phase8.txt` | neu gebaut |
| 01.11.2026 | 9 | **Felssturz** (Freigabe „Phase9, los!“; Entscheidungen: Kern jetzt in Java, Compute Shader mit der Welle in Phase 11; Newmark mit Entfestigung und Auslösung von Hand; Übergabe aus `fracture` mit Demo, Glacier und Sylt unverändert; Preset auf gerechnetem Gelände, Demo in Java2D).<br>• **`com.dan.landslide`** (neu): siehe 5.2.<br>• **`com.dan.fracture`:** Übergabe und Ablösezone, siehe 6.2 (unter der Tabelle).<br>• **`com.dan.quake`:** `Slope` mit Entfestigung (`residual`, `softening`, `cohesionAt`, `acAt`; `Result.runaway`, `acMin`); ohne Rest wie bisher.<br>• **LituyaBay:** `tools.Sturz` (Auslösung über `daten/ereignis`, Platte in `fracture`, Übergabe, Strom; Uhr der Szene), `PHASE` 9, Abschnitt Sturz in `ant pruefung`.<br>• **Neu in `build.xml`:** `ant landslidecheck`, `ant landslidebild`, `ant landslidedemo`, `ant fracturecheck`, `ant sturz`.<br>• **Hier geprüft:** `LandslideCheck` (16), `QuakeCheck` und `FractureCheck` grün (alt gegen neu verglichen), `check-packages` grün (vier Packages je für sich), `tools.Sturz` über den Testdaten, `Pruefung` mit dem neuen Abschnitt.<br>• **Befunde:** Aufprall 105 m/s, aber der Zufluss dauert 16 s statt 6,8 s und ist bis 31 m statt 134 m mächtig; Auslösung nach Newmark auch mit Entfestigung nach 22–35 s (9.2).<br>• **Ausstehend auf dem Rechner:** Renderer bauen und kopieren; `ant landslidecheck`, `ant fracturecheck`, `ant quakecheck`, `ant sturz`, `ant landslidebild`, `ant pruefung` | `docs/status/Vorschau_Phase9_Sturz.png`, `Vorschau_Phase9_Landslide.png` | neu zu bauen (mit `landslide`) |
| 01.11.2026 | 9 | **Abnahme** auf der Radeon: `ant pruefung` 113 Prüfungen, alles in Ordnung (ein Hinweis: `scree` aus 6a). `ant landslidecheck` grün (16 Prüfungen): Aufprall 105,2 m/s im Mittel (FRITZ01 96–110 m/s), höchstens 112,3 m/s; Fahrböschung 16,2° gegen SCHEIDEGGER73 15,8°; Volumen auf 1e-9. `ant fracturecheck` grün (53 Prüfungen): alle alten Zeilen wie vorher, Gletscherfront wie vor Phase 11, Übergabe auf 3,6e-16, 1 000 Stücke 1,85 ms je Schritt. `ant quakecheck` grün (Entfestigung: Gilbert FS 1,10 geht mit Rest 20 % nach 25 s durch). `ant sturz`: 342 Stücke, 3,074e7 m³ übergeben (100,5 % der Platte), im Strom auf 1,4e-14 wieder; Zufluss 105 m/s, 90 % in 16,0 s, bis 30 m senkrecht; Uhr der Szene: Auslösung bei 93 s, Zufluss ab 105 s. `ant landslidebild` und `ant landslidedemo` gelaufen | `docs/status/Sturz.png`, `Landslide_Demo.png`, `*_Phase9.txt` | neu gebaut (mit `landslide`) |
| 02.11.2026 | 10 | **Nahfeld, in Arbeit: 14 von 15 Prüfungen grün, Laborfall bei 885 m noch zu hoch** (Freigabe „Phase10, los!“; Entscheidungen: SPH im Schnitt (2D, je Meter Breite) in Java, Würfel und Compute mit der Welle; Laborfall FRITZ01 und Strom aus `landslide` als zweiter Fall; `Impact.Far` mit einem HLL-Kanal im Check, `wave` in Phase 11; Demo als Java2D-Schnitt. Danach „Einzelwelle zuerst, los!“).<br>• **`com.dan.impact`** (neu, in `Renderer/src`, noch kein Branch, kein Jar): `Impact` (WCSPH, Wendland C2, Hu-Adams, δ-SPH nach Fourtakis, Adami-Wände mit hartem Schutz am Linienzug, Gleitkörper frei auf einer Rampe, Pegel, Kaverne, Energie, Rand `Far`), `Granulate` (Mohr-Coulomb implizit, 16 Jacobi-Schritte), `Inflow` (`fritz01()`), `Channel` (1D HLL, `fromEta`), `Presets.gilbertInlet1958`, `demo/ImpactCheck`, `demo/ImpactDemo`, README. `check-packages` grün.<br>• **FRITZ01 nachgelesen:** Rinne 0,5 m (Schnitt), Granulat PP-BaSO₄, Korn 2,64 t/m³, **Schüttdichte 1,61 t/m³** (39 % Poren), 98,5 · 10³ t je Meter, bis 134 m, 748 m, 6,8 s; Gleitkörper läuft über den Grund aus; Reibungswinkel nicht genannt.<br>• **Prüfung (`impactcheck_Phase10_Zwischenstand.txt`, 8 m, 354 s):** Becken Druck am Grund 405 gegen 402 kPa; **Einzelwelle 21,7 m/s = √(g(h + H))**, Höhe 6,8 → 5,5 m (bei 1 m Abstand 7,4 m nach 12 s: Auflösung); Schüttung 25° steht, 55° sackt auf 34,5° (φ 35°); Kaverne 4 096 m² nach 13 s; Energieanteil 14,1 % (2–30 %); 600 m über 200 m ab 9,4 s; Übergabe an den Kanal 223 gegen 212 m bei 1 100 m; Masse genau; zwei Läufe gleich. **Rot: 885 m 229 m nach 14,8 s statt 152 m nach 16 s.** Über die Auflösung konvergiert (8 m 229, 5 m 206, 4 m 221 m): die Abweichung kommt aus dem Modell des Gleitkörpers.<br>• **Befunde und Berichtigungen:** (1) Der Zulauf als geführter Puffer war ein Stempel mit unbegrenzter Kraft (Körner bis 180 m/s); jetzt liegt der ganze Körper frei auf einer Rampe (Hangabtrieb aufgehoben, keine Reibung), an der Uferlinie kommen 3,9e11 statt 6,0e11 J/m an, weil das Wasser schon bremst. (2) Wandlagen ließen an einspringenden Ecken Wasser durch: harter Schutz am Linienzug. (3) Die Einzelwelle war nicht falsch, sondern die Messung (höchster Stand an zwei Pegeln); jetzt Kamm im geglätteten Profil. (4) Der Pegel zählte eingeschlossenes Wasser unter dem Schutt als Fuß der Säule (Sprünge auf −110 m, Energie 1,3 %); jetzt Säule aus Wasser und Granulat. (5) Mohr-Coulomb braucht den mittleren Druck: mit der senkrechten Auflast blieb die steile Schüttung bei 44° stehen; jetzt SPH-Druck ohne Wasserdruck (Auflast als Schalter `capLoad`). (6) Reibung am Grund mit dem Staudruck (`dynamicBed`) macht die Welle eher höher, Vorgabe aus. (7) Mit dem gemessenen Durchfluss am Rand (samt überkippender Wand) liefe der Kanal falsch; `fromEta`.<br>• **Offen (Vermutungen, nicht geprüft):** Poren füllen sich unter Wasser (39 % weniger Verdrängung) und Phasenmischung nach dem Zusammenbruch der Kaverne (FRITZ01, Stufe b) fehlen; der Körper bleibt eine geschlossene schwere Flüssigkeit.<br>• **Entwurf:** `docs/entwurf_phase10/Eintauchen.java` (Strom aus `landslide` je Meter Breite als `Inflow`, übersetzt, noch nicht gelaufen), kommt nach `tools/`, sobald `impact` im Jar ist | `docs/status/Vorschau_Phase10_Impact.png` | unverändert (`impact` noch nicht im Jar) |
| 02.11.2026 | 10 | **Poren und Rampe** („1 versuchen, 3 als Rückfall, los!“): 14 von 15 Prüfungen grün (Laborfall mit 5 m, 858 s), **885 m: 205 m nach 15,8 s** (FRITZ01 152 m nach 16 s, ±20 %), Zeit stimmt.<br>• **Poren** (`Granulate.porosity`, FRITZ01 0,39; `Impact.poreTime`, Vorgabe sofort [G]): Wasser, das Körnern mit freiem Porenraum näher als 1,5 dx kommt, geht ganz in sie über (Masse, Impuls); die Körner werden schwerer (Ruhedichte je Teilchen), ihr Volumen bleibt. Masse genau (Wasser + 2,7e6 kg/m in den Poren = Anfang). Es füllt sich nur ein Zehntel des Porenraums: Nur Körner am Rand berühren Wasser.<br>• **Rampe:** Hangabtrieb nur aufgehoben, solange das Korn mit der Zuflussgeschwindigkeit fährt; gestaut rutscht es nach (vorher blieben 2 % der Masse liegen). **Pegelmessung:** Kamm im geglätteten Profil, Lücken bis 6 Spalten überbrückt (Energie bei 5 m war 1,3 %, jetzt 13,6 %). **Laborfall im Check mit 5 m** (MEDIUM, rund 10 min; LOW rund 2 min).<br>• **Läufe bei 885 m:** ohne Poren 229 / 206 / 221 m (8 / 5 / 4 m); mit Poren 188 m (8 m), 167 m nach 15,9 s (5 m, mit 2 % der Masse noch auf der Rampe, bestanden), 210 m (8 m, 1 s Füllzeit), **205 m (5 m, Rampe rutscht nach)**. Kleine Änderungen verschieben den Pegel um ±20 %: Das Ergebnis liegt im Mittel rund 30 % über FRITZ01.<br>• Grün: Becken, Einzelwelle (21,7 m/s), Mohr-Coulomb (steht / 34,5°), Kanal, 600 m (über 200 m ab 9,6 s), Kaverne, Energie 13,6 %, Masse, Übergabe (190 gegen 180 m), Wiederholbarkeit | `docs/status/Vorschau_Phase10_Impact.png` (5 m, 11,5 s) | unverändert (`impact` noch nicht im Jar) |
| 02.11.2026 | 10 | **Abnahme mit Hinweis** (Nutzer: „2 nehmen, 1 als eigenen Schritt später“): `impact` gilt als gebaut; die Höhe im Laborfall liegt rund 30 % über FRITZ01 (5 m: 205 m nach 15,8 s gegen 152 m nach 16 s), die Ankunft stimmt. `ImpactCheck` prüft die Ankunft ±2 s und die Höhe −20 % bis +50 % und meldet die Abweichung als Hinweis; Wasser durch den ganzen Gleitkörper wird **Phase 10b** (Tabelle 14).<br>• **`Eintauchen` (zweiter Fall, Werkbuch 9.2):** Strom aus `landslide` (`Presets.gilbertInlet1958`, 8 m) je Meter Breite: 25 480 m² (Laborfall 61 090), 90 % in 20,4 s (5,2), bis 20 m (134), 101 m/s (110). Im Schnitt (8 m) bei 885 m **69 m nach 22,3 s** gegen 235 m nach 14,4 s im Laborfall; Energieanteil 6,1 % gegen 16,5 %. **Antwort auf 9.2:** Ohne Stauchung trägt die Kette Sturz → Eintauchen die Welle nicht; der tiefengemittelte Strom läuft 1,5 km breit und dünn ein. Zu klären mit Phase 13 (Breite der Rinne, Auflockerung, Profil) oder 10b.<br>• **App:** `tools/Eintauchen` (aus dem Entwurf; Strom jetzt aus `gilbertInlet1958`, nicht aus dem leeren Gelände), `PHASE` 10, `lituya.phase` 10, Abschnitt „Nahfeld (Phase 10)“ in `ant pruefung` (Zufluss FRITZ01, Masse samt Poren nach 3 s, Strom je Meter Breite), neue Ziele `ant impactcheck` (`-Dqualitaet=MEDIUM`, rund 10 min; `LOW` rund 2 min), `ant impactbild`, `ant impactdemo`, `ant eintauchen`. Hier übersetzt (ohne `WelleLod`, dafür fehlte `sdoapi.jar`), der neue Abschnitt hält.<br>• **Ausstehend auf dem Rechner:** Renderer-Branch `lituya-phase-10` anlegen und einchecken, Renderer bauen, `dist/Renderer.jar` nach `lib/`; `ant impactcheck`, `ant impactbild`, `ant eintauchen`, `ant pruefung` | `docs/status/Vorschau_Phase10_Impact.png`, `Vorschau_Phase10_Eintauchen.png`, `eintauchen_Phase10_Vorschau.txt` | neu zu bauen (mit `impact`) |
| 02.11.2026 | 10 | **Abnahme** auf der Radeon: `ant impactcheck` (5 m, 194 s) „Alles in Ordnung.“: 885 m 205 m nach 15,8 s (Hinweis +35 %), 600 m über 200 m ab 9,6 s, Kaverne 3 500 m², Energie 13,6 %, Masse samt 2,8e6 kg/m Porenwasser genau, Übergabe 190 gegen 180 m; 8,4 ms je Schritt (hier 42 ms). `ant pruefung` 116 Prüfungen, alles in Ordnung (ein Hinweis: `scree` aus 6a). `ant impactbild`, `ant eintauchen` (Strom 885 m 70 m nach 22,5 s gegen 235 m im Laborfall, 8 m). Renderer als „Update“ (995dfc1) auf dem Branch `lituya-phase-9` eingecheckt, kein eigener Branch für Phase 10 | `docs/status/Impact_Demo.png`, `Eintauchen.png`, `*_Phase10.txt` | neu gebaut (mit `impact`), 56,6 MB |
| 02.11.2026 | 11 | **Welle in der Bucht, Prozessor** (Freigabe „Phase11, los!“; Entscheidungen: geteilt in 11 Prozessor und **11b** Grafikkarte; neuer Löser `wave.Bay` neben `Wave`; Auflauf mit der Messwelle abgenommen, aus `impact` als Hinweis; Demo Java2D).<br>• **`com.dan.wave`:** neu `BayGrid`, `Bay`, `demo/BayDemo`; `WaveCheck` mit neuem Abschnitt „Bucht“, alle alten Zeilen wie vorher; README. `Wave`, `Grid`, `WaveGl` unberührt. `check-packages` grün. Siehe 6.1 (unter der Tabelle).<br>• **Hier geprüft (`wavecheck_Phase11_Vorschau.txt`, 25 Prüfungen, 153 s, alles in Ordnung):** ruhender See über welligem Grund mit trockener Insel über die Gittergrenze 1,8e-14 m/s; Stoker 5,079 m genau; Synolakis R/d 0,0917 (0,0890); drei Gitter Volumen genau; Welle über die Grenze 3–4 % unter durchgehend fein; Fjordbucht der Demo 50 s stabil, Volumen +1,4e-5 (Hinweis); Einlass genau; **Laborfall: Auflauf 542 m (526 ± 50)**; zwei Läufe gleich; 202 ns je Zelle und Schritt.<br>• **Befunde:** (1) Beim Füllen der Randzellen aus dem gröberen Gitter zählten trockene Zellen mit ihrem Grund als Stand und hoben den Rand an; jetzt nur nasse. (2) Halb trockene Blöcke gaben dem gröberen Gitter eine Stufe, die es nicht gibt; jetzt Grund passend zum Stand der nassen Zellen. (3) Den Fehlbetrag an Trockenfronten über Gittergrenzen den feinen Randzellen zu nehmen machte die Bucht instabil; abgeschnitten und gezählt.<br>• **App:** `tools.Auflauf` (impact → `Impact.Far` → `Bay`, gegen die Messwelle; hier: 542 / 684 / 759 m), `PHASE` 11, `lituya.phase` 11, Abschnitt „Welle (Phase 11)“ in `ant pruefung`, Ziele `ant wavecheck`, `ant baybild`, `ant baydemo`, `ant auflauf`. Hier übersetzt (ohne `WelleLod`).<br>• **Ausstehend auf dem Rechner:** Renderer einchecken (am besten auf einem Branch `lituya-phase-11`), bauen, `dist/Renderer.jar` nach `lib/`; `ant wavecheck`, `ant baybild`, `ant auflauf`, `ant pruefung`; Rungholt und Talsperre einmal starten (sollten gleich sein, `Wave` ist unberührt) | `docs/status/Vorschau_Phase11_Bay.png`, `wavecheck_Phase11_Vorschau.txt`, `auflauf_Phase11_Vorschau.txt` | neu zu bauen (mit `wave.Bay`) |
| 02.11.2026 | 11 | **Abnahme** auf der Radeon: `ant wavecheck` 25 Prüfungen, alles in Ordnung (alte Zeilen wie vorher; Auflauf 542 m; Fjordbucht +1,4e-5 mit Hinweis; 107 ns je Zelle und Schritt, 1,34 s Welle je Sekunde Rechnung bei 512²). `ant baybild` (50 s Welle in 10,9 s), `ant auflauf` (542 / 759 / 684 m; impact 8 m in 34 s). `ant pruefung` 118 Prüfungen, alles in Ordnung. **Rungholt und Edersee-Demo gestartet: sehen gleich aus** (Nutzer). Renderer als „Update“ (b3df7f3) auf `lituya-phase-9` eingecheckt | `docs/status/Bay_Demo.png`, `*_Phase11.txt` | neu gebaut (mit `wave.Bay`), 56,7 MB |
| 02.11.2026 | 11b | **Welle in der Bucht, Grafikkarte** (Freigabe „Phase11b, los!“; Entscheidungen: float mit Spielraum – glatt 1 mm, Laborfall-Auflauf 1 %, Volumen 1e-6; alles auf der Grafikkarte samt Verschachtelung; Shader hier gegen den Prozessor geprüft, Java bei dir).<br>• **`com.dan.wave.gl.BayGpu`** neu mit acht Compute Shadern `bay_*.comp` (siehe 6.1). `Bay`, `BayGrid`: nur Zugriffe dazu, Rechnung unverändert; `WaveCheck` nur ein Hinweistext.<br>• **`demo/BayGpuCheck`** (ohne Fenster): Prozessor gegen Grafikkarte mit denselben Zeitschritten, danach Zeiten je Gitter. Hier gelaufen mit Mesa 25.2 (llvmpipe) über eine Brücke von JOGL nach PyOpenGL – also der Java-Code selbst, nicht nur die Shader: Ruhe mit Insel über die Gittergrenze 3,8e-6 m, Stoker 1,7e-5 m, Fjordbucht (3 Gitter) Auflauf 89,37 = 89,37 m, Volumen 1e-7, Tiefe Median 1,3e-4 m, 99 % 3,4 cm (einzelne Zellen an der Trockenfront 3,6 m [G]), Laborfall bis 50 s Auflauf 542,2 = 542,2 m, Volumen 2,6e-6, mit eigenem Zeitschritt der Karte Pegel 1 100 m 137 = 137 m: „Alles in Ordnung.“ Zeiten misst Mesa nicht.<br>• App: `lituya.phase` 11b, Ziel `ant baygpucheck`; `PHASE` im Renderer bleibt 11 (nichts Sichtbares neu). `check-packages`: 59 Packages unabhängig; `tools/JarPruefung` liest jetzt Teilphasen mit beliebigem Buchstaben („11b“ brach zuerst mit NumberFormatException ab). | — | neu bauen (mit `wave.gl.BayGpu` und den `.comp`) |
| 02.11.2026 | 11b | **Abnahme** auf der Radeon: `ant baygpucheck` „Alles in Ordnung.“ (Ruhe 5,5e-6 m, Stoker 1,8e-5 m, Fjordbucht Auflauf 89,37 = 89,37 m, Volumen 2e-7, Median 1,5e-4 m, 99 % 5,1 cm; Laborfall 542,2 = 542,2 m, Volumen 4,8e-6 im Spielraum 1e-5; eigener Zeitschritt Pegel 137 = 137 m). Zeiten: 7,6–12,6 ns je Zelle auf der Karte gegen 38–101 ns auf dem Prozessor, 4,3- bis 6,8-mal schneller (Tabelle 15.2). `ant wavecheck` 25 Prüfungen wie Phase 11, `ant pruefung` 118 Prüfungen, alles in Ordnung. Zuerst brach `check-packages` an „11b“ ab (behoben). | — | neu gebaut (mit `wave.gl.BayGpu`) |
| 02.11.2026 | 12 | **Staub und Treibgut** (Freigabe „Phase12b, los!“, gemeint 12; Entscheidungen: beide Packages in einer Phase, Staub als Schwerestrom im Gitter, Treibgut als Stämme und Teppichfeld, Demos Java2D, Strömung aus `wave.Bay` in der App).<br>• **`com.dan.dustcloud`** neu (5.4): `DustcloudCheck` 12 Prüfungen, alles in Ordnung – Stokes 0,200 m/s und 2,00 mm/s, ruhende Schicht 0,5797 gegen e^(−wt/h) 0,5793, Schleuse: Nase 3 745 m gegen Kastenmodell 3 798–4 240 m, Hang ins Wasser, Bilanz genau, verdünnte Wolke zieht mit 9,83 von 10 m/s, dichte läuft 610 m gegen den Wind, Sicht 13,0 m wie von Hand, zwei Läufe gleich; 160–200 ns je Zelle und Schritt. Demo: Felssturz an einer Fjordflanke.<br>• **`com.dan.debris`** neu (5.5): `DebrisCheck` 13 Prüfungen, alles in Ordnung – Tiefgang 0,230 m (0,45 des Querschnitts), Strömung c/(1 + α) genau, Wind 0,361 m/s = Hand (α 3,7 %), Welle 30 m/s ohne Überschwingen, Stranden am Strand 1:20, Stoß ohne Durchdringen mit Impuls genau, Teppich aus 150 Stämmen bleibt zusammen (1,05), sinkt nach 281 Tagen, Feld: Drift 0,561 m/s genau, Stau höchstens 0,600, Fläche genau. Demo: Bucht mit Wirbel, 400 Stücke und der Teppich über drei Tage.<br>• **App:** `tools.Treibgut` (Fjordbucht aus `wave.Bay`, 300 Stämme am Hang des Arms: die Welle reißt 299 an, bis 35 m/s, 97 schwimmen nach 60 s), `tools.Staubwolke` (Sturz aus `landslide` gibt Staub an `dustcloud`, siehe 9.2). Ziele `staubcheck`, `treibgutcheck`, `staubbild`, `treibgutbild`, `staubdemo`, `treibgutdemo`, `treibgut`, `staubwolke`; `Pruefung` mit Abschnitt „Staub und Treibgut“; `lituya.phase` und `PHASE` 12; `Bibliothek.rang` liest Teilphasen wie `JarPruefung`. `check-packages` 61 Packages unabhängig. Prüf- und Demoklasse heißen nach Paketvertrag `DustcloudCheck`, `DustcloudDemo` (zuerst `DustCheck`: `check-packages` fand die fällige Prüfklasse nicht). | Vorschau_Phase12_*.png | neu bauen (mit `dustcloud`, `debris`) |
| 02.11.2026 | 12 | **Abnahme** auf der Radeon: `ant staubcheck` 12 Prüfungen und `ant treibgutcheck` 13 Prüfungen „Alles in Ordnung.“ (Werte wie hier; 102 ns je Zelle und Schritt für die Wolke, 9,7 µs je Stamm und Teilschritt), `ant staubbild`, `ant treibgutbild`, `ant treibgut` (15 s), `ant staubwolke` (5 s), `ant pruefung` 120 Prüfungen, alles in Ordnung. Zuerst fand `check-packages` die Prüfklasse nicht (`DustCheck` statt `DustcloudCheck`, umbenannt). | Staub_Demo.png, Treibgut_Demo.png, Treibgut.png, Staubwolke.png | neu gebaut (mit `dustcloud`, `debris`) |
| 02.11.2026 | 13 | **Das Ereignis, Vorrechnung** (Freigabe „Phase13, los!“; Entscheidungen: geteilt in 13 Vorrechnung, 13b Abspielen und Schaustücke, 13c Echtzeit; zwei Läufe Kette und Messwelle; Grafikkarte; Grund 1940 mit heute aufgefüllt).<br>• **`wave`:** `Bay.line` – Einlass quer durch die Bucht (WaveCheck: Laborfall über die Linie 543 gegen 542 m; BayGpuCheck: Grafikkarte gleich, `bay_force.comp`); Zellen unter einem feineren Gitter zählen nicht mehr für den Zeitschritt (dünne Filme dort drückten ihn auf 10⁻⁴ s; Fjordbucht jetzt 395 statt 453 Schritte, sonst alle Zeilen gleich); BayGpuCheck prüft auch den eigenen Zeitschritt (1,9e-7).<br>• **App:** `scene.Boden1958`, `scene.EreignisDaten`, `tools.Ereignis` (4.4), Ziel `ant ereignis` (-Dlauf, -Dqualitaet, -Dzeit, -Drechner, -Dpegel); `Pruefung` mit Abschnitt „Ereignis“ (Daten schreiben und lesen, Lage der Linie, Grund 1958, Lauf vorhanden); `lituya.phase` und `PHASE` 13.<br>• **Hier gerechnet** (Prozessor, LOW, mit einem Auszug des Grundes vom Rechner): siehe 9.5 Befund; Staub 0,21 Mio. m³, nach 300 s 48 % in der Luft, 40 % im Wasser. | Vorschau_Phase13_Ereignis_messwelle_LOW.png | neu bauen (`wave` geändert) |
| 02.11.2026 | 13 | **Abnahme mit Hinweis** auf der Radeon: `ant wavecheck` 26, `ant baygpucheck` „Alles in Ordnung.“ (Linie 542,6 = 542,5 m, Zeitschritt 2,2e-7), `ant pruefung` 124 Prüfungen, alles in Ordnung. `ant ereignis` MEDIUM: Kette 562 m am Sporn, Messwelle 409 m (9.5 Befund). Hinweis: LOW brach auf der Grafikkarte ab (NaN), und der letzte LOW-Lauf überschrieb `daten/ereignis` – beides in 13a behoben. | Ereignis_kette.png, Ereignis_messwelle.png | neu gebaut |
| 02.11.2026 | 13a | **Vergleich** (Freigabe „Phase13b? oder a, los!“ – 13a zuerst, weil die Welle in der Bucht klein bleibt; keine Grundsatzfragen: Spurlinie je Ufermeter aus dem höchsten Stand an Land bis 400 m [G], Ankunft im Wasser bis 200 m).<br>• **`tools.Vergleich`**, Ziel `ant vergleich`: je Lauf `daten/netz/ufer/spurlinie_<lauf>.tsv` (alle 50 m auf Ufer heute und Cenotaph Island: Auflauf, Ankunft), Tabellen belegt gegen gerechnet (±300 m entlang des Ufers und 1 km um den Ort), Ankunft gegen `LB_NET_WELLE` (heute, 1940, nichtlinear) und die Zeitleiste 2.2, Bild `Vergleich.png`. Zeugen: erst mit 0b.<br>• **Datenbank:** `WelleDb.ladeUfer` lädt die gerechneten Spurlinien als Rechnung (Jahr 1958, Quelle „Rechnung wave.Bay, Lauf …“) in `LB_SPURLINIE_LRS` mit `LB_SPURLINIE_STUETZ` und vergleicht in SQL je belegtem Auflauf 1958 (`ant netzdb`).<br>• **`wave`:** `BayGrid.maxVelocity` (150 m/s, auch im Shader) und NaN zu trocken – gegen den Abbruch von LOW auf der Grafikkarte; WaveCheck 26 und BayGpuCheck (kurz) hier weiter grün. **`tools.Ereignis`:** Vorgabe MEDIUM, LOW nach `<lauf>_LOW`, Meldung bei Abbruch.<br>• Hier mit dem LOW-Lauf vom Prozessor (Messwelle, 420 s) erprobt: Sporn 245 m (±300 m) / 253 m (1 km) gegen 524 m, Mudslide Creek 61 gegen 208 m; Ankunft Cenotaph 142 s (Netz 140), Anchorage 341 (320), Eingang 373 (365). `Pruefung` mit Abschnitt „Vergleich“; `lituya.phase` 13a. | Vorschau_Phase13a_Vergleich_LOW_cpu.png | neu bauen (`wave` geändert) |
| 02.11.2026 | 13a | **Abnahme** auf der Radeon: LOW läuft jetzt auch auf der Grafikkarte durch (`ereignis_Phase13a_beide_LOW.txt`), MEDIUM wie in 13. `ant vergleich` (gemessen am Ufer von heute, ±300 m): **Kette** Sporn 350 m gegen 524 m (−33 %), Mudslide Creek 118 gegen 208 m (−43 %), Ufer heute Median 30 m, 90 % unter 209 m, Cenotaph Island 21–24 m; **Messwelle** Sporn 278 m (−47 %), Mudslide Creek 68 m, Cenotaph Island 9–10 m. Ankunft (Cenotaph / Anchorage / Eingang): Kette 121 / 293 / 321 s, Messwelle 140 / 331 / 364 s, Netz 140 / 320 / 365 s. `ant pruefung` 125 Prüfungen, alles in Ordnung. `ant netzdb`: Probe wie 7a (26 und 28 mit N, 28 wie bisher „Invalid number of nodes“); der Anfang des Protokolls fehlt (Puffer der Konsole), das Laden der Spurlinien ist darin nicht zu sehen. **Befund:** Die Kette trifft die Ankunft früh (−19 s bei Cenotaph) und bleibt am Sporn und im Hauptteil der Bucht zu niedrig; die Messwelle trifft die Ankunft, ist aber kleiner. | `Vergleich.png`, `vergleich_Phase13a.txt` | neu gebaut |
| 02.11.2026 | 13b | **Abspielen und Schaustücke** (Freigabe „Phase13b, los!“; Entscheidungen: Welle als Höhenfeld je Gitter; Strom mit Vorrat und weichem Übergang, Zeitlupe bis 1:20; Strom als Netz, Staub als Volumen; Kamera + Uhr je Schaustück, „Unter Wasser“ knapp unter der Fläche).<br>• **App:** `scene.Wiedergabe` (Vorrat, Faden, Paare), `gl.EreignisGl` mit `spur.vert`, `welle.frag`, `strom.frag`, `medien.frag` (siehe 4.4); neue Durchgänge **Welle** (nach dem Gelände) und **Staub** (nach dem Wasser); `meer.frag` mit Boden 40 m unter dem Pegel im Rechteck der Welle; `gelaende.vert` mit dem Rückbau des Gilbert Inlet (außer „heute“); `EreignisDaten.roh`; `SceneClock`: Zeitraffer auch rückwärts; `camera.Schaustuecke` (10.3).<br>• **Bedienfeld EREIGNIS:** Abspielen an/aus, Lauf der Welle, ◀ ■ ▶ (J, K, L), Tempo 1:1 bis 1:20 (Z), „93 s“ (Auslösung), Schaustücke 1–7; die Leertaste merkt sich das Tempo (auch rückwärts). HUD: Zeile „Ereignis t = … · Zeitlupe 1:20 rückwärts · Welle kette, Strom, Staub“, im vollen HUD Vorrat und Kacheln.<br>• **Werkzeuge:** `tools.Ereignis` schreibt den Grund unter Strom und Staub mit; `Tempo` mit `-Dschaustueck`, `-Dereignis`, `-Dlauf`, `-Dabspielen`; Ziel `ant schaustuecke` (Standbilder 2–7). `Pruefung` mit Abschnitt „Abspielen“ (Läufe, Klammer und Nachlauf, Mischen und trocken, Wasserstand am Ort, Kacheln, Stufen und Blick, rückwärts in Zeitlupe, Schaustücke, Durchgänge); hier 13 von 13 grün (ohne `daten/`). `lituya.phase` 13b. **Renderer unverändert** (kein neues Jar).<br>• Hier mit Mesa erprobt (LOW-Lauf der Messwelle vom Prozessor, ohne Gelände, Grund unter Strom und Staub nur als Platzhalter): alle Shader übersetzen; Welle, Staub vor und hinter dem Strom, Blick von unten. **Befunde:** Schneller Rauschanteil der Normalen ergab aus 60 m Höhe Facetten, jetzt die Lagen aus `meer.frag`; das Laden des Staubs band auf Texturstelle 0 und nahm `medien.frag` die Tiefe (Staub und Trübe lagen über allem), jetzt erst laden, dann binden; wo die Welle trocken ist und das Gelände von heute tiefer liegt, sah man den Himmel, jetzt der Boden unter dem Pegel. | `Vorschau_Phase13b_Sturz_Mesa.png`, `Vorschau_Phase13b_Welle_Mesa.png`, `Vorschau_Phase13b_UnterWasser_Mesa.png` | unverändert |
| 02.11.2026 | 13b | **Abnahme** auf der Radeon: `ant pruefung` 145 Prüfungen, alles in Ordnung (2 Hinweise: der Grund unter Strom und Staub fehlte noch – danach mit `ant ereignis -Dlauf=staub` geschrieben, 18 s; Staub 0,21 Mio. m³, Wolke bis 226 m). `ant schaustuecke` (Lauf Messwelle, 1920 × 1080 Mittel): 2 Beben 74 Bilder/s; 3 Sturz 95; 4 Wand 58 (Welle 0,71, Staub 2,61 ms); 5 Sporn 54 (Welle 2,75, Staub 2,55); 6 Front 46 (Gelände 4,84, Welle 2,98, Staub 2,35; 436 Kacheln, 1,1 Mio. Dreiecke); 7 unter Wasser 66. Auspacken 2 ms je Bild, Vorrat 52 MB, die Bilder um die Zeit lagen beim Start schon bereit. **Befunde:** (1) Schaustück 3 steht bei 88 s noch vor dem Sturz (Strom ab 93 s, Staub ab 98 s), als Standbild leer, im Lauf richtig. (2) Der Staub in 4 ist ein heller Schleier mit harter Unterkante (Schicht bis auf Grund und Wasser, 25 m Zellen) – mit `sky` (5a) und Licht noch einmal ansehen. (3) Unter Wasser (7) ist das Bild einheitlich: mit 15° Blick nach oben lag das Snellsche Fenster außerhalb; jetzt 35°. | `Schaustueck_2…7_Phase13b.png`, `schaustuecke_Phase13b.txt` | unverändert |
| 02.11.2026 | 13c | **Was wäre wenn, in Echtzeit** (Freigabe „Phase13c, los!“; Entscheidungen: beide Quellen – an der Wand von 1958 die Ganglinie skaliert, anderswo Volumen-Impuls-Quelle; feste Orte und Strg-Klick; vorwärts mit Rückspulpuffer; Rechnung und Gelände umschalten).<br>• **App:** `gl.Echtzeit` (Aufbau im Faden, Rechnung im Bildlauf mit Budget, Quellen, Heller/Hager), `scene.LiveLauf` (Rückspulpuffer), `Wiedergabe` spielt Dateien oder die Echtzeit (`Spur` mit `Bilder`, Vorrat räumt auch beim Einlegen), `Boden1958.hoeheHeute`, `Ereignis.Quelle` (Linie an jedem Ort, die von 1958 wie bisher), `Ereignis` schreibt `linie_kette.tsv` (`-Dlauf=linie`), `Renderer.treffer` (Strg-Klick ins Gelände oder Wasser), Rückbau im Gelände aus, solange die Echtzeit mit Grund heute rechnet; HUD-Zeile der Echtzeit.<br>• **Bedienfeld EREIGNIS „Was wäre wenn“:** Ort (fünf feste und Strg-Klick), Volumen 1–300 Mio. m³ (logarithmisch), Tide −3 bis +4 m, Grund 1940/heute, Ganglinie Kette/Messwelle, „Rechnen ab 93 s“, „Zur Vorrechnung“; J/K/L und Z wie beim Abspielen. Schaustück 7 blickt 35° hinauf.<br>• **Werkzeug:** `tools.EchtzeitTempo`, Ziel `ant echtzeit` (Wand 1958 und Crillon Inlet, je 90 s, Bilder je Sekunde, Rückstand, zwei Bilder). `Pruefung` mit Abschnitt „Was wäre wenn“ (Linie am Hang und aus dem Wasser zurück, Heller/Hager, Linie 1958, Orte, Regler, Strg-Klick-Strahl, Rückspulpuffer mit Wiedergabe, `linie_kette.tsv`, Grund 1940 gegen heute an der Linie); hier 10 grün, Grund mangels `daten/grund` als Hinweis. `lituya.phase` 13c; Renderer unverändert.<br>• Hier mit Mesa über die Brücke auf einem gerechneten Fjord erprobt: Crillon Inlet mit 30,6 Mio. m³ gibt P = 1,84, Wellenberg 108 m über 48 s bei 150 m Tiefe; 300 m vor der Linie nach 20 s 49 m; Rückspulpuffer und Wiedergabe spielen die Bilder. **Befund:** Der Vorrat wuchs beim Einlegen frischer Bilder ohne Grenze (64 MB nach 25 s), jetzt räumt auch das Einlegen. | `Vorschau_Phase13c_Echtzeit_Mesa.png` | unverändert |
| 02.11.2026 | 13c | **Abnahme mit Befund** auf der Radeon: `ant pruefung` 156 Prüfungen, alles in Ordnung (an der Linie von 1958 Grund 1940 −122 m, heute +11 m). `ant ereignis -Dlauf=linie`: impact LOW 235 m nach 14,4 s, 46 s, `linie_kette.tsv`. `ant echtzeit` meldete „über 10 Bilder je Sekunde“ (Wand 1958 im Mittel 76, Crillon 117 Bilder/s), **die Messung taugt aber nicht**: Ohne Bildwechsel staute die Grafikkarte die Bilder (Zeiten der Durchgänge 0,0, „365–554 ms je Bild“ für die Rechnung), jeder Schritt der Welle wartete auf den ganzen Stau, und die Uhr lief nur rund 0,3 s je Sekunde (Bilder über 0,1 s zählen nur 0,1 s; 93 → 124 s in 90 s). `EchtzeitTempo` wartet jetzt nach jedem Bild auf die Grafikkarte wie ein Fenster und prüft auch die Uhr (über 0,9 s je Sekunde). Crillon Inlet: P = 75,7, Wellenberg 21 m bei 14 m Tiefe (der Kopf des Inlet ist flach; Höhe auf 1,5 h begrenzt). | `Echtzeit_Phase13c_0_b.png`, `_2_b.png` | unverändert |
| 02.11.2026 | 14 | **Störung und Wiederbewaldung** (Freigabe „Phase14, los!“; Entscheidungen: Zellautomat auf dem Pflanzgitter 6 m; Strömung aus der Tiefe u = Fr·√(g h); Abgleich am DSM 2012 in 14a; Vorführung in GL mit `forest`).<br>• **Renderer, neu `com.dan.succession`** (5.6, 9.7): `Art`, `Boden`, `Wellenwurf`, `Succession`, `demo/SuccessionCheck` (18 grün), README. `check-packages`: 62 Packages unabhängig.<br>• **Renderer, `forest`:** `TreeStates.setGrowth` (Nachwuchs: Art und Größe je Platz), `ForestCulture` und `forestgpu_cull.comp` stellen dort immer einen Baum dieser Art in dieser Größe, Textur der Zustände R16UI; `ForestGpuCheck` mit fünftem Blick (hier über die Brücke mit Mesa: alle fünf Blicke gleich dem Prozessor, 14 651 junge Bäume ohne Abweichung).<br>• **App:** `tools.Wiederbewaldung` (die Bucht der Walddemo mit dem Band am Nordufer, Spurlinie 30–524 m: 257 000 Plätze, 68 Jahre in 4,3 s; Fenster mit Jahresregler und Zeitraffer, `--bild` zwei Bögen der Jahre 0/10/30/68), Ziele `ant successioncheck`, `ant wiederbewaldung`, `ant wiederbewaldungbild`; `Pruefung` mit Abschnitt „Wiederbewaldung“ (succession aus dem Jar, Nachwuchs über die Kultur von forest); `lituya.phase` und `PHASE` 14. `EchtzeitTempo` berichtigt (13c).<br>• **Befunde:** Die Erle kam erst spät, weil nur der Altbestand sie säte und nur am Ufer: 8 % Erle im Altbestand und 15 % Fernflug (Sturm, Vögel) [G]; auf frischem Mineralboden keimen Fichte und Hemlock schlechter (0,35 / 0,12) [G]. Je breiter das Band, desto langsamer: in der Bucht der Vorführung (bis über 1 km breit) 47 % Deckung nach 68 Jahren, am Hang der Prüfung 81 %. | `Vorschau_Phase14_Wiederbewaldung_Mesa.png` | **neu bauen** (`succession`, `forest` geändert) |
| 02.11.2026 | 14 | **Abnahme** auf der Radeon: `ant pruefung` 160 Prüfungen, alles in Ordnung (1 Hinweis). `ant successioncheck`: SuccessionCheck und ForestGpuCheck „Alles in Ordnung.“ (auch die 14 651 jungen Bäume auf der Karte gleich dem Prozessor). `ant wiederbewaldungbild`: 257 052 Plätze, 68 Jahre in 2,8 s; Jahr 10 Deckung 4 %, Jahr 30 14 %, Jahr 68 47 % (Roterle 63 %), Bögen `Wiederbewaldung_Spurlinie.png` und `Wiederbewaldung_Band.png`. **13c nachgeholt** (`echtzeit_Phase14.txt`, mit Warten auf die Grafikkarte): Wand 1958 im Mittel 49,4 Bilder/s ab der Welle (kleinstes 27,6), Uhr 0,99 s je Sekunde, Rückstand höchstens 1,0 s; Crillon Inlet 92,5 Bilder/s, Uhr 1,00, Rückstand 0,9 s – beide „ABNAHME: über 10 Bilder je Sekunde, Uhr in Echtzeit, die Rechnung hält Schritt“; damit ist 13c abgenommen (`echtzeit_Phase13c.txt` ist der erste Lauf ohne Warten auf die Grafikkarte, seine Zahlen taugen nicht). **Befund:** Im Blick „Über dem Band“ wirkt der Altbestand in der Ferne kahl (Impostors im Dunst); in der Szene trägt das Gelände dort die Waldfarbe. | `Wiederbewaldung_*.png` | gebaut |
| 02.11.2026 | 14a | **Wald in der Szene** (Freigabe „Phase14a, los!“; Entscheidungen: Wald jetzt in die Szene mit `ForestGpu` und den drei Arten, Bestand aus Höhe und Hang [G], Zustände aus `succession`; Band gerechnet und auf die Belege gezogen, umschaltbar auf rein gerechnet; Abgleich ArcticDEM − Gelände als Schritt `wald`; Treibgut aus dem Wellenwurf bis zum Morgen).<br>• **Aufbereitung, Schritt `wald`** (`tools.Wald`): ArcticDEM-Mosaik 10 m (Kachel 42_06, Streifen 2009–2021, Ellipsoid WGS84, EPSG 3413 – neu `Abbildung.Polar`, polarstereographisch nach Snyder) minus Gelände Szene (IfSAR 2012). Versatz Ellipsoid − NAVD88 auf kahlem Fels gemessen: **5,11 m** (226 016 Zellen; am Strand 5,25 m). `daten/wald/kronenhoehe_10m.r32` (Kern, 2 516 379 gültige Zellen; 589 947 außer Bereich: Gletscher, Rutschungen) und `WALD.txt`. Im Band 1958 im Mittel 8,5 m (Deckung über 2 m 74 %, 90 % unter 19,3 m), gleiche Höhenlage außerhalb 11,9 m, Wald außerhalb 17,7 m.<br>• **Befund Kronenhöhe:** Die gemessenen Kronen sind zu niedrig (99 % unter 26 m, wo Sitka-Fichten 40–60 m erreichen): Das IfSAR-Gelände liegt unter dichtem Wald zu hoch, und das Mosaik glättet auf 10 m. Abgeglichen wird darum das **Verhältnis** Band zu Wald außerhalb (Ziel 8,5 · 40/17,7 = 19,1 m im Jahr 2011 = Jahr 53) [G]: **Wuchs ×1,69** auf die Rate k aller Arten (Halbierung, 8 Läufe). Die Wiederbewaldung rechnet damit Deckung 3 % (1958) → 20 % (1968) → 53 % (1988) → 79 % (2011) → 83 % (2025), im Band 19,1 m (2011). Die frühen Jahre bleiben zu licht (Anwuchs langsam, Wuchs dafür schnell) – gemessen ist nur eine Zahl.<br>• **Band 1958** (`scene.Band1958`): Spurlinie der Kette (13a) je Uferpunkt, gezogen auf die Belege: Sporn ×1,50 (dort 523 m gegen 524), Mudslide Creek ×1,77 (208 m), dazwischen längs des Ufers linear, außerhalb gleich, Cenotaph Island ×1,63 [G]. Über flachem Land sinkt die Spurlinie mit der Entfernung vom Ufer auf 0 in 300 m + 10·T [G]; sonst lief das Band an der Mündung 1,5 km ins Flachland. Höchster Stand g + 0,35·(T − g) + 0,5 m wie in 14. **7,2 km², 200 313 Plätze**: unberührt 22 453, steht 6 297, entrindet 52 500, gebrochen 46 839, umgelegt 6 158, fort 66 066. Schalter „rein gerechnet“ im Bedienfeld.<br>• **`scene.Wald1958`:** Boden 1958 (mit Rückbau), Band, Kronenhöhe, Altbestand aus Höhe und Hang [G] (geschlossen bis 650 m, licht bis 900 m, nicht über 45°). Dichte für `forest`: im Band der Altbestand, sonst aus der Kronenhöhe; kein Wald im Wasser und im Rückbau. Wiederbewaldung über das Band (rund 6 s, 69 Jahre bis 2027).<br>• **`gl.WaldGl`** und Durchgang **„Wald“** zwischen Gelände und Welle: `ForestGpu` über 24 km (Gelände 4096², Dichte 2048²), Stufe folgt der App, Licht aus dem Platzhalter-Himmel. Zustände nach der Uhr: Vor dem Stoß steht alles. Während der Welle bekommt jeder Platz im Band seine Folge, sobald die Welle an seinem Ufer ankommt (Ankunft der Kette, alle 2 s). Ab einer Stunde danach gilt der Jahrgang zum Datum (Jahr 0 ab 10.7.1958); „danach“ und „heute“ ergeben sich so. Boden im Band (Fels, Mineralboden, Waldboden je Jahr) als Textur im Gelände (`gelaende.frag`). Rechnung im Hintergrund beim Start (rund 15 s hier).<br>• **Treibgut** (`tools.Treibgut1958`, `ant treibgut1958`): 57 087 fortgerissene Bäume gehen hangab ins Wasser, der Rückstrom trägt sie 50–600 m hinaus [G]. 3 778 Stämme (Auswahl) treiben mit `debris` 8 h bis 6 Uhr im Fallwind 3 m/s buchtabwärts und 5 cm/s Ausstrom [G]: 482 schwimmen, 327 auf Grund, 2 969 an Land. Holzteppich 2,66 km² (`Carpet`, 25 m): 2,46 km² schwimmend, 0,13 km² gestrandet. In der Szene am Morgen danach (bis drei Wochen): Stämme als umgelegte Bäume auf dem Wasser, der Teppich in `meer.frag`.<br>• **Jahres-Zeitraffer** (`Renderer.jahresRaffer`, 68 Jahre je Minute, Uhrzeit bleibt, hält am 9.7.2026). Bedienfeld EREIGNIS „Wald und Wiederbewaldung“: Wald, Band gezogen, Treibgut, Jahr nach der Welle, „Morgen danach“, „68 Jahre (Y)“, „heute“. **Schaustücke 10 „Der Morgen danach“ und 11 „68 Jahre“** (10.3).<br>• **`tools.Waldkarte`** (`ant waldkarte`): Karte des Bands mit den Folgen, dem Treibgut und heute. `Pruefung`: Abschnitt „Wald in der Szene“ (EPSG 3413 an der Kachelmitte auf 0,1 m, Jahr zum Datum, Schaustücke, Band gezogen am Sporn 523 m, daten/wald, Wiederbewaldung, kein Wald im Wasser, Treibgut – hier 10 grün). `Tempo` wartet auf den Wald (`-Dwald=aus`). `lituya.phase` 14a.<br>• **Renderer:** `succession.Succession.altArt(k)`, `altHoehe(k)` (für das Treibgut), README.<br>• **Befunde:** Lituya liegt ganz in der Kachel 42_06 (42_05 trägt nichts bei). Die Brücke reicht keine 227 MB; für die Probe hier wurde der Ausschnitt auf deinem Rechner mit tifffile geschnitten (`_p5/42_06_10m_v4.1_lituya.tif`). Der Schritt `wald` liest bei dir die ganze Kachel. Der Holzteppich treibt im gleichförmigen Wind in Streifen der Zellen; darum geglättet (2 × 3 × 3) [G]. | `Vorschau_Phase14a_*.png` (Mesa) | **neu bauen** (`succession`) |
| 02.11.2026 | 14a | **Abnahme** auf der Radeon: `ant pruefung` 169 Prüfungen, alles in Ordnung (1 Hinweis: `scree` aus 6a). Zuerst 2 Fehler in alten Prüfungen aus 13b (genau 7 Schaustücke, genau 7 Durchgänge), berichtigt. `ant aufbereitung -Dschritte=wald` wie hier: Versatz 5,11 m, im Band 8,5 m, Wuchs ×1,691; `ant treibgut1958` in 60 s (57 087 Bäume fort, 3 778 Stämme, Teppich 2,66 km²); `ant waldkarte` (`Wald1958.png`, `Wald1958_2025.png`). **`ant waldszene`** (1920 × 1080, Mittel): **10 „Der Morgen danach“ 65 Bilder/s** (Wald 1,99 ms, Gelände 4,65, Wasser 2,19), **11 „68 Jahre“ 50 Bilder/s** (Wald 3,00 ms); der Wald ist nach 4,5 s bereit. **`ant schaustuecke` mit Wald:** 2 Beben 34 Bilder/s (Wald 12,3 ms: Auge 40 m über Cenotaph Island, mitten im Altbestand), 3 Sturz 60 (4,87), 4 Wand 40 (5,75), 5 Sporn 46 (2,52), 6 Front 40 (2,36), 7 unter Wasser 111 (1,36). **Befunde:** (1) Schaustück 2 fällt mit dem Wald von 74 auf 34 Bilder/s – zu viele nahe Bäume in voller Stufe; für 20 (Schliff) Reichweite und Stufen je Blick. (2) Der Jahres-Zeitraffer schob Tage und stand in Schaustück 11 im Januar um 6 Uhr in der Nacht – in Phase 15 berichtigt: er springt in ganzen Jahren am 10. Juli. (3) In Schaustück 11 ein heller Keil unten rechts, Ursache noch offen. `echtzeit_Phase13c.txt` ist der alte Lauf von 13:11 (ohne Warten auf die Grafikkarte), gültig bleibt `echtzeit_Phase14.txt`. | `Schaustueck_10_Phase14a.png`, `Schaustueck_11_Phase14a.png` | gebaut |
| 02.11.2026 | 15 | **Boote: `com.dan.vessel`** (Freigabe „Phase15, los!“; Entscheidungen: Starrkörper mit sechs Freiheiten (das Streifenmodell aus `buoyancy` trägt keine Welle, die das Boot ganz anhebt, und `vessel` darf `buoyancy` nicht kennen); Ankerkette aus Massenpunkten; Boote jetzt recherchieren; GL-Demo mit Welle).<br>• **Recherche** (MILLER60, Augenzeugen Ulrich und Swanson): alle drei Troller, rund 40 Fuß; Edrie in 5 Faden am Südufer mit 40 Faden Kette, Anker saß fest, die Kette riss, als das Boot mit der Front (50–75 Fuß) stieg; Badger in 4 Faden in Anchorage Cove, über La Chaussee Spit getragen; Sunmore am Eingang verloren. **Tabelle 2.3 berichtigt** (die Edrie lag nicht in Anchorage Cove).<br>• **Renderer, neu `com.dan.vessel`** (5.9). `VesselCheck` 21 Prüfungen, alles in Ordnung: Troller 39–41 Fuß, 21–25 t, Völligkeit 0,31; Schwimmlage auf 1 mm; GZ bei 2° 0,0358 gegen GM·sin φ 0,0349 m, GZ(30°) 0,24 m, GZ(60°) 0,17 m; **Rollperiode 3,15 s gegen die Handrechnung 3,15 s**, Tauchen 1,73 gegen 1,71 s; trocken in Ruhe; **Kette gegen die Kettenlinie** (Anker 66 / 69 m entfernt): 188 gegen 190 N, 330 gegen 332 N an der Klüse, auf dem Grund 55,4 gegen 54,7 m; Anker slippt über 3 kN; festgehakt reißt die Kette bei 42,4 kN; kopflastig gekentert nach 2,6 s; voll Wasser gesunken; 8,3 kn mit voller Maschine (Rumpfgeschwindigkeit 8,0), Ruder dreht 158° in 30 s. **Die Edrie in der Einzelwelle** (gerechnete Bucht, 5 Faden, Front 15 m [G]): Kette reißt nach 43,5 s, 2 s vor dem Kamm, bei 42,5 kN am Anker; das Boot steigt 24 m, wird 65 m zum Ufer getragen, der Rückstrom bringt es aufrecht und schwimmend zurück (4,5 t Wasser übergenommen); 95 ms Rechnung je Sekunde Welle. `VesselDemo` im Fenster und `--bild` (vier Augenblicke, Kurven Hub, Wasser, Trimm, Zug mit Bruchlast); hier über Mesa: `Vorschau_Phase15_Vessel_Mesa.png`. `check-packages`: 63 Packages unabhängig.<br>• **App:** Ziele `ant vesselcheck`, `ant vesselbild`, `ant vesseldemo`; `Pruefung` mit Abschnitt „Boote“; `lituya.phase` und `PHASE` 15. Jahres-Zeitraffer in ganzen Jahren (Befund 14a).<br>• **Befunde beim Bauen:** Der erste Rumpf war zu voll (47 t, Völligkeit 0,66) – ein Troller hat einen flachen Unterwasserkörper über einem tiefen schmalen Kiel; mit Kiel und V-Spant 21–25 t. Zu wenig Freibord (0,35 m) brach die Hebelkurve bei 20°; Seitenhöhe 2,45 m (Freibord 0,85 m). Eine Lücke zwischen Kiel und Rumpf im Raster ließ Wasser „übers Deck“ in den Kiel laufen. Ohne Wellenwiderstand lief der Kutter 12,5 kn. In der Bucht ergab Welle plus Reflexion plus Auflauf 45 m Hub; jetzt Reflexion ohne Aufschlag, 24 m. Mit 2 % Öffnungen im Deck lief das Boot im Rückstrom voll und sank; 0,4 % für einen gedeckten Troller [G]. | `Vorschau_Phase15_Vessel_Mesa.png` | **neu bauen** (`vessel` neu) |
| 02.11.2026 | 15 | **Abnahme** auf der Radeon: `ant pruefung` 172 Prüfungen, alles in Ordnung. `ant vesselcheck` 21 Prüfungen „Alles in Ordnung.“, 62 ms Rechnung je Sekunde Welle. `ant vesselbild`: `Vessel_Demo.png` wie erwartet (Kette reißt, das Boot steigt und kommt zurück). Das Werkbuch war schreibgeschützt: das alte liegt als `LITUYABAY1958_WERKBUCH_bis14a.md`, dieses ist neu. `echtzeit_Phase13c.txt` ist weiter der alte Lauf von 13:11, gültig bleibt `echtzeit_Phase14.txt`. **Befund 15a:** das Ruder drehte verkehrt herum (die 158° in 30 s waren 202° nach Backbord); in 15a berichtigt. | `Vessel_Demo.png` | gebaut |
| 02.11.2026 | 15a | **Boote in der Szene, `LB_NET_SEE`** (Freigabe „Phase15a, los!“; Entscheidungen: Bahnen vorrechnen; Gezeiten aus harmonischen Konstanten; vor der Front überall; Schaustück 8 an Bord, 9 nebenher).<br>• **Strömung in der Vorrechnung:** `EreignisDaten` schreibt zu jedem Bild `geschw_g<k>.bin` (u Ost, v Nord, Stufe 0,01 m/s, Deflate); `Ereignis` auf Grafikkarte (`BayGpu.velocities`, neu im Renderer) und Prozessor. **Die Läufe `kette` und `messwelle` müssen neu gerechnet werden**, ältere Läufe tragen die Boote nur auf und ab.<br>• **`scene.VorrechnungWasser`:** ein Lauf als `vessel.Water` (bilinear im feinsten Gitter, linear zwischen den Bildern, Grund 1958).<br>• **`tools.Boote1958`** (`ant boote1958`, `-Dlauf=kette`): Edrie in 9,1 m nahe dem Südufer östlich Harbor Point (E 347 545, N 6 500 506 [G]), 40 Faden, Anker fest; Badger in 7,3 m in Anchorage Cove, 30 Faden, Anker hält 15 kN [G]; Sunmore daneben, geht 60 s nach dem Stoß ankerauf und läuft mit Autopilot durch die Mitte des Tors 1 km hinaus [G]. Je Boot `bahn_<boot>.bin` (`scene.BootBahn`, 15 Bilder je s mit Kette) und `BOOTE.txt` (Hub, Kette, Höhe über Grund über Land, Ende). Testlauf über `messwelle` LOW ohne Strömung (Welle dort 2,6 m): 165 s Rechnung für 565 s.<br>• **Abspielen:** `gl.BooteGl` mit `vessel.gl.VesselGl` (Licht wie der Wald), neuer Durchgang „Boote“ zwischen Wald und Welle (9 Durchgänge), zu sehen in VOR und WELLE; HUD-Zeile mit Zustand der Boote und der Flucht. **Schaustück 8** „An Bord der Edrie“ (Auge vor dem Fenster des Steuerhauses, mit Stampfen und Rollen), **Schaustück 9** „Neben der Badger“ (25 m an Steuerbord, Blick nach Backbord), beide t = 200 s, Echtzeit; Mitfahrt auf der Bahn (`Renderer.mitfahrtBoot`).<br>• **`vessel`:** `Boat.pose`, `orientation`, `steuern` (Autopilot), `VesselGl.draw` aus Knoten. **Befund:** das Ruder drehte verkehrt herum (Seitenkraft am Heck nach Steuerbord); berichtigt, `VesselCheck` zählt die Drehung jetzt über 180° hinaus (+202° in 30 s).<br>• **Gezeiten und `LB_NET_SEE`:** siehe 7.3.<br>• **`Pruefung`:** Abschnitt „Boote in der Szene und LB_NET_SEE“: Gezeiten gegen NOAA (±25 min, ±0,25 m), Hochwasser 9.7.1958 bei 20:00 (±30 min), `BootBahn` hin und zurück, die Welle erreicht die Ankerplätze, drei Bahnen, Schaustücke 8 und 9, `LB_NET_SEE` gerichtet, LOD = Dijkstra; der Vergleich mit MILLER60 (Kette der Edrie gerissen, Badger über die Nehrung, Sunmore verloren) als Befund, nicht als Bedingung, weil die Welle der Vorrechnung am Eingang niedriger bleibt als 1958 (13a). 9 Durchgänge, 11 Schaustücke, 11 Skripte in `db/netz`. `lituya.phase` 15a. | `Vorschau_Phase15a_Flucht_LOW.png`, `Vorschau_Phase15a_Schaustueck_8_Mesa.png`, `Vorschau_Phase15a_Schaustueck_9_Mesa.png` | **neu bauen** (`vessel`, `wave.gl.BayGpu`) |
| 02.11.2026 | 15a | **Abnahme mit Rest** auf der Radeon: `ant pruefung` 178 Prüfungen, alles in Ordnung, 2 Hinweise (`scree` aus 6a; keine Bahnen in `daten/ereignis/boote/kette`). `ant vesselcheck` 21 Prüfungen, Ruder +202° in 30 s. `ant ereignis` beide Läufe MEDIUM mit Strömung (`geschw_g*.bin`): Kette Auflauf am Sporn **562 m** (MILLER60 524 m), Eingang Ankunft 447 s, 3,3 GB, 1 019 s Rechnung. Schritt `see`: 1 832 Knoten, 14 606 Kanten; Gezeiten gegen NOAA bis 21 min und 0,18 m; LOD = Dijkstra (8 792 Ankünfte gleich). **Flucht:** Edrie linear nur bei Abfahrt 268 s vor dem Stoß (Vorrechnung 251 s vor), Badger bis 216 s danach (Vorrechnung 254 s), Reserve bei Abfahrt 0 s 216 / 220 s. `Flucht.png` wie erwartet. **Befund:** `ant aufbereitung -Dschritte=see,herkunft,inhalt` brach nach `see` ab („Target herkunft does not exist“): cmd.exe trennt an Kommas, aus der Liste wurden Ant-Ziele. Schon in Phase 3 gefunden, in der Anleitung zu 15a aber ohne Anführungszeichen geschrieben. Berichtigt: `Aufbereitung` trennt jetzt auch an `+` (`-Dschritte=see+herkunft+inhalt` geht ohne Anführungszeichen), Anleitung, `build.xml` und Kopfzeile der Aufbereitung (stand noch auf „Phase 8“). **Offen aus 15a:** `ant aufbereitung -Dschritte=herkunft+inhalt`, `ant boote1958` (Lauf `kette`, `boote1958_Phase15a_kette.txt`), Schaustücke 8 und 9 auf der Radeon mit Booten, wahlweise `ant netzdb` | `*_Phase15a.txt`, `Flucht.png` | gebaut |
| 02.11.2026 | 16 | **`LB_NET_WASSER`** (Freigabe „Phase16, los!“; Entscheidungen: geteilt in 16 (Wasser) und 16a (Weg); ITS_LIVE nachladen; Weg 10 m + 50 m wie geplant (16a); Klassen aus WorldCover, Wald und RGI (16a)).<br>• **Aufbereitung:** Schritt `wasser` (`tools.Wasser`, `tools.Rgi`, `tools.Umriss`), siehe 7.4; Leser `scene.WasserNetz`. 80 s, 2,5 GB.<br>• **Katalog:** `itslive_geschwindigkeit` lädt jetzt nur den Ausschnitt der Szene mit 5 km Rand (Stufe standard; die Abbildung wird aus dem Kopf der COGs gelesen). Hier ist S3 gesperrt, daher nicht geladen.<br>• **Oracle:** `09_wasser.sql`, `10_wasser_nach_dem_laden.sql`, `99` räumt `LB_EINZUG`, `LB_SEE`, `LB_SENKE` mit ab; `tools.WasserDb`, `netz.WasserLod`; in `NetzDb` eingehängt.<br>• **App:** HUD-Zeile „Einzugsgebiete (LB_NET_WASSER)“, `PHASE` und `lituya.phase` 16; `ant wasserkarte`.<br>• **Hier geprüft** (Gelände der Szene von der Radeon auf 10 m gemittelt und auf 0,1 m gerundet herübergeholt, RGI 7 als Ausschnitt): Abschnitt „Gewässer und Gletscher“ der `Pruefung` alles in Ordnung (ein Hinweis: ohne ITS_LIVE): Baum stromab und kreisfrei, Fläche je Ziel über Ebene 2 = Raster bis 0,05 km², Ebenen passen, 5 Komponenten je Ebene, **LOD traceIn = Datei** (17 853 Knoten gleich, längste Fließzeit gleich), **route = Datei** (17 858 Fließzeiten), Umrisse bis 1,5 % von der Fläche aus 10 m. ITS_LIVE-Weg mit einer künstlichen Datei in EPSG:3413 geprüft: Soll (−100, −50) m/a in UTM, gelesen (−100,0; −50,1). Oracle nicht (hier keine Datenbank).<br>• **Befunde:** das 120-m-Mittel des rohen Geländes hat auf dem Eis Mulden, die Eislinien endeten bei 1 474 m; jetzt über das aufgefüllte Gelände, in Mulden dem Wasserweg nach, Kreise aufgelöst. Eis über Wasserscheiden (siehe 7.4). Die geraden parallelen Linien in flachen Stücken der Vorschau kommen von den gerundeten Testhöhen. | `Vorschau_Phase16_Wasser_Test.png` | unverändert |
| 03.11.2026 | 16 | **Abnahme mit Befunden** auf der Radeon (Dateien `*_Phase16a.txt`, `netzdb_Phas16a.txt`):<br>• **Rest aus 15a:** `ant boote1958` in 204 s. Gegen MILLER60 als Befund: Kette der Edrie **hält** (73 m, Spitze 6 kN; belegt: gerissen), Badger **nicht** über die Nehrung getragen, Sunmore schwimmt 1 km draußen (belegt: verloren); passt dazu, dass die Welle der Vorrechnung am Eingang niedriger bleibt als 1958 (13a).<br>• **ITS_LIVE** geladen (Ausschnitt, EPSG:3413, je Datei 1,6 MB). Schritt `wasser` in 63 s: **Gilbert Inlet 381,0 km² (66 % Eis), Crillon Inlet 110,2 km² (60 %), Bucht 50,0 km² (12 %), Lituya Bay zusammen 541,2 km² (60 % Eis)**; kein Gebiet am Rand; 18 466 Knoten, 18 456 Kanten; 104 525 Eiszellen alle mit Geschwindigkeit, 898 km Fließlinien; traceOut Lituya-Gletscher: 18,2 km Eis in 428 Jahren bis Gilbert Inlet. (Gegen den Testlauf 275 km²: dort waren die Höhen gerundet.)<br>• **`ant pruefung`** 192 Prüfungen, Abschnitt Wasser alles in Ordnung (LOD = Datei, route = Datei, Ebenen passen); **ein Fehler**: die alte Prüfung erwartete 11 Skripte in `db/netz`, jetzt 13 – berichtigt.<br>• **`ant netzdb`:** `VALIDATE_NETWORK(LB_NET_WASSER)` = TRUE, geladen in 17 s, Partitionen beider Ebenen und BLOBs ja (Probe 31), CONNECT BY auf Ebene 2 = Dateien, traceIn in Oracle = Datei (18 201 Knoten, 1,3 s). Zwei Befunde: (1) **LOD zählt 10 Komponenten statt 5** – in NDM gehören die Kanten höherer Ebene auch zur Ebene 1, `findConnectedComponents(1)` sieht beide Ebenen (5 + 5); die Zweitrechnung erwartet das jetzt. (2) **76 Umrisse in `LB_EINZUG` ungültig:** wo sich zwei Zellen eines Gebiets im 50-m-Raster nur an einer Ecke berühren, lief der Ring zweimal durch den Punkt, und Löcher berührten den Außenring. Berichtigt in `tools.Umriss`: solche Eck-Kontakte werden vor dem Umfahren über eine Nachbarzelle geschlossen, doppelte Punkte trennen den Ring in Schleifen; mit Shapely geprüft: alle 252 Umrisse des Testlaufs gültig, außen gegen, Löcher mit dem Uhrzeigersinn. Ungültige Umrisse meldet `netzdb` künftig mit der Oracle-Meldung. | `Wasser.png` | unverändert |
| 03.11.2026 | 16 | **Abnahme:** `ant pruefung` 192 Prüfungen, alles in Ordnung. `ant netzdb` ok: `LB_NET_WASSER` geladen in 16,7 s, VALIDATE TRUE, LOD = Datei (10 Komponenten = 5 + 5), **0 Umrisse ungültig**, Probe 31 ja. | `Wasser.png`, `*_Phase16.txt` | unverändert |
| 03.11.2026 | 16a | **Abnahme** auf der Radeon: `ant pruefung` 200 Prüfungen, alles in Ordnung. `ant netzdb`: `LB_NET_WEG` geladen, Knoten in 9 s, 26,4 Mio. Kanten in 71 s, **Lage, Indizes, Kacheln und BLOBs 5 708 s** (zusammen 96,5 min); VALIDATE TRUE (4 s); LOD in Oracle = Datei (1 h vom Strand 41 024 Knoten gleich, Washburn 29 724 / 42 884 s, k = 3), 301 s; Probe 32 ja (948 + 247 Partitionen). | `Weg.png`, `*_Phase16a.txt` | unverändert |
| 03.11.2026 | 16b | **Flucht an Land, `ant wegdb`** (Freigabe „Phase16b, los!“; 16b war nicht geplant, gewählt: alle vier Themen, geteilt in 16b Daten und 16c Szene; gerettet über dem Auflauf der Vorrechnung + 10 m; mit dem Boot an den Strand; eigenes Ziel `ant wegdb`).<br>• **Aufbereitung:** Schritt `landflucht` (`tools.Landflucht`) → `daten/netz/weg/landflucht.tsv`, `landwege.tsv`, `sicher_kern_10m.u8`, `LANDFLUCHT.txt`; siehe 7.5.<br>• **Oracle:** `ant wegdb` (`NetzDb weg`, `WegDb.alles`): `97_weg_abbau.sql` (neu), `11` mit `LB_LANDFLUCHT`, Laden, `12` neu geschrieben: jede Anweisung einzeln, `NetzDb.skript` meldet jede über 5 s mit Zeit; Kacheln 1 km / 5 km (rund 10 000 Knoten, halb so viele Partitionen); Linkindizes parallel; Index auf `LB_NET_WEG_PART$` und Statistiken vor `GENERATE_PARTITION_BLOBS`; Sicht `LB_V_WEG_LANDFLUCHT`; Landwege in `LB_WEG_ROUTE`. `99_abbau` lässt `LB_NET_WEG` stehen, `netzdb` trägt seinen Stand nach (`WegDb.standNachtragen`) und braucht wieder nur 3 GB.<br>• **App:** HUD-Zeile mit der Flucht an Land; `Weg.png` mit den Landwegen; `lituya.phase` 16b.<br>• **Hier geprüft** (deine Daten, Lauf `kette`): `Pruefung` „Wege über Gelände“ alles in Ordnung (Landflucht 6 von 6 nachgerechnet, LOD = eigen), `db/netz` 16 Skripte. Oracle nicht.<br>• **Befund beim Bauen:** zuerst zählte für den Auflauf nur überflutetes Land; an der Außenküste war dann jede trockene Zelle sicher (Badger „sicher“ auf 4 m). Jetzt alle nassen Zellen, Meer eingeschlossen. | `Vorschau_Phase16b_Landflucht_Test.png` | unverändert |
| 03.11.2026 | 16a | **`LB_NET_WEG` und Geländeklassen** (Freigabe „Phase16a, los!“; Entscheidungen: gerichtet je Richtung eine Kante; Spalten aus ITS_LIVE; Bäche nach Mittelwasser; Washburns Route 1934), siehe 7.5.<br>• **Aufbereitung:** Schritte `klassen` (`tools.Klassen`, 31 s) und `weg` (`tools.Weg`, 4 s); Leser `scene.WegNetz`.<br>• **Oracle:** `11_weg.sql`, `12_weg_nach_dem_laden.sql`, `99` räumt `LB_WEG_UMSTIEG`, `LB_WEG_ROUTE` ab; `tools.WegDb`, `netz.WegLod`; `netzdb` mit 12 GB.<br>• **App:** HUD-Zeile „Wege (LB_NET_WEG)“; `ant wegkarte`; `lituya.phase` 16a.<br>• **Hier geprüft** mit deinen Daten (Kern von 0,5 m auf 10 m auf deinem Rechner gemittelt, WorldCover, Kronenhöhe, ITS_LIVE, die Netze Wasser und See): Abschnitt „Wege über Gelände“ der `Pruefung` alles in Ordnung (Tobler, Mittelwasser, Klassen, Gegenkanten, 1 h nachgerechnet, Washburn, LOD = Dijkstra); `db/netz` 15 Skripte. Oracle nicht.<br>• **Befunde:** (1) die effektive Dehnungsrate schloss den South Crillon Glacier und Crillon Lake vom Gipfel ab – Spalten reißen unter Zug auf, jetzt die größte Hauptdehnungsrate; (2) Landeplatz der Edrie und GNIS-Punkt des Gletschers lagen in abgeschnittenen Taschen – jetzt im größten zusammenhängenden Teil; (3) 26,4 statt 39 Mio. Kanten, weil Wasser, Spalten, Bäche und Steilstufen wegfallen. | `Vorschau_Phase16a_Weg_Test.png` | unverändert |
| | 0b | parallel: Recherche vervollständigen (Miller S. 51–86, Zeugen, Boote, Tide, Zeitzone), Texturquellen und Lizenzen, `docs/QUELLEN.md` | | |

**Nächster Schritt:** Phase 16b auf der Radeon (Renderer unverändert):

1. `ant aufbereitung -Dschritte=landflucht+inhalt`, dann `ant wegkarte`.
2. `ant pruefung`.
3. `ant wegdb` (lädt `LB_NET_WEG` neu mit den neuen Kacheln; das Protokoll zeigt jede Anweisung über 5 s mit ihrer Zeit, so sehen wir, was die 95 min kostete). Wer mag danach `ant netzdb` (ohne `LB_NET_WEG`, kurz; der Stand von WEG wird nachgetragen).

Abnahme: `pruefung_Phase16b.txt`, `aufbereitung_Phase16b.txt`, `wegdb_Phase16b.txt`, `Weg.png`. Danach 16c (Netzblick für Wasser und Weg, Bäche in der Szene), dann 17 (`LB_NET_SICHT`, Regiebücher, Kino); offen bleiben 10b, 6a (Unterwuchs, Gletscher) und 5a (App), `rueckbau1958`, `spurlinie`, die Tide nach COOPS, die Zeugen (0b), aus 14a die Bildrate von Schaustück 2 mit Wald.
