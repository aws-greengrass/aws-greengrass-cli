/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.CLI;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Base command that has CLI as parent to get common parameters.
 */
public abstract class BaseCommand implements Runnable {
    @Spec
    protected CommandSpec spec;

    @ParentCommand
    protected CLI parent;

    protected CommandLine commandLine = new CommandLine(this);

    @Override
    public void run() {
        System.out.println(commandLine.getColorScheme()
                .errorText("No subcommand provided, please invoke a subcommand").toString());
        commandLine.usage(System.out);
    }

}
