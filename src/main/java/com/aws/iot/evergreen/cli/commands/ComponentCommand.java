/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import com.aws.iot.evergreen.cli.adapter.LocalOverrideRequest;
import picocli.CommandLine;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

@CommandLine.Command(name = "component", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class)
public class ComponentCommand extends BaseCommand {

    @Inject
    private KernelAdapter kernelAdapter;

    //TODO: handle multiple recipe and artifacts to be copied to package store path
    // Test? 10 min


    @CommandLine.Command(name = "update",
            description = "Updates Evergreen applications with provided recipes, artifacts, and runtime parameters")
    public int deploy(@CommandLine.Option(names = {"-m", "--merge"},
            paramLabel = "Component") Map<String, String> componentsToMerge,
                      @CommandLine.Option(names = {"--remove"},
                              paramLabel = "Component Name") List<String> componentsToRemove,
                      @CommandLine.Option(names = {"-r", "--recipeDir"}, paramLabel = "Folder") String recipeDir,
                      @CommandLine.Option(names = {"-a", "--artifactDir"}, paramLabel = "Folder") String artifactDir,
                      @CommandLine.Option(names = {"-p", "--param"},
                              paramLabel = "Key Value Pair") Map<String, String> parameters

    ) {

        // TODO Validate folder exists and folder structure
        Map<String, Map<String, Object>> componentNameToConfig = convertParameters(parameters);


        LocalOverrideRequest localOverrideRequest = LocalOverrideRequest.builder().componentsToMerge(componentsToMerge)
                .componentsToRemove(componentsToRemove).recipeDir(recipeDir).artifactDir(artifactDir)
                .componentNameToConfig(componentNameToConfig).build();


        kernelAdapter.localOverride(localOverrideRequest);
        System.out.println("Local override request has been submitted!");
        return 0;
    }

    /**
     * Convert parameters.
     * For example: {Component.path.key: value} -> {Component: {path: {key: {value}}}}
     *
     * @param params
     * @return convertedParam
     */
    private Map<String, Map<String, Object>> convertParameters(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, Object>> componentNameToConfig = new HashMap<>();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String multiLevelKey = entry.getKey();
            String value = entry.getValue();

            String[] levels = multiLevelKey.split("\\.");
            String componentName = levels[0];

            componentNameToConfig.putIfAbsent(componentName, new HashMap<>());

            HashMap map = (HashMap) componentNameToConfig.get(componentName);

            if (levels.length < 2) {
                throw new IllegalArgumentException("--param is not in the format of <ComponentName>.<key>=<value>");
            }

            String key;

            for (int i = 1; i < levels.length - 1; i++) {
                key = levels[i];
                map.putIfAbsent(key, new HashMap<>());
                map = (HashMap<Object, Object>) map.get(key);
            }

            map.put(levels[levels.length - 1], value);

        }
        return componentNameToConfig;
    }


}
