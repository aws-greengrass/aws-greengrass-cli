/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.adapter;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class LocalOverrideRequest {

    @EqualsAndHashCode.Exclude
    String requestId;   // UUID

    @EqualsAndHashCode.Exclude
    long requestTimestamp;

    Map<String, String> componentsToMerge;  // name to version
    List<String> componentsToRemove; // remove just need name
    String recipeDir;
    String artifactDir;

    Map<String, Map<String, Object>> componentNameToConfig;
}