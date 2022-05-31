/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli;

import com.aws.greengrass.authorization.AuthorizationHandler;
import com.aws.greengrass.authorization.Permission;
import com.aws.greengrass.authorization.exceptions.AuthorizationException;
import com.aws.greengrass.componentmanager.ComponentStore;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.deployment.DeploymentQueue;
import com.aws.greengrass.deployment.model.ConfigurationUpdateOperation;
import com.aws.greengrass.deployment.model.Deployment;
import com.aws.greengrass.deployment.model.LocalOverrideRequest;
import com.aws.greengrass.lifecyclemanager.GreengrassService;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.lifecyclemanager.exceptions.ServiceLoadException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.Utils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractCreateDebugPasswordOperationHandler;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractCreateLocalDeploymentOperationHandler;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractGetComponentDetailsOperationHandler;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractGetLocalDeploymentStatusOperationHandler;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractListComponentsOperationHandler;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractListLocalDeploymentsOperationHandler;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractRestartComponentOperationHandler;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractStopComponentOperationHandler;
import software.amazon.awssdk.aws.greengrass.model.ComponentDetails;
import software.amazon.awssdk.aws.greengrass.model.CreateDebugPasswordRequest;
import software.amazon.awssdk.aws.greengrass.model.CreateDebugPasswordResponse;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentResponse;
import software.amazon.awssdk.aws.greengrass.model.DeploymentStatus;
import software.amazon.awssdk.aws.greengrass.model.GetComponentDetailsRequest;
import software.amazon.awssdk.aws.greengrass.model.GetComponentDetailsResponse;
import software.amazon.awssdk.aws.greengrass.model.GetLocalDeploymentStatusRequest;
import software.amazon.awssdk.aws.greengrass.model.GetLocalDeploymentStatusResponse;
import software.amazon.awssdk.aws.greengrass.model.InvalidArgumentsError;
import software.amazon.awssdk.aws.greengrass.model.InvalidArtifactsDirectoryPathError;
import software.amazon.awssdk.aws.greengrass.model.LifecycleState;
import software.amazon.awssdk.aws.greengrass.model.ListComponentsRequest;
import software.amazon.awssdk.aws.greengrass.model.ListComponentsResponse;
import software.amazon.awssdk.aws.greengrass.model.ListLocalDeploymentsRequest;
import software.amazon.awssdk.aws.greengrass.model.ListLocalDeploymentsResponse;
import software.amazon.awssdk.aws.greengrass.model.LocalDeployment;
import software.amazon.awssdk.aws.greengrass.model.RequestStatus;
import software.amazon.awssdk.aws.greengrass.model.ResourceNotFoundError;
import software.amazon.awssdk.aws.greengrass.model.RestartComponentRequest;
import software.amazon.awssdk.aws.greengrass.model.RestartComponentResponse;
import software.amazon.awssdk.aws.greengrass.model.ServiceError;
import software.amazon.awssdk.aws.greengrass.model.StopComponentRequest;
import software.amazon.awssdk.aws.greengrass.model.StopComponentResponse;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.eventstreamrpc.OperationContinuationHandlerContext;
import software.amazon.awssdk.eventstreamrpc.model.EventStreamJsonMessage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Inject;

import static com.aws.greengrass.cli.CLIService.CLI_SERVICE;
import static com.aws.greengrass.cli.CLIService.GREENGRASS_CLI_CLIENT_ID_PREFIX;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.VERSION_CONFIG_KEY;
import static com.aws.greengrass.deployment.DeploymentConfigMerger.DEPLOYMENT_ID_LOG_KEY;
import static com.aws.greengrass.deployment.DeploymentStatusKeeper.DEPLOYMENT_ID_KEY_NAME;
import static com.aws.greengrass.deployment.DeploymentStatusKeeper.DEPLOYMENT_STATUS_KEY_NAME;
import static com.aws.greengrass.deployment.DeploymentStatusKeeper.DEPLOYMENT_TYPE_KEY_NAME;
import static com.aws.greengrass.ipc.common.ExceptionUtil.translateExceptions;
import static com.aws.greengrass.ipc.common.IPCErrorStrings.DEPLOYMENTS_QUEUE_FULL;
import static com.aws.greengrass.ipc.common.IPCErrorStrings.DEPLOYMENTS_QUEUE_NOT_INITIALIZED;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCServiceModel.CREATE_DEBUG_PASSWORD;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCServiceModel.CREATE_LOCAL_DEPLOYMENT;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCServiceModel.GET_COMPONENT_DETAILS;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCServiceModel.GET_LOCAL_DEPLOYMENT_STATUS;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCServiceModel.LIST_COMPONENTS;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCServiceModel.LIST_LOCAL_DEPLOYMENTS;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCServiceModel.RESTART_COMPONENT;
import static software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCServiceModel.STOP_COMPONENT;

