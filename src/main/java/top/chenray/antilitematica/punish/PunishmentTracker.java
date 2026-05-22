package top.chenray.antilitematica.punish;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Manages violation records using SQLite or in-memory storage.
 */
public final class PunishmentTracker {
   private final Plugin plugin;
   private final boolean useSqlite;
   private final long windowMinutes;
   private Connection connection;
   private final Map<UUID, ViolationRecord> memoryCache = new ConcurrentHashMap<>();

   public PunishmentTracker(Plugin plugin, boolean useSqlite, long windowMinutes) {
      this.plugin = plugin;
      this.useSqlite = useSqlite;
      this.windowMinutes = Math.max(1, windowMinutes);
      if (this.useSqlite) {
         this.initSqlite();
      }
   }

   private void initSqlite() {
      try {
         File dataFolder = this.plugin.getDataFolder();
         if (!dataFolder.exists()) {
            dataFolder.mkdirs();
         }
         File dbFile = new File(dataFolder, "violations.db");
         this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
         try (Statement stmt = this.connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS violations ("
                  + "uuid TEXT PRIMARY KEY,"
                  + "player_name TEXT,"
                  + "count INTEGER DEFAULT 0,"
                  + "first_violation INTEGER,"
                  + "last_violation INTEGER,"
                  + "total_violations INTEGER DEFAULT 0"
                  + ")");
         }
         // Schedule cleanup every 24 hours
         Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, this::cleanupOldRecords,
               20L * 60L * 60L * 24L, 20L * 60L * 60L * 24L);
      } catch (SQLException e) {
         this.plugin.getLogger().severe("Failed to initialize SQLite: " + e.getMessage());
      }
   }

   /**
    * Records a violation and returns the updated record.
    */
   public synchronized ViolationRecord recordViolation(Player player) {
      UUID uuid = player.getUniqueId();
      long now = System.currentTimeMillis();
      ViolationRecord record = this.loadRecord(uuid);

      if (record == null) {
         record = new ViolationRecord(uuid, player.getName(), 1, now, now, 1);
      } else {
         long windowMs = this.windowMinutes * 60L * 1000L;
         if (now - record.firstViolation() > windowMs) {
            // Window expired, reset counter but keep total
            record.count(1);
            record.firstViolation(now);
         } else {
            record.count(record.count() + 1);
         }
         record.lastViolation(now);
         record.totalViolations(record.totalViolations() + 1);
         record.playerName(player.getName());
      }

      this.saveRecord(record);
      return record;
   }

   public synchronized ViolationRecord getRecord(UUID uuid) {
      return this.loadRecord(uuid);
   }

   public synchronized void resetPlayer(UUID uuid) {
      if (this.useSqlite && this.connection != null) {
         try (PreparedStatement ps = this.connection.prepareStatement(
               "DELETE FROM violations WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
         } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to reset player: " + e.getMessage());
         }
      }
      this.memoryCache.remove(uuid);
   }

   public synchronized List<ViolationRecord> getAllRecords() {
      List<ViolationRecord> list = new ArrayList<>();
      if (this.useSqlite && this.connection != null) {
         try (Statement stmt = this.connection.createStatement();
              ResultSet rs = stmt.executeQuery("SELECT * FROM violations")) {
            while (rs.next()) {
               list.add(fromResultSet(rs));
            }
         } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to load records: " + e.getMessage());
         }
      } else {
         list.addAll(this.memoryCache.values());
      }
      return list;
   }

   public void clearExpiredRecords() {
      long windowMs = this.windowMinutes * 60L * 1000L;
      long now = System.currentTimeMillis();
      List<UUID> toRemove = new ArrayList<>();
      for (ViolationRecord record : getAllRecords()) {
         if (now - record.lastViolation() > windowMs) {
            toRemove.add(record.uuid());
         }
      }
      for (UUID uuid : toRemove) {
         resetPlayer(uuid);
      }
      if (!toRemove.isEmpty()) {
         this.plugin.getLogger().info("Cleared " + toRemove.size() + " expired violation records.");
      }
   }

   public void shutdown() {
      if (this.connection != null) {
         try {
            this.connection.close();
         } catch (SQLException e) {
            // ignore
         }
         this.connection = null;
      }
      this.memoryCache.clear();
   }

   private ViolationRecord loadRecord(UUID uuid) {
      if (this.useSqlite && this.connection != null) {
         try (PreparedStatement ps = this.connection.prepareStatement(
               "SELECT * FROM violations WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
               if (rs.next()) {
                  return fromResultSet(rs);
               }
            }
         } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to load record: " + e.getMessage());
         }
      }
      return this.memoryCache.get(uuid);
   }

   private void saveRecord(ViolationRecord record) {
      if (this.useSqlite && this.connection != null) {
         try (PreparedStatement ps = this.connection.prepareStatement(
               "INSERT OR REPLACE INTO violations (uuid, player_name, count, first_violation, last_violation, total_violations) "
                     + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, record.uuid().toString());
            ps.setString(2, record.playerName());
            ps.setInt(3, record.count());
            ps.setLong(4, record.firstViolation());
            ps.setLong(5, record.lastViolation());
            ps.setInt(6, record.totalViolations());
            ps.executeUpdate();
         } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to save record: " + e.getMessage());
         }
      } else {
         this.memoryCache.put(record.uuid(), record);
      }
   }

   private void cleanupOldRecords() {
      long cutoff = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L);
      if (this.useSqlite && this.connection != null) {
         try (PreparedStatement ps = this.connection.prepareStatement(
               "DELETE FROM violations WHERE last_violation < ?")) {
            ps.setLong(1, cutoff);
            int rows = ps.executeUpdate();
            if (rows > 0) {
               this.plugin.getLogger().info("Cleaned up " + rows + " old violation records.");
            }
         } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to cleanup old records: " + e.getMessage());
         }
      } else {
         this.memoryCache.values().removeIf(r -> r.lastViolation() < cutoff);
      }
   }

   private static ViolationRecord fromResultSet(ResultSet rs) throws SQLException {
      return new ViolationRecord(
            UUID.fromString(rs.getString("uuid")),
            rs.getString("player_name"),
            rs.getInt("count"),
            rs.getLong("first_violation"),
            rs.getLong("last_violation"),
            rs.getInt("total_violations")
      );
   }
}
