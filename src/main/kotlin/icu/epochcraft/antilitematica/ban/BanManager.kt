package icu.epochcraft.antilitematica.ban

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.database.BanRecord
import icu.epochcraft.antilitematica.database.DetectionDatabase
import icu.epochcraft.antilitematica.util.Scheduler
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import java.util.UUID

/**
 * 封禁管理：封禁 / 解封 / 登录拦截 / 过期自动清理。
 *
 * 支持封禁后端联动（config.yml 的 ban-backend）：
 *   - 内置 SQLite（默认）
 *   - LiteBans / AdvancedBan（外部后端，双写：外部插件 + 本地数据库，
 *     保证本插件的 list / stats / 登录拦截始终一致）
 *
 * @author 阿清
 */
class BanManager(
    private val plugin: AntiLitematica,
    private val database: DetectionDatabase,
) : Listener {

    /** 当前封禁后端（按配置自动探测） */
    val backend: BanBackend = BanBackendFactory.create(plugin, database, plugin.configHolder.banBackend)

    /** 当前后端名称（日志用） */
    val backendName: String get() = backend.name

    /** 是否为内置后端 */
    private val isInternal: Boolean get() = backend is InternalBanBackend

    /** 定时清理过期封禁的调度句柄 */
    private var purgeTask: Scheduler.TaskHandle? = null

    /** 开始定时任务（每 5 分钟清理一次过期封禁） */
    fun start() {
        purgeTask = Scheduler.asyncTimer(plugin, 20L * 60 * 5, 20L * 60 * 5) {
            val removed = database.purgeExpiredBans()
            if (removed > 0) {
                plugin.logger.info("已自动解封 $removed 名玩家（封禁到期）")
            }
        }
    }

    fun stop() {
        purgeTask?.cancel()
        purgeTask = null
    }

    /**
     * 封禁玩家（双写：外部后端 + 本地数据库）。
     * @param durationMillis 时长（毫秒），[BanRecord.PERMANENT] 表示永久
     */
    fun ban(uuid: UUID, name: String, reason: String, durationMillis: Long) {
        val expiresAt = if (durationMillis == BanRecord.PERMANENT) BanRecord.PERMANENT
        else System.currentTimeMillis() + durationMillis

        backend.ban(uuid, name, reason, durationMillis)

        // 外部后端联动时同步写本地数据库（list / stats / 登录拦截统一查询）
        if (!isInternal) {
            database.insertBan(BanRecord(uuid, name, reason, expiresAt = expiresAt))
        }

        plugin.logger.warning("已封禁 $name ($backendName): $reason")

        // 踢出在线玩家
        Bukkit.getPlayer(uuid)?.let { player ->
            Scheduler.entity(player, plugin) {
                if (player.isOnline) {
                    val msg = plugin.configHolder.lang("ban.kick")
                        .replace("{reason}", reason)
                        .replace("{expires}", if (expiresAt == BanRecord.PERMANENT) "永久" else formatRemaining(expiresAt))
                    player.kickPlayer(msg)
                }
            }
        }
    }

    /** 解封（外部后端 + 本地数据库双清） */
    fun unban(uuid: UUID) {
        backend.unban(uuid)
        if (!isInternal) {
            database.removeBan(uuid)
        }
        plugin.logger.info("已解封 ${uuid} ($backendName)")
    }

    /** 查询玩家当前封禁（已过期返回 null） */
    fun getActiveBan(uuid: UUID): BanRecord? = database.getActiveBan(uuid)

    /** 查询玩家是否被封禁 */
    fun isBanned(uuid: UUID): Boolean = getActiveBan(uuid) != null

    /** 全部有效封禁 */
    fun getAllBans(): List<BanRecord> = database.getAllActiveBans()

    /** 登录拦截：被封禁玩家不允许进入 */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        val ban = database.getActiveBan(event.uniqueId) ?: return
        event.disallow(
            AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
            plugin.configHolder.lang("ban.login")
                .replace("{reason}", ban.reason)
                .replace("{expires}", if (ban.expiresAt == BanRecord.PERMANENT) "永久" else formatRemaining(ban.expiresAt)),
        )
    }

    /** 将剩余时间格式化为人类可读文本 */
    private fun formatRemaining(expiresAt: Long): String {
        val millis = expiresAt - System.currentTimeMillis()
        if (millis <= 0) return "已到期"
        val seconds = millis / 1000
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        return buildString {
            if (days > 0) append("${days}天")
            if (hours > 0) append("${hours}小时")
            if (minutes > 0) append("${minutes}分钟")
            if (isEmpty()) append("${seconds}秒")
        }
    }
}
