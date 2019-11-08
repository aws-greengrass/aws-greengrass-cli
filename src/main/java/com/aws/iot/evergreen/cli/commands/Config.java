/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */
package com.aws.iot.evergreen.cli.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

import java.util.ResourceBundle;

/**
 * Command for retrieving and modifying kernel configuration.
 */
@Command(name = "config", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages", subcommands = HelpCommand.class)
public class Config extends BaseCommand {
    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    @Command(name = "get")
    public int get(@Option(names = {"-p", "--path"}, paramLabel = "path", descriptionKey = "path", required = true) String path) {
        // TODO: connect to Evergreen and retrieve config key
        return 0;
    }

    @Command(name = "set")
    public int set(@Option(names = {"-p", "--path"}, paramLabel = "path", descriptionKey = "path", required = true) String path, @Option(names = {"-v", "--value"}, paramLabel = "value", required = true) String value) {
        // TODO: connect to Evergreen and set config key to a value
        return 0;
    }

    @Override
    public void run() {
        String msg = ResourceBundle.getBundle("com.aws.iot.evergreen.cli.CLI_messages").getString("exception.missing.command");
        throw new ParameterException(spec.commandLine(), msg);
    }
}

