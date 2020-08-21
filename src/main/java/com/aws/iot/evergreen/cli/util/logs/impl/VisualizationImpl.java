/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.LogEntry;
import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
import com.aws.iot.evergreen.cli.util.logs.Visualization;
import com.aws.iot.evergreen.logging.impl.EvergreenStructuredLogMessage;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VisualizationImpl implements Visualization {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";

    /*
     * Display a log entry in text format
     */
    @Override
    public void visualize(LogEntry logEntry, boolean highlight, boolean verbose) {
        try {
            EvergreenStructuredLogMessage logMessage = LogsUtil.EVERGREEN_STRUCTURED_LOG_READER.readValue(logEntry.getLine());
            String message = verbose ? logMessage.getTextMessage() : abbreviate(logMessage);

            if (!highlight) {
                LogsUtil.getPrintStream().println(message);
                return;
            }
            LogsUtil.getPrintStream().println(highlight(message, logEntry.getMatchedKeywords()));
        } catch (IOException e) {
            LogsUtil.getErrorStream().println("Unable to parse EvergreenStructuredLogMessage: ");
            LogsUtil.getErrorStream().println(logEntry.getLine());
        }
    }

    /**
     * Find keywords in string and highlight them to be red.
     */
    private String highlight(String line, List<String> keywords) {
        for (String key : keywords) {
            line = line.replace(key, String.format("%s%s%s", ANSI_RED, key, ANSI_RESET));
        }
        return line;
    }

    /**
     * Get abbreviated formatted message including all fields but thread.
     * Abbreviate loggerName. Replace full stacktrace of cause to only its message.
     *
     * @return String
     */
    private String abbreviate(EvergreenStructuredLogMessage message) {
        String msg = String.format("%s [%s] %s: %s",
                new SimpleDateFormat("yyyy-MM-dd hh:mm:ss z").format(new Date(message.getTimestamp())),
                message.getLevel(),
                message.getLoggerName() != null ? abbreviateClassname(message.getLoggerName()) : null,
                //EvergreenStructuredLogMessage.getFormattedMessage()
                Stream.of(message.getEventType(), message.getMessage(), message.getContexts()).filter(Objects::nonNull)
                        .map(Object::toString).filter((x) -> !x.isEmpty()).collect(Collectors.joining(". ")));

        if (message.getCause() == null) {
            return msg;
        }
        return String.format("%s%n%sEXCEPTION: %s%s", msg, ANSI_RED, message.getCause().getMessage(), ANSI_RESET);
    }

    /**
     * Abbreviate the fully qualified name of a class only the last directory containing it and its own name.
     *
     * @param name the fully qualified name of class
     * @return String
     */
    private String abbreviateClassname(String name) {
        String[] splitArray = name.split("\\.");
        return splitArray.length < 2 ? name
                : splitArray[splitArray.length - 2] + "." + splitArray[splitArray.length - 1];
    }

}
