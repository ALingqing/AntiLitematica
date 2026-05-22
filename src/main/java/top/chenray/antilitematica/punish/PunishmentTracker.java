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
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Manages violation records using SQLite, MySQL, or in-memory storage.
 */
public final class PunishmentTracker {
   private final Plugin plugin;
   private final String storageType; // "sqlite", "mysql", "memory"
   private final long windowMinutes;
   private Connection connection;
   private final Map<UUID, ViolationRecord> memoryCache = new ConcurrentHashMap<>();

   // MySQL config
   private final String mysqlHost;
   private final int mysqlPort;
   private final String mysqlDatabase;
   private final String mysqlUser;
   private final String mysqlPassword;

   public PunishmentTracker(Plugin plugin, String storageType, long windowMinutes) {
      this(plugin, storageType, windowMinutes, null, 0, null, null, null);
   }

   public PunishmentTracker(Plugin plugin, String storageType, long windowMinutes,
                            String mysqlHost, int mysqlPort, String mysqlDatabase,
                            String mysqlUser, String mysqlPassword) {
      this.plugin = plugin;
      this.storageType = storageType != null ? storageType.toLowerCase() : "memory";
      this.windowMinutes = Math.max(1, windowMinutes);
      this.mysqlHost = mysqlHost;
      this.mysqlPort = mysqlPort;
      this.mysqlDatabase = mysqlDatabase;
      this.mysqlUser = mysqlUser;
      this.mysqlPassword = mysqlPassword;
      initDatabase();
   }

   private void initDatabase() {
      try {
         switch (storageType) {
            case "sqlite":
               initSqlite();
               break;
            case "mysql":
               initMysql();
               break;
            default:
               plugin.getLogger().info("Violation storage: memory (data lost on restart)");
               return;
         }
         // Schedule cleanup every 24 hours
         Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, this::cleanupOldRecords,
               20L * 60L * 60L * 24L, 20L * 60L * 60L * 24L);
      } catch (Exception e) {
         this.plugin.getLogger().severe("Failed to initialize " + storageType + ": " + e.getMessage());
      }
   }

   private void initSqlite() throws SQLException {
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
      plugin.getLogger().info("Violation storage: SQLite (" + dbFile.getAbsolutePath() + ")");
   }

   private void initMysql() throws SQLException, ClassNotFoundException {
      Class.forName("com.mysql.cj.jdbc.Driver");
      String url = "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase
            + "?useSSL=false&characterEncoding=utf8&serverTimezone=UTC";
      Properties props = new Properties();
      props.setProperty("user", mysqlUser);
      if (mysqlPassword != null && !mysqlPassword.isEmpty()) {
         props.setProperty("password", mysqlPassword);
      }
      this.connection = DriverManager.getConnection(url, props);

      try (Statement stmt = this.connection.createStatement()) {
         stmt.execute("CREATE TABLE IF NOT EXISTS violations ("
               + "uuid VARCHAR(36) PRIMARY KEY,"
               + "player_name VARCHAR(64),"
               + "count INT DEFAULT 0,"
               + "first_violation BIGINT,"
               + "last_violation BIGINT,"
               + "total_violations INT DEFAULT 0"
               + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
      }
      plugin.getLogger().info("Violation storage: MySQL (" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase + ")");
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

   /**
    * Get paginated violation detail entries for a player.
    */
   public synchronized List<String> getViolationDetails(UUID uuid) {
      List<String> details = new ArrayList<>();
      ViolationRecord record = this.loadRecord(uuid);
      if (record != null) {
         details.add("Window count: " + record.count() + " | Total: " + record.totalViolations());
         details.add("First: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
               .format(new java.util.Date(record.firstViolation())));
         details.add("Last: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
               .format(new java.util.Date(record.lastViolation())));
         details.add("Player: " + record.playerName());
      }
      return details;
   }

   /**
    * Import a previously exported violation record.
    */
   public synchronized void importRecord(ViolationRecord record) {
      if (record == null) return;
      if (hasDatabase()) {
         saveRecord(record);
      }
      this.memoryCache.put(record.uuid(), record);
   }

   private boolean hasDatabase() {
      return !"memory".equals(storageType) && this.connection != null;
   }

   public synchronized void resetPlayer(UUID uuid) {
      if (hasDatabase()) {
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
      if (hasDatabase()) {
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
      if (hasDatabase()) {
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
      if (hasDatabase()) {
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
      if (hasDatabase()) {
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
