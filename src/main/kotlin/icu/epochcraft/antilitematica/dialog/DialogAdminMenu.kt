package icu.epochcraft.antilitematica.dialog

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.config.PluginConfig
import icu.epochcraft.antilitematica.menu.AdminMenu
import icu.epochcraft.antilitematica.util.MessageUtil
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import java.time.Duration

/**
 * Paper 1.21.7+ 原生 Dialog 菜单实现（Minecraft 1.21.6 加入的对话框界面）。
 *
 * 重要：本类引用了 Dialog API，只应在服务端支持 Dialog 时被加载
 * （由 [icu.epochcraft.antilitematica.menu.MenuFactory] 通过反射实例化），
 * 旧版本服务端永远不会加载本类，因此不会触发 NoClassDefFoundError。
 *
 * 菜单结构：
 *   主菜单（multiAction）→ 通道管理 / 设置 / 重载配置 / 关闭
 *   通道管理（multiAction）→ 添加通道 / 移除通道 / 返回
 *   添加/移除（confirmation + text 输入）→ 回调中读写配置
 *   设置（confirmation + bool 开关）→ 回调中保存开关
 */
class DialogAdminMenu(private val plugin: AntiLitematica) : AdminMenu {

    override val modeName: String = "Dialog"

    /** 回调点击选项：仅使用一次，有效期 12 小时 */
    private val clickOptions: ClickCallback.Options =
        ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofHours(12))
            .build()

    override fun open(player: Player) {
        player.showDialog(buildMainDialog())
    }

    override fun close(player: Player) {
        player.closeDialog()
    }

    // ---------------- 主菜单 ----------------

    private fun buildMainDialog(): Dialog = Dialog.create { factory ->
        factory.empty()
            .base(
                DialogBase.builder(title("AntiLitematica 管理"))
                    .body(
                        listOf(
                            DialogBody.plainMessage(
                                Component.text("插件版本: v${plugin.description.version}", NamedTextColor.GRAY),
                            ),
                            DialogBody.plainMessage(
                                Component.text(
                                    "禁用通道: ${plugin.configHolder.channels.keys.sorted().joinToString(", ")}",
                                    NamedTextColor.AQUA,
                                ),
                            ),
                            DialogBody.plainMessage(
                                Component.text(
                                    "预设: ${plugin.configHolder.mode.displayName}",
                                    NamedTextColor.LIGHT_PURPLE,
                                ),
                            ),
                        ),
                    )
                    .build(),
            )
            .type(
                DialogType.multiAction(
                    listOf(
                        button("通道管理", "添加 / 移除禁用通道", NamedTextColor.AQUA) { _, audience ->
                            showChannelMenu(audience)
                        },
                        button("设置", "切换日志 / 通知开关", NamedTextColor.LIGHT_PURPLE) { _, audience ->
                            showSettingsDialog(audience)
                        },
                        button("切换预设", "strict → normal → lite", NamedTextColor.DARK_AQUA) { _, audience ->
                            val player = audience.asPlayer() ?: return@button
                            plugin.configHolder.setMode(nextMode(plugin.configHolder.mode))
                            send(player, "&a已切换预设: &f${plugin.configHolder.mode.displayName}")
                            showMain(audience)
                        },
                        button("重载配置", "重新读取 config.yml", NamedTextColor.YELLOW) { _, audience ->
                            plugin.configHolder.reload()
                            send(audience, "&a配置已重载")
                            showMain(audience)
                        },
                        button("关闭", "关闭菜单", NamedTextColor.GRAY) { _, audience ->
                            audience.closeDialog()
                        },
                    ),
                )
                    .columns(2)
                    .build(),
            )
    }

    // ---------------- 通道管理 ----------------

    private fun showChannelMenu(audience: Audience) {
        val player = audience.asPlayer() ?: return
        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(title("通道管理"))
                        .body(
                            plugin.configHolder.channels.keys.sorted().map { channel ->
                                DialogBody.plainMessage(
                                    Component.text(
                                        "• $channel（${plugin.configHolder.getAction(channel).displayName}）",
                                        NamedTextColor.GREEN,
                                    ),
                                )
                            },
                        )
                        .build(),
                )
                .type(
                    DialogType.multiAction(
                        listOf(
                            button("添加通道", "通过输入添加新通道", NamedTextColor.GREEN) { _, a ->
                                showAddDialog(a)
                            },
                            button("移除通道", "通过输入移除通道", NamedTextColor.RED) { _, a ->
                                showRemoveDialog(a)
                            },
                            button("返回", "返回主菜单", NamedTextColor.GRAY) { _, a ->
                                showMain(a)
                            },
                        ),
                    )
                        .columns(1)
                        .build(),
                )
        }
        player.showDialog(dialog)
    }

    // ---------------- 添加 / 移除通道 ----------------

    private fun showAddDialog(audience: Audience) {
        val player = audience.asPlayer() ?: return
        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(title("添加通道"))
                        .inputs(
                            listOf(
                                DialogInput.text("channel", Component.text("通道名", NamedTextColor.AQUA))
                                    .maxLength(64)
                                    .initial("servux:litematics")
                                    .build(),
                            ),
                        )
                        .build(),
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            Component.text("确认添加", NamedTextColor.GREEN),
                            Component.text("点击确认添加该通道", NamedTextColor.GRAY),
                            100,
                            DialogAction.customClick({ view, a ->
                                val channel = view.getText("channel")?.trim()?.lowercase()
                                when {
                                    channel.isNullOrEmpty() -> send(a, "&c通道名为空")
                                    plugin.configHolder.addChannel(channel) -> send(a, "&a已添加通道 &f$channel")
                                    else -> send(a, "&c通道已存在: &f$channel")
                                }
                                showChannelMenu(a)
                            }, clickOptions),
                        ),
                        ActionButton.create(Component.text("取消", NamedTextColor.GRAY), null, 100, null),
                    ),
                )
        }
        player.showDialog(dialog)
    }

    private fun showRemoveDialog(audience: Audience) {
        val player = audience.asPlayer() ?: return
        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(title("移除通道"))
                        .inputs(
                            listOf(
                                DialogInput.text("channel", Component.text("通道名", NamedTextColor.AQUA))
                                    .maxLength(64)
                                    .build(),
                            ),
                        )
                        .build(),
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            Component.text("确认移除", NamedTextColor.RED),
                            Component.text("点击确认移除该通道", NamedTextColor.GRAY),
                            100,
                            DialogAction.customClick({ view, a ->
                                val channel = view.getText("channel")?.trim()?.lowercase()
                                when {
                                    channel.isNullOrEmpty() -> send(a, "&c通道名为空")
                                    plugin.configHolder.removeChannel(channel) -> send(a, "&a已移除通道 &f$channel")
                                    else -> send(a, "&c通道不存在: &f$channel")
                                }
                                showChannelMenu(a)
                            }, clickOptions),
                        ),
                        ActionButton.create(Component.text("取消", NamedTextColor.GRAY), null, 100, null),
                    ),
                )
        }
        player.showDialog(dialog)
    }

    // ---------------- 设置 ----------------

    private fun showSettingsDialog(audience: Audience) {
        val player = audience.asPlayer() ?: return
        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(title("设置"))
                        .inputs(
                            listOf(
                                DialogInput.bool("log", Component.text("控制台日志", NamedTextColor.GRAY))
                                    .initial(plugin.configHolder.logDetections)
                                    .build(),
                                DialogInput.bool("notify", Component.text("管理员通知", NamedTextColor.GRAY))
                                    .initial(plugin.configHolder.notifyAdmins)
                                    .build(),
                            ),
                        )
                        .build(),
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            Component.text("保存", NamedTextColor.GREEN),
                            Component.text("点击保存设置", NamedTextColor.GRAY),
                            100,
                            DialogAction.customClick({ view, a ->
                                val log = view.getBoolean("log") ?: plugin.configHolder.logDetections
                                val notify = view.getBoolean("notify") ?: plugin.configHolder.notifyAdmins
                                plugin.configHolder.setLogDetections(log)
                                plugin.configHolder.setNotifyAdmins(notify)
                                send(a, "&a设置已保存 | 日志=$log 通知=$notify")
                                showMain(a)
                            }, clickOptions),
                        ),
                        ActionButton.create(Component.text("取消", NamedTextColor.GRAY), null, 100, null),
                    ),
                )
        }
        player.showDialog(dialog)
    }

    // ---------------- 工具方法 ----------------

    private fun showMain(audience: Audience) {
        val player = audience.asPlayer() ?: return
        player.showDialog(buildMainDialog())
    }

    private fun title(text: String): Component =
        Component.text(text, NamedTextColor.GOLD)

    private fun nextMode(mode: PluginConfig.Mode): PluginConfig.Mode = when (mode) {
        PluginConfig.Mode.STRICT -> PluginConfig.Mode.NORMAL
        PluginConfig.Mode.NORMAL -> PluginConfig.Mode.LITE
        PluginConfig.Mode.LITE -> PluginConfig.Mode.STRICT
    }

    private fun button(
        label: String,
        tooltip: String,
        color: NamedTextColor,
        onClick: (DialogResponseView, Audience) -> Unit,
    ): ActionButton =
        ActionButton.builder(Component.text(label, color))
            .tooltip(Component.text(tooltip, NamedTextColor.GRAY))
            .action(DialogAction.customClick(onClick, clickOptions))
            .build()

    private fun send(audience: Audience, message: String) {
        val player = audience.asPlayer() ?: return
        MessageUtil.send(player, message)
    }

    private fun Audience.asPlayer(): Player? = this as? Player
}
