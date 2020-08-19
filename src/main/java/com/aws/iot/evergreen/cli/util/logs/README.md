#Evergreen Log Tool User Guide

##Overview

The goal of this log tool is to improve the customer experience of processing and analyzing Evergreen logs,
 by supporting customer’s queries from Evergreen CLI to parse/filter log files and displays human-readable information.

##Requirements

Evergreen Log Tool is a part of AWS Greengrass CLI. To download and install the latest iteration, clone the repo 
from https://github.com/aws/aws-greengrass-cli/tree/log-tool, and run the following commands: 

```
$ mvn install
$ cd $YOUR_INSTALL_ROOT; unzip $PKG_ROOT/target/evergreen-cli-1.0-SNAPSHOT.zip; cd evergreen-cli-1.0-SNAPSHOT;
$ source install.sh
$ greengrass-cli help
```

Evergreen Log Tool currently only supports parsing log information in JSON format. Hence, for log tool to function
 correctly, use ``-Dlog.fmt=JSON`` in launch command of Evergreen Kernel to make sure that kernel emits logs in JSON format.

##Getting Help

Type ``greengrass-cli logs help`` or ``greengrass-cli logs help <command>`` for helpful information.

##Detailed Usage
```
$ greengrass-cli logs get --log-dir <log-directory> --log-file <file-path>
                          --time-window beginTime,endTime
                          --filter "regex","key"="val"
                          --follow
$ greengrass-cli logs list-log-files --log-dir <log-directory>
```

###Setting source of log information

Use at least one of ``--log-dir`` and ``--log-file`` options to specify the location of log information. The two options
 work in association. For example:
```
# collect all logs from ~/.evergreen
$ greengrass-cli logs get --log-dir ~/.evegreen

# collect log from file ~/.evergreen/evergreen.log
$ greengrass-cli logs get --log-file ~/.evergreen/evergreen.log

# multiple options work in association
# collect log from evergreen.log_1, evergreen.log_2, and ~/.evergreen
$ greengrass-cli logs get --log-file evergreen.log_1 --log-file evergreen.log_2 --log-dir ~/.evergreen
```

For reading directories, the log tool will check all files under the given directory and only files whose name contain
 “log” will be considered as a log file. To help you decide the source of log information, you could also use the 
 command list-log-files to check the list of log files in a given directory.
 
```
# display all files under ~/.evergreen
$ greengrass-cli logs list-log-files --log-dir ~/.evergreen
```

###Setting time window

The --time-window option helps you to set start times and end times for filtered log results.
 Timestamps entered are converted to local date time of the queried machine. 
 
```
# general structure of time window
# a log entry satisfies the option if it's within any of the time window input

# within each option, begin and end time are separated by a comma
$ greengrass-cli logs get --time-window beginTime1,endTime1 --time-window beginTime2,endTime2
```

The log tool supports both inputs by timestamp and relative offsets:
```
# input by timestamp
# supports selected JAVA 8 DateTimeFormatter predefined formats and some custom-defined formats:
#     ISO_LOCAL_DATE: yyyy-MM-DD
#     BASIC_ISO_DATE: yyyyMMDD
#     ISO_LOCAL_TIME: HH:mm:ss
#     HH:mm:ssSSS
#     ISO_INSTANT: YYYY-MM-DD'T'HH:mm:ss'Z'
#     ISO_LOCAL_DATE_TIME: YYYY-MM-DD'T'HH:mm:ss
#     yyyy-MM-dd'T'HH:mm:ssSSS

# when time is not specified, defaults time to 00:00:00;
# when date is not specified, defaults date to that of current time.
# when one of the begin time or endtime is not specified, defaults it to current time.

# Example:
# getting log entries between 00:00 and 05:00 of 2020-07-01
$ greengreass-cli logs get --time-window 2020-07-01,2020-07-01T05:00:00 --log-file evergreen.log

# getting log entries between 12:00 and 16:00:05 of today
$ greengreass-cli logs get --time-window 12:00,16:00:05 --log-file evergreen.log

# getting log entries between 12:00 and now of today
$ greengreass-cli logs get --time-window 12:00, --log-file evergreen.log
```

```
# input by relative offsets, an offset period from current time
# the offset requires: 
#     1) a sign (+ or -) 
#     2) followed by a number 
#     3) followed by a time unit (d/day/days, h/hr/hours, m/min/minutes, s/sec/seconds).

# Example: 
# getting log entries between 1 hour ago and 2 hours 15 minutes ago
$ greengreass-cli logs get --time-window -2h15min,-1hr --log-file evergreen.log
```

###Adding filter

The --filter option is able to filtered log entries based on provided keyword, regular expression, or key-value pair.

```
# filter by keyword or regular expression (getting entries containting HelloWorld)
$ greengreass-cli logs get --filter HelloWorld --log-file evergreen.log

# filter by key-value pair (getting entries from main thread)
$ greengreass-cli logs get --filter thread=main --log-file evergreen.log
```

The log tool supports adding multiple filters, with AND-relation between filter options and OR-relation within filter 
option, separated by comma.
```
# mulitiple filter
# getting entries from main thread, that contains either "Deployment" or "HelloWorld".
$ greengreass-cli logs get --filter thread=main --filter Deployment,HelloWorld --log-file evergreen.log
```
When the user queries a log level, e.g. ``level=DEBUG``, all log entries whose level are above queried level will 
be displayed. We have ``ALL < DEBUG < INFO < WARN < ERROR < FATAL < OFF``.

###Following Realtime Update

The ``--follow`` option is a boolean option that decides if the log tool is actively following real time changes in 
queried log files/directories. In accordance with log rotation rule of Evergreen, ``--follow`` option is specified, 
the log tool will continuously follow and update files that doesn’t contain a timestamp in their file name. 
Most commonly, it will follow ``evergreen.log``.

```
# follow changes of ~/.evergreen
$ greengreass-cli logs get --log-dir ~/.evergreen/ --follow
```

To stop the log tool, the user can either terminates the program manually in command line(i.e. ``Ctrl+C``), or set up a time
 window to schedule a termination in future. More specifically, the program will stop when the current time is after all
  of the end time in the time windows user queried.

```
# stop follow after 5 minutes.
$ greengreass-cli logs get --time-window ,+5min --log-dir ~/.evergreen/ --follow
```
