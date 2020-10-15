package com.aws.greengrass.cli.adapter;

import com.aws.greengrass.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.greengrass.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.greengrass.ipc.services.cli.models.ComponentDetails;
import com.aws.greengrass.ipc.services.cli.models.CreateLocalDeploymentRequest;
import com.aws.greengrass.ipc.services.cli.models.LocalDeployment;

import java.util.List;

public interface NucleusAdapterIpc {

    ComponentDetails getComponentDetails(String componentName)
            throws GenericCliIpcServerException, CliIpcClientException;

    void restartComponent(String componentName) throws GenericCliIpcServerException, CliIpcClientException;

    void stopComponent(String componentName) throws GenericCliIpcServerException, CliIpcClientException;

    void updateRecipesAndArtifacts(String recipesDirectoryPath, String artifactsDirectoryPath)
            throws GenericCliIpcServerException, CliIpcClientException;

    LocalDeployment getLocalDeploymentStatus(String deploymentId)
            throws GenericCliIpcServerException, CliIpcClientException;

    List<LocalDeployment> listLocalDeployments() throws GenericCliIpcServerException, CliIpcClientException;

    String createLocalDeployment(CreateLocalDeploymentRequest createLocalDeploymentRequest)
            throws GenericCliIpcServerException, CliIpcClientException;

    List<ComponentDetails> listComponents() throws GenericCliIpcServerException, CliIpcClientException;

}
