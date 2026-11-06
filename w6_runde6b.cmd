@echo off
rem Werkbuch Welle W6 Runde 6b: Augenanpassung ohne Abgleich (dauerhaft abgebildete Puffer, im Flug keine Messung, Schritt 8, jedes zweite Bild, vier Kopien der Faecher), Bezug 0,08, ins Helle hoechstens 1/3 Blende, im Standbild sofort. Renderer.jar neu, ExposureCheck; Proben W6R6P; Heldenbilder W6R6C gegen W6R6O. Doppelklick; Protokoll _transfer\w6r6\w6_runde6b_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r6\w6_runde6b_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 ExposureCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.exposure.demo.ExposureCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Proben (W6R6P, mit Teilzeiten) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R6P" "-Dnur=82,116,84,115,90,114,107,108,113,85,63" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 3b Proben ohne Bartflechten (W6R6Q) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true -Dlituya.ohneBartflechten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R6Q" "-Dnur=82,84,90" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
echo === 4 Heldenbilder mit Runde 6b (W6R6C gegen W6R6O) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6R6C" "-Dvorher=W6R6O" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
