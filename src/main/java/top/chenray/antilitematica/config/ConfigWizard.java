package top.chenray.antilitematica.config;

import java.io.File;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Detects server type on first run and recommends optimal configuration.
 * Triggered when config_version is 0 (fresh install) or via /al wizard.
 */
public final class ConfigWizard {

   private final Plugin plugin;

   public ConfigWizard(Plugin plugin) {
      this.plugin = plugin;
   }

   /**
    * Detect server type and recommend config.
    */
   public void run() {
      ServerProfile profile = detectProfile();
      plugin.getLogger().info("Detected server profile: " + profile.name);
      plugin.getLogger().info("Recommended settings:");
      for (String rec : profile.recommendations) {
         plugin.getLogger().info("  " + rec);
      }
   }

   /**
    * Detect the server profile based on worlds and plugins.
    */
   public ServerProfile detectProfile() {
      boolean hasMinigamePlugins = hasPlugin("MinigameLib", "BedWars", "SkyWars", "TheHCF");
      boolean hasCreativePlugins = hasPlugin("WorldEdit", "FastAsyncWorldEdit", "PlotSquared");
      boolean hasSurvivalPlugins = hasPlugin("EssentialsX", "McMMO", "Jobs");
      boolean hasPvPPlugins = hasPlugin("CombatLog", "HCF", "Kingdoms");

      // Count world types
      int survivalWorlds = 0;
      int creativeWorlds = 0;
      for (World w : Bukkit.getWorlds()) {
         String name = w.getName().toLowerCase(Locale.ROOT);
         if (name.contains("creative") || name.contains("plot") || name.contains("build")) {
            creativeWorlds++;
         } else if (!name.contains("nether") && !name.contains("end")) {
            survivalWorlds++;
         }
      }

      if (hasMinigamePlugins || (hasPvPPlugins && !hasSurvivalPlugins)) {
         return minigameProfile();
      }
      if (creativeWorlds > survivalWorlds || hasCreativePlugins) {
         return creativeProfile();
      }
      return survivalProfile();
   }

   private ServerProfile survivalProfile() {
      return new ServerProfile("Survival",
            "Set detection.action to KICK or COMMANDS",
            "Enable graduated_punishment with warn→tempban→ban",
            "Set anti_printer.max_blocks_per_second to 12 (legit players rarely exceed 8)",
            "Enable enforce_raytrace (prevents Litematica printer)",
            "Consider world_whitelist for build worlds");
   }

   private ServerProfile creativeProfile() {
      return new ServerProfile("Creative/Build",
            "Add a 'build' world to world_whitelist.worlds",
            "Or set a per-world override: worlds.build.detection.enabled: false",
            "Set anti_printer.max_blocks_per_second to 20 (creative players place fast)",
            "Consider disabling detect_consecutive_same_type (builders use same blocks)",
            "Consider disabling detect_no_look_change");
   }

   private ServerProfile minigameProfile() {
      return new ServerProfile("Minigame/PvP",
            "Set detection.action to BAN (minigame cheaters should be removed)",
            "Set anti_printer.max_blocks_per_second to 8",
            "Enable graduated punishment with quick escalation: warn→30m tempban→ban",
            "Set dynamic_threshold multiplier.max to 3.0 (detect more aggressively)");
   }

   private boolean hasPlugin(String... names) {
      for (String name : names) {
         if (Bukkit.getPluginManager().getPlugin(name) != null) return true;
      }
      return false;
   }

   public static final class ServerProfile {
      public final String name;
      public final String[] recommendations;

      ServerProfile(String name, String... recommendations) {
         this.name = name;
         this.recommendations = recommendations;
      }
   }
}
