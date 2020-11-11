/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli;

import com.aws.greengrass.cli.commands.ComponentCommand;
import com.aws.greengrass.cli.commands.DeploymentCommand;
import com.aws.greengrass.cli.commands.TemplateCommand;
import com.aws.greengrass.cli.commands.Logs;
import com.aws.greengrass.cli.module.AdapterModule;
import com.aws.greengrass.cli.module.CommandsComponent;
import com.aws.greengrass.cli.module.DaggerCommandsComponent;
import com.aws.greengrass.ipc.services.cli.exceptions.GenericCliIpcServerException;
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
@Command(name = "cli",
        subcommands = {HelpCommand.class, ComponentCommand.class,
            TemplateCommand.class, DeploymentCommand.class, Logs.class},
        resourceBundle = "com.aws.greengrass.cli.CLI_messages")
public class CLI implements Runnable {

    @CommandLine.Option(names = "--ggcRootPath", description = "The path to the root directory of Greengrass")
    String ggcRootPath;

    @Spec
    CommandSpec spec;

    public static void main(String... args) {
        System.exit(new CLI().runCommand(args));
    }
    public int runCommand(String... args) {
        /* break out as seperate function for use in unit tests */
        int exitCode = 0;
        try {
            populateCommand(args);
            exitCode = new CommandLine(this, new CommandFactory(createCommandComponent()))
                    .setExecutionExceptionHandler((e, commandLine, parseResult) -> {
                        if (e instanceof CommandLine.UnmatchedArgumentException
                                || e instanceof CommandLine.MissingParameterException
                                || e instanceof RuntimeException // see NucleusAdapterIpcClientImpl:250
                                || e instanceof GenericCliIpcServerException) {
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
        return exitCode;
    }

    private void populateCommand(String[] args) {
        CommandLine parser = new CommandLine(this, new CommandFactory(DaggerCommandsComponent.builder()
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

