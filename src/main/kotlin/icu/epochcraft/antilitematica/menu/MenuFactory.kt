package icu.epochcraft.antilitematica.menu

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.util.PaperVersion

/**
 * 菜单工厂：根据服务端版本选择菜单实现。
 *
 * 关键点：Dialog API 类只在 Paper 1.21.7+ 存在，旧版本服务端如果直接加载
 * [DialogAdminMenu] 会触发 NoClassDefFoundError，因此这里必须通过反射延迟加载，
 * 加载失败时安全回退到 [ChestAdminMenu]。
 */
object MenuFactory {

    /** 创建当前服务端可用的管理菜单 */
    fun create(plugin: AntiLitematica): AdminMenu {
        if (PaperVersion.supportsDialogApi) {
            val dialogMenu = loadDialogMenu(plugin)
            if (dialogMenu != null) {
                plugin.logger.info("当前服务端支持 Dialog API，使用原生 Dialog 菜单")
                return dialogMenu
            }
            plugin.logger.warning("Dialog 菜单加载失败，已回退到箱子菜单")
        } else {
            plugin.logger.info("当前服务端不支持 Dialog API，使用箱子菜单")
        }
        return ChestAdminMenu(plugin)
    }

    /** 反射实例化 Dialog 菜单，避免旧版本服务端加载 Dialog API 类 */
    private fun loadDialogMenu(plugin: AntiLitematica): AdminMenu? = runCatching {
        val clazz = Class.forName("icu.epochcraft.antilitematica.dialog.DialogAdminMenu")
        clazz.getConstructor(AntiLitematica::class.java)
            .newInstance(plugin) as AdminMenu
    }.getOrNull()
}
