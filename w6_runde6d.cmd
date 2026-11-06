@echo off
rem Werkbuch Welle W6 Runde 6d: ForestGpu.draw schreibt die Tiefe selbst (helle ferne Staemme mit Schattenkarte). Renderer.jar neu, ForestGpuCheck; Proben W6R6EP; Heldenbilder W6R6E gegen W6R5F2. Doppelklick; Protokoll _transfer\w6r6\w6_runde6d_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r6\w6_runde6d_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 ForestGpuCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.forest.demo.ForestGpuCheck >> "%LOG%" 2>&1
echo === 2 ForestCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.forest.demo.ForestCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Proben (W6R6EP, mit Teilzeiten) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R6EP" "-Dnur=90,105,114,82,97,84,108" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 4 Heldenbilder Runde 6 fertig (W6R6E gegen W6R5F2) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6R6E" "-Dvorher=W6R5F2" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
