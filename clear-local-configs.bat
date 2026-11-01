@echo off
setlocal EnableExtensions

rem Clears Wrights client/server configs in the local NeoForge run folder
rem so the next ./gradlew runClient starts with fresh defaults.
rem Does NOT delete worlds, options.txt, or other mods' configs.

set "CFG=%LOCALAPPDATA%\Wrights\run\config"

echo.
echo Wrights — clear local test configs
echo Target: "%CFG%"
echo.

if not exist "%CFG%" (
  echo No run config folder found. Nothing to clear.
  echo.
  pause
  exit /b 0
)

echo Close runClient / the game first if it is open, or files may be locked.
echo.
choice /C YN /M "Delete Wrights configs and backups"
if errorlevel 2 (
  echo Cancelled.
  pause
  exit /b 0
)

set "GONE=0"
for %%F in (
  "%CFG%\wrights-client.toml"
  "%CFG%\wrights-server.toml"
  "%CFG%\wrights-clipboard.txt"
) do (
  if exist %%F (
    del /f /q %%F >nul 2>&1
    if exist %%F (
      echo Failed: %%~nxF  ^(file in use?^)
    ) else (
      echo Deleted: %%~nxF
      set /a GONE+=1
    )
  )
)

for %%F in ("%CFG%\wrights-client-*.toml.bak" "%CFG%\wrights-server-*.toml.bak") do (
  if exist %%F (
    del /f /q %%F >nul 2>&1
    if not exist %%F (
      echo Deleted: %%~nxF
      set /a GONE+=1
    )
  )
)

echo.
if %GONE%==0 (
  echo No Wrights config files were present.
) else (
  echo Cleared %GONE% file^(s^). Next runClient will regenerate defaults.
)
echo.
pause
endlocal
