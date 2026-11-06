@echo off
rem Werkbuch Welle W6 Runde 7: Renderer.jar neu (com.dan.moss, com.dan.lichen, Moostextur und Bartflechten als Karten in forest und tree) und pruefen; Pruefung; Proben W6R7P mit Wald-Protokoll; Proben ohne Flechtenbild (W6R7Q); Heldenbilder W6R7 gegen W6R6E und ohne Moos-/Flechtenbild (W6R7O). Doppelklick; Protokoll _transfer\w6r7\w6_runde7_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r7\w6_runde7_log.txt"
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
echo === 2 LichenCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.lichen.demo.LichenCheck >> "%LOG%" 2>&1
echo === 2 Vorlagen messen === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.moss.demo.MossVorlage "%~dp0templates\png\Moos1.png" "%~dp0templates\png\Moos2.png" "%~dp0templates\png\Moos3.png" "%~dp0templates\png\Moos4.png" >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.lichen.demo.LichenVorlage "%~dp0templates\png\Bartflechten1.png" "%~dp0templates\png\Bartflechten2.png" "%~dp0templates\png\Bartflechten3.png" >> "%LOG%" 2>&1
echo === 2 ExposureCheck, GpuSpansCheck, CliffCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.exposure.demo.ExposureCheck >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.gputime.demo.GpuSpansCheck >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.cliff.demo.CliffCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 4 Proben (W6R7P, Teilzeiten, Wald-Protokoll) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true -Dlituya.waldProtokoll=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R7P" "-Dnur=121,122,123,84,82,97,90,104,105,107,114,119,120,125,126,108" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 5 Proben ohne Flechtenbild (W6R7Q) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true -Dlituya.ohneFlechtenbild=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R7Q" "-Dnur=122,84,82" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 6 Heldenbilder (W6R7 gegen W6R6E) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbild.phase=W6R7" "-Dvorher=W6R6E" bildbank >> "%LOG%" 2>&1
echo === 7 Heldenbilder ohne Moos- und Flechtenbild (W6R7O) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true -Dlituya.ohneMoosbild=true -Dlituya.ohneFlechtenbild=true"
call "%ANT%" "%UP%" "-Dbild.phase=W6R7O" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
