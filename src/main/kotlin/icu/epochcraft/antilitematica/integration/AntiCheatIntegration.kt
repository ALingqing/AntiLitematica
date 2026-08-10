package icu.epochcraft.antilitematica.integration

import org.bukkit.entity.Player

/**
 * 反作弊集成接口：把 AntiLitematica 的检测同步上报给第三方反作弊。
 *
 * 所有实现均通过反射调用，无编译期依赖——服务端未安装对应反作弊时自动降级。
 *
 * @author 阿清
 */
interface AntiCheatIntegration {

    /** 集成名称 */
    val name: String

    /** 当前服务端是否可用（反射探测成功） */
    fun isAvailable(): Boolean

    /** 上报一次违规 */
    fun flag(player: Player, check: String, level: Int, detail: String)
}
