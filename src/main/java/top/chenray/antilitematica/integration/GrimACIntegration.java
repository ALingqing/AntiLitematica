package top.chenray.antilitematica.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.chenray.antilitematica.AntiLitematicaPlugin;

public final class GrimACIntegration implements AntiCheatIntegration {
   private final AntiLitematicaPlugin plugin;
   private boolean active = false;

   public GrimACIntegration(AntiLitematicaPlugin plugin) {
      this.plugin = plugin;
   }

   public void enable() {
      if (Bukkit.getPluginManager().getPlugin("GrimAC") == null) {
         this.plugin.getLogger().warning("GrimAC not found.");
      } else {
         this.active = true;
         this.plugin.getLogger().info("GrimAC integration enabled (limited functionality).");
      }
   }

   public void disable() {
      this.active = false;
   }

   public boolean isActive() {
      return this.active;
   }

   public void flag(Player player, String checkName, int vl, String details) {
      if (this.isActive()) {
         ;
      }
   }

   public String getName() {
      return "GrimAC";
   }
}
