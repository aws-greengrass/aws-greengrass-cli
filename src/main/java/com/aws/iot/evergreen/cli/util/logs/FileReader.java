package com.aws.iot.evergreen.cli.util.logs;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/*
 * Runnable class responsible of reading a single log file
 */
public class FileReader implements Runnable {
    private final File fileToRead;
    private static BlockingQueue<LogEntry> queue;
    private static BlockingQueue<LogEntry> logEntryArray;
    private static Boolean follow;
    private static Filter filterInterface;

    /**
     * Initialize static variables of FileReader class.
     * The method is invoked every time Logs.get() is called.
     *
     * @param queue destination of LogEntry processed for visualization
     * @param logEntryArray a pool of LogEntry maintained for recycling resources
     * @param follow whether FileReader needs to follow live updates
     * @param filter Filter class that decides if a LogEntry should be put into queue
     */
    public static void init(BlockingQueue<LogEntry> queue, BlockingQueue<LogEntry> logEntryArray, Boolean follow,
                            Filter filter) {
        FileReader.queue = queue;
        FileReader.logEntryArray = logEntryArray;
        FileReader.follow = follow;
        FileReader.filterInterface = filter;
    }

    public FileReader(File fileToRead) {
        this.fileToRead = fileToRead;
    }

    @Override
    public void run() {
        boolean isFollowing = isFollowing();
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(fileToRead))) {
            String line;
            while ((line = reader.readLine()) != null || isFollowing) {
                if (line == null) {
                    continue;
                }
                try {
                    LogEntry entry = logEntryArray.remainingCapacity() != 0 ? new LogEntry() : logEntryArray.take();
                    entry.setLogEntry(line);
                    // if the time of entry is after time window given, we stop the thread.
                    if (!filterInterface.checkEndTime(entry)) {
                        break;
                    }
                    logEntryArray.put(entry);
                    // We only put filtered result into blocking queue to save memory.
                    if (filterInterface.filter(entry)) {
                        queue.put(entry);
                        entry.setVisualizeFinished(false);
                        continue;
                    }
                    entry.setVisualizeFinished(true);
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
        return follow != null && follow && fileToRead.getName().equals("evergreen.log");
    }
}