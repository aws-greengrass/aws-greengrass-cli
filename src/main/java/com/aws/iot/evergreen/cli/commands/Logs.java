/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.util.logs.Aggregation;
import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
import com.aws.iot.evergreen.cli.util.logs.Visualization;
import com.aws.iot.evergreen.logging.impl.EvergreenStructuredLogMessage;
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
        filter.composeRule(timeWindow, filterExpressions);
        BlockingQueue<LogsUtil.LogEntry> logQueue = aggregation.readLog(logFile, logDir);

        while (!logQueue.isEmpty() || aggregation.isAlive()) {
            LogsUtil.LogEntry entry = logQueue.poll();
            if (entry != null && !entry.getLine().isEmpty()) {
                if (filter.filter(entry.getLine(), entry.getMap())) {
                    //TODO: Expand LogEntry class and use it for visualization
                    LogsUtil.getPrintStream().println(visualization.visualize(LogsUtil.getMapper()
                            .readValue(entry.getLine(), EvergreenStructuredLogMessage.class)));
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