package icu.epochcraft.antilitematica.api

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.database.BanRecord
import icu.epochcraft.antilitematica.detection.ActionType
import icu.epochcraft.antilitematica.event.DetectionEvent
import icu.epochcraft.antilitematica.event.DetectionType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * AntiLitematica 公开 API（供附属插件 / 外部系统调用）。
 *
 * 获取方式（Java）：
 * ```java
 * AntiLitematicaAPI api = AntiLitematicaAPI.get();
 * if (api != null) { ... }
 * ```
 *
 * 功能：
 *   - 基础信息：版本 / 语言 / 预设模式 / 封禁后端
 *   - 通道配置：查询 / 添加 / 移除禁用通道
 *   - 封禁管理：封禁 / 解封 / 查询
 *   - 检测联动：主动 flag 玩家、注册检测监听器（等价于监听 [DetectionEvent]）
 *   - 误报豁免、统计查询
 *
 * @author 阿清
 */
class AntiLitematicaAPI private constructor(private val plugin: AntiLitematica) {

    /** 注册的检测监听器 */
    private val listeners = CopyOnWriteArrayList<DetectionListener>()

    /** 内部 Bukkit 监听器（转发 DetectionEvent -> DetectionListener） */
    private val eventBridge = EventBridge()

    // 基础信息

    /** 插件版本号 */
    fun getPluginVersion(): String = plugin.description.version

    /** 当前语言代码（如 zh_cn / en_us） */
    fun getLanguage(): String = plugin.langManager.currentLang

    /** 当前预设模式（strict / normal / lite） */
    fun getMode(): String = plugin.configHolder.mode.name.lowercase()

    /** 当前封禁后端名称（内置SQLite / LiteBans / AdvancedBan） */
    fun getBanBackendName(): String = plugin.banManager.backendName

    // 通道配置

    /** 全部禁用通道（只读副本） */
    fun getChannels(): Map<String, ChannelConfig> =
        plugin.configHolder.channels.mapValues { (channel, cfg) ->
            ChannelConfig(channel, cfg.action.name, cfg.banDuration)
        }

    /** 查询通道动作（KICK / BAN / WARN / LOG；通道不存在返回默认 KICK） */
    fun getChannelAction(channel: String): String = plugin.configHolder.getAction(channel).name

    /** 查询通道封禁时长（毫秒，仅 BAN 动作有意义） */
    fun getChannelBanDuration(channel: String): Long =
        plugin.configHolder.channels[channel.lowercase()]?.banDuration ?: plugin.configHolder.autoBanDuration

    /** 添加禁用通道（action: KICK / BAN / WARN / LOG，非法值按 KICK 处理） */
    fun addChannel(channel: String, action: String): Boolean =
        plugin.configHolder.addChannel(channel, ActionType.parse(action))

    /** 移除禁用通道 */
    fun removeChannel(channel: String): Boolean = plugin.configHolder.removeChannel(channel)

    // 封禁管理

    /** 封禁玩家（无玩家名） */
    fun banPlayer(uuid: UUID, reason: String, durationMillis: Long) {
        plugin.banManager.ban(uuid, uuid.toString(), reason, durationMillis)
    }

    /** 封禁玩家（带玩家名） */
    fun banPlayer(uuid: UUID, name: String, reason: String, durationMillis: Long) {
        plugin.banManager.ban(uuid, name, reason, durationMillis)
    }

    /** 解封玩家 */
    fun unbanPlayer(uuid: UUID) = plugin.banManager.unban(uuid)

    /** 玩家是否被封禁 */
    fun isBanned(uuid: UUID): Boolean = plugin.banManager.isBanned(uuid)

    /** 查询玩家当前封禁信息（未封禁返回 null） */
    fun getBanInfo(uuid: UUID): BanInfo? =
        plugin.banManager.getActiveBan(uuid)?.let { toBanInfo(it) }

    /** 全部有效封禁 */
    fun getAllBans(): List<BanInfo> =
        plugin.banManager.getAllBans().map { toBanInfo(it) }

