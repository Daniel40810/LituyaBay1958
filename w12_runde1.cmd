@echo off
rem Werkbuch Welle W12 Runde 1 "Polarlicht" (5h): lib\Renderer.jar ist der neue Stand mit com.dan.aurora (Phase 1 und 2,
rem gl\AuroraGl); LituyaBay1958 sauber bauen; Pruefung; den Block POLARLICHT im echten Fenster bedienen (ant
rem polarlichtfenster, docs\bild\W12\Fenster_*.png); Proben 1201-1207 (W12P1: Winternacht ueber der Bucht, Kp 2 bis 7,
rem rot, der Sommerabend 1958 in der Daemmerung, Feinregler). Protokoll _transfer\w12\w12_runde1_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w12" mkdir "%~dp0_transfer\w12"
set "LOG=%~dp0_transfer\w12\w12_runde1_log.txt"
echo === 0 Renderer.jar mit com.dan.aurora.gl? %TIME% === > "%LOG%"
jar tf lib\Renderer.jar | findstr /C:"com/dan/aurora/gl/AuroraGl.class" > nul
if errorlevel 1 (
    echo lib\Renderer.jar hat kein com.dan.aurora.gl: im Projekt Renderer "ant jar" und dist\Renderer.jar nach lib\ kopieren. >> "%LOG%"
    goto ende
)
echo === 1 LituyaBay1958 sauber bauen %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" clean jar >> "%LOG%" 2>&1
echo === 2 Pruefung %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 3 Block POLARLICHT im Fenster %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" polarlichtfenster >> "%LOG%" 2>&1
echo === 4 Proben W12 (1201-1207) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w12.txt" "-Dbild.phase=W12P1" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
:ende
echo === FERTIG %TIME% === >> "%LOG%"
