/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.util.logs;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 *  LogEntry class that contains the line, parsed JSON map, and timestamp.
 *  Note: this class has a natural ordering that is inconsistent with equals.
 */
@Getter
public class LogEntry implements Comparable<LogEntry> {
    private String line;
    private Map<String, Object> map;
    private long timestamp;

    private List<String> matchedKeywords = new ArrayList<>();

    // Pattern to match for syslog format defined by RFC 3164 https://tools.ietf.org/html/rfc3164#section-4.1
    // "<$Priority>$Timestamp $Host $Logger ($Class): $Message"
    private static final Pattern SYSLOG_PATTERN = Pattern.compile("(<([0-9]+)>)?"
                    // Matching for timestamp of format "Mmm dd hh:mm:ss". This is mandatory.
                    + "([a-zA-z]{3}\\s[0-9\\s][0-9]\\s[0-9]{2}:[0-9]{2}:[0-9]{2})"
                    // Matching for "host logger (class): ". These fields are optional.
                    + "((\\s([\\S]+))?"
                    + "(\\s([\\S]+))?"
                    + "(\\s\\(([\\S]+)\\))?"
                    // The rest after colon is defaulted to be message.
                    + ":\\s)?(.+)"
            );
    private static final DateTimeFormatter SYSLOG_TIME_FORMAT = new DateTimeFormatterBuilder()
            .parseDefaulting(ChronoField.YEAR, LocalDateTime.now().getYear())
            .parseCaseInsensitive().appendPattern("MMM dd HH:mm:ss").toFormatter(Locale.ENGLISH);

    /**
     * Constructor for LogEntry.
     * @param line a line of log entry
     * We throw an IOException to the outside to handle failed parsing.
     */
    public LogEntry(String line) throws JsonProcessingException {
        this.line = line;
        if (LogsUtil.isSyslog()) {
            parseSyslogFromString(line);
            return;
        }
        //We handle parsing first so that if JsonProcessingException is thrown we won't change the fields of this class.
        this.map = parseJSONFromString(line);
        if (map.get("timestamp") instanceof Long) {
            this.timestamp = (long) map.get("timestamp");
            return;
        }
        this.timestamp = Long.parseLong(map.get("timestamp").toString());
    }

    private void parseSyslogFromString(String line) {
        Matcher matcher = SYSLOG_PATTERN.matcher(line);
        if (!matcher.find()) {
            LogsUtil.getErrorStream().println("Unable to parse syslog: " + line);
            return;
        }
        map = new HashMap<>();
        map.put("priority", matcher.group(2));
        map.put("host", matcher.group(6));
        map.put("logger", matcher.group(8));
        map.put("class", matcher.group(10));
        timestamp = LocalDateTime.parse(matcher.group(3).replace("  ", " 0"), SYSLOG_TIME_FORMAT)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        map.put("timestamp", timestamp);

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
