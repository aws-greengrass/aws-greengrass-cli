/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli;

import com.aws.greengrass.cli.module.AdapterModule;
import com.aws.greengrass.cli.module.CommandsComponent;
import com.aws.greengrass.cli.module.DaggerCommandsComponent;
import picocli.CommandLine;

import java.lang.reflect.Method;
import java.util.Arrays;

public class CommandFactory implements CommandLine.IFactory {
    private final CommandsComponent commands;

    public CommandFactory(final CommandsComponent commands) {
        this.commands = commands;
    }

    public CommandFactory() {
        this(DaggerCommandsComponent.builder()
                .adapterModule(new AdapterModule(null))
                .build());
    }

    @Override
    public <K> K create(final Class<K> aClass) throws Exception {
        try {
            Method componentMethod = Arrays.stream(CommandsComponent.class.getMethods())
                    .filter(method -> method.getReturnType().equals(aClass))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unable to create " + aClass));
            return aClass.cast(componentMethod.invoke(commands));
        } catch (IllegalArgumentException e) {
            return CommandLine.defaultFactory().create(aClass);
        }
    }
}
