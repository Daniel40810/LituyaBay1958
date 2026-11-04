# LituyaBay1958 Werkbuch Welle

**Die Welle. Die Bucht. Das Bild.** Das dritte Werkbuch des Projekts. Es hat nur einen Zweck: Aus den vorhandenen Daten, dem vorhandenen Renderer und dem Projekt Nature ein scharfes Bild, eine echte Welle mit großen Effekten, Wetter und eine Landschaft machen, die man nicht vergisst.

> **Stand: 04.11.2026, 02:40. Fragen WF1–WF14: alle A. W0 „Scharf“ committet (7265766). W1 „Licht“ gebaut und auf der Radeon gerechnet, wartet auf deine Abnahme.**
> Neu in W0: Abschnitt 2b „Was nie gebaut wurde“ (das strukturelle Problem), die Versprechensliste (2b.2), K8–K10, Fortschritt (12).
> Neu am 04.11.: deine Rückmeldung mit den Bildern in `docs/wahrHeeldenbilder` (Abschnitt 2a: Ursachen für Brei und Korn), W0 wird „Scharf“, Wetter als eigenes Kapitel und eigene Phase (Abschnitt 5a, W7), Bäume und Böden aus dem Projekt Nature (Abschnitt 3), Heldenbild H9.
> Artefakt: „LituyaBay1958 Werkbuch Welle“. Diese Datei ist die Quelle.
> Das erste Werkbuch (`LITUYABAY1958_WERKBUCH.md`) bleibt gültig für Physik, Netze, Datenbank, Werkzeuge. Das Werkbuch Bild (`LITUYABAY1958_WERKBUCH_BILD.md`) wird mit WF1 A zum Ideenspeicher; sein Phasenplan B1–B16 wird durch W0–W8 ersetzt.
> Kennzeichen wie bisher: **belegt**, **gemessen**, **gerechnet**, **gestaltet [G]**, **[zu prüfen]**.

---

## Inhalt

0. Was gerade läuft (Klarstellung)
1. Auftrag
2. Befund
2a. Brei und Korn: die Ursachen
2b. Was nie gebaut wurde: das strukturelle Problem
3. Das Vorhandene: 64 Packages, 16 genutzt, dazu Nature
4. Die Welle: vierzehn Effekte
5. Die Landschaft: zehn Aufwertungen
5a. Wetter
6. Inszenierung: neun Heldenbilder, Startflug, Kino
7. Zwei Stufen: Echtzeit und Kino
8. Phasen W0–W9
9. Arbeitsweise
10. Risiken
11. Fragen WF1–WF14
12. Fortschritt

---

## 0. Was gerade läuft (Klarstellung)

Heute Nachmittag lief Phase B0 aus dem Werkbuch Bild („Bildbank und Bestandsaufnahme“). Das war Vorarbeit ohne sichtbare Verbesserung, deshalb war nicht zu erkennen, was passiert. Stand von B0:

| Was | Wo | Zustand |
|---|---|---|
| Werkzeug `tools/Bildbank` (`ant bildbank`): rendert feste Ansichten, baut Kontaktbogen und Vorher/Nachher-Tafeln | `src/com/dan/lituya/tools/Bildbank.java`, `scene/bildbank.txt`, `build.xml` | fertig, **nicht committet** |
| Brennweite in mm für die Kamera (`Renderer.brennweite`), Schalter in `Tempo` | `gl/Renderer.java`, `tools/Tempo.java`, `gl/EreignisGl.java` | fertig, **nicht committet** |
| Zwölf Ist-Bilder in 1920 × 1080, Stufe HOCH, auf der Radeon | `docs/bild/B0/01…12`, `Bildbank_B0.png` | vorhanden |
| Referenzfotos, Farbmessung Sentinel-2 | – | nicht begonnen, wird hier nicht gebraucht |

Am Bild selbst wurde nichts verändert. Dieses Werkbuch nimmt die Bildbank als Werkzeug für die Vorher/Nachher-Abnahme (WF2) und setzt danach nur noch auf sichtbare Schritte.

---

## 1. Auftrag

In deinen Worten (03.11.2026):

> Eine echte Welle mit richtig tollen Effekten. Eine grandiose Optik. Die Landschaft aufpeppen. Das Vorhandene nutzen und richtig in Szene setzen.

**Leitsatz:** Wer die App startet, sieht in den ersten zehn Sekunden: Diese Bucht ist wunderschön. Wer die Welle abspielt, sieht: Was hier passiert ist, war ungeheuer.

**Was „grandios“ hier heißt:**

1. **Licht macht die Form.** Tiefe Sonne, Schatten der Berge über der Bucht, Alpenglühen auf 4 671 m. Ohne das bleibt jeder Effekt flach.
2. **Maßstab.** Ein Kutter von rund 12 m, Fichten von 40–60 m, und dahinter eine Wand aus Wasser und Gischt, die höher ist als jedes Gebäude Europas.
3. **Bewegung im Bild.** Gischt, die im Gegenlicht leuchtet, Schaum, der mit der Strömung zieht, Bäume, die in Reihen knicken, Wasser, das 524 m hoch reißt und in Schleiern zurückfällt.
4. **Gegensatz.** Erst Stille und Spiegel, dann Gewalt, dann fahles Morgenlicht über der Narbe.
5. **Wahr bleiben.** Form, Höhe und Zeit der Welle kommen aus der Vorrechnung (Läufe `kette`, `messwelle`). Was darauf gestaltet ist, trägt [G].

---

## 2. Befund

| Befund | Zahl | Folge im Bild |
|---|---|---|
| Die Szene nutzt **16 von 64** Packages im `Renderer.jar`. Für Himmel, Wolken, Nebel, Meer, Brandung, Gischt, Fels, Eis, Wasserfall, Linsentropfen und Kino nutzt sie **keines**. | `grep import com.dan` in `src` | Lituya zeichnet mit weniger als Geiranger, Sylt oder Glacier |
| Alle eigenen Shader zusammen haben **837 Zeilen**. Himmel: 9 Zeilen Farbverlauf. Gelände: 46 Zeilen, sechs feste Farben, Lambert. Welle: 63 Zeilen. | `gl/shader/*` | Berge wie Teig, Himmel leer, Wasser wie Rauschen |
| Die Welle ist ein Höhenfeld mit weißen Flecken, wo es steil ist. | `welle.frag` | aus 2 km ein Brett (Bild 07), die Narbe eine rosa Fläche (Bild 05) |
| Keine Schatten von Gelände und Wald. | – | 4 671 m wirken wie 800 m |
| Die Netzblick-Karte steht in **allen zwölf** B0-Bildern, obwohl die Bildbank „ohne HUD und Marken“ sagt. | `docs/bild/B0` | erster Eindruck: Messgerät |
| **Gut:** Die Vorrechnung ist fein genug für große Effekte: drei Gitter zu 16 m, 4 m und 2 m (Gilbert Inlet), 601–721 Bilder, Geschwindigkeiten gespeichert (`geschw_g*.bin`). | `daten/ereignis/kette/EREIGNIS.txt` | Lippe, Schaum mit Strömung und Gischt lassen sich daraus ableiten, ohne neu zu rechnen |

