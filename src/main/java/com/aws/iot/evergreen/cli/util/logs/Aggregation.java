// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.aws.iot.evergreen.cli.util.logs;

import java.io.BufferedReader;
import java.nio.file.Path;
import java.util.List;

public interface Aggregation {
    List<BufferedReader> ReadLog(String[] logFile, String[] logDir);

    List<Path> ListLog(String[] logDir);
}