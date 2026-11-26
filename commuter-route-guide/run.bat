@echo off
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\com\mycompany\routeph1\*.java || goto :err
java -cp "out;lib\*" com.mycompany.routeph1.WebServer config.properties
goto :eof
:err
echo Compile failed. Make sure JDK 11 or newer is installed.
pause
