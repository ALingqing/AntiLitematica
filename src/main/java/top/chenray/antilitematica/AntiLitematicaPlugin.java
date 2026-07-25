package top.chenray.antilitematica;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import top.chenray.antilitematica.cmd.AntiLitematicaCommand;
import top.chenray.antilitematica.config.ConfigMigrator;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.detection.ModChannelDetector;
import top.chenray.antilitematica.guard.CommandGuard;
import top.chenray.antilitematica.guard.PlacementGuard;
import top.chenray.antilitematica.integration.IntegrationManager;
import top.chenray.antilitematica.protocol.ProtocolLibBridge;
import top.chenray.antilitematica.punish.GraduatedPunisher;
import top.chenray.antilitematica.punish.PunishmentTracker;
import top.chenray.antilitematica.placeholder.AntiLitematicaExpansion;
import top.chenray.antilitematica.state.PunishStateListener;
import top.chenray.antilitematica.threshold.DynamicThresholdManager;
import top.chenray.antilitematica.api.AntiLitematicaAPIImpl;
import top.chenray.antilitematica.util.DetectionLogger;
import top.chenray.antilitematica.util.OneBotNotifier;
import top.chenray.antilitematica.util.StatsTracker;

public final class AntiLitematicaPlugin extends JavaPlugin {
   private volatile Settings settings;
   private ModChannelDetector modChannelDetector;
   private PlacementGuard placementGuard;
   private CommandGuard commandGuard;
   private ProtocolLibBridge protocolLibBridge;
   private IntegrationManager integrationManager;
   private PunishmentTracker punishmentTracker;
   private GraduatedPunisher graduatedPunisher;
   /** World-aware punished set: world name -> set of punished player UUIDs */
   private final Map<String, Set<UUID>> punished = new ConcurrentHashMap<>();
   private DynamicThresholdManager dynamicThresholdManager;
   private DetectionLogger detectionLogger;
   private OneBotNotifier oneBotNotifier;
   private StatsTracker statsTracker;

   public void onEnable() {
      // Fancy startup ASCII art
      this.getLogger().info("\n" +
              "   █████╗ ███╗   ██╗████████╗██╗██╗     ██╗████████╗███████╗███╗   ███╗ █████╗ ████████╗██╗██╗  ██╗ █████╗   \n" +
              "  ██╔══██╗████╗  ██║╚══██╔══╝██║██║     ██║╚══██╔══╝██╔════╝████╗ ████║██╔══██╗╚══██╔══╝██║██║ ██╔╝██╔══██╗  \n" +
              "  ███████║██╔██╗ ██║   ██║   ██║██║     ██║   ██║   █████╗  ██╔████╔██║███████║   ██║   ██║█████╔╝ ███████║  \n" +
              "  ██╔══██║██║╚██╗██║   ██║   ██║██║     ██║   ██║   ██╔══╝  ██║╚██╔╝██║██╔══██║   ██║   ██║██╔═██╗ ██╔══██║  \n" +
              "  ██║  ██║██║ ╚████║   ██║   ██║███████╗██║   ██║   ███████╗██║ ╚═╝ ██║██║  ██║   ██║   ██║██║  ██╗██║  ██║  \n" +
              "  ╚═╝  ╚═╝╚═╝  ╚═══╝   ╚═╝   ╚═╝╚══════╝╚═╝   ╚═╝   ╚══════╝╚═╝     ╚═╝╚═╝  ╚═╝   ╚═╝   ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝  \n");
      this.getLogger().info("AntiLitematica enabled | Author: ALingqing_ | Version: " + this.getDescription().getVersion());
      // Auto-migrate old config files to include new default sections
      new ConfigMigrator(this).migrate();
      this.saveDefaultConfig();
      // Save bundled language files from JAR resources/lang/ to plugin lang/ folder
      File langDir = new File(this.getDataFolder(), "lang");
      if (!langDir.exists()) langDir.mkdirs();
      String[] bundledLangs = {"messages.yml", "messages_zh_CN.yml", "messages_en_US.yml", "messages_zh_TW.yml"};
      for (String name : bundledLangs) {
         String resourcePath = "lang/" + name;
         if (this.getResource(resourcePath) != null) {
            File target = new File(langDir, name);
            if (!target.exists()) {
               try (java.io.InputStream in = this.getResource(resourcePath)) {
                  if (in != null) {
                     java.nio.file.Files.copy(in, target.toPath());
                  }
               } catch (java.io.IOException e) {
                  this.getLogger().warning("Failed to save " + name + ": " + e.getMessage());
               }
            }
         }
      }
      this.getServer().getPluginManager().registerEvents(new PunishStateListener(this), this);
      PluginCommand cmd = this.getCommand("antilitematica");
      if (cmd != null) {
         cmd.setExecutor(new AntiLitematicaCommand(this));
      }

      this.dynamicThresholdManager = new DynamicThresholdManager(this);
      this.reloadSettings();

      // PlaceholderAPI
      if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
         new AntiLitematicaExpansion(this).register();
      }

