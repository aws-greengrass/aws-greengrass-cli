package com.aws.greengrass.cli.util.logs.impl;

import com.aws.greengrass.cli.util.logs.Filter;
import com.aws.greengrass.cli.util.logs.LogQueue;
import lombok.Getter;

import java.util.concurrent.PriorityBlockingQueue;

/*
 * Singleton config enum that defines parameters for all FileReaders
 *
 * The enum is placed in logs.impl to make sure that init() is package-private,
 * so that the enum field is not modifiable by the outer world.
 */
@Getter
public class AggregationImplConfig {
    private boolean follow;
    private Filter filterInterface;
    private int before;
    private int after;
    private int max;

    private LogQueue queue;

    AggregationImplConfig(boolean follow, Filter filter, int before, int after, int max) {
        this.follow = follow;
        this.filterInterface = filter;
        this.before = before;
        this.after = after;
        this.max = max;
    }

    public void initialize() {
        this.queue = new LogQueue(new PriorityBlockingQueue<>(), max);
    }
}
