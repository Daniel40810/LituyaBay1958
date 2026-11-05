@echo off
rem Werkbuch Welle W5: Diagnose am Sporn. Doppelklick; Protokoll _transfer\w5\w5_diag_log.txt.
rem Runde 8: dazu der Zustand des Rechners (Netzteil oder Akku, Energieplan, Takt des Prozessors, Temperatur) alle 2 s in _transfer\w5\zustand.csv
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w5\w5_diag_log.txt"
echo === W5 Runde 9 (W5R9) === > "%LOG%"
powercfg /getactivescheme >> "%LOG%" 2>&1
powershell -NoProfile -Command "Get-CimInstance Win32_Battery | Format-List BatteryStatus,EstimatedChargeRemaining; Get-CimInstance Win32_VideoController | Format-List Name,DriverVersion,CurrentRefreshRate" >> "%LOG%" 2>&1
start "zustand" /min powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0zustand.ps1"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5d.txt" "-Dbild.phase=W5R9" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo.> "_transfer\w5\zustand.stop"
echo === FERTIG %TIME% === >> "%LOG%"
