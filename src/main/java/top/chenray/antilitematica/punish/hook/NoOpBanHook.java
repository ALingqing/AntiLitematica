package top.chenray.antilitematica.punish.hook;

import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.entity.Player;

/**
 * Fallback hook using Bukkit native ban/kick when no ban plugin is present.
 */
public final class NoOpBanHook implements BanPluginHook {

   @Override
   public String getName() {
      return "BukkitNative";
   }

   @Override
   public boolean isAvailable() {
      return true;
   }

   @Override
   public void warn(Player player, String reason) {
      if (player.isOnline()) {
         player.sendMessage(reason);
      }
   }

   @Override
   public void kick(Player player, String reason) {
      if (player.isOnline()) {
         player.kickPlayer(reason);
      }
   }

   @Override
   public void tempBan(Player player, String reason, long durationSeconds) {
      if (player.isOnline()) {
         long expires = System.currentTimeMillis() + (durationSeconds * 1000L);
         Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), reason, new java.util.Date(expires), "AntiLitematica");
         player.kickPlayer(reason);
      }
   }

   @Override
   public void ban(Player player, String reason) {
      if (player.isOnline()) {
         Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), reason, (java.util.Date) null, "AntiLitematica");
         player.kickPlayer(reason);
      }
   }
}
