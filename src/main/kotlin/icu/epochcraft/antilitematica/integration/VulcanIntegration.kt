package icu.epochcraft.antilitematica.integration

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Vulcan 集成（反射，me.frep.vulcan.api.VulcanAPI）。
 *
 * @author 阿清
 */
class VulcanIntegration : AntiCheatIntegration {

    override val name: String = "Vulcan"

    private var api: Any? = null
    private var available = false

    override fun isAvailable(): Boolean {
        if (Bukkit.getPluginManager().getPlugin("Vulcan") == null) return false
        return runCatching {
            val apiClass = Class.forName("me.frep.vulcan.api.VulcanAPI")
            api = apiClass.getMethod("getApi").invoke(null)
            available = api != null
        }.isSuccess && available
    }

    override fun flag(player: Player, check: String, level: Int, detail: String) {
        val vulcan = api ?: return
        runCatching {
            val vp = vulcan.javaClass.getMethod("getPlayer", UUID::class.java).invoke(vulcan, player.uniqueId) ?: return
            vp.javaClass.getMethod("flag", String::class.java, String::class.java, Int::class.javaPrimitiveType)
                .invoke(vp, check, detail, level)
        }
    }
}
