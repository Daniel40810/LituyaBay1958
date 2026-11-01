# LituyaBay1958 — Planungsprompt für das Werkbuch

> **Zweck dieser Datei:** Aus ihr erstellt Claude das **„LituyaBay1958 Werkbuch“**.
> Noch **kein Code, keine Skripte** (außer dem schon vorhandenen Werkzeug `tools/kartenmaterial`). Zuerst wird geplant: Ausgangspunkt klären, Themen und Struktur festlegen, offene Fragen stellen. Programmiert wird erst, wenn Phase 1 freigegeben ist.
>
> Stand: 01.11.2026 · NetBeans-Projekt `C:\Users\Dan40810\Documents\NetBeansProjects\LituyaBay1958` · Rohdaten `C:\Daten\LituyaBay1958` · Repo `Daniel40810/LituyaBay1958`

---

## 1. Auftrag

**Lituya Bay, Alaska, 9. Juli 1958: das Projekt der Riesenwelle und des Netzes.** Das Werkbuch beschreibt eine animierte 3D-Echtzeitszene der Bucht am Fuß der Fairweather-Kette. Ein Beben an der Fairweather-Störung löst einen Felssturz in den Gilbert Inlet aus. Die Welle läuft am Gegenhang bis 524 m hinauf, rasiert den Wald von den Ufern und läuft durch die Bucht bis zum Eingang. Drei Fischerboote liegen in dieser Nacht vor Anker. Die Szene zeigt die Bucht **vor**, **während** und **nach** der Welle und **heute**.

Auf Sylt („Brandung“) und Timmendorf („Segel“) folgt das **„Projekt der Riesenwelle“**. Es hat zwei gleichrangige Säulen:

1. **Die Welle im Bild:** Felssturz, Impulswelle, Auflauf am steilen Hang, Wald und Spurlinie, mit den Packages aus `Renderer.jar`.
2. **Das Oracle Network Data Model (NDM), richtig ausgereizt.** In Timmendorf war NDM ein Wegespeicher: vier Netze, Partitionen, LOD-Prüfwege gegen `route`. In Lituya Bay gibt es keine Straßen. Die Netze sind die der Natur und des Ereignisses: die Laufzeit der Welle, das Ufer als Messstrecke, Gewässer und Gletscher, Wege über Gelände, Sichtlinien, die Herkunft der Daten. Jede Netzart und jede Analysefunktion von NDM, die einen echten Zweck in der Szene hat, kommt zum Einsatz und wird gegen eine unabhängige Rechnung geprüft.

**Leitsatz für NDM:** Kein Netz um seiner selbst willen. Jedes Netz beantwortet eine Frage, die man in der Szene sieht oder in einem Regiebuch erzählt. Jede Antwort wird gegen eine zweite Rechnung (`route`, `wave`) oder gegen einen Beleg (Augenzeugen, Miller 1960, NCEI) gehalten.

**Detailtreue und Physik sind ausdrücklich gewünscht**, wie in Sylt und Timmendorf. Eigene Ideen sind erwünscht. Wo nötig, entstehen neue eigenständige Engines.

---

## 2. Ausgangslage (geprüft am 01.11.2026)

### 2.1 Das Projekt LituyaBay1958

| Bereich | Stand |
|---|---|
| JDK | `javac.source/target=21` |
| `lib/` (Klassenpfad) | `Renderer.jar`, `FStyle.jar`, JOGL + gluegen (win-amd64), `ojdbc11.jar`, `sdoapi.jar`, `sdogr.jar`, `sdoutl.jar`. **Es fehlt `sdonm.jar`** (NDM-Java-API, `oracle.spatial.network.lod`), in Timmendorf vorhanden. |
| `src/` | leer, `main.class` nicht gesetzt |
| Datenbank | `db/` ist angelegt, aber nicht im Repo (`db/db.properties`, `db/info/` in `.gitignore`). Schema noch festzulegen (F28). |
| `docs/` | in `.gitignore` bis auf `docs/claude/` (diese Datei) |
| Bilder | `images/1958_Lituya_Bay1.jpg` (Luftbild der Bucht nach der Welle), Übersichten `images/Gebiete_*.jpg`, `images/Kern_BlueTopo.png` |
| Git | Repo mit Zweig `main`; Phase 0 (Kartenmaterial) liegt auf `claude/practical-edison-5c0iy8` |

### 2.2 Phase 0 ist erledigt: Gebiete und Kartenmaterial

Siehe `Kartenmaterial.md` (Katalog von 47 Quellen) und `download/INVENTAR.md` (Planung mit Größen).

**Gebiete** in UTM Zone 8 Nord (EPSG:26908, NAD83), volle Kilometer (`tools/kartenmaterial/geo.py`, `geodaten/gebiete.geojson`):

| Gebiet | Ost (m) | Nord (m) | Größe | Inhalt |
|---|---|---|---|---|
| Kern | 340 000 – 364 000 | 6 494 000 – 6 512 000 | 24 × 18 km | Bucht, Gilbert und Crillon Inlet, Cenotaph Island, Eingang, Hänge über der Spurlinie, Zunge des Lituya-Gletschers |
| Szene | 322 000 – 388 000 | 6 480 000 – 6 540 000 | 66 × 60 km | Fairweather-Kette (Mt Fairweather, Mt Crillon, Mt La Perouse), Küstenebene, Cape Fairweather |
| Fern | 220 000 – 480 000 | 6 385 000 – 6 625 000 | 260 × 240 km | Horizont von Yakutat bis Cross Sound |

**Was die Daten für die Netze hergeben** (alle frei, Lizenzen im Katalog):

