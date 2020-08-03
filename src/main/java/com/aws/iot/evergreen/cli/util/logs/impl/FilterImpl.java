/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.event.Level;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FilterImpl implements Filter {
    // defined delimiters to parse filter expressions and time windows.
    private static final String FILTER_DELIMITER = ",";
    private static final String TIME_WINDOW_DELIMITER = ",";
    private static final String KEY_VAL_DELIMITER = "=";
    private static final String LEVEL_KEY = "level";

    // defined formats for input time windows.
    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = {DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssSSS"),
            DateTimeFormatter.ISO_INSTANT, DateTimeFormatter.ISO_LOCAL_DATE_TIME};
    private static final DateTimeFormatter[] DATE_FORMATTERS = {DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.BASIC_ISO_DATE};
    private static final DateTimeFormatter[] TIME_FORMATTERS = {DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ofPattern("HH:mm:ssSSS")};

    // regex pattern for detecting relative offset.
    private static final Pattern OFFSET_PATTERN;

    static {
        StringBuilder regex = new StringBuilder("^");
        /*
         * regular expression for converting relative offset (e.g. "+1days-3hr") to integer
         * possible relative offset are any combinations of the following concatenated by + or -:
         * XXdays    or XXd
         * XXhours   or XXhrs or XXh
         * XXminutes or XXmin or XXm
         * XXseconds or XXsec or XXs
         */
        for (String unit : new String[]{"d|day", "h|hr|hour", "m|min|minute", "s|sec|second"}) {
            regex.append(String.format("(?:([+\\-]?[0-9]+)(?:%s)s?)?", unit));
        }
        regex.append("$");
        OFFSET_PATTERN = Pattern.compile(regex.toString());
    }

    @Getter
    private Map<LocalDateTime, LocalDateTime> parsedTimeWindowMap = new HashMap<>();
    @Getter
    private List<FilterEntry> filterEntryCollection = new ArrayList<>();

    /*
     *  A helper entry class for filter expression
     */
    @AllArgsConstructor
    @Getter
    public static class FilterEntry {
        private Map<String, Set<String>> filterMap;
        private List<Pattern> regexList;
        private Level logLevel;
    }

    /*
     * Determines if a log entry matches the defined filter.
     */
    @Override
    public boolean filter(String logEntry, Map<String, Object> parsedJsonMap) {
        return checkTimeWindow(parsedJsonMap) && checkFilterExpression(logEntry, parsedJsonMap);
    }

    /*
     * Parses time windows and filter expressions into parsedTimeWindow and filterMapCollection.
     *
     * @param timeWindow array containing time windows in format "beginTime,endTime"
     * @param filterExpressions array containing filter expressions in format "key1=val1,key2=val2"
     */
    @Override
    public void composeRule(String[] timeWindow, String[] filterExpressions) {
        composeParsedTimeWindow(timeWindow);
        composeFilterMapCollection(filterExpressions);
    }

    /*
     * Helper function to construct parsedTimeWindow.
     */
    private void composeParsedTimeWindow(String[] timeWindow) {
        parsedTimeWindowMap.clear();
        if (timeWindow == null) {
            return;
        }
        for (String window : timeWindow) {
            String[] time = window.split(TIME_WINDOW_DELIMITER);

            if (time.length > 2 || time.length == 0 || window.equals(",")) {
                throw new RuntimeException("Time window provided invalid: " + window);
            }
            // if ony one of beginTime and engTime is provided, treat the other one as currentTime.
            LocalDateTime currentTime = LocalDateTime.now();
            LocalDateTime beginTime = (time[0].isEmpty()) ? currentTime : composeTimeFromString(time[0], currentTime);
            LocalDateTime endTime = (time.length == 1) ? currentTime : composeTimeFromString(time[1], currentTime);

            if (parsedTimeWindowMap.containsKey(beginTime)) {
                if (parsedTimeWindowMap.get(beginTime).isBefore(endTime)) {
                    parsedTimeWindowMap.replace(beginTime, endTime);
                }
                continue;
            }
            parsedTimeWindowMap.put(beginTime, endTime);
        }
    }

    /*
     * Helper function to construct filterMapCollection.
     */
    private void composeFilterMapCollection(String[] filterExpressions) {
        filterEntryCollection.clear();
        if (filterExpressions == null) {
            return;
        }
        for (String expression : filterExpressions) {
            String[] parsedExpression = expression.split(FILTER_DELIMITER);

            Map<String, Set<String>> filterMap = new HashMap<>();
            List<Pattern> regexList = new ArrayList<>();
            Level logLevel = null;

            for (String element : parsedExpression) {
                // If it doesn't contain the delimiter, treat it as a regex
                if (!element.contains(KEY_VAL_DELIMITER)) {
                    Pattern regex = Pattern.compile(element);
                    regexList.add(regex);
                    continue;
                }
                // Otherwise treat it as a key-val pair or a log level pair
                String[] parsedMap = element.split(KEY_VAL_DELIMITER);
                if (parsedMap.length != 2) {
                    throw new RuntimeException("Filter expression provided invalid: " + element);
                }

                // If the filter concerns log level, we need translate it into a slf4j.event.Level object
                if (parsedMap[0].equals(LEVEL_KEY)) {
                    try {
                        Level level = Level.valueOf(parsedMap[1]);
                        if (logLevel == null || level.toInt() < logLevel.toInt()) {
                            logLevel = level;
                        }
                        continue;
                    } catch (IllegalArgumentException e) {
                        //If the log level provided cannot be translated, we still default it as a key-val pair
                        LogsUtil.getErrorStream().println("Invalid log level: " + parsedMap[1]);
                        LogsUtil.getErrorStream().println(e.getMessage());
                    }
                }

                filterMap.putIfAbsent(parsedMap[0], new HashSet<>());
                filterMap.get(parsedMap[0]).add(parsedMap[1]);
            }
            filterEntryCollection.add(new FilterEntry(filterMap, regexList, logLevel));
        }
    }

    /*
     * Check if the data matches defined time windows.
     */
    private boolean checkTimeWindow(Map<String, Object> parsedJsonMap) {
        if (parsedTimeWindowMap.isEmpty()) {
            return true;
        }
        for (Map.Entry<LocalDateTime, LocalDateTime> entry : parsedTimeWindowMap.entrySet()) {
            LocalDateTime dataTime = new Timestamp(Long.parseLong(parsedJsonMap.get("timestamp").toString()))
                    .toLocalDateTime();
            if (entry.getKey().isBefore(dataTime) && entry.getValue().isAfter(dataTime)) {
                return true;
            }
        }
        return false;
    }

    /*
     * Check if the data matches defined filter expression.
     */
    private boolean checkFilterExpression(String logEntry, Map<String, Object> parsedJsonMap) {
        // filterEntryCollection is grouped by AND-relation, and with in each FilterEntry is OR-relation
        for (FilterEntry filterEntry : filterEntryCollection) {
            //Since OR-relation, any matched key-val pair, regex, or log level leads to next filterEntry
            if (checkLogLevel(filterEntry.getLogLevel(), parsedJsonMap, logEntry)
                    || checkFilterMap(filterEntry.getFilterMap(), parsedJsonMap)
                    || checkRegexList(filterEntry.getRegexList(), logEntry)) {
                continue;
            }
            //Since And-relation between filterEntry, if all members of a filterEntry fails, return false
            return false;
        }
        return true;
    }

    /*
     * Helper function to check if the data matches defined filterMap.
     */
    private boolean checkFilterMap(Map<String, Set<String>> filterMap, Map<String, Object> parsedJsonMap) {
        for (Map.Entry<String, Set<String>> entry : filterMap.entrySet()) {
            for (String val : entry.getValue()) {
                if (parsedJsonMap.containsKey(entry.getKey())
                        && parsedJsonMap.get(entry.getKey()).toString().equals(val)) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * Helper function to check if the data matches defined regexList.
     */
    private boolean checkRegexList(List<Pattern> regexList, String logEntry) {
        for (Pattern regex : regexList) {
            if (regex.matcher(logEntry).find()) {
                return true;
            }
        }
        return false;
    }

    /*
     * Helper function to check if the data matches defined logLevel.
     */
    private boolean checkLogLevel(Level logLevel, Map<String, Object> parsedJsonMap, String logEntry) {
        if (parsedJsonMap.containsKey(LEVEL_KEY) && logLevel != null) {
            try {
                Level level = Level.valueOf(parsedJsonMap.get(LEVEL_KEY).toString());
                if (level.toInt() >= logLevel.toInt()) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                LogsUtil.getErrorStream().println("Invalid log level from: " + logEntry);
            }
        }
        return false;
    }

    /*
     * Helper function to compose timestamp from a time string.
     */
    private LocalDateTime composeTimeFromString(String timeString, LocalDateTime currentTime) {
        // Relative offset.
        Matcher matcher = OFFSET_PATTERN.matcher(timeString);
        if (matcher.find()) {
            if (matcher.group(1) != null) {
                currentTime = currentTime.plusDays(Integer.parseInt(matcher.group(1)));
            }
            if (matcher.group(2) != null) {
                currentTime = currentTime.plusHours(Integer.parseInt(matcher.group(2)));
            }
            if (matcher.group(3) != null) {
                currentTime = currentTime.plusMinutes(Integer.parseInt(matcher.group(3)));
            }
            if (matcher.group(4) != null) {
                currentTime = currentTime.plusSeconds(Integer.parseInt(matcher.group(4)));
            }
            return currentTime;
        }

        // Exact time.
        // parsing is not exact match hence we need to try DATE_TIME_FORMATTERS first.
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(timeString, formatter);
            } catch (DateTimeParseException ignore) {
                //This exception is expected whenever a format is not matched.
                //If none of the format is matched, we throw a RuntimeException in the last block.
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(timeString, formatter).atStartOfDay();
            } catch (DateTimeParseException ignore) {
                //Same as above.
            }
        }
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalTime.parse(timeString, formatter).atDate(currentTime.toLocalDate());
            } catch (DateTimeParseException ignore) {
                //Same as above.
            }
        }
        throw new RuntimeException("Cannot parse: " + timeString);
    }
}