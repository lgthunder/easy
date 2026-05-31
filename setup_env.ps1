# Setup Environment Variables for Android Project
# PowerShell version

Write-Host "=== Android Project Environment Setup" -ForegroundColor Green
Write-Host ""

$os = [System.Environment]::OSVersion.Platform

if ($os -eq "Win32NT") {
    Write-Host "Detected: Windows System" -ForegroundColor Cyan
    Write-Host ""
    
    $javaHome = "D:\jdk-17.0.2"
    $androidHome = "E:\Android\android-sdk"
    
    Write-Host "Setting JAVA_HOME: $javaHome" -ForegroundColor Yellow
    $env:JAVA_HOME = $javaHome
    
    Write-Host "Setting ANDROID_HOME: $androidHome" -ForegroundColor Yellow
    $env:ANDROID_HOME = $androidHome
    
    $env:PATH = "$androidHome\platform-tools;$androidHome\tools;$javaHome\bin;$env:PATH"
    
    Write-Host ""
    Write-Host "Environment variables set (current session only)" -ForegroundColor Green
    Write-Host ""
    Write-Host "JAVA_HOME: $env:JAVA_HOME"
    Write-Host "ANDROID_HOME: $env:ANDROID_HOME"
    
} elseif ($os -eq "Unix") {
    Write-Host "Detected: Unix/Mac System" -ForegroundColor Cyan
    Write-Host "Please use setup_env.sh instead" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Done!" -ForegroundColor Green
