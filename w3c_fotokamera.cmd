@echo off
rem W3c: Kamera aller F12-Fotos vom 04.11. aus Oracle lesen (_transfer\w3c\fotokamera.txt).
cd /d "%~dp0"
"C:\Program Files\Java\jdk-21\bin\java.exe" -cp lib\ojdbc11.jar _transfer\w3c\FotoKameraW3c.java > _transfer\w3c\fotokamera_log.txt 2>&1
