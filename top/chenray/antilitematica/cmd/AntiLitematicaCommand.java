package top.chenray.antilitematica.cmd;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.punish.ViolationRecord;

public final class AntiLitematicaCommand implements CommandExecutor {
   private final AntiLitematicaPlugin plugin;

   public AntiLitematicaCommand(AntiLitematicaPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (args.length == 0) {
         return false;
      }
      switch (args[0].toLowerCase()) {
         case "reload":
            this.plugin.reloadSettings();
            Settings.Messages msg = this.plugin.settings().messages();
            String reloadMsg = msg != null && msg.reload() != null ? msg.reload() : "&aAntiLitematica configuration reloaded.";
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', reloadMsg));
            return true;
         case "gui":
            if (!(sender instanceof Player player)) {
               sender.sendMessage(ChatColor.RED + "Only players can open the GUI.");
               return true;
            }
            if (!player.hasPermission("antilitematica.admin")) {
               player.sendMessage(ChatColor.RED + "You don't have permission.");
               return true;
            }
            this.plugin.getConfigGui().openMainPage(player);
            return true;
         case "status":
            Settings s = this.plugin.settings();
            Settings.Signals sig = s.detection().signals();
            String gray = String.valueOf(ChatColor.GRAY);
            sender.sendMessage(gray + "enabled=" + s.enabled() + " detection=" + s.detection().enabled() + " detectionAction=" + String.valueOf(s.detection().action()) + " blockServux=" + s.detection().blockServux() + " signals={servuxMeta=" + (sig == null || sig.servuxMetadata() == null || sig.servuxMetadata().enabled()) + ", easyPlace=" + (sig != null && sig.easyPlace() != null && sig.easyPlace().enabled()) + ", nbtQuery=" + (sig != null && sig.nbtQuery() != null && sig.nbtQuery().enabled()) + "} anti_printer=" + s.antiPrinter().enabled() + " command_guard=" + s.commandGuard().enabled() + " graduated=" + (s.graduatedPunishment() != null && s.graduatedPunishment().enabled()));
            return true;
         case "reset":
            if (args.length < 2) {
               sender.sendMessage(ChatColor.RED + "Usage: /antilitematica reset <player>");
               return true;
            }
            return handleReset(sender, args[1]);
         case "history":
            if (args.length < 2) {
               sender.sendMessage(ChatColor.RED + "Usage: /antilitematica history <player>");
               return true;
            }
            return handleHistory(sender, args[1]);
         default:
            return false;
      }
   }

   private boolean handleReset(CommandSender sender, String targetName) {
      if (this.plugin.getPunishmentTracker() == null) {
         sender.sendMessage(ChatColor.RED + "Graduated punishment system is not enabled.");
         return true;
      }
      @SuppressWarnings("deprecation")
      OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
      if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
         sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
         return true;
      }
      UUID uuid = target.getUniqueId();
      this.plugin.getPunishmentTracker().resetPlayer(uuid);
      sender.sendMessage(ChatColor.GREEN + "Reset violation record for " + targetName);
      return true;
   }

   private boolean handleHistory(CommandSender sender, String targetName) {
      if (this.plugin.getPunishmentTracker() == null) {
         sender.sendMessage(ChatColor.RED + "Graduated punishment system is not enabled.");
         return true;
      }
      @SuppressWarnings("deprecation")
      OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
      if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
         sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
         return true;
      }
      UUID uuid = target.getUniqueId();
      ViolationRecord record = this.plugin.getPunishmentTracker().getRecord(uuid);
      if (record == null) {
         sender.sendMessage(ChatColor.YELLOW + targetName + " has no violation record.");
         return true;
      }
      sender.sendMessage(ChatColor.GOLD + "=== Violation History: " + targetName + " ===");
      sender.sendMessage(ChatColor.GRAY + "Current window count: " + ChatColor.WHITE + record.count());
      sender.sendMessage(ChatColor.GRAY + "Total violations: " + ChatColor.WHITE + record.totalViolations());
      sender.sendMessage(ChatColor.GRAY + "First violation: " + ChatColor.WHITE + formatTime(record.firstViolation()));
      sender.sendMessage(ChatColor.GRAY + "Last violation: " + ChatColor.WHITE + formatTime(record.lastViolation()));
      return true;
   }

   private static String formatTime(long millis) {
      java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      return sdf.format(new java.util.Date(millis));
   }
}
