package com.aws.iot.evergreen.cli.adapter;

import com.aws.iot.evergreen.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.iot.evergreen.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.iot.evergreen.ipc.services.cli.models.ComponentDetails;
import com.aws.iot.evergreen.ipc.services.cli.models.CreateLocalDeploymentRequest;
import com.aws.iot.evergreen.ipc.services.cli.models.LocalDeployment;

import java.util.List;

public interface KernelAdapterIpc {

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
