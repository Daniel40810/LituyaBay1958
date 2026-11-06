@echo off
rem Werkbuch Welle W6 Runde 8b: Strand nur am Land und glatt je Landquelle (keine Ruecken im Wasser). Renderer.jar neu, ShoreCheck; Gelaende Szene und Kern neu; INHALT.txt neu. Doppelklick; Protokoll _transfer\w6r8\w6_runde8b_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r8\w6_runde8b_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 ShoreCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.shore.demo.ShoreCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Gelaende neu (Strand nur am Land) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dschritte=gelaende" "-Dneu=true" "-Dsicherung=daten_vor_strand" aufbereitung >> "%LOG%" 2>&1
echo === 4 INHALT.txt neu === >> "%LOG%"
call "%ANT%" "%UP%" "-Dschritte=inhalt" aufbereitung >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
