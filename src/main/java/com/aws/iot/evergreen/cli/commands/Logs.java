/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.util.logs.Aggregation;
import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.LogEntry;
import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
import com.aws.iot.evergreen.cli.util.logs.Visualization;
import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import javax.inject.Inject;

@Command(name = "logs", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages", subcommands = HelpCommand.class)
public class Logs extends BaseCommand {

    // setters created only for unit tests
    @Inject
    @Setter
    @Getter
    private Aggregation aggregation;
    // setters created only for unit tests
    @Inject
    @Setter
    private Filter filter;
    // setters created only for unit tests
    @Inject
    @Setter
    private Visualization visualization;

    @Command(name = "get")
    public int get(@CommandLine.Option(names = {"--log-file"}, paramLabel = "Log File Paths") String[] logFile,
                   @CommandLine.Option(names = {"--log-dir"}, paramLabel = "Log Directory Paths") String[] logDir,
                   @CommandLine.Option(names = {"--time-window"}, paramLabel = "Time Window") String[] timeWindow,
                   @CommandLine.Option(names = {"--filter"}, paramLabel = "Filter Expression")
                           String[] filterExpressions) throws IOException {
        Runtime.getRuntime().addShutdownHook(new Thread(aggregation::close));
        filter.composeRule(timeWindow, filterExpressions);
        BlockingQueue<LogEntry> logQueue = aggregation.readLog(logFile, logDir);

        while (!logQueue.isEmpty() || aggregation.isAlive()) {
            LogEntry entry = logQueue.poll();
            if (entry != null) {
                try {
                    if (!entry.getLine().isEmpty() && filter.filter(entry)) {
                        //TODO: Expand LogEntry class and use it for visualization
                        LogsUtil.getPrintStream().println(visualization.visualize(LogsUtil.getEvergreenStructuredLogReader()
                                .readValue(entry.getLine())));
                    }
                } finally {
                    entry.setVisualizeFinished(true);
                }
            }
        }
        return 0;
    }

    @Command(name = "list-log-files")
    public void list_log(@CommandLine.Option(names = {"--log-dir"}, paramLabel = "Log Directory Paths")
                                 String[] logDir) {
        Set<File> logFileSet = aggregation.listLog(logDir);
        if (!logFileSet.isEmpty()) {
            for (File file : logFileSet) {
                LogsUtil.getPrintStream().println(file.getPath());
            }
            LogsUtil.getPrintStream().format("Total %d files found.", logFileSet.size());
            return;
        }
        LogsUtil.getPrintStream().println("No log file found.");
    }
}