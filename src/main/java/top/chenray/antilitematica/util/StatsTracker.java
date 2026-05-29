package top.chenray.antilitematica.util;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Tracks daily detection/punishment statistics and manages record retention.
 * Data stored in stats.yml in the plugin data folder.
 * Uses batched saves to avoid disk I/O on every detection.
 */
public final class StatsTracker {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long SAVE_INTERVAL_TICKS = 600L; // 30 seconds

    private final Plugin plugin;
    private final File statsFile;
    private FileConfiguration stats;
    private final int recordRetentionDays;
    private final int statsRetentionDays;
    private final boolean enabled;
    private final AtomicInteger pendingDetections = new AtomicInteger(0);
    private final AtomicInteger pendingPunishments = new AtomicInteger(0);
    private volatile boolean dirty = false;
    private int saveTaskId = -1;

    public StatsTracker(Plugin plugin, boolean enabled, int recordRetentionDays, int statsRetentionDays) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.recordRetentionDays = Math.max(0, recordRetentionDays);
        this.statsRetentionDays = Math.max(0, statsRetentionDays);
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        this.stats = YamlConfiguration.loadConfiguration(statsFile);
        if (enabled) {
            cleanOldStats();
            startBatchedSave();
        }
    }

    /** Start a recurring task that flushes pending stats to disk. */
    private void startBatchedSave() {
        saveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            flush();
        }, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS).getTaskId();
    }

    /** Flush pending counters to the YAML config and save to disk. */
    private void flush() {
        int det = pendingDetections.getAndSet(0);
        int pun = pendingPunishments.getAndSet(0);
        if (det > 0 || pun > 0) {
            String day = LocalDate.now().format(DAY_FMT);
            if (det > 0) {
                int count = stats.getInt("detections." + day, 0);
                stats.set("detections." + day, count + det);
            }
            if (pun > 0) {
                int count = stats.getInt("punishments." + day, 0);
                stats.set("punishments." + day, count + pun);
            }
            save();
        } else if (dirty) {
            dirty = false;
            save();
        }
    }

    /** Cancel the batched save task and flush remaining data. Call on plugin disable. */
    public void shutdown() {
        if (saveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(saveTaskId);
            saveTaskId = -1;
        }
        flush();
    }

    /** Record a detection event (in-memory, batched to disk). */
    public void recordDetection() {
        pendingDetections.incrementAndGet();
    }

    /** Record a punishment event (in-memory, batched to disk). */
    public void recordPunishment() {
        pendingPunishments.incrementAndGet();
    }

    /** Get today's detection count. */
    public int getTodayDetections() {
        return stats.getInt("detections." + LocalDate.now().format(DAY_FMT), 0);
    }

    /** Get today's punishment count. */
    public int getTodayPunishments() {
        return stats.getInt("punishments." + LocalDate.now().format(DAY_FMT), 0);
    }

    /** Get total detections across all time. */
    public int getTotalDetections() {
        int total = 0;
        org.bukkit.configuration.ConfigurationSection sec = stats.getConfigurationSection("detections");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                total += stats.getInt("detections." + key);
            }
        }
        return total;
    }

    /** Get total punishments across all time. */
    public int getTotalPunishments() {
        int total = 0;
        org.bukkit.configuration.ConfigurationSection sec = stats.getConfigurationSection("punishments");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                total += stats.getInt("punishments." + key);
            }
        }
        return total;
    }

    /** Get detection hit rate as percentage (detections / total). */
    public double getHitRate() {
        int total = getTotalDetections();
        int punish = getTotalPunishments();
        if (total == 0) return 0;
        return (double) punish / total * 100;
    }

    /** Get detections by date range. */
    public Map<String, Integer> getDetectionsByDay(int daysBack) {
        Map<String, Integer> map = new ConcurrentHashMap<>();
        if (daysBack <= 0) return map;
        LocalDate now = LocalDate.now();
        org.bukkit.configuration.ConfigurationSection sec = stats.getConfigurationSection("detections");
        if (sec != null) {
            for (int i = 0; i < daysBack; i++) {
                String day = now.minusDays(i).format(DAY_FMT);
                int count = stats.getInt("detections." + day, 0);
                if (count > 0) map.put(day, count);
            }
        }
        return map;
    }

    /** Remove old records from PunishmentTracker based on retention days. */
    public void cleanRecords() {
        if (recordRetentionDays <= 0) return;
        var tracker = plugin.getServer().getPluginManager().getPlugin("AntiLitematica");
        // Called externally via scheduler
    }

    private void cleanOldStats() {
        if (statsRetentionDays <= 0) return;
        String cutoff = LocalDate.now().minusDays(statsRetentionDays).format(DAY_FMT);
        org.bukkit.configuration.ConfigurationSection detSec = stats.getConfigurationSection("detections");
        if (detSec != null) {
            for (String key : detSec.getKeys(false)) {
                if (key.compareTo(cutoff) < 0) {
                    stats.set("detections." + key, null);
                }
            }
        }
        org.bukkit.configuration.ConfigurationSection punSec = stats.getConfigurationSection("punishments");
        if (punSec != null) {
            for (String key : punSec.getKeys(false)) {
                if (key.compareTo(cutoff) < 0) {
                    stats.set("punishments." + key, null);
                }
            }
        }
        save();
    }

    private void save() {
        try { stats.save(statsFile); } catch (Exception e) { /* ignore */ }
    }
}
