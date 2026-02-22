@echo off
echo Setting up ymorozov.ru in hosts file...
echo.

:: Check for admin rights
net session >nul 2>&1
if %errorLevel% == 0 (
    echo Running with admin privileges...
    goto :run
) else (
    echo Requesting admin privileges...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

:run
cd /d "%~dp0"

:: Check if entry exists
findstr /C:"ymorozov.ru" %SystemRoot%\System32\drivers\etc\hosts >nul
if %errorLevel% == 0 (
    echo [OK] ymorozov.ru already in hosts file
) else (
    echo Adding 127.0.0.1 ymorozov.ru to hosts...
    echo 127.0.0.1 ymorozov.ru >> %SystemRoot%\System32\drivers\etc\hosts
    if %errorLevel% == 0 (
        echo [OK] Entry added successfully
    ) else (
        echo [ERROR] Failed to add entry
        pause
        exit /b 1
    )
)

echo.
echo Flushing DNS cache...
ipconfig /flushdns >nul

echo.
echo Testing connection...
ping ymorozov.ru -n 1 | findstr "127.0.0.1" >nul
if %errorLevel% == 0 (
    echo [OK] ymorozov.ru resolves to 127.0.0.1
) else (
    echo [WARNING] DNS resolution test failed
)

echo.
echo ====================================
echo SUCCESS! Setup complete.
echo You can now open http://ymorozov.ru
echo ====================================
echo.
pause
