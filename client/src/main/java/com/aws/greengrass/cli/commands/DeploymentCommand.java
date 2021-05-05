/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.LocalDeployment;
import software.amazon.awssdk.aws.greengrass.model.RunWithInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

import static com.aws.greengrass.cli.adapter.impl.NucleusAdapterIpcClientImpl.deTilde;

@CommandLine.Command(name = "deployment", resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class, mixinStandardHelpOptions = true,
        versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
public class DeploymentCommand extends BaseCommand {

    private final String RUN_WITH_OPTION_POSIX_USER = "posixUser";
    private final ObjectMapper mapper = new ObjectMapper();
    private NucleusAdapterIpc nucleusAdapterIpc;

    @Inject
    public DeploymentCommand(NucleusAdapterIpc nucleusAdapterIpc) {
        this.nucleusAdapterIpc = nucleusAdapterIpc;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "create",
            description = "Create local deployment with provided recipes, artifacts, and runtime parameters",
            mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public int create
    (@CommandLine.Option(names = {"-m", "--merge"}, paramLabel = "Component and version") Map<String, String> componentsToMerge,
     @CommandLine.Option(names = {"--remove"}, paramLabel = "Component Names") List<String> componentsToRemove,
     @CommandLine.Option(names = {"-g", "--groupId"}, paramLabel = "Thing group ID") String groupId,
     @CommandLine.Option(names = {"-r", "--recipeDir"}, paramLabel = "Recipe directory") String recipeDir,
     @CommandLine.Option(names = {"-a", "--artifactDir"}, paramLabel = "Artifacts directory") String artifactDir,
     @CommandLine.Option(names = {"--runWith"}, paramLabel = "Component user and/or group") Map<String, String> runWithOptions,
     @CommandLine.Option(names = {"-c", "--update-config"}, paramLabel = "Component configuration") String configUpdate)
            throws IOException {
        // GG_NEEDS_REVIEW: TODO Validate folder exists and folder structure

        Map<String, Map<String, Object>> configurationUpdate = null;
        if (configUpdate != null && !configUpdate.isEmpty()) {
            try {
                // Try to read JSON from a file if it is a path and the file exists
                try {
                    Optional<Path> filePath = deTilde(configUpdate);
                    if (filePath.isPresent() && Files.exists(filePath.get())) {
                        configurationUpdate = mapper.readValue(filePath.get().toFile(), Map.class);
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

        CreateLocalDeploymentRequest createLocalDeploymentRequest = new CreateLocalDeploymentRequest();
        createLocalDeploymentRequest.setGroupName(groupId);
        createLocalDeploymentRequest.setComponentToConfiguration(configurationUpdate);
        createLocalDeploymentRequest.setRootComponentVersionsToAdd(componentsToMerge);
        createLocalDeploymentRequest.setRootComponentsToRemove(componentsToRemove);
        createLocalDeploymentRequest.setComponentToRunWithInfo(getComponentToRunWithInfo(runWithOptions));
        Optional<Path> recipeDirPath = deTilde(recipeDir);
        createLocalDeploymentRequest.setRecipeDirectoryPath(recipeDirPath.isPresent() ? recipeDirPath.get().toString() :
                null);
        Optional<Path> artifactDirPath = deTilde(artifactDir);
        createLocalDeploymentRequest.setArtifactsDirectoryPath(artifactDirPath.isPresent() ?
                artifactDirPath.get().toString() : null);
        String deploymentId = nucleusAdapterIpc.createLocalDeployment(createLocalDeploymentRequest);
        System.out.println("Local deployment submitted! Deployment Id: " + deploymentId);
        return 0;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "status",
            description = "Retrieve the status of a specific deployment", mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public int status(@CommandLine.Option(names = {"-i", "--deploymentId"}, paramLabel = "Deployment ID",
            required = true) String deploymentId) {

        LocalDeployment status = nucleusAdapterIpc.getLocalDeploymentStatus(deploymentId);
        System.out.printf("%s: %s%n", status.getDeploymentId(), status.getStatus());
        return 0;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "list", description = "Retrieve the status of local deployments",
            mixinStandardHelpOptions = true, versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public int list() {
        List<LocalDeployment> localDeployments = nucleusAdapterIpc.listLocalDeployments();
        localDeployments.forEach((status) -> System.out.println(status.getDeploymentId() + ": " + status.getStatus()));
        return 0;
    }

    private Map<String, RunWithInfo> getComponentToRunWithInfo(Map<String, String> runWithOptions) {
        if (runWithOptions == null || runWithOptions.size() == 0) {
            return null;
        }
        Map<String, RunWithInfo> componentToRunWithInfo = new HashMap<>();
        for (Map.Entry<String, String> entry : runWithOptions.entrySet()) {

            String componentNameAndRunWithOption = entry.getKey();
            String[] parts = componentNameAndRunWithOption.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("--runWith must be in the following format <component>:posixUser=<user>[:<group>] ");
            }
            String componentName = parts[0];
            String runWithOption = parts[1];
            RunWithInfo runWithInfo = new RunWithInfo();
            switch (runWithOption) {
                case RUN_WITH_OPTION_POSIX_USER:
                    runWithInfo.setPosixUser(entry.getValue());
                    break;
                default:
                    throw new IllegalArgumentException("Invalid --runWith option: " + runWithOption);
            }
            componentToRunWithInfo.put(componentName, runWithInfo);
        }
        return componentToRunWithInfo;
    }

}
