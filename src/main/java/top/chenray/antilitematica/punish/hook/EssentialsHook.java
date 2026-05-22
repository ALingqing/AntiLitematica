package top.chenray.antilitematica.punish.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * EssentialsX fallback hook using reflection.
 */
public final class EssentialsHook implements BanPluginHook {
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
   public void warn(Player player, String reason) {
      if (player.isOnline()) {
         player.sendMessage(reason);
      }
   }

   @Override
   public void kick(Player player, String reason) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ekick " + player.getName() + " " + reason);
   }

   @Override
   public void tempBan(Player player, String reason, long durationSeconds) {
      long minutes = Math.max(1, durationSeconds / 60);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "etempban " + player.getName() + " " + minutes + "m " + reason);
   }

   @Override
   public void ban(Player player, String reason) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eban " + player.getName() + " " + reason);
   }
}
