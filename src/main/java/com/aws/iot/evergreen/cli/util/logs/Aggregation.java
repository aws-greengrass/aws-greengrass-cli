/* Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs;

import java.io.BufferedReader;
import java.nio.file.Path;
import java.util.ArrayList;

public interface Aggregation {
    ArrayList<BufferedReader> ReadLog(String[] logFile, String[] logDir);

    ArrayList<Path> ListLog(String[] logDir);
}
