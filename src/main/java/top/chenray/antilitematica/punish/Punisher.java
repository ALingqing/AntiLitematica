package top.chenray.antilitematica.punish;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.BanList.Type;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.api.event.DetectionEvent;
import top.chenray.antilitematica.api.event.PunishmentEvent;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.util.DetectionLogger;
import top.chenray.antilitematica.util.DiscordWebhook;
import top.chenray.antilitematica.util.Msg;
import top.chenray.antilitematica.util.OneBotNotifier;
import top.chenray.antilitematica.util.StatsTracker;

public final class Punisher {
   private Punisher() {
   }

   public static void punishDetection(AntiLitematicaPlugin plugin, Settings settings, Player player, String channel, String why) {
      // ---- Fire DetectionEvent (cancellable) ----
      DetectionEvent detectionEvent = new DetectionEvent(player, channel, why,
            "printer".equals(channel) ? DetectionEvent.DetectionType.PRINTER
            : DetectionEvent.DetectionType.SIGNAL);
      Bukkit.getPluginManager().callEvent(detectionEvent);
      if (detectionEvent.isCancelled()) {
         plugin.getLogger().info("[API] Detection cancelled by event for " + player.getName());
         return;
      }

      // ---- Whitelist check ----
      if (isWhitelisted(settings, player)) {
         plugin.getLogger().info("[Whitelist] " + player.getName() + " triggered detection (" + why + ") but is whitelisted — logging only.");
         return;
      }

      // Use graduated punishment system if enabled
      if (isGraduatedEnabled(settings)) {
         plugin.getGraduatedPunisher().punish(player, channel, why);
         return;
      }

      // Fallback to legacy single-action logic
      executeLegacyAction(plugin, settings, player, channel, why);
   }

   /** Check if graduated punishment is configured and enabled. */
   private static boolean isGraduatedEnabled(Settings settings) {
      Settings.GraduatedPunishment gp = settings.graduatedPunishment();
      return gp != null && gp.enabled();
   }

   /** Execute the legacy single-action (LOG/KICK/BAN/COMMANDS) fallback. */
   private static void executeLegacyAction(AntiLitematicaPlugin plugin, Settings settings,
                                           Player player, String channel, String why) {
      Settings.Detection det = settings.detection();
      if (det == null || !det.enabled()) return;

      String reason = (det.reason() != null) ? det.reason() : "Forbidden client mod detected.";
      String kickMsg = Msg.color(Msg.prefix(settings) + settings.messages().kick());
      flagAntiCheat(plugin, settings, player, channel, why);

      boolean punishExecuted = false;
      String actionName = "LOG";

      switch (det.action()) {
         case LOG:
            plugin.getLogger().info("Detected blocked channel '" + channel + "' from " + player.getName() + " via " + why);
            break;
         case WARN:
            actionName = "WARN";
            // 仅发送警告消息给玩家，不执行踢出或封禁
            plugin.getLogger().info("Warning " + player.getName() + " (blocked channel '" + channel + "' via " + why + ")");
            String warnMsg = Msg.color(Msg.prefix(settings) + (det.reason() != null ? det.reason() : "Forbidden client mod detected."));
            Bukkit.getScheduler().runTask(plugin, () -> {
               if (player.isOnline() && !player.hasPermission("antilitematica.bypass")) {
                  player.sendMessage(warnMsg);
               }
            });
            break;
         case KICK:
            actionName = "KICK";
            punishExecuted = true;
            plugin.getLogger().info("Kicking " + player.getName() + " (blocked channel '" + channel + "' via " + why + ")");
            runCommands(plugin, det.commands(), player, channel, why, reason);
            scheduleKick(plugin, player, kickMsg);
            break;
         case BAN:
            actionName = "BAN";
            punishExecuted = true;
            plugin.getLogger().info("Banning " + player.getName() + " (blocked channel '" + channel + "' via " + why + ")");
            runCommands(plugin, det.commands(), player, channel, why, reason);
            scheduleBan(plugin, player, reason, kickMsg);
            break;
         case COMMANDS:
            actionName = "COMMANDS";
            punishExecuted = true;
            plugin.getLogger().info("Detected blocked channel '" + channel + "' from " + player.getName() + " via " + why + " (commands)");
            runCommands(plugin, det.commands(), player, channel, why, reason);
            if (det.kickAfterCommands()) {
               scheduleKick(plugin, player, kickMsg);
            }
            break;
      }

      sendNotifications(plugin, settings, player, channel, why, actionName, punishExecuted);
      trackStats(plugin, punishExecuted);
      logDetection(plugin, player, channel, reason, actionName, why);
      firePunishmentEvent(player, channel, reason, actionName);
   }

   /** Flag player in anti-cheat integration if enabled. */
   private static void flagAntiCheat(AntiLitematicaPlugin plugin, Settings settings,
                                     Player player, String channel, String why) {
      if (settings.integration().enabled()) {
         Settings.Integration integ = settings.integration();
         plugin.getIntegrationManager().flag(player,
               integ.checkPrefix() + ":" + channel, integ.violationLevel(), why);
      }
   }

   /** Schedule a kick on the main thread. */
   private static void scheduleKick(AntiLitematicaPlugin plugin, Player player, String kickMsg) {
      Bukkit.getScheduler().runTask(plugin, () -> {
         if (player.isOnline() && !player.hasPermission("antilitematica.bypass")) {
            player.kickPlayer(kickMsg);
         }
      });
   }

