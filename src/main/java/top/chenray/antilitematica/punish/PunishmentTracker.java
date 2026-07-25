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

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import top.chenray.antilitematica.util.SchedulerUtil;

/**
 * Manages violation records using SQLite, MySQL, or in-memory storage.
 */
public final class PunishmentTracker {
   private final Plugin plugin;
   private final String storageType; // "sqlite", "mysql", "memory"
   private final long windowMinutes;
   private Connection connection;
   private ScheduledTask cleanupTask;
   private final Map<String, ViolationRecord> memoryCache = new ConcurrentHashMap<>();

   // MySQL config
   private final String mysqlHost;
   private final int mysqlPort;
   private final String mysqlDatabase;
   private final String mysqlUser;
   private final String mysqlPassword;

   /** Compound key for world-aware records: "uuid:world" or just "uuid" for global. */
   private static String recordKey(UUID uuid, String world) {
      return world != null && !world.isEmpty()
            ? uuid.toString() + ":" + world.toLowerCase(java.util.Locale.ROOT)
            : uuid.toString();
   }

   private static String recordKeyPlayer(Player player) {
      String world = player.getWorld() != null ? player.getWorld().getName() : null;
      return recordKey(player.getUniqueId(), world);
   }

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
         // Schedule cleanup every 24 hours (Folia-compatible async timer)
         cleanupTask = SchedulerUtil.runTimerAsync(this.plugin, this::cleanupOldRecords,
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
         // Check if the table already exists with old schema using PRAGMA table_info
         boolean needsMigration = false;
         boolean tableExists = false;
         try (var rs = stmt.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='violations'")) {
            if (rs.next() && rs.getInt(1) > 0) tableExists = true;
         }
         if (tableExists) {
            boolean hasIdColumn = false;
            try (var rs = stmt.executeQuery("PRAGMA table_info(violations)")) {
               while (rs.next()) {
                  if ("id".equals(rs.getString("name"))) {
                     hasIdColumn = true;
                     break;
                  }
               }
            }
            needsMigration = !hasIdColumn;
         }

         if (needsMigration) {
            // Old schema: migrate to new schema with compound keys
            stmt.execute("CREATE TABLE IF NOT EXISTS violations_new ("
                  + "id TEXT PRIMARY KEY,"
                  + "uuid TEXT NOT NULL,"
                  + "player_name TEXT,"
                  + "count INTEGER DEFAULT 0,"
                  + "first_violation INTEGER,"
                  + "last_violation INTEGER,"
                  + "total_violations INTEGER DEFAULT 0,"
                  + "world TEXT DEFAULT NULL"
                  + ")");
            stmt.execute("INSERT OR IGNORE INTO violations_new (id, uuid, player_name, count, first_violation, last_violation, total_violations, world) "
                  + "SELECT uuid, uuid, player_name, count, first_violation, last_violation, total_violations, NULL FROM violations");
            stmt.execute("DROP TABLE violations");
            stmt.execute("ALTER TABLE violations_new RENAME TO violations");
            plugin.getLogger().info("Migrated violations database to new schema.");
         } else {
            stmt.execute("CREATE TABLE IF NOT EXISTS violations ("
                  + "id TEXT PRIMARY KEY,"
                  + "uuid TEXT NOT NULL,"
                  + "player_name TEXT,"
                  + "count INTEGER DEFAULT 0,"
                  + "first_violation INTEGER,"
                  + "last_violation INTEGER,"
                  + "total_violations INTEGER DEFAULT 0,"
                  + "world TEXT DEFAULT NULL"
                  + ")");
         }

         // Migration: add world column if missing (safe for old schema)
         try {
            stmt.execute("ALTER TABLE violations ADD COLUMN world TEXT DEFAULT NULL");
         } catch (SQLException ignored) {
            // Column already exists
         }
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
               + "id VARCHAR(100) PRIMARY KEY,"
               + "uuid VARCHAR(36) NOT NULL,"
               + "player_name VARCHAR(64),"
               + "count INT DEFAULT 0,"
               + "first_violation BIGINT,"
               + "last_violation BIGINT,"
               + "total_violations INT DEFAULT 0,"
               + "world VARCHAR(64) DEFAULT NULL"
               + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
         // Migration: add world column if missing (safe on MySQL)
         try {
            stmt.execute("ALTER TABLE violations ADD COLUMN IF NOT EXISTS world VARCHAR(64) DEFAULT NULL");
         } catch (SQLException ignored) {
            // Column may already exist or IF NOT EXISTS not supported
         }
      }
      plugin.getLogger().info("Violation storage: MySQL (" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase + ")");
   }

   /**
    * Records a violation for a player, world-aware.
    */
   public synchronized ViolationRecord recordViolation(Player player) {
      return recordViolation(player, player.getWorld() != null ? player.getWorld().getName() : null);
   }

   /**
    * Records a violation and returns the updated record.
    * Uses a compound key (uuid:world) for per-world tracking.
    */
   public synchronized ViolationRecord recordViolation(Player player, String world) {
      UUID uuid = player.getUniqueId();
      long now = System.currentTimeMillis();
      String key = recordKey(uuid, world);
      ViolationRecord record = this.loadRecord(key, uuid);

      if (record == null) {
         record = new ViolationRecord(uuid, player.getName(), 1, now, now, 1, world);
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

      this.saveRecord(key, record);
      return record;
   }

   public synchronized ViolationRecord getRecord(UUID uuid) {
      return this.loadRecord(recordKey(uuid, null), uuid);
   }

   /**
    * Get violation record for a player in a specific world.
    */
   public synchronized ViolationRecord getRecord(UUID uuid, String world) {
      return this.loadRecord(recordKey(uuid, world), uuid);
   }

   /**
    * Get paginated violation detail entries for a player.
    */
   public synchronized List<String> getViolationDetails(UUID uuid) {
      List<String> details = new ArrayList<>();
      ViolationRecord record = this.loadRecord(recordKey(uuid, null), uuid);
      if (record != null) {
         details.add("Window count: " + record.count() + " | Total: " + record.totalViolations());
         details.add("First: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
               .format(new java.util.Date(record.firstViolation())));
         details.add("Last: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
               .format(new java.util.Date(record.lastViolation())));
         details.add("Player: " + record.playerName());
         if (record.world() != null) {
            details.add("World: " + record.world());
         }
      }
      return details;
   }

   /**
    * Import a previously exported violation record.
    */
   public synchronized void importRecord(ViolationRecord record) {
      if (record == null) return;
      String key = recordKey(record.uuid(), record.world());
      if (hasDatabase()) {
         saveRecord(key, record);
      }
      this.memoryCache.put(key, record);
   }

   private boolean hasDatabase() {
      return !"memory".equals(storageType) && this.connection != null;
   }

   public synchronized void resetPlayer(UUID uuid) {
      // Reset in all worlds + global
      if (hasDatabase()) {
         try (PreparedStatement ps = this.connection.prepareStatement(
               "DELETE FROM violations WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
         } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to reset player: " + e.getMessage());
         }
      }
      this.memoryCache.entrySet().removeIf(e -> e.getKey().startsWith(uuid.toString()));
   }

   /**
    * Reset player's violation record in a specific world.
    */
   public synchronized void resetPlayer(UUID uuid, String world) {
      String key = recordKey(uuid, world);
      if (hasDatabase()) {
         try (PreparedStatement ps = this.connection.prepareStatement(
               "DELETE FROM violations WHERE id = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
         } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to reset player in world: " + e.getMessage());
         }
      }
      this.memoryCache.remove(key);
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
      int count = 0;
      for (ViolationRecord record : getAllRecords()) {
         if (now - record.lastViolation() > windowMs) {
            resetPlayer(record.uuid());
            count++;
         }
      }
      if (count > 0) {
         this.plugin.getLogger().info("Cleared " + count + " expired violation records.");
      }
   }

   public void shutdown() {
      if (cleanupTask != null) {
         cleanupTask.cancel();
         cleanupTask = null;
      }
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

   private ViolationRecord loadRecord(String key, UUID uuid) {
      if (hasDatabase()) {
         try (PreparedStatement ps = this.connection.prepareStatement(
               "SELECT * FROM violations WHERE id = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
               if (rs.next()) {
                  return fromResultSet(rs);
               }
            }
         } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to load record: " + e.getMessage());
         }
      }
      return this.memoryCache.get(key);
   }

   private void saveRecord(String key, ViolationRecord record) {
      if (hasDatabase()) {
         try (PreparedStatement ps = this.connection.prepareStatement(
               "INSERT OR REPLACE INTO violations (id, uuid, player_name, count, first_violation, last_violation, total_violations, world) "
                     + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, record.uuid().toString());
            ps.setString(3, record.playerName());
            ps.setInt(4, record.count());
            ps.setLong(5, record.firstViolation());
            ps.setLong(6, record.lastViolation());
            ps.setInt(7, record.totalViolations());
            ps.setString(8, record.world());
            ps.executeUpdate();
         } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to save record: " + e.getMessage());
         }
      } else {
         this.memoryCache.put(key, record);
      }
   }

   private void cleanupOldRecords() {
      // Use retention days from config, default 30 days
      long retentionDays = 30L;
      try {
         java.io.File configFile = new java.io.File(this.plugin.getDataFolder(), "config.yml");
         if (configFile.exists()) {
            retentionDays = org.bukkit.configuration.file.YamlConfiguration
                  .loadConfiguration(configFile).getLong("stats.record_retention_days", 30L);
         }
      } catch (Exception ignored) { }
      long cutoff = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
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
      String world;
      try {
         world = rs.getString("world");
      } catch (SQLException e) {
         world = null;
      }
      return new ViolationRecord(
            UUID.fromString(rs.getString("uuid")),
            rs.getString("player_name"),
            rs.getInt("count"),
            rs.getLong("first_violation"),
            rs.getLong("last_violation"),
            rs.getInt("total_violations"),
            world
      );
   }
}