| Netz | Daten (Katalog-ID) | Stand |
|---|---|---|
| Welle, Seenetz | Grund vor der Welle: NOS-Vermessungen H06581–83 (1940) und H04608/H04648 (1926); nach der Welle: H08492 (21.7.–27.8.1959); heute: BlueTopo (4–16 m, NAVD88, Quellentabelle je Zelle) mit H14228 (Rainier, Juli 2025) und USACE-Topobathy 2019 (1 m) | BlueTopo im Kern geladen und gelesen: Becken bis −135 m, Bucht vollständig (`images/Kern_BlueTopo.png`). Vermessungen: Adressen nach NCEI-Muster, ungeprüft |
| Ufer | Lidar Glacier Bay 2019/20 (0,5–1 m, 312 Kacheln im Kern), USACE 2019, Karten 1951 (Luftbilder 1948) und C-5/C-6 (Luftbilder 1954/1948), Miller 1960 mit Karte 1:50 000, NCEI-Auflaufhöhen | Lidar und Karten geprüft (S3), NCEI ungeprüft |
| Wald | IfSAR-DSM 2012 (5 m, mit Bewuchs), Lidar, ArcticDEM-Streifen (47 datierte 2-m-DSM über dem Kern, November 2013 bis Januar 2022), Sentinel-2, WorldCover | geprüft |
| Gewässer, Gletscher | Lidar, IfSAR-DTM, 3DEP, ITS_LIVE-Geschwindigkeit, RGI 7, GLIMS (mehrere Zeitstände) | S3 geprüft, RGI/GLIMS ungeprüft |
| Wege | Gelände wie oben, WorldCover, GNIS-Namen (94 in der Szene, `geodaten/namen_szene.geojson`), OSM | geprüft bis auf OSM |
| Ereignis | ComCat (Beben iscgem884702), NCEI-Tsunami-Datenbank, Gezeiten 9452928, ERA5 Juli 1958, Fotos Miller | ungeprüft bis auf ERA5 |

### 2.3 Rohdaten unter `C:\Daten\LituyaBay1958`

- Geladen mit `python tools/kartenmaterial/kartenmaterial.py laden --ziel C:\Daten\LituyaBay1958` (Standard ≈ 11 GB, mit `--gross` ≈ 50 GB). Danach `INVENTAR.md` mit „fehlgeschlagen“ prüfen. Die ungeprüften Adressen werden beim ersten Lauf auf dem eigenen Rechner berichtigt (in `quellen_web.py`).
- **Regel wie in Sylt (Abschnitt 2.4 dort), verbindlich:** Rohdaten liest nur die Aufbereitung (`com.dan.lituya.tools`) einmal und schreibt alles, was die App braucht, nach `daten/` (in `.gitignore`, mit `daten/INHALT.txt` und Prüfsummen). **Die App liest nie aus `C:\Daten`.** Die Netze haben ein Datei-Abbild in `daten/netz/` wie in Timmendorf; Oracle ist Speicher und Analyse, die App startet auch ohne Datenbank.

### 2.4 Der Renderer: was schon da ist

Im Renderer (52 Packages): `beach beachchair beacon buoyancy cliff cloth cloud dune fall figure firework flock fog forest fracture ground hearth herd ice jelly kino lamp lightning longship mudflat muster ocean rail rain reed rime river road route sail seal season sky smoke soundscape spray store surf tide timber tree wake wave weather weathering wetglass wind`.

Vor dem Werkbuch die READMEs dieser Packages lesen:

| Package | Stand laut README | Rolle in Lituya Bay |
|---|---|---|
| `wave` | Löser (Rungholt Phase 8, `hold` seit 12): Flachwasser nach Arakawa C, zwei Gitter (8 m / 2 m), Auflaufen mit `runupAt`, Pegel, Einschlag als Volumen; „wiederverwendbar für einen Felssturz in einen See“ | **Die Welle.** Grenzen laut README: kein Impuls (nur Volumen), keine Dispersion, kein Brechen, grobes Gitter 8 km. Lituya Bay braucht 24 km, Impuls und Auflauf an 30°+-Hängen → Erweiterung (5.1) |
| `fracture` | Engine (Glacier Phase 6), Lockergestein seit Sylt 11 | Felssturz an der Wand des Gilbert Inlet; Gletscherfront |
| `ice` | Gletscher als Körper (Glacier Phase 5) | Lituya- und Nord-Crillon-Gletscher, Front 1958 |
| `cliff` | Fels als Material (Geiranger), `brodtenerUfer()` seit Timmendorf | Wände der Bucht, Rutschnarbe |
| `forest`, `tree` | Bäume im Wind, Wälder instanziert | Sitka-Fichte und Hemlock bis zur Spurlinie; **Wellenwurf** neu (5.3) |
| `route` | Timmendorf Phase 12: Graph, Dijkstra, A*, Umkreis, `Crowd`, `Line` | Zweite Rechnung zu allen NDM-Netzen; Gitternetze und Laufzeitbaum neu (5.2) |
| `ocean`, `surf`, `spray`, `tide` | aus Sylt und Timmendorf | offene See, Brandung am Eingang, Gischt, Pegel |
| `fall`, `river` | Geiranger | Bäche und Wasserfälle von den Gletschern |
| `sky`, `cloud`, `fog`, `rain`, `weather`, `smoke`, `flock`, `seal`, `soundscape`, `kino`, `store`, `season` | aus den Vorgängern | Licht der Juliabende in 58,6° N, Staubwolke des Felssturzes, Möwen, Seehunde, Klang, Kino |

### 2.5 NDM: was Timmendorf gelernt hat (übernehmen)

