/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Aggregation;
import com.aws.iot.evergreen.cli.util.logs.FileReader;
import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.LogEntry;
import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
import lombok.Getter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AggregationImpl implements Aggregation {
    // We define the max number here as a space holder, and will expand it in the next iteration.
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    @Getter
    private List<Future<?>> readLogFutureList;

    private AggregationImplConfig config;

    @Override
    public void configure(boolean follow, Filter filter, int max) {
        config = new AggregationImplConfig(follow, filter, max);
    }

    /*
     * Read log files from input commands.
     *
     * @param logFile an array of file paths
     * @param logDir an array of file directories
     * @return a PriorityBlockingQueue containing log entries. Log entries from multiple files
     *  will read into this queue concurrently ordered by their timestamps.
     */
    @Override
    public BlockingQueue<LogEntry> readLog(String[] logFile, String[] logDir) {
        if (logFile == null && logDir == null) {
            throw new RuntimeException("No valid log input. Please provide a log file or directory.");
        }

        Set<File> logFileSet = listLog(logDir);
        if (logFile != null) {
            for (String filePath : logFile) {
                File file = new File(filePath);
                logFileSet.add(file);
            }
        }

        if (logFileSet.isEmpty() && logDir != null) {
            throw new RuntimeException("Log directory provided contains no valid log files.");
        }

        readLogFutureList = new ArrayList<>();
        // We initialize the queue and log entry pool here to save overhead for when no log file is provided.
        config.setUpFileReader(logFileSet.size());

        for (File file : logFileSet) {
            readLogFutureList.add(executorService.submit(new FileReader(file, config)));
        }
        //TODO: track log rotation
        return config.getQueue();
    }

    /*
     * List available log files from given directories.
     *
     * @param logDir an array of file directories
     * @return a list of Path to each found log files
     */
    @Override
    public Set<File> listLog(String[] logDir) {
        Set<File> logFileSet = new HashSet<>();
        if (logDir == null) {
            return logFileSet;
        }
        for (String dir : logDir) {
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
}