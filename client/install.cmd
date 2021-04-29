@REM -------------------------------
@REM Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
@REM SPDX-License-Identifier: Apache-2.0
@REM -------------------------------
@echo off
SETLOCAL
@REM Check if greengrass-cli is already a recognized command, if so, exit, we don't need to install
FOR /F "delims=" %%A IN ('WHERE greengrass-cli 2^>NUL') DO SET CLI_PATH=%%A
IF NOT "%CLI_PATH%"=="" (
    ECHO Greengrass-cli is already installed
    EXIT /B 0   
)

@REM Return is always going to be as follows
@REM Path REG_EXPAND_SZ <Path/data/>
@REM But the path data might have spaces in the directory names so 2* will tokenize everything after the first 2 tokens into the last one
FOR /F "tokens=2*" %%a IN ('REG QUERY "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /V Path') DO SET SYS_PATH=%%~b

@REM Get registry type (should be REG_EXPAND_SZ)
FOR /F "tokens=1,2" %%a IN ('REG QUERY "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /V Path') DO SET REG_TYPE=%%b

SET DIR=%~dp0
SET GG_BIN=%DIR%bin

SET SYS_PATH=%GG_BIN%;%SYS_PATH%

@REM Add /greengrass/v2/bin to System PATH via registry
REG ADD "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /V Path /T %REG_TYPE% /D "%SYS_PATH%" /F

@REM Add /greengrass/v2/bin to PATH currently to avoid having to restart for the registry update to take effect
ENDLOCAL & SET "PATH=%GG_BIN%;%PATH%"
ECHO Start using greengrass-cli