@echo off
rem Werkbuch Welle W4b: Clip und Prüfung auf der Radeon (die Wand: Lippe, Durchlicht, Walze, Kamm, Schaumspur, Stämme; Staub in halber Auflösung). Doppelklick; Protokoll _transfer\w4\w4b_film_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w4\w4b_film_log.txt"
echo === 4 Film Die Wand (20 s, 1280 x 720) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Die Wand" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === 5 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
