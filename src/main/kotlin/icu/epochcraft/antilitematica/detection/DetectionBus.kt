package icu.epochcraft.antilitematica.detection

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.event.DetectionEvent
import icu.epochcraft.antilitematica.event.DetectionType
import icu.epochcraft.antilitematica.util.BedrockPlayerDetector
import org.bukkit.entity.Player
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 检测事件总线：解耦"检测源"与"惩罚处理"。
 *
 * 流程：
 *   检测源（通道检测 / 信号检测 / 防 Printer / 命令防护 …）调用 [emit]
 *   → 先 fire [DetectionEvent]（Bukkit 事件，外部插件可取消）
 *   → 再按注册顺序交给 [DetectionHandler] 链，第一个返回 true 的认领
 *
 * 这是旧版 Java 版 DetectionBus 的 Kotlin 移植，并修复：
 *   1. 取消事件不消耗冷却（不会吞掉同会话后续检测）
 *   2. 所有检测源统一走本总线（惩罚路径唯一化）
 *
 * @author 阿清
 */
class DetectionBus(private val plugin: AntiLitematica) {

    private val handlers = CopyOnWriteArrayList<DetectionHandler>()

    /** 注册检测处理器 */
    fun register(handler: DetectionHandler) {
        if (handler != null) handlers += handler
    }

    /** 移除检测处理器 */
    fun unregister(handler: DetectionHandler) {
        handlers -= handler
    }

    /** 清空所有处理器 */
    fun clear() {
        handlers.clear()
    }

    /**
     * 发射一次检测。
     *
     * @param ctx 检测上下文
     * @return true 表示有处理器认领
     */
    fun emit(ctx: DetectionContext): Boolean {
        // 基岩版玩家豁免（Geyser，基岩版无 Litematica，从源头消除误报）
        if (plugin.configHolder.bedrockExempt && BedrockPlayerDetector.isBedrockPlayer(ctx.player)) {
            plugin.logger.fine("基岩版玩家豁免: ${ctx.player.name}")
            return false
        }

        // 1. fire Bukkit 事件（外部插件可取消）
        val event = DetectionEvent(ctx.player, ctx.channel, ctx.reason, ctx.detectionType)
        plugin.server.pluginManager.callEvent(event)
        if (event.isCancelled) {
            plugin.logger.fine("检测被外部插件取消: ${ctx.player.name} ${ctx.channel}")
            return false
        }

        // 2. 交给处理链
        for (handler in handlers) {
            if (handler.handle(ctx)) return true
        }
        return false
    }

    /** 便捷：构造上下文并发射 */
    fun emit(player: Player, channel: String, reason: String, type: DetectionType): Boolean =
        emit(DetectionContext(player, channel, reason, type))
}
