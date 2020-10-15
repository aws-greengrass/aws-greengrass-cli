package com.aws.greengrass.cli.adapter.impl;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.aws.greengrass.ipc.IPCClient;
import com.aws.greengrass.ipc.IPCClientImpl;
import com.aws.greengrass.ipc.config.KernelIPCClientConfig;
import com.aws.greengrass.ipc.services.cli.Cli;
import com.aws.greengrass.ipc.services.cli.CliImpl;
import com.aws.greengrass.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.greengrass.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.greengrass.ipc.services.cli.models.ComponentDetails;
import com.aws.greengrass.ipc.services.cli.models.CreateLocalDeploymentRequest;
import com.aws.greengrass.ipc.services.cli.models.GetComponentDetailsRequest;
import com.aws.greengrass.ipc.services.cli.models.GetLocalDeploymentStatusRequest;
import com.aws.greengrass.ipc.services.cli.models.ListComponentsResponse;
import com.aws.greengrass.ipc.services.cli.models.LocalDeployment;
import com.aws.greengrass.ipc.services.cli.models.RestartComponentRequest;
import com.aws.greengrass.ipc.services.cli.models.StopComponentRequest;
import com.aws.greengrass.ipc.services.cli.models.UpdateRecipesAndArtifactsRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

public class NucleusAdapterIpcClientImpl implements NucleusAdapterIpc {

    protected static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String CLI_IPC_INFO_FILENAME = "cli_ipc_info";
    private static final String CLI_AUTH_TOKEN = "cli_auth_token";
    private static final String SOCKET_URL = "socket_url";
    private static final String HOME_DIR_PREFIX = "~/";

    private String root;
    private Cli cliClient;

    @Inject
    public NucleusAdapterIpcClientImpl(@Nullable @Named("ggcRootPath") String root) {
        this.root = root;
    }

    @Override
    public ComponentDetails getComponentDetails(String componentName)
            throws CliIpcClientException, GenericCliIpcServerException {
        return getCliClient().getComponentDetails(GetComponentDetailsRequest.builder()
                .componentName(componentName).build()).getComponentDetails();
    }

    @Override
    public void restartComponent(String componentName) throws GenericCliIpcServerException, CliIpcClientException {
        getCliClient().restartComponent(RestartComponentRequest.builder().componentName(componentName).build());
    }

    @Override
    public void stopComponent(String componentName) throws GenericCliIpcServerException, CliIpcClientException {
        getCliClient().stopComponent(StopComponentRequest.builder().componentName(componentName).build());
    }

    @Override
    public void updateRecipesAndArtifacts(String recipesDirectoryPath, String artifactsDirectoryPath)
            throws GenericCliIpcServerException, CliIpcClientException {
        UpdateRecipesAndArtifactsRequest updateRecipesAndArtifactsRequest = UpdateRecipesAndArtifactsRequest.builder()
                .recipeDirectoryPath(Paths.get(deTilde(recipesDirectoryPath)).toAbsolutePath().toString())
                .artifactDirectoryPath(Paths.get(deTilde(artifactsDirectoryPath)).toAbsolutePath().toString())
                .build();
        getCliClient().updateRecipesAndArtifacts(updateRecipesAndArtifactsRequest);
    }

    @Override
    public String createLocalDeployment(CreateLocalDeploymentRequest createLocalDeploymentRequest)
            throws GenericCliIpcServerException, CliIpcClientException {
        return getCliClient().createLocalDeployment(createLocalDeploymentRequest).getDeploymentId();
    }

    @Override
    public List<ComponentDetails> listComponents() throws GenericCliIpcServerException, CliIpcClientException {
        ListComponentsResponse listComponentsResponse = getCliClient().listComponents();
        return listComponentsResponse.getComponents();
    }

    @Override
    public LocalDeployment getLocalDeploymentStatus(String deploymentId)
            throws GenericCliIpcServerException, CliIpcClientException {
        GetLocalDeploymentStatusRequest DeploymentStatusRequest =
                GetLocalDeploymentStatusRequest.builder().deploymentId(deploymentId).build();
        return getCliClient().getLocalDeploymentStatus(DeploymentStatusRequest).getDeployment();
    }

    @Override
    public List<LocalDeployment> listLocalDeployments()
            throws GenericCliIpcServerException, CliIpcClientException {
        return getCliClient().listLocalDeployments().getLocalDeployments();
    }

    private Cli getCliClient() {
        if (cliClient != null) {
            return cliClient;
        }
        // TODO: When the greengrass-cli is installed in the Greengrass root path this will derived from the current
        // working directory, instead of an env variable. Until then using env variable.
        // check if root path was passed as an argument to the command line, else fall back to env variable
        // if root path not found then throw exception
        String ggcRootPath = root != null ? root : System.getenv("GGC_ROOT_PATH");
        if (ggcRootPath == null) {
            throw new RuntimeException("GGC root path not configured. Provide ggc root path via cli greengrass-cli "
                    + "--ggcRootPath {PATH} {rest of the arguments} " +
                    "or set the environment variable GGC_ROOT_PATH");
        }
        Path filepath = Paths.get(deTilde(ggcRootPath)).resolve(CLI_IPC_INFO_FILENAME);
        if (!Files.exists(filepath)) {
            throw new RuntimeException("CLI IPC info file not present at " + filepath);
        }
        try {
            Map<String, String> ipcInfoMap = OBJECT_MAPPER.readValue(Files.readAllBytes(filepath), HashMap.class);
            String socketUrl = ipcInfoMap.get(SOCKET_URL);
            String token = ipcInfoMap.get(CLI_AUTH_TOKEN);
            URI uri = new URI(socketUrl);
            IPCClient ipcClient = new IPCClientImpl(KernelIPCClientConfig.builder()
                    .hostAddress(uri.getHost())
                    .port(uri.getPort())
                    .token(token)
                    .build());
            cliClient = new CliImpl(ipcClient);
        } catch (Exception e) {
            System.out.println("Unable to create cli client " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
        return cliClient;
    }

    private String deTilde(String path) {
        if (path.startsWith(HOME_DIR_PREFIX)) {
            return Paths.get(System.getProperty("user.home"))
                    .resolve(path.substring(HOME_DIR_PREFIX.length())).toString();
        }
        return path;
    }
}
