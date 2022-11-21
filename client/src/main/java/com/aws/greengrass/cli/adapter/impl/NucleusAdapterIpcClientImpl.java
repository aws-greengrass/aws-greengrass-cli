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
import software.amazon.awssdk.aws.greengrass.SubscribeToIoTCoreResponseHandler;
import software.amazon.awssdk.aws.greengrass.SubscribeToTopicResponseHandler;
import software.amazon.awssdk.aws.greengrass.model.BinaryMessage;
import software.amazon.awssdk.aws.greengrass.model.ComponentDetails;
import software.amazon.awssdk.aws.greengrass.model.CreateDebugPasswordRequest;
import software.amazon.awssdk.aws.greengrass.model.CreateDebugPasswordResponse;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentResponse;
import software.amazon.awssdk.aws.greengrass.model.GetComponentDetailsRequest;
import software.amazon.awssdk.aws.greengrass.model.GetLocalDeploymentStatusRequest;
import software.amazon.awssdk.aws.greengrass.model.IoTCoreMessage;
import software.amazon.awssdk.aws.greengrass.model.ListComponentsRequest;
import software.amazon.awssdk.aws.greengrass.model.ListComponentsResponse;
import software.amazon.awssdk.aws.greengrass.model.ListLocalDeploymentsRequest;
import software.amazon.awssdk.aws.greengrass.model.ListLocalDeploymentsResponse;
import software.amazon.awssdk.aws.greengrass.model.LocalDeployment;
import software.amazon.awssdk.aws.greengrass.model.PublishMessage;
import software.amazon.awssdk.aws.greengrass.model.PublishToIoTCoreRequest;
import software.amazon.awssdk.aws.greengrass.model.PublishToTopicRequest;
import software.amazon.awssdk.aws.greengrass.model.QOS;
import software.amazon.awssdk.aws.greengrass.model.RestartComponentRequest;
import software.amazon.awssdk.aws.greengrass.model.StopComponentRequest;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToIoTCoreRequest;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToIoTCoreResponse;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToTopicRequest;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToTopicResponse;
import software.amazon.awssdk.aws.greengrass.model.SubscriptionResponseMessage;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnectionConfig;
import software.amazon.awssdk.eventstreamrpc.GreengrassConnectMessageSupplier;
import software.amazon.awssdk.eventstreamrpc.StreamResponseHandler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

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
    private static final int DEFAULT_TIMEOUT_IN_SEC = 60;

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
    public void restartComponent(String... componentNames) {
        try {
            for (String componentName : componentNames) {
                RestartComponentRequest request = new RestartComponentRequest();
                request.setComponentName(componentName);
                getIpcClient().restartComponent(request, Optional.empty()).getResponse()
                        .get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
            }
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            //TODO: update when the sdk method signature includes exceptions
            throw new RuntimeException(e);
        } finally {
            close();
        }
    }

    @Override
    public void stopComponent(String... componentNames) {
        try {
            for (String componentName : componentNames) {
                StopComponentRequest request = new StopComponentRequest();
                request.setComponentName(componentName);
                getIpcClient().stopComponent(request, Optional.empty()).getResponse()
                        .get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
            }
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            //TODO: update when the sdk method signature includes exceptions
            throw new RuntimeException(e);
        } finally {
            close();
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

    @Override
    public CreateDebugPasswordResponse createDebugPassword() {
        CreateDebugPasswordRequest request = new CreateDebugPasswordRequest();
        try {
            return getIpcClient().createDebugPassword(request, Optional.empty()).getResponse()
                    .get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            close();
        }
    }

    @Override
    public void publishToTopic(String topicName, String message) {
        try {
            PublishToTopicRequest publishToTopicRequest = new PublishToTopicRequest();
            PublishMessage publishMessage = new PublishMessage();
            BinaryMessage binaryMessage = new BinaryMessage();
            binaryMessage.setMessage(message.getBytes(StandardCharsets.UTF_8));
            publishMessage.setBinaryMessage(binaryMessage);
            publishToTopicRequest.setPublishMessage(publishMessage);
            publishToTopicRequest.setTopic(topicName);
            getIpcClient().publishToTopic(publishToTopicRequest, Optional.empty());
        } finally {
            close();
        }
    }

    @Override
    public void publishToIoTCore(String topicName, String message, String qos) {
        try {
            QOS qos1 = QOS.get(qos);
            PublishToIoTCoreRequest publishToIoTCoreRequest = new PublishToIoTCoreRequest();
            publishToIoTCoreRequest.setTopicName(topicName);
            publishToIoTCoreRequest.setPayload(message.getBytes(StandardCharsets.UTF_8));
            publishToIoTCoreRequest.setQos(qos1);
            getIpcClient().publishToIoTCore(publishToIoTCoreRequest, Optional.empty());
        } finally {
            close();
        }
    }

    @Override
    public void subscribeToTopic(String topic) {
        try {
            StreamResponseHandler<SubscriptionResponseMessage> streamResponseHandler =
                    new SubscriptionResponseHandler(topic);
            SubscribeToTopicResponseHandler responseHandler =
                    subscribeToTopic(getIpcClient(), topic, streamResponseHandler);
            CompletableFuture<SubscribeToTopicResponse> futureResponse =
                    responseHandler.getResponse();
            try {
                futureResponse.get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
                System.out.println("Successfully subscribed to topic: " + topic);
            } catch (TimeoutException e) {
                System.err.println("Timeout occurred while subscribing to topic: " + topic);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof UnauthorizedError) {
                    System.err.println("Unauthorized error while publishing to topic: " + topic);
                } else {
                    throw e;
                }
            }

            // Keep the main thread alive, or the process will exit.
            try {
                while (true) {
                    Thread.sleep(10000);
                }
            } catch (InterruptedException e) {
                System.out.println("Subscribe interrupted.");
            }

            // To stop subscribing, close the stream.
            responseHandler.closeStream();
        } catch (InterruptedException e) {
            System.out.println("IPC interrupted.");
        } catch (ExecutionException e) {
            System.err.println("Exception occurred when using IPC.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void subscribeToIoTCore(String topicName, String qos) {
        try{
            StreamResponseHandler<IoTCoreMessage> streamResponseHandler = new SubscriptionMqttResponseHandler();
            SubscribeToIoTCoreRequest subscribeToIoTCoreRequest = new SubscribeToIoTCoreRequest();
            subscribeToIoTCoreRequest.setTopicName(topicName);
            subscribeToIoTCoreRequest.setQos(qos);
            SubscribeToIoTCoreResponseHandler responseHandler = getIpcClient().subscribeToIoTCore(subscribeToIoTCoreRequest,
                    Optional.of(streamResponseHandler));
            CompletableFuture<SubscribeToIoTCoreResponse> futureResponse = responseHandler.getResponse();
            try {
                futureResponse.get(DEFAULT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
                System.out.println("Successfully subscribed to topic: " + topicName);
            } catch (TimeoutException e) {
                System.err.println("Timeout occurred while subscribing to topic: " + topicName);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof UnauthorizedError) {
                    System.err.println("Unauthorized error while subscribing to topic: " + topicName);
                } else {
                    throw e;
                }
            }

            // Keep the main thread alive, or the process will exit.
            try {
                while (true) {
                    Thread.sleep(10000);
                }
            } catch (InterruptedException e) {
                System.out.println("Subscribe interrupted.");
            }

            // To stop subscribing, close the stream.
            responseHandler.closeStream();
        } catch (InterruptedException e) {
            System.out.println("IPC interrupted.");
        } catch (ExecutionException e) {
            System.err.println("Exception occurred when using IPC.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static class SubscriptionMqttResponseHandler implements StreamResponseHandler<IoTCoreMessage> {

        @Override
        public void onStreamEvent(IoTCoreMessage ioTCoreMessage) {
            try {
                String topic = ioTCoreMessage.getMessage().getTopicName();
                String message = new String(ioTCoreMessage.getMessage().getPayload(),
                        StandardCharsets.UTF_8);
                System.out.printf("Received new message on topic %s: %s%n", topic, message);
            } catch (Exception e) {
                System.err.println("Exception occurred while processing subscription response " +
                        "message.");
                e.printStackTrace();
            }
        }

        @Override
        public boolean onStreamError(Throwable error) {
            System.err.println("Received a stream error.");
            error.printStackTrace();
            return false;
        }

        @Override
        public void onStreamClosed() {
            System.out.println("Subscribe to IoT Core stream closed.");
        }
    }

    private SubscribeToTopicResponseHandler subscribeToTopic(GreengrassCoreIPCClient greengrassCoreIPCClient
            , String topic, StreamResponseHandler<SubscriptionResponseMessage> streamResponseHandler) {
        SubscribeToTopicRequest subscribeToTopicRequest = new SubscribeToTopicRequest();
        subscribeToTopicRequest.setTopic(topic);
        return greengrassCoreIPCClient.subscribeToTopic(subscribeToTopicRequest,
                Optional.of(streamResponseHandler));
    }


    public static class SubscriptionResponseHandler implements StreamResponseHandler<SubscriptionResponseMessage> {

        private final String topic;

        public SubscriptionResponseHandler(String topic) {
            this.topic = topic;
        }

        @Override
        public void onStreamEvent(SubscriptionResponseMessage subscriptionResponseMessage) {
            try {
                String message =
                        new String(subscriptionResponseMessage.getBinaryMessage().getMessage(),
                                StandardCharsets.UTF_8);
                System.out.printf("Received new message on topic %s: %s%n", this.topic, message);
            } catch (Exception e) {
                System.err.println("Exception occurred while processing subscription response " +
                        "message.");
                e.printStackTrace();
            }
        }

        @Override
        public boolean onStreamError(Throwable error) {
            System.err.println("Received a stream error.");
            error.printStackTrace();
            return false; // Return true to close stream, false to keep stream open.
        }

        @Override
        public void onStreamClosed() {
            System.out.println("Subscribe to topic stream closed.");
        }
    }

    public static void onStreamClosed() {
        System.out.println("Subscribe to topic stream closed.");
    }

    private String getGgcRoot() {
        // check if root path was passed as an argument to the command line, else fall back to env variable
        // if root path not found then throw exception
        String ggcRootPath = root != null ? root : System.getenv("GGC_ROOT_PATH");
        if (ggcRootPath == null) {
            throw new RuntimeException("GGC root path not configured. Provide ggc root path via cli greengrass-cli "
                    + "--ggcRootPath {PATH} {rest of the arguments} " +
                    "or set the environment variable GGC_ROOT_PATH");
        }

        return ggcRootPath;
    }

    private GreengrassCoreIPCClient getIpcClient() {
        if (ipcClient != null) {
            return ipcClient;
        }
        String ggcRootPath = getGgcRoot();
        try {
            Map<String, String> ipcInfoMap = OBJECT_MAPPER.readValue(loadCliIpcInfo(ggcRootPath), HashMap.class);
            String domainSocketPath = ipcInfoMap.get(DOMAIN_SOCKET_PATH);
            if (Files.exists(Paths.get(IPC_SERVER_SOCKET_SYMLINK), LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(Paths.get(IPC_SERVER_SOCKET_SYMLINK));
            }
            boolean symlinkCreated = false;
            // Only symlink when the absolute path would overflow
            if (Paths.get(domainSocketPath).toString().length() >= 108) {
                try {
                    Files.createSymbolicLink(Paths.get(IPC_SERVER_SOCKET_SYMLINK), Paths.get(domainSocketPath));
                    symlinkCreated = true;
                } catch (IOException e) {
                    //Symlink not created, ignoring and using absolute path
                }
            }
            String token = ipcInfoMap.get(CLI_AUTH_TOKEN);

            socketOptions = new SocketOptions();
            //timeout for establishing the connection
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
        elGroup = new EventLoopGroup(1);
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

    public static Optional<Path> deTilde(String path) {
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }
        if (path.startsWith(HOME_DIR_PREFIX)) {
            return Optional.of(Paths.get(System.getProperty("user.home")).resolve(path.substring(HOME_DIR_PREFIX.length()))
                    .toAbsolutePath());
        }
        return Optional.of(Paths.get(path).toAbsolutePath());
    }

    private byte[] loadCliIpcInfo(String ggcRootPath) throws IOException {
        Path directory = deTilde(ggcRootPath).orElse(Paths.get("")).resolve(CLI_IPC_INFO_DIRECTORY).normalize();

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
            if (Files.exists(Paths.get(IPC_SERVER_SOCKET_SYMLINK), LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(Paths.get(IPC_SERVER_SOCKET_SYMLINK));
            }
        } catch (Exception e) {
        }
    }
}
