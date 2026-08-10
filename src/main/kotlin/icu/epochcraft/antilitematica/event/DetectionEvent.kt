package icu.epochcraft.antilitematica.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 检测事件（供外部插件扩展）。
 *
 * 每次检测命中都会触发本事件，外部插件可监听并调用 [setCancelled] 阻止处理。
 * 注：取消检测不消耗冷却，玩家可在短时间内再次触发（与旧版 markPunished 吞检测的 bug 划清界限）。
 *
 * @author 阿清
 */
class DetectionEvent(
    val player: Player,
    /** 命中的通道 / 信号名 */
    val channel: String,
    /** 处理原因 */
    val reason: String,
    val detectionType: DetectionType,
) : Event(), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        /**
         * 事件注册表。
         *
         * 注意：必须用 @JvmField 暴露为静态字段，不能写成普通伴生对象属性。
         * 否则 Kotlin 编译器会把 getHandlers() 体内的 `handlers` 解析成
         * getHandlers() 方法自身（属性 getter 与方法同名），导致无限递归 StackOverflowError。
         */
        @JvmField
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}

/** 检测来源类型 */
enum class DetectionType {
    /** 插件通道（minecraft:register） */
    CHANNEL,

    /** 客户端 Brand */
    BRAND,

    /** 防 Printer（连续自动放置） */
    PRINTER,

    /** 命令防护 */
    COMMAND,

    /** NBT 查询风暴 */
    NBT_QUERY,

    /** ProtocolLib 信号（EasyPlace 等） */
    SIGNAL,

    /** 其他 */
    OTHER,
}
