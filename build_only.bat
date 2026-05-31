@echo off
REM Build Android APK Only (without installing)
REM This script only compiles the APK, no device required

echo === Building Android APK Only ===
echo.

REM Set environment variables
set JAVA_HOME=D:\jdk-17.0.2
set ANDROID_HOME=E:\Android\android-sdk
set PATH=%ANDROID_HOME%\platform-tools;%JAVA_HOME%\bin;%PATH%

echo Environment:
echo JAVA_HOME=%JAVA_HOME%
echo ANDROID_HOME=%ANDROID_HOME%
echo.

echo Building APK...
call gradlew.bat assembleDebug --no-daemon
echo.

if errorlevel 1 (
    echo Build failed!
    echo Exit code: %errorlevel%
) else (
    echo Build completed successfully!
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
)
echo.

pause
