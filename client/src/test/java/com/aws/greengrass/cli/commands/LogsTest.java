/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.CLI;
import com.aws.greengrass.cli.CommandFactory;
import com.aws.greengrass.cli.TestUtil;
import com.aws.greengrass.cli.module.AdapterModule;
import com.aws.greengrass.cli.module.DaggerCommandsComponent;
import com.aws.greengrass.cli.util.logs.LogsUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Calendar;

import static com.aws.greengrass.cli.TestUtil.deleteDir;
import static com.aws.greengrass.cli.util.logs.impl.VisualizationImpl.ANSI_HIGHLIGHT;
import static com.aws.greengrass.cli.util.logs.impl.VisualizationImpl.ANSI_HIGHLIGHT_RESET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;


public class LogsTest {
    private static final String logEntry0 = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 80000 MILLISECONDS\","
            + "\"timestamp\":0,\"cause\":null}";

    private static final String logEntry1 = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";

    private static final String logEntry2 = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 70000 MILLISECONDS\","
            + "\"timestamp\":1594836028090,\"cause\":null}";

    private static final String syslogEntry1 = "Jan  1 00:00:01 3c22fb9c16f9 com.apple.xpc.launchd[1] "
            + "(com.apple.mdworker.shared.0A000000-0000-0000-0000-000000000000[83373]): "
            + "Service exited due to SIGKILL | sent by mds[142]";

    private static final String syslogEntry2 = "Jan  1 00:00:02 ip-172-31-55-139 systemd: "
            + "Created slice User Slice of root.";

    private static final String syslogEntry3 = "Jan  1 00:00:03 ip-172-31-48-70 systemd[1]: "
            + "Started Load Kernel Modules.";

