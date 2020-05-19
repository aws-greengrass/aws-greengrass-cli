/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli;

import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import com.aws.iot.evergreen.cli.adapter.LocalOverrideRequest;
import com.aws.iot.evergreen.cli.adapter.impl.KernelAdapterHttpClientImpl;
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
import java.util.Arrays;
import java.util.Collections;

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
    private KernelAdapter kernelAdapter;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.out;

    @BeforeEach
    void setup() {
        cli = new CLI();

//        System.setOut(new PrintStream(outContent));
//        System.setErr(new PrintStream(errContent));
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
                bind(KernelAdapter.class).toInstance(kernelAdapter);
            }
        })).execute(args);
    }

    @Test
    public void helpCommand() {
        int exitCode = runCommandLine("help");
        assertThat(exitCode, is(0));
        assertThat(cli.getHost(), is("localhost"));
        assertThat(cli.getPort(), is(8080));
    }

    @Test
    public void getConfigCommand() {
        when(kernelAdapter.getConfigs(any())).thenReturn(Collections.singletonMap("httpd.port", "1441"));
        int exitCode = runCommandLine("config", "get", "-p", "httpd.run,httpd.port,main.run");
        assertThat(exitCode, is(0));
        assertThat(cli.getHost(), is("localhost"));
        assertThat(cli.getPort(), is(8080));

        assertEquals("httpd.port: 1441\n", outContent.toString());
    }

    @Test
    public void setConfigCommand() {
        int exitCode = runCommandLine("config", "set", "-p", "main.run", "-v", "/Users/zhengang/sprinting");
        assertThat(exitCode, is(0));
        assertThat(cli.getHost(), is("localhost"));
        assertThat(cli.getPort(), is(8080));
    }

    @Test
    public void healthCommand() {
        when(kernelAdapter.healthPing()).thenReturn("{\"status\": \"good\"}");
        int exitCode = runCommandLine("health");
        assertThat(exitCode, is(0));
        assertThat(cli.getHost(), is("localhost"));
        assertThat(cli.getPort(), is(8080));

        assertEquals("Kernel health status:\n{\"status\": \"good\"}\n", outContent.toString());
    }

    @Test
    public void serviceStatusCommand() {
        when(kernelAdapter.getServicesStatus(any()))
                .thenReturn(Collections.singletonMap("main", Collections.singletonMap("status", "running")));
        int exitCode = runCommandLine("service", "status", "-n", "main,httpd");
        assertThat(exitCode, is(0));
        assertThat(cli.getHost(), is("localhost"));
        assertThat(cli.getPort(), is(8080));

        assertEquals("main:\n    status: running\n", outContent.toString());
    }

    @Test
    public void stopServiceCommand() {
        when(kernelAdapter.stopServices(any()))
                .thenReturn(Collections.singletonMap("main", Collections.singletonMap("status", "running")));
        int exitCode = runCommandLine("service", "stop", "-n", "main");
        assertThat(exitCode, is(0));
        assertThat(cli.getHost(), is("localhost"));
        assertThat(cli.getPort(), is(8080));

        assertEquals("main:\n    status: running\n", outContent.toString());
    }

    @Test
    public void restartServiceCommand() {
        when(kernelAdapter.restartServices(any()))
                .thenReturn(Collections.singletonMap("main", Collections.singletonMap("status", "running")));
        int exitCode = runCommandLine("service", "restart", "-n", "main");
        assertThat(exitCode, is(0));
        assertThat(cli.getHost(), is("localhost"));
        assertThat(cli.getPort(), is(8080));

        assertEquals("main:\n    status: running\n", outContent.toString());
    }

    @Test
    public void deployServiceCommand() {

       int exitCode = new CommandLine(cli, new CLI.GuiceFactory(new AbstractModule() {
            @Override
            protected void configure() {
                bind(KernelAdapter.class).toInstance(new KernelAdapterHttpClientImpl());
            }
        })).execute("component", "deploy", "-n", "newComponent1", "--name", "newComponent2", "--recipeDir",
               "recipeFolderPath", "--artifactDir", "artifactFolderPath");


//        int exitCode =
//                runCommandLine("component", "deploy", "-n", "newComponent1", "--names", "newComponent2", "--recipe",
//                        "recipeFolderPath", "--artifact", "artifactFolderPath");

        LocalOverrideRequest expectedRequest =
                new LocalOverrideRequest(Arrays.asList("newComponent1", "newComponent2"), "recipeFolderPath",
                        "artifactFolderPath");

        verify(kernelAdapter).localOverride(expectedRequest);

        assertThat(exitCode, is(0));
        assertThat(cli.getHost(), is("localhost"));
        assertThat(cli.getPort(), is(8080));
    }

    @Test
    public void missingCommand() {
        int exitCode = runCommandLine();
        assertThat(exitCode, is(2));
    }

    @Test
    public void hostPort() {
        int exitCode = runCommandLine("--host=foo", "--port=1234");
        assertThat(exitCode, is(2));
        assertThat(cli.getHost(), is("foo"));
        assertThat(cli.getPort(), is(1234));
    }
}
