package icu.epochcraft.antilitematica.util

import org.bukkit.entity.Player
import java.util.UUID

/**
 * 基岩版玩家检测（Geyser / Floodgate）。
 *
 * 基岩版客户端不存在 Litematica / Schematica 等投影 mod，
 * 检测到基岩版玩家时全部豁免，从源头消除误报。
 *
 * 检测方式：
 *   1. Floodgate API（最可靠，反射调用零依赖）
 *   2. Geyser 默认玩家名前缀 `.`（兜底）
 *
 * @author 阿清
 */
object BedrockPlayerDetector {

    @Volatile
    private var floodgateChecked: Boolean? = null
    private var floodgateApiClass: Class<*>? = null

    /** 玩家是否为基岩版玩家（Geyser 接入） */
    fun isBedrockPlayer(player: Player?): Boolean {
        if (player == null) return false
        return hasFloodgateApi(player) || hasGeyserPrefix(player)
    }

    /** Floodgate API 反射检测 */
    private fun hasFloodgateApi(player: Player): Boolean {
        try {
            if (floodgateChecked == null) {
                floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                floodgateChecked = true
            }
            val apiClass = floodgateApiClass
            if (apiClass != null) {
                val instance = apiClass.getMethod("getInstance").invoke(null)
                return instance.javaClass.getMethod("isFloodgatePlayer", UUID::class.java)
                    .invoke(instance, player.uniqueId) as Boolean
            }
        } catch (e: Exception) {
            floodgateChecked = false
            floodgateApiClass = null
        }
        return false
    }

    /** 兜底：Geyser 默认玩家名前缀 `.`（可在 Geyser 配置中修改） */
    private fun hasGeyserPrefix(player: Player): Boolean =
        player.name?.startsWith(".") == true
}
