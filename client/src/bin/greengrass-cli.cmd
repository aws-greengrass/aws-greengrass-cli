@echo off

set SCRIPT_PATH=%~dp0
set CLI_HOME=%SCRIPT_PATH%..
set GGC_ROOT_PATH=%CLI_HOME%\..\..\..\..\..\..
set CLI_JAR=%CLI_HOME%\lib\*
set CLI_LAUNCHER="com.aws.greengrass.cli.CLI"

java -classpath %CLI_JAR% %CLI_LAUNCHER% %*
