/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FilterImpl implements Filter {

    private HashMap<Timestamp, ArrayList<Timestamp>> parsedTimeWindow = new HashMap<>();
    private ArrayList<HashMap<String, ArrayList<String>>> filterMapCollection = new ArrayList<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Determines if a log entry matches the defined filter.
     */
    @Override
    public boolean Filter(String logEntry) {
        Map<String, Object> parsedJsonMap;
        try {
            parsedJsonMap = mapper.readValue(logEntry, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse log entry: " + logEntry, e);
        }
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
        parsedTimeWindow.clear();
        filterMapCollection.clear();

        if (timeWindow != null) {
            for (String window : timeWindow) {
                String[] time = window.split(",");

                if (time.length != 2)
                    throw new RuntimeException("Time window provided invalid: " + window);

                Timestamp beginTime = Timestamp.valueOf(LocalDateTime.parse(time[0]));
                Timestamp endTime = Timestamp.valueOf(LocalDateTime.parse(time[1]));

                if (!parsedTimeWindow.containsKey(beginTime))
                    parsedTimeWindow.put(beginTime, new ArrayList<>());

                parsedTimeWindow.get(beginTime).add(endTime);
            }
        }

        if (filterExpressions != null) {
            for (String expression : filterExpressions) {
                String[] parsedExpression = expression.split(",");

                HashMap<String, ArrayList<String>> filterMap = new HashMap<>();

                for (String element : parsedExpression) {
                    String[] parsedMap = element.split("=");

                    if (parsedMap.length != 2)
                        throw new RuntimeException("Filter expression provided invalid: " + element);

                    if (!filterMap.containsKey(parsedMap[0]))
                        filterMap.put(parsedMap[0], new ArrayList<>());

                    filterMap.get(parsedMap[0]).add(parsedMap[1]);
                }

                filterMapCollection.add(filterMap);
            }
        }

        if (filterMapCollection.isEmpty() && parsedTimeWindow.isEmpty())
            throw new RuntimeException("No filter provided!");
    }


    /**
     * Check if the data matches defined filter expression
     * "message" defines keyword search
     * And-relation between filters
     * Or-relation within filters
     */
    private boolean CheckFilterExpression(String logEntry, Map<String, Object> parsedJsonMap) {
        for (HashMap<String, ArrayList<String>> filterMap : filterMapCollection) {
            boolean check = false;
            for (String key : filterMap.keySet()) {
                ArrayList<String> valArray = filterMap.get(key);
                for (String val : valArray) {
                    if (key.equals("message") && logEntry.contains(val)) {
                        check = true;
                        break;
                    }
                    if (!key.equals("message"))
                        if (parsedJsonMap.containsKey(key))
                            if (parsedJsonMap.get(key).toString().equals(val)) {
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
        if (parsedTimeWindow.isEmpty())
            return true;
        for (Map.Entry<Timestamp, ArrayList<Timestamp>> entry : parsedTimeWindow.entrySet()) {
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

    public ArrayList<HashMap<String, ArrayList<String>>> getFilterMapCollection() {
        return filterMapCollection;
    }

    public HashMap<Timestamp, ArrayList<Timestamp>> getParsedTimeWindow() {
        return parsedTimeWindow;
    }
}
