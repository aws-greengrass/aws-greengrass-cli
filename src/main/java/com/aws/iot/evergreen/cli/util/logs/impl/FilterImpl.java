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
    @Getter
    private Map<Timestamp, Timestamp> parsedTimeWindowMap = new HashMap<>();
    @Getter
    private List<FilterEntry> filterEntryCollection = new ArrayList<>();

    // defined delimiters to parse filter expressions and time windows.
    private static final String filterDelimiter = ",";
    private static final String timeWindowDelimiter = ",";
    private static final String keyValDelimiter = "=";

    /*
     *  A helper entry class for filter expression
     */
    @AllArgsConstructor
    public static class FilterEntry {
        @Getter
        private Map<String, Set<String>> filterMap;
        @Getter
        private List<Pattern> regexList;
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
        if (timeWindow == null) {
            return;
        }
        parsedTimeWindowMap.clear();
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

        if (filterExpressions == null) {
            return;
        }

        filterEntryCollection.clear();
        for (String expression : filterExpressions) {
            String[] parsedExpression = expression.split(filterDelimiter);

            Map<String, Set<String>> filterMap = new HashMap<>();
            List<Pattern> regexList = new ArrayList<>();
            for (String element : parsedExpression) {
                // If it contains =, treat it as a key-val pair
                if (element.contains("=")) {
                    String[] parsedMap = element.split(keyValDelimiter);
                    if (parsedMap.length != 2) {
                        throw new RuntimeException("Filter expression provided invalid: " + element);
                    }
                    if (!filterMap.containsKey(parsedMap[0])) {
                        filterMap.put(parsedMap[0], new HashSet<>());
                    }
                    // If the filter concerns log level, we need include all log levels above the queried level
                    if (parsedMap[0].equals("level")) {
                        filterMap.get(parsedMap[0]).addAll(logLevelSet(parsedMap[1]));
                        continue;
                    }
                    filterMap.get(parsedMap[0]).add(parsedMap[1]);
                }
                // Otherwise treat it as a REGEX
                Pattern regex = Pattern.compile(element);
                regexList.add(regex);
            }
            filterEntryCollection.add(new FilterEntry(filterMap, regexList));
        }
    }


    /*
     * Check if the data matches defined filter expression
     * "KEYWORD" defines keyword search
     * And-relation between filters
     * Or-relation within filters.
     */
    private boolean checkFilterExpression(String logEntry, Map<String, Object> parsedJsonMap) {
        for (FilterEntry filterEntry : filterEntryCollection) {
            Map<String, Set<String>> filterMap = filterEntry.filterMap;
            List<Pattern> regexList = filterEntry.regexList;
            boolean check = false;

            for (Map.Entry<String, Set<String>> entry : filterMap.entrySet()) {
                for (String val : entry.getValue()) {
                    if (parsedJsonMap.containsKey(entry.getKey())
                            && parsedJsonMap.get(entry.getKey()).toString().equals(val)) {
                        check = true;
                        break;
                    }
                }
            }
            for (Pattern regex : regexList) {
                if (regex.matcher(logEntry).find()) {
                    check = true;
                    break;
                }
            }
            // Since And-relation between filters, one filter fails -> return false
            if (!check) {
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

    /*
     * Return a set containing all log levels above the input logLevel.
     */
    Set<String> logLevelSet(String logLevel) {
        Set<String> levelSet = new HashSet<>();
        levelSet.add(logLevel);
        try {
            Level level = Level.valueOf(logLevel);
            for (Level l : Level.values()) {
                if (l.compareTo(level) < 0) {
                    levelSet.add(l.toString());
                }
            }
        } catch (IllegalArgumentException e) {
            LogsUtil.getErrorStream().println("Invalid log level: " + logLevel);
            LogsUtil.getErrorStream().println(e.getMessage());
        }
        return levelSet;
    }
}