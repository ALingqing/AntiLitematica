package top.chenray.antilitematica.punish;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.detection.DetectionBus;
import top.chenray.antilitematica.detection.DetectionHandler;

/**
 * Wraps {@link Punisher} as a {@link DetectionHandler} for the DetectionBus.
 * <p>
 * Registered first: tries graduated punishment if enabled, falls back to legacy action.
 */
public final class PunisherHandler implements DetectionHandler {

   private final AntiLitematicaPlugin plugin;
   private final Settings settings;

   public PunisherHandler(AntiLitematicaPlugin plugin, Settings settings) {
      this.plugin = plugin;
      this.settings = settings;
   }

   @Override
   public boolean handle(DetectionBus.DetectionContext ctx) {
      // Whitelist check
      if (Punisher.isWhitelisted(settings, ctx.player())) {
         plugin.getLogger().info("[Whitelist] " + ctx.player().getName()
               + " triggered detection (" + ctx.reason() + ") but is whitelisted — logging only.");
         return true;
      }

      // Graduated punishment takes priority
      if (settings.graduatedPunishment() != null && settings.graduatedPunishment().enabled()
            && plugin.getGraduatedPunisher() != null) {
         plugin.getGraduatedPunisher().punish(ctx.player(), ctx.channel(), ctx.reason());
         return true;
      }

      // Legacy single-action fallback
      Punisher.executeLegacyAction(plugin, settings, ctx.player(), ctx.channel(), ctx.reason());
      return true;
   }
}
