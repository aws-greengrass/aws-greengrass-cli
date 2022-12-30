package com.aws.greengrass.cli.commands.topic;

import com.aws.greengrass.cli.commands.BaseCommand;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

import static com.aws.greengrass.cli.adapter.impl.NucleusAdapterIpcClientImpl.deTilde;

public class TopicBaseInfo extends BaseCommand {

    protected static final String SUB_COMMAND_DESCRIPTION = "Subscribe the specific topic ";
    protected static final String PUB_COMMAND_DESCRIPTION = "Publish message to the specific topic ";
    protected static final String TOPIC_NAME_DESCRIPTION = "The name of the topic.";
    protected static final String MESSAGE_DESCRIPTION = "The message that is published.";
    protected static final String QOS_DESCRIPTION = "The MQTT QoS to use. It has the following values(default is 0): " +
            "0. The MQTT message is delivered at most once. " +
            " 1. The MQTT message is delivered at least once.";
    protected static final String TOPIC_EMPTY_ERROR_MESSAGE = "Topic name cannot be empty";

    /**
     * Determine if string is empty.
     *
     * @param s
     * @return boolean
     */
    protected boolean isEmpty(String s) {
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

    /**
     * Get content from a file or string.
     *
     * @param message
     * @return String
     */
    protected String getContent(String message) {
        String content = null;
        if (message != null && !message.isEmpty()) {
            try {
                // Try to read content from a file if it is a path and the file exists
                try {
                    Optional<Path> filePath = deTilde(message);
                    if (filePath.isPresent() && Files.exists(filePath.get())) {
                        content = new String(Files.readAllBytes(filePath.get()), StandardCharsets.UTF_8);
                        return content;
                    }
                } catch (InvalidPathException ignored) {
                    // InvalidPathException is thrown from deTilde and needs to be ignored.
                }

                // If it wasn't a file or a path, then try reading it as a string
                if (content == null) {
                    content = message;
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
        return content;
    }
}
