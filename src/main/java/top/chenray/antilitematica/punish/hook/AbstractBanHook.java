package top.chenray.antilitematica.punish.hook;

import org.bukkit.entity.Player;
import top.chenray.antilitematica.util.CommandSanitizer;

/**
 * Abstract base for ban plugin hooks.
 * Provides shared sanitization and warn() implementation.
 */
public abstract class AbstractBanHook implements BanPluginHook {

   @Override
   public void warn(Player player, String reason) {
      if (player != null && player.isOnline()) {
         player.sendMessage(reason);
      }
   }

   /**
    * Sanitize a value for safe use in console commands.
    */
   protected static String sanitize(String input) {
      return CommandSanitizer.sanitize(input);
   }

   /**
    * Sanitize a player name for safe use in console commands.
    */
   protected static String sanitizeName(String name) {
      return CommandSanitizer.sanitizePlayerName(name);
   }

   /**
    * Format seconds to a duration string (e.g. "30m", "2h", "7d").
    */
   protected static String formatDuration(long seconds) {
      if (seconds < 60) return seconds + "s";
      if (seconds < 3600) return (seconds / 60) + "m";
      if (seconds < 86400) return (seconds / 3600) + "h";
      return (seconds / 86400) + "d";
   }
}
