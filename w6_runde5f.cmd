@echo off
rem Werkbuch Welle W6 Runde 5f: Baumschatten nicht mehr bei jeder Pruefung des Walds neu. Zeitvergleich im selben Lauf: W6R5F (an), W6R5F0 (aus), W6R5F2 (an). Doppelklick; Protokoll _transfer\w6r5\w6_runde5f_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=%~dp0_transfer\w6r5\w6_runde5f_log.txt"
set "LITUYA_BILDBANK_ZUSATZ="
echo === 1 Heldenbilder mit Runde 5 (W6R5F) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W6R5F" "-Dvorher=W6G" bildbank >> "%LOG%" 2>&1
echo === 2 Heldenbilder ohne Runde 5 (W6R5F0) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.ohneBaumschatten=true -Dlituya.ohneRinde=true -Dlituya.ohneTriebe=true -Dlituya.cliffOhneFernstufe=true -Dlituya.schollenSchattenAlt=true -Dlituya.eis50=true -Dlituya.schnee20=true"
call "%ANT%" "%UP%" "-Dbild.phase=W6R5F0" bildbank >> "%LOG%" 2>&1
echo === 3 Heldenbilder mit Runde 5, zweiter Lauf (W6R5F2) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ="
call "%ANT%" "%UP%" "-Dbild.phase=W6R5F2" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
