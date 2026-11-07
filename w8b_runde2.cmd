@echo off
rem Werkbuch Welle W8b Runde 2 (5b.4 Punkte 4-8 und 10): Renderer bauen (precip feines Feld, Schichten mit dem Wind, halbe
rem Schritte im Vorhang; ocean feines Feld, Maske der Schauer, Facetten an den Einschlaegen), Pruefungen, Proben 806 808 809 810
rem 776 777 764 775 (W8BP2), dieselben wie W7b (W8BP2ALT), Heldenbilder 1 14 (W8B2 gegen W8B1). Protokoll _transfer\w8\w8b_runde2_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w8" mkdir "%~dp0_transfer\w8"
set "LOG=%~dp0_transfer\w8\w8b_runde2_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 PrecipCheck, CieCheck, CraftCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.precip.demo.PrecipCheck >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.sky.demo.CieCheck >> "%LOG%" 2>&1
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.vessel.demo.CraftCheck >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 3 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 4 Proben W8b Runde 2 === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w8.txt" "-Dbild.phase=W8BP2" "-Dnur=806,808,809,810" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w7r.txt" "-Dbild.phase=W8BP2" "-Dnur=764,775,776,777" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 5 Regen wie W7b (Vergleich und Kosten) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true -Dlituya.regenW7b=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w8.txt" "-Dbild.phase=W8BP2ALT" "-Dnur=810" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w7r.txt" "-Dbild.phase=W8BP2ALT" "-Dnur=764,776,777" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 6 Heldenbilder (W8B2 gegen W8B1, nur 1 14) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbild.phase=W8B2" "-Dvorher=W8B1" "-Dnur=1,14" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
