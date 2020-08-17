package com.aws.iot.evergreen.cli.util.logs;

import com.aws.iot.evergreen.cli.util.logs.impl.AggregationImplConfig;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import static java.lang.Thread.sleep;

/*
 * Runnable class responsible of reading a single log file
 */
public class FileReader implements Runnable {
    private final File fileToRead;
    private AggregationImplConfig config;

    // Rotated file name contains timestamp "_{yyyy-MM-dd_HH}_"
    private static final Pattern fileRotationPattern = Pattern.compile(("(?:_([0-9]+)-([0-9]+)-([0-9]+)_([0-9]+)_?)"));

    public FileReader(File fileToRead, AggregationImplConfig config) {
        this.fileToRead = fileToRead;
        this.config = config;
    }

    @Override
    public void run() {
        boolean isFollowing = isFollowing();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileToRead),
                LogsUtil.DEFAULT_CHARSETS))) {
            String line;
            // if the current time is after time window given, we break the loop and stop the thread.
            while ((line = reader.readLine()) != null || (isFollowing && config.getFilterInterface().reachedEndTime())) {
                if (line == null) {
                    //TODO: remove busy polling by adding a WatcherService to track and notify file changes.
                    try {
                        sleep(100);
                        continue;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
                try {
                    LogEntry entry = config.getLogEntryArray().remainingCapacity() != 0 ? new LogEntry()
                            : config.getLogEntryArray().take();
                    entry.setLogEntry(line);
                    config.getLogEntryArray().put(entry);
                    // We only put filtered result into blocking queue to save memory.
                    if (config.getFilterInterface().filter(entry)) {
                        config.getQueue().put(entry);
                        continue;
                    }
                    entry.resetLogEntry();
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
        return config.isFollow() && !fileRotationPattern.matcher(fileToRead.getName()).find();
    }
}