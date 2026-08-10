package icu.epochcraft.antilitematica.signal

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.event.DetectionType
import org.bukkit.entity.Player
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ProtocolLib 信号检测（需服务端安装 ProtocolLib，由 [SignalFactory] 反射加载）。
 *
 * 信号：
 *   1. EasyPlace 易放模式：USE_ITEM_ON 包的命中向量相对方块坐标偏移异常
 *      （Litematica 易放命中点偏离真实射线），连续 N 次才触发（防误伤）
 *   2. NBT 查询风暴：TILE/ENTITY_NBT_QUERY 高频查询（默认关闭 + 频率门控，
 *      修复旧版无条件惩罚导致 F3 查看方块信息被误踢的 bug）
 *
 * 实现说明：本类不引用任何 ProtocolLib 类型（全反射 + Proxy 实现 PacketListener），
 * 因此编译期无 ProtocolLib 依赖，服务端未安装时由 [SignalFactory] 直接跳过。
 *
 * @author 阿清
 */
class ProtocolLibSignalDetector(private val plugin: AntiLitematica) {

    private var manager: Any? = null
    private var listener: Any? = null

    /** EasyPlace 连续命中计数 */
    private data class EpCounter(var count: Int = 0, var lastHit: Long = 0)
    private val epCounters = ConcurrentHashMap<UUID, EpCounter>()
    private val EP_WINDOW_MS = 10_000L

    /** NBT 查询频率计数 */
    private data class NbtCounter(var count: Int = 0, var last: Long = 0)
    private val nbtCounters = ConcurrentHashMap<UUID, NbtCounter>()
    private val NBT_WINDOW_MS = 5_000L

    // ---- 配置缓存 ----
    private var epEnabled = false
    private var epCancel = true
    private var epRelMin = -0.5
    private var epRelMax = 1.5
    private var epMinConsecutive = 3
    private var nbtEnabled = false
    private var nbtAllowOp = true
    private var nbtCancel = true
    private var nbtThreshold = 15

