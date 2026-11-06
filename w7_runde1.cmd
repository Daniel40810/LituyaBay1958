@echo off
rem Werkbuch Welle W7 "Wetter", Runde 1: Renderer.jar neu (weather: Uebergang, Wechsel ohne Sprung, Naesse; cloud: Wolken am Gelaende;
rem fog: Vorstoss des Nebels) mit WeatherCheck, CloudCheck, FogCheck; Pruefung; Heldenbilder W7R1 gegen W6R8F (H9 neu, H1 bei Regen neu);
rem Proben W7 (701-707); Clip "Wetterwechsel" (60 s). Doppelklick; Protokoll _transfer\w7\w7_runde1_log.txt.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "UP=-Duser.properties.file=C:\Users\Dan40810\AppData\Roaming\NetBeans\30\build.properties"
if not exist "%~dp0_transfer\w7" mkdir "%~dp0_transfer\w7"
set "LOG=%~dp0_transfer\w7\w7_runde1_log.txt"
echo === 1 Renderer bauen === > "%LOG%"
pushd ..\Renderer
call "%ANT%" "%UP%" jar >> "%LOG%" 2>&1
set "RCP=dist\Renderer.jar;lib\FStyle.jar;lib\jogl-all.jar;lib\gluegen-rt.jar;lib\jogl-all-natives-windows-amd64.jar;lib\gluegen-rt-natives-windows-amd64.jar"
echo === 2 WeatherCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.weather.demo.WeatherCheck >> "%LOG%" 2>&1
echo === 3 CloudCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.cloud.demo.CloudCheck >> "%LOG%" 2>&1
echo === 4 FogCheck === >> "%LOG%"
java -Dstdout.encoding=UTF-8 -cp "%RCP%" com.dan.fog.demo.FogCheck >> "%LOG%" 2>&1
echo === 5 CloudDemo (Grafikkarte = Prozessor) === >> "%LOG%"
java -Dstdout.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -cp "%RCP%" com.dan.cloud.demo.CloudDemo --bild "%~dp0_transfer\w7\clouddemo.png" 1620 >> "%LOG%" 2>&1
popd
copy /Y ..\Renderer\dist\Renderer.jar lib\Renderer.jar >> "%LOG%" 2>&1
echo === 6 Pruefung === >> "%LOG%"
call "%ANT%" "%UP%" pruefung >> "%LOG%" 2>&1
echo === 7 Proben W7 (701-707) === >> "%LOG%"
set "LITUYA_BILDBANK_ZUSATZ=-Dlituya.teilzeiten=true"
call "%ANT%" "%UP%" "-Dbildbank=src/com/dan/lituya/scene/aufnahmen_w7.txt" "-Dbild.phase=W7P1" "-Dbreite=1920" "-Dhoehe=1080" bildbank >> "%LOG%" 2>&1
echo === 8 Heldenbilder (W7R1 gegen W6R8F) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbild.phase=W7R1" "-Dvorher=W6R8F" bildbank >> "%LOG%" 2>&1
set "LITUYA_BILDBANK_ZUSATZ="
echo === 9 Clip Wetterwechsel (60 s, 1280 x 720) === >> "%LOG%"
call "%ANT%" "%UP%" "-Dbuch=Wetterwechsel" "-Dbreite=1280" "-Dhoehe=720" film >> "%LOG%" 2>&1
echo === FERTIG %TIME% === >> "%LOG%"
