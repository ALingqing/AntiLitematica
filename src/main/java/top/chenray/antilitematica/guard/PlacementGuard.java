package top.chenray.antilitematica.guard;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.RayTraceResult;
import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.api.event.DetectionEvent;
import top.chenray.antilitematica.api.event.PunishmentEvent;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.util.Msg;
import top.chenray.antilitematica.util.TokenBucket;
import top.chenray.antilitematica.util.ViolationWindow;

public final class PlacementGuard implements Listener {
   private final AntiLitematicaPlugin plugin;
   private final Settings settings;
   private final Map<UUID, TokenBucket> buckets = new ConcurrentHashMap();
   private final Map<UUID, ViolationWindow> violations = new ConcurrentHashMap();
   private final Map<UUID, Deque<PlacementSnapshot>> recentPlacements = new ConcurrentHashMap();
   private final Map<UUID, Float> lastYaw = new ConcurrentHashMap();
   private final Map<UUID, Float> lastPitch = new ConcurrentHashMap();
   private final Map<UUID, Integer> noLookChangeCount = new ConcurrentHashMap();

   public PlacementGuard(AntiLitematicaPlugin plugin, Settings settings) {
      this.plugin = plugin;
      this.settings = settings;
   }

   public void start() {
      if (this.settings.antiPrinter().enabled()) {
         Bukkit.getPluginManager().registerEvents(this, this.plugin);
      }

   }

