@echo off
rem Werkbuch Welle W6 Runde 3c: Renderer.jar neu, Proben W6R3C, Abnahme Heldenbilder (W6E gegen W6B) und deine Aufnahmen (W6EA gegen W6BA), Pruefung. Doppelklick; Protokoll _transfer\w6r3\w6_runde3c_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r3\w6_runde3c_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 2 Proben (W6R3C) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R3C" "-Dnur=78,96,82,83,85,86" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 3 Heldenbilder (W6E gegen W6B) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6E" "-Dvorher=W6B" bildbank >> "%LOG%" 2>&1
echo === 4 deine Aufnahmen (W6EA gegen W6BA) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W6EA" "-Dvorher=W6BA" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 5 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