    private static final String YEAR = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));

    @TempDir
    Path logDir;
    private Path logFile;

    private ByteArrayOutputStream byteArrayOutputStream;
    private PrintStream printStream;
    private PrintStream fileWriter;

    @BeforeEach
    void init() throws FileNotFoundException {
        logFile = logDir.resolve("greengrass.log");
        byteArrayOutputStream = new ByteArrayOutputStream();
        printStream = TestUtil.createPrintStreamFromOutputStream(byteArrayOutputStream);
        LogsUtil.setPrintStream(printStream);
        fileWriter = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile.toFile()));
    }

    @Test
    void ListKeywordsHappyCase() {
        runCommandLine("logs", "list-keywords");
        runCommandLine("logs", "list-keywords", "--syslog");
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream),
                containsString("Suggested keywords for syslog format:"));
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream),
                containsString("Suggested keywords for Greengrass log format:"));
        LogsUtil.setSyslog(false);
    }

    @Test
    void testListLogFileHappyCase() {
        fileWriter.println(logEntry1);
        runCommandLine("logs", "list-log-files", "--log-dir", logDir.toString());

        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream),
                containsString(logFile.toString() + System.lineSeparator() + "Total 1 files found."));
    }

    @Test
    void testListLogFileEmptyCase() {
        runCommandLine("logs", "list-log-files", "--log-dir", logFile.toString());
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream),
                containsString("No log file found."));
    }

    @Test
    void testGetHappyCase() throws InterruptedException {
        fileWriter.println(logEntry1);

        Thread thread = new Thread(() -> runCommandLine("logs", "get", "--log-file", logFile.toString(),
                "--filter", "level=DEBUG", "--filter", "thread=idle-connection-reaper", "--filter", "60000",
                "--time-window", "2020-07-14T02:00:00,2020-07-16T03:00:00", "--verbose", "--no-color"));

        thread.start();
        thread.join();
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[DEBUG] "
                + "(idle-connection-reaper) null: null. Closing connections idle longer than 60000 MILLISECONDS"));
    }

    @Test
    void testGetHighLightCase() throws InterruptedException {
        fileWriter.println(logEntry1);

        Thread thread = new Thread(() -> runCommandLine("logs", "get", "--log-file", logFile.toString(),
                "--filter", "level=DEBUG", "--filter", "thread=idle-connection-reaper", "--filter", "bad,60000",
                "--time-window", "2020-07-14T02:00:00,2020-07-16T03:00:00", "--verbose"));

        thread.start();
        thread.join();
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[" + ANSI_HIGHLIGHT
                + "DEBUG" + ANSI_HIGHLIGHT_RESET +"] " + "(" + ANSI_HIGHLIGHT + "idle-connection-reaper" + ANSI_HIGHLIGHT_RESET
                + ") null: null. Closing connections idle longer than " + ANSI_HIGHLIGHT +"60000" + ANSI_HIGHLIGHT_RESET + " MILLISECONDS"));
    }

    @Test
    void testGetFollowHappyCase() throws InterruptedException {
        fileWriter.println(logEntry1);
        Thread thread = new Thread(() -> runCommandLine("logs", "get", "--log-file", logFile.toString(),
                "--time-window", "2020-07-14T02:00:00,+1s", "--follow", "--verbose", "--no-color"));
        thread.start();
        // we wait for 500ms to write more entries to the file to test the follow option.
        fileWriter.println(logEntry2);
        fileWriter.println(logEntry0);
        thread.join();

        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[DEBUG] "
                + "(idle-connection-reaper) null: null. Closing connections idle longer than 60000 MILLISECONDS"));
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[DEBUG] "
                + "(idle-connection-reaper) null: null. Closing connections idle longer than 70000 MILLISECONDS"));
        assertFalse(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream).contains("80000"));
    }

    @Test
    void testGetFollowMultipleGroupCase() throws InterruptedException, FileNotFoundException {
        Path logFile2 = logDir.resolve("xxx.log");
        Thread thread = new Thread(() -> runCommandLine("logs", "get", "--log-file", logFile.toString(),
                "--log-file", logFile2.toString(), "--time-window", "2020-07-14T02:00:00,+1s",
                "--follow", "--verbose", "--no-color"));
        thread.start();
        fileWriter.println(logEntry1);

        try (PrintStream fileWriter2 = TestUtil.createPrintStreamFromOutputStream(new FileOutputStream(logFile2.toFile()))) {
            fileWriter2.println(logEntry2);
        }
        thread.join();

        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[DEBUG] "
                + "(idle-connection-reaper) null: null. Closing connections idle longer than 60000 MILLISECONDS"));
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[DEBUG] "
                + "(idle-connection-reaper) null: null. Closing connections idle longer than 70000 MILLISECONDS"));
    }

    @Test
    void testGetBeforeHappyCase() throws InterruptedException {
        fileWriter.println(logEntry2);
        fileWriter.println(logEntry1);
        Thread thread = new Thread(() -> runCommandLine("logs", "get", "--log-file", logFile.toString(),
                "--before", "1", "--verbose", "--no-color", "--filter", "60000"));
        thread.start();
        thread.join();
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[DEBUG] "
                + "(idle-connection-reaper) null: null. Closing connections idle longer than 60000 MILLISECONDS"));
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[DEBUG] "
                + "(idle-connection-reaper) null: null. Closing connections idle longer than 70000 MILLISECONDS"));
    }

    @Test
    void testGetAfterHappyCase() throws InterruptedException {
        fileWriter.println(logEntry2);
        fileWriter.println(logEntry1);
        Thread thread = new Thread(() -> runCommandLine("logs", "get", "--log-file", logFile.toString(),
                "--after", "1", "--verbose", "--no-color", "--filter", "70000"));
        thread.start();
        thread.join();
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[DEBUG] "
                + "(idle-connection-reaper) null: null. Closing connections idle longer than 60000 MILLISECONDS"));
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[DEBUG] "
                + "(idle-connection-reaper) null: null. Closing connections idle longer than 70000 MILLISECONDS"));
    }

    @Test
    void testGetSyslogHappyCase() throws InterruptedException {
        fileWriter.println(syslogEntry1);
        fileWriter.println(syslogEntry2);
        Thread thread = new Thread(() -> runCommandLine("logs", "get", "--log-file", logFile.toString(),
                "--syslog", "--time-window", YEAR + "-01-01," + YEAR + "-01-02", "--filter", "apple,systemd"));
        thread.start();
        thread.join();
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("Jan  1 00:00:01 3c22fb9c16f9 com."
                + ANSI_HIGHLIGHT + "apple" + ANSI_HIGHLIGHT_RESET + ".xpc.launchd[1] (com."
                + ANSI_HIGHLIGHT + "apple" + ANSI_HIGHLIGHT_RESET + ".mdworker.shared.0A000000-0000-0000-0000-000000000000[83373]): "
                + "Service exited due to SIGKILL | sent by mds[142]"));
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("Jan  1 00:00:02 ip-172-31-55-139 "
                + ANSI_HIGHLIGHT + "systemd" + ANSI_HIGHLIGHT_RESET + ": Created slice User Slice of root."));
        LogsUtil.setSyslog(false);
    }

    @Test
    void testGetSyslogFollowHappyCase() throws InterruptedException {
        fileWriter.println(syslogEntry1);
        Thread thread = new Thread(() -> runCommandLine("logs", "get", "--log-file", logFile.toString(),
                "--time-window", YEAR + "-01-01,+5s", "--follow", "--syslog", "--no-color"));
        thread.start();

        fileWriter.println(syslogEntry2);
        fileWriter.println(syslogEntry3);

        // The thread should exit in 5 seconds. See the `--time-window` `+5s` argument. But we wait here for at most
        // 10 seconds just in case.
        thread.join(10_000);

        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString(syslogEntry1));
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString(syslogEntry2));
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString(syslogEntry3));
        LogsUtil.setSyslog(false);
    }

    @Test
    void testGetSyslogDirAndVerbose() throws InterruptedException {
        LogsUtil.setErrorStream(printStream);
        Thread thread = new Thread(() -> runCommandLine("logs", "get", "--log-file", logFile.toString(),
                "--syslog", "--time-window", "2020-08-30,2020-09-05", "--log-dir", logDir.toString(), "--verbose"));
        thread.start();
        thread.join();
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream),
                containsString("Syslog does not support directory input!"));
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream),
                containsString("Syslog does not support verbosity!"));
        LogsUtil.setSyslog(false);
    }

    @AfterEach
    void cleanup() {
        printStream.close();
        fileWriter.close();
        deleteDir(logDir.toFile());
    }

    private void runCommandLine(String... args) {
        new CommandLine(new CLI(), new CommandFactory(DaggerCommandsComponent.builder().adapterModule(new AdapterModule(null)).build())).execute(args);
    }
}
