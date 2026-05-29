package top.chenray.antilitematica.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Logs admin actions (config changes, resets, etc.) to audit.log.
 * Uses batched writes to reduce disk I/O.
 */
public final class AuditLogger {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long FLUSH_INTERVAL_TICKS = 200L; // 10 seconds

    private final Plugin plugin;
    private final File auditFile;
    private PrintWriter writer;
    private final List<String> pendingLines = new ArrayList<>();
    private int flushTaskId = -1;

    public AuditLogger(Plugin plugin) {
        this.plugin = plugin;
        this.auditFile = new File(plugin.getDataFolder(), "audit.log");
        try {
            writer = new PrintWriter(new FileWriter(auditFile, true));
            startFlushTask();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to open audit log: " + e.getMessage());
        }
    }

    private void startFlushTask() {
        flushTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            flush();
        }, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS).getTaskId();
    }

    private synchronized void flush() {
        if (writer == null || pendingLines.isEmpty()) return;
        try {
            for (String line : pendingLines) {
                writer.println(line);
            }
            writer.flush();
            pendingLines.clear();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to flush audit log: " + e.getMessage());
        }
    }

    public void log(String action, String admin, String details) {
        if (writer == null) return;
        String line = String.format("[%s] [%s] %s: %s",
                LocalDateTime.now().format(FMT), admin, action, details);
        synchronized (this) {
            pendingLines.add(line);
        }
    }

    /** Flush remaining lines and close. Call on plugin disable. */
    public void close() {
        if (flushTaskId != -1) {
            Bukkit.getScheduler().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
        flush();
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
