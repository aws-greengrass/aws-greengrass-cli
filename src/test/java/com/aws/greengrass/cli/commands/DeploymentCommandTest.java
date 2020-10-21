package com.aws.greengrass.cli.commands;

import com.aws.greengrass.cli.CLI;
import com.aws.greengrass.cli.CommandFactory;
import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.aws.greengrass.cli.module.AdapterModule;
import com.aws.greengrass.cli.module.DaggerCommandsComponent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;
import software.amazon.awssdk.aws.greengrass.model.CreateLocalDeploymentRequest;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DeploymentCommandTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private NucleusAdapterIpc nucleusAdapteripc;

    @Test
    void GIVEN_WHEN_configs_are_provided_THEN_request_contain_all_config()
            throws JsonProcessingException {
        String updateConfigString = "{ \"Component1\": { \"MERGE\": { \"Company\": { \"Office\": { \"temperature\": 22 } }, \"path1\": { \"Object2\": { \"key2\": \"val2\" } } } }, \"Component2\": { \"RESET\": [ \"/secret/first\" ] } }";
        int exitCode = runCommandLine("deployment", "create", "--update-config", updateConfigString);

        Map<String, Map<String, Object>> componentNameToConfig = mapper.readValue(updateConfigString, Map.class);

        CreateLocalDeploymentRequest request = new CreateLocalDeploymentRequest();
        request.setComponentToConfiguration(componentNameToConfig);

        verify(nucleusAdapteripc).createLocalDeployment(request);
        assertThat(exitCode, is(0));
    }

    private int runCommandLine(String... args) {
        return new CommandLine(new CLI(), new CommandFactory(DaggerCommandsComponent.builder()
                .adapterModule(new AdapterModule(null) {
                    @Override
                    protected NucleusAdapterIpc providesAdapter() {
                        return nucleusAdapteripc;
                    }
                }).build()
        )).execute(args);
    }
}