- **Rechte direkt, nicht über Rollen:** `SDO_NET` legt Tabellen aus PL/SQL an, dort gelten Rollen nicht (`db/netz/00_sys.sql` in Timmendorf). `DATA_PUMP_DIR` mit `READ, WRITE` für das Protokoll von `SPATIAL_PARTITION`.
- **Raum-Metadaten auch für `<netz>_PATH$`**, sonst meldet `VALIDATE_NETWORK` „path geom metadata missing“ (Befund Timmendorf Phase 4).
- **`sdonm.jar` von 2021 und JDBC 23:** Die LOD-API bindet Platzhalter mit Namen und Nummern zugleich, der Treiber 23 weist das ab (ORA-17090). Abhilfe in Timmendorf: eigener Klassenlader mit `ojdbc11.jar` aus `ORACLE_HOME\jdbc\lib` in `lib/lod/` (`tools/NetzDb.java`).
- **Skripte** in `db/netz/*.sql`, jede Anweisung mit einer Zeile `/`. SQL Developer, SQL*Plus und das Ant-Ziel lesen dasselbe Format.
- **Prüfwege** (`pruefwege.tsv`) laufen in Oracle (LOD) und in `route`, das Ergebnis muss gleich sein (`ant netzvergleich`). Eine Prüfsumme in einer Stand-Tabelle sagt der App, ob Oracle und Datei-Abbild zusammenpassen.
- Geometrie mit Höhe (3001/3002), Toleranz 5 mm, Index `SPATIAL_INDEX_V2`, Partitionen mit 5 000 Knoten.

---

## 3. Recherche vor dem Werkbuch

Ergebnisse nach `docs/QUELLEN.md`, je **belegt / gemessen / gestaltet [G]** wie in Sylt. Alle Zahlen hier sind zu prüfen, nicht zu übernehmen.

- **Ereignis:** Bebenzeit (ComCat iscgem884702: 10.07.1958 06:15:58 UTC), Magnitude, Ortszeit und Zeitzone in Alaska 1958, Sonnenstand und Dämmerung um diese Zeit in 58,6° N. Tidenstand aus der Vorausberechnung für 9452928.
- **Felssturz:** Volumen (Miller 1960: rund 40 Mio. yd³ ≈ 30 Mio. m³), Fallhöhe, Abrissfläche, Gestein, Dauer des Eintauchens. Lage der Abrissnarbe am Lidar 2019 nachmessen.
- **Welle:** Auflauf 524 m (1 720 ft) am Sporn gegenüber, Höhe der Spurlinie entlang der Ufer (Miller, Karte 1:50 000), Fläche des zerstörten Walds, Geschwindigkeit der Front (Augenzeugen, Fritz et al. 2001/2009), Front des Lituya-Gletschers vor und nach dem Ereignis.
- **Boote:** Edrie (Howard Ulrich mit Sohn, Anchorage Cove), Badger (Swanson, über La Chaussee Spit getragen), Sunmore (Wagner, im Eingang gesunken). Ankerplätze, Zeiten und Beobachtungen aus Miller 1960 und den Berichten der Überlebenden; was nicht belegt ist, wird [G].
- **Frühere Wellen** in der Bucht (Miller: 1853/54, 1874, 1899, 1936) mit ihren Spurlinien.
- **Bucht 1958:** Grund aus H06581–83 (1940) und H08492 (1959), Vergleich mit Franco et al. 2020 (Rekonstruktion der Bathymetrie vor 1958). Schwelle im Eingang, Gezeitenstrom im Eingang (La Pérouse verlor dort 1786 21 Mann).
- **Wald:** Arten (Sitka-Fichte, Westliche Hemlocktanne, Erle), Höhen am DSM 2012, wie die Welle die Bäume brach und entrindete (Fotos Miller). Nachwuchs seit 1958 am DSM und an den ArcticDEM-Streifen.
- **Wege:** Washburns Crillon-Expedition 1933/34 (Ausgangspunkt, Route), Tlingit-Namen und -Wege (NPS „Tlingit Place Names of Huna Káawu“).
- **NDM-Funktionen gegen die installierte Version:** `select banner from v$version`; vorhanden oder nicht: LRS-Netze, Feature-Layer (`SDO_NET.ADD_FEATURE_LAYER`), `FIND_CONNECTED_COMPONENTS`, LOD `NetworkAnalyst` (`withinCost`, `kShortestPaths`, `tsp`, `mcst`, `traceIn/traceOut`, `nearestNeighbors`), `LinkCostCalculator`/`NodeCostCalculator`, Network Buffer, Property Graph mit SQL/PGQ (nur 23ai). Die Liste im Werkbuch heißt „NDM-Funktionsprobe“ und wird in Phase 3 mit je einer Zeile SQL oder Java belegt.

---

## 4. Kernprinzip: der Paketvertrag (unverändert aus Sylt Abschnitt 4)

Alle zwölf Punkte gelten. Dazu kommt ein dreizehnter:

13. **Kein Package kennt Oracle.** NDM, JDBC und `sdonm.jar` gibt es nur in der App (`com.dan.lituya.netz`) und in den Werkzeugen (`com.dan.lituya.tools`). Was ein Package rechnen soll, das auch NDM rechnet (Laufzeitbaum, Umkreis, Messung entlang einer Linie), steht in `route` als unabhängige Zweitrechnung. So bleibt jedes NDM-Ergebnis prüfbar.

---

## 5. Package-Katalog (Vorschlag, im Werkbuch zu bestätigen)

### 5.1 Erweiterung `com.dan.wave` — die Impulswelle *(Prunkstück 1)*

- **Großes Gebiet:** grobes Gitter über den ganzen Kern (24 × 18 km, etwa 16 m), feines Gitter um den Gilbert Inlet (2 m), dazwischen ein mittleres (F7). Fenster, die mit der Welle wachsen, bleiben.
- **Impuls statt Volumen:** Der Felssturz bringt Masse **und** Geschwindigkeit ein (Quelle als bewegter Körper, Verdrängung nach der Eintauchgeschwindigkeit), Eingang von `fracture` über die App.
- **Auflauf an steilen Hängen:** Nass-trocken-Rand, der 500 m Hang hinauf nicht einbricht; höchster Stand je Zelle (`runupAt`) liefert die Spurlinie der Rechnung.
- **Nichtlinear:** Die Welle ist so hoch wie die Bucht tief. Prüfen, ob Flachwasser mit Stoßerfassung reicht oder ob eine Dispersionskorrektur nötig ist (F8). Das Brechen der Front als Weißwasser über `spray`.
- **Prüfung:** Laborversuche zu Lituya Bay (Fritz, Hager & Minor 2001) als Grenzfall, Ankunftszeiten gegen das Wellennetz (6.1), Spurlinie gegen Miller.
- Bestehende Nutzer (Rungholt, Edersee-Demo) bleiben unberührt (Paketvertrag Punkt 12).