Die Vorher-Bilder (B0) sind im Artefakt bei den Heldenbildern (Abschnitt 6) zu sehen.

---

## 2a. Brei und Korn: die Ursachen

Rückmeldung vom 04.11.2026 zu den Bildern in `docs/wahrHeeldenbilder` (elf Aufnahmen aus der App):

> Das ist eine breiige, körnig verpixelte Masse. Das zieht sich durch das gesamte Projekt durch. Das wirkt lieblos dahingepfuscht. Nebensächlich zu erwähnen, dass es kein Wetter gibt.

Das stimmt, und es hat feste Ursachen im Code. Keine davon ist Schicksal der Grafikkarte.

| Nr. | Ursache | Wo | Was du siehst |
|---|---|---|---|
| K1 | **Die Auflösung wird heimlich gesenkt.** Die Regelung zielt auf 60 Bilder/s und senkt die Rechenauflösung bis auf 50 %; die Grundstufe MITTEL rechnet ohnehin nur 85 %. Das kleine Bild wird danach auf das Fenster gezogen. | `Renderer.java` (`targetFps = 60`, `MIN_SCALE = 0.5`, `autoScale = true`), `Quality.MITTEL 0.85` | Brei: alles weich und unscharf |
| K2 | **Keine Kantenglättung.** Weder MSAA noch TAA noch FXAA. | `Renderer.java`, `post.frag` | Kanten treppen, Bäume und Gischt zerfallen in Pixel |
| K3 | **Wald aus der Ferne als Einzelbäume, die kleiner sind als ein Bildpunkt.** | `WaldGl`, `forest` | die Hänge sind Pixelrauschen statt Kronendach |
| K4 | **Rauschen feiner als ein Bildpunkt in den Shadern.** Gischt und Felssturz werden mit Hash-Rauschen gesprenkelt, das in der Ferne nicht gefiltert wird. | `welle.frag` (`vn(q / 0.9)`), `strom.frag` (`vn(q / 0.7)`) | weiße und graue Sprenkel, die beim Bewegen flimmern |
| K5 | **Die Welle hat aus der Nähe keine Form.** Die Fläche zeigt nur gespiegelte Himmelsfarbe ohne Feinform. | `welle.frag` | eine glatte rosa Wand über dem halben Bild |
| K6 | **Die Netzblick-Karte ist beim Abspielen immer an.** | `Netzblick`, `Renderer` | ein Messgerät im Bild |
| K7 | **Kein Wetter.** Der Regie-Befehl `regen` ändert nur, wie voll die Bäche sind; Wolken, Nebel, Wind und Regen werden nicht gezeichnet. | `Renderer.java` (`regen` → `BaecheGl`), kein `cloud`/`fog`/`rain`/`weather` eingebunden | immer derselbe leere Himmel |
| K8 | **Leere Versprechen in der Oberfläche.** Der Block WETTER zeigte seit Phase 1 nur den Satz „Kommt mit Phase 5a und 18“. Beide Phasen wurden nie gebaut (Abschnitt 2b). | `ControlPanel.java` | ein leeres Menü |
| K9 | **Staub mit festem Rauschen.** Der Strahl durch die Staubwolke beginnt je Bildpunkt an einer festen Zufallsstelle. Am dünnen Rand der Wolke entsteht so ein Muster aus Einzelpunkten, das auch die Kantenglättung nicht mitteln kann, weil es in jedem Bild gleich ist. **Das war das weiße Gesprenkel auf dem Gegenhang in deinen Aufnahmen**, nicht der Wald (meine erste Annahme in K3). | `medien.frag` | weiße Punkte über dem ganzen Hang hinter der Welle |
| K10 | **Wellenfläche und Gelände durchdringen sich.** Wo das Wasser dünn über Land läuft, liegen Fläche (Gitter 2–16 m) und Gelände (feiner) fast aufeinander; Bildpunkt für Bildpunkt gewinnt mal das eine, mal das andere. | `spur.vert` | Körnung am Rand des Auflaufs |

**Folge für den Plan:** Bevor irgendein Effekt kommt, muss das Bild scharf und ruhig sein. Deshalb wird W0 zur Phase „Scharf“ (Abschnitt 8). Sonst wird jede neue Gischt wieder zu Korn.

## 2b. Was nie gebaut wurde: das strukturelle Problem

Deine Rückmeldung vom 04.11.: „Die Aufnahmen waren exemplarisch, um ein strukturelles Problem aufzuzeigen. Problematik zieht sich durch das Projekt durch. … Wetter vergessen (Menü ist leer).“

**Befund:** Das erste Werkbuch hatte eine Phase **5a „Grundbild: Gelände, Texturen, Himmel, Wasser mit Gletschermilch“**. Sie wurde nie gebaut. Alle späteren Phasen (Wald, Welle, Boote, Netze, Klang, Datenbank, Archiv, Schliff) haben auf die Platzhalter von Phase 1 gezeichnet und sie mit „bis Phase 5a“ vertröstet. Phase 20 hat das Projekt „fertig“ genannt und 5a, 6a, 18b, 18c als „Nachträge“ geführt. Im Code steht das an rund 30 Stellen:

| Was | Stand im Code | Versprochen in | Kommt jetzt in |
|---|---|---|---|
| Himmel | `sky.frag` 9 Zeilen Verlauf, „Platzhalter bis com.dan.sky in Phase 5a“ | 5a | **W1** |
| Licht, Schatten | Lambert ohne Schatten in `gelaende.frag` | 5a | **W1** |
| Wasser | `meer.frag` Rauschen, „Platzhalter … bis ocean mit Gletschermilch in Phase 5a“ | 5a | **W2** |
| Gelände, Texturen | sechs feste Farben, „Platzhalter (Phase 4, bis surface in Phase 5 und Grundbild in 5a)“; 13 Texturen gebacken, nie aufs Gelände gelegt | 5, 5a | **W6** |
| Unterwuchs, Gletscher, Geröll | – | 6a | **W6** |
| Wetter (Wolken, Nebel, Regen, Wind) | Block WETTER leer; `Wiring`: Wind „ab Phase 5a aus der Wetterregie“ | 5a, 18b | **W7** |
| Polarlicht | – | 18b | W7 (nur „heute“) |
| Leben (Bären, Adler, Otter) | – | 18c | offen, nach W8 |
| Wasser durch den ganzen Gleitkörper | – | 10b | offen (Physik, nicht Bild) |

