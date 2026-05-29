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
import top.chenray.antilitematica.util.DiscordWebhook;

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
            return handleStatus(sender);
         case "reset":
            if (args.length < 2) {
               sender.sendMessage(ChatColor.RED + "Usage: /al reset <player|all|expired>");
               return true;
            }
            return handleReset(sender, args);
         case "history":
            if (args.length < 2) {
               sender.sendMessage(ChatColor.RED + "Usage: /al history <player> [page]");
               return true;
            }
            int page = 1;
            if (args.length >= 3) {
               try { page = Math.max(1, Integer.parseInt(args[2])); } catch (NumberFormatException e) { /* ignore */ }
            }
            return handleHistory(sender, args[1], page);
         case "update":
            return handleUpdate(sender);
         case "export":
            return handleExport(sender, args);
         case "import":
            return handleImport(sender, args);
         case "testnotify":
            return handleTestNotify(sender);
         case "kickall":
            return handleKickAll(sender);
         case "whitelist":
            return handleWhitelist(sender, args);
         default:
            return false;
      }
   }

   private boolean handleStatus(CommandSender sender) {
      Settings s = this.plugin.settings();
      Settings.Signals sig = s.detection().signals();
      String gray = String.valueOf(ChatColor.GRAY);
      String storage = s.graduatedPunishment() != null ? s.graduatedPunishment().storage() : "none";
      sender.sendMessage(gray + "enabled=" + s.enabled() + " detection=" + s.detection().enabled()
            + " action=" + s.detection().action()
            + " storage=" + storage
            + " blockServux=" + s.detection().blockServux()
            + " signals={servuxMeta=" + (sig == null || sig.servuxMetadata() == null || sig.servuxMetadata().enabled())
            + ", easyPlace=" + (sig != null && sig.easyPlace() != null && sig.easyPlace().enabled())
            + ", nbtQuery=" + (sig != null && sig.nbtQuery() != null && sig.nbtQuery().enabled())
            + "} anti_printer=" + s.antiPrinter().enabled()
            + " command_guard=" + s.commandGuard().enabled()
            + " graduated=" + (s.graduatedPunishment() != null && s.graduatedPunishment().enabled())
            + " storage=" + storage);
      return true;
   }

   private boolean handleReset(CommandSender sender, String[] args) {
      var tracker = this.plugin.getPunishmentTracker();
      if (tracker == null) {
         sender.sendMessage(ChatColor.RED + "Graduated punishment system is not enabled.");
         return true;
      }
      String target = args[1].toLowerCase();
      if (target.equals("all")) {
         List<ViolationRecord> all = tracker.getAllRecords();
         for (ViolationRecord r : all) {
            tracker.resetPlayer(r.uuid());
         }
         sender.sendMessage(ChatColor.GREEN + "Reset " + all.size() + " violation records.");
         return true;
      }
      if (target.equals("expired")) {
         tracker.clearExpiredRecords();
         sender.sendMessage(ChatColor.GREEN + "Cleared expired violation records.");
         return true;
      }
      @SuppressWarnings("deprecation")
      OfflinePlayer p = Bukkit.getOfflinePlayer(target);
      if (p == null || (!p.hasPlayedBefore() && !p.isOnline())) {
         sender.sendMessage(ChatColor.RED + "Player not found: " + target);
         return true;
      }
      tracker.resetPlayer(p.getUniqueId());
      sender.sendMessage(ChatColor.GREEN + "Reset violation record for " + target);
      return true;
   }

   private boolean handleHistory(CommandSender sender, String targetName, int page) {
      var tracker = this.plugin.getPunishmentTracker();
      if (tracker == null) {
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
      ViolationRecord record = tracker.getRecord(uuid);
      if (record == null) {
         sender.sendMessage(ChatColor.YELLOW + targetName + " has no violation record.");
         return true;
      }
      int perPage = 5;
      List<String> details = tracker.getViolationDetails(uuid);
      int totalPages = Math.max(1, (int) Math.ceil((double) details.size() / perPage));
      if (page > totalPages) page = totalPages;

      sender.sendMessage(ChatColor.GOLD + "=== Violation History: " + targetName + " ===");
      sender.sendMessage(ChatColor.GRAY + "Total violations: " + ChatColor.WHITE + record.totalViolations()
            + ChatColor.GRAY + " | Window count: " + ChatColor.WHITE + record.count());
      sender.sendMessage(ChatColor.GRAY + "First: " + ChatColor.WHITE + formatTime(record.firstViolation())
            + ChatColor.GRAY + " | Last: " + ChatColor.WHITE + formatTime(record.lastViolation()));

      if (!details.isEmpty()) {
         sender.sendMessage(ChatColor.GRAY + "--- Page " + page + "/" + totalPages + " ---");
         int start = (page - 1) * perPage;
         int end = Math.min(start + perPage, details.size());
         for (int i = start; i < end; i++) {
            sender.sendMessage(ChatColor.GRAY + " #" + (i + 1) + " " + ChatColor.WHITE + details.get(i));
         }
      }
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

   private boolean handleTestNotify(CommandSender sender) {
      Settings s = this.plugin.settings();
      int sent = 0;
      int failed = 0;

      // Test Discord webhook
      if (s.discord() != null && s.discord().enabled() && s.discord().webhookUrl() != null && !s.discord().webhookUrl().isEmpty()) {
         sender.sendMessage(ChatColor.GRAY + "Testing Discord webhook...");
         Settings.Discord dc = s.discord();
         DiscordWebhook wh = new DiscordWebhook(this.plugin,
               dc.webhookUrl(), dc.username(),
               dc.avatarUrl() != null ? dc.avatarUrl() : "",
               dc.embedTitle(), dc.embedColor(),
               dc.footerText() != null ? dc.footerText() : "",
               dc.proxyHost() != null ? dc.proxyHost() : "",
               dc.proxyPort(),
               dc.proxyUsername() != null ? dc.proxyUsername() : "",
               dc.proxyPassword() != null ? dc.proxyPassword() : "");
         if (wh.test()) {
            sender.sendMessage(ChatColor.GREEN + "Discord webhook test successful!");
            sent++;
         } else {
            sender.sendMessage(ChatColor.RED + "Discord webhook test failed. Check console for details.");
            failed++;
         }
      }

      // Test OneBot
      var oneBot = this.plugin.getOneBotNotifier();
      if (oneBot != null) {
         sender.sendMessage(ChatColor.GRAY + "Testing OneBot (QQ) notification...");
         if (oneBot.test()) {
            sender.sendMessage(ChatColor.GREEN + "OneBot test message sent!");
            sent++;
         } else {
            sender.sendMessage(ChatColor.RED + "OneBot test failed. Check console for details.");
            failed++;
         }
      }

      if (sent == 0 && failed == 0) {
         sender.sendMessage(ChatColor.YELLOW + "No notification services configured. Enable discord or onebot in config.yml.");
      } else {
         sender.sendMessage(ChatColor.GRAY + "Result: " + ChatColor.GREEN + sent + " succeeded"
               + ChatColor.GRAY + ", " + ChatColor.RED + failed + " failed");
      }
      return true;
   }

   private boolean handleKickAll(CommandSender sender) {
      int kicked = 0;
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (p.hasPermission("antilitematica.bypass")) continue;
         if (this.plugin.isPunished(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               p.kickPlayer(ChatColor.RED + "Kicked by admin (AntiLitematica batch)");
            });
            kicked++;
         }
      }
      sender.sendMessage(ChatColor.GREEN + "Kicked " + kicked + " flagged players.");
      if (this.plugin.getAuditLogger() != null) {
         this.plugin.getAuditLogger().log("kickall", sender.getName(), kicked + " players kicked");
      }
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

   private boolean handleExport(CommandSender sender, String[] args) {
      var tracker = this.plugin.getPunishmentTracker();
      if (tracker == null) {
         sender.sendMessage(ChatColor.RED + "Graduated punishment system is not enabled.");
         return true;
      }
      List<ViolationRecord> records = tracker.getAllRecords();
      java.io.File exportFile = new java.io.File(this.plugin.getDataFolder(), "violations_export.json");
      try {
         StringBuilder json = new StringBuilder();
         json.append("{\n  \"exported_at\": \"").append(java.time.Instant.now()).append("\",\n");
         json.append("  \"total\": ").append(records.size()).append(",\n");
         json.append("  \"records\": [\n");
         for (int i = 0; i < records.size(); i++) {
            ViolationRecord r = records.get(i);
            json.append("    {");
            json.append("\"uuid\":\"").append(escapeJson(r.uuid().toString())).append("\",");
            json.append("\"playerName\":\"").append(escapeJson(r.playerName())).append("\",");
            json.append("\"count\":").append(r.count()).append(",");
            json.append("\"totalViolations\":").append(r.totalViolations()).append(",");
            json.append("\"firstViolation\":").append(r.firstViolation()).append(",");
            json.append("\"lastViolation\":").append(r.lastViolation());
            json.append("}");
            if (i < records.size() - 1) json.append(",");
            json.append("\n");
         }
         json.append("  ]\n}");
         java.nio.file.Files.writeString(exportFile.toPath(), json.toString(), java.nio.charset.StandardCharsets.UTF_8);
         sender.sendMessage(ChatColor.GREEN + "Exported " + records.size() + " records to " + exportFile.getName());
      } catch (java.io.IOException e) {
         sender.sendMessage(ChatColor.RED + "Export failed: " + e.getMessage());
      }
      return true;
   }

   private boolean handleImport(CommandSender sender, String[] args) {
      var tracker = this.plugin.getPunishmentTracker();
      if (tracker == null) {
         sender.sendMessage(ChatColor.RED + "Graduated punishment system is not enabled.");
         return true;
      }
      java.io.File importFile = new java.io.File(this.plugin.getDataFolder(), "violations_export.json");
      if (!importFile.exists()) {
         sender.sendMessage(ChatColor.RED + "Export file not found: " + importFile.getName());
         sender.sendMessage(ChatColor.GRAY + "Run /al export first or place violations_export.json in the plugin folder.");
         return true;
      }
      try {
         String content = java.nio.file.Files.readString(importFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
         // Simple JSON parsing without external dependencies
         String recordsSection = content.substring(content.indexOf("\"records\""));
         int arrayStart = recordsSection.indexOf("[");
         int arrayEnd = recordsSection.lastIndexOf("]");
         if (arrayStart < 0 || arrayEnd < 0) {
            sender.sendMessage(ChatColor.RED + "Invalid export file format.");
            return true;
         }
         String arrayContent = recordsSection.substring(arrayStart + 1, arrayEnd);
         String[] entries = arrayContent.split("\\},\\s*\\{");
         int count = 0;
         for (String entry : entries) {
            String clean = entry.replace("{", "").replace("}", "").trim();
            if (clean.isEmpty()) continue;
            String uuidStr = extractJsonField(clean, "uuid");
            String name = extractJsonField(clean, "playerName");
            int cnt = parseIntField(clean, "count");
            int total = parseIntField(clean, "totalViolations");
            long first = parseLongField(clean, "firstViolation");
            long last = parseLongField(clean, "lastViolation");
            if (uuidStr != null) {
               UUID uuid = UUID.fromString(uuidStr);
               ViolationRecord record = new ViolationRecord(uuid, name != null ? name : "unknown", cnt, first, last, total);
               tracker.importRecord(record);
               count++;
            }
         }
         sender.sendMessage(ChatColor.GREEN + "Imported " + count + " records from " + importFile.getName());
      } catch (Exception e) {
         sender.sendMessage(ChatColor.RED + "Import failed: " + e.getMessage());
      }
      return true;
   }

   private static String extractJsonField(String json, String key) {
      String search = "\"" + key + "\":\"";
      int start = json.indexOf(search);
      if (start >= 0) {
         start += search.length();
         int end = json.indexOf("\"", start);
         if (end >= 0) return json.substring(start, end);
      }
      return null;
   }

   private static int parseIntField(String json, String key) {
      String search = "\"" + key + "\":";
      int start = json.indexOf(search);
      if (start >= 0) {
         start += search.length();
         int end = json.indexOf(",", start);
         if (end < 0) end = json.indexOf("}", start);
         if (end < 0) end = json.length();
         try { return Integer.parseInt(json.substring(start, end).trim()); } catch (Exception e) { /* ignore */ }
      }
      return 0;
   }

   private static long parseLongField(String json, String key) {
      String search = "\"" + key + "\":";
      int start = json.indexOf(search);
      if (start >= 0) {
         start += search.length();
         int end = json.indexOf(",", start);
         if (end < 0) end = json.indexOf("}", start);
         if (end < 0) end = json.length();
         try { return Long.parseLong(json.substring(start, end).trim()); } catch (Exception e) { /* ignore */ }
      }
      return 0;
   }

   private static String escapeJson(String s) {
      return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
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
