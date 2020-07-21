package com.aws.iot.evergreen.cli.util.logs;

import lombok.Getter;
import lombok.Setter;

import java.io.PrintStream;

public class LogsUtil {
    @Setter
    @Getter
    private static PrintStream printStream = System.out;

    @Setter
    @Getter
    private static PrintStream errorStream = System.err;
}
