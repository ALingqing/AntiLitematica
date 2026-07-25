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
import top.chenray.antilitematica.gui.AdminGUI;
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
   private AdminGUI adminGUI;

   @Override
   public void onEnable() {
      logAsciiArt();
      this.saveDefaultConfig();
      new ConfigMigrator(this).migrate();
      saveBundledLangFiles();
      this.getServer().getPluginManager().registerEvents(new PunishStateListener(this), this);

      PluginCommand cmd = this.getCommand("antilitematica");
      if (cmd != null) {
         AntiLitematicaCommand executor = new AntiLitematicaCommand(this);
         cmd.setExecutor(executor);
         cmd.setTabCompleter(executor);
      }

      this.dynamicThresholdManager = new DynamicThresholdManager(this);
      this.adminGUI = new AdminGUI(this);
      this.getServer().getPluginManager().registerEvents(this.adminGUI, this);

      this.reloadSettings();

      // PlaceholderAPI
      if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
         new AntiLitematicaExpansion(this).register();
      }

      // bStats
      if (this.getConfig().getBoolean("bstats.enabled", true)) {
         Metrics metrics = new Metrics(this, 31012);
         metrics.addCustomChart(new SimplePie("detection_action",
               () -> this.settings != null ? this.settings.detection().action().name() : "NONE"));
      }

      // API
      AntiLitematicaAPIImpl api = new AntiLitematicaAPIImpl(this);
      AntiLitematicaAPIImpl.INSTANCE = api;
      this.getServer().getServicesManager().register(
            top.chenray.antilitematica.api.AntiLitematicaAPI.class,
            api, this, org.bukkit.plugin.ServicePriority.Normal);
      this.getLogger().info("API registered");
   }

   @Override
   public void onDisable() {
      shutdownAll();
      this.punished.clear();
   }

   public void clearPunishedWorld(String worldName) {
      if (worldName != null) this.punished.remove(worldName.toLowerCase());
   }

   /**
    * Get the set of punished UUIDs for a specific world (exposed for API).
    */
   public Set<UUID> getPunishedWorldSet(String worldName) {
      return worldName != null ? this.punished.get(worldName.toLowerCase()) : null;
   }

   public void reloadSettings() {
      shutdownAll();
      this.reloadConfig();
      this.settings = Settings.from(this, this.getConfig());

      // ---- Detection Logger ----
      boolean logEnabled = this.getConfig().getBoolean("detection_log.enabled", false);
      String logFile = this.getConfig().getString("detection_log.file", "detections.log");
      this.detectionLogger = new DetectionLogger(this, logEnabled, logFile);

      // ---- Stats Tracker ----
      boolean statsEnabled = this.getConfig().getBoolean("stats.enabled", true);
      this.statsTracker = new StatsTracker(statsEnabled);

      if (this.settings.onebot() != null && this.settings.onebot().enabled()) {
         Settings.OneBot ob = this.settings.onebot();
         this.oneBotNotifier = new OneBotNotifier(this, ob.httpUrl(), ob.accessToken(), ob.groupId());
         this.getLogger().info("OneBot notifier enabled: " + ob.httpUrl());
      }

      if (!this.settings.enabled()) {
         this.getLogger().info("Disabled by config.");
         return;
      }

      this.dynamicThresholdManager.reload();
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

   private void shutdownAll() {
      if (this.modChannelDetector != null) { this.modChannelDetector.shutdown(); }
      if (this.placementGuard != null) { this.placementGuard.shutdown(); }
      if (this.commandGuard != null) { this.commandGuard.shutdown(); }
      if (this.protocolLibBridge != null) { this.protocolLibBridge.shutdown(); }
      if (this.integrationManager != null) { this.integrationManager.unload(); }
      if (this.punishmentTracker != null) { this.punishmentTracker.shutdown(); }
      if (this.detectionLogger != null) { this.detectionLogger.close(); }
      if (this.statsTracker != null) { this.statsTracker.shutdown(); }
      this.oneBotNotifier = null;
      this.punished.clear();
   }

   // ========================== Punished State (World-Aware) ==========================

   public boolean markPunished(Player player) {
      String world = player.getWorld() != null ? player.getWorld().getName() : null;
      return markPunished(player.getUniqueId(), world);
   }

   public boolean markPunished(UUID uuid, String worldName) {
      String world = worldName != null ? worldName.toLowerCase() : "_global_";
      return this.punished.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet()).add(uuid);
   }

   public void unmarkPunished(UUID uuid) {
      this.punished.values().forEach(s -> s.remove(uuid));
   }

   public void unmarkPunished(UUID uuid, String worldName) {
      if (worldName == null) { unmarkPunished(uuid); return; }
      Set<UUID> worldSet = this.punished.get(worldName.toLowerCase());
      if (worldSet != null) worldSet.remove(uuid);
   }

   public boolean isPunished(UUID uuid) {
      return this.punished.values().stream().anyMatch(s -> s.contains(uuid));
   }

   public boolean isPunished(Player player) {
      String world = player.getWorld() != null ? player.getWorld().getName().toLowerCase() : null;
      if (world != null) {
         Set<UUID> worldSet = this.punished.get(world);
         if (worldSet != null && worldSet.contains(player.getUniqueId())) return true;
      }
      Set<UUID> globalSet = this.punished.get("_global_");
      return globalSet != null && globalSet.contains(player.getUniqueId());
   }

   // ========================== Getters ==========================

   public Settings settings() { return this.settings; }
   public IntegrationManager getIntegrationManager() { return this.integrationManager; }
   public PunishmentTracker getPunishmentTracker() { return this.punishmentTracker; }
   public GraduatedPunisher getGraduatedPunisher() { return this.graduatedPunisher; }
   public DynamicThresholdManager getDynamicThresholdManager() { return this.dynamicThresholdManager; }
   public DetectionLogger getDetectionLogger() { return this.detectionLogger; }
   public OneBotNotifier getOneBotNotifier() { return this.oneBotNotifier; }
   public StatsTracker getStatsTracker() { return this.statsTracker; }
   public AdminGUI getAdminGUI() { return this.adminGUI; }

   // ========================== Helpers ==========================

   private void logAsciiArt() {
      this.getLogger().info("\n" +
            "   █████╗ ███╗   ██╗████████╗██╗██╗     ██╗████████╗███████╗███╗   ███╗ █████╗ ████████╗██╗ █████╗   \n" +
            "  ██╔══██╗████╗  ██║╚══██╔══╝██║██║     ██║╚══██╔══╝██╔════╝████╗ ████║██╔══██╗╚══██╔══╝██║██╔══██╗  \n" +
            "  ███████║██╔██╗ ██║   ██║   ██║██║     ██║   ██║   █████╗  ██╔████╔██║███████║   ██║   ██║███████║  \n" +
            "  ██╔══██║██║╚██╗██║   ██║   ██║██║     ██║   ██║   ██╔══╝  ██║╚██╔╝██║██╔══██║   ██║   ██║██╔══██║  \n" +
            "  ██║  ██║██║ ╚████║   ██║   ██║███████╗██║   ██║   ███████╗██║ ╚═╝ ██║██║  ██║   ██║   ██║██║  ██║  \n" +
            "  ╚═╝  ╚═╝╚═╝  ╚═══╝   ╚═╝   ╚═╝╚══════╝╚═╝   ╚═╝   ╚══════╝╚═╝     ╚═╝╚═╝  ╚═╝   ╚═╝   ╚═╝╚═╝  ╚═╝  \n");
      this.getLogger().info("AntiLitematica v" + this.getDescription().getVersion() + " enabled | Author: ALingqing_");
   }

   private void saveBundledLangFiles() {
      File langDir = new File(this.getDataFolder(), "lang");
      if (!langDir.exists()) langDir.mkdirs();
      String[] bundledLangs = {"messages.yml", "messages_zh_CN.yml", "messages_en_US.yml", "messages_zh_TW.yml"};
      for (String name : bundledLangs) {
         String resourcePath = "lang/" + name;
         if (this.getResource(resourcePath) != null) {
            File target = new File(langDir, name);
            if (!target.exists()) {
               try (java.io.InputStream in = this.getResource(resourcePath)) {
                  if (in != null) java.nio.file.Files.copy(in, target.toPath());
               } catch (java.io.IOException e) {
                  this.getLogger().warning("Failed to save " + name + ": " + e.getMessage());
               }
            }
         }
      }
   }
}
