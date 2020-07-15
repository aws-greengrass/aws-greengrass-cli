/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

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

public class AggregationImpl implements Aggregation {
    /**
     * Read log files from input commands
     *
     * @param logFile arguments of --log-file
     * @param logDir  arguments of --log-dir
     * @return a list of BufferedReader, each reading one log file.
     */
    @Override
    public ArrayList<BufferedReader> ReadLog(String[] logFile, String[] logDir) {

        ArrayList<BufferedReader> logReader = new ArrayList<>();
        ArrayList<Path> logFilePath = new ArrayList<>();

        // Reading files from --log-file into logFilePath
        if (logFile != null) {
            for (String file : logFile) {
                Path p = Paths.get(file);
                logFilePath.add(p);
            }
        }

        // Scanning and reading files from directory --log-dir into logFilePath
        logFilePath.addAll(ListLog(logDir));

        // Return BufferedReader
        if (logFilePath.isEmpty()) {
            if (logDir == null)
                throw new RuntimeException("No valid log input. Please provide a log file or directory.");
            else
                throw new RuntimeException("Log directory provided contains no valid log files.");
        }
        Charset charset = StandardCharsets.US_ASCII;
        for (Path filePath : logFilePath) {
            try {
                BufferedReader reader = Files.newBufferedReader(filePath, charset);
                logReader.add(reader);
            } catch (IOException e) {
                throw new RuntimeException("File path provided invalid: " + filePath.toString(), e);
            }
        }
        return logReader;
    }

    /**
     * List available log files from given directories
     *
     * @param logDir arguments of --log-dir or list-log-files
     * @return a list of Path to each found log files.
     */
    @Override
    public ArrayList<Path> ListLog(String[] logDir) {
        ArrayList<Path> logFilePath = new ArrayList<>();
        if (logDir != null) {
            for (String dir : logDir) {
                try {
                    File[] files = new File(dir).listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.getName().contains("log"))
                                logFilePath.add(Paths.get(file.getPath()));
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Log dir provided invalid: " + dir, e);
                }
            }
        }
        return logFilePath;
    }
}
