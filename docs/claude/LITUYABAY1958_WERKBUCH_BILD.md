# LituyaBay1958 Werkbuch Bild

**Die Bucht, wie man sie nicht vergisst.** Das zweite Werkbuch des Projekts. Es regelt nur das, was man sieht: Licht, Luft, Wasser, Fels, Eis, Wald, die Welle als Bild, die Kamera und die Inszenierung.

> **Stand: 03.11.2026. Entwurf, wartet auf die Antworten zu den Fragen FB1–FB20 (Abschnitt 14) und die Freigabe „PhaseB0, los“.**
> Das erste Werkbuch (`LITUYABAY1958_WERKBUCH.md`) bleibt gültig für Physik, Netze, Datenbank und Werkzeuge; es wird nicht umgeschrieben. Wo dieses Werkbuch etwas daraus aufnimmt (6.5 Gletschermilch, 6.6 Himmel, 6.10 Schatten, Phase 5a, 6a, 18b, 18c), steht der Verweis dabei, und die Arbeit zählt hier.
> Regeln wie im ersten Werkbuch: Jede Zahl ist **belegt**, **gemessen**, **gerechnet** oder **gestaltet [G]**; was noch keine Quelle hat, trägt **[zu prüfen]**. Neu ist die vierte Regel: **Jede Phase wird mit Bildern abgenommen, nicht mit Zahlen allein** (Abschnitt 3).

---

## Inhalt

0. Anlass
1. Befund: Warum das Bild nicht trägt
2. Leitbild: Was das Bild erzählen soll
3. Ziele und wie sie gemessen werden
4. Fundament: Licht, Schatten, Kamera, Bildkette
5. Die Elemente der Szene
6. Die Welle als Bild *(Herzstück)*
7. Die Narbe *(Blickfang)*
8. Inszenierung
9. Oberfläche und Marken
10. Neue und erweiterte Packages
11. Leistungsbudget und Qualitätsstufen
12. Phasenplan
13. Risiken
14. Fragen und Entscheidungen
15. Ideenspeicher
16. Rückfluss in die anderen Szenen
17. Fortschritt

---

## 0. Anlass

Rückmeldung zu Version 1.0 (03.11.2026), wörtlich:

> Hud aus, Marken aus beim Starten der App. Schließt sich an Gesamteindruck des Projects an. Es wirkt lieblos und unkreativ. Bei den Daten, die zur Verfügung standen, nicht viel herausgeholt. Ästhetik war hier nicht dein Schwerpunkt. War keine Weiterentwicklung zu vorherigen Projekten. Mit der Landschaft hätte man einen richtigen Eyecatcher bauen können. Schade!!!

Die Kritik trifft zu. Zwanzig Phasen lang galten Physik, Netze, Datenbank und Werkzeuge als Arbeit. Das Bild war nie eine eigene Aufgabe und wurde nie mit Bildern abgenommen. Dieses Werkbuch macht es zur Hauptsache.

**Auftrag:** Aus der Lituya Bay das schönste Bild aller Szenenprojekte machen, ohne die Wahrheit der Daten aufzugeben. Wer die App startet, soll in den ersten zehn Sekunden sehen, warum dieser Ort und diese Nacht besonders sind.

---

## 1. Befund: Warum das Bild nicht trägt

### 1.1 Was im Code steht

Die Bildebene ist auf dem Stand von Phase 1 geblieben. Drei der vier großen Flächen sind ausdrücklich Platzhalter, und Phase 5a („Grundbild: Gelände, Texturen, Himmel, Wasser mit Gletschermilch“, erstes Werkbuch Zeile 1072) fehlt im Fortschritt: Auf Phase 5 folgt Phase 6.

| Ebene | Stand 1.0 | Datei | Folge im Bild |
|---|---|---|---|
| Himmel | Farbverlauf nach der Sonnenhöhe, 9 Zeilen, „Platzhalter bis com.dan.sky in Phase 5a“ | `gl/shader/sky.frag`, `common.glsl` | keine Dämmerung, kein Alpenglühen, kein Erdschatten, keine Wolken |
| Wasser | Wertrauschen in zwei Lagen, Himmel nach Fresnel gespiegelt, feste Farbe „fjordgrün“, „Platzhalter bis ocean mit Gletschermilch in Phase 5a“ | `meer.frag` | wirkt wie verwaschenes Rauschen, spiegelt keine Berge, im Licht wie Schnee |
| Gelände | Farbe nur aus Höhe und Hang (sechs feste Farben), Lambert, „Platzhalter (Phase 4, bis surface in Phase 5 und Grundbild in 5a)“ | `gelaende.frag` | Berge wie Teig, keine Felsform, kein Material, kein Schnee mit Form |
| Schatten | keine: weder Gelände noch Wald werfen Schatten (6.10 „Kaskadenschatten bis 60 km“ nicht gebaut) | – | 4 671 m wirken wie 800 m; tiefe Sonne ohne Wirkung |
| Texturen | 13 CC0-Sätze von ambientCG gebacken (Phase 5), `surface` mit Hex-Kachelung gebaut – **nie aufs Gelände gelegt** | `daten/texturen`, `Renderer/surface` | – |
| Makrofarbe | Schritt `klassen` liefert Klassen (16a), aber keine Farbe aus Sentinel-2 | – | Hänge einfarbig |
| Gletscher | Schneefarbe über 900 m, sonst nichts | `gelaende.frag` | Lituya-, Crillon-, Cascade-Gletscher unsichtbar |
| Welle | Wasser wie `meer.frag`, Gischt als weiße Flecken auf steilen Flanken | `welle.frag` | die Front ist aus 2 km ein Strich |
| Nachbearbeitung | ACES nach Narkowicz, Vignette, Rauschen gegen Stufen | `post.frag` | kein Bloom, keine Kantenglättung, keine Farbabstimmung |
| Kamera | senkrecht 55° Bildwinkel (`Camera.FOVY`), rund 23 mm Kleinbild-Brennweite, für alle Ansichten | `camera/Camera.java` | Weitwinkel staucht die Berge, alle Bilder sehen gleich aus |
| Start | HUD kurz und Marken an (behoben am 03.11.2026) | `Renderer.java`, `ControlPanel.java` | erster Eindruck: Messgerät statt Landschaft |

### 1.2 Was die README-Bilder zeigen

| Bild | Was fehlt |
|---|---|
| Abend vor Anker | Das Wasser spiegelt die Berge nicht, die Gipfel glühen nicht, der Himmel ist leer. Die Kette wirkt wie Hügel in 10 km, nicht wie 4 000 m in 25 km. |
| Die Wand aus Wasser | Die Rutschnarbe ist eine glatte rosa Fläche, der Staub ein Punktnebel. Keine Wasserwand zu sehen. |
| 524 m | Der Sporn ist eine schwarze Masse, das Wasser ein grobes Muster. Der Auflauf ist nicht zu erkennen. |
| Die Front | Die Welle ist ein dünner Strich mit bunten Punkten (Marken) am Horizont. |
| Netzblick | Bunte Linien über einem Bild, das selbst nichts erzählt. |
| Der Morgen danach | Das Band ist richtig gerechnet, aber hellblau-grau wie Schnee. Das Wasser wirkt wie ein Schneefeld. |
| 68 Jahre | Gegenlicht ohne Wirkung, Berge ohne Form. |

### 1.3 Ursachen

