@echo off
rem Werkbuch Welle W6 Runde 2: Proben (Eisfront, Fälle, Zeit) und Clip „Flug zur Kette“. Doppelklick; Protokoll _transfer\w6\w6_runde2_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w6\w6_runde2_log.txt"
echo === 1 Proben (W6S) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=W6S" "-Dnur=78,79,71,64,76,77" "-Dbreite=1280" "-Dhoehe=720" bildbank >> "%LOG%" 2>&1
echo === 2 Heldenbilder 3, 8, 9 (W6T gegen W6) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6T" "-Dnur=3,8,9" "-Dvorher=W6" "-Dbreite=1280" "-Dhoehe=720" bildbank >> "%LOG%" 2>&1
echo === 3 Film Flug zur Kette (45 s, 1280 x 720) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Flug zur Kette" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
