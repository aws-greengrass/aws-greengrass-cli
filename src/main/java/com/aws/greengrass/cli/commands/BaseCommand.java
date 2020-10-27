/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.CLI;
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

    @Override
    public void run() {
        // do nothing
    }

}