### 2b.1 Was ich daraus ändere

1. **Keine Phase heißt „fertig“, solange ein Platzhalter im Bild steht.** Die Versprechensliste (2b.2) gehört zu jeder Abnahme. Ein Eintrag verlässt sie nur mit einem Bild, das ihn zeigt.
2. **Kein leerer Block im Bedienfeld.** Was es noch nicht gibt, steht dort ehrlich mit der Phase, die es bringt. `ant pruefung` prüft seit W0, dass im Bedienfeld kein „kommt mit Phase …“ statt Inhalt steht.
3. **Nachträge werden Phasen.** 5a, 6a und 18b sind keine Nachträge mehr, sondern W1, W2, W6 und W7 dieses Werkbuchs.
4. **Jede Abnahme an deinen Bildern.** Deine elf Aufnahmen sind jetzt eine feste Liste der Bildbank (`scene/aufnahmen.txt`, Kamera und Zeit aus `LB_SCHNAPPSCHUSS`) und werden in jeder Phase mitgerechnet.

### 2b.2 Versprechensliste

| Nr. | Versprechen | Wo es steht | Phase | Stand |
|---|---|---|---|---|
| V1 | Himmel aus `sky` (Dämmerung, Alpenglühen, Erdschatten) | `sky.frag`, `common.glsl`, `Renderer` | W1 | **im Bild seit W1** (`gl/Licht`, H1–H3) |
| V2 | Schatten der Kette und des Walds | `gelaende.frag` | W1 | **im Bild seit W1** (Horizontkarten; Schatten einzelner Bäume aufeinander noch nicht) |
| V3 | Wasser aus `ocean`, Spiegelung, Gletschermilch | `meer.frag` | W2 | offen; seit W1 spiegelt es den echten Abendhimmel, mit dem alten Rauschen wirkt es wie Öl (Befund W1) |
| V4 | Texturen aufs Gelände, Farbe aus Sentinel-2 | `gelaende.frag`, `daten/texturen` | W6 | offen |
| V5 | Wetter: Lagen, Übergänge, Wind, Regen im Bild | Block WETTER, `Wiring` | W7 | offen; Block zeigt seit W0 den Regen für die Bäche und kündigt W7 an |
| V6 | Staub mit Licht (oben in der Sonne, unten blau) | `medien.frag`, Befund 13b „heller Schleier mit harter Unterkante“ | W3 | offen |
| V7 | Welle mit Form (Lippe, Durchlicht, Weißwasser) | `welle.frag` (K5) | W4 | offen |
| V8 | Unterwuchs, Gletscher, Geröll | 6a | W6 | offen |
| V9 | Polarlicht | 18b | W7 | offen |
| V10 | Leben in der Szene | 18c | nach W8 | offen |

## 3. Das Vorhandene: 64 Packages, 16 genutzt, dazu Nature

Was im `Renderer.jar` liegt, für die Szene aber brachliegt, und was es im Bild bringt:

| Package | Klassen (Auswahl) | Was es im Bild bringt | Phase |
|---|---|---|---|
| `sky` | Atmosphere, Scattering, Celestial, GreenFlash, Halo | Himmel der Julinacht: Gold an der Kimm, Venusgürtel, Erdschatten, Alpenglühen | W1 |
| `ocean` | Fft, Spectrum, SeaState, Ripples, Obstacle, HullMask, underwater | Meer statt Rauschen, Spiegel der Berge, Ringe beim Beben, Unterwasserblick | W2 |
| `spray` | Spray, Parcel, Body | Gischtvorhang beim Einschlag, Kammgischt an der Front | W3, W4 |
| `dustcloud` | DustCloud (schon eingebunden) | Staubwolke bekommt Licht: oben orange in der Sonne, unten blau | W3 |
| `cliff` | Cliff, CliffMap, StrataMap, Overhang, Blockfield | Abrisskante, Blöcke, Felswände mit Schichtung | W3, W6 |
| `ice` | Glacier, GlacierWall, FrontBlock, IceOptics | Gletscherfront im Gilbert Inlet, Eisbrocken beim Einschlag, Gletscher mit Spalten | W3, W6 |
| `surf` | BreakerShape, BreakerMesh, FoamField, Swash, WaveTrain | überhängende Lippe der Front, Schaum mit Strömung, Auflaufzunge | W4, W5 |
| `debris` | Debris, Carpet (schon eingebunden) | Stämme rollen in der Front, Treibholzteppich danach | W4, W8 |
| `forest` | TreeAnimator, Wind, WindField (schon eingebunden) | Bäume knicken in Reihen statt umzuschalten, Wind im Wald | W5 |
| `fall` | Fall | Wasser reißt am Sporn ab und fällt in Schleiern zurück; Wasserfälle an den Hängen | W5, W6 |
| `surface` | Material, HexTiling, Bake (13 Texturen gebacken, nie aufs Gelände gelegt) | Fels, Geröll, Moos, Schnee mit echter Oberfläche | W6 |
| `cloud` | Clouds, CloudType | Wolkenkappe am Fairweather, Bänke an der Kette | W7 |
| `fog` | Fog, TerrainFog, Wind | Nebel in den Tälern, Dunst über dem Wasser nach der Welle | W7, W8 |
| `vessel`, `buoyancy`, `wake` | (vessel schon eingebunden) | Die Edrie reitet die Welle, Kielwasser | W8 |
| `wetglass` | – | Tropfen auf der Linse im Bootsblick und im Regen | W7, W8 |
| `flock` | – | Möwen fliehen vor der Front | W8 |
| `weather`, `rain`, `wind`, `season` | – | Wetterlagen mit Übergängen, Regen, Wind in Wasser und Wald, Herbst und Neuschnee | W7 |
| `kino` | Kino, KinoBild, KinoMaler | Kinomodus: Breitbild, Korn, Farbe je Zeitstand | W9 |
| `soundscape` | (schon eingebunden) | Klang an die neuen Effekte koppeln, Regen und Wind hörbar | W7, W9 |

**Projekt Nature** (`NetBeansProjects\Nature`, seit 04.11. freigegeben, nur lesen):

| Package | Inhalt | Wofür hier | Phase |
|---|---|---|---|
| `com.dan.tree` | echte Baummodelle als `.dtree`: Schwarzerle (`blackalder`, vier Formen), Baltische Kiefer, Buche, `supertree`, `hptree`, `trees60`, Greasewood; eigener GL-Zeichner `TreeGl` | nahe Bäume als echte Modelle statt Pixelpunkte; die Erle steht für die Erlen, die heute in der Narbe wachsen | W6 |
| `com.dan.ground` | 4K-Bodentexturen (grober und rauer Boden), Felsen | Nahbereich am Ufer und im Band der Narbe | W6 |
| `com.dan.grass` | Wildgras, Trockengras, Weizengras, Vegetationsmix | Ufersäume und Lichtungen | W6 |