### 5.2 Erweiterung `com.dan.route` — Gitternetze und Messstrecken

- **Gitternetz** aus einem Raster (4, 8 oder 16 Nachbarn), Kosten aus einer Funktion der Zelle und der Richtung.
- **Laufzeitbaum** von einer oder mehreren Quellen (mehrere Startknoten mit Startzeit), **Isochronen** als Linien.
- **Messstrecke:** Lage auf einer Linie als Meter (für das Ufer-LRS) und Abschnitte mit Werten.
- **Zweck:** die unabhängige Zweitrechnung zu NDM (Paketvertrag Punkt 13). `RouteCheck` bekommt Handrechnungen: Laufzeit über konstante Tiefe = L/√(g·h), Fehler des 16er-Gitters gegen den Kreis unter 2 %.

### 5.3 Erweiterung `com.dan.forest` / `com.dan.tree` — Wellenwurf

- Bäume, die die Welle bricht, entwurzelt, entrindet und mitnimmt; Stämme als Treibgut (über `buoyancy`).
- Zustand je Baum: stehend, geknickt, entrindet, fort. Die Grenze kommt aus dem Ufer-LRS (6.2) und aus `wave.runupAt`.
- Nachwuchs: Wald unter der Spurlinie in den Jahren danach jünger und niedriger (Zeitraffer bis heute, Abgleich am DSM 2012 und den ArcticDEM-Streifen).

### 5.4 Erweiterung `com.dan.fracture` — Felssturz

- Ablösen einer Felsplatte an der Wand des Gilbert Inlet, Fall, Zerlegen zur Gesteinslawine, Eintauchen.
- Liefert Masse, Geschwindigkeit und Ort an `wave`, Staub an `smoke`, Klang an `soundscape`.

### 5.5 Wiederverwendung ohne Änderung

`ice` (Gletscherfront, Abbruch durch die Welle über `fracture`), `cliff`, `ocean`, `surf` (Eingang), `spray`, `tide`, `fall`, `river`, `sky`, `cloud`, `fog`, `rain`, `weather`, `smoke`, `flock`, `seal`, `soundscape`, `kino`, `store`, `season`, `buoyancy` (Boote, Treibholz).

---

## 6. Die Netze (Prunkstück 2: NDM ausgereizt)

Präfix der Netze `LB_NET_` (F28). Alle Netze in UTM 8N mit Höhe, außer den logischen. Jedes Netz hat: Herkunft in `daten/netz/<netz>/`, Ladeskript, Prüfwege, Zweitrechnung, mindestens eine Frage, die es für die Szene beantwortet.

### 6.1 `LB_NET_WELLE` — Laufzeit der Welle *(Herzstück)*

- **Knoten:** Gitter über dem Wasser der Bucht (Vorschlag 20 m, F9), etwa 75 000 Knoten im Kern; **Kanten:** 16 Nachbarn, ungerichtet.
- **Kosten:** Laufzeit = Länge / √(g·h). Nach Fermat ist der kürzeste Weg die Ankunft der Front, der kürzeste-Wege-Baum ist die Wellenfront.
- **Mehrere Kosten** über `LinkCostCalculator` (F10): `T_1940` (Grund vor der Welle), `T_1959` (nach der Welle), `T_HEUTE` (BlueTopo), `T_NICHTLINEAR` mit c = √(g·(h + a)), Amplitude a aus `wave`.
- **Quelle:** ein virtueller Quellknoten, mit Kosten 0 an alle Knoten der Einschlaglinie im Gilbert Inlet angebunden (F11).
- **Analysen:** `withinCost` und kürzeste-Wege-Baum → Ankunftszeit je Knoten; Isochronen alle 10 s (Network Buffer oder eigene Konturen, F12); `nearestNeighbors` von jedem Boot zur Front.
- **Groß genug für LOD:** Partitionen und Partition-BLOBs werden gebraucht, nicht nur angelegt.
- **Prüfung, dreifach:** NDM gegen `route` (gleiche Kosten, gleiches Ergebnis), NDM gegen `wave` (Ankunftszeit an Pegeln), beide gegen die Augenzeugen (Zeit bis Anchorage Cove, bis zum Eingang).
- **Für die Szene:** der „Netzblick“ (Isochronen über dem Wasser, Wellenstrahlen als kürzeste Wege), die Uhr im Kino, die Frage „Wie viel Zeit hatte jedes Boot?“.

### 6.2 `LB_NET_UFER` — das Ufer als Messstrecke (LRS)

- **LRS-Netz** (`SDO_NET.CREATE_LRS_NETWORK`): die Uferlinie 1958 (aus Karte und Vermessung 1940) und heute (Lidar), Meter ab La Chaussee Spit, im Uhrzeigersinn um die Bucht und um Cenotaph Island.
- **Ereignisse auf der Strecke** (dynamische Segmentierung, `SDO_LRS`): Auflaufhöhen (NCEI, Miller), Höhe der Spurlinie, Breite des Schadensbands, Ablagerungen, Fotostandorte von Miller.
- **Feature-Layer** (wenn die Version es kann): Auflaufpunkte und Fotostandorte als Features auf den Kanten.
- **Für die Szene:** Je Ufermeter weiß der Wald, bis wohin er fehlt (5.3); Vergleich Spurlinie belegt gegen `wave.runupAt`.

### 6.3 `LB_NET_SEE` — Fahrwasser 1958 mit Gezeiten

