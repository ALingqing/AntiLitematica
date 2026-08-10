package icu.epochcraft.antilitematica.command

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.config.PluginConfig
import icu.epochcraft.antilitematica.database.BanRecord
import icu.epochcraft.antilitematica.detection.ActionType
import icu.epochcraft.antilitematica.util.DurationParser
import icu.epochcraft.antilitematica.util.MessageUtil
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * /antilitematica 命令：
 *   menu / gui           打开管理菜单（自动选择 Dialog / 箱子）
 *   reload               重载配置
 *   list                 列出禁用通道与动作
 *   add <通道> [动作]     添加禁用通道（KICK/BAN/WARN/LOG）
 *   remove <通道>        移除禁用通道
 *   ban <玩家> <时长> [原因]  手动封禁（如 30d / permanent）
 *   unban <玩家>         解封
 *   preset <strict|normal|lite> 切换预设模式
 *   stats                查看统计
 *   history <玩家>       查看玩家检测记录
 *   forgive <玩家>       标记该玩家所有命中为误报
 *   version              查看版本与更新信息
 *
 * @author 阿清
 */
class AntiLitematicaCommand(private val plugin: AntiLitematica) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val sub = args.firstOrNull()?.lowercase()

        // 控制台可用的子命令
        if (sub == "reload") {
            if (!requireAdmin(sender)) return true
            plugin.configHolder.reload()
            plugin.placementGuard.reload()
            plugin.commandGuard.reload()
            plugin.signalDetector?.reload()
            plugin.handshakeDetector?.reload()
            MessageUtil.send(sender, plugin.configHolder.lang("cmd.reloaded")
                .replace("{channels}", plugin.configHolder.channels.keys.sorted().joinToString(", ")))
            return true
        }
        if (sub == "ban" || sub == "unban") {
            if (!requireAdmin(sender)) return true
            return handleBan(sender, sub, args)
        }

        // 其余需要玩家身份
        if (sender !is Player) {
            MessageUtil.send(sender, plugin.configHolder.lang("common.player-only"))
            return true
        }
        if (!requireAdmin(sender)) return true

        when (sub) {
            null, "", "menu", "gui", "open" -> plugin.adminMenu.open(sender)
            "list" -> handleList(sender)
            "add" -> handleAdd(sender, args)
            "remove" -> handleRemove(sender, args)
            "preset" -> handlePreset(sender, args)
            "stats" -> handleStats(sender)
            "history" -> handleHistory(sender, args)
            "forgive" -> handleForgive(sender, args)
            "version" -> handleVersion(sender)
            else -> MessageUtil.send(
                sender,
                plugin.configHolder.lang("common.unknown-command")
                    .replace("{command}", sub)
                    .replace("{available}", "menu / reload / list / add / remove / ban / unban / preset / stats / history / forgive / version"),
            )
        }
        return true
    }

    // ---------------- 子命令实现 ----------------

    private fun handleList(sender: CommandSender) {
        val cfg = plugin.configHolder
        MessageUtil.send(sender, cfg.lang("cmd.list-header").replace("{count}", cfg.channels.size.toString()))
        cfg.channels.toSortedMap().forEach { (channel, c) ->
            val durationSuffix = if (c.action == ActionType.BAN)
                cfg.lang("cmd.list-ban-suffix").replace("{duration}", DurationParser.format(c.banDuration))
            else ""
            MessageUtil.sendNoPrefix(
                sender,
                cfg.lang("cmd.list-line")
                    .replace("{channel}", channel)
                    .replace("{color}", if (c.action == ActionType.BAN) "c" else "a")
                    .replace("{action}", cfg.lang("action.${c.action.name}"))
                    .replace("{duration}", durationSuffix),
            )
        }
        val autoBanStatus = if (cfg.autoBanEnabled)
            cfg.lang("cmd.auto-ban-on")
                .replace("{kicks}", cfg.kicksBeforeBan.toString())
                .replace("{duration}", DurationParser.format(cfg.autoBanDuration))
        else cfg.lang("cmd.auto-ban-off")
        MessageUtil.sendNoPrefix(
            sender,
            cfg.lang("cmd.list-footer")
                .replace("{mode}", cfg.lang("mode.${cfg.mode.name}"))
                .replace("{status}", autoBanStatus),
        )
    }

    private fun handleAdd(sender: CommandSender, args: Array<out String>) {
        val channel = args.getOrNull(1)
        if (channel.isNullOrBlank()) {
            MessageUtil.send(sender, plugin.configHolder.lang("common.usage").replace("{usage}", "/antilitematica add <通道> [KICK|BAN|WARN|LOG]"))
            return
        }
        val action = ActionType.parse(args.getOrNull(2))
        if (plugin.configHolder.addChannel(channel, action)) {
            MessageUtil.send(
                sender,
                plugin.configHolder.lang("cmd.channel-added")
                    .replace("{channel}", channel.trim().lowercase())
                    .replace("{action}", plugin.configHolder.lang("action.${action.name}")),
            )
        } else {
            MessageUtil.send(sender, plugin.configHolder.lang("cmd.channel-exists").replace("{channel}", channel.trim().lowercase()))
        }
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        val channel = args.getOrNull(1)
        if (channel.isNullOrBlank()) {
            MessageUtil.send(sender, plugin.configHolder.lang("common.usage").replace("{usage}", "/antilitematica remove <通道>"))
            return
        }
        if (plugin.configHolder.removeChannel(channel)) {
            MessageUtil.send(sender, plugin.configHolder.lang("cmd.channel-removed").replace("{channel}", channel.trim().lowercase()))
        } else {
            MessageUtil.send(sender, plugin.configHolder.lang("cmd.channel-not-found").replace("{channel}", channel.trim().lowercase()))
        }
    }

    private fun handleBan(sender: CommandSender, sub: String, args: Array<out String>): Boolean {
        val target = args.getOrNull(1)
        if (target.isNullOrBlank()) {
            MessageUtil.send(sender, plugin.configHolder.lang("common.usage")
                .replace("{usage}", if (sub == "ban") "/antilitematica ban <玩家> <时长> [原因]" else "/antilitematica unban <玩家>"))
            return true
        }
        val uuid = resolveUuid(target) ?: run {
            MessageUtil.send(sender, plugin.configHolder.lang("common.not-found").replace("{target}", target))
            return true
        }

        if (sub == "unban") {
            plugin.banManager.unban(uuid)
            MessageUtil.send(sender, plugin.configHolder.lang("ban.unban").replace("{player}", target))
            return true
        }

        // ban 需要时长
        val duration = DurationParser.parseMillis(args.getOrNull(2), -1L)
        val reason = args.drop(3).joinToString(" ").ifBlank { plugin.configHolder.lang("ban.reason-admin") }
        plugin.banManager.ban(uuid, target, reason, duration)
        MessageUtil.send(
            sender,
            plugin.configHolder.lang("ban.success")
                .replace("{player}", target)
                .replace("{reason}", reason)
                .replace("{duration}", DurationParser.format(duration)),
        )
        return true
    }

    private fun handlePreset(sender: CommandSender, args: Array<out String>) {
        val mode = PluginConfig.Mode.parse(args.getOrNull(1))
        plugin.configHolder.setMode(mode)
        MessageUtil.send(sender, plugin.configHolder.lang("cmd.preset").replace("{mode}", plugin.configHolder.lang("mode.${mode.name}")))
    }

    private fun handleStats(sender: CommandSender) {
        plugin.statsService.buildStats().lines().forEach { MessageUtil.sendNoPrefix(sender, it) }
    }

    private fun handleHistory(sender: CommandSender, args: Array<out String>) {
        val target = args.getOrNull(1) ?: run {
            MessageUtil.send(sender, plugin.configHolder.lang("common.usage").replace("{usage}", "/antilitematica history <玩家>"))
            return
        }
        val uuid = resolveUuid(target) ?: run {
            MessageUtil.send(sender, plugin.configHolder.lang("common.not-found").replace("{target}", target))
            return
        }
        val records = plugin.database.getDetectionsOf(uuid, 10)
        if (records.isEmpty()) {
            MessageUtil.send(sender, plugin.configHolder.lang("cmd.history-empty"))
            return
        }
        MessageUtil.send(
            sender,
            plugin.configHolder.lang("cmd.history-header")
                .replace("{target}", target)
                .replace("{count}", records.size.toString()),
        )
        records.forEach { r ->
            MessageUtil.sendNoPrefix(
                sender,
                plugin.configHolder.lang("cmd.history-line")
                    .replace("{channel}", r.channel)
                    .replace("{mod}", r.modDescription ?: plugin.configHolder.lang("cmd.unknown"))
                    .replace("{action}", plugin.configHolder.lang("action.${r.action.name}"))
                    .replace("{time}", plugin.statsService.formatTime(r.timestamp)),
            )
        }
    }

    private fun handleForgive(sender: CommandSender, args: Array<out String>) {
        val target = args.getOrNull(1) ?: run {
            MessageUtil.send(sender, plugin.configHolder.lang("common.usage").replace("{usage}", "/antilitematica forgive <玩家>"))
            return
        }
        val uuid = resolveUuid(target) ?: run {
            MessageUtil.send(sender, plugin.configHolder.lang("common.not-found").replace("{target}", target))
            return
        }
        // 将该玩家最近命中过的所有通道标记为误报
        val channels = plugin.database.getDetectionsOf(uuid, 20).map { it.channel }.distinct()
        channels.forEach { plugin.database.addFalsePositive(uuid, it.removePrefix("brand:")) }
        MessageUtil.send(sender, plugin.configHolder.lang("cmd.forgive").replace("{player}", target))
    }

    private fun handleVersion(sender: CommandSender) {
        MessageUtil.send(
            sender,
            plugin.configHolder.lang("cmd.version-current").replace("{version}", plugin.description.version),
        )
        val latest = plugin.updateChecker.latestVersion
        when {
            plugin.updateChecker.hasUpdate ->
                MessageUtil.send(sender, plugin.configHolder.lang("cmd.version-update").replace("{version}", latest ?: "?"))
            latest != null ->
                MessageUtil.send(sender, plugin.configHolder.lang("cmd.version-latest").replace("{version}", latest))
            else -> MessageUtil.send(sender, plugin.configHolder.lang("cmd.version-unavailable"))
        }
    }

    // ---------------- 工具 ----------------

    private fun requireAdmin(sender: CommandSender): Boolean {
        if (sender.hasPermission("antilitematica.admin")) return true
        MessageUtil.send(sender, plugin.configHolder.lang("common.no-permission"))
        return false
    }

    private fun resolveUuid(name: String): java.util.UUID? {
        val online = Bukkit.getPlayerExact(name)
        if (online != null) return online.uniqueId
        return Bukkit.getOfflinePlayer(name).takeIf { it.hasPlayedBefore() }?.uniqueId
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String>? {
        if (args.size == 1) {
            return listOf("menu", "reload", "list", "add", "remove", "ban", "unban", "preset", "stats", "history", "forgive", "version")
                .filter { it.startsWith(args[0].lowercase()) }
        }
        return when {
            args.size == 2 && args[0].equals("remove", true) -> plugin.configHolder.channels.keys.sorted()
            args.size == 2 && args[0].equals("add", true) -> listOf("servux:litematics", "schematica", "litematica:main")
            args.size == 3 && args[0].equals("add", true) -> ActionType.entries.map { it.name }
            args.size == 2 && args[0].equals("preset", true) -> PluginConfig.Mode.entries.map { it.name.lowercase() }
            args.size == 3 && args[0].equals("ban", true) -> listOf("1d", "7d", "30d", "permanent")
            else -> emptyList()
        }
    }
}