1. **Phase 5a übersprungen.** Danach hat jede Phase auf den Platzhaltern weitergebaut („Licht aus dem Platzhalter-Himmel“, Phase 14a).
2. **Abnahmen in Zahlen.** 524 m am Sporn wurden auf den Meter geprüft, das Bild davon nie angesehen.
3. **Die Bibliothek nicht genutzt.** Der Renderer hat einen Himmel nach Hillaire 2020 mit Mehrfachstreuung (`sky`), Volumenwolken mit Silberrand (`cloud`), ein FFT-Meer mit Schaum (`ocean`), Fels mit Feinform und Klüften (`cliff`), Eis mit Absorption je Farbkanal (`ice`), Nebelbänke (`fog`), Gischt (`spray`), Brecher (`surf`) und Verwitterung (`weathering`). Lituya zeichnet mit weniger davon als Geiranger oder Sylt. Das ist der Rückschritt, den die Kritik meint.
4. **Kein Leitbild.** Es gab nie eine Antwort auf die Frage, wie das Bild aussehen soll.

---

## 2. Leitbild: Was das Bild erzählen soll

### 2.1 Der Satz

**Eine stille, goldene Sommernacht in der schönsten Bucht Alaskas, und dann das Unvorstellbare.** Das Bild lebt vom Gegensatz: zuerst Frieden, Spiegel, Glühen; dann Staub, Gischt, Gewalt; dann ein graues, kahles Morgenlicht; heute wieder grün, aber mit der Narbe.

### 2.2 Bildregeln (verbindlich)

1. **Maßstab zeigen.** In jedem Schaustück ein Ding bekannter Größe: Kutter (rund 12 m [zu prüfen, 2.3 im ersten Werkbuch]), Baum (40–60 m), Mensch, Möwe. Die Berge erst neben etwas Kleinem groß.
2. **Drei Ebenen.** Vordergrund (Ufer, Stamm, Bug), Mittelgrund (Bucht, Welle), Hintergrund (Kette). Luftperspektive trennt sie.
3. **Licht vor Farbe.** Die Form kommt aus Licht und Schatten, nicht aus bunten Flächen. Schatten der Berge über dem Fjord sind Pflicht.
4. **Ruhige Flächen, dichte Stellen.** Spiegelwasser und Himmel dürfen leer sein; dafür sind Narbe, Front und Gletscherfront dicht und scharf.
5. **Silhouetten.** Gipfel, Baumränder, Kutter gegen den hellen Himmel lesbar.
6. **Kein Messgerät im Bild.** HUD, Marken und Netze nur auf Wunsch, und dann gestaltet (Abschnitt 9).
7. **Wahr bleiben.** Farben aus Sentinel-2 und Fotos, Sonne aus 9.10, Formen aus dem Lidar. Gestaltetes trägt [G] im Code und im Werkbuch; nichts wird übertrieben, was belegt anders war.

### 2.3 Vorbilder (nur für Stimmung und Licht, nichts wird nachgebaut)

- **Fotos von D. J. Miller 1958** (USGS, gemeinfrei): Narbe, Spurlinie, Treibholz, Zustand des Walds.
- **Heutige Fotos aus Glacier Bay National Park** (NPS, gemeinfrei): Farbe der Gletschermilch, Nebel in den Hängen, die hellgrüne Narbe im dunklen Wald.
- **Luminismus und alaskische Landschaftsmalerei** (19./frühes 20. Jh.): goldenes Licht auf Schnee, dunkle Wälder, Spiegelwasser. Als Haltung, nicht als Vorlage.
- **Naturfilm:** lange Brennweiten, ruhige Kamera, Zeitlupe nur am Höhepunkt.

### 2.4 Farbskript je Zeitstand

| Zeitstand | Licht | Himmel | Wasser | Land | Grundton [G] |
|---|---|---|---|---|---|
| **vor**, 20:00–22:15 | Sonne tief im NW, ab 22:04 unter der Kimm (9.10); Alpenglühen auf Fairweather und Crillon | Gold an der Kimm, Rosa im Venusgürtel, Erdschatten im SO | Spiegel, Türkis-Milch, Gipfel rosa gespiegelt | Wald fast schwarz, Fels im Schatten blau | warm oben, kühl unten |
| **die Welle**, 22:16–22:30 | Dämmerung; Staub im Gegenlicht leuchtet orange | wie vor, dann Staubschleier | dunkel, braun von Schlamm, weiße Front | Hänge zittern, Lawinen weiß | Kontrast hart, Schwarz-Weiß-Orange |
| **danach**, 10.7. Morgen | flach, grau, Sonne aus NO [zu prüfen: Bedeckung, ERA5] | blass, Dunst | grau-braun, Holzteppiche | Band hell-ocker bis 524 m, darüber unberührt dunkelgrün | entsättigt, kalt |
| **heute**, Sommer | wechselnd: Sonne, Regen, Nebel in den Hängen | Wolkenbänke an der Kette | Milch-Türkis | Band hellgrün (Erle, junge Fichte), darüber dunkel | satt, grün, feucht |
| **heute bei Nacht**, Herbst | Mond, Polarlicht (18b) | Sterne, grünes Band | schwarz, Spiegel der Aurora | Silhouetten | blau-grün |

---

## 3. Ziele und wie sie gemessen werden

### 3.1 Bildbank (neu, Grundlage jeder Abnahme)

Zwölf feste Ansichten (Kamera, Brennweite, Uhr, Zeitstand, Wetter) als `scene/bildbank.txt`, geschrieben mit `ant bildbank` nach `docs/bild/<Phase>/<Nr>_<Name>.png`, 1920 × 1080 und 3840 × 2160, ohne HUD:

| Nr | Ansicht | Zeitstand | Warum |
|---|---|---|---|
| 1 | Anchorage Cove, Blick zum T-Kopf, 50 mm | vor, 22:00 | Spiegel, Alpenglühen, Kutter |
| 2 | Fairweather vom Eingang, 200 mm | vor, 22:10 | Maßstab, Luftperspektive, Schnee |
| 3 | Gilbert Inlet von Süden, 35 mm | vor, 22:15 | Gletscherfront, Wand, Eis im Wasser |
| 4 | Der Sturz, 85 mm | Welle, 93–100 s | Fels, Staub im Gegenlicht |
| 5 | Die Wand aus Wasser, 24 mm, Zeitlupe | Welle, 104 s | Krone, Gischt, Licht durch das Wasser |
| 6 | 524 m, 135 mm | Welle, 106 s | Auflauf, Wald verschwindet |
| 7 | Die Front vor Cenotaph Island, 50 mm, aus Bootshöhe | Welle, 200 s | Wasserwand mit Treibgut |
| 8 | Unter Wasser | Welle, 115 s | Trübe, Licht, Stämme |
| 9 | Der Morgen danach, Hubschrauber 35 mm | danach | Narbe, Holzteppich |
| 10 | Millers Standort (Foto 1958) | danach | Abgleich mit dem Foto |
| 11 | Narbe heute, 85 mm | heute, Sonne | hellgrünes Band |
| 12 | Heute im Regen, Nebel in den Hängen, 35 mm | heute, Regen | Wetter, Stimmung |

### 3.2 Was als „besser“ zählt

