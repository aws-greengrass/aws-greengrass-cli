/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
public class EvergreenStructuredLogMessage {
    public String thread;
    public String level;
    public String eventType;
    public String message;
    public Map<String, String> contexts;
    public String loggerName;
    public long timestamp;
    public Throwable cause;

    public EvergreenStructuredLogMessage(String loggerName, String level, String eventType, String msg,
                                         Map<String, String> context, Throwable cause) {
        this.level = level;
        this.message = msg;
        this.contexts = context;
        this.eventType = eventType;
        this.loggerName = loggerName;
        this.timestamp = Instant.now().toEpochMilli();
        this.cause = cause;
        this.thread = Thread.currentThread().getName();
    }

    @JsonIgnore
    public String getTextMessage() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy MMM dd hh:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String msg = String.format("%s [%s] (%s) %s: %s",
                dateFormat.format(new Date(timestamp)),
                level, thread,
                loggerName,
                getFormattedMessage());
        return msg;
    }

    @JsonIgnore
    private String getFormattedMessage() {
        return Stream.of(eventType, message, contexts).filter(Objects::nonNull).map(Object::toString)
                .filter((x) -> !x.isEmpty()).collect(Collectors.joining(". "));
    }

}