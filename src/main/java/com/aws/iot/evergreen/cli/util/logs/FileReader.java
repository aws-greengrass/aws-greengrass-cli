package com.aws.iot.evergreen.cli.util.logs;

import com.aws.iot.evergreen.cli.util.logs.impl.AggregationImplConfig;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Thread.sleep;

/*
 * Runnable class responsible of reading a single log file
 */
public class FileReader implements Runnable {
    private final List<LogFile> filesToRead;
    private AggregationImplConfig config;
    private List<LogEntry> logEntryList;
    private int afterCount = 0;

    public FileReader(List<LogFile> fileToRead, AggregationImplConfig config) {
        this.filesToRead = fileToRead;
        this.config = config;
        this.logEntryList = new ArrayList<>();
    }

    @Override
    public void run() {
        for (LogFile logFile : filesToRead) {
            File file = logFile.getFile();
            boolean isFollowing = config.isFollow() && logFile.isUpdate();
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
                        LogEntry entry = new LogEntry();
                        entry.setLogEntry(line);
                        // We only put filtered result into blocking queue to save memory.
                        if (config.getFilterInterface().filter(entry)) {
                            afterCount = config.getAfter();
                            entry.setFilter(true);
                            // Adding entries before
                            for (int i = logEntryList.size() > config.getBefore() ? logEntryList.size() - config.getBefore() : 0;
                                 i < logEntryList.size(); i++) {
                                if (!logEntryList.get(i).isFilter()) {
                                    config.getQueue().put(logEntryList.get(i));
                                }
                            }
                            config.getQueue().put(entry);
                            continue;
                        }
                        // Adding entries after
                        if (afterCount > 0) {
                            afterCount--;
                            config.getQueue().put(entry);
                            continue;
                        }
                        logEntryList.add(entry);
                        if (logEntryList.size() > config.getBefore()) {
                            logEntryList.remove(0);
                        }
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
}