@echo off
echo Testing device detection...
echo.

set ANDROID_HOME=E:\Android\android-sdk
set DEVICE_FOUND=0

echo Step 1: Get devices list:
"%ANDROID_HOME%\platform-tools\adb.exe" devices > temp_devices.txt
type temp_devices.txt
echo.

echo Step 2: Testing detection logic:
findstr /i "device" temp_devices.txt > device_found.txt
for /f %%a in (device_found.txt) do (
    echo Found line: %%a
    if not "%%a"=="List" (
        if not "%%a"=="of" (
            if not "%%a"=="attached" (
                set DEVICE_FOUND=1
                echo   -> Setting DEVICE_FOUND=1
            )
        )
    )
)

del temp_devices.txt 2>nul
del device_found.txt 2>nul

echo.
echo Result: DEVICE_FOUND=%DEVICE_FOUND%
echo.

if %DEVICE_FOUND%==1 (
    echo Device detected!
) else (
    echo No device detected
)
echo.

pause
