package com.aws.greengrass.cli.module;

import com.aws.greengrass.cli.commands.ComponentCommand;
import com.aws.greengrass.cli.commands.DeploymentCommand;
import com.aws.greengrass.cli.commands.Logs;
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
}
