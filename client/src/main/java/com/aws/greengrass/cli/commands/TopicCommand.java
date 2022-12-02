/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

import static com.aws.greengrass.cli.adapter.impl.NucleusAdapterIpcClientImpl.deTilde;

@CommandLine.Command(name = "topic", resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class, mixinStandardHelpOptions = true,
        versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
public class TopicCommand extends BaseCommand {
    private final NucleusAdapterIpc nucleusAdapterIpc;

    private static final String LOCAL = "LOCAL";
    private static final String MQTT = "MQTT";

    private enum MSGTYPE{
        LOCAL,
        MQTT
    }

    public static class MessageType{
        @CommandLine.Option(names = {"--pubsub"}, paramLabel = "To send local message.",
                required = true, arity = "0..1", fallbackValue = LOCAL) MSGTYPE local;

        @CommandLine.Option(names = {"--iotcore"}, paramLabel = "To send Iot Core message.",
                required = true, arity = "0..1", fallbackValue = MQTT) MSGTYPE mqtt;
    }

    @Inject
    public TopicCommand(NucleusAdapterIpc nucleusAdapterIpc) {
        this.nucleusAdapterIpc = nucleusAdapterIpc;
    }

    @CommandLine.Command(name = "pub",
            description = "Publish message to the specific topic ",
            mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public void pub(@CommandLine.Option(names = {"-t", "--topic"}, paramLabel = "The name of the topic.", required = true) String topicName,
                    @CommandLine.Option(names = {"-m", "--message"}, paramLabel = "The message that is published.", required = true) String message,
                    @CommandLine.ArgGroup(multiplicity = "1") MessageType type,
                    @CommandLine.Option(names = {"-q", "--qos"}, paramLabel = "This applies to the --iotcore option only.", defaultValue = "0") String qos)
            throws IOException {
        if(isEmpty(topicName)){
            System.err.println("Topic name cannot be empty.");
            return;
        }

        String configurationUpdate = null;
        if (message != null && !message.isEmpty()) {
            try {
                // Try to read JSON from a file if it is a path and the file exists
                try {
                    Optional<Path> filePath = deTilde(message);
                    if (filePath.isPresent() && Files.exists(filePath.get())) {
                        configurationUpdate = new String(Files.readAllBytes(filePath.get()), StandardCharsets.UTF_8);
                    }
                } catch (InvalidPathException ignored) {
                    // If the input is a JSON, InvalidPathException is thrown from deTilde and needs to be ignored
                }

                // If it wasn't a file or a path, then try reading it as a JSON string
                if (configurationUpdate == null) {
                    configurationUpdate = message;
                }
            } catch (JsonProcessingException e) {
                System.err.println(spec.commandLine().getColorScheme()
                        .errorText("Update configuration parameter is not a properly formatted JSON "
                                + "file or a JSON string"));
                System.err.println(spec.commandLine().getColorScheme().errorText(e.getMessage()));
            }
        }

        if (type.local != null && type.local.equals(MSGTYPE.LOCAL)) {
            nucleusAdapterIpc.publishToTopic(topicName, configurationUpdate);
        } else if (type.mqtt != null && type.mqtt.equals(MSGTYPE.MQTT)) {
            nucleusAdapterIpc.publishToIoTCore(topicName, configurationUpdate, qos);
        } else {
            System.err.println(spec.commandLine().getColorScheme()
                    .errorText("The type of message error occurred, pubsub can only be LOCAL and iotcore can only be MQTT."));
        }
    }

    @CommandLine.Command(name = "sub",
            description = "Subscribe the specific topic ",
            mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public void sub(@CommandLine.Option(names = {"-t", "--topic"}, paramLabel = "The name of the topic.", required = true) String topicName,
                    @CommandLine.ArgGroup(multiplicity = "1") MessageType type,
                    @CommandLine.Option(names = {"-q", "--qos"}, paramLabel = "This applies to the --iotcore option only.", defaultValue = "0") String qos) throws IOException {
        if(isEmpty(topicName)){
            System.err.println("Topic name cannot be empty.");
            return;
        }

        if (type.local != null && type.local.equals(MSGTYPE.LOCAL)) {
            nucleusAdapterIpc.subscribeToTopic(topicName);
        } else if (type.mqtt != null && type.mqtt.equals(MSGTYPE.MQTT)) {
            nucleusAdapterIpc.subscribeToIoTCore(topicName, qos);
        } else {
            System.err.println(spec.commandLine().getColorScheme()
                    .errorText("The type of message error occurred, pubsub can only be LOCAL and iotcore can only be MQTT."));
        }
    }

    private boolean isEmpty(String s) {
        if (s == null) {
            return true;
        }
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (!Character.isSpaceChar(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

}
