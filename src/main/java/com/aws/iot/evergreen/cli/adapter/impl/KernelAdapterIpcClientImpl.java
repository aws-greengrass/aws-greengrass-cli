package com.aws.iot.evergreen.cli.adapter.impl;

import com.aws.iot.evergreen.cli.CLI;
import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import com.aws.iot.evergreen.cli.adapter.KernelAdapterIpc;
import com.aws.iot.evergreen.cli.adapter.LocalOverrideRequest;
import com.aws.iot.evergreen.ipc.IPCClient;
import com.aws.iot.evergreen.ipc.IPCClientImpl;
import com.aws.iot.evergreen.ipc.config.KernelIPCClientConfig;
import com.aws.iot.evergreen.ipc.exceptions.IPCClientException;
import com.aws.iot.evergreen.ipc.services.cli.Cli;
import com.aws.iot.evergreen.ipc.services.cli.CliImpl;
import com.aws.iot.evergreen.ipc.services.cli.models.GetComponentDetailsRequest;
import com.aws.iot.evergreen.ipc.services.cli.models.GetComponentDetailsResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class KernelAdapterIpcClientImpl implements KernelAdapterIpc {

    protected static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // TODO: When the greengrass-cli is installed in the kernel root path this will derived from the current working
    // directory, instead of an env variable. Until then using env variable.
    private static final Path ggcRootPath = Paths.get(System.getenv("GGC_ROOT_PATH"));
    private static final String CLI_IPC_INFO_FILENAME = "cli_ipc_info";
    private static final String CLI_AUTH_TOKEN = "cli_auth_token";
    private static final String SOCKET_URL = "socket_url";

    private IPCClient ipcClient;
    private Cli cliClient;

    public KernelAdapterIpcClientImpl() throws InterruptedException, IPCClientException, IOException {
        Path filepath = ggcRootPath.resolve(CLI_IPC_INFO_FILENAME);
        if (!Files.exists(filepath)) {
            throw new RuntimeException("Kernel root path not configured");
        }
        Map<String, String> ipcInfoMap = OBJECT_MAPPER.readValue(Files.readAllBytes(filepath), HashMap.class);
        String socketUrl = ipcInfoMap.get(SOCKET_URL);
        String token = ipcInfoMap.get(CLI_AUTH_TOKEN);
        String[] socketParams = socketUrl.split(":");
        ipcClient = new IPCClientImpl(KernelIPCClientConfig.builder()
                .hostAddress(socketParams[0])
                .port(Integer.parseInt(socketParams[1]))
                .token(token)
                .build());
        cliClient = new CliImpl(ipcClient);
    }

    @Override
    public Map<String, String> getComponentDetails(String componentName) {
        return null;
    }

    @Override
    public void restartComponent(String componentName) {

    }

    @Override
    public void stopComponent(String componentName) {

    }

    @Override
    public void updateRecipesAndArtifacts(String recipesDirectoryPath, String artifactsDirectoryPath) {

    }

    @Override
    public void createLocalDeployment(LocalOverrideRequest localOverrideRequest) {

    }

    @Override
    public Set<Map<String, String>> listComponents() {
        return null;
    }
}
