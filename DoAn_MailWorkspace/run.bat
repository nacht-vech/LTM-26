@echo off
chcp 65001 >nul
cd /d "%~dp0src"
if not exist "..\data" mkdir "..\data"
if not exist "..\attachments" mkdir "..\attachments"
echo Dang bien dich Do An Mail Workspace...
javac -encoding UTF-8 model\*.java storage\*.java protocol\*.java *.java
if %errorlevel% neq 0 (
    echo Loi bien dich!
    pause
    exit /b %errorlevel%
)
echo Dang khoi chay Mail Workspace...
java MainApp