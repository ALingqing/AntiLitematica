package top.chenray.antilitematica.guard;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.util.Msg;
import top.chenray.antilitematica.util.ViolationWindow;

/**
 * Detects Litematica "quick paste" abuse via rapid command execution
 * (e.g. /setblock spam used by Litematica's executeOperation).
 */
public final class CommandGuard implements Listener {
   private final AntiLitematicaPlugin plugin;
   private final Settings settings;
   private final Map<UUID, Long> lastCommandMs = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> commandBurst = new ConcurrentHashMap<>();
   private final Map<UUID, ViolationWindow> violations = new ConcurrentHashMap<>();

   public CommandGuard(AntiLitematicaPlugin plugin, Settings settings) {
      this.plugin = plugin;
      this.settings = settings;
   }

   public void start() {
      if (this.settings.commandGuard().enabled()) {
         Bukkit.getPluginManager().registerEvents(this, this.plugin);
      }
   }

   public void shutdown() {
      HandlerList.unregisterAll(this);
      this.lastCommandMs.clear();
      this.commandBurst.clear();
      this.violations.clear();
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      Player p = event.getPlayer();
      if (shouldBypass(p)) {
         return;
      }
      // Check world whitelist
      if (this.settings.worldWhitelist() != null
            && this.settings.worldWhitelist().isWorldExempt(p.getWorld().getName())) {
         return;
      }

      Settings.CommandGuard cg = this.settings.commandGuard();
      int effectiveMaxPerWindow = plugin.getDynamicThresholdManager().adjustInt(cg.maxPerWindow());
      String cmd = event.getMessage();
      String cmdLower = cmd.toLowerCase(Locale.ROOT);

      // Check allowed commands whitelist (takes priority)
      for (String allowed : cg.allowedCommands()) {
         if (cmdLower.startsWith(allowed.toLowerCase(Locale.ROOT))) {
            return; // Skip all checks for allowed commands
         }
      }

      // Check blocked commands (Litematica quick-paste often uses /setblock)
      boolean blocked = false;
      for (String blockedCmd : cg.blockedCommands()) {
         if (cmdLower.startsWith(blockedCmd.toLowerCase(Locale.ROOT))) {
            blocked = true;
            break;
         }
      }

      // Burst detection: rapid commands in short window
      UUID id = p.getUniqueId();
      long now = System.currentTimeMillis();
      Long last = this.lastCommandMs.put(id, now);
      if (last != null && now - last < cg.windowMs()) {
         int burst = this.commandBurst.merge(id, 1, Integer::sum);
         if (burst >= effectiveMaxPerWindow) {
            this.deny(event, p, "command_burst", burst);
            return;
         }
      } else {
         this.commandBurst.put(id, 1);
      }

      if (blocked) {
         this.deny(event, p, "blocked_command", 1);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID id = event.getPlayer().getUniqueId();
      this.lastCommandMs.remove(id);
      this.commandBurst.remove(id);
      this.violations.remove(id);
   }

   private void deny(PlayerCommandPreprocessEvent event, Player p, String type, int burstCount) {
      event.setCancelled(true);
      ViolationWindow vw = this.violations.computeIfAbsent(p.getUniqueId(),
            ignored -> new ViolationWindow(this.settings.commandGuard().violations().windowMs()));
      int n = vw.addViolation();

      if (this.settings.integration().enabled()) {
         Settings.Integration integ = this.settings.integration();
         this.plugin.getIntegrationManager().flag(p, integ.checkPrefix() + ":command_" + type,
               integ.violationLevel(), "command guard: " + type + " burst=" + burstCount);
      }

      if (n == 1 || n % 2 == 0) {
         String prefix = Msg.prefix(this.settings);
         p.sendMessage(Msg.color(prefix + "&cCommand blocked: suspected Litematica quick-paste abuse."));
      }

      if (n >= plugin.getDynamicThresholdManager().adjustInt(this.settings.commandGuard().violations().kickAt())) {
         if (p.isOnline()) {
            this.plugin.getLogger().info("Kicking " + p.getName() + " due to repeated blocked commands (" + type + "), violations=" + n);
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (p.isOnline() && !shouldBypass(p)) {
                  String prefix = Msg.prefix(this.settings);
                  p.kickPlayer(Msg.color(prefix + this.settings.messages().kick()));
               }
            });
         }
      } else {
         this.notifyStaff(p, type, n);
      }
   }

   private void notifyStaff(Player p, String type, int violations) {
      String prefix = Msg.prefix(this.settings);
      String msg = Msg.color(prefix + "&e" + p.getName() + " &7blocked command (&f" + type + "&7), vio=&f" + violations);
      for (Player online : Bukkit.getOnlinePlayers()) {
         if (online.hasPermission("antilitematica.notify")) {
            online.sendMessage(msg);
         }
      }
   }

   private static boolean shouldBypass(Player p) {
      return p.hasPermission("antilitematica.bypass");
   }
}
