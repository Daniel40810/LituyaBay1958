@echo off
rem Werkbuch Welle W3 "Der Sturz": Abnahme auf der Radeon. Doppelklick; Protokoll _transfer\w3_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w3_log.txt"
echo === 1 Heldenbilder (W3 gegen W2) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W3" "-Dvorher=W2" bildbank >> "%LOG%" 2>&1
echo === 2 Aufnahmen (W3A gegen W2A) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W3A" "-Dvorher=W2A" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 3 Film Der Sturz (20 s, 1280 x 720) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Der Sturz" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === 4 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
