/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs;

public interface Visualization {
    void visualize(LogEntry logEntry, boolean removeColor, boolean verbose);
}