- Wie `TD_NET_SEE`, aber **zeitabhängig:** Wassertiefe aus der Vermessung 1940 plus Pegel zur Uhrzeit (Vorausberechnung 9452928), Strom im Eingang.
- **Einschränkung** (`LODNetworkConstraint`): keine Kante, deren Tiefe zur Zeit t unter dem Tiefgang liegt.
- **Knoten:** Ankerplätze der drei Boote, Anchorage Cove, Eingang, offenes Meer.
- **Analysen:** Fluchtwege von jedem Ankerplatz, gegen die Ankunft der Welle aus 6.1 („Wer hätte entkommen können?“), *k* kürzeste Wege als Varianten. La Pérouse 1786 als zweites Szenario.

### 6.4 `LB_NET_WASSER` — Gewässer und Gletscher (hierarchisch)

- **Zwei Ebenen:** unten die Bäche aus dem Abfluss im Gelände (Lidar/IfSAR), oben die Fließlinien der Gletscher (ITS_LIVE) und die Einzugsgebiete als Elternknoten. Gerichtet stromab.
- **Analysen:** erreichbare Knoten stromauf (`traceIn`) → Einzugsgebiete von Gilbert Inlet, Crillon Inlet, Fish Lake, Mudslide Creek; Fließzeit; Zusammenhangskomponenten.
- **Für die Szene:** Wasserführung der Bäche und Fälle (`fall`, `river`) nach Schmelze und Regen; Kalben und Schutt der Gletscher.

### 6.5 `LB_NET_WEG` — Wege über Gelände, mehrere Kosten, mehrere Modi

- **Hierarchisch:** fein (10 m) im Kern, grob (50 m) in der Szene.
- **Kosten:** Gehzeit nach Tobler aus der Neigung, Zuschläge aus WorldCover (Wald, Gebüsch), Gletscher, Bäche.
- **Mehrere Modi:** Umsteigekanten Boot → Strand verbinden mit `LB_NET_SEE`.
- **Analysen:** *k* kürzeste Wege (Routenvarianten), `withinCost` (wie weit kommt man in einer Stunde vom Strand), Washburns Route 1934 nachgerechnet.
- **Groß:** Das Netz der Szene hat Millionen Kanten → Partitionen, LOD und Speicher werden gemessen (Leistungstabelle im Werkbuch).

### 6.6 `LB_NET_SICHT` — Sicht und Kamera

- **Knoten:** Aussichtspunkte, Gipfel, Ankerplätze, Kamerapunkte. **Kanten:** freie Sicht im Höhenmodell, Kosten = Flugzeit der Kamera.
- **Analysen:** TSP für einen Rundflug über alle Motive, minimaler Spannbaum für eine Kamerafahrt, `nearestNeighbors` (welches Motiv ist von hier zu sehen).
- **Für die Szene:** speist Kamera-Presets und Regiebücher.

### 6.7 `LB_NET_HERKUNFT` — woher jede Zahl kommt (logisch)

- **Logisches Netz** ohne Geometrie: Quelle → Rohdatei → Aufbereitung → Befund → Szenenobjekt. Gerichtet.
- **Analysen:** Erreichbarkeit („Was hängt an H08492?“, „Woher kommt diese Wassertiefe?“), Zusammenhangskomponenten (Befunde ohne Quelle = [G]).
- Verbindet sich mit `LB_QUELLE` und `LB_BEFUND` (wie `SY_QUELLE`/`SY_BEFUND` in Sylt).

### 6.8 Was von NDM genutzt wird (Abnahme-Matrix im Werkbuch)

| Fähigkeit | Netz |
|---|---|
| räumliches Netz mit Höhe, gerichtet und ungerichtet | alle außer 6.7 |
| logisches Netz | 6.7 |
| LRS-Netz, dynamische Segmentierung | 6.2 |
| hierarchisches Netz | 6.4, 6.5 |
| mehrere Kosten, eigener `LinkCostCalculator` | 6.1, 6.3, 6.5 |
| zeitabhängige Kosten | 6.3 |
| Einschränkungen (`LODNetworkConstraint`) | 6.3 |
| kürzester Weg (Dijkstra, A*), *k* kürzeste Wege | 6.1, 6.3, 6.5 |
| `withinCost`, Isochronen, Network Buffer | 6.1, 6.5 |
| `nearestNeighbors` | 6.1, 6.6 |
| TSP, minimaler Spannbaum | 6.6 |
| `traceIn`/`traceOut`, Erreichbarkeit, Zusammenhangskomponenten | 6.4, 6.7 |
| Feature-Layer | 6.2 (wenn vorhanden) |
| Partitionen, Partition-BLOBs, LOD | 6.1, 6.5 |
| `VALIDATE_NETWORK`, Prüfwege gegen `route` | alle |
| Property Graph mit SQL/PGQ | alle, nur wenn 23ai (F27) |

---

## 7. Die Szene (projektspezifisch, `com.dan.lituya`)

**Zeitstände** (F4): **vor** (Abend des 9. Juli 1958: Wald bis ans Ufer, Gletscherfront 1958, Grund 1940), **die Welle** (Beben, Felssturz, Welle, die ersten Minuten), **danach** (Spurlinie, Treibholz, Grund 1959), **heute** (Lidar, BlueTopo 2025, Wald jünger unter der Spurlinie).

**Bauteile und Details:**
- **Gelände:** Lidar im Kern, IfSAR/3DEP in der Szene, Copernicus/GEBCO im Fern. Rückbau auf 1958 [G] an den Stellen, an denen sich seit 1958 etwas verändert hat (Gletscherfront, Rutschnarbe, Ufer).
- **Wasser:** `ocean` draußen, `wave` in der Bucht, `surf` an der Barre im Eingang.
- **Gletscher:** Lituya- und Nord-Crillon-Gletscher mit Front, Spalten und Kalben.
- **Wald:** Fichten und Hemlock, dichte Ufer, Wellenwurf bis zur Spurlinie.
- **Boote:** Edrie, Badger, Sunmore als Fischkutter der 1950er (Bauart recherchieren), vor Anker, mit Licht.
- **Leben:** Möwen, Seehunde im Eingang, Bären am Ufer (F21).

