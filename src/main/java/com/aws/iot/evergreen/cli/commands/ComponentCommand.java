/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.adapter.KernelAdapterIpc;
import com.aws.iot.evergreen.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.iot.evergreen.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.iot.evergreen.ipc.services.cli.models.ComponentDetails;
import com.aws.iot.evergreen.ipc.services.cli.models.CreateLocalDeploymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "component", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class)
public class ComponentCommand extends BaseCommand {

    @Inject
    private KernelAdapterIpc kernelAdapterIpc;

    @CommandLine.Command(name = "restart")
    public int reload(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names)
            throws CliIpcClientException, GenericCliIpcServerException {
        String[] componentNames = names.split(" *[&,]+ *");
        for (String componentName : componentNames) {
            kernelAdapterIpc.restartComponent(componentName);
        }
        return 0;
    }

    @CommandLine.Command(name = "stop")
    public int close(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names)
            throws CliIpcClientException, GenericCliIpcServerException {
        String[] componentNames = names.split(" *[&,]+ *");
        for (String componentName : componentNames) {
            kernelAdapterIpc.stopComponent(componentName);
        }
        return 0;
    }


    //TODO: deprecate this for "create" sub command under deployment command space. (pending UAT update)
    @CommandLine.Command(name = "update",
            description = "Updates Evergreen applications with provided recipes, artifacts, and runtime parameters")
    public int deploy
    (@CommandLine.Option(names = {"-m", "--merge"}, paramLabel = "Component") Map<String, String> componentsToMerge,
     @CommandLine.Option(names = {"--remove"}, paramLabel = "Component Name") List<String> componentsToRemove,
     @CommandLine.Option(names = {"-g", "--groupId"}, paramLabel = "group Id") String groupId,
     @CommandLine.Option(names = {"-r", "--recipeDir"}, paramLabel = "Folder") String recipeDir,
     @CommandLine.Option(names = {"-a", "--artifactDir"}, paramLabel = "Folder") String artifactDir,
     @CommandLine.Option(names = {"-p", "--param"}, paramLabel = "Key Value Pair") Map<String, String> parameters)
            throws CliIpcClientException, GenericCliIpcServerException {
        // TODO Validate folder exists and folder structure
        Map<String, Map<String, Object>> componentNameToConfig = convertParameters(parameters);
        if (recipeDir != null || artifactDir != null) {
            kernelAdapterIpc.updateRecipesAndArtifacts(recipeDir, artifactDir);
        }
        CreateLocalDeploymentRequest createLocalDeploymentRequest = CreateLocalDeploymentRequest.builder()
                .groupName(groupId)
                .componentToConfiguration(componentNameToConfig)
                .rootComponentVersionsToAdd(componentsToMerge)
                .rootComponentsToRemove(componentsToRemove)
                .build();
        String deploymentId = kernelAdapterIpc.createLocalDeployment(createLocalDeploymentRequest);
        System.out.println("Local deployment has been submitted! Deployment Id: "+ deploymentId);
        return 0;
    }

    @CommandLine.Command(name = "list",
            description = "Prints root level components names, component information and runtime parameters")
    public int list() throws CliIpcClientException, GenericCliIpcServerException {
        List<ComponentDetails> componentDetails = kernelAdapterIpc.listComponents();
        System.out.println("Components currently running in Evergreen:");
        componentDetails.forEach(c -> printComponentDetails(c));
        return 0;
    }

    @CommandLine.Command(name = "details")
    public int details(@CommandLine.Option(names = {"-n", "--name"}, paramLabel = "name", descriptionKey = "name", required = true) String componentName) throws CliIpcClientException, GenericCliIpcServerException {
        ComponentDetails componentDetails = kernelAdapterIpc.getComponentDetails(componentName);
        printComponentDetails(componentDetails);
        return 0;
    }

    private void printComponentDetails(ComponentDetails component){
        System.out.println("Component Name: " + component.getComponentName());
        System.out.println("Component Version: " + component.getVersion());
        System.out.println("Component State: " + component.getState());
        System.out.println("Component Configuration: " + component.getConfiguration());
    }

    /**
     * Convert parameters. For example: {Component:path.key: value} -> {Component: {path: {key: {value}}}}
     *
     * @param params runtime parameters in the flat map
     * @return converted runtime parameters map, with each key as component name and each value as the component's
     *         configuration map
     */
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
