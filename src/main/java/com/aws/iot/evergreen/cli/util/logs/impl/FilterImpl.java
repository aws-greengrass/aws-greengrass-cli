// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Filter;
import lombok.Getter;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilterImpl implements Filter {
    @Getter
    private Map<Timestamp, List<Timestamp>> parsedTimeWindowMap = new HashMap<>();
    @Getter
    private List<Map<String, List<String>>> filterMapCollection = new ArrayList<>();

    /**
     * Determines if a log entry matches the defined filter.
     */
    @Override
    public boolean Filter(String logEntry, Map<String, Object> parsedJsonMap) {
        return CheckFilterExpression(logEntry, parsedJsonMap) && CheckTimeWindow(parsedJsonMap);
    }

    /**
     * Parses time windows and filter expressions into parsedTimeWindow and filterMapCollection
     *
     * @param timeWindow        arguments of --time-window beginTime,endTime
     * @param filterExpressions arguments of --filter key1=val1,key2-val2
     */
    @Override
    public void ComposeRule(String[] timeWindow, String[] filterExpressions) {
        ComposeParsedTimeWindow(timeWindow);
        ComposeFilterMapCollection(filterExpressions);

        if (filterMapCollection.isEmpty() && parsedTimeWindowMap.isEmpty()) {
            throw new RuntimeException("No filter provided!");
        }
    }

    /**
     * Helper function to construct parsedTimeWindow
     */
    private void ComposeParsedTimeWindow(String[] timeWindow) {
        /*
         *  TODO: Add support for simpler time window input. Handle time zone difference.
         *  https://github.com/aws/aws-greengrass-cli/pull/14#discussion_r455419380
         */

        parsedTimeWindowMap.clear();
        if (timeWindow != null) {
            for (String window : timeWindow) {
                String[] time = window.split(",");

                if (time.length != 2) {
                    throw new RuntimeException("Time window provided invalid: " + window);
                }

                Timestamp beginTime = Timestamp.valueOf(LocalDateTime.parse(time[0]));
                Timestamp endTime = Timestamp.valueOf(LocalDateTime.parse(time[1]));

                if (!parsedTimeWindowMap.containsKey(beginTime)) {
                    parsedTimeWindowMap.put(beginTime, new ArrayList<>());
                }
                parsedTimeWindowMap.get(beginTime).add(endTime);
            }
        }
    }

    /**
     * Helper function to construct filterMapCollection
     */
    private void ComposeFilterMapCollection(String[] filterExpressions) {
        //TODO: Add support for regex.
        filterMapCollection.clear();
        if (filterExpressions != null) {
            for (String expression : filterExpressions) {
                String[] parsedExpression = expression.split(",");

                Map<String, List<String>> filterMap = new HashMap<>();

                for (String element : parsedExpression) {
                    String[] parsedMap = element.split("=");

                    if (parsedMap.length != 2) {
                        throw new RuntimeException("Filter expression provided invalid: " + element);
                    }

                    if (!filterMap.containsKey(parsedMap[0])) {
                        filterMap.put(parsedMap[0], new ArrayList<>());
                    }
                    filterMap.get(parsedMap[0]).add(parsedMap[1]);
                }

                filterMapCollection.add(filterMap);
            }
        }
    }


    /**
     * Check if the data matches defined filter expression
     * "KEYWORD" defines keyword search
     * And-relation between filters
     * Or-relation within filters
     */
    private boolean CheckFilterExpression(String logEntry, Map<String, Object> parsedJsonMap) {
        for (Map<String, List<String>> filterMap : filterMapCollection) {
            boolean check = false;
            for (String key : filterMap.keySet()) {
                List<String> valArray = filterMap.get(key);
                for (String val : valArray) {
                    if (key.equals("KEYWORD") && logEntry.contains(val)) {
                        check = true;
                        break;
                    }
                    if (!key.equals("KEYWORD") && parsedJsonMap.containsKey(key) && parsedJsonMap.get(key).toString().equals(val)) {
                        check = true;
                        break;
                    }
                }
            }
            if (!check) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the data matches defined time windows
     */
    private boolean CheckTimeWindow(Map<String, Object> parsedJsonMap) {
        if (parsedTimeWindowMap.isEmpty()) {
            return true;
        }
        for (Map.Entry<Timestamp, List<Timestamp>> entry : parsedTimeWindowMap.entrySet()) {
            Timestamp beginTime = entry.getKey();
            for (Timestamp endTime : entry.getValue()) {
                Timestamp dataTime = new Timestamp(Long.parseLong(parsedJsonMap.get("timestamp").toString()));

                if (beginTime.before(dataTime) && endTime.after(dataTime)) {
                    return true;
                }
            }
        }
        return false;
    }
}