**Schaustücke** (Kamera-Presets und Regie):
1. **Abend vor Anker:** Anchorage Cove, ruhiges Wasser, Mt Fairweather im Abendlicht
2. **Das Beben:** Die Bucht zittert, Lawinen an den Hängen, Eis fällt vom Gletscher
3. **Der Sturz:** die Wand des Gilbert Inlet bricht, Staub, Eintauchen
4. **524 m:** die Welle läuft den Sporn hinauf, der Wald verschwindet
5. **Die Front:** Kamera vor der Front die Bucht hinab, Isochronen im **Netzblick** (6.1)
6. **Mit der Edrie:** die Welle aus der Sicht des Boots in Anchorage Cove
7. **Über die Nehrung:** die Badger über La Chaussee Spit
8. **Der Morgen danach:** Spurlinie, Treibholz, nackter Fels
9. **Heute:** derselbe Blick 2026, der junge Wald zeichnet die Spurlinie nach
10. **Netze:** Gewässer, Wege, Sicht, Herkunft als Ebenen über der Szene

---

## 8. Erfahrungen aus bisherigen Werkbüchern (übernehmen)

Sylt Abschnitt 7 gilt vollständig (Werkbuch zuerst, Fragen mit A als Empfehlung, Statusbilder je Phase, `…Check` mit „Alles in Ordnung.“, Quellen belegt/gemessen/[G], Kino, Hörproben, GL-Engine aus der jüngsten Szene, FFrame und FStyle, Sequence + Trigger statt IDENTITY, Git ab Phase 1, Schliff mit README, Icon, Video und Dauerlauf, Leistung von Anfang an messen). Dazu aus Timmendorf: Abschnitt 2.5 dieser Datei. Neu:

- **Jedes Netz hat seine Prüfwege und seine Zweitrechnung**, bevor die Szene es nutzt.
- **NDM-Funktionsprobe** zu Beginn (Abschnitt 3): Was die Version nicht kann, wird nicht geplant, sondern als Wunsch notiert.
- **Leistung der Netze messen:** Ladezeit, Partitionen, LOD-Speicher, Zeit je Analyse; Ziel und Ergebnis in einer Tabelle im Werkbuch.

---

## 9. Gliederung des Werkbuchs (zu erstellen)

1. Auftrag und Leitidee („Projekt der Riesenwelle und des Netzes“)
2. Recherche und was daraus folgt (Ereignis, Boote, Grund 1940/1959, Wald)
3. Daten: Gebiete, Kartenmaterial, Aufbereitung, Regel „ohne `C:\Daten`“
4. Grundsatzentscheidungen (Renderer-Ablauf, Engine, Paketvertrag mit Punkt 13)
5. Package-Katalog: `wave`, `route`, `forest`/`tree`, `fracture` mit Schnittstellen und Abhängigkeitsbild
6. **Die Netze:** je Netz Frage, Daten, Modell, Kosten, Analysen, Prüfung; Abnahme-Matrix 6.8; NDM-Funktionsprobe
7. Physik in Kurzform: Formeln mit Quelle und [G]
8. Szene: Zeitstände, Gelände, Wasser, Gletscher, Wald, Boote, Leben
9. Regie: Uhrzeit, Gezeiten, Wetter, Kamera-Presets, Regiebücher, Kino, Netzblick
10. Oberfläche (FFrame, Bedienfeld NETZE)
11. Datenbank: Schema, Netze, Quellen, Befunde, Zeugen, Szenarien
12. **Phasenplan** mit Lieferumfang und Abnahme, getrennt nach Renderer, App und Datenbank
13. Leistungsbudget (Bild und Netze) und Risiken
14. **Offene Fragen** (nummeriert, A/B/C)
15. Ideenspeicher
16. Fortschritt (Statusbilder, Freigaben, JAR-Stände, Netzstände, Datum)

---

## 10. Phasenplan — Entwurf (im Werkbuch verfeinern)

„R“ heißt Renderer, „L“ heißt LituyaBay1958, „D“ heißt Datenbank.

