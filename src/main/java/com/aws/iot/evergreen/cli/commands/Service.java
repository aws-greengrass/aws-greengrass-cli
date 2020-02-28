package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import javax.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(name = "service", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages", subcommands = CommandLine.HelpCommand.class)
public class Service extends BaseCommand {

    @Inject
    private KernelAdapter kernelAdapter;

    @CommandLine.Command(name = "status")
    public int status(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names) {
        String[] serviceNames = names.split(" *[&,]+ *");
        Map<String, Map<String, String>> serviceStatusMap = kernelAdapter.getServicesStatus(new HashSet<>(Arrays.asList(serviceNames)));
        printServicesStatus(serviceStatusMap);
        return 0;
    }

    @CommandLine.Command(name = "restart")
    public int reload(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names) {
        String[] serviceNames = names.split(" *[&,]+ *");
        Map<String, Map<String, String>> serviceStatusMap = kernelAdapter.restartServices(new HashSet<>(Arrays.asList(serviceNames)));
        printServicesStatus(serviceStatusMap);
        return 0;
    }

    @CommandLine.Command(name = "stop")
    public int close(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names) {
        String[] serviceNames = names.split(" *[&,]+ *");
        Map<String, Map<String, String>> serviceStatusMap = kernelAdapter.stopServices(new HashSet<>(Arrays.asList(serviceNames)));
        printServicesStatus(serviceStatusMap);
        return 0;
    }

    private void printServicesStatus(Map<String, Map<String, String>> serviceStatusMap) {
        serviceStatusMap.forEach((name, nestedMap) -> {
            System.out.printf("%s:%n", name);
            nestedMap.forEach((k, v) -> {
                System.out.printf("    %s: %s%n", k, v);
            });
        });
    }
}
