package icu.epochcraft.antilitematica.guard

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.event.DetectionType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 命令防护：拦截 Litematica quick-paste 命令滥用（如 /setblock 连发）。
 *
 * 逻辑（移植自旧版 CommandGuard，修复 B6）：
 *   - allowed 白名单优先（精确匹配或前缀+空格，不误伤 /setblockinfo 这类）
 *   - 命中 blocked 列表的命令：窗口内 burst 限流，超限判定
 *   - 惩罚统一走检测总线（不再自实现踢人）
 *
 * 默认 blocked 列表为空——由管理员按服配置，避免误伤（旧版默认禁 /setblock 全服）。
 *
 * @author 阿清
 */
class CommandGuard(private val plugin: AntiLitematica) : Listener {

    // ---- 缓存配置 ----
    private var enabled = false
    private var allowedCommands: List<String> = emptyList()
    private var blockedCommands: List<String> = emptyList()
    private var maxPerWindow = 8
    private var windowMs = 2000L

    /** world -> player -> 上次命令时间 */
    private val lastCommandMs = ConcurrentHashMap<String, MutableMap<UUID, Long>>()
    /** world -> player -> 窗口内 burst 计数 */
    private val burstCount = ConcurrentHashMap<String, MutableMap<UUID, Int>>()

    /** reload 配置缓存 */
    fun reload() {
        val cg = plugin.configHolder.commandGuard
        enabled = cg.enabled
        allowedCommands = cg.allowedCommands
        blockedCommands = cg.blockedCommands
        maxPerWindow = cg.maxPerWindow.coerceAtLeast(1)
        windowMs = cg.windowMs.coerceAtLeast(500)
        if (!enabled) {
            lastCommandMs.clear()
            burstCount.clear()
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        if (!enabled) return
        val p = event.player
        if (p.hasPermission("antilitematica.bypass")) return

        val cmdLower = event.message.lowercase()
        val world = p.world.name.lowercase()

        // 1. allowed 白名单优先（精确匹配或 "命令 " 前缀，避免误伤子命令）
        if (allowedCommands.any { a -> cmdLower == a || cmdLower.startsWith("$a ") }) return

        // 2. 是否命中 blocked 列表
        val blocked = blockedCommands.any { b -> cmdLower == b || cmdLower.startsWith("$b ") }
        if (!blocked) return

        // 3. 窗口内 burst 限流
        val now = System.currentTimeMillis()
        val cmdMap = lastCommandMs.computeIfAbsent(world) { ConcurrentHashMap() }
        val burstMap = burstCount.computeIfAbsent(world) { ConcurrentHashMap() }

        val last = cmdMap.put(p.uniqueId, now)
        val burst: Int = if (last != null && now - last < windowMs) {
            burstMap.merge(p.uniqueId, 1, Int::plus) ?: 1
        } else {
            burstMap[p.uniqueId] = 1
            1
        }

        // 连续快速执行 = quick-paste 特征
        if (burst >= maxPerWindow) {
            deny(event, p, "command_burst")
            return
        }
        deny(event, p, "blocked_command")
    }

    /** 判定命中：取消命令并发射到检测总线 */
    private fun deny(event: PlayerCommandPreprocessEvent, p: Player, type: String) {
        event.isCancelled = true
        plugin.detectionBus.emit(
            p,
            "command:$type",
            "命令滥用: $type",
            DetectionType.COMMAND,
        )
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val id = event.player.uniqueId
        lastCommandMs.values.forEach { it.remove(id) }
        burstCount.values.forEach { it.remove(id) }
    }
}
