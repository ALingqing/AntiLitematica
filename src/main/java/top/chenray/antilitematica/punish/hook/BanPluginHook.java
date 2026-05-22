package top.chenray.antilitematica.punish.hook;

import org.bukkit.entity.Player;

/**
 * Unified interface for ban plugin integrations.
 */
public interface BanPluginHook {

   String getName();

   boolean isAvailable();

   void warn(Player player, String reason);

   void kick(Player player, String reason);

   void tempBan(Player player, String reason, long durationSeconds);

   void ban(Player player, String reason);
}
