/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs;

public interface Filter {
    boolean Filter(String data);

    void ComposeRule(String[] timeWindow, String[] filterExpressions);
}