| Phase | Ort | Inhalt | Abnahme |
|---|---|---|---|
| 0 | L | **erledigt:** Gebiete, Kartenmaterial (47 Quellen), Werkzeug `tools/kartenmaterial` | `Kartenmaterial.md`, `download/INVENTAR.md` |
| 1 | L | Grundgerüst: FFrame, GL-Engine übernehmen, Kamera, HUD mit FPS, `wiring`, Startbild mit DB-Anmeldung; `sdonm.jar` und `lib/lod/` wie Timmendorf | leere Szene mit Horizont |
| 2 | L | Aufbereitung (`tools`): Gelände Kern/Szene/Fern, Grund 1940/1959/heute, Lidar, DSM, Karten 1951/1961 georeferenziert, Namen → `daten/` mit `INHALT.txt` | Prüfbilder; **Start mit umbenanntem `C:\Daten`** gelingt |
| 3 | D | Schema, Rechte (2.5), **NDM-Funktionsprobe**, Ladeskripte `db/netz/`, erstes Netz `LB_NET_HERKUNFT` (klein, logisch) als Probe des Ablaufs | Funktionsliste belegt, `VALIDATE_NETWORK` grün |
| 4 | R | `route` III: Gitternetze, Laufzeitbaum, Isochronen, Messstrecke | `RouteCheck` mit Handrechnungen |
| 5 | L+D | **`LB_NET_WELLE`**: Gitter aus dem Grund, vier Kosten, Quellknoten, Partitionen, LOD; Vergleich NDM ↔ `route` | gleiche Ankunftszeiten; Isochronenbild 1940 und heute |
| 6 | L+D | **`LB_NET_UFER`** (LRS): Ufer 1958 und heute, Auflaufhöhen, Spurlinie, Feature-Layer | Spurlinie je Ufermeter als Bild, belegt gegen Karte Miller |
| 7 | L | Grundbild: Gelände, Wasser, Himmel, Gletscher, Wald bis ans Ufer (Zeitstand „vor“) | Abend vor Anker |
| 8 | R | `wave` erweitern: großes Gitter, Impuls, Auflauf am Steilhang, Front als Weißwasser | `WaveCheck` neu, Laborfall Fritz 2001; Rungholt unverändert |
| 8a | L | Welle in der Szene; **Vergleich `wave` ↔ `LB_NET_WELLE` ↔ Augenzeugen** | Tabelle der Ankunftszeiten |
| 9 | R | `fracture`: Felssturz mit Impuls an `wave` | Demo: Platte stürzt in ein Becken |
| 9a | L | Felssturz im Gilbert Inlet, Staub, Gletscherfront | „Der Sturz“ und „524 m“ |
| 10 | R | `forest`/`tree`: Wellenwurf, Treibgut, Nachwuchs | Demo; Haithabu/Timmendorf unverändert |
| 10a | L | Wald und Spurlinie aus `LB_NET_UFER` und `wave.runupAt`; Zeitstände „danach“ und „heute“ | „Der Morgen danach“, „Heute“ |
| 11 | L+D | **`LB_NET_SEE`**: zeitabhängige Kosten, Einschränkung Tiefgang, Fluchtwege; Boote in der Szene | „Wer hätte entkommen können?“ als Tabelle und Bild |
| 12 | L+D | **`LB_NET_WASSER`** (hierarchisch): Bäche, Gletscher, Einzugsgebiete; `fall`/`river` in der Szene | Einzugsgebiete als Bild, Bäche führen Wasser |
| 13 | L+D | **`LB_NET_WEG`** (hierarchisch, mehrere Modi, groß): Gehzeit, Varianten, Washburn 1934; Leistung der Partitionen | Leistungstabelle, Routenbild |
| 14 | L+D | **`LB_NET_SICHT`**: TSP-Rundflug, Spannbaum; Regiebücher und Kino, Netzblick | Kino läuft durch |
| 15 | L | Klang (Beben, Sturz, Welle, Wald), Wetter, Leben; Hörproben | Hörproben abgenommen |
| 16 | L+D | Oberfläche und Datenbank (eigener Fragenblock): Bedienfeld NETZE, Speichern und Laden, `LB_ZEUGE`, Szenarien | Tabellen freigegeben |
| 17 | L | Schliff: Qualitätsstufen, READMEs, GitHub-README, Icon, Verknüpfungen, Video, Dauerlauf | „fertig“ |

---

## 11. Fragen-Kandidaten für das Werkbuch

*(Claude formuliert sie aus, ergänzt sie und setzt jeweils die Empfehlung als A.)*

| Nr. | Frage | A (Empfehlung) | B | C |
|---|---|---|---|---|
| F1 | Gebiete | Kern/Szene/Fern wie in 2.2 | Kern enger (nur die Bucht) | größer |
| F2 | Maßstab und Lage | Echt, UTM 8N, Höhen NAVD88 | Lage frei | — |
| F3 | Höhenbezug | NAVD88 (GEOID12B), alte Vermessungen von MLLW umgerechnet über die Bezugshöhen der Station | MLLW für Wasser, NAVD88 für Land | — |
| F4 | Zeitstände | vor, Welle, danach, heute; umschaltbar, die Welle verändert die Szene im Lauf | nur 1958 | nur heute mit Rückblick |
| F5 | Grund 1958 | Vermessung 1940, abgeglichen mit Franco 2020 und 1959 | BlueTopo heute [G] | — |
| F6 | Engine | GL-Engine aus der jüngsten Szene (Timmendorf) | neu | — |
| F7 | Gitter für `wave` | drei Gitter (Kern 16 m, mittel 4–8 m, Gilbert Inlet 2 m) | zwei Gitter, Kern 8 m | eines |
| F8 | Nichtlinearität | Flachwasser mit Stoßerfassung, Dispersion nur prüfen | Boussinesq-Korrektur einbauen | linear |
| F9 | Gitter `LB_NET_WELLE` | 20 m, 16 Nachbarn | 10 m, 8 Nachbarn | 50 m |
| F10 | Kosten im Wellennetz | vier Kosten über `LinkCostCalculator` in einem Netz | je Zeitstand ein eigenes Netz | nur linear heute |
| F11 | Quelle der Welle | virtueller Quellknoten an der Einschlaglinie | ein Startknoten | mehrere Läufe |
| F12 | Isochronen | Network Buffer, wenn vorhanden; sonst Konturen aus den Ankunftszeiten | immer eigene Konturen | — |
| F13 | Netze im Projekt | alle sieben, schrittweise nach Phasenplan | nur Welle, Ufer, See | nur Welle |
| F14 | Datei-Abbild | `daten/netz/` wie Timmendorf, App läuft ohne DB, Oracle prüft und rechnet | nur Oracle | nur Datei |
| F15 | LOD und Treiber | wie Timmendorf: eigener Klassenlader, Treiber 21 in `lib/lod/` | neueres `sdonm.jar` suchen | PL/SQL statt LOD |
| F16 | Felssturz | `fracture` mit Impuls an `wave` | Volumenquelle über Zeit [G] | gestaltet ohne Physik |
| F17 | Spurlinie im Wald | belegt (Ufer-LRS) und gerechnet (`wave`) nebeneinander, umschaltbar | nur belegt | nur gerechnet |
| F18 | Boote | drei Boote nach Miller und Zeugen, Positionen [G] wo unbelegt | ohne Boote | nur die Edrie |
| F19 | Menschen | an Bord sichtbar, klein, ohne Gesicht (`figure`) | keine | — |
| F20 | La Pérouse 1786 | als zweites Szenario im Seenetz | als Ideenspeicher | weglassen |
| F21 | Tiere | Möwen, Seehunde; Bären am Ufer als Zugabe | nur Möwen | keine |
| F22 | Washburn 1934 | Route im Wegenetz nachrechnen, falls belegt | frei | weglassen |
| F23 | Gletscher 1958 | Front aus Karte 1951 und C-5 (Luftbilder 1954), Miller | heutige Front | — |
| F24 | Ton | erzeugt, ohne Audiodateien | keiner | Dateien |
| F25 | Leistungsziel | Hoch ≥ 30 fps Full HD, Mittel 60 fps; Netzanalysen außerhalb des Bildfadens | nur 30 fps | frei |
| F26 | Netzblick | Bedienfeld NETZE: Isochronen, Wellenstrahlen, Ufer-LRS, Gewässer, Wege, Sicht | nur Isochronen | keiner |
| F27 | Property Graph | nur wenn die Datenbank 23ai ist: SQL/PGQ-Sichten auf die Netze | nicht nutzen | Upgrade planen |
| F28 | Schema | `LITUYA`, Präfix `LB_`, Netze `LB_NET_*` | `LITUYABAY1958` | DEMO |
| F29 | SRID | 26908 (NAD83 / UTM 8N) für alles Räumliche, 4326 für Austausch über Sichten | 32608 | — |
| F30 | Programmsymbol | `.ico` mit Goldkante wie `heidelberg.ico`, Motiv Spurlinie über der Bucht | Motiv Welle | — |

