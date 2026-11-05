@echo off
rem Werkbuch Welle W6: Zeitprobe H13 mit und ohne Fälle/Landschaft (1920 x 1080). Doppelklick; Protokoll _transfer\w6\w6_zeit_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w6\w6_zeit_log.txt"
echo === Zeitprobe (W6Z) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6Z" "-Dnur=76,74,75,77,76" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
