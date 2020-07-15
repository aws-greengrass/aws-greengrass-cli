/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.util.logs.Aggregation;
import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.Visualization;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;


@Command(name = "logs", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages", subcommands = HelpCommand.class)
public class Logs extends BaseCommand {
    @Inject
    Aggregation aggregation;
    @Inject
    Filter filter;
    @Inject
    Visualization visualization;

    @Command(name = "get")
    public int get(@CommandLine.Option(names = {"--log-file"}, paramLabel = "Log File Paths") String[] logFile,
                   @CommandLine.Option(names = {"--log-dir"}, paramLabel = "Log Directory Paths") String[] logDir,
                   @CommandLine.Option(names = {"--time-window"}, paramLabel = "Time Window") String[] timeWindow,
                   @CommandLine.Option(names = {"--filter"}, paramLabel = "Filter Expression") String[] filterExpressions
    ) {
        ArrayList<BufferedReader> logReader = aggregation.ReadLog(logFile, logDir);
        filter.ComposeRule(timeWindow, filterExpressions);
        for (BufferedReader reader : logReader) {
            String line;
            try {
                while ((line = reader.readLine()) != null)
                    if (filter.Filter(line))
                        System.out.println(visualization.Visualize(line));
            } catch (IOException e) {
                throw new RuntimeException("Readline() failed ", e);
            }

        }
        return 0;
    }

    @Command(name = "list-log-files")
    public int list_log(String[] logDir) {
        ArrayList<Path> logFilePath = aggregation.ListLog(logDir);
        if (!logFilePath.isEmpty()) {
            int fileCount = 0;
            for (Path file : logFilePath) {
                System.out.println(file.toAbsolutePath().toString());
                fileCount++;
            }
            System.out.format("Total %d files found.", fileCount);
        } else {
            System.out.print("No log file found.");
        }

        return 0;
    }

    // setters created only for unit tests
    void setAggregation(Aggregation aggregation) {
        this.aggregation = aggregation;
    }

    void setFilter(Filter filter) {
        this.filter = filter;
    }

    void setVisualization(Visualization visualization) {
        this.visualization = visualization;
    }
}





