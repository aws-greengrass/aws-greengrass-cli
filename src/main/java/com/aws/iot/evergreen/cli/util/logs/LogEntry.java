package com.aws.iot.evergreen.cli.util.logs;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 *  LogEntry class that contains the line, parsed JSON map, and timestamp.
 *  Note: this class has a natural ordering that is inconsistent with equals.
 */
@Getter
public class LogEntry implements Comparable<LogEntry> {
    private String line;
    private Map<String, Object> map;
    private long timestamp;

    @Setter
    private boolean matched;

    private List<String> matchedKeywords = new ArrayList<>();

    /**
     * Setter for LogEntry.
     * @param line a line of log entry
     * We prefer setter over a constructor because a LogEntry instance is expected to be reused for multiple times.
     * We throw an IOException to the outside to handle failed parsing.
     */
    public LogEntry(String line) throws JsonProcessingException {
        //We handle parsing first so that if JsonProcessingException is thrown we won't change the fields of this class.
        this.map = parseJSONFromString(line);
        this.matched = false;
        this.line = line;
        if (map.get("timestamp") instanceof Long) {
            this.timestamp = (long) map.get("timestamp");
            return;
        }
        this.timestamp = Long.parseLong(map.get("timestamp").toString());
    }

    private Map<String, Object> parseJSONFromString(String line) throws JsonProcessingException {
        return LogsUtil.MAP_READER.readValue(line);
    }

    // Order by timestamp.
    @Override
    public int compareTo(LogEntry other) {
        if (this.getTimestamp() < other.getTimestamp()) {
            return -1;
        }
        return 1;
    }
}
