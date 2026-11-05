@echo off
rem Werkbuch Welle W5, Runde 10: (1) Schaum der Welle abwechselnd, Spiegel in der Fahrt; (2) Wärme: 5 min Pause, kalt messen,
rem 5 min Last, heiß messen, 5 min Pause, wieder kalt. Zustand alle 2 s in _transfer\w5\zustand.csv. Doppelklick; Protokoll _transfer\w5\w5_runde10_log.txt. Dauert rund 35 min.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w5\w5_runde10_log.txt"
echo === 1 Schaum abwechselnd, Spiegel in der Fahrt (W5R10) %TIME% === > "%LOG%"
start "zustand" /min powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0zustand.ps1"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5d.txt" "-Dbild.phase=W5R10" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 2 Pause 5 min %TIME% === >> "%LOG%"
timeout /t 300 /nobreak > nul
echo === 3 kalt (W5K10a) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5_kalt.txt" "-Dbild.phase=W5K10a" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 4 heiss nach 5 min Last (W5H10) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5_heiss.txt" "-Dbild.phase=W5H10" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 5 Pause 5 min %TIME% === >> "%LOG%"
timeout /t 300 /nobreak > nul
echo === 6 wieder kalt (W5K10b) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5_kalt.txt" "-Dbild.phase=W5K10b" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo.> "_transfer\w5\zustand.stop"
echo === FERTIG %TIME% === >> "%LOG%"
