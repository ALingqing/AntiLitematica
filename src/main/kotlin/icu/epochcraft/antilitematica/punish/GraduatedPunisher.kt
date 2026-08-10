package icu.epochcraft.antilitematica.punish

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.database.BanRecord
import icu.epochcraft.antilitematica.database.DetectionRecord
import icu.epochcraft.antilitematica.detection.ActionType
import icu.epochcraft.antilitematica.detection.ChannelRegistry
import icu.epochcraft.antilitematica.detection.DetectionContext
import icu.epochcraft.antilitematica.detection.DetectionHandler
import icu.epochcraft.antilitematica.event.DetectionType
import icu.epochcraft.antilitematica.util.MessageUtil
import org.bukkit.entity.Player

/**
 * 渐进惩罚：按违规次数逐级升级（WARN → KICK → TEMPBAN → BAN）。
 *
 * 作为 [DetectionHandler] 注册到检测总线，认领所有检测并执行升级惩罚。
 *
 * 相对旧版 Java 版修复：
 *   - 等级表为空时不再抛 IndexOutOfBoundsException（空表防御）
 *   - 支持 antilitematica.bypass 权限豁免
 *   - 全部走统一检测总线（不再有独立的第三条惩罚路径）
 *
 * @author 阿清
 */
class GraduatedPunisher(
    private val plugin: AntiLitematica,
    private val tracker: ViolationTracker,
) : DetectionHandler {

    override fun handle(ctx: DetectionContext): Boolean {
        // 渐进惩罚未开启 → 放行给 DetectionPunisher（基础动作）
        if (!plugin.configHolder.graduatedPunishment.enabled) return false
        // 环境通道（fml:/fabric:）→ 放行（强制 LOG，防误伤 Forge/Fabric）
        if (ctx.detectionType == DetectionType.CHANNEL && ChannelRegistry.isEnvironmentChannel(ctx.channel)) return false
        punish(ctx.player, ctx.channel, ctx.reason)
        return true
    }

    /** 执行一次渐进惩罚 */
    fun punish(player: Player, channel: String, reason: String) {
        val cfg = plugin.configHolder.graduatedPunishment
        if (!cfg.enabled) return

        // 权限豁免（测试号 / 白名单）
        if (player.hasPermission("antilitematica.bypass")) return

        val record = tracker.recordViolation(player, player.world.name)
        val levels = cfg.levels

        // 空等级表防御（旧版 B3 bug 修复）
        if (levels.isEmpty()) {
            plugin.logger.warning("渐进惩罚等级表为空，已跳过惩罚（请检查 config.yml graduated-punishment.levels）")
            return
        }

        // 当前级别：按次数取 level，超限使用 exceedMax
        val level: PunishmentLevel =
            if (record.count > levels.size) cfg.exceedMax
            else levels[record.count - 1]

        val msg = level.reason
            .replace("{player}", player.name)
            .replace("{count}", record.count.toString())
            .replace("{total}", record.totalViolations.toString())
            .let { MessageUtil.colorize(it) }

        plugin.logger.info(
            "[GraduatedPunish] ${player.name} count=${record.count} " +
                "action=${level.action.name} reason=$msg"
        )

        // ---- 执行动作 ----
        when (level.action) {
            PunishmentAction.WARN -> player.sendMessage(msg)
            PunishmentAction.KICK -> plugin.server.scheduler.runTask(plugin, Runnable {
                if (player.isOnline) player.kickPlayer(msg)
            })
            PunishmentAction.TEMPBAN ->
                plugin.banManager.ban(player.uniqueId, player.name, msg, level.durationMillis)
            PunishmentAction.BAN ->
                plugin.banManager.ban(player.uniqueId, player.name, msg, BanRecord.PERMANENT)
        }

        // ---- 全服广播 ----
        if (level.broadcast) {
            val broadcast = MessageUtil.colorize(
                "&c[AntiLitematica] &e${player.name} &7因使用投影 mod 被处理 (&f${level.action.displayName}&7) &8(第 ${record.count} 次)",
            )
            plugin.server.onlinePlayers.forEach { it.sendMessage(broadcast) }
        }

        // ---- 管理员通知 ----
        if (level.staffAlert && plugin.configHolder.notifyAdmins) {
            val alert = MessageUtil.colorize(
                "&c[AntiLitematica] &e${player.name} &7被处理 (&f${level.action.displayName}&7): &f$msg &8[count=${record.count}]",
            )
            plugin.server.onlinePlayers
                .filter { it.hasPermission("antilitematica.notify") }
                .forEach { it.sendMessage(alert) }
        }

        // ---- 数据库记录 ----
        plugin.database.insertDetection(
            DetectionRecord(
                uuid = player.uniqueId,
                name = player.name,
                channel = channel,
                modDescription = "graduated:${level.action.name}",
                action = mapAction(level.action),
            ),
        )

        // ---- 出站通知（Discord / QQ） ----
        plugin.notificationService?.notifyAlert(
            "渐进惩罚",
            listOf(
                "玩家: ${player.name} (${player.uniqueId})",
                "动作: ${level.action.displayName}（第 ${record.count} 次 / 累计 ${record.totalViolations}）",
                "原因: ${level.reason}",
            ),
        )

        // ---- 反作弊联动 ----
        plugin.integrationManager.flag(
            player,
            "antilitematica:graduated",
            record.count,
            "level=${level.action.name}",
        )
    }

    /** 渐进动作 -> 检测记录动作（DB 兼容） */
    private fun mapAction(action: PunishmentAction): ActionType = when (action) {
        PunishmentAction.WARN -> ActionType.WARN
        PunishmentAction.KICK -> ActionType.KICK
        PunishmentAction.TEMPBAN, PunishmentAction.BAN -> ActionType.BAN
    }
}
