param([string]$LogPath)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $LogPath)) {
    Write-Host '[FEHLER] Build-Protokoll fehlt.'
    exit 1
}
$lines = @(Get-Content -LiteralPath $LogPath -Encoding UTF8)
$patterns = @('Fehler:', 'error:', '* What went wrong:', 'FAILURE:', 'Could not resolve', 'Could not find', 'Compilation failed')
$hits = @()
for ($i=0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    foreach ($needle in $patterns) {
        if ($line.Contains($needle)) {
            $hits += $i
            break
        }
    }
    if ($hits.Count -ge 15) { break }
}
if ($hits.Count -eq 0) {
    Write-Host '[INFO] Keine eindeutigen Fehlermeldungen gefunden. Letzte 35 Zeilen:'
    $lines | Select-Object -Last 35
} else {
    foreach ($i in $hits) {
        $start = [Math]::Max(0, $i - 1)
        $end = [Math]::Min($lines.Count - 1, $i + 4)
        for ($j = $start; $j -le $end; $j++) {
            Write-Host $lines[$j]
        }
        Write-Host '------------------------'
    }
}
