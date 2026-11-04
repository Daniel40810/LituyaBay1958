@echo off
rem Werkbuch Welle W4b: Abnahme (Lippe als Form, Kammgischt) auf der Radeon (die Wand: Lippe, Durchlicht, Walze, Kamm, Schaumspur, Stämme; Staub in halber Auflösung). Doppelklick; Protokoll _transfer\w4\w4b_abnahme_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w4\w4b_abnahme_log.txt"
echo === 1 deine elf Aufnahmen (W4bA gegen W4A) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W4bA" "-Dvorher=W4A" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 2 Heldenbilder (W4b gegen W4) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W4b" "-Dvorher=W4" bildbank >> "%LOG%" 2>&1
echo === 3 Aufnahmen W03 (W4bB gegen W4B) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w3.txt" "-Dbild.phase=W4bB" "-Dvorher=W4B" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 4 Film Die Wand (20 s, 1280 x 720) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Die Wand" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === 5 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
