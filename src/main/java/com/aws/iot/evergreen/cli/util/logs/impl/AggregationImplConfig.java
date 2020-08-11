package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.LogEntry;
import lombok.Getter;

import java.util.concurrent.ArrayBlockingQueue;
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
    private static final AggregationImplConfig INSTANCE = new AggregationImplConfig();

    private Boolean follow;
    private Filter filterInterface;
    private int maxNumEntry;

    private BlockingQueue<LogEntry> queue;
    private BlockingQueue<LogEntry> logEntryArray;

    public static AggregationImplConfig getInstance() {
        return INSTANCE;
    }

    void init(Boolean follow, Filter filter, int maxNumEntry) {
        this.follow = follow;
        this.filterInterface = filter;
        this.maxNumEntry = maxNumEntry;
    }

    public void setUpFileReader() {
        this.queue = new PriorityBlockingQueue<>();
        this.logEntryArray = new ArrayBlockingQueue<>(maxNumEntry, true);
    }
}