Eine Sitka-Fichte fehlt in Nature. Sie kommt aus dem Baumgenerator in `forest` mit Form und Rinde nach Foto [zu prüfen in W6, ob die Kiefer als Vorlage für Nadeln und Zweige taugt].


---

## 4. Die Welle: vierzehn Effekte

In der Reihenfolge des Ereignisses. Zeiten nach dem ersten Stoß; Physik aus der Vorrechnung, Darstellung [G].

| Nr. | Effekt | Was man sieht | Wie | Package | Phase |
|---|---|---|---|---|---|
| E1 | **Die Erde bebt** | Kamera zittert, Lawinen stauben von den Hängen, der Spiegel der Bucht zerfällt in Ringe | Stärke aus `quake`, Ringe aus `ocean.Ripples`, Schneestaub als kleine Wolken an steilen Schneeflächen [G] | quake, ocean, dustcloud | W3 |
| E2 | **Abbruch** | Die Wand löst sich als Platte, frische helle Abrisskante, Blöcke mit Fels-Material, Staub aus jeder Fuge | Blöcke aus `fracture` mit `cliff`-Material, Kante als eigenes Netz | fracture, cliff, dustcloud | W3 |
| E3 | **Staub im letzten Licht** | Die Staubwolke steigt über die Kante; wo sie höher als rund 1 000 m reicht, steht sie noch in der Sonne und glüht orange, darunter blau-grau | Sonne 1° unter der Kimm (22:16, gerechnet im ersten Werkbuch 9.10); Sichtbarkeit der Sonne ab rund 1 000 m [gerechnet, ohne Brechung]; Licht aus `sky` in `dustcloud` | dustcloud, sky | W3 |
| E4 | **Einschlag** | Das Wasser weicht aus, eine Kaverne öffnet sich, ein Dom aus Wasser und Schlamm steigt | Form aus dem Laborfall in `impact` (SPH im Schnitt) als Rotationskörper längs der Linie gestreckt [G] | impact | W3 |
| E5 | **Der Vorhang** | Eine Wand aus Gischt steigt mehrere hundert Meter, leuchtet im Gegenlicht und fällt als Regen zurück | `spray` im Maßstab 100 m statt 10 m, Teilchen nah, Volumen fern; Streuung nach Henyey-Greenstein | spray | W3 |
| E6 | **Die Gletscherfront bricht** | Bis 400 m Eis abgeschert (belegt, FRITZ01): weiße Brocken, Eisstaub, danach eine frische blaue Wand | `ice.FrontBlock`, `GlacierWall`, `IceOptics` | ice | W3 |
| E7 | **Die Wand** | Die Front als steile Wand mit überhängender Lippe, die sich einrollt | Fein-Netz längs der Front aus dem 2-m- und 4-m-Gitter; Lippe aus Steilheit und Steiggeschwindigkeit nach `BreakerShape`/`BreakerMesh` [G] | surf, wave | W4 |
| E8 | **Durchlicht** | Der dünne Kamm leuchtet von hinten: türkis wo klar, braun wo Schlamm | Dicke des Kamms aus dem Netz, Absorption je Farbkanal, Trübe aus der Höhe über dem Pegel | neu in `welle.frag` | W4 |
| E9 | **Weißwasser und Kammgischt** | An der Front eine Walze aus Weißwasser; von der Krone reißt Gischt und weht zurück | Volumen dicht über der Front; Teilchen aus `spray`, ausgelöst wo die Lippe bricht | spray, surf | W4 |
| E10 | **Schaumspur** | Hinter der Front Streifen, Wirbel, Teppich aus Schaum, der mit der Strömung zieht | Schaumfeld mit den gespeicherten Geschwindigkeiten mitgetragen, klingt ab | surf.FoamField | W4 |
| E11 | **524 m** | Eine Wasserzunge schießt den Sporn hinauf, Bäume knicken reihenweise, oben reißt das Wasser ab und fällt in Schleiern zurück, Erde und Stämme werden mitgerissen | Auflauf aus der Vorrechnung, Rand als `Swash`; Folge der Bäume aus `succession`, Bewegung mit `TreeAnimator`; Rückfall mit `fall` | surf, forest, succession, fall, debris | W5 |
| E12 | **Treibgut in der Front** | Stämme rollen in der Walze, stellen sich auf, werden an Land geworfen | `debris` mit Stämmen aus `forest`, Lage aus der Strömung | debris | W4 |
| E13 | **Die Bucht hinab** | Die Front frisst den Uferwald, Möwen fliehen, ein Kutter wird angehoben und reitet die Welle | Front aus der Vorrechnung, Boot aus `vessel`/`buoyancy`, Kielwasser `wake`, Tropfen auf der Linse `wetglass` | vessel, flock, wetglass | W8 |
| E14 | **Danach** | Schaumteppich, Treibholz von Ufer zu Ufer, Dunst, Schwappen der Bucht; fahles Morgenlicht über der hellen Narbe | `debris.Carpet`, `fog`, Seiche aus der Vorrechnung | debris, fog | W8 |

**Grenze:** Die Physik wird nicht neu gerechnet. Höhe, Zeiten und Auflauf bleiben die geprüften Werte (Auflauf 514 m im Lauf gegen 524 m belegt; Front an Cenotaph Island nach 227 s).

---

## 5. Die Landschaft: zehn Aufwertungen

| Nr. | Was | Wie | Package | Phase |
|---|---|---|---|---|
| L1 | **Himmel der Julinacht** | Gold an der Kimm im NW, Venusgürtel, Erdschatten im SO; Alpenglühen auf Fairweather (4 671 m) und Crillon (3 879 m) | sky | W1 |
| L2 | **Schatten** | Die Kette wirft Schatten über die Bucht; Selbstschatten der Hänge; Wald schattet den Boden | Horizontkarte, Kaskadenschatten | W1 |
| L3 | **Luft** | Luftperspektive trennt Vorder-, Mittel- und Hintergrund; Dunst in der Tiefe der Bucht | sky, fog | W1 |
| L4 | **Fels** | Wände mit Schichtung, Rinnen, Überhängen; Normalen aus dem Lidar (0,5 m) | cliff | W6 |
| L5 | **Material und Farbe** | Die 13 gebackenen Texturen kommen aufs Gelände (Fels, Geröll, Moos, Kies); Farbe der Hänge aus Sentinel-2 statt sechs fester Farben | surface | W6 |
| L6 | **Schnee und Gletscher** | Schnee mit Form an Graten und Windseiten; Lituya-, Crillon- und Cascade-Gletscher mit Spalten und Blaueis; Fronten in den Inlets | ice | W6 |
| L7 | **Wasser** | Spiegel der Berge, Gletschermilch-Türkis zum Inlet hin, Uferlinie, Katzenpfoten im Wind | ocean | W2 |
| L8 | **Wald** | dunkle Silhouetten gegen den hellen Himmel, Licht durch die Kronen, Wind | forest | W6 |
| L9 | **Wasserfälle** | Die Bäche aus Phase 16c fallen als Schleier über die Steilstufen in die Bucht | fall | W6 |
| L10 | **Wolken und Nebel** | Kappe am Fairweather, Bänke an der Kette, Nebel in den Seitentälern | cloud, fog | W7 |

