@echo off
rem Werkbuch Welle W6 Runde 4: Renderer.jar neu und pruefen; Pruefung; Proben W6R4; Heldenbilder (W6G gegen W6E); deine Aufnahmen (W6GA gegen W6EA); Clip Flug zur Kette. Doppelklick; Protokoll _transfer\w6r4\w6_runde4_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r4\w6_runde4_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 TreeCheck schwarzerle === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.tree.demo.TreeCheck schwarzerle >> "%LOG%" 2>&1
echo === 2 ForestCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.forest.demo.ForestCheck >> "%LOG%" 2>&1
echo === 2 BergCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.buoyancy.demo.BergCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 4 Proben (W6R4) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R4" "-Dnur=78,85,86,82,83,84,87,63" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 5 Heldenbilder (W6G gegen W6E) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6G" "-Dvorher=W6E" bildbank >> "%LOG%" 2>&1
echo === 6 deine Aufnahmen (W6GA gegen W6EA) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W6GA" "-Dvorher=W6EA" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 7 Film Flug zur Kette (1280 x 720) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Flug zur Kette" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
