@echo off
setlocal
set SCRIPT_DIR=%~dp0
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%capture-background-timer-evidence.ps1" %*
exit /b %ERRORLEVEL%
