@echo off
rem Werkbuch Welle W6 „Landschaft“: Abnahme auf der Radeon (Heldenbilder gegen W5, deine Aufnahmen gegen W5A, Proben W6, Prüfung). Doppelklick; Protokoll _transfer\w6\w6_abnahme_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w6\w6_abnahme_log.txt"
echo === 1 Heldenbilder (W6 gegen W5) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6" "-Dvorher=W5" bildbank >> "%LOG%" 2>&1
echo === 2 deine elf Aufnahmen (W6A gegen W5A) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W6A" "-Dvorher=W5A" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 3 Proben W6 (W6R) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6R" "-Dnur=61,62,63,64,65,66,67,68,71" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 4 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
