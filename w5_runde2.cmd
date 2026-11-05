@echo off
rem Werkbuch Welle W5, Runde 5: Heldenbilder, deine Aufnahmen, Proben und H6 dreimal mit der Verdeckung im eigenen Faden. Doppelklick; Protokoll _transfer\w5\w5_runde2_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w5\w5_runde2_log.txt"
echo === 1 Heldenbilder (W5 gegen W4b) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W5" "-Dvorher=W4b" bildbank >> "%LOG%" 2>&1
echo === 2 deine elf Aufnahmen (W5A gegen W4bA) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W5A" "-Dvorher=W4bA" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 3 Proben am Sporn (W5R) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5.txt" "-Dbild.phase=W5R" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 4 H6 dreimal (W5N) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w5d.txt" "-Dbild.phase=W5N" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
