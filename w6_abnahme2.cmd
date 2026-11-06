@echo off
rem Werkbuch Welle W6 Runde 2: Abnahme auf der Radeon (Heldenbilder gegen W6, deine Aufnahmen gegen W6A, Proben, Clip „Flug zur Kette“, Prüfung). Doppelklick; Protokoll _transfer\w6\w6_abnahme2_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w6\w6_abnahme2_log.txt"
echo === 1 Heldenbilder (W6B gegen W6) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6B" "-Dvorher=W6" bildbank >> "%LOG%" 2>&1
echo === 2 deine elf Aufnahmen (W6BA gegen W6A) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W6BA" "-Dvorher=W6A" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 3 Proben (W6BR) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6BR" "-Dnur=63,64,71,76,77,78,79,80,81" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 4 Film Flug zur Kette (1280 x 720) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Flug zur Kette" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === 5 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
