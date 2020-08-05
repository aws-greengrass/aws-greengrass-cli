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

    @Getter
    private static final ObjectReader mapper = new ObjectMapper().readerFor(Map.class);

    @Getter
    private static final ObjectReader evergreenStructuredLogReader = new ObjectMapper().readerFor(EvergreenStructuredLogMessage.class);
}
