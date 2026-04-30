package top.chenray.antilitematica.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class GuiInputManager {
    private final Plugin plugin;
    private final Map<Player, InputSession> sessions = new HashMap<>();

    public GuiInputManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void registerInput(Player player, InputType type, Consumer<String> callback, String promptMessage) {
        cancel(player);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            InputSession session = sessions.remove(player);
            if (session != null) {
                player.sendMessage(GuiItems.color("&c输入超时，已取消"));
            }
        }, 20L * 30L);

        sessions.put(player, new InputSession(type, callback, timeout));
        player.closeInventory();
        if (promptMessage != null && !promptMessage.isEmpty()) {
            for (String line : promptMessage.split("\\n")) {
                player.sendMessage(GuiItems.color(line));
            }
        }
    }

    public InputSession getSession(Player player) {
        return sessions.get(player);
    }

    public boolean isInputting(Player player) {
        return sessions.containsKey(player);
    }

    public void handleInput(Player player, String message) {
        InputSession session = sessions.remove(player);
        if (session != null && session.timeout != null) {
            session.timeout.cancel();
        }
        if (session != null && session.callback != null) {
            session.callback.accept(message);
        }
    }

    public void cancel(Player player) {
        InputSession session = sessions.remove(player);
        if (session != null && session.timeout != null) {
            session.timeout.cancel();
        }
    }

    public void shutdown() {
        for (InputSession session : sessions.values()) {
            if (session.timeout != null) {
                session.timeout.cancel();
            }
        }
        sessions.clear();
    }

    public enum InputType {
        WEBHOOK_URL,
        BAN_REASON,
        CHANNEL_LIST,
        PLAYER_NAME,
        PREFIX,
        KICK_MSG,
        BLOCKED_MSG,
        REACH_DISTANCE,
        CUSTOM
    }

    public static final class InputSession {
        public final InputType type;
        public final Consumer<String> callback;
        public final BukkitTask timeout;

        InputSession(InputType type, Consumer<String> callback, BukkitTask timeout) {
            this.type = type;
            this.callback = callback;
            this.timeout = timeout;
        }
    }
}
