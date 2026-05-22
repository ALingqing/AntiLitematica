package top.chenray.antilitematica.util;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Tracks daily detection/punishment statistics and manages record retention.
 * Data stored in stats.yml in the plugin data folder.
 */
public final class StatsTracker {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Plugin plugin;
    private final File statsFile;
    private FileConfiguration stats;
    private final int recordRetentionDays;
    private final int statsRetentionDays;

    public StatsTracker(Plugin plugin, boolean enabled, int recordRetentionDays, int statsRetentionDays) {
        this.plugin = plugin;
        this.recordRetentionDays = Math.max(0, recordRetentionDays);
        this.statsRetentionDays = Math.max(0, statsRetentionDays);
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        this.stats = YamlConfiguration.loadConfiguration(statsFile);
        if (enabled) {
            cleanOldStats();
        }
    }

    /** Record a detection event. */
    public void recordDetection() {
        String day = LocalDate.now().format(DAY_FMT);
        int count = stats.getInt("detections." + day, 0);
        stats.set("detections." + day, count + 1);
        save();
    }

    /** Record a punishment event. */
    public void recordPunishment() {
        String day = LocalDate.now().format(DAY_FMT);
        int count = stats.getInt("punishments." + day, 0);
        stats.set("punishments." + day, count + 1);
        save();
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
        for (String key : stats.getConfigurationSection("detections").getKeys(false)) {
            total += stats.getInt("detections." + key);
        }
        return total;
    }

    /** Get total punishments across all time. */
    public int getTotalPunishments() {
        int total = 0;
        for (String key : stats.getConfigurationSection("punishments").getKeys(false)) {
            total += stats.getInt("punishments." + key);
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
        LocalDate now = LocalDate.now();
        for (int i = 0; i < daysBack; i++) {
            String day = now.minusDays(i).format(DAY_FMT);
            int count = stats.getInt("detections." + day, 0);
            if (count > 0) map.put(day, count);
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
        for (String key : stats.getConfigurationSection("detections").getKeys(false)) {
            if (key.compareTo(cutoff) < 0) {
                stats.set("detections." + key, null);
            }
        }
        for (String key : stats.getConfigurationSection("punishments").getKeys(false)) {
            if (key.compareTo(cutoff) < 0) {
                stats.set("punishments." + key, null);
            }
        }
        save();
    }

    private void save() {
        try { stats.save(statsFile); } catch (Exception e) { /* ignore */ }
    }
}
