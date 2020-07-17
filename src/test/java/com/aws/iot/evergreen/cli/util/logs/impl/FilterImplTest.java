// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.TestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilterImplTest {

    private static final String[] timeWindow = new String[]{"2020-07-14T00:00:00,2020-07-14T01:00:00",
            "2020-07-14T02:00:00,2020-07-16T03:00:00"};
    private static final String[] filterExpression = new String[]{"level=DEBUG,level=INFO", "thread=main-lifecycle"};

    private static final String[] emptyTimeWindow = new String[]{};
    private static final String[] emptyFilterExpression = new String[]{};

    private static final String[] wrongTimeWindow = new String[]{"2020-07-14T00:00:992020-07-14T01:00:99",
            "2020-07-14T02:00:00,2020-07-14T03:00:00"};
    private static final String[] wrongFilterExpression = new String[]{"levelDEBUG,levelINFO", "threadmain-lifecycle"};

    private static final Timestamp beginTime1 = Timestamp.valueOf(LocalDateTime.parse("2020-07-14T00:00:00"));
    private static final Timestamp endTime1 = Timestamp.valueOf(LocalDateTime.parse("2020-07-14T01:00:00"));

    private static final String goodTimeWindow = "2020-07-14T00:00:00,2020-07-16T12:00:00";
    private static final String badTimeWindow = "2020-07-14T00:00:00,2020-07-14T12:00:00";

    private static final String[] goodFilterExpression = {"level=DEBUG,thread=dummy", "KEYWORD=60000", "eventType=null"};
    private static final String[] badFilterExpression = {"level=INFO", "KEYWORD=60000", "eventType=null"};

    private static final String logEntry = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";
    private static final String invalidLogEntry = "{\"thread-idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";

    @Test
    public void testComposeRuleHappyCase() {

        FilterImpl filter = new FilterImpl();

        filter.composeRule(timeWindow, filterExpression);
        assertEquals(2, filter.getFilterMapCollection().size());
        assertEquals(2, filter.getParsedTimeWindowMap().size());

        assertTrue(filter.getParsedTimeWindowMap().get(beginTime1).equals(endTime1));
        assertTrue(filter.getFilterMapCollection().get(0).get("level").contains("DEBUG"));
        assertTrue(filter.getFilterMapCollection().get(0).get("level").contains("INFO"));
    }

    @Test
    public void testComposeRuleEmptyInput() {

        FilterImpl filter1 = new FilterImpl();
        filter1.composeRule(emptyTimeWindow, filterExpression);
        assertEquals(2, filter1.getFilterMapCollection().size());
        assertEquals(0, filter1.getParsedTimeWindowMap().size());

        FilterImpl filter2 = new FilterImpl();
        filter2.composeRule(timeWindow, emptyFilterExpression);
        assertEquals(0, filter2.getFilterMapCollection().size());
        assertEquals(2, filter2.getParsedTimeWindowMap().size());

        FilterImpl filter3 = new FilterImpl();
        filter3.composeRule(emptyTimeWindow, emptyFilterExpression);
        assertEquals(0, filter3.getFilterMapCollection().size());
        assertEquals(0, filter3.getParsedTimeWindowMap().size());
    }

    @Test
    public void testComposeRuleInvalidInput() {

        FilterImpl filter = new FilterImpl();

        Exception timeWindowException = assertThrows(RuntimeException.class,
                () -> filter.composeRule(wrongTimeWindow, filterExpression));
        assertEquals("Time window provided invalid: " + wrongTimeWindow[0], timeWindowException.getMessage());

        Exception filterExpressionException = assertThrows(RuntimeException.class,
                () -> filter.composeRule(timeWindow, wrongFilterExpression));
        assertEquals("Filter expression provided invalid: " + "levelDEBUG", filterExpressionException.getMessage());
    }

    @Test
    public void testFilterHappyCase() throws JsonProcessingException {
        FilterImpl filter = new FilterImpl();
        Map<String, Object> parsedJsonMap = TestUtil.mapper.readValue(logEntry, Map.class);

        String[] timeWindow1 = {goodTimeWindow, badTimeWindow};
        filter.composeRule(timeWindow1, goodFilterExpression);
        assertTrue(filter.filter(logEntry, parsedJsonMap));

        String[] timeWindow2 = {badTimeWindow};
        filter.composeRule(timeWindow2, goodFilterExpression);
        assertFalse(filter.filter(logEntry, parsedJsonMap));

        filter.composeRule(timeWindow1, badFilterExpression);
        assertFalse(filter.filter(logEntry, parsedJsonMap));
    }

    @Test
    public void testFilterEmptyCase() throws JsonProcessingException {
        FilterImpl filter = new FilterImpl();
        Map<String, Object> parsedJsonMap = TestUtil.mapper.readValue(logEntry, Map.class);
        filter.composeRule(emptyTimeWindow, emptyFilterExpression);
        assertTrue(filter.filter(logEntry, parsedJsonMap));
    }

}