package top.chenray.antilitematica.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Writes detection events to a dedicated log file (detections.log)
 * with automatic daily rotation and batched writes.
 */
public final class DetectionLogger {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final long FLUSH_INTERVAL_TICKS = 200L; // 10 seconds

    private final Plugin plugin;
    private final File logDir;
    private final String fileName;
    private String currentDate;
    private PrintWriter writer;
    private boolean enabled;
    private final List<String> pendingLines = new ArrayList<>();
    private ScheduledTask flushTask;

    public DetectionLogger(Plugin plugin, boolean enabled, String fileName) {
        this.plugin = plugin;
        this.logDir = plugin.getDataFolder();
        this.fileName = fileName != null && !fileName.isEmpty() ? fileName : "detections.log";
        this.enabled = enabled;
        if (enabled) {
            rotateLog();
            startFlushTask();
        }
    }

    private void startFlushTask() {
        flushTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin,
                t -> flush(),
                FLUSH_INTERVAL_TICKS * 50L, FLUSH_INTERVAL_TICKS * 50L,
                TimeUnit.MILLISECONDS);
    }

    /** Flush all pending lines to disk. */
    private synchronized void flush() {
        if (!enabled || writer == null || pendingLines.isEmpty()) return;
        try {
            rotateIfNeeded();
            for (String line : pendingLines) {
                writer.println(line);
            }
            writer.flush();
            pendingLines.clear();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to flush detection log", e);
        }
    }

    /**
     * Log a detection event to the log file (batched write).
     */
    public void log(String playerName, String uuid, String channel,
                                  String reason, String action, String details) {
        if (!enabled) return;
        String timestamp = TIMESTAMP_FORMAT.format(new Date());
        String line = String.format("[%s] player=%s uuid=%s channel=%s reason=%s action=%s details=%s",
                timestamp, playerName, uuid, channel, reason, action, details);
        synchronized (this) {
            pendingLines.add(line);
        }
    }

    /**
     * Log a simple message (batched write).
     */
    public void log(String message) {
        if (!enabled) return;
        String timestamp = TIMESTAMP_FORMAT.format(new Date());
        synchronized (this) {
            pendingLines.add("[" + timestamp + "] " + message);
        }
    }

    private void rotateIfNeeded() {
        String today = DATE_FORMAT.format(new Date());
        if (!today.equals(currentDate)) {
            close();
            rotateLog();
        }
    }

    private void rotateLog() {
        try {
            currentDate = DATE_FORMAT.format(new Date());
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            File logFile = new File(logDir, fileName);
            boolean append = logFile.exists();
            writer = new PrintWriter(new FileWriter(logFile, true), true);
            if (!append) {
                writer.println("# AntiLitematica Detection Log - " + currentDate);
                writer.println("# ============================================================");
                writer.flush();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create detection log file", e);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled && writer == null) {
            rotateLog();
            startFlushTask();
        } else if (!enabled) {
            close();
        }
    }

    /** Flush remaining lines and cancel flush task. Call on plugin disable. */
    public void close() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flush();
        if (writer != null) {
            writer.close();
            writer = null;
        }
        currentDate = null;
    }
}
