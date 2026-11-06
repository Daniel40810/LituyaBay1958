@echo off
rem Werkbuch Welle W6 Runde 4b: Renderer.jar neu (Nadeln Stufe 1), Front ohne Tuerkis; Proben W6R4B. Doppelklick; Protokoll _transfer\w6r4\w6_runde4b_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r4\w6_runde4b_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 ForestCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.forest.demo.ForestCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Proben (W6R4B) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R4B" "-Dnur=78,85,84,87,80" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 4 Heldenbild 13 und Aufnahme 2 (Zeit) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6H" "-Dnur=11,13" "-Dvorher=W6G" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
