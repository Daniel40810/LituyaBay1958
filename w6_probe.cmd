@echo off
rem Werkbuch Welle W6 „Landschaft“: Probe. Doppelklick; Protokoll _transfer\w6\w6_probe_log.txt. Runde und Bilder aus _transfer\w6\runde.txt (Zeile 1: Phase der Proben, Zeile 2: nur Proben, Zeile 3: Phase der Heldenbilder, Zeile 4: nur Heldenbilder).
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
set "LOG=_transfer\w6\w6_probe_log.txt"
< _transfer\w6\runde.txt (
  set /p PQ=
  set /p NQ=
  set /p PH=
  set /p NH=
)
echo === 1 Proben W6 (%PQ%: %NQ%) === > "%LOG%"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w6.txt" "-Dbild.phase=%PQ%" "-Dnur=%NQ%" "-Dbreite=1280" "-Dhoehe=720" bildbank >> "%LOG%" 2>&1
echo === 2 Heldenbilder (%PH%: %NH%) gegen W5 === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=%PH%" "-Dnur=%NH%" "-Dvorher=W5" "-Dbreite=1280" "-Dhoehe=720" bildbank >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
