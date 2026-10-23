@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title Music Display - Minecraft 26.3 Build
set "LOG=%CD%\build-26.3.log"
set "LOG_PATH=%LOG%"
set "TOOLS=%USERPROFILE%\.dualmusichud-tools"
set "GRADLE=%TOOLS%\gradle-9.7.1\bin\gradle.bat"
echo =======================================
echo Music Display - Minecraft 26.3
echo =======================================
echo.
set "JDK25_HOME="
set "JDK25_RESULT=%TEMP%\md_java25_%RANDOM%_%RANDOM%.txt"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-java25.ps1" > "%JDK25_RESULT%"
if errorlevel 1 (
  echo [FEHLER] Die Java-25-Suche ist fehlgeschlagen.
  echo [INFO] Bitte pruefe, ob JDK 25 mit javac.exe installiert ist.
  if exist "%JDK25_RESULT%" del /q "%JDK25_RESULT%" >nul 2>nul
  goto :bad
)
if exist "%JDK25_RESULT%" (
  set /p "JDK25_HOME=" < "%JDK25_RESULT%"
  del /q "%JDK25_RESULT%" >nul 2>nul
)
if not defined JDK25_HOME (
  echo [FEHLER] Java 25 JDK wurde nicht gefunden.
  echo [INFO] Pruefe deine Java-25-Installation unter Program Files.
  echo [INFO] Java 21 bleibt fuer Minecraft 1.21.11 erhalten.
  goto :bad
)
if not exist "%JDK25_HOME%\bin\java.exe" (
  echo [FEHLER] Java nicht vorhanden unter: "%JDK25_HOME%"
  goto :bad
)
set "JAVA_HOME=%JDK25_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [INFO] Verwende Java 25 aus: %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version 2>&1
echo.
if exist "gradlew.bat" (
  set "GRADLE=%CD%\gradlew.bat"
  echo [INFO] Gradle Wrapper vorhanden.
) else (
  if not exist "%GRADLE%" (
    echo [INFO] Gradle 9.7.1 ist nicht vorhanden. Lade es automatisch herunter.
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%CD%\setup-gradle.ps1"
    if errorlevel 1 (
      echo [FEHLER] Gradle Download fehlgeschlagen. Pruefe Internet und Speicherplatz.
      goto :bad
    )
  ) else (
    echo [INFO] Verwende gemeinsames Gradle aus %TOOLS%
  )
)
if not exist "%GRADLE%" (
  echo [FEHLER] Gradle Startdatei fehlt: %GRADLE%
  goto :bad
)
echo [INFO] Baue die Windows-Medienbruecke fuer den automatischen Start...
where dotnet >nul 2>nul
if errorlevel 1 (
  echo [FEHLER] .NET 8 SDK fehlt. Fuer eine Mod ohne separate Bridge wird es beim Build benoetigt.
  echo Bitte zuerst .NET 8 SDK installieren.
  goto :bad
)
dotnet publish "%CD%\WindowsMediaBridge\WindowsMediaBridge.csproj" -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true /p:PublishTrimmed=false
if errorlevel 1 (
  echo [FEHLER] Windows Bridge konnte nicht erstellt werden.
  goto :bad
)
if not exist "%CD%\WindowsMediaBridge\bin\Release\net8.0-windows10.0.19041.0\win-x64\publish\WindowsMediaBridge.exe" (
  echo [FEHLER] WindowsMediaBridge.exe wurde nicht erstellt.
  goto :bad
)
echo [INFO] WalksyLib ist eine optionale Mod-Abhaengigkeit. Kein ReGlass-Download.
echo [INFO] Starte Fabric Build. Log: %LOG%
call "%GRADLE%" --no-daemon clean build --stacktrace > "%LOG%" 2>&1
if errorlevel 1 (
  echo.
  echo [FEHLER] Build fehlgeschlagen. Erste wichtige Compilerfehler:
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%CD%\show-build-errors.ps1" -LogPath "%LOG%"
  echo.
  echo Vollstaendiges Fehlerprotokoll: "%LOG%"
  goto :bad
)
echo.
echo [INFO] Pruefe JAR-Ausgabe...
if exist "build\libs\Musicdisplay.jar" goto :jarok
set "SOURCE_JAR="
for %%F in ("build\libs\*.jar") do (
  echo %%~nxF | findstr /i /c:"sources" /c:"dev" /c:"javadoc" >nul
  if errorlevel 1 set "SOURCE_JAR=%%~fF"
)
if not defined SOURCE_JAR (
  echo [FEHLER] Keine nutzbare Mod-JAR gefunden. Bitte build-26.3.log pruefen.
  goto :bad
)
copy /y "%SOURCE_JAR%" "build\libs\Musicdisplay.jar" >nul
if errorlevel 1 goto :bad
:jarok
echo [OK] Build erfolgreich: build\libs\Musicdisplay.jar
pause
exit /b 0
:bad
echo.
echo [HINWEIS] Bitte build-26.3.log kontrollieren.
pause
exit /b 1
