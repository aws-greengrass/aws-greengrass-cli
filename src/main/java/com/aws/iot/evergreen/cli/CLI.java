/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli;

import com.aws.iot.evergreen.cli.adapter.AdapterModule;
import com.aws.iot.evergreen.cli.commands.*;
import com.aws.iot.evergreen.cli.util.logs.LogsModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;

import java.util.ResourceBundle;

/**
 * Main entry point into the command line.
 */
@Command(name = "cli",
        subcommands = {HelpCommand.class, Config.class, Health.class, Service.class, ComponentCommand.class, Logs.class},
        resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages")
public class CLI implements Runnable {
    @Option(names = "--host", defaultValue = "localhost")
    String host;
    @Option(names = "--port", defaultValue = "8080")
    Integer port;

    @Spec
    CommandSpec spec;

    public static void main(String... args) {
        int exitCode = new CommandLine(new CLI(), new GuiceFactory(new AdapterModule(), new LogsModule())).execute(args);
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
