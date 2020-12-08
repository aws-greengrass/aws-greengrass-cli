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

@Command(name = "logs", resourceBundle = "com.aws.greengrass.cli.CLI_messages", subcommands = HelpCommand.class,
        mixinStandardHelpOptions = true, versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
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

    @Command(name = "get", mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public int get(@CommandLine.Option(names = {"-lf", "--log-file"}, paramLabel = "Log file") String[] logFileArray,
                   @CommandLine.Option(names = {"-ld", "--log-dir"}, paramLabel = "Log directory") String[] logDirArray,
                   @CommandLine.Option(names = {"-t", "--time-window"}, paramLabel = "Time window") String[] timeWindow,
                   @CommandLine.Option(names = {"-f", "--filter"}, paramLabel = "Filter expression") String[] filterExpressions,
                   @CommandLine.Option(names = {"-b", "--before"}, paramLabel = "Show N lines preceding matched line",
                           defaultValue = "0") int before,
                   @CommandLine.Option(names = {"-a", "--after"}, paramLabel = "Show N lines following matched line",
                           defaultValue = "0") int after,
                   @CommandLine.Option(names = {"-m", "--max-log-queue-size"}, paramLabel = "Max log entries",
                           defaultValue = "100") int max,
                   @CommandLine.Option(names = {"-fol", "--follow"}, paramLabel = "Follow live updates") boolean follow,
                   @CommandLine.Option(names = {"-nc", "--no-color"}, paramLabel = "Output without any colors") boolean noColor,
                   @CommandLine.Option(names = {"-v", "--verbose"}, paramLabel = "Use verbose logging") boolean verbose,
                   @CommandLine.Option(names = {"-s", "--syslog"}, paramLabel = "Use syslog format") boolean syslog) {
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

    @Command(name = "list-log-files", mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public void listLogFiles(@CommandLine.Option(names = {"-ld", "--log-dir"}, paramLabel = "Log directory")
                                 String[] logDir) {
        Set<File> logFileSet = aggregation.listLog(logDir);
        if (!logFileSet.isEmpty()) {
            for (File file : logFileSet) {
                LogsUtil.getPrintStream().println(file.getPath());
            }
            LogsUtil.getPrintStream().format("Total %d files found.%n", logFileSet.size());
            return;
        }
        LogsUtil.getPrintStream().println("No log file found.");
    }

    @Command(name = "list-keywords", mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public void listKeywords(@CommandLine.Option(names = {"-s", "--syslog"}, paramLabel = "Use syslog format") boolean syslog) {
        if (syslog) {
            LogsUtil.getPrintStream().println(new StringBuilder("Suggested keywords for syslog format:")
                    .append(System.lineSeparator()).append("priority=$int").append(System.lineSeparator())
                    .append("host=$str").append(System.lineSeparator()).append("logger=$str")
                    .append(System.lineSeparator()).append("class=$str").toString());
            return;
        }
        LogsUtil.getPrintStream().println(new StringBuilder("Suggested keywords for Greengrass log format:")
                .append(System.lineSeparator()).append("level=$str").append(System.lineSeparator())
                .append("thread=$str").append(System.lineSeparator()).append("loggerName=$str")
                .append(System.lineSeparator()).append("eventType=$str").append(System.lineSeparator())
                .append("serviceName=$str").append(System.lineSeparator()).append("error=$str").toString());

    }
}
