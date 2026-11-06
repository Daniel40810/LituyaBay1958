@echo off
rem Werkbuch Welle W6 Runde 3: Renderer.jar bauen und pruefen, nach lib kopieren; Pruefung; Proben (W6R3); Heldenbilder gegen W6B (W6C); Clip. Doppelklick; Protokoll _transfer\w6r3\w6_runde3_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r3\w6_runde3_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" clean jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 Renderer pruefen: FallCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.fall.demo.FallCheck >> "%LOG%" 2>&1
echo === 2 BergCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.buoyancy.demo.BergCheck >> "%LOG%" 2>&1
echo === 2 ForestCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.forest.demo.ForestCheck >> "%LOG%" 2>&1
echo === 2 ForestGpuCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -cp "%RCP%" com.dan.forest.demo.ForestGpuCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Pruefung LituyaBay1958 === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 4 Proben (W6R3) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R3" "-Dnur=78,82,83,84,85,86,80,81" "-Dbreite=1280" "-Dhoehe=720" bildbank >> "%LOG%" 2>&1
echo === 5 Heldenbilder 3, 6, 7, 11, 13 (W6C gegen W6B) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6C" "-Dnur=3,6,7,11,13" "-Dvorher=W6B" bildbank >> "%LOG%" 2>&1
echo === 6 Film Flug zur Kette (1280 x 720) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Flug zur Kette" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
