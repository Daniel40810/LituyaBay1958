@echo off
rem Werkbuch Welle W6 Runde 6: Renderer.jar neu (Augenanpassung com.dan.exposure, Teilzeiten com.dan.gputime, Moos und Bartflechten, Impostors unter glClipControl, eigene Schattenauswahl) und pruefen; Pruefung; Proben W6R6 mit Teilzeiten; Heldenbilder an (W6R6) / aus (W6R6O) / an (W6R6B); Film. Doppelklick; Protokoll _transfer\w6r6\w6_runde6_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w6r6" mkdir "%~dp0_transfer\w6r6"
set "LOG=%~dp0_transfer\w6r6\w6_runde6_log.txt"
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
echo === 2 ExposureCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.exposure.demo.ExposureCheck >> "%LOG%" 2>&1
echo === 2 GpuSpansCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.gputime.demo.GpuSpansCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 4 Proben (W6R6, mit Teilzeiten) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R6" "-Dnur=82,116,84,115,87,85,90,114,104,105,107,97,63,108,109,110,111,112,113" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 5 Heldenbilder mit Runde 6 (W6R6) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6R6" "-Dvorher=W6G" bildbank >> "%LOG%" 2>&1
echo === 6 Heldenbilder ohne Runde 6 (W6R6O) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true -Dlituya.ohneAugenanpassung=true -Dlituya.ohneBartflechten=true -Dlituya.ohneSchattenAuswahl=true"
call "%ANT%" "%UP%" "-Dbild.phase=W6R6O" bildbank >> "%LOG%" 2>&1
echo === 7 Heldenbilder mit Runde 6, zweiter Lauf (W6R6B) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbild.phase=W6R6B" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === 8 Film Flug zur Kette (1280 x 720) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Flug zur Kette" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