| Ziel | Maß | Schwelle |
|---|---|---|
| Farben wahr | ΔE2000 Wasser, Wald, Fels, Schnee gegen Sentinel-2 L2A (Mittel über Flächen, gleiche Sonnenhöhe) | ≤ 8 [G] |
| Narbe wie auf dem Foto | Bild 10 neben Millers Foto, Kante und Helligkeit des Bands | Blickprüfung, Kante auf ± 20 m |
| Keine Kachelung | Autokorrelation wie `SurfaceCheck` auf Fels, Kies, Schnee | < 0,3 |
| Kein Flimmern | zeitliche Varianz je Bildpunkt bei ruhender Kamera (Wald, Wasser, Schnee) | < 2 % der Helligkeit [G] |
| Tiefe | Kontrast Vordergrund zu Kette (Luftperspektive), gemessen im Bild | Kette ≤ 50 % des Kontrasts vorn [G] |
| Tempo | `ant qualitaet`, Radeon 680M, 1920 × 1080 | Hoch ≥ 30 fps, Mittel ≥ 45 fps, Ultra ≥ 20 fps |
| Gesamteindruck | **Blindtest:** Bildbank alt und neu gemischt, drei Personen ordnen | neu überall vorn |

---

## 4. Fundament: Licht, Schatten, Kamera, Bildkette

Ohne dieses Fundament wird jede schöne Textur flach. Es kommt zuerst.

### 4.1 Licht in echten Einheiten

- **Sonne und Himmel in cd/m²** aus `sky` (Hillaire 2020), Mond aus `sky.astro`. Die Belichtung folgt nicht mehr „aus der Sonnenhöhe des Platzhalter-Himmels“ (`Renderer.exposure`), sondern wie eine Kamera: **EV100 aus dem Histogramm** (Mittel der Mitte, Ausreißer weg), mit Anpassung des Auges (heller in 0,5 s, dunkler in 3 s [G]). Belichtungskorrektur je Schaustück.
- **Himmelslicht als Kugelfunktionen** (SH, 9 Werte) je Bild aus der Himmels-LUT: blaues Licht in den Schatten, warmes auf Gipfeln.
- **Bodenreflexion:** Wasser und Schnee hellen Hänge von unten auf (ein Wert je Gebiet aus der Albedo [G]).

### 4.2 Schatten

1. **Geländeschatten über Horizontkarten** (neu, `horizon`): je Kachel der Clipmap der Horizontwinkel in 16 Richtungen, einmal in der Aufbereitung gerechnet (Schritt `horizont`, Fern 30 m, Szene 5 m, Kern 2 m). Im Shader: Sonne über dem Horizont → Licht, mit weichem Rand nach dem Sonnenradius. Kostet einen Texturzugriff, reicht bis 140 km. **Damit wirft der Fairweather um 22:00 seinen Schatten über die Bucht.**
2. **Kaskadenschatten** (CSM, 4 Stufen, 50 m / 400 m / 3 km / 20 km [G]) für Wald, Boote, Welle, Felssturz, Staub, mit stabiler Rasterung (kein Wandern beim Drehen) und PCF mit Rauschen.
3. **Horizont-Umgebungsverdeckung:** aus derselben Horizontkarte die Himmelsöffnung (Täler, Rinnen und Schluchten dunkler).
4. **Bildschirm-AO** (GTAO, halbe Auflösung) für Nahes: Steine, Stämme, Bootsdeck.
5. **Kontaktschatten** im Bildschirm für Gras, Stämme am Ufer.
6. **Wolkenschatten** aus `cloud` auf Gelände und Wasser.

### 4.3 Kamera wie eine echte

- **Objektiv statt Bildwinkel:** Brennweite in mm (Kleinbild), 14 bis 800 mm; Standard **35 mm** statt heute rund 23 mm. Blende, Verschlusszeit und ISO bestimmen Tiefenschärfe, Bewegungsunschärfe und Korn; die Belichtung bleibt automatisch, wenn nicht anders gewollt.
- **Führungen:** Stativ (ruhig), Hand (leichtes Zittern [G]), Boot (folgt Rollen und Stampfen aus `vessel`), Hubschrauber (Trägheit, Kurvenlage), Drohne. Fahrten mit Trägheit und weichem Halt (aus `kino`).
- **Beben:** Die Kamera zittert mit der Bodenbewegung aus `quake` am Standort, nicht mit Zufall.

### 4.4 Bildkette (Reihenfolge je Bild)

1. Himmels-LUTs (Transmission, Mehrfachstreuung, Himmelsbild, Luftperspektive als Froxel-Volumen) — `sky`
2. Schatten: Kaskaden; Geländeschatten aus der Horizontkarte im Shader
3. Spiegelbild für das Wasser (Gelände, Wald, Wolken, Himmel an der Ebene des Pegels gespiegelt, halbe Auflösung) — `mirror`
4. Gelände (`clipmap` + `surface` + `cliff` + Makrofarbe + Schnee), Gletscher (`ice`), Wald, Boote, Felssturz
5. Wasser (`ocean` + Gletschermilch + Spiegelbild + Bildschirmspiegelung), Welle, Uferlinie
6. Volumen: Wolken (`cloud`, halbe oder viertel Auflösung), Nebel (`fog`), Staub (`dustcloud`), Gischtschleier
7. Teilchen: Gischt, Tropfen, Treibgut-Splitter, Vögel
8. **TAA** mit Hochrechnung (TAAU: Bild in 67–100 % gerechnet, auf 100 % gebracht)
9. Bewegungsunschärfe, Tiefenschärfe, Bloom, Linsenschmutz (sparsam), Belichtung, Filmkurve, **Farbabstimmung (3D-LUT je Zeitstand)**, Vignette, Korn, Balken
10. HUD und Marken (nur wenn an)

### 4.5 Filmkurve und Farbe

- **Filmkurve:** AgX oder ACES 1.3 RRT statt der Narkowicz-Näherung; Ziel: Schnee und Gischt laufen weich in Weiß aus, ohne die Farbe zu kippen (Alpenglühen bleibt rosa, wird nicht gelb). Entscheidung FB4.
- **Farbabstimmung als 3D-LUT** (33³) je Zeitstand und Regiebuch, erstellt im Bedienfeld BILD (Lift/Gamma/Gain, Sättigung je Ton) und als `.cube` gespeichert. Grundsatz: Die Abstimmung verstärkt das Farbskript (2.4), sie verfälscht keine belegten Farben [G].

---

## 5. Die Elemente der Szene

Jedes Element mit **Ist**, **Ziel**, **Mittel** (Package, Daten) und **Abnahme** (Bildbank-Nummer).

### 5.1 Himmel und das Licht der Julinacht

- **Ist:** Verlauf, 9 Zeilen.
- **Ziel:** Der Himmel einer Nacht in 58,6° N, die nicht dunkel wird. 22:00 Gold an der Kimm im NW; ab 22:04 Sonne unter der Kimm; **Alpenglühen** auf allen Gipfeln über 2 500 m (die Sonne steht für den Gipfel des Fairweather noch über dessen Kimm: bei 4 671 m sinkt die Kimm um $\sqrt{2H/R} \approx 2{,}2°$, er liegt also bis gut 22:30 im direkten Licht [gerechnet, mit 9.10 zu prüfen]); Erdschatten und Venusgürtel im SO; blaue Stunde ohne Ende.
- **Mittel:** `sky` (Hillaire, Ozon, Dunst nach Koschmieder), Sonne aus 9.10, Sichtweite nach FB6. Alpenglühen fällt aus der Rechnung heraus, wenn das Sonnenlicht je Höhe gerechnet wird (Transmission entlang des Strahls zum Gipfel), nicht als Effekt.
- **Abnahme:** Bild 1, 2; Zeitraffer 21:00–01:00 als Film; Sonnenstand gegen 9.10.

### 5.2 Wolken

