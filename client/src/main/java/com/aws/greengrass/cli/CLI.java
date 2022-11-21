/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli;

import com.aws.greengrass.cli.commands.ComponentCommand;
import com.aws.greengrass.cli.commands.DeploymentCommand;
import com.aws.greengrass.cli.commands.Logs;
import com.aws.greengrass.cli.commands.PasswordCommand;
import com.aws.greengrass.cli.commands.TopicCommand;
import com.aws.greengrass.cli.module.AdapterModule;
import com.aws.greengrass.cli.module.CommandsComponent;
import com.aws.greengrass.cli.module.DaggerCommandsComponent;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.util.ResourceBundle;

/**
 * Main entry point into the command line.
 */
@Command(name = "greengrass-cli",
        mixinStandardHelpOptions = true,
        subcommands = {HelpCommand.class, ComponentCommand.class, DeploymentCommand.class, Logs.class,
                PasswordCommand.class, TopicCommand.class},
        resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
public class CLI implements Runnable {

    @CommandLine.Option(names = "--ggcRootPath", description = "The path to the root directory of Greengrass")
    String ggcRootPath;

    @Spec
    CommandSpec spec;

    public static void main(String... args) {
        CLI cli = new CLI();
        int exitCode = 0;
        try {
            populateCommand(cli, args);
            exitCode = new CommandLine(cli, new CommandFactory(cli.createCommandComponent()))
                    .setExecutionExceptionHandler((e, commandLine, parseResult) -> {
                        if (e instanceof CommandLine.UnmatchedArgumentException
                                || e instanceof CommandLine.MissingParameterException) {
                            System.out.println(commandLine.getColorScheme().errorText(e.getMessage()));
                            commandLine.usage(System.out);
                            return 0;
                        } else {
                            throw e;
                        }
                    })
                    .execute(args);

        } catch (ParameterException e) {
            CommandLine.defaultExceptionHandler().handleParseException(e, args);
        }
        System.exit(exitCode);
    }

    private static void populateCommand(CLI cli, String[] args) {
        CommandLine parser = new CommandLine(cli, new CommandFactory(DaggerCommandsComponent.builder()
                .adapterModule(new AdapterModule(null))
                .build()));
        parser.parseArgs(args);
    }

    private CommandsComponent createCommandComponent() {
        return DaggerCommandsComponent.builder()
                .adapterModule(new AdapterModule(getGgcRootPath()))
                .build();
    }

    public String getGgcRootPath() {
        return ggcRootPath;
    }

    @Override
    public void run() {
        String msg = ResourceBundle.getBundle("com.aws.greengrass.cli.CLI_messages")
                .getString("exception.missing.command");
        throw new ParameterException(spec.commandLine(), msg);
    }
}

