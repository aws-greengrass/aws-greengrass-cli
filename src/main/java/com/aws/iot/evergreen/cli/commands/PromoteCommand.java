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
import com.amazonaws.services.greengrasscomponentmanagement.model.InvalidInputException;
import com.amazonaws.services.greengrasscomponentmanagement.model.ResourceAlreadyExistException;
import com.amazonaws.services.greengrassfleetconfiguration.AWSGreengrassFleetConfiguration;
import com.amazonaws.services.greengrassfleetconfiguration.AWSGreengrassFleetConfigurationClientBuilder;
import com.amazonaws.services.greengrassfleetconfiguration.model.FailureHandlingPolicy;
import com.amazonaws.services.greengrassfleetconfiguration.model.PackageMetaData;
import com.amazonaws.services.greengrassfleetconfiguration.model.PublishConfigurationRequest;
import com.amazonaws.services.greengrassfleetconfiguration.model.PublishConfigurationResult;
import com.amazonaws.services.greengrassfleetconfiguration.model.SetConfigurationRequest;
import com.amazonaws.services.greengrassfleetconfiguration.model.SetConfigurationResult;
import picocli.CommandLine;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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

    @CommandLine.Command(name = "register-component",
            description = "Updates Evergreen applications with provided recipes, artifacts, and runtime parameters")
    public int component(
            @CommandLine.Option(names = {"-r", "--recipe-file"}, paramLabel = "File Path", required = true) String recipeFilePath,
            @CommandLine.Option(names = {"-a", "--artifact-dir"}, paramLabel = "Folder", required = true) String artifactDir,
            @CommandLine.Option(names = {"-c", "--component-name"}, paramLabel = "Component Name") String cname,
            @CommandLine.Option(names = {"-v", "--component-version"}, paramLabel = "Component Version") String cversion
    ) throws IOException {
        AWSGreengrassComponentManagement cmsClient = AWSGreengrassComponentManagementClientBuilder
                .standard().withClientConfiguration(
                        new ClientConfiguration().withRequestTimeout(50000).withClientExecutionTimeout(50000))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        CMS_BETA_ENDPOINT, BETA_REGION)).build();
        // 2.1. create-component
        ByteBuffer recipeBuf;
        recipeBuf = ByteBuffer.wrap(Files.readAllBytes(Paths.get(recipeFilePath)));
        String componentName = null;
        String componentVersion = null;
        List<String> artifacts = null;

        CreateComponentRequest createComponentRequest = new CreateComponentRequest().withRecipe(recipeBuf);
        try {
            CreateComponentResult createComponentResult = cmsClient.createComponent(createComponentRequest);
            System.out.println("Created component " + createComponentResult);
            componentName = createComponentResult.getComponentName();
            componentVersion = createComponentResult.getComponentVersion();
            artifacts = createComponentResult.getArtifacts();
        } catch (ResourceAlreadyExistException e) {
            // TODO: seeing behaviors of duplicate requests in this call: getting failure message and creating
            // components successfully at the same time.
            // Not sure why ResourceAlreadyExistException on a brand new component. Need to follow up.
            // As we know this component is created for the first time, we will just proceed with a workaround.

            // com.amazonaws.services.greengrasscomponentmanagement.model.ResourceAlreadyExistException:
            // Package (HuiApp:1.0.0) already exist (Service: AWSGreengrassComponentManagement; Status Code: 409;
            // Error Code: ResourceAlreadyExistException; Request ID: c1f4b9b8-54e0-4f7e-8221-aea4b7359823; Proxy: null)
            System.out.println(e);

            componentName = cname;
            componentVersion = cversion;
            artifacts = Arrays.asList(new File(artifactDir).list());
        }
        Objects.requireNonNull(componentName);
        Objects.requireNonNull(componentVersion);
        Objects.requireNonNull(artifacts);

        // 2.2. create-component-artifact-upload-url
        // Assuming all artifacts are referenced by file name in the recipe
        for (String artifactName : artifacts) {
            Path artifact = Paths.get(artifactDir).resolve(artifactName);
            System.out.println("Uploading [" + artifactName + "] from file://" + artifact.toAbsolutePath().toString());
            CreateComponentArtifactUploadUrlRequest artifactUploadUrlRequest = new CreateComponentArtifactUploadUrlRequest()
                    .withArtifactName(artifactName)
                    .withComponentName(componentName)
                    .withComponentVersion(componentVersion);
            CreateComponentArtifactUploadUrlResult artifactUploadUrlResult = cmsClient
                    .createComponentArtifactUploadUrl(artifactUploadUrlRequest);
            URL s3PreSignedURL = new URL(artifactUploadUrlResult.getUrl());
            HttpURLConnection connection = (HttpURLConnection) s3PreSignedURL.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("PUT");
            connection.connect();
            // read artifact from file
            BufferedOutputStream bos = new BufferedOutputStream(connection.getOutputStream());
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(artifact.toFile()));
            int i;
            // read byte by byte until end of stream
            while ((i = bis.read()) > 0) {
                bos.write(i);
            }
            bis.close();
            bos.close();
            System.out.println(connection.getResponseMessage());
            System.out.println("Uploaded");
        }

        // 2.3. commit-component
        try {
            CommitComponentRequest commitComponentRequest = new CommitComponentRequest().withComponentName(componentName).withComponentVersion(componentVersion);
            CommitComponentResult commitComponentResult = cmsClient.commitComponent(commitComponentRequest);
            System.out.println("Committed component " + commitComponentResult);
        } catch (InvalidInputException e) {
            // TODO: also seeing behaviors of duplicate requests in this call: getting failure message and committing
            // components successfully at the same time.

            // com.amazonaws.services.greengrasscomponentmanagement.model.InvalidInputException:
            // Package arn:aws:greengrass:us-east-1:634967961153:Package/HuiTestApp:1.0.0 is not in draft status, and
            // only the draft package is allowed to be committed (Service: AWSGreengrassComponentManagement;
            // Status Code: 400; Error Code: InvalidInputException; Request ID: 4aaa7de4-c94d-41c2-93d4-4134846bbc31;
            // Proxy: null)
            System.out.println(e);
        }
        return 0;
    }

    @CommandLine.Command(name = "publish-configuration", description = "Deploy via Fleet Configuration Service")
    public int configuration(
            @CommandLine.Option(names = {"-n", "--thing-group-name"}, paramLabel = "thing-group-name", required = true) String thingGroupName) {
        AWSGreengrassFleetConfiguration fcsClient = AWSGreengrassFleetConfigurationClientBuilder
                .standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        FCS_BETA_ENDPOINT, BETA_REGION)).build();
        // 3.1. set-configuration
        // TODO: Update configuration
        SetConfigurationRequest setRequest = new SetConfigurationRequest()
                .withTargetName(thingGroupName)
                .withTargetType(THING_GROUP_TARGET_TYPE)
                .withFailureHandlingPolicy(FailureHandlingPolicy.DO_NOTHING)
                .addPackagesEntry("HuiTestApp", new PackageMetaData().withRootComponent(true).withVersion("1.0.0"));
        SetConfigurationResult setResult = fcsClient.setConfiguration(setRequest);
        System.out.println("Created configuration " + setResult);

        // 3.2. publish-configuration
        PublishConfigurationRequest publishRequest = new PublishConfigurationRequest()
                .withTargetName(setRequest.getTargetName())
                .withTargetType(setRequest.getTargetType())
                .withRevisionId(setResult.getRevisionId());
        PublishConfigurationResult publishResult = fcsClient.publishConfiguration(publishRequest);
        System.out.println("Published configuration " + publishResult);
        return 0;
    }

}
