/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.cli.commands;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.greengrasscomponentmanagement.AWSGreengrassComponentManagement;
import com.amazonaws.services.greengrasscomponentmanagement.AWSGreengrassComponentManagementClientBuilder;
import com.amazonaws.services.greengrasscomponentmanagement.model.CommitComponentRequest;
import com.amazonaws.services.greengrasscomponentmanagement.model.CommitComponentResult;
import com.amazonaws.services.greengrasscomponentmanagement.model.CreateComponentArtifactUploadUrlRequest;
import com.amazonaws.services.greengrasscomponentmanagement.model.CreateComponentArtifactUploadUrlResult;
import com.amazonaws.services.greengrasscomponentmanagement.model.CreateComponentRequest;
import com.amazonaws.services.greengrasscomponentmanagement.model.CreateComponentResult;
import com.amazonaws.services.greengrassfleetconfiguration.AWSGreengrassFleetConfiguration;
import com.amazonaws.services.greengrassfleetconfiguration.AWSGreengrassFleetConfigurationClientBuilder;
import com.amazonaws.services.greengrassfleetconfiguration.model.FailureHandlingPolicy;
import com.amazonaws.services.greengrassfleetconfiguration.model.PackageMetaData;
import com.amazonaws.services.greengrassfleetconfiguration.model.PublishConfigurationRequest;
import com.amazonaws.services.greengrassfleetconfiguration.model.PublishConfigurationResult;
import com.amazonaws.services.greengrassfleetconfiguration.model.SetConfigurationRequest;
import com.amazonaws.services.greengrassfleetconfiguration.model.SetConfigurationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import picocli.CommandLine;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Command for promoting local apps to Component Management Service, and trigger deployments via Fleet Configuration
 * Service.
 */
@CommandLine.Command(name = "promote", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages", subcommands =
        CommandLine.HelpCommand.class)
public class PromoteCommand extends BaseCommand {
    private static final String CMS_BETA_ENDPOINT = "https://3w5ajog718.execute-api.us-east-1.amazonaws.com/Beta/";
    private static final String FCS_BETA_ENDPOINT = "https://aqzw8qdn5l.execute-api.us-east-1.amazonaws.com/Beta/";
    private static final String BETA_REGION = "us-east-1";

    private static final String THING_GROUP_TARGET_TYPE = "thinggroup";
    private static final String COMPONENT_DIVIDER = "=====PROMOTE COMPONENT=====";
    private static final String ARTIFACT_DIVIDER = "-----PROMOTE ARTIFACT------";

