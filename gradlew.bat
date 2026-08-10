@echo off
setlocal
set GRADLE_VERSION=9.5.0
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set CACHE_DIR=%GRADLE_USER_HOME%\native-wrapper
set GRADLE_HOME=%CACHE_DIR%\gradle-%GRADLE_VERSION%
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$zip='%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip'; if (!(Test-Path $zip)) { Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile $zip }; Expand-Archive -Path $zip -DestinationPath '%CACHE_DIR%' -Force"
)
call "%GRADLE_HOME%\bin\gradle.bat" %*
