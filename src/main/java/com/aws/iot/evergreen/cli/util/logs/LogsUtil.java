package com.aws.iot.evergreen.cli.util.logs;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.PrintStream;
import java.util.Map;

import static java.lang.Long.signum;

public class LogsUtil {
    @Setter
    @Getter
    private static PrintStream printStream = System.out;

    @Setter
    @Getter
    private static PrintStream errorStream = System.err;

    // Create one ObjectMapper for each thread.
    private static final ThreadLocal<ObjectMapper> mapper = ThreadLocal.withInitial(ObjectMapper::new);

    public static ObjectMapper getMapper() {
        return mapper.get();
    }

    // LogEntry class that contains the line and parsed JSON map.
    @AllArgsConstructor
    @Getter
    public static class LogEntry implements Comparable<LogEntry> {
        private String line;
        private Map<String, Object> map;

        // Order by timestamp.
        @Override
        public int compareTo(LogEntry other) {
            String thisTimestamp = this.getMap().get("timestamp").toString();
            String otherTimestamp = other.getMap().get("timestamp").toString();
            if (thisTimestamp == null) {
                if (otherTimestamp == null) {
                    return 0;
                }
                return 1;
            }
            if (otherTimestamp == null) {
                return -1;
            }
            return signum(Long.parseLong(thisTimestamp) - Long.parseLong(otherTimestamp));
        }
    }
}
