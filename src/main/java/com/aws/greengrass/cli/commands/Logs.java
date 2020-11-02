/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.util.logs.Aggregation;
import com.aws.greengrass.cli.util.logs.Filter;
import com.aws.greengrass.cli.util.logs.LogEntry;
import com.aws.greengrass.cli.util.logs.LogQueue;
import com.aws.greengrass.cli.util.logs.LogsUtil;
import com.aws.greengrass.cli.util.logs.Visualization;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import java.io.File;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import static com.aws.greengrass.cli.adapter.impl.NucleusAdapterIpcClientImpl.deTilde;

@Command(name = "logs", resourceBundle = "com.aws.greengrass.cli.CLI_messages", subcommands = HelpCommand.class)
public class Logs extends BaseCommand {

    private final Aggregation aggregation;
    private final Filter filter;
    private final Visualization visualization;

    @Inject
    public Logs(
            Aggregation aggregation,
            Filter filter,
            Visualization visualization
    ) {
        this.aggregation = aggregation;
        this.filter = filter;
        this.visualization = visualization;
    }

    @Command(name = "get")
    public int get(@CommandLine.Option(names = {"-lf", "--log-file"}, paramLabel = "Log File Path") String[] logFileArray,
                   @CommandLine.Option(names = {"-ld", "--log-dir"}, paramLabel = "Log Directory Path") String[] logDirArray,
                   @CommandLine.Option(names = {"-t", "--time-window"}, paramLabel = "Time Window") String[] timeWindow,
                   @CommandLine.Option(names = {"-f", "--filter"}, paramLabel = "Filter Expression") String[] filterExpressions,
                   @CommandLine.Option(names = {"-b", "--before"}, paramLabel = "Before", defaultValue = "0") int before,
                   @CommandLine.Option(names = {"-a", "--after"}, paramLabel = "After", defaultValue = "0") int after,
                   @CommandLine.Option(names = {"-m", "--max-log-queue-size"}, paramLabel = "Max Number Of Log Entries",
                           defaultValue = "100") int max,
                   @CommandLine.Option(names = {"-fol", "--follow"}, paramLabel = "Live Update Flag") boolean follow,
                   @CommandLine.Option(names = {"-nc", "--no-color"}, paramLabel = "Remove Color") boolean noColor,
                   @CommandLine.Option(names = {"-v", "--verbose"}, paramLabel = "Verbosity") boolean verbose,
                   @CommandLine.Option(names = {"-s", "--syslog"}, paramLabel = "Syslog Flag") boolean syslog) {
        Runtime.getRuntime().addShutdownHook(new Thread(aggregation::close));
        LogsUtil.setSyslog(syslog);
        if (syslog && verbose) {
            LogsUtil.getErrorStream().println("Syslog does not support verbosity!");
        }
        logFileArray = deTildeArray(logFileArray);
        logDirArray = deTildeArray(logDirArray);
        filter.composeRule(timeWindow, filterExpressions);
        aggregation.configure(follow, filter, before, after, max);
        LogQueue logQueue = aggregation.readLog(logFileArray, logDirArray);
        while (!logQueue.isEmpty() || aggregation.isAlive()) {
            try {
                // GG_NEEDS_REVIEW: TODO: remove busy polling.
                LogEntry entry = logQueue.poll(10, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    visualization.visualize(entry, noColor, verbose);
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        return 0;
    }

    private String[] deTildeArray(String[] arr) {
        if (arr == null) {
            return arr;
        }
        for (int i = 0; i < arr.length; i++) {
            String s = arr[i];
            arr[i] = deTilde(s);
        }
        return arr;
    }

    @Command(name = "list-log-files")
    public void list_log(@CommandLine.Option(names = {"-ld", "--log-dir"}, paramLabel = "Log Directory Path")
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

    @Command(name = "list-keywords")
    public void list_keywords(@CommandLine.Option(names = {"-s", "--syslog"}, paramLabel = "Syslog Flag") boolean syslog) {
        if (syslog) {
            LogsUtil.getPrintStream().println(new StringBuilder("Here is a list of suggested keywords for syslog: ")
                    .append(System.lineSeparator()).append("priority=$int").append(System.lineSeparator())
                    .append("host=$str").append(System.lineSeparator()).append("logger=$str")
                    .append(System.lineSeparator()).append("class=$str").toString());
            return;
        }
        LogsUtil.getPrintStream().println(new StringBuilder("Here is a list of suggested keywords for Greengrass log: ")
                .append(System.lineSeparator()).append("level=$str").append(System.lineSeparator())
                .append("thread=$str").append(System.lineSeparator()).append("loggerName=$str")
                .append(System.lineSeparator()).append("eventType=$str").append(System.lineSeparator())
                .append("serviceName=$str").append(System.lineSeparator()).append("error=$str").toString());

    }
}
