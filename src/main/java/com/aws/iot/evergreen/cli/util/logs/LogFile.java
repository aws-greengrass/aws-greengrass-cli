package com.aws.iot.evergreen.cli.util.logs;

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

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH");

    public LogFile(File file, String timeString, String indexString) {
        this.file = file;
        // If no timestamp or index string is provided, we default the timestamp and index to be maximum,
        // so that they will be latest in an ascending order.
        if (timeString == null || indexString == null) {
            this.timestamp = LocalDateTime.MAX;
            this.index = Integer.MAX_VALUE;
            // this is only true when the file name contains no timestamp or index.
            this.update = true;
            return;
        }
        this.timestamp = LocalDateTime.parse(timeString, formatter);
        this.index = Integer.parseInt(indexString);
        this.update = false;
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
