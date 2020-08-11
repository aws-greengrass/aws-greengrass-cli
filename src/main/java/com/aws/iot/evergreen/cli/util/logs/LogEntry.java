package com.aws.iot.evergreen.cli.util.logs;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import lombok.Synchronized;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

/*
 *  LogEntry class that contains the line, parsed JSON map, and timestamp.
 *  Note: this class has a natural ordering that is inconsistent with equals.
 */
@Getter
public class LogEntry implements Comparable<LogEntry> {
    private String line;
    private Map<String, Object> map;
    private long timestamp;

    // We use a CountDownLatch to make sure that the log entry is visualized before it's recycled
    private static final CountDownLatch defaultLatch = new CountDownLatch(0);
    private CountDownLatch countDownLatch = defaultLatch;

    /**
     * Setter for LogEntry.
     * @param line a line of log entry
     * We prefer setter over a constructor because a LogEntry instance is expected to be reused for multiple times.
     * We throw an IOException to the outside to handle failed parsing.
     */
    public void setLogEntry(String line) throws JsonProcessingException {
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        //We handle parsing first so that if JsonProcessingException is thrown we won't change the fields of this class.
        this.map = parseJSONFromString(line);

        this.line = line;
        if (map.get("timestamp") instanceof Long) {
            this.timestamp = (long) map.get("timestamp");
            visualizeReady();
            return;
        }
        this.timestamp = Long.parseLong(map.get("timestamp").toString());
        visualizeReady();
    }

    private Map<String, Object> parseJSONFromString(String line) throws JsonProcessingException {
        return LogsUtil.MAP_READER.readValue(line);
    }

    // Order by timestamp.
    @Override
    public int compareTo(LogEntry other) {
        return Long.compare(this.getTimestamp(), other.getTimestamp());
    }

    @Synchronized
    public void visualizeFinished() {
        countDownLatch.countDown();
    }

    @Synchronized
    public void visualizeReady() {
        countDownLatch = new CountDownLatch(1);
    }
}
