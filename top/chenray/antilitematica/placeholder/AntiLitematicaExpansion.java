package top.chenray.antilitematica.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import top.chenray.antilitematica.AntiLitematicaPlugin;

public class AntiLitematicaExpansion extends PlaceholderExpansion {

   private final AntiLitematicaPlugin plugin;

   public AntiLitematicaExpansion(AntiLitematicaPlugin plugin) {
      this.plugin = plugin;
   }

   @Override
   @NotNull
   public String getIdentifier() {
      return "antilitematica";
   }

   @Override
   @NotNull
   public String getAuthor() {
      return plugin.getDescription().getAuthors().isEmpty()
            ? "aqing"
            : String.join(", ", plugin.getDescription().getAuthors());
   }

   @Override
   @NotNull
   public String getVersion() {
      return plugin.getDescription().getVersion();
   }

   @Override
   public boolean persist() {
      return true;
   }

   @Override
   public boolean canRegister() {
      return true;
   }

   @Override
   @Nullable
   public String onPlaceholderRequest(Player player, @NotNull String params) {
      if (params.equalsIgnoreCase("version")) {
         return plugin.getDescription().getVersion();
      }

      if (params.equalsIgnoreCase("enabled")) {
         return String.valueOf(plugin.settings().enabled());
      }

      if (params.equalsIgnoreCase("detection_action")) {
         return plugin.settings().detection().action().name();
      }

      if (params.equalsIgnoreCase("anti_printer_enabled")) {
         return String.valueOf(plugin.settings().antiPrinter().enabled());
      }

      if (params.equalsIgnoreCase("command_guard_enabled")) {
         return String.valueOf(plugin.settings().commandGuard().enabled());
      }

      if (params.equalsIgnoreCase("graduated_punishment_enabled")) {
         return String.valueOf(plugin.settings().graduatedPunishment().enabled());
      }

      if (params.equalsIgnoreCase("punished")) {
         if (player == null) {
            return "false";
         }
         return String.valueOf(plugin.isPunished(player.getUniqueId()));
      }

      return null;
   }
}
