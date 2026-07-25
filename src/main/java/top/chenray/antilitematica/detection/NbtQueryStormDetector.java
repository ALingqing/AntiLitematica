package top.chenray.antilitematica.detection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.api.event.DetectionEvent;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.util.Msg;
import top.chenray.antilitematica.util.SchedulerUtil;

/**
 * Detects Litematica schematic loading via NBT query storms.
 * <p>
 * When Litematica loads a schematic, it sends a burst of TILE_NBT_QUERY
 * packets to read block entity data. Legitimate players rarely query
 * more than a few tile entities per second.
 */
public final class NbtQueryStormDetector implements Listener {

   private final AntiLitematicaPlugin plugin;
   private final Settings settings;

   // Per-player NBT query tracking
   private final Map<UUID, long[]> queryTimestamps = new ConcurrentHashMap<>();

   // Config
   private static final int QUERIES_PER_SECOND_THRESHOLD = 15;  // 15+ queries/sec = suspicious
   private static final int QUERY_WINDOW_MS = 3000;              // 3 second window
   private static final int COOLDOWN_MS = 10000;                 // 10s cooldown between detections

   private final Map<UUID, Long> lastDetection = new ConcurrentHashMap<>();

   public NbtQueryStormDetector(AntiLitematicaPlugin plugin, Settings settings) {
      this.plugin = plugin;
      this.settings = settings;
   }

   public void start() {
      if (this.settings.detection().enabled()) {
         Bukkit.getPluginManager().registerEvents(this, this.plugin);
      }
   }

   public void shutdown() {
      HandlerList.unregisterAll(this);
      this.queryTimestamps.clear();
      this.lastDetection.clear();
   }

   /**
    * Called when a TILE_NBT_QUERY or ENTITY_NBT_QUERY packet is received.
    * Tracks query frequency per player.
    */
   public boolean onNbtQuery(Player player) {
      if (player.hasPermission("antilitematica.bypass")
            || top.chenray.antilitematica.util.BedrockPlayerDetector.isBedrockPlayer(player)) {
         return false;
      }
      // Check world detection config
      if (!this.settings.isDetectionEnabledForWorld(player.getWorld().getName())) return false;

      UUID id = player.getUniqueId();
      long now = System.currentTimeMillis();

      // Cooldown check
      Long last = lastDetection.get(id);
      if (last != null && now - last < COOLDOWN_MS) return false;

      // Track query timestamps (sliding window)
      long[] timestamps = this.queryTimestamps.computeIfAbsent(id, k -> new long[64]);
      int idx = (int) ((now / 1000) % timestamps.length);
      timestamps[idx] = now;

      // Count queries in the window
      int count = 0;
      for (long ts : timestamps) {
         if (ts > 0 && now - ts < QUERY_WINDOW_MS) count++;
      }

      if (count >= QUERIES_PER_SECOND_THRESHOLD) {
         lastDetection.put(id, now);
         queryTimestamps.remove(id);

         // Emit detection via bus
         this.plugin.getDetectionBus().emit(
               new DetectionBus.DetectionContext(player, "nbt_query_storm",
                     "NBT query storm detected: " + count + " queries in " + (QUERY_WINDOW_MS / 1000) + "s",
                     DetectionEvent.DetectionType.SIGNAL));
         return true;
      }
      return false;
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID id = event.getPlayer().getUniqueId();
      this.queryTimestamps.remove(id);
      this.lastDetection.remove(id);
   }
}