---

## 12. Datenbank-Ideen (für Phase 16, noch nicht ausarbeiten)

- `LB_QUELLE`, `LB_BEFUND`, `LB_BEFUND_QUELLE` wie in Sylt; dazu `LB_NET_HERKUNFT` (6.7)
- `LB_ZEUGE`: Aussagen der Überlebenden mit Zeit, Ort und Quelle; Prüfpunkte für 6.1 und 8a
- `LB_AUFLAUF`: Auflaufhöhen (NCEI, Miller) als Punkte und als Ereignisse auf `LB_NET_UFER`
- `LB_SPURLINIE`: Spurlinie je Stand (Miller 1958, Rechnung, Lidar heute) als `SDO_GEOMETRY`
- `LB_ISOCHRONE`: Isochronen je Kostenart als Polygone
- `LB_BOOT`, `LB_BOOT_LAGE`: Boote mit Ankerplatz und Bahn
- `LB_NETZ_STAND`: Prüfsumme je Netz gegen das Datei-Abbild (wie `TD_NETZ_STAND`)
- `LB_SZENARIO` / `LB_SZENARIO_SCHRITT`: Regiebücher; `LB_KAMERA_FAHRT` aus `LB_NET_SICHT`

---

## 13. Ideenspeicher (eingebracht von Claude)

- **Rückwärts rechnen:** Aus den Zeiten der Zeugen an mehreren Orten auf die Quelle schließen (`withinCost` von den Booten aus, Schnitt der Isochronen). Trifft es den Gilbert Inlet?
- **Wellenstrahlen:** Die kürzesten Wege im Wellennetz als Linien zeigen, wie die Front sich an Cenotaph Island teilt und dahinter wieder schließt.
- **Die Uhr der Welle:** Im Kino eine Laufzeituhr je Ort aus dem Netz.
- **1940 gegen heute:** Dieselbe Welle auf dem Grund von heute; um wie viel früher oder später käme sie an?
- **Frühere Wellen:** Spurlinien von 1853/54, 1874, 1899 und 1936 als Ereignisse auf dem Ufer-LRS, so wie Miller sie am Wald gelesen hat.
- **Der Wald als Kalender:** Höhe des Nachwuchses unter der Spurlinie aus den ArcticDEM-Streifen 2013–2022.
- **Wege der Tlingit:** Namen und Wege als eigene Ebene im Wegenetz, wenn belegt.
- **Herkunft im Bild:** Klick auf eine Wassertiefe zeigt den Weg im Herkunftsnetz bis zur Vermessung.

---

## 14. Arbeitsanweisung an Claude

1. **Projektordner sichten:** LituyaBay1958 (`Kartenmaterial.md`, `download/INVENTAR.md`, `tools/kartenmaterial/`, `geodaten/`), Renderer (READMEs der Packages in 2.4, `muster`, `tools/PaketPruefung.java`), Timmendorf (`db/netz/*.sql`, `tools/NetzDb.java`, `data/NetzOracle.java`, `docs/WERDEGANG.md` Phase 3/3b/4), Sylt (Werkbuch-Aufbau, `QUELLEN.md`).
2. **Bilder ansehen:** `images/1958_Lituya_Bay1.jpg`, `images/Gebiete_*.jpg`, `images/Kern_BlueTopo.png`.
3. **Recherche** nach Abschnitt 3; alles mit Quelle in die Fragen und in „Physik in Kurzform“.
4. Das **„LituyaBay1958 Werkbuch“** als Artefakt anlegen, nach der Gliederung in Abschnitt 9, mit Paketvertrag (13 Punkte), Package-Katalog, **Netzkatalog mit Abnahme-Matrix**, Phasenplan und **nummerierten Fragen** (A = Empfehlung).
5. **Keinen Code und keine Skripte schreiben**, bevor die Fragen beantwortet sind und Phase 1 freigegeben ist.
6. Nach jeder Phase das Werkbuch fortschreiben: Statusbild, Stand von `Renderer.jar`, Stand jedes Netzes (Knoten, Kanten, Partitionen, Prüfwege, Zeiten).
7. Regel „ohne `C:\Daten`“ in jeder Phase. Neue Rohdaten kommen über `tools/kartenmaterial` (Katalog) und die Aufbereitung, nie über den Laufzeitcode.