   public void shutdown() {
      HandlerList.unregisterAll(this);
      this.buckets.clear();
      this.violations.clear();
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent event) {
      if (event instanceof BlockMultiPlaceEvent) {
         return;
      }
      Player p = event.getPlayer();
      if (!shouldBypass(p) && (this.settings.antiPrinter().applyToCreative() || p.getGameMode() != GameMode.CREATIVE)) {
         if (this.settings.antiPrinter().maxBlocksPerSecond() > 0 && !this.consume(p.getUniqueId(), 1)) {
            this.deny(event, p, "rate");
            return;
         }
         if (this.settings.antiPrinter().enforceRaytrace() && !this.rayTraceMatches(p, event.getBlockPlaced(), event.getBlockAgainst())) {
            this.deny(event, p, "raytrace");
            return;
         }
         if (this.settings.antiPrinter().detectConsecutiveSameType() && this.checkConsecutiveSameType(p, event.getBlockPlaced())) {
            this.deny(event, p, "consecutive_same");
            return;
         }
         if (this.settings.antiPrinter().detectNoLookChange() && this.checkNoLookChange(p)) {
            this.deny(event, p, "no_look_change");
            return;
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onMultiPlace(BlockMultiPlaceEvent event) {
      Player p = event.getPlayer();
      if (!shouldBypass(p) && (this.settings.antiPrinter().applyToCreative() || p.getGameMode() != GameMode.CREATIVE)) {
         int count = Math.max(1, event.getReplacedBlockStates().size());
         if (this.settings.antiPrinter().maxBlocksPerSecond() > 0 && !this.consume(p.getUniqueId(), count)) {
            this.deny(event, p, "rate");
            return;
         }
         if (this.settings.antiPrinter().enforceRaytrace() && !this.rayTraceMatches(p, event.getBlockPlaced(), event.getBlockAgainst())) {
            this.deny(event, p, "raytrace");
            return;
         }
         if (this.settings.antiPrinter().detectConsecutiveSameType() && this.checkConsecutiveSameType(p, event.getBlockPlaced())) {
            this.deny(event, p, "consecutive_same");
            return;
         }
         if (this.settings.antiPrinter().detectNoLookChange() && this.checkNoLookChange(p)) {
            this.deny(event, p, "no_look_change");
            return;
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID id = event.getPlayer().getUniqueId();
      this.buckets.remove(id);
      this.violations.remove(id);
      this.recentPlacements.remove(id);
      this.lastYaw.remove(id);
      this.lastPitch.remove(id);
      this.noLookChangeCount.remove(id);
   }

   private boolean consume(UUID playerId, int blocks) {
      TokenBucket bucket = (TokenBucket)this.buckets.computeIfAbsent(playerId, (ignored) -> {
         int rate = Math.max(1, this.effectiveMaxBlocksPerSecond());
         return TokenBucket.perSecond((double)rate, (double)Math.max((long)rate * 2L, 10L));
      });
      return bucket.tryConsume(blocks);
   }

   private void deny(Cancellable event, Player p, String type) {
      event.setCancelled(true);
      ViolationWindow vw = (ViolationWindow)this.violations.computeIfAbsent(p.getUniqueId(), (ignored) -> new ViolationWindow(this.settings.antiPrinter().violations().windowMs()));
      int n = vw.addViolation();
      if (this.settings.integration().enabled()) {
         Settings.Integration integ = this.settings.integration();
         this.plugin.getIntegrationManager().flag(p, integ.checkPrefix() + ":printer_" + type, integ.violationLevel(), "printer detection: " + type);
      }

      if (n == 1 || n % 3 == 0) {
         String var10001 = Msg.prefix(this.settings);
         p.sendMessage(Msg.color(var10001 + this.settings.messages().blockedPlace()));
      }

      // ---- Fire DetectionEvent ----
      DetectionEvent detectionEvent = new DetectionEvent(p, "printer", type, DetectionEvent.DetectionType.PRINTER);
      Bukkit.getPluginManager().callEvent(detectionEvent);

      if (n >= this.effectiveKickAtPrinter()) {
         if (p.isOnline() && !detectionEvent.isCancelled()) {
            this.plugin.getLogger().info("Kicking " + p.getName() + " due to repeated blocked placements (" + type + "), violations=" + n);
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               if (p.isOnline() && !shouldBypass(p)) {
                  String var10001 = Msg.prefix(this.settings);
                  p.kickPlayer(Msg.color(var10001 + this.settings.messages().kick()));
               }

            });
            // ---- Fire PunishmentEvent ----
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               try {
                  PunishmentEvent pe = new PunishmentEvent(p, "printer", type,
                        PunishmentEvent.PunishmentAction.KICK, n, "printer");
                  Bukkit.getPluginManager().callEvent(pe);
               } catch (Exception ignored) {}
            });
         }
      } else {
         this.notifyStaff(p, type, n);
      }

   }

   private void notifyStaff(Player p, String type, int violations) {
      String var10000 = Msg.prefix(this.settings);
      String msg = Msg.color(var10000 + "&e" + p.getName() + " &7blocked placement (&f" + type + "&7), vio=&f" + violations);

      for(Player online : Bukkit.getOnlinePlayers()) {
         if (online.hasPermission("antilitematica.notify")) {
            online.sendMessage(msg);
         }
      }

   }

   private boolean rayTraceMatches(Player p, Block placed, Block against) {
      double reach = (p.getGameMode() == GameMode.CREATIVE ? this.settings.antiPrinter().reachCreative() : this.settings.antiPrinter().reachSurvival()) + this.settings.antiPrinter().extraReachAllowance();
      RayTraceResult r = p.rayTraceBlocks(reach, FluidCollisionMode.NEVER);
      if (r == null) {
         return false;
      } else {
         Block hit = r.getHitBlock();
         if (hit == null) {
            return false;
         } else {
            return sameBlock(hit, against) || sameBlock(hit, placed);
         }
      }
   }

   private boolean checkConsecutiveSameType(Player p, Block placed) {
      UUID id = p.getUniqueId();
      Deque<PlacementSnapshot> deque = this.recentPlacements.computeIfAbsent(id, k -> new ArrayDeque<>());
      long now = System.currentTimeMillis();
      String type = placed.getType().name();
      deque.addLast(new PlacementSnapshot(now, type));
      // Remove entries older than 3 seconds
      while (!deque.isEmpty() && now - deque.peekFirst().time > 3000L) {
         deque.pollFirst();
      }
      int threshold = Math.max(3, plugin.getDynamicThresholdManager().adjustInt(this.settings.antiPrinter().consecutiveSameTypeThreshold()));
      if (deque.size() >= threshold) {
         boolean allSame = true;
         for (PlacementSnapshot snap : deque) {
            if (!snap.blockType.equals(type)) {
               allSame = false;
               break;
            }
         }
         if (allSame) {
            deque.clear();
            return true;
         }
      }
      return false;
   }

   private boolean checkNoLookChange(Player p) {
      UUID id = p.getUniqueId();
      Float lastY = this.lastYaw.put(id, p.getLocation().getYaw());
      Float lastP = this.lastPitch.put(id, p.getLocation().getPitch());
      if (lastY != null && lastP != null) {
         float yawDiff = Math.abs(p.getLocation().getYaw() - lastY);
         float pitchDiff = Math.abs(p.getLocation().getPitch() - lastP);
         // Normalize yaw diff to 0..180
         if (yawDiff > 180.0f) yawDiff = 360.0f - yawDiff;
         if (yawDiff < 0.05f && pitchDiff < 0.05f) {
            int count = this.noLookChangeCount.merge(id, 1, Integer::sum);
            if (count >= Math.max(2, plugin.getDynamicThresholdManager().adjustInt(this.settings.antiPrinter().noLookChangeThreshold()))) {
               this.noLookChangeCount.put(id, 0);
               return true;
            }
         } else {
            this.noLookChangeCount.put(id, 0);
         }
      }
      return false;
   }

   private static boolean sameBlock(Block a, Block b) {
      if (a != null && b != null) {
         if (a.getWorld() != b.getWorld()) {
            return false;
         } else {
            return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
         }
      } else {
         return false;
      }
   }

   private int effectiveMaxBlocksPerSecond() {
      return plugin.getDynamicThresholdManager().adjustInt(this.settings.antiPrinter().maxBlocksPerSecond());
   }

   private int effectiveKickAtPrinter() {
      return plugin.getDynamicThresholdManager().adjustInt(this.settings.antiPrinter().violations().kickAt());
   }

   private static boolean shouldBypass(Player p) {
      return p.hasPermission("antilitematica.bypass");
   }

   private static final class PlacementSnapshot {
      final long time;
      final String blockType;
      PlacementSnapshot(long time, String blockType) {
         this.time = time;
         this.blockType = blockType;
      }
   }
}
