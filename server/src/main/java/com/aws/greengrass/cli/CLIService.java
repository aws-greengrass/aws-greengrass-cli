/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli;

import com.aws.greengrass.componentmanager.models.ComponentIdentifier;
import com.aws.greengrass.config.PlatformResolver;
import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.ImplementsService;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.deployment.DeploymentStatusKeeper;
import com.aws.greengrass.deployment.model.Deployment;
import com.aws.greengrass.ipc.AuthenticationHandler;
import com.aws.greengrass.ipc.exceptions.UnauthenticatedException;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.lifecyclemanager.PluginService;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.FileSystemPermission;
import com.aws.greengrass.util.Utils;
import com.aws.greengrass.util.platforms.Platform;
import com.aws.greengrass.util.platforms.UserPlatform;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.SemverException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Data;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService;
import software.amazon.awssdk.aws.greengrass.model.DeploymentStatus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.VERSION_CONFIG_KEY;
import static com.aws.greengrass.ipc.AuthenticationHandler.SERVICE_UNIQUE_ID_KEY;
import static com.aws.greengrass.ipc.IPCEventStreamService.NUCLEUS_DOMAIN_SOCKET_FILEPATH;
import static com.aws.greengrass.ipc.modules.MqttProxyIPCService.MQTT_PROXY_SERVICE_NAME;
import static com.aws.greengrass.ipc.modules.PubSubIPCService.PUB_SUB_SERVICE_NAME;

@ImplementsService(name = CLIService.CLI_SERVICE, autostart = true)
public class CLIService extends PluginService {
    public static final String GREENGRASS_CLI_CLIENT_ID_PREFIX = "greengrass-cli#";
    public static final String GREENGRASS_CLI_CLIENT_ID_FMT = GREENGRASS_CLI_CLIENT_ID_PREFIX + "%s";
    public static final String CLI_SERVICE = "aws.greengrass.Cli";
    public static final String CLI_CLIENT_ARTIFACT = "aws.greengrass.cli.client";
    public static final String[] CLI_CLIENT_BINARIES = {"greengrass-cli", "greengrass-cli.cmd"};
    public static final String CLI_CLIENT_DIRECTORY = "cliclient";
    public static final String CLI_CLIENT_BIN = "bin";
    public static final String CLI_CLIENT_LIB = "lib";
    public static final String CLI_AUTH_TOKEN = "cli_auth_token";
    public static final String AUTHORIZED_POSIX_GROUPS = "AuthorizedPosixGroups";
    public static final String AUTHORIZED_WINDOWS_GROUPS = "AuthorizedWindowsGroups";

    static final String USER_CLIENT_ID_PREFIX = "user-";
    static final String GROUP_CLIENT_ID_PREFIX = "group-";
    static final FileSystemPermission DEFAULT_FILE_PERMISSION = new FileSystemPermission(null, null,
            true, true, false, false, false, false, false, false, false);
    public static final String DOMAIN_SOCKET_PATH = "domain_socket_path";

    protected static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Map<String, String> clientIdToAuthToken = new HashMap<>();


    @Inject
    private DeploymentStatusKeeper deploymentStatusKeeper;

    @Inject
    private AuthenticationHandler authenticationHandler;

    @Inject
    private Kernel kernel;

    @Inject
    private CLIEventStreamAgent cliEventStreamAgent;

    @Inject
    private GreengrassCoreIPCService greengrassCoreIPCService;

    public CLIService(Topics topics) {
        super(topics);
    }

    /**
     * Constructor for unit testing.
     * @param topics Service config
     * @param privateConfig Private config for the service
     * @param cliEventStreamAgent {@link CLIEventStreamAgent}
     * @param deploymentStatusKeeper {@link DeploymentStatusKeeper}
     * @param authenticationHandler {@link AuthenticationHandler}
     * @param kernel {@link Kernel}
     * @param greengrassCoreIPCService {@link GreengrassCoreIPCService}
     */
    public CLIService(Topics topics, Topics privateConfig,
                      CLIEventStreamAgent cliEventStreamAgent,
                      DeploymentStatusKeeper deploymentStatusKeeper, AuthenticationHandler authenticationHandler,
                      Kernel kernel, GreengrassCoreIPCService greengrassCoreIPCService) {
        super(topics, privateConfig);
        this.cliEventStreamAgent = cliEventStreamAgent;
        this.deploymentStatusKeeper = deploymentStatusKeeper;
        this.authenticationHandler = authenticationHandler;
        this.kernel = kernel;
        this.greengrassCoreIPCService = greengrassCoreIPCService;
    }

