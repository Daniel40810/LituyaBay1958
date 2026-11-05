@echo off
rem Werkbuch Welle W5: Diagnose am Sporn. Doppelklick; Protokoll _transfer\w5\w5_diag_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w5\w5_diag_log.txt"
echo === W5 Runde 7 H13 Welle und Staub (W5X2) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5d.txt" "-Dbild.phase=W5X2" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
