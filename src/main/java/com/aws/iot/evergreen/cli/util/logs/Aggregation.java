/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs;

import java.io.BufferedReader;
import java.io.File;
import java.util.List;
import java.util.Set;

public interface Aggregation {
    List<BufferedReader> readLog(String[] logFile, String[] logDir);

    Set<File> listLog(String[] logDir);
}