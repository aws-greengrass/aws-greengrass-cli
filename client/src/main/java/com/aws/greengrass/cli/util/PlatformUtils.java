/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class PlatformUtils {
    private PlatformUtils() {
    }

    public static int getEffectiveUID() throws IOException {
        return Integer.parseInt(runCommand("id -u"));
    }

    public static int getEffectiveGID() throws IOException {
        return Integer.parseInt(runCommand("id -g"));
    }

    private static String runCommand(String command) throws IOException {
        Process process = Runtime.getRuntime().exec(command);

        StringBuilder sb = new StringBuilder();
        InputStream is = process.getInputStream();
        InputStream es = process.getErrorStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString().trim();
        }
    }
}
