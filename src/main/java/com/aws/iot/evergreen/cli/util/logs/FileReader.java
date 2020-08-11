package com.aws.iot.evergreen.cli.util.logs;

import com.aws.iot.evergreen.cli.util.logs.impl.AggregationImplConfig;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Pattern;

/*
 * Runnable class responsible of reading a single log file
 */
public class FileReader implements Runnable {
    private final File fileToRead;
    private BlockingQueue<LogEntry> queue = AggregationImplConfig.getInstance().getQueue();
    private BlockingQueue<LogEntry> logEntryArray = AggregationImplConfig.getInstance().getLogEntryArray();
    private Boolean follow = AggregationImplConfig.getInstance().getFollow();
    private Filter filterInterface = AggregationImplConfig.getInstance().getFilterInterface();

    // Rotated file name contains timestamp "_{yyyy-MM-dd_HH}_"
    private static final Pattern fileRotationPattern = Pattern.compile(("(?:_([0-9]+)-([0-9]+)-([0-9]+)_([0-9]+)_?)"));

    public FileReader(File fileToRead) {
        this.fileToRead = fileToRead;
    }

    @Override
    public void run() {
        boolean isFollowing = isFollowing();
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(fileToRead))) {
            String line;
            // if the current time is after time window given, we break the loop and stop the thread.
            while ((line = reader.readLine()) != null || (isFollowing && filterInterface.checkEndTime())) {
                if (line == null) {
                    continue;
                }
                try {
                    LogEntry entry = logEntryArray.remainingCapacity() != 0 ? new LogEntry() : logEntryArray.take();
                    entry.setLogEntry(line);
                    // We only put filtered result into blocking queue to save memory.
                    if (filterInterface.filter(entry)) {
                        queue.put(entry);
                    }
                    logEntryArray.put(entry);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (JsonProcessingException e) {
                    LogsUtil.getErrorStream().println("Failed to serialize: " + line);
                    LogsUtil.getErrorStream().println(e.getMessage());
                }
            }
        } catch (FileNotFoundException e) {
            LogsUtil.getErrorStream().println("Can not find file: " + fileToRead);
        } catch (IOException e) {
            LogsUtil.getErrorStream().println(fileToRead + "readLine() failed.");
            LogsUtil.getErrorStream().println(e.getMessage());
        }
    }

    private boolean isFollowing() {
        return follow != null && follow && !fileRotationPattern.matcher(fileToRead.getName()).find();
    }
}