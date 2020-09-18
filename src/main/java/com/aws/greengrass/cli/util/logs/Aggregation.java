/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.greengrass.cli.util.logs;

import java.io.File;
import java.util.Set;

public interface Aggregation {
    void configure(boolean follow, Filter filter, int before, int after, int max);

    LogQueue readLog(String[] logFileArray, String[] logDirArray);

    Set<File> listLog(String[] logDir);

    Boolean isAlive();

    void close();
}