- **Ziel:** Wolken, wie sie zur Küste Südostalaskas gehören: **Wolkenkappe und Fahnenwolke** am Fairweather (Lee-Wirbel, Föhnmauer), **Stratusbänder in den Hängen** (Nebel, der im Wald hängt; das Bild von Südostalaska), hohe Cirren, die nach Sonnenuntergang noch rot leuchten. Für 1958 die Lage nach FB5.
- **Mittel:** `cloud` (Schneider/Wrenninge, Licht für acht Höhen, Kuppe abends rot), erweitert um **orographische Wolken**: Kappe und Fahne als Dichtefeld am Gipfel, gerechnet aus Wind und Gelände [G]; **Hangnebel** als dünne Schicht über dem Gelände in fester Höhe (`fog` mit Untergrenze aus Haithabu, über Land).
- **Abnahme:** Bild 2, 12; Wolkenschatten auf dem Wasser.

### 5.3 Luft, Dunst, Nebel

- **Luftperspektive** aus dem Froxel-Volumen von `sky` (Rayleigh 8 km, Mie 1,2 km Skalenhöhe; 9.11), ersetzt `aerial()` in `common.glsl`.
- **Seenebel am Eingang** (`fog`, Bänke) für „heute“ und als Stimmung am Morgen danach.
- **Gletscherwind:** Abends fällt kalte Luft vom Eis; flacher Dunst über dem T-Kopf [G].
- **Gottesstrahlen:** Licht durch Lücken am Grat in Dunst und Staub (Volumen mit Schattenabfrage über die Horizontkarte).

### 5.4 Gelände

Der größte Umbau. Heute sechs Farben; künftig vier Schichten:

1. **Makrofarbe** aus Sentinel-2 L2A (klarste Juli-Szenen 2017–2025, 10 m, Kacheln 08VLK, 08VLL) als Grundfarbe bis 140 km, **entschattet**: Farbe ÷ (Beleuchtung aus Gelände und Sonnenstand der Aufnahme) [gerechnet], sonst sind Schatten von 11 Uhr in die Farbe gebacken. Für 1958 im Band und am T-Kopf der Rückbau (Wald statt Narbe, Fronten 1958). Schritt `farbe` der Aufbereitung (neu).
2. **Materialien** aus den Klassen (16a: Wald, Gebüsch, Fels, Schutt, Eis, Schnee, Wasser, Strand) auf die 13 Sätze aus Phase 5 (`surface`, Hex-Kachelung, Höhenmischung), mit Rand in Rauschen statt Kante.
3. **Fels** an allen Hängen über 35°: `cliff` mit Feinform (Verschiebung im Scheitelshader bis 4 m), Schieferung, Klüften, Flechten, Sickerstreifen unter Bächen, Schnee auf Absätzen. Gestein: Schiefer und Granodiorit [zu prüfen, Geologie aus Miller 1960].
4. **Erosionsfeinform** wo das Gelände grob ist (Fern 30 m, Szene 5 m): Rinnen und Grate als Höhendetail, die dem Gefälle folgen (Erosionsfilter nach Hangrichtung, kein reines Rauschen) [G]. Damit werden die Gipfel der Kette scharf, statt rund wie Teig.

Dazu:

- **Schnee** nach Höhe, Exposition (Nordhänge tiefer), Hang (über 50° blank), Rinnen (Lawinenrinnen länger weiß), Krümmung (Kuppen abgeweht); Firnlinie Juli [zu prüfen, RGI/Sentinel-2]. Glitzern in der Sonne, blaue Schatten.
- **Nasses Ufer:** Gezeitenband (`weathering`): dunkler nasser Kies, Algenband, Tang, Spülsaum.
- **Wasserfälle:** Die Bäche aus `LB_NET_WASSER` (heute Bänder) stürzen an Steilstufen als Fall mit Sprühnebel (`fall` aus dem Renderer).
- **Geröllkegel, Moränen** (`scree`, offen aus 6a).
- **Lidar-Normalen** im Kern bis 0,5 m: Strandwälle, Erosionskanten an der Spurlinie, Rutschnarbe (erstes Werkbuch 3.2).
- **Abnahme:** Bild 2, 3, 9, 11; ΔE gegen Sentinel-2; Kontaktbogen Fels/Schnee/Kies aus 30 m, 300 m, 3 km.

### 5.5 Gletscher

- **Ziel:** Lituya-, North-Crillon- und Cascade-Gletscher als Eis, nicht als Schnee: Spalten, Seracs, Schuttbänder (Mittel- und Seitenmoränen als dunkle Streifen), blaues Eis in Spalten und an der Front, eine **Kalbungsfront** als Wand am Gilbert Inlet, **Eisberge und Brucheis** im Wasser davor.
- **Mittel:** `ice` (Absorption je Farbkanal, Dicke je Bildpunkt); Spaltenrichtung aus den Dehnungsraten von ITS_LIVE (`--gross`) [gerechnet]; Fronten 1958 aus der Karte (Schritt `gletscher`, offen aus Phase 2); Brucheis als `debris`-Teilchen mit Eismaterial [G]. Bei der Welle: 400 m Front abgeschert (erstes Werkbuch 10.1), das Eis treibt mit.
- **Abnahme:** Bild 3, 4.

### 5.6 Wasser

- **Ist:** Rauschen, feste Farbe, nur der Himmel gespiegelt.
- **Ziel:**
  - **Spiegel.** An einem windstillen Abend spiegelt die Bucht die ganze Kette. Das ist das stärkste Einzelbild der Szene und kostet nur eine **planare Spiegelung** (die Fläche ist eben, bis auf die Erdkrümmung), gerechnet in halber Auflösung, gestört durch die Normale der kleinen Wellen.
  - **Gletschermilch** nach 9.9 im ersten Werkbuch, aber als **Feld**: milchig-türkis am T-Kopf, wo Gletscher und Bäche Schwebstoff bringen, klarer zum Eingang; **Schwebstofffahnen** vor Gletscherfronten und Bachmündungen (aus Sentinel-2 B3/B4 gemessen, Schritt `truebe`). Farbe aus Absorption und Rückstreuung, nicht gemalt.
  - **Wind auf dem Wasser:** `ocean` (FFT, JONSWAP) mit wenig Wind; **Katzenpfoten** (Böenflecken, die über den Spiegel ziehen) und **Glattstreifen** [G]. Am Eingang **Gezeitenstrom** mit Strömungskante und Stehwellen über der Barre, **Brandung an der Barre** (`surf`).
  - **Ufer:** Saum aus Schaum, nasser Kies, Spülsaum; Tiefe sichtbar im Flachen (Grund durchscheinend, Kaustiken).
  - **Treibholz und Kelp** (vor), Holzteppich (danach, jetzt mit Stämmen als Formen, nicht als Farbe).
  - **Unterwasser:** Trübe aus der Gletschermilch, Gottesstrahlen, Kaustiken auf dem Grund, Schwebteilchen.
- **Mittel:** `ocean` (Gletschermilch aus 6.5 im ersten Werkbuch, Trübung 0 lässt andere Szenen unverändert), `mirror` (neu, 10.1), Bildschirmspiegelung für die Welle, `surf`, `weathering`.
- **Abnahme:** Bild 1, 3, 8; ΔE Wasser gegen Sentinel-2; der Spiegel in Bild 1 zeigt die Gipfel bis auf Wellenstörung scharf.

### 5.7 Wald

