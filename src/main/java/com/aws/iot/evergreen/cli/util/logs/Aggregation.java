/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs;

import java.io.File;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

public interface Aggregation {
    void configure(Boolean follow, Filter filter, int max);

    BlockingQueue<LogEntry> readLog(String[] logFile, String[] logDir);

    Set<File> listLog(String[] logDir);

    Boolean isAlive();

    void close();
}