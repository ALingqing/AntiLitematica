package top.chenray.antilitematica.punish.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * AdvancedBan integration via console commands (most reliable across versions).
 */
public final class AdvancedBanHook extends AbstractBanHook {
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
   public void kick(Player player, String reason) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancedban:kick " + sanitizeName(player.getName()) + " " + sanitize(reason));
   }

   @Override
   public void tempBan(Player player, String reason, long durationSeconds) {
      String duration = formatDuration(durationSeconds);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancedban:tempban " + sanitizeName(player.getName()) + " " + duration + " " + sanitize(reason));
   }

   @Override
   public void ban(Player player, String reason) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancedban:ban " + sanitizeName(player.getName()) + " " + sanitize(reason));
   }

}
