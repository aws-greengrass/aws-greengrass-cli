/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli;

import com.aws.greengrass.authorization.AuthorizationHandler;
import com.aws.greengrass.authorization.exceptions.AuthorizationException;
import com.aws.greengrass.componentmanager.ComponentStore;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.deployment.DeploymentQueue;
import com.aws.greengrass.deployment.model.Deployment;
import com.aws.greengrass.deployment.model.DeploymentResult;
import com.aws.greengrass.deployment.model.LocalOverrideRequest;
import com.aws.greengrass.lifecyclemanager.GreengrassService;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.lifecyclemanager.exceptions.ServiceLoadException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.Coerce;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.aws.greengrass.model.CancelLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.CancelLocalDeploymentResponse;
import software.amazon.awssdk.aws.greengrass.model.ComponentDetails;
import software.amazon.awssdk.aws.greengrass.model.CreateDebugPasswordRequest;
import software.amazon.awssdk.aws.greengrass.model.CreateDebugPasswordResponse;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.DeploymentStatus;
import software.amazon.awssdk.aws.greengrass.model.FailureHandlingPolicy;
import software.amazon.awssdk.aws.greengrass.model.GetComponentDetailsRequest;
import software.amazon.awssdk.aws.greengrass.model.GetComponentDetailsResponse;
import software.amazon.awssdk.aws.greengrass.model.GetLocalDeploymentStatusRequest;
import software.amazon.awssdk.aws.greengrass.model.GetLocalDeploymentStatusResponse;
import software.amazon.awssdk.aws.greengrass.model.InvalidArgumentsError;
import software.amazon.awssdk.aws.greengrass.model.LifecycleState;
import software.amazon.awssdk.aws.greengrass.model.ListComponentsRequest;
import software.amazon.awssdk.aws.greengrass.model.ListComponentsResponse;
import software.amazon.awssdk.aws.greengrass.model.ListLocalDeploymentsRequest;
import software.amazon.awssdk.aws.greengrass.model.ListLocalDeploymentsResponse;
import software.amazon.awssdk.aws.greengrass.model.ResourceNotFoundError;
import software.amazon.awssdk.aws.greengrass.model.RestartComponentRequest;
import software.amazon.awssdk.aws.greengrass.model.ServiceError;
import software.amazon.awssdk.aws.greengrass.model.StopComponentRequest;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.crt.eventstream.ServerConnectionContinuation;
import software.amazon.awssdk.eventstreamrpc.OperationContinuationHandlerContext;
import software.amazon.awssdk.utils.ImmutableMap;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.aws.greengrass.cli.CLIEventStreamAgent.LOCAL_DEPLOYMENT_CREATED_ON;
import static com.aws.greengrass.cli.CLIEventStreamAgent.LOCAL_DEPLOYMENT_CREATED_ON_FORMATTER;
import static com.aws.greengrass.cli.CLIEventStreamAgent.PERSISTENT_LOCAL_DEPLOYMENTS;
import static com.aws.greengrass.cli.CLIService.CLI_SERVICE;
import static com.aws.greengrass.cli.CLIService.GREENGRASS_CLI_CLIENT_ID_FMT;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.VERSION_CONFIG_KEY;
import static com.aws.greengrass.deployment.DeploymentService.DEPLOYMENT_DETAILED_STATUS_KEY;
import static com.aws.greengrass.deployment.DeploymentService.DEPLOYMENT_ERROR_STACK_KEY;
import static com.aws.greengrass.deployment.DeploymentService.DEPLOYMENT_ERROR_TYPES_KEY;
import static com.aws.greengrass.deployment.DeploymentService.DEPLOYMENT_FAILURE_CAUSE_KEY;
import static com.aws.greengrass.deployment.DeploymentStatusKeeper.DEPLOYMENT_STATUS_DETAILS_KEY_NAME;
import static com.aws.greengrass.deployment.DeploymentStatusKeeper.DEPLOYMENT_STATUS_KEY_NAME;
import static com.aws.greengrass.ipc.common.IPCErrorStrings.DEPLOYMENTS_QUEUE_NOT_INITIALIZED;
import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasLength;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class CLIEventStreamAgentTest {

    private static final String TEST_SERVICE = "TestService";
    private static final String MOCK_GROUP = "mockGroup";
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Mock
    Kernel kernel;

    @Mock
    OperationContinuationHandlerContext mockContext;

    @TempDir
    Path mockPath;

    @Mock
    DeploymentQueue deploymentQueue;

    @Mock
    AuthorizationHandler authorizationHandler;

    @Mock
    private ComponentStore componentStore;

    @InjectMocks
    private CLIEventStreamAgent cliEventStreamAgent;

    @Mock
    Context context;

    @BeforeEach
    void setup() throws AuthorizationException {
        when(mockContext.getContinuation()).thenReturn(mock(ServerConnectionContinuation.class));
        when(mockContext.getAuthenticationData()).thenReturn(() -> String.format(GREENGRASS_CLI_CLIENT_ID_FMT, "abc"));
        lenient().when(authorizationHandler.isAuthorized(eq(CLI_SERVICE), any()))
                .thenThrow(new AuthorizationException("bad"));
    }

    @Test
    void test_GetComponentDetails_with_bad_auth_token_rejects() {
        // Pretend that TEST_SERVICE has connected and wants to call the CLI APIs
        when(mockContext.getAuthenticationData()).thenReturn(() -> TEST_SERVICE);
        GetComponentDetailsRequest request = new GetComponentDetailsRequest();
        request.setComponentName("any");
        assertThrows(UnauthorizedError.class,
                () -> cliEventStreamAgent.getGetComponentDetailsHandler(mockContext).handleRequest(request));
    }

    @Test
    void test_GetComponentDetails_with_non_cli_auth_token_calls_authZ()
            throws AuthorizationException, ServiceLoadException {
        // Pretend that TEST_SERVICE has connected and wants to call the CLI APIs
        when(mockContext.getAuthenticationData()).thenReturn(() -> TEST_SERVICE);
        GetComponentDetailsRequest request = new GetComponentDetailsRequest();
        request.setComponentName("any");
        when(authorizationHandler.isAuthorized(eq(CLI_SERVICE), any())).thenReturn(true);
        when(kernel.locate("any")).thenThrow(ServiceLoadException.class);
        assertThrows(ResourceNotFoundError.class,
                () -> cliEventStreamAgent.getGetComponentDetailsHandler(mockContext).handleRequest(request));
    }

    @Test
    void test_GetComponentDetails_empty_component_name() {
        GetComponentDetailsRequest request = new GetComponentDetailsRequest();
        assertThrows(InvalidArgumentsError.class,
                () -> cliEventStreamAgent.getGetComponentDetailsHandler(mockContext).handleRequest(request));
    }

    @Test
    void test_GetComponentDetails_component_does_not_exist(ExtensionContext context) throws ServiceLoadException {
        ignoreExceptionOfType(context, ServiceLoadException.class);
        GetComponentDetailsRequest request = new GetComponentDetailsRequest();
        request.setComponentName(TEST_SERVICE);
        when(kernel.locate(TEST_SERVICE)).thenThrow(new ServiceLoadException("error"));
        assertThrows(ResourceNotFoundError.class,
                () -> cliEventStreamAgent.getGetComponentDetailsHandler(mockContext).handleRequest(request));
    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    void test_GetComponentDetails_successful() throws ServiceLoadException, IOException {
        GetComponentDetailsRequest request = new GetComponentDetailsRequest();
        request.setComponentName(TEST_SERVICE);
        GreengrassService mockTestService = mock(GreengrassService.class);
        when(mockTestService.getName()).thenReturn(TEST_SERVICE);
        when(mockTestService.getState()).thenReturn(State.RUNNING);
        Context context = new Context();
        Topics mockServiceConfig = Topics.of(context, TEST_SERVICE, null);
        mockServiceConfig.lookup(VERSION_CONFIG_KEY).withValue("1.0.0");
        Map<String, Object> mockParameterConfig = ImmutableMap.of("param1", "value1");
        mockServiceConfig.lookupTopics(CONFIGURATION_CONFIG_KEY).replaceAndWait(mockParameterConfig);
        when(mockTestService.getServiceConfig()).thenReturn(mockServiceConfig);
        when(kernel.locate(TEST_SERVICE)).thenReturn(mockTestService);
        GetComponentDetailsResponse response =
                cliEventStreamAgent.getGetComponentDetailsHandler(mockContext).handleRequest(request);
        assertEquals(TEST_SERVICE, response.getComponentDetails().getComponentName());
        assertEquals(LifecycleState.RUNNING, response.getComponentDetails().getState());
        assertEquals("1.0.0", response.getComponentDetails().getVersion());
        assertEquals(mockParameterConfig, response.getComponentDetails().getConfiguration());
        context.close();
    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    void test_GetListComponent_success() throws IOException {
        ListComponentsRequest request = new ListComponentsRequest();
        GreengrassService mockTestService = mock(GreengrassService.class);
        GreengrassService mockMainService = mock(GreengrassService.class);
        when(mockTestService.getName()).thenReturn(TEST_SERVICE);
        when(mockTestService.getState()).thenReturn(State.RUNNING);
        try (Context context = new Context()) {
            Topics mockServiceConfig = Topics.of(context, TEST_SERVICE, null);
            mockServiceConfig.lookup(VERSION_CONFIG_KEY).withValue("1.0.0");
            Map<String, Object> mockParameterConfig = ImmutableMap.of("param1", "value1");
            mockServiceConfig.lookupTopics(CONFIGURATION_CONFIG_KEY).replaceAndWait(mockParameterConfig);
            when(mockTestService.getServiceConfig()).thenReturn(mockServiceConfig);
            when(kernel.getMain()).thenReturn(mockMainService);
            when(kernel.orderedDependencies()).thenReturn(Arrays.asList(mockTestService, mockMainService));
            ListComponentsResponse response =
                    cliEventStreamAgent.getListComponentsHandler(mockContext).handleRequest(request);
            assertEquals(1, response.getComponents().size());
            ComponentDetails componentDetails = response.getComponents().get(0);
            assertEquals(TEST_SERVICE, componentDetails.getComponentName());
            assertEquals(mockParameterConfig, componentDetails.getConfiguration());
            assertEquals("1.0.0", componentDetails.getVersion());
        }
    }

    @Test
    void testRestartComponent_emptyComponentName() {
        RestartComponentRequest restartComponentRequest = new RestartComponentRequest();
        assertThrows(InvalidArgumentsError.class, () -> cliEventStreamAgent.getRestartComponentsHandler(mockContext)
                .handleRequest(restartComponentRequest));
    }

    @Test
    void testRestartComponent_component_not_found(ExtensionContext context) throws ServiceLoadException {
        ignoreExceptionOfType(context, ServiceLoadException.class);
        RestartComponentRequest restartComponentRequest = new RestartComponentRequest();
        restartComponentRequest.setComponentName("INVALID_COMPONENT");
        when(kernel.locate("INVALID_COMPONENT")).thenThrow(new ServiceLoadException("error"));
        assertThrows(ResourceNotFoundError.class, () -> cliEventStreamAgent.getRestartComponentsHandler(mockContext)
                .handleRequest(restartComponentRequest));
    }

    @Test
    void testRestartComponent_component_restart() throws ServiceLoadException {
        RestartComponentRequest restartComponentRequest = new RestartComponentRequest();
        restartComponentRequest.setComponentName(TEST_SERVICE);
        GreengrassService mockTestService = mock(GreengrassService.class);
        when(kernel.locate(TEST_SERVICE)).thenReturn(mockTestService);
        cliEventStreamAgent.getRestartComponentsHandler(mockContext).handleRequest(restartComponentRequest);
        verify(mockTestService).requestRestart();
    }

    @Test
    void testStopComponent_emptyComponentName() {
        StopComponentRequest stopComponentRequest = new StopComponentRequest();
        assertThrows(InvalidArgumentsError.class,
                () -> cliEventStreamAgent.getStopComponentsHandler(mockContext).handleRequest(stopComponentRequest));
    }

    @Test
    void testStopComponent_component_not_found(ExtensionContext context) throws ServiceLoadException {
        ignoreExceptionOfType(context, ServiceLoadException.class);
        StopComponentRequest stopComponentRequest = new StopComponentRequest();
        stopComponentRequest.setComponentName("INVALID_COMPONENT");
        when(kernel.locate("INVALID_COMPONENT")).thenThrow(new ServiceLoadException("error"));
        assertThrows(ResourceNotFoundError.class,
                () -> cliEventStreamAgent.getStopComponentsHandler(mockContext).handleRequest(stopComponentRequest));
    }

    @Test
    void testStopComponent_component_restart() throws ServiceLoadException {
        StopComponentRequest stopComponentRequest = new StopComponentRequest();
        stopComponentRequest.setComponentName(TEST_SERVICE);
        GreengrassService mockTestService = mock(GreengrassService.class);
        when(kernel.locate(TEST_SERVICE)).thenReturn(mockTestService);
        cliEventStreamAgent.getStopComponentsHandler(mockContext).handleRequest(stopComponentRequest);
        verify(mockTestService).requestStop();
    }

    @Test
    void testCreateLocalDeployment_deployments_Q_not_initialized(ExtensionContext context) {
        ignoreExceptionOfType(context, ServiceError.class);
        cliEventStreamAgent.setDeploymentQueue(null);   // set it to be not initialized
        Topics mockCliConfig = mock(Topics.class);
        CreateLocalDeploymentRequest request = new CreateLocalDeploymentRequest();
        try {
            cliEventStreamAgent.getCreateLocalDeploymentHandler(mockContext, mockCliConfig).handleRequest(request);
        } catch (ServiceError e) {
            assertEquals(DEPLOYMENTS_QUEUE_NOT_INITIALIZED, e.getMessage());
            return;
        }
        fail();
    }

    @Test
    void testCreateLocalDeployment_successful() throws JsonProcessingException {
        Topics mockCliConfig = mock(Topics.class);
        Topics localDeployments = mock(Topics.class);
        Topics localDeploymentDetailsTopics = mock(Topics.class);
        when(localDeployments.lookupTopics(any())).thenReturn(localDeploymentDetailsTopics);
        when(mockCliConfig.lookupTopics(PERSISTENT_LOCAL_DEPLOYMENTS)).thenReturn(localDeployments);
        when(deploymentQueue.offer(any())).thenReturn(true);
        cliEventStreamAgent.setDeploymentQueue(deploymentQueue);
        CreateLocalDeploymentRequest request = new CreateLocalDeploymentRequest();
        request.setGroupName(MOCK_GROUP);
        request.setRootComponentVersionsToAdd(ImmutableMap.of(TEST_SERVICE, "1.0.0"));
        request.setRootComponentsToRemove(Arrays.asList("SomeService"));
        request.setFailureHandlingPolicy(FailureHandlingPolicy.ROLLBACK.getValue());
        Map<String, String> componentConfigToMerge = new HashMap<>();
        componentConfigToMerge.put("param1", "value1");
        Map<String, Map<String, Object>> action = new HashMap<>();
        action.put(TEST_SERVICE, ImmutableMap.of("MERGE", componentConfigToMerge));
        request.setComponentToConfiguration(action);
        cliEventStreamAgent.getCreateLocalDeploymentHandler(mockContext, mockCliConfig).handleRequest(request);
        ArgumentCaptor<Deployment> deploymentCaptor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentQueue).offer(deploymentCaptor.capture());
        String deploymentDoc = deploymentCaptor.getValue().getDeploymentDocument();
        LocalOverrideRequest localOverrideRequest = OBJECT_MAPPER.readValue(deploymentDoc, LocalOverrideRequest.class);
        assertEquals(MOCK_GROUP, localOverrideRequest.getGroupName());
        assertTrue(localOverrideRequest.getComponentsToMerge().containsKey(TEST_SERVICE));
        assertTrue(localOverrideRequest.getComponentsToMerge().containsValue("1.0.0"));
        assertTrue(localOverrideRequest.getComponentsToRemove().contains("SomeService"));
        assertEquals(localOverrideRequest.getFailureHandlingPolicy().getValue(), FailureHandlingPolicy.ROLLBACK.getValue());
        assertNotNull(localOverrideRequest.getConfigurationUpdate().get(TEST_SERVICE));
        assertEquals("value1",
                localOverrideRequest.getConfigurationUpdate().get(TEST_SERVICE).getValueToMerge().get("param1"));


        verify(localDeployments).lookupTopics(localOverrideRequest.getRequestId());
        ArgumentCaptor<Map> deploymentDetailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(localDeploymentDetailsTopics).replaceAndWait(deploymentDetailsCaptor.capture());
        CLIEventStreamAgent.LocalDeploymentDetails localDeploymentDetails = OBJECT_MAPPER
                .convertValue((Map<String, Object>) deploymentDetailsCaptor.getValue(),
                        CLIEventStreamAgent.LocalDeploymentDetails.class);
        assertEquals(Deployment.DeploymentType.LOCAL, localDeploymentDetails.getDeploymentType());
        assertEquals(DeploymentStatus.QUEUED, localDeploymentDetails.getStatus());
    }

    @Test
    void testCreateLocalDeployment_clean_up_queued_deployment() throws JsonProcessingException {
        Topics cliServiceConfig = generateTopics();
        when(deploymentQueue.isEmpty()).thenReturn(true);
        when(deploymentQueue.offer(any())).thenReturn(true);
        cliEventStreamAgent.setDeploymentQueue(deploymentQueue);
        CreateLocalDeploymentRequest request = new CreateLocalDeploymentRequest();
        request.setGroupName(MOCK_GROUP);
        request.setRootComponentVersionsToAdd(ImmutableMap.of(TEST_SERVICE, "1.0.0"));
        request.setRootComponentsToRemove(Arrays.asList("SomeService"));
        Map<String, String> componentConfigToMerge = new HashMap<>();
        componentConfigToMerge.put("param1", "value1");
        Map<String, Map<String, Object>> action = new HashMap<>();
        action.put(TEST_SERVICE, ImmutableMap.of("MERGE", componentConfigToMerge));
        request.setComponentToConfiguration(action);
        cliEventStreamAgent.getCreateLocalDeploymentHandler(mockContext, cliServiceConfig).handleRequest(request);
        ArgumentCaptor<Deployment> deploymentCaptor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentQueue).offer(deploymentCaptor.capture());
        String deploymentDoc = deploymentCaptor.getValue().getDeploymentDocument();
        LocalOverrideRequest localOverrideRequest = OBJECT_MAPPER.readValue(deploymentDoc, LocalOverrideRequest.class);
        assertEquals(MOCK_GROUP, localOverrideRequest.getGroupName());
        assertTrue(localOverrideRequest.getComponentsToMerge().containsKey(TEST_SERVICE));
        assertTrue(localOverrideRequest.getComponentsToMerge().containsValue("1.0.0"));
        assertTrue(localOverrideRequest.getComponentsToRemove().contains("SomeService"));
        assertNotNull(localOverrideRequest.getConfigurationUpdate().get(TEST_SERVICE));
        assertEquals("value1",
                localOverrideRequest.getConfigurationUpdate().get(TEST_SERVICE).getValueToMerge().get("param1"));
    }

    private Topics generateTopics() {
        Topics cliServiceConfig = Topics.of(context, "runtime", null);
        cliServiceConfig.lookup(PERSISTENT_LOCAL_DEPLOYMENTS, "7f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd",
                "DeploymentId").withNewerValue(100, "NULL");
        cliServiceConfig.lookup(PERSISTENT_LOCAL_DEPLOYMENTS, "7f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd",
                "DeploymentStatus").withNewerValue(100, "FAILED");
        cliServiceConfig.lookup(PERSISTENT_LOCAL_DEPLOYMENTS, "7f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd",
                "DeploymentStatusDetails", "detailed-deployment-status").withNewerValue(100, "FAILED_ROLLBACK_NOT_REQUESTED");

        cliServiceConfig.lookup(PERSISTENT_LOCAL_DEPLOYMENTS, "6f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd",
                "DeploymentId").withNewerValue(100, "NULL");
        cliServiceConfig.lookup(PERSISTENT_LOCAL_DEPLOYMENTS, "6f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd",
                "DeploymentStatus").withNewerValue(100, "QUEUED");
        cliServiceConfig.lookup(PERSISTENT_LOCAL_DEPLOYMENTS, "6f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd",
                "DeploymentStatusDetails", "detailed-deployment-status").withNewerValue(100, "QUEUED1");

        cliServiceConfig.lookup(PERSISTENT_LOCAL_DEPLOYMENTS, "5f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd",
                "DeploymentId").withNewerValue(100, "NULL");
        cliServiceConfig.lookup(PERSISTENT_LOCAL_DEPLOYMENTS, "5f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd",
                "DeploymentStatus").withNewerValue(100, "QUEUED");
        cliServiceConfig.lookup(PERSISTENT_LOCAL_DEPLOYMENTS, "5f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd",
                "DeploymentStatusDetails", "detailed-deployment-status").withNewerValue(100, "QUEUED2");

        cliServiceConfig.lookup(PERSISTENT_LOCAL_DEPLOYMENTS, "4f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd",
                "DeploymentId").withNewerValue(100, "NULL");
        cliServiceConfig.lookup(PERSISTENT_LOCAL_DEPLOYMENTS, "4f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd",
                "DeploymentStatus").withNewerValue(100, "SUCCEEDED");
        cliServiceConfig.lookup(PERSISTENT_LOCAL_DEPLOYMENTS, "4f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd",
                "DeploymentStatusDetails", "detailed-deployment-status").withNewerValue(100, "SUCCEEDED");
        return cliServiceConfig;
    }

    @Test
    void testCancelLocalDeployment_invalidDeploymentId() {
        Topics mockCliConfig = mock(Topics.class);
        CancelLocalDeploymentRequest request = new CancelLocalDeploymentRequest();
        request.setDeploymentId("InvalidId");
        assertThrows(InvalidArgumentsError.class,
                () -> cliEventStreamAgent.getCancelLocalDeploymentHandler(mockContext, mockCliConfig)
                        .handleRequest(request));
    }

    @Test
    void testCancelLocalDeployment_deploymentId_not_exist() {
        Topics localDeployments = mock(Topics.class);
        Topics mockCliConfig = mock(Topics.class);
        String deploymentId = UUID.randomUUID().toString();
        when(mockCliConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS)).thenReturn(localDeployments);
        when(localDeployments.findTopics(deploymentId)).thenReturn(null);
        CancelLocalDeploymentRequest request = new CancelLocalDeploymentRequest();
        request.setDeploymentId(deploymentId);
        assertThrows(ResourceNotFoundError.class,
                () -> cliEventStreamAgent.getCancelLocalDeploymentHandler(mockContext, mockCliConfig)
                        .handleRequest(request));
    }

    @Test
    void testCancelLocalDeployment_deployment_already_finished() throws IOException {
        Topics localDeployments = mock(Topics.class);
        Topics mockCliConfig = mock(Topics.class);
        try (Context context = new Context()) {
            String deploymentId = UUID.randomUUID().toString();
            Topics mockLocalDeployment = Topics.of(context, deploymentId, null);
            mockLocalDeployment.lookup(DEPLOYMENT_STATUS_KEY_NAME).withValue(DeploymentStatus.SUCCEEDED.toString());
            when(mockCliConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS)).thenReturn(localDeployments);
            when(localDeployments.findTopics(deploymentId)).thenReturn(mockLocalDeployment);
            CancelLocalDeploymentRequest request = new CancelLocalDeploymentRequest();
            request.setDeploymentId(deploymentId);
            CancelLocalDeploymentResponse response =
                    cliEventStreamAgent.getCancelLocalDeploymentHandler(mockContext, mockCliConfig)
                            .handleRequest(request);
            assertEquals("Cancellation request is not processed because deployment is already finished",
                    response.getMessage());
        }
    }

    @Test
    void testCancelLocalDeployment_cancellation_submitted() throws IOException {
        Topics localDeployments = mock(Topics.class);
        Topics mockCliConfig = mock(Topics.class);
        when(deploymentQueue.offer(any())).thenReturn(true);
        cliEventStreamAgent.setDeploymentQueue(deploymentQueue);
        try (Context context = new Context()) {
            String deploymentId = UUID.randomUUID().toString();
            Topics mockLocalDeployment = Topics.of(context, deploymentId, null);
            mockLocalDeployment.lookup(DEPLOYMENT_STATUS_KEY_NAME).withValue(DeploymentStatus.IN_PROGRESS.toString());
            when(mockCliConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS)).thenReturn(localDeployments);
            when(localDeployments.findTopics(deploymentId)).thenReturn(mockLocalDeployment);
            CancelLocalDeploymentRequest request = new CancelLocalDeploymentRequest();
            request.setDeploymentId(deploymentId);
            CancelLocalDeploymentResponse response =
                    cliEventStreamAgent.getCancelLocalDeploymentHandler(mockContext, mockCliConfig)
                            .handleRequest(request);
            assertEquals("Cancel request submitted. Deployment ID: " + deploymentId,
                    response.getMessage());
            ArgumentCaptor<Deployment> deploymentCaptor = ArgumentCaptor.forClass(Deployment.class);
            verify(deploymentQueue).offer(deploymentCaptor.capture());
            Deployment deployment = deploymentCaptor.getValue();
            assertTrue(deployment.isCancelled());
            assertEquals(deploymentId, deployment.getId());
        }
    }

    @Test
    void testGetLocalDeploymentStatus_invalidDeploymentId() {
        Topics mockCliConfig = mock(Topics.class);
        GetLocalDeploymentStatusRequest request = new GetLocalDeploymentStatusRequest();
        request.setDeploymentId("InvalidId");
        assertThrows(InvalidArgumentsError.class,
                () -> cliEventStreamAgent.getGetLocalDeploymentStatusHandler(mockContext, mockCliConfig)
                        .handleRequest(request));
    }

    @Test
    void testGetLocalDeploymentStatus_deploymentId_not_exist() {
        Topics localDeployments = mock(Topics.class);
        Topics mockCliConfig = mock(Topics.class);
        String deploymentId = UUID.randomUUID().toString();
        when(mockCliConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS)).thenReturn(localDeployments);
        when(localDeployments.findTopics(deploymentId)).thenReturn(null);
        GetLocalDeploymentStatusRequest request = new GetLocalDeploymentStatusRequest();
        request.setDeploymentId(deploymentId);
        assertThrows(ResourceNotFoundError.class,
                () -> cliEventStreamAgent.getGetLocalDeploymentStatusHandler(mockContext, mockCliConfig)
                        .handleRequest(request));
    }

    @Test
    void testGetLocalDeploymentStatus_deployment_completes_succeeded() throws IOException {
        Topics localDeployments = mock(Topics.class);
        Topics mockCliConfig = mock(Topics.class);
        try (Context context = new Context()) {
            String deploymentId = UUID.randomUUID().toString();
            Topics mockLocalDeployment = Topics.of(context, deploymentId, null);
            long creationTime = System.currentTimeMillis();
            mockLocalDeployment.lookup(LOCAL_DEPLOYMENT_CREATED_ON).withValue(creationTime);
            mockLocalDeployment.lookup(DEPLOYMENT_STATUS_KEY_NAME).withValue(DeploymentStatus.SUCCEEDED.toString());
            Topics mockLocalDeploymentStatusDetails = mockLocalDeployment.createInteriorChild(DEPLOYMENT_STATUS_DETAILS_KEY_NAME);
            mockLocalDeploymentStatusDetails.lookup(DEPLOYMENT_DETAILED_STATUS_KEY).withValue(DeploymentResult.DeploymentStatus.SUCCESSFUL.toString());
            when(mockCliConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS)).thenReturn(localDeployments);
            when(localDeployments.findTopics(deploymentId)).thenReturn(mockLocalDeployment);
            GetLocalDeploymentStatusRequest request = new GetLocalDeploymentStatusRequest();
            request.setDeploymentId(deploymentId);
            GetLocalDeploymentStatusResponse response =
                    cliEventStreamAgent.getGetLocalDeploymentStatusHandler(mockContext, mockCliConfig)
                            .handleRequest(request);
            assertEquals(deploymentId, response.getDeployment().getDeploymentId());
            assertEquals(Instant.ofEpochMilli(creationTime).atZone(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofPattern(LOCAL_DEPLOYMENT_CREATED_ON_FORMATTER)), response.getDeployment().getCreatedOn());
            assertEquals(DeploymentStatus.SUCCEEDED, response.getDeployment().getStatus());
            assertEquals(DeploymentResult.DeploymentStatus.SUCCESSFUL.toString(),
                    response.getDeployment().getDeploymentStatusDetails().getDetailedDeploymentStatus().toString());
        }
    }

    @Test
    void testGetLocalDeploymentStatus_deployment_completes_failed() throws IOException {
        Topics localDeployments = mock(Topics.class);
        Topics mockCliConfig = mock(Topics.class);
        try (Context context = new Context()) {
            String deploymentId = UUID.randomUUID().toString();
            Topics mockLocalDeployment = Topics.of(context, deploymentId, null);
            mockLocalDeployment.lookup(DEPLOYMENT_STATUS_KEY_NAME).withValue(DeploymentStatus.FAILED.toString());
            Topics mockLocalDeploymentStatusDetails = mockLocalDeployment.createInteriorChild(DEPLOYMENT_STATUS_DETAILS_KEY_NAME);
            mockLocalDeploymentStatusDetails.lookup(DEPLOYMENT_DETAILED_STATUS_KEY).withValue(DeploymentResult.DeploymentStatus.FAILED_NO_STATE_CHANGE.toString());
            mockLocalDeploymentStatusDetails.lookup(DEPLOYMENT_ERROR_STACK_KEY).withValue(Collections.singletonList("ERROR_STACK_1, ERROR_STACK_2, ERROR_STACK_3"));
            mockLocalDeploymentStatusDetails.lookup(DEPLOYMENT_ERROR_TYPES_KEY).withValue(Collections.singletonList("ERROR_TYPE_1"));
            mockLocalDeploymentStatusDetails.lookup(DEPLOYMENT_FAILURE_CAUSE_KEY).withValue("FAILURE_CAUSE");
            when(mockCliConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS)).thenReturn(localDeployments);
            when(localDeployments.findTopics(deploymentId)).thenReturn(mockLocalDeployment);
            GetLocalDeploymentStatusRequest request = new GetLocalDeploymentStatusRequest();
            request.setDeploymentId(deploymentId);
            GetLocalDeploymentStatusResponse response =
                    cliEventStreamAgent.getGetLocalDeploymentStatusHandler(mockContext, mockCliConfig)
                            .handleRequest(request);
            assertEquals(deploymentId, response.getDeployment().getDeploymentId());
            assertEquals(DeploymentStatus.FAILED, response.getDeployment().getStatus());
            assertEquals(DeploymentResult.DeploymentStatus.FAILED_NO_STATE_CHANGE.toString(),
                    response.getDeployment().getDeploymentStatusDetails().getDetailedDeploymentStatus().toString());
            assertEquals(Collections.singletonList("ERROR_STACK_1, ERROR_STACK_2, ERROR_STACK_3"),
                    response.getDeployment().getDeploymentStatusDetails().getDeploymentErrorStack());
            assertEquals(Collections.singletonList("ERROR_TYPE_1"),
                    response.getDeployment().getDeploymentStatusDetails().getDeploymentErrorTypes());
            assertEquals("FAILURE_CAUSE", response.getDeployment().getDeploymentStatusDetails().getDeploymentFailureCause());
        }
    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    void testGetLocalDeploymentStatus_deployment_in_progress() throws IOException {
        Topics localDeployments = mock(Topics.class);
        Topics mockCliConfig = mock(Topics.class);
        try (Context context = new Context()) {
            String deploymentId = UUID.randomUUID().toString();
            Topics mockLocalDeployment = Topics.of(context, deploymentId, null);
            mockLocalDeployment.lookup(DEPLOYMENT_STATUS_KEY_NAME).withValue(DeploymentStatus.IN_PROGRESS.toString());
            when(mockCliConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS)).thenReturn(localDeployments);
            when(localDeployments.findTopics(deploymentId)).thenReturn(mockLocalDeployment);
            GetLocalDeploymentStatusRequest request = new GetLocalDeploymentStatusRequest();
            request.setDeploymentId(deploymentId);
            GetLocalDeploymentStatusResponse response =
                    cliEventStreamAgent.getGetLocalDeploymentStatusHandler(mockContext, mockCliConfig)
                            .handleRequest(request);
            assertEquals(deploymentId, response.getDeployment().getDeploymentId());
            assertEquals(DeploymentStatus.IN_PROGRESS, response.getDeployment().getStatus());
        }
    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    void testGetLocalDeploymentStatus_clean_up_queued_deployment() {
        when(deploymentQueue.isEmpty()).thenReturn(true);
        Topics cliServiceConfig = generateTopics();
        GetLocalDeploymentStatusRequest request = new GetLocalDeploymentStatusRequest();
        request.setDeploymentId("7f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd");
        cliEventStreamAgent.getGetLocalDeploymentStatusHandler(mockContext, cliServiceConfig)
                .handleRequest(request);
        assertNotNull(cliServiceConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS, "7f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd"));
        assertNull(cliServiceConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS, "6f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd"));
        assertNull(cliServiceConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS, "5f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd"));
        assertNotNull(cliServiceConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS, "4f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd"));
    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    void testListLocalDeployment_no_local_deployments() throws IOException {
        Topics mockCliConfig = mock(Topics.class);
        try (Context context = new Context()) {
            Topics localDeployments = Topics.of(context, "localDeployments", null);
            when(mockCliConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS)).thenReturn(localDeployments);
            ListLocalDeploymentsRequest request = new ListLocalDeploymentsRequest();
            ListLocalDeploymentsResponse response =
                    cliEventStreamAgent.getListLocalDeploymentsHandler(mockContext, mockCliConfig)
                            .handleRequest(request);
            assertEquals(0, response.getLocalDeployments().size());
        }
    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    void testListLocalDeployment_successful() throws IOException {
        Topics mockCliConfig = mock(Topics.class);
        try (Context context = new Context()) {
            Topics localDeployments = Topics.of(context, "localDeployments", null);
            String deploymentId1 = UUID.randomUUID().toString();
            localDeployments.lookupTopics(deploymentId1).lookup(DEPLOYMENT_STATUS_KEY_NAME)
                    .withValue(DeploymentStatus.IN_PROGRESS.toString());
            String deploymentId2 = UUID.randomUUID().toString();
            localDeployments.lookupTopics(deploymentId2).lookup(DEPLOYMENT_STATUS_KEY_NAME)
                    .withValue(DeploymentStatus.SUCCEEDED.toString());
            when(mockCliConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS)).thenReturn(localDeployments);
            ListLocalDeploymentsRequest request = new ListLocalDeploymentsRequest();
            ListLocalDeploymentsResponse response =
                    cliEventStreamAgent.getListLocalDeploymentsHandler(mockContext, mockCliConfig)
                            .handleRequest(request);
            assertEquals(2, response.getLocalDeployments().size());
            response.getLocalDeployments().stream().forEach(ld -> {
                if (ld.getDeploymentId().equals(deploymentId1)) {
                    assertEquals(DeploymentStatus.IN_PROGRESS, ld.getStatus());
                } else if (ld.getDeploymentId().equals(deploymentId2)) {
                    assertEquals(DeploymentStatus.SUCCEEDED, ld.getStatus());
                } else {
                    fail("Invalid deploymentId found in list of local deployments");
                }
            });
        }
    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    void testListLocalDeployment_clean_up_queued_deployment() throws IOException {
        when(deploymentQueue.isEmpty()).thenReturn(true);
        Topics cliServiceConfig = generateTopics();
        ListLocalDeploymentsRequest request = new ListLocalDeploymentsRequest();
        ListLocalDeploymentsResponse response =
                cliEventStreamAgent.getListLocalDeploymentsHandler(mockContext, cliServiceConfig)
                        .handleRequest(request);
        assertNotNull(cliServiceConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS, "7f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd"));
        assertNull(cliServiceConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS, "6f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd"));
        assertNull(cliServiceConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS, "5f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd"));
        assertNotNull(cliServiceConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS, "4f3d2572-e8fb-4b7b-b4b6-4f774b5d16bd"));
        assertEquals(2, response.getLocalDeployments().size());
    }

    @Test
    void test_createDebugPassword() throws IOException {
        CreateDebugPasswordRequest request = new CreateDebugPasswordRequest();
        try (Context context = new Context()) {
            Topics topics = Topics.of(context, "", null);
            CreateDebugPasswordResponse response =
                    cliEventStreamAgent.getCreateDebugPasswordHandler(mockContext, topics).handleRequest(request);

            assertEquals("debug", response.getUsername());
            assertThat(response.getPassword(), hasLength(43)); // Length is 43 due to Base64 encoding overhead
            assertNotNull(topics.findTopics("_debugPassword", "debug", response.getPassword()));
            assertEquals(response.getPasswordExpiration().toEpochMilli(),
                    Coerce.toLong(topics.find("_debugPassword", "debug", response.getPassword(), "expiration")));
            assertNull(response.getCertificateSHA256Hash());
            assertNull(response.getCertificateSHA1Hash());

            topics.lookup("_certificateFingerprint", "SHA-1").withValue("ABCD");
            topics.lookup("_certificateFingerprint", "SHA-256").withValue("ABCDE");
            response =
                    cliEventStreamAgent.getCreateDebugPasswordHandler(mockContext, topics).handleRequest(request);
            assertEquals("ABCD", response.getCertificateSHA1Hash());
            assertEquals("ABCDE", response.getCertificateSHA256Hash());
        }
    }
}