- **Ist:** Millionen Bäume (`ForestGpu`), aber flach beleuchtet, fast schwarz, Kanten flimmern.
- **Ziel:**
  - **Licht im Wald:** Schatten der Kaskaden, Durchlicht der Nadeln im Gegenlicht (Rand hell), Himmelslicht von oben, Kronen oben heller [G].
  - **Farbe nach Art und Alter:** Sitka-Fichte blaugrün, Hemlock dunkler, **Erle hell** (der junge Wald im Band heute, das erklärt die sichtbare Narbe nach 68 Jahren), Bartflechten grau in alten Kronen.
  - **Fernwald als Fläche mit Relief:** jenseits der Impostors das Kronendach aus der Kronenhöhe (ArcticDEM − Gelände, Schritt `wald`) als Höhen- und Normalenfeld; Kronenschatten im Fernwald.
  - **Waldrand:** Silhouetten gegen den Himmel und gegen die Narbe; einzelne tote Spitzen [G].
  - **Wind:** Wiegen der Kronen, mit `wind`.
  - **Kein Flimmern:** TAA (4.4) und Mip-Auswahl der Impostors.
- **Abnahme:** Bild 1, 6, 11; Flimmermaß 3.2.

### 5.8 Boote und Menschen

- **Edrie, Badger, Sunmore** (`vessel`) mit Material (Farbe, Holz, Tau, Rost, nass), **Ankerlaternen** (Licht auf Wasser und Rumpf), Rauch aus dem Ofenrohr [G], Figuren an Deck (`figure`), Wasser an Deck, Gischt am Bug in der Welle (`spray`).
- **Abnahme:** Bild 1, 7.

### 5.9 Leben (aus 18c)

Möwen und Weißkopfseeadler (`flock`), Seehunde und Seeotter (`seal`), Bären am Ufer (`bear`). Vögel fliegen vor dem Beben auf [G]; nach der Welle ist es still. Maßstab und Ruhe vor dem Sturm.

### 5.10 Wetter und Jahreszeiten „heute“ (aus 18b)

Regen (`rain`, nasse Flächen über `weathering`), Nebel, Herbstfarben der Erlen, Winter mit Schnee bis ans Wasser (`season`), Polarlicht (`aurora`, neu) über dem Fairweather in klaren Herbstnächten, gespiegelt im Wasser.

---

## 6. Die Welle als Bild *(Herzstück)*

Die Physik ist da (Läufe `kette`, `messwelle`; Auflauf 514 m; Front an Cenotaph Island nach 227 s). Sie muss sichtbar werden.

### 6.1 Was man sehen soll

1. **Der Einschlag (Zeitlupe):** Fels und Eis tauchen ein; eine Kaverne öffnet sich; eine Wand aus Wasser und Schlamm steigt, aus ihr ein **Schleier aus Gischt**, der hunderte Meter steigt und im Abendlicht leuchtet.
2. **Der Auflauf am Sporn:** Ein Wasserfilm schießt den Hang hinauf, Bäume knicken in Reihen, Erde und Stämme werden mitgerissen; oben reißt das Wasser ab und fällt zurück.
3. **Die Front in der Bucht:** eine brechende Wand, vorne steil, oben eine **weiße Krone**, dahinter aufgewühltes, braunes Wasser voller Stämme; Gischt reißt von der Krone; das Licht scheint durch den dünnen Kamm.
4. **Am Ufer:** Die Front frisst den Wald am Ufer weg; Treibgut wird an Land geworfen.
5. **Nachlauf:** Schwall und Rückstrom, Schaumteppich, kreuzende Wellen (Seiche), Treibholz überall.

### 6.2 Mittel

| Teil | Wie | Package |
|---|---|---|
| Form der Front | Höhenfeld der Vorrechnung, dazu **Krone als eigenes Netz** längs der Front, wo $\partial\eta/\partial t$ und die Steilheit groß sind; überhängende Lippe nach `BreakerShape`/`BreakerMesh` [G, aus der Steilheit] | `wave`, `surf` |
| Schaum | Schaumfeld, das mit der Strömung der Vorrechnung mitwandert (Advektion), entsteht an der Front und im Auflauf, klingt ab; Textur mit Blasen und Streifen | `ocean` (FoamField) |
| Weißwasser | Volumen dicht über der Fläche, wo die Front bricht | neu in `spray` |
| Gischtschleier | große Teilchenwolken (Tropfen und Nebel), in die Höhe getrieben, im Wind abziehend; Gegenlicht nach Henyey-Greenstein | `spray` (Maßstab 100 m statt 10 m) |
| Licht durch die Krone | Durchlicht (Einfachstreuung durch die Dicke des Kamms), türkis im klaren, braun im trüben Wasser | `welle.frag` neu |
| Trübe | Schwebstoff aus der Vorrechnung (Fels, Boden), Farbe aus Absorption | `ocean` (Gletschermilch-Modell) |
| Treibgut in der Front | Stämme aus `debris` als echte Stämme, rollend, mit Bewegungsunschärfe | `debris`, `forest` |
| Bäume fallen | Folge je Platz aus `succession` sichtbar als Animation (knicken, kippen, verschwinden im Wasser), nicht als Umschalten | `forest`, `succession` |
| Schall im Bild | Kamera zittert mit dem Grollen, Staubwolken pulsieren mit Böen [G] | `quake`, `soundscape` |

### 6.3 Felssturz und Staub

- **Fels:** Blöcke aus `fracture` mit `cliff`-Material, Staub von jedem Block, Funken nicht [G: kein Beleg]. Der Rest der Wand bleibt als frische, helle Abrisskante stehen.
- **Staubwolke:** `dustcloud` mit Mehrfachstreuung und Schatten in sich selbst (Volumen mit Licht aus der Horizontkarte), im Gegenlicht orange, im Schatten blau-grau [G].
- **Eis:** abgescherte Front als weiße Brocken und Eisstaub.

### 6.4 Abnahme

Bild 4–8 in Standbild und als 20-s-Film je Schaustück; der Film „Zehn Minuten“ in 4K.

---

## 7. Die Narbe *(Blickfang)*

Die bis 524 m kahl gerissene Waldgrenze ist das bekannteste Bild des Ereignisses. Sie bekommt eine eigene Phase.

- **Kante:** scharf, wie mit dem Messer gezogen (Spurlinie aus `Band1958`, im Bild auf den Meter genau, aus 12 km sichtbar).
- **Boden im Band, 10. Juli 1958:** frischer Fels hell grau-weiß, Mineralboden ocker bis rostbraun, Wurzelteller, stehende **entrindete Stämme silbern**, gebrochene Stümpfe, umgelegte Stämme in Richtung des Rückstroms (Daten aus `succession`), Schlamm und Geröll am Hangfuß. Material aus `surface` (Rock058, Wood035) mit Makrofarbe aus Millers Fotos [G].
- **Über der Kante:** unberührter dunkler Altwald, scharfer Gegensatz.
- **Übergang 1958 → 2026:** Im Jahres-Zeitraffer wandert die Farbe von Ocker über Grün der Erlen zu jungem Fichtenwald; die Kante bleibt als Helligkeitssprung sichtbar, wie auf heutigen Fotos.
- **Vergleich mit dem Foto:** Bild 10 an Millers Standort (LB_NET_SICHT, 7.6 im ersten Werkbuch), Millers Foto als Überblendung (Schieber im Bild, 8.5).
- **Abnahme:** Bild 9, 10, 11; Film „68 Jahre“.

---

## 8. Inszenierung

### 8.1 Der Start der App

