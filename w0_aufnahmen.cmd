@echo off
rem Werkbuch Welle W0: deine elf Aufnahmen mit der Kamera aus LB_SCHNAPPSCHUSS nachstellen (W0A gegen A0). Protokoll _transfer\w0_aufnahmen_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W0A" "-Dvorher=A0" "-Dbreite=1495" "-Dhoehe=940" bildbank > _transfer\w0_aufnahmen_log.txt 2>&1
echo === FERTIG %TIME% === >> _transfer\w0_aufnahmen_log.txt
