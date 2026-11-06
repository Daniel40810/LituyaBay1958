@echo off
rem Werkbuch Welle W7 "Wetter", Runde 1e: nur H9 "Nebel zieht ein" (Bildbank 12) im neuen Blick aus 1 200 m (wie Probe 751).
rem Doppelklick; Protokoll _transfer\w7\w7_runde1e_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w7\w7_runde1e_log.txt"
echo === 1 Heldenbild 12 (W7R1E gegen W6R8F) === > "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbild.phase=W7R1E" "-Dvorher=W6R8F" "-Dnur=12" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
