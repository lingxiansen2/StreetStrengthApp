@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-emulator-local.ps1" %*
exit /b %ERRORLEVEL%
