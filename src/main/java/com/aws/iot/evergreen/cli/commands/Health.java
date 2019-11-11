/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */
package com.aws.iot.evergreen.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/**
 * Command to retrieve kernel health.
 */
@Command(name = "health", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages", subcommands = HelpCommand.class)
public class Health extends BaseCommand {

    @Override
    public void run() {

        // TODO: do something
    }

}

