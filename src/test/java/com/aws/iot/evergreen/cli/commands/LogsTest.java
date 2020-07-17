/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.util.logs.impl.AggregationImpl;
import com.aws.iot.evergreen.cli.util.logs.impl.FilterImpl;
import com.aws.iot.evergreen.cli.util.logs.impl.VisualizationImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class LogsTest {
    private static final String[] timeWindow = new String[]{"2020-07-14T00:00:00,2020-07-14T01:00:00",
            "2020-07-14T02:00:00,2020-07-16T03:00:00"};
    private static final String[] filterExpression = new String[]{"level=DEBUG,level=INFO",
            "thread=idle-connection-reaper"};

    private static final String logEntry = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";

    private static final String invalidLogEntry = "{\"thread-idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";

    private Logs logs;
    @TempDir
    File logDir;
    private File logFile;
    private ByteArrayOutputStream outContent;

    @BeforeEach
    void init() {
        logFile = new File(logDir.getPath() + "/evergreen.log");
        logs = new Logs();
        logs.setAggregation(new AggregationImpl());
        logs.setFilter(new FilterImpl());
        logs.setVisualization(new VisualizationImpl());
        outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
    }


    @Test
    void testGetHappyCase() throws Exception {
        String[] logFilePath = {logFile.getAbsolutePath()};
        PrintStream fileWriter = new PrintStream(new FileOutputStream(logFile));
        fileWriter.print(logEntry);

        logs.get(logFilePath, null, timeWindow, filterExpression);
        System.out.println(outContent.toString());
        assertTrue(outContent.toString().contains("[DEBUG] (idle-connection-reaper) null: null. "
                + "Closing connections idle longer than 60000 MILLISECONDS\n"));
    }

    @Test
    void testGetInvalidLogEntry() throws Exception {
        String[] logFilePath = {logFile.getAbsolutePath()};
        PrintStream fileWriter = new PrintStream(new FileOutputStream(logFile));
        fileWriter.print(invalidLogEntry);

        Exception invalidLogEntryException = assertThrows(RuntimeException.class,
                () -> logs.get(logFilePath, null, timeWindow, filterExpression));
        assertTrue(invalidLogEntryException.getMessage().contains("Failed to serialize: " + invalidLogEntry));
    }

    @Test
    void testListLogFileHappyCase() throws Exception {
        String[] logDirPath = {logDir.getAbsolutePath()};
        PrintStream fileWriter = new PrintStream(new FileOutputStream(logFile));
        fileWriter.print(logEntry);

        logs.list_log(logDirPath);
        assertEquals(logFile.getAbsolutePath() + "\n" + "Total 1 files found.",
                outContent.toString());
    }

    @AfterEach
    void cleanup() {
        System.setOut(System.out);
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