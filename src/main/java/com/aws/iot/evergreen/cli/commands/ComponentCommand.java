/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import com.aws.iot.evergreen.cli.adapter.LocalOverrideRequest;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

@CommandLine.Command(name = "component", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class)
public class ComponentCommand extends BaseCommand {

    @Inject
    private KernelAdapter kernelAdapter;

    //TODO: handle package parameters
    //TODO: root package name should include version, either accept name as blah=1.0.0 or provide option to enter
    // version separately
    //TODO: handle multiple recipe and artifacts to be copied to package store path
    @CommandLine.Command(name = "deploy")
    public int deploy(@CommandLine.Option(names = {"-n", "--name"}, paramLabel = "name",
            descriptionKey = "name") String[] rootComponentNames,
                      @CommandLine.Option(names = {"-r", "--recipeFile"}, paramLabel = "recipeFile",
                              descriptionKey = "recipeFile") String recipeFile,
                      @CommandLine.Option(names = {"-a", "--artifactDir"}, paramLabel = "artifactDir",
                              descriptionKey = "artifactDir") String artifactDir) {

        // TODO Validate folder exists and folder structure

        List<String> rootComponentList =
                rootComponentNames == null || rootComponentNames.length == 0 ? Collections.emptyList()
                        : Arrays.asList(rootComponentNames);


        LocalOverrideRequest localOverrideRequest =
                LocalOverrideRequest.builder().rootComponentNames(rootComponentList).recipeFile(recipeFile)
                        .artifactDir(artifactDir).build();

        kernelAdapter.localOverride(localOverrideRequest);
        return 0;
    }

}
