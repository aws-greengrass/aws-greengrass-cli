/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.hamcrest.core.*;
import static org.hamcrest.core.Is.is;
import picocli.CommandLine;

public class CLITest {

    CLI cli;

    @Before
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
