// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.aws.iot.evergreen.cli.util.logs;

import com.aws.iot.evergreen.cli.util.logs.impl.AggregationImpl;
import com.aws.iot.evergreen.cli.util.logs.impl.FilterImpl;
import com.aws.iot.evergreen.cli.util.logs.impl.VisualizationImpl;
import com.google.inject.AbstractModule;


public class LogsModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(Aggregation.class).to(AggregationImpl.class);
        bind(Visualization.class).to(VisualizationImpl.class);
        bind(Filter.class).to(FilterImpl.class);
    }
}
