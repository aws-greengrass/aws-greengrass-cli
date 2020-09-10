package com.aws.iot.evergreen.cli.adapter.impl;

import com.aws.iot.evergreen.cli.adapter.KernelAdapterIpc;
import com.aws.iot.evergreen.ipc.IPCClient;
import com.aws.iot.evergreen.ipc.IPCClientImpl;
import com.aws.iot.evergreen.ipc.config.KernelIPCClientConfig;
import com.aws.iot.evergreen.ipc.exceptions.IPCClientException;
import com.aws.iot.evergreen.ipc.services.cli.Cli;
import com.aws.iot.evergreen.ipc.services.cli.CliImpl;
import com.aws.iot.evergreen.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.iot.evergreen.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.iot.evergreen.ipc.services.cli.models.ComponentDetails;
import com.aws.iot.evergreen.ipc.services.cli.models.CreateLocalDeploymentRequest;
import com.aws.iot.evergreen.ipc.services.cli.models.GetComponentDetailsRequest;
import com.aws.iot.evergreen.ipc.services.cli.models.GetLocalDeploymentStatusRequest;
import com.aws.iot.evergreen.ipc.services.cli.models.ListComponentsResponse;
import com.aws.iot.evergreen.ipc.services.cli.models.LocalDeployment;
import com.aws.iot.evergreen.ipc.services.cli.models.RestartComponentRequest;
import com.aws.iot.evergreen.ipc.services.cli.models.StopComponentRequest;
import com.aws.iot.evergreen.ipc.services.cli.models.UpdateRecipesAndArtifactsRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;


import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KernelAdapterIpcClientImpl implements KernelAdapterIpc {

    protected static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String CLI_IPC_INFO_FILENAME = "cli_ipc_info";
    private static final String CLI_AUTH_TOKEN = "cli_auth_token";
    private static final String SOCKET_URL = "socket_url";

    private IPCClient ipcClient;
    private Cli cliClient;

    @Inject
    public KernelAdapterIpcClientImpl(@Nullable @Named("ggcRootPath") String root)
            throws InterruptedException, IPCClientException, IOException, URISyntaxException {
        // TODO: When the greengrass-cli is installed in the kernel root path this will derived from the current working
        // directory, instead of an env variable. Until then using env variable.
        // check if root path was passed as an argument to the command line, else fall back to env variable
        // if root path not found then throw exception
        String ggcRootPath = root != null ? root : System.getenv("GGC_ROOT_PATH");
        if (ggcRootPath == null) {
            throw new RuntimeException("GGC root path not configured. Provide ggc root path via cli greengrass-cli --root {PATH} {rest of the arguments} " +
                    "or set the environment variable GGC_ROOT_PATH");
        }
        Path filepath = Paths.get(ggcRootPath).resolve(CLI_IPC_INFO_FILENAME);
        if (!Files.exists(filepath)) {
            throw new RuntimeException("CLI IPC info file not present");
        }
        Map<String, String> ipcInfoMap = OBJECT_MAPPER.readValue(Files.readAllBytes(filepath), HashMap.class);
        String socketUrl = ipcInfoMap.get(SOCKET_URL);
        String token = ipcInfoMap.get(CLI_AUTH_TOKEN);
        URI uri = new URI(socketUrl);
        ipcClient = new IPCClientImpl(KernelIPCClientConfig.builder()
                .hostAddress(uri.getHost())
                .port(uri.getPort())
                .token(token)
                .build());
        cliClient = new CliImpl(ipcClient);
    }

    @Override
    public ComponentDetails getComponentDetails(String componentName)
            throws CliIpcClientException, GenericCliIpcServerException {
        return cliClient.getComponentDetails(GetComponentDetailsRequest.builder()
                .componentName(componentName).build()).getComponentDetails();
    }

    @Override
    public void restartComponent(String componentName) throws GenericCliIpcServerException, CliIpcClientException {
        cliClient.restartComponent(RestartComponentRequest.builder().componentName(componentName).build());
    }

    @Override
    public void stopComponent(String componentName) throws GenericCliIpcServerException, CliIpcClientException {
        cliClient.stopComponent(StopComponentRequest.builder().componentName(componentName).build());
    }

    @Override
    public void updateRecipesAndArtifacts(String recipesDirectoryPath, String artifactsDirectoryPath)
            throws GenericCliIpcServerException, CliIpcClientException {
        UpdateRecipesAndArtifactsRequest updateRecipesAndArtifactsRequest = UpdateRecipesAndArtifactsRequest.builder()
                .recipeDirectoryPath(recipesDirectoryPath)
                .artifactDirectoryPath(artifactsDirectoryPath)
                .build();
        cliClient.updateRecipesAndArtifacts(updateRecipesAndArtifactsRequest);
    }

    @Override
    public String createLocalDeployment(CreateLocalDeploymentRequest createLocalDeploymentRequest)
            throws GenericCliIpcServerException, CliIpcClientException {
        return cliClient.createLocalDeployment(createLocalDeploymentRequest).getDeploymentId();
    }

    @Override
    public List<ComponentDetails> listComponents() throws GenericCliIpcServerException, CliIpcClientException {
        ListComponentsResponse listComponentsResponse = cliClient.listComponents();
        return listComponentsResponse.getComponents();
    }

    @Override
    public LocalDeployment getLocalDeploymentStatus(String deploymentId)
            throws GenericCliIpcServerException, CliIpcClientException {
        GetLocalDeploymentStatusRequest DeploymentStatusRequest =
                GetLocalDeploymentStatusRequest.builder().deploymentId(deploymentId).build();
        return cliClient.getLocalDeploymentStatus(DeploymentStatusRequest).getDeployment();
    }

    @Override
    public List<LocalDeployment> listLocalDeployments()
            throws GenericCliIpcServerException, CliIpcClientException {
        return cliClient.listLocalDeployments().getLocalDeployments();
    }
}
