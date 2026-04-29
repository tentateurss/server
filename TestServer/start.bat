@echo off
REM Гарантируем что CWD = директория этого .bat (TestServer)
cd /d "%~dp0"

REM Очистка возможных stale-локов от прошлого запуска
del /q "logs\latest.log" 2>nul
del /q "world\session.lock" 2>nul
del /q "beach\session.lock" 2>nul
del /q "lobby\session.lock" 2>nul

java -Xms2G -Xmx4G -jar paper.jar --nogui
echo.
echo Server stopped. Press any key to close window.
pause >nul