1. Schwarzes Bild, Ton: Wasser, ein Kutter, Möwen (sofort, während die Daten laden).
2. Erstes Bild nach spätestens 5 s in grober Stufe (Clipmap grob, Wald aus der Fernfläche); feiner wird es im Lauf, ohne Sprung (Überblenden der Stufen).
3. **Startflug** (60 s, abbrechbar mit jeder Taste): vom Golf von Alaska in 300 m Höhe über die Barre am Eingang, über die Kutter vor Anker, die Bucht hinauf zum T-Kopf, Blick hoch zum Fairweather im Alpenglühen; Titel „Lituya Bay · 9. Juli 1958“; Ende in der freien Kamera über der Anchorage Cove.
4. Bedienfeld eingeklappt, HUD und Marken aus; erscheint erst, wenn die Maus an den Rand geht (FB13).

### 8.2 Objektive je Schaustück

| Schaustück | Brennweite | Führung | Uhr | Bildidee |
|---|---|---|---|---|
| 1 Abend vor Anker | 50 mm | Stativ am Ufer | 22:00 | Kutter im Spiegel, Kette glüht |
| 2 Das Beben | 35 mm | Hand, Zittern aus `quake` | −5 s | Lawinen weiß an allen Hängen, Vögel steigen auf |
| 3 Der Sturz | 85 mm | Stativ | 93 s, 1:2 | die Wand bricht, Staub im Gegenlicht |
| 4 Die Wand aus Wasser | 24 mm | Hubschrauber | 104 s, 1:20 | Krone, Schleier, Licht durch den Kamm |
| 5 524 m | 135 mm | Stativ gegenüber | 106 s, 1:5 | Wasserfilm den Hang hinauf, Wald fällt in Reihen |
| 6 Die Front | 50 mm | Boot | 200 s | Wand mit Stämmen, Cenotaph Island dahinter |
| 7 Unter Wasser | 18 mm | Taucher | 115 s, 1:2 | Licht von oben, Stämme im Strom |
| 8 Mit der Edrie | 28 mm | an Deck | 400 s | Bug steigt, Kette reißt |
| 9 Über die Nehrung | 200 mm | vom Ufer | 420 s | die Badger auf dem Kamm über der Nehrung |
| 10 Der Morgen danach | 35 mm | Hubschrauber | 10.7., 06:00 | Narbe, Holzteppich, Stille |
| 11 68 Jahre | 85 mm | Stativ, Zeitraffer | 1958 → 2026 | Narbe wächst zu |
| 12 Heute bei Nacht | 24 mm | Stativ, 15 s Belichtung | Herbst | Polarlicht im Spiegel |

### 8.3 Regiebücher neu geschnitten

Die fünf Regiebücher (17a) bleiben im Inhalt; neu sind Objektiv, Licht, Abstimmung und Ton je Einstellung, Schnitt auf Bewegung, Musik nach FB14. Neu dazu: **„Die Bucht“** (3 min, ohne Ereignis): nur Landschaft, Licht und Wetter über einen Sommertag. Das ist das Vorzeigestück für Leute, die nichts über die Welle wissen.

### 8.4 Fotomodus

- Freie Kamera mit Objektiv, Blende, Verschluss, Filter (Polfilter für das Wasser, Grauverlauf), Gitter für die Drittelregel.
- **Akkumulation:** 64 bis 1 024 Bilder je Foto mit verschobenem Raster, Blende und Verschluss → saubere Kanten, echte Tiefenschärfe und Bewegungsunschärfe; Ausgabe bis 8K als PNG und EXR.
- **Plakat:** Bild mit Titel, Datum, Koordinaten, Bildunterschrift im F-Stil.

### 8.5 Damals und heute

- **Schieber im Bild:** links 1958, rechts heute, an derselben Kamera; oder 9. Juli 22:00 gegen 10. Juli 06:00.
- **Millers Fotos an Ort und Stelle:** An seinen Standorten liegt das Foto im Bild, überblendbar, mit gleicher Brennweite (Bildbank 10). Braucht die Fotos von Hand (FB9).

### 8.6 Netzblick und Erklärbilder

Die Netze bleiben, bekommen aber einen eigenen Stil: dünne helle Linien und Isochronen wie eingeritzt, Zahlen in einer ruhigen Schrift, Farben aus dem Farbskript (keine Bonbonfarben); als Karte im Stil einer Feldkarte von 1958 (Papier, Schraffen) wählbar [G].

---

## 9. Oberfläche und Marken

- **Start:** HUD aus, Marken aus (erledigt am 03.11.2026).
- **Bedienfeld:** eingeklappt, kommt mit der Maus an den rechten Rand oder mit Tab; Blöcke nach Häufigkeit (ZEIT, ANSICHT, EREIGNIS oben).
- **Neuer Block BILD:** Objektiv, Belichtung, Abstimmung (LUT), Wetter, Wolken, Dunst, Schnee, Fotomodus.
- **Zeitleiste** unten (ausblendbar) mit den Ereignissen als Marken: Stoß, Sturz, Einschlag, Sporn, Cenotaph Island, Eingang; ziehbar wie in einem Schnittprogramm.
- **Marken in der Szene:** Ortsnamen als schlichte Beschriftung mit feiner Linie zum Ort, ausgeblendet nach Abstand und Wichtigkeit; keine farbigen Kugeln.
- **HUD:** nur noch auf Wunsch, Leistungswerte in eigener Ansicht (F3).

---

## 10. Neue und erweiterte Packages

Paketvertrag wie im ersten Werkbuch (4.3, 15 Punkte). Jedes neue Package hat Demo, Prüfung und README mit Bild; jede Erweiterung lässt die anderen Szenen gleich rechnen (Vergleichsbilder).

### 10.1 Neu

| Package | Inhalt | Prüfung |
|---|---|---|
| `com.dan.horizon` | Horizontkarten je Kachel (16 Richtungen), Geländeschatten mit weichem Rand, Himmelsöffnung als AO; Kern rechnet, `gl/horizon.glsl` fragt ab | Schatten gegen Strahlverfolgung im Höhenfeld an 1 000 Stichproben (Winkel auf 0,5°) |
| `com.dan.mirror` | planare Spiegelung mit Erdkrümmung, Schnittebene, halbe Auflösung, Störung durch Normale | Spiegelbild gegen gespiegelte Kamera pixelgleich bei ruhigem Wasser |
| `com.dan.lens` | Kamera in Brennweite/Blende/Verschluss/ISO, Belichtung aus dem Histogramm, TAA/TAAU, Bewegungsunschärfe, Tiefenschärfe (Bokeh), Bloom, Linsenschmutz, Korn, Filmkurve (AgX/ACES), 3D-LUT, Akkumulation für Fotos | Kantenbild gegen 64-fach-Akkumulation (Fehler < 1 %); Flimmermaß |
| `com.dan.grade` *(oder Teil von `lens`, FB16)* | Farbabstimmung als Werkzeug: LUT erzeugen, laden, mischen; Farbskript je Zeitstand | LUT neutral = Bild unverändert |
| `com.dan.aurora` | Polarlicht (aus dem ersten Werkbuch 5.13) | Spektrum der Linien 557,7/630,0/427,8 nm |

### 10.2 Erweitert

