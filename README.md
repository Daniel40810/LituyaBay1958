# LituyaBay1958 – das Projekt der Riesenwelle und des Netzes

Lituya Bay, Alaska, 9. Juli 1958, 22:16 Uhr Ortszeit: Das Beben an der Fairweather-Störung löst einen Felssturz von 30,6 Mio. m³ in den Gilbert Inlet aus, die Welle läuft am Sporn gegenüber bis 524 m hinauf und die 12 km lange Bucht hinab bis zu den drei Kuttern am Eingang. Eine animierte 3D-Echtzeitszene der Bucht vor, während und nach der Welle und heute, mit der Ereigniskette als Physik in neuen Packages des Projekts Renderer und sieben Netzen im Oracle Network Data Model. Plan und Fortschritt stehen im Werkbuch `docs/claude/LITUYABAY1958_WERKBUCH.md`, die Statusbilder in `docs/status/`.

**Stand: Phase 3 von 20, Schema und NDM-Funktionsprobe** (01.11.2026). Schema ist der vorhandene Benutzer `LITUYABAY1958` (F40 B), Präfix `LB_`. Die Skripte in `db/netz/` (00 Rechte als SYS, 01 Tabellen, 02 Netze, 03 Funktionsprobe, 04 Partitionen, 05 Sichten, 98 und 99 Abbau) lädt `ant netzdb`: zuerst `LB_NET_HERKUNFT`, das Herkunftsnetz (je Rohdatei aus `download/inventar.csv` ein Knoten, je Datei in `daten/` eine Ausgabe, dazu Quellen, Schritte, Szenenobjekte und Befunde; Dateien in `daten/netz/herkunft/` aus dem Schritt `herkunft` der Aufbereitung). Die Funktionsprobe (Werkbuch 2.8) prüft in SQL und mit LOD (Treiber 21 im eigenen Klassenlader, eigene Kostenfunktion und Netzbedingung) auf Probenetzen, die danach wieder verschwinden; das Ergebnis steht in `LB_NDM_PROBE`. Gegenrechnung: `VALIDATE_NETWORK`, CONNECT BY und `traceIn` gegen `pruefwege.tsv`; Prüfsumme in `LB_NETZ_STAND`.

**Phase 2, die Aufbereitung** (01.11.2026). `ant aufbereitung` liest das Kartenmaterial einmal und schreibt nach `daten/`: das Gelände in drei Stufen (Kern 0,5 m aus dem Lidar 2019, Szene 5 m aus IfSAR 2012, Fern 30 m aus 3DEP und Copernicus, im Wasser USACE 2019, BlueTopo und GEBCO; Kacheln zu 2000² als `.r32`, Format in `scene.Stufe`), den Grund der Bucht heute (4 m, mit Wassermaske) und 1926, 1940, 1959 aus den Lotungen der NOS (20 m, Delaunay), die Namen aus GNIS und `INHALT.txt` mit SHA-256 je Datei; Prüfbilder nach `docs/status/`. Noch offen aus 3.4: `rueckbau1958`, `spurlinie`, `klassen`, `wald`, `gletscher` (siehe Werkbuch 19).

**Phase 1, das Gerüst** (01.11.2026). Die GL-Engine aus Timmendorf, entschlackt (F6 A): FFrame mit GL-Fläche, Startbild mit Anmeldung an die Datenbank, Bedienfeld mit allen Blöcken des Werkbuchs (ANSICHT, ZEIT, EREIGNIS, NETZE, WETTER, REGIEBÜCHER, DATEN, TECHNIK), HUD mit der Zeit jedes Durchgangs auf der Grafikkarte und den drei Uhren (Weltzeit, Simulationszeit, Zeitstand), Qualitätsstufen, Foto, Shader neu laden, Faden der Physik, Kinomodus als Anschluss. Noch kein Gelände: das Wasser mit Erdkrümmung reicht bis zur Kimm, darauf die Marken (UTM-Raster, Kern, Szene, Fern, die Orte aus GNIS). Neun Ansichten von Anchorage Cove bis zum Fern. `sdonm.jar` in `lib/`, der Treiber 21 für LOD in `lib/lod/` (F26 A).

## Bauen und starten

