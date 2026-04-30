package top.chenray.antilitematica.punish;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.punish.hook.AdvancedBanHook;
import top.chenray.antilitematica.punish.hook.BanPluginHook;
import top.chenray.antilitematica.punish.hook.EssentialsHook;
import top.chenray.antilitematica.punish.hook.LiteBansHook;
import top.chenray.antilitematica.punish.hook.NoOpBanHook;
import top.chenray.antilitematica.util.DiscordWebhook;
import top.chenray.antilitematica.util.Msg;

/**
 * Graduated punishment system: escalates penalties based on violation count within a time window.
 */
public final class GraduatedPunisher {
   private final AntiLitematicaPlugin plugin;
   private final Settings settings;
   private final PunishmentTracker tracker;
   private BanPluginHook hook;
   private final Map<UUID, Integer> notifiedLevel = new ConcurrentHashMap<>();

   public GraduatedPunisher(AntiLitematicaPlugin plugin, Settings settings, PunishmentTracker tracker) {
      this.plugin = plugin;
      this.settings = settings;
      this.tracker = tracker;
      this.resolveHook();
   }

   private void resolveHook() {
      Settings.GraduatedPunishment gp = this.settings.graduatedPunishment();
      if (gp == null || !gp.enabled()) {
         this.hook = new NoOpBanHook();
         return;
      }
      for (String name : gp.banPlugins()) {
         BanPluginHook candidate = createHook(name);
         if (candidate != null && candidate.isAvailable()) {
            this.hook = candidate;
            this.plugin.getLogger().info("Graduated punishment using ban plugin: " + candidate.getName());
            return;
         }
      }
      this.hook = new NoOpBanHook();
      this.plugin.getLogger().info("Graduated punishment using Bukkit native ban/kick.");
   }

   private static BanPluginHook createHook(String name) {
      String lower = name.toLowerCase(Locale.ROOT);
      if (lower.contains("litebans")) {
         return new LiteBansHook();
      } else if (lower.contains("advancedban")) {
         return new AdvancedBanHook();
      } else if (lower.contains("essentials")) {
         return new EssentialsHook();
      }
      return null;
   }

   public void punish(Player player, String channel, String why) {
      Settings.GraduatedPunishment gp = this.settings.graduatedPunishment();
      if (gp == null || !gp.enabled()) {
         return;
      }

      ViolationRecord record = this.tracker.recordViolation(player);
      int level = Math.min(record.count(), gp.levels().size());
      boolean exceeded = record.count() > gp.levels().size();

      Settings.PunishmentLevel pl = exceeded ? gp.exceedMax() : gp.levels().get(level - 1);
      if (pl == null) {
         pl = gp.exceedMax();
      }

      String reason = pl.reason()
            .replace("%player%", player.getName())
            .replace("%count%", String.valueOf(record.count()))
            .replace("%total%", String.valueOf(record.totalViolations()));

      String actionName = pl.action().toUpperCase(Locale.ROOT);
      this.plugin.getLogger().info("[GraduatedPunish] " + player.getName() + " level=" + level +
            " count=" + record.count() + " action=" + actionName + " reason=" + reason);

      // Execute action
      switch (pl.action().toLowerCase(Locale.ROOT)) {
         case "warn":
            this.hook.warn(player, Msg.color("&e[Warning] &f" + reason));
            break;
         case "kick":
            this.hook.kick(player, Msg.color(reason));
            break;
         case "tempban":
            long seconds = parseDuration(pl.duration());
            this.hook.tempBan(player, Msg.color(reason), seconds);
            break;
         case "ban":
            this.hook.ban(player, Msg.color(reason));
            break;
         default:
            this.plugin.getLogger().warning("Unknown punishment action: " + pl.action());
            break;
      }

      // Broadcast
      if (pl.broadcast()) {
         String msg = Msg.color("&c[AntiLitematica] &e" + player.getName() + " &7was punished (&f" +
               actionName + "&7) for using projection mods. &8(" + record.count() + " violations)");
         Bukkit.broadcast(msg, "bukkit.broadcast.user");
      }

      // Staff alert
      if (pl.staffAlert()) {
         String alert = Msg.color("&c[AntiLitematica] &e" + player.getName() + " &7punished (&f" +
               actionName + "&7): &f" + reason + " &8[count=" + record.count() + ",total=" + record.totalViolations() + "]");
         for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("antilitematica.notify")) {
               online.sendMessage(alert);
            }
         }
      }

      // Integration flag
      if (this.settings.integration().enabled()) {
         Settings.Integration integ = this.settings.integration();
         this.plugin.getIntegrationManager().flag(player, integ.checkPrefix() + ":graduated_" + pl.action(),
               integ.violationLevel(), "graduated punishment level=" + level + " action=" + actionName);
      }

      // Discord notification
      sendDiscordNotification(player, channel, why, actionName, reason);
   }

   public void reload() {
      this.resolveHook();
      this.notifiedLevel.clear();
   }

   private long parseDuration(String duration) {
      if (duration == null || duration.isEmpty() || duration.equals("0")) {
         return 0;
      }
      duration = duration.trim().toLowerCase(Locale.ROOT);
      try {
         if (duration.endsWith("d")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 86400L;
         } else if (duration.endsWith("h")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 3600L;
         } else if (duration.endsWith("m")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 60L;
         } else if (duration.endsWith("s")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1));
         } else {
            return Long.parseLong(duration);
         }
      } catch (NumberFormatException e) {
         this.plugin.getLogger().warning("Invalid duration format: " + duration);
         return 0;
      }
   }

   private void sendDiscordNotification(Player player, String channel, String why, String action, String reason) {
      Settings.Discord dc = this.settings.discord();
      if (dc != null && dc.enabled() && dc.webhookUrl() != null && !dc.webhookUrl().isEmpty()) {
         if (dc.notifyOnPunish()) {
            DiscordWebhook webhook = new DiscordWebhook(
                  this.plugin,
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
            webhook.sendDetection(player.getName(), player.getUniqueId().toString(), channel,
                  reason != null ? reason : "Unknown", action);
         }
      }
   }
}