@echo off
rem Werkbuch Welle W5 „524 m“: Abnahme auf der Radeon (Runde 11: Wald ohne Rücklesen, Verdeckung nur bei Bewegung, Lippe ohne hypot). Doppelklick; Protokoll _transfer\w5\w5_abnahme_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w5\w5_abnahme_log.txt"
echo === 1 Heldenbilder (W5 gegen W4b) === > "%LOG%"
start "zustand" /min powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0zustand.ps1"
call "%ANT%" "%UP%" "-Dbild.phase=W5" "-Dvorher=W4b" bildbank >> "%LOG%" 2>&1
echo === 2 Proben am Sporn (W5R) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5.txt" "-Dbild.phase=W5R" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 3 deine elf Aufnahmen (W5A gegen W4bA) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W5A" "-Dvorher=W4bA" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 4 Film 524 m (20 s, 1280 x 720, 50 mm) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=524 m" "-Dbreite=1280" "-Dhoehe=720" "-Dbrennweite=50" film >> "%LOG%" 2>&1
echo === 5 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 6 t142, H6, H13, H7 im Fluss (W5Z11) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5d.txt" "-Dbild.phase=W5Z11" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo.> "_transfer\w5\zustand.stop"
echo === FERTIG %TIME% === >> "%LOG%"
