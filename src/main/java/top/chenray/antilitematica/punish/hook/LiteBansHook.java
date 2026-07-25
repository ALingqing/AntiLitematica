package top.chenray.antilitematica.punish.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * LiteBans integration via its API (fallback to console commands if API unavailable).
 */
public final class LiteBansHook extends AbstractBanHook {
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
   public void kick(Player player, String reason) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "litebans:kick " + sanitizeName(player.getName()) + " " + sanitize(reason));
   }

   @Override
   public void tempBan(Player player, String reason, long durationSeconds) {
      String duration = formatDuration(durationSeconds);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "litebans:tempban " + sanitizeName(player.getName()) + " " + duration + " " + sanitize(reason));
   }

   @Override
   public void ban(Player player, String reason) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "litebans:ban " + sanitizeName(player.getName()) + " " + sanitize(reason));
   }

}
