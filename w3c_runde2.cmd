@echo off
rem Werkbuch Welle W3c: Runde 2 (Eis nur flach, Unterwasser heller). Doppelklick; Protokoll _transfer\w3c\w3c_runde2_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w3c\w3c_runde2_log.txt"
echo === 1 Aufnahmen W03 4,5,6,12 (W3CA) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w3.txt" "-Dbild.phase=W3CA" "-Dvorher=W3C0" "-Dbreite=1495" "-Dhoehe=940" "-Dnur=4,5,6,12" bildbank >> "%LOG%" 2>&1
echo === 2 Heldenbilder 3,8 (W3C) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W3C" "-Dvorher=W3B" "-Dnur=3,8" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
