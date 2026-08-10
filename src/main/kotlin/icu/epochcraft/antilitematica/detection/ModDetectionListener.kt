package icu.epochcraft.antilitematica.detection

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.util.Scheduler
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRegisterChannelEvent

/**
 * 检测监听器：
 *   1. [PlayerRegisterChannelEvent] 通道检测（Litematica 等禁用通道 + Forge/Fabric 环境记录）
 *   2. [PlayerJoinEvent] 延迟二次验证（防绕过 + 客户端 Brand 检测）
 *
 * @author 阿清
 */
class ModDetectionListener(
    private val plugin: AntiLitematica,
    private val service: ModDetectionService,
) : Listener {

    /** 客户端注册插件通道时触发 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onChannelRegister(event: PlayerRegisterChannelEvent) {
        val channel = event.channel.lowercase()
        val cfg = plugin.configHolder

        // MasaMods 兼容：记录玩家使用的 Masa 系模组（Tweakeroo 等）
        plugin.masaCompat.detectChannel(event.player, channel)

        // 命中禁用通道 -> 按配置动作处理
        if (channel in cfg.channels.keys) {
            service.handleDetection(event.player, channel, DetectionSource.CHANNEL)
            return
        }

        // 环境通道（Forge/Fabric）仅记录信息，不踢出
        if (ChannelRegistry.isEnvironmentChannel(channel)) {
            val envEnabled = when {
                channel.startsWith("fml") -> cfg.detectForgeHandshake
                channel.startsWith("fabric") -> cfg.detectFabricApi
                else -> true
            }
            if (envEnabled) {
                service.handleDetection(event.player, channel, DetectionSource.CHANNEL)
            }
        }
    }

    /**
     * 兜底复查：
     *   - 重新检查已注册通道（防注册-注销欺骗）
     *   - 检查客户端 Brand（minecraft:brand 载荷）
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val cfg = plugin.configHolder

        Scheduler.globalLater(plugin, cfg.recheckDelayTicks) {
            if (!player.isOnline) return@globalLater

            // 通道复查
            val hit = service.findBannedChannel(player.listeningPluginChannels)
            if (hit != null) {
                service.handleDetection(player, hit, DetectionSource.CHANNEL)
                return@globalLater
            }

            // Brand 检测
            val brand = player.clientBrandName
            if (brand != null && brand.lowercase() in cfg.brandBlocklist) {
                service.handleDetection(player, brand, DetectionSource.BRAND)
                return@globalLater
            }

            // Mod List 交叉验证（mod 列表 × 通道 × Brand 三方互证）
            val parsed = plugin.handshakeDetector?.sessionModsOf(player)
            if (parsed != null && cfg.modList.detectXCheck) {
                val channels = player.listeningPluginChannels.map { it.lowercase() }

                // 1. mod 列表有禁用 mod 但未注册关联通道 -> 通道注销欺骗 / 禁用通道上报
                parsed.mods.keys.firstOrNull { it in cfg.modList.bannedMods.keys }?.let { modId ->
                    if (channels.none { ChannelRegistry.associatesMod(it, modId) }) {
                        service.handleDetection(
                            player, "xcheck:channel:$modId", DetectionSource.MOD_LIST,
                            reason = "Mod 列表含禁用 Mod $modId 但未注册关联通道（疑似通道注销欺骗）",
                            evidence = parsed.summary(),
                        )
                        return@globalLater
                    }
                }

                // 2. mod 加载器与客户端 Brand 矛盾 -> 伪装
                val brandLower = player.clientBrandName?.lowercase()
                val loaderMismatch = when (parsed.loader) {
                    icu.epochcraft.antilitematica.signal.HandshakeParser.ModLoader.FABRIC ->
                        brandLower != null && "fabric" !in brandLower
                    icu.epochcraft.antilitematica.signal.HandshakeParser.ModLoader.FORGE,
                    icu.epochcraft.antilitematica.signal.HandshakeParser.ModLoader.NEOFORGE ->
                        brandLower != null && "fml" !in brandLower && "forge" !in brandLower
                    else -> false
                }
                if (loaderMismatch) {
                    service.handleDetection(
                        player, "xcheck:brand:${parsed.loader.name.lowercase()}", DetectionSource.MOD_LIST,
                        reason = "Mod 列表为 ${parsed.loader.displayName} 但客户端 Brand 为 $brandLower（疑似伪装）",
                        evidence = parsed.summary(),
                    )
                }
            }
        }
    }

    /** 玩家退出时清理 Masa 模组记录 */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        plugin.masaCompat.clearPlayer(event.player)
    }
}
