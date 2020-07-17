// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.aws.iot.evergreen.cli.impl;

import com.aws.iot.evergreen.cli.util.logs.impl.VisualizationImpl;
import com.aws.iot.evergreen.logging.impl.EvergreenStructuredLogMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VisualizationImplTest {
    private static final String logEntry = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\"," +
            "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\"," +
            "\"timestamp\":1594836028088,\"cause\":null}";
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void VisualizeHappyCase() throws JsonProcessingException {
        VisualizationImpl visualization = new VisualizationImpl();

        assertTrue(visualization.Visualize(mapper.readValue(logEntry, EvergreenStructuredLogMessage.class))
                .contains("[DEBUG] (idle-connection-reaper) null: null. " +
                        "Closing connections idle longer than 60000 MILLISECONDS"));
    }
}