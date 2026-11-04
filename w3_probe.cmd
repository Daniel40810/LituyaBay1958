@echo off
rem Werkbuch Welle W1 "Licht": Abnahme auf der Radeon. Doppelklick; Protokoll _transfer\w3_probe_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w3_probe_log.txt"
echo === 1 Heldenbilder H1-H3 und alle (W1 gegen W0) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W3P" "-Dnur=4,5" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
