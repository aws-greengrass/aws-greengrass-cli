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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilterImplTest {

    private static final String[] timeWindow = new String[]{"2020-07-14,2020-07-14T01:00:00",
            "2020-07-14T02:00:00,2020-07-16T03:00:00"};
    private static final String[] emptyTimeWindow = new String[]{};
    private static final String[] emptyFilterExpression = new String[]{};

    private static final String[] emptyArgumentTimeWindow = new String[] {"2020-07-14T00:00:00", ",2020-07-14T00:00:00"};
    private static final String[] wrongTimeWindow1 = new String[]{"o2020-07-14T00:00:992020-07-14T01:00:99",
            "2020-07-14T02:00:00,2020-07-14T03:00:00"};
    private static final String[] wrongTimeWindow2 = new String[]{"2020-07-14T00:00:99,,2020-07-14T01:00:99",
            "2020-07-14T02:00:00,2020-07-14T03:00:00"};
    private static final String[] formatTimeWindow1 = new String[]{"2020-07-14T00:00:00", "2020-07-14T00:00:00000",
            "2020-07-14T00:00:00", "2020-07-14"};
    private static final String[] formatTimeWindow2 = new String[] {"12:34:00000", "12:34:00", "12:34"};
    private static final String[] offsetTimeWindow = new String[] {"-1days-2hours-3minutes-4seconds,+1d+2h+3m+4s",
            "-1day-28hr-6min-8sec", "-187567s,+1s"};
    private static final String[] badOffsetTimeWindow = new String[] {"+1.5days"};

    private static final LocalDateTime beginTime1 = LocalDateTime.parse("2020-07-14T00:00:00");
    private static final LocalDateTime endTime1 = LocalDateTime.parse("2020-07-14T01:00:00");

    private static final String goodTimeWindow = "2020-07-14T00:00:00,20200716";
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

        assertEquals(filter.getParsedTimeWindowMap().get(beginTime1), endTime1);
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
        Exception timeWindowException1 = assertThrows(RuntimeException.class,
                () -> filter.composeRule(wrongTimeWindow1, goodFilterExpression));
        assertThat(timeWindowException1.getMessage(), containsString("Cannot parse: " + wrongTimeWindow1[0]));

        Exception timeWindowException2 = assertThrows(RuntimeException.class,
                () -> filter.composeRule(wrongTimeWindow2, goodFilterExpression));
        assertThat(timeWindowException2.getMessage(), containsString("Time window provided invalid: " + wrongTimeWindow2[0]));


        Exception filterExpressionException = assertThrows(RuntimeException.class,
                () -> filter.composeRule(timeWindow, invalidFilterExpression));
        assertEquals("Filter expression provided invalid: " + invalidFilterExpression[0], filterExpressionException.getMessage());

    }

    @Test
    public void testTimeWindowEmptyArgument() {
        filter.composeRule(emptyArgumentTimeWindow, emptyFilterExpression);
        assertEquals(2, filter.getParsedTimeWindowMap().size());
        assertTrue(filter.getParsedTimeWindowMap().containsKey(beginTime1));
        assertTrue(filter.getParsedTimeWindowMap().containsValue(beginTime1));

    }

    @Test
    public void testTimeWindowMultipleFormat() {
        filter.composeRule(formatTimeWindow1, emptyFilterExpression);
        assertEquals(1, filter.getParsedTimeWindowMap().size());
        assertTrue(filter.getParsedTimeWindowMap().containsKey(LocalDateTime.parse("2020-07-14T00:00:00")));

        filter.composeRule(formatTimeWindow2, emptyFilterExpression);
        assertEquals(1, filter.getParsedTimeWindowMap().size());
    }

    @Test
    public void testTimeWindowOffset() {
        filter.composeRule(offsetTimeWindow, emptyFilterExpression);
        assertEquals(3, filter.getParsedTimeWindowMap().size());
        for (Map.Entry<LocalDateTime,LocalDateTime> entry : filter.getParsedTimeWindowMap().entrySet()) {
            // (86400*2+3600*4+60*6+8)*1000 = 187568000
            assertEquals(187568000, Duration.between(entry.getKey(), entry.getValue()).toMillis());
        }

        Exception offsetException = assertThrows(RuntimeException.class,
                () -> filter.composeRule(badOffsetTimeWindow, emptyFilterExpression));
        assertEquals("Cannot parse: " + badOffsetTimeWindow[0], offsetException.getMessage());
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