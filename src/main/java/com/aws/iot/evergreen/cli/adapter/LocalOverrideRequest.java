/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.adapter;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class LocalOverrideRequest {
    List<String> rootComponentNames;
    String recipeDir;
    String artifactDir;
}
