/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
import com.aws.iot.evergreen.cli.util.logs.Visualization;
import com.aws.iot.evergreen.logging.impl.EvergreenStructuredLogMessage;

import java.io.IOException;

public class VisualizationImpl implements Visualization {
    /*
     * Display a log entry in text format
     */
    @Override
    public void visualize(String line) {
        try {
            EvergreenStructuredLogMessage logMessage = LogsUtil.EVERGREEN_STRUCTURED_LOG_READER.readValue(line);
            LogsUtil.getPrintStream().println(logMessage.getTextMessage());
        } catch (IOException e) {
            LogsUtil.getErrorStream().println("Unable to parse EvergreenStructuredLogMessage: ");
            LogsUtil.getErrorStream().println(line);
        }
    }
}
