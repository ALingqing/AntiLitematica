package icu.epochcraft.antilitematica.database

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.detection.ActionType
import icu.epochcraft.antilitematica.punish.ViolationRecord
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID

/**
 * SQLite 数据库：检测记录 / 封禁 / 误报名单。
 *
 * 使用文件数据库（data.db），不需要监听任何端口。
 *
 * @author 阿清
 */
class DetectionDatabase(private val plugin: AntiLitematica) {

    private var connection: Connection? = null

    /** 初始化连接并建表 */
    fun init() {
        val dbFile = plugin.dataFolder.resolve("data.db")
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
            connection!!.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS detections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        channel TEXT NOT NULL,
                        mod_desc TEXT,
                        action TEXT NOT NULL,
                        timestamp BIGINT NOT NULL,
                        evidence TEXT
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS bans (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        expires_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS false_positives (
                        uuid TEXT NOT NULL,
                        channel TEXT NOT NULL,
                        created_at BIGINT NOT NULL,
                        PRIMARY KEY (uuid, channel)
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS violations (
                        uuid TEXT NOT NULL,
                        world TEXT NOT NULL DEFAULT '',
                        name TEXT NOT NULL,
                        count INTEGER NOT NULL,
                        total INTEGER NOT NULL,
                        first_time BIGINT NOT NULL,
                        last_time BIGINT NOT NULL,
                        PRIMARY KEY (uuid, world)
                    )
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS player_mods (
                        uuid TEXT NOT NULL,
                        mod_id TEXT NOT NULL,
                        version TEXT,
                        first_seen BIGINT NOT NULL,
                        last_seen BIGINT NOT NULL,
                        PRIMARY KEY (uuid, mod_id)
                    )
                    """.trimIndent()
                )
            }
            // 旧版本数据库缺少 evidence 列（兼容 ALTER 升级）
            ensureEvidenceColumn()
            plugin.logger.info("数据库已就绪: ${dbFile.name}")
        } catch (e: Exception) {
            plugin.logger.severe("数据库初始化失败: ${e.message}")
        }
    }

    fun close() {
        runCatching { connection?.close() }
        connection = null
    }

    // ---------------- 检测记录 ----------------

    fun insertDetection(record: DetectionRecord) {
        runCatching {
            connection!!.prepareStatement(
                "INSERT INTO detections (uuid, name, channel, mod_desc, action, timestamp, evidence) VALUES (?,?,?,?,?,?,?)"
            ).use { ps ->
                ps.setString(1, record.uuid.toString())
                ps.setString(2, record.name)
                ps.setString(3, record.channel)
                ps.setString(4, record.modDescription)
                ps.setString(5, record.action.name)
                ps.setLong(6, record.timestamp)
                ps.setString(7, record.evidence)
                ps.executeUpdate()
            }
        }.onFailure { plugin.logger.warning("写入检测记录失败: ${it.message}") }
    }

    /** 旧库升级：为 detections 表补充 evidence 列（已存在则跳过） */
    private fun ensureEvidenceColumn() {
        runCatching {
            val hasColumn = connection!!.createStatement().use { stmt ->
                stmt.executeQuery("PRAGMA table_info(detections)").use { rs ->
                    var found = false
                    while (rs.next()) {
                        if (rs.getString("name") == "evidence") {
                            found = true
                            break
                        }
                    }
                    found
                }
            }
            if (!hasColumn) {
                connection!!.createStatement().execute("ALTER TABLE detections ADD COLUMN evidence TEXT")
                plugin.logger.info("检测记录表已升级（新增 evidence 证据列）")
            }
        }.onFailure { plugin.logger.warning("检测记录表升级失败: ${it.message}") }
    }

    // ---------------- Mod 档案（握手解析的完整 mod 列表） ----------------

    /** 写入玩家本次握手上报的全部 mod（幂等 upsert，保留 first_seen） */
    fun upsertPlayerMods(uuid: UUID, mods: Map<String, String>) {
        if (mods.isEmpty()) return
        val now = System.currentTimeMillis()
        runCatching {
            connection!!.prepareStatement(
                """INSERT INTO player_mods (uuid, mod_id, version, first_seen, last_seen)
                   VALUES (?,?,?,?,?)
                   ON CONFLICT(uuid, mod_id) DO UPDATE SET
                     version = excluded.version,
                     last_seen = excluded.last_seen""".trimIndent()
            ).use { ps ->
                mods.forEach { (modId, version) ->
                    ps.setString(1, uuid.toString())
                    ps.setString(2, modId)
                    ps.setString(3, version)
                    ps.setLong(4, now)
                    ps.setLong(5, now)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }.onFailure { plugin.logger.warning("写入 mod 档案失败: ${it.message}") }
    }

    /** 读取玩家历史上上报过的全部 mod（mod_id -> version） */
    fun getPlayerMods(uuid: UUID): Map<String, String> {
        val map = linkedMapOf<String, String>()
        runCatching {
            connection!!.prepareStatement(
                "SELECT mod_id, version FROM player_mods WHERE uuid = ?"
            ).use { ps ->
                ps.setString(1, uuid.toString())
                ps.executeQuery().use { rs ->
                    while (rs.next()) map[rs.getString("mod_id")] = rs.getString("version")
                }
            }
        }
        return map
    }

    /** 删除玩家 mod 档案（解封/清理时可选调用） */
    fun clearPlayerMods(uuid: UUID) {
        runCatching {
            connection!!.prepareStatement("DELETE FROM player_mods WHERE uuid = ?").use { ps ->
                ps.setString(1, uuid.toString())
                ps.executeUpdate()
            }
        }
    }

    /** 玩家累计检测命中次数 */
    fun getDetectionCount(uuid: UUID): Int = queryInt(
        "SELECT COUNT(*) FROM detections WHERE uuid = ?", uuid.toString()
    )

    /** 全部检测命中次数 */
    fun getAllDetectionsCount(): Int = queryInt("SELECT COUNT(*) FROM detections")

    /** 玩家累计被 KICK 处理次数（用于自动封禁判定） */
    fun getKickCount(uuid: UUID): Int = queryInt(
        "SELECT COUNT(*) FROM detections WHERE uuid = ? AND action = 'KICK'", uuid.toString()
    )

    /** 全部检测记录（倒序，limit 条） */
    fun getRecentDetections(limit: Int = 100): List<DetectionRecord> =
        queryList("SELECT * FROM detections ORDER BY id DESC LIMIT $limit")

    /** 某玩家最近检测记录 */
    fun getDetectionsOf(uuid: UUID, limit: Int = 20): List<DetectionRecord> =
        queryList("SELECT * FROM detections WHERE uuid = ? ORDER BY id DESC LIMIT $limit", uuid.toString())

    /** 各通道命中次数分布 */
    fun getChannelStats(): Map<String, Int> {
        val map = linkedMapOf<String, Int>()
        runCatching {
            connection!!.createStatement().use { stmt ->
                stmt.executeQuery("SELECT channel, COUNT(*) AS cnt FROM detections GROUP BY channel ORDER BY cnt DESC").use { rs ->
                    while (rs.next()) map[rs.getString("channel")] = rs.getInt("cnt")
                }
            }
        }
        return map
    }

    /** 最近 N 天每日命中数（用于趋势展示） */
    fun getDailyStats(days: Int): Map<String, Int> {
        val map = linkedMapOf<String, Int>()
        val since = System.currentTimeMillis() - days * 86_400_000L
        runCatching {
            connection!!.prepareStatement(
                "SELECT date(timestamp/1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS cnt FROM detections WHERE timestamp >= ? GROUP BY day ORDER BY day"
            ).use { ps ->
                ps.setLong(1, since)
                ps.executeQuery().use { rs ->
                    while (rs.next()) map[rs.getString("day") ?: "?"] = rs.getInt("cnt")
                }
            }
        }
        return map
    }

    // ---------------- 封禁 ----------------

    fun insertBan(ban: BanRecord) {
        runCatching {
            connection!!.prepareStatement(
                "INSERT OR REPLACE INTO bans (uuid, name, reason, created_at, expires_at) VALUES (?,?,?,?,?)"
            ).use { ps ->
                ps.setString(1, ban.uuid.toString())
                ps.setString(2, ban.name)
                ps.setString(3, ban.reason)
                ps.setLong(4, ban.createdAt)
                ps.setLong(5, ban.expiresAt)
                ps.executeUpdate()
            }
        }.onFailure { plugin.logger.warning("写入封禁记录失败: ${it.message}") }
    }

    fun getActiveBan(uuid: UUID): BanRecord? {
        var ban: BanRecord? = null
        runCatching {
            connection!!.prepareStatement("SELECT * FROM bans WHERE uuid = ?").use { ps ->
                ps.setString(1, uuid.toString())
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        ban = mapBan(rs)
                        // 已过期的封禁视为不存在，顺手清理
                        if (ban != null && ban!!.isExpired()) {
                            removeBan(uuid)
                            ban = null
                        }
                    }
                }
            }
        }
        return ban
    }

    fun getAllActiveBans(): List<BanRecord> {
        val list = mutableListOf<BanRecord>()
        runCatching {
            connection!!.createStatement().use { stmt ->
                stmt.executeQuery("SELECT * FROM bans").use { rs ->
                    while (rs.next()) {
                        val b = mapBan(rs)
                        if (!b.isExpired()) list += b
                    }
                }
            }
        }
        return list
    }

    fun removeBan(uuid: UUID) {
        runCatching {
            connection!!.prepareStatement("DELETE FROM bans WHERE uuid = ?").use { ps ->
                ps.setString(1, uuid.toString())
                ps.executeUpdate()
            }
        }
    }

    /** 清理所有已过期封禁 */
    fun purgeExpiredBans(): Int {
        var removed = 0
        runCatching {
            val now = System.currentTimeMillis()
            connection!!.prepareStatement(
                "SELECT uuid FROM bans WHERE expires_at != ? AND expires_at <= ?"
            ).use { ps ->
                ps.setLong(1, BanRecord.PERMANENT)
                ps.setLong(2, now)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        removeBan(UUID.fromString(rs.getString("uuid")))
                        removed++
                    }
                }
            }
        }
        return removed
    }

    // ---------------- 误报名单 ----------------

    fun isFalsePositive(uuid: UUID, channel: String): Boolean {
        var result = false
        runCatching {
            connection!!.prepareStatement(
                "SELECT 1 FROM false_positives WHERE uuid = ? AND channel = ?"
            ).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, channel.lowercase())
                ps.executeQuery().use { rs -> result = rs.next() }
            }
        }
        return result
    }

    fun addFalsePositive(uuid: UUID, channel: String) {
        runCatching {
            connection!!.prepareStatement(
                "INSERT OR IGNORE INTO false_positives (uuid, channel, created_at) VALUES (?,?,?)"
            ).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, channel.lowercase())
                ps.setLong(3, System.currentTimeMillis())
                ps.executeUpdate()
            }
        }
    }

    fun removeFalsePositive(uuid: UUID) {
        runCatching {
            connection!!.prepareStatement("DELETE FROM false_positives WHERE uuid = ?").use { ps ->
                ps.setString(1, uuid.toString())
                ps.executeUpdate()
            }
        }
    }

    // ---------------- 违规记录（渐进惩罚） ----------------

    /** 读取玩家违规记录（world 为 null 表示全局记录） */
    fun getViolation(uuid: UUID, world: String?): ViolationRecord? {
        var record: ViolationRecord? = null
        runCatching {
            connection!!.prepareStatement("SELECT * FROM violations WHERE uuid = ? AND world = ?").use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, world ?: "")
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        record = ViolationRecord(
                            uuid = UUID.fromString(rs.getString("uuid")),
                            playerName = rs.getString("name"),
                            count = rs.getInt("count"),
                            firstViolation = rs.getLong("first_time"),
                            lastViolation = rs.getLong("last_time"),
                            totalViolations = rs.getInt("total"),
                            world = rs.getString("world").takeIf { it.isNotEmpty() },
                        )
                    }
                }
            }
        }
        return record
    }

    /** 写入/更新玩家违规记录 */
    fun upsertViolation(record: ViolationRecord) {
        runCatching {
            connection!!.prepareStatement(
                "INSERT OR REPLACE INTO violations (uuid, world, name, count, total, first_time, last_time) VALUES (?,?,?,?,?,?,?)"
            ).use { ps ->
                ps.setString(1, record.uuid.toString())
                ps.setString(2, record.world ?: "")
                ps.setString(3, record.playerName)
                ps.setInt(4, record.count)
                ps.setInt(5, record.totalViolations)
                ps.setLong(6, record.firstViolation)
                ps.setLong(7, record.lastViolation)
                ps.executeUpdate()
            }
        }.onFailure { plugin.logger.warning("写入违规记录失败: ${it.message}") }
    }

    // ---------------- 工具 ----------------

    private fun queryInt(sql: String, vararg args: String): Int {
        var result = 0
        runCatching {
            connection!!.prepareStatement(sql).use { ps ->
                args.forEachIndexed { i, v -> ps.setString(i + 1, v) }
                ps.executeQuery().use { rs -> if (rs.next()) result = rs.getInt(1) }
            }
        }
        return result
    }

    private fun queryList(sql: String, vararg args: String): List<DetectionRecord> {
        val list = mutableListOf<DetectionRecord>()
        runCatching {
            connection!!.prepareStatement(sql).use { ps ->
                args.forEachIndexed { i, v -> ps.setString(i + 1, v) }
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        list += DetectionRecord(
                            uuid = UUID.fromString(rs.getString("uuid")),
                            name = rs.getString("name"),
                            channel = rs.getString("channel"),
                            modDescription = rs.getString("mod_desc"),
                            action = ActionType.parse(rs.getString("action")),
                            timestamp = rs.getLong("timestamp"),
                            evidence = runCatching { rs.getString("evidence") }.getOrNull(),
                        )
                    }
                }
            }
        }
        return list
    }

    private fun mapBan(rs: ResultSet): BanRecord = BanRecord(
        uuid = UUID.fromString(rs.getString("uuid")),
        name = rs.getString("name"),
        reason = rs.getString("reason"),
        createdAt = rs.getLong("created_at"),
        expiresAt = rs.getLong("expires_at"),
    )
}
