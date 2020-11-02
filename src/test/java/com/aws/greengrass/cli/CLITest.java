/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.aws.greengrass.cli.module.AdapterModule;
import com.aws.greengrass.cli.module.DaggerCommandsComponent;
import com.aws.greengrass.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.greengrass.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.greengrass.ipc.services.cli.models.ComponentDetails;
import com.aws.greengrass.ipc.services.cli.models.LifecycleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@DisplayName("CLI basic test")
@ExtendWith(MockitoExtension.class)
class CLITest {

    private CLI cli;

    @Mock
    private NucleusAdapterIpc nucleusAdapterIpc;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.out;

    @BeforeEach
    void setup() {
        cli = new CLI();

        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void afterEach() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    int runCommandLine(String... args) {
        return new CommandLine(cli, new CommandFactory(DaggerCommandsComponent.builder()
                .adapterModule(new AdapterModule(null) {
                    @Override
                    protected NucleusAdapterIpc providesAdapter() {
                        return nucleusAdapterIpc;
                    }
                }).build()))
                .execute(args);
    }

    @Test
    void helpCommand() {
        int exitCode = runCommandLine("help");
        assertThat(exitCode, is(0));
    }

    @Test
    void componentStatusCommand() throws CliIpcClientException, GenericCliIpcServerException {
        ComponentDetails componentDetails = ComponentDetails.builder().componentName("main")
                .state(LifecycleState.RUNNING).build();
        when(nucleusAdapterIpc.getComponentDetails(any()))
                .thenReturn(componentDetails);
        int exitCode = runCommandLine("component", "details", "-n", "main");
        assertThat(exitCode, is(0));

        assertEquals("Component Name: main\n" + "Version: null\n" + "State: RUNNING\n" + "Configuration: null\n"
                + "Configurations: null\n", outContent.toString());
    }

    @Test
    void stopComponentCommand() throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("component", "stop", "-n", "main");
        verify(nucleusAdapterIpc).stopComponent("main");
        assertThat(exitCode, is(0));
    }

    @Test
    void restartComponentCommand() throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("component", "restart", "-n", "main");
        verify(nucleusAdapterIpc).restartComponent("main");
        assertThat(exitCode, is(0));
    }

    @Test
    void missingCommand() {
        int exitCode = runCommandLine();
        assertThat(exitCode, is(2));
    }
}
