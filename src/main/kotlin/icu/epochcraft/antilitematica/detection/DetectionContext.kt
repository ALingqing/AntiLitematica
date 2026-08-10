package icu.epochcraft.antilitematica.detection

import icu.epochcraft.antilitematica.event.DetectionType
import org.bukkit.entity.Player

/**
 * 检测上下文：一次检测命中的全部信息，由检测源 -> [DetectionBus] -> 处理链传递。
 *
 * @author 阿清
 */
data class DetectionContext(
    val player: Player,
    /** 命中的通道 / 信号名 */
    val channel: String,
    /** 处理原因（用于踢出/封禁消息） */
    val reason: String,
    val detectionType: DetectionType,
    /** 检测证据（如握手解析出的完整 mod 列表），用于数据库留存 */
    val evidence: String? = null,
)
