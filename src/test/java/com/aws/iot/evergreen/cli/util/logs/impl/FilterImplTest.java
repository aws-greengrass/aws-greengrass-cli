// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.TestUtil;
import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilterImplTest {

    private static final String[] timeWindow = new String[]{"2020-07-14T00:00:00,2020-07-14T01:00:00",
            "2020-07-14T02:00:00,2020-07-16T03:00:00"};

    private static final String[] emptyTimeWindow = new String[]{};
    private static final String[] emptyFilterExpression = new String[]{};

    private static final String[] wrongTimeWindow = new String[]{"2020-07-14T00:00:992020-07-14T01:00:99",
            "2020-07-14T02:00:00,2020-07-14T03:00:00"};

    private static final Timestamp beginTime1 = Timestamp.valueOf(LocalDateTime.parse("2020-07-14T00:00:00"));
    private static final Timestamp endTime1 = Timestamp.valueOf(LocalDateTime.parse("2020-07-14T01:00:00"));

    private static final String goodTimeWindow = "2020-07-14T00:00:00,2020-07-16T12:00:00";
    private static final String badTimeWindow1 = "2020-07-14T00:00:00,2020-07-14T12:00:00";
    private static final String badTimeWindow2 = "2020-07-16T12:00:00,2020-07-16T12:00:00";


    private static final String[] goodFilterExpression = {"level=DEBUG", "thread=idle-connection-reaper", "60000", "60*"};
    private static final String[] orFilterExpression = {"level=INFO,level=DEBUG,level=TRACE", "a=b,thread=x,thread=idle-connection-reaper",
            "70000,60000", "70*,60*"};
    private static final String[] falseFilterExpression = {"level=INFO", "60000", "eventType=null"};
    private static final String[] invalidFilterExpression = {"level=WARN=INFO", "60000", "eventType=null"};
    private static final String[] invalidLogLevelFilterExpression = {"level=WARNING", "60000", "eventType=null"};

    private static final String logEntry = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";

    private static final String logEntryBadLevel = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUGING\","
            + "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\","
            + "\"timestamp\":1594836028088,\"cause\":null}";

    private FilterImpl filter;
    private ByteArrayOutputStream errOutputStream;
    private PrintStream errorStream;

    @BeforeEach
    void init() {
        filter = new FilterImpl();
        errOutputStream = new ByteArrayOutputStream();
        errorStream = new PrintStream(errOutputStream);
        LogsUtil.setErrorStream(errorStream);
    }

    @Test
    public void testComposeRuleHappyCase() {
        //Testing regex, log level, key-val pair, time window.
        filter.composeRule(timeWindow, goodFilterExpression);
        assertEquals(4, filter.getFilterEntryCollection().size());
        assertEquals(2, filter.getParsedTimeWindowMap().size());
        assertEquals(1, filter.getFilterEntryCollection().get(1).getFilterMap().size()
                & filter.getFilterEntryCollection().get(2).getRegexList().size()
                & filter.getFilterEntryCollection().get(3).getRegexList().size());

        assertTrue(filter.getParsedTimeWindowMap().get(beginTime1).equals(endTime1));
        assertEquals("DEBUG", filter.getFilterEntryCollection().get(0).getLogLevel().toString());
        assertTrue(filter.getFilterEntryCollection().get(1).getFilterMap().get("thread").contains("idle-connection-reaper"));
        assertEquals("60000", filter.getFilterEntryCollection().get(2).getRegexList().get(0).toString());
        assertEquals("60*", filter.getFilterEntryCollection().get(3).getRegexList().get(0).toString());
    }

    @Test
    public void testComposeRuleEmptyInput() {
        filter.composeRule(emptyTimeWindow, goodFilterExpression);
        assertEquals(4, filter.getFilterEntryCollection().size());
        assertEquals(0, filter.getParsedTimeWindowMap().size());

        filter.composeRule(timeWindow, emptyFilterExpression);
        assertEquals(0, filter.getFilterEntryCollection().size());
        assertEquals(2, filter.getParsedTimeWindowMap().size());

        filter.composeRule(emptyTimeWindow, emptyFilterExpression);
        assertEquals(0, filter.getFilterEntryCollection().size());
        assertEquals(0, filter.getParsedTimeWindowMap().size());
    }

    @Test
    public void testComposeRuleInvalidInput() {
        Exception timeWindowException = assertThrows(RuntimeException.class,
                () -> filter.composeRule(wrongTimeWindow, goodFilterExpression));
        assertEquals("Time window provided invalid: " + wrongTimeWindow[0], timeWindowException.getMessage());

        Exception filterExpressionException = assertThrows(RuntimeException.class,
                () -> filter.composeRule(timeWindow, invalidFilterExpression));
        assertEquals("Filter expression provided invalid: " + invalidFilterExpression[0], filterExpressionException.getMessage());

    }

    @Test
    public void testComposeRuleInvalidLogLevel() {
        filter.composeRule(null, invalidLogLevelFilterExpression);
        assertTrue(filter.getFilterEntryCollection().get(0).getFilterMap().get("level").contains("WARNING"));
        assertThat(errOutputStream.toString(), containsString("Invalid log level: WARNING"));
    }

    @Test
    public void testFilterHappyCase() throws JsonProcessingException {
        Map<String, Object> parsedJsonMap = TestUtil.getMapper().readValue(logEntry, Map.class);

        String[] timeWindow1 = {badTimeWindow1, badTimeWindow2, goodTimeWindow};
        filter.composeRule(timeWindow1, goodFilterExpression);
        assertTrue(filter.filter(logEntry, parsedJsonMap));

        filter.composeRule(timeWindow1, orFilterExpression);
        assertTrue(filter.filter(logEntry, parsedJsonMap));

        String[] timeWindow2 = {badTimeWindow1};
        filter.composeRule(timeWindow2, goodFilterExpression);
        assertFalse(filter.filter(logEntry, parsedJsonMap));

        filter.composeRule(timeWindow1, falseFilterExpression);
        assertFalse(filter.filter(logEntry, parsedJsonMap));
    }

    @Test
    public void testFilterEmptyCase() throws JsonProcessingException {
        Map<String, Object> parsedJsonMap = TestUtil.getMapper().readValue(logEntry, Map.class);
        filter.composeRule(emptyTimeWindow, emptyFilterExpression);
        assertTrue(filter.filter(logEntry, parsedJsonMap));

        filter.composeRule(null, null);
        assertTrue(filter.filter(logEntry, parsedJsonMap));
    }

    @Test
    public void testFilterInvalidLogLevel() throws JsonProcessingException {
        Map<String, Object> parsedJsonMap = TestUtil.getMapper().readValue(logEntryBadLevel, Map.class);
        String[] timeWindow1 = {goodTimeWindow, badTimeWindow1};
        filter.composeRule(timeWindow1, goodFilterExpression);
        assertFalse(filter.filter(logEntry, parsedJsonMap));
        assertThat(errOutputStream.toString(), containsString("Invalid log level from: "));

    }

    @AfterEach
    void cleanup() {
        errorStream.close();
    }

}