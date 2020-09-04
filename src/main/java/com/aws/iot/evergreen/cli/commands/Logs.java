/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.util.logs.Aggregation;
import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.LogEntry;
import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
import com.aws.iot.evergreen.cli.util.logs.Visualization;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import java.io.File;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

@Command(name = "logs", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages", subcommands = HelpCommand.class)
public class Logs extends BaseCommand {

    @Inject
    private Aggregation aggregation;
    @Inject
    private Filter filter;
    @Inject
    private Visualization visualization;

    @Command(name = "get")
    public int get(@CommandLine.Option(names = {"-lf", "--log-file"}, paramLabel = "Log File Path") String[] logFileArray,
                   @CommandLine.Option(names = {"-ld", "--log-dir"}, paramLabel = "Log Directory Path") String[] logDirArray,
                   @CommandLine.Option(names = {"-t", "--time-window"}, paramLabel = "Time Window") String[] timeWindow,
                   @CommandLine.Option(names = {"-f", "--filter"}, paramLabel = "Filter Expression") String[] filterExpressions,
                   @CommandLine.Option(names = {"-B", "--before"}, paramLabel = "Before", defaultValue = "0") int before,
                   @CommandLine.Option(names = {"-A", "--after"}, paramLabel = "After", defaultValue = "0") int after,
                   @CommandLine.Option(names = {"-F", "--follow"}, paramLabel = "Live Update Flag") boolean follow,
                   @CommandLine.Option(names = {"-N", "--no-color"}, paramLabel = "Remove color") boolean noColor,
                   @CommandLine.Option(names = {"-V", "--verbose"}, paramLabel = "Verbosity") boolean verbose,
                   @CommandLine.Option(names = {"-S", "--syslog"}, paramLabel = "Syslog") boolean syslog) {
        Runtime.getRuntime().addShutdownHook(new Thread(aggregation::close));
        LogsUtil.setSyslog(syslog);
        if (syslog && verbose) {
            LogsUtil.getErrorStream().println("Syslog does not support verbosity!");
        }
        filter.composeRule(timeWindow, filterExpressions);
        aggregation.configure(follow, filter, before, after);
        BlockingQueue<LogEntry> logQueue = aggregation.readLog(logFileArray, logDirArray);
        while (!logQueue.isEmpty() || aggregation.isAlive()) {
            try {
                //TODO: remove busy polling.
                LogEntry entry = logQueue.poll(10, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    visualization.visualize(entry, noColor, verbose);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Log tool polling interrupted! " + e.getMessage());
            }
        }
        return 0;
    }

    @Command(name = "list-log-files")
    public void list_log(@CommandLine.Option(names = {"--log-dir"}, paramLabel = "Log Directory Path")
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