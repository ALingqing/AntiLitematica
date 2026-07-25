package top.chenray.antilitematica.config;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Automatically migrates old config.yml to include new sections with default values.
 * Preserves existing user configuration while adding missing default keys.
 */
public final class ConfigMigrator {

    private static final int CURRENT_VERSION = 5;

    private final Plugin plugin;
    private final Logger logger;

    public ConfigMigrator(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Run migration. Should be called before loading settings.
     *
     * @return true if config was updated
     */
    public boolean migrate() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            return false;
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        int oldVersion = cfg.getInt("config_version", 0);

        if (oldVersion >= CURRENT_VERSION) {
            return false; // Already up to date
        }

        logger.info("Migrating config.yml from version " + oldVersion + " to " + CURRENT_VERSION + "...");
        boolean changed = false;

        // v1 → v2: add detection_log, mysql, etc.
        if (oldVersion < 1) {
            changed |= addDefault(cfg, "detection_log.enabled", false);
            changed |= addDefault(cfg, "detection_log.file", "detections.log");
        }

        if (oldVersion < 2) {
            // Add MySQL config to graduated_punishment
            changed |= addDefault(cfg, "graduated_punishment.mysql.host", "localhost");
            changed |= addDefault(cfg, "graduated_punishment.mysql.port", 3306);
            changed |= addDefault(cfg, "graduated_punishment.mysql.database", "antilitematica");
            changed |= addDefault(cfg, "graduated_punishment.mysql.user", "root");
            changed |= addDefault(cfg, "graduated_punishment.mysql.password", "");
        }

        if (oldVersion < 3) {
            // v3: command_guard.allowed_commands, world_whitelist, stats
            changed |= addDefault(cfg, "command_guard.allowed_commands", java.util.List.of("/msg", "/tell", "/r"));
            changed |= addDefault(cfg, "world_whitelist.enabled", false);
            changed |= addDefault(cfg, "world_whitelist.worlds", java.util.List.of());
            changed |= addDefault(cfg, "stats.enabled", true);
            changed |= addDefault(cfg, "stats.record_retention_days", 30);
            changed |= addDefault(cfg, "stats.stats_retention_days", 90);
        }

        if (oldVersion < 4) {
            // v4: worlds section (multi-world)
            changed |= addDefault(cfg, "worlds", null);
        }

        if (oldVersion < 5) {
            // v5: compatibility section (Tweakeroo)
            changed |= addDefault(cfg, "compatibility.tweakeroo_mode", false);
        }

        // Set current version
        cfg.set("config_version", CURRENT_VERSION);
        changed = true;

        if (changed) {
            try {
                cfg.save(configFile);
                logger.info("Config migrated to version " + CURRENT_VERSION + ".");
            } catch (IOException e) {
                logger.warning("Failed to save migrated config: " + e.getMessage());
            }
        }

        return changed;
    }

    private boolean addDefault(FileConfiguration cfg, String path, Object value) {
        if (!cfg.contains(path)) {
            cfg.set(path, value);
            logger.fine("  Added default: " + path + " = " + value);
            return true;
        }
        return false;
    }

    /**
     * Get the current config version.
     */
    public static int currentVersion() {
        return CURRENT_VERSION;
    }
}
