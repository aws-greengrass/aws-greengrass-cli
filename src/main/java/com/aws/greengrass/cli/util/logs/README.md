# Evergreen Log Tool User Guide

## Overview

The goal of this log tool is to improve the customer experience of processing and analyzing Evergreen logs,
 by supporting customer’s queries from Evergreen CLI to parse/filter log files and displaying human-readable information.

## Requirements

Evergreen Log Tool is a part of AWS Greengrass CLI. To download and install the latest iteration, clone the repo 
from https://github.com/aws/aws-greengrass-cli, and run the installation commands from [here](https://github.com/aws/aws-greengrass-cli/blob/master/README.md#installation).

Evergreen Log Tool currently only supports parsing log information in JSON format. Hence, for log tool to function
 correctly, use ``-Dlog.fmt=JSON`` and ``-Dlog.store=FILE`` in launch command of Evergreen Kernel to make sure that kernel emits log files in JSON format.

## Getting Help

Type ``greengrass-cli logs help`` or ``greengrass-cli logs help <command>`` for helpful information.

## Detailed Usage
```
$ greengrass-cli logs get [-ld --log-dir <log-directory> ...] [-lf --log-file <file-path> ...]
                          [-t --time-window "beginTime","endTime" ...]
                          [-f --filter "regex","key"="val" ...]
                          [-fol --follow] [-v --verbose] [-n --no-color]
                          [-b --before] [-a --after] [-s --syslog]
$ greengrass-cli logs list-log-files [-ld --log-dir <log-directory> ...]
$ greengrass-cli logs list-keywords [-s --syslog]
```

### Setting source of log information

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
 command ``list-log-files`` to check the list of Greengrass log files in a given directory.
 
```
# display all files under ~/.evergreen
$ greengrass-cli logs list-log-files --log-dir ~/.evergreen
```

### Setting time window

The ``--time-window`` option helps you to set start times and end times for filtered log results.
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
$ greengrass-cli logs get --time-window 2020-07-01,2020-07-01T05:00:00 --log-file evergreen.log

# getting log entries between 12:00 and 16:00:05 of today
$ greengrass-cli logs get --time-window 12:00,16:00:05 --log-file evergreen.log

# getting log entries between 12:00 and now of today
$ greengrass-cli logs get --time-window 12:00, --log-file evergreen.log
```

```
# input by relative offsets, an offset period from current time
# the offset requires: 
#     1) a sign (+ or -) 
#     2) followed by a number 
#     3) followed by a time unit (d/day/days, h/hr/hours, m/min/minutes, s/sec/seconds).

# Example: 
# getting log entries between 1 hour ago and 2 hours 15 minutes ago
$ greengrass-cli logs get --time-window -2h15min,-1hr --log-file evergreen.log
```

### Adding filter

The ``--filter`` option is able to filtered log entries based on provided keyword, regular expression, or key-value pair.

```
# filter by keyword or regular expression (getting entries containting HelloWorld)
$ greengrass-cli logs get --filter HelloWorld --log-file evergreen.log

# filter by key-value pair (getting entries from main thread)
$ greengrass-cli logs get --filter thread=main --log-file evergreen.log
```

The log tool supports adding multiple filters, with AND-relation between filter options and OR-relation within filter 
option, separated by comma.
```
# mulitiple filter
# getting entries from main thread, that contains either "Deployment" or "HelloWorld".
$ greengrass-cli logs get --filter thread=main --filter Deployment,HelloWorld --log-file evergreen.log
```
When the user queries a log level, e.g. ``level=DEBUG``, all log entries whose level are above queried level will 
be displayed. We have ``ALL < DEBUG < INFO < WARN < ERROR < FATAL < OFF``.

The user can use key "error" to search with in all exceptions, e.g. ``error=HelloWorld``. Filter will return true for
any log entry that contains "HelloWorld" in its exception stack trace. When the user queries ``error=any``,
filtering any log entry with a valid exception will return true.

To help you filter with key-val pairs, you could also use the command ``list-keywords`` to see a list of frequent keywords
for filter.
```
# show all suggested keywords for Greengrass log
$ greengrass-cli logs list-keywords
```

### Following Realtime Update

The ``--follow`` option is a boolean option that decides if the log tool is actively following real time changes in 
queried log files/directories. In accordance with log rotation rule of Evergreen, ``--follow`` option is specified, 
the log tool will continuously follow and update files that doesn’t contain a timestamp in their file name. 
Most commonly, it will follow ``evergreen.log``.

```
# follow changes of ~/.evergreen
$ greengrass-cli logs get --log-dir ~/.evergreen/ --follow
```

To stop the log tool, the user can either terminates the program manually in command line(i.e. ``Ctrl+C``), or set up a time
 window to schedule a termination in future. More specifically, the program will stop when the current time is after all
  of the end time in the time windows user queried.

```
# stop follow after 5 minutes.
$ greengrass-cli logs get --time-window ,+5min --log-dir ~/.evergreen/ --follow
```
### Simplified Output
Currently two boolean options are supported to control output formats: ``--no-color`` and ``--verbose``.
By default, log tool outputs in a simplified format and adds highlight to all filtered keywords, regex,
 and key-value pairs. Default highlight is red and bold.

If input ``--no-color``, highlight will be removed.

If input ``--verbose``, the log tool will output verbose messages.

### Showing context around matched result
The log tool uses ``--before`` and ``--after`` to input the number of lines leading and trailing matched results, respectively.
If multiple log entries share some parts of context, for example when ``--before 5`` and two consecutive log entries are matched,
 the log tool will remove the duplicates and only print that context line once.

The default is 0 for both these options.

### Getting system log
The ``--syslog`` option is a boolean option that decides if the log tool is reading Greengrass log or syslog.
When specified, the log tool will try to read all input log files with respect to syslog format defined by RFC3164: 
``<$Priority>$Timestamp $Host $Logger ($Class): $Message``.

This option is only supported in Unix and Linux platforms, where syslog is enabled. If no log file is provided,
the log tool will try to read from ``/var/log/messages``, ``/var/log/syslog``, and ``/var/log/system.log``. 

``--log-dir`` and ``--verbose`` options are disabled for syslog.

To help you filter with key-val pairs, you could also use the command ``list-keywords`` with ``--syslog`` to see
a list of frequent keywords for filter.
```
# show all suggested keywords for syslog
$ greengrass-cli logs list-keywords --syslog
```

### Optimize memory usage
The log tool uses ``--max-log-queue-size`` options to input the maximum number of log entries allowed to allocate. 
Since reading logs from file is faster than writing to output, when the number of log entries read but not written
reaches the maximum number specified, the thread for reading will block and wait until all remaining read entries are 
written. 

The default number for ``--max`` is 100.