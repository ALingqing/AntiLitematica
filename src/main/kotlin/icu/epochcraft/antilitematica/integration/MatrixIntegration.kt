package icu.epochcraft.antilitematica.integration

import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Matrix 集成（反射，com.matrixplugins.matrixapi.MatrixAPI）。
 *
 * @author 阿清
 */
class MatrixIntegration : AntiCheatIntegration {

    override val name: String = "Matrix"

    private var api: Any? = null
    private var available = false

    override fun isAvailable(): Boolean {
        if (Bukkit.getPluginManager().getPlugin("Matrix") == null) return false
        return runCatching {
            val apiClass = Class.forName("com.matrixplugins.matrixapi.MatrixAPI")
            api = apiClass.getMethod("getAPI").invoke(null)
            available = api != null
        }.isSuccess && available
    }

    override fun flag(player: Player, check: String, level: Int, detail: String) {
        val matrix = api ?: return
        runCatching {
            val data = matrix.javaClass.getMethod("getPlayerData", Player::class.java).invoke(matrix, player) ?: return
            data.javaClass.getMethod("addViolation", String::class.java, Int::class.javaPrimitiveType)
                .invoke(data, check, level)
        }
    }
}
