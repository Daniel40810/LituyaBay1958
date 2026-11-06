@echo off
rem Werkbuch Welle W6 Runde 8: Diagnose der Kante aus docs\status\Kante_W06.png (Proben 129 bis 134, Phase W6K1). Doppelklick; Protokoll _transfer\w6r7\w6_kante_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r7\w6_kante_log.txt"
echo === 1 Proben (W6K1) === > "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6K1" "-Dnur=129,130,131,132,133,134" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
