/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import picocli.CommandLine;
import software.amazon.awssdk.aws.greengrass.model.CreateDebugPasswordResponse;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.inject.Inject;

@CommandLine.Command(name = "get-debug-password", resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class, mixinStandardHelpOptions = true)
public class PasswordCommand extends BaseCommand {

    private final NucleusAdapterIpc nucleusAdapterIpc;

    @Inject
    public PasswordCommand(NucleusAdapterIpc nucleusAdapterIpc) {
        this.nucleusAdapterIpc = nucleusAdapterIpc;
    }

    @Override
    public void run() {
        CreateDebugPasswordResponse response = nucleusAdapterIpc.createDebugPassword();
        System.out.println("Username: " + response.getUsername());
        System.out.println("Password: " + response.getPassword());
        System.out.println("Password will expire at: " +
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
                        .format(response.getPasswordExpiration()));
    }
}
