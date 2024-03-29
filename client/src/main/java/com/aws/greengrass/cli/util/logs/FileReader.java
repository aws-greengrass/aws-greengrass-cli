/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cli.util.logs;

import com.aws.greengrass.cli.util.logs.impl.AggregationImplConfig;

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
    private final AggregationImplConfig config;
    private final List<LogEntry> beforeContextList;
    private int afterCount = 0;

    public FileReader(List<LogFile> fileToRead, AggregationImplConfig config) {
        this.filesToRead = fileToRead;
        this.config = config;
        // GG_NEEDS_REVIEW: TODO: investigate which data structure to use for logEntryList
        this.beforeContextList = new ArrayList<>();
    }

    @Override
    public void run() {
        // filesToRead is already ordered from oldest file to most recent file,
        // and we assumed that only the most recent file is updating,
        // hence there won't be any busy polling until the last file.
        for (LogFile logFile : filesToRead) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            File file = logFile.getFile();
            boolean isFollowing = config.isFollow() && logFile.isUpdate();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                    LogsUtil.DEFAULT_CHARSETS))) {
                String line;
                // if the current time is after time window given, we break the loop and stop the thread.
                while (!Thread.currentThread().isInterrupted()
                        && ((line = reader.readLine()) != null || (isFollowing && config.getFilterInterface().reachedEndTime()))) {
                    if (line != null && line.trim().isEmpty()) {
                        continue;
                    }
                    if (line == null) {
                        // GG_NEEDS_REVIEW: TODO: remove busy polling by adding a WatcherService to track and notify file changes.
                        try {
                            sleep(100);
                            continue;
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    try {
                        LogEntry entry = new LogEntry(line);

                        // We only put filtered result into blocking queue to save memory.
                        if (config.getFilterInterface().filter(entry)) {
                            // We use afterCount to record if the next lines are within context
                            afterCount = config.getAfter();
                            // Adding entries before the matched line into the queue
                            for (LogEntry logEntry : beforeContextList) {
                                config.getQueue().put(logEntry);
                            }
                            beforeContextList.clear();
                            config.getQueue().put(entry);
                            continue;
                        }

                        // Adding entries after the matched line into the queue
                        if (afterCount > 0) {
                            afterCount--;
                            config.getQueue().put(entry);
                            continue;
                        }
                        // Add line that are not matched into before context
                        beforeContextList.add(entry);
                        // We remove the entry outside the context to save memory
                        if (beforeContextList.size() > config.getBefore()) {
                            beforeContextList.remove(0);
                        }
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            } catch (FileNotFoundException e) {
                LogsUtil.getErrorStream().println("Cannot find file: " + file);
            } catch (IOException e) {
                LogsUtil.getErrorStream().println(file + "readLine() failed.");
                LogsUtil.getErrorStream().println(e.getMessage());
            }
        }
    }
}
