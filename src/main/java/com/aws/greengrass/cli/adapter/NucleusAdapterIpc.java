package com.aws.greengrass.cli.adapter;


import software.amazon.awssdk.aws.greengrass.model.ComponentDetails;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.LocalDeployment;

import java.util.List;

public interface NucleusAdapterIpc {

    ComponentDetails getComponentDetails(String componentName);

    void restartComponent(String componentName);

    void stopComponent(String componentName);

    void updateRecipesAndArtifacts(String recipesDirectoryPath, String artifactsDirectoryPath);

    LocalDeployment getLocalDeploymentStatus(String deploymentId);

    List<LocalDeployment> listLocalDeployments();

    String createLocalDeployment(CreateLocalDeploymentRequest createLocalDeploymentRequest);

    List<ComponentDetails> listComponents();

}