### 5.1 Nahbereich

Wo die Kamera nah am Ufer oder im Wald steht, zeichnen echte Bäume aus Nature und dem Baumgenerator, dazu Gras und 4K-Boden. Ab rund 1–2 km [G] übernimmt ein Kronendach als Textur auf dem Gelände mit Relief und Licht. So gibt es weder Pixelrauschen in der Ferne noch Pappaufsteller in der Nähe.

---

## 5a. Wetter

Die Bucht lebt vom Wetter: Nebel, der durch den Eingang zieht, Wolkenbänke an der Kette, Regen, der die Wasserfälle anschwellen lässt. Das gehört zur Landschaft und kommt als eigene Phase W7.

### 5a.1 Wetterlagen

| Lage | Zeitstand | Was man sieht | Herkunft |
|---|---|---|---|
| **Klarer Abend** | 9.7.1958, vor der Welle | still, Spiegel, Alpenglühen, Dunst nur in der Tiefe | Zeugen berichten ruhige See [zu prüfen mit ERA5] |
| **Grauer Morgen** | 10.7.1958, danach | Dunst über dem Wasser, tiefe Wolken [zu prüfen mit ERA5], fahles Licht | ERA5 |
| **Sonne und Quellwolken** | heute | Wolken bauen sich an der Kette auf, Kappe am Fairweather, Schatten ziehen über die Hänge | [G] |
| **Küstennebel** | heute | eine Nebelbank zieht durch den Eingang in die Bucht, Gipfel ragen heraus, Wald taucht ein und auf | [G] |
| **Regen** | heute | tiefe Wolken an den Hängen, Regenschleier über dem Wasser, Ringe auf der Fläche, nasser Fels glänzt, Wasserfälle schwellen an, Tropfen auf der Linse | [G] |
| **Sturm an der Barre** | heute, Herbst | Brecher am Eingang gegen den Ebbstrom, Gischt, Wolkenfetzen | [G] |
| **Neuschnee** | heute, Herbst | weiße Gipfel bis tief in die Hänge, gelbes Laub der Erlen in der Narbe | [G] |
| **Winternacht** | heute | Sterne, Mond, Spiegel im schwarzen Wasser; Polarlicht [zu prüfen, ob `sky` es hat] | [G] |

### 5a.2 Wie es wirkt

- **Ein Zustand für alles:** Bewölkung, Wolkenhöhe, Feuchte, Wind, Niederschlag, Schneegrenze. Licht, Wasser, Wald, Fels, Bäche und Klang lesen denselben Zustand.
- **Übergänge statt Umschalten:** Eine Wetterlage geht in Minuten in die nächste über; im Zeitraffer sieht man Wolken aufziehen und Nebel steigen.
- **Bedienung:** Wetter im Bedienfeld als Auswahl mit Regler für den Übergang; im Regiebuch als Befehl `wetter`.
- **Wind:** wirkt auf Katzenpfoten und Wellen (`ocean`), Bäume (`forest.Wind`), Nebel und Gischt.
- **Regen:** Schleier (`rain`), Ringe (`ocean.Ripples`), nasse Oberflächen (dunkler, glänzender), Abfluss in die Bäche aus Phase 16c, Tropfen auf der Linse (`wetglass`).

---

## 6. Inszenierung

### 6.1 Neun Heldenbilder (Abnahme jeder Phase)

Jede Phase wird an diesen Bildern gemessen, dazu an deinen elf Aufnahmen in `docs/wahrHeeldenbilder`: vorher (B0) und nachher nebeneinander, gerendert auf der Radeon.

| Nr. | Name | Zeit | Linse | Was es zeigen muss | Vorher |
|---|---|---|---|---|---|
| H1 | **Die letzte Ruhe** | 22:10 | 50 mm | Spiegel der Kette im Wasser, Alpenglühen, Kutter vor Anker | B0/01 |
| H2 | **Fairweather** | 22:10 | 200 mm | Kette in Schichten, Schnee mit Form, Maßstab | B0/02 |
| H3 | **Gilbert Inlet** | 22:15 | 35 mm | Gletscherfront, Wand, Wasserfälle, Stille vor dem Sturz | B0/03 |
| H4 | **Der Sturz** | t ≈ 96 s | 85 mm | Platte löst sich, Abrisskante, Staub glüht | B0/04 |
| H5 | **Der Vorhang** | t ≈ 104 s | 24 mm | Gischtvorhang im Gegenlicht, Kaverne | B0/05 |
| H6 | **524 m** | t ≈ 106–140 s | 135 mm | Wasser am Sporn, Bäume knicken, Rückfall in Schleiern | B0/06 |
| H7 | **Die Wand** | t ≈ 170 s | 24–50 mm | Front vor Cenotaph aus Bootshöhe: Lippe, Durchlicht, Weißwasser, Stämme | B0/07 |
| H8 | **Danach** | 10.7., Morgen | 35 mm | Narbe bis 524 m, Treibholzteppich, Dunst | B0/09 |
| H9 | **Nebel zieht ein** | heute, Vormittag | 35 mm | Nebelbank im Eingang, Gipfel darüber, Wolken an der Kette, Regen im Hintergrund | B0/12 |

### 6.2 Start der App

Kein HUD, keine Marken, keine Karte. Ein Startflug von 20 s [G]: von See über die Barre am Eingang, an Cenotaph Island vorbei auf den Gilbert Inlet zu, 22:10, Alpenglühen. Danach freie Kamera an dieser Stelle. Abbrechen mit einer Taste.

### 6.3 Regiebuch „Die Welle“ (90 s)

Ruhe (H1) → Beben (E1) → Sturz und Staub (H4) → Vorhang in Zeitlupe 1/8 (H5) → 524 m (H6) → die Wand aus Bootshöhe (H7) → Stille, Dunst, Treibholz → Morgen (H8). Die bestehenden Regiebücher bleiben und profitieren von allem.

