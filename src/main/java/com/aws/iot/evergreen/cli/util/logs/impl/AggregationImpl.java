// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Aggregation;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class AggregationImpl implements Aggregation {
    /**
     * Read log files from input commands
     *
     * @param logFile arguments of --log-file
     * @param logDir  arguments of --log-dir
     * @return a list of BufferedReader, each reading one log file.
     */
    @Override
    public List<BufferedReader> ReadLog(String[] logFile, String[] logDir) {
        /*
         * TODO: implement Producer-Consumer model for ReadLog, which read lines into a shared BlockingQueue.
         */
        List<BufferedReader> logReaderList = new ArrayList<>();
        List<Path> logFilePathList = new ArrayList<>();

        // Reading files from --log-file into logFilePathList
        if (logFile != null) {
            for (String file : logFile) {
                logFilePathList.add(Paths.get(file));
            }
        }

        // Scanning and reading files from directory --log-dir into logFilePathList
        logFilePathList.addAll(ListLog(logDir));

        // Return BufferedReader
        if (logFilePathList.isEmpty()) {
            if (logDir == null) {
                throw new RuntimeException("No valid log input. Please provide a log file or directory.");
            }
            throw new RuntimeException("Log directory provided contains no valid log files.");
        }
        Charset charset = StandardCharsets.UTF_8;
        for (Path filePath : logFilePathList) {
            try {
                logReaderList.add(Files.newBufferedReader(filePath, charset));
            } catch (IOException e) {
                throw new RuntimeException("File path provided invalid: " + filePath.toString(), e);
            }
        }
        return logReaderList;
    }

    /**
     * List available log files from given directories
     *
     * @param logDir arguments of --log-dir or list-log-files
     * @return a list of Path to each found log files.
     */
    @Override
    public List<Path> ListLog(String[] logDir) {
        List<Path> logFilePathList = new ArrayList<>();
        if (logDir != null) {
            for (String dir : logDir) {
                try {
                    File[] files = new File(dir).listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.getName().contains("log"))
                                logFilePathList.add(Paths.get(file.getPath()));
                        }
                    }
                } catch (Exception e) {
                    /*
                     *  TODO: process other directories when one is invalid
                     *  https://github.com/aws/aws-greengrass-cli/pull/14/files#r456008055
                     */
                    throw new RuntimeException("Log dir provided invalid: " + dir, e);
                }
            }
        }
        return logFilePathList;
    }
}