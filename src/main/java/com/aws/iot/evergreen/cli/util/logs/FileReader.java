package com.aws.iot.evergreen.cli.util.logs;

import com.aws.iot.evergreen.cli.util.logs.impl.AggregationImplConfig;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Pattern;

import static java.lang.Thread.sleep;

/*
 * Runnable class responsible of reading a single log file
 */
public class FileReader implements Runnable {
    private final List<LogFile> filesToRead;
    private AggregationImplConfig config;

    // Rotated file name contains timestamp "_{yyyy-MM-dd_HH}_"
    private static final Pattern fileRotationPattern = Pattern.compile(("(?:_([0-9]+)-([0-9]+)-([0-9]+)_([0-9]+)_?)"));

    public FileReader(List<LogFile> fileToRead, AggregationImplConfig config) {
        this.filesToRead = fileToRead;
        this.config = config;
    }

    @Override
    public void run() {
        for (LogFile logFile : filesToRead) {
            File file = logFile.getFile();
            boolean isFollowing = isFollowing(file);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),
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
                        LogEntry entry = LogsUtil.getLogEntryPool().take();
                        entry.setLogEntry(line);
                        // We only put filtered result into blocking queue to save memory.
                        if (config.getFilterInterface().filter(entry)) {
                            config.getQueue().put(entry);
                            continue;
                        }
                        LogsUtil.getLogEntryPool().put(entry);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } catch (JsonProcessingException e) {
                        LogsUtil.getErrorStream().println("Failed to serialize: " + line);
                        LogsUtil.getErrorStream().println(e.getMessage());
                    }
                }
            } catch (FileNotFoundException e) {
                LogsUtil.getErrorStream().println("Can not find file: " + file);
            } catch (IOException e) {
                LogsUtil.getErrorStream().println(file + "readLine() failed.");
                LogsUtil.getErrorStream().println(e.getMessage());
            }
        }
    }

    private boolean isFollowing(File file) {
        return config.isFollow() && !fileRotationPattern.matcher(file.getName()).find();
    }
}