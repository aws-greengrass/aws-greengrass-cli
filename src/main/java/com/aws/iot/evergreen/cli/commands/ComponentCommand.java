/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import com.aws.iot.evergreen.cli.adapter.LocalOverrideRequest;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.Collections;
import javax.inject.Inject;

@CommandLine.Command(name = "component", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class)
public class ComponentCommand extends BaseCommand {

    @Inject
    private KernelAdapter kernelAdapter;


    @CommandLine.Command(name = "deploy")
    public int deploy(@CommandLine.Option(names = {"-n", "--name"}, paramLabel = "name",
            descriptionKey = "name") String[] rootComponentNames,
                      @CommandLine.Option(names = {"-r", "--recipeDir"}, paramLabel = "recipeDir",
                              descriptionKey = "recipeDir") String recipeDir,
                      @CommandLine.Option(names = {"-a", "--artifactDir"}, paramLabel = "artifactDir",
                              descriptionKey = "artifactDir") String artifactDir) {

        // TODO validation

        LocalOverrideRequest localOverrideRequest = new LocalOverrideRequest(
                rootComponentNames.length == 0 ? Collections.emptyList() : Arrays.asList(rootComponentNames), recipeDir,
                artifactDir);

        kernelAdapter.localOverride(localOverrideRequest);

        // Exception handling
        return 0;
    }

}
