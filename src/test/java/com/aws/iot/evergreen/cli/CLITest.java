/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli;

import com.aws.iot.evergreen.cli.adapter.KernelAdapterIpc;
import com.aws.iot.evergreen.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.iot.evergreen.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.iot.evergreen.ipc.services.cli.models.ComponentDetails;
import com.aws.iot.evergreen.ipc.services.cli.models.LifecycleState;
import com.google.inject.AbstractModule;
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
public class CLITest {

    private CLI cli;

    @Mock
    private KernelAdapterIpc kernelAdapterIpc;

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
        return new CommandLine(cli, new CLI.GuiceFactory(new AbstractModule() {
            @Override
            protected void configure() {
                bind(KernelAdapterIpc.class).toInstance(kernelAdapterIpc);
            }
        })).execute(args);
    }

    @Test
    public void helpCommand() {
        int exitCode = runCommandLine("help");
        assertThat(exitCode, is(0));
    }

    @Test
    public void serviceStatusCommand() throws CliIpcClientException, GenericCliIpcServerException {
        ComponentDetails componentDetails = ComponentDetails.builder().componentName("main")
                .state(LifecycleState.RUNNING).build();
        when(kernelAdapterIpc.getComponentDetails(any()))
                .thenReturn(componentDetails);
        int exitCode = runCommandLine("service", "status", "-n", "main");
        assertThat(exitCode, is(0));

        assertEquals("main: state: RUNNING\n", outContent.toString());
    }

    @Test
    public void stopServiceCommand() throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("service", "stop", "-n", "main");
        verify(kernelAdapterIpc).stopComponent("main");
        assertThat(exitCode, is(0));
    }

    @Test
    public void restartServiceCommand() throws CliIpcClientException, GenericCliIpcServerException {
        int exitCode = runCommandLine("service", "restart", "-n", "main");
        verify(kernelAdapterIpc).restartComponent("main");
        assertThat(exitCode, is(0));
    }

    @Test
    public void missingCommand() {
        int exitCode = runCommandLine();
        assertThat(exitCode, is(2));
    }
}
