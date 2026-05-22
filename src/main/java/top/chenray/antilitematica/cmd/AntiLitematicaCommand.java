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
         case "update":
            return handleUpdate(sender);
         case "build":
            return handleBuild(sender);
         case "whitelist":
            return handleWhitelist(sender, args);
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

   private boolean handleUpdate(CommandSender sender) {
      if (this.plugin.getUpdateChecker() == null) {
         sender.sendMessage(ChatColor.RED + "Update checker not available.");
         return true;
      }
      if (this.plugin.getUpdateChecker().isUpdateAvailable()) {
         sender.sendMessage(ChatColor.GREEN + "Update available: v" + this.plugin.getUpdateChecker().getLatestVersion()
               + " (current: v" + this.plugin.getDescription().getVersion() + ")");
         sender.sendMessage(ChatColor.GRAY + "Download: " + ChatColor.AQUA + this.plugin.getUpdateChecker().getLatestDownloadUrl());
      } else {
         sender.sendMessage(ChatColor.GREEN + "You are running the latest version (v"
               + this.plugin.getDescription().getVersion() + ").");
         // Force re-check
         this.plugin.getUpdateChecker().checkAsync();
      }
      return true;
   }

   private boolean handleBuild(CommandSender sender) {
      Settings.AutoBuild ab = this.plugin.settings().autoBuild();
      if (ab == null || !ab.enabled()) {
         sender.sendMessage(ChatColor.RED + "Auto-update is not enabled in config.yml.");
         return true;
      }
      if (ab.outputPath() == null || ab.outputPath().isEmpty()) {
         sender.sendMessage(ChatColor.RED + "Auto-update output_path is not configured.");
         return true;
      }
      sender.sendMessage(ChatColor.GRAY + "Checking for updates on GitHub...");
      this.plugin.getAutoBuildManager().checkUpdateAsync().thenAccept(version -> {
         if (version == null) {
            sender.sendMessage(ChatColor.RED + "Failed to check for updates. See console for details.");
            return;
         }
         String current = this.plugin.getDescription().getVersion();
         if (version.equals(current)) {
            sender.sendMessage(ChatColor.GREEN + "Already at the latest version (v" + current + ").");
            return;
         }
         sender.sendMessage(ChatColor.GRAY + "New version found: v" + version + " (current: v" + current + "). Downloading...");
         this.plugin.getAutoBuildManager().downloadLatestAsync().thenAccept(success -> {
            if (success) {
               sender.sendMessage(ChatColor.GREEN + "Downloaded v" + version + " successfully!");
               sender.sendMessage(ChatColor.GRAY + "Run &e/plugman reload AntiLitematica&7 or restart server to apply.");
            } else {
               sender.sendMessage(ChatColor.RED + "Download failed. Check server console for details.");
            }
         });
      });
      return true;
   }

   private boolean handleWhitelist(CommandSender sender, String[] args) {
      if (args.length < 2) {
         sender.sendMessage(ChatColor.GOLD + "=== AntiLitematica Whitelist ===");
         sender.sendMessage(ChatColor.GRAY + "/al whitelist list" + ChatColor.WHITE + " — List whitelisted players");
         sender.sendMessage(ChatColor.GRAY + "/al whitelist add <player>" + ChatColor.WHITE + " — Add player to whitelist");
         sender.sendMessage(ChatColor.GRAY + "/al whitelist remove <player>" + ChatColor.WHITE + " — Remove player from whitelist");
         return true;
      }
      Settings settings = this.plugin.settings();
      Settings.Whitelist wl = settings.whitelist();
      if (wl == null) {
         sender.sendMessage(ChatColor.RED + "Whitelist config not available.");
         return true;
      }
      java.util.Set<String> players = new java.util.LinkedHashSet<>(wl.players());
      String sub = args[1].toLowerCase();
      switch (sub) {
         case "list":
            if (players.isEmpty()) {
               sender.sendMessage(ChatColor.YELLOW + "Whitelist is empty.");
            } else {
               sender.sendMessage(ChatColor.GOLD + "=== Whitelisted Players (" + players.size() + ") ===");
               for (String name : players) {
                  sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + name);
               }
            }
            return true;
         case "add":
            if (args.length < 3) {
               sender.sendMessage(ChatColor.RED + "Usage: /al whitelist add <player>");
               return true;
            }
            players.add(args[2].toLowerCase());
            saveWhitelist(players);
            sender.sendMessage(ChatColor.GREEN + "Added " + args[2] + " to whitelist.");
            return true;
         case "remove":
            if (args.length < 3) {
               sender.sendMessage(ChatColor.RED + "Usage: /al whitelist remove <player>");
               return true;
            }
            if (players.remove(args[2].toLowerCase())) {
               saveWhitelist(players);
               sender.sendMessage(ChatColor.GREEN + "Removed " + args[2] + " from whitelist.");
            } else {
               sender.sendMessage(ChatColor.RED + args[2] + " is not in the whitelist.");
            }
            return true;
         default:
            sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + sub);
            return true;
      }
   }

   private void saveWhitelist(java.util.Set<String> players) {
      java.io.File configFile = new java.io.File(this.plugin.getDataFolder(), "config.yml");
      org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
      cfg.set("whitelist.players", new java.util.ArrayList<>(players));
      try {
         cfg.save(configFile);
      } catch (java.io.IOException e) {
         this.plugin.getLogger().warning("Failed to save whitelist: " + e.getMessage());
      }
      // Reload to apply changes
      this.plugin.reloadSettings();
   }

   private static String formatTime(long millis) {
      java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      return sdf.format(new java.util.Date(millis));
   }
}
