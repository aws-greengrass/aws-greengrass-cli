package com.aws.iot.evergreen.cli.commands;

import com.aws.iot.evergreen.cli.adapter.KernelAdapterIpc;
import com.aws.iot.evergreen.ipc.services.cli.exceptions.CliIpcClientException;
import com.aws.iot.evergreen.ipc.services.cli.exceptions.GenericCliIpcServerException;
import com.aws.iot.evergreen.ipc.services.cli.models.CreateLocalDeploymentRequest;
import com.aws.iot.evergreen.ipc.services.cli.models.LocalDeployment;
import picocli.CommandLine;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static com.aws.iot.evergreen.cli.commands.ComponentCommand.convertParameters;

@CommandLine.Command(name = "deployment", resourceBundle = "com.aws.iot.evergreen.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class)
public class DeploymentCommand extends BaseCommand {

    @Inject
    private KernelAdapterIpc kernelAdapterIpc;

    @CommandLine.Command(name = "create",
            description = "Create local deployment with provided recipes, artifacts, and runtime parameters")
    public int deploy
            (@CommandLine.Option(names = {"-m", "--merge"}, paramLabel = "Component") Map<String, String> componentsToMerge,
             @CommandLine.Option(names = {"--remove"}, paramLabel = "Component Name") List<String> componentsToRemove,
             @CommandLine.Option(names = {"-r", "--recipeDir"}, paramLabel = "Folder") String recipeDir,
             @CommandLine.Option(names = {"-g", "--groupId"}, paramLabel = "group Id") String groupId,
             @CommandLine.Option(names = {"-a", "--artifactDir"}, paramLabel = "Folder") String artifactDir,
             @CommandLine.Option(names = {"-p", "--param"}, paramLabel = "Key Value Pair") Map<String, String> parameters)
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

    @CommandLine.Command(name = "status",
            description = "Retrieve the status of a deployment")
    public int status(@CommandLine.Option(names = {"-i", "--deploymentId"}) String deploymentId)
            throws CliIpcClientException, GenericCliIpcServerException {

        LocalDeployment status = kernelAdapterIpc.getLocalDeploymentStatus(deploymentId);
        System.out.printf("%s: %s", status.getDeploymentId(), status.getStatus());
        return 0;
    }


    @CommandLine.Command(name = "list", description = "Retrieve the status of local deployments")
    public int list() throws CliIpcClientException, GenericCliIpcServerException {
        List<LocalDeployment> localDeployments = kernelAdapterIpc.listLocalDeployments();
        localDeployments.forEach((status) -> System.out.printf("%s: %s", status.getDeploymentId(), status.getStatus()));
        return 0;
    }
}
