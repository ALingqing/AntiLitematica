package top.chenray.antilitematica.punish.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * AdvancedBan integration via console commands (most reliable across versions).
 */
public final class AdvancedBanHook implements BanPluginHook {
   private boolean available = false;

   public AdvancedBanHook() {
      this.available = Bukkit.getPluginManager().getPlugin("AdvancedBan") != null;
   }

   @Override
   public String getName() {
      return "AdvancedBan";
   }

   @Override
   public boolean isAvailable() {
      return this.available;
   }

   @Override
   public void warn(Player player, String reason) {
      if (player.isOnline()) {
         player.sendMessage(reason);
      }
   }

   @Override
   public void kick(Player player, String reason) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancedban:kick " + player.getName() + " " + reason);
   }

   @Override
   public void tempBan(Player player, String reason, long durationSeconds) {
      String duration = formatDuration(durationSeconds);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancedban:tempban " + player.getName() + " " + duration + " " + reason);
   }

   @Override
   public void ban(Player player, String reason) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancedban:ban " + player.getName() + " " + reason);
   }

   private static String formatDuration(long seconds) {
      if (seconds < 60) {
         return seconds + "s";
      } else if (seconds < 3600) {
         return (seconds / 60) + "m";
      } else if (seconds < 86400) {
         return (seconds / 3600) + "h";
      } else {
         return (seconds / 86400) + "d";
      }
   }
}
