/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

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

    @Setter
    private PrintStream printStream = new PrintStream(System.out);

    @Setter
    private PrintStream errorStream = new PrintStream(System.err);

    @Command(name = "get")
    public int get(@CommandLine.Option(names = {"--log-file"}, paramLabel = "Log File Paths") String[] logFile,
                   @CommandLine.Option(names = {"--log-dir"}, paramLabel = "Log Directory Paths") String[] logDir,
                   @CommandLine.Option(names = {"--time-window"}, paramLabel = "Time Window") String[] timeWindow,
                   @CommandLine.Option(names = {"--filter"}, paramLabel = "Filter Expression")
                               String[] filterExpressions) throws IOException {
        filter.composeRule(timeWindow, filterExpressions);
        List<BufferedReader> logReaderList = aggregation.readLog(logFile, logDir);
        for (BufferedReader reader : logReaderList) {
            String line = "";
            try {
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        errorStream.println("Empty line from " + reader.toString());
                        continue;
                    }
                    if (filter.filter(line, mapper.readValue(line, Map.class))) {
                        printStream.println(visualization.visualize(mapper
                                .readValue(line, EvergreenStructuredLogMessage.class)));
                    }
                }
            } catch (IOException e) {
                errorStream.println("Failed to serialize: " + line);
                errorStream.println(e.getMessage());
            } finally {
                reader.close();
            }
        }
        return 0;
    }

    @Command(name = "list-log-files")
    public int list_log(@CommandLine.Option(names = {"--log-dir"}, paramLabel = "Log Directory Paths")
                                    String[] logDir) {
        List<File> logFilePathList = aggregation.listLog(logDir);
        if (!logFilePathList.isEmpty()) {
            for (File file : logFilePathList) {
                printStream.println(file.getPath());
            }
            printStream.format("Total %d files found.", logFilePathList.size());
            printStream.close();
            return 0;
        }
        printStream.println("No log file found.");
        return 0;
    }
}