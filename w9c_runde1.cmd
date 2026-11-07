@echo off
rem Werkbuch Welle W9c Runde 1 "Die Belichtung": Renderer bauen (neu com.dan.shutter, com.dan.lens und com.dan.film schneller,
rem mit Paketpruefung), LensCheck, FilmCheck, ShutterCheck, Demo-Bilder, Pruefung, Proben 921-928 (W9CP1: Kino-Stufe,
rem Bewegungsunschaerfe, Zeitlupe), die Proben 901 904 905 (W9CP1A) und 911 915 (W9CP1B) neu: Kosten des Objektivs bei offener Blende und des
rem Films gegen W9AP2/W9BP1), Heldenbilder 1 14 (W9C1 gegen W9B1: ohne Objektiv und Film unveraendert).
rem Protokoll _transfer\w9\w9c_runde1_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w9" mkdir "%~dp0_transfer\w9"
set "LOG=%~dp0_transfer\w9\w9c_runde1_log.txt"
echo === 1 Renderer bauen (mit Paketpruefung) === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 LensCheck, FilmCheck, ShutterCheck, Demo-Bilder === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.lens.demo.LensCheck >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.film.demo.FilmCheck >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.shutter.demo.ShutterCheck >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.shutter.demo.ShutterDemo --bild docs\img\shutter.png >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.lens.demo.LensDemo --bild docs\img\lens.png >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
if not exist "%~dp0docs\bild\W9CP1" mkdir "%~dp0docs\bild\W9CP1"
copy /Y ..\Renderer\docs\img\shutter.png docs\bild\W9CP1\ShutterDemo.png >> "%LOG%" 2>&1
copy /Y ..\Renderer\docs\img\lens.png docs\bild\W9CP1\LensDemo.png >> "%LOG%" 2>&1
echo === 3 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
echo === 4 Proben W9c (Kino-Stufe, Bewegungsunschaerfe, Zeitlupe, 921-928) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w9c.txt" "-Dbild.phase=W9CP1" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 5 Kosten: Objektiv offen und Film (901 904 905 aus W9a, 915 aus W9b) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w9.txt" "-Dbild.phase=W9CP1A" "-Dnur=901,904,905" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w9b.txt" "-Dbild.phase=W9CP1B" "-Dnur=911,915" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 6 Heldenbilder ohne Objektiv und Film (W9C1 gegen W9B1, nur 1 14) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W9C1" "-Dvorher=W9B1" "-Dnur=1,14" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
