# Laedt alle Kacheln aus den Massendownload-GeoJSONs im Ordner, deren Mitte in einer BBox liegt.
# Standard-BBox: Timmendorfer Strand (EPSG:25832). Andere BBox mit -BBox "xmin,ymin,xmax,ymax".
#
# Aufruf (PowerShell, Ordner anpassen):
#   .\bbox_download.ps1 -Quelle "C:\Users\Dan40810\Downloads\geojson" -Ziel "C:\Daten\Timmendorf" -DryRun
#   .\bbox_download.ps1 -Quelle "C:\Users\Dan40810\Downloads\geojson" -Ziel "C:\Daten\Timmendorf"

param(
    [Parameter(Mandatory = $true)][string]$Quelle,
    [Parameter(Mandatory = $true)][string]$Ziel,
    [string]$BBox = "612000,5977000,630000,5991000",
    [switch]$DryRun
)

$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$b = $BBox.Split(',') | ForEach-Object { [double]$_ }
$xmin = $b[0]; $ymin = $b[1]; $xmax = $b[2]; $ymax = $b[3]

# 1) Kacheln in der BBox aus allen GeoJSON-Dateien sammeln
$jobs = @()
foreach ($gj in Get-ChildItem -Path $Quelle -Filter *.geojson) {
    $d = Get-Content -Raw -Encoding UTF8 -Path $gj.FullName | ConvertFrom-Json
    $produkt = ($d.name -split '_')[0]
    foreach ($ft in $d.features) {
        # DGM1/DOP20/bDOM: link_data, LOD2: data_link
        $url = [string]$ft.properties.link_data
        if (-not $url) { $url = [string]$ft.properties.data_link }
        if ($url -notmatch 'file=([^&]+)') { continue }
        $datei = $matches[1]
        # MultiPolygon (DGM1/DOP20/bDOM) oder Polygon (LOD2)
        if ($ft.geometry.type -eq 'MultiPolygon') { $ring = $ft.geometry.coordinates[0][0] }
        else                                      { $ring = $ft.geometry.coordinates[0] }
        $xs = @($ring | ForEach-Object { [double]$_[0] })
        $ys = @($ring | ForEach-Object { [double]$_[1] })
        $cx = (($xs | Measure-Object -Minimum).Minimum + ($xs | Measure-Object -Maximum).Maximum) / 2
        $cy = (($ys | Measure-Object -Minimum).Minimum + ($ys | Measure-Object -Maximum).Maximum) / 2
        if ($cx -ge $xmin -and $cx -le $xmax -and $cy -ge $ymin -and $cy -le $ymax) {
            $jobs += [pscustomobject]@{ Produkt = $produkt; Datei = $datei; Url = $url }
        }
    }
}

Write-Host "BBox: $BBox"
Write-Host "$($jobs.Count) Kacheln in der BBox gefunden:"
$jobs | Group-Object Produkt | ForEach-Object { Write-Host "  $($_.Name): $($_.Count)" }

if ($DryRun) { exit 0 }

# 2) Herunterladen (vorhandene Dateien werden uebersprungen, Fehlversuche wiederholt)
$i = 0; $fehler = 0
foreach ($j in $jobs) {
    $i++
    $dir = Join-Path $Ziel $j.Produkt
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $out = Join-Path $dir $j.Datei
    if ((Test-Path $out) -and ((Get-Item $out).Length -gt 0)) { continue }

    $ok = $false
    for ($n = 1; $n -le 3 -and -not $ok; $n++) {
        try {
            Invoke-WebRequest -Uri $j.Url -OutFile $out -UseBasicParsing -Headers @{ 'User-Agent' = 'Mozilla/5.0' }
            $ok = $true
        } catch {
            Write-Host "    Versuch $n fehlgeschlagen: $($_.Exception.Message)"
            Start-Sleep -Seconds (5 * $n)
        }
    }
    if ($ok) { Write-Host "[$i/$($jobs.Count)] OK     $($j.Produkt)/$($j.Datei)" }
    else     { Write-Host "[$i/$($jobs.Count)] FEHLER $($j.Produkt)/$($j.Datei)"; $fehler++ }
}

if ($fehler -eq 0) { Write-Host "Fertig." } else { Write-Host "Fertig mit $fehler Fehlern. Skript erneut starten, um fehlende Dateien nachzuholen." }
