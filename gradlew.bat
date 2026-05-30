@echo off
rem Gradle wrapper script for Windows

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.

set WRAPPER_JAR=%DIRNAME%gradle\wrapper\gradle-wrapper.jar
set WRAPPER_PROPERTIES=%DIRNAME%gradle\wrapper\gradle-wrapper.properties

if not exist "%WRAPPER_JAR%" (
    echo ERROR: Wrapper JAR file not found: %WRAPPER_JAR%
    exit /b 1
)

if not exist "%WRAPPER_PROPERTIES%" (
    echo ERROR: Wrapper properties file not found: %WRAPPER_PROPERTIES%
    exit /b 1
)

java -jar "%WRAPPER_JAR%" %*