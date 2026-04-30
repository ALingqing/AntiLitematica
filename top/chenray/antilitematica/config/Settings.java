package top.chenray.antilitematica.config;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public record Settings(boolean enabled, Messages messages, Detection detection, AntiPrinter antiPrinter, CommandGuard commandGuard, GraduatedPunishment graduatedPunishment, Integration integration, Discord discord) {
   public static Settings from(Plugin plugin, FileConfiguration cfg) {
      boolean enabled = cfg.getBoolean("enabled", true);
      // Load messages from separate messages.yml file
      File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
      FileConfiguration messagesCfg = YamlConfiguration.loadConfiguration(messagesFile);
      String prefix = messagesCfg.getString("prefix", "&7[&cAntiLitematica&7] ");
      String kickMsg = messagesCfg.getString("kick", "&cYou are not allowed to use Litematica / Printer (or its network fingerprint) on this server.");
      String blockedPlaceMsg = messagesCfg.getString("blocked_place", "&cPlease aim at the block before placing (projection/printer placement is forbidden).");
      String reloadMsg = messagesCfg.getString("reload", "&aAntiLitematica configuration reloaded.");
      Messages messages = new Messages(prefix, kickMsg, blockedPlaceMsg, reloadMsg);
      ConfigurationSection det = section(cfg, "detection");
      Set<String> channels = normalizedSet(det.getStringList("channels"));
      DetectionAction action = Settings.DetectionAction.fromString(det.getString("action", "KICK"));
      String reason = det.getString("reason", "Forbidden client mod detected (Litematica).");
      List<String> commands = det.getStringList("commands");
      boolean kickAfterCommands = det.getBoolean("kick_after_commands", true);
      ConfigurationSection sig = section(det, "signals");
      ConfigurationSection servuxMeta = section(sig, "servux_metadata");
      ConfigurationSection easyPlace = section(sig, "easy_place");
      ConfigurationSection nbtQuery = section(sig, "nbt_query");
      Signals signals = new Signals(
            new ServuxMetadataSignal(servuxMeta.getBoolean("enabled", true)),
            new EasyPlaceSignal(
                  easyPlace.getBoolean("enabled", true),
                  easyPlace.getDouble("rel_min", -0.5),
                  easyPlace.getDouble("rel_max", 1.5),
                  easyPlace.getBoolean("cancel_packet", false)
            ),
            new NbtQuerySignal(
                  nbtQuery.getBoolean("enabled", true),
                  nbtQuery.getBoolean("allow_op", true),
                  nbtQuery.getBoolean("cancel_packet", true)
            )
      );
      boolean blockServux = det.getBoolean("block_servux", true);
      Detection detection = new Detection(det.getBoolean("enabled", true), channels, action, reason, commands, kickAfterCommands, blockServux, signals);
      ConfigurationSection ap = section(cfg, "anti_printer");
      ConfigurationSection vio = section(ap, "violations");
      AntiPrinter antiPrinter = new AntiPrinter(ap.getBoolean("enabled", true), ap.getBoolean("apply_to_creative", true), ap.getBoolean("enforce_raytrace", true), ap.getDouble("reach_survival", (double)5.0F), ap.getDouble("reach_creative", (double)6.0F), ap.getDouble("extra_reach_allowance", (double)0.25F), ap.getInt("max_blocks_per_second", 14), ap.getBoolean("detect_consecutive_same_type", true), ap.getInt("consecutive_same_type_threshold", 6), ap.getBoolean("detect_no_look_change", true), ap.getInt("no_look_change_threshold", 5), new Violations(vio.getLong("window_ms", 8000L), vio.getInt("kick_at", 8)));
      ConfigurationSection cg = section(cfg, "command_guard");
      ConfigurationSection cgVio = section(cg, "violations");
      CommandGuard commandGuard = new CommandGuard(cg.getBoolean("enabled", true), normalizedSet(cg.getStringList("blocked_commands")), cg.getInt("max_per_window", 8), cg.getLong("window_ms", 3000L), new Violations(cgVio.getLong("window_ms", 8000L), cgVio.getInt("kick_at", 5)));
      ConfigurationSection integ = section(cfg, "integration");
      Integration integration = new Integration(integ.getString("type", "none").toLowerCase(Locale.ROOT), integ.getBoolean("enabled", false), integ.getInt("violation_level", 10), integ.getString("check_prefix", "AntiLitematica"));
      ConfigurationSection dc = section(cfg, "discord");
      String avatarUrl = dc.getString("avatar_url", "");
      String footerText = dc.getString("footer_text", "");
      Discord discord = new Discord(
         dc.getBoolean("enabled", false),
         dc.getString("webhook_url", ""),
         dc.getString("username", "AntiLitematica"),
         avatarUrl,
         dc.getString("embed_title", "Forbidden client detected"),
         dc.getInt("embed_color", 16711680),
         footerText,
         dc.getBoolean("notify_on_detection", true),
         dc.getBoolean("notify_on_punish", true),
         dc.getString("proxy_host", ""),
         dc.getInt("proxy_port", 0),
         dc.getString("proxy_username", ""),
         dc.getString("proxy_password", "")
      );
      ConfigurationSection gp = section(cfg, "graduated_punishment");
      ConfigurationSection gpLevels = section(gp, "levels");
      List<PunishmentLevel> levels = new ArrayList<>();
      for (String key : gpLevels.getKeys(false)) {
         ConfigurationSection lvl = gpLevels.getConfigurationSection(key);
         if (lvl != null) {
            levels.add(new PunishmentLevel(
                  lvl.getString("action", "kick"),
                  lvl.getString("duration", "0"),
                  lvl.getString("reason", "Violation"),
                  lvl.getBoolean("broadcast", false),
                  lvl.getBoolean("staff_alert", true)
            ));
         }
      }
      ConfigurationSection gpExceed = section(gp, "exceed_max");
      PunishmentLevel exceedMax = new PunishmentLevel(
            gpExceed.getString("action", "ban"),
            gpExceed.getString("duration", "0"),
            gpExceed.getString("reason", "Too many violations"),
            gpExceed.getBoolean("broadcast", true),
            gpExceed.getBoolean("staff_alert", true)
      );
      List<String> banPlugins = gp.getStringList("ban_plugins");
      if (banPlugins.isEmpty()) {
         banPlugins = List.of("LiteBans", "AdvancedBan", "EssentialsX");
      }
      GraduatedPunishment graduatedPunishment = new GraduatedPunishment(
            gp.getBoolean("enabled", true),
            gp.getLong("window_minutes", 60),
            gp.getString("storage", "sqlite").toLowerCase(Locale.ROOT),
            levels,
            exceedMax,
            banPlugins
      );
      return new Settings(enabled, messages, detection, antiPrinter, commandGuard, graduatedPunishment, integration, discord);
   }

   private static ConfigurationSection section(ConfigurationSection parent, String path) {
      ConfigurationSection sec = parent.getConfigurationSection(path);
      return sec != null ? sec : parent.createSection(path);
   }

   private static Set<String> normalizedSet(List<String> in) {
      Set<String> out = new LinkedHashSet<>();

      for(String s : in) {
         if (s != null) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
               out.add(t);
            }
         }
      }

      return out;
   }

   public static record Messages(String prefix, String kick, String blockedPlace, String reload) {
   }

   public static record Detection(boolean enabled, Set<String> channels, DetectionAction action, String reason, List<String> commands, boolean kickAfterCommands, boolean blockServux, Signals signals) {
   }

   public static record Signals(ServuxMetadataSignal servuxMetadata, EasyPlaceSignal easyPlace, NbtQuerySignal nbtQuery) {
   }

   public static record ServuxMetadataSignal(boolean enabled) {
   }

   public static record EasyPlaceSignal(boolean enabled, double relMin, double relMax, boolean cancelPacket) {
   }

   public static record NbtQuerySignal(boolean enabled, boolean allowOp, boolean cancelPacket) {
   }

   public static enum DetectionAction {
      LOG,
      KICK,
      BAN,
      COMMANDS;

      public static DetectionAction fromString(String s) {
         if (s == null) {
            return KICK;
         }
         switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "LOG":
               return LOG;
            case "BAN":
               return BAN;
            case "COMMAND":
            case "COMMANDS":
               return COMMANDS;
            default:
               return KICK;
         }
      }
   }

   public static record AntiPrinter(boolean enabled, boolean applyToCreative, boolean enforceRaytrace, double reachSurvival, double reachCreative, double extraReachAllowance, int maxBlocksPerSecond, boolean detectConsecutiveSameType, int consecutiveSameTypeThreshold, boolean detectNoLookChange, int noLookChangeThreshold, Violations violations) {
   }

   public static record CommandGuard(boolean enabled, Set<String> blockedCommands, int maxPerWindow, long windowMs, Violations violations) {
   }

   public static record GraduatedPunishment(boolean enabled, long windowMinutes, String storage, List<PunishmentLevel> levels, PunishmentLevel exceedMax, List<String> banPlugins) {
   }

   public static record PunishmentLevel(String action, String duration, String reason, boolean broadcast, boolean staffAlert) {
   }

   public static record Violations(long windowMs, int kickAt) {
   }

   public static record Integration(String type, boolean enabled, int violationLevel, String checkPrefix) {
   }

   public static record Discord(
      boolean enabled,
      String webhookUrl,
      String username,
      String avatarUrl,
      String embedTitle,
      int embedColor,
      String footerText,
      boolean notifyOnDetection,
      boolean notifyOnPunish,
      String proxyHost,
      int proxyPort,
      String proxyUsername,
      String proxyPassword
   ) {
      public String avatarUrl() { return avatarUrl; }
      public String footerText() { return footerText; }
      public boolean hasProxy() {
         return proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0;
      }
   }
}