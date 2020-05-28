/*
  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
  * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.CLI;
import com.aws.iot.evergreen.cli.adapter.AdapterModule;
import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import com.aws.iot.evergreen.cli.adapter.LocalOverrideRequest;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ComponentCommandTest {

    private static final String RECIPE_FOLDER_PATH_STR = "recipeFolderPath";
    private static final String ARTIFACT_FOLDER_PATH_STR = "artifactFolderPath";
    private static final String NEW_COMPONENT_1 = "newComponent1";
    private static final String NEW_COMPONENT_2 = "newComponent2";
    private static final String NEW_COMPONENT_1_WITH_VERSION = "newComponent1=1.0.0";
    private static final String NEW_COMPONENT_2_WITH_VERSION = "newComponent2=2.0.0";


    private static final Map<String, String> ROOT_COMPONENTS =
            ImmutableMap.of(NEW_COMPONENT_1, "1.0.0", NEW_COMPONENT_2, "2.0.0");

    @Mock
    private KernelAdapter kernelAdapter;

    @Test
    void GIVEN_WHEN_components_to_merge_provided_THEN_request_contains_provided_components_to_merge() {
        int exitCode = runCommandLine("component", "update", "-m", NEW_COMPONENT_1_WITH_VERSION, "--merge",
                NEW_COMPONENT_2_WITH_VERSION);

        LocalOverrideRequest expectedRequest = LocalOverrideRequest.builder().componentsToMerge(ROOT_COMPONENTS)
                .componentNameToConfig(Collections.emptyMap()).build();

        verify(kernelAdapter).localOverride(expectedRequest);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_components_to_remove_provided_THEN_request_contains_provided_components_to_remove() {
        int exitCode = runCommandLine("component", "update", "--remove", NEW_COMPONENT_1, "--remove",
                NEW_COMPONENT_2);

        LocalOverrideRequest expectedRequest =
                LocalOverrideRequest.builder().componentsToRemove(Arrays.asList(NEW_COMPONENT_1, NEW_COMPONENT_2))
                        .componentNameToConfig(Collections.emptyMap()).build();

        verify(kernelAdapter).localOverride(expectedRequest);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_artifact_dir_is_provided_THEN_request_contains_provided_artifact_dir() {
        int exitCode = runCommandLine("component", "update", "--artifactDir", ARTIFACT_FOLDER_PATH_STR);

        LocalOverrideRequest expectedRequest = LocalOverrideRequest.builder().artifactDir(ARTIFACT_FOLDER_PATH_STR)
                .componentNameToConfig(Collections.emptyMap()).build();

        verify(kernelAdapter).localOverride(expectedRequest);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_artifact_dir_is_provided_with_short_name_THEN_request_contains_provided_artifact_dir() {
        int exitCode = runCommandLine("component", "update", "-a", ARTIFACT_FOLDER_PATH_STR);

        LocalOverrideRequest expectedRequest = LocalOverrideRequest.builder().artifactDir(ARTIFACT_FOLDER_PATH_STR)
                .componentNameToConfig(Collections.emptyMap()).build();

        verify(kernelAdapter).localOverride(expectedRequest);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_artifact_dir_is_provided_more_than_once_THEN_invalid_request_is_returned() {
        int exitCode =
                runCommandLine("component", "update", "-a", ARTIFACT_FOLDER_PATH_STR, "-a", ARTIFACT_FOLDER_PATH_STR);

        LocalOverrideRequest expectedRequest = LocalOverrideRequest.builder().artifactDir(ARTIFACT_FOLDER_PATH_STR)
                .componentNameToConfig(Collections.emptyMap()).build();

        verify(kernelAdapter, never()).localOverride(expectedRequest);
        assertThat(exitCode, is(2));
    }

    @Test
    void GIVEN_WHEN_recipe_dir_is_provided_THEN_request_contains_provided_recipe_dir() {
        int exitCode = runCommandLine("component", "update", "--recipeDir", RECIPE_FOLDER_PATH_STR);

        LocalOverrideRequest expectedRequest = LocalOverrideRequest.builder().recipeDir(RECIPE_FOLDER_PATH_STR)
                .componentNameToConfig(Collections.emptyMap()).build();

        verify(kernelAdapter).localOverride(expectedRequest);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_recipe_dir_is_provided_with_short_name_THEN_request_contains_provided_recipe_dir() {
        int exitCode = runCommandLine("component", "update", "-r", RECIPE_FOLDER_PATH_STR);

        LocalOverrideRequest expectedRequest = LocalOverrideRequest.builder().recipeDir(RECIPE_FOLDER_PATH_STR)
                .componentNameToConfig(Collections.emptyMap()).build();

        verify(kernelAdapter).localOverride(expectedRequest);
        assertThat(exitCode, is(0));
    }


    @Test
    void GIVEN_WHEN_recipe_dir_is_provided_more_than_once_THEN_invalid_request_is_returned() {
        int exitCode =
                runCommandLine("component", "update", "-r", RECIPE_FOLDER_PATH_STR, "-r", RECIPE_FOLDER_PATH_STR);

        LocalOverrideRequest expectedRequest =
                LocalOverrideRequest.builder().componentsToMerge(ROOT_COMPONENTS).recipeDir(RECIPE_FOLDER_PATH_STR)
                        .build();

        verify(kernelAdapter, never()).localOverride(expectedRequest);
        assertThat(exitCode, is(2));
    }


    @Test
    void GIVEN_WHEN_params_are_provided_THEN_request_contain_all_params() {
        int exitCode = runCommandLine("component", "update", "--param", "newComponent1.K1=V1", "--param",
                "newComponent1" + ".nested.K2=V2", "--param", "newComponent2.K3=V3");

        Map<String, Map<String, Object>> componentNameToConfig = new HashMap<>();
        componentNameToConfig.put(NEW_COMPONENT_1, new HashMap<>());
        componentNameToConfig.get(NEW_COMPONENT_1).put("K1", "V1");
        componentNameToConfig.get(NEW_COMPONENT_1).put("nested", new HashMap<>());
        ((HashMap) componentNameToConfig.get(NEW_COMPONENT_1).get("nested")).put("K2", "V2");

        componentNameToConfig.put(NEW_COMPONENT_2, new HashMap<>());
        componentNameToConfig.get(NEW_COMPONENT_2).put("K3", "V3");

        LocalOverrideRequest expectedRequest =
                LocalOverrideRequest.builder().componentNameToConfig(componentNameToConfig).build();

        verify(kernelAdapter).localOverride(expectedRequest);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_no_option_provided_THEN_request_is_empty() {
        int exitCode = runCommandLine("component", "update");
        LocalOverrideRequest expectedRequest =
                LocalOverrideRequest.builder().componentNameToConfig(Collections.emptyMap()).build();
        verify(kernelAdapter, only()).localOverride(expectedRequest);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_invalid_params_are_provided_THEN_exit_1() {
        int exitCode = runCommandLine("component", "update", "--param", "newComponent1=V1");
        verify(kernelAdapter, never()).localOverride(any());
        assertThat(exitCode, is(1));
    }

    @Test
    void WHEN_list_components_called_THEN_print_info_and_exit_0(){
        int exitCode = runCommandLine("component", "list");
        verify(kernelAdapter, only()).listComponents();
        assertThat(exitCode, is(0));
    }

    private int runCommandLine(String... args) {
        return new CommandLine(new CLI(), new CLI.GuiceFactory(new AbstractModule() {
            @Override
            protected void configure() {
                bind(KernelAdapter.class).toInstance(kernelAdapter);
            }
        })).execute(args);
    }
}