package icu.epochcraft.antilitematica.integration

import icu.epochcraft.antilitematica.AntiLitematica
import org.bukkit.entity.Player

/**
 * 反作弊集成管理器：按配置偏好探测并接入 Grim / Vulcan / Matrix。
 *
 * @author 阿清
 */
class IntegrationManager(private val plugin: AntiLitematica) {

    /** 当前生效的集成（未探测到则为 NoOp） */
    private var current: AntiCheatIntegration = NoOp

    /** 是否有生效的集成 */
    val isActive: Boolean get() = current != NoOp

    /** 当前集成名称（未接入时为 "none"，供 bStats 统计） */
    val currentName: String get() = if (current != NoOp) current.name else "none"

    /** 启动时探测 */
    fun init() {
        val candidates = when (plugin.configHolder.antiCheatIntegration.lowercase()) {
            "grim" -> listOf(GrimIntegration(), VulcanIntegration(), MatrixIntegration())
            "vulcan" -> listOf(VulcanIntegration(), GrimIntegration(), MatrixIntegration())
            "matrix" -> listOf(MatrixIntegration(), GrimIntegration(), VulcanIntegration())
            else -> listOf(GrimIntegration(), VulcanIntegration(), MatrixIntegration())
        }
        for (candidate in candidates) {
            if (candidate.isAvailable()) {
                current = candidate
                plugin.logger.info("已接入反作弊联动: ${candidate.name}")
                return
            }
        }
        plugin.logger.info("未检测到 GrimAC / Vulcan / Matrix，反作弊联动关闭")
    }

    /** 上报违规 */
    fun flag(player: Player, check: String, level: Int, detail: String) {
        if (current != NoOp) {
            current.flag(player, check, level, detail)
        }
    }

    /** 空实现 */
    private object NoOp : AntiCheatIntegration {
        override val name: String = "NoOp"
        override fun isAvailable(): Boolean = false
        override fun flag(player: Player, check: String, level: Int, detail: String) = Unit
    }
}
