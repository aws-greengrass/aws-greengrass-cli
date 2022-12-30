package com.aws.greengrass.cli.commands.topic;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import picocli.CommandLine;

import javax.inject.Inject;
import java.io.IOException;

@CommandLine.Command(name = "pubsub", resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class, mixinStandardHelpOptions = true,
        versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
public class PubSubCommand extends TopicBaseInfo {

    private final NucleusAdapterIpc nucleusAdapterIpc;

    @Inject
    public PubSubCommand(NucleusAdapterIpc nucleusAdapterIpc) {
        super();
        this.nucleusAdapterIpc = nucleusAdapterIpc;
    }

    @CommandLine.Command(name = "pub",
            description = PUB_COMMAND_DESCRIPTION,
            mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public void pub(@CommandLine.Option(names = {"-t", "--topic"}, paramLabel = TOPIC_NAME_DESCRIPTION, required = true) String topicName,
                    @CommandLine.Option(names = {"-m", "--message"}, paramLabel = MESSAGE_DESCRIPTION, required = true) String message) {
        if(isEmpty(topicName)){
            System.err.println(TOPIC_EMPTY_ERROR_MESSAGE);
            return;
        }
        String content = getContent(message);
        nucleusAdapterIpc.publishToTopic(topicName, content);
    }

    @CommandLine.Command(name = "sub",
            description = SUB_COMMAND_DESCRIPTION,
            mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public void sub(@CommandLine.Option(names = {"-t", "--topic"}, paramLabel = TOPIC_NAME_DESCRIPTION, required = true) String topicName)
            throws IOException {
        if(isEmpty(topicName)){
            System.err.println(TOPIC_EMPTY_ERROR_MESSAGE);
            return;
        }
        nucleusAdapterIpc.subscribeToTopic(topicName);
    }
}
