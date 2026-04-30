package top.chenray.antilitematica.integration;

import org.bukkit.entity.Player;
import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;

public final class IntegrationManager {
   private final AntiLitematicaPlugin plugin;
   private AntiCheatIntegration integration;

   public IntegrationManager(AntiLitematicaPlugin plugin) {
      this.plugin = plugin;
      this.integration = new NoOpIntegration();
   }

   public void load(Settings settings) {
      if (this.integration != null) {
         this.integration.disable();
      }

      String type = settings.integration().type();
      switch (type.toLowerCase()) {
         case "grim":
         case "grimac":
            this.integration = new GrimACIntegration(this.plugin);
            break;
         case "none":
         default:
            this.integration = new NoOpIntegration();
      }

      this.integration.enable();
   }

   public void unload() {
      if (this.integration != null) {
         this.integration.disable();
         this.integration = new NoOpIntegration();
      }

   }

   public void flag(Player player, String checkName, int vl, String details) {
      if (this.integration.isActive()) {
         this.integration.flag(player, checkName, vl, details);
      }

   }

   public AntiCheatIntegration getIntegration() {
      return this.integration;
   }
}
