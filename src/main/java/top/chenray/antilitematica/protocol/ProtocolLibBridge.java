package top.chenray.antilitematica.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.PacketType.Play.Client;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.MinecraftKey;
import com.comphenix.protocol.wrappers.MovingObjectPositionBlock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.punish.Punisher;
import top.chenray.antilitematica.util.MinecraftVarInt;
import top.chenray.antilitematica.util.NbtLite;

public final class ProtocolLibBridge {
   private static final String SERVUX_LITEMATICS = "servux:litematics";
   private static final String MINECRAFT_REGISTER = "minecraft:register";
   private static final String MINECRAFT_UNREGISTER = "minecraft:unregister";
   private static final String LEGACY_REGISTER = "register";
   private static final String LEGACY_UNREGISTER = "unregister";
   private final AntiLitematicaPlugin plugin;
   private final Settings settings;
   private ProtocolManager protocolManager;
   private PacketAdapter customPayloadListener;

   // Cached signal config, avoids getter chain at runtime
   private volatile boolean svEnabled;
   private volatile boolean epEnabled;
   private volatile double epRelMin;
   private volatile double epRelMax;
   private volatile boolean epCancel;
   private volatile boolean nbtEnabled;
   private volatile boolean nbtAllowOp;
   private volatile boolean nbtCancel;
   private volatile boolean blockServux;
   private volatile java.util.Set<String> detectionChannels;

   // Consecutive EasyPlace tracking: require multiple hits to reduce false positives
   private final Map<UUID, EasyPlaceCounter> epTracking = new ConcurrentHashMap<>();
   private static final long EP_WINDOW_MS = 10000L; // 10 second window
   private static final int EP_MIN_CONSECUTIVE = 3; // require 3+ EasyPlace hits

   // Known Litematica channels for registration monitoring (same as ModChannelDetector)
   private static final java.util.Set<String> KNOWN_LITEMATICA_CHANNELS = java.util.Set.of(
         "servux:litematics", "servux:litematica",
         "litematica:hello", "litematica:place",
         "schematica", "minecraft:schematica"
   );

   public ProtocolLibBridge(AntiLitematicaPlugin plugin, Settings settings) {
      this.plugin = plugin;
      this.settings = settings;
   }

   public void start() {
      if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
         this.plugin.getLogger().severe("ProtocolLib not found. Detection features requiring ProtocolLib will not work.");
         return;
      }
      this.protocolManager = ProtocolLibrary.getProtocolManager();

      // Cache all signal configs at start time
      Settings.Signals signals = this.settings.detection().signals();
      Settings.EasyPlaceSignal ep = (signals != null) ? signals.easyPlace() : null;
      Settings.NbtQuerySignal nbt = (signals != null) ? signals.nbtQuery() : null;
      Settings.ServuxMetadataSignal sv = (signals != null) ? signals.servuxMetadata() : null;
      this.svEnabled = sv != null && sv.enabled();
      this.epEnabled = ep != null && ep.enabled();
      this.epRelMin = ep != null ? ep.relMin() : -0.5;
      this.epRelMax = ep != null ? ep.relMax() : 1.5;
      this.epCancel = ep != null && ep.cancelPacket();
      this.nbtEnabled = nbt != null && nbt.enabled();
      this.nbtAllowOp = nbt == null || nbt.allowOp();
      this.nbtCancel = nbt == null || nbt.cancelPacket();
      this.blockServux = this.settings.detection().blockServux();
      this.detectionChannels = this.settings.detection().channels();

      List<PacketType> types = new ArrayList<>();
      types.add(Client.CUSTOM_PAYLOAD);
      if (epEnabled) types.add(Client.USE_ITEM_ON);
      if (nbtEnabled) {
         types.add(Client.TILE_NBT_QUERY);
         types.add(Client.ENTITY_NBT_QUERY);
      }

      final boolean detectionEnabled = this.settings.detection().enabled();
      final boolean globalEnabled = this.settings.enabled();

