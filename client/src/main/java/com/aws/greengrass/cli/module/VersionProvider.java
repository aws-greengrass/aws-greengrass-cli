package com.aws.greengrass.cli.module;

import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class VersionProvider implements CommandLine.IVersionProvider {
    protected static final String BUILD_METADATA_DIRECTORY = "conf";
    protected static final String BUILD_METADATA_FILENAME = "build.properties";
    protected static final String VERSION_BUILD_METADATA_KEY = "cli.version";
    protected static final String FALLBACK_VERSION = "2.0.0";
    private static final Properties BUILD_PROPERTIES = new Properties();

    @Override
    public String[] getVersion() {
        String cliHome = System.getenv("CLI_HOME");

        String version = null;
        try {
            try (InputStream is = Files.newInputStream(
                    Paths.get(cliHome).resolve(BUILD_METADATA_DIRECTORY).resolve(BUILD_METADATA_FILENAME))) {
                BUILD_PROPERTIES.load(is);
            }
            version = BUILD_PROPERTIES.getProperty(VERSION_BUILD_METADATA_KEY);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        if (version == null) {
            System.err.println("Unable to determine exact CLI version from build metadata file. "
                    + "Build file not found, or version not found in file");
            version = FALLBACK_VERSION;
        }

        return new String[]{"Greengrass CLI Version: " + version};
    }
}
