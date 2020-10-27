/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.util.logs.impl;

import com.aws.greengrass.cli.util.logs.Aggregation;
import com.aws.greengrass.cli.util.logs.FileReader;
import com.aws.greengrass.cli.util.logs.Filter;
import com.aws.greengrass.cli.util.logs.LogFile;
import com.aws.greengrass.cli.util.logs.LogQueue;
import com.aws.greengrass.cli.util.logs.LogsUtil;
import lombok.Getter;

import java.io.File;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AggregationImpl implements Aggregation {
    // Log file name is assumed to be one of patterns {}.log_yyyy-MM-dd_HH_index or {}.log_yyyy-MM-dd_HH-mm_index.
    // Greengrass currently rotate log files every hour, but it likely will change to rotate by 15 minutes in future iteration.
    // We support both patterns now and we can remove one of the patterns in future.
    // TODO: remove unused file name pattern
    private static final Pattern fileNamePatternByHour = Pattern.compile("(\\w+)\\.log(_([0-9]+-[0-9]+-[0-9]+_[0-9]+)_([0-9]+))?$");
    private static final Pattern fileNamePatternByMin = Pattern.compile("(\\w+)\\.log(_([0-9]+-[0-9]+-[0-9]+_[0-9]+-[0-9]+)_([0-9]+))?$");

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    @Getter
    private List<Future<?>> readLogFutureList;

    private AggregationImplConfig config;

    @Override
    public void configure(boolean follow, Filter filter, int before, int after, int max) {
        config = new AggregationImplConfig(follow, filter, before, after, max);
    }

    /*
     * Read log files from input commands.
     *
     * @param logFileArray an array of file paths
     * @param logDirArray an array of file directories
     * @return a PriorityBlockingQueue containing log entries. Log entries from multiple files
     *  will read into this queue concurrently ordered by their timestamps.
     */
    @Override
    public LogQueue readLog(String[] logFileArray, String[] logDirArray) {
        if (LogsUtil.isSyslog()) {
            if (logDirArray != null) {
                LogsUtil.getErrorStream().println("Syslog does not support directory input!");
                logDirArray = null;
            }
            if (logFileArray == null) {
                // These three locations are most likely the default location of syslog in Linux or Unix
                logFileArray = new String[]{"/var/log/system.log", "/var/log/syslog", "/var/log/messages"};
            }
        }
        if (logFileArray == null && logDirArray == null) {
            throw new RuntimeException("No valid log input. Please provide a log file or directory.");
        }

        Set<File> logFileSet = listLog(logDirArray);
        if (logFileArray != null) {
            for (String filePath : logFileArray) {
                File file = new File(filePath);
                logFileSet.add(file);
            }
        }

        if (logFileSet.isEmpty() && logDirArray != null) {
            throw new RuntimeException("Log directory provided contains no valid log files.");
        }

        Map<String, List<LogFile>> logGroupMap = parseLogGroup(logFileSet);

        readLogFutureList = new ArrayList<>();
        // We initialize the queue here to save overhead for when no log file is provided.
        config.initialize();


        for (Map.Entry<String, List<LogFile>> entry : logGroupMap.entrySet()) {
            // Here we sort all files in a log group by ascending order of their timestamps and indexes.
            if (!LogsUtil.isSyslog()) {
                Collections.sort(entry.getValue());
            }
            readLogFutureList.add(executorService.submit(new FileReader(entry.getValue(), config)));
        }
        //TODO: track log rotation
        return config.getQueue();
    }

    /*
     * List available log files from given directories.
     *
     * @param logDirArray an array of file directories
     * @return a list of Path to each found log files
     */
    @Override
    public Set<File> listLog(String[] logDirArray) {
        Set<File> logFileSet = new HashSet<>();
        if (logDirArray == null) {
            return logFileSet;
        }
        for (String dir : logDirArray) {
            File[] files = new File(dir).listFiles();
            if (files == null) {
                LogsUtil.getErrorStream().println("Log dir provided invalid: " + dir);
                continue;
            }
            for (File file : files) {
                if (isLogFile(file)) {
                    logFileSet.add(file);
                }
            }
        }
        return logFileSet;
    }

    /*
     * Check if readLog is active.
     */
    @Override
    public Boolean isAlive() {
        for (Future<?> future: readLogFutureList) {
            if (!future.isDone()) {
                return true;
            }
        }
        return false;
    }

    /*
     * Close the resources.
     */
    @Override
    public void close() {
        executorService.shutdownNow();
    }

    /*
     * Help function that checks if a file is a log file.
     * TODO: further investigate log file criteria
     * https://github.com/aws/aws-greengrass-cli/pull/14#discussion_r456007545
     */
    private boolean isLogFile(File file) {
        return file.getName().contains("log");
    }

    private Map<String, List<LogFile>> parseLogGroup(Set<File> logFileSet) {
        // key is logGroupName and value is all files within that log group.
        Map<String, List<LogFile>> logGroupMap = new HashMap<>();
        // if we are parsing syslog, we default all syslogs into one single log group without ordering.
        if (LogsUtil.isSyslog()) {
            logGroupMap.put("syslog", new ArrayList<>());
            for (File file : logFileSet) {
                logGroupMap.get("syslog").add(new LogFile(file, null, null, false));
            }
            return logGroupMap;
        }

        for (File file : logFileSet) {
            boolean patternByHour = fileNamePatternByHour.matcher(file.getName()).matches();
            Matcher fileNameMatcher = patternByHour ? fileNamePatternByHour.matcher(file.getName())
                    : fileNamePatternByMin.matcher(file.getName());
            if (!fileNameMatcher.matches()) {
                LogsUtil.getErrorStream().println("Unable to parse file name: " + file.getName());
                continue;
            }

            //group(1) = logGroupName, group2 = timestamp + index, group(3) = timestamp, group(4) = index.
            String logGroupName = fileNameMatcher.group(1);
            try {
                LogFile logFile = new LogFile(file, fileNameMatcher.group(3), fileNameMatcher.group(4), patternByHour);
                logGroupMap.putIfAbsent(logGroupName, new ArrayList<>());
                logGroupMap.get(logGroupName).add(logFile);
            } catch (DateTimeParseException e) {
                LogsUtil.getErrorStream().println("Unable to parse timestamp from file name: " + file.getName());
                continue;
            } catch (NumberFormatException e) {
                LogsUtil.getErrorStream().println("Unable to parse file index from file name: " + file.getName());
            }
        }
        return logGroupMap;
    }
}
