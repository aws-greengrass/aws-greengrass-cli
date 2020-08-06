package com.aws.iot.evergreen.cli.util.logs;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Long.signum;

/*
 *  LogEntry class that contains the line, parsed JSON map, and timestamp.
 *  Note: this class has a natural ordering that is inconsistent with equals.
 */
@Getter
public class LogEntry implements Comparable<LogEntry> {
    private String line;
    private Map<String, Object> map;
    private long timestamp;

    private final ReentrantLock lock = new ReentrantLock(true);

    public void setLogEntry(String line, Map<String, Object> map) {
        lock.lock();
        try {
            this.line = line;
            this.map = map;
            this.timestamp = (long) map.get("timestamp");
        } catch (ClassCastException e) {
            this.timestamp = Long.parseLong(map.get("timestamp").toString());
        } finally {
            lock.unlock();
        }
    }

    // Order by timestamp.
    @Override
    public int compareTo(LogEntry other) {
        return signum(this.getTimestamp() - other.getTimestamp());
    }
}
