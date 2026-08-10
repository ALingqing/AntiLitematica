package icu.epochcraft.antilitematica.punish

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.database.DetectionRecord
import icu.epochcraft.antilitematica.detection.ActionType
import icu.epochcraft.antilitematica.detection.ChannelRegistry
import icu.epochcraft.antilitematica.detection.DetectionContext
import icu.epochcraft.antilitematica.detection.DetectionHandler
import icu.epochcraft.antilitematica.detection.DetectionSource
import icu.epochcraft.antilitematica.event.DetectionType
import icu.epochcraft.antilitematica.util.Scheduler
import org.bukkit.entity.Player

/**
 * 基础动作兜底处理器：注册在检测总线的最后一环。
 *
 * 职责：按通道配置执行 KICK / BAN / WARN / LOG 基础动作 + 数据库记录 + 通知。
 * 渐进惩罚开启时由 [GraduatedPunisher] 优先认领，本处理器负责其余所有检测。
 *
 * 安全设计：环境通道（fml:/fabric:）强制 LOG，绝不对 Forge/Fabric 玩家误踢。
 *
 * @author 阿清
 */
class DetectionPunisher(private val plugin: AntiLitematica) : DetectionHandler {

    override fun handle(ctx: DetectionContext): Boolean {
        execute(ctx.player, ctx.channel, ctx.detectionType)
        return true
    }

    /** 执行基础动作 */
    fun execute(player: Player, channel: String, type: DetectionType) {
        val cfg = plugin.configHolder
        val uuid = player.uniqueId

        // 环境通道强制 LOG（防误伤 Forge/Fabric 环境）
        val action = if (ChannelRegistry.isEnvironmentChannel(channel)) ActionType.LOG else cfg.getAction(channel)

        when (action) {
            ActionType.BAN -> {
                val duration = cfg.channels[channel.lowercase()]?.banDuration ?: cfg.autoBanDuration
                plugin.banManager.ban(uuid, player.name, defaultReason(channel), duration)
            }
            ActionType.KICK -> {
                // 自动封禁：累计 KICK 达到阈值转封禁
                if (cfg.autoBanEnabled) {
                    val kicks = plugin.database.getKickCount(uuid) + 1
                    if (kicks >= cfg.kicksBeforeBan) {
                        plugin.banManager.ban(uuid, player.name, defaultReason(channel), cfg.autoBanDuration)
                        record(player, channel, type, ActionType.BAN)
                        return
                    }
                }
                Scheduler.entity(player, plugin) {
                    if (player.isOnline) player.kickPlayer(cfg.kickMessage)
                }
            }
            ActionType.WARN -> {
                val mod = ChannelRegistry.describe(channel) ?: channel
                player.sendMessage(cfg.lang("detection.warn").replace("{mod}", mod))
            }
            ActionType.LOG -> Unit
        }

        record(player, channel, type, action)
    }

    /** 仅记录（误报豁免 / 环境通道） */
    fun recordLogOnly(player: Player, channel: String, type: DetectionType, flagged: Boolean = true) {
        record(player, channel, type, ActionType.LOG, flagged)
    }

    private fun defaultReason(channel: String): String =
        plugin.configHolder.lang("detection.ban-reason").replace("{channel}", channel)

    /** 数据库记录 + 控制台日志 + 管理员通知 + 出站通知 */
    private fun record(
        player: Player,
        channel: String,
        type: DetectionType,
        action: ActionType,
        flagged: Boolean = false,
    ) {
        val cfg = plugin.configHolder
        val mod = ChannelRegistry.describe(channel) ?: channel
        val sourceName = when (type) {
            DetectionType.BRAND -> "BRAND"
            DetectionType.PRINTER -> "PRINTER"
            DetectionType.COMMAND -> "COMMAND"
            else -> "CHANNEL"
        }

        // 数据库记录
        plugin.database.insertDetection(
            DetectionRecord(
                uuid = player.uniqueId,
                name = player.name,
                channel = if (type == DetectionType.BRAND) "brand:$channel" else channel,
                modDescription = mod,
                action = action,
            ),
        )

        // 控制台日志
        if (cfg.logDetections) {
            plugin.logger.warning(
                "检测到玩家 ${player.name} ${if (flagged) "[误报豁免] " else ""}命中 [$channel] ($sourceName)，处理: ${action.name}"
            )
        }

        // 在线管理员通知
        if (cfg.notifyAdmins) {
            val text = cfg.lang("detection.notify-admin")
                .replace("{player}", player.name)
                .replace("{mod}", mod)
                .replace("{channel}", channel)
                .replace("{action}", action.displayName)
            plugin.server.onlinePlayers
                .filter { it.hasPermission("antilitematica.notify") }
                .forEach { it.sendMessage("§c[AntiLitematica] §7$text") }
        }

        // 出站通知（Discord / QQ）
        plugin.notificationService?.notifyDetection(
            player, channel, mod, action,
            if (type == DetectionType.BRAND) DetectionSource.BRAND else DetectionSource.CHANNEL,
            flagged,
        )

        // 反作弊联动
        if (!flagged) {
            plugin.integrationManager.flag(player, "antilitematica:$channel", 1, "$sourceName/${action.name}")
        }
    }
}
