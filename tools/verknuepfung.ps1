# Phase 20 (LituyaBay1958): Verknuepfungen auf dem Desktop und im Startmenue, aufgerufen von "ant verknuepfung".
# Startet javaw ohne Konsolenfenster im Projektordner (daten/, docs/status, db/ liegen dort), mit dem Symbol icon/LituyaBay1958.ico.
# Nur ASCII in dieser Datei (Windows PowerShell 5 liest Skripte ohne BOM als ANSI).
param(
    [Parameter(Mandatory = $true)][string]$Datei,
    [string]$Wo = "beide"
)
$ErrorActionPreference = "Stop"
# Die Datei (von ant verknuepfung, UTF-8) hat fuenf Zeilen: Ziel (javaw.exe), Argumente, Ordner, Symbol, Name.
$z = Get-Content -LiteralPath $Datei -Encoding UTF8
if ($z.Count -lt 5) { throw "$Datei hat nicht fuenf Zeilen" }
$Ziel = $z[0].Trim(); $Argumente = $z[1].Trim(); $Ordner = $z[2].Trim(); $Symbol = $z[3].Trim(); $Name = $z[4].Trim()
if ($Argumente.Length -gt 1000) { throw ("Argumente zu lang fuer eine Verknuepfung: " + $Argumente.Length + " Zeichen") }
$Ziel = [System.IO.Path]::GetFullPath($Ziel)
$Ordner = [System.IO.Path]::GetFullPath($Ordner)
$Symbol = [System.IO.Path]::GetFullPath($Symbol)
if (-not (Test-Path $Ziel)) { throw "javaw nicht gefunden: $Ziel" }
if (-not (Test-Path $Symbol)) { throw "Symbol nicht gefunden: $Symbol (ant symbol)" }
$orte = @()
if ($Wo -eq "beide" -or $Wo -eq "desktop") { $orte += [Environment]::GetFolderPath("Desktop") }
if ($Wo -eq "beide" -or $Wo -eq "startmenue") { $orte += [Environment]::GetFolderPath("Programs") }
$sh = New-Object -ComObject WScript.Shell
foreach ($o in $orte) {
    $datei = Join-Path $o ($Name + ".lnk")
    $l = $sh.CreateShortcut($datei)
    $l.TargetPath = $Ziel
    $l.Arguments = $Argumente
    $l.WorkingDirectory = $Ordner
    $l.IconLocation = $Symbol + ",0"
    $l.Description = "Lituya Bay 1958 - das Projekt der Riesenwelle und des Netzes"
    $l.WindowStyle = 1
    $l.Save()
    Write-Output ("Verknuepfung: " + $datei)
}
