package icu.epochcraft.antilitematica.detection

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.event.DetectionType
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 通道检测服务（检测源之一）。
 *
 * 职责：命中判断 + 误报/冷却检查 + 发射到统一检测总线。
 * 实际的惩罚执行（渐进惩罚 / 基础动作）由总线处理链负责：
 *   检测总线 → [GraduatedPunisher]（渐进，可选）→ [DetectionPunisher]（基础动作兜底）
 *
 * 相对旧版的关键修复：只用冷却防 spam，不用"已处理标记"——冷却过期后再次检测
 * 会再次计数，渐进惩罚才能正常升级（旧版 markPunished 吞检测的 bug 已消除）。
 *
 * @author 阿清
 */
class ModDetectionService(
    private val plugin: AntiLitematica,
) {

    /** 短时间重复检测冷却表：uuid -> 上次发射时间戳 */
    private val lastEmittedAt = ConcurrentHashMap<UUID, Long>()

    /** 从一组通道中找出第一个命中的禁用通道 */
    fun findBannedChannel(channels: Collection<String>): String? =
        channels.firstOrNull { it.lowercase() in plugin.configHolder.channels.keys }

    /** 检测命中统一入口：误报/冷却检查后发射到检测总线 */
    fun handleDetection(
        player: Player,
        channel: String,
        source: DetectionSource,
        reason: String? = null,
        evidence: String? = null,
    ) {
        val cfg = plugin.configHolder
        val uuid = player.uniqueId

        // 1. 误报检查：该玩家 + 该通道已被管理员标记为误报则仅记录
        if (plugin.database.isFalsePositive(uuid, channel)) {
            plugin.detectionPunisher.recordLogOnly(player, channel, typeOf(source), evidence = evidence)
            return
        }

        // 2. 冷却检查：避免同一玩家短时间被反复处理
        val now = System.currentTimeMillis()
        val last = lastEmittedAt[uuid]
        if (last != null && now - last < cfg.detectionCooldownMillis) {
            return
        }

        // 3. 发射到统一检测总线（惩罚由管线决定：渐进 / 基础动作）
        lastEmittedAt[uuid] = now
        plugin.detectionBus.emit(player, channel, reason ?: banReason(channel), typeOf(source), evidence)
    }

    private fun typeOf(source: DetectionSource): DetectionType = when (source) {
        DetectionSource.BRAND -> DetectionType.BRAND
        DetectionSource.MOD_LIST -> DetectionType.MOD_LIST
        DetectionSource.CHANNEL -> DetectionType.CHANNEL
    }

    private fun banReason(channel: String): String =
        plugin.configHolder.lang("detection.ban-reason").replace("{channel}", channel)
}
