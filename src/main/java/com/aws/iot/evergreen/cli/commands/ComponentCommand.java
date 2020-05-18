/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import com.aws.iot.evergreen.cli.adapter.LocalOverrideRequest;
import picocli.CommandLine;

import java.util.Arrays;
import javax.inject.Inject;

@CommandLine.Command(name = "component", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class)
public class ComponentCommand extends BaseCommand {

    @Inject
    private KernelAdapter kernelAdapter;


    @CommandLine.Command(name = "deploy")
    public int deploy(@CommandLine.Option(names = {"-n", "--name"}, paramLabel = "name",
            descriptionKey = "name") String[] rootComponentNames,
                      @CommandLine.Option(names = {"-r", "--recipe"}, paramLabel = "recipe",
                              descriptionKey = "recipe") String recipeFolder,
                      @CommandLine.Option(names = {"-a", "--artifact"}, paramLabel = "artifact",
                              descriptionKey = "artifact") String artifactFolder) {

        // TODO validation
        LocalOverrideRequest localOverrideRequest = new LocalOverrideRequest(Arrays.asList(rootComponentNames),
                recipeFolder, artifactFolder);

        kernelAdapter.localOverride(localOverrideRequest);

        // Exception handling
        return 0;
    }

}