    @Override
    public void postInject() {
        super.postInject();
        // Does not happen for built-in/plugin services so doing explicitly
        AuthenticationHandler.registerAuthenticationToken(this);

        deploymentStatusKeeper.registerDeploymentStatusConsumer(Deployment.DeploymentType.LOCAL,
                this::deploymentStatusChanged, CLIService.class.getName());


        config.lookup(CONFIGURATION_CONFIG_KEY, AUTHORIZED_POSIX_GROUPS).subscribe((why, newv) -> {
            requestReinstall();
        });
        config.lookup(CONFIGURATION_CONFIG_KEY, AUTHORIZED_WINDOWS_GROUPS).subscribe((why, newv) -> {
            requestReinstall();
        });
    }

    private void registerIpcEventStreamHandlers() {
        greengrassCoreIPCService.setGetComponentDetailsHandler((context)
                -> cliEventStreamAgent.getGetComponentDetailsHandler(context));
        greengrassCoreIPCService.setListComponentsHandler((context)
                -> cliEventStreamAgent.getListComponentsHandler(context));
        greengrassCoreIPCService.setRestartComponentHandler((context)
                -> cliEventStreamAgent.getRestartComponentsHandler(context));
        greengrassCoreIPCService.setStopComponentHandler((context)
                -> cliEventStreamAgent.getStopComponentsHandler(context));
        greengrassCoreIPCService.setCreateLocalDeploymentHandler((context)
                -> cliEventStreamAgent.getCreateLocalDeploymentHandler(context, this.getRuntimeConfig()));
        greengrassCoreIPCService.setGetLocalDeploymentStatusHandler((context)
                -> cliEventStreamAgent.getGetLocalDeploymentStatusHandler(context, this.getRuntimeConfig()));
        greengrassCoreIPCService.setListLocalDeploymentsHandler((context)
                -> cliEventStreamAgent.getListLocalDeploymentsHandler(context, this.getRuntimeConfig()));
        greengrassCoreIPCService.setCreateDebugPasswordHandler((context)
                -> cliEventStreamAgent.getCreateDebugPasswordHandler(context, config.getRoot()));
    }

    @Override
    protected void install() {
        try {
            Path clientArtifact = kernel.getNucleusPaths().unarchiveArtifactPath(new ComponentIdentifier(CLI_SERVICE,
                    new Semver(Coerce.toString(getConfig().find(VERSION_CONFIG_KEY)))), CLI_CLIENT_ARTIFACT);
            if (!Files.exists(clientArtifact)) {
                logger.atWarn().kv("path", clientArtifact)
                        .log("Unable to locate CLI binary. Make sure CLI component is properly deployed");
                return;
            }
            Path unpackDir = clientArtifact.resolve(CLI_CLIENT_DIRECTORY);
            setCliClientPermission(unpackDir);

            for (String bin : CLI_CLIENT_BINARIES) {
                Path binary = unpackDir.resolve(CLI_CLIENT_BIN).resolve(bin);
                Path link = kernel.getNucleusPaths().binPath().resolve(bin);
                Files.deleteIfExists(link);
                Files.createSymbolicLink(link, binary);
                logger.atInfo().kv("binary", binary).kv("link", link).log("Set up symlink to CLI binary");
            }

            // authorize pub/sub for the cli
            authorizePubSubPermission(PUB_SUB_SERVICE_NAME);
            authorizePubSubPermission(MQTT_PROXY_SERVICE_NAME);
        } catch (IOException | SemverException e) {
            logger.atError().log("Failed to set up symlink to CLI binary", e);
        }
    }

