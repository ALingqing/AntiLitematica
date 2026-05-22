package top.chenray.antilitematica.util;

import org.bukkit.ChatColor;
import top.chenray.antilitematica.config.Settings;

public final class Msg {
   private Msg() {
   }

   public static String color(String s) {
      return ChatColor.translateAlternateColorCodes('&', s);
   }

   public static String prefix(Settings settings) {
      return settings.messages() == null ? "" : settings.messages().prefix();
   }
}
