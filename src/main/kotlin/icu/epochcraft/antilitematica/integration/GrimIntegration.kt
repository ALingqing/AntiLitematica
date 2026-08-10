package icu.epochcraft.antilitematica.integration

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.lang.reflect.Method
import java.util.UUID

/**
 * GrimAC 集成（反射，ac.grim.grimac.api.GrimAPI）。
 *
 * @author 阿清
 */
class GrimIntegration : AntiCheatIntegration {

    override val name: String = "GrimAC"

    private var getPlayerData: Method? = null
    private var flagMethod: Method? = null
    private var playerDataManager: Any? = null
    private var available = false

    override fun isAvailable(): Boolean {
        if (Bukkit.getPluginManager().getPlugin("GrimAC") == null) return false
        return runCatching {
            val apiClass = Class.forName("ac.grim.grimac.api.GrimAPI")
            val instance = apiClass.getMethod("getInstance").invoke(null)
            playerDataManager = apiClass.getMethod("getPlayerDataManager").invoke(instance)
            getPlayerData = playerDataManager!!.javaClass.getMethod("getPlayerData", UUID::class.java)

            // 兼容不同版本的 flag 签名
            val dataClass = getPlayerData!!.returnType
            flagMethod = try {
                dataClass.getMethod(
                    "flag", Boolean::class.javaPrimitiveType,
                    String::class.java, String::class.java, Int::class.javaPrimitiveType,
                )
            } catch (e: NoSuchMethodException) {
                dataClass.getMethod("flag", Boolean::class.javaPrimitiveType, String::class.java, String::class.java)
            }
            available = flagMethod != null
        }.isSuccess && available
    }

    override fun flag(player: Player, check: String, level: Int, detail: String) {
        val m = flagMethod ?: return
        val g = getPlayerData ?: return
        val mgr = playerDataManager ?: return
        runCatching {
            val data = g.invoke(mgr, player.uniqueId) ?: return
            if (m.parameterCount == 4) {
                m.invoke(data, false, check, detail, level)
            } else {
                m.invoke(data, false, check, detail)
            }
        }
    }
}
