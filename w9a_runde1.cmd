@echo off
rem Werkbuch Welle W9a Runde 1 "Das Objektiv": Renderer bauen (neu com.dan.lens), LensCheck, LensDemo-Bild, Pruefung,
rem Proben 901-908 (W9AP1), Heldenbilder 1 14 (W9A1 gegen W8B2: ohne Objektiv unveraendert). Protokoll _transfer\w9\w9a_runde1_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w9" mkdir "%~dp0_transfer\w9"
set "LOG=%~dp0_transfer\w9\w9a_runde1_log.txt"
echo === 1 Renderer bauen (mit Paketpruefung) === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 LensCheck und LensDemo-Bild === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.lens.demo.LensCheck >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.lens.demo.LensDemo --bild docs\img\lens.png >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
if not exist "%~dp0docs\bild\W9AP1" mkdir "%~dp0docs\bild\W9AP1"
copy /Y ..\Renderer\docs\img\lens.png docs\bild\W9AP1\LensDemo.png >> "%LOG%" 2>&1
echo === 3 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 4 Proben W9a (Objektiv) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w9.txt" "-Dbild.phase=W9AP1" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 5 Heldenbilder ohne Objektiv (W9A1 gegen W8B2, nur 1 14) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W9A1" "-Dvorher=W8B2" "-Dnur=1,14" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
