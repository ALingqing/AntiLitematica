package icu.epochcraft.antilitematica.detection

/**
 * 检测处理器：注册到 [DetectionBus] 的处理链。
 *
 * 按注册顺序依次尝试；返回 true 表示认领本次检测（后续处理器跳过）。
 *
 * @author 阿清
 */
fun interface DetectionHandler {

    /**
     * @param ctx 检测上下文
     * @return true 认领（后续处理器跳过），false 放行给下一个
     */
    fun handle(ctx: DetectionContext): Boolean
}
