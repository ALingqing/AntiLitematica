package icu.epochcraft.antilitematica.ban

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.database.BanRecord
import org.bukkit.Bukkit
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * LiteBans 联动后端（全反射，零编译期依赖）。
 *
 * 通过 `litetech.litebans.api` 反射调用：
 *   Events.get().getDatabase()                       -> CompletableFuture<Database>
 *   Database.punish(Punishment, Callback)             -> 封禁
 *   Database.getActivePunishments(UUID, PunishmentType) -> 查询封禁
 *   Database.unpunish(Punishment, Callback)           -> 解封
 *
 * 服务端需安装 LiteBans（plugin.yml softdepend），未安装时 [isAvailable] 返回 false。
 *
 * @author 阿清
 */
class LiteBansBackend(private val plugin: AntiLitematica) : BanBackend {

    override val name: String = "LiteBans"

    override fun isAvailable(): Boolean = Bukkit.getPluginManager().getPlugin("LiteBans") != null

    /** 反射句柄（延迟加载，服务端未装 LiteBans 时不触发） */
    private val api by lazy { loadApi() }

    private class Api(
        val events: Any,
        val databaseClass: Class<*>,
        val punishmentClass: Class<*>,
        val punishmentTypeBan: Any,
        val builderClass: Class<*>,
        val callbackClass: Class<*>,
    )

    private fun loadApi(): Api {
        val eventsClass = Class.forName("litetech.litebans.api.Events")
        // Events.get() 静态方法（LiteBans 也注册了 Bukkit service，双保险用静态单例）
        val events = eventsClass.getMethod("get").invoke(null)
        val databaseClass = Class.forName("litetech.litebans.api.Database")
        val punishmentClass = Class.forName("litetech.litebans.api.Punishment")
        val typeClass = Class.forName("litetech.litebans.api.PunishmentType")
        val banType = typeClass.getField("BAN").get(null)
        val builderClass = Class.forName("litetech.litebans.api.Punishment\$Builder")
        val callbackClass = Class.forName("litetech.litebans.api.Callback")
        return Api(events, databaseClass, punishmentClass, banType, builderClass, callbackClass)
    }

    override fun ban(uuid: UUID, name: String, reason: String, durationMillis: Long) {
        try {
            val a = api
            val builder = a.builderClass.getConstructor().newInstance()

            a.builderClass.getMethod("setType", Class.forName("litetech.litebans.api.PunishmentType"))
                .invoke(builder, a.punishmentTypeBan)
            a.builderClass.getMethod("setVictim", UUID::class.java).invoke(builder, uuid)
            a.builderClass.getMethod("setReason", String::class.java).invoke(builder, reason)
            // 执行者显示为插件名（控制台封禁）
            a.builderClass.getMethod("setExecutorName", String::class.java).invoke(builder, "AntiLitematica")

            if (durationMillis == BanRecord.PERMANENT) {
                a.builderClass.getMethod("setPermanent", Boolean::class.javaPrimitiveType).invoke(builder, true)
            } else {
                a.builderClass.getMethod("setDuration", Long::class.javaPrimitiveType, TimeUnit::class.java)
                    .invoke(builder, durationMillis, TimeUnit.MILLISECONDS)
            }

            val punishment = a.builderClass.getMethod("build").invoke(builder)
            submit(a, uuid, punishment)
        } catch (e: ReflectiveOperationException) {
            plugin.logger.warning("LiteBans 封禁调用失败: ${e.message}")
        }
    }

    override fun unban(uuid: UUID) {
        try {
            val a = api
            databaseFuture(a).whenComplete { db, err ->
                if (err != null) {
                    plugin.logger.warning("LiteBans 解封失败（获取数据库）: ${err.message}")
                    return@whenComplete
                }
                @Suppress("UNCHECKED_CAST")
                val bansFuture = a.databaseClass.getMethod(
                    "getActivePunishments", UUID::class.java, Class.forName("litetech.litebans.api.PunishmentType"),
                ).invoke(db, uuid, a.punishmentTypeBan) as CompletableFuture<List<*>>
                bansFuture.whenComplete { bans, err2 ->
                    if (err2 != null) return@whenComplete
                    bans.forEach { p ->
                        a.databaseClass.getMethod("unpunish", a.punishmentClass, a.callbackClass)
                            .invoke(db, p, newCallback())
                    }
                }
            }
        } catch (e: ReflectiveOperationException) {
            plugin.logger.warning("LiteBans 解封调用失败: ${e.message}")
        }
    }

    // ---------------- 内部 ----------------

    private fun submit(a: Api, uuid: UUID, punishment: Any) {
        databaseFuture(a).whenComplete { db, err ->
            if (err != null) {
                plugin.logger.warning("LiteBans 封禁失败（获取数据库）: ${err.message}")
                return@whenComplete
            }
            try {
                a.databaseClass.getMethod("punish", a.punishmentClass, a.callbackClass)
                    .invoke(db, punishment, newCallback())
            } catch (e: ReflectiveOperationException) {
                plugin.logger.warning("LiteBans 封禁调用失败: ${e.message}")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun databaseFuture(a: Api): CompletableFuture<*> {
        val eventsClass = Class.forName("litetech.litebans.api.Events")
        return eventsClass.getMethod("getDatabase").invoke(a.events) as CompletableFuture<*>
    }

    /** 反射创建 LiteBans Callback（忽略结果，仅记录错误） */
    private fun newCallback(): Any {
        val a = api
        return Proxy.newProxyInstance(a.callbackClass.classLoader, arrayOf(a.callbackClass)) { _, method, args ->
            if (method.name == "onError") {
                plugin.logger.warning("LiteBans 操作失败: ${args?.getOrNull(0)}")
            }
            null
        }
    }
}
