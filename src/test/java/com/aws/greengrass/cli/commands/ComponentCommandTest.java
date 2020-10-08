/*
  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
  * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.CLI;
import com.aws.greengrass.cli.CommandFactory;
import com.aws.greengrass.cli.adapter.KernelAdapterIpc;
import com.aws.greengrass.cli.module.AdapterModule;
import com.aws.greengrass.cli.module.DaggerCommandsComponent;
import com.aws.greengrass.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.greengrass.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.greengrass.ipc.services.cli.models.ComponentDetails;
import com.aws.greengrass.ipc.services.cli.models.CreateLocalDeploymentRequest;
import com.aws.greengrass.ipc.services.cli.models.LifecycleState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComponentCommandTest {

    private static final String RECIPE_FOLDER_PATH_STR = "recipeFolderPath";
    private static final String ARTIFACT_FOLDER_PATH_STR = "artifactFolderPath";
    private static final String NEW_COMPONENT_1 = "newComponent1";
    private static final String NEW_COMPONENT_2 = "newComponent2";
    private static final String NEW_COMPONENT_3 = "aws.greengrass.componentname";
    private static final String NEW_COMPONENT_1_WITH_VERSION = "newComponent1=1.0.0";
    private static final String NEW_COMPONENT_2_WITH_VERSION = "newComponent2=2.0.0";


    private static final Map<String, String> ROOT_COMPONENTS =
            ImmutableMap.of(NEW_COMPONENT_1, "1.0.0", NEW_COMPONENT_2, "2.0.0");

    @Mock
    private KernelAdapterIpc kernelAdapteripc;

    @Test
    void GIVEN_WHEN_components_to_merge_and_remove_provided_THEN_request_contains_the_info()
            throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("component", "update", "-m", NEW_COMPONENT_1_WITH_VERSION, "--merge",
                                      NEW_COMPONENT_2_WITH_VERSION, "--remove", NEW_COMPONENT_1, "--remove",
                                      NEW_COMPONENT_2);

        CreateLocalDeploymentRequest createLocalDeploymentRequest = CreateLocalDeploymentRequest.builder()
                .rootComponentVersionsToAdd(ROOT_COMPONENTS)
                .rootComponentsToRemove(Arrays.asList(NEW_COMPONENT_1, NEW_COMPONENT_2))
                .componentToConfiguration(Collections.emptyMap())
                .build();

        verify(kernelAdapteripc).createLocalDeployment(createLocalDeploymentRequest);
        assertThat(exitCode, is(0));
    }


    @Test
    void GIVEN_WHEN_artifact_dir_is_provided_THEN_request_contains_provided_artifact_dir()
            throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("component", "update", "--artifactDir", ARTIFACT_FOLDER_PATH_STR);
        verify(kernelAdapteripc).updateRecipesAndArtifacts(null, ARTIFACT_FOLDER_PATH_STR);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_artifact_dir_is_provided_with_short_name_THEN_request_contains_provided_artifact_dir()
            throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("component", "update", "-a", ARTIFACT_FOLDER_PATH_STR);

        verify(kernelAdapteripc).updateRecipesAndArtifacts(null, ARTIFACT_FOLDER_PATH_STR);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_artifact_dir_is_provided_more_than_once_THEN_invalid_request_is_returned()
            throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode =
                runCommandLine("component", "update", "-a", ARTIFACT_FOLDER_PATH_STR, "-a", ARTIFACT_FOLDER_PATH_STR);

        verify(kernelAdapteripc, never()).updateRecipesAndArtifacts(any(), any());
        assertThat(exitCode, is(2));
    }

    @Test
    void GIVEN_WHEN_recipe_dir_is_provided_THEN_request_contains_provided_recipe_dir()
            throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("component", "update", "--recipeDir", RECIPE_FOLDER_PATH_STR);
        verify(kernelAdapteripc).updateRecipesAndArtifacts(RECIPE_FOLDER_PATH_STR, null);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_recipe_dir_is_provided_with_short_name_THEN_request_contains_provided_recipe_dir()
            throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("component", "update", "-r", RECIPE_FOLDER_PATH_STR);
        verify(kernelAdapteripc).updateRecipesAndArtifacts(RECIPE_FOLDER_PATH_STR, null);
        assertThat(exitCode, is(0));
    }


    @Test
    void GIVEN_WHEN_recipe_dir_is_provided_more_than_once_THEN_invalid_request_is_returned()
            throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode =
                runCommandLine("component", "update", "-r", RECIPE_FOLDER_PATH_STR, "-r", RECIPE_FOLDER_PATH_STR);
        verify(kernelAdapteripc, never()).createLocalDeployment(any());
        assertThat(exitCode, is(2));
    }

    @Test
    void GIVEN_a_running_component_WHEN_list_component_details_THEN_component_info_is_printed()
            throws CliIpcClientException, GenericCliIpcServerException, JsonProcessingException {

        // GIVEN
        ComponentDetails componentDetails = getTestComponentDetails();
        when(kernelAdapteripc.listComponents()).thenReturn(Collections.singletonList(componentDetails));

        // WHEN
        // We need to do some print stream magic here to verify the content of System.out.println
        // Create a stream to hold the output
        ByteArrayOutputStream outputCaptor = new ByteArrayOutputStream();
        // Save the old System.out!
        PrintStream old = System.out;
        // Switch special stream
        System.setOut(new PrintStream(outputCaptor));

        // Call. System.out.println now goes to outputCaptor
        int exitCode = runCommandLine("component", "list");

        // Put things back
        System.out.flush();
        System.setOut(old);

        // THEN
        assertThat(exitCode, is(0));

        String output = outputCaptor.toString();
        verifyComponentDetails(componentDetails, output);

    }

    @Test
    void GIVEN_a_running_component_WHEN_check_component_details_THEN_component_info_is_printed()
            throws CliIpcClientException, GenericCliIpcServerException, JsonProcessingException {

        // GIVEN
        ComponentDetails componentDetails = getTestComponentDetails();
        when(kernelAdapteripc.getComponentDetails(any())).thenReturn(componentDetails);

        // WHEN
        // We need to do some print stream magic here to verify the content of System.out.println
        // Create a stream to hold the output
        ByteArrayOutputStream outputCaptor = new ByteArrayOutputStream();
        // Save the old System.out!
        PrintStream old = System.out;
        // Switch special stream
        System.setOut(new PrintStream(outputCaptor));

        // Call. System.out.println now goes to outputCaptor
        int exitCode = runCommandLine("component", "details", "-n", NEW_COMPONENT_3);

        // Put things back
        System.out.flush();
        System.setOut(old);

        // THEN
        assertThat(exitCode, is(0));

        String output = outputCaptor.toString();
        verifyComponentDetails(componentDetails, output);
    }

    private void verifyComponentDetails(ComponentDetails componentDetails, String output)
            throws JsonProcessingException {
        assertThat(output, StringContains.containsString("Component Name: " + componentDetails.getComponentName()));
        assertThat(output, StringContains.containsString("Version: " + componentDetails.getVersion()));
        assertThat(output, StringContains.containsString("State: " + componentDetails.getState()));
        assertThat(output, StringContains.containsString(
                "Configurations: " + new ObjectMapper().writeValueAsString(componentDetails.getNestedConfiguration())));
    }

    private static ComponentDetails getTestComponentDetails() {
        Map<String, Object> config = ImmutableMap.of("key", "val1", "nested", ImmutableMap.of("leafkey", "value1"));

        return ComponentDetails.builder()
                .componentName(NEW_COMPONENT_3)
                .version("1.0.1")
                .state(LifecycleState.FINISHED)
                .nestedConfiguration(config)
                .build();
    }
    @Test
    void GIVEN_WHEN_params_are_provided_THEN_request_contain_all_params()
            throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("component", "update", "--param", "newComponent1:K1=V1", "--param",
                                      "newComponent1:nested.K2=V2", "--param", "newComponent2:K3=V3", "--param",
                                      "aws.greengrass.componentname:nested.K2=V2");

        Map<String, Map<String, Object>> componentNameToConfig = new HashMap<>();
        componentNameToConfig.put(NEW_COMPONENT_1, new HashMap<>());
        componentNameToConfig.get(NEW_COMPONENT_1).put("K1", "V1");
        componentNameToConfig.get(NEW_COMPONENT_1).put("nested", new HashMap<>());
        ((HashMap) componentNameToConfig.get(NEW_COMPONENT_1).get("nested")).put("K2", "V2");

        componentNameToConfig.put(NEW_COMPONENT_2, new HashMap<>());
        componentNameToConfig.get(NEW_COMPONENT_2).put("K3", "V3");
        componentNameToConfig.put(NEW_COMPONENT_3, new HashMap<>());
        componentNameToConfig.get(NEW_COMPONENT_3).put("nested", new HashMap<>());
        ((HashMap) componentNameToConfig.get(NEW_COMPONENT_3).get("nested")).put("K2", "V2");

        CreateLocalDeploymentRequest createLocalDeploymentRequest =
                CreateLocalDeploymentRequest.builder().componentToConfiguration(componentNameToConfig).build();

        verify(kernelAdapteripc).createLocalDeployment(createLocalDeploymentRequest);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_no_option_provided_THEN_request_is_empty()
            throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("component", "update");
        CreateLocalDeploymentRequest createLocalDeploymentRequest =
                CreateLocalDeploymentRequest.builder().componentToConfiguration(Collections.emptyMap()).build();
        verify(kernelAdapteripc).createLocalDeployment(createLocalDeploymentRequest);
        assertThat(exitCode, is(0));
    }

    @Test
    void GIVEN_WHEN_invalid_params_are_provided_THEN_exit_1()
            throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("component", "update", "--param", "newComponent1=V1");
        verify(kernelAdapteripc, never()).createLocalDeployment(any());
        assertThat(exitCode, is(1));
    }

    @Test
    void WHEN_list_command_request_THEN_print_info_and_exit_0()
            throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("component", "list");
        verify(kernelAdapteripc, only()).listComponents();
        assertThat(exitCode, is(0));
    }

    private int runCommandLine(String... args) {
        return new CommandLine(new CLI(), new CommandFactory(
                DaggerCommandsComponent.builder().adapterModule(new AdapterModule(null) {
                    @Override
                    protected KernelAdapterIpc providesKernelAdapter() {
                        return kernelAdapteripc;
                    }
                }).build())).execute(args);
    }
}
