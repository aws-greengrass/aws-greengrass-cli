package com.aws.iot.evergreen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.io.File;

/* Helper class for unit tests. */
public class TestUtil {
    @Getter
    private static final ObjectMapper mapper = new ObjectMapper();

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
