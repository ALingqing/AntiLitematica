package top.chenray.antilitematica.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.bukkit.plugin.Plugin;

/**
 * Logs admin actions (config changes, resets, etc.) to audit.log.
 */
public final class AuditLogger {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Plugin plugin;
    private final File auditFile;
    private PrintWriter writer;

    public AuditLogger(Plugin plugin) {
        this.plugin = plugin;
        this.auditFile = new File(plugin.getDataFolder(), "audit.log");
        try {
            writer = new PrintWriter(new FileWriter(auditFile, true), true);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to open audit log: " + e.getMessage());
        }
    }

    public void log(String action, String admin, String details) {
        if (writer == null) return;
        writer.printf("[%s] [%s] %s: %s%n",
                LocalDateTime.now().format(FMT), admin, action, details);
    }

    public void close() {
        if (writer != null) writer.close();
    }
}