| Package | Erweiterung |
|---|---|
| `sky` | Froxel-Luftperspektive für Gelände bis 140 km; Licht je Höhe für Alpenglühen; SH-Himmelslicht |
| `cloud` | orographische Wolken (Kappe, Fahne), Hangnebel-Schicht; Licht aus `horizon` |
| `ocean` | Gletschermilch als Feld (Trübe-Karte), Schaum mit fremder Strömung, Katzenpfoten |
| `surface` | Makrofarbe entschattet, Klassen-Mischung mit Rauschrand, Schnee nach Exposition und Krümmung |
| `cliff` | Gesteine Schiefer/Granodiorit, Feinform auf Clipmap-Gelände (bisher Einzelwände) |
| `ice` | Spalten aus Dehnungsraten, Schuttbänder, Brucheis als Teilchen |
| `spray` | großer Maßstab (Gischtschleier 100–500 m), Weißwasser-Volumen |
| `surf` | Krone und Lippe auf fremdem Höhenfeld (nicht nur auf eigener Brandung) |
| `forest` | Durchlicht, Farbe nach Art und Alter, Erle, Fernwald mit Kronenrelief, Fallen als Animation |
| `fall` | Wasserfälle an Bächen aus einem Netz |
| `kino` | Objektive je Einstellung, LUT je Einstellung, Schnitt auf Bewegung |

---

## 11. Leistungsbudget und Qualitätsstufen

Radeon 680M (geteilter Speicher, Budget VRAM 2 GB wie F19), 1920 × 1080. Ziel **Hoch ≥ 30 fps = 33 ms**, gerechnet mit TAAU aus 1600 × 900 [G]:

| Durchgang | Hoch, Ziel (ms) | Heute gemessen | Rückfall |
|---|---|---|---|
| Himmels-LUTs, Himmel | 0,8 | 0,59 (Mittel, Platzhalter) | LUTs nur jede 4. Bild |
| Kaskadenschatten | 3,0 | – | 3 Stufen, halbe Größe |
| Spiegelbild | 3,0 | – | viertel Auflösung, ohne Wald fern |
| Gelände (Material, Fels, Horizont) | 6,0 | 6,37 (Mittel, Platzhalter) | Fels-Verschiebung nur nah |
| Wald | 6,0 | 4,55 (Hoch, Bucht) | Reichweite der Stufen |
| Wasser, Welle | 3,0 | 2,81 | Bildschirmspiegelung aus |
| Wolken, Nebel, Staub | 4,0 | – | viertel Auflösung, zeitlich verteilt |
| Teilchen | 1,5 | – | weniger Tropfen |
| TAAU und Nachbearbeitung | 2,0 | 0,18 | ohne Tiefenschärfe |
| Rest (Boote, Bäche, HUD) | 2,0 | 2,18 (HUD) | – |
| **Summe** | **31,3** | | |

Neue Stufe **Film**: alles auf Ultra, Akkumulation, ohne Zeitgrenze; für `ant film`, `ant readmebilder`, Fotomodus.

---

## 12. Phasenplan

„R“ Renderer, „L“ LituyaBay1958. Je Phase: Bildbank vorher und nachher nebeneinander als `docs/bild/Vergleich_PhaseBn.png`, Eintrag in Abschnitt 17, Freigabe „PhaseBn, los“. Reihenfolge nach Wirkung: Was viele Bilder auf einmal hebt, kommt zuerst.

| Phase | Ort | Inhalt | Abnahme |
|---|---|---|---|
| **B0** | L | **Bildbank und Bestandsaufnahme:** `scene/bildbank.txt`, `ant bildbank`, Ist-Bilder aller 12 Ansichten; Referenzfotos (Miller, NPS) beschaffen und je Ansicht ablegen; Sentinel-2-Farben der Flächen messen (Wasser, Wald, Fels, Schnee, Band) | Tafel „Ist gegen Foto“ je Ansicht; Farbtabelle |
| **B1** | R+L | **Fundament Licht:** `horizon` (Schritt `horizont`), Kaskadenschatten, SH-Himmelslicht, Horizont-AO, `lens` Teil 1 (Brennweite, Belichtung aus Histogramm, AgX/ACES, TAA) | Bild 1, 2, 9: Schatten der Kette über der Bucht; Flimmermaß |
| **B2** | R+L | **Himmel und Luft:** `sky` statt Platzhalter (Licht je Höhe, Alpenglühen), Froxel-Luftperspektive, `cloud` mit Kappe und Hangnebel, `fog`, Gottesstrahlen | Bild 1, 2, 12; Zeitraffer 21–01 Uhr |
| **B3** | R+L | **Gelände:** Schritt `farbe` (Sentinel-2 entschattet), Klassen → `surface`, `cliff` auf der Clipmap, Erosionsfeinform, Schnee, Gezeitenband, Lidar-Normalen | Bild 2, 3, 11; ΔE ≤ 8; Kontaktbogen 30 m/300 m/3 km |
| **B4** | R+L | **Wasser:** `mirror`, `ocean` mit Gletschermilch-Feld (Schritt `truebe`), Katzenpfoten, Uferlinie, Barre mit `surf`, Kaustiken, Unterwasser | Bild 1, 3, 8; Spiegel scharf; ΔE Wasser |
| **B5** | R+L | **Gletscher:** `ice` auf den Gletschern, Spalten aus ITS_LIVE, Fronten 1958 (Schritt `gletscher`), Brucheis, Wasserfälle (`fall`) | Bild 3 |
| **B6** | R+L | **Wald und Narbe:** Licht im Wald, Farbe nach Art und Alter, Erle, Fernwald mit Kronenrelief; die Narbe nach Abschnitt 7; Millers Foto an Ort und Stelle | Bild 6, 9, 10, 11; Film „68 Jahre“ |
| **B7** | R+L | **Die Welle als Bild:** Krone, Schaum mit Strömung, Weißwasser, Gischtschleier, Durchlicht, Treibgut in der Front, Bäume fallen; Felssturz mit Material, Staub mit Licht | Bild 4–8, Film „Zehn Minuten“ |
| **B8** | R | **Linse:** `lens` Teil 2 (Bewegungsunschärfe, Tiefenschärfe, Bloom, Korn, LUT), Fotomodus mit Akkumulation, Plakat | Fotos 8K; Vergleich Kanten gegen Akkumulation |
| **B9** | L | **Inszenierung:** Startflug, Objektive je Schaustück (8.2), Regiebücher neu geschnitten, „Die Bucht“, Damals/Heute-Schieber, Farbskript als LUTs | Start in 5 s, Startflug, alle Regiebücher im Kino |
| **B10** | R+L | **Leben und Wetter** (18b, 18c): Vögel, Robben, Bären, Regen, Nebel, Herbst, Winter, Polarlicht | Bild 12; Schaustück 12 |
| **B11** | L | **Oberfläche:** Bedienfeld einklappbar, Block BILD, Zeitleiste, Marken und Netzblick neu gestaltet | Blickprüfung, Bedienung ohne Handbuch |
| **B12** | L | **Abnahme Bild:** Blindtest, `ant qualitaet`, README und GitHub-Seite mit neuen Bildern und einem Film, Trailer (60 s) | 3.2 vollständig erfüllt |

Grobe Zeit: B0–B4 bringen den größten Sprung (das, was die Kritik am meisten trifft). Nach B4 wird die Bildbank gezeigt und entschieden, ob die Reihenfolge bleibt.

---

## 13. Risiken

