package icu.epochcraft.antilitematica.papi

import icu.epochcraft.antilitematica.AntiLitematica
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * PlaceholderAPI 挂钩（可选依赖）。
 *
 * 注册占位符：
 *   %antilitematica_mode%       -> 预设模式
 *   %antilitematica_banned%     -> 当前玩家是否被封禁
 *   %antilitematica_detections% -> 当前玩家检测次数
 *   %antilitematica_channel_count% -> 禁用通道数量
 *
 * @author 阿清
 */
class PlaceholderHook(private val plugin: AntiLitematica) {

    /** 注册占位符（服务端没有 PlaceholderAPI 时静默跳过） */
    fun register() {
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
                plugin.logger.info("未检测到 PlaceholderAPI，跳过占位符注册")
                return
            }
            Expansion(plugin).register()
            plugin.logger.info("PlaceholderAPI 占位符已注册: %antilitematica_*%")
        } catch (e: Throwable) {
            plugin.logger.warning("PlaceholderAPI 注册失败: ${e.message}")
        }
    }

    /** PlaceholderAPI 扩展实现 */
    class Expansion(private val plugin: AntiLitematica) : PlaceholderExpansion() {

        override fun getIdentifier(): String = "antilitematica"

        override fun getAuthor(): String = "阿清"

        override fun getVersion(): String = plugin.description.version

        override fun persist(): Boolean = true

        override fun onPlaceholderRequest(player: Player?, params: String): String? =
            when (params.lowercase()) {
                "mode" -> plugin.configHolder.mode.displayName
                "banned" -> if (player != null && plugin.banManager.isBanned(player.uniqueId)) "true" else "false"
                "detections" -> if (player != null) plugin.database.getDetectionCount(player.uniqueId).toString() else "?"
                "channel_count" -> plugin.configHolder.channels.size.toString()
                else -> null
            }
    }
}
