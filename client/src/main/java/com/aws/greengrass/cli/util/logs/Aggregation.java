/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.util.logs;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface Aggregation {
    void configure(boolean follow, Filter filter, int before, int after, int max);

    LogQueue readLog(List<Path> logFileList, List<Path> logDirList);

    Set<File> listLog(List<Path> logDirList);

    Boolean isAlive();

    void close();
}
