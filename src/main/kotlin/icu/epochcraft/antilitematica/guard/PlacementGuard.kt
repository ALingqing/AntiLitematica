package icu.epochcraft.antilitematica.guard

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.event.DetectionType
import icu.epochcraft.antilitematica.util.TokenBucket
import org.bukkit.FluidCollisionMode
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockMultiPlaceEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 防 Printer（Litematica 打印机自动放置检测）。
 *
 * 检测手段（移植自旧版 PlacementGuard）：
 *   1. 令牌桶限速（max-blocks-per-second）——打印机放置速度远超人类
 *   2. 射线校验（enforce-raytrace）——Tweakeroo 灵活放置会偏离真实射线
 *   3. 连续同类型方块（比例 >= 80%）
 *   4. 视角不变检测（yaw/pitch 无变化连续放置）
 *
 * 与旧版的关键差异：本实现只负责"判定 + 取消事件"，惩罚统一走
 * [icu.epochcraft.antilitematica.detection.DetectionBus]（渐进惩罚或基础动作），
 * 不再有独立的惩罚路径（修复旧版 B5）。
 *
 * @author 阿清
 */
class PlacementGuard(private val plugin: AntiLitematica) : Listener {

    private data class PlayerTracker(
        var bucket: TokenBucket? = null,
        var recentPlacements: ArrayDeque<PlacementSnapshot>? = null,
        var lastYaw: Float? = null,
        var lastPitch: Float? = null,
    )

    private data class PlacementSnapshot(val time: Long, val blockType: String)

    private val trackers = ConcurrentHashMap<UUID, PlayerTracker>()

    // ---- 缓存配置（reload 时刷新） ----
    private var enabled = false
    private var maxBlocksPerSecond = 14
    private var applyToCreative = false
    private var enforceRaytrace = true
    private var detectConsecutive = true
    private var consecutiveThreshold = 8
    private var consecutiveWindowMs = 3000L
    private var detectNoLook = true
    private var reachSurvival = 4.5
    private var reachCreative = 5.0
    private var extraReachAllowance = 0.5

    /** reload 配置缓存 */
    fun reload() {
        val ap = plugin.configHolder.antiPrinter
        enabled = ap.enabled
        maxBlocksPerSecond = ap.maxBlocksPerSecond.coerceAtLeast(1)
        applyToCreative = ap.applyToCreative
        enforceRaytrace = ap.enforceRaytrace
        detectConsecutive = ap.detectConsecutiveSameType
        consecutiveThreshold = ap.consecutiveSameTypeThreshold.coerceAtLeast(3)
        consecutiveWindowMs = ap.consecutiveWindowMs.coerceAtLeast(1000)
        detectNoLook = ap.detectNoLookChange
        reachSurvival = ap.reachSurvival
        reachCreative = ap.reachCreative
        extraReachAllowance = ap.extraReachAllowance
        if (!enabled) trackers.clear()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (event is BlockMultiPlaceEvent) return
        handlePlace(event, event.player, event.blockPlaced, event.blockAgainst, 1)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onMultiPlace(event: BlockMultiPlaceEvent) {
        val count = event.replacedBlockStates.size.coerceAtLeast(1)
        handlePlace(event, event.player, event.blockPlaced, event.blockAgainst, count)
    }

    private fun handlePlace(event: Cancellable, p: Player, placed: Block, against: Block, count: Int) {
        if (!enabled) return
        if (p.hasPermission("antilitematica.bypass")) return
        if (!applyToCreative && p.gameMode == GameMode.CREATIVE) return

        // 1. 限速
        if (maxBlocksPerSecond > 0 && !consume(p, count)) {
            deny(event, p, "rate")
            return
        }
        // 2. 射线校验
        if (enforceRaytrace && !rayTraceMatches(p, placed, against)) {
            deny(event, p, "raytrace")
            return
        }
        // 3. 连续同类型
        if (detectConsecutive && checkConsecutiveSameType(p, placed)) {
            deny(event, p, "consecutive_same")
            return
        }
        // 4. 视角不变
        if (detectNoLook && checkNoLookChange(p)) {
            deny(event, p, "no_look_change")
        }
    }

    /** 判定命中：取消事件并发射到检测总线（统一惩罚管线） */
    private fun deny(event: Cancellable, p: Player, type: String) {
        event.isCancelled = true
        plugin.detectionBus.emit(
            p,
            "printer:$type",
            plugin.configHolder.lang("detection.ban-reason").replace("{channel}", "printer:$type"),
            DetectionType.PRINTER,
        )
    }

    // ---------------- 检测逻辑 ----------------

    private fun consume(p: Player, blocks: Int): Boolean {
        val tracker = trackers.computeIfAbsent(p.uniqueId) { PlayerTracker() }
        if (tracker.bucket == null) {
            tracker.bucket = TokenBucket.perSecond(maxBlocksPerSecond, maxBlocksPerSecond * 2L.toInt().coerceAtLeast(10))
        }
        return tracker.bucket!!.tryConsume(blocks)
    }

    private fun rayTraceMatches(p: Player, placed: Block, against: Block): Boolean {
        val reach = (if (p.gameMode == GameMode.CREATIVE) reachCreative else reachSurvival) + extraReachAllowance
        val result = p.rayTraceBlocks(reach, FluidCollisionMode.NEVER) ?: return false
        val hit = result.hitBlock ?: return false
        return hit == against || hit == placed
    }

    private fun checkConsecutiveSameType(p: Player, placed: Block): Boolean {
        val tracker = trackers.computeIfAbsent(p.uniqueId) { PlayerTracker() }
        val deque = tracker.recentPlacements ?: ArrayDeque<PlacementSnapshot>().also { tracker.recentPlacements = it }
        val now = System.currentTimeMillis()
        val type = placed.type.name

        deque.addLast(PlacementSnapshot(now, type))
        while (deque.isNotEmpty() && now - deque.peekFirst().time > consecutiveWindowMs) {
            deque.pollFirst()
        }
        if (deque.size < consecutiveThreshold) return false

        // 窗口内主导类型占比 >= 80% 判定为打印机（人类建造会混合方块）
        val sameCount = deque.count { it.blockType == type }
        if (sameCount.toDouble() / deque.size >= 0.8) {
            deque.clear()
            return true
        }
        return false
    }

    private fun checkNoLookChange(p: Player): Boolean {
        val tracker = trackers.computeIfAbsent(p.uniqueId) { PlayerTracker() }
        val loc: Location = p.location
        val yaw = loc.yaw
        val pitch = loc.pitch

        val lastYaw = tracker.lastYaw
        val lastPitch = tracker.lastPitch
        tracker.lastYaw = yaw
        tracker.lastPitch = pitch

        // 视角完全没变（差值 < 0.05°）连续放置 = 自动化特征
        return lastYaw != null && lastPitch != null &&
            kotlin.math.abs(yaw - lastYaw) < 0.05f && kotlin.math.abs(pitch - lastPitch) < 0.05f
    }

    // ---------------- 状态清理 ----------------

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        trackers.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        // 换世界重置状态（新世界全新开始）
        trackers[event.player.uniqueId]?.let { t ->
            t.bucket = null
            t.recentPlacements = null
            t.lastYaw = null
            t.lastPitch = null
        }
    }
}