      // bStats metrics
      if (this.getConfig().getBoolean("bstats.enabled", true)) {
         int pluginId = 31012;
         Metrics metrics = new Metrics(this, pluginId);
         metrics.addCustomChart(new SimplePie("detection_action", () -> this.settings.detection().action().name()));
      }

      // ---- Detection Logger ----
      boolean logEnabled = this.getConfig().getBoolean("detection_log.enabled", false);
      String logFile = this.getConfig().getString("detection_log.file", "detections.log");
      this.detectionLogger = new DetectionLogger(this, logEnabled, logFile);

      // ---- Stats Tracker (pure in-memory) ----
      boolean statsEnabled = this.getConfig().getBoolean("stats.enabled", true);
      this.statsTracker = new StatsTracker(statsEnabled);

      // ---- OneBot (QQ Bot) notifier ----
      if (this.settings.onebot() != null && this.settings.onebot().enabled()) {
         Settings.OneBot ob = this.settings.onebot();
         this.oneBotNotifier = new OneBotNotifier(this, ob.httpUrl(), ob.accessToken(), ob.groupId());
         this.getLogger().info("OneBot notifier enabled: " + ob.httpUrl());
      }

      // ---- Register API ----
      AntiLitematicaAPIImpl api = new AntiLitematicaAPIImpl(this);
      AntiLitematicaAPIImpl.INSTANCE = api;
      this.getServer().getServicesManager().register(
            top.chenray.antilitematica.api.AntiLitematicaAPI.class,
            api, this,
            org.bukkit.plugin.ServicePriority.Normal);
      this.getLogger().info("API registered: " + api.getClass().getName());

   }

   public void onDisable() {
      if (this.modChannelDetector != null) {
         this.modChannelDetector.shutdown();
      }

      if (this.placementGuard != null) {
         this.placementGuard.shutdown();
      }

      if (this.commandGuard != null) {
         this.commandGuard.shutdown();
      }

      if (this.protocolLibBridge != null) {
         this.protocolLibBridge.shutdown();
      }

      if (this.integrationManager != null) {
         this.integrationManager.unload();
      }

      if (this.punishmentTracker != null) {
         this.punishmentTracker.shutdown();
      }

      if (this.detectionLogger != null) {
         this.detectionLogger.close();
      }

      if (this.statsTracker != null) {
         this.statsTracker.shutdown();
      }

      this.punished.clear();
   }

   /**
    * Clear the punished set for a specific world (e.g. when a world unloads).
    */
   public void clearPunishedWorld(String worldName) {
      if (worldName != null) {
         this.punished.remove(worldName.toLowerCase());
      }
   }

   public Settings settings() {
      return this.settings;
   }

   public void reloadSettings() {
      this.reloadConfig();
      this.settings = Settings.from(this, this.getConfig());
      if (this.modChannelDetector != null) {
         this.modChannelDetector.shutdown();
      }

      if (this.placementGuard != null) {
         this.placementGuard.shutdown();
      }

      if (this.commandGuard != null) {
         this.commandGuard.shutdown();
      }

      if (this.protocolLibBridge != null) {
         this.protocolLibBridge.shutdown();
      }

      if (this.integrationManager != null) {
         this.integrationManager.unload();
      }

      if (this.punishmentTracker != null) {
         this.punishmentTracker.shutdown();
      }

      this.punished.clear();
      if (!this.settings.enabled()) {
         this.getLogger().info("Disabled by config.");
      } else {
         this.integrationManager = new IntegrationManager(this);
         this.integrationManager.load(this.settings);
         this.protocolLibBridge = new ProtocolLibBridge(this, this.settings);
         this.protocolLibBridge.start();
         this.modChannelDetector = new ModChannelDetector(this, this.settings);
         this.modChannelDetector.start();
         this.placementGuard = new PlacementGuard(this, this.settings);
         this.placementGuard.start();
         this.commandGuard = new CommandGuard(this, this.settings);
         this.commandGuard.start();
         if (this.settings.graduatedPunishment() != null && this.settings.graduatedPunishment().enabled()) {
            Settings.GraduatedPunishment gp = this.settings.graduatedPunishment();
            String storage = gp.storage();
            if ("mysql".equals(storage)) {
               this.punishmentTracker = new PunishmentTracker(this, storage, gp.windowMinutes(),
                     gp.mysqlHost(), gp.mysqlPort(), gp.mysqlDatabase(),
                     gp.mysqlUser(), gp.mysqlPassword());
            } else {
               this.punishmentTracker = new PunishmentTracker(this, storage, gp.windowMinutes());
            }
            this.graduatedPunisher = new GraduatedPunisher(this, this.settings, this.punishmentTracker);
         }
      }

   }

   /**
    * Mark a player as punished in a specific world.
    * @return true if the player was not already punished in this world
    */
   public boolean markPunished(UUID uuid) {
      return markPunished(uuid, null);
   }

   public boolean markPunished(UUID uuid, String worldName) {
      String world = worldName != null ? worldName.toLowerCase() : "_global_";
      Set<UUID> worldSet = this.punished.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet());
      return worldSet.add(uuid);
   }

   public boolean markPunished(Player player) {
      return markPunished(player.getUniqueId(), player.getWorld().getName());
   }

   /**
    * Unmark a player as punished in a specific world.
    */
   public void unmarkPunished(UUID uuid) {
      unmarkPunished(uuid, null);
   }

   public void unmarkPunished(UUID uuid, String worldName) {
      String world = worldName != null ? worldName.toLowerCase() : "_global_";
      Set<UUID> worldSet = this.punished.get(world);
      if (worldSet != null) {
         worldSet.remove(uuid);
      }
   }

   /**
    * Check if a player is punished in any world, or in a specific world.
    */
   public boolean isPunished(UUID uuid) {
      return isPunished(uuid, null);
   }

   public boolean isPunished(UUID uuid, String worldName) {
      if (worldName != null) {
         String world = worldName.toLowerCase();
         Set<UUID> worldSet = this.punished.get(world);
         if (worldSet != null && worldSet.contains(uuid)) return true;
         // Also check global
         Set<UUID> globalSet = this.punished.get("_global_");
         return globalSet != null && globalSet.contains(uuid);
      }
      // Check all worlds
      for (Set<UUID> worldSet : this.punished.values()) {
         if (worldSet.contains(uuid)) return true;
      }
      return false;
   }

   public boolean isPunished(Player player) {
      return isPunished(player.getUniqueId(), player.getWorld().getName());
   }

   public IntegrationManager getIntegrationManager() {
      return this.integrationManager;
   }

   public PunishmentTracker getPunishmentTracker() {
      return this.punishmentTracker;
   }

   public GraduatedPunisher getGraduatedPunisher() {
      return this.graduatedPunisher;
   }

   public DynamicThresholdManager getDynamicThresholdManager() {
      return this.dynamicThresholdManager;
   }

   public DetectionLogger getDetectionLogger() {
      return this.detectionLogger;
   }

   public OneBotNotifier getOneBotNotifier() {
      return this.oneBotNotifier;
   }

   public StatsTracker getStatsTracker() {
      return this.statsTracker;
   }
}
