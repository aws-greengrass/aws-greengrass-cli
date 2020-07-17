/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Visualization;
import com.aws.iot.evergreen.logging.impl.EvergreenStructuredLogMessage;

public class VisualizationImpl implements Visualization {
    /*
     * Display a log entry in text format
     */
    @Override
    public String visualize(EvergreenStructuredLogMessage eg) {
        return eg.getTextMessage();
    }
}