| Risiko | Folge | Gegenmittel |
|---|---|---|
| Hier ohne `daten/` und ohne Radeon kein echtes Bild | Shader nur mit Mesa übersetzt, Wirkung unbekannt | Jede Phase endet mit `ant bildbank` auf der Radeon; Bildbank-Ausschnitt (Kachel um Anchorage Cove, 200 MB) als Probe für Mesa [G] |
| VRAM 2 GB | Spiegelbild, Schatten, Texturen, Wolken zusammen zu viel | Budget je Durchgang (11), Streaming, Stufen |
| Sentinel-2 hat Schatten, Wolken, Schnee vom falschen Tag | falsche Makrofarbe | Entschatten, Median über Szenen, Maske aus der Wolkenkarte (SCL) |
| TAA zieht Schlieren an der Welle | Welle unscharf | Bewegungsvektoren aus dem Höhenfeld, Begrenzung in der Nachbarschaft |
| Schöner wird falscher | Glaubwürdigkeit | Regel 7 (2.2), [G] im Code, Farbe gegen Satellit und Foto |
| Umfang | B7 allein ist groß | Reihenfolge nach Wirkung; nach B4 Zwischenabnahme |
| Andere Szenen ändern sich mit | Geiranger, Sylt, Glacier sehen anders aus | Paketvertrag 14: neue Rechnung abschaltbar, Vergleichsbilder |

---

## 14. Fragen und Entscheidungen

Wie im ersten Werkbuch: je Frage Möglichkeiten, **A** ist die Empfehlung. „Alle A“ genügt als Antwort.

| Nr | Frage | A (Empfehlung) | B | C |
|---|---|---|---|---|
| FB1 | Wie viel Gestaltung neben den Belegen? | belegt, wo es Belege gibt; sonst [G] mit dem Ziel Schönheit | nur Belegtes | frei, Hauptsache schön |
| FB2 | Mindest-Bildrate auf der Radeon 680M bei Hoch | 30 fps | 60 fps (weniger Effekte) | 20 fps (mehr Effekte) |
| FB3 | Hochrechnung | TAAU aus 83 % (Hoch), 67 % (Mittel) | immer volle Auflösung | FSR 2 als Fremdcode |
| FB4 | Filmkurve | AgX | ACES 1.3 | Narkowicz wie heute |
| FB5 | Wetter am 9. Juli 1958 | wie Zeugen und ERA5 belegen [zu prüfen in B0]; wenn klar, dann klar mit Cirren [G] | stimmungsvoll mit Kappe am Fairweather [G] | wählbar im Bedienfeld |
| FB6 | Sichtweite am Abend | 80 km (klare Nordluft) [G] | 40 km (dunstig) | aus ERA5 |
| FB7 | Sentinel-2 als Makrofarbe | ja, entschattet, Juli 2017–2025 | WorldCover-Klassen mit festen Farben | Luftbilder (USGS) wo vorhanden |
| FB8 | Erosionsfeinform auf grobem Gelände | ja, nur Fern und Szene, nie im Lidar-Kern | nein | überall |
| FB9 | Fotos von Miller und NPS | du lädst sie von Hand (ScienceBase, NPS), ich lege sie an und passe ein | ohne Fotos | nur NPS (frei abrufbar) |
| FB10 | Spiegelung | planar (`mirror`) + Bildschirmspiegelung für die Welle | nur Bildschirmspiegelung | nur planar |
| FB11 | Krone der Welle | eigenes Netz längs der Front (überhängend) | nur Schaum auf dem Höhenfeld | Teilchen allein |
| FB12 | Startflug | 60 s, abbrechbar, beim ersten Start immer, danach wählbar | immer | nie, nur im Kino |
| FB13 | Bedienfeld beim Start | eingeklappt, mit Maus am Rand | offen wie heute | eigenes Fenster |
| FB14 | Musik in Regiebüchern | keine, nur Klang der Natur (Regel „wahr bleiben“) | ruhige Musik (CC0) im Vor- und Abspann | durchgehend |
| FB15 | Fotomodus mit Akkumulation | ja, bis 8K | bis 4K | nein |
| FB16 | Farbabstimmung als eigenes Package | Teil von `lens` | eigenes `grade` | nur in der App |
| FB17 | Neues Regiebuch „Die Bucht“ (nur Landschaft) | ja | nein | statt „Die Bucht der Wellen“ |
| FB18 | Netzblick im neuen Stil | ruhige Linien, Farbskript; Feldkarte wählbar | wie heute | nur Feldkarte |
| FB19 | Reihenfolge | wie Abschnitt 12, Zwischenabnahme nach B4 | Welle (B7) früher | erst Inszenierung (B9) mit heutigem Bild |
| FB20 | Rückfluss | jede Erweiterung abschaltbar, andere Szenen bekommen sie in eigenen Nachträgen | sofort überall an | nur Lituya |

---

## 15. Ideenspeicher

- **Mit der Front reiten:** Kamera auf der Krone, 227 s bis Cenotaph Island in Echtzeit, Bäume fliegen vorbei.
- **Zeitlupe 1:1 000 beim Einschlag** (aus `impact`, Partikel im Schnitt): die Kaverne als Bild.
- **Die Welle neben dem Kölner Dom:** Maßstabsfigur als Silhouette im Dunst (aus dem ersten Werkbuch 17), mit gleicher Luftperspektive.
- **Tag in einer Minute:** 24 Stunden Licht am 9. Juli in 60 s, die Nacht wird nie dunkel.
- **Stereo und VR:** zwei Augen aus `lens`, für Brillen als Seitenpaar.
- **Bilder der Zeugen:** Ulrich, Swanson im Archiv als gezeichnete Szenen in ihrem Blickwinkel [G].
- **Farben aus der Zeit:** Fotos 1958 in Kodachrome-Abstimmung für „danach“, als wählbare LUT.
- **Gletscherkalben im Echtzeitbild** vor der Welle (Abendruhe mit einzelnen Abbrüchen und Wellenringen, `ocean`-Ringwellen).
- **Kamera im Kutter:** Fenster mit Tropfen (`wetglass`) beim Brecher.
- **Licht der Laternen:** die drei Kutter um 22:00 mit Laternen, gespiegelt; nach der Welle nur noch zwei.
- **Live-Hintergrund:** die Szene als Bildschirmschoner, Uhrzeit wie draußen, Wetter aus der Vorhersage für Lituya Bay.

---

## 16. Rückfluss in die anderen Szenen

Das ist die Antwort auf „keine Weiterentwicklung“: Was hier für das Bild entsteht, hebt auch die früheren Szenen.

| Was | wohin | warum |
|---|---|---|
| `horizon` (Geländeschatten) | Geiranger, Glacier, Brocken | Berge mit Schatten über Fjord und Tal |
| `mirror` | Geiranger, Haithabu (Schlei), Timmendorf | Spiegel der Ufer |
| `lens` (TAA, Objektive, LUT, Fotomodus) | alle | gleiche Bildkette, Fotos in 8K |
| `cloud` orographisch | Geiranger, Brocken | Kappe auf dem Gipfel |
| `ocean` Trübe-Feld | Glacier, Geiranger | Gletschermilch |
| `spray` großer Maßstab | Sylt, Lighthouse, Rungholt | Gischt im Orkan |
| `forest` Durchlicht, Fernwald mit Relief | Haithabu, Brocken, Timmendorf | Wald im Gegenlicht, Wald bis zum Horizont |
| Bildbank (`ant bildbank`) | alle | jede Szene mit festen Abnahmebildern |

---

## 17. Fortschritt

| Datum | Phase | Was | Bilder | Renderer.jar |
|---|---|---|---|---|
| 03.11.2026 | — | Kritik zu 1.0 aufgenommen; HUD und Marken beim Start aus (`Renderer.java`, `ControlPanel.java`) | – | unverändert |
| 03.11.2026 | — | **Dieses Werkbuch als Entwurf**: Befund (Platzhalter seit Phase 1, Phase 5a übersprungen), Leitbild, Bildbank, Fundament, Elemente, Welle, Narbe, Inszenierung, 13 Phasen B0–B12, Fragen FB1–FB20 | – | – |
