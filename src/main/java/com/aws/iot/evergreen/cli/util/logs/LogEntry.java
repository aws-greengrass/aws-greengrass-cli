package com.aws.iot.evergreen.cli.util.logs;

import lombok.Getter;
import lombok.Synchronized;

import java.util.Map;

import static java.lang.Long.signum;

/*
 *  LogEntry class that contains the line, parsed JSON map, and timestamp.
 *  Note: this class has a natural ordering that is inconsistent with equals.
 */
@Getter(onMethod_ = {@Synchronized})
public class LogEntry implements Comparable<LogEntry> {
    private String line;
    private Map<String, Object> map;
    private long timestamp;

    @Synchronized
    public void setLogEntry(String line, Map<String, Object> map) {
        this.line = line;
        this.map = map;
        try {
            this.timestamp = (long) map.get("timestamp");
        } catch (ClassCastException e) {
            this.timestamp = Long.parseLong(map.get("timestamp").toString());
        }
    }

    // Order by timestamp.
    @Override
    public int compareTo(LogEntry other) {
        return signum(this.getTimestamp() - other.getTimestamp());
    }
}