@SuppressWarnings("PMD.CouplingBetweenObjects")
public class CLIEventStreamAgent {
    public static final String PERSISTENT_LOCAL_DEPLOYMENTS = "LocalDeployments";
    public static final String LOCAL_DEPLOYMENT_RESOURCE = "LocalDeployment";

    private static final Logger logger = LogManager.getLogger(CLIEventStreamAgent.class);
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final int DEBUG_PASSWORD_LENGTH_REQUIREMENT = 32;
    private static final String DEBUG_USERNAME = "debug";
    private static final Duration DEBUG_PASSWORD_EXPIRATION = Duration.ofHours(8);
    protected static final String CERT_FINGERPRINT_NAMESPACE = "_certificateFingerprint";

    @Inject
    private Kernel kernel;

    @Inject
    @Setter(AccessLevel.PACKAGE)
    private DeploymentQueue deploymentQueue;

    @Inject
    private AuthorizationHandler authHandler;

    private final SecureRandom random = new SecureRandom();

    public GetComponentDetailsHandler getGetComponentDetailsHandler(OperationContinuationHandlerContext context) {
        return new GetComponentDetailsHandler(context);
    }

    public ListComponentsHandler getListComponentsHandler(OperationContinuationHandlerContext context) {
        return new ListComponentsHandler(context);
    }

    public RestartComponentsHandler getRestartComponentsHandler(OperationContinuationHandlerContext context) {
        return new RestartComponentsHandler(context);
    }

    public StopComponentHandler getStopComponentsHandler(OperationContinuationHandlerContext context) {
        return new StopComponentHandler(context);
    }

    public CreateLocalDeploymentHandler getCreateLocalDeploymentHandler(OperationContinuationHandlerContext context,
                                                                        Topics cliServiceConfig) {
        return new CreateLocalDeploymentHandler(context, cliServiceConfig);
    }

    public GetLocalDeploymentStatusHandler getGetLocalDeploymentStatusHandler(
            OperationContinuationHandlerContext context, Topics cliServiceConfig) {
        return new GetLocalDeploymentStatusHandler(context, cliServiceConfig);
    }

    public ListLocalDeploymentsHandler getListLocalDeploymentsHandler(OperationContinuationHandlerContext context,
                                                                      Topics cliServiceConfig) {
        return new ListLocalDeploymentsHandler(context, cliServiceConfig);
    }

    public CreateDebugPasswordHandler getCreateDebugPasswordHandler(OperationContinuationHandlerContext context,
                                                                    Topics config) {
        return new CreateDebugPasswordHandler(context, config);
    }

    /**
     * Persists the local deployment details in the config.
     *
     * @param serviceConfig     CLI service configuration
     * @param deploymentDetails Details of the local deployment to save
     */
    public void persistLocalDeployment(Topics serviceConfig, Map<String, Object> deploymentDetails) {
        Topics localDeployments = serviceConfig.lookupTopics(PERSISTENT_LOCAL_DEPLOYMENTS);
        String deploymentId = (String) deploymentDetails.get(DEPLOYMENT_ID_KEY_NAME);
        Topics localDeploymentDetails = localDeployments.lookupTopics(deploymentId);
        localDeploymentDetails.replaceAndWait(deploymentDetails);
        // TODO: [P41178971]: Implement a limit on no of local deployments to persist status for
    }

    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC")
    class GetComponentDetailsHandler extends GeneratedAbstractGetComponentDetailsOperationHandler {

