package top.chenray.antilitematica.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player is detected using Litematica / Schematica / Printer.
 * <p>
 * This event is <b>cancellable</b>. If cancelled, no punishment will be applied
 * but the detection will still be logged.
 */
public class DetectionEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String channel;
    private final String reason;
    private final DetectionType detectionType;
    private boolean cancelled;

    public DetectionEvent(@NotNull Player player, @NotNull String channel,
                          @NotNull String reason, @NotNull DetectionType detectionType) {
        super(player);
        this.channel = channel;
        this.reason = reason;
        this.detectionType = detectionType;
    }

    /**
     * The plugin channel that triggered the detection (e.g. "servux:litematics").
     * For printer detections this will be "printer".
     */
    @NotNull
    public String getChannel() {
        return channel;
    }

    /**
     * A human-readable reason for the detection.
     */
    @NotNull
    public String getReason() {
        return reason;
    }

    /**
     * The type of detection that occurred.
     */
    @NotNull
    public DetectionType getDetectionType() {
        return detectionType;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    @SuppressWarnings("unused") // required by Bukkit
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * The type of detection.
     */
    public enum DetectionType {
        /** Detected via plugin channel registration/payload (Litematica/Schematica) */
        CHANNEL,
        /** Detected via anti-printer engine (block placement analysis) */
        PRINTER,
        /** Detected via ProtocolLib signal (EasyPlace, NBT query, Servux metadata) */
        SIGNAL,
        /** Detected via command guard (rapid command execution) */
        COMMAND_GUARD,
        /** Triggered manually via API */
        API
    }
}
