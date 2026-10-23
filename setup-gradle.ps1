$ErrorActionPreference = 'Stop'
$tools = Join-Path $env:USERPROFILE '.dualmusichud-tools'
$ver = '9.7.1'
$folder = Join-Path $tools "gradle-$ver"
$bin = Join-Path $folder 'bin\gradle.bat'
if (Test-Path $bin) { Write-Host "Gradle $ver bereits vorhanden unter $tools"; exit 0 }
New-Item -ItemType Directory -Force -Path $tools | Out-Null
$zip = Join-Path $tools "gradle-$ver-bin.zip"
$uri = "https://services.gradle.org/distributions/gradle-$ver-bin.zip"
Write-Host "Lade Gradle $ver von $uri herunter (ca. 135 MB)..."
try {
    Invoke-WebRequest -Uri $uri -OutFile $zip -MaximumRedirection 10
    Write-Host 'Entpacke Gradle...'
    Expand-Archive -LiteralPath $zip -DestinationPath $tools -Force
    if (-not (Test-Path $bin)) { throw 'gradle.bat wurde nicht gefunden.' }
} finally {
    if (Test-Path $zip) { Remove-Item $zip -Force }
}
Write-Host "Gradle installiert unter $tools"
