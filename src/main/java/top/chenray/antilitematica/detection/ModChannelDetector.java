package top.chenray.antilitematica.detection;

import java.nio.charset.StandardCharsets;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.punish.Punisher;
import top.chenray.antilitematica.util.MinecraftVarInt;
import top.chenray.antilitematica.util.NbtLite;

public final class ModChannelDetector implements Listener, PluginMessageListener {
   private final AntiLitematicaPlugin plugin;
   private final Settings settings;
   private static final String SERVUX_LITEMATICS = "servux:litematics";

   public ModChannelDetector(AntiLitematicaPlugin plugin, Settings settings) {
      this.plugin = plugin;
      this.settings = settings;
   }

   public void start() {
      if (this.settings.detection().enabled()) {
         Bukkit.getPluginManager().registerEvents(this, this.plugin);

         for(String channel : this.settings.detection().channels()) {
            try {
               Bukkit.getMessenger().registerIncomingPluginChannel(this.plugin, channel, this);
            } catch (IllegalArgumentException ex) {
               this.plugin.getLogger().warning("Invalid plugin channel in config: '" + channel + "' (" + ex.getMessage() + ")");
            }
         }
      }

   }

   public void shutdown() {
      HandlerList.unregisterAll(this);
      if (this.settings.detection().enabled()) {
         for(String channel : this.settings.detection().channels()) {
            try {
               Bukkit.getMessenger().unregisterIncomingPluginChannel(this.plugin, channel, this);
            } catch (Throwable var4) {
            }
         }
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onRegisterChannel(PlayerRegisterChannelEvent event) {
      if (this.settings.detection().enabled()) {
         String ch = normalize(event.getChannel());
         if (this.settings.detection().channels().contains(ch)) {
            this.handleDetection(event.getPlayer(), ch, "register");
         }
      }

   }

   public void onPluginMessageReceived(String channel, Player player, byte[] message) {
      if (this.settings.detection().enabled()) {
         String ch = normalize(channel);
         if (this.settings.detection().channels().contains(ch)) {
            String why = "payload";
            if ("servux:litematics".equals(ch)) {
               String version = tryExtractServuxVersionString(message);
               if (version != null) {
                  why = "payload (servux metadata version=" + version + ")";
               }
            } else {
               String ascii = new String(message, StandardCharsets.ISO_8859_1);
               if (ascii.contains("litematica") || ascii.contains("malilib")) {
                  why = "payload (contains litematica/malilib)";
               }
            }

            this.handleDetection(player, ch, why);
         }
      }

   }

   private void handleDetection(Player player, String channel, String why) {
      if (!player.hasPermission("antilitematica.bypass") && this.plugin.markPunished(player.getUniqueId())) {
         Punisher.punishDetection(this.plugin, this.settings, player, channel, why);
      }

   }

   private static String normalize(String channel) {
      return channel == null ? "" : channel.trim().toLowerCase();
   }

   private static String tryExtractServuxVersionString(byte[] payload) {
      if (payload != null && payload.length >= 2) {
         MinecraftVarInt.ReadResult rr = MinecraftVarInt.read(payload, 0);
         if (!rr.ok()) {
            return null;
         } else {
            int packetType = rr.value();
            int nbtOffset = rr.nextIndex();
            if (packetType != 2) {
               return null;
            } else {
               String version = NbtLite.tryReadRootCompoundString(payload, nbtOffset, "version");
               if (version == null) {
                  return null;
               } else {
                  return !version.toLowerCase().contains("litematica") ? null : version;
               }
            }
         }
      } else {
         return null;
      }
   }
}
