package icu.epochcraft.antilitematica.menu

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.config.PluginConfig
import icu.epochcraft.antilitematica.util.MessageUtil
import icu.epochcraft.antilitematica.util.PaperVersion
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import net.kyori.adventure.text.Component
import java.util.UUID

/**
 * 箱子菜单实现（服务端 < 1.21.6 回退方案）。
 *
 * 布局（54 格）：
 *   [0-2]    状态栏：插件信息 / 菜单模式 / 通道数量
 *   [9-16]   当前禁用通道（点击移除，最多展示 8 个）
 *   [18-25]  常用通道快捷添加（点击切换）
 *   [45]     控制台日志开关
 *   [46]     管理员通知开关
 *   [47]     重载配置
 *   [48]     刷新菜单
 *   [49]     关闭菜单
 */
class ChestAdminMenu(private val plugin: AntiLitematica) : AdminMenu, Listener {

    /** 常用通道快捷列表（仅为建议，管理员自行决定） */
    private val commonChannels = listOf(
        "servux:litematics", // Litematica 投影（Fabric 26.x / Forge 移植版均注册）
        "schematica",        // Schematica（1.12 旧版投影）
        "litematica:main",   // Litematica 旧版本通道
        "servux:main",       // Servux 本体
    )

    private data class Session(val inventory: Inventory, val actions: Map<Int, (Player) -> Unit>)

    /** 打开中的菜单会话：玩家 UUID -> 会话 */
    private val sessions = mutableMapOf<UUID, Session>()

    override val modeName: String = "Chest"

    override fun open(player: Player) {
        val (inventory, actions) = buildInventory(player)
        sessions[player.uniqueId] = Session(inventory, actions)
        player.openInventory(inventory)
    }

    override fun close(player: Player) {
        player.closeInventory()
        sessions.remove(player.uniqueId)
    }

    // ---------------- 菜单构建 ----------------

    private fun buildInventory(player: Player): Pair<Inventory, Map<Int, (Player) -> Unit>> {
        val inventory = plugin.server.createInventory(null, 54, "§8AntiLitematica 管理")
        val actions = mutableMapOf<Int, (Player) -> Unit>()
        val cfg = plugin.configHolder

        // ---- 顶部状态栏 ----
        inventory.setItem(
            0,
            item(Material.PAPER, "§e§lAntiLitematica", listOf("§7管理面板 v${plugin.description.version}", "§7作者: 阿清")),
        )
        inventory.setItem(
            1,
            item(Material.COMPASS, "§b菜单模式", listOf("§7当前: §f$modeName", "§7服务端: §f${PaperVersion.current}")),
        )
        inventory.setItem(
            2,
            item(Material.CHEST, "§b已启用通道", listOf("§7共 §f${cfg.channels.size} §7个")),
        )

        // ---- 当前禁用通道（点击移除）----
        val channels = cfg.channels.keys.sorted()
        channels.take(8).forEachIndexed { index, channel ->
            val slot = 9 + index
            val action = cfg.getAction(channel)
            inventory.setItem(
                slot,
                item(Material.RED_DYE, "§c$channel", listOf("§7动作: §f${action.displayName}", "§7点击移除该通道")),
            )
            actions[slot] = { p ->
                cfg.removeChannel(channel)
                MessageUtil.send(p, "&a已移除通道 &f$channel")
                refresh(p)
            }
        }

        // ---- 常用通道快捷添加 ----
        commonChannels.forEachIndexed { index, channel ->
            val slot = 18 + index
            val exists = channel in cfg.channels.keys
            inventory.setItem(
                slot,
                item(
                    if (exists) Material.GRAY_DYE else Material.LIME_DYE,
                    (if (exists) "§7" else "§a") + channel,
                    listOf(if (exists) "§7已启用，点击移除" else "§7点击添加到禁用列表"),
                ),
            )
            actions[slot] = { p ->
                if (exists) cfg.removeChannel(channel) else cfg.addChannel(channel)
                refresh(p)
            }
        }

        // ---- 设置开关 ----
        inventory.setItem(45, toggleItem("控制台日志", cfg.logDetections))
        actions[45] = { p -> cfg.setLogDetections(!cfg.logDetections); refresh(p) }

        inventory.setItem(46, toggleItem("管理员通知", cfg.notifyAdmins))
        actions[46] = { p -> cfg.setNotifyAdmins(!cfg.notifyAdmins); refresh(p) }

        // ---- 操作按钮 ----
        inventory.setItem(47, item(Material.CLOCK, "§e重载配置", listOf("§7重新读取 config.yml")))
        actions[47] = { p -> cfg.reload(); MessageUtil.send(p, "&a配置已重载"); refresh(p) }

        inventory.setItem(48, item(Material.SPECTRAL_ARROW, "§b刷新菜单", listOf("§7刷新当前面板")))
        actions[48] = { p -> refresh(p) }

        inventory.setItem(49, item(Material.BARRIER, "§c关闭菜单", listOf("§7关闭当前面板")))
        actions[49] = { p -> close(p) }

        inventory.setItem(
            50,
            item(Material.REPEATER, "§d切换预设", listOf("§7当前: §f${cfg.mode.displayName}", "§7点击循环: strict → normal → lite")),
        )
        actions[50] = { p -> cfg.setMode(nextMode(cfg.mode)); refresh(p) }

        inventory.setItem(
            52,
            item(Material.ENCHANTED_BOOK, "§b统计面板", listOf("§7查看检测统计")),
        )
        actions[52] = { p ->
            plugin.statsService.buildStats().lines().forEach { MessageUtil.sendNoPrefix(p, it) }
        }

        return inventory to actions
    }

    private fun nextMode(mode: PluginConfig.Mode): PluginConfig.Mode = when (mode) {
        PluginConfig.Mode.STRICT -> PluginConfig.Mode.NORMAL
        PluginConfig.Mode.NORMAL -> PluginConfig.Mode.LITE
        PluginConfig.Mode.LITE -> PluginConfig.Mode.STRICT
    }

    private fun refresh(player: Player) {
        val (inventory, actions) = buildInventory(player)
        sessions[player.uniqueId] = Session(inventory, actions)
        player.openInventory(inventory)
    }

    // ---------------- 事件处理 ----------------

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val session = sessions[event.whoClicked.uniqueId] ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val action = session.actions[event.rawSlot] ?: return
        action(player)
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val session = sessions[event.player.uniqueId]
        // 仅当关闭的确实是当前会话的箱子时清理，避免刷新菜单时误删新会话
        if (session != null && session.inventory == event.inventory) {
            sessions.remove(event.player.uniqueId)
        }
    }

    // ---------------- 物品工具 ----------------

    private fun item(material: Material, name: String, lore: List<String>): ItemStack =
        ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(MessageUtil.colorize(name)))
                meta.lore(lore.map { Component.text(MessageUtil.colorize(it)) })
            }
        }

    private fun toggleItem(label: String, enabled: Boolean): ItemStack =
        item(
            if (enabled) Material.LIME_DYE else Material.GRAY_DYE,
            (if (enabled) "§a" else "§7") + "§l$label",
            listOf(
                "§7状态: " + if (enabled) "§a已开启" else "§c已关闭",
                "§7点击切换",
            ),
        )
}
