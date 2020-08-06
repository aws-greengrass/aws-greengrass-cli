package com.aws.iot.evergreen.cli.util.logs;

import lombok.Getter;
import lombok.Synchronized;

import java.util.Map;

import static java.lang.Long.signum;
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
        try {
            this.line = line;
            this.map = map;
            this.timestamp = (long) map.get("timestamp");
        } catch (ClassCastException e) {
            this.timestamp = Long.parseLong(map.get("timestamp").toString());
        } finally {
            setVisualizeFinished(false);
        }
    }

    // Order by timestamp.
    @Override
    public int compareTo(LogEntry other) {
        return signum(this.getTimestamp() - other.getTimestamp());
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
