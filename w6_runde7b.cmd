@echo off
rem Werkbuch Welle W6 Runde 7b: Moos heller, Karten streifend ausgeblendet, Zaehlung ohne Warten (statsAsync) fuer das Wald-Protokoll. Renderer.jar neu und pruefen; Proben W6R7B (Protokoll ohne Warten) und Zeit des Moosbilds; Heldenbilder W6R7B gegen W6R6E. Doppelklick; Protokoll _transfer\w6r7\w6_runde7b_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r7\w6_runde7b_log.txt"
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
echo === 2 MossCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.moss.demo.MossCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Proben (W6R7B, Wald-Protokoll ohne Warten) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true -Dlituya.waldProtokoll=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R7B" "-Dnur=121,122,123,84,127,82,128,90,104,105,107,114,119,120" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 4 Proben ohne Protokoll (W6R7C), Zeit === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R7C" "-Dnur=84,127,82,128,90,105,107" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 5 Heldenbilder (W6R7B gegen W6R6E) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6R7BH" "-Dvorher=W6R6E" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
