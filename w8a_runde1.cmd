@echo off
rem Werkbuch Welle W8a "Spiegel und Kutter", Runde 1: Renderer bauen (ocean planare Spiegelung, sky, vessel Bootsbauer),
rem CraftCheck, Pruefung, Proben 801-805 und 776 777 neu (W8AP1) und mit altem Spiegel und Kutter (W8AP1ALT),
rem Heldenbilder 1 und 14 (W8A1 gegen W7B6). Doppelklick; Protokoll _transfer\w8\w8a_runde1_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w8" mkdir "%~dp0_transfer\w8"
set "LOG=%~dp0_transfer\w8\w8a_runde1_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 CraftCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.vessel.demo.CraftCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 4 Proben W8a neu (801-805) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w8.txt" "-Dbild.phase=W8AP1" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w7r.txt" "-Dbild.phase=W8AP1" "-Dnur=776,777" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 5 Proben mit altem Spiegel und Kutter (Vergleich) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true -Dlituya.alterSpiegel=true -Dlituya.alterKutter=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w8.txt" "-Dbild.phase=W8AP1ALT" "-Dnur=801,802,804" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w7r.txt" "-Dbild.phase=W8AP1ALT" "-Dnur=776" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 6 Heldenbilder (W8A1 gegen W7B6, nur 1 14) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbild.phase=W8A1" "-Dvorher=W7B6" "-Dnur=1,14" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
