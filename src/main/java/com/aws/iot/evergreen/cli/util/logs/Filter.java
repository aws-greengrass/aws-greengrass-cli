/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs;

import java.util.Map;

public interface Filter {
    boolean filter(String logEntry, Map<String, Object> parsedJsonMap);

    void composeRule(String[] timeWindow, String[] filterExpressions);
}