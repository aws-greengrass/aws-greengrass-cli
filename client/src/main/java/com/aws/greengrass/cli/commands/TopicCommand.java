/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import picocli.CommandLine;

import javax.inject.Inject;
import java.io.IOException;

@CommandLine.Command(name = "topic", resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class, mixinStandardHelpOptions = true,
        versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
public class TopicCommand extends BaseCommand {
    private final NucleusAdapterIpc nucleusAdapterIpc;

    @Inject
    public TopicCommand(NucleusAdapterIpc nucleusAdapterIpc) {
        this.nucleusAdapterIpc = nucleusAdapterIpc;
    }

    @CommandLine.Command(name = "pub",
            description = "Publish message to the specific topic ",
            mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public void pub(@CommandLine.Option(names = {"-tn", "--topicname"}, paramLabel = "The name of the topic.", required = true) String topicName,
                    @CommandLine.Option(names = {"-msg", "--message"}, paramLabel = "The message that is published.", required = true) String message,
                    @CommandLine.Option(names = {"-mt", "--messagetype"}, paramLabel = "The type of the message(local/mqtt).", required = true) String messageType,
                    @CommandLine.Option(names = {"-qos", "--qos"}, paramLabel = "The MQTT QoS to use.", defaultValue = "0") String qos) {
        if (messageType.equals("local")) {
            nucleusAdapterIpc.publishToTopic(topicName, message);
        } else if (messageType.equals("mqtt")) {
            nucleusAdapterIpc.publishToIoTCore(topicName, message, qos);
        } else {
            System.err.println(spec.commandLine().getColorScheme()
                    .errorText("The type of message error occurred, it can only be local or mqtt"));
        }
    }

    @CommandLine.Command(name = "sub",
            description = "Subscribe the specific topic ",
            mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public void sub(@CommandLine.Option(names = {"-tn", "--topicname"}, paramLabel = "The name of the topic.", required = true) String topicName,
                    @CommandLine.Option(names = {"-mt", "--messagetype"}, paramLabel = "The type of the message(local/mqtt).", required = true) String messageType,
                    @CommandLine.Option(names = {"-qos", "--qos"}, paramLabel = "The MQTT QoS to use.", defaultValue = "0") String qos) throws IOException {
        if (messageType.equals("local")) {
            nucleusAdapterIpc.subscribeToTopic(topicName);
        } else if (messageType.equals("mqtt")) {
            nucleusAdapterIpc.subscribeToIoTCore(topicName, qos);
        } else {
            System.err.println(spec.commandLine().getColorScheme()
                    .errorText("The type of message error occurred, it can only be local or mqtt"));
        }
    }
}
