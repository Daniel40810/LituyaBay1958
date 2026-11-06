@echo off
rem Werkbuch Welle W6 Runde 8: Kante am Ufer (docs\status\Kante_W06.png) und WF20 A. Renderer.jar neu mit com.dan.shore und ShoreCheck; Gelaende Szene und Kern neu (Strand aus dem Lidar, Boeschung an der Naht; alte Kacheln nach daten_vor_strand); Pruefung; Proben W6K2; Heldenbilder W6R8E gegen W6R7E. Doppelklick; Protokoll _transfer\w6r8\w6_runde8_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w6r8" mkdir "%~dp0_transfer\w6r8"
set "LOG=%~dp0_transfer\w6r8\w6_runde8_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 ShoreCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.shore.demo.ShoreCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Gelaende neu (Strand und Boeschung) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dschritte=gelaende" "-Dneu=true" "-Dsicherung=daten_vor_strand" aufbereitung >> "%LOG%" 2>&1
echo === 4 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 5 Proben (W6K2) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6K2" "-Dnur=129,130,131,132,133,134,80" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 6 Heldenbilder (W6R8E gegen W6R7E) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6R8E" "-Dvorher=W6R7E" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
