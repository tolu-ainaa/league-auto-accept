@echo off
rem Compiles the app into the out\ folder.
setlocal
cd /d "%~dp0"

set "JAVAC=javac"
where javac >nul 2>nul
if errorlevel 1 (
    for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do set "JAVAC=%%d\bin\javac.exe"
)

if not exist out mkdir out
"%JAVAC%" -d out src\autoaccept\*.java
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)
echo Build OK.
