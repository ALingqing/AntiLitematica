package top.chenray.antilitematica.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Detects Bedrock Edition players connected through Geyser.
 * Uses Floodgate API if available, falls back to name prefix detection.
 */
public final class BedrockPlayerDetector {

   private static Boolean floodgateChecked;
   private static Class<?> floodgateApiClass;

   private BedrockPlayerDetector() {}

   /**
    * Check if a player is a Bedrock Edition player (connected via Geyser).
    */
   public static boolean isBedrockPlayer(Player player) {
      if (player == null) return false;
      return hasFloodgateApi(player) || hasGeyserPrefix(player);
   }

   /**
    * Check using Floodgate API (most reliable).
    */
   private static boolean hasFloodgateApi(Player player) {
      try {
         if (floodgateChecked == null) {
            floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateChecked = true;
         }
         if (floodgateApiClass != null) {
            Object instance = floodgateApiClass.getMethod("getInstance").invoke(null);
            return (boolean) instance.getClass()
                  .getMethod("isFloodgatePlayer", java.util.UUID.class)
                  .invoke(instance, player.getUniqueId());
         }
      } catch (Exception ignored) {
         floodgateChecked = false;
         floodgateApiClass = null;
      }
      return false;
   }

   /**
    * Fallback: check Geyser's default name prefix (configurable in Geyser's config.yml).
    * Default prefix is "." — Bedrock players appear as ".playerName".
    */
   private static boolean hasGeyserPrefix(Player player) {
      return player.getName() != null && player.getName().startsWith(".");
   }
}
