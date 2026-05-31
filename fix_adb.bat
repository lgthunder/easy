@echo off
REM Fix ADB Issues
REM This script kills existing ADB processes and restarts the ADB server

echo === Fixing ADB Issues ===
echo.

REM Set Android SDK path
set ANDROID_HOME=E:\Android\android-sdk
set ADB=%ANDROID_HOME%\platform-tools\adb.exe

echo Killing existing ADB processes...
taskkill /F /IM adb.exe 2>nul
timeout /t 2 /nobreak >nul

echo Starting ADB server...
"%ADB%" start-server
timeout /t 2 /nobreak >nul

echo.
echo Checking connected devices...
"%ADB%" devices
echo.

echo ADB Status:
if exist "%ADB%" (
    echo ADB executable found at: %ADB%
) else (
    echo ERROR: ADB executable not found!
)
echo.

echo Done! You can now try installing the app again.
echo.
pause
