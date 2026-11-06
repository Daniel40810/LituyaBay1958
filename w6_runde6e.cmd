@echo off
rem Werkbuch Welle W6 Runde 6e: Diagnose helle ferne Staemme mit Baumschatten (Proben 90, 105, 117 bis 119). Doppelklick; Protokoll _transfer\w6r6\w6_runde6e_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r6\w6_runde6e_log.txt"
echo === 1 Proben (W6R6F) === > "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R6F" "-Dnur=90,105,117,118,119,120,104" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
