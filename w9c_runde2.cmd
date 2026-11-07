@echo off
rem Werkbuch Welle W9c Runde 2 "Schwebe, Lichter, Tropfen": Renderer sauber bauen (ant clean jar: sonst bleiben Konstanten in
rem alten Pruefklassen), LensCheck, FilmCheck, ShutterCheck, ForestCheck, Demo-Bilder; LituyaBay1958 sauber bauen;
rem Wald am Boden VORHER (938 939 auf den alten Daten, W9CP2V); Aufbereitung: Gelaende neu (Szene mit Lidar), Angleich
rem (Szene = Mittel des Kerns, Fern = Mittel der Szene), Wald (Kronenhoehe gegen das Lidar), Inhalt; alte Kacheln nach
rem _sicherung\w9c2; Pruefung; Proben 931-939 (W9CP2); Heldenbilder 1 14 (W9C2 gegen W9C1).
rem Protokoll _transfer\w9\w9c_runde2_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w9" mkdir "%~dp0_transfer\w9"
set "LOG=%~dp0_transfer\w9\w9c_runde2_log.txt"
echo === 1 Renderer sauber bauen (clean jar, mit Paketpruefung) %TIME% === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" clean jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 LensCheck, FilmCheck, ShutterCheck, ForestCheck, Demo-Bilder === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.lens.demo.LensCheck >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.film.demo.FilmCheck >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.shutter.demo.ShutterCheck >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.forest.demo.ForestCheck >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.shutter.demo.ShutterDemo --bild docs\img\shutter.png >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.lens.demo.LensDemo --bild docs\img\lens.png >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 LituyaBay1958 sauber bauen === >> "%LOG%"
call "%ANT%" "%UP%" clean jar >> "%LOG%" 2>&1
echo === 4 Wald am Boden VORHER (938 939 auf den alten Daten, W9CP2V) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w9c2.txt" "-Dbild.phase=W9CP2V" "-Dnur=938,939" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 5 Aufbereitung: gelaende neu (Szene mit Lidar), angleich, wald, inhalt %TIME% === >> "%LOG%"
if not exist "%~dp0_sicherung\w9c2\wald" mkdir "%~dp0_sicherung\w9c2\wald"
copy /Y daten\wald\kronenhoehe_10m.r32 _sicherung\w9c2\wald\ >> "%LOG%" 2>&1
copy /Y daten\wald\WALD.txt _sicherung\w9c2\wald\ >> "%LOG%" 2>&1
copy /Y daten\gelaende\szene_5m\STUFE.txt _sicherung\w9c2\STUFE_szene_5m.txt >> "%LOG%" 2>&1
copy /Y daten\gelaende\fern_30m\STUFE.txt _sicherung\w9c2\STUFE_fern_30m.txt >> "%LOG%" 2>&1
call "%ANT%" "%UP%" "-Dschritte=gelaende,wald,inhalt" "-Dneu=true" "-Dsicherung=_sicherung/w9c2" aufbereitung >> "%LOG%" 2>&1
echo === 6 Pruefung %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
echo === 7 Proben W9c Runde 2 (931-939) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w9c2.txt" "-Dbild.phase=W9CP2" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 8 Heldenbilder ohne Objektiv und Film (W9C2 gegen W9C1, nur 1 14) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W9C2" "-Dvorher=W9C1" "-Dnur=1,14" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
