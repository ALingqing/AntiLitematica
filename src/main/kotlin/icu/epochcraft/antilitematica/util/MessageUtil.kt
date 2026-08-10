package icu.epochcraft.antilitematica.util

import org.bukkit.command.CommandSender

/**
 * 消息工具：统一前缀、颜色代码转换。
 */
object MessageUtil {

    /** 插件统一消息前缀 */
    const val PREFIX = "§8[§cAntiLitematica§8] §7"

    /** 将 & 颜色代码转换为 § */
    fun colorize(text: String): String = text.replace('&', '§')

    /** 带前缀发送消息 */
    fun send(sender: CommandSender, message: String) {
        sender.sendMessage(PREFIX + colorize(message))
    }

    /** 不带前缀发送消息 */
    fun sendNoPrefix(sender: CommandSender, message: String) {
        sender.sendMessage(colorize(message))
    }
}
