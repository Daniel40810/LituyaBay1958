@echo off
rem Werkbuch Welle W5: Probe, Stand W5 am Sporn. Doppelklick; Protokoll _transfer\w5\w5_probe_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w5\w5_probe_log.txt"
echo === W5 Probe (W5P) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5.txt" "-Dbild.phase=W5Q" "-Dbreite=1280" "-Dhoehe=720" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
