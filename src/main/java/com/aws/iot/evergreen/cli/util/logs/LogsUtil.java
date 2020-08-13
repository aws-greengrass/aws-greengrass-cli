package com.aws.iot.evergreen.cli.util.logs;

import com.aws.iot.evergreen.logging.impl.EvergreenStructuredLogMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.Getter;
import lombok.Setter;

import java.io.PrintStream;
import java.util.Map;

public class LogsUtil {
    @Setter
    @Getter
    private static PrintStream printStream = System.out;

    @Setter
    @Getter
    private static PrintStream errorStream = System.err;

    public static final ObjectReader MAP_READER = new ObjectMapper().readerFor(Map.class);

    public static final ObjectReader EVERGREEN_STRUCTURED_LOG_READER = new ObjectMapper()
            .readerFor(EvergreenStructuredLogMessage.class);
}
