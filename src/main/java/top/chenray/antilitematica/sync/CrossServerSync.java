package top.chenray.antilitematica.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.punish.ViolationRecord;
import top.chenray.antilitematica.util.SchedulerUtil;

/**
 * Cross-server violation sync via BungeeCord/Velocity plugin messaging.
 * <p>
 * When a player is detected on one server, the violation record is broadcast
 * to all other servers in the network. Each server independently applies
 * graduated punishment based on the synced violation count.
 * <p>
 * Channels:
 * - {@code "al:violation"} — Broadcast violation record to all servers
 * - {@code "al:punish"} — Broadcast punishment action (kick/ban) to all servers
 * <p>
 * Protocol:
 * - Payload: [uuid(36) + world(bytes) + count(int) + total(int) + timestamp(long)]
 */
public final class CrossServerSync implements PluginMessageListener {

   private final AntiLitematicaPlugin plugin;
   private static final String CHANNEL_VIOLATION = "al:violation";
   private static final String CHANNEL_PUNISH = "al:punish";
   private static final String BUNGEECORD_CHANNEL = "BungeeCord";

   private boolean enabled = false;

   public CrossServerSync(AntiLitematicaPlugin plugin) {
      this.plugin = plugin;
   }

   public void enable() {
      if (!hasBungeeOrVelocity()) {
         plugin.getLogger().info("Cross-server sync: no proxy detected (BungeeCord/Velocity).");
         return;
      }
      Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, BUNGEECORD_CHANNEL);
      Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_VIOLATION, this);
      Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_PUNISH, this);
      this.enabled = true;
      plugin.getLogger().info("Cross-server sync enabled (channels: " + CHANNEL_VIOLATION + ", " + CHANNEL_PUNISH + ")");
   }

   public void disable() {
      if (!enabled) return;
      try {
         Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEECORD_CHANNEL);
         Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_VIOLATION, this);
         Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_PUNISH, this);
      } catch (Exception ignored) {}
      this.enabled = false;
   }

   public boolean isEnabled() { return enabled; }

   /**
    * Broadcast a violation record to all servers.
    */
   public void broadcastViolation(Player player, String world, int count, int total) {
      if (!enabled) return;
      try {
         ByteArrayOutputStream bout = new ByteArrayOutputStream();
         DataOutputStream out = new DataOutputStream(bout);
         out.writeUTF("Forward");
         out.writeUTF("ALL");
         out.writeUTF(CHANNEL_VIOLATION);

         ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
         DataOutputStream msgOut = new DataOutputStream(msgBytes);
         msgOut.writeUTF(player.getUniqueId().toString());
         msgOut.writeUTF(world != null ? world : "");
         msgOut.writeInt(count);
         msgOut.writeInt(total);
         msgOut.writeLong(System.currentTimeMillis());
         msgOut.writeUTF(player.getName());

         out.writeShort(msgBytes.toByteArray().length);
         out.write(msgBytes.toByteArray());

         sendPluginMessage(bout.toByteArray());
      } catch (Exception e) {
         plugin.getLogger().warning("Cross-server sync: failed to broadcast violation: " + e.getMessage());
      }
   }

   /**
    * Broadcast a punishment action to all servers (e.g. ban a player network-wide).
    */
   public void broadcastPunishment(Player player, String action, String reason) {
      if (!enabled) return;
      try {
         ByteArrayOutputStream bout = new ByteArrayOutputStream();
         DataOutputStream out = new DataOutputStream(bout);
         out.writeUTF("Forward");
         out.writeUTF("ALL");
         out.writeUTF(CHANNEL_PUNISH);

         ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
         DataOutputStream msgOut = new DataOutputStream(msgBytes);
         msgOut.writeUTF(player.getUniqueId().toString());
         msgOut.writeUTF(action);
         msgOut.writeUTF(reason != null ? reason : "");
         msgOut.writeLong(System.currentTimeMillis());
         msgOut.writeUTF(player.getName());

         out.writeShort(msgBytes.toByteArray().length);
         out.write(msgBytes.toByteArray());

         sendPluginMessage(bout.toByteArray());
      } catch (Exception e) {
         plugin.getLogger().warning("Cross-server sync: failed to broadcast punishment: " + e.getMessage());
      }
   }

   @Override
   public void onPluginMessageReceived(String channel, Player player, byte[] message) {
      if (!enabled) return;
      try {
         DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));

         if (CHANNEL_VIOLATION.equals(channel)) {
            String uuidStr = in.readUTF();
            String world = in.readUTF();
            int count = in.readInt();
            int total = in.readInt();
            long timestamp = in.readLong();
            String playerName = in.readUTF();

            UUID uuid = UUID.fromString(uuidStr);
            plugin.getLogger().info("[Sync] Received violation from " + playerName
                  + " (count=" + count + ", total=" + total + ")");

            // Import the record into local tracker
            if (plugin.getPunishmentTracker() != null) {
               ViolationRecord record = new ViolationRecord(uuid, playerName, count,
                     timestamp, timestamp, total);
               plugin.getPunishmentTracker().importRecord(record);
            }
         } else if (CHANNEL_PUNISH.equals(channel)) {
            String uuidStr = in.readUTF();
            String action = in.readUTF();
            String reason = in.readUTF();
            long timestamp = in.readLong();
            String playerName = in.readUTF();

            // If ban action, apply locally
            if ("ban".equalsIgnoreCase(action) || "tempban".equalsIgnoreCase(action)) {
               Player target = Bukkit.getPlayer(UUID.fromString(uuidStr));
               if (target != null && target.isOnline()) {
                  String finalReason = "[Cross-Server] " + reason;
                  SchedulerUtil.runForPlayer(plugin, target, () -> {
                     if (target.isOnline()) {
                        target.kickPlayer(finalReason);
                        org.bukkit.Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                              .addBan(target.getName(), finalReason, null, plugin.getName());
                     }
                  });
               }
            }
         }
      } catch (Exception e) {
         plugin.getLogger().warning("Cross-server sync: failed to handle message: " + e.getMessage());
      }
   }

   private void sendPluginMessage(byte[] data) {
      // Use a dummy player to send BungeeCord forward message
      Player dummy = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
      if (dummy != null) {
         dummy.sendPluginMessage(plugin, BUNGEECORD_CHANNEL, data);
      }
   }

   private static boolean hasBungeeOrVelocity() {
      try {
         Class.forName("net.md_5.bungee.api.ProxyServer");
         return true;
      } catch (ClassNotFoundException e) {
         try {
            Class.forName("com.velocitypowered.api.proxy.ProxyServer");
            return true;
         } catch (ClassNotFoundException e2) {
            return false;
         }
      }
   }
}
