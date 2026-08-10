package icu.epochcraft.antilitematica.detection

import icu.epochcraft.antilitematica.AntiLitematica
import org.bukkit.entity.Player

/**
 * 检测来源。
 *
 * @author 阿清
 */
enum class DetectionSource {
    /** 插件通道（minecraft:register） */
    CHANNEL,

    /** 客户端 Brand（minecraft:brand 载荷） */
    BRAND,

    /** FML / Fabric 握手 Mod 列表解析 */
    MOD_LIST,
}
