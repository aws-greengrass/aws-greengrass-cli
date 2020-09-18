/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.greengrass.cli.util.logs;

public interface Filter {
    boolean filter(LogEntry entry);

    void composeRule(String[] timeWindow, String[] filterExpressions);

    boolean reachedEndTime();
}
