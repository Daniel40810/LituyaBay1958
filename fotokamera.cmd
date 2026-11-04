@echo off
rem Werkbuch Welle W0: Kamera der Fotos vom 04.11. aus Oracle lesen (_transfer\fotokamera.txt).
cd /d "%~dp0"
"C:\Program Files\Java\jdk-21\bin\java.exe" -cp lib\ojdbc11.jar _transfer\FotoKamera.java > _transfer\fotokamera_log.txt 2>&1
