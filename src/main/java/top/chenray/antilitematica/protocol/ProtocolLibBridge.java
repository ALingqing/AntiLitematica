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
import java.util.Set;
import java.util.UUID;
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
      List<PacketType> types = new ArrayList<>();
      types.add(Client.CUSTOM_PAYLOAD);
      Settings.Signals signals = this.settings.detection().signals();
      if (signals != null) {
         if (signals.easyPlace() != null && signals.easyPlace().enabled()) {
            types.add(Client.USE_ITEM_ON);
         }
         if (signals.nbtQuery() != null && signals.nbtQuery().enabled()) {
            types.add(Client.TILE_NBT_QUERY);
            types.add(Client.ENTITY_NBT_QUERY);
         }
      }

      this.customPayloadListener = new PacketAdapter(this.plugin, ListenerPriority.HIGHEST, types) {
         public void onPacketReceiving(PacketEvent event) {
            if (ProtocolLibBridge.this.settings.enabled() && ProtocolLibBridge.this.settings.detection().enabled()) {
               Player player = event.getPlayer();
               if (player != null && !player.hasPermission("antilitematica.bypass")) {
                  PacketContainer packet = event.getPacket();
                  if (packet.getType() == Client.CUSTOM_PAYLOAD) {
                     ProtocolLibBridge.this.handleCustomPayload(event, player, packet);
                  } else {
                     Settings.Signals sig = ProtocolLibBridge.this.settings.detection().signals();
                     if (packet.getType() == Client.USE_ITEM_ON && sig != null && sig.easyPlace() != null && sig.easyPlace().enabled()) {
                        if (ProtocolLibBridge.detectEasyPlaceUseItemOn(packet, sig.easyPlace().relMin(), sig.easyPlace().relMax())) {
                           if (sig.easyPlace().cancelPacket()) {
                              event.setCancelled(true);
                           }
                           ProtocolLibBridge.this.punishOnce(player, "litematica:easy_place", "use_item_on (abnormal hit vec)");
                        }
                     } else if ((packet.getType() == Client.TILE_NBT_QUERY || packet.getType() == Client.ENTITY_NBT_QUERY) && sig != null && sig.nbtQuery() != null && sig.nbtQuery().enabled()) {
                        boolean allow = sig.nbtQuery().allowOp() && player.isOp();
                        if (!allow) {
                           if (sig.nbtQuery().cancelPacket()) {
                              event.setCancelled(true);
                           }
                           ProtocolLibBridge.this.punishOnce(player, "minecraft:tag_query", "debug nbt query (" + packet.getType().name() + ")");
                        }
                     }
                  }
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
   }

   private void punishOnce(Player player, String channel, String why) {
      UUID uuid = player.getUniqueId();
      if (this.plugin.markPunished(uuid)) {
         Punisher.punishDetection(this.plugin, this.settings, player, channel, why);
      }

   }

   private void handleCustomPayload(PacketEvent event, Player player, PacketContainer packet) {
      String channel = readChannel(packet);
      if (channel != null && !channel.isEmpty()) {
         channel = normalize(channel);
         if (isRegisterChannel(channel)) {
            byte[] data = readPayloadBytes(packet);
            if (data != null && data.length != 0) {
               Set<String> announced = parseRegisterList(data);
               if (!announced.isEmpty()) {
                  for(String blocked : this.settings.detection().channels()) {
                     if (announced.contains(normalize(blocked))) {
                        if ("servux:litematics".equals(normalize(blocked)) && ProtocolLibBridge.this.settings.detection().blockServux()) {
                           event.setCancelled(true);
                        }
                        this.punishOnce(player, blocked, "register-list via " + channel);
                        return;
                     }
                  }
               }
            }
         } else {
            Settings.Signals sig = this.settings.detection().signals();
            boolean servuxMetaEnabled = sig == null || sig.servuxMetadata() == null || sig.servuxMetadata().enabled();
            if (servuxMetaEnabled && "servux:litematics".equals(channel)) {
               byte[] data = readPayloadBytes(packet);
               String version = tryExtractServuxVersionString(data);
               if (version != null) {
                  if (ProtocolLibBridge.this.settings.detection().blockServux()) {
                     event.setCancelled(true);
                  }
                  this.punishOnce(player, channel, "payload (servux metadata version=" + version + ")");
                  return;
               }
            }

            if (this.settings.detection().channels().contains(channel)) {
               if ("servux:litematics".equals(channel) && ProtocolLibBridge.this.settings.detection().blockServux()) {
                  event.setCancelled(true);
               }
               this.punishOnce(player, channel, "payload");
            }
         }
      }

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
