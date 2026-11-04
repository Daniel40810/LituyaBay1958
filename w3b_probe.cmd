@echo off
rem Werkbuch Welle W3b: Probe auf der Radeon (Gischt aus den Daten, Texturen der Haenge). Doppelklick; Protokoll _transfer\w3b_probe_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w3b_probe_log.txt"
echo === 1 Aufnahmen 1,3,6,8,11 (W3BP gegen W3A) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W3BP" "-Dvorher=W3A" "-Dnur=1,3,6,8,11" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 2 Heldenbilder 4,5 (W3BH gegen W3) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W3BH" "-Dvorher=W3" "-Dnur=4,5" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
