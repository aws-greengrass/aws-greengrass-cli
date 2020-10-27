/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

/*
  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
  * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.CLI;
import com.aws.greengrass.cli.CommandFactory;
import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.aws.greengrass.cli.module.AdapterModule;
import com.aws.greengrass.cli.module.DaggerCommandsComponent;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;
import software.amazon.awssdk.aws.greengrass.model.ComponentDetails;
import software.amazon.awssdk.aws.greengrass.model.LifecycleState;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComponentCommandTest {

    private static final String NEW_COMPONENT_3 = "aws.greengrass.componentname";

    @Mock
    private NucleusAdapterIpc nucleusAdapteripc;

    @Test
    void GIVEN_a_running_component_WHEN_list_component_details_THEN_component_info_is_printed() {

        // GIVEN
        ComponentDetails componentDetails = getTestComponentDetails();
        when(nucleusAdapteripc.listComponents()).thenReturn(Collections.singletonList(componentDetails));

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
    void GIVEN_a_running_component_WHEN_check_component_details_THEN_component_info_is_printed() {

        // GIVEN
        ComponentDetails componentDetails = getTestComponentDetails();
        when(nucleusAdapteripc.getComponentDetails(any())).thenReturn(componentDetails);

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

    private void verifyComponentDetails(ComponentDetails componentDetails, String output) {
        assertThat(output, StringContains.containsString("Component Name: " + componentDetails.getComponentName()));
        assertThat(output, StringContains.containsString("Version: " + componentDetails.getVersion()));
        assertThat(output, StringContains.containsString("State: " + componentDetails.getState()));
    }

    private static ComponentDetails getTestComponentDetails() {
        ComponentDetails componentDetails = new ComponentDetails();
        componentDetails.setComponentName(NEW_COMPONENT_3);
        componentDetails.setVersion("1.0.1");
        componentDetails.setState(LifecycleState.FINISHED);
        return componentDetails;
    }

    @Test
    void WHEN_list_command_request_THEN_print_info_and_exit_0() {
        int exitCode = runCommandLine("component", "list");
        verify(nucleusAdapteripc, only()).listComponents();
        assertThat(exitCode, is(0));
    }

    private int runCommandLine(String... args) {
        return new CommandLine(new CLI(), new CommandFactory(
                DaggerCommandsComponent.builder().adapterModule(new AdapterModule(null) {
                    @Override
                    protected NucleusAdapterIpc providesAdapter() {
                        return nucleusAdapteripc;
                    }
                }).build())).execute(args);
    }
}