### 6.4 Kinomodus

Breitbild 2,39 : 1, Filmkorn, Farbe je Zeitstand, Tiefenschärfe und Bewegungsunschärfe, Zeitlupe am Einschlag. Aus `kino`.

---

## 7. Zwei Stufen: Echtzeit und Kino

| Stufe | Ziel | Was an ist |
|---|---|---|
| **Echtzeit** | 1080p, Stufe HOCH, ≥ 30 Bilder/s auf der Radeon 680M (Ryzen 7 7735HS, 2 GB) | alles, aber Gischt und Staub mit weniger Teilchen und gröberem Volumen, Spiegel in halber Auflösung, Schatten in drei Kaskaden |
| **Kino** | Film und Foto, Bild für Bild, bis 4K, ohne Zeitdruck (`ant film`, Fotomodus) | alles voll: dichte Gischt, feines Volumen, volle Spiegel, Akkumulation gegen Kanten und Rauschen, Bewegungsunschärfe |

Die Bildbank rendert die Heldenbilder in beiden Stufen.

---

## 8. Phasen W0–W9

Jede Phase beginnt mit dem Satz **„Danach siehst du …“** und endet mit einer Vorher/Nachher-Tafel der genannten Heldenbilder plus einem kurzen Clip. Freigabe wie gewohnt mit „PhaseWn, los!“.

| Phase | Name | Danach siehst du | Inhalt | Abnahme |
|---|---|---|---|---|
| **W0** | Scharf | ein scharfes, ruhiges Bild ohne Brei, Korn und Karte; deine elf Aufnahmen nachgestellt, vorher und nachher | K1–K6: Rechenauflösung in HOCH fest 100 %, Regelung nur noch auf 30 Bilder/s und nie unter 85 %; Kantenglättung TAA (Bewegung aus Kamera und Höhenfeld), FXAA als Rückfall; Rauschen in den Shadern gefiltert; Fernwald als Kronendach statt Pixelpunkte; Netzblick-Karte aus; B0-Stand committen, H1–H9 festlegen | deine elf Aufnahmen und H1–H9 vorher/nachher; Flimmermaß · **gebaut 04.11., siehe 12** |
| **W1** ✓ gebaut 04.11. | Licht | einen echten Dämmerhimmel, Alpenglühen auf den Gipfeln, Schatten der Kette über der Bucht, Berge mit Form | L1–L3: `sky` statt Platzhalter, Horizontkarte und Kaskadenschatten, Himmelslicht, Luftperspektive, Filmkurve, Bloom | H1, H2, H3 |
| **W2** | Spiegel | Wasser, das die Berge spiegelt, Gletschermilch, eine lebendige Fläche | L7: `ocean` (FFT, Ripples), Spiegelung, Farbe zum Inlet hin, Uferlinie, Unterwasser | H1, H7 (Wasser vor der Welle) |
| **W3** | Der Sturz | Fels bricht, Staub glüht im letzten Licht, der Gischtvorhang steigt, die Eisfront bricht | E1–E6 | H4, H5, Clip 20 s |
| **W4** | Die Wand | die Front als brechende Wand mit Lippe, Durchlicht, Weißwasser, Kammgischt, Schaumspur und Stämmen | E7–E10, E12 | H7, Clip 20 s |
| **W5** | 524 m | Wasser schießt den Sporn hinauf, Bäume knicken in Reihen, Wasser fällt in Schleiern zurück | E11 | H6, Clip 20 s |
| **W6** | Landschaft | Fels mit Schichtung, echte Oberflächen, Gletscher mit Spalten, Wasserfälle, echte Bäume in der Nähe | L4–L6, L8, L9, 5.1 (Nature) | H2, H3, H1 |
| **W7** | Wetter | Wolken an der Kette, Nebel, der durch den Eingang zieht, Regen mit Ringen und Tropfen, Wind in Wasser und Wald, Wetterwechsel im Zeitraffer | L10, 5a | H9, H1 bei Regen, Zeitraffer 60 s |
| **W8** | Die Bucht | die Front die Bucht hinab, ein Kutter reitet die Welle, Möwen fliehen, danach Treibholz und Dunst | E13, E14 | H7 aus dem Boot, H8 |
| **W9** | Kino | Startflug, Regiebuch „Die Welle“, Kinomodus, Film in 4K mit Klang, neue README-Bilder | 6.2–6.4, Klang | Film „Die Welle“ (90 s), alle neun Heldenbilder |

Reihenfolge nach WF3 A: erst scharf, dann Licht (es hebt alles), dann Wasser, dann die Welle in drei Schritten, dann Landschaft und Wetter, zum Schluss das Kino.

---

## 9. Arbeitsweise

1. **Keine Phase ohne Bild.** Jede Phase endet mit Bildern, die du ansiehst. Reine Vorarbeit gibt es nicht mehr als eigene Phase.
2. **Ansage vorher.** Vor jeder Phase ein Satz, was du danach siehst; unterwegs Zwischenbilder, sobald etwas zu sehen ist.
3. **Auf deiner Radeon gemessen.** Die Abnahmebilder rendert `ant bildbank` auf deinem Rechner, nicht die Vorschau hier.
4. **Physik bleibt.** Form, Höhe, Zeiten aus der Vorrechnung; Effekte darauf tragen [G].
5. **Sofort sagen, wenn etwas nicht geht.** Passt ein Effekt nicht ins Budget, zeige ich die Alternative, statt ihn still wegzulassen.
6. **Fortschritt kurz.** Abschnitt 12 bekommt je Phase eine Zeile und die Tafel.

---

## 10. Risiken

| Risiko | Folge | Gegenmittel |
|---|---|---|
| 2 GB Grafikspeicher | Spiegel, Schatten, Gischt, Wolken zusammen zu viel | Stufen (Abschnitt 7), Spiegel in halber Auflösung, Kino-Stufe für das Volle |
| Vorschau hier (Mesa) ≠ Radeon | Wirkung erst auf deinem Rechner sicher | jede Phase endet mit `ant bildbank` auf der Radeon |
| `spray`, `surf` sind für Brandung im Meter-Maßstab gebaut | Gischt zu fein, Lippe zu klein | Maßstab als Parameter, Teilchen nah plus Volumen fern [G] |
| W4 ist groß | Phase zieht sich | Zwischenbild nach Lippe und Durchlicht, dann Weißwasser und Schaum |
| Schauwert gegen Wahrheit | Glaubwürdigkeit | WF5 A: Form aus der Vorrechnung, Effekte gekennzeichnet |
| Änderungen am Renderer verändern andere Szenen | Geiranger, Sylt, Glacier sehen anders aus | WF10 A: neue Effekte zuerst in `com.dan.lituya`, Rückfluss erst nach Abnahme |

