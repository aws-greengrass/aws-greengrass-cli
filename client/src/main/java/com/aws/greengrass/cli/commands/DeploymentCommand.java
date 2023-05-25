/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import picocli.CommandLine;
import software.amazon.awssdk.aws.greengrass.model.CancelLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.DeploymentStatusDetails;
import software.amazon.awssdk.aws.greengrass.model.FailureHandlingPolicy;
import software.amazon.awssdk.aws.greengrass.model.LocalDeployment;
import software.amazon.awssdk.aws.greengrass.model.LocalDeploymentStatus;
import software.amazon.awssdk.aws.greengrass.model.RunWithInfo;
import software.amazon.awssdk.aws.greengrass.model.SystemResourceLimits;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private final String RUN_WITH_OPTION_WINDOWS_USER = "windowsUser";
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
     @CommandLine.Option(names = {"--systemLimits"}, paramLabel = "Component system resource limits") String systemLimits,
     @CommandLine.Option(names = {"-c", "--update-config"}, paramLabel = "Component configuration") String configUpdate,
     @CommandLine.Option(names = {"-fp", "--failure-handling-policy"}, paramLabel = "Failure handling policy") FailureHandlingPolicy failureHandlingPolicy)
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
                    // If the input is a JSON, InvalidPathException is thrown from deTilde and needs to be ignored
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

        MapType mapType = mapper.getTypeFactory().constructMapType(HashMap.class, String.class,
                SystemResourceLimits.class);
        Map<String, SystemResourceLimits> systemResourceLimits = new HashMap<>();
        if (systemLimits != null && !systemLimits.isEmpty()) {
            try {
                // Try to read JSON from a file if it is a path and the file exists
                try {
                    Optional<Path> filePath = deTilde(systemLimits);
                    if (filePath.isPresent() && Files.exists(filePath.get())) {
                        systemResourceLimits = mapper.readValue(filePath.get().toFile(), mapType);
                    }
                } catch (InvalidPathException ignored) {
                    // If the input is a JSON, InvalidPathException is thrown from deTilde and needs to be ignored
                }

                // If it wasn't a file or a path, then try reading it as a JSON string
                if (systemResourceLimits.isEmpty()) {
                    systemResourceLimits = mapper.readValue(systemLimits, mapType);
                }
            } catch (JsonProcessingException e) {
                System.err.println(spec.commandLine().getColorScheme()
                        .errorText("systemLimits parameter file is not a properly formatted JSON"));
                System.err.println(spec.commandLine().getColorScheme().errorText(e.getMessage()));
                return 1;
            }
        }

        CreateLocalDeploymentRequest createLocalDeploymentRequest = new CreateLocalDeploymentRequest();
        createLocalDeploymentRequest.setGroupName(groupId);
        createLocalDeploymentRequest.setComponentToConfiguration(configurationUpdate);
        createLocalDeploymentRequest.setRootComponentVersionsToAdd(componentsToMerge);
        createLocalDeploymentRequest.setRootComponentsToRemove(componentsToRemove);
        createLocalDeploymentRequest.setComponentToRunWithInfo(getComponentToRunWithInfo(runWithOptions,
                systemResourceLimits));
        Optional<Path> recipeDirPath = deTilde(recipeDir);
        createLocalDeploymentRequest.setRecipeDirectoryPath(recipeDirPath.isPresent() ? recipeDirPath.get().toString() :
                null);
        Optional<Path> artifactDirPath = deTilde(artifactDir);
        createLocalDeploymentRequest.setArtifactsDirectoryPath(artifactDirPath.isPresent() ?
                artifactDirPath.get().toString() : null);
        if (failureHandlingPolicy != null) {
            createLocalDeploymentRequest.setFailureHandlingPolicy(failureHandlingPolicy);
        }
        String deploymentId = nucleusAdapterIpc.createLocalDeployment(createLocalDeploymentRequest);
        System.out.println("Local deployment submitted! Deployment Id: " + deploymentId);
        return 0;
    }

    @CommandLine.Command(name = "cancel",
            description = "Cancel a local deployment", mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public int cancel(@CommandLine.Option(names = {"-i", "--deploymentId"}, paramLabel = "Deployment ID"
            , required = true) String deploymentId) {
        CancelLocalDeploymentRequest cancelLocalDeploymentRequest = new CancelLocalDeploymentRequest();
        if (deploymentId != null) {
            cancelLocalDeploymentRequest.setDeploymentId(deploymentId);
        }
        String message = nucleusAdapterIpc.cancelLocalDeployment(cancelLocalDeploymentRequest);
        System.out.printf("%s%n", message);
        return 0;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    /*
    Output example:
    Deployment ID: Deployment UUID
    Created on: Time in UTC when the deployment was created (dd-MM-uuuu HH:mm:ss UTC)
    Status: DEPLOYMENT_STATUS
    Deployment Error Stack: [List, of, deployment, error, if, any] (null if successful deployment)
    Deployment Error Types: [List, of, deployment, error, types, if, any] (null if successful deployment)
    Deployment Failure Cause: Deployment failure cause in text (null if successful deployment)
     */
    @CommandLine.Command(name = "status",
            description = "Retrieve the status of a specific deployment", mixinStandardHelpOptions = true,
            versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public int status(@CommandLine.Option(names = {"-i", "--deploymentId"}, paramLabel = "Deployment ID",
            required = true) String deploymentId) {

        LocalDeploymentStatus status = nucleusAdapterIpc.getLocalDeploymentStatus(deploymentId);
        StringBuilder statusBuilder = new StringBuilder();
        statusBuilder.append(String.format(
                "Deployment ID: %s\n", status.getDeploymentId()));
        statusBuilder.append(String.format(
                "Created on: %s\n", status.getCreatedOn()));
        String deploymentStatus = String.valueOf(status.getStatus());
        DeploymentStatusDetails deploymentStatusDetails = status.getDeploymentStatusDetails();
        if (deploymentStatusDetails != null) {
            deploymentStatus = deploymentStatusDetails.getDetailedDeploymentStatusAsString();
            statusBuilder.append(String.format("Status: %s\n", deploymentStatus));
            if (deploymentStatusDetails.getDeploymentErrorStack() != null &&
                    !deploymentStatusDetails.getDeploymentErrorStack().isEmpty()) {
                statusBuilder.append(String.format("Deployment Error Stack: %s\n",
                        String.join(", ", deploymentStatusDetails.getDeploymentErrorStack())));
            }
            if (deploymentStatusDetails.getDeploymentErrorTypes() != null &&
                    !deploymentStatusDetails.getDeploymentErrorTypes().isEmpty()) {
                statusBuilder.append(String.format("Deployment Error Types: %s\n",
                        String.join(", ", deploymentStatusDetails.getDeploymentErrorTypes())));
            }
            if (deploymentStatusDetails.getDeploymentFailureCause() != null &&
                    !deploymentStatusDetails.getDeploymentFailureCause().trim().isEmpty()) {
                statusBuilder.append(String.format(
                        "Deployment Failure Cause: %s\n", deploymentStatusDetails.getDeploymentFailureCause()));
            }
        } else {
            statusBuilder.append(String.format("Status: %s\n", deploymentStatus));
        }
        System.out.printf(statusBuilder.toString());
        return 0;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    /*
    Output example:
    Deployment ID: Deployment UUID 1
    Created on: Time in UTC when the deployment was created (dd-MM-uuuu HH:mm:ss UTC)
    Status: DEPLOYMENT_STATUS_1

    Deployment ID: Deployment UUID 2
    Created on: Time in UTC when the deployment was created (dd-MM-uuuu HH:mm:ss UTC)
    Status: DEPLOYMENT_STATUS_2

    ...
     */
    @CommandLine.Command(name = "list", description = "Retrieve the status of local deployments",
            mixinStandardHelpOptions = true, versionProvider = com.aws.greengrass.cli.module.VersionProvider.class)
    public int list() {
        List<LocalDeployment> localDeployments = nucleusAdapterIpc.listLocalDeployments();
        List<String> localDeploymentShortStatuses = new ArrayList<>();
        for (LocalDeployment localDeployment : localDeployments) {
            String statusBuilder = String.format("Deployment ID: %s\n", localDeployment.getDeploymentId()) +
                    String.format("Created on: %s\n", localDeployment.getCreatedOn()) +
                    String.format("Status: %s\n", localDeployment.getStatus());
            localDeploymentShortStatuses.add(statusBuilder);
        }
        System.out.printf(String.join("\n", localDeploymentShortStatuses));
        return 0;
    }

    private Map<String, RunWithInfo> getComponentToRunWithInfo(Map<String, String> runWithOptions,
            Map<String, SystemResourceLimits> systemLimits) {
        if (runWithOptions == null) {
            runWithOptions = new HashMap<>();
        }

        Map<String, RunWithInfo> componentToRunWithInfo = new HashMap<>();
        for (Map.Entry<String, String> entry : runWithOptions.entrySet()) {
            String componentNameAndRunWithOption = entry.getKey();
            String[] parts = componentNameAndRunWithOption.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("--runWith must be in the following format "
                        + "<component>:{posixUser|windowsUser}=<user>[:<group>] ");
            }
            String componentName = parts[0];
            String runWithOption = parts[1];
            RunWithInfo runWithInfo = componentToRunWithInfo.computeIfAbsent(componentName, k -> new RunWithInfo());
            switch (runWithOption) {
                case RUN_WITH_OPTION_POSIX_USER:
                    runWithInfo.setPosixUser(entry.getValue());
                    break;
                case RUN_WITH_OPTION_WINDOWS_USER:
                    runWithInfo.setWindowsUser(entry.getValue());
                    break;
                default:
                    throw new IllegalArgumentException("Invalid --runWith option: " + runWithOption);
            }
        }

        for (Map.Entry<String, SystemResourceLimits> mapEntry : systemLimits.entrySet()) {
            String componentName = mapEntry.getKey();
            componentToRunWithInfo.compute(componentName, (k, v) -> {
                if (v == null) {
                    v = new RunWithInfo();
                }
                v.setSystemResourceLimits(mapEntry.getValue());
                return v;
            });
        }
        return componentToRunWithInfo.isEmpty() ? null : componentToRunWithInfo;
    }
}
