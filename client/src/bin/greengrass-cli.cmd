@echo off

set SCRIPT_PATH=%~dp0
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
