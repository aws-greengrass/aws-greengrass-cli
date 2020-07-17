// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.util.logs.Aggregation;
import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.Visualization;
import com.aws.iot.evergreen.logging.impl.EvergreenStructuredLogMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;


@Command(name = "logs", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages", subcommands = HelpCommand.class)
public class Logs extends BaseCommand {
    private static final ObjectMapper mapper = new ObjectMapper();
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
                   @CommandLine.Option(names = {"--filter"}, paramLabel = "Filter Expression") String[] filterExpressions
    ) throws IOException {
        PrintWriter writer = new PrintWriter(System.out);
        List<BufferedReader> logReaderList = aggregation.ReadLog(logFile, logDir);
        filter.ComposeRule(timeWindow, filterExpressions);
        for (BufferedReader reader : logReaderList) {
            String line = "";
            try {
                while ((line = reader.readLine()) != null) {
                    if (filter.Filter(line, mapper.readValue(line, Map.class))) {
                        writer.println(visualization.Visualize(mapper.readValue(line, EvergreenStructuredLogMessage.class)));
                    }
                }
            } catch (IOException e) {
                CleanUp(logReaderList, writer);
                if (line.isEmpty()) {
                    throw new RuntimeException("readLine() failed.", e);
                }
                /*
                 * TODO: Separate into different error scenarios.
                 * https://github.com/aws/aws-greengrass-cli/pull/14/files#r456012462
                 * https://github.com/aws/aws-greengrass-cli/pull/14/files#r456015467
                 */
                throw new RuntimeException("Failed to serialize: " + line, e);
            }
        }
        CleanUp(logReaderList, writer);
        return 0;
    }

    @Command(name = "list-log-files")
    public int list_log(@CommandLine.Option(names = {"--log-dir"}, paramLabel = "Log Directory Paths") String[] logDir) {
        List<Path> logFilePathList = aggregation.ListLog(logDir);

        if (!logFilePathList.isEmpty()) {
            for (Path file : logFilePathList) {
                System.out.println(file);
            }
            System.out.format("Total %d files found.", logFilePathList.size());
            return 0;
        }

        System.out.print("No log file found.");
        return 0;
    }

    private void CleanUp(List<BufferedReader> logReaderList, PrintWriter writer) throws IOException {
        for (BufferedReader reader : logReaderList) {
            reader.close();
        }
        writer.close();
    }
}