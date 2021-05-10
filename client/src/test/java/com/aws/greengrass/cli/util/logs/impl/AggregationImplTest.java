/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.util.logs.impl;

import com.aws.greengrass.cli.TestUtil;
import com.aws.greengrass.cli.util.logs.Filter;
import com.aws.greengrass.cli.util.logs.LogQueue;
import com.aws.greengrass.cli.util.logs.LogsUtil;
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
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.aws.greengrass.cli.TestUtil.deleteDir;
import static java.lang.Thread.sleep;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregationImplTest {
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
    private LogQueue logQueue;
    private Filter filterInterface = new FilterImpl();

    @BeforeEach
    void init() throws FileNotFoundException {
        aggregation = new AggregationImpl();
        aggregation.configure(false, filterInterface, 0, 0, 100);
        errOutputStream = new ByteArrayOutputStream();
        errorStream = TestUtil.createPrintStreamFromOutputStream(errOutputStream);
        LogsUtil.setErrorStream(errorStream);
        logFile = logDir.resolve("greengrass.log").toFile();
        writer = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile));
    }

    @Test
    void testReadLogFileHappyCase() throws InterruptedException {
        writer.println(logEntry);

        List<Path> logFilePath = Arrays.asList(logFile.toPath().toAbsolutePath());
        logQueue = aggregation.readLog(logFilePath, null);
        assertEquals(1, aggregation.getReadLogFutureList().size());

        assertEquals(logEntry, logQueue.take().getLine());
    }



    @Test
    void testReadLogDirHappyCase() throws InterruptedException {
        writer.println(logEntry);

        List<Path> logDirPath = Arrays.asList(logDir);
        logQueue = aggregation.readLog(null, logDirPath);
        assertEquals(1, aggregation.getReadLogFutureList().size());

        assertEquals(logEntry, logQueue.take().getLine());
    }
    @Test
    void testReadLogInvalidLine() throws InterruptedException {
        writer.println(invalidLogEntry);

        List<Path> logFilePath = Arrays.asList(logFile.toPath().toAbsolutePath());

        logQueue = aggregation.readLog(logFilePath, null);
        while (aggregation.isAlive()) {
            sleep(1);
        }
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Failed to deserialize as JSON map: " + invalidLogEntry));
    }

    @Test
    void testReadLogEmptyLine() throws InterruptedException {
        writer.print(System.lineSeparator());

        List<Path> logFilePath = Arrays.asList(logFile.toPath().toAbsolutePath());

        logQueue = aggregation.readLog(logFilePath, null);
        while (aggregation.isAlive()) {
            sleep(1);
        }
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Failed to deserialize as JSON map: "));
    }

    @Test
    void testReadLogDuplicateFile() throws InterruptedException {
        writer.println(logEntry);

        List<Path> logFilePath = Arrays.asList(logFile.toPath().toAbsolutePath(), logFile.toPath().toAbsolutePath());

        logQueue = aggregation.readLog(logFilePath, null);
        assertEquals(1, aggregation.getReadLogFutureList().size());
        assertEquals(logEntry, logQueue.take().getLine());
    }

    @Test
    void testReadLogMultipleFile() throws IOException, InterruptedException {
        writer.println(logEntry);

        File logFile2 = logDir.resolve("greengrass_2020_12_01_00_0.log").toFile();
        try (PrintStream writer = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile2))) {
            writer.println(logEntry2);
        }

        File logFile3 = logDir.resolve("aws.greengrass.Nucleus_2020_12_01_00_0.log").toFile();
        try (PrintStream writer = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile3))) {
            writer.println(logEntry3);
        }

        File logFile4 = logDir.resolve("aws.greengrass.Nucleus_2020_12_01_02_0.log").toFile();
        try (PrintStream writer = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile4))) {
            writer.println(logEntry4);
        }

        List<Path> logFilePath = Arrays.asList(logFile.toPath(), logFile2.toPath(), logFile3.toPath(), logFile4.toPath());

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
        List<Path> logFilePath = Arrays.asList(Paths.get("bad path"));
        aggregation.readLog(logFilePath, null);
        while (aggregation.isAlive()) {
            sleep(1);
        }
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Unable to parse file name: bad path"));
        Path invalidPath = Paths.get("/xxx/greengrass.log");
        logFilePath = Arrays.asList(invalidPath);
        aggregation.readLog(logFilePath, null);
        while (aggregation.isAlive()) {
            sleep(1);
        }
        String expected = String.format("Cannot find file: %s", invalidPath.toString());
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream), containsString(expected));
    }

    @Test
    void testReadLogInvalidFileRotationPattern() throws InterruptedException {
        Path logFile2 = logDir.resolve("greengrass_2020_02_00_13_01.log");
        List<Path> logFilePath = Arrays.asList(logFile2);
        aggregation.readLog(logFilePath, null);
        while (aggregation.isAlive()) {
            sleep(1);
        }
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Unable to parse timestamp from file name:"));

        logFile2 = logDir.resolve("/greengrass_2020_12_08_13_11111111111111.log");
        logFilePath = Arrays.asList(logFile2);
        aggregation.readLog(logFilePath, null);
        while (aggregation.isAlive()) {
            sleep(1);
        }
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Unable to parse file index from file name:"));

    }


    @Test
    void testReadLogEmptyDir() {
        Path dir = logDir.resolve("x");
        List<Path> logDirPath = Arrays.asList(dir);
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

        Set<File> logFileSet = aggregation.listLog(Arrays.asList(logDir));

        assertEquals(1, logFileSet.size());
        assertTrue(logFileSet.contains(logFile));
    }

    @Test
    void testListLogEmptyDir() {
        Set<File> logFileSet = aggregation.listLog(Arrays.asList(logDir.resolve("x")));

        assertEquals(0, logFileSet.size());
    }

    @Test
    void testListLogInvalidDir() {
        Set<File> logFileSet = aggregation.listLog(Arrays.asList(Paths.get("BadPath")));

        assertEquals(0, logFileSet.size());
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Log dir provided invalid: BadPath"));

    }

    @AfterEach
    void cleanup() {
        aggregation.close();
        writer.close();
        errorStream.close();
        deleteDir(logDir.toFile());
    }
}