        private final String componentName;

        public GetComponentDetailsHandler(OperationContinuationHandlerContext context) {
            super(context);
            this.componentName = context.getAuthenticationData().getIdentityLabel();
        }

        @Override
        protected void onStreamClosed() {

        }

        @Override
        @SuppressWarnings("PMD.PreserveStackTrace")
        public GetComponentDetailsResponse handleRequest(GetComponentDetailsRequest request) {
            return translateExceptions(() -> {
                validateGetComponentDetailsRequest(request);
                authorizeRequest(Permission.builder()
                        .principal(componentName)
                        .resource(request.getComponentName())
                        .operation(GET_COMPONENT_DETAILS)
                        .build());
                String componentName = request.getComponentName();
                GreengrassService service;
                try {
                    service = kernel.locate(componentName);
                } catch (ServiceLoadException e) {
                    logger.atError().kv("ComponentName", componentName)
                            .log("Did not find the component with the given name in Greengrass");
                    throw new ResourceNotFoundError(
                            "Component with name " + componentName + " not found in Greengrass");
                }
                ComponentDetails componentDetails = getComponentDetails(service);
                GetComponentDetailsResponse response = new GetComponentDetailsResponse();
                response.setComponentDetails(componentDetails);
                return response;
            });
        }

        @Override
        public void handleStreamEvent(EventStreamJsonMessage streamRequestEvent) {

        }

        private void validateGetComponentDetailsRequest(GetComponentDetailsRequest request) {
            validateComponentName(request.getComponentName());
        }
    }

    private void authorizeRequest(Permission permission) {
        if (Utils.isEmpty(permission.getPrincipal())) {
            throw new UnauthorizedError("Component is not authorized to call CLI APIs");
        }
        // Allow CLI and CLI clients to call anything
        if (permission.getPrincipal().startsWith(GREENGRASS_CLI_CLIENT_ID_PREFIX)
                || CLI_SERVICE.equals(permission.getPrincipal())) {
            return;
        }
        // Check for authorization for all other components
        try {
            authHandler.isAuthorized(CLI_SERVICE, permission);
        } catch (AuthorizationException e) {
            logger.atWarn().kv("error", e.getMessage()).kv("componentName", permission.getPrincipal())
                    .log("Not Authorized");
            throw new UnauthorizedError(e.getMessage());
        }
    }

    private ComponentDetails getComponentDetails(GreengrassService service) {
        ComponentDetails componentDetails = new ComponentDetails();
        componentDetails.setComponentName(service.getName());
        componentDetails.setState(LifecycleState.valueOf(service.getState().toString()));

        if (service.getServiceConfig().find(VERSION_CONFIG_KEY) != null) {
            componentDetails.setVersion(Coerce.toString(service.getServiceConfig().find(VERSION_CONFIG_KEY)));
        }
        if (service.getServiceConfig().findInteriorChild(CONFIGURATION_CONFIG_KEY) != null) {
            componentDetails
                    .setConfiguration(service.getServiceConfig().findInteriorChild(CONFIGURATION_CONFIG_KEY).toPOJO());
        }
        return componentDetails;
    }

    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC")
    class ListComponentsHandler extends GeneratedAbstractListComponentsOperationHandler {

        private final String componentName;

        public ListComponentsHandler(OperationContinuationHandlerContext context) {
            super(context);
            this.componentName = context.getAuthenticationData().getIdentityLabel();
        }

        @Override
        protected void onStreamClosed() {

        }

        @Override
        public ListComponentsResponse handleRequest(ListComponentsRequest request) {
            return translateExceptions(() -> {
                authorizeRequest(Permission.builder()
                        .principal(componentName)
                        .resource(AuthorizationHandler.ANY_REGEX)
                        .operation(LIST_COMPONENTS)
                        .build());
                Collection<GreengrassService> services = kernel.orderedDependencies();
                List<ComponentDetails> listOfComponents =
                        services.stream().filter(service -> service != kernel.getMain())
                                .map(CLIEventStreamAgent.this::getComponentDetails).collect(Collectors.toList());
                ListComponentsResponse response = new ListComponentsResponse();
                response.setComponents(listOfComponents);
                return response;
            });
        }

