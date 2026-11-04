@echo off
rem Werkbuch Welle W3c: Probe 2 (nur die Liste). Doppelklick; Protokoll _transfer\w3c\w3c_probe2_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w3c\w3c_probe2_log.txt"
echo === 1 Aufnahmen W03 (W3CQ gegen W3C0) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w3.txt" "-Dbild.phase=W3CQ" "-Dvorher=W3C0" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
