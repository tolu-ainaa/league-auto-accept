@echo off
rem Launches the app (build.bat must have been run at least once).
setlocal
cd /d "%~dp0"

set "JAVAW=javaw"
where javaw >nul 2>nul
if errorlevel 1 (
    for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do set "JAVAW=%%d\bin\javaw.exe"
)

start "" "%JAVAW%" -cp out autoaccept.App