        @Override
        public void handleStreamEvent(EventStreamJsonMessage streamRequestEvent) {

        }
    }

    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC")
    class RestartComponentsHandler extends GeneratedAbstractRestartComponentOperationHandler {

        private final String componentName;

        public RestartComponentsHandler(OperationContinuationHandlerContext context) {
            super(context);
            this.componentName = context.getAuthenticationData().getIdentityLabel();
        }

        @Override
        protected void onStreamClosed() {

        }

        @Override
        @SuppressWarnings("PMD.PreserveStackTrace")
        public RestartComponentResponse handleRequest(RestartComponentRequest request) {
            return translateExceptions(() -> {
                validateRestartComponentRequest(request);
                authorizeRequest(Permission.builder()
                        .principal(componentName)
                        .resource(request.getComponentName())
                        .operation(RESTART_COMPONENT)
                        .build());
                String componentName = request.getComponentName();
                RestartComponentResponse response = new RestartComponentResponse();
                response.setRestartStatus(RequestStatus.SUCCEEDED);
                try {
                    GreengrassService service = kernel.locate(componentName);
                    // TODO: [P41179234]: Add checks that can prevent triggering a component restart/stop
                    // Success of this request means restart was triggered successfully
                    if (!service.requestRestart()) {
                        response.setRestartStatus(RequestStatus.FAILED);
                    }
                } catch (ServiceLoadException e) {
                    logger.atError().kv("ComponentName", componentName)
                            .log("Did not find the component with the given name in Greengrass");
                    throw new ResourceNotFoundError(
                            "Component with name " + componentName + " not found in Greengrass");
                }
                return response;
            });
        }

        private void validateRestartComponentRequest(RestartComponentRequest request) {
            validateComponentName(request.getComponentName());
        }

        @Override
        public void handleStreamEvent(EventStreamJsonMessage streamRequestEvent) {

        }
    }

    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC")
    class StopComponentHandler extends GeneratedAbstractStopComponentOperationHandler {

        private final String componentName;

        public StopComponentHandler(OperationContinuationHandlerContext context) {
            super(context);
            this.componentName = context.getAuthenticationData().getIdentityLabel();
        }

        @Override
        protected void onStreamClosed() {

        }

        @Override
        @SuppressWarnings("PMD.PreserveStackTrace")
        public StopComponentResponse handleRequest(StopComponentRequest request) {
            return translateExceptions(() -> {
                validateStopComponentRequest(request);
                authorizeRequest(Permission.builder()
                        .principal(componentName)
                        .resource(request.getComponentName())
                        .operation(STOP_COMPONENT)
                        .build());
                String componentName = request.getComponentName();
                try {
                    GreengrassService service = kernel.locate(componentName);
                    // TODO: [P41179234]: Add checks that can prevent triggering a component restart/stop
                    // Success of this request means stop was triggered successfully
                    service.requestStop();
                } catch (ServiceLoadException e) {
                    logger.atError().kv("ComponentName", componentName)
                            .log("Did not find the component with the given name in Greengrass");
                    throw new ResourceNotFoundError(
                            "Component with name " + componentName + " not found in Greengrass");
                }
                StopComponentResponse response = new StopComponentResponse();
                response.setStopStatus(RequestStatus.SUCCEEDED);
                return response;
            });
        }

        @Override
        public void handleStreamEvent(EventStreamJsonMessage streamRequestEvent) {

        }

        private void validateStopComponentRequest(StopComponentRequest request) {
            validateComponentName(request.getComponentName());
        }
    }

    class CreateLocalDeploymentHandler extends GeneratedAbstractCreateLocalDeploymentOperationHandler {

        private final Topics cliServiceConfig;
        private final String componentName;

        public CreateLocalDeploymentHandler(OperationContinuationHandlerContext context, Topics cliServiceConfig) {
            super(context);
            this.cliServiceConfig = cliServiceConfig;
            this.componentName = context.getAuthenticationData().getIdentityLabel();
        }

