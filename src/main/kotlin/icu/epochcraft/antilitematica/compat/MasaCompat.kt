package icu.epochcraft.antilitematica.compat

import icu.epochcraft.antilitematica.AntiLitematica
import org.bukkit.entity.Player
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * MasaMods（Masa 家族模组）兼容管理。
 *
 * Masa 家族包括：
 *   - Litematica（投影）—— 本插件主要检测对象
 *   - Tweakeroo（推客入）—— 灵活放置 / 强制放置等辅助功能
 *   - MiniHUD —— 显示信息叠加层，无检测冲突
 *   - ItemScroller —— 物品快捷移动，无检测冲突
 *
 * 通过插件通道识别玩家使用的 Masa 模组，并依据 config.yml 的
 * `compatibility.tweakeroo-mode` 调整检测灵敏度，减少对正常玩家的误报：
 *   - Tweakeroo 用户跳过射线校验（flexi placement 修改命中向量）
 *   - EasyPlace 信号阈值 3 → 8 次
 *   - 连续同类型放置阈值提高
 *
 * @author 阿清
 */
class MasaCompat(private val plugin: AntiLitematica) {

    /** 玩家 UUID -> 检测到的 Masa 模组集合 */
    private val playerMods = ConcurrentHashMap<UUID, MasaMods>()

    /**
     * 记录玩家注册的 Masa 模组通道（由通道监听器调用）。
     * @return true 如果是已知的 Masa 通道
     */
    fun detectChannel(player: Player, channel: String): Boolean {
        val lower = channel.lowercase(Locale.ROOT)
        if (lower !in MASA_CHANNELS) return false
        val mods = playerMods.computeIfAbsent(player.uniqueId) { MasaMods() }
        when {
            lower in TWEAKEROO_CHANNELS -> mods.tweakeroo = true
            lower in LITEMATICA_CHANNELS -> mods.litematica = true
            lower == "minihud:hello" -> mods.minihud = true
            lower == "itemscroller:hello" -> mods.itemScroller = true
        }
        return true
    }

    /** 清除玩家的模组记录（退出时调用） */
    fun clearPlayer(player: Player) {
        playerMods.remove(player.uniqueId)
    }

    fun clearPlayer(uuid: UUID) {
        playerMods.remove(uuid)
    }

    /** 玩家是否使用 Tweakeroo */
    fun hasTweakeroo(player: Player): Boolean = playerMods[player.uniqueId]?.tweakeroo == true

    // ======================== 兼容策略 ========================

    /** 是否跳过射线校验（Tweakeroo 柔性放置会偏离真实射线） */
    fun shouldSkipRaytrace(player: Player): Boolean =
        plugin.configHolder.tweakerooMode && hasTweakeroo(player)

    /** EasyPlace 信号最低连续命中次数（tweakeroo 模式 3 → 8，减少误报） */
    fun easyPlaceThreshold(default: Int): Int =
        if (plugin.configHolder.tweakerooMode) 8 else default

    /** 连续同类型放置阈值（tweakeroo 模式提高，建筑玩家不易误报） */
    fun consecutiveThreshold(default: Int): Int =
        if (plugin.configHolder.tweakerooMode) default + 4 else default

    private class MasaMods {
        var litematica = false
        var tweakeroo = false
        var minihud = false
        var itemScroller = false
    }

    companion object {
        /** 已知 Masa 模组插件通道 */
        private val MASA_CHANNELS = setOf(
            "servux:litematics", "servux:litematica",
            "litematica:hello", "litematica:main", "litematica:place",
            "tweakeroo:hello", "tweakeroo:main",
            "minihud:hello", "itemscroller:hello",
        )

        private val TWEAKEROO_CHANNELS = setOf("tweakeroo:hello", "tweakeroo:main")

        private val LITEMATICA_CHANNELS = setOf(
            "servux:litematics", "servux:litematica",
            "litematica:hello", "litematica:main", "litematica:place",
        )
    }
}
