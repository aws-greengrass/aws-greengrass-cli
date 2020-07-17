/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.cli.util.logs;

import java.io.BufferedReader;
import java.io.File;
import java.util.List;

public interface Aggregation {
    List<BufferedReader> readLog(String[] logFile, String[] logDir);

    List<File> listLog(String[] logDir);
}