        @Override
        protected void onStreamClosed() {

        }

        @Override
        @SuppressWarnings({"PMD.PreserveStackTrace", "PMD.AvoidCatchingGenericException"})
        public CreateLocalDeploymentResponse handleRequest(CreateLocalDeploymentRequest request) {
            return translateExceptions(() -> {
                authorizeRequest(Permission.builder()
                        .principal(componentName)
                        .resource(AuthorizationHandler.ANY_REGEX)
                        .operation(CREATE_LOCAL_DEPLOYMENT)
                        .build());
                String deploymentId = UUID.randomUUID().toString();
                //All inputs are valid. If all inputs are empty, then user might just want to retrigger the deployment
                // with new recipes set using the updateRecipesAndArtifacts API.
                Map<String, ConfigurationUpdateOperation> configUpdate = null;
                if (request.getComponentToConfiguration() != null) {
                    configUpdate = request.getComponentToConfiguration().entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                                ConfigurationUpdateOperation configUpdateOption = new ConfigurationUpdateOperation();
                                configUpdateOption.setValueToMerge((Map) e.getValue().get("MERGE"));
                                configUpdateOption.setPathsToReset((List) e.getValue().get("RESET"));
                                return configUpdateOption;
                            }));
                }

                if (!Utils.isEmpty(request.getArtifactsDirectoryPath())) {
                    Path artifactsDirectoryPath = Paths.get(request.getArtifactsDirectoryPath());
                    Path kernelArtifactsDirectoryPath = kernel.getNucleusPaths().componentStorePath()
                            .resolve(ComponentStore.ARTIFACT_DIRECTORY);
                    if (kernelArtifactsDirectoryPath.startsWith(artifactsDirectoryPath)) {
                        String errorString = "Requested artifacts directory path is parent of kernel artifacts "
                                + "directory path. Specify another path to avoid recursive copy";
                        logger.atError().log(errorString);
                        throw new InvalidArtifactsDirectoryPathError(errorString);
                    }
                }