---

## 11. Fragen WF1–WF14

Je Frage Möglichkeiten, **A** ist die Empfehlung. „Alle A“ genügt.

**Antwort 04.11.2026: alle A.** Damit gilt: Werkbuch Bild ist Ideenspeicher (WF1), B0-Stand wird übernommen (WF2), Reihenfolge Scharf → Licht → Wasser → Welle → Landschaft → Wetter → Bucht → Kino (WF3), Echtzeit und Kino getrennt (WF4), Form aus der Vorrechnung (WF5), Fein-Netz der Front (WF6), Teilchen nah und Volumen fern (WF7), Kamera-Effekte im Kino immer (WF8), Vorher/Nachher-Tafel plus Clip (WF9), neue Effekte zuerst in `com.dan.lituya` (WF10), keine Karte beim Start und in Heldenbildern (WF11), die Edrie reitet die Welle (WF12), klarer Abend 1958 nach den Zeugen (WF13), echte Bäume nah und Kronendach fern (WF14).

| Nr. | Frage | Möglichkeiten |
|---|---|---|
| WF1 | Verhältnis zum Werkbuch Bild | **A** Werkbuch Welle ersetzt den Phasenplan B1–B16, Werkbuch Bild bleibt als Ideenspeicher · B beide parallel · C Werkbuch Bild verwerfen |
| WF2 | B0-Stand (Bildbank-Werkzeug, Brennweite, Ist-Bilder; nicht committet) | **A** übernehmen und in W0 committen · B verwerfen |
| WF3 | Reihenfolge | **A** Scharf → Licht → Wasser → Welle (W3–W5) → Landschaft → Wetter → Bucht → Kino · B Welle zuerst (W3–W5), dann Licht und Landschaft · C Landschaft komplett vor der Welle |
| WF4 | Stufen | **A** Echtzeit und Kino getrennt (Abschnitt 7) · B nur Echtzeit |
| WF5 | Wahrheit und Schauwert | **A** Form und Zeiten aus der Vorrechnung, Effekte [G] gekennzeichnet · B freie Übertreibung erlaubt, wo es wirkt |
| WF6 | Form der Front | **A** Fein-Netz längs der Front aus dem 2-m-/4-m-Gitter plus Lippe als eigenes Netz · B Live-Rechnung in höherer Auflösung |
| WF7 | Gischt und Staub | **A** Teilchen nah, Volumen fern · B nur Teilchen · C nur Volumen |
| WF8 | Kamera-Effekte (Zittern, Tropfen, Bewegungsunschärfe, Zeitlupe) | **A** in Kino immer, in Echtzeit abschaltbar · B überall aus |
| WF9 | Abnahme | **A** je Phase Vorher/Nachher-Tafel der Heldenbilder plus Clip · B nur Standbilder |
| WF10 | Wo die neuen Effekte entstehen | **A** zuerst in `com.dan.lituya`, Rückfluss in den Renderer nach Abnahme · B direkt in den Renderer-Packages |
| WF11 | Netzblick, HUD, Marken | **A** beim Start und in Heldenbildern ganz aus, nur über das Menü · B Karte klein in der Ecke lassen |
| WF12 | Boot in W8 | **A** die Edrie (Ulrich) reitet die Welle, Blick vom Deck · B nur Boote von außen |
| WF13 | Wetter am 9.7.1958 | **A** klarer Abend nach den Zeugen, mit ERA5 geprüft; Wetterlagen frei nur „heute“ · B Wetter auch 1958 frei wählbar, als [G] gekennzeichnet |
| WF14 | Bäume in der Nähe | **A** echte Modelle aus Nature und dem Baumgenerator in der Nähe, Kronendach in der Ferne · B nur der Baumgenerator aus `forest` |

---

## 12. Fortschritt

