@echo off
REM Build and Install Android App (Auto Mode)
REM Automatically handles missing devices by building APK

echo ======================================
echo   Android Build and Install Script
echo ======================================
echo.

REM Set environment variables
set JAVA_HOME=D:\jdk-17.0.2
set ANDROID_HOME=E:\Android\android-sdk
set PATH=%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\tools;%JAVA_HOME%\bin;%PATH%

REM App configuration
set APP_PACKAGE=com.lei.save_box
set MAIN_ACTIVITY=FakeHomeActivity

echo [1/4] Environment setup:
echo   JAVA_HOME=%JAVA_HOME%
echo   ANDROID_HOME=%ANDROID_HOME%
echo   APP_PACKAGE=%APP_PACKAGE%
echo.

REM Step 1: Fix ADB
@REM echo [2/4] Fixing ADB...
@REM taskkill /F /IM adb.exe 2>nul
@REM timeout /t 1 /nobreak >nul
@REM "%ANDROID_HOME%\platform-tools\adb.exe" start-server > nul 2>&1
@REM echo   ADB server started
@REM echo.

REM Step 2: Check devices
echo [3/4] Checking devices...
set DEVICE_FOUND=0

"%ANDROID_HOME%\platform-tools\adb.exe" devices > temp_devices.txt
findstr /i "device" temp_devices.txt > device_found.txt
for /f %%a in (device_found.txt) do (
    if not "%%a"=="List" (
        if not "%%a"=="of" (
            if not "%%a"=="attached" (
                set DEVICE_FOUND=1
            )
        )
    )
)

del temp_devices.txt 2>nul
del device_found.txt 2>nul

"%ANDROID_HOME%\platform-tools\adb.exe" devices
echo.

if %DEVICE_FOUND%==1 (
    echo   Device found - will install after build
) else (
    echo   No devices found - will build APK only
)
echo.

REM Step 3: Build APK
echo [4/4] Building APK...
echo.
call gradlew.bat assembleDebug --no-daemon

if errorlevel 1 (
    echo.
    echo ======================================
    echo   BUILD FAILED!
    echo ======================================
    pause
    exit /b 1
)

echo.
echo ======================================
echo   BUILD SUCCESSFUL!
echo ======================================
echo.

REM Step 4: Install if device found
if %DEVICE_FOUND%==1 (
    echo Installing APK to device...
    echo.
    "%ANDROID_HOME%\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
    
    if errorlevel 1 (
        echo.
        echo   INSTALLATION FAILED!
        echo   APK is ready at:
        echo   app\build\outputs\apk\debug\app-debug.apk
    ) else (
        echo.
        echo   INSTALLATION SUCCESSFUL!
        echo.
        echo   Launching app...
        "%ANDROID_HOME%\platform-tools\adb.exe" shell am start -n %APP_PACKAGE%/.%MAIN_ACTIVITY%
    )
) else (
    echo ======================================
    echo   NO DEVICE CONNECTED
    echo ======================================
    echo.
    echo   APK built successfully!
    echo   Location: app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo   To install manually:
    echo   1. Connect your Android device
    echo   2. Enable USB debugging on the device
    echo   3. Run: adb install -r app\build\outputs\apk\debug\app-debug.apk
    echo   4. Run: adb shell am start -n %APP_PACKAGE%/.%MAIN_ACTIVITY%
    echo.
)

echo.
echo   Done!
echo.

pause
