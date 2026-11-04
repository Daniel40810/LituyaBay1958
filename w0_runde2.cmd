@echo off
rem Werkbuch Welle W0, Runde 2: die Heldenbilder neu (die Aufnahmen: w0_aufnahmen.cmd). Protokoll _transfer\w0_runde2_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w0_runde2_log.txt"
echo === Heldenbilder === > "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W0" "-Dvorher=B0" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
