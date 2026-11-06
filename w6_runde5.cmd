@echo off
rem Werkbuch Welle W6 Runde 5: Renderer.jar neu (Rinde, Triebe, Sonnenschattenkarte, Schatten der Schollen, Fernstufe cliff) und pruefen; Pruefung; Proben W6R5; Heldenbilder (W6R5 gegen W6G); Clip Flug zur Kette. Doppelklick; Protokoll _transfer\w6r5\w6_runde5_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r5\w6_runde5_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 TreeCheck schwarzerle === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.tree.demo.TreeCheck schwarzerle >> "%LOG%" 2>&1
echo === 2 ForestCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.forest.demo.ForestCheck >> "%LOG%" 2>&1
echo === 2 ForestGpuCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.forest.demo.ForestGpuCheck >> "%LOG%" 2>&1
echo === 2 BergCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.buoyancy.demo.BergCheck >> "%LOG%" 2>&1
echo === 2 CliffCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.cliff.demo.CliffCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 4 Proben (W6R5) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R5" "-Dnur=82,97,98,103,84,99,87,85,100,63,101,90,102,94,78,80" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 5 Heldenbilder (W6R5H gegen W6G) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6R5H" "-Dvorher=W6G" bildbank >> "%LOG%" 2>&1
echo === 6 Film Flug zur Kette (1280 x 720) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Flug zur Kette" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
