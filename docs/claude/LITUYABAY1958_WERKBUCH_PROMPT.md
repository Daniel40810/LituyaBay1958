# LituyaBay1958 — Planungsprompt für das Werkbuch

> **Zweck dieser Datei:** Aus ihr erstellt Claude das **„LituyaBay1958 Werkbuch“** (angelegt am 01.11.2026 als `docs/claude/LITUYABAY1958_WERKBUCH.md`).
> Noch **kein Code, keine Skripte** (außer dem schon vorhandenen Werkzeug `tools/kartenmaterial`). Zuerst wird geplant: Ausgangspunkt klären, Themen und Struktur festlegen, offene Fragen stellen. Programmiert wird erst, wenn Phase 1 freigegeben ist.
>
> Stand: 01.11.2026, überarbeitet nach den Wünschen vom selben Tag (Kartenmaterial nach Abschluss auslagern, möglichst viele neue Packages für den Renderer, passende Texturen, bestehende Engines optimieren, **episch**) · NetBeans-Projekt `C:\Users\Dan40810\Documents\NetBeansProjects\LituyaBay1958` · Repo `Daniel40810/LituyaBay1958`

---

## 1. Auftrag

**Lituya Bay, Alaska, 9. Juli 1958: das Projekt der Riesenwelle und des Netzes.** Das Werkbuch beschreibt eine animierte 3D-Echtzeitszene der Bucht am Fuß der Fairweather-Kette. Ein Beben an der Fairweather-Störung löst einen Felssturz in den Gilbert Inlet aus. Die Welle läuft am Gegenhang bis 524 m hinauf, die höchste je gemessene Auflaufhöhe einer Welle. Sie rasiert den Wald von den Ufern und läuft durch die Bucht bis zum Eingang. Drei Fischerboote liegen in dieser Nacht vor Anker. Die Szene zeigt die Bucht **vor**, **während** und **nach** der Welle und **heute**.

Auf Sylt („Brandung“) und Timmendorf („Segel“) folgt das **„Projekt der Riesenwelle“**. Es hat drei Säulen:

1. **Die Ereigniskette als Physik:** Beben → Hangversagen → Felssturz → Eintauchen → Impulswelle → Auflauf → Wellenwurf → Treibgut → Wiederbewaldung. Jedes Glied ist ein eigenes Package im Renderer.
2. **Das Oracle Network Data Model (NDM), richtig ausgereizt.** Sieben Netze der Natur und des Ereignisses (Abschnitt 7). Jedes beantwortet eine Frage, die man in der Szene sieht, und wird gegen eine zweite Rechnung oder einen Beleg geprüft.
3. **Das Bild: episch.** Mt Fairweather (4 671 m) steht rund 24 km vom Meer (gemessen am Copernicus-DEM), der Maßstab ist riesig, die Julinacht hell. Gelände bis 0,5 m aus dem Lidar, Fernsicht bis zum Horizont, echte Texturen, Wald in Millionen Bäumen, Kino mit Zeitlupe und Unterwasserblick.

### 1.1 Was „episch“ heißt (Leitplanken)

- **Maßstab zeigen:** Die Welle ist höher als jedes Gebäude in Europa, die Berge steigen in 24 km von null auf fast 4 700 m. Kamera und Licht müssen das spürbar machen: Menschen, Boote und Bäume als Maßstab im Bild, atmosphärische Perspektive über 60 km, Erdkrümmung am Horizont.
- **Zeit dehnen:** Die ersten zehn Minuten nach dem Beben in Echtzeit, in Zeitlupe (bis 1:20) und im Zeitraffer; dazu 68 Jahre Wiederbewaldung in einer Minute.
- **Alle Sinne:** Klang (Grollen des Bebens, Donner des Sturzes, Brüllen der Welle, brechender Wald), Licht (Alpenglühen am Fairweather, Staub im Gegenlicht), Wasser (Gletschermilch, Gischt, Unterwasser).
- **Wahr bleiben:** Episch durch Physik und Belege, nicht durch Übertreibung. Was gestaltet ist, trägt [G]. Die 524 m sind beeindruckend genug.

**Detailtreue und Physik sind ausdrücklich gewünscht**, wie in Sylt und Timmendorf. Eigene Ideen sind erwünscht. Wo nötig, entstehen neue eigenständige Engines.

---

## 2. Ausgangslage (geprüft am 01.11.2026)

### 2.1 Das Projekt LituyaBay1958

