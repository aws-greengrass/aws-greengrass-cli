package com.aws.iot.evergreen.cli.util.logs;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Bounded blocking queue of LogEntry.
 */
public class LogQueue {
    private BlockingQueue<LogEntry> queue;
    private int capacity;
    private Semaphore sem;

    public LogQueue(BlockingQueue<LogEntry> queue, int capacity) {
        this.queue = queue;
        this.capacity = capacity;
        this.sem = new Semaphore(capacity, true);
    }

    public LogEntry poll(long l, TimeUnit timeUnit) throws InterruptedException {
        LogEntry entry = queue.poll(l, timeUnit);
        if (entry != null) {
            sem.release();
        }
        return entry;
    }

    public LogEntry take() throws InterruptedException {
        sem.release();
        return queue.take();
    }

    public void put(LogEntry e) throws InterruptedException {
        if (e == null) {
            return;
        }
        // if the current queue is full, wait until it's emptied.
        if (!sem.tryAcquire()) {
            sem.acquire(capacity);
            // capacity - 1 to subtract the current log entry putting to queue
            sem = new Semaphore(capacity - 1, true);
        }
        queue.put(e);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }
}