   /** Schedule a ban + kick on the main thread. */
   private static void scheduleBan(AntiLitematicaPlugin plugin, Player player,
                                    String reason, String kickMsg) {
      Bukkit.getScheduler().runTask(plugin, () -> {
         if (player.isOnline() && !player.hasPermission("antilitematica.bypass")) {
            Bukkit.getBanList(Type.NAME).addBan(player.getName(), reason, null, plugin.getName());
            player.kickPlayer(kickMsg);
         }
      });
   }

   /** Track detection/punishment statistics. */
   private static void trackStats(AntiLitematicaPlugin plugin, boolean punishExecuted) {
      StatsTracker stats = plugin.getStatsTracker();
      if (stats != null) {
         stats.recordDetection();
         if (punishExecuted) stats.recordPunishment();
      }
   }

   /** Write to the dedicated detection log file. */
   private static void logDetection(AntiLitematicaPlugin plugin, Player player,
                                     String channel, String reason, String actionName, String why) {
      DetectionLogger detLog = plugin.getDetectionLogger();
      if (detLog != null) {
         detLog.log(player.getName(), player.getUniqueId().toString(), channel, reason, actionName, why);
      }
   }

   /** Fire the post-punishment event. */
   private static void firePunishmentEvent(Player player, String channel,
                                            String reason, String actionName) {
      try {
         PunishmentEvent.PunishmentAction pa = parsePunishmentAction(actionName);
         PunishmentEvent punishmentEvent = new PunishmentEvent(player, channel, reason, pa, 0, "legacy");
         Bukkit.getPluginManager().callEvent(punishmentEvent);
      } catch (Exception ignored) {
         // event listener errors must not break punishment logic
      }
   }

   private static PunishmentEvent.PunishmentAction parsePunishmentAction(String action) {
      try {
         return PunishmentEvent.PunishmentAction.valueOf(action.toUpperCase(Locale.ROOT));
      } catch (Exception e) {
         return PunishmentEvent.PunishmentAction.LOG;
      }
   }

   /**
    * Send Discord webhook and/or OneBot (QQ) notifications.
    * DiscordWebhook is created on demand (lazily) to avoid construction when Discord is disabled.
    */
   private static void sendNotifications(AntiLitematicaPlugin plugin, Settings settings,
                                          Player player, String channel, String why,
                                          String action, boolean punishExecuted) {
      String reason = why != null ? why : "Unknown";
      sendDiscordIfNeeded(plugin, settings, player, channel, reason, action, punishExecuted);
      sendOneBotIfNeeded(plugin, player, reason, action);
   }

   private static void sendDiscordIfNeeded(AntiLitematicaPlugin plugin, Settings settings,
                                            Player player, String channel, String reason,
                                            String action, boolean punishExecuted) {
      Settings.Discord dc = settings.discord();
      if (dc == null || !dc.enabled() || dc.webhookUrl() == null || dc.webhookUrl().isEmpty()) return;
      if (!punishExecuted && !dc.notifyOnDetection()) return;
      if (punishExecuted && !dc.notifyOnPunish()) return;

      DiscordWebhook webhook = new DiscordWebhook(
          plugin,
          dc.webhookUrl(), dc.username(),
          dc.avatarUrl() != null ? dc.avatarUrl() : "",
          dc.embedTitle(), dc.embedColor(),
          dc.footerText() != null ? dc.footerText() : "",
          dc.proxyHost() != null ? dc.proxyHost() : "",
          dc.proxyPort(),
          dc.proxyUsername() != null ? dc.proxyUsername() : "",
          dc.proxyPassword() != null ? dc.proxyPassword() : ""
      );
      webhook.sendDetection(player.getName(), player.getUniqueId().toString(), channel, reason, action);
   }

   private static void sendOneBotIfNeeded(AntiLitematicaPlugin plugin, Player player,
                                           String reason, String action) {
      OneBotNotifier oneBot = plugin.getOneBotNotifier();
      if (oneBot != null) {
         oneBot.sendDetection(player.getName(), reason, action);
      }
   }

   /**
    * Check if a player is on the violation whitelist.
    * If whitelist is enabled and player is listed, only log the detection.
    */
   private static boolean isWhitelisted(Settings settings, Player player) {
      Settings.Whitelist wl = settings.whitelist();
      if (wl == null || !wl.enabled()) return false;
      if (!wl.isLogOnly()) return false;
      return wl.players().contains(player.getName().toLowerCase(Locale.ROOT));
   }

   private static void runCommands(AntiLitematicaPlugin plugin, List<String> commands, Player player, String channel, String why, String reason) {
      if (commands != null && !commands.isEmpty()) {
         UUID uuid = player.getUniqueId();
         CommandSender console = Bukkit.getConsoleSender();
         Bukkit.getScheduler().runTask(plugin, () -> {
            for(String cmd : commands) {
               if (cmd != null) {
                  String c = cmd.replace("%player%", player.getName()).replace("%uuid%", uuid.toString()).replace("%reason%", reason).replace("%channel%", channel == null ? "" : channel).replace("%why%", why == null ? "" : why);
                  c = c.trim();
                  if (!c.isEmpty()) {
                     if (c.startsWith("/")) {
                        c = c.substring(1);
                     }

                     Bukkit.dispatchCommand(console, c);
                  }
               }
            }

         });
      }

   }
}