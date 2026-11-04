@echo off
rem Werkbuch Welle W3c: Abnahme auf der Radeon (Becken, Hang, Gletscher, Staubfahne). Doppelklick; Protokoll _transfer\w3c\w3c_abnahme_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w3c\w3c_abnahme_log.txt"
echo === 1 Aufnahmen W03 (W3CA gegen W3C0) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w3.txt" "-Dbild.phase=W3CA" "-Dvorher=W3C0" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 2 deine elf Aufnahmen (W3CB gegen W3BA) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W3CB" "-Dvorher=W3BA" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 3 Heldenbilder (W3C gegen W3B) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W3C" "-Dvorher=W3B" bildbank >> "%LOG%" 2>&1
echo === 4 Film Der Sturz (20 s, 1280 x 720) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Der Sturz" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === 5 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
