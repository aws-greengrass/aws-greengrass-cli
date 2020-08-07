package com.aws.iot.evergreen.cli.util.logs;

import lombok.Getter;
import lombok.Synchronized;

import java.util.Map;

import static java.lang.Thread.sleep;

/*
 *  LogEntry class that contains the line, parsed JSON map, and timestamp.
 *  Note: this class has a natural ordering that is inconsistent with equals.
 */
@Getter
public class LogEntry implements Comparable<LogEntry> {
    private String line;
    private Map<String, Object> map;
    private long timestamp;

    private boolean visualizeFinished = true;

    public void setLogEntry(String line, Map<String, Object> map) {
        while (!isVisualizeFinished()) {
            //TODO: remove busy-wait.
            try {
                sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        setVisualizeFinished(false);
        this.line = line;
        this.map = map;
        if (map.get("timestamp") instanceof Long) {
            this.timestamp = (long) map.get("timestamp");
            return;
        }
        this.timestamp = Long.parseLong(map.get("timestamp").toString());
    }

    // Order by timestamp.
    @Override
    public int compareTo(LogEntry other) {
        return Long.compare(this.getTimestamp(), other.getTimestamp());
    }

    @Synchronized
    public void setVisualizeFinished(boolean target) {
        visualizeFinished = target;
    }

    @Synchronized
    public boolean isVisualizeFinished() {
        return visualizeFinished;
    }
}
