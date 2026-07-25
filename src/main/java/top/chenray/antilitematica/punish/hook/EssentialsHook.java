package top.chenray.antilitematica.punish.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * EssentialsX fallback hook using reflection.
 */
public final class EssentialsHook extends AbstractBanHook {
   private boolean available = false;

   public EssentialsHook() {
      this.available = Bukkit.getPluginManager().getPlugin("Essentials") != null;
   }

   @Override
   public String getName() {
      return "EssentialsX";
   }

   @Override
   public boolean isAvailable() {
      return this.available;
   }

   @Override
   public void kick(Player player, String reason) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ekick " + sanitizeName(player.getName()) + " " + sanitize(reason));
   }

   @Override
   public void tempBan(Player player, String reason, long durationSeconds) {
      long minutes = Math.max(1, durationSeconds / 60);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "etempban " + sanitizeName(player.getName()) + " " + minutes + "m " + sanitize(reason));
   }

   @Override
   public void ban(Player player, String reason) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eban " + sanitizeName(player.getName()) + " " + sanitize(reason));
   }
}
