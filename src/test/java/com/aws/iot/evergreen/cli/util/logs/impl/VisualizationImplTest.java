/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.TestUtil;
import com.aws.iot.evergreen.logging.impl.EvergreenStructuredLogMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class VisualizationImplTest {
    private static final String logEntry = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";

    @Test
    void visualizeHappyCase() throws JsonProcessingException {
        VisualizationImpl visualization = new VisualizationImpl();

        assertThat(visualization.visualize(TestUtil.getMapper().readValue(logEntry, EvergreenStructuredLogMessage.class)),
                containsString("[DEBUG] (idle-connection-reaper) null: null. "
                        + "Closing connections idle longer than 60000 MILLISECONDS"));
    }
}