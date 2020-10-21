package com.aws.greengrass.cli.adapter.impl;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.aws.greengrass.GetComponentDetailsResponseHandler;
import software.amazon.awssdk.aws.greengrass.GetLocalDeploymentStatusResponseHandler;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.model.ComponentDetails;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentResponse;
import software.amazon.awssdk.aws.greengrass.model.GetComponentDetailsRequest;
import software.amazon.awssdk.aws.greengrass.model.GetLocalDeploymentStatusRequest;
import software.amazon.awssdk.aws.greengrass.model.ListComponentsRequest;
import software.amazon.awssdk.aws.greengrass.model.ListComponentsResponse;
import software.amazon.awssdk.aws.greengrass.model.ListLocalDeploymentsRequest;
import software.amazon.awssdk.aws.greengrass.model.ListLocalDeploymentsResponse;
import software.amazon.awssdk.aws.greengrass.model.LocalDeployment;
import software.amazon.awssdk.aws.greengrass.model.RestartComponentRequest;
import software.amazon.awssdk.aws.greengrass.model.StopComponentRequest;
import software.amazon.awssdk.aws.greengrass.model.UpdateRecipesAndArtifactsRequest;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnectionConfig;
import software.amazon.awssdk.eventstreamrpc.GreengrassConnectMessageSupplier;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class NucleusAdapterIpcClientImpl implements NucleusAdapterIpc {

    protected static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String CLI_IPC_INFO_FILENAME = "cli_ipc_info";
    private static final String CLI_AUTH_TOKEN = "cli_auth_token";
    private static final String DOMAIN_SOCKET_PATH = "domain_socket_path";
    private static final String HOME_DIR_PREFIX = "~/";

    private String root;
    private GreengrassCoreIPCClient ipcClient;

    public NucleusAdapterIpcClientImpl(String root) {
        this.root = root;
    }

    @Override
    public ComponentDetails getComponentDetails(String componentName) {

        try {
            GetComponentDetailsRequest request = new GetComponentDetailsRequest();
            request.setComponentName(componentName);
            GetComponentDetailsResponseHandler componentDetails = getIpcClient().getComponentDetails(request, Optional.empty());
            software.amazon.awssdk.aws.greengrass.model.ComponentDetails componentDetails1 = componentDetails.getResponse().get().getComponentDetails();
            return componentDetails1;
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    @Override
    public void restartComponent(String componentName) {
        try {
            RestartComponentRequest request = new RestartComponentRequest();
            request.setComponentName(componentName);
            getIpcClient().restartComponent(request, Optional.empty()).getResponse().get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stopComponent(String componentName) {
        try {
            StopComponentRequest request = new StopComponentRequest();
            request.setComponentName(componentName);
            getIpcClient().stopComponent(request, Optional.empty()).getResponse().get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateRecipesAndArtifacts(String recipesDirectoryPath, String artifactsDirectoryPath) {

        try {
            UpdateRecipesAndArtifactsRequest request = new UpdateRecipesAndArtifactsRequest();
            request.setRecipeDirectoryPath(recipesDirectoryPath);
            request.setArtifactsDirectoryPath(artifactsDirectoryPath);
            getIpcClient().updateRecipesAndArtifacts(request, Optional.empty()).getResponse().get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public LocalDeployment getLocalDeploymentStatus(String deploymentId) {

        try {
            GetLocalDeploymentStatusRequest request = new GetLocalDeploymentStatusRequest();
            request.setDeploymentId(deploymentId);
            GetLocalDeploymentStatusResponseHandler localDeploymentStatus = getIpcClient().getLocalDeploymentStatus(request, Optional.empty());
            LocalDeployment deployment = localDeploymentStatus.getResponse().get().getDeployment();
            return deployment;
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<LocalDeployment> listLocalDeployments() {
        try {
            ListLocalDeploymentsRequest request = new ListLocalDeploymentsRequest();
            ListLocalDeploymentsResponse listLocalDeploymentsResponse = getIpcClient().listLocalDeployments(request, Optional.empty()).getResponse().get();
            return listLocalDeploymentsResponse.getLocalDeployments();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    @Override
    public String createLocalDeployment(CreateLocalDeploymentRequest createLocalDeploymentRequest)  {
        try {
            CreateLocalDeploymentResponse createLocalDeploymentResponse =
                    getIpcClient().createLocalDeployment(createLocalDeploymentRequest, Optional.empty()).getResponse().get();
            return createLocalDeploymentResponse.getDeploymentId();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    @Override
    public List<ComponentDetails> listComponents() {
        try {
            ListComponentsRequest request = new ListComponentsRequest();
            ListComponentsResponse listComponentsResponse = getIpcClient().listComponents(request, Optional.empty()).getResponse().get();
            return listComponentsResponse.getComponents();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private GreengrassCoreIPCClient getIpcClient() {
        if (ipcClient != null) {
            return ipcClient;
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
            String domainSocketPath = ipcInfoMap.get(DOMAIN_SOCKET_PATH);
            String token = ipcInfoMap.get(CLI_AUTH_TOKEN);

            SocketOptions socketOptions = new SocketOptions();
            socketOptions.connectTimeoutMs = 3000;
            socketOptions.domain = SocketOptions.SocketDomain.LOCAL;
            socketOptions.type = SocketOptions.SocketType.STREAM;

            final EventStreamRPCConnection clientConnection = connectToGGCOverEventStreamIPC(socketOptions, token, domainSocketPath);

            GreengrassCoreIPCClient greengrassCoreIPCClient = new GreengrassCoreIPCClient(clientConnection);
            ipcClient = greengrassCoreIPCClient;
            return greengrassCoreIPCClient;
        } catch (Exception e) {
            throw new RuntimeException("Unable to create cli client", e);
        }

    }

    public static EventStreamRPCConnection connectToGGCOverEventStreamIPC(SocketOptions socketOptions, String authToken,
                                                                          String ipcServerSocketPath)  {

        try (EventLoopGroup elGroup = new EventLoopGroup(1);
             ClientBootstrap clientBootstrap = new ClientBootstrap(elGroup, null)) {

            final EventStreamRPCConnectionConfig config = new EventStreamRPCConnectionConfig(clientBootstrap, elGroup,
                    socketOptions, null, ipcServerSocketPath, 8033,
                    GreengrassConnectMessageSupplier.connectMessageSupplier(authToken));
            final CompletableFuture<Void> connected = new CompletableFuture<>();
            final EventStreamRPCConnection connection = new EventStreamRPCConnection(config);
            final boolean disconnected[] = {false};
            final int disconnectedCode[] = {-1};
            //this is a bit cumbersome but does not prevent a convenience wrapper from exposing a sync
            //connect() or a connect() that returns a CompletableFuture that errors
            //this could be wrapped by utility methods to provide a more
            connection.connect(new EventStreamRPCConnection.LifecycleHandler() {
                //only called on successful connection. That is full on Connect -> ConnectAck(ConnectionAccepted=true)
                @Override
                public void onConnect() {
                    connected.complete(null);
                }

                @Override
                public void onDisconnect(int errorCode) {
                    disconnected[0] = true;
                    disconnectedCode[0] = errorCode;
                }

                //This on error is for any errors that is connection level, including problems during connect()
                @Override
                public boolean onError(Throwable t) {
                    connected.completeExceptionally(t);
                    return true;    //hints at handler to disconnect due to this error
                }
            });
            try {
                connected.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            return connection;
        }
    }


    private String deTilde(String path) {
        if (path.startsWith(HOME_DIR_PREFIX)) {
            return Paths.get(System.getProperty("user.home"))
                    .resolve(path.substring(HOME_DIR_PREFIX.length())).toString();
        }
        return path;
    }


//    public static void main(String[] args) {
//        NucleusAdapterIpcClientImpl impl = new NucleusAdapterIpcClientImpl("/Users/faham/.evergreen");
//        List<ComponentDetails> c =  impl.listComponents();
//        System.out.println(c.size());
//    }
}
