@echo off
rem Werkbuch Welle W4b: Suche nach dem Heldenbild „Die Röhre“. Doppelklick; Protokoll _transfer\w4\w4b_roehre_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w4\w4b_roehre_log.txt"
echo === Roehre (W4bR) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w4b.txt" "-Dbild.phase=W4bR" "-Dnur=3,4,7" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
call "%ANT%" "%UP%" "-Dbild.phase=W4bRH" "-Dvorher=W4b" "-Dnur=7,13" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
