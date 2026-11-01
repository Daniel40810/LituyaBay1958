# Laedt die LOD2-Kacheln aus der Liste "LOD2_nachladen.txt" herunter (Windows PowerShell).
#
# Aufruf (Pfade anpassen, Zielordner = Ordner LOD2 aus daten\rohdaten.properties):
#   .\lod2_download.ps1 -Liste "C:\Pfad\LOD2_nachladen.txt" -Ziel "C:\Pfad\Rohdaten\LOD2" -DryRun
#   .\lod2_download.ps1 -Liste "C:\Pfad\LOD2_nachladen.txt" -Ziel "C:\Pfad\Rohdaten\LOD2"

param(
    [Parameter(Mandatory = $true)][string]$Liste,
    [Parameter(Mandatory = $true)][string]$Ziel,
    [switch]$DryRun
)

$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# 1) Links aus der Liste lesen; Kacheln ohne Link werden gemeldet
$jobs = @{}
$ohneLink = @()
foreach ($z in (Get-Content -Path $Liste)) {
    if ($z -match 'nicht in der') {
        if ($z -match '(LoD2_\S+?\.xml)') { $ohneLink += $matches[1] }
    }
    elseif ($z -match '(https://\S+)') {
        $url = $matches[1]
        if ($url -match 'file=([^&\s]+)') { $jobs[$matches[1]] = $url }
    }
}

Write-Host "$($jobs.Count) Dateien mit Link gefunden."
if ($ohneLink.Count -gt 0) {
    Write-Host "$($ohneLink.Count) Kacheln OHNE Link in der Liste (werden uebersprungen):"
    $ohneLink | ForEach-Object { Write-Host "  $_" }
}

if ($DryRun) { exit 0 }

# 2) Herunterladen (vorhandene Dateien werden uebersprungen, Fehlversuche wiederholt)
New-Item -ItemType Directory -Force -Path $Ziel | Out-Null
$i = 0; $fehler = 0
foreach ($datei in $jobs.Keys) {
    $i++
    $out = Join-Path $Ziel $datei
    if ((Test-Path $out) -and ((Get-Item $out).Length -gt 0)) { continue }

    $ok = $false
    for ($n = 1; $n -le 3 -and -not $ok; $n++) {
        try {
            Invoke-WebRequest -Uri $jobs[$datei] -OutFile $out -UseBasicParsing -Headers @{ 'User-Agent' = 'Mozilla/5.0' }
            $ok = $true
        } catch {
            Write-Host "    Versuch $n fehlgeschlagen: $($_.Exception.Message)"
            Start-Sleep -Seconds (5 * $n)
        }
    }
    if ($ok) { Write-Host "[$i/$($jobs.Count)] OK     $datei" }
    else     { Write-Host "[$i/$($jobs.Count)] FEHLER $datei"; $fehler++ }
}

if ($fehler -eq 0) { Write-Host "Fertig. Danach im Projekt: ant aufbereitung -Dschritt=bauten,inhalt" }
else { Write-Host "Fertig mit $fehler Fehlern. Skript erneut starten, um fehlende Dateien nachzuholen." }
