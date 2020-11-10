/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.aws.greengrass.cli.adapter.impl.*;
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
import javax.inject.Inject;

import static com.aws.greengrass.cli.adapter.impl.NucleusAdapterIpcClientImpl.deTilde;

@CommandLine.Command(name = "deployment", resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class, mixinStandardHelpOptions = true)
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
            mixinStandardHelpOptions = true)
    public int create
    (@CommandLine.Option(names = {"-m", "--merge"}, paramLabel = "Component and version") Map<String, String> componentsToMerge,
     @CommandLine.Option(names = {"--remove"}, paramLabel = "Component Names") List<String> componentsToRemove,
     @CommandLine.Option(names = {"-g", "--groupId"}, paramLabel = "group Id") String groupId,
     @CommandLine.Option(names = {"-r", "--recipeDir"}, paramLabel = "Recipe Folder Path") String recipeDir,
     @CommandLine.Option(names = {"-a", "--artifactDir"}, paramLabel = "Artifacts Folder Path") String artifactDir,
     @CommandLine.Option(names = {"--runWith"}, paramLabel = "Component Run With Info") Map<String, String> runWithOptions,
     @CommandLine.Option(names = {"-c", "--update-config"}, paramLabel = "Update configuration") String configUpdate)
            throws IOException {
        if(files!=null) {
            TemplateProcessor tp = new TemplateProcessor(files);
            tp.setGenTemplateDir(NucleusAdapterIpcClientImpl.deTilde(generatedTemplateDirectory));
            tp.setArtifactDir(artifactDir);
            tp.setRecipeDir(recipeDir);
            tp.setWhatToMerge(componentsToMerge);
            if(!tp.build()) {
                return 1;
            }
            artifactDir = tp.getArtifactDir();
            recipeDir = tp.getRecipeDir();
            componentsToMerge = tp.getWhatToMerge();
        }
        // GG_NEEDS_REVIEW: TODO Validate folder exists and folder structure

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
        if(dryrun) {
            System.out.println("This was just a dry run, deployment abandoned.");
            return 0;
        }
        try {
            if (recipeDir != null || artifactDir != null) {
                nucleusAdapterIpc.updateRecipesAndArtifacts(recipeDir, artifactDir);
            }

        CreateLocalDeploymentRequest createLocalDeploymentRequest = new CreateLocalDeploymentRequest();
        createLocalDeploymentRequest.setGroupName(groupId);
        createLocalDeploymentRequest.setComponentToConfiguration(configurationUpdate);
        createLocalDeploymentRequest.setRootComponentVersionsToAdd(componentsToMerge);
        createLocalDeploymentRequest.setRootComponentsToRemove(componentsToRemove);
        createLocalDeploymentRequest.setComponentToRunWithInfo(getComponentToRunWithInfo(runWithOptions));
        String deploymentId = nucleusAdapterIpc.createLocalDeployment(createLocalDeploymentRequest);
        System.out.println("Local deployment has been submitted! Deployment Id: " + deploymentId);
        return 0;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "status",
            description = "Retrieve the status of a deployment", mixinStandardHelpOptions = true)
    public int status(@CommandLine.Option(names = {"-i", "--deploymentId"}, paramLabel = "Deployment Id", required = true) String deploymentId) {

        LocalDeployment status = nucleusAdapterIpc.getLocalDeploymentStatus(deploymentId);
        System.out.printf("%s: %s", status.getDeploymentId(), status.getStatus());
        return 0;
    }

    // GG_NEEDS_REVIEW: TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "list", description = "Retrieve the status of local deployments",
            mixinStandardHelpOptions = true)
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
                throw new IllegalArgumentException("--runWith is not in the format <ComponentName>:<RunWithOption>=<value> ");
            }
            String componentName = parts[0];
            String runWithOption = parts[1];
            RunWithInfo runWithInfo = new RunWithInfo();
            switch (runWithOption) {
                case RUN_WITH_OPTION_POSIX_USER:
                    runWithInfo.setPosixUser(entry.getValue());
                    break;
                default:
                    throw new IllegalArgumentException("Invalid run with option: " + runWithOption);
            }
            componentToRunWithInfo.put(componentName, runWithInfo);
        }
        return componentToRunWithInfo;
    }

}
