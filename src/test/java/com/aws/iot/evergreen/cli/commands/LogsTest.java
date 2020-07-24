/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
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
import java.io.IOException;
import java.io.PrintStream;

import static com.aws.iot.evergreen.cli.TestUtil.deleteDir;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;



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

    /*
     * TODO: use mocks for LogsTest.
     */
    private Logs logs;
    @TempDir
    File logDir;
    private File logFile;

    private ByteArrayOutputStream byteArrayOutputStream;
    private ByteArrayOutputStream errOutputStream;
    private PrintStream printStream;
    private PrintStream errorStream;

    @BeforeEach
    void init() {
        logFile = new File(logDir.getPath() + "/evergreen.log");
        logs = new Logs();
        logs.setAggregation(new AggregationImpl());
        logs.setFilter(new FilterImpl());
        logs.setVisualization(new VisualizationImpl());
        byteArrayOutputStream = new ByteArrayOutputStream();
        errOutputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(byteArrayOutputStream);
        errorStream = new PrintStream(errOutputStream);
        LogsUtil.setPrintStream(printStream);
        LogsUtil.setErrorStream(errorStream);
    }

    @Test
    void testGetHappyCase() throws IOException {
        PrintStream fileWriter = new PrintStream(new FileOutputStream(logFile));
        fileWriter.print(logEntry);
        fileWriter.close();

        String[] logFilePath = {logFile.getAbsolutePath()};
        logs.get(logFilePath, null, timeWindow, filterExpression);
        assertThat(byteArrayOutputStream.toString(), containsString("[DEBUG] (idle-connection-reaper) "
                + "null: null. Closing connections idle longer than 60000 MILLISECONDS"));
    }

    @Test
    void testGetInvalidLogEntry() throws IOException {
        PrintStream fileWriter = new PrintStream(new FileOutputStream(logFile));
        fileWriter.print(invalidLogEntry);
        fileWriter.close();

        String[] logFilePath = {logFile.getAbsolutePath()};
        logs.get(logFilePath, null, timeWindow, filterExpression);
        assertThat(errOutputStream.toString(), containsString("Failed to serialize: " + invalidLogEntry));
    }

    @Test
    void testGetEmptyLine() throws IOException {
        PrintStream fileWriter = new PrintStream(new FileOutputStream(logFile));
        fileWriter.print(logEntry);
        fileWriter.println("\n");
        fileWriter.close();

        String[] logFilePath = {logFile.getAbsolutePath()};
        logs.get(logFilePath, null, timeWindow, filterExpression);
        assertThat(byteArrayOutputStream.toString(), containsString("[DEBUG] (idle-connection-reaper) "
                + "null: null. Closing connections idle longer than 60000 MILLISECONDS"));
        assertThat(errOutputStream.toString(), containsString("Empty line"));
    }

    @Test
    void testListLogFileHappyCase() throws Exception {
        String[] logDirPath = {logDir.getAbsolutePath()};
        PrintStream fileWriter = new PrintStream(new FileOutputStream(logFile));
        fileWriter.print(logEntry);
        fileWriter.close();

        logs.list_log(logDirPath);
        assertEquals(logFile.getAbsolutePath() + "\n" + "Total 1 files found.",
                byteArrayOutputStream.toString());
    }

    @AfterEach
    void cleanup() {
        deleteDir(logDir);
        printStream.close();
        errorStream.close();
    }
}