package top.chenray.antilitematica.guard;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.RayTraceResult;
import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.api.event.DetectionEvent;
import top.chenray.antilitematica.api.event.PunishmentEvent;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.util.Msg;
import top.chenray.antilitematica.util.SchedulerUtil;
import top.chenray.antilitematica.util.TokenBucket;
import top.chenray.antilitematica.util.ViolationWindow;

public final class PlacementGuard implements Listener {
   private final AntiLitematicaPlugin plugin;
   private final Settings settings;
   /** Unified per-player tracking: player UUID -> tracker. Single map, single cleanup point. */
   private final Map<UUID, PlayerTracker> trackers = new ConcurrentHashMap<>();
   /** Cached set of staff UUIDs with antilitematica.notify permission. */
   private final Set<UUID> staffNotify = ConcurrentHashMap.newKeySet();

   // Cached config values, refreshed on start/reload
   private volatile int cachedMaxBlocksPerSecond = 14;
   private volatile int cachedKickAt = 8;
   private volatile boolean apEnabled;
   private volatile boolean applyToCreative;
   private volatile boolean enforceRaytrace;
   private volatile boolean detectConsecutiveSameType;
   private volatile boolean detectNoLookChange;
   private volatile long consecutiveWindowMs = 3000L; // now configurable

   public PlacementGuard(AntiLitematicaPlugin plugin, Settings settings) {
      this.plugin = plugin;
      this.settings = settings;
   }

