package com.aws.iot.evergreen.cli.adapter;

import java.util.Map;
import java.util.Set;

public interface KernelAdapterIpc {

    Map<String, String> getComponentDetails(String componentName);

    void restartComponent(String componentName);

    void stopComponent(String componentName);

    void updateRecipesAndArtifacts(String recipesDirectoryPath, String artifactsDirectoryPath);

    void createLocalDeployment(LocalOverrideRequest localOverrideRequest);

    Set<Map<String, String>> listComponents();
}
