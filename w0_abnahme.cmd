@echo off
rem Werkbuch Welle W0 "Scharf": Abnahme auf der Radeon. Doppelklick genuegt; Protokoll in _transfer\w0_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT="
for %%a in (ant.bat) do if not "%%~$PATH:a"=="" set "ANT=%%~$PATH:a"
if not defined ANT for /d %%d in ("C:\Program Files\NetBeans*" "C:\Program Files\Apache NetBeans*" "%LOCALAPPDATA%\Programs\NetBeans*" "C:\NetBeans*") do if exist "%%~d\netbeans\extide\ant\bin\ant.bat" set "ANT=%%~d\netbeans\extide\ant\bin\ant.bat"
if not defined ANT for /d %%d in ("C:\Program Files\NetBeans*" "C:\Program Files\Apache NetBeans*") do if exist "%%~d\extide\ant\bin\ant.bat" set "ANT=%%~d\extide\ant\bin\ant.bat"
set "LOG=_transfer\w0_log.txt"
echo W0 Abnahme %DATE% %TIME% > "%LOG%"
if not defined ANT (echo FEHLER: ant.bat nicht gefunden >> "%LOG%" & echo ant.bat nicht gefunden & pause & exit /b 1)
echo Ant: %ANT% >> "%LOG%"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
echo === 1 Aufnahmen nachgestellt (W0A gegen A0) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen.txt" "-Dbild.phase=W0A" "-Dvorher=A0" "-Dbreite=1495" "-Dhoehe=940" bildbank >> "%LOG%" 2>&1
echo === 2 Heldenbilder (W0 gegen B0) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W0" "-Dvorher=B0" bildbank >> "%LOG%" 2>&1
echo === 3 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
