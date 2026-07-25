package top.chenray.antilitematica.detection;

/**
 * Handler for detection events dispatched through {@link DetectionBus}.
 * <p>
 * Implementations should check {@link DetectionBus.DetectionContext#detectionType()}
 * and return {@code true} if they handled the detection (consuming it),
 * or {@code false} to pass it to the next handler in the chain.
 */
@FunctionalInterface
public interface DetectionHandler {

   /**
    * Handle a detection event.
    *
    * @param ctx the detection context
    * @return true if this handler consumed the detection (subsequent handlers are skipped)
    */
   boolean handle(DetectionBus.DetectionContext ctx);
}
