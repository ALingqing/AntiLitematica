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
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.util.DiscordWebhook;
import top.chenray.antilitematica.util.Msg;

public final class Punisher {
   private Punisher() {
   }

   public static void punishDetection(AntiLitematicaPlugin plugin, Settings settings, Player player, String channel, String why) {
      // ---- Whitelist check ----
      if (isWhitelisted(plugin, settings, player)) {
         plugin.getLogger().info("[Whitelist] " + player.getName() + " triggered detection (" + why + ") but is whitelisted — logging only.");
         return;
      }
      // Use graduated punishment system if enabled
      if (settings.graduatedPunishment() != null && settings.graduatedPunishment().enabled()) {
         plugin.getGraduatedPunisher().punish(player, channel, why);
         return;
      }

      // Fallback to legacy single-action logic
      Settings.Detection det = settings.detection();
      if (det != null && det.enabled()) {
         String reason = det.reason() != null ? det.reason() : "Forbidden client mod detected.";
         String var10000 = Msg.prefix(settings);
         String kickMsg = Msg.color(var10000 + settings.messages().kick());
         if (settings.integration().enabled()) {
            Settings.Integration integ = settings.integration();
            plugin.getIntegrationManager().flag(player, integ.checkPrefix() + ":" + channel, integ.violationLevel(), why);
         }

         boolean punishExecuted = false;
         String actionName = "LOG";
         switch (det.action()) {
            case LOG:
               plugin.getLogger().info("Detected blocked channel '" + channel + "' from " + player.getName() + " via " + why);
               actionName = "LOG";
               break;
            case KICK:
               plugin.getLogger().info("Kicking " + player.getName() + " (blocked channel '" + channel + "' via " + why + ")");
               runCommands(plugin, det.commands(), player, channel, why, reason);
               punishExecuted = true;
               actionName = "KICK";
               Bukkit.getScheduler().runTask(plugin, () -> {
                  if (player.isOnline() && !player.hasPermission("antilitematica.bypass")) {
                     player.kickPlayer(kickMsg);
                  }

               });
               break;
            case BAN:
               plugin.getLogger().info("Banning " + player.getName() + " (blocked channel '" + channel + "' via " + why + ")");
               runCommands(plugin, det.commands(), player, channel, why, reason);
               punishExecuted = true;
               actionName = "BAN";
               Bukkit.getScheduler().runTask(plugin, () -> {
                  if (player.isOnline() && !player.hasPermission("antilitematica.bypass")) {
                     Bukkit.getBanList(Type.NAME).addBan(player.getName(), reason, (Date)null, plugin.getName());
                     player.kickPlayer(kickMsg);
                  }

               });
               break;
            case COMMANDS:
               plugin.getLogger().info("Detected blocked channel '" + channel + "' from " + player.getName() + " via " + why + " (commands)");
               runCommands(plugin, det.commands(), player, channel, why, reason);
               punishExecuted = true;
               actionName = "COMMANDS";
               if (det.kickAfterCommands()) {
                  Bukkit.getScheduler().runTask(plugin, () -> {
                     if (player.isOnline() && !player.hasPermission("antilitematica.bypass")) {
                        player.kickPlayer(kickMsg);
                     }

                  });
               }
         }

         sendDiscordNotification(plugin, settings, player, channel, why, actionName, punishExecuted);
      }

   }

   private static void sendDiscordNotification(AntiLitematicaPlugin plugin, Settings settings, Player player, String channel, String why, String action, boolean punishExecuted) {
      Settings.Discord dc = settings.discord();
      if (dc != null && dc.enabled() && dc.webhookUrl() != null && !dc.webhookUrl().isEmpty()) {
         if (!punishExecuted || dc.notifyOnPunish()) {
            if (punishExecuted || dc.notifyOnDetection()) {
               // Compatible with newer DiscordWebhook constructor parameters
               DiscordWebhook webhook = new DiscordWebhook(
                   plugin,
                   dc.webhookUrl(),
                   dc.username(),
                   dc.avatarUrl() != null ? dc.avatarUrl() : "",
                   dc.embedTitle(),
                   dc.embedColor(),
                   dc.footerText() != null ? dc.footerText() : "",
                   dc.proxyHost() != null ? dc.proxyHost() : "",
                   dc.proxyPort(),
                   dc.proxyUsername() != null ? dc.proxyUsername() : "",
                   dc.proxyPassword() != null ? dc.proxyPassword() : ""
               );
               String reason = why != null ? why : "Unknown";
               webhook.sendDetection(player.getName(), player.getUniqueId().toString(), channel, reason, action);
            }
         }
      }
   }

   /**
    * Check if a player is on the violation whitelist.
    * If whitelist is enabled and player is listed, only log the detection.
    */
   private static boolean isWhitelisted(AntiLitematicaPlugin plugin, Settings settings, Player player) {
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