/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs;

import java.io.File;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

public interface Aggregation {
    BlockingQueue<LogEntry> readLog(String[] logFile, String[] logDir, Boolean follow, Filter filter);

    Set<File> listLog(String[] logDir);

    Boolean isAlive();

    void close();
}