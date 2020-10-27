/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.aws.greengrass.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.greengrass.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.greengrass.ipc.services.cli.models.ComponentDetails;
import picocli.CommandLine;

import javax.inject.Inject;

//TODO: Moved stop/restart as sub-commands of "component" name space space. Remove these methods after UAT's are updated
@CommandLine.Command(name = "service", resourceBundle = "com.aws.greengrass.cli.CLI_messages", subcommands = CommandLine.HelpCommand.class)
public class Service extends BaseCommand {

    private final NucleusAdapterIpc nucleusAdapterIpc;

    @Inject
    public Service(
            NucleusAdapterIpc nucleusAdapterIpc
    ) {
        this.nucleusAdapterIpc = nucleusAdapterIpc;
    }

    //TODO : status provides a subset of information from getComponentDetails. Move UAT to use getComponentDetails and
    // remove this command.
    @CommandLine.Command(name = "status")
    public int status(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names) throws CliIpcClientException, GenericCliIpcServerException {
        String[] serviceNames = names.split(" *[&,]+ *");
        for (String serviceName : serviceNames) {
            ComponentDetails componentDetails = nucleusAdapterIpc.getComponentDetails(serviceName);
            System.out.printf("%s: state: %s\n", componentDetails.getComponentName(), componentDetails.getState().toString());
        }
        return 0;
    }

    @CommandLine.Command(name = "restart")
    public int reload(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names)
            throws CliIpcClientException, GenericCliIpcServerException {
        String[] serviceNames = names.split(" *[&,]+ *");
        for (String serviceName : serviceNames) {
            nucleusAdapterIpc.restartComponent(serviceName);
        }
        return 0;
    }

    @CommandLine.Command(name = "stop")
    public int close(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names)
            throws CliIpcClientException, GenericCliIpcServerException {
        String[] serviceNames = names.split(" *[&,]+ *");
        for (String serviceName : serviceNames) {
            nucleusAdapterIpc.stopComponent(serviceName);
        }
        return 0;
    }
}
