/*
  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
  * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.CLI;
import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import com.aws.iot.evergreen.cli.adapter.LocalOverrideRequest;
import com.google.inject.AbstractModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ComponentCommandTest {

    private static final String RECIPE_FOLDER_PATH_STR = "recipeFolderPath";
    private static final String ARTIFACT_FOLDER_PATH_STR = "artifactFolderPath";
    private static final String NEW_COMPONENT_1 = "newComponent1";
    private static final String NEW_COMPONENT_2 = "newComponent2";

    @Mock
    private KernelAdapter kernelAdapter;

    @Test
    void GIVEN_WHEN_all_options_supplied_THEN_request_contains_all_options() {
        int exitCode =
                runCommandLine("component", "deploy", "-n", NEW_COMPONENT_1, "--name", NEW_COMPONENT_2, "--recipeDir",
                        RECIPE_FOLDER_PATH_STR, "--artifactDir", ARTIFACT_FOLDER_PATH_STR);

        LocalOverrideRequest expectedRequest =
                LocalOverrideRequest.builder().rootComponentNames(Arrays.asList(NEW_COMPONENT_1, NEW_COMPONENT_2))
                        .recipeFile(RECIPE_FOLDER_PATH_STR).artifactDir(ARTIFACT_FOLDER_PATH_STR).build();

        verify(kernelAdapter).localOverride(expectedRequest);

        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_names_not_provided_THEN_request_contain_empty_name_list() {
        int exitCode = runCommandLine("component", "deploy", "--recipeFile", RECIPE_FOLDER_PATH_STR, "--artifactDir",
                ARTIFACT_FOLDER_PATH_STR);

        LocalOverrideRequest expectedRequest =
                LocalOverrideRequest.builder().rootComponentNames(Collections.emptyList())
                        .recipeFile(RECIPE_FOLDER_PATH_STR).artifactDir(ARTIFACT_FOLDER_PATH_STR).build();

        verify(kernelAdapter).localOverride(expectedRequest);

        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_recipe_dir_not_provided_THEN_request_contain_null_recipe_dir() {
        int exitCode =
                runCommandLine("component", "deploy", "-n", NEW_COMPONENT_1, "--name", NEW_COMPONENT_2, "--artifactDir",
                        ARTIFACT_FOLDER_PATH_STR);

        LocalOverrideRequest expectedRequest =
                LocalOverrideRequest.builder().rootComponentNames(Arrays.asList(NEW_COMPONENT_1, NEW_COMPONENT_2))
                        .artifactDir(ARTIFACT_FOLDER_PATH_STR).build();

        verify(kernelAdapter).localOverride(expectedRequest);

        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_artifact_dir_not_provided_THEN_request_contain_null_artifact_dir() {
        int exitCode =
                runCommandLine("component", "deploy", "-n", NEW_COMPONENT_1, "--name", NEW_COMPONENT_2, "--recipeDir",
                        RECIPE_FOLDER_PATH_STR);

        LocalOverrideRequest expectedRequest =
                LocalOverrideRequest.builder().rootComponentNames(Arrays.asList(NEW_COMPONENT_1, NEW_COMPONENT_2))
                        .recipeFile(RECIPE_FOLDER_PATH_STR).build();

        verify(kernelAdapter).localOverride(expectedRequest);

        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_no_option_provided_THEN_request_is_empty() {
        int exitCode = runCommandLine("component", "deploy");

        LocalOverrideRequest expectedRequest =
                LocalOverrideRequest.builder().rootComponentNames(Collections.emptyList()).build();

        verify(kernelAdapter).localOverride(expectedRequest);

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