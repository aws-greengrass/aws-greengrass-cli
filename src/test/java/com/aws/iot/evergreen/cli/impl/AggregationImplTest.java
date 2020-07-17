/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.impl;

import com.aws.iot.evergreen.cli.util.logs.impl.AggregationImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AggregationImplTest {
    private static final String logEntry = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";
    @TempDir
    File logDir;
    private File logFile;
    private AggregationImpl aggregation;

    @BeforeEach
    void init() {
        aggregation = new AggregationImpl();
    }

    @Test
    void testReadLogHappyCase() throws Exception {
        logFile = new File(logDir.getPath() + "/evergreen.log");
        PrintStream writer = new PrintStream(new FileOutputStream(logFile));
        writer.print(logEntry);

        String[] logFilePath = {logFile.getAbsolutePath()};
        List<BufferedReader> readerArrayList = aggregation.readLog(logFilePath, null);
        assertEquals(1, readerArrayList.size());

        String line = readerArrayList.get(0).readLine();
        assertEquals(line, logEntry);

        String[] logDirPath = {logDir.getAbsolutePath()};
        readerArrayList = aggregation.readLog(null, logDirPath);
        assertEquals(1, readerArrayList.size());

        line = readerArrayList.get(0).readLine();
        assertEquals(line, logEntry);
    }

    @Test
    void testReadLogInvalidPath() {
        String[] logFilePath = {"bad path"};
        Exception invalidLogFileException = assertThrows(RuntimeException.class,
                () -> aggregation.readLog(logFilePath, null));
        assertTrue(invalidLogFileException.getMessage().contains("File path provided invalid: "
                + logFilePath[0]));
    }

    @Test
    void testReadLogEmptyDir() {
        String[] logDirPath = {logDir.getPath()};

        Exception emptyLogDirException = assertThrows(RuntimeException.class,
                () -> aggregation.readLog(null, logDirPath));
        assertEquals("Log directory provided contains no valid log files.", emptyLogDirException.getMessage());
    }

    @Test
    void testReadLogEmptyArg() {
        Exception emptyArgException = assertThrows(RuntimeException.class,
                () -> aggregation.readLog(null, null));
        assertEquals("No valid log input. Please provide a log file or directory.", emptyArgException.getMessage());
    }

    @Test
    void testListLogHappyCase() throws Exception {
        logFile = new File(logDir.getPath() + "/evergreen.log");
        PrintStream writer = new PrintStream(new FileOutputStream(logFile));
        writer.print(logEntry);

        String[] logDirPath = {logDir.getPath()};
        List<Path> logFilePath = aggregation.listLog(logDirPath);

        assertEquals(1, logFilePath.size());
        assertEquals(logFile.getPath(), logFilePath.get(0).toString());
    }


    @Test
    void testListLogEmptyDir() {
        String[] logDirPath = {logDir.getPath()};
        List<Path> logFilePath = aggregation.listLog(logDirPath);

        assertEquals(0, logFilePath.size());
    }

    @AfterEach
    void cleanup() {
        deleteDir(logDir);
    }

    private void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }
}