| Datum | Phase | Was | Bilder |
|---|---|---|---|
| 04.11.2026 | **W1** | **Licht, gebaut** (Freigabe „PhaseW1, los!“). Danach siehst du: den Abendhimmel aus `com.dan.sky`, Alpenglühen auf Fairweather, Crillon und den Graten, die Bucht im Schatten der Kette, Gipfel klar über dem Dunst.<br>• **Neu `gl/Licht`:** `Sky` mit Ort Lituya, Sicht 60 km [G], Augenhöhe der Kamera; `SkyGl` zeichnet den Himmel (statt `sky.frag`) und ein Rundumbild 512 × 256 mit Mip-Stufen, aus dem `common.glsl` Spiegelung, Himmelslicht und Dunstfarbe liest (`uSkyMode = 1`, seit Phase 1 vorbereitet, nie benutzt). Vorbelichtung aus Himmel und Blickfeld (Anpassung 0,8, geglättet, im Standbild sofort), Nachbearbeitung danach mit 1; Stäbchensehen aus `sky`.<br>• **Horizontkarten (L2):** `schatten.comp` sucht je Bodenpunkt den höchsten Geländewinkel in Richtung Sonne (Erdkrümmung, Brechung k = 0,13, mindestens die Kimm des Meers), fein 24 km / 2048² über den Kern, grob 66 km / 1024² über die Szene (Fairweather, Crillon). Höhen aus dem Kachelstapel in 1,4 s, Karten in 83–106 ms, neu nur bei 0,25° Azimut. Ob Sonne, entscheidet der Shader aus der Sonnenhöhe: das Alpenglühen steigt beim Sinken der Sonne von selbst.<br>• **Sonne am Punkt (`sunAt`):** Durchlässigkeit nach Höhe und Sonnenhöhe (Rayleigh 8 km, Dunst 1,2 km, streifend nach Chapman); auf 4 000 m bei 0,5° unter der Kimm rot, rund ein Fünftel im Rot. Gelände, Welle, Wasser, Felssturz, Bäche und Staub (je Schritt) nutzen sie; der Staub glüht damit oben, sobald W3 ihn zeigt.<br>• **Wald und Boote:** Licht aus demselben Himmel, Rundumbild für den Dunst, Bergschatten als Karte von oben (`sonnenkarte.comp`).<br>• **Luft (L3):** zwei Schichten, Luft 8 km und Dunst 1,2 km Skalenhöhe; erste Probe zu dicht, Dunst auf 0,45 gewichtet.<br>• **Glühen (Bloom)** aus den Mip-Stufen des geglätteten Bilds; Schalter „Himmel und Licht aus com.dan.sky (W1)“ und „Glühen“ im Block TECHNIK; HUD zeigt Vorbelichtung und Stand des Lichts.<br>• **Prüfung:** `ant pruefung` **273 Prüfungen, alles in Ordnung** (5 Hinweise wie bisher); neu: Gitter decken Fairweather, Crillon, Gilbert Inlet; Shader im Klassenpfad; Kimm aus 4 000 m 1,89°.<br>• **Radeon, Heldenbilder** (1920 × 1080, Hoch): 11,2–37,2 ms, 1–4 ms mehr als W0 (Himmel, Rundumbild, Sonne je Punkt). **Deine Aufnahmen:** 21–41 ms (W0: 24–40); mit der Welle im Bild (t = 9–24 s) über 33 ms, dort regelt die App auf 85 %<br>• **Befunde für die nächsten Phasen:** (1) Das Wasser spiegelt jetzt den echten Himmel, aber mit dem Rauschen von Phase 1 wirkt es wie Öl: W2. (2) Die Hänge sind im Schatten gleichförmig braun: Texturen und Farbe aus Sentinel-2 in W6. (3) Die Welle bleibt das glatte Brett: W4. (4) Licht nur für den 9.7.1958 geprüft; „heute“ und Winternacht mit Sternen in W7. | `docs/bild/W1/Vergleich_W1.png`, `docs/bild/W1A/Vergleich_W1A.png` |
| 04.11.2026 | **W0** | **Scharf, gebaut** (Freigabe „Alle A. Lass uns mit Phase 1 mal anfangen“).<br>• **K1 Auflösung:** Start in „Hoch“ mit 100 % (vorher „Mittel“ 85 %, Regelung bis 50 %); Regelung auf 30 Bilder/s, nie unter 85 %; Regler im Bedienfeld steht auf 100.<br>• **K2 Kantenglättung:** neuer Durchgang `taa.frag` (Versatz Halton 2/3 in 8 Lagen, Verlauf über Tiefe und Blickmatrix des vorigen Bilds in double zurückgeholt, Klammer an die Nachbarschaft in YCoCg, Verlauf mit Catmull-Rom gelesen, Grelles gedämpft); Verlauf verworfen bei neuer Größe, Ansicht, Schaustück und Sprung über 200 m. Ohne TAA FXAA in `post.frag` (`Renderer.taa`, `Renderer.fxaa`). Zeit unter „Nachbearbeitung“: 1,3–2,4 ms.<br>• **K4 Rauschen:** in `welle.frag`, `strom.frag`, `meer.frag` blendet jede Rauschlage zu ihrem Mittel aus, sobald ein Bildpunkt mehr als rund ein Drittel ihrer Periode deckt (`fwidth`); das Wasser ist kein Rauschteppich mehr.<br>• **K9 Staub:** der Versatz im Strahl wandert von Bild zu Bild (`uBild` in `common.glsl`), die Kantenglättung mittelt ihn: aus dem weißen Gesprenkel wird ein weicher Staubsaum.<br>• **K10 Welle und Gelände:** `spur.vert` rückt die Fläche um 0,15 % (Strom bis 0,2 %, wo er dünn ist) des Abstands auf dem Sehstrahl zur Kamera; Bildort unverändert.<br>• **K3 Fernwald:** Einzelbäume bis 3 km (in „Hoch“ vorher 4,5 km), Ausdünnen ab 55 %; dahinter Kronendach im Gelände aus dem Dichtegitter des Walds (Bestände 90 m, Gruppen 25 m, Kronen 9 m, Erlen heller). Erste Fassung mit 1,8 km machte den Gegenhang zu glatt; zurückgenommen.<br>• **K6, WF11:** Netzblick-Karte beim Start und in Schaustücken aus, keine Netzlinien mehr im Schaustück; Tempo zeigt die Karte nur mit `netzblick=an` oder gewähltem Netz; Bildbank immer ohne.<br>• **K8 WETTER:** Regler „Regen“ aus NETZE in WETTER, Text kündigt W7 an; Hilfe angepasst.<br>• **Bildbank:** `-Dbildbank=` (eigene Liste), `-Dbreite`, `-Dhoehe`, Schlüssel `hoehe` (Auge absolut); `scene/aufnahmen.txt` mit deinen elf Aufnahmen (Kamera aus `LB_SCHNAPPSCHUSS`: E 353 843, N 6 505 325, 202 m, Blick 40°, 12° nach oben; t = 9 … 46 s nach dem Einschlag), Vorher-Bilder in `docs/bild/A0`.<br>• **Prüfung:** `ant pruefung` **269 Prüfungen, alles in Ordnung** (5 Hinweise wie bisher), davon 6 neu für W0 (Start in „Hoch“, Regelung, Halton, Rückprojektion umkehrbar 1,8e-12, keine Karte beim Start, kein leeres Versprechen im Bedienfeld).<br>• **Radeon, deine Aufnahmen** (1495 × 940, Hoch): 24–40 ms je Bild; mit der Welle im Bild (t = 9–20 s) über dem Ziel von 33 ms, dann regelt die App auf 85 %. **Heldenbilder** (1920 × 1080, Hoch): 9,6–36,3 ms je Bild, in 10 von 11 Ansichten schneller als B0 (Die Wand aus Wasser 36,3 statt 42,5 ms, 524 m 27,1 statt 34,5, Gilbert Inlet 16,8 statt 22,0), weil Einzelbäume nur noch bis 3 km gerechnet werden; Abend vor Anker 16,8 statt 13,9 ms (Kantenglättung)<br>• **Ehrlich offen:** Welle bleibt ein glattes Brett (K5 → W4), Himmel und Licht unverändert (W1), Staub ohne Licht (W3). W0 macht das Bild ruhig und scharf, noch nicht schön.<br>• Skripte: `w0_abnahme.cmd` (alles), `w0_aufnahmen.cmd`, `w0_runde2.cmd`, `fotokamera.cmd` (Kamera deiner Fotos aus Oracle). | `docs/bild/W0A/Vergleich_W0A.png` (deine Aufnahmen), `docs/bild/W0/Vergleich_W0.png` (Heldenbilder gegen B0) |
| 04.11.2026 | – | **Zweite Fassung nach deinen Bildern.** Ursachen K1–K7 für Brei, Korn und fehlendes Wetter; W0 wird „Scharf“; Wetter als Kapitel 5a und Phase W7; Bucht jetzt W8, Kino W9; Nature-Bäume und -Böden; H9; WF13–WF14 | deine Aufnahmen: `docs/wahrHeeldenbilder` |
| 03.11.2026 | – | **Werkbuch Welle als Entwurf.** Klarstellung zu B0, Befund (16 von 64 Packages, 837 Zeilen Shader), vierzehn Effekte der Welle, zehn Aufwertungen der Landschaft, acht Heldenbilder, zwei Stufen, Phasen W0–W8, Fragen WF1–WF12 | Vorher: `docs/bild/B0` |
