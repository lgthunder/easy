@echo off
REM Setup Environment Variables for Android Project
REM Batch script version

echo === Android Project Environment Setup
echo.

REM Detect OS (though this is Windows-specific)
echo Detected: Windows System
echo.

REM Windows system environment setup
set JAVA_HOME=D:\jdk-17.0.2
set ANDROID_HOME=E:\Android\android-sdk

echo Setting JAVA_HOME: %JAVA_HOME%
echo Setting ANDROID_HOME: %ANDROID_HOME%

set PATH=%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\tools;%JAVA_HOME%\bin;%PATH%

echo.
echo Environment variables set (current session only)
echo.
echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_HOME: %ANDROID_HOME%
echo.
echo Done!
echo.
