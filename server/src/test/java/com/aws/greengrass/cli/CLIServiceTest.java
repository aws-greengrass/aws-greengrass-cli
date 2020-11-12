/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli;

import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.deployment.DeploymentStatusKeeper;
import com.aws.greengrass.deployment.model.Deployment;
import com.aws.greengrass.ipc.AuthenticationHandler;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.GGServiceTestUtil;
import com.aws.greengrass.util.Exec;
import com.aws.greengrass.util.NucleusPaths;
import com.aws.greengrass.util.platforms.unix.UnixGroupAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.exceptions.misusing.InvalidUseOfMatchersException;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCService;
import software.amazon.awssdk.eventstreamrpc.OperationContinuationHandlerContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.aws.greengrass.cli.CLIService.CLI_AUTH_TOKEN;
import static com.aws.greengrass.cli.CLIService.CLI_SERVICE;
import static com.aws.greengrass.cli.CLIService.DOMAIN_SOCKET_PATH;
import static com.aws.greengrass.cli.CLIService.OBJECT_MAPPER;
import static com.aws.greengrass.cli.CLIService.AUTHORIZED_POSIX_GROUPS;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.PARAMETERS_CONFIG_KEY;
import static com.aws.greengrass.ipc.IPCEventStreamService.NUCLEUS_DOMAIN_SOCKET_FILEPATH;
import static com.aws.greengrass.lifecyclemanager.GreengrassService.PRIVATE_STORE_NAMESPACE_TOPIC;
import static com.aws.greengrass.lifecyclemanager.GreengrassService.SERVICES_NAMESPACE_TOPIC;
import static com.aws.greengrass.lifecyclemanager.GreengrassService.SETENV_CONFIG_NAMESPACE;
import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
@SuppressWarnings("PMD.CouplingBetweenObjects")
class CLIServiceTest extends GGServiceTestUtil {

    private static final String MOCK_AUTH_TOKEN = "CliAuthToken";
    private static final Object MOCK_SOCKET_URL = "tcp://127.0.0.1:5667";

    @Mock
    private DeploymentStatusKeeper deploymentStatusKeeper;
    @Mock
    private AuthenticationHandler authenticationHandler;
    @Mock
    private Kernel kernel;
    @Mock
    private NucleusPaths nucleusPaths;
    @Mock
    private GreengrassCoreIPCService greengrassCoreIPCService;
    @Mock
    private CLIEventStreamAgent cliEventStreamAgent;
    @TempDir
    Path tmpDir;

    private CLIService cliService;
    private Topics serviceConfigSpy;
    private Topics cliConfigSpy;
    private Topics privateConfigSpy;

    @BeforeEach
    void setup() {
        lenient().when(kernel.getNucleusPaths()).thenReturn(nucleusPaths);
        serviceFullName = CLI_SERVICE;
        initializeMockedConfig();
        serviceConfigSpy = spy(Topics.of(context, SERVICES_NAMESPACE_TOPIC, null));
        cliConfigSpy = spy(Topics.of(context, CLI_SERVICE, serviceConfigSpy));
        privateConfigSpy = spy(Topics.of(context, PRIVATE_STORE_NAMESPACE_TOPIC, cliConfigSpy));

        cliService = new CLIService(cliConfigSpy, privateConfigSpy, cliEventStreamAgent,
                deploymentStatusKeeper, authenticationHandler, kernel, greengrassCoreIPCService);
        cliService.postInject();
    }

    @Test
    void testPostInject_calls_made() {
        verify(deploymentStatusKeeper).registerDeploymentStatusConsumer(eq(Deployment.DeploymentType.LOCAL), any(),
                eq(CLIService.class.getName()));
        verify(cliConfigSpy).lookup(PARAMETERS_CONFIG_KEY, AUTHORIZED_POSIX_GROUPS);
    }

    @Test
    void testStartup_default_auth() throws Exception {
        when(authenticationHandler.registerAuthenticationTokenForExternalClient(anyString(), anyString())).thenReturn(
                MOCK_AUTH_TOKEN);
        when(nucleusPaths.cliIpcInfoPath()).thenReturn(tmpDir);
        Topic mockSocketUrlTopic = mock(Topic.class);
        when(mockSocketUrlTopic.getOnce()).thenReturn(MOCK_SOCKET_URL);
        Topics mockRootTopics = mock(Topics.class);
        when(mockRootTopics.find(SETENV_CONFIG_NAMESPACE, NUCLEUS_DOMAIN_SOCKET_FILEPATH))
                .thenReturn(mockSocketUrlTopic);
        when(cliConfigSpy.getRoot()).thenReturn(mockRootTopics);
        cliService.startup();
        verifyHandlersRegisteredForAllOperations();
        verify(authenticationHandler).registerAuthenticationTokenForExternalClient
                (anyString(), startsWith("greengrass-cli-user"));

        Path authDir = nucleusPaths.cliIpcInfoPath();
        assertTrue(Files.exists(authDir));
        File[] files = authDir.toFile().listFiles();
        assertEquals(1, files.length);

        Map<String, String> ipcInfo = OBJECT_MAPPER.readValue(Files.readAllBytes(files[0].toPath()), Map.class);
        assertEquals(MOCK_SOCKET_URL, ipcInfo.get(DOMAIN_SOCKET_PATH));
        assertEquals(MOCK_AUTH_TOKEN, ipcInfo.get(CLI_AUTH_TOKEN));
    }

