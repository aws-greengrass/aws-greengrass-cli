/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.EvergreenStructuredLogMessage;
import com.aws.iot.evergreen.cli.util.logs.Visualization;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class VisualizationImpl implements Visualization {
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Display a log entry in text format
     */
    @Override
    public String Visualize(String logEntry) {
        String msg;
        try {
            EvergreenStructuredLogMessage eg = mapper.readValue(logEntry, EvergreenStructuredLogMessage.class);
            msg = eg.getTextMessage();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize: " + logEntry, e);
        }
        return msg;
    }
}
