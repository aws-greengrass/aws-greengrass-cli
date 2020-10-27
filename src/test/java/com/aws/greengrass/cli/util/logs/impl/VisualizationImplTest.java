/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.util.logs.impl;

import com.aws.greengrass.cli.TestUtil;
import com.aws.greengrass.cli.util.logs.LogEntry;
import com.aws.greengrass.cli.util.logs.LogsUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static com.aws.greengrass.cli.util.logs.impl.VisualizationImpl.ANSI_HIGHLIGHT;
import static com.aws.greengrass.cli.util.logs.impl.VisualizationImpl.ANSI_HIGHLIGHT_RESET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class VisualizationImplTest {
    private static final String logEntry = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";
    private static final String logEntry2 = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"loggerName\":\"aaa.aaa.logger\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":{\"cause\":null,\"stackTrace\":[],"
            + "\"message\":\"Service in broken state after deployment\",\"suppressed\":[]}}";

    private static final String badLogEntry = "{\"ttttt\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028087,\"cause\":null}";

    private ByteArrayOutputStream byteArrayOutputStream;
    private ByteArrayOutputStream errOutputStream;
    private PrintStream printStream;
    private PrintStream errorStream;
    private VisualizationImpl visualization = new VisualizationImpl();
    private LogEntry entry;

    @BeforeEach
    void init() {
        byteArrayOutputStream = new ByteArrayOutputStream();
        errOutputStream = new ByteArrayOutputStream();
        printStream = TestUtil.createPrintStreamFromOutputStream(byteArrayOutputStream);
        errorStream = TestUtil.createPrintStreamFromOutputStream(errOutputStream);
        LogsUtil.setPrintStream(printStream);
        LogsUtil.setErrorStream(errorStream);
    }

    @Test
    void visualizeHappyCase() throws JsonProcessingException {
        entry = new LogEntry(logEntry);
        visualization.visualize(entry, true, true);
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[DEBUG]"
                + " (idle-connection-reaper) null: null. Closing connections idle longer than 60000 MILLISECONDS"));
    }

    @Test
    void visualizeColorHappyCase() throws JsonProcessingException {
        entry = new LogEntry(logEntry);
        entry.getMatchedKeywords().add("connection");
        visualization.visualize(entry, false, true);
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[DEBUG]"
                + " (idle-" + ANSI_HIGHLIGHT + "connection" + ANSI_HIGHLIGHT_RESET + "-reaper) null: null. Closing "
                + ANSI_HIGHLIGHT + "connection" + ANSI_HIGHLIGHT_RESET +"s idle longer than 60000 MILLISECONDS"));
    }

    @Test
    void visualizeAbbreviateHappyCase() throws JsonProcessingException {
        entry = new LogEntry(logEntry2);
        visualization.visualize(entry, true, false);
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString("[DEBUG]"
                + " aaa.logger: Closing connections idle longer than 60000 MILLISECONDS"));
        assertThat(TestUtil.byteArrayOutputStreamToString(byteArrayOutputStream), containsString(ANSI_HIGHLIGHT
                + "EXCEPTION: Service in broken state after deployment" + ANSI_HIGHLIGHT_RESET));
    }

    @Test
    void visualizeInvalidLogEntry() throws JsonProcessingException {
        entry = new LogEntry(badLogEntry);
        visualization.visualize(entry, true, true);
        assertThat(TestUtil.byteArrayOutputStreamToString(errOutputStream),
                containsString("Unable to parse log message: "));
    }

    @AfterEach
    void cleanup() {
        printStream.close();
        errorStream.close();
    }
}
