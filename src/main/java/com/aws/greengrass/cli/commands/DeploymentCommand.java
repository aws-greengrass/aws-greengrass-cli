package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentRequest;
import software.amazon.awssdk.aws.greengrass.model.LocalDeployment;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "deployment", resourceBundle = "com.aws.greengrass.cli.CLI_messages",
        subcommands = CommandLine.HelpCommand.class)
public class DeploymentCommand extends BaseCommand {

    private final ObjectMapper mapper = new ObjectMapper();
    private final NucleusAdapterIpc nucleusAdapterIpc;

    @Inject
    public DeploymentCommand(
            NucleusAdapterIpc nucleusAdapterIpc
    ) {
        this.nucleusAdapterIpc = nucleusAdapterIpc;
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
     @CommandLine.Option(names = {"-c", "--update-config"}, paramLabel = "Update configuration") String configUpdate)
            throws JsonProcessingException {
        // TODO Validate folder exists and folder structure
        Map<String, Map<String, Object>> configurationUpdate = null;
        if (configUpdate != null && !configUpdate.isEmpty()) {
            configurationUpdate = mapper.readValue(configUpdate, Map.class);
        }
        if (recipeDir != null || artifactDir != null) {
            nucleusAdapterIpc.updateRecipesAndArtifacts(recipeDir, artifactDir);
        }

        CreateLocalDeploymentRequest createLocalDeploymentRequest = new CreateLocalDeploymentRequest();
        createLocalDeploymentRequest.setGroupName(groupId);
        createLocalDeploymentRequest.setComponentToConfiguration(configurationUpdate);
        createLocalDeploymentRequest.setRootComponentVersionsToAdd(componentsToMerge);
        createLocalDeploymentRequest.setRootComponentsToRemove(componentsToRemove);

        String deploymentId = nucleusAdapterIpc.createLocalDeployment(createLocalDeploymentRequest);
        System.out.println("Local deployment has been submitted! Deployment Id: " + deploymentId);
        return 0;
    }

    //TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "status",
            description = "Retrieve the status of a deployment")
    public int status(@CommandLine.Option(names = {"-i", "--deploymentId"}, paramLabel = "Deployment Id", required = true) String deploymentId) {
        LocalDeployment status = nucleusAdapterIpc.getLocalDeploymentStatus(deploymentId);
        System.out.printf("%s: %s", status.getDeploymentId(), status.getStatus());
        return 0;
    }

    //TODO: input validation and better error handling https://sim.amazon.com/issues/P39478724
    @CommandLine.Command(name = "list", description = "Retrieve the status of local deployments")
    public int list() {
        List<LocalDeployment> localDeployments = nucleusAdapterIpc.listLocalDeployments();
        localDeployments.forEach((status) -> System.out.println(status.getDeploymentId() + ": " + status.getStatus()));
        return 0;
    }
}
