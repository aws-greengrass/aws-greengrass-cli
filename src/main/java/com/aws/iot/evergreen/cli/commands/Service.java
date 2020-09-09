package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.adapter.KernelAdapterIpc;
import com.aws.iot.evergreen.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.iot.evergreen.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.iot.evergreen.ipc.services.cli.models.ComponentDetails;
import picocli.CommandLine;

import javax.inject.Inject;
import java.util.Map;

//TODO: Moved stop/restart as sub-commands of "component" name space space. Remove these methods after UAT's are updated
@CommandLine.Command(name = "service", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages", subcommands = CommandLine.HelpCommand.class)
public class Service extends BaseCommand {

    @Inject
    private KernelAdapterIpc kernelAdapterIpc;
    //TODO : status provides a subset of information from getComponentDetails. Move UAT to use getComponentDetails and
    // remove this command.
    @CommandLine.Command(name = "status")
    public int status(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names) throws CliIpcClientException, GenericCliIpcServerException {
        String[] serviceNames = names.split(" *[&,]+ *");
        for (String serviceName : serviceNames) {
            ComponentDetails componentDetails = kernelAdapterIpc.getComponentDetails(serviceName);
            System.out.printf("%s:%s\n", componentDetails.getComponentName(), componentDetails.getState().toString());
        }
        return 0;
    }

    @CommandLine.Command(name = "restart")
    public int reload(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names)
            throws CliIpcClientException, GenericCliIpcServerException {
        String[] serviceNames = names.split(" *[&,]+ *");
        for (String serviceName : serviceNames) {
            kernelAdapterIpc.restartComponent(serviceName);
        }
        return 0;
    }

    @CommandLine.Command(name = "stop")
    public int close(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names)
            throws CliIpcClientException, GenericCliIpcServerException {
        String[] serviceNames = names.split(" *[&,]+ *");
        for (String serviceName : serviceNames) {
            kernelAdapterIpc.stopComponent(serviceName);
        }
        return 0;
    }
}
