package icu.epochcraft.antilitematica.menu

import org.bukkit.entity.Player

/**
 * 管理菜单抽象。
 *
 * 根据服务端版本由 [MenuFactory] 选择实现：
 *   - [DialogAdminMenu]（icu.epochcraft.antilitematica.dialog）：Paper 1.21.7+ 原生 Dialog
 *   - [ChestAdminMenu]：旧版本服务端回退的箱子菜单
 */
interface AdminMenu {

    /** 菜单实现名称（用于日志与界面展示） */
    val modeName: String

    /** 为玩家打开管理菜单 */
    fun open(player: Player)

    /** 关闭管理菜单（默认空实现） */
    fun close(player: Player) = Unit
}
