package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.LogEntry;
import lombok.Getter;

import java.util.concurrent.BlockingQueue;
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
    private int maxNumEntry;
    private int before;

    private BlockingQueue<LogEntry> queue;

    AggregationImplConfig(boolean follow, Filter filter, int maxNumEntry, int before) {
        this.follow = follow;
        this.filterInterface = filter;
        this.maxNumEntry = maxNumEntry;
        this.before = before;
    }

    public void setUpFileReader() {
        this.queue = new PriorityBlockingQueue<>();
    }
}
