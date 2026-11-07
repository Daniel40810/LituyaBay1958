@echo off
rem Werkbuch Welle W7b "Regen", Runde 5d: Spiegel genau nur fuer die Neigung des Regens (Kraeusel wie bisher).
rem Proben 761 762 764 774 (W7BP5D), Heldenbilder 1, 14 (W7B5D gegen W7R1D).
rem Doppelklick; Protokoll _transfer\w7\w7b_runde5d_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w7" mkdir "%~dp0_transfer\w7"
set "LOG=%~dp0_transfer\w7\w7b_runde5d_log.txt"
echo === 5 Proben W7b Runde 5d === > "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w7r.txt" "-Dbild.phase=W7BP5D" "-Dnur=761,762,764,774" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 6 Heldenbilder (W7B5D gegen W7R1D, nur 1 14) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W7B5D" "-Dvorher=W7R1D" "-Dnur=1,14" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
