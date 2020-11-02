/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.aws.greengrass.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.greengrass.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.greengrass.ipc.services.cli.models.ComponentDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

@CommandLine.Command(name = "component", resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class, mixinStandardHelpOptions = true)
public class ComponentCommand extends BaseCommand {

    private final ObjectMapper mapper = new ObjectMapper();
    private final NucleusAdapterIpc nucleusAdapterIpc;

    @Inject
    public ComponentCommand(
            NucleusAdapterIpc nucleusAdapterIpc
    ) {
        this.nucleusAdapterIpc = nucleusAdapterIpc;
    }

    @CommandLine.Command(name = "restart", mixinStandardHelpOptions = true)
    public int restart(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "component names separated by comma", descriptionKey = "names", required = true) String names)
            throws CliIpcClientException, GenericCliIpcServerException {
        String[] componentNames = names.split(" *[&,]+ *");
        for (String componentName : componentNames) {
            nucleusAdapterIpc.restartComponent(componentName);
        }
        return 0;
    }

    @CommandLine.Command(name = "stop", mixinStandardHelpOptions = true)
    public int stop(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "component names separated by comma", descriptionKey = "names", required = true) String names)
            throws CliIpcClientException, GenericCliIpcServerException {
        String[] componentNames = names.split(" *[&,]+ *");
        for (String componentName : componentNames) {
            nucleusAdapterIpc.stopComponent(componentName);
        }
        return 0;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "list", mixinStandardHelpOptions = true,
            description = "Prints root level components names, component information and runtime parameters")
    public int list() throws CliIpcClientException, GenericCliIpcServerException, JsonProcessingException {
        List<ComponentDetails> componentDetails = nucleusAdapterIpc.listComponents();
        System.out.println("Components currently running in Greengrass:");
        for (ComponentDetails c : componentDetails) {
            printComponentDetails(c);
        }
        return 0;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "details", mixinStandardHelpOptions = true)
    public int details(@CommandLine.Option(names = {"-n", "--name"}, paramLabel = " component name", descriptionKey = "name", required = true) String componentName)
            throws CliIpcClientException, GenericCliIpcServerException, JsonProcessingException {
        ComponentDetails componentDetails = nucleusAdapterIpc.getComponentDetails(componentName);
        printComponentDetails(componentDetails);
        return 0;
    }

    private void printComponentDetails(ComponentDetails component) throws JsonProcessingException {
        System.out.println("Component Name: " + component.getComponentName());
        System.out.println("Version: " + component.getVersion());
        System.out.println("State: " + component.getState());
        System.out.println("Configuration: " + mapper.writeValueAsString(component.getConfiguration()));
        System.out.println("Configurations: " + mapper.writeValueAsString(component.getNestedConfiguration()));
    }

    /**
     * Convert parameters. For example: {Component:path.key: value} -> {Component: {path: {key: {value}}}}
     *
     * @param params runtime parameters in the flat map
     * @return converted runtime parameters map, with each key as component name and each value as the component's
     *         configuration map
     */
    @Deprecated
     static Map<String, Map<String, Object>> convertParameters(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> componentNameToConfig = new HashMap<>();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String componentNameAndKey = entry.getKey();
            String value = entry.getValue();
            String[] parts = componentNameAndKey.split(":");

            if (parts.length != 2) {
                throw new IllegalArgumentException("--param is not in the format of <ComponentName>:<key>=<value>");
            }
            String componentName = parts[0];
            String multiLevelKey = parts[1];
            String[] levels = multiLevelKey.split("\\.");

            componentNameToConfig.putIfAbsent(componentName, new HashMap<>());
            HashMap map = (HashMap) componentNameToConfig.get(componentName);
            String key;
            for (int i = 0; i < levels.length - 1; i++) {
                key = levels[i];
                map.putIfAbsent(key, new HashMap<>());
                map = (HashMap<Object, Object>) map.get(key);
            }
            map.put(levels[levels.length - 1], value);
        }
        return componentNameToConfig;
    }
}
