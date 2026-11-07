@echo off
rem Werkbuch Welle 5e "W1-W5 in Renderer": die Effekte aus W1 bis W5 liegen jetzt in Renderer-Packages
rem (neu com.dan.horizon und com.dan.surge; erweitert exposure, ocean, dustcloud, cliff, forest, clipmap).
rem Das Bild soll gleich bleiben. Ablauf: 0. die alten Jars sichern (dist\LituyaBay1958.jar und lib\Renderer.jar sind
rem noch vom Stand vor der Umstellung); 1. Heldenbilder VORHER mit den alten Jars (PAKETE_VORHER) und zur Kontrolle
rem des Rauschens zwischen zwei Laeufen H1 und H7 noch einmal (PAKETE_VORHER2); 2. Renderer sauber bauen (mit
rem Paketpruefung) und die acht Checks; 3. LituyaBay1958 sauber bauen; 4. Pruefung; 5. Heldenbilder NACHHER
rem (PAKETE gegen PAKETE_VORHER); 6. Bild fuer Bild vergleichen (tools\BildVergleich.java).
rem Protokoll _transfer\w15\w1w5_pakete_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w15\alt" mkdir "%~dp0_transfer\w15\alt"
set "LOG=%~dp0_transfer\w15\w1w5_pakete_log.txt"
set "ALT=%~dp0_transfer\w15\alt"
echo === 0 alte Jars sichern %TIME% === > "%LOG%"
if exist "%ALT%\LituyaBay1958.jar" goto gesichert
jar tf dist\LituyaBay1958.jar | findstr /C:"paketlicht.glsl" > nul
if not errorlevel 1 (
    echo dist\LituyaBay1958.jar ist schon der neue Stand: keine Bilder VORHER, nur NACHHER. >> "%LOG%"
    goto bauen
)
jar tf lib\Renderer.jar | findstr /C:"com/dan/surge/" > nul
if not errorlevel 1 (
    echo lib\Renderer.jar ist schon der neue Stand: keine Bilder VORHER, nur NACHHER. >> "%LOG%"
    goto bauen
)
copy /Y dist\LituyaBay1958.jar "%ALT%\LituyaBay1958.jar" >> "%LOG%" 2>&1
copy /Y lib\Renderer.jar "%ALT%\Renderer.jar" >> "%LOG%" 2>&1
:gesichert
set "ACP=%ALT%\Renderer.jar;lib\FStyle.jar;lib\gluegen-rt-natives-windows-amd64.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\jogl-all.jar;lib\ojdbc11.jar;lib\sdoapi.jar;lib\sdogr.jar;lib\sdoutl.jar;lib\sdonm.jar;lib\ojdbc8.jar;%ALT%\LituyaBay1958.jar"
set "BB=-Dstdout.encoding=UTF-8 -Dgross=aus -Dvorher= -Dstufe=HOCH -Dbildbank= -Dbreite=1920 -Dhoehe=1080"
echo === 1 Heldenbilder VORHER mit den alten Jars (PAKETE_VORHER) %TIME% === >> "%LOG%"
java %BB% -Dbild.phase=PAKETE_VORHER -Dnur= -cp "%ACP%" com.dan.lituya.tools.Bildbank >> "%LOG%" 2>&1
echo === 1b H1 und H7 noch einmal mit den alten Jars (PAKETE_VORHER2: Rauschen zwischen zwei Laeufen) %TIME% === >> "%LOG%"
java %BB% -Dbild.phase=PAKETE_VORHER2 -Dnur=1,7 -cp "%ACP%" com.dan.lituya.tools.Bildbank >> "%LOG%" 2>&1
:bauen
echo === 2 Renderer sauber bauen (clean jar, mit Paketpruefung) und Checks %TIME% === >> "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" clean jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
for %%c in (horizon.demo.HorizonCheck surge.demo.SurgeCheck exposure.demo.ExposureCheck ocean.demo.OceanCheck dustcloud.demo.DustcloudCheck cliff.demo.CliffCheck forest.demo.ForestCheck clipmap.demo.ClipmapCheck) do (
    echo --- %%c >> "%LOG%"
    java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.%%c >> "%LOG%" 2>&1
)
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 LituyaBay1958 sauber bauen %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" clean jar >> "%LOG%" 2>&1
echo === 4 Pruefung %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 5 Heldenbilder NACHHER (PAKETE gegen PAKETE_VORHER) %TIME% === >> "%LOG%"
if exist "docs\bild\PAKETE_VORHER" (
    call "%ANT%" "%UP%" "-Dbild.phase=PAKETE" "-Dvorher=PAKETE_VORHER" bildbank >> "%LOG%" 2>&1
) else (
    call "%ANT%" "%UP%" "-Dbild.phase=PAKETE" bildbank >> "%LOG%" 2>&1
)
echo === 6 Bild fuer Bild: VORHER gegen NACHHER, dazu zwei Laeufe VORHER gegeneinander %TIME% === >> "%LOG%"
java -Dstdout.encoding=UTF-8 tools\BildVergleich.java docs\bild\PAKETE_VORHER docs\bild\PAKETE >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 tools\BildVergleich.java docs\bild\PAKETE_VORHER2 docs\bild\PAKETE_VORHER >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