    @Test
    void testStartup_group_auth(ExtensionContext context) throws Exception {
        if (Exec.isWindows) {
            // GG_NEEDS_REVIEW: TODO support group auth on Windows
            return;
        }
        ignoreExceptionOfType(context, UserPrincipalNotFoundException.class);

        String MOCK_AUTH_TOKEN_2 = "CliAuthToken2";
        when(authenticationHandler.registerAuthenticationTokenForExternalClient(anyString(), anyString()))
                .thenAnswer(i -> {
                    Object clientId = i.getArgument(1);
                    if ("greengrass-cli-group-123".equals(clientId)) {
                        return MOCK_AUTH_TOKEN;
                    } else if ("greengrass-cli-group-456".equals(clientId)) {
                        return MOCK_AUTH_TOKEN_2;
                    }
                    throw new InvalidUseOfMatchersException(
                            String.format("Argument %s does not match", clientId)
                    );
                });
        when(nucleusPaths.cliIpcInfoPath()).thenReturn(tmpDir);
        Topic mockSocketUrlTopic = mock(Topic.class);
        when(mockSocketUrlTopic.getOnce()).thenReturn(MOCK_SOCKET_URL);
        Topics mockRootTopics = mock(Topics.class);
        when(mockRootTopics.find(SETENV_CONFIG_NAMESPACE, NUCLEUS_DOMAIN_SOCKET_FILEPATH))
                .thenReturn(mockSocketUrlTopic);
        when(cliConfigSpy.getRoot()).thenReturn(mockRootTopics);

        Topic mockPosixGroupsTopic = mock(Topic.class);
        when(mockPosixGroupsTopic.getOnce()).thenReturn("ubuntu,123,someone");
        when(cliConfigSpy.find(PARAMETERS_CONFIG_KEY, AUTHORIZED_POSIX_GROUPS)).thenReturn(mockPosixGroupsTopic);

        CLIService cliServiceSpy = spy(cliService);
        UnixGroupAttributes groupUbuntu = UnixGroupAttributes.builder()
                .principalName("ubuntu")
                .principalIdentifier("123").build();
        doAnswer(i -> {
            Object argument = i.getArgument(0);
            if ("ubuntu".equals(argument) || "123".equals(argument)) {
                return groupUbuntu;
            } else if ("someone".equals(argument)) {
                return UnixGroupAttributes.builder()
                        .principalName("someone")
                        .principalIdentifier("456").build();
            }
            throw new InvalidUseOfMatchersException(
                    String.format("Argument %s does not match", argument)
            );
        }).when(cliServiceSpy).getGroup(anyString());
        cliServiceSpy.startup();

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(authenticationHandler, times(2)).registerAuthenticationTokenForExternalClient
                (anyString(), argument.capture());
        assertThat(argument.getAllValues(), containsInRelativeOrder("greengrass-cli-group-123",
                "greengrass-cli-group-456"));

        Path authDir = nucleusPaths.cliIpcInfoPath();
        assertTrue(Files.exists(authDir.resolve("group-123")));
        assertTrue(Files.exists(authDir.resolve("group-456")));
        assertEquals(2, authDir.toFile().listFiles().length);

        Map<String, String> ipcInfo =
                OBJECT_MAPPER.readValue(Files.readAllBytes(authDir.resolve("group-123")), Map.class);
        assertEquals(MOCK_SOCKET_URL, ipcInfo.get(DOMAIN_SOCKET_PATH));
        assertEquals(MOCK_AUTH_TOKEN, ipcInfo.get(CLI_AUTH_TOKEN));

        ipcInfo = OBJECT_MAPPER.readValue(Files.readAllBytes(authDir.resolve("group-456")), Map.class);
        assertEquals(MOCK_AUTH_TOKEN_2, ipcInfo.get(CLI_AUTH_TOKEN));
        assertEquals(MOCK_SOCKET_URL, ipcInfo.get(DOMAIN_SOCKET_PATH));
    }

    private void verifyHandlersRegisteredForAllOperations() {
        OperationContinuationHandlerContext mockContext = mock(OperationContinuationHandlerContext.class);
        ArgumentCaptor<Function> argumentCaptor = ArgumentCaptor.forClass(Function.class);
        verify(greengrassCoreIPCService).setGetComponentDetailsHandler(argumentCaptor.capture());
        verify(greengrassCoreIPCService).setListComponentsHandler(argumentCaptor.capture());
        argumentCaptor.getAllValues().stream().forEach(handler -> handler.apply(mockContext));
        verify(cliEventStreamAgent).getGetComponentDetailsHandler(mockContext);
        verify(cliEventStreamAgent).getListComponentsHandler(mockContext);
    }

    @Test
    void testDeploymentStatusChanged_calls() {
        Map<String, Object> deploymentDetails = new HashMap<>();
        cliService.deploymentStatusChanged(deploymentDetails);
        verify(cliEventStreamAgent).persistLocalDeployment(cliConfigSpy, deploymentDetails);
    }
}
