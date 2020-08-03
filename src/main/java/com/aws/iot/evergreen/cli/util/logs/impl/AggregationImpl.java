/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Aggregation;
import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
import lombok.Getter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;

public class AggregationImpl implements Aggregation {
    //TODO: Add limit on maximum number of threads and capacity of queue.
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private BlockingQueue<LogsUtil.LogEntry> queue;
    @Getter
    private List<Future<?>> readLogFutureList;

    /*
     * Read log files from input commands.
     *
     * @param logFile an array of file paths
     * @param logDir an array of file directories
     * @return a PriorityBlockingQueue containing log entries.
     */
    @Override
    public BlockingQueue<LogsUtil.LogEntry> readLog(String[] logFile, String[] logDir) {
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
        queue = new PriorityBlockingQueue<>();
        for (File file : logFileSet) {
            readLogFutureList.add(executorService.submit(new FileReader(file)));
        }
        return queue;
    }

    /*
     * Runnable class responsible of reading a single log file
     */
    public class FileReader implements Runnable {
        private final File fileToRead;

        public FileReader(File file) {
            fileToRead = file;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new java.io.FileReader(fileToRead));
                String line;
                //TODO: Follow live updates of log file
                while ((line = reader.readLine()) != null) {
                    try {
                        queue.put(new LogsUtil.LogEntry(line, LogsUtil.getMapper().readValue(line, Map.class)));
                    } catch (InterruptedException e) {
                        LogsUtil.getErrorStream().println(e.getMessage());
                    } catch (IOException e) {
                        LogsUtil.getErrorStream().println("Failed to serialize: " + line);
                        LogsUtil.getErrorStream().println(e.getMessage());
                    }
                }
                reader.close();
            } catch (FileNotFoundException e) {
                LogsUtil.getErrorStream().println("Cannot open file: " + fileToRead);
            } catch (IOException e) {
                LogsUtil.getErrorStream().println(fileToRead + "readLine() failed.");
                LogsUtil.getErrorStream().println(e.getMessage());
            }
        }
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
        executorService.shutdown();
        return false;
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