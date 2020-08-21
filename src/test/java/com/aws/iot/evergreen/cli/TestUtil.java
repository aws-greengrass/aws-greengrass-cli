package com.aws.iot.evergreen.cli;

import com.aws.iot.evergreen.cli.util.logs.LogsUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

/* Helper class for unit tests. */
public class TestUtil {
    public static void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        if (!file.delete()) {
            throw new RuntimeException("Unable to delete file " + file.getPath());
        }
    }

    public static PrintStream createPrintStreamFromOutputStream(OutputStream outputStream) {
        try {
            return new PrintStream(outputStream, true, LogsUtil.DEFAULT_CHARSETS.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String byteArrayOutputStreamToString(ByteArrayOutputStream outputStream) {
        try {
            return outputStream.toString(LogsUtil.DEFAULT_CHARSETS.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
