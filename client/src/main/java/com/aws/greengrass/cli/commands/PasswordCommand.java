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
        subcommands = CommandLine.HelpCommand.class, mixinStandardHelpOptions = true,
        versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
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
        if (response.getCertificateSignature() != null && !response.getCertificateSignature().isEmpty()) {
            System.out.println(); // Newline to separate the TLS information + warning
            System.out.println("Local debug console is configured to use TLS security. Since the certificate is "
                    + "self-signed you will need to bypass any warnings from your web browser.");
            System.out.println("Before bypassing the security alerts, verify the certificate SHA256 fingerprint "
                    + "matches: ");
            System.out.println(response.getCertificateSignature());
        }
    }
}
