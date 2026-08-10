package icu.epochcraft.antilitematica.ban

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.database.BanRecord
import org.bukkit.Bukkit
import java.util.UUID

/**
 * AdvancedBan 联动后端（全反射，零编译期依赖）。
 *
 * 通过 `me.leoko.advancedban.manager.PunishmentManager` 反射调用：
 *   PunishmentManager.get().ban(uuid, executor, reason, end, "BAN")
 *   PunishmentManager.get().unban(uuid, executor, silent)
 *
 * 不同版本 AdvancedBan 的 ban 签名存在差异（UUID / 玩家名），已做兼容回退。
 * 服务端需安装 AdvancedBan（plugin.yml softdepend），未安装时 [isAvailable] 返回 false。
 *
 * @author 阿清
 */
class AdvancedBanBackend(private val plugin: AntiLitematica) : BanBackend {

    override val name: String = "AdvancedBan"

    override fun isAvailable(): Boolean = Bukkit.getPluginManager().getPlugin("AdvancedBan") != null

    /** 反射句柄（延迟加载，服务端未装 AdvancedBan 时不触发） */
    private val api by lazy { loadApi() }

    private class Api(val manager: Any, val managerClass: Class<*>)

    private fun loadApi(): Api {
        val managerClass = Class.forName("me.leoko.advancedban.manager.PunishmentManager")
        val manager = managerClass.getMethod("get").invoke(null)
        return Api(manager, managerClass)
    }

    override fun ban(uuid: UUID, name: String, reason: String, durationMillis: Long) {
        try {
            val a = api
            val end = if (durationMillis == BanRecord.PERMANENT) Long.MAX_VALUE
            else System.currentTimeMillis() + durationMillis
            try {
                // 新版：ban(UUID, executor, reason, end, type)
                a.managerClass.getMethod(
                    "ban", UUID::class.java, String::class.java, String::class.java,
                    Long::class.javaPrimitiveType, String::class.java,
                ).invoke(a.manager, uuid, "AntiLitematica", reason, end, "BAN")
            } catch (e: NoSuchMethodException) {
                // 旧版：ban(String name, executor, reason, end, type)
                a.managerClass.getMethod(
                    "ban", String::class.java, String::class.java, String::class.java,
                    Long::class.javaPrimitiveType, String::class.java,
                ).invoke(a.manager, name, "AntiLitematica", reason, end, "BAN")
            }
        } catch (e: ReflectiveOperationException) {
            plugin.logger.warning("AdvancedBan 封禁调用失败: ${e.message}")
        }
    }

    override fun unban(uuid: UUID) {
        try {
            val a = api
            try {
                // 新版：unban(UUID, executor, silent)
                a.managerClass.getMethod("unban", UUID::class.java, String::class.java, Boolean::class.javaPrimitiveType)
                    .invoke(a.manager, uuid, "AntiLitematica", false)
            } catch (e: NoSuchMethodException) {
                // 旧版：unban(String name, executor, silent)
                a.managerClass.getMethod("unban", String::class.java, String::class.java, Boolean::class.javaPrimitiveType)
                    .invoke(a.manager, uuid.toString(), "AntiLitematica", false)
            }
        } catch (e: ReflectiveOperationException) {
            plugin.logger.warning("AdvancedBan 解封调用失败: ${e.message}")
        }
    }
}
