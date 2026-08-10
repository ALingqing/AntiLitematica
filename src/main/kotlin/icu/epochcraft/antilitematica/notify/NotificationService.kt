package icu.epochcraft.antilitematica.notify

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.detection.ActionType
import icu.epochcraft.antilitematica.detection.DetectionSource
import org.bukkit.entity.Player

/**
 * 通知聚合服务：管理 Discord Webhook 与 QQ OneBot 两个纯出站通道。
 *
 * @author 阿清
 */
class NotificationService(private val plugin: AntiLitematica) {

    private val discord = DiscordWebhookNotifier(plugin)
    private val onebot = OneBotNotifier(plugin)

    /** 检测命中时推送告警 */
    fun notifyDetection(
        player: Player,
        channel: String,
        mod: String,
        action: ActionType,
        source: DetectionSource,
        flagged: Boolean,
    ) {
        val header = if (flagged) "[误报豁免]" else "AntiLitematica 检测告警"

        discord.send(
            header,
            mapOf(
                "玩家" to player.name,
                "UUID" to player.uniqueId.toString(),
                "通道" to channel,
                "Mod" to mod,
                "来源" to source.name,
                "动作" to action.displayName,
            ),
        )

        onebot.sendGroupMessage(
            "${header}\n" +
                "玩家: ${player.name} (${player.uniqueId})\n" +
                "通道: $channel | Mod: $mod\n" +
                "处理: ${action.displayName}",
        )
    }

    /** 通用告警（封禁、解封等） */
    fun notifyAlert(title: String, lines: List<String>) {
        discord.send(title, lines.mapIndexed { i, line -> "行${i + 1}" to line }.toMap())
        onebot.sendGroupMessage("$title\n${lines.joinToString("\n")}")
    }
}
