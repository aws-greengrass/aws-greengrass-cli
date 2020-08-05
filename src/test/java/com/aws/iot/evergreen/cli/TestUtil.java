package com.aws.iot.evergreen.cli;

import com.aws.iot.evergreen.logging.impl.EvergreenStructuredLogMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.Getter;

import java.io.File;
import java.util.Map;

/* Helper class for unit tests. */
public class TestUtil {
    @Getter
    private static final ObjectReader mapper = new ObjectMapper().readerFor(Map.class);

    @Getter
    private static final ObjectReader evergreenStructuredLogReader = new ObjectMapper().readerFor(EvergreenStructuredLogMessage.class);

    public static void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }
}
