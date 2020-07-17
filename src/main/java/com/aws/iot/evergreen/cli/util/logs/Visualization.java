// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.aws.iot.evergreen.cli.util.logs;

import com.aws.iot.evergreen.logging.impl.EvergreenStructuredLogMessage;


public interface Visualization {
    String Visualize(EvergreenStructuredLogMessage eg);
}
