@echo off
setlocal enabledelayedexpansion

cd /d %~dp0

if not exist ".env" (
    pause
    exit /b 1
)

REM Load configuration from .env
for /f "usebackq tokens=1,2 delims==" %%a in (".env") do (
    if not "%%a"=="" if not "%%a:~0,1%"=="#" (
        set "%%a=%%b"
    )
) 2>nul

if not exist "bin" mkdir bin

javac -encoding UTF-8 -cp "lib/json-20210307.jar" -d bin src\client\*.java src\common\*.java src\manager\*.java src\master\*.java src\worker\*.java

if %errorlevel% neq 0 (
    pause
    exit /b 1
)

start "Reducer" cmd /k "java -cp bin;%JSON_LIB_PATH% master.Reducer"
timeout /t 2 /nobreak >nul

start "Master" cmd /k "java -cp bin;%JSON_LIB_PATH% master.Master"
timeout /t 2 /nobreak >nul

start "Worker-1" cmd /k "java -cp bin;%JSON_LIB_PATH% worker.Worker %WORKER_IP% %WORKER_PORT_1%"
start "Worker-2" cmd /k "java -cp bin;%JSON_LIB_PATH% worker.Worker %WORKER_IP% %WORKER_PORT_2%"
start "Worker-3" cmd /k "java -cp bin;%JSON_LIB_PATH% worker.Worker %WORKER_IP% %WORKER_PORT_3%"

timeout /t 5 /nobreak >nul

start "Manager" cmd /k "java -cp bin;%JSON_LIB_PATH% manager.ManagerApp %WORKER_IP%"
start "Client" cmd /k "java -cp bin;%JSON_LIB_PATH% client.ClientApp %WORKER_IP%"
