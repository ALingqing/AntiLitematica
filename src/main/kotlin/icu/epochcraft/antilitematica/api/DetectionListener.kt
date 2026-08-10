package icu.epochcraft.antilitematica.api

/**
 * 检测监听器：每次检测命中时回调（同步，主线程）。
 *
 * 通过 [AntiLitematicaAPI.addDetectionListener] 注册。
 * 与 Bukkit 事件 [icu.epochcraft.antilitematica.event.DetectionEvent] 等价，
 * 但无需自己注册 Listener，且自带处理动作上下文无关的轻量信息。
 *
 * Java 中可直接用 lambda：`api.addDetectionListener(info -> { ... })`
 */
fun interface DetectionListener {

    /** 检测命中回调 */
    fun onDetection(info: DetectionInfo)
}
