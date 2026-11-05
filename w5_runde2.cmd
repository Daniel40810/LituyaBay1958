@echo off
rem Werkbuch Welle W5, Runde 2: fallende Bäume nah höchstens 120 und bis 150 m (deine Aufnahmen am Sporn). Doppelklick; Protokoll _transfer\w5\w5_runde2_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w5\w5_runde2_log.txt"
echo === 1 deine elf Aufnahmen (W5A gegen W4bA) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W5A" "-Dvorher=W4bA" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
