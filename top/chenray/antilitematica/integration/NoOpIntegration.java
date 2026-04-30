package top.chenray.antilitematica.integration;

import org.bukkit.entity.Player;

public final class NoOpIntegration implements AntiCheatIntegration {
   public void enable() {
   }

   public void disable() {
   }

   public boolean isActive() {
      return false;
   }

   public void flag(Player player, String checkName, int vl, String details) {
   }

   public String getName() {
      return "None";
   }
}
