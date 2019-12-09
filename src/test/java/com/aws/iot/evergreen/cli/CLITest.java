/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;


@DisplayName("CLI basic test")
public class CLITest {

    CLI cli;

    @BeforeEach
    public void setup() {
        cli = new CLI();
    }

    int runCommandLine(String... args) {
        return new CommandLine(cli).execute(args);
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
        int exitCode = runCommandLine("config", "get", "-p", "httpd.run,httpd.port,httpd");
        assertThat(exitCode, is(0));
        assertThat(cli.getHost(), is("localhost"));
        assertThat(cli.getPort(), is(8080));
    }

    @Test
    public void setConfigCommand() {
        int exitCode = runCommandLine("config", "set", "-p", "main", "-v", "haha");
        assertThat(exitCode, is(0));
        assertThat(cli.getHost(), is("localhost"));
        assertThat(cli.getPort(), is(8080));
    }

    @Test
    public void healthCommand() {
        int exitCode = runCommandLine("health");
        assertThat(exitCode, is(0));
        assertThat(cli.getHost(), is("localhost"));
        assertThat(cli.getPort(), is(8080));
    }

    @Test
    public void serviceStatusCommand() {
        int exitCode = runCommandLine("service", "status", "-n", "main,httpd");
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
