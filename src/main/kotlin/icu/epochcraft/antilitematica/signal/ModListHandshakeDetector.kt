package icu.epochcraft.antilitematica.signal

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.detection.ActionType
import icu.epochcraft.antilitematica.detection.DetectionSource
import icu.epochcraft.antilitematica.event.DetectionType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * FML / Fabric Mod List 深度解析（需服务端安装 ProtocolLib，由 [HandshakeDetectorFactory] 反射加载）。
 *
 * 与通道检测互补：通道检测只能看到"注册了哪些插件通道"，而本检测器在握手阶段
 * 直接解析客户端上报的**完整 mod 列表**（modid + 版本），即使 mod 不注册插件通道
 * （或客户端禁用通道上报）也能精确识别。
 *
 * 拦截范围（反射获取，按 ProtocolLib 版本可用性自动降级）：
 *   - Play.Client.CUSTOM_PAYLOAD：fml:handshake（1.13-1.20.1）/ fabric:handshake（1.16.2-1.20.4）
 *   - Login.Client.CUSTOM_PAYLOAD / Configuration.Client.CUSTOM_PAYLOAD：
 *     fml:login / fabric:login（1.20.2+ / 1.20.5+，需 ProtocolLib 5.2+）
 *
 * 实现说明：本类不引用任何 ProtocolLib 类型（全反射 + Proxy 实现 PacketListener），
 * 因此编译期无 ProtocolLib 依赖，服务端未安装时由工厂直接跳过。
 *
 * @author 阿清
 */
class ModListHandshakeDetector(private val plugin: AntiLitematica) : Listener {

    private var manager: Any? = null
    private var listener: Any? = null

    // 反射缓存的方法
    private var getPlayer: Method? = null
    private var getPacket: Method? = null
    private var getStrings: Method? = null
    private var getByteArrays: Method? = null

    /** 本次会话已记录过 mod 列表的玩家（防 configuration + play 重复记录） */
    private val recorded = ConcurrentHashMap.newKeySet<UUID>()

    /** 会话 mod 档案：uuid -> 本次握手解析的 mod 列表（供二次验证交叉核对） */
    private val sessionMods = ConcurrentHashMap<UUID, HandshakeParser.ParsedModList>()

    // ---- 配置缓存 ----
    private var enabled = false
    private var bannedModIds: Set<String> = emptySet()
    private var detectModChanges = true
    private var logAll = false

    /** 获取玩家本次握手上报的 mod 列表（无则 null） */
    fun sessionModsOf(player: Player): HandshakeParser.ParsedModList? = sessionMods[player.uniqueId]

