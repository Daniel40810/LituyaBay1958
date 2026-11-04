@echo off
rem Werkbuch Welle W4b: Probe (Lippe als Form, Kammgischt). Doppelklick; Protokoll _transfer\w4\w4b_probe_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w4\w4b_probe_log.txt"
echo === 1 Aufnahmen 3,5 (W4bP gegen W4A) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W4bP" "-Dvorher=W4A" "-Dnur=5" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 2 Heldenbilder 5,7 (W4bPH gegen W4) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W4bPH" "-Dvorher=W4" "-Dnur=5,7" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
