@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0verify-local.ps1" %*
exit /b %ERRORLEVEL%
