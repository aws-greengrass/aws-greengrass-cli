/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.adapter;


import software.amazon.awssdk.aws.greengrass.model.ComponentDetails;
import software.amazon.awssdk.aws.greengrass.model.CreateDebugPasswordResponse;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.LocalDeployment;

import java.util.List;

public interface NucleusAdapterIpc {

    ComponentDetails getComponentDetails(String componentName);

    void restartComponent(String componentName);

    void stopComponent(String componentName);

    //void updateRecipesAndArtifacts(String recipesDirectoryPath, String artifactsDirectoryPath);

    LocalDeployment getLocalDeploymentStatus(String deploymentId);

    List<LocalDeployment> listLocalDeployments();

    String createLocalDeployment(CreateLocalDeploymentRequest createLocalDeploymentRequest);

    List<ComponentDetails> listComponents();

    CreateDebugPasswordResponse createDebugPassword();

}
