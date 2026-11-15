@echo off
rem Builds the distributable installer: compile -> jar -> app image -> setup exe.
setlocal
cd /d "%~dp0"

set "JDK_BIN="
where javac >nul 2>nul && set "JDK_BIN="
for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do set "JDK_BIN=%%d\bin\"

call "%~dp0build.bat" || exit /b 1

if not exist build mkdir build
"%JDK_BIN%jar.exe" --create --file build\LeagueAutoAccept.jar --main-class autoaccept.App -C out . || exit /b 1

rem jpackage refuses to overwrite an existing app image
if exist "dist\League Auto Accept" rmdir /s /q "dist\League Auto Accept"

"%JDK_BIN%jpackage.exe" --type app-image --name "League Auto Accept" ^
  --input build --main-jar LeagueAutoAccept.jar --main-class autoaccept.App ^
  --icon assets\icon.ico --dest dist --app-version 1.0.0 --vendor "Tolu" ^
  --add-modules java.base,java.desktop,java.net.http,jdk.charsets,jdk.crypto.ec,jdk.crypto.mscapi ^
  --java-options "-Xmx128m" || exit /b 1

"%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe" /Q installer.iss || exit /b 1

echo.
echo Done: dist\LeagueAutoAccept-Setup-1.0.0.exe
