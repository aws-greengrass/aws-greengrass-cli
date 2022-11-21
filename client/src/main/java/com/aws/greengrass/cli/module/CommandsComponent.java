/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.module;

import com.aws.greengrass.cli.commands.ComponentCommand;
import com.aws.greengrass.cli.commands.DeploymentCommand;
import com.aws.greengrass.cli.commands.Logs;
import com.aws.greengrass.cli.commands.PasswordCommand;
import com.aws.greengrass.cli.commands.TopicCommand;
import dagger.Component;

import javax.inject.Singleton;

@Component(modules = {
        AdapterModule.class,
        LogsModule.class
})
@Singleton
public interface CommandsComponent {
    Logs logs();

    ComponentCommand component();

    DeploymentCommand deployment();

    PasswordCommand password();

    TopicCommand topic();
}
