package com.aws.greengrass.cli.module;

import com.aws.greengrass.cli.util.logs.Aggregation;
import com.aws.greengrass.cli.util.logs.Filter;
import com.aws.greengrass.cli.util.logs.Visualization;
import com.aws.greengrass.cli.util.logs.impl.AggregationImpl;
import com.aws.greengrass.cli.util.logs.impl.FilterImpl;
import com.aws.greengrass.cli.util.logs.impl.VisualizationImpl;
import dagger.Module;
import dagger.Provides;

@Module
public class LogsModule {

    @Provides
    static Aggregation providesAggregation() {
        return new AggregationImpl();
    }

    @Provides
    static Filter providesFilter() {
        return new FilterImpl();
    }

    @Provides
    static Visualization providesVisualization() {
        return new VisualizationImpl();
    }
}
