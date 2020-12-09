/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.util.logs;

import lombok.Getter;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
public class LogFile implements Comparable<LogFile> {
    private File file;
    private LocalDateTime timestamp;
    private int index;
    private boolean update;

    private static final DateTimeFormatter formatterByHour = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH");

    public LogFile(File file, String timeString, String indexString) {
        this.file = file;
        // If no timestamp or index string is provided, we default the timestamp and index to be maximum,
        // so that they will be latest in an ascending order.
        if (timeString == null || timeString.isEmpty() || indexString == null || indexString.isEmpty()) {
            this.timestamp = LocalDateTime.MAX;
            this.index = Integer.MAX_VALUE;
            // this is only true when the file name contains no timestamp or index.
            this.update = true;
            return;
        }
        this.index = Integer.parseInt(indexString);
        this.update = false;
        this.timestamp = LocalDateTime.parse(timeString, formatterByHour);
    }

    @Override
    public int compareTo(LogFile logFile) {
        if (timestamp.isBefore(logFile.getTimestamp())) {
            return -1;
        }
        if (timestamp.isAfter(logFile.getTimestamp())) {
            return 1;
        }
        return Integer.compare(index, logFile.getIndex());
    }
}
