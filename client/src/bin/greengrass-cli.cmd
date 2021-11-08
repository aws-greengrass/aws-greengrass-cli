@echo off
@REM set local scope for variables so we don't leak into the caller's environment
SETLOCAL

set SCRIPT_PATH=%~dp0

@REM Trace symlink to real path if we were launched from a symlink
for /f "tokens=2 delims=[]" %%H in  ('dir /al %SCRIPT_PATH%* ^| findstr /i /c:"%~nx0"') do (
    set SCRIPT_PATH=%%~dpH
)

set CLI_HOME=%SCRIPT_PATH%..
If Not Defined GGC_ROOT_PATH (
    set GGC_ROOT_PATH=%CLI_HOME%\..\..\..\..\..\..
)
set CLI_JAR="%CLI_HOME%\lib\*"
set CLI_LAUNCHER="com.aws.greengrass.cli.CLI"

if defined JAVA_HOME (
    set JAVA_CMD="%JAVA_HOME%\bin\java"
) else (
    set JAVA_CMD="java"
)

%JAVA_CMD% -classpath %CLI_JAR% %CLI_LAUNCHER% %*
