/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.CLI;
import com.aws.greengrass.cli.CommandFactory;
import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.aws.greengrass.cli.module.AdapterModule;
import com.aws.greengrass.cli.module.DaggerCommandsComponent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.RunWithInfo;

import java.util.Arrays;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeploymentCommandTest {
    private static final String RECIPE_FOLDER_PATH_STR = "recipeFolderPath";
    private static final String ARTIFACT_FOLDER_PATH_STR = "artifactFolderPath";
    private static final String NEW_COMPONENT_1 = "newComponent1";
    private static final String NEW_COMPONENT_2 = "newComponent2";
    private static final String NEW_COMPONENT_3 = "aws.greengrass.componentname";
    private static final String NEW_COMPONENT_1_WITH_VERSION = "newComponent1=1.0.0";
    private static final String NEW_COMPONENT_2_WITH_VERSION = "newComponent2=2.0.0";


    private static final Map<String, String> ROOT_COMPONENTS =
            ImmutableMap.of(NEW_COMPONENT_1, "1.0.0", NEW_COMPONENT_2, "2.0.0");

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private NucleusAdapterIpc nucleusAdapteripc;

    @Test
    void GIVEN_WHEN_artifact_dir_is_provided_THEN_request_contains_provided_artifact_dir() {
        int exitCode = runCommandLine("deployment", "create", "--artifactDir", ARTIFACT_FOLDER_PATH_STR);
        verify(nucleusAdapteripc).updateRecipesAndArtifacts(null, ARTIFACT_FOLDER_PATH_STR);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_artifact_dir_is_provided_with_short_name_THEN_request_contains_provided_artifact_dir() {
        int exitCode = runCommandLine("deployment", "create", "-a", ARTIFACT_FOLDER_PATH_STR);

        verify(nucleusAdapteripc).updateRecipesAndArtifacts(null, ARTIFACT_FOLDER_PATH_STR);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_artifact_dir_is_provided_more_than_once_THEN_invalid_request_is_returned() {
        int exitCode =
                runCommandLine("deployment", "create", "-a", ARTIFACT_FOLDER_PATH_STR, "-a", ARTIFACT_FOLDER_PATH_STR);

        verify(nucleusAdapteripc, never()).updateRecipesAndArtifacts(any(), any());
        assertThat(exitCode, is(2));
    }

    @Test
    void GIVEN_WHEN_recipe_dir_is_provided_THEN_request_contains_provided_recipe_dir() {
        int exitCode = runCommandLine("deployment", "create", "--recipeDir", RECIPE_FOLDER_PATH_STR);
        verify(nucleusAdapteripc).updateRecipesAndArtifacts(RECIPE_FOLDER_PATH_STR, null);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_recipe_dir_is_provided_more_than_once_THEN_invalid_request_is_returned() {
        int exitCode =
                runCommandLine("deployment", "create", "-r", RECIPE_FOLDER_PATH_STR, "-r", RECIPE_FOLDER_PATH_STR);
        verify(nucleusAdapteripc, never()).createLocalDeployment(any());
        assertThat(exitCode, is(2));
    }

    @Test
    void GIVEN_WHEN_no_option_provided_THEN_request_is_empty() {
        int exitCode = runCommandLine("deployment", "create");
        CreateLocalDeploymentRequest request = new CreateLocalDeploymentRequest();
        verify(nucleusAdapteripc).createLocalDeployment(request);
        assertThat(exitCode, is(0));
    }


    @Test
    void GIVEN_WHEN_recipe_dir_is_provided_with_short_name_THEN_request_contains_provided_recipe_dir() {
        int exitCode = runCommandLine("deployment", "create", "-r", RECIPE_FOLDER_PATH_STR);
        verify(nucleusAdapteripc).updateRecipesAndArtifacts(RECIPE_FOLDER_PATH_STR, null);
        assertThat(exitCode, is(0));
    }
    @Test
    void GIVEN_WHEN_configs_are_provided_THEN_request_contain_all_config()
            throws JsonProcessingException {
        String updateConfigString = "{ \"Component1\": { \"MERGE\": { \"Company\": { \"Office\": { \"temperature\": 22 } }, \"path1\": { \"Object2\": { \"key2\": \"val2\" } } } }, \"Component2\": { \"RESET\": [ \"/secret/first\" ] } }";
        int exitCode = runCommandLine("deployment", "create", "--update-config", updateConfigString);

        Map<String, Map<String, Object>> componentNameToConfig = mapper.readValue(updateConfigString, Map.class);

        CreateLocalDeploymentRequest request = new CreateLocalDeploymentRequest();
        request.setComponentToConfiguration(componentNameToConfig);

        verify(nucleusAdapteripc).createLocalDeployment(request);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_components_to_merge_and_remove_provided_THEN_request_contains_the_info() {
        int exitCode = runCommandLine("deployment", "create", "-m", NEW_COMPONENT_1_WITH_VERSION, "--merge",
                NEW_COMPONENT_2_WITH_VERSION, "--remove", NEW_COMPONENT_1, "--remove",
                NEW_COMPONENT_2);

        CreateLocalDeploymentRequest request = new CreateLocalDeploymentRequest();
        request.setRootComponentVersionsToAdd(ROOT_COMPONENTS);
        request.setRootComponentsToRemove(Arrays.asList(NEW_COMPONENT_1, NEW_COMPONENT_2));

        verify(nucleusAdapteripc).createLocalDeployment(request);
        assertThat(exitCode, is(0));
    }


    @Test
    void GIVEN_WHEN_components_runwith_provided_THEN_request_contains_the_info() {
        int exitCode = runCommandLine("deployment", "create", "--runWith" , "Component1:posixUser=foo:bar",
                "--runWith" , "Component2:posixUser=xxx:yyy");

        Map<String, RunWithInfo> componentToRunWithInfo = new HashMap<>();
        RunWithInfo runWithInfo = new RunWithInfo();
        runWithInfo.setPosixUser("foo:bar");
        componentToRunWithInfo.put("Component1", runWithInfo);

        runWithInfo = new RunWithInfo();
        runWithInfo.setPosixUser("xxx:yyy");
        componentToRunWithInfo.put("Component2", runWithInfo);

        CreateLocalDeploymentRequest request = new CreateLocalDeploymentRequest();
        request.setComponentToRunWithInfo(componentToRunWithInfo);

        verify(nucleusAdapteripc).createLocalDeployment(request);
        assertThat(exitCode, is(0));
    }

    private int runCommandLine(String... args) {
        return new CommandLine(new CLI(), new CommandFactory(DaggerCommandsComponent.builder()
                .adapterModule(new AdapterModule(null) {
                    @Override
                    protected NucleusAdapterIpc providesAdapter() {
                        return nucleusAdapteripc;
                    }
                }).build()
        )).execute(args);
    }
}
