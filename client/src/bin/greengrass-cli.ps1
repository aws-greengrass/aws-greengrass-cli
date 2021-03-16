$bin = Split-Path $MyInvocation.MyCommand.Path -Parent
$Env:CLI_HOME = Split-Path $bin -Parent
$Env:GGC_ROOT_PATH = $Env:CLI_HOME + "\..\..\..\..\..\.."
$CLI_JAR = $Env:CLI_HOME + "\lib\*"
$CLI_LAUNCHER = "com.aws.greengrass.cli.CLI"

java -classpath $CLI_JAR $CLI_LAUNCHER $args
