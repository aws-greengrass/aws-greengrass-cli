package com.aws.iot.evergreen.cli;

import java.io.File;

/* Helper class for unit tests. */
public class TestUtil {
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
