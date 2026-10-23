$ErrorActionPreference = 'Continue'
$candidates = New-Object System.Collections.Generic.List[string]
if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
$roots = @($env:ProgramFiles, ${env:ProgramFiles(x86)}, 'C:\Program Files', 'C:\Program Files (x86)') | Where-Object { $_ } | Select-Object -Unique
foreach ($root in $roots) {
  foreach ($sub in @('Eclipse Adoptium','Java','Microsoft','Zulu','Azul Systems','Amazon Corretto','BellSoft','Semeru')) {
    $parent = Join-Path $root $sub
    if (Test-Path -LiteralPath $parent -PathType Container) {
      Get-ChildItem -LiteralPath $parent -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '25' } | ForEach-Object { $candidates.Add($_.FullName) }
    }
  }
}
$cmd = Get-Command javac.exe -ErrorAction SilentlyContinue
if ($cmd -and $cmd.Source) { $candidates.Add((Split-Path -Parent (Split-Path -Parent $cmd.Source))) }
foreach ($candidate in ($candidates | Select-Object -Unique)) {
  $java = Join-Path $candidate 'bin\java.exe'
  $javac = Join-Path $candidate 'bin\javac.exe'
  if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or -not (Test-Path -LiteralPath $javac -PathType Leaf)) { continue }
  try {
    $version = (& $javac -version 2>$null | Out-String).Trim()
    if ($version -match '^javac 25(?:\.|\s|$)') {
      [Console]::Out.WriteLine($candidate)
      exit 0
    }
  } catch { continue }
}
[Console]::Error.WriteLine('Kein verwendbares JDK 25 gefunden. Geprueft: ' + (($candidates | Select-Object -Unique) -join '; '))
exit 2
