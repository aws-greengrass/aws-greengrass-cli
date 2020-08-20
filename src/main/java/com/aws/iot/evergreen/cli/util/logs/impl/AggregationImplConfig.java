package com.aws.iot.evergreen.cli.util.logs.impl;

import com.aws.iot.evergreen.cli.util.logs.Filter;
import com.aws.iot.evergreen.cli.util.logs.LogEntry;
import com.aws.iot.evergreen.cli.util.logs.LogsUtil;
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
    private boolean follow;
    private Filter filterInterface;
    private int maxNumEntry;

    private BlockingQueue<LogEntry> queue;

    AggregationImplConfig(boolean follow, Filter filter, int maxNumEntry) {
        this.follow = follow;
        this.filterInterface = filter;
        this.maxNumEntry = maxNumEntry;
    }

    public void setUpFileReader(int numOfFileReaders) {
        this.queue = new PriorityBlockingQueue<>();
        /* We define the capacity of logEntryPool to be at least 2 * numOfThreads + 1
           because we want to make sure that each thread of FileReaders have one log
           entry, the main thread has one log entry, and there are numOfThreads of
           log entries left */
        LogsUtil.setLogEntryPool(new ArrayBlockingQueue<>(Math.max(maxNumEntry, 2 * numOfFileReaders + 1), true));
        while (LogsUtil.getLogEntryPool().remainingCapacity() > 0) {
            LogsUtil.getLogEntryPool().add(new LogEntry());
        }
    }
}