    @CommandLine.Command(name = "component",
            description = "Updates Evergreen applications with provided recipes, artifacts, and runtime parameters")
    public int component(
            @CommandLine.Option(names = {"-r", "--recipe-file"}, paramLabel = "File Path", required = true) List<String> recipeFiles,
            @CommandLine.Option(names = {"-a", "--artifact-dir"}, paramLabel = "Folder", required = true) String artifactDir
    ) throws IOException {

        AWSGreengrassComponentManagement cmsClient =
                AWSGreengrassComponentManagementClientBuilder.standard().withClientConfiguration(
                new ClientConfiguration().withRequestTimeout(50000).withClientExecutionTimeout(50000))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        CMS_BETA_ENDPOINT, BETA_REGION)).build();
        for (String recipeFilePath : recipeFiles) {
            System.out.println(COMPONENT_DIVIDER);
            promoteComponent(cmsClient, recipeFilePath, artifactDir);
        }
        return 0;
    }

    @CommandLine.Command(name = "fleet-configuration", description = "Deploy via Fleet Configuration Service")
    public int configuration(
            @CommandLine.Option(names = {"-n", "--thing-group-name"}, paramLabel = "thing-group-name",
                    required = true) String thingGroupName,
            @CommandLine.Option(names = {"-c", "--components"}, paramLabel = "Component and pinned version",
                    required = true) Map<String, String> components,
            @CommandLine.Option(names = {"-f", "--failure-handling-policy"}, paramLabel = "Failure Handling Policy",
                    description = "Valid values: ${COMPLETION-CANDIDATES}", defaultValue = "DO_NOTHING")
                    FailureHandlingPolicy policy,
            @CommandLine.Option(names = {"-p", "--parameters"}, paramLabel = "Key Value Pairs")
                    Map<String, String> parameters
    ) throws JsonProcessingException {
        Map<String, Map<String, Object>> componentNameToConfig = ComponentCommand.convertParameters(parameters);

        AWSGreengrassFleetConfiguration fcsClient = AWSGreengrassFleetConfigurationClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        FCS_BETA_ENDPOINT, BETA_REGION)).build();
        // 3.1. set-configuration
        SetConfigurationRequest setRequest = new SetConfigurationRequest()
                .withTargetName(thingGroupName)
                .withTargetType(THING_GROUP_TARGET_TYPE)
                .withFailureHandlingPolicy(policy);
        for (Map.Entry<String, Map<String, Object>> entry : componentNameToConfig.entrySet()) {
            String componentName = entry.getKey();
            PackageMetaData metaData = new PackageMetaData();
            if (components.containsKey(componentName)) {
                metaData.withRootComponent(true).withVersion(components.get(componentName));
            }
            metaData.withConfiguration(ComponentCommand.SERIALIZER.writeValueAsString(entry.getValue()));
            setRequest.addPackagesEntry(componentName, metaData);
        }

        components.keySet().removeAll(componentNameToConfig.keySet());
        for (Map.Entry<String, String> entry : components.entrySet()) {
            setRequest.addPackagesEntry(entry.getKey(),
                    new PackageMetaData().withRootComponent(true).withVersion(entry.getValue()));
        }

        SetConfigurationResult setResult = fcsClient.setConfiguration(setRequest);
        System.out.println("Created configuration " + setResult);

        // 3.2. publish-configuration
        PublishConfigurationRequest publishRequest = new PublishConfigurationRequest()
                .withTargetName(setRequest.getTargetName())
                .withTargetType(setRequest.getTargetType())
                .withRevisionId(setResult.getRevisionId());
        PublishConfigurationResult publishResult = fcsClient.publishConfiguration(publishRequest);
        System.out.println("\nPublished configuration " + publishResult);
        return 0;
    }

    private void promoteComponent(AWSGreengrassComponentManagement cmsClient, String recipeFilePath,
                                  String artifactDir) throws IOException {
        // 2.1. create-component
        ByteBuffer recipeBuf = ByteBuffer.wrap(Files.readAllBytes(Paths.get(recipeFilePath)));

        CreateComponentRequest createComponentRequest = new CreateComponentRequest().withRecipe(recipeBuf);
        CreateComponentResult createComponentResult = cmsClient.createComponent(createComponentRequest);

        System.out.println("Created component " + createComponentResult);
        String componentName = createComponentResult.getComponentName();
        String componentVersion = createComponentResult.getComponentVersion();
        // TODO: follow up whether the result should contain artifacts list
        // List<String> artifacts = createComponentResult.getArtifacts();
        CreateComponentArtifactUploadUrlRequest artifactUploadUrlRequest = new CreateComponentArtifactUploadUrlRequest()
                .withComponentName(componentName)
                .withComponentVersion(componentVersion);
        Path artifactDirPath = Paths.get(artifactDir, componentName, componentVersion);
        try {
            File[] artifacts = artifactDirPath.toFile().listFiles();

            // 2.2. create-component-artifact-upload-url
            // Assuming all artifacts are referenced by file name in the recipe
            for (File artifact : artifacts) {
                if (skipArtifactUpload(artifact)) {
                    continue;
                }
                uploadArtifact(cmsClient, artifact, artifactUploadUrlRequest.withArtifactName(artifact.getName()));
            }
        } catch (NullPointerException e) {
            System.out.println("Skip artifact upload. No artifacts found at " + artifactDirPath.toAbsolutePath().toString());
        }

        // 2.3. commit-component
        CommitComponentRequest commitComponentRequest = new CommitComponentRequest().withComponentName(componentName)
                .withComponentVersion(componentVersion);
        CommitComponentResult commitComponentResult = cmsClient.commitComponent(commitComponentRequest);
        System.out.println("\nCommitted component " + commitComponentResult);
    }

    private boolean skipArtifactUpload(File artifact) {
        if (artifact.getName().equals(".DS_Store") || artifact.isDirectory()) {
            System.out.println("Skip artifact upload. Not a regular file: " + artifact.getAbsolutePath());
            return true;
        }
        return false;
    }

    private void uploadArtifact(AWSGreengrassComponentManagement cmsClient, File artifact,
                                CreateComponentArtifactUploadUrlRequest artifactUploadUrlRequest) throws IOException {
        System.out.println(ARTIFACT_DIVIDER);
        System.out.println("Uploading [" + artifact.getName() + "] from file://" + artifact.getAbsolutePath());
        CreateComponentArtifactUploadUrlResult artifactUploadUrlResult = cmsClient
                .createComponentArtifactUploadUrl(artifactUploadUrlRequest);
        URL s3PreSignedURL = new URL(artifactUploadUrlResult.getUrl());
        HttpURLConnection connection = (HttpURLConnection) s3PreSignedURL.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("PUT");
        connection.connect();
        // read artifact from file
        try (BufferedOutputStream bos = new BufferedOutputStream(connection.getOutputStream())) {
            long length = Files.copy(artifact.toPath(), bos);
            System.out.println("File size: " + length);
        }
        System.out.println("Upload " + connection.getResponseMessage());
    }
}
