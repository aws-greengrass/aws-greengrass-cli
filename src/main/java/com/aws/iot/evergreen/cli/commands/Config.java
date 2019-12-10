/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */
package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import com.aws.iot.evergreen.cli.adapter.impl.KernelAdapterHttpClientImpl;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
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

    private KernelAdapter kernelAdapter = new KernelAdapterHttpClientImpl();

    @Command(name = "get")
    public int get(@Option(names = {"-p", "--path"}, paramLabel = "path", descriptionKey = "path", required = true) String path) {
        String[] pathArray = path.split(" *[&,]+ *");
        Map<String, String> valueMap = kernelAdapter.getConfigs(new HashSet<>(Arrays.asList(pathArray)));
        valueMap.forEach((k, v) -> {
            System.out.printf("%s: %s%n", k, v);
        });
        return 0;
    }

    @Command(name = "set")
    public int set(@Option(names = {"-p", "--path"}, paramLabel = "path", descriptionKey = "path", required = true) String path, @Option(names = {"-v", "--value"}, paramLabel = "value", required = true) String value) {
        kernelAdapter.setConfigs(Collections.singletonMap(path, value));
        return 0;
    }

    @Override
    public void run() {
        String msg = ResourceBundle.getBundle("com.aws.iot.evergreen.cli.CLI_messages").getString("exception.missing.command");
        throw new ParameterException(spec.commandLine(), msg);
    }
}

