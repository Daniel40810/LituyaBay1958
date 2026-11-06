@echo off
rem Werkbuch Welle W6 Runde 8c: auf dem Gelaende aus 8b Pruefung, Proben W6K3 (Kante 129 bis 134, Ufer 80), Heldenbilder W6R8F gegen W6R7E. Doppelklick; Protokoll _transfer\w6r8\w6_runde8c_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r8\w6_runde8c_log.txt"
echo === 1 Pruefung === > "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 2 Proben (W6K3) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6K3" "-Dnur=129,130,131,132,133,134,80" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 3 Heldenbilder (W6R8F gegen W6R7E) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6R8F" "-Dvorher=W6R7E" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
