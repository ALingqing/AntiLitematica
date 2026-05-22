package top.chenray.antilitematica.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when AntiLitematica executes a punishment action against a player.
 * <p>
 * This event is fired <b>after</b> the punishment has been applied.
 * It is not cancellable — use {@link DetectionEvent} to cancel before punishment.
 */
public class PunishmentEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String channel;
    private final String reason;
    private final PunishmentAction action;
    private final int violationCount;
    private final String punishmentType;

    public PunishmentEvent(@NotNull Player player, @Nullable String channel,
                           @NotNull String reason, @NotNull PunishmentAction action,
                           int violationCount, @NotNull String punishmentType) {
        super(true); // async if needed
        this.player = player;
        this.channel = channel;
        this.reason = reason;
        this.action = action;
        this.violationCount = violationCount;
        this.punishmentType = punishmentType;
    }

    /**
     * The player who was punished.
     */
    @NotNull
    public Player getPlayer() {
        return player;
    }

    /**
     * The plugin channel that led to the punishment, or null if N/A.
     */
    @Nullable
    public String getChannel() {
        return channel;
    }

    /**
     * The punishment reason message.
     */
    @NotNull
    public String getReason() {
        return reason;
    }

    /**
     * The action that was taken.
     */
    @NotNull
    public PunishmentAction getAction() {
        return action;
    }

    /**
     * The player's current violation count (for graduated punishment).
     */
    public int getViolationCount() {
        return violationCount;
    }

    /**
     * The type of punishment system used (e.g. "graduated", "legacy").
     */
    @NotNull
    public String getPunishmentType() {
        return punishmentType;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Types of punishment actions.
     */
    public enum PunishmentAction {
        /** Detection was logged only, no action taken */
        LOG,
        /** Player was kicked from the server */
        KICK,
        /** Player received a warning */
        WARN,
        /** Player was temporarily banned */
        TEMPBAN,
        /** Player was permanently banned */
        BAN,
        /** Console commands were executed */
        COMMANDS
    }
}
