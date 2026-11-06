@echo off
rem Werkbuch Welle W6 Runde 3b: Renderer.jar neu (Schollen, Nadeln), Pruefungen, Proben W6R3B (Erlen, Nadeln, Schollen, Zeit H11), Heldenbild 3 (W6D). Doppelklick; Protokoll _transfer\w6r3\w6_runde3b_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r3\w6_runde3b_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 BergCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.buoyancy.demo.BergCheck >> "%LOG%" 2>&1
echo === 2 ForestCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.forest.demo.ForestCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Proben (W6R3B) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R3B" "-Dnur=90,91,92,93,94,95,78,85,86,82,83,84,87" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 4 Heldenbild 3 (W6D gegen W6B) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6D" "-Dnur=3,11" "-Dvorher=W6B" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