    /** 启动监听（由 HandshakeDetectorFactory 在确认 ProtocolLib 存在后调用） */
    fun start() {
        reload()
        if (!enabled) {
            plugin.logger.info("Mod List 深度解析已关闭（config.yml mod-list.enabled）")
            return
        }

        // 反射获取 ProtocolManager
        val library = Class.forName("com.comphenix.protocol.ProtocolLibrary")
        manager = library.getMethod("getProtocolManager").invoke(null)

        // 反射获取各阶段 CUSTOM_PAYLOAD 包类型（按 ProtocolLib 版本可用性降级）
        val whitelist = buildSet<Any> {
            runCatching {
                val playClient = Class.forName("com.comphenix.protocol.PacketType\$Play\$Client")
                add(playClient.getField("CUSTOM_PAYLOAD").get(null))
            }
            runCatching {
                val loginClient = Class.forName("com.comphenix.protocol.PacketType\$Login\$Client")
                add(loginClient.getField("CUSTOM_PAYLOAD").get(null))
            }
            runCatching {
                val configClient = Class.forName("com.comphenix.protocol.PacketType\$Configuration\$Client")
                add(configClient.getField("CUSTOM_PAYLOAD").get(null))
            }
        }
        if (whitelist.isEmpty()) {
            plugin.logger.warning("ProtocolLib 未暴露 CUSTOM_PAYLOAD 包类型，Mod List 深度解析已跳过")
            return
        }

        // Proxy 实现 PacketListener 接口（避免继承抽象类 PacketAdapter）
        val listenerClass = Class.forName("com.comphenix.protocol.events.PacketListener")
        val eventClass = Class.forName("com.comphenix.protocol.events.PacketEvent")
        getPlayer = eventClass.getMethod("getPlayer")
        getPacket = eventClass.getMethod("getPacket")
        val container = Class.forName("com.comphenix.protocol.PacketContainer")
        getStrings = container.getMethod("getStrings")
        getByteArrays = container.getMethod("getByteArrays")

        listener = Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass),
        ) { _, method, args ->
            when (method.name) {
                "onPacketReceiving" -> {
                    runCatching { handleReceiving(args[0]) }
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
        plugin.logger.info(
            "FML/Fabric Mod List 深度解析已启用（阶段=${whitelist.size}, 禁用 mod=${bannedModIds.size}, 变化追踪=${detectModChanges}）"
        )
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
        recorded.clear()
        sessionMods.clear()
    }

    /** reload 配置缓存 */
    fun reload() {
        val m = plugin.configHolder.modList
        enabled = m.enabled
        bannedModIds = m.bannedMods.keys.map { it.lowercase() }.toSet()
        detectModChanges = m.detectModChanges
        logAll = m.logAllModLists
    }

    /** 玩家退出时清理本次会话记录 */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        recorded.remove(uuid)
        sessionMods.remove(uuid)
    }

    // ---------------- 包分发 ----------------

    private fun handleReceiving(event: Any) {
        val player = getPlayer?.invoke(event) as? Player ?: return
        // 握手阶段（Login/Configuration）可能暂无完整玩家，此处为空则跳过
        if (player.hasPermission("antilitematica.bypass")) return

        val packet = getPacket?.invoke(event) ?: return
        val channel = (getStrings?.invoke(packet) as? List<*>)?.firstOrNull() as? String ?: return
        if (!HandshakeParser.isHandshakeChannel(channel)) return
        val data = (getByteArrays?.invoke(packet) as? List<*>)?.firstOrNull() as? ByteArray ?: return

        val parsed = HandshakeParser.parse(channel, data) ?: return
        handleParsed(player, parsed)
    }

    private fun handleParsed(player: Player, parsed: HandshakeParser.ParsedModList) {
        val uuid = player.uniqueId
        // 会话档案（供二次验证交叉核对 + mod 变化追踪）
        sessionMods[uuid] = parsed

        // 1. 命中黑名单 modid -> 走统一检测管线（误报/冷却由 ModDetectionService 负责）
        val hit = parsed.mods.keys.firstOrNull { it in bannedModIds }
        if (hit != null) {
            val version = parsed.mods[hit]
            plugin.detectionService.handleDetection(
                player = player,
                channel = "mod:$hit",
                source = DetectionSource.MOD_LIST,
                reason = "检测到 Mod: $hit${version?.let { " v$it" } ?: ""}（握手 Mod 列表解析）",
                evidence = parsed.summary(),
            )
        } else if (detectModChanges) {
            // 2. 变化追踪：历史有禁用 mod 但本次握手未上报 -> 疑似进服前卸载
            val history = plugin.database.getPlayerMods(uuid)
            val hidden = history.keys.firstOrNull { it in bannedModIds && it !in parsed.mods }
            if (hidden != null) {
                plugin.detectionService.handleDetection(
                    player = player,
                    channel = "mod:hide:$hidden",
                    source = DetectionSource.MOD_LIST,
                    reason = "历史安装过禁用 Mod: $hidden，本次握手未上报（疑似进服前卸载）",
                    evidence = "历史: ${history.entries.joinToString(", ") { "${it.key}:${it.value}" }} | 本次: ${parsed.summary()}",
                )
            }
        }

        // 3. 持久化 mod 档案（每次握手都更新，供变化追踪跨会话对比）
        plugin.database.upsertPlayerMods(uuid, parsed.mods)

        // 4. 未命中黑名单但开启审计：每次会话记录一条（LOG，不处罚）
        if (hit == null && logAll && recorded.add(uuid)) {
            plugin.detectionPunisher.recordLogOnly(
                player,
                "modlist:${parsed.loader.displayName}",
                DetectionType.MOD_LIST,
                evidence = parsed.summary(),
            )
        }
    }
}
