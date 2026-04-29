@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0gradlew-local.ps1" %*
exit /b %ERRORLEVEL%
