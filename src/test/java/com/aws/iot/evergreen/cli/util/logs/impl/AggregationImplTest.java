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

    private static final String invalidLogEntry = "{\"thread-idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";

    @TempDir
    File logDir;
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
        aggregation.configure(false, filterInterface, 50, 0, 0);
        errOutputStream = new ByteArrayOutputStream();
        errorStream = TestUtil.createPrintStreamFromOutputStream(errOutputStream);
        LogsUtil.setErrorStream(errorStream);
        logFile = new File(logDir.getPath() + "/evergreen.log");
        writer = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile));
    }

    @Test
    void testReadLogFileHappyCase() throws InterruptedException {
        writer.print(logEntry);

        String[] logFilePath = {logFile.getAbsolutePath()};
        logQueue = aggregation.readLog(logFilePath, null);
        assertEquals(1, aggregation.getReadLogFutureList().size());

        assertEquals(logEntry, logQueue.take().getLine());
    }



    @Test
    void testReadLogDirHappyCase() throws InterruptedException {
        writer.print(logEntry);

        String[] logDirPath = {logDir.getAbsolutePath()};
        logQueue = aggregation.readLog(null, logDirPath);
        assertEquals(1, aggregation.getReadLogFutureList().size());

        assertEquals(logEntry, logQueue.take().getLine());
    }
    @Test
    void testReadLogInvalidLine() throws InterruptedException {
        writer.print(invalidLogEntry);

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
        writer.print(logEntry);

        String[] logFilePath = {logFile.getAbsolutePath(), logFile.getAbsolutePath()};

        logQueue = aggregation.readLog(logFilePath, null);
        assertEquals(1, aggregation.getReadLogFutureList().size());
        assertEquals(logEntry, logQueue.take().getLine());
    }

    @Test
    void testReadLogMultipleFile() throws IOException, InterruptedException {
        writer.print(logEntry);
        File logFile2 = new File(logDir.getPath() + "/evergreen2.log");
        writer = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile2));
        writer.print(logEntry2);

        File logFile3 = new File(logDir.getPath() + "/evergreen.log_2000-01-01_03_1");
        writer = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile3));
        writer.print(logEntry3);

        String[] logFilePath = {logFile.getAbsolutePath(), logFile2.getAbsolutePath(), logFile3.getPath()};

        logQueue = aggregation.readLog(logFilePath, null);
        assertEquals(2, aggregation.getReadLogFutureList().size());
        while (aggregation.isAlive()) {
            sleep(1);
        }
        assertEquals(3, logQueue.size());
        assertEquals(logEntry3, logQueue.take().getLine());
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
    }

    @Test
    void testReadLogEmptyDir() {
        File Dir = new File(logDir.getPath() + "/x");
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
    void testListLogHappyCase() {
        writer.print(logEntry);

        String[] logDirPath = {logDir.getPath()};
        Set<File> logFileSet = aggregation.listLog(logDirPath);

        assertEquals(1, logFileSet.size());
        assertTrue(logFileSet.contains(logFile));
    }


    @Test
    void testListLogEmptyDir() {
        File Dir = new File(logDir.getPath() + "/x");
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
        deleteDir(logDir);
        writer.close();
        errorStream.close();
    }

}