/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;
import software.amazon.awssdk.aws.greengrass.model.ComponentDetails;

import java.util.List;
import javax.inject.Inject;

@CommandLine.Command(name = "component", resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class)
public class ComponentCommand extends BaseCommand {

    private final ObjectMapper mapper = new ObjectMapper();
    private final NucleusAdapterIpc nucleusAdapterIpc;

    @Inject
    public ComponentCommand(NucleusAdapterIpc nucleusAdapterIpc) {
        this.nucleusAdapterIpc = nucleusAdapterIpc;
    }

    @CommandLine.Command(name = "restart")
    public int restart(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "component names separated by comma", descriptionKey = "names", required = true) String names) {
        String[] componentNames = names.split(" *[&,]+ *");
        for (String componentName : componentNames) {
            nucleusAdapterIpc.restartComponent(componentName);
        }
        return 0;
    }

    @CommandLine.Command(name = "stop")
    public int stop(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "component names separated by comma", descriptionKey = "names", required = true) String names) {
        String[] componentNames = names.split(" *[&,]+ *");
        for (String componentName : componentNames) {
            nucleusAdapterIpc.stopComponent(componentName);
        }
        return 0;
    }

    //TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "list",
            description = "Prints root level components names, component information and runtime parameters")
    public int list() throws JsonProcessingException {
        List<ComponentDetails> componentDetails = nucleusAdapterIpc.listComponents();
        System.out.println("Components currently running in Greengrass:");
        for (ComponentDetails c : componentDetails) {
            printComponentDetails(c);
        }
        return 0;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "details")
    public int details(@CommandLine.Option(names = {"-n", "--name"}, paramLabel = " component name", descriptionKey = "name", required = true) String componentName)
            throws JsonProcessingException {
        ComponentDetails componentDetails = nucleusAdapterIpc.getComponentDetails(componentName);
        printComponentDetails(componentDetails);
        return 0;
    }

    private void printComponentDetails(ComponentDetails component) throws JsonProcessingException {
        System.out.println("Component Name: " + component.getComponentName());
        System.out.println("Version: " + component.getVersion());
        System.out.println("State: " + component.getState());
        System.out.println("Configuration: " + mapper.writeValueAsString(component.getConfiguration()));
    }
}