      this.customPayloadListener = new PacketAdapter(this.plugin, ListenerPriority.HIGHEST, types) {
         public void onPacketReceiving(PacketEvent event) {
            if (!globalEnabled || !detectionEnabled) return;
            Player player = event.getPlayer();
            if (player == null || player.hasPermission("antilitematica.bypass")) return;

            PacketContainer packet = event.getPacket();
            if (packet.getType() == Client.CUSTOM_PAYLOAD) {
               ProtocolLibBridge.this.handleCustomPayload(event, player, packet);
            } else if (packet.getType() == Client.USE_ITEM_ON && ProtocolLibBridge.this.epEnabled) {
               if (ProtocolLibBridge.detectEasyPlaceUseItemOn(packet,
                     ProtocolLibBridge.this.epRelMin, ProtocolLibBridge.this.epRelMax)) {
                  // Track consecutive EasyPlace hits — require multiple to reduce false positives
                  if (ProtocolLibBridge.this.trackEasyPlaceHit(player.getUniqueId())) {
                     if (ProtocolLibBridge.this.epCancel) event.setCancelled(true);
                     ProtocolLibBridge.this.punishOnce(player, "litematica:easy_place", "use_item_on (abnormal hit vec)");
                  }
               } else {
                  // Reset on legitimate placement
                  ProtocolLibBridge.this.epTracking.remove(player.getUniqueId());
               }
            } else if ((packet.getType() == Client.TILE_NBT_QUERY || packet.getType() == Client.ENTITY_NBT_QUERY)
                  && ProtocolLibBridge.this.nbtEnabled) {
               boolean allow = ProtocolLibBridge.this.nbtAllowOp && player.isOp();
               if (!allow) {
                  // Inspect the NBT query transaction ID — Litematica sends specific patterns
                  String queryDetail = ProtocolLibBridge.this.inspectNbtQuery(packet, player);
                  if (ProtocolLibBridge.this.nbtCancel) event.setCancelled(true);
                  ProtocolLibBridge.this.punishOnce(player, "minecraft:tag_query",
                        "debug nbt query" + queryDetail);
               }
            }
         }
      };
      this.protocolManager.addPacketListener(this.customPayloadListener);
   }

   public void shutdown() {
      if (this.protocolManager != null && this.customPayloadListener != null) {
         this.protocolManager.removePacketListener(this.customPayloadListener);
      }

      this.protocolManager = null;
      this.customPayloadListener = null;
      this.epTracking.clear();
   }

   /**
    * Track consecutive EasyPlace hits within a time window.
    * Returns true only after EP_MIN_CONSECUTIVE hits are reached.
    * This reduces false positives from legitimate but slightly-off block interactions.
    */
   private boolean trackEasyPlaceHit(UUID playerId) {
      long now = System.currentTimeMillis();
      EasyPlaceCounter counter = epTracking.computeIfAbsent(playerId, k -> new EasyPlaceCounter());
      if (now - counter.lastHitTime > EP_WINDOW_MS) {
         counter.count = 0; // reset if window expired
      }
      counter.count++;
      counter.lastHitTime = now;
      return counter.count >= EP_MIN_CONSECUTIVE;
   }

   /** Simple counter for EasyPlace tracking. */
   private static final class EasyPlaceCounter {
      int count = 0;
      long lastHitTime = 0;
   }

   private void punishOnce(Player player, String channel, String why) {
      UUID uuid = player.getUniqueId();
      if (this.plugin.markPunished(uuid)) {
         Punisher.punishDetection(this.plugin, this.settings, player, channel, why);
      }

   }

   private void handleCustomPayload(PacketEvent event, Player player, PacketContainer packet) {
      String channel = readChannel(packet);
      if (channel == null || channel.isEmpty()) return;
      channel = normalize(channel);

      if (isRegisterChannel(channel)) {
         byte[] data = readPayloadBytes(packet);
         if (data != null && data.length != 0) {
            Set<String> announced = parseRegisterList(data);
            if (!announced.isEmpty()) {
               for (String blocked : detectionChannels) {
                  if (announced.contains(normalize(blocked))) {
                     if ("servux:litematics".equals(normalize(blocked)) && blockServux) {
                        event.setCancelled(true);
                     }
                     this.punishOnce(player, blocked, "register-list via " + channel);
                     return;
                  }
               }
               // Also check against known Litematica channels not in config
               for (String announced_ch : announced) {
                  if (KNOWN_LITEMATICA_CHANNELS.contains(announced_ch)
                        && !detectionChannels.contains(announced_ch)) {
                     this.plugin.getLogger().info("[ProtocolLib] " + player.getName()
                           + " registered known Litematica channel '" + announced_ch
                           + "' via " + channel + " (not in config blocked list)");
                     // Don't punish for channels not in config — but warn in log
                  }
               }
            }
         }
         return;
      }

      // Servux metadata check
      if (svEnabled && "servux:litematics".equals(channel)) {
         byte[] data = readPayloadBytes(packet);
         String version = tryExtractServuxVersionString(data);
         if (version != null) {
            if (blockServux) event.setCancelled(true);
            this.punishOnce(player, channel, "servux metadata version=" + version);
            return;
         }
         // Also check for servux metadata even without version string
         if (data != null && data.length >= 2) {
            // servux metadata packets have a distinct structure
            MinecraftVarInt.ReadResult rr = MinecraftVarInt.read(data, 0);
            if (rr.ok() && rr.value() <= 10) {
               // This is a valid servux protocol message — highly likely Litematica
               if (blockServux) event.setCancelled(true);
               this.punishOnce(player, channel, "servux protocol packet type=" + rr.value());
               return;
            }
         }
      }

      // Direct channel match
      if (detectionChannels.contains(channel)) {
         if ("servux:litematics".equals(channel) && blockServux) {
            event.setCancelled(true);
         }
         this.punishOnce(player, channel, "payload");
      }
   }

   /**
    * Inspect an NBT query packet to extract more detail about the query target.
    * Litematica queries specific block entities — having better details aids debugging.
    */
   private String inspectNbtQuery(PacketContainer packet, Player player) {
      StringBuilder detail = new StringBuilder();
      detail.append(" (").append(packet.getType().name());
      try {
         // Try to read the transaction ID / position from the packet
         if (packet.getIntegers() != null && packet.getIntegers().size() > 0) {
            int txId = packet.getIntegers().read(0);
            detail.append(" tx=").append(txId);
         }
         // TILE_NBT_QUERY has a BlockPosition
         if (packet.getType() == Client.TILE_NBT_QUERY
               && packet.getBlockPositionModifier() != null
               && packet.getBlockPositionModifier().size() > 0) {
            BlockPosition pos = packet.getBlockPositionModifier().read(0);
            if (pos != null) {
               detail.append(" pos=").append(pos.getX()).append(",")
                     .append(pos.getY()).append(",").append(pos.getZ());
            }
         }
      } catch (Throwable ignored) {
         // Packet structure varies by version; silently skip on error
      }
      detail.append(")");
      return detail.toString();
   }

   private static boolean detectEasyPlaceUseItemOn(PacketContainer packet, double relMin, double relMax) {
      try {
         if (packet.getMovingBlockPositions() != null && packet.getMovingBlockPositions().size() > 0) {
            MovingObjectPositionBlock mop = (MovingObjectPositionBlock)packet.getMovingBlockPositions().read(0);
            if (mop == null) {
               return false;
            } else {
               BlockPosition pos = mop.getBlockPosition();
               Vector vec = mop.getPosVector();
               if (pos != null && vec != null) {
                  double dx = vec.getX() - (double)pos.getX();
                  double dy = vec.getY() - (double)pos.getY();
                  double dz = vec.getZ() - (double)pos.getZ();
                  return dx < relMin || dx > relMax || dy < relMin || dy > relMax || dz < relMin || dz > relMax;
               } else {
                  return false;
               }
            }
         } else {
            return false;
         }
      } catch (Throwable var14) {
         return false;
      }
   }

   private static boolean isRegisterChannel(String ch) {
      return "minecraft:register".equals(ch) || "register".equals(ch) || "minecraft:unregister".equals(ch) || "unregister".equals(ch);
   }

   private static Set<String> parseRegisterList(byte[] payload) {
      String s = new String(payload, StandardCharsets.UTF_8);
      String[] parts = s.split("\u0000");
      Set<String> out = new HashSet();

      for(String p : parts) {
         String t = normalize(p);
         if (!t.isEmpty()) {
            out.add(t);
         }
      }

      return out;
   }

   private static String readChannel(PacketContainer packet) {
      try {
         if (packet.getMinecraftKeys() != null && packet.getMinecraftKeys().size() > 0) {
            MinecraftKey key = (MinecraftKey)packet.getMinecraftKeys().read(0);
            if (key != null) {
               return key.getFullKey();
            }
         }
      } catch (Throwable var3) {
      }

      try {
         if (packet.getStrings() != null && packet.getStrings().size() > 0) {
            return (String)packet.getStrings().read(0);
         }
      } catch (Throwable var2) {
      }

      return null;
   }

   private static byte[] readPayloadBytes(PacketContainer packet) {
      try {
         if (packet.getByteArrays() != null && packet.getByteArrays().size() > 0) {
            return (byte[])packet.getByteArrays().read(0);
         }
      } catch (Throwable var8) {
      }

      try {
         Object byteBufs = packet.getClass().getMethod("getByteBufs").invoke(packet);
         if (byteBufs == null) {
            return null;
         } else {
            int size = (Integer)byteBufs.getClass().getMethod("size").invoke(byteBufs);
            if (size <= 0) {
               return null;
            } else {
               Object buf = byteBufs.getClass().getMethod("read", Integer.TYPE).invoke(byteBufs, 0);
               if (buf == null) {
                  return null;
               } else {
                  int readable = (Integer)buf.getClass().getMethod("readableBytes").invoke(buf);
                  int readerIndex = (Integer)buf.getClass().getMethod("readerIndex").invoke(buf);
                  if (readable <= 0) {
                     return null;
                  } else {
                     byte[] out = new byte[readable];
                     buf.getClass().getMethod("getBytes", Integer.TYPE, byte[].class).invoke(buf, readerIndex, out);
                     return out;
                  }
               }
            }
         }
      } catch (Throwable var7) {
         return null;
      }
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
                  return !version.toLowerCase(Locale.ROOT).contains("litematica") ? null : version;
               }
            }
         }
      } else {
         return null;
      }
   }

   private static String normalize(String s) {
      return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
   }
}
