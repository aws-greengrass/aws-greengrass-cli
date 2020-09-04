/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.TestUtil;
import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.LogEntry;
import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import static com.aws.iot.evergreen.cli.TestUtil.deleteDir;
import static java.lang.Thread.sleep;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AggregationImplTest {
    private static final String logEntry = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";

    private static final String logEntry2 = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028089,\"cause\":null}";

    private static final String logEntry3 = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":0,\"cause\":null}";

    private static final String logEntry4 = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028087,\"cause\":null}";

    private static final String invalidLogEntry = "{\"thread-idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";

    @TempDir
    Path logDir;
    private File logFile;
    private AggregationImpl aggregation;
    private ByteArrayOutputStream errOutputStream;
    private PrintStream errorStream;
    private PrintStream writer;
    private BlockingQueue<LogEntry> logQueue;
    private Filter filterInterface = new FilterImpl();

    @BeforeEach
    void init() throws FileNotFoundException {
        aggregation = new AggregationImpl();
        aggregation.configure(false, filterInterface, 0, 0);
        errOutputStream = new ByteArrayOutputStream();
        errorStream = TestUtil.createPrintStreamFromOutputStream(errOutputStream);
        LogsUtil.setErrorStream(errorStream);
        logFile = new File (logDir.resolve("evergreen.log").toString());
        writer = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile));
    }

    @Test
    void testReadLogFileHappyCase() throws InterruptedException {
        writer.println(logEntry);

        String[] logFilePath = {logFile.getAbsolutePath()};
        logQueue = aggregation.readLog(logFilePath, null);
        assertEquals(1, aggregation.getReadLogFutureList().size());

        assertEquals(logEntry, logQueue.take().getLine());
    }



    @Test
    void testReadLogDirHappyCase() throws InterruptedException {
        writer.println(logEntry);

        String[] logDirPath = {logDir.toString()};
        logQueue = aggregation.readLog(null, logDirPath);
        assertEquals(1, aggregation.getReadLogFutureList().size());

        assertEquals(logEntry, logQueue.take().getLine());
    }
    @Test
    void testReadLogInvalidLine() throws InterruptedException {
        writer.println(invalidLogEntry);

        String[] logFilePath = {logFile.getAbsolutePath()};

        logQueue = aggregation.readLog(logFilePath, null);
        while (aggregation.isAlive()) {
            sleep(1);
        }
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Failed to serialize: " + invalidLogEntry));
    }

    @Test
    void testReadLogEmptyLine() throws InterruptedException {
        writer.print("\n");

        String[] logFilePath = {logFile.getAbsolutePath()};

        logQueue = aggregation.readLog(logFilePath, null);
        while (aggregation.isAlive()) {
            sleep(1);
        }
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Failed to serialize: "));
    }

    @Test
    void testReadLogDuplicateFile() throws InterruptedException {
        writer.println(logEntry);

        String[] logFilePath = {logFile.getAbsolutePath(), logFile.getAbsolutePath()};

        logQueue = aggregation.readLog(logFilePath, null);
        assertEquals(1, aggregation.getReadLogFutureList().size());
        assertEquals(logEntry, logQueue.take().getLine());
    }

    @Test
    void testReadLogMultipleFile() throws IOException, InterruptedException {
        writer.println(logEntry);

        File logFile2 = logDir.resolve("evergreen2.log").toFile();
        writer = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile2));
        writer.println(logEntry2);

        File logFile3 = logDir.resolve("evergreen.log_2000-01-01_03_1").toFile();
        writer = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile3));
        writer.println(logEntry3);

        File logFile4 = logDir.resolve("evergreen.log_2000-01-01_03_2").toFile();
        writer = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile4));
        writer.println(logEntry4);

        String[] logFilePath = {logFile.getPath(), logFile2.getPath(), logFile3.getPath(), logFile4.getPath()};

        logQueue = aggregation.readLog(logFilePath, null);
        assertEquals(2, aggregation.getReadLogFutureList().size());
        while (aggregation.isAlive()) {
            sleep(1);
        }

        assertEquals(4, logQueue.size());
        assertEquals(logEntry3, logQueue.take().getLine());
        assertEquals(logEntry4, logQueue.take().getLine());
        assertEquals(logEntry, logQueue.take().getLine());
        assertEquals(logEntry2, logQueue.take().getLine());
        assertTrue(logQueue.isEmpty());
    }

    @Test
    void testReadLogInvalidPath() throws InterruptedException {
        String[] logFilePath = {"bad path"};
        aggregation.readLog(logFilePath, null);
        while (aggregation.isAlive()) {
            sleep(1);
        }
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Unable to parse file name: bad path"));
        logFilePath = new String[]{"/xxx/evergreen.log"};
        aggregation.readLog(logFilePath, null);
        while (aggregation.isAlive()) {
            sleep(1);
        }
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Can not find file: /xxx/evergreen.log"));
    }

    @Test
    void testReadLogInvalidFileRotationPattern() throws InterruptedException {
        File logFile2 = logDir.resolve("evergreen.log_2020-02-00_03_01").toFile();
        String[] logFilePath = {logFile2.getPath()};
        aggregation.readLog(logFilePath, null);
        while (aggregation.isAlive()) {
            sleep(1);
        }
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Unable to parse timestamp from file name: evergreen.log_2020-02-00_03_01"));

        logFile2 = logDir.resolve("/evergreen.log_2020-02-01_03_11111111111111").toFile();
        String[] logFilePath2 = {logFile2.getPath()};
        aggregation.readLog(logFilePath2, null);
        while (aggregation.isAlive()) {
            sleep(1);
        }
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Unable to parse file index from file name: evergreen.log_2020-02-01_03_11111111111111"));

    }


    @Test
    void testReadLogEmptyDir() {
        File Dir = logDir.resolve("x").toFile();
        String[] logDirPath = {Dir.getPath()};
        Exception emptyLogDirException = assertThrows(RuntimeException.class,
                () -> aggregation.readLog(null, logDirPath));
        assertEquals("Log directory provided contains no valid log files.",
                emptyLogDirException.getMessage());
    }

    @Test
    void testReadLogEmptyArg() {
        Exception emptyArgException = assertThrows(RuntimeException.class,
                () -> aggregation.readLog(null, null));
        assertEquals("No valid log input. Please provide a log file or directory.", emptyArgException.getMessage());
    }

    @Test
    void testReadSyslogEmptyArg() {
        LogsUtil.setSyslog(true);
        aggregation.readLog(null, null);
        assertEquals(1, aggregation.getReadLogFutureList().size());
        LogsUtil.setSyslog(false);
    }

    @Test
    void testListLogHappyCase() {
        writer.println(logEntry);

        String[] logDirPath = {logDir.toString()};
        Set<File> logFileSet = aggregation.listLog(logDirPath);

        assertEquals(1, logFileSet.size());
        assertTrue(logFileSet.contains(logFile));
    }

    @Test
    void testListLogEmptyDir() {
        File Dir = logDir.resolve("x").toFile();
        String[] logDirPath = {Dir.getPath()};
        Set<File> logFileSet = aggregation.listLog(logDirPath);

        assertEquals(0, logFileSet.size());
    }

    @Test
    void testListLogInvalidDir() {
        String[] logDirPath = {"BadPath"};
        Set<File> logFileSet = aggregation.listLog(logDirPath);

        assertEquals(0, logFileSet.size());
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Log dir provided invalid: BadPath"));

    }

    @AfterEach
    void cleanup() {
        aggregation.close();
        deleteDir(logDir.toFile());
        writer.close();
        errorStream.close();
    }
}