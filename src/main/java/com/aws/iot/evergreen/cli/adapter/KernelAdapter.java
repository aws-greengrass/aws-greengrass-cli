package com.aws.iot.evergreen.cli.adapter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface KernelAdapter {

    Map<String, String> getConfigs(Set<String> configPaths);

    void setConfigs(Map<String, String> configs);

    String healthPing();

    Map<String, Map<String, String>> getServicesStatus(Set<String> serviceNames);

    Map<String, Map<String, String>> restartServices(Set<String> serviceNames);

    Map<String, Map<String, String>> stopServices(Set<String> serviceNames);

    void localOverride(LocalOverrideRequest localOverrideRequest);

    String listComponents();
}
