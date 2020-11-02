/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.aws.greengrass.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.greengrass.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.greengrass.ipc.services.cli.models.CreateLocalDeploymentRequest;
import com.aws.greengrass.ipc.services.cli.models.LocalDeployment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import static com.aws.greengrass.cli.adapter.impl.NucleusAdapterIpcClientImpl.deTilde;
import static com.aws.greengrass.cli.commands.ComponentCommand.convertParameters;

@CommandLine.Command(name = "deployment", resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class, mixinStandardHelpOptions = true)
public class DeploymentCommand extends BaseCommand {

    private final ObjectMapper mapper = new ObjectMapper();
    private final NucleusAdapterIpc nucleusAdapterIpc;

    @Inject
    public DeploymentCommand(
            NucleusAdapterIpc nucleusAdapterIpc
    ) {
        this.nucleusAdapterIpc = nucleusAdapterIpc;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "create",
            description = "Create local deployment with provided recipes, artifacts, and runtime parameters",
            mixinStandardHelpOptions = true)
    public int create
    (@CommandLine.Option(names = {"-m", "--merge"}, paramLabel = "Component and version") Map<String, String> componentsToMerge,
     @CommandLine.Option(names = {"--remove"}, paramLabel = "Component Names") List<String> componentsToRemove,
     @CommandLine.Option(names = {"-g", "--groupId"}, paramLabel = "group Id") String groupId,
     @CommandLine.Option(names = {"-r", "--recipeDir"}, paramLabel = "Recipe Folder Path") String recipeDir,
     @CommandLine.Option(names = {"-a", "--artifactDir"}, paramLabel = "Artifacts Folder Path") String artifactDir,
     @CommandLine.Option(names = {"-p", "--param"}, paramLabel = "Runtime parameters") Map<String, String> parameters,
     @CommandLine.Option(names = {"-c", "--update-config"}, paramLabel = "Update configuration") String configUpdate)
            throws CliIpcClientException, GenericCliIpcServerException, IOException {
        // GG_NEEDS_REVIEW: TODO Validate folder exists and folder structure
        Map<String, Map<String, Object>> componentNameToConfig = convertParameters(parameters);
        Map<String, Map<String, Object>> configurationUpdate = null;
        if (configUpdate != null && !configUpdate.isEmpty()) {
            try {
                // Try to read JSON from a file if it is a path and the file exists
                try {
                    Path filePath = Paths.get(deTilde(configUpdate));
                    if (Files.exists(filePath)) {
                        configurationUpdate = mapper.readValue(filePath.toFile(), Map.class);
                    }
                } catch (InvalidPathException ignored) {
                }
                // If it wasn't a file or a path, then try reading it as a JSON string
                if (configurationUpdate == null) {
                    configurationUpdate = mapper.readValue(configUpdate, Map.class);
                }
            } catch (JsonProcessingException e) {
                System.err.println(spec.commandLine().getColorScheme()
                        .errorText("Update configuration parameter is not a properly formatted JSON "
                                + "file or a JSON string"));
                System.err.println(spec.commandLine().getColorScheme().errorText(e.getMessage()));
                return 1;
            }
        }
        if (recipeDir != null || artifactDir != null) {
            nucleusAdapterIpc.updateRecipesAndArtifacts(recipeDir, artifactDir);
        }
        CreateLocalDeploymentRequest createLocalDeploymentRequest = CreateLocalDeploymentRequest.builder()
                .groupName(groupId)
                .configurationUpdate(configurationUpdate)
                .componentToConfiguration(componentNameToConfig)
                .rootComponentVersionsToAdd(componentsToMerge)
                .rootComponentsToRemove(componentsToRemove)
                .build();
        String deploymentId = nucleusAdapterIpc.createLocalDeployment(createLocalDeploymentRequest);
        System.out.println("Local deployment has been submitted! Deployment Id: " + deploymentId);
        return 0;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "status",
            description = "Retrieve the status of a deployment", mixinStandardHelpOptions = true)
    public int status(@CommandLine.Option(names = {"-i", "--deploymentId"}, paramLabel = "Deployment Id", required = true) String deploymentId)
            throws CliIpcClientException, GenericCliIpcServerException {

        LocalDeployment status = nucleusAdapterIpc.getLocalDeploymentStatus(deploymentId);
        System.out.printf("%s: %s", status.getDeploymentId(), status.getStatus());
        return 0;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "list", description = "Retrieve the status of local deployments",
            mixinStandardHelpOptions = true)
    public int list() throws CliIpcClientException, GenericCliIpcServerException {
        List<LocalDeployment> localDeployments = nucleusAdapterIpc.listLocalDeployments();
        localDeployments.forEach((status) -> System.out.println(status.getDeploymentId() + ": " + status.getStatus()));
        return 0;
    }
}
