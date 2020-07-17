/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Aggregation;
import lombok.Setter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AggregationImpl implements Aggregation {
    @Setter
    private PrintStream errorStream = new PrintStream(System.err);
    /*
     * Read log files from input commands.
     *
     * @param logFile an array of file paths
     * @param logDir an array of file directories
     * @return a list of BufferedReader, each reading one log file
     */
    @Override
    public List<BufferedReader> readLog(String[] logFile, String[] logDir) {
        /*
         * TODO: implement Producer-Consumer model for ReadLog, which read lines into a shared BlockingQueue.
         */
        List<BufferedReader> logReaderList = new ArrayList<>();

        // Scanning and reading files from logDir into logFilePathList
        Set<File> logFileSet = new HashSet<>(listLog(logDir));

        // Reading files from logFile into logFilePathList
        if (logFile != null) {
            for (String filePath : logFile) {
                File file = new File(filePath);
                logFileSet.add(file);
            }
        }
        // Construct BufferedReaders
        for (File file : logFileSet) {
            try {
                logReaderList.add(new BufferedReader(new FileReader(file)));
            } catch (FileNotFoundException e) {
                errorStream.println(e.getMessage());
            }
        }

        // Return BufferedReader
        if (logReaderList.isEmpty()) {
            if (logDir == null) {
                throw new RuntimeException("No valid log input. Please provide a log file or directory.");
            }
            throw new RuntimeException("Log directory provided contains no valid log files.");
        }
        return logReaderList;
    }

    /*
     * List available log files from given directories.
     *
     * @param logDir arguments of --log-dir or list-log-files
     * @return a list of Path to each found log files
     */
    @Override
    public List<File> listLog(String[] logDir) {
        List<File> logFileList = new ArrayList<>();
        if (logDir == null) {
            return logFileList;
        }
        for (String dir : logDir) {
            try {
                File[] files = new File(dir).listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (isLogFile(file)) {
                            logFileList.add(file);
                        }
                    }
                }
            } catch (Exception e) {
                /*
                 * TODO: process other directories when one is invalid
                 * https://github.com/aws/aws-greengrass-cli/pull/14/files#r456008055
                 */
                throw new RuntimeException("Log dir provided invalid: " + dir, e);
            }
        }
        return logFileList;
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