package com.aws.iot.evergreen.cli.util.logs;

import lombok.AllArgsConstructor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/*
 * Runnable class responsible of reading a single log file
 */
@AllArgsConstructor
public class FileReader implements Runnable {
    private final File fileToRead;
    private final BlockingQueue<LogEntry> queue;
    private final BlockingQueue<LogEntry> logEntryArray;

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(fileToRead))) {
            String line;
            //TODO: Follow live updates of log file
            while ((line = reader.readLine()) != null) {
                try {
                    LogEntry entry = logEntryArray.remainingCapacity() != 0 ? new LogEntry() : logEntryArray.take();
                    entry.setLogEntry(line, LogsUtil.getMAP_READER().readValue(line));
                    queue.put(entry);
                    logEntryArray.put(entry);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (IOException e) {
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
}