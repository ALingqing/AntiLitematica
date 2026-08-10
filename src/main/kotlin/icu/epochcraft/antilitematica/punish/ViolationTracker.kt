package icu.epochcraft.antilitematica.punish

import icu.epochcraft.antilitematica.AntiLitematica
import org.bukkit.entity.Player
import java.util.Locale
import java.util.UUID

/**
 * 违规记录跟踪器：记录玩家违规次数，支持时间窗口（窗口外重置计数）。
 *
 * 存储：复用插件的 SQLite 数据库（violations 表，持久化），
 * 附带内存缓存避免频繁查询。
 *
 * @author 阿清
 */
class ViolationTracker(private val plugin: AntiLitematica) {

    /** 复合主键：uuid:world 或 uuid（全局） */
    private fun key(uuid: UUID, world: String?): String =
        if (!world.isNullOrEmpty()) "${uuid}:${world.lowercase(Locale.ROOT)}" else uuid.toString()

    /**
     * 记录一次违规并返回最新记录。
     * 窗口内计数 +1，窗口外重置为 1；totalViolations 始终累计。
     */
    fun recordViolation(player: Player, world: String?): ViolationRecord {
        val now = System.currentTimeMillis()
        val window = plugin.configHolder.graduatedPunishment.windowMinutes * 60_000L

        val cached = cache[key(player.uniqueId, world)]
        val record = if (cached != null && !isExpired(cached.lastViolation, window)) {
            cached.copy(count = cached.count + 1, lastViolation = now, totalViolations = cached.totalViolations + 1)
        } else {
            // 从数据库读取（可能跨重启）
            val fromDb = plugin.database.getViolation(player.uniqueId, world)
            if (fromDb != null && !isExpired(fromDb.lastViolation, window)) {
                fromDb.copy(count = fromDb.count + 1, lastViolation = now, totalViolations = fromDb.totalViolations + 1)
            } else {
                ViolationRecord(
                    uuid = player.uniqueId,
                    playerName = player.name,
                    count = 1,
                    firstViolation = now,
                    lastViolation = now,
                    totalViolations = (fromDb?.totalViolations ?: 0) + 1,
                    world = world,
                )
            }
        }

        cache[key(player.uniqueId, world)] = record
        plugin.database.upsertViolation(record)
        return record
    }

    private fun isExpired(lastViolation: Long, windowMillis: Long): Boolean =
        System.currentTimeMillis() - lastViolation > windowMillis

    /** 内存缓存：复合 key -> 记录 */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, ViolationRecord>()
}