| Bereich | Stand |
|---|---|
| JDK | `javac.source/target=21` |
| `lib/` (Klassenpfad) | `Renderer.jar`, `FStyle.jar`, JOGL + gluegen (win-amd64), `ojdbc11.jar`, `sdoapi.jar`, `sdogr.jar`, `sdoutl.jar`. **Es fehlt `sdonm.jar`** (NDM-Java-API, `oracle.spatial.network.lod`), in Timmendorf vorhanden. |
| `src/` | leer, `main.class` nicht gesetzt |
| Datenbank | `db/` lokal angelegt, nicht im Repo (`db/db.properties`, `db/info/` in `.gitignore`). Schema noch festzulegen (F40). |
| `docs/` | in `.gitignore` bis auf `docs/claude/` (diese Datei) |
| Bilder | `images/1958_Lituya_Bay1.jpg` (Luftbild nach der Welle), `images/Gebiete_*.jpg`, `images/Kern_BlueTopo.png` |
| Git | `main` mit Phase 0 (PR #1 und #2) |

### 2.2 Phase 0 ist erledigt: Gebiete und Kartenmaterial

Siehe `Kartenmaterial.md` (Katalog von 48 Quellen) und `download/INVENTAR.md` (Planung mit Größen).

**Gebiete** in UTM Zone 8 Nord (EPSG:26908, NAD83), volle Kilometer (`tools/kartenmaterial/geo.py`, `geodaten/gebiete.geojson`):

| Gebiet | Ost (m) | Nord (m) | Größe | Inhalt |
|---|---|---|---|---|
| Kern | 340 000 – 364 000 | 6 494 000 – 6 512 000 | 24 × 18 km | Bucht, Gilbert und Crillon Inlet, Cenotaph Island, Eingang, Hänge über der Spurlinie, Zunge des Lituya-Gletschers |
| Szene | 322 000 – 388 000 | 6 480 000 – 6 540 000 | 66 × 60 km | Fairweather-Kette (Mt Fairweather, Mt Crillon, Mt La Perouse), Küstenebene, Cape Fairweather |
| Fern | 220 000 – 480 000 | 6 385 000 – 6 625 000 | 260 × 240 km | Horizont von Yakutat bis Cross Sound |

**Was die Daten hergeben** (alle frei, Lizenzen im Katalog):

| Thema | Daten (Katalog-ID) | Stand der Prüfung |
|---|---|---|
| Grund der Bucht | vor der Welle: NOS-Vermessungen H06581–83 (1940), H04608/H04648 (1926); nach der Welle: H08492 (21.7.–27.8.1959); heute: BlueTopo (4–16 m, NAVD88, Quellentabelle je Zelle) mit H14228 (Rainier, Juli 2025), USACE-Topobathy 2019 (1 m) | BlueTopo im Kern geladen und gelesen: Becken bis −135 m, Bucht vollständig (`images/Kern_BlueTopo.png`). Vermessungen ungeprüft |
| Gelände | Lidar Glacier Bay 2019/20 (0,5–1 m, 312 Kacheln im Kern), IfSAR 2012 (5 m DTM, DSM, Radarbild), 3DEP, ArcticDEM (Mosaik bis 2 m, 47 datierte Streifen über dem Kern 2013–2022), Copernicus, GEBCO | geprüft |
| Karten | USGS 1951 (Luftbilder 1948, Stand vor der Welle), C-5/C-6 1961 (Luftbilder 1954/1948), US Topo 2017, ENC | Karten geprüft, ENC ungeprüft |
| Bild und Bewuchs | Sentinel-2 ab 2017, Landsat ab 1972, WorldCover 2021, Fotos Miller 1958 | S3 geprüft, Rest ungeprüft |
| Gletscher | ITS_LIVE-Geschwindigkeit, RGI 7, GLIMS (mehrere Zeitstände) | S3 geprüft, Rest ungeprüft |
| Ereignis | ComCat (Beben iscgem884702), NCEI-Tsunami-Datenbank, Gezeiten 9452928, ERA5 Juli 1958, Miller 1960 (PP 354-C mit Karte 1:50 000) | ERA5 geprüft, Rest ungeprüft |
| Namen | 94 GNIS-Namen in der Szene (`geodaten/namen_szene.geojson`) | geprüft |

### 2.3 Kartenmaterial: nur in Phase 2 gelesen, nach Projektabschluss ausgelagert

Das Kartenmaterial (`download/` bzw. `C:\Daten\LituyaBay1958`, Standard ≈ 11 GB, mit `--gross` ≈ 50 GB) **wird nach Abschluss des Projekts verschoben** (Wechselfestplatte). Daraus folgt eine Regel wie in Sylt (Abschnitt 2.4 dort). **Sie gilt verbindlich und ist Abnahme-Bedingung:**

1. **Kartenmaterial liest nur die Aufbereitung** (`com.dan.lituya.tools`), einmal. Ihren Pfad legt `daten/rohdaten.properties` fest (in `.gitignore`), Vorgabe `download/`.
2. Die Aufbereitung schreibt **alles, was die App braucht, nach `daten/`**: Gelände, Grund 1940/1959/heute, Material- und Klassenkarten, Netze (`daten/netz/`), Texturen (`daten/texturen/`), vorgerechnete Ereignisse (`daten/ereignis/`). `daten/` steht in `.gitignore` und wird in `daten/INHALT.txt` mit Prüfsumme je Datei beschrieben.
3. **Die App und die Netze lesen nie Kartenmaterial.** Oracle wird aus `daten/netz/` geladen, nicht aus `download/`.
4. **Abnahme von Phase 2:** `download/` wird vorübergehend umbenannt, und App, Netze und Prüfungen laufen vollständig. Fehlt `daten/`, sagt das Startbild, welches Werkzeug mit welchem Pfad zu laufen hat.
5. **Nach Projektabschluss** bleiben im Repo: `tools/kartenmaterial/` (damit lässt sich alles jederzeit neu laden), `Kartenmaterial.md`, `download/INVENTAR.md` als Nachweis, `geodaten/`. `docs/QUELLEN.md` nennt für jede Rohdatei Stand, Lizenz und den Ort auf der Wechselfestplatte.
6. Wer neue Rohdaten braucht, ergänzt sie im Katalog (`tools/kartenmaterial/quellen*.py`) und in der Aufbereitung, nie im Laufzeitcode.

Die ungeprüften Adressen aus Phase 0 werden beim ersten Laden auf dem eigenen Rechner berichtigt (`INVENTAR.md`, Abschnitt „Fehlgeschlagen“).

### 2.4 Der Renderer: was schon da ist

Im Renderer (52 Packages): `beach beachchair beacon buoyancy cliff cloth cloud dune fall figure firework flock fog forest fracture ground hearth herd ice jelly kino lamp lightning longship mudflat muster ocean rail rain reed rime river road route sail seal season sky smoke soundscape spray store surf tide timber tree wake wave weather weathering wetglass wind`.

Vor dem Werkbuch die READMEs dieser Packages lesen:

| Package | Stand laut README | Rolle in Lituya Bay |
|---|---|---|
| `wave` | Löser (Rungholt Phase 8, `hold` seit 12): Flachwasser nach Arakawa C, zwei Gitter (8 m / 2 m), Auflaufen, Pegel; rechnet auf der CPU (rund 290 ms je Sekunde Welle bei 512² + 1 024²); „wiederverwendbar für einen Felssturz in einen See“ | **Fernfeld der Welle.** Grenzen laut README: Einschlag nur als Volumen, keine Dispersion, kein Brechen, grobes Gitter 8 km → **optimieren** (6.1) |
| `fracture` | Engine (Glacier Phase 6), Lockergestein seit Sylt 11 | Ablösen der Felsplatte, Gletscherfront |
| `ice` | Gletscher als Körper (Glacier Phase 5) | Lituya- und Nord-Crillon-Gletscher |
| `cliff` | Fels als Material (Geiranger), `brodtenerUfer()` seit Timmendorf | Wände der Bucht, Rutschnarbe |
| `forest`, `tree` | Bäume im Wind, Wälder instanziert, `ForestField` mit Kacheln zu 64 m | Regenwald bis zur Spurlinie → **optimieren** (6.4) |
| `route` | Timmendorf Phase 12: Graph, Dijkstra, A*, Umkreis, `Crowd`, `Line` | Zweitrechnung zu NDM → **erweitern** (6.9) |
| `ocean`, `surf`, `spray`, `tide`, `wake` | aus Sylt und Timmendorf | offene See, Barre im Eingang, Gischt, Pegel, Kielwasser der Boote |
| `fall`, `river` | Geiranger | Bäche und Wasserfälle von den Gletschern |
| `sky`, `cloud`, `fog`, `rain`, `weather`, `smoke`, `flock`, `seal`, `figure`, `buoyancy`, `soundscape`, `kino`, `store`, `season` | aus den Vorgängern | Licht in 58,6° N, Möwen, Seehunde, Menschen an Bord, Klang, Kino |

**Texturen aus Assets** gibt es seit Timmendorf Phase 21: `Renderer/tools/beach/SandAsset.java` backt aus `sand_dunes_gltf.zip` kachelbare Karten (Relief und Korn, 1024²) ins Package `com.dan.beach`. Dieses Verfahren wird verallgemeinert (Abschnitt 5.10 und 8).

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

- **Ereignis:** Bebenzeit (ComCat iscgem884702: 10.07.1958 06:15:58 UTC), Magnitude, Bruchlänge und Versatz an der Fairweather-Störung, Ortszeit und Zeitzone in Alaska 1958, Sonnenstand und Dämmerung in 58,6° N, Tidenstand aus der Vorausberechnung für 9452928, Wetter (ERA5, Augenzeugen: klar und ruhig?).
- **Felssturz:** Volumen (Miller 1960: rund 40 Mio. yd³ ≈ 30 Mio. m³), Fallhöhe, Abrissfläche, **Gestein** (für Texturen und `landslide`), Dauer des Eintauchens. Abrissnarbe am Lidar 2019 nachmessen.
- **Welle:** Auflauf 524 m (1 720 ft) am Sporn gegenüber, Höhe der Spurlinie entlang der Ufer (Miller, Karte 1:50 000), Fläche des zerstörten Walds, Geschwindigkeit der Front (Augenzeugen, Fritz et al. 2001/2009, Laborversuche), Front des Lituya-Gletschers vor und nach dem Ereignis.
- **Boote:** Edrie (Howard Ulrich mit Sohn, Anchorage Cove), Badger (Swanson, über La Chaussee Spit getragen), Sunmore (Wagner, im Eingang gesunken). Bauart, Länge, Ankerplätze, Zeiten und Beobachtungen aus Miller 1960 und den Berichten der Überlebenden; was nicht belegt ist, wird [G].
- **Frühere Wellen** in der Bucht (Miller: 1853/54, 1874, 1899, 1936) mit ihren Spurlinien.
- **Bucht 1958:** Grund aus 1940 und 1959, Vergleich mit Franco et al. 2020; Schwelle und Gezeitenstrom im Eingang (La Pérouse verlor dort 1786 21 Mann). Farbe und Trübung des Wassers (Gletschermilch).
- **Wald:** Arten (Sitka-Fichte, Westliche Hemlocktanne, Erle), Höhen am DSM 2012, Unterwuchs (Moos, Farne, Teufelskeule), wie die Welle die Bäume brach und entrindete (Fotos Miller), Abfolge der Wiederbesiedlung, Nachwuchs am DSM und an den ArcticDEM-Streifen.
- **Gletscher:** Spalten, Seracs, Schuttbedeckung, Farbe des Eises; Kalbung an der Front 1958.
- **Tiere:** Küstenbraunbär, Seehunde, Seeotter (Name „Sea Otter Glacier“), Möwen, Weißkopfseeadler.
- **Wege:** Washburns Crillon-Expedition 1933/34 (Ausgangspunkt, Route), Tlingit-Namen und -Wege (NPS „Tlingit Place Names of Huna Káawu“).
- **Polarlicht:** in welchen Monaten in 58,6° N sichtbar, Farben, Häufigkeit (für den Zeitstand „heute“ im Herbst und Winter).
- **NDM-Funktionen gegen die installierte Version:** `select banner from v$version`; vorhanden oder nicht: LRS-Netze, Feature-Layer (`SDO_NET.ADD_FEATURE_LAYER`), `FIND_CONNECTED_COMPONENTS`, LOD `NetworkAnalyst` (`withinCost`, `kShortestPaths`, `tsp`, `mcst`, `traceIn/traceOut`, `nearestNeighbors`), `LinkCostCalculator`/`NodeCostCalculator`, Network Buffer, Property Graph mit SQL/PGQ (nur 23ai). Die Liste im Werkbuch heißt „NDM-Funktionsprobe“ und wird in Phase 3 mit je einer Zeile SQL oder Java belegt.
- **Texturquellen:** Lizenzen der CC0-Bibliotheken (Poly Haven, ambientCG) prüfen, passende Sätze je Material suchen (Abschnitt 8).

---

## 4. Kernprinzip: der Paketvertrag (aus Sylt Abschnitt 4, erweitert)

Die zwölf Punkte aus Sylt gelten. Dazu kommen drei:

13. **Kein Package kennt Oracle.** NDM, JDBC und `sdonm.jar` gibt es nur in der App (`com.dan.lituya.netz`) und in den Werkzeugen (`com.dan.lituya.tools`). Was ein Package rechnen soll, das auch NDM rechnet (Laufzeitbaum, Umkreis, Messung entlang einer Linie), steht in `route` als unabhängige Zweitrechnung.
14. **Optimierung bestehender Engines** nur mit Messung vorher und nachher (Zeit je Bild, GPU-Zeit, Speicher) und Vergleichsbildern. Alle Szenen, die das Package nutzen (Rungholt, Sylt, Timmendorf, Glacier, Geiranger, Haithabu …), rechnen danach gleich: `…Check` grün, Statusbilder pixelgleich oder als Unterschied erklärt. Die alte Rechnung bleibt als Rückfall wählbar, bis die neue in allen Szenen abgenommen ist.
15. **Texturen:** Kleine gebackene Karten (bis etwa 4 MB je Package) liegen als Ressource im Package, wie in `com.dan.beach`. Große Texturensätze kommen über eine Schnittstelle von der App aus `daten/texturen/`. Jede Textur hat Herkunft und Lizenz in `texturen.txt` des Packages bzw. in `daten/texturen/LIZENZEN.txt`.

---

## 5. Package-Katalog: neue Packages (Vorschlag, im Werkbuch zu bestätigen)

Ziel: **möglichst viele neue Packages**, die nach dem Projekt im Renderer bleiben und in anderen Szenen weiterleben. Jedes neue Package entsteht in `Renderer/src` aus `muster`, mit Kern, `gl/`, `demo/`, `…Check`, README und Statusbild. Die Spalte „später für“ ist die Probe, dass es wirklich wiederverwendbar ist.

| Nr. | Package | Inhalt | später für |
|---|---|---|---|
| 5.1 | `quake` | Erdbeben | Geiranger (Åkerneset), Glacier, jede Gebirgsszene |
| 5.2 | `landslide` | Fels-, Eis- und Schneelawinen | Geiranger, Glacier, Brocken (Winter) |
| 5.3 | `impact` | Nahfeld des Eintauchens | Glacier (Kalbung), Edersee, Stapellauf |
| 5.4 | `dustcloud` | Staubwolke als Dichtestrom | Sylt (Abbruch), Steinbruch, Vulkan |
| 5.5 | `debris` | Treibgut, Holzteppiche | Rungholt, Sylt (Sturmflut), Hochwasser |
| 5.6 | `succession` | Störung und Wiederbewaldung | Brocken (Windwurf, Borkenkäfer), Glacier (Vorfeld) |
| 5.7 | `understory` | Unterwuchs des Regenwalds | Brocken, Haithabu, jede Waldszene |
| 5.8 | `scree` | Geröll, Blöcke, Moränen | Geiranger, Glacier, Brocken (Blockmeere) |
| 5.9 | `vessel` | Motorboote und Kutter mit Anker | Timmendorf (Niendorfer Hafen), Lighthouse, Kiel |
| 5.10 | `surface` | PBR-Materialien und Texturensätze | alle Szenen |
| 5.11 | `clipmap` | Geländestreaming bis 0,5 m und zum Horizont | alle Szenen |
| 5.12 | `netview` | Netze und Felder auf dem Gelände zeichnen | Timmendorf (Netze), Rungholt (Flutlinien) |
| 5.13 | `aurora` | Polarlicht (Zugabe) | Lofoten, Glacier |
| 5.14 | `bear` | Küstenbraunbär (Zugabe) | jede Wald- und Küstenszene |

### 5.1 `com.dan.quake` — Erdbeben

- **Quelle:** Störung als Linie mit Bruchlänge, Versatz und Magnitude (Skalierung nach Wells & Coppersmith), Bruch läuft mit Geschwindigkeit entlang der Störung.
- **Bodenbewegung je Ort:** P-, S- und Oberflächenwellen mit Laufzeit, Beschleunigung und Geschwindigkeit nach Abstand (Abminderungsgesetz), Dauer; als Zeitreihe abrufbar (`accelerationAt(x, z, t)`).
- **Folgen als Ereignisse:** Hangversagen nach Newmark (Verschiebung aus Beschleunigung und kritischer Beschleunigung des Hangs) → `landslide`; Seiche im Becken → `wave`; Kamerazittern; Schnee- und Eisabbrüche.
- **Klang:** Grollen, Infraschall hörbar gemacht, Knistern im Wald.
- **Prüfung:** Laufzeiten P/S gegen Handrechnung, Abminderung gegen Tabelle, Newmark-Verschiebung gegen Beispiel aus der Literatur.

### 5.2 `com.dan.landslide` — Fels-, Eis- und Schneelawinen *(Prunkstück)*

- **Ablösen:** Platte an Kluftflächen (aus `cliff`), ausgelöst durch `quake` oder von Hand; Übergabe von `fracture` (Bruch) an den Strom.
- **Strom:** tiefengemittelter Granularstrom (Savage-Hutter, Reibung nach Voellmy oder µ(I)) über dem Gelände, auf der GPU; Materialien Fels, Eis, Schnee, Schutt.
- **Eintauchen:** Masse, Geschwindigkeit und Querschnitt je Zeitschritt an `impact` und `wave` (Schnittstelle `Landslide.Water`).
- **Ablagerung:** Schuttkegel ins Gelände, Blöcke an `scree`.
- **Bild:** Blöcke und Splitter nahe der Kamera (Instanzen), Strom als Höhenfeld mit Material, Staub an `dustcloud`.
- **Prüfung:** Rutschen auf der schiefen Ebene (Handrechnung), Reichweite gegen Fahrböschung (Heim), Volumen erhalten.

### 5.3 `com.dan.impact` — das Nahfeld des Eintauchens

- Wasserkrater, Wasserwand (Splash Curtain), Strahl nach oben, Spritzer und Sprühnebel, Luftblasen und Weißwasser; Partikel (SPH oder Hybrid) in einem Würfel um den Einschlag.
- Übergabe an `wave` am Rand des Würfels (Wasserstand und Durchfluss), damit Nah- und Fernfeld zusammenpassen.
- **Prüfung:** Laborversuche zum Felssturz-Tsunami (Fritz, Hager & Minor 2001: Lituya Bay im Modell 1:675) als Grenzfall; Volumen erhalten.

### 5.4 `com.dan.dustcloud` — Staubwolke

- Dichtestrom aus Gesteinsstaub: fließt hangab und übers Wasser, hebt sich, verdünnt, driftet im Wind (`wind`), setzt sich ab.
- Licht im Staub: Streuung (Mie), Schatten in der Wolke, Sonne als Scheibe durch den Staub.
- Prüfung: Ausbreitung gegen ein einfaches Modell der Schwerkraftströmung (Benjamin).

### 5.5 `com.dan.debris` — Treibgut

- Stämme mit und ohne Krone, Äste, Eisbrocken, Torf, Schaummatten; Schwimmen über `buoyancy`, Drift mit der Strömung aus `wave` oder `ocean`.
- Verkeilen zu **Holzteppichen**, Stranden am Ufer, Spülsaum aus Holz, langsames Vergrauen über Jahre (`weathering`).
- Prüfung: Drift gegen Strömung, Dichte des Teppichs, Volumen.

### 5.6 `com.dan.succession` — Störung und Wiederbewaldung

- **Störung:** Schadensband mit Zuständen je Fläche (abgeräumt bis zum Fels, Boden freigelegt, Bäume gebrochen, entrindet, umgelegt in Strömungsrichtung), die Kante als scharfe Linie im Wald.
- **Wiederbesiedlung:** Pionierarten (Erle, Weide), dann Fichte und Hemlock; Altersklassen und Höhe nach Jahren; das Band bleibt Jahrzehnte sichtbar (heutiges DSM als Abgleich).
- **Zeitraffer:** 1958 bis heute in einer Minute.
- Schnittstelle zu `forest`/`tree` (welcher Baum steht wie) und zu `understory`.

### 5.7 `com.dan.understory` — Unterwuchs des Regenwalds

- Moospolster auf Boden, Stämmen und Ästen, Bartflechten, Farne (Schwertfarn), Teufelskeule, Beerensträucher, Totholz und umgestürzte Stämme mit Moos, Nadelstreu.
- Dichte aus Licht unter dem Kronendach und Feuchte; im Schadensband anders (frei, dann Pioniere).
- Schnittstelle zu `ground` (Boden) und `forest` (Licht).

### 5.8 `com.dan.scree` — Geröll und Blöcke

- Blöcke als Instanzen nach Korngrößenverteilung, Moränenwälle, Schuttkegel, Geröllstrände, Blockfelder; Formen aus wenigen Grundkörpern mit Verformung, Material aus `surface`.
- Platzierung nach Hang und Schüttwinkel; Ablagerung aus `landslide`.

### 5.9 `com.dan.vessel` — Motorboote und Kutter

- Rümpfe der 1950er Fischkutter (Troller, Seiner; Maße nach Recherche), Aufbau, Masten, Licht; Anker mit **Kette als Kettenlinie**, Schwojen um den Anker, Rollen und Stampfen über `buoyancy` auf `wave` und `ocean`, Kielwasser über `wake`.
- Kentern, Volllaufen, Sinken; Fahrt mit Motor und Ruder.
- Menschen an Bord über `figure`.

### 5.10 `com.dan.surface` — PBR-Materialien und Texturensätze *(passende Texturen)*

- Materialbibliothek: je Material Albedo, Normale, Rauheit, Umgebungsverdeckung und Höhe.
- **Ohne sichtbare Kachelung:** stochastische Kachelung (Hex-Tiling nach Mikkelsen bzw. Heitz und Neyret), triplanar an steilen Wänden, Makro-Variation aus Luftbild oder Rauschen.
- **Mischen über die Höhe** (Höhenmischung statt weicher Überblendung), Nässe (dunkler, glänzender), Schnee und Staub als Auflage.
- Komprimiert (BC7 für Farbe, BC5 für Normalen), Mipmaps, Budget im VRAM; Werkzeug zum Backen aus Asset-Paketen (Verallgemeinerung von `SandAsset`).
- Liefert die Materialien an `clipmap`, `cliff`, `ice`, `scree`, `forest` (Rinde), `vessel`.

### 5.11 `com.dan.clipmap` — Gelände bis zum Horizont

- **Geometrie-Clipmaps** um das Auge, gespeist aus Kacheln in `daten/` (Lidar 0,5 m im Kern, 5–10 m in der Szene, 30–460 m im Fern), nachgeladen in einem eigenen Faden.
- **Virtuelle Textur** für Material und Farbe, damit 0,5 m Detail und 250 km Fernsicht in 2 GB VRAM passen.
- Erdkrümmung und Refraktion am Horizont, Nähte ohne Risse, Höhen in NAVD88.
- Ersetzt auf Dauer die eigenen Geländeklassen der Szenen (Rückfluss nach Sylt und Timmendorf als Wunsch, nicht Pflicht).

### 5.12 `com.dan.netview` — Netze und Felder zeichnen

- Graphen, Wege, Ströme mit Pfeilen, Isolinien und **laufende Isochronen**, Strahlen, Messstrecken mit Werten, über Gelände und Wasser gelegt, in der Ferne ausgedünnt.
- Kennt keine Datenbank: Es bekommt Knoten, Kanten und Werte. Die App füllt es aus NDM oder aus `route`.
- Der „Netzblick“ der Szene (Abschnitt 7.9) und die Erklärbilder der Regiebücher.

### 5.13 `com.dan.aurora` — Polarlicht *(Zugabe)*

- Bögen, Vorhänge und Strahlen, Farben nach Höhe (Sauerstoff grün bei 557,7 nm und rot bei 630 nm, Stickstoff violett), Bewegung und Pulsieren, Stärke nach Kp-Index.
- In Lituya Bay nur im Zeitstand „heute“ in dunklen Monaten (Recherche).

### 5.14 `com.dan.bear` — Küstenbraunbär *(Zugabe)*

- Gang, Laufen, Graben, Fischen am Bach, Schnüffeln, Flucht; Fell im Wind und nass. Verhalten wie `herd` (Ziele, Abstand).

---

## 6. Bestehende Engines optimieren

Nach Paketvertrag Punkt 14: messen, vergleichen, alle Szenen gleich, alter Weg als Rückfall.

| Nr. | Package | Warum | Was | Abnahme |
|---|---|---|---|---|
| 6.1 | `wave` | Kern 24 × 18 km, Welle so hoch wie die Bucht tief, Auflauf an Hängen über 30° | **GPU-Rechnung** (Compute-Shader) neben der CPU-Rechnung; drei Gitter (grob über den Kern, mittel, fein um den Gilbert Inlet); Quelle mit **Impuls** aus `landslide`/`impact`; stabiler Nass-trocken-Rand an steilen Hängen; Stoßerfassung; Brechen der Front an `spray` | `WaveCheck` alt und neu gleich auf CPU und GPU; Laborfall Fritz 2001; Rungholt und Edersee-Demo unverändert; Zeit je Sekunde Welle vorher/nachher |
| 6.2 | `fracture` | Millionen Tonnen Fels | Übergabe an `landslide` nach dem Bruch statt Starrkörper für jedes Stück; mehr Zellen in der Ablösezone | Glacier-Kalbung und Sylt-Abbruch unverändert |
| 6.3 | `ice` | Gletscherfront 1958, Kalben durch Beben und Welle | Spalten, Seracs, Schuttbedeckung, blaues Eis in Spalten (Optik), Kalben über `fracture` und `impact` | Glacier unverändert |
| 6.4 | `forest`, `tree` | Millionen Bäume im Kern, Zustände je Baum | GPU-Kultur und Impostors (Oktaeder-Impostors aus mehreren Richtungen), Zustände aus `succession` (stehend, gebrochen, entrindet, fort), Sitka-Fichte und Hemlock als Arten, Wellenwurf mit Richtung | Bäume je Bild vorher/nachher; Haithabu, Timmendorf, Brocken unverändert |
| 6.5 | `ocean` | Wasser in der Bucht ist milchig türkis | **Gletschermilch:** Trübung und Farbe aus Schwebstoff (Streuung im Wasser), Übergang `ocean` ↔ `wave` am Eingang, Gezeitenstrom im Eingang | Farbe gegen Sentinel-2; andere Szenen unverändert (Trübung 0) |
| 6.6 | `sky`, `cloud`, `fog` | 58,6° N im Juli, Berge bis 4 671 m | helle Nacht (Dämmerung je nach Recherche), Alpenglühen, atmosphärische Perspektive über 60 km, Wolkenkappe und Wolkenbank an der Kette | Sonnenstand gegen Tabelle; andere Szenen unverändert |
| 6.7 | `soundscape` | Beben, Sturz, Welle, Wald | tiefe Frequenzen, Laufzeit des Schalls über Kilometer, Echo an den Wänden der Bucht | Hörproben |
| 6.8 | `kino` | episch | Zeitlupe bis 1:20, Zeitleiste der Ereignisse, Bild im Bild (Netzblick neben der Szene), Kamerafahrten mit Trägheit | Kino läuft durch |
| 6.9 | `route` | Zweitrechnung zu NDM | Gitternetze (4/8/16 Nachbarn), Laufzeitbaum mit mehreren Quellen, Isochronen, Messstrecke | `RouteCheck` mit Handrechnungen: L/√(g·h), Fehler des 16er-Gitters gegen den Kreis unter 2 % |
| 6.10 | GL-Engine (aus Timmendorf) | Fernsicht, große Schatten | Kaskadenschatten bis 60 km, Gelände über `clipmap`, Texturen über `surface` | fps-Tabelle je Qualitätsstufe |

---

## 7. Die Netze (Oracle NDM ausgereizt)

Präfix der Netze `LB_NET_` (F40). Alle Netze in UTM 8N mit Höhe, außer den logischen. Jedes Netz hat: Herkunft in `daten/netz/<netz>/`, Ladeskript, Prüfwege, Zweitrechnung, mindestens eine Frage, die es für die Szene beantwortet.

### 7.1 `LB_NET_WELLE` — Laufzeit der Welle *(Herzstück)*

- **Knoten:** Gitter über dem Wasser der Bucht (Vorschlag 20 m, F21), etwa 75 000 Knoten im Kern; **Kanten:** 16 Nachbarn, ungerichtet.
- **Kosten:** Laufzeit = Länge / √(g·h). Nach Fermat ist der kürzeste Weg die Ankunft der Front, der kürzeste-Wege-Baum ist die Wellenfront.
- **Mehrere Kosten** über `LinkCostCalculator`: `T_1940` (Grund vor der Welle), `T_1959` (nach der Welle), `T_HEUTE` (BlueTopo), `T_NICHTLINEAR` mit c = √(g·(h + a)), Amplitude a aus `wave`.
- **Quelle:** ein virtueller Quellknoten, mit Kosten 0 an alle Knoten der Einschlaglinie im Gilbert Inlet angebunden.
- **Analysen:** `withinCost` und kürzeste-Wege-Baum → Ankunftszeit je Knoten; Isochronen alle 10 s (Network Buffer oder eigene Konturen); `nearestNeighbors` von jedem Boot zur Front.
- **Groß genug für LOD:** Partitionen und Partition-BLOBs werden gebraucht, nicht nur angelegt.
- **Prüfung, dreifach:** NDM gegen `route` (gleiche Kosten, gleiches Ergebnis), NDM gegen `wave` (Ankunftszeit an Pegeln), beide gegen die Augenzeugen.
- **Für die Szene:** laufende Isochronen im Netzblick (`netview`), Wellenstrahlen, die Uhr im Kino, „Wie viel Zeit hatte jedes Boot?“.

### 7.2 `LB_NET_UFER` — das Ufer als Messstrecke (LRS)

- **LRS-Netz** (`SDO_NET.CREATE_LRS_NETWORK`): Uferlinie 1958 (Karte, Vermessung 1940) und heute (Lidar), Meter ab La Chaussee Spit, im Uhrzeigersinn um die Bucht und um Cenotaph Island.
- **Ereignisse auf der Strecke** (dynamische Segmentierung, `SDO_LRS`): Auflaufhöhen (NCEI, Miller), Höhe der Spurlinie, Breite des Schadensbands, Ablagerungen, Fotostandorte von Miller; Feature-Layer, wenn die Version es kann.
- **Für die Szene:** `succession` bekommt je Ufermeter die belegte Kante; Vergleich belegt gegen `wave.runupAt`.

### 7.3 `LB_NET_SEE` — Fahrwasser 1958 mit Gezeiten

- Wie `TD_NET_SEE`, aber **zeitabhängig:** Wassertiefe aus der Vermessung 1940 plus Pegel zur Uhrzeit (Vorausberechnung 9452928), Strom im Eingang; **Einschränkung** (`LODNetworkConstraint`) nach Tiefgang zur Zeit t.
- **Knoten:** Ankerplätze der drei Boote, Anchorage Cove, Eingang, offenes Meer.
- **Analysen:** Fluchtwege gegen die Ankunft der Welle aus 7.1 („Wer hätte entkommen können?“), *k* kürzeste Wege; La Pérouse 1786 als zweites Szenario.

### 7.4 `LB_NET_WASSER` — Gewässer und Gletscher (hierarchisch)

- **Zwei Ebenen:** Bäche aus dem Abfluss im Gelände; darüber Fließlinien der Gletscher (ITS_LIVE) und Einzugsgebiete als Elternknoten. Gerichtet stromab.
- **Analysen:** `traceIn` → Einzugsgebiete von Gilbert Inlet, Crillon Inlet, Fish Lake, Mudslide Creek; Fließzeit; Zusammenhangskomponenten.
- **Für die Szene:** Wasserführung von `fall` und `river`, Schutt und Kalben der Gletscher.

### 7.5 `LB_NET_WEG` — Wege über Gelände, mehrere Kosten, mehrere Modi

- **Hierarchisch:** fein (10 m) im Kern, grob (50 m) in der Szene; Gehzeit nach Tobler, Zuschläge aus WorldCover, Gletscher, Bäche; Umsteigekanten Boot → Strand zu `LB_NET_SEE`.
- **Analysen:** *k* kürzeste Wege, `withinCost` (eine Stunde vom Strand), Washburns Route 1934.
- **Groß:** Millionen Kanten → Partitionen, LOD, Speicher und Zeit gemessen.

### 7.6 `LB_NET_SICHT` — Sicht und Kamera

- **Knoten:** Aussichtspunkte, Gipfel, Ankerplätze, Kamerapunkte; **Kanten:** freie Sicht im Höhenmodell, Kosten = Flugzeit der Kamera.
- **Analysen:** TSP für einen Rundflug, minimaler Spannbaum für eine Kamerafahrt, `nearestNeighbors`. Speist die Regiebücher.

### 7.7 `LB_NET_HERKUNFT` — woher jede Zahl kommt (logisch)

- Quelle → Rohdatei → Aufbereitung → Befund → Szenenobjekt; Erreichbarkeit („Was hängt an H08492?“), Zusammenhangskomponenten (Befunde ohne Quelle = [G]). Verbindet sich mit `LB_QUELLE` und `LB_BEFUND`.
- Nach Projektabschluss der Nachweis, welche Datei auf der Wechselfestplatte wofür gebraucht wurde (2.3).

### 7.8 Was von NDM genutzt wird (Abnahme-Matrix im Werkbuch)

| Fähigkeit | Netz |
|---|---|
| räumliches Netz mit Höhe, gerichtet und ungerichtet | alle außer 7.7 |
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
| Feature-Layer | 7.2 (wenn vorhanden) |
| Partitionen, Partition-BLOBs, LOD | 7.1, 7.5 |
| `VALIDATE_NETWORK`, Prüfwege gegen `route` | alle |
| Property Graph mit SQL/PGQ | alle, nur wenn 23ai (F39) |

### 7.9 Netzblick

Bedienfeld NETZE: Isochronen laufen mit der Welle über das Wasser, Wellenstrahlen, Ufer mit Spurlinie und Auflaufhöhen, Gewässer, Wege, Sichtlinien, Herkunft einer angeklickten Zahl. Gezeichnet mit `netview`, im Kino als Bild im Bild.

---

## 8. Texturen

**Grundsatz:** Echte Texturen statt Farbe, wo das Auge nah ist; Luftbild und Klassen für die Ferne; nirgends sichtbare Kachelung.

**Quellen** (je Textur Herkunft und Lizenz, Paketvertrag Punkt 15):
1. **CC0-Bibliotheken** (Poly Haven, ambientCG): gescannte PBR-Sätze für Fels, Kies, Moos, Rinde, Schnee, Eis. Lizenz in Phase 0b prüfen.
2. **Eigene Assets** in `templates/` (wie `sand_dunes_gltf.zip` in Timmendorf), gebacken mit einem Werkzeug nach dem Muster von `Renderer/tools/beach/SandAsset.java`.
3. **Aus den Daten:** Sentinel-2 und Radarbild für die Makrofarbe und die Klassen, DSM für die Kronenhöhe, Lidar-Intensität (falls vorhanden) für Feinheiten am Ufer.
4. **Fotos von Miller 1958** (gemeinfrei) nur als Vorlage für Farbe und Zustand, nicht als Textur.

**Materialien** (Vorschlag, im Werkbuch nach Recherche festlegen):

| Material | wo | Hinweis |
|---|---|---|
| Fels der Fairweather-Kette, verwittert | Wände, Grate | Gestein recherchieren (3); Flechten, Nässestreifen |
| Fels, frischer Bruch | Rutschnarbe, abgeräumte Hänge | hell, scharfkantig; 1958 frisch, heute verwittert |
| Gletschereis | Fronten, Spalten | blau in Spalten, weiß an der Oberfläche, Schuttbänder |
| Firn und Schnee | Gipfel, Firnfelder | Wind gezeichnet, Sastrugi |
| Moräne, Schutt, Geröll | Gletschervorfeld, Kegel | mit `scree` |
| Strandkies und Gerölle | Ufer, La Chaussee Spit | nass und trocken |
| Sand und Schlick | Flachwasser, Fish Lake | Gletschermehl |
| Waldboden mit Nadeln und Moos | Regenwald | mit `understory` |
| Moos | Boden, Stämme, Steine | dichte Polster |
| Rinde Sitka-Fichte, Hemlock | Bäume | mit `forest` |
| entrindetes Holz | Stämme im Schadensband 1958 | hell, splittrig; Treibholz silbergrau |
| Seetang, Kelp | Ufer, Wasser | Spülsaum |
| Kutter: Holz, Farbe, Metall | `vessel` | 1950er |

**Budget:** VRAM 2 GB (Radeon 680M). Vorschlag: Texturen höchstens 600 MB, gestreamt über `clipmap`, BC7/BC5. Prüfung: Kontaktbogen `docs/status/Texturen.png`; Prüfbild „1 km Kiesstrand ohne Wiederholung“.

---

## 9. Die Szene (projektspezifisch, `com.dan.lituya`)

### 9.1 Zeitstände (F4)

- **vor:** Abend des 9. Juli 1958, Wald bis ans Ufer, Gletscherfront 1958, Grund 1940
- **die Welle:** Beben, Hangversagen, Felssturz, Eintauchen, Welle, die ersten zehn Minuten
- **danach:** Spurlinie, Holzteppiche, Staub in der Luft, Grund 1959, der Morgen
- **heute:** Lidar, BlueTopo 2025, junger Wald unter der Spurlinie, Gletscher heute

### 9.2 Vorgerechnetes Ereignis

Das Ereignis in voller Auflösung (Felssturz, Nahfeld, Welle in drei Gittern, Wellenwurf) ist auf der Radeon 680M voraussichtlich nicht in Echtzeit zu rechnen. Vorschlag (F10): **beides.** Das Ereignis wird in der Aufbereitung in hoher Auflösung **vorgerechnet** und nach `daten/ereignis/` geschrieben (Höhenfelder, Strom, Partikelbilder, Zustände der Bäume in Zeitschritten). Die App spielt es ab, frei mit der Kamera, in Zeitlupe und rückwärts. Dazu läuft eine **Echtzeit-Rechnung** in geringerer Auflösung für „Was wäre wenn“: andere Volumen, anderer Ort, andere Tide, Grund 1940 gegen heute.

### 9.3 Bauteile und Details

- **Gelände:** `clipmap` mit Lidar im Kern, IfSAR/3DEP in der Szene, Copernicus/GEBCO im Fern; Rückbau auf 1958 [G] an den Stellen, an denen sich seit 1958 etwas verändert hat (Gletscherfront, Rutschnarbe, Ufer).
- **Wasser:** `ocean` mit Gletschermilch draußen und in der Bucht, `wave` und `impact` im Ereignis, `surf` an der Barre im Eingang, `debris` auf dem Wasser.
- **Gletscher:** Lituya- und Nord-Crillon-Gletscher mit Front, Spalten, Seracs, Kalben.
- **Wald:** Sitka-Fichte und Hemlock in Millionen (`forest`), Unterwuchs (`understory`), Wellenwurf und Wiederbewaldung (`succession`).
- **Boden:** `surface` mit den Materialien aus Abschnitt 8, Geröll und Blöcke (`scree`).
- **Boote:** Edrie, Badger, Sunmore (`vessel`), vor Anker, mit Licht, Menschen an Bord.
- **Leben:** Möwen, Seehunde, Weißkopfseeadler, Küstenbraunbär am Ufer (`bear`).
- **Himmel:** helle Julinacht, Alpenglühen, Wolkenbank an der Kette; Polarlicht im Zeitstand „heute“ (`aurora`).

### 9.4 Schaustücke (Kamera-Presets und Regie)

1. **Abend vor Anker:** Anchorage Cove, Spiegel aus Gletschermilch, Mt Fairweather im Alpenglühen
2. **Das Beben:** die Bucht zittert, Lawinen lösen sich überall an den Hängen, Eis bricht von der Front
3. **Der Sturz:** die Wand des Gilbert Inlet löst sich, die Gesteinslawine, Staub im Gegenlicht
4. **Die Wand aus Wasser:** das Nahfeld, Krater und Wasserwand aus nächster Nähe, in Zeitlupe
5. **524 m:** die Welle steigt den Sporn hinauf, der Wald verschwindet vor ihr; ein Mensch und ein Baum als Maßstab
6. **Die Front:** Kamera vor der Front die Bucht hinab, Isochronen im Netzblick
7. **Unter Wasser:** die Front von unten, Geröll und Stämme im Strom
8. **Mit der Edrie:** aus dem Boot in Anchorage Cove, die Ankerkette reißt
9. **Über die Nehrung:** die Badger über La Chaussee Spit
10. **Der Morgen danach:** Spurlinie, Holzteppiche, Staub, nackter Fels, Stille
11. **68 Jahre:** Zeitraffer von 1958 bis heute, der Wald wächst nach, die Kante bleibt
12. **Heute bei Nacht:** Polarlicht über dem Fairweather, die Bucht ruhig
13. **Die Bucht der Wellen:** 1786, 1853/54, 1874, 1899, 1936, 1958 als Spurlinien am Wald
14. **Das Netz:** die Analysen als Bilder, von der Laufzeit bis zur Herkunft
15. Freie Kamera: Orbit, Zoom, Rundblick, Bodennähe, Boot, Drohne

### 9.5 Regiebücher (Vorschlag)

- **„Die Nacht der Welle“:** 9. Juli 1958 in Echtzeit, vom Abend vor Anker bis zum Morgen
- **„Zehn Minuten“:** die Ereigniskette in Zeitlupe, mit Netzblick als Bild im Bild
- **„Die drei Boote“:** Edrie, Badger, Sunmore, je aus ihrer Sicht
- **„68 Jahre“:** vom Morgen danach bis heute
- **„Die Bucht der Wellen“:** von La Pérouse bis heute

---

## 10. Erfahrungen aus bisherigen Werkbüchern (übernehmen)

Sylt Abschnitt 7 gilt vollständig (Werkbuch zuerst, Fragen mit A als Empfehlung, Statusbilder je Phase, `…Check` mit „Alles in Ordnung.“, Quellen belegt/gemessen/[G], Kino, Hörproben, GL-Engine aus der jüngsten Szene, FFrame und FStyle, Sequence + Trigger statt IDENTITY, Git ab Phase 1, Schliff mit README, Icon, Video und Dauerlauf, Leistung von Anfang an messen). Dazu aus Timmendorf Abschnitt 2.5 dieser Datei. Neu:

- **Jedes Netz hat seine Prüfwege und seine Zweitrechnung**, bevor die Szene es nutzt.
- **NDM-Funktionsprobe** zu Beginn (Abschnitt 3): Was die Version nicht kann, wird nicht geplant, sondern als Wunsch notiert.
- **Viele Packages heißt viele Abnahmen:** Je neues Package eine eigene Phase im Renderer mit Demo, `…Check`, README und Statusbild, bevor die Szene es nutzt.
- **Optimieren heißt messen:** Leistungstabelle vorher/nachher je Engine im Werkbuch (Abschnitt 6, Paketvertrag Punkt 14).
- **Leistung der Netze:** Ladezeit, Partitionen, LOD-Speicher, Zeit je Analyse.

---

## 11. Gliederung des Werkbuchs (zu erstellen)

1. Auftrag und Leitidee („Projekt der Riesenwelle und des Netzes“), was „episch“ heißt
2. Recherche und was daraus folgt
3. Daten: Gebiete, Kartenmaterial, Aufbereitung, **Regel „Kartenmaterial nur in Phase 2, nach Abschluss ausgelagert“**
4. Grundsatzentscheidungen (Renderer-Ablauf, Engine, Paketvertrag mit Punkt 13–15, Vorrechnung)
5. **Neue Packages** (14) mit Schnittstellen und Abhängigkeitsbild (die App in der Mitte, Packages ringsum ohne Querverbindungen)
6. **Optimierung bestehender Engines** mit Messplan
7. **Die Netze:** je Netz Frage, Daten, Modell, Kosten, Analysen, Prüfung; Abnahme-Matrix; NDM-Funktionsprobe
8. **Texturen:** Quellen, Materialien, Lizenzen, Budget
9. Physik in Kurzform: Formeln mit Quelle und [G]
10. Szene: Zeitstände, Gelände, Wasser, Gletscher, Wald, Boote, Leben, Himmel
11. Regie: Uhrzeit, Gezeiten, Wetter, Kamera-Presets, Regiebücher, Kino, Netzblick
12. Oberfläche (FFrame, Bedienfelder EREIGNIS, ZEIT, NETZE)
13. Datenbank: Schema, Netze, Quellen, Befunde, Zeugen, Szenarien
14. **Phasenplan** mit Lieferumfang und Abnahme, getrennt nach Renderer, App und Datenbank
15. Leistungsbudget (Bild, Ereignis, Netze) und Risiken
16. **Offene Fragen** (nummeriert, A/B/C)
17. Ideenspeicher
18. Rückfluss: welche neuen Packages und Optimierungen in welche alten Szenen gehen (Wünsche, keine Pflicht)
19. Fortschritt (Statusbilder, Freigaben, JAR-Stände, Netzstände, Datum)

---

## 12. Phasenplan — Entwurf (im Werkbuch verfeinern)

„R“ heißt Renderer, „L“ heißt LituyaBay1958, „D“ heißt Datenbank.

| Phase | Ort | Inhalt | Abnahme |
|---|---|---|---|
| 0 | L | **erledigt:** Gebiete, Kartenmaterial (48 Quellen; zweiter Lauf 1 227 Dateien, 13,5 GB), Werkzeug `tools/kartenmaterial` | `Kartenmaterial.md`, `download/INVENTAR.md` |
| 0b | L | Kartenmaterial auf dem eigenen Rechner laden, Fehlgeschlagenes berichtigen; Texturquellen sichten und Lizenzen prüfen | `INVENTAR.md` ohne Fehler, Texturliste |
| 1 | L | Grundgerüst: FFrame, GL-Engine aus Timmendorf, Kamera, HUD mit FPS, `wiring`, Startbild mit DB-Anmeldung; `sdonm.jar` und `lib/lod/` wie Timmendorf | leere Szene mit Horizont |
| 2 | L | Aufbereitung (`tools`): Gelände in Kacheln für `clipmap`, Grund 1940/1959/heute, Karten 1951/1961 georeferenziert, Klassen, Namen → `daten/` mit `INHALT.txt` | **Start mit umbenanntem `download/`** gelingt |
| 3 | D | Schema, Rechte (2.5), **NDM-Funktionsprobe**, Ladeskripte, `LB_NET_HERKUNFT` als Probe des Ablaufs | Funktionsliste belegt, `VALIDATE_NETWORK` grün |
| 4 | R | `clipmap` | Demo: Lidar-Kachel bis zum Horizont; Leistung |
| 5 | R | `surface` + Werkzeug zum Backen | Kontaktbogen, Kiesstrand ohne Wiederholung |
| 5a | L | Grundbild: Gelände, Texturen, Himmel (6.6), Wasser mit Gletschermilch (6.5) | „Abend vor Anker“ ohne Wald |
| 6 | R | `forest`/`tree` optimieren (6.4), `understory` | Millionen Bäume, fps-Tabelle; andere Szenen unverändert |
| 6a | L | Regenwald, Unterwuchs, Gletscher (6.3), `scree` (R) | „Abend vor Anker“ vollständig |
| 7 | R | `route` erweitern (6.9), `netview` | `RouteCheck`, Demo Isochronen |
| 7a | L+D | **`LB_NET_WELLE`** und **`LB_NET_UFER`**; Netzblick | gleiche Ankunftszeiten NDM ↔ `route`; Spurlinie je Ufermeter |
| 8 | R | `quake` | `QuakeCheck`, Demo |
| 9 | R | `landslide` (mit `fracture`, 6.2) | `LandslideCheck`, Demo: Lawine in ein Becken |
| 10 | R | `impact` | Laborfall Fritz 2001 |
| 11 | R | `wave` optimieren (6.1): GPU, drei Gitter, Impuls, steiler Auflauf | `WaveCheck` CPU = GPU; Rungholt unverändert |
| 12 | R | `dustcloud`, `debris` | je Check und Demo |
| 13 | L | **Das Ereignis:** Kette verdrahtet, Vorrechnung nach `daten/ereignis/`, Abspielen, Echtzeit „Was wäre wenn“ | „Der Sturz“, „Die Wand aus Wasser“, „524 m“, „Die Front“ |
| 13a | L+D | Vergleich `wave` ↔ `LB_NET_WELLE` ↔ Augenzeugen ↔ Miller | Tabelle der Ankunftszeiten und Spurlinien |
| 14 | R | `succession` | Demo: Band und Wiederbewaldung |
| 14a | L | Wellenwurf, „danach“ und „heute“, 68 Jahre | „Der Morgen danach“, „68 Jahre“ |
| 15 | R | `vessel` | Demo: Kutter vor Anker auf Welle |
| 15a | L+D | Boote in der Szene, **`LB_NET_SEE`** | „Mit der Edrie“, „Wer hätte entkommen können?“ |
| 16 | L+D | **`LB_NET_WASSER`**, **`LB_NET_WEG`** (groß, Partitionen) | Einzugsgebiete, Leistungstabelle |
| 17 | L+D | **`LB_NET_SICHT`**, Regiebücher, Kino (6.8) | Kino läuft durch |
| 18 | R+L | Klang (6.7), Wetter, Leben; Zugaben `aurora`, `bear` | Hörproben, „Heute bei Nacht“ |
| 19 | L+D | Oberfläche und Datenbank (eigener Fragenblock) | Tabellen freigegeben |
| 20 | L | Schliff: Qualitätsstufen, READMEs, GitHub-README, Icon, Verknüpfungen, Video, Dauerlauf; **Kartenmaterial ausgelagert, Start ohne `download/`** | „fertig“ |

---

## 13. Fragen-Kandidaten für das Werkbuch

*(Claude formuliert sie aus, ergänzt sie und setzt jeweils die Empfehlung als A.)*

| Nr. | Frage | A (Empfehlung) | B | C |
|---|---|---|---|---|
| F1 | Gebiete | Kern/Szene/Fern wie in 2.2 | Kern enger (nur die Bucht) | größer |
| F2 | Maßstab und Lage | echt, UTM 8N, Höhen NAVD88 | Lage frei | — |
| F3 | Höhenbezug | NAVD88 (GEOID12B), alte Vermessungen von MLLW umgerechnet | MLLW für Wasser, NAVD88 für Land | — |
| F4 | Zeitstände | vor, Welle, danach, heute; umschaltbar | nur 1958 | nur heute mit Rückblick |
| F5 | Grund 1958 | Vermessung 1940, abgeglichen mit Franco 2020 und 1959 | BlueTopo heute [G] | — |
| F6 | Engine | GL-Engine aus Timmendorf, ergänzt um `clipmap` und `surface` | neu | ohne `clipmap` |
| F7 | Kartenmaterial | in Phase 2 nach `daten/`, danach nie mehr gelesen; Auslagerung in Phase 20 geprüft | auch zur Laufzeit lesen | — |
| F8 | Neue Packages | alle 14 nach 5 (Zugaben `aurora`, `bear` zuletzt) | ohne Zugaben | nur die Ereigniskette |
| F9 | Optimierungen | alle aus 6, je mit Messung und Rückfall | nur `wave` und `forest` | keine |
| F10 | Ereignis | vorgerechnet in hoher Auflösung **und** Echtzeit in geringer Auflösung für „Was wäre wenn“ | nur vorgerechnet | nur Echtzeit |
| F11 | `wave` auf der GPU | Compute-Shader neben der CPU-Rechnung, gleiches Ergebnis geprüft | nur CPU, mehr Fäden | neue Engine |
| F12 | Nichtlinearität | Flachwasser mit Stoßerfassung, Dispersion nur prüfen | Boussinesq-Korrektur | linear |
| F13 | Nahfeld | `impact` mit Partikeln, Übergabe an `wave` | nur Volumenquelle in `wave` | gestaltet |
| F14 | Lawinenmodell | Savage-Hutter auf der GPU, Reibung nach Voellmy | µ(I) | Partikel |
| F15 | Beben | `quake` mit Newmark für Hangversagen | nur Kamerazittern und Auslöser | — |
| F16 | Wald | Millionen Bäume mit Impostors, Zustände aus `succession` | nur nahe Bäume echt, Ferne als Textur | — |
| F17 | Spurlinie | belegt (Ufer-LRS) und gerechnet (`wave`) nebeneinander, umschaltbar | nur belegt | nur gerechnet |
| F18 | Texturquellen | CC0-Bibliotheken + eigene Assets + Daten, je mit Lizenz | nur prozedural | nur eigene |
| F19 | Texturbudget | 600 MB, gestreamt, BC7/BC5 | 400 MB | 1 GB |
| F20 | Gletschermilch | Trübung und Farbe aus Schwebstoff in `ocean`, gegen Sentinel-2 geeicht | feste Farbe | — |
| F21 | Gitter `LB_NET_WELLE` | 20 m, 16 Nachbarn | 10 m, 8 Nachbarn | 50 m |
| F22 | Kosten im Wellennetz | vier Kosten über `LinkCostCalculator` in einem Netz | je Zeitstand ein Netz | nur linear heute |
| F23 | Isochronen | Network Buffer, wenn vorhanden; sonst Konturen | immer Konturen | — |
| F24 | Netze im Projekt | alle sieben nach Phasenplan | nur Welle, Ufer, See | nur Welle |
| F25 | Datei-Abbild | `daten/netz/` wie Timmendorf, App läuft ohne DB | nur Oracle | nur Datei |
| F26 | LOD und Treiber | wie Timmendorf: eigener Klassenlader, Treiber 21 in `lib/lod/` | neueres `sdonm.jar` suchen | PL/SQL statt LOD |
| F27 | Boote | drei Boote nach Miller und Zeugen, Positionen [G] wo unbelegt | ohne Boote | nur die Edrie |
| F28 | Menschen | an Bord sichtbar, klein, ohne Gesicht (`figure`) | keine | — |
| F29 | La Pérouse 1786 | zweites Szenario im Seenetz und im Regiebuch „Die Bucht der Wellen“ | Ideenspeicher | weglassen |
| F30 | Tiere | Möwen, Seehunde, Adler, Bär (`bear`) | ohne Bär | nur Möwen |
| F31 | Washburn 1934 | Route im Wegenetz, falls belegt | frei | weglassen |
| F32 | Gletscher 1958 | Front aus Karte 1951 und C-5, Miller | heutige Front | — |
| F33 | Zeitlupe | bis 1:20 im Kino, frei mit der Kamera im vorgerechneten Ereignis | nur Echtzeit | — |
| F34 | Unterwasser | Front von unten mit Geröll und Stämmen | weglassen | — |
| F35 | Polarlicht | `aurora` im Zeitstand „heute“, wenn die Recherche es für die Monate belegt | immer wählbar [G] | weglassen |
| F36 | Ton | erzeugt, ohne Audiodateien, mit tiefen Frequenzen | keiner | Dateien |
| F37 | Leistungsziel | Hoch ≥ 30 fps Full HD, Mittel 60 fps; Ereignis abgespielt in Hoch ≥ 30 fps; Netzanalysen außerhalb des Bildfadens | nur 30 fps | frei |
| F38 | Netzblick | Bedienfeld NETZE mit allen Ebenen, im Kino als Bild im Bild | nur Isochronen | keiner |
| F39 | Property Graph | nur wenn die Datenbank 23ai ist: SQL/PGQ-Sichten auf die Netze | nicht nutzen | Upgrade planen |
| F40 | Schema | `LITUYA`, Präfix `LB_`, Netze `LB_NET_*` | `LITUYABAY1958` | DEMO |
| F41 | SRID | 26908 (NAD83 / UTM 8N), 4326 für Austausch über Sichten | 32608 | — |
| F42 | Rückfluss | Wünsche an alte Szenen (z. B. `clipmap` und `surface` nach Sylt und Timmendorf) als Liste, keine Pflicht | Rückfluss als Phase | keiner |
| F43 | Programmsymbol | `.ico` mit Goldkante wie `heidelberg.ico`, Motiv Spurlinie über der Bucht | Motiv Welle | — |

---

## 14. Datenbank-Ideen (für Phase 19, noch nicht ausarbeiten)

- `LB_QUELLE`, `LB_BEFUND`, `LB_BEFUND_QUELLE` wie in Sylt; dazu `LB_NET_HERKUNFT` (7.7)
- `LB_ZEUGE`: Aussagen der Überlebenden mit Zeit, Ort und Quelle; Prüfpunkte für 7.1 und 13a
- `LB_AUFLAUF`: Auflaufhöhen (NCEI, Miller) als Punkte und als Ereignisse auf `LB_NET_UFER`
- `LB_SPURLINIE`: Spurlinie je Stand (Miller 1958, Rechnung, Lidar heute) als `SDO_GEOMETRY`
- `LB_ISOCHRONE`: Isochronen je Kostenart als Polygone
- `LB_BOOT`, `LB_BOOT_LAGE`: Boote mit Ankerplatz und Bahn
- `LB_EREIGNIS_LAUF`: vorgerechnete Läufe und Echtzeitläufe mit Eingaben (Volumen, Ort, Tide, Grund) und Kennzahlen (Auflauf, Ankunftszeiten)
- `LB_NETZ_STAND`: Prüfsumme je Netz gegen das Datei-Abbild (wie `TD_NETZ_STAND`)
- `LB_TEXTUR`: Texturen mit Herkunft und Lizenz
- `LB_SZENARIO` / `LB_SZENARIO_SCHRITT`: Regiebücher; `LB_KAMERA_FAHRT` aus `LB_NET_SICHT`

---

## 15. Ideenspeicher (eingebracht von Claude)

- **Rückwärts rechnen:** Aus den Zeiten der Zeugen an mehreren Orten auf die Quelle schließen (`withinCost` von den Booten aus, Schnitt der Isochronen). Trifft es den Gilbert Inlet?
- **Wellenstrahlen:** Die kürzesten Wege im Wellennetz zeigen, wie die Front sich an Cenotaph Island teilt und dahinter wieder schließt.
- **Die Uhr der Welle:** Im Kino eine Laufzeituhr je Ort aus dem Netz.
- **1940 gegen heute:** Dieselbe Welle auf dem Grund von heute; um wie viel früher oder später käme sie an?
- **Was wäre wenn:** Halbes oder doppeltes Volumen, Felssturz an der Crillon-Seite, Flut statt Ebbe; die Spurlinie wandert im Wald mit.
- **Frühere Wellen:** Spurlinien von 1853/54, 1874, 1899 und 1936 als Ereignisse auf dem Ufer-LRS, so wie Miller sie am Wald gelesen hat.
- **Der Wald als Kalender:** Höhe des Nachwuchses unter der Spurlinie aus den ArcticDEM-Streifen 2013–2022.
- **Maßstabsfiguren:** Im Schaustück „524 m“ wahlweise der Kölner Dom, die Freiheitsstatue oder der Kampener Leuchtturm neben der Welle.
- **Wege der Tlingit:** Namen und Wege als eigene Ebene im Wegenetz, wenn belegt.
- **Herkunft im Bild:** Klick auf eine Wassertiefe zeigt den Weg im Herkunftsnetz bis zur Vermessung.

---

## 16. Arbeitsanweisung an Claude

1. **Projektordner sichten:** LituyaBay1958 (`Kartenmaterial.md`, `download/INVENTAR.md`, `tools/kartenmaterial/`, `geodaten/`), Renderer (READMEs der Packages in 2.4, `muster`, `tools/PaketPruefung.java`, `tools/beach/SandAsset.java`), Timmendorf (`db/netz/*.sql`, `tools/NetzDb.java`, `data/NetzOracle.java`, GL-Engine, `docs/WERDEGANG.md`), Sylt (Werkbuch-Aufbau, `QUELLEN.md`).
2. **Bilder ansehen:** `images/1958_Lituya_Bay1.jpg`, `images/Gebiete_*.jpg`, `images/Kern_BlueTopo.png`.
3. **Recherche** nach Abschnitt 3; alles mit Quelle in die Fragen und in „Physik in Kurzform“.
4. Das **„LituyaBay1958 Werkbuch“** als Artefakt anlegen, nach der Gliederung in Abschnitt 11, mit Paketvertrag (15 Punkte), **14 neuen Packages**, **Optimierungsplan mit Messung**, **Netzkatalog mit Abnahme-Matrix**, **Texturplan**, Phasenplan und **nummerierten Fragen** (A = Empfehlung).
5. **Keinen Code und keine Skripte schreiben**, bevor die Fragen beantwortet sind und Phase 1 freigegeben ist.
6. Nach jeder Phase das Werkbuch fortschreiben: Statusbild, Stand von `Renderer.jar`, Leistung vorher/nachher, Stand jedes Netzes (Knoten, Kanten, Partitionen, Prüfwege, Zeiten).
7. Regel aus 2.3 in jeder Phase: Kartenmaterial liest nur die Aufbereitung. Neue Rohdaten kommen über den Katalog und die Aufbereitung, nie über den Laufzeitcode.
