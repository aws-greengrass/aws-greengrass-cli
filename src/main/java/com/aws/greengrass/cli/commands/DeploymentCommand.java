package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.KernelAdapterIpc;
import com.aws.greengrass.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.greengrass.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.greengrass.ipc.services.cli.models.CreateLocalDeploymentRequest;
import com.aws.greengrass.ipc.services.cli.models.LocalDeployment;
import picocli.CommandLine;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static com.aws.greengrass.cli.commands.ComponentCommand.convertParameters;

@CommandLine.Command(name = "deployment", resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class)
public class DeploymentCommand extends BaseCommand {

    private final KernelAdapterIpc kernelAdapterIpc;

    @Inject
    public DeploymentCommand(
            KernelAdapterIpc kernelAdapterIpc
    ) {
        this.kernelAdapterIpc = kernelAdapterIpc;
    }

    //TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "create",
            description = "Create local deployment with provided recipes, artifacts, and runtime parameters")
    public int create
    (@CommandLine.Option(names = {"-m", "--merge"}, paramLabel = "Component and version") Map<String, String> componentsToMerge,
     @CommandLine.Option(names = {"--remove"}, paramLabel = "Component Names") List<String> componentsToRemove,
     @CommandLine.Option(names = {"-g", "--groupId"}, paramLabel = "group Id") String groupId,
     @CommandLine.Option(names = {"-r", "--recipeDir"}, paramLabel = "Recipe Folder Path") String recipeDir,
     @CommandLine.Option(names = {"-a", "--artifactDir"}, paramLabel = "Artifacts Folder Path") String artifactDir,
     @CommandLine.Option(names = {"-p", "--param"}, paramLabel = "Runtime parameters") Map<String, String> parameters)
            throws CliIpcClientException, GenericCliIpcServerException {
        // TODO Validate folder exists and folder structure
        Map<String, Map<String, Object>> componentNameToConfig = convertParameters(parameters);
        if (recipeDir != null || artifactDir != null) {
            kernelAdapterIpc.updateRecipesAndArtifacts(recipeDir, artifactDir);
        }
        CreateLocalDeploymentRequest createLocalDeploymentRequest = CreateLocalDeploymentRequest.builder()
                .groupName(groupId)
                .componentToConfiguration(componentNameToConfig)
                .rootComponentVersionsToAdd(componentsToMerge)
                .rootComponentsToRemove(componentsToRemove)
                .build();
        String deploymentId = kernelAdapterIpc.createLocalDeployment(createLocalDeploymentRequest);
        System.out.println("Local deployment has been submitted! Deployment Id: " + deploymentId);
        return 0;
    }

    //TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "status",
            description = "Retrieve the status of a deployment")
    public int status(@CommandLine.Option(names = {"-i", "--deploymentId"}, paramLabel = "Deployment Id") String deploymentId)
            throws CliIpcClientException, GenericCliIpcServerException {

        LocalDeployment status = kernelAdapterIpc.getLocalDeploymentStatus(deploymentId);
        System.out.printf("%s: %s", status.getDeploymentId(), status.getStatus());
        return 0;
    }

    //TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "list", description = "Retrieve the status of local deployments")
    public int list() throws CliIpcClientException, GenericCliIpcServerException {
        List<LocalDeployment> localDeployments = kernelAdapterIpc.listLocalDeployments();
        localDeployments.forEach((status) -> System.out.printf("%s: %s", status.getDeploymentId(), status.getStatus()));
        return 0;
    }
}