    /** 启动监听（由 SignalFactory 在确认 ProtocolLib 存在后调用） */
    fun start() {
        reload()

        // 反射获取 ProtocolManager
        val library = Class.forName("com.comphenix.protocol.ProtocolLibrary")
        manager = library.getMethod("getProtocolManager").invoke(null)

        // 反射获取 PacketType 静态字段
        val playClient = Class.forName("com.comphenix.protocol.PacketType\$Play\$Client")
        val useItemOn = playClient.getField("USE_ITEM_ON").get(null)
        val tileNbtQuery = playClient.getField("TILE_NBT_QUERY").get(null)
        val entityNbtQuery = playClient.getField("ENTITY_NBT_QUERY").get(null)

        val whitelist: Set<Any> = setOf(useItemOn, tileNbtQuery, entityNbtQuery)

        // Proxy 实现 PacketListener 接口（避免继承抽象类 PacketAdapter）
        val listenerClass = Class.forName("com.comphenix.protocol.events.PacketListener")
        val eventClass = Class.forName("com.comphenix.protocol.events.PacketEvent")
        val getPlayer = eventClass.getMethod("getPlayer")
        val getPacketType = eventClass.getMethod("getPacketType")
        val getPacket = eventClass.getMethod("getPacket")
        val setCancelled = eventClass.getMethod("setCancelled", Boolean::class.javaPrimitiveType)

        listener = Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass),
        ) { _, method, args ->
            when (method.name) {
                "onPacketReceiving" -> {
                    runCatching {
                        handleReceiving(args[0], getPlayer, getPacketType, getPacket, setCancelled, useItemOn, tileNbtQuery, entityNbtQuery)
                    }
                    null
                }
                "onPacketSending" -> null
                "getPlugin" -> plugin
                "getSendingWhitelist" -> emptySet<Any>()
                "getReceivingWhitelist" -> whitelist
                else -> null
            }
        }

        manager!!.javaClass.getMethod("addPacketListener", listenerClass).invoke(manager, listener)
        plugin.logger.info("ProtocolLib 信号检测已启用 (EasyPlace=${epEnabled}, NBT=${nbtEnabled})")
    }

    fun shutdown() {
        runCatching {
            if (manager != null && listener != null) {
                val listenerClass = Class.forName("com.comphenix.protocol.events.PacketListener")
                manager!!.javaClass.getMethod("removePacketListener", listenerClass).invoke(manager, listener)
            }
        }
        listener = null
        manager = null
        epCounters.clear()
        nbtCounters.clear()
    }

    /** reload 配置缓存 */
    fun reload() {
        val s = plugin.configHolder.signals
        epEnabled = s.easyPlaceEnabled
        epCancel = s.easyPlaceCancel
        epRelMin = s.easyPlaceRelMin
        epRelMax = s.easyPlaceRelMax
        epMinConsecutive = s.easyPlaceMinConsecutive.coerceAtLeast(2)
        nbtEnabled = s.nbtQueryEnabled
        nbtAllowOp = s.nbtQueryAllowOp
        nbtCancel = s.nbtQueryCancel
        nbtThreshold = s.nbtQueryThreshold.coerceAtLeast(3)
    }

    // ---------------- 包分发 ----------------

    private fun handleReceiving(
        event: Any,
        getPlayer: Method,
        getPacketType: Method,
        getPacket: Method,
        setCancelled: Method,
        useItemOn: Any,
        tileNbtQuery: Any,
        entityNbtQuery: Any,
    ) {
        val player = getPlayer.invoke(event) as? Player ?: return
        if (player.hasPermission("antilitematica.bypass")) return
        val type = getPacketType.invoke(event)

        when {
            type == useItemOn -> handleEasyPlace(event, player, getPacket, setCancelled)
            type == tileNbtQuery || type == entityNbtQuery -> handleNbtQuery(event, player, setCancelled)
        }
    }

    // ---------------- EasyPlace 易放模式 ----------------

    private fun handleEasyPlace(event: Any, player: Player, getPacket: Method, setCancelled: Method) {
        if (!epEnabled) return
        if (!detectAbnormalHitVec(getPacket.invoke(event))) {
            epCounters.remove(player.uniqueId) // 正常放置清零
            return
        }
        val now = System.currentTimeMillis()
        val counter = epCounters.computeIfAbsent(player.uniqueId) { EpCounter() }
        if (now - counter.lastHit > EP_WINDOW_MS) counter.count = 0
        counter.count++
        counter.lastHit = now

        // 连续 N 次异常命中才判定（防误伤）
        if (counter.count >= epMinConsecutive) {
            counter.count = 0
            if (epCancel) runCatching { setCancelled.invoke(event, true) }
            emit(player, "litematica:easy_place", "异常命中向量（易放模式特征）")
        }
    }

    /** 命中向量相对方块坐标的偏移超出合理范围 = EasyPlace 特征 */
    private fun detectAbnormalHitVec(packetContainer: Any?): Boolean {
        if (packetContainer == null) return false
        return try {
            val getMovingBlockPositions = packetContainer.javaClass.getMethod("getMovingBlockPositions")
            val list = getMovingBlockPositions.invoke(packetContainer) as? List<*> ?: return false
            val mop = list.firstOrNull() ?: return false

            val getBlockPosition = mop.javaClass.getMethod("getBlockPosition")
            val getPosVector = mop.javaClass.getMethod("getPosVector")
            val pos = getBlockPosition.invoke(mop) ?: return false
            val vec = getPosVector.invoke(mop) as? org.bukkit.util.Vector ?: return false

            val posClass = pos.javaClass
            val x = posClass.getMethod("getX").invoke(pos) as Int
            val y = posClass.getMethod("getY").invoke(pos) as Int
            val z = posClass.getMethod("getZ").invoke(pos) as Int

            val dx = vec.x - x
            val dy = vec.y - y
            val dz = vec.z - z
            dx < epRelMin || dx > epRelMax || dy < epRelMin || dy > epRelMax || dz < epRelMin || dz > epRelMax
        } catch (e: Exception) {
            false
        }
    }

    // ---------------- NBT 查询风暴 ----------------

    private fun handleNbtQuery(event: Any, player: Player, setCancelled: Method) {
        if (!nbtEnabled) return
        if (nbtAllowOp && player.isOp) return

        val now = System.currentTimeMillis()
        val counter = nbtCounters.computeIfAbsent(player.uniqueId) { NbtCounter() }
        if (now - counter.last > NBT_WINDOW_MS) counter.count = 0
        counter.count++
        counter.last = now

        // 频率门控：窗口内超过阈值才触发（修复旧版无条件惩罚）
        if (counter.count >= nbtThreshold) {
            counter.count = 0
            if (nbtCancel) runCatching { setCancelled.invoke(event, true) }
            emit(player, "minecraft:tag_query", "NBT 查询风暴")
        }
    }

    // ---------------- 发射到统一管线 ----------------

    private fun emit(player: Player, channel: String, reason: String) {
        plugin.detectionBus.emit(player, channel, reason, DetectionType.SIGNAL)
    }
}
