package top.chenray.antilitematica.detection;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.punish.Punisher;
import top.chenray.antilitematica.util.MinecraftVarInt;
import top.chenray.antilitematica.util.NbtLite;

/**
 * Detects Litematica / Schematica mod presence through plugin channel monitoring.
 * <p>
 * Litematica uses several plugin channels:
 * - {@code servux:litematics} — Primary channel (Servux integration, Litematica 1.21.11+)
 * - {@code litematica:hello} — Legacy channel handshake
 * - Various Schematica-era channels
 */
public final class ModChannelDetector implements Listener, PluginMessageListener {
   private final AntiLitematicaPlugin plugin;
   private final Settings settings;

   // Known Litematica/Schematica plugin channels (beyond what's in config)
   private static final String SERVUX_LITEMATICS = "servux:litematics";
   private static final String SERVUX_LITEMATICS_ALT = "servux:litematica"; // alt spelling
   private static final String LITEMATICA_MAIN = "litematica:main";
   private static final String LITEMATICA_HELLO = "litematica:hello";
   private static final String LITEMATICA_PLACE = "litematica:place";
   private static final String SCHEMATICA_CHANNEL = "schematica";
   private static final String SCHEMATICA_CHANNEL_NS = "minecraft:schematica";

   // Well-known Litematica channel fingerprints
   private static final Set<String> KNOWN_LITEMATICA_CHANNELS = Set.of(
         SERVUX_LITEMATICS, SERVUX_LITEMATICS_ALT,
         LITEMATICA_MAIN, LITEMATICA_HELLO, LITEMATICA_PLACE,
         SCHEMATICA_CHANNEL, SCHEMATICA_CHANNEL_NS
   );

   // Detection cooldown per player (millis) to prevent spam
   private static final long DETECTION_COOLDOWN_MS = 5000L;
   private final Map<UUID, Long> lastDetectionTime = new ConcurrentHashMap<>();

   public ModChannelDetector(AntiLitematicaPlugin plugin, Settings settings) {
      this.plugin = plugin;
      this.settings = settings;
   }

   public void start() {
      if (this.settings.detection().enabled()) {
         Bukkit.getPluginManager().registerEvents(this, this.plugin);

         // Register configured channels
         for(String channel : this.settings.detection().channels()) {
            try {
               Bukkit.getMessenger().registerIncomingPluginChannel(this.plugin, channel, this);
            } catch (IllegalArgumentException ex) {
               this.plugin.getLogger().warning("Invalid plugin channel in config: '" + channel + "' (" + ex.getMessage() + ")");
            }
         }

         // Also passively register known Litematica channels not in config
         // This lets us catch litematica:main etc. in onPluginMessageReceived
         for (String channel : KNOWN_LITEMATICA_CHANNELS) {
            if (!this.settings.detection().channels().contains(channel)) {
               try {
                  Bukkit.getMessenger().registerIncomingPluginChannel(this.plugin, channel, this);
               } catch (IllegalArgumentException ignored) {
                  // Channel already registered or invalid — skip silently
               }
            }
         }
      }
   }

   public void shutdown() {
      HandlerList.unregisterAll(this);
      // Unregister all channels we registered (config + passive)
      java.util.Set<String> allRegistered = new java.util.HashSet<>(this.settings.detection().channels());
      allRegistered.addAll(KNOWN_LITEMATICA_CHANNELS);
      for(String channel : allRegistered) {
         try {
            Bukkit.getMessenger().unregisterIncomingPluginChannel(this.plugin, channel, this);
         } catch (Throwable var4) {
         }
      }
      this.lastDetectionTime.clear();
   }

   /**
    * Monitors channel registration events.
    * This catches players who register Litematica channels,
    * even if we haven't explicitly registered to listen on them.
    */
   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onRegisterChannel(PlayerRegisterChannelEvent event) {
      if (!this.settings.detection().enabled()) return;
      String ch = normalize(event.getChannel());

      // Check configured channels first
      if (this.settings.detection().channels().contains(ch)) {
         this.handleDetection(event.getPlayer(), ch, "register (configured)");
         return;
      }

      // Also check known Litematica channels that might not be in config
      if (KNOWN_LITEMATICA_CHANNELS.contains(ch)) {
         this.handleDetection(event.getPlayer(), ch, "register (known litematica channel)");
      }
   }

   /**
    * Monitors channel unregistration — Litematica may try to unregister then
    * re-register to avoid detection.
    */
   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onUnregisterChannel(PlayerUnregisterChannelEvent event) {
      if (!this.settings.detection().enabled()) return;
      String ch = normalize(event.getChannel());
      if (KNOWN_LITEMATICA_CHANNELS.contains(ch)
            || this.settings.detection().channels().contains(ch)) {
         // Suspicious: unregistering a known mod channel mid-session
         // Log but don't punish — they may just be reloading
         this.plugin.getLogger().info("[ChannelDetect] " + event.getPlayer().getName()
               + " unregistered channel '" + ch + "' (suspicious)");
      }
   }