    // 检测联动

    /**
     * 主动触发一次检测（走完整惩罚管线：渐进惩罚 → 基础动作 → 通知）。
     *
     * @return true 表示有处理链认领
     */
    fun flagPlayer(player: Player, channel: String, reason: String, detectionType: DetectionType): Boolean =
        plugin.detectionBus.emit(player, channel, reason, detectionType)

    /** 便捷重载：以 CHANNEL 类型触发检测 */
    fun flagPlayer(player: Player, channel: String): Boolean =
        flagPlayer(player, channel, "检测到禁用 Mod", DetectionType.CHANNEL)

    /** 注册检测监听器（每次检测命中回调，主线程同步） */
    fun addDetectionListener(listener: DetectionListener) {
        if (listeners.addIfAbsent(listener)) {
            ensureBridgeRegistered()
        }
    }

    /** 移除检测监听器 */
    fun removeDetectionListener(listener: DetectionListener) {
        listeners.remove(listener)
    }

    // 误报豁免

    /** 将该玩家标记为误报（其命中过的所有通道豁免） */
    fun forgivePlayer(uuid: UUID) {
        val channels = plugin.database.getDetectionsOf(uuid, 20).map { it.channel }.distinct()
        channels.forEach { plugin.database.addFalsePositive(uuid, it.removePrefix("brand:")) }
    }

    /** 该玩家 + 通道组合是否已豁免 */
    fun isForgiven(uuid: UUID, channel: String): Boolean =
        plugin.database.isFalsePositive(uuid, channel.removePrefix("brand:"))

    // 统计

    /** 检测总数 */
    fun getTotalDetections(): Int = plugin.database.getAllDetectionsCount()

    /** 指定玩家检测次数 */
    fun getDetectionCount(uuid: UUID): Int = plugin.database.getDetectionCount(uuid)

    /** 通道命中分布 */
    fun getChannelStats(): Map<String, Int> = plugin.database.getChannelStats()

    // 内部

    private fun toBanInfo(record: BanRecord): BanInfo {
        val permanent = record.expiresAt == BanRecord.PERMANENT
        val remaining = if (permanent) -1L
        else maxOf(0L, record.expiresAt - System.currentTimeMillis())
        return BanInfo(
            uuid = record.uuid,
            name = record.name,
            reason = record.reason,
            createdAt = record.createdAt,
            expiresAt = record.expiresAt,
            isPermanent = permanent,
            expiresInMillis = remaining,
        )
    }

    /** 首次注册监听器时才注册 Bukkit 桥接 */
    private fun ensureBridgeRegistered() {
        if (bridgeRegistered) return
        plugin.server.pluginManager.registerEvents(eventBridge, plugin)
        bridgeRegistered = true
    }

    private var bridgeRegistered = false

    /** 转发 DetectionEvent -> API 监听器 */
    private inner class EventBridge : Listener {
        @EventHandler
        fun onDetection(event: DetectionEvent) {
            val info = DetectionInfo(
                player = event.player,
                channel = event.channel,
                reason = event.reason,
                detectionType = event.detectionType,
                timestamp = System.currentTimeMillis(),
            )
            listeners.forEach { it.onDetection(info) }
        }
    }

    companion object {

        @Volatile
        private var instance: AntiLitematicaAPI? = null

        /**
         * 获取 API 实例。
         *
         * @return 插件已加载时返回实例，否则返回 null（建议判空）
         */
        @JvmStatic
        fun get(): AntiLitematicaAPI? = instance

        /** 插件是否已加载且 API 可用 */
        @JvmStatic
        fun isLoaded(): Boolean = instance != null

        /** 插件 onEnable 时初始化（内部调用） */
        internal fun init(plugin: AntiLitematica) {
            instance = AntiLitematicaAPI(plugin)
        }

        /** 插件 onDisable 时清理（内部调用） */
        internal fun shutdown() {
            instance = null
        }
    }
}
