package top.chenray.antilitematica.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.chenray.antilitematica.punish.ViolationRecord;

import java.util.List;
import java.util.UUID;

/**
 * AntiLitematica public API.
 * <p>
 * External plugins can obtain an instance via:
 * <pre>{@code
 * AntiLitematicaAPI api = AntiLitematicaAPI.getInstance();
 * if (api != null) { ... }
 * }</pre>
 * Or via Bukkit ServicesManager:
 * <pre>{@code
 * RegisteredServiceProvider<AntiLitematicaAPI> provider =
 *     Bukkit.getServicesManager().getRegistration(AntiLitematicaAPI.class);
 * if (provider != null) {
 *     AntiLitematicaAPI api = provider.getProvider();
 * }
 * }</pre>
 */
public interface AntiLitematicaAPI {

    // ==================== Singleton Access ====================

    /**
     * Get the API instance. Returns null if AntiLitematica is not loaded.
     */
    @Nullable
    static AntiLitematicaAPI getInstance() {
        return AntiLitematicaAPIImpl.INSTANCE;
    }

    // ==================== Plugin Status ====================

    /**
     * Check if AntiLitematica is globally enabled.
     */
    boolean isPluginEnabled();

    /**
     * Check if detection is enabled.
     */
    boolean isDetectionEnabled();

    /**
     * Check if anti-printer is enabled.
     */
    boolean isAntiPrinterEnabled();

    /**
     * Check if command guard is enabled.
     */
    boolean isCommandGuardEnabled();

    /**
     * Check if graduated punishment is enabled.
     */
    boolean isGraduatedPunishmentEnabled();

    /**
     * Get the current plugin version.
     */
    @NotNull
    String getPluginVersion();

    // ==================== Player State ====================

    /**
     * Check if a player is currently marked as punished (flagged in this session).
     */
    boolean isPlayerPunished(@NotNull UUID uuid);

    /**
     * Check if a player is whitelisted (exempt from punishment).
     */
    boolean isPlayerWhitelisted(@NotNull UUID uuid);

    /**
     * Check if a player is whitelisted by name.
     */
    boolean isPlayerWhitelisted(@NotNull String playerName);

    /**
     * Manually flag a player for punishment. Returns true if not already flagged.
     */
    boolean markPlayerPunished(@NotNull UUID uuid);

    /**
     * Remove a player from the punished set.
     */
    void unmarkPlayerPunished(@NotNull UUID uuid);

    // ==================== Violation Records ====================

    /**
     * Get the violation record for a player, or null if they have no record.
     */
    @Nullable
    ViolationRecord getViolationRecord(@NotNull UUID uuid);

    /**
     * Get the current violation count for a player within the punishment window.
     * Returns 0 if graduated punishment is not enabled or player has no record.
     */
    int getViolationCount(@NotNull UUID uuid);

    /**
     * Get all players who have violation records.
     */
    @NotNull
    List<ViolationRecord> getAllViolationRecords();

    /**
     * Reset violation record for a player.
     */
    boolean resetViolationRecord(@NotNull UUID uuid);

    // ==================== Whitelist Management ====================

    /**
     * Get all whitelisted player names.
     */
    @NotNull
    List<String> getWhitelistedPlayers();

    /**
     * Add a player to the whitelist.
     */
    boolean addToWhitelist(@NotNull String playerName);

    /**
     * Remove a player from the whitelist.
     */
    boolean removeFromWhitelist(@NotNull String playerName);

    /**
     * Get the whitelist mode (LOG_ONLY or NORMAL).
     */
    @NotNull
    String getWhitelistMode();

    // ==================== Detection Triggers ====================

    /**
     * Manually trigger a detection for a player.
     * This simulates a detection and applies configured punishment.
     *
     * @param player the detected player
     * @param channel the detected channel (e.g. "servux:litematics")
     * @param reason a description of why the detection was triggered
     */
    void triggerDetection(@NotNull Player player, @NotNull String channel, @NotNull String reason);

    /**
     * Manually trigger an anti-printer detection for a player.
     *
     * @param player the detected player
     * @param reason a description of why the detection was triggered
     */
    void triggerPrinterDetection(@NotNull Player player, @NotNull String reason);

    // ==================== Integration ====================

    /**
     * Flag a player in the configured anti-cheat integration (e.g. GrimAC).
     */
    void flagAntiCheat(@NotNull Player player, @NotNull String checkName, int violationLevel,
                       @NotNull String details);

    // ==================== Configuration ====================

    /**
     * Reload the plugin configuration from disk.
     */
    void reloadConfig();

    /**
     * Get the configured detection action (LOG, KICK, BAN, COMMANDS).
     */
    @NotNull
    String getDetectionAction();

    /**
     * Get the list of monitored plugin channels.
     */
    @NotNull
    List<String> getMonitoredChannels();

    /**
     * Get the configured punishment reason.
     */
    @NotNull
    String getPunishmentReason();

    // ==================== Auto-Update ====================

    /**
     * Check if auto-update is enabled.
     */
    boolean isAutoUpdateEnabled();

    /**
     * Trigger an automatic update check and download.
     * Returns a CompletableFuture that completes with true if a new version was downloaded.
     */
    java.util.concurrent.CompletableFuture<Boolean> triggerAutoUpdate();

    // ==================== Detection Log ====================

    /**
     * Check if the dedicated detection log file is enabled.
     */
    boolean isDetectionLogEnabled();

    // ==================== Storage ====================

    /**
     * Get the current violation storage type.
     *
     * @return "sqlite", "mysql", or "memory"
     */
    @NotNull
    String getStorageType();
}
