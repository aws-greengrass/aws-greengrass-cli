/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.event.Level;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class FilterImpl implements Filter {
    // defined delimiters to parse filter expressions and time windows.
    private static final String filterDelimiter = ",";
    private static final String timeWindowDelimiter = ",";
    private static final String keyValDelimiter = "=";
    private static final String levelKey = "level";
    @Getter
    private Map<Timestamp, Timestamp> parsedTimeWindowMap = new HashMap<>();
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
        return checkFilterExpression(logEntry, parsedJsonMap) && checkTimeWindow(parsedJsonMap);
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
        /*
         *  TODO: Add support for simpler time window input. Handle time zone difference.
         *  https://github.com/aws/aws-greengrass-cli/pull/14#discussion_r455419380
         */
        parsedTimeWindowMap.clear();
        if (timeWindow == null) {
            return;
        }
        for (String window : timeWindow) {
            String[] time = window.split(timeWindowDelimiter);

            if (time.length != 2) {
                throw new RuntimeException("Time window provided invalid: " + window);
            }

            Timestamp beginTime = Timestamp.valueOf(LocalDateTime.parse(time[0]));
            Timestamp endTime = Timestamp.valueOf(LocalDateTime.parse(time[1]));

            if (parsedTimeWindowMap.containsKey(beginTime)) {
                if (parsedTimeWindowMap.get(beginTime).before(endTime)) {
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
            String[] parsedExpression = expression.split(filterDelimiter);

            Map<String, Set<String>> filterMap = new HashMap<>();
            List<Pattern> regexList = new ArrayList<>();
            Level logLevel = null;

            for (String element : parsedExpression) {
                // If it doesn't contain the delimiter, treat it as a regex
                if (!element.contains(keyValDelimiter)) {
                    Pattern regex = Pattern.compile(element);
                    regexList.add(regex);
                    continue;
                }
                // Otherwise treat it as a key-val pair or a log level pair
                String[] parsedMap = element.split(keyValDelimiter);
                if (parsedMap.length != 2) {
                    throw new RuntimeException("Filter expression provided invalid: " + element);
                }

                // If the filter concerns log level, we need translate it into a slf4j.event.Level object
                if (parsedMap[0].equals(levelKey)) {
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
     * Check if the data matches defined filter expression
     * And-relation between filterEntry
     * Or-relation within each filterEntry.
     */
    private boolean checkFilterExpression(String logEntry, Map<String, Object> parsedJsonMap) {
        for (FilterEntry filterEntry : filterEntryCollection) {
            Map<String, Set<String>> filterMap = filterEntry.getFilterMap();
            List<Pattern> regexList = filterEntry.getRegexList();
            Level logLevel = filterEntry.getLogLevel();

            //Since Or-relation within filterEntry, any matched key-val pair or regex will set matchFilterEntry=true
            boolean matchFilterEntry = false;

            for (Map.Entry<String, Set<String>> entry : filterMap.entrySet()) {
                for (String val : entry.getValue()) {
                    if (parsedJsonMap.containsKey(entry.getKey())
                            && parsedJsonMap.get(entry.getKey()).toString().equals(val)) {
                        matchFilterEntry = true;
                        break;
                    }
                }
            }

            for (Pattern regex : regexList) {
                if (regex.matcher(logEntry).find()) {
                    matchFilterEntry = true;
                    break;
                }
            }

            if (parsedJsonMap.containsKey(levelKey) && logLevel != null) {
                try {
                    Level level = Level.valueOf(parsedJsonMap.get(levelKey).toString());
                    if (level.toInt() >= logLevel.toInt()) {
                        matchFilterEntry = true;
                    }
                } catch (IllegalArgumentException e) {
                    LogsUtil.getErrorStream().println("Invalid log level from: " + logEntry);
                }
            }
            // Since And-relation between filterEntry, one filter fails -> return false
            if (!matchFilterEntry) {
                return false;
            }
        }
        return true;
    }

    /*
     * Check if the data matches defined time windows.
     */
    private boolean checkTimeWindow(Map<String, Object> parsedJsonMap) {
        if (parsedTimeWindowMap.isEmpty()) {
            return true;
        }
        for (Map.Entry<Timestamp, Timestamp> entry : parsedTimeWindowMap.entrySet()) {
            Timestamp dataTime = new Timestamp(Long.parseLong(parsedJsonMap.get("timestamp").toString()));
            if (entry.getKey().before(dataTime) && entry.getValue().after(dataTime)) {
                return true;
            }
        }
        return false;
    }
}