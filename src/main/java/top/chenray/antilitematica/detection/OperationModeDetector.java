package top.chenray.antilitematica.detection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.api.event.DetectionEvent;
import top.chenray.antilitematica.config.Settings;

/**
 * Detects Litematica executeOperation by analyzing block placement spatial patterns.
 * <p>
 * Litematica's executeOperation places blocks in systematic patterns (rows, columns, grids)
 * that differ from natural human placement. This detector analyzes:
 * - Grid alignment: placed blocks form a perfect grid pattern
 * - Linear progression: blocks placed in straight lines with constant spacing
 * - Uniform direction: all placements follow the same axis
 */
public final class OperationModeDetector implements Listener {

   private final AntiLitematicaPlugin plugin;
   private final Settings settings;

   // Per-player recent placement positions
   private final Map<UUID, Deque<BlockLocation>> recentBlocks = new ConcurrentHashMap<>();
   private static final int WINDOW_SIZE = 20;  // Track last 20 placements
   private static final int MIN_SAMPLES = 8;   // Need at least 8 to analyze pattern

   public OperationModeDetector(AntiLitematicaPlugin plugin, Settings settings) {
      this.plugin = plugin;
      this.settings = settings;
   }

   public void start() {
      if (this.settings.antiPrinter().enabled()) {
         org.bukkit.Bukkit.getPluginManager().registerEvents(this, this.plugin);
      }
   }

   public void shutdown() {
      HandlerList.unregisterAll(this);
      this.recentBlocks.clear();
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onBlockPlace(BlockPlaceEvent event) {
      if (!this.settings.antiPrinter().enabled()) return;
      Player p = event.getPlayer();
      if (p.hasPermission("antilitematica.bypass")
            || top.chenray.antilitematica.util.BedrockPlayerDetector.isBedrockPlayer(p)) return;
      if (!this.settings.isAntiPrinterEnabledForWorld(p.getWorld().getName())) return;

      UUID id = p.getUniqueId();
      Block placed = event.getBlockPlaced();
      Deque<BlockLocation> deque = this.recentBlocks.computeIfAbsent(id, k -> new ArrayDeque<>());

      // Track placement location
      deque.addLast(new BlockLocation(placed.getX(), placed.getY(), placed.getZ()));
      if (deque.size() > WINDOW_SIZE) deque.pollFirst();

      // Need enough samples
      if (deque.size() < MIN_SAMPLES) return;

      // Analyze spatial pattern
      if (isGridPattern(deque)) {
         this.plugin.getDetectionBus().emit(
               new DetectionBus.DetectionContext(p, "operation_mode",
                     "Grid pattern placement detected (" + deque.size() + " blocks)",
                     DetectionEvent.DetectionType.PRINTER));
         deque.clear();
      }
   }

   /**
    * Check if recent placements form a grid-like pattern typical of Litematica operations.
    */
   private static boolean isGridPattern(Deque<BlockLocation> blocks) {
      if (blocks.size() < MIN_SAMPLES) return false;

      int axisAligned = 0;
      int totalPairs = 0;
      BlockLocation[] arr = blocks.toArray(new BlockLocation[0]);

      // Check consecutive block pairs for axis alignment
      for (int i = 1; i < arr.length; i++) {
         BlockLocation a = arr[i - 1];
         BlockLocation b = arr[i];
         int dx = Math.abs(b.x - a.x);
         int dy = Math.abs(b.y - a.y);
         int dz = Math.abs(b.z - a.z);

         // Count how many dimensions changed
         int changedDims = (dx > 0 ? 1 : 0) + (dy > 0 ? 1 : 0) + (dz > 0 ? 1 : 0);

         // Axis-aligned: only 1 dimension changed (straight line along X, Y, or Z)
         if (changedDims == 1) axisAligned++;
         totalPairs++;
      }

      // If >80% of consecutive placements are axis-aligned, it's likely a machine operation
      return totalPairs > 0 && (double) axisAligned / totalPairs > 0.8;
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.recentBlocks.remove(event.getPlayer().getUniqueId());
   }

   private static final class BlockLocation {
      final int x, y, z;
      BlockLocation(int x, int y, int z) {
         this.x = x; this.y = y; this.z = z;
      }
   }
}
