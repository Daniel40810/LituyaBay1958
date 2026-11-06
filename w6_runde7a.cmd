@echo off
rem Werkbuch Welle W6 Runde 7a: Diagnose helle ferne Staemme mit Wald-Protokoll (welche Pruefung je Bild, wie viele Baeume). Proben 90, 105, 107, 119, 120. Doppelklick; Protokoll _transfer\w6r7\w6_runde7a_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w6r7" mkdir "%~dp0_transfer\w6r7"
set "LOG=%~dp0_transfer\w6r7\w6_runde7a_log.txt"
echo === 1 Proben mit Wald-Protokoll (W6R7A) === > "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true -Dlituya.waldProtokoll=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R7A" "-Dnur=90,105,107,119,120" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
