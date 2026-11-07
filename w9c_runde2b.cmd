@echo off
rem Werkbuch Welle W9c Runde 2b "Speicher": Die Aufbereitung aus w9c_runde2.cmd brach am 7.11. um 15:55 ab
rem (hs_err_pid16552.log: der Heap wuchs auf 23 GB, Windows konnte nichts mehr zusagen). Jetzt sparsam:
rem Renderer sauber bauen (HydroFlat sparsam) mit ShoreCheck; LituyaBay1958 sauber bauen; freie Zusage von Windows
rem messen und den Heap danach waehlen, die groessten Programme ins Protokoll; Aufbereitung gelaende (neu, Szene mit
rem Lidar), angleich, wald, inhalt (alte Kacheln nach _sicherung\w9c2, schon gesicherte bleiben); bei Fehler ABBRUCH;
rem Pruefung; Proben 931-939 (W9CP2); Heldenbilder 1 14 (W9C2 gegen W9C1). Die Vorher-Bilder W9CP2V sind schon da.
rem Protokoll _transfer\w9\w9c_runde2b_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w9" mkdir "%~dp0_transfer\w9"
set "LOG=%~dp0_transfer\w9\w9c_runde2b_log.txt"
echo === 0 Speicher beim Start %TIME% === > "%LOG%"
call :speicher
echo === 1 Renderer sauber bauen (clean jar, mit Paketpruefung), ShoreCheck %TIME% === >> "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" clean jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.shore.demo.ShoreCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 2 LituyaBay1958 sauber bauen %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" clean jar >> "%LOG%" 2>&1
echo === 3 Speicher vor der Aufbereitung %TIME% === >> "%LOG%"
call :speicher
set "FREI=-1"
for /f "usebackq delims=" %%a in (`powershell -NoProfile -Command "[math]::Floor((Get-CimInstance Win32_OperatingSystem).FreeVirtualMemory/1MB)"`) do set "FREI=%%a"
set "SPEICHER=12g"
if %FREI% LSS 20 set "SPEICHER=8g"
if %FREI% LSS 14 set "SPEICHER=6g"
rem unbekannt (PowerShell gab nichts): 8g und kein Abbruch
if %FREI% LSS 0 set "SPEICHER=8g"
echo Windows sagt noch %FREI% GB zu (-1: unbekannt), Heap der Aufbereitung %SPEICHER% >> "%LOG%"
if %FREI% LSS 0 goto aufbereitung
if %FREI% LSS 10 (
    echo ABBRUCH: Windows kann nur noch %FREI% GB zusagen, die Aufbereitung braucht mindestens 10. Bitte Programme schliessen und neu starten. >> "%LOG%"
    echo ABBRUCH: Windows kann nur noch %FREI% GB zusagen, die Aufbereitung braucht mindestens 10. Bitte Programme schliessen und neu starten.
    goto ende
)
:aufbereitung
echo === 4 Aufbereitung: gelaende neu, angleich, wald, inhalt (Heap %SPEICHER%) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dschritte=gelaende,wald,inhalt" "-Dneu=true" "-Dsicherung=_sicherung/w9c2" "-Dspeicher=%SPEICHER%" aufbereitung >> "%LOG%" 2>&1
if errorlevel 1 (
    echo ABBRUCH: die Aufbereitung ist nicht durchgelaufen, siehe daten\aufbereitung.log. Pruefung und Bilder laufen nicht. >> "%LOG%"
    echo ABBRUCH: die Aufbereitung ist nicht durchgelaufen, siehe daten\aufbereitung.log.
    call :speicher
    goto ende
)
echo === 5 Speicher nach der Aufbereitung %TIME% === >> "%LOG%"
call :speicher
echo === 6 Pruefung %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
echo === 7 Proben W9c Runde 2 (931-939) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w9c2.txt" "-Dbild.phase=W9CP2" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 8 Heldenbilder ohne Objektiv und Film (W9C2 gegen W9C1, nur 1 14) %TIME% === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W9C2" "-Dvorher=W9C1" "-Dnur=1,14" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
:ende
echo === FERTIG %TIME% === >> "%LOG%"
goto :eof

:speicher
powershell -NoProfile -Command "$o = Get-CimInstance Win32_OperatingSystem; 'Windows sagt noch {0:N1} GB zu (Grenze {1:N1} GB), frei im Arbeitsspeicher {2:N1} GB von {3:N1} GB' -f ($o.FreeVirtualMemory/1MB), ($o.TotalVirtualMemorySize/1MB), ($o.FreePhysicalMemory/1MB), ($o.TotalVisibleMemorySize/1MB); 'Die zehn Programme mit der groessten Zusage (GB):'; Get-Process | Sort-Object PagedMemorySize64 -Descending | Select-Object -First 10 | ForEach-Object { '  {0,-32} {1,6:N1}' -f $_.ProcessName, ($_.PagedMemorySize64/1GB) }" >> "%LOG%" 2>&1
goto :eof
