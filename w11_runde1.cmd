@echo off
rem Werkbuch Welle W11 Runde 1 "Die Bootstour" (5g): LituyaBay1958 sauber bauen (Renderer unveraendert: wake, ocean HullMask und
rem kielTexture liegen schon in lib\Renderer.jar); Tour vorrechnen (ant bootstour: Edrie 1958 ab 20:00, Kittiwake heute ab 10:00
rem nach daten\ereignis\boote\tour); Pruefung; Proben 1101-1108 (W11P1: H15 Bugwelle, H16 Kielwasser, an Bord, Drohne, heute,
rem Vergleich ohne); Heldenbild 1 (W11R1 gegen W10B0: der Kutter vor Anker mit Rumpfmaske); Regiebuch "Die Bootstour" als Film
rem (die ersten 90 s zum Probieren). Protokoll _transfer\w11\w11_runde1_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w11" mkdir "%~dp0_transfer\w11"
set "LOG=%~dp0_transfer\w11\w11_runde1_log.txt"
echo === 1 LituyaBay1958 sauber bauen %TIME% === > "%LOG%"
call "%ANT%" "%UP%" clean jar >> "%LOG%" 2>&1
echo === 2 Bootstour vorrechnen (Edrie 1958, Kittiwake heute) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" bootstour >> "%LOG%" 2>&1
echo === 3 Pruefung %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 4 Proben W11 (1101-1108) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w11.txt" "-Dbild.phase=W11P1" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 5 Heldenbild 1 (W11R1 gegen W10B0) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W11R1" "-Dvorher=W10B0" "-Dnur=1" bildbank >> "%LOG%" 2>&1
echo === 6 Film "Die Bootstour", die ersten 90 s %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Die Bootstour" "-Dbis=90" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
