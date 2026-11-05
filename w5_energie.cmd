@echo off
rem Werkbuch Welle W5, Runde 11: Gegenprobe zur Wärme je Energieplan. Den Plan stellst du selbst ein (Windows: Einstellungen,
rem System, Strom: Energiemodus, oder Systemsteuerung: Energieoptionen), dann Doppelklick. Ablauf wie in Runde 10: 5 min Pause,
rem kalt messen, 5 min Last, heiß messen. Die Bilder landen in docs\bild\W5E<kalt/heiss>_<Kennung des Plans>; Protokoll
rem _transfer\w5\w5_energie_log.txt, Zustand in _transfer\w5\zustand_<Datum>_<Zeit>.csv. Dauert rund 13 min.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w5\w5_energie_log.txt"
for /f "tokens=4" %%g in ('powercfg /getactivescheme') do set "PG=%%g"
set "PK=%PG:~0,8%"
echo === Energieplan %PK% %TIME% === >> "%LOG%"
powercfg /getactivescheme >> "%LOG%" 2>&1
start "zustand" /min powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0zustand.ps1"
echo === Pause 5 min %TIME% === >> "%LOG%"
timeout /t 300 /nobreak > nul
echo === kalt (W5EK_%PK%) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5_kalt.txt" "-Dbild.phase=W5EK_%PK%" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === heiss nach 5 min Last (W5EH_%PK%) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5_heiss.txt" "-Dbild.phase=W5EH_%PK%" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo.> "_transfer\w5\zustand.stop"
echo === FERTIG %TIME% === >> "%LOG%"
