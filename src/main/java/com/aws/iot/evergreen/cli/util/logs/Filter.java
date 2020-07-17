// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.aws.iot.evergreen.cli.util.logs;

import java.util.Map;

public interface Filter {
    boolean Filter(String logEntry, Map<String, Object> parsedJsonMap);

    void ComposeRule(String[] timeWindow, String[] filterExpressions);
}