                LocalOverrideRequest localOverrideRequest = LocalOverrideRequest.builder().requestId(deploymentId)
                        .componentsToMerge(request.getRootComponentVersionsToAdd())
                        .componentsToRemove(request.getRootComponentsToRemove())
                        .componentToRunWithInfo(request.getComponentToRunWithInfo())
                        .recipeDirectoryPath(request.getRecipeDirectoryPath())
                        .artifactsDirectoryPath(request.getArtifactsDirectoryPath())
                        .requestTimestamp(System.currentTimeMillis())
                        .groupName(request.getGroupName())
                        .configurationUpdate(configUpdate).build();
                String deploymentDocument;
                try {
                    deploymentDocument = OBJECT_MAPPER.writeValueAsString(localOverrideRequest);
                } catch (JsonProcessingException e) {
                    logger.atError().setCause(e).log("Caught exception while parsing local deployment request");
                    throw new ServiceError(e.getMessage());
                }
                Deployment deployment =
                        new Deployment(deploymentDocument, Deployment.DeploymentType.LOCAL, deploymentId);
                if (deploymentQueue == null) {
                    logger.atError().log("Deployments queue not initialized");
                    throw new ServiceError(DEPLOYMENTS_QUEUE_NOT_INITIALIZED);
                } else {
                    // save the deployment status as queued
                    LocalDeploymentDetails localDeploymentDetails = new LocalDeploymentDetails();
                    localDeploymentDetails.setDeploymentId(deploymentId);
                    localDeploymentDetails.setDeploymentType(Deployment.DeploymentType.LOCAL);
                    localDeploymentDetails.setStatus(DeploymentStatus.QUEUED);
                    persistLocalDeployment(cliServiceConfig, localDeploymentDetails.convertToMapOfObject());
                    if (deploymentQueue.offer(deployment)) {
                        logger.atInfo().kv(DEPLOYMENT_ID_LOG_KEY, deploymentId)
                                .log("Submitted local deployment request.");
                        CreateLocalDeploymentResponse createLocalDeploymentResponse =
                                new CreateLocalDeploymentResponse();
                        createLocalDeploymentResponse.setDeploymentId(deploymentId);
                        return createLocalDeploymentResponse;
                    } else {
                        logger.atError().kv(DEPLOYMENT_ID_LOG_KEY, deploymentId)
                                .log("Failed to submit local deployment request because deployment queue is full");
                        throw new ServiceError(DEPLOYMENTS_QUEUE_FULL);
                    }
                }
            });
        }

        @Override
        public void handleStreamEvent(EventStreamJsonMessage streamRequestEvent) {
            //NA
        }
    }

    class GetLocalDeploymentStatusHandler extends GeneratedAbstractGetLocalDeploymentStatusOperationHandler {

        private final Topics cliServiceConfig;
        private final String componentName;

        public GetLocalDeploymentStatusHandler(OperationContinuationHandlerContext context, Topics cliServiceConfig) {
            super(context);
            this.cliServiceConfig = cliServiceConfig;
            this.componentName = context.getAuthenticationData().getIdentityLabel();
        }

        @Override
        protected void onStreamClosed() {

        }

        @Override
        public GetLocalDeploymentStatusResponse handleRequest(GetLocalDeploymentStatusRequest request) {
            return translateExceptions(() -> {
                validateGetLocalDeploymentStatusRequest(request);
                authorizeRequest(Permission.builder()
                        .principal(componentName)
                        .resource(request.getDeploymentId())
                        .operation(GET_LOCAL_DEPLOYMENT_STATUS)
                        .build());
                Topics localDeployments = cliServiceConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS);
                if (localDeployments == null || localDeployments.findTopics(request.getDeploymentId()) == null) {
                    ResourceNotFoundError rnf = new ResourceNotFoundError();
                    rnf.setMessage("Cannot find deployment");
                    rnf.setResourceType(LOCAL_DEPLOYMENT_RESOURCE);
                    rnf.setResourceName(request.getDeploymentId());
                    throw rnf;
                } else {
                    Topics deployment = localDeployments.findTopics(request.getDeploymentId());
                    DeploymentStatus status =
                            deploymentStatusFromString(Coerce.toString(deployment.find(DEPLOYMENT_STATUS_KEY_NAME)));
                    GetLocalDeploymentStatusResponse response = new GetLocalDeploymentStatusResponse();
                    LocalDeployment localDeployment = new LocalDeployment();
                    localDeployment.setDeploymentId(request.getDeploymentId());
                    localDeployment.setStatus(status);
                    response.setDeployment(localDeployment);
                    return response;
                }
            });
        }

        @Override
        public void handleStreamEvent(EventStreamJsonMessage streamRequestEvent) {

        }

        @SuppressWarnings("PMD.PreserveStackTrace")
        private void validateGetLocalDeploymentStatusRequest(GetLocalDeploymentStatusRequest request) {
            try {
                UUID.fromString(request.getDeploymentId());
            } catch (IllegalArgumentException e) {
                throw new InvalidArgumentsError("Invalid deploymentId format received. DeploymentId is a UUID");
            }
        }
    }

    class ListLocalDeploymentsHandler extends GeneratedAbstractListLocalDeploymentsOperationHandler {

        private final Topics cliServiceConfig;
        private final String componentName;

        public ListLocalDeploymentsHandler(OperationContinuationHandlerContext context, Topics cliServiceConfig) {
            super(context);
            this.cliServiceConfig = cliServiceConfig;
            this.componentName = context.getAuthenticationData().getIdentityLabel();
        }

        @Override
        protected void onStreamClosed() {

        }

        @Override
        public ListLocalDeploymentsResponse handleRequest(ListLocalDeploymentsRequest request) {
            return translateExceptions(() -> {
                authorizeRequest(Permission.builder()
                        .principal(componentName)
                        .resource(AuthorizationHandler.ANY_REGEX)
                        .operation(LIST_LOCAL_DEPLOYMENTS)
                        .build());
                List<LocalDeployment> persistedDeployments = new ArrayList<>();
                Topics localDeployments = cliServiceConfig.findTopics(PERSISTENT_LOCAL_DEPLOYMENTS);
                if (localDeployments != null) {
                    localDeployments.forEach(topic -> {
                        Topics topics = (Topics) topic;
                        LocalDeployment localDeployment = new LocalDeployment();
                        localDeployment.setDeploymentId(topics.getName());
                        localDeployment.setStatus(
                                deploymentStatusFromString(Coerce.toString(topics.find(DEPLOYMENT_STATUS_KEY_NAME))));
                        persistedDeployments.add(localDeployment);
                    });
                }
                ListLocalDeploymentsResponse response = new ListLocalDeploymentsResponse();
                response.setLocalDeployments(persistedDeployments);
                return response;
            });
        }

        @Override
        public void handleStreamEvent(EventStreamJsonMessage streamRequestEvent) {

        }
    }

    class CreateDebugPasswordHandler extends GeneratedAbstractCreateDebugPasswordOperationHandler {
        private final String componentName;
        private final Topics config;

        public CreateDebugPasswordHandler(OperationContinuationHandlerContext context, Topics config) {
            super(context);
            this.componentName = context.getAuthenticationData().getIdentityLabel();
            this.config = config;
        }

        @Override
        protected void onStreamClosed() {
        }

        @Override
        public CreateDebugPasswordResponse handleRequest(CreateDebugPasswordRequest request) {
            return translateExceptions(() -> {
                authorizeRequest(Permission.builder()
                        .principal(componentName)
                        .resource(AuthorizationHandler.ANY_REGEX)
                        .operation(CREATE_DEBUG_PASSWORD)
                        .build());
                CreateDebugPasswordResponse response = new CreateDebugPasswordResponse();

                String password = generatePassword(DEBUG_PASSWORD_LENGTH_REQUIREMENT);
                Instant expiration = Instant.now().plus(DEBUG_PASSWORD_EXPIRATION);
                ((Topics) config.lookupTopics("_debugPassword").withParentNeedsToKnow(false))
                        .lookup(DEBUG_USERNAME, password, "expiration").withValue(expiration.toEpochMilli());

                response.setCertificateSHA1Hash(Coerce.toString(config.find(CERT_FINGERPRINT_NAMESPACE, "SHA-1")));
                response.setCertificateSHA256Hash(Coerce.toString(config.find(CERT_FINGERPRINT_NAMESPACE, "SHA-256")));
                response.setPassword(password);
                response.setPasswordExpiration(expiration);
                response.setUsername(DEBUG_USERNAME);

                return response;
            });
        }

        @Override
        public void handleStreamEvent(EventStreamJsonMessage streamRequestEvent) {
        }
    }

    private String generatePassword(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        try {
            bytes = MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException ignored) {
            // Not possible since we know that sha-256 definitely exists
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validateComponentName(String componentName) {
        if (Utils.isEmpty(componentName)) {
            throw new InvalidArgumentsError("Component name cannot be empty");
        }
    }

    private DeploymentStatus deploymentStatusFromString(String status) {
        for (DeploymentStatus ds : DeploymentStatus.values()) {
            if (ds.getValue().equals(status.toUpperCase())) {
                return ds;
            }
        }
        return null;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LocalDeploymentDetails {
        @JsonProperty(DEPLOYMENT_ID_KEY_NAME)
        private String deploymentId;
        @JsonProperty(DEPLOYMENT_STATUS_KEY_NAME)
        private DeploymentStatus status;
        @JsonProperty(DEPLOYMENT_TYPE_KEY_NAME)
        private Deployment.DeploymentType deploymentType;

        /**
         * Returns a map of string to object representing the deployment details.
         *
         * @return Map of string to object
         */
        public Map<String, Object> convertToMapOfObject() {
            Map<String, Object> deploymentDetails = new HashMap<>();
            deploymentDetails.put(DEPLOYMENT_ID_KEY_NAME, deploymentId);
            deploymentDetails.put(DEPLOYMENT_STATUS_KEY_NAME, status.getValue());
            deploymentDetails.put(DEPLOYMENT_TYPE_KEY_NAME, Coerce.toString(deploymentType));
            return deploymentDetails;
        }
    }
}
