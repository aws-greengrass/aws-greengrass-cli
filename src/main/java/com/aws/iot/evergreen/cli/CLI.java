/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */
package com.aws.iot.evergreen.cli;

import com.aws.iot.evergreen.cli.commands.Config;
import com.aws.iot.evergreen.cli.commands.Health;
import com.aws.iot.evergreen.cli.commands.Service;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;

import java.util.ResourceBundle;

/**
 * Main entry point into the command line.
 */
@Command(name = "cli", subcommands = {HelpCommand.class, Config.class, Health.class, Service.class}, resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages")
public class CLI implements Runnable {
    @Option(names = "--host", defaultValue = "localhost")
    String host;
    @Option(names = "--port", defaultValue = "8080")
    Integer port;

    @Spec
    CommandSpec spec;

    public static void main(String... args) {
        int exitCode = new CommandLine(new CLI()).execute(args);
        System.exit(exitCode);
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    @Override
    public void run() {
        String msg = ResourceBundle.getBundle("com.aws.iot.evergreen.cli.CLI_messages").getString("exception.missing.command");
        throw new ParameterException(spec.commandLine(), msg);
    }
}