   public void start() {
      refreshConfigCache();
      if (this.apEnabled) {
         Bukkit.getPluginManager().registerEvents(this, this.plugin);
         // Pre-populate staff notify cache
         for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("antilitematica.notify")) {
               staffNotify.add(online.getUniqueId());
            }
         }
      }
   }

   /** Reload cached config values to avoid repeated getter chain calls at runtime. */
   public void refreshConfigCache() {
      Settings.AntiPrinter ap = this.settings.antiPrinter();
      this.apEnabled = ap.enabled();
      this.applyToCreative = ap.applyToCreative();
      this.enforceRaytrace = ap.enforceRaytrace();
      this.detectConsecutiveSameType = ap.detectConsecutiveSameType();
      this.detectNoLookChange = ap.detectNoLookChange();
      this.cachedMaxBlocksPerSecond = Math.max(1, plugin.getDynamicThresholdManager().adjustInt(ap.maxBlocksPerSecond()));
      this.cachedKickAt = plugin.getDynamicThresholdManager().adjustInt(ap.violations().kickAt());
      this.consecutiveWindowMs = Math.max(1000L, this.settings.antiPrinter().violations().windowMs() / 2); // use half the violation window
   }

   public void shutdown() {
      HandlerList.unregisterAll(this);
      this.trackers.clear();
      this.staffNotify.clear();
   }

   /** Get world key for map lookups. */
   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent event) {
      if (event instanceof BlockMultiPlaceEvent) return;
      if (!apEnabled) return;
      Player p = event.getPlayer();
      if (shouldBypass(p)) return;
      // Check per-world anti-printer config
      if (!this.settings.isAntiPrinterEnabledForWorld(p.getWorld().getName())) return;
      if (!applyToCreative && p.getGameMode() == GameMode.CREATIVE) return;

      if (cachedMaxBlocksPerSecond > 0 && !this.consume(p, 1)) {
         this.deny(event, p, "rate");
         return;
      }
      if (this.enforceRaytrace && !this.rayTraceMatches(p, event.getBlockPlaced(), event.getBlockAgainst())) {
         // Tweakeroo flexi placement modifies hit vectors — skip raytrace in compat mode
         if (!this.plugin.getMasaCompat().shouldSkipRaytrace(p)) {
            this.deny(event, p, "raytrace");
            return;
         }
      }
      if (this.detectConsecutiveSameType && this.checkConsecutiveSameType(p, event.getBlockPlaced())) {
         this.deny(event, p, "consecutive_same");
         return;
      }
      if (this.detectNoLookChange && this.checkNoLookChange(p)) {
         this.deny(event, p, "no_look_change");
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onMultiPlace(BlockMultiPlaceEvent event) {
      if (!apEnabled) return;
      Player p = event.getPlayer();
      if (shouldBypass(p)) return;
      // Check per-world anti-printer config
      if (!this.settings.isAntiPrinterEnabledForWorld(p.getWorld().getName())) return;
      if (!applyToCreative && p.getGameMode() == GameMode.CREATIVE) return;

      int count = Math.max(1, event.getReplacedBlockStates().size());
      if (cachedMaxBlocksPerSecond > 0 && !this.consume(p, count)) {
         this.deny(event, p, "rate");
         return;
      }
      if (this.enforceRaytrace && !this.rayTraceMatches(p, event.getBlockPlaced(), event.getBlockAgainst())) {
         if (!this.plugin.getMasaCompat().shouldSkipRaytrace(p)) {
            this.deny(event, p, "raytrace");
            return;
         }
      }
      if (this.detectConsecutiveSameType && this.checkConsecutiveSameType(p, event.getBlockPlaced())) {
         this.deny(event, p, "consecutive_same");
         return;
      }
      if (this.detectNoLookChange && this.checkNoLookChange(p)) {
         this.deny(event, p, "no_look_change");
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player p = event.getPlayer();
      if (p.hasPermission("antilitematica.notify")) {
         staffNotify.add(p.getUniqueId());
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID id = event.getPlayer().getUniqueId();
      this.trackers.remove(id);
      this.staffNotify.remove(id);
   }

   @EventHandler
   public void onWorldChange(PlayerChangedWorldEvent event) {
      // Reset tracker state for the new world (fresh start in different world)
      Player p = event.getPlayer();
      PlayerTracker tracker = this.trackers.get(p.getUniqueId());
      if (tracker != null) tracker.resetState();
   }

   private boolean consume(Player p, int blocks) {
      PlayerTracker tracker = this.trackers.computeIfAbsent(p.getUniqueId(), k -> new PlayerTracker(cachedMaxBlocksPerSecond));
      if (tracker.bucket == null) {
         tracker.bucket = TokenBucket.perSecond(cachedMaxBlocksPerSecond, Math.max(cachedMaxBlocksPerSecond * 2L, 10L));
      }
      return tracker.bucket.tryConsume(blocks);
   }

   private void deny(Cancellable event, Player p, String type) {
      // Check world whitelist
      if (this.settings.worldWhitelist() != null
            && this.settings.worldWhitelist().isWorldExempt(p.getWorld().getName())) {
         return;
      }
      event.setCancelled(true);
      PlayerTracker tracker = this.trackers.computeIfAbsent(p.getUniqueId(), k -> new PlayerTracker(cachedMaxBlocksPerSecond));
      if (tracker.violations == null) {
         tracker.violations = new ViolationWindow(this.settings.antiPrinter().violations().windowMs());
      }
      int n = tracker.violations.addViolation();

      if (this.settings.integration().enabled()) {
         Settings.Integration integ = this.settings.integration();
         this.plugin.getIntegrationManager().flag(p, integ.checkPrefix() + ":printer_" + type,
               integ.violationLevel(), "printer detection: " + type);
      }

      if (n == 1 || n % 3 == 0) {
         p.sendMessage(Msg.color(Msg.prefix(this.settings) + this.settings.messages().blockedPlace()));
      }

      // ---- Fire DetectionEvent ----
      DetectionEvent detectionEvent = new DetectionEvent(p, "printer", type, DetectionEvent.DetectionType.PRINTER);
      Bukkit.getPluginManager().callEvent(detectionEvent);

      if (n >= cachedKickAt) {
         if (p.isOnline() && !detectionEvent.isCancelled()) {
            this.plugin.getLogger().info("Kicking " + p.getName() + " due to repeated blocked placements (" + type + "), violations=" + n);
            final String kickMsgStr = Msg.color(Msg.prefix(this.settings) + this.settings.messages().kick());
            SchedulerUtil.runForPlayer(this.plugin, p, () -> {
               if (p.isOnline() && !shouldBypass(p)) {
                  p.kickPlayer(kickMsgStr);
               }
            });
            // ---- Fire PunishmentEvent ----
            SchedulerUtil.runGlobal(this.plugin, () -> {
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
      String msg = Msg.color(Msg.prefix(this.settings) + "&e" + p.getName()
            + " &7blocked placement (&f" + type + "&7), vio=&f" + violations);
      for (UUID uid : staffNotify) {
         Player staff = Bukkit.getPlayer(uid);
         if (staff != null && staff.isOnline()) {
            staff.sendMessage(msg);
         }
      }
   }

   private boolean rayTraceMatches(Player p, Block placed, Block against) {
      double reach = (p.getGameMode() == GameMode.CREATIVE
            ? this.settings.antiPrinter().reachCreative()
            : this.settings.antiPrinter().reachSurvival())
            + this.settings.antiPrinter().extraReachAllowance();
      RayTraceResult r = p.rayTraceBlocks(reach, FluidCollisionMode.NEVER);
      if (r == null) return false;
      Block hit = r.getHitBlock();
      return hit != null && (sameBlock(hit, against) || sameBlock(hit, placed));
   }

   private boolean checkConsecutiveSameType(Player p, Block placed) {
      UUID id = p.getUniqueId();
      PlayerTracker tracker = this.trackers.computeIfAbsent(id, k -> new PlayerTracker(cachedMaxBlocksPerSecond));
      if (tracker.recentPlacements == null) {
         tracker.recentPlacements = new ArrayDeque<>();
      }
      Deque<PlacementSnapshot> deque = tracker.recentPlacements;
      long now = System.currentTimeMillis();
      String type = placed.getType().name();
      deque.addLast(new PlacementSnapshot(now, type));
      // Remove entries older than configured window
      while (!deque.isEmpty() && now - deque.peekFirst().time > consecutiveWindowMs) {
         deque.pollFirst();
      }
      int threshold = Math.max(3, plugin.getDynamicThresholdManager().adjustInt(
            this.settings.antiPrinter().consecutiveSameTypeThreshold()));
      // Require at least some placements to avoid premature flagging
      if (deque.size() < threshold) return false;

      // Use sliding ratio: flag if the dominant type accounts for >80% of placements
      // This is smarter than requiring ALL to be same type (human builders mix blocks)
      int sameCount = 0;
      for (PlacementSnapshot snap : deque) {
         if (snap.blockType.equals(type)) sameCount++;
      }
      double ratio = (double) sameCount / deque.size();
      if (ratio >= 0.8) {
         deque.clear();
         return true;
      }
      return false;
   }

   private boolean checkNoLookChange(Player p) {
      UUID id = p.getUniqueId();
      PlayerTracker tracker = this.trackers.computeIfAbsent(id, k -> new PlayerTracker(cachedMaxBlocksPerSecond));
      Location loc = p.getLocation();
      float yaw = loc.getYaw();
      float pitch = loc.getPitch();
      Float lastY = tracker.lastYaw;
      Float lastP = tracker.lastPitch;
      tracker.lastYaw = yaw;
      tracker.lastPitch = pitch;
      if (lastY != null && lastP != null) {
         float yawDiff = Math.abs(yaw - lastY);
         float pitchDiff = Math.abs(pitch - lastP);
         // Normalize yaw diff to 0..180
         if (yawDiff > 180.0f) yawDiff = 360.0f - yawDiff;
         // Use different tolerances: yaw is stricter (0.05), pitch allows a bit more (0.1)
         // because vertical mouse movement is more common during building
         if (yawDiff < 0.05f && pitchDiff < 0.1f) {
            tracker.noLookCount++;
            if (tracker.noLookCount >= Math.max(2, plugin.getDynamicThresholdManager().adjustInt(
                  this.settings.antiPrinter().noLookChangeThreshold()))) {
               tracker.noLookCount = 0;
               return true;
            }
         } else {
            tracker.noLookCount = 0;
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

   private static boolean shouldBypass(Player p) {
      return p.hasPermission("antilitematica.bypass")
            || top.chenray.antilitematica.util.BedrockPlayerDetector.isBedrockPlayer(p);
   }

   /**
    * Unified per-player tracking data. Replaces 6 separate maps with 1.
    * Player leaves → one remove() call cleans everything.
    * Player changes world → resetState() resets counters for fresh start.
    */
   private static final class PlayerTracker {
      TokenBucket bucket;
      ViolationWindow violations;
      Deque<PlacementSnapshot> recentPlacements;
      float lastYaw = Float.NaN;
      float lastPitch = Float.NaN;
      int noLookCount;

      PlayerTracker(int maxBlocksPerSecond) {
         this.bucket = TokenBucket.perSecond(maxBlocksPerSecond, Math.max(maxBlocksPerSecond * 2L, 10L));
         this.violations = null; // Created lazily in deny()
      }

      void resetState() {
         this.bucket = null; // Recreated on next consume()
         this.violations = null;
         if (this.recentPlacements != null) this.recentPlacements.clear();
         this.lastYaw = Float.NaN;
         this.lastPitch = Float.NaN;
         this.noLookCount = 0;
      }
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