    private void authorizePubSubPermission(String serviceIdentifier) throws IOException {
        String defaultClientId =
                USER_CLIENT_ID_PREFIX + Platform.getInstance().lookupCurrentUser().getPrincipalIdentifier();
        String serviceName = getAuthClientIdentifier(defaultClientId);
        List<String> list = new ArrayList<>();
        list.add("*");
        String policyName = "aws.greengrass.Cli:pubsub:10";
        config.parent.lookup(serviceName, CONFIGURATION_CONFIG_KEY, ACCESS_CONTROL_NAMESPACE_TOPIC,
                serviceIdentifier,
                policyName, "resources").dflt(list);
        config.parent.lookup(serviceName, CONFIGURATION_CONFIG_KEY, ACCESS_CONTROL_NAMESPACE_TOPIC,
                serviceIdentifier, policyName, "operations").dflt(list);

        config.parent.lookup(serviceName, CONFIGURATION_CONFIG_KEY, ACCESS_CONTROL_NAMESPACE_TOPIC,
                serviceIdentifier, policyName, "policyDescription").dflt("Allows access to publish/subscribe topic.");
    }

    private void setCliClientPermission(Path clientDir) {
        for (String bin : CLI_CLIENT_BINARIES) {
            Path binary = clientDir.resolve(CLI_CLIENT_BIN).resolve(bin);
            binary.toFile().setExecutable(true, false);
            binary.toFile().setReadable(true, false);
            binary.toFile().setWritable(true);
        }

        File[] libraries = clientDir.resolve(CLI_CLIENT_LIB).toFile().listFiles();
        if (libraries != null) {
            for (File file : libraries) {
                file.setWritable(true);
                file.setReadable(true, false);
            }
        }
    }

    @Override
    protected void startup() {
        registerIpcEventStreamHandlers();
        try {
            generateCliIpcInfo();
            reportState(State.RUNNING);
        } catch (IOException | UnauthenticatedException e) {
            logger.atError().setEventType("cli-ipc-info-generation-error")
                    .setCause(e)
                    .log("Failed to create cli_ipc_info file");
            reportState(State.ERRORED);
        }
    }

    @Override
    protected void shutdown() {

    }

    String getClientIdForGroup(String groupId) {
        return GROUP_CLIENT_ID_PREFIX + groupId;
    }

    UserPlatform.BasicAttributes getGroup(String posixGroup) throws IOException {
        return Platform.getInstance().lookupGroupByName(posixGroup);
    }

    private synchronized void generateCliIpcInfo() throws UnauthenticatedException, IOException {
        // GG_NEEDS_REVIEW: TODO: replace with the new IPC domain socket path
        if (config.getRoot().find(SETENV_CONFIG_NAMESPACE, NUCLEUS_DOMAIN_SOCKET_FILEPATH) == null) {
            logger.atWarn().log("Did not find IPC socket URL in the config. Not creating the cli ipc info file");
            return;
        }

        Path authTokenDir = kernel.getNucleusPaths().cliIpcInfoPath();
        revokeOutdatedAuthTokens(authTokenDir);

        Topic authorizedGroups = config.find(CONFIGURATION_CONFIG_KEY,
                PlatformResolver.isWindows ? AUTHORIZED_WINDOWS_GROUPS : AUTHORIZED_POSIX_GROUPS);
        String groups = Coerce.toString(authorizedGroups);
        if (Utils.isEmpty(groups)) {
            generateCliIpcInfoForEffectiveUser(authTokenDir);
            return;
        }

        for (String group : groups.split(",")) {
            group = group.trim();
            UserPlatform.BasicAttributes groupAttributes;
            try {
                groupAttributes = getGroup(group);
            } catch (NumberFormatException | IOException e) {
                logger.atError().kv("group", group).log("Failed to get group ID", e);
                continue;
            }
            generateCliIpcInfoForGroup(groupAttributes, authTokenDir);
        }
    }

