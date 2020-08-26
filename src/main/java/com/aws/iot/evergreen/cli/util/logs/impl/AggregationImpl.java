/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Aggregation;
import com.aws.iot.evergreen.cli.util.logs.FileReader;
import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.LogEntry;
import com.aws.iot.evergreen.cli.util.logs.LogFile;
import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AggregationImpl implements Aggregation {
    private static final Pattern fileNamePattern = Pattern.compile("(\\w+)\\.log(_([0-9]+-[0-9]+-[0-9]+_[0-9]+)_([0-9]+))?$");

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    @Getter
    private List<Future<?>> readLogFutureList;

    private AggregationImplConfig config;

    @Override
    public void configure(boolean follow, Filter filter, int max, int before) {
        config = new AggregationImplConfig(follow, filter, max, before);
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
    public BlockingQueue<LogEntry> readLog(String[] logFileArray, String[] logDirArray) {
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
        config.setUpFileReader();


        for (Map.Entry<String, List<LogFile>> entry : logGroupMap.entrySet()) {
            Collections.sort(entry.getValue());
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
        Map<String, List<LogFile>> logGroupMap = new HashMap<>();

        for (File file : logFileSet) {
            Matcher fileNameMatcher = fileNamePattern.matcher(file.getName());
            if (!fileNameMatcher.matches()) {
                LogsUtil.getErrorStream().println("Unable to parse file name: " + file.getName());
                continue;
            }

            String logGroupName = fileNameMatcher.group(1);
            try {
                LogFile logFile = new LogFile(file, fileNameMatcher.group(3), fileNameMatcher.group(4));
                logGroupMap.putIfAbsent(logGroupName, new ArrayList<>());
                logGroupMap.get(logGroupName).add(logFile);
            } catch (DateTimeParseException e) {
                LogsUtil.getErrorStream().println("Unable to parse timestamp from file name: " + file.getName());
            } catch (NumberFormatException e) {
                LogsUtil.getErrorStream().println("Unable to parse file index from file name: " + file.getName());
            }

        }
        return logGroupMap;
    }
}