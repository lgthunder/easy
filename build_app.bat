@echo off
set JAVA_HOME=D:\jdk-17.0.2
set ANDROID_HOME=E:\Android\android-sdk
set PATH=%ANDROID_HOME%\tools;%ANDROID_HOME%\platform-tools;%PATH%

echo JAVA_HOME=%JAVA_HOME%
echo ANDROID_HOME=%ANDROID_HOME%
echo Starting build...

call gradlew.bat assembleDebug --no-daemon 2>&1
echo Build completed with exit code: %errorlevel%
pause