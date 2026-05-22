package top.chenray.antilitematica.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.api.event.DetectionEvent;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.punish.Punisher;
import top.chenray.antilitematica.punish.ViolationRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the AntiLitematicaAPI.
 * <p>
 * Registered as a singleton and via Bukkit ServicesManager.
 */
public final class AntiLitematicaAPIImpl implements AntiLitematicaAPI {

    /** Global singleton instance, set on plugin enable. */
    public static volatile AntiLitematicaAPIImpl INSTANCE;

    private final AntiLitematicaPlugin plugin;

    public AntiLitematicaAPIImpl(AntiLitematicaPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== Plugin Status ====================

    @Override
    public boolean isPluginEnabled() {
        Settings s = plugin.settings();
        return s != null && s.enabled();
    }

    @Override
    public boolean isDetectionEnabled() {
        Settings s = plugin.settings();
        return s != null && s.detection() != null && s.detection().enabled();
    }

    @Override
    public boolean isAntiPrinterEnabled() {
        Settings s = plugin.settings();
        return s != null && s.antiPrinter() != null && s.antiPrinter().enabled();
    }

    @Override
    public boolean isCommandGuardEnabled() {
        Settings s = plugin.settings();
        return s != null && s.commandGuard() != null && s.commandGuard().enabled();
    }

    @Override
    public @NotNull String getPluginVersion() {
        return plugin.getDescription().getVersion();
    }

    // ==================== Player State ====================

    @Override
    public boolean isPlayerPunished(@NotNull UUID uuid) {
        return plugin.isPunished(uuid);
    }

    @Override
    public boolean isPlayerWhitelisted(@NotNull UUID uuid) {
        Settings.Whitelist wl = plugin.settings().whitelist();
        if (wl == null || !wl.enabled()) return false;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        return wl.players().contains(player.getName().toLowerCase());
    }

    @Override
    public boolean isPlayerWhitelisted(@NotNull String playerName) {
        Settings.Whitelist wl = plugin.settings().whitelist();
        if (wl == null || !wl.enabled()) return false;
        return wl.players().contains(playerName.toLowerCase());
    }

    @Override
    public boolean markPlayerPunished(@NotNull UUID uuid) {
        return plugin.markPunished(uuid);
    }

    @Override
    public void unmarkPlayerPunished(@NotNull UUID uuid) {
        plugin.unmarkPunished(uuid);
    }

    // ==================== Violation Records ====================

    @Override
    public @Nullable ViolationRecord getViolationRecord(@NotNull UUID uuid) {
        var tracker = plugin.getPunishmentTracker();
        if (tracker == null) return null;
        return tracker.getRecord(uuid);
    }

    @Override
    public int getViolationCount(@NotNull UUID uuid) {
        var tracker = plugin.getPunishmentTracker();
        if (tracker == null) return 0;
        ViolationRecord record = tracker.getRecord(uuid);
        return record != null ? record.count() : 0;
    }

    @Override
    public @NotNull List<ViolationRecord> getAllViolationRecords() {
        var tracker = plugin.getPunishmentTracker();
        if (tracker == null) return List.of();
        return new ArrayList<>(tracker.getAllRecords());
    }

    @Override
    public boolean resetViolationRecord(@NotNull UUID uuid) {
        var tracker = plugin.getPunishmentTracker();
        if (tracker == null) return false;
        tracker.resetPlayer(uuid);
        return true;
    }

    // ==================== Whitelist Management ====================

    @Override
    public @NotNull List<String> getWhitelistedPlayers() {
        Settings.Whitelist wl = plugin.settings().whitelist();
        if (wl == null) return List.of();
        return new ArrayList<>(wl.players());
    }

    @Override
    public boolean addToWhitelist(@NotNull String playerName) {
        Settings.Whitelist wl = plugin.settings().whitelist();
        if (wl == null) return false;
        String lower = playerName.toLowerCase();
        if (wl.players().contains(lower)) return false;
        // Save via command logic
        var players = new java.util.LinkedHashSet<>(wl.players());
        players.add(lower);
        saveWhitelist(players);
        return true;
    }

    @Override
    public boolean removeFromWhitelist(@NotNull String playerName) {
        Settings.Whitelist wl = plugin.settings().whitelist();
        if (wl == null) return false;
        String lower = playerName.toLowerCase();
        if (!wl.players().contains(lower)) return false;
        var players = new java.util.LinkedHashSet<>(wl.players());
        players.remove(lower);
        saveWhitelist(players);
        return true;
    }

    @Override
    public @NotNull String getWhitelistMode() {
        Settings.Whitelist wl = plugin.settings().whitelist();
        if (wl == null) return "NONE";
        return wl.mode();
    }

    private void saveWhitelist(java.util.Set<String> players) {
        java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
        cfg.set("whitelist.players", new ArrayList<>(players));
        try {
            cfg.save(configFile);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("API: Failed to save whitelist: " + e.getMessage());
        }
        plugin.reloadSettings();
    }

    // ==================== Detection Triggers ====================

    @Override
    public void triggerDetection(@NotNull Player player, @NotNull String channel, @NotNull String reason) {
        Settings settings = plugin.settings();
        if (settings == null || !settings.enabled()) return;

        // Fire detection event
        DetectionEvent event = new DetectionEvent(player, channel, reason, DetectionEvent.DetectionType.CHANNEL);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        Punisher.punishDetection(plugin, settings, player, channel, reason + " [API-triggered]");
    }

    @Override
    public void triggerPrinterDetection(@NotNull Player player, @NotNull String reason) {
        Settings settings = plugin.settings();
        if (settings == null || !settings.enabled()) return;

        DetectionEvent event = new DetectionEvent(player, "printer", reason, DetectionEvent.DetectionType.PRINTER);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        Punisher.punishDetection(plugin, settings, player, "printer", reason + " [API-triggered]");
    }

    // ==================== Integration ====================

    @Override
    public void flagAntiCheat(@NotNull Player player, @NotNull String checkName, int violationLevel,
                              @NotNull String details) {
        var integ = plugin.getIntegrationManager();
        if (integ != null) {
            integ.flag(player, checkName, violationLevel, details);
        }
    }

    // ==================== Configuration ====================

    @Override
    public void reloadConfig() {
        plugin.reloadSettings();
    }

    @Override
    public @NotNull String getDetectionAction() {
        Settings s = plugin.settings();
        if (s == null || s.detection() == null) return "NONE";
        return s.detection().action().name();
    }

    @Override
    public @NotNull List<String> getMonitoredChannels() {
        Settings s = plugin.settings();
        if (s == null || s.detection() == null) return List.of();
        return new ArrayList<>(s.detection().channels());
    }

    @Override
    public @NotNull String getPunishmentReason() {
        Settings s = plugin.settings();
        if (s == null || s.detection() == null) return "";
        String reason = s.detection().reason();
        return reason != null ? reason : "";
    }

    // ==================== Auto-Update ====================

    @Override
    public boolean isAutoUpdateEnabled() {
        Settings.AutoBuild ab = plugin.settings().autoBuild();
        return ab != null && ab.enabled();
    }

    @Override
    public CompletableFuture<Boolean> triggerAutoUpdate() {
        var mgr = plugin.getAutoBuildManager();
        if (mgr == null) return CompletableFuture.completedFuture(false);
        return mgr.downloadLatestAsync();
    }
}
