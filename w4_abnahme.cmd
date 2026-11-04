@echo off
rem Werkbuch Welle W4: Abnahme auf der Radeon (die Wand: Lippe, Durchlicht, Walze, Kamm, Schaumspur, Stämme; Staub in halber Auflösung). Doppelklick; Protokoll _transfer\w4\w4_abnahme2_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w4\w4_abnahme2_log.txt"
echo === 1 deine elf Aufnahmen (W4A gegen W3CB) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W4A" "-Dvorher=W3CB" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 2 Heldenbilder (W4 gegen W3C) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W4" "-Dvorher=W3C" bildbank >> "%LOG%" 2>&1
echo === 3 Aufnahmen W03 (W4B gegen W3CA) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w3.txt" "-Dbild.phase=W4B" "-Dvorher=W3CA" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 4 Film Die Wand (20 s, 1280 x 720) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Die Wand" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === 5 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