    @SuppressFBWarnings(value = {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "RV_RETURN_VALUE_IGNORED"},
            justification = "File is created in the same method")
    private synchronized void generateCliIpcInfoForEffectiveUser(Path directory)
            throws UnauthenticatedException, IOException {
        String defaultClientId =
                USER_CLIENT_ID_PREFIX + Platform.getInstance().lookupCurrentUser().getPrincipalIdentifier();
        Path ipcInfoFile = generateCliIpcInfoForClient(defaultClientId, directory);
        if (ipcInfoFile == null) {
            return;
        }
        Platform.getInstance().setPermissions(DEFAULT_FILE_PERMISSION, ipcInfoFile);
    }

    private synchronized void generateCliIpcInfoForGroup(UserPlatform.BasicAttributes group, Path directory)
            throws UnauthenticatedException, IOException {
        Path ipcInfoFile = generateCliIpcInfoForClient(getClientIdForGroup(group.getPrincipalIdentifier()), directory);
        if (ipcInfoFile == null) {
            return;
        }

        FileSystemPermission filePermission = null;
        try {
            filePermission = FileSystemPermission.builder().ownerUser(
                    Platform.getInstance().lookupCurrentUser().getPrincipalName())
                    .ownerGroup(group.getPrincipalName()).ownerRead(true).ownerWrite(true).groupRead(true).build();
            Platform.getInstance().setPermissions(filePermission, ipcInfoFile);
        } catch (IOException e) {
            logger.atError().kv("file", ipcInfoFile).kv("permission", filePermission)
                    .kv("groupOwner", group.getPrincipalName()).log("Failed to set up posix file permissions and"
                    + " group owner.  Admin may have to manually update the file permission so that CLI authentication "
                    + "works as intended", e);
        }
    }

    private synchronized Path generateCliIpcInfoForClient(String clientId, Path directory)
            throws UnauthenticatedException, IOException {
        if (clientIdToAuthToken.containsKey(clientId)) {
            // Duplicate user input. No need to override auth token.
            return null;
        }

        String cliAuthToken = authenticationHandler.registerAuthenticationTokenForExternalClient(
                Coerce.toString(getPrivateConfig().find(SERVICE_UNIQUE_ID_KEY)), getAuthClientIdentifier(clientId));

        clientIdToAuthToken.put(clientId, cliAuthToken);

        Map<String, String> ipcInfo = new HashMap<>();
        ipcInfo.put(CLI_AUTH_TOKEN, cliAuthToken);
        ipcInfo.put(DOMAIN_SOCKET_PATH, Coerce.toString(
                config.getRoot().find(SETENV_CONFIG_NAMESPACE, NUCLEUS_DOMAIN_SOCKET_FILEPATH)));

        Path filePath = directory.resolve(clientId);
        Files.write(filePath, OBJECT_MAPPER.writeValueAsString(ipcInfo)
                .getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        ipcInfo.clear();
        return filePath;
    }

    private String getAuthClientIdentifier(String clientId) {
        return String.format(GREENGRASS_CLI_CLIENT_ID_FMT, clientId);
    }

    @SuppressFBWarnings(value = {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE"},
            justification = "File to be deleted should exist")
    private synchronized void revokeOutdatedAuthTokens(Path authTokenDir) throws UnauthenticatedException {
        for (Map.Entry<String, String> entry : clientIdToAuthToken.entrySet()) {
            authenticationHandler.revokeAuthenticationTokenForExternalClient(
                    Coerce.toString(getPrivateConfig().find(SERVICE_UNIQUE_ID_KEY)), entry.getValue());
        }
        clientIdToAuthToken.clear();
        File[] allContents = authTokenDir.toFile().listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                try {
                    Files.delete(file.toPath());
                } catch (IOException e) {
                    logger.atWarn().log("Unable to delete auth file " + file, e);
                }
            }
        }
        logger.atInfo().log("Auth tokens have been revoked");
    }

    @Data
    public static class LocalDeploymentDetails {
        String deploymentId;
        DeploymentStatus status;
    }

    @SuppressWarnings("PMD.EmptyIfStmt")
    protected Boolean deploymentStatusChanged(Map<String, Object> deploymentDetails) {
        cliEventStreamAgent.persistLocalDeployment(this.getRuntimeConfig(), deploymentDetails);
        return true;
    }
}
