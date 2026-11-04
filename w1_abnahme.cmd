@echo off
rem Werkbuch Welle W1 "Licht": Abnahme auf der Radeon. Doppelklick; Protokoll _transfer\w1_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w1_log.txt"
echo === 1 Heldenbilder H1-H3 und alle (W1 gegen W0) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W1" "-Dvorher=W0" bildbank >> "%LOG%" 2>&1
echo === 2 Aufnahmen (W1A gegen W0A) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W1A" "-Dvorher=W0A" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 3 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
