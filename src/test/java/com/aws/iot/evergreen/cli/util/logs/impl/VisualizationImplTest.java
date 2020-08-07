/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class VisualizationImplTest {
    private static final String logEntry = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";

    private static final String badLogEntry = "{\"ttttt\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028087,\"cause\":null}";

    private ByteArrayOutputStream byteArrayOutputStream;
    private ByteArrayOutputStream errOutputStream;
    private PrintStream printStream;
    private PrintStream errorStream;
    private VisualizationImpl visualization = new VisualizationImpl();

    @BeforeEach
    void init() {
        byteArrayOutputStream = new ByteArrayOutputStream();
        errOutputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(byteArrayOutputStream);
        errorStream = new PrintStream(errOutputStream);
        LogsUtil.setPrintStream(printStream);
        LogsUtil.setErrorStream(errorStream);
    }

    @Test
    void visualizeHappyCase() {
        visualization.visualize(logEntry);
        assertThat(byteArrayOutputStream.toString(), containsString("[DEBUG] (idle-connection-reaper) "
                + "null: null. Closing connections idle longer than 60000 MILLISECONDS"));
    }

    @Test
    void visualizeInvalidLogEntry() {
        visualization.visualize(badLogEntry);
        assertThat(errOutputStream.toString(), containsString("Unable to parse EvergreenStructuredLogMessage: "));
    }

    @AfterEach
    void cleanup() {
        printStream.close();
        errorStream.close();
    }
}