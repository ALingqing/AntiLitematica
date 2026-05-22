package top.chenray.antilitematica.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

/**
 * Writes detection events to a dedicated log file (detections.log)
 * with automatic daily rotation.
 * <p>
 * Configurable via config.yml:
 * <pre>
 * detection_log:
 *   enabled: true
 *   file: "detections.log"   # relative to plugin data folder
 * </pre>
 */
public final class DetectionLogger {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final Plugin plugin;
    private final File logDir;
    private final String fileName;
    private String currentDate;
    private PrintWriter writer;
    private boolean enabled;

    public DetectionLogger(Plugin plugin, boolean enabled, String fileName) {
        this.plugin = plugin;
        this.logDir = plugin.getDataFolder();
        this.fileName = fileName != null && !fileName.isEmpty() ? fileName : "detections.log";
        this.enabled = enabled;
        if (enabled) {
            rotateLog();
        }
    }

    /**
     * Log a detection event to the log file.
     */
    public synchronized void log(String playerName, String uuid, String channel,
                                  String reason, String action, String details) {
        if (!enabled) return;

        try {
            rotateIfNeeded();
            if (writer == null) return;

            String timestamp = TIMESTAMP_FORMAT.format(new Date());
            writer.printf("[%s] [%s] player=%s uuid=%s channel=%s reason=%s action=%s details=%s%n",
                    timestamp, Thread.currentThread().getName(),
                    playerName, uuid, channel, reason, action, details);
            writer.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write detection log", e);
        }
    }

    /**
     * Log a simple message to the detection log.
     */
    public synchronized void log(String message) {
        if (!enabled || writer == null) return;
        try {
            String timestamp = TIMESTAMP_FORMAT.format(new Date());
            writer.printf("[%s] %s%n", timestamp, message);
            writer.flush();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write detection log", e);
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
            // If file from today exists, append; otherwise create new
            boolean append = logFile.exists();
            writer = new PrintWriter(new FileWriter(logFile, true), true);
            if (!append) {
                writer.println("# AntiLitematica Detection Log - " + currentDate);
                writer.println("# Format: [timestamp] [thread] player=... uuid=... channel=...");
                writer.println("# ============================================================");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create detection log file", e);
        }
    }

    /**
     * Set enabled state at runtime.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled && writer == null) {
            rotateLog();
        } else if (!enabled) {
            close();
        }
    }

    /**
     * Close the log file writer.
     */
    public synchronized void close() {
        if (writer != null) {
            writer.close();
            writer = null;
        }
        currentDate = null;
    }
}
