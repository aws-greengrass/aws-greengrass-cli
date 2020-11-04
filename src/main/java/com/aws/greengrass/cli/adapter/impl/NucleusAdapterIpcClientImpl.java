/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.adapter.impl;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;

import com.aws.greengrass.cli.util.PlatformUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NucleusAdapterIpcClientImpl implements NucleusAdapterIpc {

    protected static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);



    private static final String CLI_IPC_INFO_DIRECTORY = "cli_ipc_info";
    private static final String USER_CLIENT_ID_PREFIX = "user-";
    private static final String GROUP_CLIENT_ID_PREFIX = "group-";
    private static final String CLI_AUTH_TOKEN = "cli_auth_token";
    private static final String DOMAIN_SOCKET_PATH = "domain_socket_path";
    private static final String IPC_SERVER_SOCKET_SYMLINK = "./ipcCliServerSocketPath.socket";
    private static final String HOME_DIR_PREFIX = "~/";
    private static final int DEFAULT_TIMEOUT_IN_SEC = 30;

    private String root;
    private GreengrassCoreIPCClient ipcClient;
    private EventStreamRPCConnection clientConnection;
    private SocketOptions socketOptions;
    private ClientBootstrap clientBootstrap;
    private EventLoopGroup elGroup;

    @Inject
    public NucleusAdapterIpcClientImpl(@Nullable @Named("ggcRootPath") String root) {
        this.root = root;
    }

    @Override
    public ComponentDetails getComponentDetails(String componentName) {

        try {
            GetComponentDetailsRequest request = new GetComponentDetailsRequest();
            request.setComponentName(componentName);
            ComponentDetails componentDetails = getIpcClient().getComponentDetails(request, Optional.empty())
                    .getResponse().get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS).getComponentDetails();
            return componentDetails;
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            //TODO: update when the sdk method signature includes exceptions
            throw new RuntimeException(e);
        } finally {
            close();
        }

    }

    @Override
    public void restartComponent(String componentName) {
        try {
            RestartComponentRequest request = new RestartComponentRequest();
            request.setComponentName(componentName);
            getIpcClient().restartComponent(request, Optional.empty()).getResponse()
                    .get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            //TODO: update when the sdk method signature includes exceptions
            throw new RuntimeException(e);
        } finally {
            close();
        }
    }

    @Override
    public void stopComponent(String componentName) {
        try {
            StopComponentRequest request = new StopComponentRequest();
            request.setComponentName(componentName);
            getIpcClient().stopComponent(request, Optional.empty()).getResponse()
                    .get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            //TODO: update when the sdk method signature includes exceptions
            throw new RuntimeException(e);
        } finally {
            close();
        }
    }

    @Override
    public void updateRecipesAndArtifacts(String recipesDirectoryPath, String artifactsDirectoryPath) {

        try {
            UpdateRecipesAndArtifactsRequest request = new UpdateRecipesAndArtifactsRequest();
            request.setRecipeDirectoryPath(recipesDirectoryPath);
            request.setArtifactsDirectoryPath(artifactsDirectoryPath);
            getIpcClient().updateRecipesAndArtifacts(request, Optional.empty()).getResponse()
                    .get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            //TODO: update when the sdk method signature includes exceptions
            throw new RuntimeException(e);
        }
    }

    @Override
    public LocalDeployment getLocalDeploymentStatus(String deploymentId) {

        try {
            GetLocalDeploymentStatusRequest request = new GetLocalDeploymentStatusRequest();
            request.setDeploymentId(deploymentId);
            GetLocalDeploymentStatusResponseHandler localDeploymentStatus = getIpcClient().getLocalDeploymentStatus(request, Optional.empty());
            LocalDeployment deployment = localDeploymentStatus.getResponse()
                    .get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS).getDeployment();
            return deployment;
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            //TODO: update when the sdk method signature includes exceptions
            throw new RuntimeException(e);
        } finally {
            close();
        }
    }

    @Override
    public List<LocalDeployment> listLocalDeployments() {
        try {
            ListLocalDeploymentsRequest request = new ListLocalDeploymentsRequest();
            ListLocalDeploymentsResponse listLocalDeploymentsResponse = getIpcClient()
                    .listLocalDeployments(request, Optional.empty()).getResponse()
                    .get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
            return listLocalDeploymentsResponse.getLocalDeployments();
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            clientConnection.disconnect();
            //TODO: update when the sdk method signature includes exceptions
            throw new RuntimeException(e);
        } finally {
            close();
        }

    }

    @Override
    public String createLocalDeployment(CreateLocalDeploymentRequest createLocalDeploymentRequest)  {
        try {
            CreateLocalDeploymentResponse createLocalDeploymentResponse =
                    getIpcClient().createLocalDeployment(createLocalDeploymentRequest, Optional.empty()).getResponse()
                            .get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
            return createLocalDeploymentResponse.getDeploymentId();
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            //TODO: update when the sdk method signature includes exceptions
            throw new RuntimeException(e);
        } finally {
            close();
        }

    }

    @Override
    public List<ComponentDetails> listComponents() {
        try {
            ListComponentsRequest request = new ListComponentsRequest();
            ListComponentsResponse listComponentsResponse = getIpcClient().listComponents(request, Optional.empty()).getResponse()
                    .get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
            return listComponentsResponse.getComponents();
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            //TODO: update when the sdk method signature includes exceptions
            throw new RuntimeException(e);
        } finally {
            close();
        }
    }

    private GreengrassCoreIPCClient getIpcClient() {
        if (ipcClient != null) {
            return ipcClient;
        }
        // GG_NEEDS_REVIEW: TODO: When the greengrass-cli is installed in the Greengrass root path this will derived from the current
        // working directory, instead of an env variable. Until then using env variable.
        // check if root path was passed as an argument to the command line, else fall back to env variable
        // if root path not found then throw exception
        String ggcRootPath = root != null ? root : System.getenv("GGC_ROOT_PATH");
        if (ggcRootPath == null) {
            throw new RuntimeException("GGC root path not configured. Provide ggc root path via cli greengrass-cli "
                    + "--ggcRootPath {PATH} {rest of the arguments} " +
                    "or set the environment variable GGC_ROOT_PATH");
        }
        try {
            Map<String, String> ipcInfoMap = OBJECT_MAPPER.readValue(loadCliIpcInfo(ggcRootPath), HashMap.class);
            String domainSocketPath = ipcInfoMap.get(DOMAIN_SOCKET_PATH);
            if (Files.exists(Paths.get(IPC_SERVER_SOCKET_SYMLINK), LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(Paths.get(IPC_SERVER_SOCKET_SYMLINK));
            }
            boolean symlinkCreated = false;
            try {
                Files.createSymbolicLink(Paths.get(IPC_SERVER_SOCKET_SYMLINK), Paths.get(domainSocketPath));
                symlinkCreated = true;
            } catch (IOException e) {
                //Symlink not created, ignoring and using absolute path
            }
            String token = ipcInfoMap.get(CLI_AUTH_TOKEN);

            socketOptions = new SocketOptions();
            socketOptions.connectTimeoutMs = 3000;
            socketOptions.domain = SocketOptions.SocketDomain.LOCAL;
            socketOptions.type = SocketOptions.SocketType.STREAM;

            clientConnection = connectToGGCOverEventStreamIPC(socketOptions, token,
                    symlinkCreated ? IPC_SERVER_SOCKET_SYMLINK : domainSocketPath);

            ipcClient = new GreengrassCoreIPCClient(clientConnection);
            return ipcClient;
        } catch (Exception e) {
            throw new RuntimeException("Unable to create ipc client", e);
        }

    }

    private EventStreamRPCConnection connectToGGCOverEventStreamIPC(SocketOptions socketOptions, String authToken,
                                                                          String ipcServerSocketPath) {
        elGroup = new EventLoopGroup(2);
        clientBootstrap = new ClientBootstrap(elGroup, null);
        final EventStreamRPCConnectionConfig config = new EventStreamRPCConnectionConfig(clientBootstrap, elGroup,
                socketOptions, null, ipcServerSocketPath, 8033,
                GreengrassConnectMessageSupplier.connectMessageSupplier(authToken));
        final CompletableFuture<Void> connected = new CompletableFuture<>();
        final EventStreamRPCConnection connection = new EventStreamRPCConnection(config);

        connection.connect(new EventStreamRPCConnection.LifecycleHandler() {
            //only called on successful connection. That is full on Connect -> ConnectAck(ConnectionAccepted=true)
            @Override
            public void onConnect() {
                connected.complete(null);
            }

            @Override
            public void onDisconnect(int errorCode) {
            }

            //This on error is for any errors that is connection level, including problems during connect()
            @Override
            public boolean onError(Throwable t) {
                connected.completeExceptionally(t);
                return true;    //hints at handler to disconnect due to this error
            }
        });

        try {
            connected.get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
        return connection;

    }

    public static String deTilde(String path) {
        if (path.startsWith(HOME_DIR_PREFIX)) {
            return Paths.get(System.getProperty("user.home")).resolve(path.substring(HOME_DIR_PREFIX.length())).toString();
        }
        return path;
    }

    private byte[] loadCliIpcInfo(String ggcRootPath) throws IOException {
        Path directory = Paths.get(deTilde(ggcRootPath)).resolve(CLI_IPC_INFO_DIRECTORY);

        IOException e = new IOException("Not able to find auth information in directory: " + directory +
                ". Please run CLI as authorized user or group.");

        try {
            return Files.readAllBytes(directory.resolve(USER_CLIENT_ID_PREFIX + PlatformUtils.getEffectiveUID()));
        } catch (IOException ioe) {
            e.addSuppressed(ioe);
        }

        try {
            return Files.readAllBytes(directory.resolve(GROUP_CLIENT_ID_PREFIX + PlatformUtils.getEffectiveGID()));
        } catch (IOException ioe) {
            e.addSuppressed(ioe);
        }

        File[] files = directory.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    return Files.readAllBytes(file.toPath());
                } catch (IOException ioe) {
                    e.addSuppressed(ioe);
                }
            }
        } else {
            e.addSuppressed(new IOException("Unable to list files under: " + directory));
        }
        throw e;
    }

    private void close() {
        try {
            if (clientConnection != null) {
                clientConnection.close();
            }
            if (socketOptions != null) {
                socketOptions.close();
            }
            if (clientBootstrap != null) {
                clientBootstrap.close();
            }
            if (elGroup != null) {
                elGroup.close();
            }
        } catch (Exception e) {
        }
    }
}
