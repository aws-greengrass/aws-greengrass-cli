/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli;

import com.aws.iot.evergreen.cli.adapter.AdapterModule;
import com.aws.iot.evergreen.cli.commands.ComponentCommand;
import com.aws.iot.evergreen.cli.commands.Config;
import com.aws.iot.evergreen.cli.commands.Health;
import com.aws.iot.evergreen.cli.commands.Service;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.util.ResourceBundle;

/**
 * Main entry point into the command line.
 */
@Command(name = "cli",
        subcommands = {HelpCommand.class, Config.class, Health.class, Service.class, ComponentCommand.class},
        resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages")
public class CLI implements Runnable {
    @Option(names = "--host", defaultValue = "localhost")
    String host;
    @Option(names = "--port", defaultValue = "8081")
    Integer port;

    @Spec
    CommandSpec spec;

    public static void main(String... args) {
        int exitCode = new CommandLine(new CLI(), new GuiceFactory(new AdapterModule())).execute(args);
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
        String msg = ResourceBundle.getBundle("com.aws.iot.evergreen.cli.CLI_messages")
                .getString("exception.missing.command");
        throw new ParameterException(spec.commandLine(), msg);
    }


    static class GuiceFactory implements IFactory {
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
