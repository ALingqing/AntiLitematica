package top.chenray.antilitematica.punish.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * LiteBans integration via its API (fallback to console commands if API unavailable).
 */
public final class LiteBansHook implements BanPluginHook {
   private boolean available = false;

   public LiteBansHook() {
      this.available = Bukkit.getPluginManager().getPlugin("LiteBans") != null;
   }

   @Override
   public String getName() {
      return "LiteBans";
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
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "litebans:kick " + player.getName() + " " + reason);
   }

   @Override
   public void tempBan(Player player, String reason, long durationSeconds) {
      String duration = formatDuration(durationSeconds);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "litebans:tempban " + player.getName() + " " + duration + " " + reason);
   }

   @Override
   public void ban(Player player, String reason) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "litebans:ban " + player.getName() + " " + reason);
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
