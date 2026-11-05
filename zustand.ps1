# Werkbuch Welle W5 Runde 8: Zustand des Rechners alle 2 s (WMI-Klassen mit englischen Namen, typeperf kennt auf
# deutschem Windows nur die übersetzten Zählernamen). Ende, sobald _transfer\w5\zustand.stop da ist.
$aus = Join-Path $PSScriptRoot "_transfer\w5\zustand.csv"
$stop = Join-Path $PSScriptRoot "_transfer\w5\zustand.stop"
if (Test-Path $stop) { Remove-Item $stop }
"Zeit;Netz;Akku;CPU_Leistung_Prozent;CPU_Auslastung_Prozent;Temperatur_C;GPU_3D_Prozent" | Out-File -Encoding utf8 $aus
while (-not (Test-Path $stop)) {
    $b = Get-CimInstance Win32_Battery -ErrorAction SilentlyContinue | Select-Object -First 1
    $p = Get-CimInstance Win32_PerfFormattedData_Counters_ProcessorInformation -Filter "Name='_Total'" -ErrorAction SilentlyContinue
    $t = Get-CimInstance Win32_PerfFormattedData_Counters_ThermalZoneInformation -ErrorAction SilentlyContinue | Measure-Object -Property Temperature -Maximum
    $g = Get-CimInstance Win32_PerfFormattedData_GPUPerformanceCounters_GPUEngine -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*engtype_3D" } | Measure-Object -Property UtilizationPercentage -Sum
    $temp = if ($t.Maximum) { [math]::Round($t.Maximum - 273.15, 1) } else { "" }
    "{0};{1};{2};{3};{4};{5};{6}" -f (Get-Date -Format "HH:mm:ss"), $b.BatteryStatus, $b.EstimatedChargeRemaining, $p.PercentProcessorPerformance, $p.PercentProcessorUtility, $temp, $g.Sum | Out-File -Append -Encoding utf8 $aus
    Start-Sleep -Seconds 2
}
