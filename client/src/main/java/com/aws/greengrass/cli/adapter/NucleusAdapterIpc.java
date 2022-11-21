/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.adapter;


import software.amazon.awssdk.aws.greengrass.model.ComponentDetails;
import software.amazon.awssdk.aws.greengrass.model.CreateDebugPasswordResponse;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.LocalDeployment;

import java.io.IOException;
import java.util.List;

public interface NucleusAdapterIpc {

    ComponentDetails getComponentDetails(String componentName);

    void restartComponent(String... componentNames);

    void stopComponent(String... componentNames);

    LocalDeployment getLocalDeploymentStatus(String deploymentId);

    List<LocalDeployment> listLocalDeployments();

    String createLocalDeployment(CreateLocalDeploymentRequest createLocalDeploymentRequest);

    List<ComponentDetails> listComponents();

    CreateDebugPasswordResponse createDebugPassword();

    void publishToTopic(String topicName, String message);

    void publishToIoTCore(String topicName, String message, String qos);

    void subscribeToTopic(String topicName) throws IOException;

    void subscribeToIoTCore(String topicName, String qos) throws IOException;
}
