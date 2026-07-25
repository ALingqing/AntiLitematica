package top.chenray.antilitematica.detection;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.entity.Player;

import top.chenray.antilitematica.api.event.DetectionEvent;

/**
 * Detection event bus — decouples detection sources from punishment handlers.
 * <p>
 * Detection sources (ModChannelDetector, PlacementGuard, etc.) call {@link #emit(DetectionContext)},
 * and registered {@link DetectionHandler} instances process the detection in order.
 * The first handler that returns {@code true} claims the detection; subsequent handlers are skipped.
 * <p>
 * External plugins can register custom handlers via {@link #register(DetectionHandler)}.
 */
public final class DetectionBus {

   private final List<DetectionHandler> handlers = new CopyOnWriteArrayList<>();

   /**
    * Register a detection handler. Handlers are tried in registration order.
    */
   public void register(DetectionHandler handler) {
      if (handler != null) handlers.add(handler);
   }

   /**
    * Unregister a previously registered handler.
    */
   public void unregister(DetectionHandler handler) {
      if (handler != null) handlers.remove(handler);
   }

   /**
    * Remove all handlers.
    */
   public void clear() {
      handlers.clear();
   }

   /**
    * Emit a detection event through the bus.
    * Fires a cancellable {@link DetectionEvent} first, then delegates to registered handlers.
    *
    * @param ctx detection context
    * @return true if a handler claimed the detection
    */
   public boolean emit(DetectionContext ctx) {
      // Fire DetectionEvent for external plugins
      DetectionEvent event = new DetectionEvent(ctx.player, ctx.channel, ctx.reason, ctx.detectionType);
      org.bukkit.Bukkit.getPluginManager().callEvent(event);
      if (event.isCancelled()) return false;

      // Delegate to registered handlers
      for (DetectionHandler handler : handlers) {
         if (handler.handle(ctx)) return true;
      }
      return false;
   }

   /**
    * Detection context — carries all information about a detection event.
    */
   public static final class DetectionContext {
      private final Player player;
      private final String channel;
      private final String reason;
      private final DetectionEvent.DetectionType detectionType;

      public DetectionContext(Player player, String channel, String reason,
                              DetectionEvent.DetectionType detectionType) {
         this.player = player;
         this.channel = channel;
         this.reason = reason;
         this.detectionType = detectionType;
      }

      public Player player() { return player; }
      public String channel() { return channel; }
      public String reason() { return reason; }
      public DetectionEvent.DetectionType detectionType() { return detectionType; }
   }
}
