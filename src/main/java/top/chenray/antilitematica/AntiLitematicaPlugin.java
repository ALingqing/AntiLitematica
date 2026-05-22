package top.chenray.antilitematica;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import top.chenray.antilitematica.cmd.AntiLitematicaCommand;
import top.chenray.antilitematica.config.ConfigMigrator;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.detection.ModChannelDetector;
import top.chenray.antilitematica.gui.ConfigGui;
import top.chenray.antilitematica.gui.GuiInputManager;
import top.chenray.antilitematica.gui.GuiListener;
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
import top.chenray.antilitematica.build.AutoBuildManager;
import top.chenray.antilitematica.update.UpdateChecker;
import top.chenray.antilitematica.util.DetectionLogger;
import top.chenray.antilitematica.util.OneBotNotifier;
import top.chenray.antilitematica.web.DashboardServer;

public final class AntiLitematicaPlugin extends JavaPlugin {
   private volatile Settings settings;
   private ModChannelDetector modChannelDetector;
   private PlacementGuard placementGuard;
   private CommandGuard commandGuard;
   private ProtocolLibBridge protocolLibBridge;
   private IntegrationManager integrationManager;
   private PunishmentTracker punishmentTracker;
   private GraduatedPunisher graduatedPunisher;
   private final Set<UUID> punished = ConcurrentHashMap.newKeySet();
   private ConfigGui configGui;
   private GuiInputManager guiInputManager;
   private GuiListener guiListener;
   private DynamicThresholdManager dynamicThresholdManager;
   private UpdateChecker updateChecker;
   private DashboardServer dashboardServer;
   private AutoBuildManager autoBuildManager;
   private DetectionLogger detectionLogger;
   private OneBotNotifier oneBotNotifier;

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
      this.saveResource("messages.yml", false);
      this.guiInputManager = new GuiInputManager(this);
      this.configGui = new ConfigGui(this, this.guiInputManager);
      this.guiListener = new GuiListener(this, this.configGui, this.guiInputManager);
      this.getServer().getPluginManager().registerEvents(this.guiListener, this);
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

      // Auto-update checker
      this.updateChecker = new UpdateChecker(this);
      this.updateChecker.start();

      // Web Dashboard
      if (this.settings.webDashboard() != null && this.settings.webDashboard().enabled()) {
         Settings.WebDashboard wd = this.settings.webDashboard();
         this.dashboardServer = new DashboardServer(this, wd.port(), wd.password(), wd.locale());
         this.dashboardServer.start();
      }

      // Auto Build Manager
      this.autoBuildManager = new AutoBuildManager(this);
      if (this.settings.autoBuild() != null && this.settings.autoBuild().enabled()) {
         this.autoBuildManager.start();
      }

      // ---- Detection Logger ----
      boolean logEnabled = this.getConfig().getBoolean("detection_log.enabled", false);
      String logFile = this.getConfig().getString("detection_log.file", "detections.log");
      this.detectionLogger = new DetectionLogger(this, logEnabled, logFile);

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

      if (this.updateChecker != null) {
         // UpdateChecker listeners auto-cleaned by HandlerList
      }

      if (this.detectionLogger != null) {
         this.detectionLogger.close();
      }

      if (this.autoBuildManager != null) {
         this.autoBuildManager.stop();
      }

      if (this.dashboardServer != null) {
         this.dashboardServer.stop();
      }

      this.punished.clear();
      if (this.guiInputManager != null) {
         this.guiInputManager.shutdown();
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

      // Reschedule auto-update after config reload
      if (this.autoBuildManager != null) {
         this.autoBuildManager.reschedule();
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

   public boolean markPunished(UUID uuid) {
      return this.punished.add(uuid);
   }

   public void unmarkPunished(UUID uuid) {
      this.punished.remove(uuid);
   }

   public boolean isPunished(UUID uuid) {
      return this.punished.contains(uuid);
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

   public ConfigGui getConfigGui() {
      return this.configGui;
   }

   public GuiInputManager getGuiInputManager() {
      return this.guiInputManager;
   }

   public DynamicThresholdManager getDynamicThresholdManager() {
      return this.dynamicThresholdManager;
   }

   public UpdateChecker getUpdateChecker() {
      return this.updateChecker;
   }

   public DashboardServer getDashboardServer() {
      return this.dashboardServer;
   }

   public AutoBuildManager getAutoBuildManager() {
      return this.autoBuildManager;
   }

   public DetectionLogger getDetectionLogger() {
      return this.detectionLogger;
   }

   public OneBotNotifier getOneBotNotifier() {
      return this.oneBotNotifier;
   }
}
