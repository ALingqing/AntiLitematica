package top.chenray.antilitematica.util;

/**
 * Utility for sanitizing strings used in console commands to prevent command injection.
 */
public final class CommandSanitizer {

   private CommandSanitizer() {}

   /**
    * Sanitize a string for safe use in Minecraft console commands.
    * Removes shell metacharacters that could be used for command injection.
    */
   public static String sanitize(String input) {
      if (input == null) return "";
      return input.replace("'", "'\\''")
            .replace("\"", "\\\"")
            .replace(";", "")
            .replace("&", "")
            .replace("|", "")
            .replace("$", "")
            .replace("`", "")
            .replace("\n", "")
            .replace("\r", "");
   }

   /**
    * Sanitize a player name for safe use in commands.
    * Player names in Minecraft can only contain [a-zA-Z0-9_] and . (for Geyser),
    * but we still sanitize to be safe.
    */
   public static String sanitizePlayerName(String name) {
      if (name == null) return "";
      return name.replace(";", "").replace("|", "").replace("&", "").replace("\"", "");
   }

   /**
    * Sanitize a punishment reason string.
    */
   public static String sanitizeReason(String reason) {
      return sanitize(reason);
   }

   /**
    * Sanitize a player name for use in Discord/OneBot notifications.
    */
   public static String sanitizeDisplay(String input) {
      if (input == null) return "";
      return input.replace("@", "@\u200B") // Zero-width space prevents @everyone ping
            .replace("[", "\\[")
            .replace("]", "\\]");
   }
}