- NetBeans-Projekt mit Ant, JDK 21. In `lib/`: `Renderer.jar` (aus dem Projekt Renderer, nicht im Git), JOGL mit Windows-Natives, `FStyle.jar`, `ojdbc11.jar`, `sdoapi.jar`, `sdogr.jar`, `sdoutl.jar`, `sdonm.jar`.
- **Clean and Build**, dann **Run** (Hauptklasse `com.dan.lituya.app.LituyaApp`). Voraussetzung: OpenGL 4.3.
- Nach einer Änderung im Projekt Renderer: dort „Clean and Build“, dann `dist/Renderer.jar` nach `lib/` kopieren; `check-packages` meldet, wenn etwas fehlt.
- **Prüfung:** `ant pruefung` endet mit „ERGEBNIS: alles in Ordnung“ (ohne Grafikkarte und Datenbank: Raum, Gebiete und Orte, Uhren und Sonne zur Bebenzeit, Ansichten und Kamera, Renderer.jar, Shader, `sdonm.jar` und `lib/lod`, `daten/`, die Skripte in `db/netz`, LOD auf dem Probenetz im Speicher, das Herkunftsnetz, Regel 3.3 und Paketvertrag 13).
- **Statusbild:** `ant statusbild` schreibt `docs/status/Status_Phase1.png` (Grundansicht, Full HD, Stufe Mittel, volles HUD mit den Zeiten); `-Dansicht=AnchorageCove` usw. wählt eine Ansicht.
- **Aufbereitung:** `ant aufbereitung` (alle Schritte; Dauer auf dem Ryzen geschätzt 10 bis 30 min, -Xmx24g), `-Dschritte=gelaende,grund`, `-Dneu=true`, `-Dausschnitt=OstMin,NordMin,OstMax,NordMax`; Protokoll `daten/aufbereitung.log`. Der Pfad des Kartenmaterials steht in `daten/rohdaten.properties`.
- **Netze in Oracle (Phase 3):** einmal als SYS in SQL Developer `db/netz/00_sys.sql` (F5); dann `ant aufbereitung -Dschritte=herkunft,inhalt` und `ant netzdb` (Protokoll `docs/status/netzdb_Phase3.txt`); `ant dbpruefung` prüft später nur, ob Oracle und `daten/netz/` zusammenpassen.
- **LOD:** `ant lod` holt `ojdbc11.jar` aus `ORACLE_HOME\jdbc\lib` nach `lib/lod/` und lädt die LOD-Klassen im eigenen Klassenlader (gebraucht von `ant netzdb`).
- **Datenbank:** Zugang in `db/db.properties` (Vorlage `db/db.properties.example`); ist das Passwort leer, fragt das Startbild danach und trägt es ein (oder Umgebungsvariable `LituyaBay1958_DB_PASSWORD`). `db/db.properties` steht in `.gitignore`.
- **Tasten:** F1 zeigt alle. Maus ziehen dreht und verschiebt, Rad zoomt, W A S D gleitet, F fliegt frei, R kreist, 1 bis 9 die Ansichten, H das HUD, F11 nur die Szene, F12 ein Foto nach `docs/status/`, F5 lädt die Shader neu, Leertaste hält die Uhr an.

## Raum und Zeit

UTM Zone 8 Nord (NAD83, EPSG:26908), Höhen über NAVD88, Ursprung in der Mitte des Kerns bei E 352 000, N 6 503 000 [G]: x nach Osten, z nach Süden (Nord = −z), y nach oben. Kern 24 × 18 km (E 340 000–364 000, N 6 494 000–6 512 000), Szene 66 × 60 km, Fern 260 × 240 km (`geodaten/gebiete.geojson`). Ortszeit 1958 UTC−8 (PST): der erste Stoß um 22:16 ist der 10.7.1958 06:16 UTC; Sonnenuntergang am 9.7.1958 um 22:14, beim Stoß stand die Sonne rund 1° unter der Kimm.

## Daten

Das Kartenmaterial (`Kartenmaterial.md`, 48 Quellen) lädt `tools/kartenmaterial` nach `download/`; nur die Aufbereitung in Phase 2 liest es und schreibt nach `daten/`. Die App liest nie aus `download/` (Werkbuch 3.3).
