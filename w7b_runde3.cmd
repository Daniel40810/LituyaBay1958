@echo off
rem Werkbuch Welle W7b "Regen", Runde 3: com.dan.precip (neu in Renderer.jar) - Physik gegen die Literatur, Selbsttest
rem Grafikkarte = Prozessor, acht Proben der Demo, Pruefung, Proben 761, 762, 764, 765, 766, 771 (W7BP3): Regen im Spiegel, ohne Zickzack, Schichten ohne Rauschen, Heldenbilder 1, 14 (W7B3 gegen W7R1D).
rem Doppelklick; Protokoll _transfer\w7\w7b_runde3_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w7" mkdir "%~dp0_transfer\w7"
set "LOG=%~dp0_transfer\w7\w7b_runde3_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 PrecipCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.precip.demo.PrecipCheck >> "%LOG%" 2>&1
echo === 3 PrecipDemo (Selbsttest, acht Proben) === >> "%LOG%"
java -Dstdout.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -cp "%RCP%" com.dan.precip.demo.PrecipDemo --bild "%~dp0_transfer\w7\precipdemo_b3.png" 3840 >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 4 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 5 Proben W7b Runde 3 === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w7r.txt" "-Dbild.phase=W7BP3" "-Dnur=761,762,764,765,766,771" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 6 Heldenbilder (W7B3 gegen W7R1D, nur 1 14) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W7B3" "-Dvorher=W7R1D" "-Dnur=1,14" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === FERTIG %TIME% === >> "%LOG%"
