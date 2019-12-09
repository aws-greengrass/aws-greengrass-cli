/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */
package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import com.aws.iot.evergreen.cli.adapter.impl.KernelAdapterHttpClientImpl;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/**
 * Command to retrieve kernel health.
 */
@Command(name = "health", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages", subcommands = HelpCommand.class)
public class Health extends BaseCommand {

    private KernelAdapter kernelAdapter = new KernelAdapterHttpClientImpl();

    @Override
    public void run() {
        String result = kernelAdapter.healthPing();
        System.out.println("Kernel health status:");
        System.out.println(result);
    }

}

