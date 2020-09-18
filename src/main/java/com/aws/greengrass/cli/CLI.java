/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli;

import com.aws.greengrass.cli.adapter.AdapterModule;
import com.aws.greengrass.cli.commands.ComponentCommand;
import com.aws.greengrass.cli.commands.DeploymentCommand;
import com.aws.greengrass.cli.commands.Logs;
import com.aws.greengrass.cli.commands.Service;
import com.aws.greengrass.cli.util.logs.LogsModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.util.ResourceBundle;

/**
 * Main entry point into the command line.
 */
@Command(name = "cli",
        subcommands = {HelpCommand.class, Service.class, ComponentCommand.class, DeploymentCommand.class, Logs.class},
        resourceBundle = "com.aws.greengrass.cli.CLI_messages")
public class CLI implements Runnable {

    @CommandLine.Option(names = "--ggcRootPath")
    String ggcRootPath;

    @Spec
    CommandSpec spec;

    public static void main(String... args) {
        CLI cli = new CLI();
        CommandLine.populateCommand(cli, args);
        int exitCode = new CommandLine(cli, new GuiceFactory(new AdapterModule(cli.getGgcRootPath()), new LogsModule())).execute(args);
        System.exit(exitCode);
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


    public static class GuiceFactory implements IFactory {
        private final Injector injector;

        public GuiceFactory(Module... modules) {
            injector = Guice.createInjector(modules);
        }

        @Override
        public <K> K create(Class<K> aClass) throws Exception {
            try {
                return injector.getInstance(aClass);
            } catch (ConfigurationException ex) {
                return CommandLine.defaultFactory().create(aClass);
            }
        }
    }
}
