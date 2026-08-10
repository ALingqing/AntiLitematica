package icu.epochcraft.antilitematica.api

import icu.epochcraft.antilitematica.event.DetectionType
import org.bukkit.entity.Player

/**
 * 检测信息（API 监听回调参数）。
 *
 * @param player 被检测的玩家
 * @param channel 命中的通道 / 信号名
 * @param reason 处理原因
 * @param detectionType 检测来源类型（CHANNEL / BRAND / PRINTER / COMMAND …）
 * @param timestamp 检测时间戳（毫秒）
 */
class DetectionInfo(
    val player: Player,
    val channel: String,
    val reason: String,
    val detectionType: DetectionType,
    val timestamp: Long,
) {

    override fun toString(): String =
        "DetectionInfo(player=${player.name}, channel=$channel, type=$detectionType)"
}
