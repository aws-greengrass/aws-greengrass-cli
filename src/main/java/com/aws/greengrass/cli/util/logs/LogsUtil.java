package com.aws.greengrass.cli.util.logs;

import com.aws.greengrass.logging.impl.GreengrassLogMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.Getter;
import lombok.Setter;

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class LogsUtil {
    @Setter
    @Getter
    private static PrintStream printStream = System.out;

    @Setter
    @Getter
    private static PrintStream errorStream = System.err;

    @Setter
    @Getter
    private static boolean syslog = false;
    public static final ObjectReader MAP_READER = new ObjectMapper().readerFor(Map.class);

    public static final ObjectReader STRUCTURED_LOG_READER = new ObjectMapper()
            .readerFor(GreengrassLogMessage.class);

    public static final Charset DEFAULT_CHARSETS = StandardCharsets.UTF_8;
}