   public void onPluginMessageReceived(String channel, Player player, byte[] message) {
      if (!this.settings.detection().enabled()) return;
      String ch = normalize(channel);

      // Check if channel matches config or known channels
      boolean isConfigured = this.settings.detection().channels().contains(ch);
      boolean isKnownLitematica = KNOWN_LITEMATICA_CHANNELS.contains(ch);

      if (!isConfigured && !isKnownLitematica) return;

      String why = inspectPayload(ch, message);
      this.handleDetection(player, ch, why);
   }

   /**
    * Inspect the payload to build a detailed detection reason.
    * More specific reasons help with debugging false positives.
    */
   private String inspectPayload(String channel, byte[] message) {
      if (message == null || message.length == 0) {
         return "payload (empty)";
      }

      // -- servux:litematics payload inspection --
      if (SERVUX_LITEMATICS.equals(channel) || SERVUX_LITEMATICS_ALT.equals(channel)) {
         return inspectServuxPayload(message);
      }

      // -- litematica:main (primary Litematica channel) --
      if (LITEMATICA_MAIN.equals(channel)) {
         return inspectLitematicaMainPayload(message);
      }

      // -- litematica:hello (handshake) --
      if (LITEMATICA_HELLO.equals(channel)) {
         return "litematica:hello handshake [" + message.length + "B]";
      }

      // -- litematica:place (operation place requests) --
      if (LITEMATICA_PLACE.equals(channel)) {
         return "litematica:place operation [" + message.length + "B]";
      }

      // -- Generic channel inspection --
      String ascii = new String(message, StandardCharsets.ISO_8859_1);
      StringBuilder details = new StringBuilder("payload");
      if (ascii.contains("litematica")) {
         details.append(" (contains 'litematica')");
      }
      if (ascii.contains("malilib")) {
         details.append(" (contains 'malilib')");
      }
      if (ascii.contains("schematica")) {
         details.append(" (contains 'schematica')");
      }
      if (ascii.contains("Litematica")) {
         details.append(" (contains 'Litematica')");
      }
      details.append(" [").append(message.length).append("B]");
      return details.toString();
   }

   /**
    * Inspect litematica:main payload. This is Litematica's primary channel.
    * It typically contains serialized NBT data; just detecting traffic on this
    * channel is enough to confirm Litematica is present.
    */
   private String inspectLitematicaMainPayload(byte[] message) {
      if (message.length > 500) {
         // Large payload = schematic data transfer or operation request
         return "litematica:main data transfer [" + message.length + "B]";
      }
      // Small payload = handshake or status update
      return "litematica:main packet [" + message.length + "B]";
   }

   /**
    * Deep-inspect a servux:litematics payload for specific Litematica fingerprints.
    */
   private String inspectServuxPayload(byte[] message) {
      // Try to extract Servux metadata version string
      String version = tryExtractServuxVersionString(message);
      if (version != null) {
         return "servux metadata version=" + version;
      }

      // Try to detect by payload structure:
      // servux metadata packets are typically small and structured
      if (message.length > 0 && message.length < 500) {
         // Check if first byte is a valid VarInt
         MinecraftVarInt.ReadResult rr = MinecraftVarInt.read(message, 0);
         if (rr.ok()) {
            int packetType = rr.value();
            if (packetType >= 0 && packetType <= 10) {
               return "servux packet type=" + packetType + " [" + message.length + "B]";
            }
         }
      }

      // Large payload = likely schematic data transfer
      if (message.length > 500) {
         return "servux data transfer [" + message.length + "B]";
      }

      return "servux channel [" + message.length + "B]";
   }

   private void handleDetection(Player player, String channel, String why) {
      // Check world whitelist
      if (this.settings.worldWhitelist() != null
            && this.settings.worldWhitelist().isWorldExempt(player.getWorld().getName())) {
         return;
      }
      if (player.hasPermission("antilitematica.bypass")) return;

      // Detection cooldown: prevent spam from repeated channel messages
      UUID uuid = player.getUniqueId();
      long now = System.currentTimeMillis();
      Long last = lastDetectionTime.get(uuid);
      if (last != null && now - last < DETECTION_COOLDOWN_MS) {
         return;
      }
      lastDetectionTime.put(uuid, now);

      // Resolve channel to mod name for better logging
      String modName = resolveModName(channel);
      String enrichedWhy = modName + " " + why;

      if (this.plugin.markPunished(uuid)) {
         Punisher.punishDetection(this.plugin, this.settings, player, channel, enrichedWhy);
      }
   }

   /**
    * Resolve a channel name to a human-readable mod name.
    * Used for logging and display purposes.
    */
   public static String resolveModName(String channel) {
      if (channel == null) return "Unknown";
      String ch = normalize(channel);
      if (ch.equals("servux:litematics") || ch.equals("servux:litematica")) return "Litematica (Servux)";
      if (ch.equals("litematica:main")) return "Litematica";
      if (ch.equals("litematica:hello")) return "Litematica (handshake)";
      if (ch.equals("litematica:place")) return "Litematica (place)";
      if (ch.equals("schematica") || ch.equals("minecraft:schematica")) return "Schematica";
      return ch;
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
