package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.adapter.KernelAdapter;
import com.aws.iot.evergreen.cli.adapter.impl.KernelAdapterHttpClientImpl;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import picocli.CommandLine;

@CommandLine.Command(name = "service", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages", subcommands = CommandLine.HelpCommand.class)
public class Service extends BaseCommand {

    private KernelAdapter kernelAdapter = new KernelAdapterHttpClientImpl();

    @CommandLine.Command(name = "status")
    public int status(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names) {
        String[] serviceNames = names.split(" *[&,]+ *");
        Map<String, Map<String, String>> serviceStatusMap = kernelAdapter.getServicesStatus(new HashSet<>(Arrays.asList(serviceNames)));
        printServicesStatus(serviceStatusMap);
        return 0;
    }

    @CommandLine.Command(name = "reload")
    public int reload(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names) {
        String[] serviceNames = names.split(" *[&,]+ *");
        Map<String, Map<String, String>> serviceStatusMap = kernelAdapter.reloadServices(new HashSet<>(Arrays.asList(serviceNames)));
        printServicesStatus(serviceStatusMap);
        return 0;
    }

    @CommandLine.Command(name = "close")
    public int close(@CommandLine.Option(names = {"-n", "--names"}, paramLabel = "names", descriptionKey = "names", required = true) String names) {
        String[] serviceNames = names.split(" *[&,]+ *");
        Map<String, Map<String, String>> serviceStatusMap = kernelAdapter.closeServices(new HashSet<>(Arrays.asList(serviceNames)));
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
