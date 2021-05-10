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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AggregationImpl implements Aggregation {
    // Log file name has pattern ComponentName_yyyy_MM_dd_HH_index.log or ComponentName.log
    // Greengrass currently rotate log files every hour
    private static final Pattern fileNamePatternByHour = Pattern.compile(
            "([a-zA-Z0-9-_.]+)(_([0-9]{4}_[0-9]{2}_[0-9]{2}_[0-9]{2})_([0-9]+))\\.log$");
    private static final Pattern fileNamePatternCurrent = Pattern.compile("([a-zA-Z0-9-_.]+)\\.log$");

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
     * @param logFileList a list of file paths
     * @param logDirList a list of file directories
     * @return a PriorityBlockingQueue containing log entries. Log entries from multiple files
     *  will read into this queue concurrently ordered by their timestamps.
     */
    @Override
    public LogQueue readLog(List<Path> logFileList, List<Path> logDirList) {
        if (LogsUtil.isSyslog()) {
            if (logDirList != null && !logDirList.isEmpty()) {
                LogsUtil.getErrorStream().println("Syslog does not support directory input!");
                logDirList = null;
            }
            if (logFileList == null || logFileList.isEmpty()) {
                // These three locations are most likely the default location of syslog in Linux or Unix
                logFileList = Arrays.asList(
                        Paths.get("/var/log/system.log"),
                        Paths.get("/var/log/syslog"),
                        Paths.get("/var/log/messages"));
            }
        }
        if ((logFileList == null || logFileList.isEmpty()) && (logDirList == null || logDirList.isEmpty())) {
            throw new RuntimeException("No valid log input. Please provide a log file or directory.");
        }

        Set<File> logFileSet = listLog(logDirList);
        if (logFileList != null) {
            for (Path filePath : logFileList) {
                File file = filePath.toFile();
                logFileSet.add(file);
            }
        }

        if (logFileSet.isEmpty() && (logDirList != null && !logDirList.isEmpty())) {
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
        // GG_NEEDS_REVIEW: TODO: track log rotation
        return config.getQueue();
    }

    /*
     * List available log files from given directories.
     *
     * @param logDirList a list of file directories
     * @return a list of Path to each found log files
     */
    @Override
    public Set<File> listLog(List<Path> logDirList) {
        Set<File> logFileSet = new HashSet<>();
        if (logDirList == null) {
            return logFileSet;
        }
        for (Path dir : logDirList) {
            File[] files = dir.toFile().listFiles();
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
        try {
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*
     * Help function that checks if a file is a log file.
     * GG_NEEDS_REVIEW: TODO: further investigate log file criteria
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
                logGroupMap.get("syslog").add(new LogFile(file, null, null));
            }
            return logGroupMap;
        }

        for (File file : logFileSet) {
            Matcher fileNameMatcher = fileNamePatternByHour.matcher(file.getName());
            boolean patternByHour = fileNameMatcher.matches();
            String logGroupName;
            String timestamp = "";
            String index = "";
            if (patternByHour) {
                // group(1) = logGroupName, group2 = timestamp + index, group(3) = timestamp, group(4) = index.
                logGroupName = fileNameMatcher.group(1);
                timestamp = fileNameMatcher.group(3);
                index = fileNameMatcher.group(4);
            } else {
                fileNameMatcher = fileNamePatternCurrent.matcher(file.getName());
                if (!fileNameMatcher.matches()) {
                    LogsUtil.getErrorStream().println("Unable to parse file name: " + file.getName());
                    continue;
                }
                logGroupName = fileNameMatcher.group(1);
            }

            try {
                LogFile logFile = new LogFile(file, timestamp, index);
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
